package com.cio.createinteroperable.smoke;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Boots a real NeoForge dedicated server once per mod-set profile and checks
 * that CIO comes up cleanly on it: the server reaches "Done", no crash report,
 * no class-loading / mixin error mentioning CIO, and CIO's "modules enabled"
 * log line lists exactly the modules that mod set should enable.
 *
 * <p>Mod jars come from a local mods folder (normally a real instance's); each
 * profile names top-level mod ids and their required (non-client) dependencies
 * are added from the jars' own neoforge.mods.toml, jar-in-jar mods included.
 * The server is installed once (NeoForge installer) and reused; each profile
 * starts from empty mods/config/world folders.</p>
 *
 * <p>Run through {@code ./gradlew smokeTest}; see build.gradle for options.</p>
 */
public final class SmokeRunner {

    private static final String ENABLED_PREFIX = "Create: Interoperable modules enabled: ";
    private static final Pattern LOAD_ERROR = Pattern.compile(
            "NoClassDefFoundError|ClassNotFoundException|IllegalClassLoadError|for invalid dist|"
                    + "MixinApplyError|InvalidMixinException|MixinTransformerError|ExceptionInInitializerError|"
                    + "NoSuchMethodError|NoSuchFieldError|IncompatibleClassChangeError|VerifyError");

    private static final Pattern BENIGN = Pattern.compile("WARN\\] \\[mixin/\\]: Error loading class: ");

    record Profile(String name, List<String> mods, Set<String> expectedModules) {
    }

    record ModJar(Path jar, Set<String> modIds, Map<String, Set<String>> requiredDeps) {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parseArgs(args);
        Path serverDir = Path.of(opts.get("server-dir"));
        Path modsSource = Path.of(opts.get("mods-dir"));
        Path cioJar = Path.of(opts.get("cio-jar"));
        String neoVersion = opts.get("neoforge-version");
        int timeout = Integer.parseInt(opts.getOrDefault("timeout-seconds", "300"));
        List<Profile> profiles = readProfiles(Path.of(opts.get("profiles")));
        String only = opts.getOrDefault("only", "");
        if (!only.isBlank()) {
            Set<String> wanted = new LinkedHashSet<>(Arrays.asList(only.split(",")));
            profiles = profiles.stream().filter(p -> wanted.contains(p.name())).toList();
            if (profiles.isEmpty()) {
                throw new IllegalArgumentException("no profile matches " + wanted);
            }
        }
        if (!Files.isDirectory(modsSource)) {
            throw new IllegalArgumentException("mods folder not found: " + modsSource
                    + " (set -PcioSmokeModsDir=<folder with the mod jars>)");
        }
        if (!"true".equals(opts.get("accept-eula"))) {
            throw new IllegalArgumentException("A Minecraft server only starts once its EULA "
                    + "(https://aka.ms/MinecraftEULA) is accepted: pass -PcioSmokeAcceptEula=true to accept it.");
        }

        Map<String, ModJar> index = indexMods(modsSource);
        installServer(serverDir, neoVersion);

        List<String> failures = new ArrayList<>();
        List<String> flaky = new ArrayList<>();
        for (Profile profile : profiles) {
            System.out.println();
            System.out.println("=== smoke profile '" + profile.name() + "' ===");
            List<String> problems = runProfile(profile, serverDir, cioJar, index, neoVersion, timeout);
            if (problems.isEmpty()) {
                System.out.println("PASS " + profile.name());
                continue;
            }
            // One retry: a failure that doesn't reproduce is reported as flaky (its
            // log and crash report are kept) instead of failing the run outright.
            System.out.println("failed, retrying once:");
            problems.forEach(p -> System.out.println("  - " + p));
            List<String> retry = runProfile(profile, serverDir, cioJar, index, neoVersion, timeout);
            if (retry.isEmpty()) {
                System.out.println("FLAKY " + profile.name() + " (passed on retry; first failure kept in smoke-logs)");
                flaky.add(profile.name());
            } else {
                System.out.println("FAIL " + profile.name());
                retry.forEach(p -> System.out.println("  - " + p));
                failures.add(profile.name());
            }
        }
        System.out.println();
        Path logs = serverDir.resolve("smoke-logs");
        if (!failures.isEmpty()) {
            System.out.println("Smoke test FAILED for: " + String.join(", ", failures) + " (logs: " + logs + ")");
            System.exit(1);
        }
        System.out.println("Smoke test passed: " + profiles.size() + " profile(s)."
                + (flaky.isEmpty() ? "" : " FLAKY (failed once, passed on retry): " + String.join(", ", flaky)
                + " — see the *-FAILED-* files in " + logs));
    }

    // --- profiles ----------------------------------------------------------

    private static List<Profile> readProfiles(Path file) throws IOException {
        List<Profile> profiles = new ArrayList<>();
        for (String raw : Files.readAllLines(file)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] cols = line.split("\\|");
            if (cols.length != 3) {
                throw new IllegalArgumentException("bad profile line (want name | mods | modules): " + raw);
            }
            Set<String> expected = new TreeSet<>(words(cols[2]));
            expected.remove("-");
            profiles.add(new Profile(cols[0].strip(), words(cols[1]), expected));
        }
        return profiles;
    }

    private static List<String> words(String s) {
        return Arrays.stream(s.strip().split("\\s+")).filter(w -> !w.isEmpty()).toList();
    }

    // --- mod index ---------------------------------------------------------

    private static Map<String, ModJar> indexMods(Path dir) throws IOException {
        Map<String, ModJar> index = new HashMap<>();
        try (Stream<Path> jars = Files.list(dir)) {
            for (Path jar : jars.filter(p -> p.toString().endsWith(".jar")).sorted().toList()) {
                ModJar mod = readModJar(jar);
                for (String id : mod.modIds()) {
                    index.putIfAbsent(id, mod);
                }
            }
        }
        return index;
    }

    /** Mod ids a jar provides (its own and every jar-in-jar's) and their required server-side deps. */
    private static ModJar readModJar(Path jar) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        Map<String, Set<String>> deps = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(jar)) {
            scanJar(in, ids, deps, 0);
        }
        return new ModJar(jar, ids, deps);
    }

    private static void scanJar(InputStream in, Set<String> ids, Map<String, Set<String>> deps, int depth) throws IOException {
        ZipInputStream zip = new ZipInputStream(in);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            String name = entry.getName();
            if (name.equals("META-INF/neoforge.mods.toml")) {
                parseToml(new String(zip.readAllBytes(), StandardCharsets.UTF_8), ids, deps);
            } else if (depth < 2 && name.endsWith(".jar")
                    && (name.startsWith("META-INF/jarjar/") || name.startsWith("META-INF/jars/"))) {
                scanJar(new ByteArrayInputStream(zip.readAllBytes()), ids, deps, depth + 1);
            }
        }
    }

    private static final Pattern MOD_ID = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"");

    private static void parseToml(String toml, Set<String> ids, Map<String, Set<String>> deps) {
        String[] parts = toml.split("\\[\\[");
        for (String part : parts) {
            if (part.startsWith("mods]]")) {
                Matcher m = MOD_ID.matcher(part);
                if (m.find()) {
                    ids.add(m.group(1));
                }
            } else if (part.startsWith("dependencies.")) {
                String owner = part.substring("dependencies.".length(), part.indexOf("]]")).replace("\"", "").strip();
                Matcher m = MOD_ID.matcher(part);
                if (!m.find()) {
                    continue;
                }
                String dep = m.group(1);
                boolean required = Pattern.compile("type\\s*=\\s*\"required\"", Pattern.CASE_INSENSITIVE).matcher(part).find()
                        || Pattern.compile("mandatory\\s*=\\s*true").matcher(part).find();
                Matcher side = Pattern.compile("side\\s*=\\s*\"([A-Z]+)\"").matcher(part);
                boolean serverSide = !side.find() || !side.group(1).equals("CLIENT");
                if (required && serverSide && !dep.equals("neoforge") && !dep.equals("minecraft")) {
                    deps.computeIfAbsent(owner, k -> new LinkedHashSet<>()).add(dep);
                }
            }
        }
    }

    /** The jars to install for a profile: CIO's own required deps plus the profile's mods, closed over requirements. */
    private static Set<Path> resolve(Profile profile, ModJar cio, Map<String, ModJar> index, List<String> problems) {
        Set<Path> jars = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>(profile.mods());
        cio.requiredDeps().values().forEach(queue::addAll);
        while (!queue.isEmpty()) {
            String id = queue.remove(0);
            if (!seen.add(id) || cio.modIds().contains(id)) {
                continue;
            }
            ModJar mod = index.get(id);
            if (mod == null) {
                problems.add("mod '" + id + "' is not in the mods folder");
                continue;
            }
            if (jars.add(mod.jar())) {
                mod.requiredDeps().values().forEach(queue::addAll);
            }
        }
        return jars;
    }

    // --- server ------------------------------------------------------------

    private static void installServer(Path serverDir, String neoVersion) throws Exception {
        if (Files.exists(argsFile(serverDir, neoVersion))) {
            return;
        }
        Files.createDirectories(serverDir);
        Path installer = serverDir.resolve("neoforge-" + neoVersion + "-installer.jar");
        if (!Files.exists(installer)) {
            String url = "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + neoVersion
                    + "/neoforge-" + neoVersion + "-installer.jar";
            System.out.println("Downloading " + url);
            HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<Path> response = http.send(HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofFile(installer));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(installer);
                throw new IOException("installer download failed: HTTP " + response.statusCode());
            }
        }
        System.out.println("Installing NeoForge " + neoVersion + " server into " + serverDir + " (one-time)");
        Process p = new ProcessBuilder(javaExe(), "-jar", installer.toString(), "--install-server", serverDir.toString())
                .directory(serverDir.toFile()).redirectErrorStream(true).inheritIO().start();
        if (p.waitFor() != 0 || !Files.exists(argsFile(serverDir, neoVersion))) {
            throw new IOException("NeoForge server install failed (exit " + p.exitValue() + ")");
        }
    }

    private static Path argsFile(Path serverDir, String neoVersion) {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        return serverDir.resolve("libraries/net/neoforged/neoforge/" + neoVersion
                + (windows ? "/win_args.txt" : "/unix_args.txt"));
    }

    private static String javaExe() {
        return ProcessHandle.current().info().command().orElse("java");
    }

    private static List<String> runProfile(Profile profile, Path serverDir, Path cioJarPath, Map<String, ModJar> index,
                                           String neoVersion, int timeoutSeconds) throws Exception {
        List<String> problems = new ArrayList<>();
        ModJar cio = readModJar(cioJarPath);
        Set<Path> jars = resolve(profile, cio, index, problems);
        if (!problems.isEmpty()) {
            return problems;
        }

        for (String dir : List.of("mods", "config", "world", "logs", "crash-reports", "defaultconfigs")) {
            deleteTree(serverDir.resolve(dir));
        }
        Path mods = Files.createDirectories(serverDir.resolve("mods"));
        Files.copy(cioJarPath, mods.resolve(cioJarPath.getFileName()));
        for (Path jar : jars) {
            Files.copy(jar, mods.resolve(jar.getFileName()));
        }
        System.out.println("mods: " + jars.stream().map(j -> j.getFileName().toString()).sorted().toList());

        Files.writeString(serverDir.resolve("eula.txt"), "# Accepted via -PcioSmokeAcceptEula=true (smoke test)\neula=true\n");
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Files.writeString(serverDir.resolve("server.properties"), String.join("\n",
                "level-type=minecraft\\:flat", "generate-structures=false", "online-mode=false",
                "server-port=" + port, "spawn-protection=0", "max-tick-time=-1", "view-distance=3",
                "simulation-distance=3", "sync-chunk-writes=false", "enable-rcon=false", "enable-query=false",
                "motd=CIO smoke test") + "\n");

        List<String> command = new ArrayList<>(List.of(javaExe(), "-Xmx3G",
                "@" + serverDir.relativize(argsFile(serverDir, neoVersion)).toString().replace('\\', '/'), "--nogui"));
        Process server = new ProcessBuilder(command).directory(serverDir.toFile()).redirectErrorStream(true).start();
        List<String> output = new ArrayList<>();
        AtomicBoolean done = new AtomicBoolean();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(server.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    synchronized (output) {
                        output.add(line);
                    }
                    if (line.contains("Done (") && line.contains("For help, type")) {
                        done.set(true);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        reader.setDaemon(true);
        reader.start();

        while (!done.get() && server.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(250);
        }
        if (done.get()) {
            OutputStream stdin = server.getOutputStream();
            stdin.write("stop\n".getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            if (!server.waitFor(120, TimeUnit.SECONDS)) {
                problems.add("server did not stop within 120 s after 'stop'");
                server.destroyForcibly();
            }
        } else if (server.isAlive()) {
            problems.add("server not ready within " + timeoutSeconds + " s");
            server.destroyForcibly();
        } else {
            problems.add("server exited (code " + server.exitValue() + ") before it was ready");
        }
        server.waitFor(30, TimeUnit.SECONDS);
        reader.join(5000);

        List<String> lines;
        synchronized (output) {
            lines = new ArrayList<>(output);
        }
        Path logDir = Files.createDirectories(serverDir.resolve("smoke-logs"));
        Files.write(logDir.resolve(profile.name() + ".log"), lines);

        analyse(profile, lines, serverDir, problems);
        if (!problems.isEmpty()) {
            // Keep the evidence: the next run of this profile overwrites <name>.log and clears crash-reports.
            String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Files.write(logDir.resolve(profile.name() + "-FAILED-" + stamp + ".log"), lines);
            Path crashes = serverDir.resolve("crash-reports");
            if (Files.isDirectory(crashes)) {
                try (Stream<Path> files = Files.list(crashes)) {
                    for (Path f : files.toList()) {
                        Files.copy(f, logDir.resolve(profile.name() + "-FAILED-" + stamp + "-" + f.getFileName()));
                    }
                }
            }
        }
        return problems;
    }

    private static void analyse(Profile profile, List<String> lines, Path serverDir, List<String> problems) throws IOException {
        Path crashes = serverDir.resolve("crash-reports");
        if (Files.isDirectory(crashes)) {
            try (Stream<Path> files = Files.list(crashes)) {
                files.forEach(f -> problems.add("crash report: " + f.getFileName()));
            }
        }
        // A class-loading / mixin error, judged by whether CIO shows up within the next lines.
        for (int i = 0; i < lines.size(); i++) {
            // Mixin's own WARN when an optional (required=false) config's target mod is absent: expected.
            if (BENIGN.matcher(lines.get(i)).find()) {
                continue;
            }
            if (LOAD_ERROR.matcher(lines.get(i)).find()) {
                int end = Math.min(lines.size(), i + 25);
                boolean cio = false;
                for (int j = i; j < end && !cio; j++) {
                    cio = lines.get(j).contains("createinteroperable") || lines.get(j).contains("com.cio.");
                }
                if (cio) {
                    problems.add("CIO-related error: " + lines.get(i).strip());
                    i = end;
                }
            }
        }
        String enabledLine = lines.stream().filter(l -> l.contains(ENABLED_PREFIX)).findFirst().orElse(null);
        if (enabledLine == null) {
            problems.add("no '" + ENABLED_PREFIX.strip() + "' line (did CIO construct?)");
            return;
        }
        String list = enabledLine.substring(enabledLine.indexOf(ENABLED_PREFIX) + ENABLED_PREFIX.length()).strip();
        Set<String> actual = new TreeSet<>();
        if (!list.equals("none")) {
            for (String m : list.split(",")) {
                actual.add(m.strip());
            }
        }
        System.out.println("CIO modules: " + (actual.isEmpty() ? "none" : String.join(", ", actual)));
        if (!actual.equals(profile.expectedModules())) {
            problems.add("modules enabled " + actual + ", expected " + profile.expectedModules());
        }
    }

    // --- helpers -------------------------------------------------------------

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("expected --key=value, got " + arg);
            }
            opts.put(arg.substring(2, arg.indexOf('=')), arg.substring(arg.indexOf('=') + 1));
        }
        for (String key : List.of("server-dir", "mods-dir", "cio-jar", "neoforge-version", "profiles")) {
            if (!opts.containsKey(key)) {
                throw new IllegalArgumentException("missing --" + key);
            }
        }
        return opts;
    }

    private SmokeRunner() {
    }
}
