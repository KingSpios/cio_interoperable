package com.cio.createinteroperable.architecture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Bytecode view of CIO's compiled classes, its compile classpath and its mixin
 * configs, read with ASM only (no class is ever loaded). Generic signatures are
 * deliberately ignored: they are erased and never make the JVM load a class.
 */
final class BytecodeModel {
    static final String CIO = "com/cio/createinteroperable/";
    private static final int API = Opcodes.ASM9;

    private static final String EVENT_BUS_SUBSCRIBER = "Lnet/neoforged/fml/common/EventBusSubscriber;";
    private static final String MOD = "Lnet/neoforged/fml/common/Mod;";
    private static final String ONLY_IN = "Lnet/neoforged/api/distmarker/OnlyIn;";
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";

    /** One compiled CIO class. */
    static final class CioClass {
        final String name;
        String superName;
        final List<String> interfaces = new ArrayList<>();
        /** Referenced type (internal name) -> where it is referenced ("method foo", "field bar", ...). */
        final Map<String, Set<String>> refs = new TreeMap<>();
        /** static final fields with a compile-time constant value. */
        final Map<String, Object> constants = new HashMap<>();
        /** Owners of every invokestatic in this class. */
        final Set<String> staticCallOwners = new TreeSet<>();
        /** "owner.name" of every getstatic in this class. */
        final Set<String> staticFieldReads = new TreeSet<>();
        /** Declared static fields: name -> descriptor. */
        final Map<String, String> staticFields = new HashMap<>();
        boolean modEntrypoint;
        /** null when not an @EventBusSubscriber; otherwise its Dist values (empty = both). */
        List<String> subscriberDists;
        boolean mixin;
        final List<String> mixinTargets = new ArrayList<>();

        CioClass(String name) {
            this.name = name;
        }

        String outermost() {
            int dollar = name.indexOf('$');
            return dollar < 0 ? name : name.substring(0, dollar);
        }

        String simpleName() {
            return name.substring(name.lastIndexOf('/') + 1);
        }
    }

    /** Header of a class on the compile classpath. */
    record ForeignClass(String name, String superName, List<String> interfaces, boolean clientOnly) {
    }

    /** One {@code *.mixin.json}. Mixin class names are internal names. */
    record MixinConfig(String file, String pkg, String plugin, Set<String> common, Set<String> client) {
    }

    final Map<String, CioClass> cio = new TreeMap<>();
    final List<MixinConfig> mixinConfigs = new ArrayList<>();
    private final Map<String, Object> classpathIndex = new HashMap<>(); // name -> Path(dir file) | ZipFile
    private final Map<String, ForeignClass> foreignCache = new HashMap<>();

