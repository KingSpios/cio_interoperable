package com.cio.createinteroperable.architecture;

import com.cio.createinteroperable.architecture.BytecodeModel.CioClass;
import com.cio.createinteroperable.architecture.BytecodeModel.ForeignClass;
import com.cio.createinteroperable.architecture.BytecodeModel.MixinConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Bytecode-level guard for CIO's defining property: it loads on any mod set,
 * and only the features whose mods are present ever touch those mods' classes.
 *
 * <p>Each CIO class gets a <em>gate</em>, the set of optional mods it may rely on:</p>
 * <ul>
 *     <li><b>Always-loaded classes</b> (the {@code @Mod} entrypoint, the core
 *     registries in {@link #ALWAYS_LOADED}, every {@code @EventBusSubscriber},
 *     every mixin config plugin): gate = nothing.</li>
 *     <li><b>Modules</b>: any class declaring {@code public static final String
 *     REQUIRES = "modA,modB"} (nested classes inherit it). The bootstrap only
 *     touches a module after checking those mods are loaded.</li>
 *     <li><b>Mixins</b>: the mods owning the mixin's targets, plus whatever its
 *     config's plugin checks (the {@code Mods} constants it names).</li>
 * </ul>
 *
 * <p>Rules, all about what a class <em>references</em> (anything that can make
 * the JVM load a class: supertypes, member descriptors, instructions):</p>
 * <ol>
 *     <li>A gated class may reference an optional mod's types only if its gate
 *     covers that mod, and may reference another CIO class only if the gate
 *     covers every mod in that class's supertype chain (its "hard" needs: that
 *     class can't even be loaded without them).</li>
 *     <li>An always-loaded class may additionally not reference a non-module CIO
 *     class that itself directly touches an optional mod ("soft" needs), and
 *     only {@code CIOModules} may reference a module. Gated content is reached
 *     through a module, never straight from always-loaded code; a module that
 *     needs to react to something hooks its own listener in.</li>
 *     <li>A common (non-client) class never references a client-only type
 *     ({@code @OnlyIn(Dist.CLIENT)} or under {@code net.minecraft.client},
 *     {@code com.mojang.blaze3d}, {@code net.neoforged.neoforge.client}, or a
 *     subtype of one). Client classes are the {@code Dist.CLIENT} subscribers,
 *     mixin "client" entries, subclasses of client-only types, and classes
 *     named *Client / *ClientUtil / *ClientEvents / *Renderer / *Visual /
 *     *Screen / *Particle or living in a {@code client} package. Calling such a
 *     class from common code is the project's idiom and is allowed.</li>
 * </ol>
 *
 * <p>What this can't see: whether a bootstrap call into a module sits behind
 * the right {@code present()} check. The dedicated-server smoke run
 * ({@code ./gradlew smokeTest}) covers that by booting reduced mod sets.</p>
 */
class LoadSafetyTest {

    /** Package prefix of each optional mod CIO compiles against -> its mod id. */
    private static final Map<String, String> OPTIONAL_NAMESPACES = new LinkedHashMap<>();
    /** Mods that are always present when the key mod is (hard dependencies of it). */
    private static final Map<String, Set<String>> IMPLIES = Map.of(
            "refurbished_furniture", Set.of("framework"),
            "powergrid", Set.of("architectury"));

    static {
        OPTIONAL_NAMESPACES.put("org/patryk3211/powergrid/", "powergrid");
        OPTIONAL_NAMESPACES.put("com/george_vi/electroenergetics/", "electroenergetics");
        OPTIONAL_NAMESPACES.put("com/mrcrayfish/furniture/refurbished/", "refurbished_furniture");
        OPTIONAL_NAMESPACES.put("com/mrcrayfish/framework/", "framework");
        OPTIONAL_NAMESPACES.put("com/momosoftworks/coldsweat/", "cold_sweat");
        OPTIONAL_NAMESPACES.put("de/devin/pipesnphysics/", "pipesnphysics");
        OPTIONAL_NAMESPACES.put("de/maxhenkel/voicechat/", "voicechat");
        OPTIONAL_NAMESPACES.put("minecrafttransportsimulator/", "mts");
        OPTIONAL_NAMESPACES.put("dev/architectury/", "architectury");
    }

    /** Core classes loaded on every install (besides the auto-detected ones). Nested classes included. */
    private static final Set<String> ALWAYS_LOADED = Set.of(
            "CIOBlocks", "CIOBlockEntities", "CIOItems", "CIOCreativeTab", "modules/CIOModules",
            "CIOConfig", "CIOFluids", "CIOParticles", "compat/Mods", "compat/ModGatedMixinPlugin");

    /** The presence enum; mixin plugins name their mods by its constants. */
    private static final String MODS = BytecodeModel.CIO + "compat/Mods";

    /** The one always-loaded class allowed to touch modules (each behind its REQUIRES check). */
    private static final String BOOTSTRAP = BytecodeModel.CIO + "modules/CIOModules";

    private static final List<String> CLIENT_PREFIXES = List.of("net/minecraft/client/", "com/mojang/blaze3d/");
    private static final String NEOFORGE_CLIENT = "net/neoforged/neoforge/client/";
    private static final List<String> CLIENT_NAME_SUFFIXES = List.of(
            "Client", "ClientUtil", "ClientEvents", "Renderer", "Visual", "Screen", "Particle");

    private static BytecodeModel model;
    private static Map<String, MixinConfig> mixinOwners;
    private final Map<String, Set<String>> hardCache = new HashMap<>();
    private final Map<String, Boolean> clientTypeCache = new HashMap<>();

    @BeforeAll
    static void loadModel() {
        model = BytecodeModel.load();
        mixinOwners = model.mixinOwners();
    }

    @Test
    void modelSanity() {
        assertTrue(model.cio.size() > 100, "expected CIO classes, found " + model.cio.size());
        assertTrue(model.cio.containsKey(BytecodeModel.CIO + "CreateInteroperable"), "entrypoint missing");
        // The client-only rule leans on Minecraft's own @OnlyIn markers; make sure they are visible.
        ForeignClass minecraft = model.foreign("net/minecraft/client/Minecraft");
        assertTrue(minecraft != null && minecraft.clientOnly(), "net/minecraft/client/Minecraft not seen as @OnlyIn(CLIENT)");
        for (String mixin : mixinOwners.keySet()) {
            assertTrue(model.cio.containsKey(mixin), "mixin listed in a config but not compiled: " + mixin);
        }
        // The gating rules key off these; if they vanish the rules silently check less.
        assertTrue(model.cio.containsKey(BOOTSTRAP), "bootstrap missing: " + BOOTSTRAP);
        long modules = model.cio.values().stream().filter(LoadSafetyTest::isModule).count();
        assertTrue(modules >= 10, "expected the module classes (REQUIRES constants), found " + modules);
        // Plugin gates map Mods constants to ids through Mods.Id; every entry needs its twin.
        String enumDesc = "L" + MODS + ";";
        long entries = 0;
        for (Map.Entry<String, String> field : model.cio.get(MODS).staticFields.entrySet()) {
            if (field.getValue().equals(enumDesc)) {
                modIdOf(field.getKey());
                entries++;
            }
        }
        assertTrue(entries >= 9, "expected the Mods entries, found " + entries);
        // Every mixin config plugin must name at least one mod, or its config is ungated.
        for (MixinConfig config : model.mixinConfigs) {
            if (config.plugin() != null) {
                assertTrue(!pluginGate(config.plugin()).isEmpty(), config.plugin() + " reads no Mods constant");
            }
        }
    }

    @Test
    void optionalModsStayBehindTheirGates() {
        List<String> violations = new ArrayList<>();
        for (CioClass c : model.cio.values()) {
            Gate gate = gateOf(c);
            if (gate == null) {
                continue;
            }
            Set<String> allowed = closure(gate.mods);
            for (Map.Entry<String, Set<String>> ref : c.refs.entrySet()) {
                String type = ref.getKey();
                String where = String.join(", ", ref.getValue());
                String mod = optionalMod(type);
                if (mod != null) {
                    if (!allowed.contains(mod)) {
                        violations.add(describe(c, gate) + " references " + mod + " type " + dotted(type) + " (" + where + ")");
                    }
                    continue;
                }
                CioClass target = model.cio.get(type);
                if (target == null || target.outermost().equals(c.outermost())) {
                    continue;
                }
                Set<String> hard = minus(hardNeeds(type), allowed);
                if (!hard.isEmpty()) {
                    violations.add(describe(c, gate) + " references " + dotted(type) + ", which cannot load without "
                            + hard + " (" + where + ")");
                    continue;
                }
                if (gate.alwaysLoaded && isModule(target)) {
                    if (!c.outermost().equals(BOOTSTRAP)) {
                        violations.add(describe(c, gate) + " references module " + dotted(type)
                                + "; only " + dotted(BOOTSTRAP) + " may, after checking its REQUIRES (" + where + ")");
                    }
                } else if (gate.alwaysLoaded) {
                    Set<String> soft = minus(softNeeds(target), allowed);
                    if (!soft.isEmpty()) {
                        violations.add(describe(c, gate) + " references " + dotted(type) + ", which directly uses "
                                + soft + "; reach it through a module (" + where + ")");
                    }
                }
            }
        }
        report("Optional-mod gating", violations);
    }

    @Test
    void commonClassesNeverTouchClientTypes() {
        List<String> violations = new ArrayList<>();
        for (CioClass c : model.cio.values()) {
            if (isClientClass(c)) {
                continue;
            }
            for (Map.Entry<String, Set<String>> ref : c.refs.entrySet()) {
                String type = ref.getKey();
                if (model.cio.containsKey(type) || !isClientType(type) || allowedClientRef(c, type)) {
                    continue;
                }
                violations.add(dotted(c.name) + " references client-only " + clientChain(type)
                        + " (" + String.join(", ", ref.getValue()) + ")");
            }
        }
        report("Client-only types in common classes", violations);
    }

    // --- gates -----------------------------------------------------------

    private record Gate(Set<String> mods, boolean alwaysLoaded, String why) {
    }

    private Gate gateOf(CioClass c) {
        MixinConfig config = mixinOwners.get(c.name);
        if (config != null) {
            Set<String> mods = new TreeSet<>();
            for (String target : c.mixinTargets) {
                String mod = optionalMod(target);
                if (mod != null) {
                    mods.add(mod);
                } else if (model.cio.containsKey(target)) {
                    // A mixin into a CIO class only applies once that class loads.
                    mods.addAll(hardNeeds(target));
                }
            }
            if (config.plugin() != null) {
                mods.addAll(pluginGate(config.plugin()));
            }
            return new Gate(mods, false, "mixin of " + config.file());
        }
        String requires = requiresOf(c);
        if (requires != null) {
            Set<String> mods = new TreeSet<>();
            for (String id : requires.split(",")) {
                if (!id.isBlank()) {
                    mods.add(id.trim());
                }
            }
            return new Gate(mods, false, "module");
        }
        if (isAlwaysLoaded(c)) {
            return new Gate(Set.of(), true, "always loaded");
        }
        return null;
    }

    private boolean isAlwaysLoaded(CioClass c) {
        CioClass outer = model.cio.getOrDefault(c.outermost(), c);
        if (outer.modEntrypoint || outer.subscriberDists != null) {
            return true;
        }
        for (MixinConfig config : model.mixinConfigs) {
            if (outer.name.equals(config.plugin())) {
                return true;
            }
        }
        String rel = outer.name.substring(BytecodeModel.CIO.length());
        return ALWAYS_LOADED.contains(rel);
    }

    private static String requiresOf(CioClass c) {
        Object own = c.constants.get("REQUIRES");
        if (own instanceof String s) {
            return s;
        }
        CioClass outer = model.cio.get(c.outermost());
        if (outer != null && outer != c && outer.constants.get("REQUIRES") instanceof String s) {
            return s;
        }
        return null;
    }

    private static boolean isModule(CioClass c) {
        return requiresOf(c) != null;
    }

    /** Mods a mixin config plugin checks: every {@code Mods} constant it reads, mapped through {@code Mods.Id}. */
    private static Set<String> pluginGate(String plugin) {
        Set<String> mods = new TreeSet<>();
        CioClass p = model.cio.get(plugin);
        if (p == null) {
            return mods;
        }
        for (String read : p.staticFieldReads) {
            if (read.startsWith(MODS + ".")) {
                mods.add(modIdOf(read.substring(MODS.length() + 1)));
            }
        }
        return mods;
    }

    /** {@code Mods.Id.<constant>}: the id of the {@code Mods} entry with that name. */
    private static String modIdOf(String constant) {
        Object id = model.cio.get(MODS + "$Id").constants.get(constant);
        if (!(id instanceof String s)) {
            throw new IllegalStateException("Mods." + constant + " has no matching Mods.Id constant");
        }
        return s;
    }

    // --- needs -------------------------------------------------------------

    /** Optional mods in a class's supertype chain: it can't be loaded at all without them. */
    private Set<String> hardNeeds(String type) {
        Set<String> cached = hardCache.get(type);
        if (cached != null) {
            return cached;
        }
        hardCache.put(type, Set.of()); // cycle guard
        Set<String> needs = new TreeSet<>();
        String mod = optionalMod(type);
        if (mod != null) {
            needs.add(mod);
        } else {
            for (String sup : supertypes(type)) {
                needs.addAll(hardNeeds(sup));
            }
        }
        hardCache.put(type, needs);
        return needs;
    }

    /** Hard needs plus the mods this class touches directly in any member or instruction. */
    private Set<String> softNeeds(CioClass c) {
        Set<String> needs = new TreeSet<>(hardNeeds(c.name));
        for (String ref : c.refs.keySet()) {
            String mod = optionalMod(ref);
            if (mod != null) {
                needs.add(mod);
            } else if (model.cio.containsKey(ref)) {
                needs.addAll(hardNeeds(ref));
            }
        }
        return needs;
    }

    private List<String> supertypes(String type) {
        List<String> result = new ArrayList<>();
        CioClass c = model.cio.get(type);
        if (c != null) {
            if (c.superName != null) {
                result.add(c.superName);
            }
            result.addAll(c.interfaces);
            return result;
        }
        ForeignClass f = model.foreign(type);
        if (f != null) {
            if (f.superName() != null) {
                result.add(f.superName());
            }
            result.addAll(f.interfaces());
        }
        return result;
    }

    // --- client side ---------------------------------------------------------

    private Set<String> clientClasses;

    /**
     * Client classes: the designated ones (see the class doc), plus, to a fixed
     * point, every CIO class whose only referrers are client classes. Such a
     * class can only ever load from client code (classes found by reflection,
     * like plugins other mods discover, have no referrers and are never inferred).
     */
    private boolean isClientClass(CioClass c) {
        if (clientClasses == null) {
            clientClasses = new java.util.HashSet<>();
            Map<String, Set<String>> referrers = new HashMap<>();
            for (CioClass k : model.cio.values()) {
                if (isDesignatedClient(k)) {
                    clientClasses.add(k.name);
                }
                for (String ref : k.refs.keySet()) {
                    CioClass target = model.cio.get(ref);
                    if (target != null && !target.outermost().equals(k.outermost())) {
                        referrers.computeIfAbsent(ref, r -> new TreeSet<>()).add(k.name);
                    }
                }
            }
            boolean changed = true;
            while (changed) {
                changed = false;
                for (CioClass k : model.cio.values()) {
                    if (clientClasses.contains(k.name) || isAlwaysLoaded(k) || k.mixin) {
                        continue;
                    }
                    CioClass outer = model.cio.getOrDefault(k.outermost(), k);
                    Set<String> from = referrers.getOrDefault(outer.name, Set.of());
                    boolean outerClient = outer != k && clientClasses.contains(outer.name);
                    if (outerClient || (!from.isEmpty() && clientClasses.containsAll(from))) {
                        clientClasses.add(k.name);
                        changed = true;
                    }
                }
            }
        }
        return clientClasses.contains(c.name);
    }

    private boolean isDesignatedClient(CioClass c) {
        CioClass outer = model.cio.getOrDefault(c.outermost(), c);
        for (CioClass k : outer == c ? List.of(c) : List.of(c, outer)) {
            if (k.subscriberDists != null && k.subscriberDists.equals(List.of("CLIENT"))) {
                return true;
            }
            MixinConfig config = mixinOwners.get(k.name);
            if (config != null && config.client().contains(k.name)) {
                return true;
            }
            if (k.mixin && !k.mixinTargets.isEmpty() && k.mixinTargets.stream().allMatch(this::isClientType)) {
                return true;
            }
            if (k.name.contains("/client/")) {
                return true;
            }
            String simple = k.simpleName();
            int dollar = simple.indexOf('$');
            String base = dollar < 0 ? simple : simple.substring(0, dollar);
            if (CLIENT_NAME_SUFFIXES.stream().anyMatch(base::endsWith)) {
                return true;
            }
            for (String sup : supertypes(k.name)) {
                if (isClientType(sup) || (model.cio.containsKey(sup) && isDesignatedClient(model.cio.get(sup)))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * NeoForge's own {@code client} package counts only when referenced
     * directly: it also holds extension interfaces that common vanilla types
     * implement (e.g. {@code MenuProvider} extends one), so it must not make
     * every subtype client-only.
     */
    private boolean isClientType(String type) {
        return type.startsWith(NEOFORGE_CLIENT) || isStrictClientType(type);
    }

    private boolean isStrictClientType(String type) {
        Boolean cached = clientTypeCache.get(type);
        if (cached != null) {
            return cached;
        }
        clientTypeCache.put(type, false); // cycle guard
        boolean client = CLIENT_PREFIXES.stream().anyMatch(type::startsWith);
        if (!client && !model.cio.containsKey(type)) {
            ForeignClass f = model.foreign(type);
            if (f != null) {
                client = f.clientOnly() || supertypes(type).stream().anyMatch(this::isStrictClientType);
            }
        }
        clientTypeCache.put(type, client);
        return client;
    }

    /** "a.B (via c.D -> e.F)": the supertype path that makes a type client-only. */
    private String clientChain(String type) {
        StringBuilder path = new StringBuilder(dotted(type));
        String current = type;
        List<String> via = new ArrayList<>();
        while (!CLIENT_PREFIXES.stream().anyMatch(current::startsWith)) {
            ForeignClass f = model.foreign(current);
            if (f == null || f.clientOnly()) {
                break;
            }
            String next = null;
            for (String sup : supertypes(current)) {
                if (isClientType(sup)) {
                    next = sup;
                    break;
                }
            }
            if (next == null) {
                break;
            }
            via.add(dotted(next));
            current = next;
        }
        if (!via.isEmpty()) {
            path.append(" (via ").append(String.join(" -> ", via)).append(")");
        }
        return path.toString();
    }

    /**
     * Create's {@code ValueBoxTransform} puts {@code PoseStack} in its abstract
     * {@code rotate}/{@code transform} signatures, so every Create-style value
     * box (on common BlockEntity classes, exactly as Create does it) must name
     * it. Its body only runs client side.
     */
    private boolean allowedClientRef(CioClass c, String type) {
        return type.equals("com/mojang/blaze3d/vertex/PoseStack") && extendsType(c.name, "ValueBoxTransform");
    }

    private boolean extendsType(String type, String simpleName) {
        for (String sup : supertypes(type)) {
            if (sup.endsWith("/" + simpleName) || extendsType(sup, simpleName)) {
                return true;
            }
        }
        return false;
    }

    // --- helpers -------------------------------------------------------------

    private static String optionalMod(String type) {
        for (Map.Entry<String, String> e : OPTIONAL_NAMESPACES.entrySet()) {
            if (type.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static Set<String> closure(Set<String> mods) {
        Set<String> all = new TreeSet<>(mods);
        for (String mod : mods) {
            all.addAll(IMPLIES.getOrDefault(mod, Set.of()));
        }
        return all;
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> result = new TreeSet<>(a);
        result.removeAll(b);
        return result;
    }

    private static String describe(CioClass c, Gate gate) {
        return dotted(c.name) + " [" + gate.why + (gate.mods.isEmpty() ? "" : " " + gate.mods) + "]";
    }

    private static String dotted(String internalName) {
        String shortName = internalName.startsWith(BytecodeModel.CIO)
                ? internalName.substring(BytecodeModel.CIO.length()) : internalName;
        return shortName.replace('/', '.');
    }

    private static void report(String title, List<String> violations) {
        if (!violations.isEmpty()) {
            violations.sort(null);
            fail(title + ": " + violations.size() + " violation(s)\n  " + String.join("\n  ", violations));
        }
    }
}
