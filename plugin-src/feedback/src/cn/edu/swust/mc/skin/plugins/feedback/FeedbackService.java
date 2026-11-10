package cn.edu.swust.mc.skin.plugins.feedback;

import cn.edu.swust.mc.skin.AuthUser;
import cn.edu.swust.mc.skin.Web;
import cn.edu.swust.mc.skin.plugin.PluginResponse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 问题反馈用例编排。
 *
 * <p>用户端所有读写都先按「反馈 ID + 当前登录者」定位，管理端才做跨用户查询与受理；
 * 管理权限不会扩大用户端的数据范围。管理端的受理动作会自动把操作者登记为处理人，
 * 并把「待处理」推进为「处理中」。管理员给出结论（已解决 / 不予处理）后，提交人可归档；
 * 结论超过 7 天未归档的由系统在读取时自动归档。
 */
final class FeedbackService {

    static final int MAX_TITLE_LENGTH = 120;
    static final int MAX_DESCRIPTION_LENGTH = 5000;
    static final int MAX_ATTACHMENT_COUNT = 6;
    static final int MAX_ASSIGNEE_COUNT = 8;
    static final int MAX_CATEGORY_NAME = 30;
    static final int ARCHIVE_GRACE_DAYS = 7;
    static final String SYSTEM_ACTOR = "system";
    static final long MAX_ATTACHMENT_BYTES = 6L * 1024 * 1024;

    private static final int MAX_COMMENT_LENGTH = 2000;
    private static final int MAX_COMMENT_COUNT = 200;
    private static final int MAX_ACTIVITY_COUNT = 200;
    private static final long ARCHIVE_GRACE_MILLIS = ARCHIVE_GRACE_DAYS * 86_400_000L;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String PENDING = "pending";
    private static final String PROCESSING = "processing";
    private static final String RESOLVED = "resolved";
    private static final String REJECTED = "rejected";
    private static final String ARCHIVED = "archived";

