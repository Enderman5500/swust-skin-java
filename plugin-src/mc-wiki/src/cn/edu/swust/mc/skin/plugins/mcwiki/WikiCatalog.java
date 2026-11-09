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
        Map<String, Map<String, Object>> itemsById = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            itemsById.put(String.valueOf(item.get("namespacedId")), item);
        }
        List<Map<String, Object>> recipes = new ArrayList<>(store.recipes(version).stream()
                .map(recipe -> ensureIngredients(displayableSpecialRecipe(recipe, itemsById))).toList());
        addMapRecipes(recipes, itemsById);
        addMissingDyeRecipes(recipes, itemsById);
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


    /** 地图复制与扩展没有配方 JSON，按原版规则补充展示条目。 */
    private static void addMapRecipes(List<Map<String, Object>> recipes, Map<String, Map<String, Object>> items) {
        if (!items.containsKey("minecraft:filled_map")) return;
        if (recipes.stream().noneMatch(recipe -> "map_cloning".equals(String.valueOf(recipe.get("recipeId"))))) {
            recipes.add(mapCloningRecipe());
        }
        if (recipes.stream().noneMatch(recipe -> "map_extending".equals(String.valueOf(recipe.get("recipeId"))))) {
            recipes.add(mapExtendingRecipe());
        }
    }

    /** 旧版本导入的锻造成分可能只在九宫格中，补出原料列表供“原料 N 项”和使用关系查询。 */
    private static Map<String, Object> ensureIngredients(Map<String, Object> recipe) {
        Object existing = recipe.get("ingredients");
        if (existing instanceof List<?> list && !list.isEmpty()) return recipe;
        Object rawGrid = recipe.get("grid");
        if (!(rawGrid instanceof List<?> grid)) return recipe;
        List<String> tags = new ArrayList<>();
        Object rawTags = recipe.get("gridTags");
        if (rawTags instanceof List<?> list) {
            for (Object value : list) tags.add(value == null ? null : String.valueOf(value));
        }
        List<String> ingredients = new ArrayList<>();
        for (int slot = 0; slot < grid.size(); slot++) {
            Object value = grid.get(slot);
            if (value == null || String.valueOf(value).isBlank()) continue;
            String ingredient = slot < tags.size() && tags.get(slot) != null
                    ? "#" + tags.get(slot) : String.valueOf(value);
            if (!ingredients.contains(ingredient)) ingredients.add(ingredient);
        }
        if (ingredients.isEmpty()) return recipe;
        Map<String, Object> copy = new LinkedHashMap<>(recipe);
        copy.put("ingredients", ingredients);
        return copy;
    }

    /** 旧版本的特殊染色配方没有对应 JSON，按物品是否存在补齐。 */
    private static void addMissingDyeRecipes(List<Map<String, Object>> recipes,
            Map<String, Map<String, Object>> items) {
        for (String target : List.of("minecraft:leather_helmet", "minecraft:leather_chestplate",
                "minecraft:leather_leggings", "minecraft:leather_boots",
                "minecraft:leather_horse_armor", "minecraft:wolf_armor")) {
            if (!items.containsKey(target)) continue;
            boolean present = recipes.stream().anyMatch(recipe -> target.equals(recipe.get("resultId"))
                    && ("minecraft:crafting_dye".equals(recipe.get("type"))
                        || "minecraft:crafting_special_armordye".equals(recipe.get("type"))));
            if (present) continue;
            Map<String, Object> dyed = specialRecipe(target.substring(target.indexOf(':') + 1) + "_dyed",
                    "minecraft:crafting_dye", target, 1,
                    List.of(target, "#minecraft:dyes"), List.of(target, "minecraft:white_dye"));
            setTag(dyed, 1, "minecraft:dyes");
            recipes.add(dyed);
        }
        if (items.containsKey("minecraft:firework_star")) {
            boolean star = recipes.stream().anyMatch(recipe -> "minecraft:firework_star".equals(recipe.get("resultId"))
                    && "minecraft:crafting_special_firework_star".equals(recipe.get("type")));
            if (!star) {
                Map<String, Object> recipe = specialRecipe("firework_star", "minecraft:crafting_special_firework_star",
                        "minecraft:firework_star", 1,
                        List.of("minecraft:gunpowder", "#minecraft:dyes"),
                        List.of("minecraft:gunpowder", "minecraft:white_dye"));
                setTag(recipe, 1, "minecraft:dyes");
                recipes.add(recipe);
            }
            boolean fade = recipes.stream().anyMatch(recipe -> "minecraft:firework_star".equals(recipe.get("resultId"))
                    && "minecraft:crafting_special_firework_star_fade".equals(recipe.get("type")));
            if (!fade) {
                Map<String, Object> recipe = specialRecipe("firework_star_fade", "minecraft:crafting_special_firework_star_fade",
                        "minecraft:firework_star", 1,
                        List.of("minecraft:firework_star", "#minecraft:dyes"),
                        List.of("minecraft:firework_star", "minecraft:white_dye"));
                setTag(recipe, 1, "minecraft:dyes");
                recipes.add(recipe);
            }
        }
    }

    private static void setTag(Map<String, Object> recipe, int slot, String tag) {
        List<String> tags = new ArrayList<>();
        for (int index = 0; index < 9; index++) tags.add(null);
        tags.set(slot, tag);
        recipe.put("gridTags", tags);
    }

    private static Map<String, Object> mapCloningRecipe() {
        return specialRecipe("map_cloning", "minecraft:crafting_special_mapcloning",
                "minecraft:filled_map", 2, List.of("minecraft:filled_map", "minecraft:map"),
                List.of("minecraft:filled_map", "minecraft:map"));
    }

    private static Map<String, Object> mapExtendingRecipe() {
        return specialRecipe("map_extending", "minecraft:crafting_special_mapextending",
                "minecraft:filled_map", 1, List.of("minecraft:filled_map", "minecraft:paper"),
                List.of("minecraft:paper", "minecraft:paper", "minecraft:paper",
                        "minecraft:paper", "minecraft:filled_map", "minecraft:paper",
                        "minecraft:paper", "minecraft:paper", "minecraft:paper"));
    }

    private static final List<String> DYE_COLORS = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    /** 旧版本导入时未解析 input/material，按结果色的染料配方补全。 */
    private static Map<String, Object> dyedContainerRecipe(Map<String, Object> recipe, String type,
            Map<String, Map<String, Object>> items) {
        if (!"minecraft:crafting_transmute".equals(type)) return null;
        String resultId = String.valueOf(recipe.getOrDefault("resultId", ""));
        String path = resultId.contains(":") ? resultId.substring(resultId.indexOf(':') + 1) : resultId;
        boolean shulker = false;
        String color = null;
        for (String candidate : DYE_COLORS) {
            if (path.equals(candidate + "_bundle")) color = candidate;
            if (path.equals(candidate + "_shulker_box")) {
                color = candidate;
                shulker = true;
            }
            if (color != null) break;
        }
        if (color == null) return null;
        String tag = shulker ? "minecraft:shulker_boxes" : "minecraft:bundles";
        String base = shulker ? "minecraft:shulker_box" : "minecraft:bundle";
        if (!items.containsKey(base)) base = resultId;
        String dye = "minecraft:" + color + "_dye";
        Map<String, Object> normalized = specialRecipe(String.valueOf(recipe.getOrDefault("recipeId", "")),
                type, resultId, resultCount(recipe), List.of(tag, dye), List.of(base, dye));
        List<String> tags = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) tags.add(null);
        tags.set(0, tag);
        normalized.put("gridTags", tags);
        return normalized;
    }

    private static Map<String, Object> bookCloningRecipe(Map<String, Object> recipe) {
        Map<String, Object> normalized = specialRecipe(String.valueOf(recipe.getOrDefault("recipeId", "book_cloning")),
                "minecraft:crafting_special_bookcloning", "minecraft:written_book", resultCount(recipe),
                List.of("minecraft:written_book", "#minecraft:book_cloning_target"),
                List.of("minecraft:written_book", "minecraft:writable_book"));
        List<String> tags = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) tags.add(null);
        tags.set(1, "minecraft:book_cloning_target");
        normalized.put("gridTags", tags);
        return normalized;
    }

    private static Map<String, Object> tippedArrowRecipe(Map<String, Object> recipe) {
        return specialRecipe(String.valueOf(recipe.getOrDefault("recipeId", "tipped_arrow")),
                "minecraft:crafting_imbue", "minecraft:tipped_arrow", resultCount(recipe),
                List.of("minecraft:lingering_potion", "minecraft:arrow"),
                List.of("minecraft:lingering_potion", "minecraft:arrow"));
    }

    private static int resultCount(Map<String, Object> recipe) {
        Object value = recipe.get("resultCount");
        return value instanceof Number number ? Math.max(1, number.intValue()) : 1;
    }

    private static Map<String, Object> specialRecipe(String id, String type, String resultId, int count,
            List<String> ingredients, List<String> cells) {
        List<String> grid = new ArrayList<>(cells);
        while (grid.size() < 9) grid.add(null);
        List<String> tags = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) tags.add(null);
        Map<String, Object> recipe = new LinkedHashMap<>();
        recipe.put("recipeId", id);
        recipe.put("type", type);
        recipe.put("resultId", resultId);
        recipe.put("resultCount", count);
        recipe.put("ingredients", ingredients);
        recipe.put("grid", grid);
        recipe.put("gridTags", tags);
        return recipe;
    }

    /** 原版特殊合成没有配方 JSON 内容，为百科展示补一组代表性材料。 */
    private static Map<String, Object> displayableSpecialRecipe(Map<String, Object> recipe,
            Map<String, Map<String, Object>> items) {
        String type = String.valueOf(recipe.getOrDefault("type", ""));
        String recipeId = String.valueOf(recipe.getOrDefault("recipeId", ""));
        if ("map_cloning".equals(recipeId) || "minecraft:crafting_special_mapcloning".equals(type)) {
            return mapCloningRecipe();
        }
        if ("map_extending".equals(recipeId) || "minecraft:crafting_special_mapextending".equals(type)) {
            return mapExtendingRecipe();
        }
        Map<String, Object> dyedContainer = dyedContainerRecipe(recipe, type, items);
        if (dyedContainer != null) return dyedContainer;
        if ("minecraft:crafting_special_bookcloning".equals(type)) {
            return bookCloningRecipe(recipe);
        }
        if ("minecraft:crafting_imbue".equals(type)) {
            return tippedArrowRecipe(recipe);
        }
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
