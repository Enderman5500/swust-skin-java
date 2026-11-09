package cn.edu.swust.mc.skin.plugins.mcwiki;

import cn.edu.swust.mc.skin.Web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 公开百科与后台的查询/发布逻辑。公开接口只允许读取已发布版本。 */
final class WikiQuery {
    private final WikiStore store;
    private final WikiCatalog catalog;
    private final RenderAssets renders;
    private final MojangClient mojang;

    WikiQuery(WikiStore store, WikiCatalog catalog, RenderAssets renders, MojangClient mojang) {
        this.store = store;
        this.catalog = catalog;
        this.renders = renders;
        this.mojang = mojang;
    }

    // ---------- 发布 ----------

    List<String> publishedVersions() {
        Map<String, Object> publication = store.publication();
        Object versions = publication.get("versions");
        if (versions instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) result.add(String.valueOf(item));
            return result;
        }
        Object single = publication.get("version");
        return single == null ? List.of() : List.of(String.valueOf(single));
    }

    String currentVersion() {
        Object current = store.publication().get("version");
        return current == null ? null : String.valueOf(current);
    }

    void publish(String version) {
        List<String> published = new ArrayList<>(publishedVersions());
        published.remove(version);
        published.add(version);
        String current = currentVersion();
        savePublication(current != null && published.contains(current) ? current : version, published);
    }

    void unpublish(String version) {
        List<String> published = new ArrayList<>(publishedVersions());
        if (!published.remove(version)) return;
        if (published.isEmpty()) {
            store.writePublication(new LinkedHashMap<>());
            return;
        }
        String current = currentVersion();
        if (current == null || !published.contains(current)) current = published.getLast();
        savePublication(current, published);
    }

    /** 从已发布版本中指定公开页面默认展示的版本。 */
    void setDefaultVersion(String version) {
        List<String> published = publishedVersions();
        if (!published.contains(version)) throw Web.badRequest("请先发布该版本");
        if (version.equals(currentVersion())) return;
        savePublication(version, published);
    }

    private void savePublication(String current, List<String> published) {
        Map<String, Object> publication = new LinkedHashMap<>();
        publication.put("version", current);
        publication.put("versions", List.copyOf(published));
        publication.put("publishedAt", System.currentTimeMillis());
        store.writePublication(publication);
    }

    /** 请求版本为空/current 时取默认发布版本；指定版本必须已发布。 */
    private Optional<String> select(String requested) {
        List<String> published = publishedVersions();
        if (published.isEmpty()) return Optional.empty();
        if (requested == null || requested.isBlank() || "current".equals(requested)) {
            String current = currentVersion();
            return Optional.of(current != null && published.contains(current) ? current : published.getLast());
        }
        if (!published.contains(requested)) {
            throw Web.badRequest("只允许查询已发布版本：" + String.join("、", published));
        }
        return Optional.of(requested);
    }

    // ---------- 公开查询 ----------

    Map<String, Object> meta(String requested) {
        List<String> published = publishedVersions();
        String current = currentVersion();
        String selected = requested != null && published.contains(requested) ? requested
                : current != null && published.contains(current) ? current
                : published.isEmpty() ? null : published.getLast();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("published", !published.isEmpty());
        meta.put("version", current);
        meta.put("versions", sortedByRelease(published));
        if (selected == null) {
            meta.put("items", 0);
            meta.put("recipes", 0);
            meta.put("kinds", Map.of());
            return meta;
        }
        WikiCatalog.Index index = catalog.get(selected);
        java.util.Set<String> hidden = store.hiddenItems();
        long visibleItems = 0;
        Map<String, Long> kinds = new LinkedHashMap<>();
        for (Map<String, Object> item : index.items()) {
            if (hidden.contains(String.valueOf(item.get("namespacedId")))) continue;
            visibleItems++;
            String kind = String.valueOf(item.getOrDefault("kind", "item"));
            kinds.merge(kind, 1L, Long::sum);
        }
        meta.put("items", visibleItems);
        meta.put("recipes", index.recipes().size());
        meta.put("kinds", kinds);
        return meta;
    }

    /** 按版本清单顺序（新版本在前）排列发布版本，供前端版本选择器使用。 */
    private List<String> sortedByRelease(List<String> published) {
        Map<String, Integer> order = new LinkedHashMap<>();
        int index = 0;
        for (Map<String, Object> version : store.versions()) {
            order.put(String.valueOf(version.get("id")), index++);
        }
        List<String> rows = new ArrayList<>(published);
        rows.sort((a, b) -> Integer.compare(order.getOrDefault(a, Integer.MAX_VALUE),
                order.getOrDefault(b, Integer.MAX_VALUE)));
        return rows;
    }

    Map<String, Object> items(String version, String keyword, String kind, int page, int size) {
        Optional<String> selected = select(version);
        if (selected.isEmpty()) return page(List.of(), 0, page, size);
        WikiCatalog.Index index = catalog.get(selected.get());
        java.util.Set<String> hidden = store.hiddenItems();
        List<Map<String, Object>> filtered = catalog.filtered(index, keyword, kind).stream()
                .filter(item -> !hidden.contains(String.valueOf(item.get("namespacedId")))).toList();
        int capped = Math.min(Math.max(size, 1), 200);
        int from = Math.min((Math.max(page, 1) - 1) * capped, filtered.size());
        List<Map<String, Object>> rows = filtered.stream().skip(from).limit(capped)
                .map(item -> itemRow(selected.get(), item)).toList();
        return page(rows, filtered.size(), page, size);
    }

    Map<String, Object> recipes(String version, String keyword, int page, int size) {
        Optional<String> selected = select(version);
        if (selected.isEmpty()) return page(List.of(), 0, page, size);
        WikiCatalog.Index index = catalog.get(selected.get());
        String query = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> recipe : index.recipes()) {
            if (query == null || query.isBlank() || recipeMatches(index, recipe, query)) filtered.add(recipe);
        }
        int capped = Math.min(Math.max(size, 1), 200);
        int from = Math.min((Math.max(page, 1) - 1) * capped, filtered.size());
        List<Map<String, Object>> rows = filtered.stream().skip(from).limit(capped)
                .map(recipe -> enrichRecipe(index, recipe)).toList();
        return page(rows, filtered.size(), page, size);
    }

    Map<String, Object> itemDetail(String version, String id) {
        Optional<String> selected = select(version);
        if (selected.isEmpty()) throw Web.notFound("还没有发布任何百科版本");
        String v = selected.get();
        WikiCatalog.Index index = catalog.get(v);
        Map<String, Object> item = index.itemsById().get(id);
        if (item == null || store.hiddenItems().contains(id)) throw Web.notFound("物品不存在：" + id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("item", itemRow(v, item));
        detail.put("producing", index.recipesByResult().getOrDefault(id, List.of()).stream()
                .map(recipe -> enrichRecipe(index, recipe)).toList());
        List<Map<String, Object>> using = new ArrayList<>();
        for (Map<String, Object> recipe : index.recipes()) {
            if (uses(recipe, id)) using.add(enrichRecipe(index, recipe));
        }
        detail.put("using", using);
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        detail.put("blockstate", store.blockstates(v).get(path));
        return detail;
    }

    private boolean uses(Map<String, Object> recipe, String id) {
        Object ingredients = recipe.get("ingredients");
        if (!(ingredients instanceof List<?> list)) return false;
        for (Object value : list) {
            String text = String.valueOf(value);
            if (text.equals(id) || text.equals("#" + id)) return true;
        }
        return false;
    }

    private boolean recipeMatches(WikiCatalog.Index index, Map<String, Object> recipe, String query) {
        for (String field : List.of("recipeId", "resultId", "type")) {
            Object value = recipe.get(field);
            if (value != null && String.valueOf(value).toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        Map<String, Object> result = index.itemsById().get(String.valueOf(recipe.get("resultId")));
        if (result != null) {
            for (String field : List.of("nameZh", "nameEn")) {
                Object value = result.get(field);
                if (value != null && String.valueOf(value).toLowerCase(Locale.ROOT).contains(query)) return true;
            }
        }
        return false;
    }

    private Map<String, Object> itemRow(String version, Map<String, Object> item) {
        String id = String.valueOf(item.get("namespacedId"));
        String kind = String.valueOf(item.getOrDefault("kind", "item"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("version", version);
        row.put("namespacedId", id);
        row.put("kind", kind);
        row.put("nameEn", item.getOrDefault("nameEn", ""));
        row.put("nameZh", item.getOrDefault("nameZh", ""));
        row.put("icon", renders.iconPath(id, kind));
        return row;
    }

    /** 配方详情：结果物品名称/图标 + 九宫格逐格物品信息，前端不再二次查询。 */
    private Map<String, Object> enrichRecipe(WikiCatalog.Index index, Map<String, Object> recipe) {
        Map<String, Object> row = new LinkedHashMap<>(recipe);
        row.remove("rawJson");
        String resultId = String.valueOf(recipe.get("resultId"));
        Map<String, Object> result = index.itemsById().get(resultId);
        row.put("resultNameZh", result == null ? "" : result.getOrDefault("nameZh", ""));
        row.put("resultNameEn", result == null ? "" : result.getOrDefault("nameEn", ""));
        row.put("resultIcon", result == null ? null
                : renders.iconPath(resultId, String.valueOf(result.getOrDefault("kind", "item"))));
        Object grid = recipe.get("grid");
        Object gridTags = recipe.get("gridTags");
        List<String> tags = new ArrayList<>();
        if (gridTags instanceof List<?> list) {
            for (Object value : list) tags.add(value == null ? null : String.valueOf(value));
        }
        List<Object> cells = new ArrayList<>();
        if (grid instanceof List<?> list) {
            int position = 0;
            for (Object value : list) {
                if (value == null) {
                    cells.add(null);
                    position++;
                    continue;
                }
                String id = String.valueOf(value);
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("id", id);
                if (position < tags.size() && tags.get(position) != null) cell.put("tag", tags.get(position));
                Map<String, Object> cellItem = index.itemsById().get(id);
                cell.put("nameZh", cellItem == null ? "" : cellItem.getOrDefault("nameZh", ""));
                cell.put("nameEn", cellItem == null ? "" : cellItem.getOrDefault("nameEn", ""));
                cell.put("kind", cellItem == null ? "item" : cellItem.getOrDefault("kind", "item"));
                cell.put("icon", cellItem == null ? null
                        : renders.iconPath(id, String.valueOf(cellItem.getOrDefault("kind", "item"))));
                cells.add(cell);
                position++;
            }
        }
        row.put("gridDetail", cells);
        return row;
    }

    private Map<String, Object> page(List<Map<String, Object>> records, long total, int page, int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", Math.max(page, 1));
        result.put("size", Math.min(Math.max(size, 1), 200));
        return result;
    }

    /** 标签成员：配方格子上的 #tag 悬浮时展示全部物品。 */
    Map<String, Object> tag(String version, String rawId) {
        String id = rawId == null ? "" : rawId.trim();
        if (id.startsWith("#")) id = id.substring(1);
        if (id.isBlank()) throw Web.badRequest("缺少标签 id");
        Optional<String> selected = select(version);
        if (selected.isEmpty()) return Map.of("tag", id, "total", 0, "members", List.of());
        String v = selected.get();
        WikiCatalog.Index index = catalog.get(v);
        java.util.Set<String> hidden = store.hiddenItems();
        List<String> members = store.tags(v).getOrDefault(id, List.of()).stream()
                .filter(member -> !hidden.contains(member)).toList();
        if (members.isEmpty() && "minecraft:dyes".equals(id)) {
            List<String> colors = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime",
                    "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
            members = colors.stream().map(color -> "minecraft:" + color + "_dye")
                    .filter(index.itemsById()::containsKey).toList();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String member : members) {
            Map<String, Object> item = index.itemsById().get(member);
            if (item == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            String kind = String.valueOf(item.getOrDefault("kind", "item"));
            row.put("namespacedId", member);
            row.put("kind", kind);
            row.put("nameZh", item.getOrDefault("nameZh", ""));
            row.put("nameEn", item.getOrDefault("nameEn", ""));
            row.put("icon", renders.iconPath(member, kind));
            rows.add(row);
            if (rows.size() >= 300) break;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tag", id);
        result.put("total", members.size());
        result.put("members", rows);
        return result;
    }

    /** 公开的已发布版本列表，供版本比对页使用。 */
    Map<String, Object> importedVersions() {
        Map<String, Map<String, Object>> imports = store.imports();
        List<String> published = publishedVersions();
        String current = currentVersion();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> version : store.versions()) {
            String id = String.valueOf(version.get("id"));
            Map<String, Object> record = imports.get(id);
            if (record == null || !published.contains(id)) continue;
            Map<String, Object> row = new LinkedHashMap<>(version);
            row.put("items", record.getOrDefault("items", 0));
            row.put("recipes", record.getOrDefault("recipes", 0));
            row.put("published", published.contains(id));
            row.put("current", id.equals(current));
            rows.add(row);
        }
        rows.sort((a, b) -> String.valueOf(b.getOrDefault("releaseTime", ""))
                .compareTo(String.valueOf(a.getOrDefault("releaseTime", ""))));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        result.put("total", rows.size());
        return result;
    }

    /** 两个已导入版本之间的物品 / 配方差异。 */
    Map<String, Object> compare(String from, String to) {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw Web.badRequest("请选择两个版本");
        }
        if (from.equals(to)) throw Web.badRequest("请选择不同的版本");
        List<String> published = publishedVersions();
        if (!published.contains(from)) throw Web.badRequest("版本尚未发布：" + from);
        if (!published.contains(to)) throw Web.badRequest("版本尚未发布：" + to);
        WikiCatalog.Index fromIndex = catalog.get(from);
        WikiCatalog.Index toIndex = catalog.get(to);

        Map<String, Map<String, Object>> fromItems = fromIndex.itemsById();
        Map<String, Map<String, Object>> toItems = toIndex.itemsById();
        List<Map<String, Object>> itemsAdded = new ArrayList<>();
        List<Map<String, Object>> itemsRemoved = new ArrayList<>();
        List<Map<String, Object>> itemsModified = new ArrayList<>();
        java.util.Set<String> hidden = store.hiddenItems();
        for (Map.Entry<String, Map<String, Object>> entry : toItems.entrySet()) {
            String id = entry.getKey();
            if (hidden.contains(id)) continue;
            Map<String, Object> next = entry.getValue();
            Map<String, Object> previous = fromItems.get(id);
            if (previous == null) {
                itemsAdded.add(compareItem(from, to, id, null, next));
                continue;
            }
            boolean previousBlank = blank(previous.get("nameZh")) && blank(previous.get("nameEn"));
            boolean nextBlank = blank(next.get("nameZh")) && blank(next.get("nameEn"));
            if (previousBlank) {
                // 旧版只是占位条目，新版补上了名称才视为新增；两侧都无名称的占位不计入差异
                if (!nextBlank) itemsAdded.add(compareItem(from, to, id, null, next));
                continue;
            }
            if (nextBlank) continue;
            // 仅补全英文名不算物品修改，避免把翻译补全误报为改动
            if (!same(next.get("nameZh"), previous.get("nameZh"))
                    || !same(next.get("kind"), previous.get("kind"))) {
                itemsModified.add(compareItem(from, to, id, previous, next));
            }
        }
        for (Map.Entry<String, Map<String, Object>> entry : fromItems.entrySet()) {
            if (toItems.containsKey(entry.getKey()) || hidden.contains(entry.getKey())) continue;
            Map<String, Object> previous = entry.getValue();
            if (blank(previous.get("nameZh")) && blank(previous.get("nameEn"))) continue;
            itemsRemoved.add(compareItem(from, to, entry.getKey(), previous, null));
        }

        Map<String, Map<String, Object>> fromRecipes = new LinkedHashMap<>();
        for (Map<String, Object> recipe : fromIndex.recipes()) fromRecipes.put(String.valueOf(recipe.get("recipeId")), recipe);
        Map<String, Map<String, Object>> toRecipes = new LinkedHashMap<>();
        for (Map<String, Object> recipe : toIndex.recipes()) toRecipes.put(String.valueOf(recipe.get("recipeId")), recipe);
        List<Map<String, Object>> recipesAdded = new ArrayList<>();
        List<Map<String, Object>> recipesRemoved = new ArrayList<>();
        List<Map<String, Object>> recipesModified = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : toRecipes.entrySet()) {
            Map<String, Object> next = entry.getValue();
            Map<String, Object> previous = fromRecipes.get(entry.getKey());
            if (previous == null) {
                recipesAdded.add(compareRecipe(fromIndex, toIndex, entry.getKey(), null, next));
                continue;
            }
            if (!same(next.get("type"), previous.get("type"))
                    || !same(next.get("resultId"), previous.get("resultId"))
                    || !same(next.get("resultCount"), previous.get("resultCount"))
                    || !same(next.get("ingredients"), previous.get("ingredients"))
                    || !same(next.get("grid"), previous.get("grid"))) {
                recipesModified.add(compareRecipe(fromIndex, toIndex, entry.getKey(), previous, next));
            }
        }
        for (String id : fromRecipes.keySet()) {
            if (!toRecipes.containsKey(id)) recipesRemoved.add(compareRecipe(fromIndex, toIndex, id, fromRecipes.get(id), null));
        }

        Map<String, Map<String, Integer>> itemKindCounts = new LinkedHashMap<>();
        for (String kind : List.of("item", "block", "entity")) {
            Map<String, Integer> entry = new LinkedHashMap<>();
            entry.put("added", 0);
            entry.put("modified", 0);
            entry.put("removed", 0);
            itemKindCounts.put(kind, entry);
        }
        countKinds(itemKindCounts, itemsAdded, "added");
        countKinds(itemKindCounts, itemsModified, "modified");
        countKinds(itemKindCounts, itemsRemoved, "removed");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from);
        result.put("to", to);
        result.put("itemKindCounts", itemKindCounts);
        result.put("fromStats", Map.of("items", visibleItemCount(fromIndex, hidden),
                "recipes", fromIndex.recipes().size()));
        result.put("toStats", Map.of("items", visibleItemCount(toIndex, hidden),
                "recipes", toIndex.recipes().size()));
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("itemsAdded", itemsAdded.size());
        counts.put("itemsRemoved", itemsRemoved.size());
        counts.put("itemsModified", itemsModified.size());
        counts.put("recipesAdded", recipesAdded.size());
        counts.put("recipesRemoved", recipesRemoved.size());
        counts.put("recipesModified", recipesModified.size());
        result.put("counts", counts);
        result.put("items", diffBlock(itemsAdded, itemsRemoved, itemsModified));
        result.put("recipes", diffBlock(recipesAdded, recipesRemoved, recipesModified));
        return result;
    }

    private static int visibleItemCount(WikiCatalog.Index index, java.util.Set<String> hidden) {
        int count = 0;
        for (Map<String, Object> item : index.items()) {
            if (!hidden.contains(String.valueOf(item.get("namespacedId")))) count++;
        }
        return count;
    }

    private static void countKinds(Map<String, Map<String, Integer>> counts, List<Map<String, Object>> rows,
                                   String bucket) {
        for (Map<String, Object> row : rows) {
            Map<String, Object> present = row.get("to") instanceof Map<?, ?> to && !to.isEmpty()
                    ? row.get("to") instanceof Map<?, ?> map ? castMap(map) : Map.of()
                    : row.get("from") instanceof Map<?, ?> from ? castMap(from) : Map.of();
            String kind = String.valueOf(present.getOrDefault("kind", "item"));
            counts.computeIfAbsent(kind, key -> {
                Map<String, Integer> entry = new LinkedHashMap<>();
                entry.put("added", 0);
                entry.put("modified", 0);
                entry.put("removed", 0);
                return entry;
            }).merge(bucket, 1, Integer::sum);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static Map<String, Object> diffBlock(List<Map<String, Object>> added,
                                                 List<Map<String, Object>> removed,
                                                 List<Map<String, Object>> modified) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("added", limit(added));
        block.put("removed", limit(removed));
        block.put("modified", limit(modified));
        return block;
    }

    private static List<Map<String, Object>> limit(List<Map<String, Object>> rows) {
        return rows.size() <= 2000 ? rows : rows.subList(0, 2000);
    }

    private static boolean blank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private static boolean same(Object a, Object b) {
        return String.valueOf(a == null ? "" : a).equals(String.valueOf(b == null ? "" : b));
    }

    private Map<String, Object> compareItem(String from, String to, String id,
                                            Map<String, Object> previous, Map<String, Object> next) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("namespacedId", id);
        row.put("row", itemRow(from, next == null ? previous : next));
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (previous != null) {
            String kind = String.valueOf(previous.getOrDefault("kind", "item"));
            before.put("nameZh", previous.getOrDefault("nameZh", ""));
            before.put("nameEn", previous.getOrDefault("nameEn", ""));
            before.put("kind", kind);
            before.put("icon", renders.iconPath(id, kind));
        }
        if (next != null) {
            String kind = String.valueOf(next.getOrDefault("kind", "item"));
            after.put("nameZh", next.getOrDefault("nameZh", ""));
            after.put("nameEn", next.getOrDefault("nameEn", ""));
            after.put("kind", kind);
            after.put("icon", renders.iconPath(id, kind));
        }
        row.put("from", before);
        row.put("to", after);
        return row;
    }

    private Map<String, Object> compareRecipe(WikiCatalog.Index fromIndex, WikiCatalog.Index toIndex,
                                              String id, Map<String, Object> previous, Map<String, Object> next) {
        Map<String, Object> source = next == null ? previous : next;
        WikiCatalog.Index index = next == null ? fromIndex : toIndex;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("recipeId", id);
        row.put("type", source.getOrDefault("type", ""));
        row.put("resultId", source.getOrDefault("resultId", ""));
        row.put("resultCount", source.getOrDefault("resultCount", 1));
        row.put("resultNameZh", recipeResultName(index, source));
        row.put("from", recipeSummary(previous, fromIndex));
        row.put("to", recipeSummary(next, toIndex));
        return row;
    }

    private static String recipeResultName(WikiCatalog.Index index, Map<String, Object> recipe) {
        Map<String, Object> item = index.itemsById().get(String.valueOf(recipe.get("resultId")));
        return item == null ? "" : String.valueOf(item.getOrDefault("nameZh", ""));
    }

    private Map<String, Object> recipeSummary(Map<String, Object> recipe, WikiCatalog.Index index) {
        if (recipe == null) return Map.of();
        Map<String, Object> row = new LinkedHashMap<>();
        String resultId = String.valueOf(recipe.getOrDefault("resultId", ""));
        Map<String, Object> result = index.itemsById().get(resultId);
        row.put("type", recipe.getOrDefault("type", ""));
        row.put("resultId", resultId);
        row.put("resultCount", recipe.getOrDefault("resultCount", 1));
        row.put("resultNameZh", result == null ? "" : result.getOrDefault("nameZh", ""));
        row.put("resultNameEn", result == null ? "" : result.getOrDefault("nameEn", ""));
        row.put("resultIcon", result == null ? null : renders.iconPath(resultId,
                String.valueOf(result.getOrDefault("kind", "item"))));
        row.put("ingredients", recipe.getOrDefault("ingredients", List.of()));
        List<String> grid = new ArrayList<>();
        Object rawGrid = recipe.get("grid");
        if (rawGrid instanceof List<?> list) {
            for (Object value : list) grid.add(value == null ? null : String.valueOf(value));
        }
        row.put("grid", grid);
        List<String> gridTags = new ArrayList<>();
        Object rawTags = recipe.get("gridTags");
        if (rawTags instanceof List<?> list) {
            for (Object value : list) gridTags.add(value == null ? null : String.valueOf(value));
        }
        List<Object> gridDetail = new ArrayList<>();
        int position = 0;
        for (String id : grid) {
            if (id == null) {
                gridDetail.add(null);
                position++;
                continue;
            }
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("id", id);
            Map<String, Object> item = index.itemsById().get(id);
            cell.put("nameZh", item == null ? "" : item.getOrDefault("nameZh", ""));
            cell.put("nameEn", item == null ? "" : item.getOrDefault("nameEn", ""));
            cell.put("kind", item == null ? "item" : item.getOrDefault("kind", "item"));
            cell.put("icon", item == null ? null : renders.iconPath(id,
                    String.valueOf(item.getOrDefault("kind", "item"))));
            if (position < gridTags.size() && gridTags.get(position) != null) {
                cell.put("tag", gridTags.get(position));
            }
            gridDetail.add(cell);
            position++;
        }
        row.put("gridDetail", gridDetail);
        return row;
    }

    // ---------- 后台 ----------

    Map<String, Object> adminState(String keyword, String type, String status, int page, int size) {
        Map<String, Map<String, Object>> imports = store.imports();
        List<String> published = publishedVersions();
        String current = currentVersion();
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String typeFilter = type == null || type.isBlank() ? null : type;
        String statusFilter = status == null || status.isBlank() ? null : status;

        List<Map<String, Object>> all = new ArrayList<>();
        Map<String, Map<String, Object>> allById = new LinkedHashMap<>();
        for (Map<String, Object> version : store.versions()) {
            Map<String, Object> row = adminRow(version, imports, published, current);
            all.add(row);
            allById.put(String.valueOf(row.get("id")), row);
        }

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : all) {
            String id = String.valueOf(row.get("id"));
            if (!query.isEmpty() && !id.toLowerCase(Locale.ROOT).contains(query)) continue;
            if (typeFilter != null && !typeFilter.equals(String.valueOf(row.get("type")))) continue;
            if ("imported".equals(statusFilter) && !Boolean.TRUE.equals(row.get("imported"))) continue;
            if ("unimported".equals(statusFilter) && Boolean.TRUE.equals(row.get("imported"))) continue;
            if ("published".equals(statusFilter) && !Boolean.TRUE.equals(row.get("published"))) continue;
            filtered.add(row);
        }

        // 有状态筛选时按行平铺；默认视图与“只看快照”把快照折叠到对应正式版下
        boolean grouped = statusFilter == null && (typeFilter == null || "snapshot".equals(typeFilter));
        int capped = Math.min(Math.max(size, 1), grouped ? 50 : 200);
        int from = Math.max(0, (Math.max(page, 1) - 1) * capped);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("publishedVersions", published);
        result.put("current", current);
        result.put("renders", renders.meta());
        result.put("rendersAvailable", renders.available());
        result.put("importedCount", imports.size());
        result.put("versionCount", store.versions().size());
        result.put("grouped", grouped);
        result.put("page", Math.max(page, 1));
        result.put("size", capped);

        if (!grouped) {
            result.put("records", filtered.stream().skip(from).limit(capped).toList());
            result.put("total", filtered.size());
            result.put("snapshotCount", 0);
            return result;
        }

        List<Map<String, Object>> entries = groupVersions(filtered, allById, "snapshot".equals(typeFilter));
        int snapshots = 0;
        for (Map<String, Object> entry : entries) {
            snapshots += ((List<?>) entry.get("snapshots")).size();
        }
        result.put("groups", entries.stream().skip(from).limit(capped).toList());
        result.put("total", entries.size());
        result.put("snapshotCount", snapshots);
        return result;
    }

    /** 渲染资产缺图或已被隐藏的物品，供后台统一管理。 */
    Map<String, Object> adminIcons(String keyword, String kind, String status, int page, int size) {
        List<Map<String, Object>> all = iconRows();
        long missingCount = all.stream().filter(row -> Boolean.TRUE.equals(row.get("missing"))).count();
        long hiddenCount = all.stream().filter(row -> Boolean.TRUE.equals(row.get("hidden"))).count();
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String statusFilter = status == null ? "" : status;
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : all) {
            if (!query.isEmpty()) {
                String haystack = (row.get("namespacedId") + " " + row.get("nameZh") + " " + row.get("nameEn"))
                        .toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) continue;
            }
            if (kind != null && !kind.isBlank() && !kind.equals(String.valueOf(row.get("kind")))) continue;
            if ("hidden".equals(statusFilter) && !Boolean.TRUE.equals(row.get("hidden"))) continue;
            if ("visible".equals(statusFilter) && Boolean.TRUE.equals(row.get("hidden"))) continue;
            filtered.add(row);
        }
        int capped = Math.min(Math.max(size, 1), 100);
        int from = Math.min((Math.max(page, 1) - 1) * capped, filtered.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", filtered.stream().skip(from).limit(capped).toList());
        result.put("total", filtered.size());
        result.put("missingCount", missingCount);
        result.put("hiddenCount", hiddenCount);
        result.put("page", Math.max(page, 1));
        result.put("size", capped);
        return result;
    }

    Map<String, Object> setIconHidden(String id, boolean hidden) {
        String target = id == null ? "" : id.trim();
        if (target.isBlank()) throw Web.badRequest("缺少物品 id");
        boolean known = iconRows().stream().anyMatch(row -> target.equals(row.get("namespacedId")));
        if (!known) throw Web.badRequest("该物品不在缺图或隐藏列表中");
        Set<String> hiddenIds = new LinkedHashSet<>(store.hiddenItems());
        if (hidden) hiddenIds.add(target);
        else hiddenIds.remove(target);
        store.writeHiddenItems(hiddenIds);
        return Map.of("id", target, "hidden", hidden, "count", hiddenIds.size());
    }

    Map<String, Object> setIconsHidden(List<String> ids, boolean hidden) {
        if (ids == null || ids.isEmpty()) throw Web.badRequest("请选择物品");
        Set<String> known = new LinkedHashSet<>();
        for (Map<String, Object> row : iconRows()) known.add(String.valueOf(row.get("namespacedId")));
        Set<String> hiddenIds = new LinkedHashSet<>(store.hiddenItems());
        int changed = 0;
        for (String raw : ids) {
            String id = raw == null ? "" : raw.trim();
            if (id.isBlank() || !known.contains(id)) continue;
            if (hidden ? hiddenIds.add(id) : hiddenIds.remove(id)) changed++;
        }
        store.writeHiddenItems(hiddenIds);
        return Map.of("count", hiddenIds.size(), "changed", changed);
    }

    Map<String, Object> bulkIconHidden(String action) {
        Set<String> hiddenIds = new LinkedHashSet<>(store.hiddenItems());
        if ("hide-missing".equals(action)) {
            for (Map<String, Object> row : iconRows()) {
                if (Boolean.TRUE.equals(row.get("missing"))) hiddenIds.add(String.valueOf(row.get("namespacedId")));
            }
        } else if ("clear".equals(action)) {
            hiddenIds.clear();
        } else {
            throw Web.badRequest("未知操作：" + action);
        }
        store.writeHiddenItems(hiddenIds);
        return Map.of("count", hiddenIds.size());
    }

    /** 汇总所有已导入版本中缺图或被隐藏的物品。 */
    private List<Map<String, Object>> iconRows() {
        Set<String> hidden = store.hiddenItems();
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        Map<String, Map<String, Object>> imports = store.imports();
        for (Map<String, Object> version : store.versions()) {
            String versionId = String.valueOf(version.get("id"));
            if (!imports.containsKey(versionId)) continue;
            for (Map<String, Object> item : catalog.get(versionId).items()) {
                String id = String.valueOf(item.get("namespacedId"));
                if (byId.containsKey(id)) continue;
                String itemKind = String.valueOf(item.getOrDefault("kind", "item"));
                boolean isHidden = hidden.contains(id);
                boolean missing = renders.iconPath(id, itemKind) == null;
                if (!missing && !isHidden) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("namespacedId", id);
                row.put("kind", itemKind);
                row.put("nameZh", item.getOrDefault("nameZh", ""));
                row.put("nameEn", item.getOrDefault("nameEn", ""));
                row.put("missing", missing);
                row.put("hidden", isHidden);
                byId.put(id, row);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>(byId.values());
        rows.sort(Comparator.comparing((Map<String, Object> row) -> String.valueOf(row.get("kind")))
                .thenComparing(row -> String.valueOf(row.get("namespacedId"))));
        return rows;
    }

    private Map<String, Object> adminRow(Map<String, Object> version, Map<String, Map<String, Object>> imports,
                                         List<String> published, String current) {
        String id = String.valueOf(version.get("id"));
        Map<String, Object> record = imports.get(id);
        Map<String, Object> row = new LinkedHashMap<>(version);
        row.put("imported", record != null);
        row.put("items", record == null ? 0 : longNumber(record.get("items")));
        row.put("recipes", record == null ? 0 : longNumber(record.get("recipes")));
        row.put("published", published.contains(id));
        row.put("current", id.equals(current));
        return row;
    }

    private static final java.util.regex.Pattern PRERELEASE_ID =
            java.util.regex.Pattern.compile("^(.+?)-(?:pre|rc)-?\\d+$");
    private static final java.util.regex.Pattern NAMED_SNAPSHOT_ID =
            java.util.regex.Pattern.compile("^(.+?)-snapshot-\\d+$");

    /**
     * 把快照归属到正式版：
     * 1. 名称带正式版号（1.20.5-pre1、1.20.5-rc1、26.3-snapshot-4）直接归到该版本；
     * 2. 周快照（24w14a）与旧快照按发布时间归到之后发布的第一个正式版；
     * 3. 晚于全部正式版的快照进入“未发布快照”分组。
     */
    private List<Map<String, Object>> groupVersions(List<Map<String, Object>> filtered,
                                                    Map<String, Map<String, Object>> allById,
                                                    boolean snapshotsOnly) {
        List<Map<String, Object>> releasesAsc = new ArrayList<>();
        for (Map<String, Object> row : allById.values()) {
            if ("release".equals(String.valueOf(row.get("type")))) releasesAsc.add(row);
        }
        releasesAsc.sort(java.util.Comparator.comparing(row -> instant(String.valueOf(row.get("releaseTime")))));

        Map<String, Map<String, Object>> entries = new LinkedHashMap<>();
        for (Map<String, Object> row : filtered) {
            String type = String.valueOf(row.get("type"));
            String id = String.valueOf(row.get("id"));
            if ("snapshot".equals(type)) {
                Map<String, Object> base = baseRelease(id, row, allById, releasesAsc);
                String key = base == null ? "__upcoming__" : String.valueOf(base.get("id"));
                Map<String, Object> entry = entries.get(key);
                if (entry == null) {
                    entry = new LinkedHashMap<>();
                    entry.put("key", key);
                    entry.put("kind", base == null ? "upcoming" : "release");
                    entry.put("release", base);
                    entry.put("snapshots", new ArrayList<Map<String, Object>>());
                    entries.put(key, entry);
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> snapshots = (List<Map<String, Object>>) entry.get("snapshots");
                snapshots.add(row);
            } else if ("release".equals(type)) {
                Map<String, Object> entry = entries.get(id);
                if (entry == null) {
                    entry = new LinkedHashMap<>();
                    entry.put("key", id);
                    entry.put("kind", "release");
                    entry.put("release", row);
                    entry.put("snapshots", new ArrayList<Map<String, Object>>());
                    entries.put(id, entry);
                }
            } else {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("key", "single:" + id);
                entry.put("kind", "single");
                entry.put("release", row);
                entry.put("snapshots", new ArrayList<Map<String, Object>>());
                entries.put("single:" + id, entry);
            }
        }

        List<Map<String, Object>> list = new ArrayList<>(entries.values());
        if (snapshotsOnly) list.removeIf(entry -> ((List<?>) entry.get("snapshots")).isEmpty());
        for (Map<String, Object> entry : list) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> snapshots = (List<Map<String, Object>>) entry.get("snapshots");
            snapshots.sort(java.util.Comparator.comparing(
                    (Map<String, Object> row) -> instant(String.valueOf(row.get("releaseTime")))).reversed());
            entry.put("snapshotCount", snapshots.size());
            if ("upcoming".equals(entry.get("kind"))) {
                entry.put("label", upcomingVersionLabel(snapshots));
            }
            Object release = entry.get("release");
            String time = release instanceof Map<?, ?> map && map.get("releaseTime") != null
                    ? String.valueOf(map.get("releaseTime"))
                    : snapshots.isEmpty() ? "" : String.valueOf(snapshots.getFirst().get("releaseTime"));
            entry.put("releaseTime", time);
        }
        list.sort((a, b) -> {
            boolean upcomingA = "upcoming".equals(a.get("kind"));
            boolean upcomingB = "upcoming".equals(b.get("kind"));
            if (upcomingA != upcomingB) return upcomingA ? -1 : 1;
            return instant(String.valueOf(b.get("releaseTime"))).compareTo(instant(String.valueOf(a.get("releaseTime"))));
        });
        return list;
    }

    private static final java.util.regex.Pattern UPCOMING_VERSION =
            java.util.regex.Pattern.compile("^(.+?)-(?:snapshot-\\d+|pre-?\\d+|rc-?\\d+)$");

    /** 未发布的快照组从快照名推断目标版本号（26.4-snapshot-7 → 26.4），推断不出时返回 null。 */
    private static String upcomingVersionLabel(List<Map<String, Object>> snapshots) {
        for (Map<String, Object> snapshot : snapshots) {
            java.util.regex.Matcher matcher = UPCOMING_VERSION.matcher(String.valueOf(snapshot.get("id")).toLowerCase(Locale.ROOT));
            if (matcher.matches()) return matcher.group(1);
        }
        return null;
    }

    private Map<String, Object> baseRelease(String id, Map<String, Object> snapshot,
                                            Map<String, Map<String, Object>> allById,
                                            List<Map<String, Object>> releasesAsc) {
        String lower = id.toLowerCase(Locale.ROOT);
        java.util.regex.Matcher matcher = PRERELEASE_ID.matcher(lower);
        if (!matcher.matches()) matcher = NAMED_SNAPSHOT_ID.matcher(lower);
        if (matcher.matches()) {
            Map<String, Object> release = allById.get(matcher.group(1));
            if (release != null && "release".equals(String.valueOf(release.get("type")))) return release;
        }
        java.time.Instant time = instant(String.valueOf(snapshot.get("releaseTime")));
        for (Map<String, Object> release : releasesAsc) {
            if (!instant(String.valueOf(release.get("releaseTime"))).isBefore(time)) return release;
        }
        return null;
    }

    private static java.time.Instant instant(String value) {
        try {
            return java.time.Instant.parse(value);
        } catch (Exception e) {
            return java.time.Instant.EPOCH;
        }
    }

    int refreshVersions() {
        List<Map<String, Object>> versions = mojang.manifest();
        store.writeVersions(versions);
        return versions.size();
    }

    Map<String, Object> deleteVersionData(String version) {
        store.deleteVersionData(version);
        catalog.invalidate(version);
        unpublish(version);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", true);
        result.put("published", publishedVersions());
        return result;
    }

    private static long longNumber(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
