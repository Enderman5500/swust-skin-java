package cn.edu.swust.mc.skin.plugins.mcwiki;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 按版本的目录索引：物品查找、模糊搜索与 resultId → 配方映射，首次访问时从文件构建。 */
final class WikiCatalog {
    record Index(String version, Map<String, Map<String, Object>> itemsById,
                 List<Map<String, Object>> items, List<Map<String, Object>> recipes,
                 Map<String, List<Map<String, Object>>> recipesByResult) {
    }

    private final WikiStore store;
    private final ConcurrentMap<String, Index> indexes = new ConcurrentHashMap<>();

    WikiCatalog(WikiStore store) {
        this.store = store;
    }

    Index get(String version) {
        return indexes.computeIfAbsent(version, this::build);
    }

    void invalidate(String version) {
        if (version != null && !version.isBlank()) indexes.remove(version);
    }

    private Index build(String version) {
        List<Map<String, Object>> items = List.copyOf(store.items(version));
        List<Map<String, Object>> recipes = List.copyOf(store.recipes(version));
        Map<String, Map<String, Object>> itemsById = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            itemsById.put(String.valueOf(item.get("namespacedId")), item);
        }
        Map<String, List<Map<String, Object>>> byResult = new LinkedHashMap<>();
        for (Map<String, Object> recipe : recipes) {
            Object result = recipe.get("resultId");
            if (result == null || String.valueOf(result).isBlank()) continue;
            byResult.computeIfAbsent(String.valueOf(result), key -> new ArrayList<>()).add(recipe);
        }
        Map<String, List<Map<String, Object>>> frozen = new LinkedHashMap<>();
        byResult.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        return new Index(version, Map.copyOf(itemsById), items, recipes, Map.copyOf(frozen));
    }

    List<Map<String, Object>> search(Index index, String keyword, String kind, int page, int size) {
        int capped = Math.min(Math.max(size, 1), 200);
        return filtered(index, keyword, kind).stream()
                .skip((long) (Math.max(page, 1) - 1) * capped).limit(capped).toList();
    }

    long count(Index index, String keyword, String kind) {
        return filtered(index, keyword, kind).size();
    }

    Map<String, Long> kindCounts(Index index) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> item : index.items()) {
            String kind = String.valueOf(item.getOrDefault("kind", "item"));
            counts.merge(kind, 1L, Long::sum);
        }
        return counts;
    }

    List<Map<String, Object>> filtered(Index index, String keyword, String kind) {
        List<Map<String, Object>> result = new ArrayList<>();
        String query = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        for (Map<String, Object> item : index.items()) {
            if (kind != null && !kind.isBlank() && !kind.equalsIgnoreCase(String.valueOf(item.get("kind")))) continue;
            if (query != null && !query.isBlank() && !matches(item, query)) continue;
            result.add(item);
        }
        return result;
    }

    private boolean matches(Map<String, Object> item, String query) {
        for (String field : List.of("namespacedId", "nameZh", "nameEn")) {
            Object value = item.get(field);
            if (value != null && String.valueOf(value).toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }
}
