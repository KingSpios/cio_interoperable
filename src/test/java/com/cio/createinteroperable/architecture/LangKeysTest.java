package com.cio.createinteroperable.architecture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every {@code Lang.component("key")} / {@code Lang.translate("key")} in the
 * source must exist in {@code en_us.json} (as {@code createinteroperable.key}),
 * so a typo shows as a failing build instead of a raw key in game. The first
 * argument is read up to its top-level comma, so a ternary of two keys is
 * checked too. Keys a call builds (rather than spells out) are listed in
 * {@link #BUILT_KEYS}.
 */
class LangKeysTest {
    /** Spelled-out keys that code extends with a suffix before use. */
    private static final Set<String> BUILT_KEYS = Set.of(
            "telephone.incoming_call_with", "telephone.in_call_with", "telephone.calling_with",
            "thermal.heat_level.freezing", "thermal.heat_level.cold", "thermal.heat_level.warm",
            "thermal.heat_level.hot", "thermal.heat_level.blazing");

    private static final Pattern CALL = Pattern.compile("Lang\\.(?:component|translate)\\(");
    private static final Pattern KEY = Pattern.compile("\"([a-z][a-z0-9_.]*)\"");

    @Test
    void everyLangKeyUsedInCodeExists() throws IOException {
        Path resources = Path.of(System.getProperty("cio.resources"));
        JsonObject lang = JsonParser.parseString(Files.readString(
                resources.resolve("assets/createinteroperable/lang/en_us.json"))).getAsJsonObject();
        Path java = resources.resolveSibling("java");

        TreeMap<String, String> used = new TreeMap<>();
        try (Stream<Path> files = Files.walk(java)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                Matcher call = CALL.matcher(text);
                while (call.find()) {
                    String arg = firstArgument(text, call.end());
                    Matcher key = KEY.matcher(arg);
                    while (key.find()) {
                        if (key.group(1).endsWith(".")) {
                            continue; // a prefix the code completes (see BUILT_KEYS)
                        }
                        used.putIfAbsent(key.group(1), file.getFileName().toString());
                    }
                }
            }
        }
        Set<String> missing = new TreeSet<>();
        used.forEach((key, file) -> {
            if (!lang.has("createinteroperable." + key)) {
                missing.add(key + "  (" + file + ")");
            }
        });
        for (String built : BUILT_KEYS) {
            if (!lang.has("createinteroperable." + built)) {
                missing.add(built + "  (built key)");
            }
        }
        if (used.size() < 50) {
            fail("expected to find the Lang calls in the source, found only " + used.size());
        }
        if (!missing.isEmpty()) {
            fail(missing.size() + " lang key(s) used in code but missing from en_us.json:\n  " + String.join("\n  ", missing));
        }
    }

    /**
     * Translations may lag behind English (Minecraft falls back to en_us), but
     * must not invent keys, and a translated value must carry the same
     * %s / %1$s / %% placeholders as the English one or the line would
     * show wrong or throw when formatted.
     */
    @Test
    void translationsMatchEnglish() throws IOException {
        Path langDir = Path.of(System.getProperty("cio.resources")).resolve("assets/createinteroperable/lang");
        JsonObject english = JsonParser.parseString(Files.readString(langDir.resolve("en_us.json"))).getAsJsonObject();
        Pattern placeholder = Pattern.compile("%(?:\\d+\\$)?s|%%");
        Set<String> problems = new TreeSet<>();
        int languages = 0;
        try (Stream<Path> files = Files.list(langDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json") && !p.endsWith("en_us.json")).toList()) {
                languages++;
                JsonObject other = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                for (String key : other.keySet()) {
                    if (!english.has(key)) {
                        problems.add(file.getFileName() + ": unknown key " + key);
                        continue;
                    }
                    if (!placeholders(placeholder, english.get(key).getAsString())
                            .equals(placeholders(placeholder, other.get(key).getAsString()))) {
                        problems.add(file.getFileName() + ": placeholders differ in " + key);
                    }
                }
            }
        }
        if (languages == 0) {
            fail("expected translation files next to en_us.json");
        }
        if (!problems.isEmpty()) {
            fail(problems.size() + " translation problem(s):\n  " + String.join("\n  ", problems));
        }
    }

    private static java.util.List<String> placeholders(Pattern placeholder, String value) {
        java.util.List<String> found = new java.util.ArrayList<>();
        Matcher m = placeholder.matcher(value);
        while (m.find()) {
            found.add(m.group());
        }
        java.util.Collections.sort(found);
        return found;
    }

    /** Every config option and section names a translation key (CIOConfig) that the lang file must carry, plus its tooltip. */
    @Test
    void everyConfigTranslationKeyExists() throws IOException {
        Path resources = Path.of(System.getProperty("cio.resources"));
        JsonObject lang = JsonParser.parseString(Files.readString(
                resources.resolve("assets/createinteroperable/lang/en_us.json"))).getAsJsonObject();
        String source = Files.readString(resources.resolveSibling("java/com/cio/createinteroperable/CIOConfig.java"));
        Matcher translation = Pattern.compile("\\.translation\\(\"([^\"]+)\"\\)").matcher(source);
        Set<String> missing = new TreeSet<>();
        int found = 0;
        while (translation.find()) {
            found++;
            String key = translation.group(1);
            if (!lang.has(key)) {
                missing.add(key);
            }
        }
        if (found < 20) {
            fail("expected the config translation keys in CIOConfig, found " + found);
        }
        if (!missing.isEmpty()) {
            fail(missing.size() + " config translation key(s) missing from en_us.json:\n  " + String.join("\n  ", missing));
        }
    }

    /** Source text of a call's first argument: up to the first top-level comma or closing parenthesis. */
    private static String firstArgument(String text, int start) {
        int depth = 0;
        boolean inString = false;
        int end = Math.min(text.length(), start + 500);
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (depth == 0) {
                    return text.substring(start, i);
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                return text.substring(start, i);
            }
        }
        return text.substring(start, end);
    }
}