    static BytecodeModel load() {
        BytecodeModel model = new BytecodeModel();
        for (String dir : property("cio.classes").split(File.pathSeparator)) {
            Path root = Path.of(dir);
            if (Files.isDirectory(root)) {
                model.readCioClasses(root);
            }
        }
        for (String entry : property("cio.compileClasspath").split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                model.indexClasspath(Path.of(entry));
            }
        }
        model.readMixinConfigs(Path.of(property("cio.resources")));
        return model;
    }

    private static String property(String key) {
        String value = System.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("System property " + key + " not set: run this test through ./gradlew test");
        }
        return value;
    }

    // --- CIO classes ---------------------------------------------------

    private void readCioClasses(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                try {
                    CioClass c = parseCio(Files.readAllBytes(p));
                    if (c.name.startsWith(CIO)) {
                        cio.put(c.name, c);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CioClass parseCio(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        CioClass c = new CioClass(reader.getClassName());
        reader.accept(new ClassVisitor(API) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                c.superName = superName;
                if (superName != null) {
                    ref(c, superName, "extends");
                }
                if (interfaces != null) {
                    for (String i : interfaces) {
                        c.interfaces.add(i);
                        ref(c, i, "implements");
                    }
                }
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                switch (descriptor) {
                    case MOD -> c.modEntrypoint = true;
                    case EVENT_BUS_SUBSCRIBER -> {
                        c.subscriberDists = new ArrayList<>();
                        return enumArrayCollector("value", c.subscriberDists);
                    }
                    case MIXIN -> {
                        c.mixin = true;
                        return new AnnotationVisitor(API) {
                            @Override
                            public AnnotationVisitor visitArray(String name) {
                                return new AnnotationVisitor(API) {
                                    @Override
                                    public void visit(String n, Object value) {
                                        if (value instanceof Type t) {
                                            c.mixinTargets.add(t.getInternalName());
                                        } else if (value instanceof String s) {
                                            c.mixinTargets.add(s.replace('.', '/'));
                                        }
                                    }
                                };
                            }
                        };
                    }
                    default -> {
                    }
                }
                return null;
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                desc(c, descriptor, "field " + name);
                if ((access & Opcodes.ACC_STATIC) != 0) {
                    c.staticFields.put(name, descriptor);
                    if ((access & Opcodes.ACC_FINAL) != 0 && value != null) {
                        c.constants.put(name, value);
                    }
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                String where = "method " + name;
                desc(c, descriptor, where);
                if (exceptions != null) {
                    for (String e : exceptions) {
                        ref(c, e, where);
                    }
                }
                return new MethodVisitor(API) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        desc(c, Type.getObjectType(type).getDescriptor(), where);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fname, String fdesc) {
                        ref(c, owner, where);
                        desc(c, fdesc, where);
                        if (opcode == Opcodes.GETSTATIC) {
                            c.staticFieldReads.add(owner + "." + fname);
                        }
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mname, String mdesc, boolean itf) {
                        desc(c, Type.getObjectType(owner).getDescriptor(), where);
                        desc(c, mdesc, where);
                        if (opcode == Opcodes.INVOKESTATIC) {
                            c.staticCallOwners.add(owner);
                        }
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String iname, String idesc, Handle bsm, Object... args) {
                        desc(c, idesc, where);
                        for (Object arg : args) {
                            constant(c, arg, where);
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        constant(c, value, where);
                    }

                    @Override
                    public void visitMultiANewArrayInsn(String adesc, int dims) {
                        desc(c, adesc, where);
                    }

                    @Override
                    public void visitTryCatchBlock(org.objectweb.asm.Label start, org.objectweb.asm.Label end,
                                                   org.objectweb.asm.Label handler, String type) {
                        if (type != null) {
                            ref(c, type, where);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return c;
    }

    private static AnnotationVisitor enumArrayCollector(String element, List<String> out) {
        return new AnnotationVisitor(API) {
            @Override
            public AnnotationVisitor visitArray(String name) {
                if (!element.equals(name)) {
                    return null;
                }
                return new AnnotationVisitor(API) {
                    @Override
                    public void visitEnum(String n, String descriptor, String value) {
                        out.add(value);
                    }
                };
            }

            @Override
            public void visitEnum(String name, String descriptor, String value) {
                if (element.equals(name)) {
                    out.add(value);
                }
            }
        };
    }

    private static void constant(CioClass c, Object value, String where) {
        if (value instanceof Type t) {
            desc(c, t.getDescriptor(), where);
        } else if (value instanceof Handle h) {
            ref(c, h.getOwner(), where);
            desc(c, h.getDesc(), where);
        }
    }

    private static void desc(CioClass c, String descriptor, String where) {
        Type type = Type.getType(descriptor);
        switch (type.getSort()) {
            case Type.METHOD -> {
                for (Type arg : type.getArgumentTypes()) {
                    desc(c, arg.getDescriptor(), where);
                }
                desc(c, type.getReturnType().getDescriptor(), where);
            }
            case Type.ARRAY -> desc(c, type.getElementType().getDescriptor(), where);
            case Type.OBJECT -> ref(c, type.getInternalName(), where);
            default -> {
            }
        }
    }

    private static void ref(CioClass c, String type, String where) {
        if (type.startsWith("[")) {
            desc(c, type, where);
            return;
        }
        if (!type.equals(c.name)) {
            c.refs.computeIfAbsent(type, k -> new LinkedHashSet<>()).add(where);
        }
    }

    // --- compile classpath ---------------------------------------------

    private void indexClasspath(Path entry) {
        try {
            if (Files.isDirectory(entry)) {
                try (Stream<Path> files = Files.walk(entry)) {
                    files.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                        String name = entry.relativize(p).toString().replace(File.separatorChar, '/');
                        classpathIndex.putIfAbsent(name.substring(0, name.length() - 6), p);
                    });
                }
            } else if (Files.isRegularFile(entry) && entry.toString().endsWith(".jar")) {
                ZipFile zip = new ZipFile(entry.toFile());
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.endsWith(".class") && !name.startsWith("META-INF/")) {
                        classpathIndex.putIfAbsent(name.substring(0, name.length() - 6), zip);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Header of a compile-classpath class, or null when it is not on the classpath. */
    ForeignClass foreign(String name) {
        if (foreignCache.containsKey(name)) {
            return foreignCache.get(name);
        }
        ForeignClass result = null;
        Object source = classpathIndex.get(name);
        if (source != null) {
            try {
                byte[] bytes;
                if (source instanceof Path p) {
                    bytes = Files.readAllBytes(p);
                } else {
                    ZipFile zip = (ZipFile) source;
                    try (InputStream in = zip.getInputStream(zip.getEntry(name + ".class"))) {
                        bytes = in.readAllBytes();
                    }
                }
                result = parseForeign(bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        foreignCache.put(name, result);
        return result;
    }

    boolean onClasspath(String name) {
        return classpathIndex.containsKey(name);
    }

    long countClasspathClasses(java.util.function.Predicate<String> filter) {
        return classpathIndex.keySet().stream().filter(filter).count();
    }

    private static ForeignClass parseForeign(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        List<String> dists = new ArrayList<>();
        boolean[] annotated = {false};
        reader.accept(new ClassVisitor(API) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (ONLY_IN.equals(descriptor)) {
                    annotated[0] = true;
                    return enumArrayCollector("value", dists);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        String[] interfaces = reader.getInterfaces();
        return new ForeignClass(reader.getClassName(), reader.getSuperName(),
                interfaces == null ? List.of() : List.of(interfaces),
                annotated[0] && dists.contains("CLIENT"));
    }

    // --- mixin configs -------------------------------------------------

    private void readMixinConfigs(Path resources) {
        try (Stream<Path> files = Files.list(resources)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".mixin.json")).sorted().toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                String pkg = json.get("package").getAsString().replace('.', '/');
                String plugin = json.has("plugin") ? json.get("plugin").getAsString().replace('.', '/') : null;
                Set<String> common = new LinkedHashSet<>();
                Set<String> client = new LinkedHashSet<>();
                names(json, "mixins", pkg, common);
                names(json, "server", pkg, common);
                names(json, "client", pkg, client);
                mixinConfigs.add(new MixinConfig(file.getFileName().toString(), pkg, plugin, common, client));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void names(JsonObject json, String key, String pkg, Set<String> out) {
        if (!json.has(key)) {
            return;
        }
        JsonArray array = json.getAsJsonArray(key);
        for (JsonElement e : array) {
            out.add(pkg + "/" + e.getAsString().replace('.', '/'));
        }
    }

    /** Mixin config listing this mixin class, keyed by class name. */
    Map<String, MixinConfig> mixinOwners() {
        Map<String, MixinConfig> owners = new LinkedHashMap<>();
        for (MixinConfig config : mixinConfigs) {
            config.common().forEach(m -> owners.put(m, config));
            config.client().forEach(m -> owners.put(m, config));
        }
        return owners;
    }
}
