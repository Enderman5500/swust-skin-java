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
        List<Map<String, Object>> recipes = store.recipes(version).stream()
                .map(WikiCatalog::displayableSpecialRecipe).toList();
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


    /** 原版特殊合成没有配方 JSON 内容，为百科展示补一组代表性材料。 */
    private static Map<String, Object> displayableSpecialRecipe(Map<String, Object> recipe) {
        String type = String.valueOf(recipe.getOrDefault("type", ""));
        if (!isBlankRecipe(recipe)) return recipe;
        List<String> ingredients;
        List<String> grid = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) tags.add(null);
        switch (type) {
            case "minecraft:crafting_special_firework_rocket" -> {
                ingredients = List.of("minecraft:paper", "minecraft:gunpowder", "minecraft:firework_star");
                grid.addAll(ingredients);
            }
            case "minecraft:crafting_special_firework_star" -> {
                ingredients = List.of("minecraft:gunpowder", "#minecraft:dyes");
                grid.add("minecraft:gunpowder");
                grid.add("minecraft:white_dye");
                tags.set(1, "minecraft:dyes");
            }
            case "minecraft:crafting_special_firework_star_fade" -> {
                ingredients = List.of("minecraft:firework_star", "#minecraft:dyes");
                grid.add("minecraft:firework_star");
                grid.add("minecraft:white_dye");
                tags.set(1, "minecraft:dyes");
            }
            case "minecraft:crafting_special_bannerduplicate" -> {
                String banner = String.valueOf(recipe.getOrDefault("resultId", "minecraft:white_banner"));
                ingredients = List.of(banner, banner);
                grid.add(banner);
                grid.add(banner);
            }
            case "minecraft:crafting_dye" -> {
                String target = String.valueOf(recipe.getOrDefault("resultId", "minecraft:leather_helmet"));
                ingredients = List.of(target, "#minecraft:dyes");
                grid.add(target);
                grid.add("minecraft:white_dye");
                tags.set(1, "minecraft:dyes");
            }
            case "minecraft:crafting_special_shielddecoration" -> {
                ingredients = List.of("minecraft:shield", "#minecraft:banners");
                grid.add("minecraft:shield");
                grid.add("minecraft:white_banner");
                tags.set(1, "minecraft:banners");
            }
            case "minecraft:crafting_decorated_pot" -> {
                ingredients = List.of("#minecraft:decorated_pot_ingredients");
                for (int slot = 0; slot < 4; slot++) {
                    grid.add("minecraft:brick");
                    tags.set(slot, "minecraft:decorated_pot_ingredients");
                }
            }
            default -> {
                return recipe;
            }
        }
        while (grid.size() < 9) grid.add(null);
        Map<String, Object> copy = new LinkedHashMap<>(recipe);
        copy.put("ingredients", ingredients);
        copy.put("grid", grid);
        copy.put("gridTags", tags);
        return copy;
    }

    private static boolean isBlankRecipe(Map<String, Object> recipe) {
        Object ingredients = recipe.get("ingredients");
        if (ingredients instanceof List<?> list && !list.isEmpty()) return false;
        Object grid = recipe.get("grid");
        if (grid instanceof List<?> list) {
            for (Object value : list) {
                if (value != null && !String.valueOf(value).isBlank()) return false;
            }
        }
        return true;
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
