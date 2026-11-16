package dev.atos1k.auc.util;

import dev.by1337.yaml.YamlMap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;

final class MessagesFile {
    static final String LANG_KEY = "$lang";

    private static final String PREFIX_SECTION = "prefix";
    private static final Set<String> REMOVED = Set.of("general.error-format");
    private static final Pattern LEGACY_PLACEHOLDER = Pattern.compile("%([A-Za-z0-9_-]+)%");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_-]+)}");
    private static final Pattern KEY_LINE = Pattern.compile("^( *)(\"[^\"]+\"|'[^']+'|[^\\s#'\"-][^:]*):(?:\\s.*)?$");
    private static final Pattern PLAIN_KEY = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$-]*");
    private static final Set<String> RESERVED_KEYS = Set.of("yes", "no", "on", "off", "true", "false", "null", "y", "n");

    private enum Format { NESTED, FLAT, LEGACY }

    private MessagesFile() {
    }

    static List<YamlMap> documents(String text) {
        List<String> lines = lines(text);
        List<YamlMap> out = new ArrayList<>();
        for (int[] range : ranges(lines)) {
            out.add(parse(lines, range));
        }
        return out;
    }

    static Map<String, Object> flatten(YamlMap document) {
        Map<String, Object> out = leaves(document);
        out.remove(LANG_KEY);
        return out;
    }

    static Map<String, Object> resolve(Map<String, Object> flat) {
        Map<String, String> prefixes = prefixes(flat);
        YamlMap resolved = new YamlMap(new LinkedHashMap<>(flat)).deepCopy();
        resolved.deepReplace(text -> {
            if (!text.contains("{" + PREFIX_SECTION + "_")) return text;
            for (Map.Entry<String, String> prefix : prefixes.entrySet()) {
                text = text.replace(prefix.getKey(), prefix.getValue());
            }
            return text;
        });
        return resolved.getRaw();
    }

    static void sync(File live, String defaults, Logger log) throws IOException {
        String text = Files.readString(live.toPath(), StandardCharsets.UTF_8);
        List<String> lines = lines(text);
        List<int[]> ranges = ranges(lines);
        if (ranges.isEmpty()) {
            Files.writeString(live.toPath(), defaults, StandardCharsets.UTF_8);
            return;
        }
        YamlMap current;
        try {
            current = parse(lines, ranges.get(0));
        } catch (RuntimeException e) {
            log.error("messages.yml не читается, пропускаю дополнение: {}", e.getMessage());
            return;
        }
        Template template = Template.of(defaults);
        Format format = format(current, template);
        String result;
        if (format == Format.NESTED) {
            String shared = sharePrefixes(text, template);
            result = merge(shared, template);
            if (result == null) {
                log.warn("Не удалось дописать новые сообщения в messages.yml, для них используются тексты по умолчанию");
                result = shared;
            }
        } else {
            result = migrate(live, text, current, template, format, log);
        }
        if (result != null && !result.equals(text)) {
            Files.writeString(live.toPath(), result, StandardCharsets.UTF_8);
        }
    }

    private static Format format(YamlMap document, Template template) {
        for (Map.Entry<String, Object> entry : document.getRaw().entrySet()) {
            String key = entry.getKey();
            if (!(entry.getValue() instanceof Map) && key.indexOf('_') >= 0
                    && template.values().containsKey(key.replace('_', '.'))) {
                return Format.FLAT;
            }
        }
        if (document.has(LANG_KEY)) return Format.NESTED;
        Set<String> names = new HashSet<>();
        for (Object value : template.values().values()) {
            strings(value, text -> {
                Matcher matcher = PLACEHOLDER.matcher(text);
                while (matcher.find()) names.add(matcher.group(1));
            });
        }
        boolean[] legacy = {false};
        for (Object value : leaves(document).values()) {
            strings(value, text -> {
                Matcher matcher = LEGACY_PLACEHOLDER.matcher(text);
                while (matcher.find()) legacy[0] |= names.contains(matcher.group(1));
            });
        }
        return legacy[0] ? Format.LEGACY : Format.NESTED;
    }

    private static String merge(String text, Template template) {
        List<String> lines = lines(text);
        List<int[]> ranges = ranges(lines);
        if (ranges.isEmpty()) return null;
        int[] range = ranges.get(0);
        Map<String, Boolean> have = structure(parse(lines, range));
        List<Node> missing = new ArrayList<>();
        for (Node node : template.outline().nodes()) {
            if (node.path.equals(LANG_KEY) || have.containsKey(node.path)) continue;
            String parent = parent(node.path);
            if (parent == null || Boolean.TRUE.equals(have.get(parent))) missing.add(node);
        }
        if (missing.isEmpty()) return text;

        Outline outline = outline(lines, range, have);
        int tail = range[0];
        for (int i = range[0]; i < range[1]; i++) {
            if (!lines.get(i).isBlank()) tail = i + 1;
        }
        Map<Integer, List<Insert>> inserts = new TreeMap<>(Comparator.reverseOrder());
        for (Node node : missing) {
            List<String> block = template.lines().subList(node.start, node.end);
            String parent = parent(node.path);
            if (parent == null) {
                List<String> top = new ArrayList<>(block.size() + 1);
                top.add("");
                top.addAll(block);
                inserts.computeIfAbsent(tail, at -> new ArrayList<>()).add(new Insert(0, top));
                continue;
            }
            Node section = outline.byPath().get(parent);
            if (section == null) return null;
            int indent = section.childIndent >= 0 ? section.childIndent : section.indent + 2;
            inserts.computeIfAbsent(section.end, at -> new ArrayList<>())
                    .add(new Insert(parent.split("\\.").length, reindent(block, node.indent, indent)));
        }
        List<String> out = new ArrayList<>(lines);
        inserts.forEach((at, group) -> {
            List<String> block = new ArrayList<>();
            group.stream().sorted(Comparator.comparingInt(Insert::depth).reversed())
                    .forEach(insert -> block.addAll(insert.lines()));
            out.addAll(at, block);
        });
        String result = join(out, text);
        return verified(text, result, template, missing) ? result : null;
    }

    private static String sharePrefixes(String text, Template template) {
        Map<String, String> prefixes = prefixes(template.values());
        Node section = template.outline().byPath().get(PREFIX_SECTION);
        List<String> lines = lines(text);
        int[] range = ranges(lines).get(0);
        YamlMap before = parse(lines, range);
        if (prefixes.isEmpty() || section == null || before.getRaw().containsKey(PREFIX_SECTION)) return text;

        List<String> out = new ArrayList<>(lines);
        for (int i = range[0]; i < range[1]; i++) {
            String line = out.get(i);
            for (Map.Entry<String, String> prefix : prefixes.entrySet()) {
                line = line.replace("\"" + prefix.getValue(), "\"" + prefix.getKey())
                        .replace("'" + prefix.getValue(), "'" + prefix.getKey());
            }
            out.set(i, line);
        }
        List<Node> nodes = outline(lines, range, structure(before)).nodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (REMOVED.contains(node.path) && !node.section) out.subList(node.start, node.end).clear();
        }
        int at = range[1];
        for (Node node : nodes) {
            if (node.path.indexOf('.') < 0 && !node.path.equals(LANG_KEY)) {
                at = node.start;
                break;
            }
        }
        while (at > range[0] && out.get(at - 1).strip().startsWith("#")) {
            at--;
        }
        List<String> block = new ArrayList<>(template.lines().subList(section.start, section.end));
        block.add("");
        out.addAll(at, block);
        String result = join(out, text);
        try {
            List<YamlMap> was = documents(text);
            List<YamlMap> now = documents(result);
            Map<String, Object> values = resolve(leaves(now.get(0)));
            values.keySet().removeIf(key -> key.startsWith(PREFIX_SECTION + "."));
            Map<String, Object> expected = leaves(before);
            expected.keySet().removeAll(REMOVED);
            boolean same = values.equals(expected) && was.subList(1, was.size()).equals(now.subList(1, now.size()));
            return same ? result : text;
        } catch (RuntimeException e) {
            return text;
        }
    }

    private static boolean verified(String before, String after, Template template, List<Node> added) {
        List<YamlMap> was;
        List<YamlMap> now;
        try {
            was = documents(before);
            now = documents(after);
        } catch (RuntimeException e) {
            return false;
        }
        if (was.size() != now.size() || !was.subList(1, was.size()).equals(now.subList(1, now.size()))) return false;
        Map<String, Object> values = leaves(now.get(0));
        for (Map.Entry<String, Object> entry : leaves(was.get(0)).entrySet()) {
            if (!entry.getValue().equals(values.get(entry.getKey()))) return false;
        }
        for (Map.Entry<String, Object> entry : template.values().entrySet()) {
            for (Node node : added) {
                boolean inside = entry.getKey().equals(node.path) || entry.getKey().startsWith(node.path + ".");
                if (inside && !entry.getValue().equals(values.get(entry.getKey()))) return false;
            }
        }
        return true;
    }

    private static String migrate(File live, String text, YamlMap document, Template template, Format format, Logger log)
            throws IOException {
        Map<String, Object> old = new LinkedHashMap<>();
        if (format == Format.FLAT) {
            document.getRaw().forEach((key, value) -> {
                String path = key.replace('_', '.');
                old.put(template.values().containsKey(path) ? path : key, value instanceof Map ? List.of(value) : value);
            });
        } else {
            leaves(document).forEach((path, value) -> {
                String last = path.substring(path.lastIndexOf('.') + 1);
                Object converted = last.equals("true") || last.equals("false") ? null : convert(value);
                if (converted != null) old.put(path, converted);
            });
        }
        old.keySet().removeIf(key -> REMOVED.contains(key.replace('_', '.')));
        Map<String, String> prefixes = prefixes(template.values());
        YamlMap shared = new YamlMap(new LinkedHashMap<>(old)).deepCopy();
        shared.deepReplace(line -> {
            for (Map.Entry<String, String> prefix : prefixes.entrySet()) {
                if (line.startsWith(prefix.getValue())) return prefix.getKey() + line.substring(prefix.getValue().length());
            }
            return line;
        });
        Map<String, Object> values = shared.getRaw();

        List<String> lines = template.lines();
        Map<Integer, Node> starts = new HashMap<>();
        for (Node node : template.outline().nodes()) {
            if (!node.section) starts.put(node.start, node);
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); ) {
            Node node = starts.get(i);
            Object value = node == null ? null : values.get(node.path);
            if (value == null || value.equals(template.values().get(node.path))) {
                out.add(lines.get(i++));
                continue;
            }
            out.addAll(override(lines, node, value, format == Format.LEGACY));
            i = node.end;
        }
        String result = String.join("\n", out) + "\n";

        Map<String, Object> extra = new LinkedHashMap<>(values);
        extra.keySet().removeAll(template.values().keySet());
        if (!extra.isEmpty()) {
            StringBuilder custom = new StringBuilder();
            tree(extra).forEach((key, value) -> emit("", key, value).forEach(line -> custom.append(line).append('\n')));
            try {
                String merged = merge(result, Template.of(custom.toString()));
                if (merged != null) result = merged;
            } catch (RuntimeException ignored) {
            }
        }

        Map<String, Object> migrated;
        try {
            migrated = leaves(documents(result).get(0));
        } catch (RuntimeException e) {
            log.error("Не удалось перенести messages.yml в формат с секциями: {}", e.getMessage());
            return null;
        }
        String name = format == Format.FLAT ? "messages.yml.flat.old" : "messages.yml.old";
        File backup = new File(live.getParentFile(), name);
        if (!backup.exists()) {
            Files.writeString(backup.toPath(), text, StandardCharsets.UTF_8);
        }
        extra.keySet().removeAll(migrated.keySet());
        if (!extra.isEmpty()) {
            log.warn("Сообщения {} не перенесены в новый messages.yml, они остались в {}", extra.keySet(), name);
        }
        log.info("messages.yml переведён в формат с секциями, старый файл сохранён как {}", name);
        return result;
    }

    private static List<String> override(List<String> lines, Node node, Object value, boolean keepActions) {
        if (keepActions && value instanceof String text && node.end - node.start > 1) {
            String first = lines.get(node.start + 1);
            if (first.strip().startsWith("- \"")) {
                List<String> out = new ArrayList<>(lines.subList(node.start, node.end));
                out.set(1, " ".repeat(indent(first)) + "- " + quote(text));
                return out;
            }
        }
        return emit(" ".repeat(node.indent), node.path.substring(node.path.lastIndexOf('.') + 1), value);
    }

    private static List<String> emit(String indent, String key, Object value) {
        List<String> out = new ArrayList<>();
        boolean plain = PLAIN_KEY.matcher(key).matches() && !RESERVED_KEYS.contains(key.toLowerCase());
        String name = plain ? key : quote(key);
        if (value instanceof Map<?, ?> section) {
            out.add(indent + name + ":");
            section.forEach((child, inner) -> out.addAll(emit(indent + "  ", String.valueOf(child), inner)));
        } else if (value instanceof List<?> list && list.stream().allMatch(MessagesFile::simple)) {
            out.add(indent + name + ":");
            for (Object item : list) {
                if (item instanceof Map<?, ?> action) {
                    String lead = indent + "  - ";
                    for (Map.Entry<?, ?> entry : action.entrySet()) {
                        out.add(lead + entry.getKey() + ": " + scalar(entry.getValue()));
                        lead = indent + "    ";
                    }
                } else {
                    out.add(indent + "  - " + scalar(item));
                }
            }
        } else if (value instanceof List<?>) {
            YamlMap holder = new YamlMap();
            holder.getRaw().put(key, value);
            for (String line : lines(holder.saveToString())) {
                out.add(indent + line);
            }
        } else {
            out.add(indent + name + ": " + scalar(value));
        }
        return out;
    }

    private static boolean simple(Object item) {
        if (item instanceof Map<?, ?> map) {
            if (map.isEmpty()) return false;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                boolean plain = PLAIN_KEY.matcher(key).matches() && !RESERVED_KEYS.contains(key.toLowerCase());
                if (!plain || !primitive(entry.getValue())) return false;
            }
            return true;
        }
        return primitive(item);
    }

    private static boolean primitive(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private static String scalar(Object value) {
        return value instanceof String text ? quote(text) : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tree(Map<String, Object> flat) {
        Map<String, Object> root = new LinkedHashMap<>();
        flat.forEach((path, value) -> {
            if (path.equals(LANG_KEY)) return;
            String[] parts = path.split("\\.");
            Map<String, Object> at = root;
            for (int i = 0; i < parts.length - 1; i++) {
                Object next = at.computeIfAbsent(parts[i], key -> new LinkedHashMap<String, Object>());
                if (!(next instanceof Map)) return;
                at = (Map<String, Object>) next;
            }
            at.putIfAbsent(parts[parts.length - 1], value);
        });
        return root;
    }

    private static Object convert(Object value) {
        if (value instanceof String text) return placeholders(text);
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(placeholders(String.valueOf(item)));
            }
            return out;
        }
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        return null;
    }

    private static String placeholders(String text) {
        return LEGACY_PLACEHOLDER.matcher(text).replaceAll(match -> "{" + match.group(1) + "}");
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static void strings(Object value, java.util.function.Consumer<String> visitor) {
        if (value instanceof String text) {
            visitor.accept(text);
        } else if (value instanceof List<?> list) {
            list.forEach(item -> strings(item, visitor));
        } else if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> strings(item, visitor));
        }
    }

    private static Map<String, String> prefixes(Map<String, Object> flat) {
        Map<String, String> out = new LinkedHashMap<>();
        flat.forEach((path, value) -> {
            if (path.startsWith(PREFIX_SECTION + ".") && value instanceof String text && !text.isEmpty()) {
                out.put("{" + path.replace('.', '_') + "}", text);
            }
        });
        return out;
    }

    private static String join(List<String> lines, String original) {
        String result = String.join("\n", lines) + "\n";
        return original.contains("\r\n") ? result.replace("\n", "\r\n") : result;
    }

    private static Map<String, Object> leaves(YamlMap document) {
        Map<String, Object> out = new LinkedHashMap<>();
        walk("", document.getRaw(), (path, value) -> {
            if (!(value instanceof Map)) out.put(path, value);
        });
        return out;
    }

    private static Map<String, Boolean> structure(YamlMap document) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        walk("", document.getRaw(), (path, value) -> out.put(path, value instanceof Map));
        return out;
    }

    private static void walk(String prefix, Map<?, ?> map, BiConsumer<String, Object> visitor) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String path = prefix + entry.getKey();
            Object value = entry.getValue();
            if (value == null) continue;
            visitor.accept(path, value);
            if (value instanceof Map<?, ?> child) walk(path + ".", child, visitor);
        }
    }

    private static List<String> lines(String text) {
        List<String> out = new ArrayList<>(Arrays.asList(text.replace("\r\n", "\n").split("\n", -1)));
        if (out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    private static List<int[]> ranges(List<String> lines) {
        List<int[]> out = new ArrayList<>();
        int from = 0;
        for (int i = 0; i <= lines.size(); i++) {
            if (i < lines.size() && !lines.get(i).stripTrailing().equals("---")) continue;
            for (int j = from; j < i; j++) {
                String body = lines.get(j).strip();
                if (!body.isEmpty() && !body.startsWith("#")) {
                    out.add(new int[]{from, i});
                    break;
                }
            }
            from = i + 1;
        }
        return out;
    }

    private static YamlMap parse(List<String> lines, int[] range) {
        return YamlMap.loadFromString(String.join("\n", lines.subList(range[0], range[1])) + "\n");
    }

    private static Outline outline(List<String> lines, int[] range, Map<String, Boolean> known) {
        List<Node> nodes = new ArrayList<>();
        Map<String, Node> byPath = new HashMap<>();
        Deque<Node> stack = new ArrayDeque<>();
        for (int i = range[0]; i < range[1]; i++) {
            String line = lines.get(i);
            String body = line.strip();
            if (body.isEmpty() || body.startsWith("#")) continue;
            int indent = indent(line);
            while (!stack.isEmpty() && indent <= stack.peek().indent) {
                stack.pop();
            }
            Node parent = stack.peek();
            Matcher matcher = KEY_LINE.matcher(line);
            if (matcher.matches() && (parent == null || parent.section)) {
                String key = matcher.group(2).strip();
                if (key.length() > 1 && (key.charAt(0) == '"' || key.charAt(0) == '\'')) {
                    key = key.substring(1, key.length() - 1);
                }
                String path = parent == null ? key : parent.path + "." + key;
                Boolean section = known.get(path);
                if (section != null && !byPath.containsKey(path)) {
                    Node node = new Node(path, indent, i, section);
                    if (parent != null && parent.childIndent < 0) parent.childIndent = indent;
                    nodes.add(node);
                    byPath.put(path, node);
                    stack.push(node);
                }
            }
            for (Node node : stack) {
                node.end = i + 1;
            }
        }
        return new Outline(nodes, byPath);
    }

    private static List<String> reindent(List<String> block, int from, int to) {
        List<String> out = new ArrayList<>(block.size());
        for (String line : block) {
            out.add(line.isBlank() ? line : " ".repeat(to) + line.substring(Math.min(from, indent(line))));
        }
        return out;
    }

    private static int indent(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') indent++;
        return indent;
    }

    private static String parent(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? null : path.substring(0, dot);
    }

    private static final class Node {
        final String path;
        final int indent;
        final int start;
        final boolean section;
        int end;
        int childIndent = -1;

        Node(String path, int indent, int start, boolean section) {
            this.path = path;
            this.indent = indent;
            this.start = start;
            this.section = section;
            this.end = start + 1;
        }
    }

    private record Outline(List<Node> nodes, Map<String, Node> byPath) {
    }

    private record Insert(int depth, List<String> lines) {
    }

    private record Template(List<String> lines, Map<String, Object> values, Outline outline) {
        static Template of(String text) {
            List<String> lines = MessagesFile.lines(text);
            List<int[]> ranges = ranges(lines);
            if (ranges.isEmpty()) return new Template(lines, Map.of(), new Outline(List.of(), Map.of()));
            YamlMap document = parse(lines, ranges.get(0));
            Outline outline = MessagesFile.outline(lines, ranges.get(0), structure(document));
            return new Template(lines, leaves(document), outline);
        }
    }
}
