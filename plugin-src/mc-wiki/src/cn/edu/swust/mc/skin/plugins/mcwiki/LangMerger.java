package cn.edu.swust.mc.skin.plugins.mcwiki;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 合并中英文语言文件。同时支持 1.13+ 的 JSON 与 1.12.2 及更早的 .lang 文本；
 * 旧版键（tile./item./entity.X.name）折算为新版命名空间键，变体键归并到基础物品。
 */
final class LangMerger {
    record Name(String en, String zh) {
    }

    private final ObjectMapper mapper;

    LangMerger(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    Map<String, Name> merge(byte[] en, byte[] zh) {
        Map<String, String> english = modernize(read(en));
        Map<String, String> chinese = modernize(read(zh));
        Map<String, Name> result = new LinkedHashMap<>();
        english.forEach((key, value) -> result.put(key, new Name(value, chinese.get(key))));
        chinese.forEach((key, value) -> result.putIfAbsent(key, new Name(english.get(key), value)));
        return result;
    }

    private Map<String, String> read(byte[] bytes) {
        if (bytes == null) return Map.of();
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        if (text.stripLeading().startsWith("{")) return readJson(text);
        return readText(text);
    }

    private Map<String, String> readJson(String text) {
        try {
            JsonNode node = mapper.readTree(text);
            Map<String, String> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(e -> {
                if (e.getValue().isTextual()) result.put(e.getKey(), e.getValue().asText());
            });
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("语言文件解析失败", e);
        }
    }

    private Map<String, String> readText(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            String row = line.trim();
            if (row.isEmpty() || row.startsWith("#")) continue;
            int eq = row.indexOf('=');
            if (eq <= 0) continue;
            result.put(row.substring(0, eq).trim(), row.substring(eq + 1).trim());
        }
        return result;
    }

    private Map<String, String> modernize(Map<String, String> raw) {
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            String converted = convert(key, false);
            if (converted != null) result.put(converted, value);
        });
        raw.forEach((key, value) -> {
            String converted = convert(key, true);
            if (converted != null) result.putIfAbsent(converted, value);
        });
        return result;
    }

    private String convert(String key, boolean variantPass) {
        for (String prefix : new String[]{"tile.", "item.", "entity."}) {
            if (!key.startsWith(prefix) || !key.endsWith(".name")) continue;
            String middle = key.substring(prefix.length(), key.length() - ".name".length());
            if (middle.isBlank()) return null;
            String kind = "tile.".equals(prefix) ? "block" : prefix.substring(0, prefix.length() - 1);
            int dot = middle.indexOf('.');
            if (dot < 0) return variantPass ? null : kind + ".minecraft." + snake(middle);
            return variantPass ? kind + ".minecraft." + snake(middle.substring(0, dot)) : null;
        }
        return variantPass ? null : key;
    }

    private static String snake(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isUpperCase(c) && out.length() > 0) out.append('_');
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    static String namespaced(String key) {
        for (String prefix : new String[]{"item.minecraft.", "block.minecraft.", "entity.minecraft."}) {
            if (key.startsWith(prefix)) return "minecraft:" + key.substring(prefix.length());
        }
        return null;
    }

    /** namespacedId 反查语言键（names 的键为 kind.minecraft.path）。 */
    static String langKey(String id, String kind) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return kind + ".minecraft." + path;
    }
}
