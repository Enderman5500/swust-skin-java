package cn.edu.swust.mc.skin.plugins.mcwiki;

import cn.edu.swust.mc.skin.Web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
        savePublication(version, published);
    }

    void unpublish(String version) {
        List<String> published = new ArrayList<>(publishedVersions());
        if (!published.remove(version)) return;
        if (published.isEmpty()) {
            store.writePublication(new LinkedHashMap<>());
            return;
        }
        savePublication(published.getLast(), published);
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
        meta.put("versions", published);
        if (selected == null) {
            meta.put("items", 0);
            meta.put("recipes", 0);
            meta.put("kinds", Map.of());
            return meta;
        }
        WikiCatalog.Index index = catalog.get(selected);
        meta.put("items", index.items().size());
        meta.put("recipes", index.recipes().size());
        meta.put("kinds", catalog.kindCounts(index));
        return meta;
    }

    Map<String, Object> items(String version, String keyword, String kind, int page, int size) {
        Optional<String> selected = select(version);
        if (selected.isEmpty()) return page(List.of(), 0, page, size);
        WikiCatalog.Index index = catalog.get(selected.get());
        List<Map<String, Object>> rows = catalog.search(index, keyword, kind, page, size).stream()
                .map(item -> itemRow(selected.get(), item)).toList();
        return page(rows, catalog.count(index, keyword, kind), page, size);
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
        if (item == null) throw Web.notFound("物品不存在：" + id);
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
        List<Object> cells = new ArrayList<>();
        if (grid instanceof List<?> list) {
            for (Object value : list) {
                if (value == null) {
                    cells.add(null);
                    continue;
                }
                String id = String.valueOf(value);
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("id", id);
                Map<String, Object> cellItem = index.itemsById().get(id);
                cell.put("nameZh", cellItem == null ? "" : cellItem.getOrDefault("nameZh", ""));
                cell.put("nameEn", cellItem == null ? "" : cellItem.getOrDefault("nameEn", ""));
                cell.put("kind", cellItem == null ? "item" : cellItem.getOrDefault("kind", "item"));
                cell.put("icon", cellItem == null ? null
                        : renders.iconPath(id, String.valueOf(cellItem.getOrDefault("kind", "item"))));
                cells.add(cell);
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