    private static final Map<String, String> STATUS_LABELS = Map.of(
            PENDING, "待处理", PROCESSING, "处理中", RESOLVED, "已解决",
            REJECTED, "不予处理", ARCHIVED, "已归档");
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "bug", "缺陷反馈", "suggestion", "功能建议", "question", "使用咨询", "other", "其他");
    private static final Map<String, String> SEVERITY_LABELS = Map.of(
            "low", "轻微", "normal", "一般", "high", "严重", "critical", "致命");
    private static final Map<String, String> ACTION_LABELS = Map.of(
            "submit", "提交反馈", "update", "修改了反馈内容", "comment", "追加说明",
            "reply", "官方回复", "note", "记录内部备注", "status", "变更状态",
            "assign", "指派处理人", "unassign", "取消指派", "severity", "调整严重程度",
            "archive", "归档");

    private final FeedbackStore store;

    FeedbackService(FeedbackStore store) {
        this.store = store;
    }

    // ------------------------------------------------------------------ 用户端

    synchronized Map<String, Object> submit(AuthUser user, Map<String, Object> body) {
        long now = System.currentTimeMillis();
        Map<String, Object> doc = FeedbackStore.row();
        doc.put("id", nextId());
        doc.put("authorId", user.id);
        doc.put("authorName", displayName(user));
        doc.put("title", normalizeTitle(text(body.get("title"))));
        doc.put("description", normalizeDescription(text(body.get("description"))));
        doc.put("type", typeOf(text(body.get("type")), true));
        doc.put("severity", severityOf(text(body.get("severity")), true));
        Map<String, Object> category = categoryFor(text(body.get("categoryId")));
        doc.put("categoryId", str(category.get("id")));
        doc.put("categoryName", str(category.get("name")));
        doc.put("status", PENDING);
        doc.put("assignees", new ArrayList<Map<String, Object>>());
        doc.put("attachments", cleanList(body.get("attachments"), MAX_ATTACHMENT_COUNT));
        doc.put("comments", new ArrayList<Map<String, Object>>());
        doc.put("activities", new ArrayList<Map<String, Object>>());
        doc.put("createdAt", now);
        doc.put("updatedAt", now);
        doc.put("closedAt", null);
        record(doc, user.id, displayName(user), "submit", "", now);
        applyCategoryDefaultAssignee(doc, category, now);
        List<Map<String, Object>> rows = store.feedbacks();
        rows.add(doc);
        store.writeFeedbacks(rows);
        return view(doc, user.id, false);
    }

    synchronized Map<String, Object> myPage(AuthUser user, Query query) {
        sweep(System.currentTimeMillis());
        return page(matched(query.withAuthor(user.id)), query, user.id, false);
    }

    synchronized Map<String, Object> myFeedback(AuthUser user, String id) {
        sweep(System.currentTimeMillis());
        return view(findOwn(id, user.id), user.id, false);
    }

    synchronized Map<String, Object> myStats(AuthUser user) {
        sweep(System.currentTimeMillis());
        return stats(user.id);
    }

    synchronized Map<String, Object> update(AuthUser user, String id, Map<String, Object> body) {
        Map<String, Object> doc = findOwn(id, user.id);
        if (!isOpen(status(doc))) {
            throw Web.badRequest("反馈已完结，无法再修改内容");
        }
        long now = System.currentTimeMillis();
        String previousCategoryId = str(doc.get("categoryId"));
        doc.put("title", normalizeTitle(text(body.get("title"))));
        doc.put("description", normalizeDescription(text(body.get("description"))));
        doc.put("type", typeOf(text(body.get("type")), true));
        doc.put("severity", severityOf(text(body.get("severity")), true));
        Map<String, Object> category = categoryFor(text(body.get("categoryId")));
        doc.put("categoryId", str(category.get("id")));
        doc.put("categoryName", str(category.get("name")));
        doc.put("attachments", cleanList(body.get("attachments"), MAX_ATTACHMENT_COUNT));
        record(doc, user.id, displayName(user), "update", "", now);
        if (!str(category.get("id")).equals(previousCategoryId)) {
            applyCategoryDefaultAssignee(doc, category, now);
        }
        persist(doc);
        return view(doc, user.id, false);
    }

    synchronized Map<String, Object> comment(AuthUser user, String id, String content) {
        Map<String, Object> doc = findOwn(id, user.id);
        long now = System.currentTimeMillis();
        addComment(doc, user.id, displayName(user), "user", content, false, now);
        persist(doc);
        return view(doc, user.id, false);
    }

    synchronized Map<String, Object> archive(AuthUser user, String id) {
        Map<String, Object> doc = findOwn(id, user.id);
        String status = status(doc);
        if (ARCHIVED.equals(status)) {
            throw Web.badRequest("反馈已归档");
        }
        if (!isConcluded(status)) {
            throw Web.badRequest("管理员给出结论后才能归档，当前状态为「" + statusLabel(status) + "」");
        }
        long now = System.currentTimeMillis();
        doc.put("status", ARCHIVED);
        doc.put("closedAt", now);
        record(doc, user.id, displayName(user), "archive", "", now);
        persist(doc);
        return view(doc, user.id, false);
    }

    synchronized void withdraw(AuthUser user, String id) {
        Map<String, Object> doc = findOwn(id, user.id);
        if (!isOpen(status(doc))) {
            throw Web.badRequest("反馈已完结，无法撤回；如仍需处理请追加说明");
        }
        List<Map<String, Object>> rows = store.feedbacks();
        rows.removeIf(item -> id.equals(str(item.get("id"))));
        store.writeFeedbacks(rows);
    }

    // ------------------------------------------------------------------ 管理端

    synchronized Map<String, Object> adminPage(Query query) {
        sweep(System.currentTimeMillis());
        return page(matched(query), query, null, true);
    }

    synchronized Map<String, Object> adminFeedback(String id) {
        sweep(System.currentTimeMillis());
        return view(find(id), null, true);
    }

    synchronized Map<String, Object> adminStats() {
        sweep(System.currentTimeMillis());
        return stats(null);
    }

    synchronized Map<String, Object> adminReply(AuthUser operator, String id, String content) {
        Map<String, Object> doc = find(id);
        long now = System.currentTimeMillis();
        claim(doc, operator, now);
        autoStartProcessing(doc, operator, now);
        addComment(doc, operator.id, displayName(operator), "admin", content, false, now);
        persist(doc);
        return view(doc, null, true);
    }

    synchronized Map<String, Object> adminNote(AuthUser operator, String id, String content) {
        Map<String, Object> doc = find(id);
        long now = System.currentTimeMillis();
        claim(doc, operator, now);
        autoStartProcessing(doc, operator, now);
        addComment(doc, operator.id, displayName(operator), "admin", content, true, now);
        persist(doc);
        return view(doc, null, true);
    }

    synchronized Map<String, Object> adminStatus(AuthUser operator, String id, String status, String note) {
        Map<String, Object> doc = find(id);
        long now = System.currentTimeMillis();
        claim(doc, operator, now);
        changeStatus(doc, statusOf(status, true), operator, note, now);
        persist(doc);
        return view(doc, null, true);
    }

    synchronized Map<String, Object> adminArchive(AuthUser operator, String id) {
        Map<String, Object> doc = find(id);
        if (ARCHIVED.equals(status(doc))) {
            throw Web.badRequest("反馈已归档");
        }
        long now = System.currentTimeMillis();
        doc.put("status", ARCHIVED);
        doc.put("closedAt", now);
        record(doc, operator.id, displayName(operator), "archive", "由管理员归档", now);
        persist(doc);
        return view(doc, null, true);
    }

    synchronized Map<String, Object> adminAssign(AuthUser operator, String id, List<Map<String, String>> requested) {
        Map<String, Object> doc = find(id);
        List<Map<String, Object>> next = normalizeAssignees(requested);
        if (next.size() > MAX_ASSIGNEE_COUNT) {
            throw Web.badRequest("单条反馈最多只能有 " + MAX_ASSIGNEE_COUNT + " 个处理人");
        }
        if (assigneeIds(next).equals(assigneeIds(assignees(doc)))) {
            throw Web.badRequest("处理人没有变化");
        }
        long now = System.currentTimeMillis();
        autoStartProcessing(doc, operator, now);
        List<String> names = new ArrayList<>();
        for (Map<String, Object> item : next) names.add(str(item.get("name")));
        setMutableList(doc, "assignees", next);
        if (next.isEmpty()) {
            record(doc, operator.id, displayName(operator), "unassign", "已清空处理人", now);
        } else {
            record(doc, operator.id, displayName(operator), "assign",
                    "处理人：" + String.join("、", names), now);
        }
        persist(doc);
        return view(doc, null, true);
    }

    synchronized Map<String, Object> adminSeverity(AuthUser operator, String id, String severity) {
        Map<String, Object> doc = find(id);
        String target = severityOf(severity, true);
        if (target.equals(severity(doc))) {
            throw Web.badRequest("严重程度已是「" + severityLabel(target) + "」");
        }
        long now = System.currentTimeMillis();
        claim(doc, operator, now);
        autoStartProcessing(doc, operator, now);
        doc.put("severity", target);
        record(doc, operator.id, displayName(operator), "severity",
                "调整为「" + severityLabel(target) + "」", now);
        persist(doc);
        return view(doc, null, true);
    }

    synchronized void adminDelete(String id) {
        List<Map<String, Object>> rows = store.feedbacks();
        rows.removeIf(item -> id.equals(str(item.get("id"))));
        store.writeFeedbacks(rows);
    }

    // ------------------------------------------------------------------ 附件

    synchronized Map<String, Object> uploadAttachment(byte[] data, String originalName) {
        if (data == null || data.length == 0) throw Web.badRequest("请选择要上传的图片");
        if (data.length > MAX_ATTACHMENT_BYTES) throw new Web.HttpError(413, "payload_too_large", "附件不能超过 6MB");
        String extension = imageExtension(data);
        if (extension == null) throw Web.badRequest("仅支持 PNG、JPG、GIF、WEBP 图片");
        String name = nextId() + extension;
        try {
            store.saveAttachment(name, data);
        } catch (Exception e) {
            throw new IllegalStateException("附件保存失败", e);
        }
        Map<String, Object> result = FeedbackStore.row();
        result.put("name", safeName(originalName));
        result.put("url", "/api/plugins/feedback/attachment?file=" + name);
        return result;
    }

    synchronized PluginResponse attachment(String file) {
        byte[] data;
        try {
            data = store.attachment(file);
        } catch (Exception e) {
            throw new IllegalStateException("附件读取失败", e);
        }
        if (data == null) throw Web.notFound("附件不存在");
        return PluginResponse.inline(contentType(file), data);
    }

    // ------------------------------------------------------------------ 分类

    /** 用户可选的分类：仅启用的定义，按创建时间排序。 */
    synchronized List<Map<String, Object>> userCategories() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> category : sortedCategories()) {
            if (!truthy(category.get("enabled"))) continue;
            Map<String, Object> item = FeedbackStore.row();
            item.put("id", str(category.get("id")));
            item.put("name", str(category.get("name")));
            result.add(item);
        }
        return result;
    }

    synchronized Map<String, Object> adminCategories() {
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> doc : store.feedbacks()) {
            String id = str(doc.get("categoryId"));
            if (!id.isEmpty()) counts.merge(id, 1, Integer::sum);
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> category : sortedCategories()) {
            String id = str(category.get("id"));
            Map<String, Object> item = FeedbackStore.row();
            item.put("id", id);
            item.put("name", str(category.get("name")));
            item.put("enabled", truthy(category.get("enabled")));
            item.put("count", counts.getOrDefault(id, 0));
            List<Map<String, Object>> defaultAssignees = categoryAssignees(category);
            List<String> defaultNames = new ArrayList<>();
            for (Map<String, Object> assignee : defaultAssignees) defaultNames.add(str(assignee.get("name")));
            item.put("defaultAssignees", defaultAssignees);
            item.put("defaultAssigneeName", String.join("、", defaultNames));
            item.put("createdAt", num(category.get("createdAt")));
            item.put("createdAtLabel", formatTime(num(category.get("createdAt"))));
            records.add(item);
        }
        Map<String, Object> result = FeedbackStore.row();
        result.put("records", records);
        return result;
    }

    synchronized Map<String, Object> saveCategory(Map<String, Object> body) {
        String id = trim(text(body.get("id")));
        String name = trim(text(body.get("name")));
        if (name.isEmpty()) throw Web.badRequest("分类名称不能为空");
        if (name.length() > MAX_CATEGORY_NAME) {
            throw Web.badRequest("分类名称不能超过 " + MAX_CATEGORY_NAME + " 个字符");
        }
        boolean enabled = !body.containsKey("enabled") || truthy(body.get("enabled"));
        boolean hasAssignees = body.containsKey("defaultAssignees") || body.containsKey("defaultAssigneeId");
        List<Map<String, Object>> defaultAssignees = cleanCategoryAssignees(body);
        List<Map<String, Object>> categories = store.categories();
        for (Map<String, Object> category : categories) {
            if (!str(category.get("id")).equals(id) && name.equalsIgnoreCase(str(category.get("name")))) {
                throw Web.badRequest("分类名称已存在");
            }
        }
        if (id.isEmpty()) {
            Map<String, Object> created = FeedbackStore.row();
            created.put("id", nextId());
            created.put("name", name);
            created.put("enabled", enabled);
            created.put("defaultAssignees", defaultAssignees);
            created.put("createdAt", System.currentTimeMillis());
            categories.add(created);
        } else {
            Map<String, Object> target = null;
            for (Map<String, Object> category : categories) {
                if (id.equals(str(category.get("id")))) target = category;
            }
            if (target == null) throw Web.notFound("分类不存在");
            target.put("name", name);
            target.put("enabled", enabled);
            // 只传启用状态时不改动默认处理人，避免启停操作把设置清空。
            if (hasAssignees) {
                target.put("defaultAssignees", defaultAssignees);
                target.remove("defaultAssigneeId");
                target.remove("defaultAssigneeName");
            }
        }
        store.writeCategories(categories);
        return adminCategories();
    }

    synchronized Map<String, Object> deleteCategory(String id) {
        String value = trim(id);
        if (value.isEmpty()) throw Web.badRequest("缺少分类 ID");
        List<Map<String, Object>> categories = store.categories();
        boolean removed = categories.removeIf(category -> value.equals(str(category.get("id"))));
        if (!removed) throw Web.notFound("分类不存在");
        store.writeCategories(categories);
        return adminCategories();
    }

    private List<Map<String, Object>> sortedCategories() {
        List<Map<String, Object>> categories = store.categories();
        categories.sort(Comparator.comparingLong(category -> num(category.get("createdAt"))));
        return categories;
    }

    private Map<String, String> categoryNames() {
        Map<String, String> names = new HashMap<>();
        for (Map<String, Object> category : store.categories()) {
            names.put(str(category.get("id")), str(category.get("name")));
        }
        return names;
    }

    /** 校验提交/修改时选择的分类；空值表示未分类，停用或不存在都拒绝。 */
    private Map<String, Object> categoryFor(String id) {
        String value = trim(id);
        Map<String, Object> empty = FeedbackStore.row();
        empty.put("id", "");
        empty.put("name", "");
        if (value.isEmpty()) return empty;
        for (Map<String, Object> category : store.categories()) {
            if (value.equals(str(category.get("id")))) {
                if (!truthy(category.get("enabled"))) {
                    throw Web.badRequest("所选分类已停用，请重新选择");
                }
                return category;
            }
        }
        throw Web.badRequest("所选分类不存在，请重新选择");
    }

    // ------------------------------------------------------------------ 查询

    /** 查询条件；authorId 是数据范围而不是可选筛选，用户端由用例强制覆盖为当前登录者。 */
    record Query(String keyword, String status, String type, String severity, String categoryId,
                 String assigneeId, boolean unassignedOnly, boolean openOnly, String authorId,
                 Long from, Long to, int page, int size) {

        Query withAuthor(String value) {
            return new Query(keyword, status, type, severity, categoryId, assigneeId, unassignedOnly,
                    openOnly, value, from, to, page, size);
        }

        int safePage() {
            return Math.max(1, page);
        }

        int safeSize() {
            return size <= 0 ? 10 : Math.min(100, size);
        }
    }

    private List<Map<String, Object>> matched(Query query) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> doc : store.feedbacks()) {
            if (query.authorId() != null && !query.authorId().isBlank()
                    && !query.authorId().equals(str(doc.get("authorId")))) {
                continue;
            }
            if (!allowsStatus(query.status(), status(doc))) continue;
            if (query.openOnly() && !isOpen(status(doc))) continue;
            if (!query.type().isBlank() && !query.type().equals(typeValue(doc))) continue;
            if (!query.severity().isBlank() && !query.severity().equals(severity(doc))) continue;
            if (!query.categoryId().isBlank() && !query.categoryId().equals(str(doc.get("categoryId")))) continue;
            if (query.unassignedOnly() && !assignees(doc).isEmpty()) continue;
            if (!query.unassignedOnly() && !query.assigneeId().isBlank()
                    && !assigneeIds(assignees(doc)).contains(query.assigneeId())) {
                continue;
            }
            if (!matchesKeyword(doc, query.keyword())) continue;
            long created = num(doc.get("createdAt"));
            if (query.from() != null && created < query.from()) continue;
            if (query.to() != null && created > query.to()) continue;
            result.add(doc);
        }
        result.sort((left, right) -> {
            int openCompare = Boolean.compare(!isOpen(status(left)), !isOpen(status(right)));
            if (openCompare != 0) return openCompare;
            int severityCompare = Integer.compare(
                    severityWeight(severity(right)), severityWeight(severity(left)));
            if (severityCompare != 0) return severityCompare;
            return Long.compare(num(right.get("updatedAt")), num(left.get("updatedAt")));
        });
        return result;
    }

    private Map<String, Object> page(List<Map<String, Object>> rows, Query query, String viewerId, boolean admin) {
        int page = query.safePage();
        int size = query.safeSize();
        int from = Math.min(rows.size(), (page - 1) * size);
        int to = Math.min(rows.size(), from + size);
        Map<String, String> names = categoryNames();
        List<Map<String, Object>> records = new ArrayList<>();
        if (from < to) {
            for (Map<String, Object> doc : rows.subList(from, to)) {
                records.add(view(doc, viewerId, admin, names));
            }
        }
        Map<String, Object> result = FeedbackStore.row();
        result.put("records", records);
        result.put("total", rows.size());
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    private boolean allowsStatus(String filter, String candidate) {
        if (filter == null || filter.isBlank()) return !ARCHIVED.equals(candidate);
        return filter.equals(candidate);
    }

    private static boolean matchesKeyword(Map<String, Object> doc, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        String needle = keyword.trim().toLowerCase();
        if (str(doc.get("title")).toLowerCase().contains(needle)
                || str(doc.get("description")).toLowerCase().contains(needle)) {
            return true;
        }
        for (Map<String, Object> comment : listOfMaps(doc.get("comments"))) {
            if (str(comment.get("content")).toLowerCase().contains(needle)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 视图

    private Map<String, Object> view(Map<String, Object> doc, String viewerId, boolean admin) {
        return view(doc, viewerId, admin, categoryNames());
    }

    private Map<String, Object> view(Map<String, Object> doc, String viewerId, boolean admin,
                                     Map<String, String> categoryNames) {
        String status = status(doc);
        String type = typeValue(doc);
        String severity = severity(doc);
        String categoryId = str(doc.get("categoryId"));
        String categoryName = categoryNames.getOrDefault(categoryId, str(doc.get("categoryName")));
        boolean owned = viewerId != null && !viewerId.isBlank()
                && viewerId.equals(str(doc.get("authorId")));
        Long closedAt = longOrNull(doc.get("closedAt"));
        Long autoArchiveAt = isConcluded(status) && closedAt != null ? closedAt + ARCHIVE_GRACE_MILLIS : null;

        List<Map<String, Object>> comments = new ArrayList<>();
        for (Map<String, Object> comment : listOfMaps(doc.get("comments"))) {
            boolean internal = truthy(comment.get("internal"));
            if (internal && !admin) continue;
            Map<String, Object> item = FeedbackStore.row();
            item.put("id", str(comment.get("id")));
            item.put("authorId", str(comment.get("authorId")));
            item.put("authorName", str(comment.get("authorName")));
            item.put("authorRole", str(comment.get("authorRole")));
            item.put("adminReply", "admin".equals(str(comment.get("authorRole"))));
            item.put("content", str(comment.get("content")));
            item.put("internal", internal);
            item.put("createdAt", num(comment.get("createdAt")));
            item.put("createdAtLabel", formatTime(num(comment.get("createdAt"))));
            comments.add(item);
        }

        List<Map<String, Object>> activities = new ArrayList<>();
        for (Map<String, Object> activity : listOfMaps(doc.get("activities"))) {
            String action = str(activity.get("action"));
            if ("note".equals(action) && !admin) continue;
            Map<String, Object> item = FeedbackStore.row();
            String actorId = str(activity.get("actorId"));
            item.put("actorId", actorId);
            item.put("actorName", SYSTEM_ACTOR.equals(actorId) ? "系统" : str(activity.get("actorName")));
            item.put("action", action);
            item.put("actionLabel", ACTION_LABELS.getOrDefault(action, "更新"));
            item.put("detail", str(activity.get("detail")));
            item.put("createdAt", num(activity.get("createdAt")));
            item.put("createdAtLabel", formatTime(num(activity.get("createdAt"))));
            activities.add(item);
        }

        List<Map<String, Object>> assignees = assignees(doc);
        List<String> assigneeIds = assigneeIds(assignees);

        Map<String, Object> view = FeedbackStore.row();
        view.put("id", str(doc.get("id")));
        view.put("title", str(doc.get("title")));
        view.put("description", str(doc.get("description")));
        view.put("type", type);
        view.put("typeLabel", TYPE_LABELS.getOrDefault(type, type));
        view.put("categoryId", categoryId);
        view.put("categoryName", categoryName);
        view.put("severity", severity);
        view.put("severityLabel", SEVERITY_LABELS.getOrDefault(severity, severity));
        view.put("status", status);
        view.put("statusLabel", statusLabel(status));
        view.put("open", isOpen(status));
        view.put("authorId", str(doc.get("authorId")));
        view.put("authorName", str(doc.get("authorName")));
        view.put("assigneeIds", assigneeIds);
        view.put("assignees", assignees);
        view.put("attachments", stringList(doc.get("attachments")));
        view.put("comments", comments);
        view.put("activities", activities);
        view.put("commentCount", comments.size());
        view.put("createdAt", num(doc.get("createdAt")));
        view.put("createdAtLabel", formatTime(num(doc.get("createdAt"))));
        view.put("updatedAt", num(doc.get("updatedAt")));
        view.put("updatedAtLabel", formatTime(num(doc.get("updatedAt"))));
        view.put("closedAt", closedAt);
        view.put("closedAtLabel", closedAt == null ? "" : formatTime(closedAt));
        view.put("owned", owned);
        view.put("canEdit", owned && isOpen(status));
        view.put("canArchive", owned && isConcluded(status));
        view.put("autoArchiveAt", autoArchiveAt);
        view.put("autoArchiveAtLabel", autoArchiveAt == null ? "" : formatTime(autoArchiveAt));
        return view;
    }

    private Map<String, Object> stats(String authorId) {
        long pending = 0, processing = 0, resolved = 0, rejected = 0, archived = 0, unassigned = 0;
        int total = 0;
        for (Map<String, Object> doc : store.feedbacks()) {
            if (authorId != null && !authorId.isBlank() && !authorId.equals(str(doc.get("authorId")))) continue;
            total++;
            String status = status(doc);
            switch (status) {
                case PENDING -> pending++;
                case PROCESSING -> processing++;
                case RESOLVED -> resolved++;
                case REJECTED -> rejected++;
                case ARCHIVED -> archived++;
                default -> {
                }
            }
            if (!ARCHIVED.equals(status) && assignees(doc).isEmpty()) unassigned++;
        }
        Map<String, Object> result = FeedbackStore.row();
        result.put("total", total - archived);
        result.put("pending", pending);
        result.put("processing", processing);
        result.put("open", pending + processing);
        result.put("resolved", resolved);
        result.put("rejected", rejected);
        result.put("archived", archived);
        result.put("unassigned", unassigned);
        return result;
    }

    // ------------------------------------------------------------------ 状态流转

    /** 分类默认处理人列表；兼容早期只存单个处理人的数据。 */
    private static List<Map<String, Object>> categoryAssignees(Map<String, Object> category) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> item : listOfMaps(category.get("defaultAssignees"))) {
            String id = trim(str(item.get("id")));
            if (id.isEmpty() || !seen.add(id)) continue;
            String name = trim(str(item.get("name")));
            Map<String, Object> entry = FeedbackStore.row();
            entry.put("id", id);
            entry.put("name", name.isEmpty() ? id : truncate(name, 64));
            result.add(entry);
            if (result.size() >= MAX_ASSIGNEE_COUNT) break;
        }
        if (result.isEmpty()) {
            String legacyId = trim(str(category.get("defaultAssigneeId")));
            if (!legacyId.isEmpty()) {
                String legacyName = trim(str(category.get("defaultAssigneeName")));
                Map<String, Object> entry = FeedbackStore.row();
                entry.put("id", legacyId);
                entry.put("name", legacyName.isEmpty() ? legacyId : truncate(legacyName, 64));
                result.add(entry);
            }
        }
        return result;
    }

    /** 读取并清洗管理端提交的默认处理人列表。 */
    private static List<Map<String, Object>> cleanCategoryAssignees(Map<String, Object> body) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Object raw = body.get("defaultAssignees");
        if (raw instanceof List<?> items) {
            for (Object item : items) {
                String id;
                String name;
                if (item instanceof Map<?, ?> map) {
                    id = text(map.get("id"));
                    name = text(map.get("name"));
                } else {
                    id = text(item);
                    name = id;
                }
                id = trim(id);
                if (id.isEmpty() || !seen.add(id)) continue;
                name = trim(name);
                Map<String, Object> entry = FeedbackStore.row();
                entry.put("id", id);
                entry.put("name", name.isEmpty() ? id : truncate(name, 64));
                result.add(entry);
                if (result.size() >= MAX_ASSIGNEE_COUNT) break;
            }
            return result;
        }
        String id = trim(text(body.get("defaultAssigneeId")));
        if (id.isEmpty()) return result;
        String name = trim(text(body.get("defaultAssigneeName")));
        Map<String, Object> entry = FeedbackStore.row();
        entry.put("id", id);
        entry.put("name", name.isEmpty() ? id : truncate(name, 64));
        result.add(entry);
        return result;
    }

    /** 分类配置了默认处理人时，提交/改分类后自动加入处理人名单。 */
    private void applyCategoryDefaultAssignee(Map<String, Object> doc, Map<String, Object> category, long now) {
        List<Map<String, Object>> defaults = categoryAssignees(category);
        if (defaults.isEmpty()) return;
        List<Map<String, Object>> assignees = assignees(doc);
        List<String> existing = assigneeIds(assignees);
        List<String> added = new ArrayList<>();
        for (Map<String, Object> candidate : defaults) {
            String assigneeId = str(candidate.get("id"));
            if (assigneeId.isEmpty() || existing.contains(assigneeId)
                    || assignees.size() >= MAX_ASSIGNEE_COUNT) {
                continue;
            }
            Map<String, Object> item = FeedbackStore.row();
            item.put("id", assigneeId);
            item.put("name", str(candidate.get("name")));
            assignees.add(item);
            existing.add(assigneeId);
            added.add(str(candidate.get("name")));
        }
        if (added.isEmpty()) return;
        setMutableList(doc, "assignees", assignees);
        record(doc, SYSTEM_ACTOR, "系统", "assign", "按分类默认处理人指派：" + String.join("、", added), now);
    }

    private void claim(Map<String, Object> doc, AuthUser operator, long now) {
        List<Map<String, Object>> assignees = assignees(doc);
        String id = operator.id;
        if (id == null || assigneeIds(assignees).contains(id) || assignees.size() >= MAX_ASSIGNEE_COUNT) {
            return;
        }
        Map<String, Object> item = FeedbackStore.row();
        item.put("id", id);
        item.put("name", displayName(operator));
        assignees.add(item);
        setMutableList(doc, "assignees", assignees);
        record(doc, id, displayName(operator), "assign", "自动加入处理人", now);
    }

    private void autoStartProcessing(Map<String, Object> doc, AuthUser operator, long now) {
        if (!PENDING.equals(status(doc))) return;
        changeStatus(doc, PROCESSING, operator, "管理员受理自动流转", now);
    }

    private void changeStatus(Map<String, Object> doc, String next, AuthUser operator, String note, long now) {
        String current = status(doc);
        if (next.equals(current)) {
            throw Web.badRequest("反馈已处于「" + statusLabel(current) + "」状态");
        }
        doc.put("status", next);
        doc.put("closedAt", isOpen(next) ? null : now);
        String suffix = note == null || note.isBlank() ? "" : "：" + note.trim();
        record(doc, operator.id, displayName(operator), "status",
                "变更为「" + statusLabel(next) + "」" + suffix, now);
    }

    private void addComment(Map<String, Object> doc, String authorId, String authorName, String role,
                            String content, boolean internal, long now) {
        String normalized = normalizeComment(content);
        List<Map<String, Object>> comments = listOfMaps(doc.get("comments"));
        if (comments.size() >= MAX_COMMENT_COUNT) {
            throw Web.badRequest("单条反馈最多保留 " + MAX_COMMENT_COUNT + " 条评论");
        }
        Map<String, Object> comment = FeedbackStore.row();
        comment.put("id", nextId());
        comment.put("authorId", authorId);
        comment.put("authorName", authorName);
        comment.put("authorRole", role);
        comment.put("content", normalized);
        comment.put("internal", internal);
        comment.put("createdAt", now);
        comments.add(comment);
        setMutableList(doc, "comments", comments);
        if (internal) {
            record(doc, authorId, authorName, "note", "", now);
        } else {
            record(doc, authorId, authorName, "admin".equals(role) ? "reply" : "comment", "", now);
        }
    }

    private void record(Map<String, Object> doc, String actorId, String actorName, String action,
                        String detail, long now) {
        List<Map<String, Object>> activities = listOfMaps(doc.get("activities"));
        if (activities.size() >= MAX_ACTIVITY_COUNT) {
            activities.remove(0);
        }
        Map<String, Object> activity = FeedbackStore.row();
        activity.put("actorId", actorId);
        activity.put("actorName", actorName);
        activity.put("action", action);
        activity.put("detail", detail == null ? "" : detail.trim());
        activity.put("createdAt", now);
        activities.add(activity);
        setMutableList(doc, "activities", activities);
        doc.put("updatedAt", now);
    }

    /** 自动归档：已给出结论且超过宽限期仍未主动归档的反馈在读取时收口。 */
    private void sweep(long now) {
        List<Map<String, Object>> rows = store.feedbacks();
        boolean changed = false;
        for (Map<String, Object> doc : rows) {
            String status = status(doc);
            Long closedAt = longOrNull(doc.get("closedAt"));
            if (!isConcluded(status) || closedAt == null || now - closedAt < ARCHIVE_GRACE_MILLIS) {
                continue;
            }
            doc.put("status", ARCHIVED);
            doc.put("closedAt", now);
            record(doc, SYSTEM_ACTOR, "系统", "archive",
                    "超过 " + ARCHIVE_GRACE_DAYS + " 天未归档，系统自动归档", now);
            changed = true;
        }
        if (changed) store.writeFeedbacks(rows);
    }

    // ------------------------------------------------------------------ 内部

    private void persist(Map<String, Object> doc) {
        List<Map<String, Object>> rows = store.feedbacks();
        String id = str(doc.get("id"));
        for (int i = 0; i < rows.size(); i++) {
            if (id.equals(str(rows.get(i).get("id")))) {
                rows.set(i, doc);
                store.writeFeedbacks(rows);
                return;
            }
        }
        rows.add(doc);
        store.writeFeedbacks(rows);
    }

    private Map<String, Object> find(String id) {
        if (id == null || id.isBlank()) throw Web.badRequest("缺少反馈 ID");
        for (Map<String, Object> doc : store.feedbacks()) {
            if (id.equals(str(doc.get("id")))) return doc;
        }
        throw Web.notFound("反馈不存在或已被删除");
    }

    private Map<String, Object> findOwn(String id, String authorId) {
        if (id == null || id.isBlank()) throw Web.badRequest("缺少反馈 ID");
        for (Map<String, Object> doc : store.feedbacks()) {
            if (id.equals(str(doc.get("id"))) && authorId != null
                    && authorId.equals(str(doc.get("authorId")))) {
                return doc;
            }
        }
        throw Web.notFound("反馈不存在或已被撤回");
    }

    static String displayName(AuthUser user) {
        if (user == null) return "";
        if (user.nickname != null && !user.nickname.isBlank()) return user.nickname.trim();
        return user.username == null ? String.valueOf(user.id) : user.username;
    }

    private static String status(Map<String, Object> doc) {
        return statusOf(str(doc.get("status")), false);
    }

    private static String typeValue(Map<String, Object> doc) {
        return typeOf(str(doc.get("type")), false);
    }

    private static String severity(Map<String, Object> doc) {
        return severityOf(str(doc.get("severity")), false);
    }

    static String statusOf(String raw, boolean strict) {
        if (raw == null || raw.isBlank()) return PENDING;
        String value = raw.trim().toLowerCase();
        if ("closed".equals(value)) return ARCHIVED;
        if (STATUS_LABELS.containsKey(value)) return value;
        if (strict) throw Web.badRequest("反馈状态仅支持 pending / processing / resolved / rejected / archived");
        return PENDING;
    }

    static String typeOf(String raw, boolean strict) {
        if (raw == null || raw.isBlank()) return "other";
        String value = raw.trim().toLowerCase();
        if (TYPE_LABELS.containsKey(value)) return value;
        if (strict) throw Web.badRequest("反馈类型仅支持 bug / suggestion / question / other");
        return "other";
    }

    static String severityOf(String raw, boolean strict) {
        if (raw == null || raw.isBlank()) return "normal";
        String value = raw.trim().toLowerCase();
        if (SEVERITY_LABELS.containsKey(value)) return value;
        if (strict) throw Web.badRequest("严重程度仅支持 low / normal / high / critical");
        return "normal";
    }

    private static String statusLabel(String code) {
        return STATUS_LABELS.getOrDefault(code, code);
    }

    private static String severityLabel(String code) {
        return SEVERITY_LABELS.getOrDefault(code, code);
    }

    private static int severityWeight(String code) {
        return switch (code) {
            case "critical" -> 3;
            case "high" -> 2;
            case "normal" -> 1;
            default -> 0;
        };
    }

    private static boolean isOpen(String status) {
        return PENDING.equals(status) || PROCESSING.equals(status);
    }

    private static boolean isConcluded(String status) {
        return RESOLVED.equals(status) || REJECTED.equals(status);
    }

    private static String normalizeTitle(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) throw Web.badRequest("反馈标题不能为空");
        if (value.length() > MAX_TITLE_LENGTH) {
            throw Web.badRequest("反馈标题不能超过 " + MAX_TITLE_LENGTH + " 个字符");
        }
        return value;
    }

    private static String normalizeDescription(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) throw Web.badRequest("请填写问题描述");
        if (value.length() > MAX_DESCRIPTION_LENGTH) {
            throw Web.badRequest("问题描述不能超过 " + MAX_DESCRIPTION_LENGTH + " 个字符");
        }
        return value;
    }

    private static String normalizeComment(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) throw Web.badRequest("评论内容不能为空");
        if (value.length() > MAX_COMMENT_LENGTH) {
            throw Web.badRequest("评论内容不能超过 " + MAX_COMMENT_LENGTH + " 个字符");
        }
        return value;
    }

    private static List<Map<String, Object>> normalizeAssignees(List<Map<String, String>> requested) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (requested != null) {
            for (Map<String, String> item : requested) {
                String id = trim(item.get("id"));
                if (id.isEmpty() || !seen.add(id)) continue;
                String name = trim(item.get("name"));
                Map<String, Object> assignee = FeedbackStore.row();
                assignee.put("id", id);
                assignee.put("name", name.isEmpty() ? id : truncate(name, 64));
                result.add(assignee);
                if (result.size() >= MAX_ASSIGNEE_COUNT) break;
            }
        }
        return result;
    }

    private static long num(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static Long longOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty() || "null".equals(raw)) return null;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) return bool;
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (raw instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> map) result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private static List<Map<String, Object>> assignees(Map<String, Object> doc) {
        return listOfMaps(doc.get("assignees"));
    }

    private static List<String> assigneeIds(List<Map<String, Object>> assignees) {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> item : assignees) ids.add(str(item.get("id")));
        return ids;
    }

    private static void setMutableList(Map<String, Object> doc, String key, List<Map<String, Object>> value) {
        doc.put(key, new ArrayList<>(value));
    }

    private static List<String> stringList(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw instanceof List<?> items) {
            for (Object item : items) {
                String value = item == null ? "" : String.valueOf(item).trim();
                if (!value.isEmpty() && !result.contains(value)) result.add(value);
            }
        }
        return result;
    }

    private static List<String> cleanList(Object raw, int limit) {
        List<String> values = stringList(raw);
        return values.size() > limit ? new ArrayList<>(values.subList(0, limit)) : values;
    }

    private static String formatTime(long millis) {
        if (millis <= 0) return "";
        return DATE_TIME.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()));
    }

    private static String nextId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String safeName(String value) {
        String name = trim(value);
        if (name.isEmpty()) return "image";
        return truncate(name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_"), 80);
    }

    private static String imageExtension(byte[] data) {
        if (data.length >= 8 && (data[0] & 0xff) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return ".png";
        }
        if (data.length >= 3 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8) return ".jpg";
        if (data.length >= 4 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8') return ".gif";
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return ".webp";
        }
        return null;
    }

    private static String contentType(String file) {
        String lower = file == null ? "" : file.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}
