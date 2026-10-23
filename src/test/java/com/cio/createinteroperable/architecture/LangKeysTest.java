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
            "telephone.incoming_call_with", "telephone.in_call_with", "telephone.calling_with");

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
