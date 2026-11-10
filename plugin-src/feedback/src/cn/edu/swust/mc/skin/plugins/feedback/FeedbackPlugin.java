package cn.edu.swust.mc.skin.plugins.feedback;

import cn.edu.swust.mc.skin.AuthUser;
import cn.edu.swust.mc.skin.Web;
import cn.edu.swust.mc.skin.plugin.PluginContext;
import cn.edu.swust.mc.skin.plugin.PluginRequest;
import cn.edu.swust.mc.skin.plugin.SkinPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 问题反馈插件入口。
 *
 * <p>宿主路由为精确匹配，所有动态参数统一通过查询串（反馈 ID）或 JSON body 传递。
 * 用户端接口只服务当前登录者本人，管理端接口由宿主按管理员身份保护。
 */
public final class FeedbackPlugin implements SkinPlugin {

    private FeedbackService service;

    @Override
    public void activate(PluginContext ctx) throws Exception {
        FeedbackStore store = new FeedbackStore(ctx.dataDir());
        service = new FeedbackService(store);
        ctx.info("数据目录：" + ctx.dataDir());

        // ---------------- 用户端 ----------------
        ctx.registerRoute("GET", "/me/feedbacks", request ->
                service.myPage(requireUser(request), query(request)));
        ctx.registerRoute("GET", "/categories", request -> {
            requireUser(request);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("records", service.userCategories());
            return result;
        });
        ctx.registerRoute("GET", "/me/stats", request ->
                service.myStats(requireUser(request)));
        ctx.registerRoute("GET", "/me/feedback", request ->
                service.myFeedback(requireUser(request), feedbackId(request)));
        ctx.registerRoute("POST", "/me/feedbacks", request ->
                service.submit(requireUser(request), request.json()));
        ctx.registerRoute("PUT", "/me/feedback", request ->
                service.update(requireUser(request), feedbackId(request), request.json()));
        ctx.registerRoute("POST", "/me/comment", request -> {
            Map<String, Object> body = request.json();
            return service.comment(requireUser(request), feedbackId(request), text(body.get("content")));
        });
        ctx.registerRoute("POST", "/me/archive", request ->
                service.archive(requireUser(request), feedbackId(request)));
        ctx.registerRoute("DELETE", "/me/feedback", request -> {
            service.withdraw(requireUser(request), feedbackId(request));
            return Map.of("deleted", true);
        });
        ctx.registerRoute("POST", "/attachment", request -> {
            requireUser(request);
            return service.uploadAttachment(
                    request.body((int) FeedbackService.MAX_ATTACHMENT_BYTES), request.query("name"));
        });
        ctx.registerRoute("GET", "/attachment", request -> {
            requireUser(request);
            return service.attachment(request.query("file"));
        });

        // ---------------- 管理端 ----------------
        ctx.registerAdminRoute("GET", "/admin/feedbacks", request -> service.adminPage(query(request)));
        ctx.registerAdminRoute("GET", "/admin/stats", request -> service.adminStats());
        ctx.registerAdminRoute("GET", "/admin/categories", request -> service.adminCategories());
        ctx.registerAdminRoute("POST", "/admin/category/save", request ->
                service.saveCategory(request.json()));
        ctx.registerAdminRoute("POST", "/admin/category/delete", request -> {
            Map<String, Object> body = request.json();
            return service.deleteCategory(required(text(body.get("id")), "id"));
        });
        ctx.registerAdminRoute("GET", "/admin/feedback", request ->
                service.adminFeedback(feedbackId(request)));
        ctx.registerAdminRoute("POST", "/admin/reply", request -> service.adminReply(
                operator(request), feedbackId(request), text(request.json().get("content"))));
        ctx.registerAdminRoute("POST", "/admin/note", request -> service.adminNote(
                operator(request), feedbackId(request), text(request.json().get("content"))));
        ctx.registerAdminRoute("PUT", "/admin/status", request -> {
            Map<String, Object> body = request.json();
            return service.adminStatus(operator(request), feedbackId(request),
                    text(body.get("status")), text(body.get("note")));
        });
        ctx.registerAdminRoute("POST", "/admin/archive", request ->
                service.adminArchive(operator(request), feedbackId(request)));
        ctx.registerAdminRoute("PUT", "/admin/assignee", request ->
                service.adminAssign(operator(request), feedbackId(request),
                        assigneeInput(request.json())));
        ctx.registerAdminRoute("PUT", "/admin/severity", request -> service.adminSeverity(
                operator(request), feedbackId(request), text(request.json().get("severity"))));
        ctx.registerAdminRoute("DELETE", "/admin/feedback", request -> {
            service.adminDelete(feedbackId(request));
            return Map.of("deleted", true);
        });

        ctx.info("问题反馈插件已就绪");
    }

    private static AuthUser requireUser(PluginRequest request) {
        AuthUser user = request.user();
        if (user == null) throw Web.unauthorized("请先登录");
        return user;
    }

    private static AuthUser operator(PluginRequest request) {
        AuthUser user = request.user();
        if (user == null) throw Web.unauthorized("请先登录");
        if (!user.admin) throw Web.forbidden("没有权限执行此操作");
        return user;
    }

    private static String feedbackId(PluginRequest request) {
        return required(request.query("id"), "id");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw Web.badRequest("缺少参数：" + name);
        return value.trim();
    }

    private static FeedbackService.Query query(PluginRequest request) {
        return new FeedbackService.Query(
                text(request.query("keyword")),
                filterStatus(request.query("status")),
                filterType(request.query("type")),
                filterSeverity(request.query("severity")),
                text(request.query("categoryId")),
                text(request.query("assigneeId")),
                bool(request.query("unassigned")),
                bool(request.query("open")),
                text(request.query("authorId")),
                time(request.query("from")),
                time(request.query("to")),
                number(request.query("page"), 1),
                number(request.query("size"), 10));
    }

    private static List<Map<String, String>> assigneeInput(Map<String, Object> body) {
        List<Map<String, String>> result = new ArrayList<>();
        Object raw = body.get("assignees");
        if (raw instanceof List<?> items) {
            for (Object item : items) {
                Map<String, String> entry = new LinkedHashMap<>();
                if (item instanceof Map<?, ?> map) {
                    entry.put("id", text(map.get("id")));
                    entry.put("name", text(map.get("name")));
                } else if (item != null) {
                    entry.put("id", text(item));
                    entry.put("name", text(item));
                }
                result.add(entry);
            }
        }
        if (result.isEmpty() && body.get("assigneeIds") instanceof List<?> ids) {
            for (Object id : ids) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("id", text(id));
                entry.put("name", text(id));
                result.add(entry);
            }
        }
        return result;
    }

    private static String filterStatus(String raw) {
        String value = filterValue(raw);
        if (value.isEmpty()) return "";
        return switch (value) {
            case "pending", "processing", "resolved", "rejected", "archived" -> value;
            case "closed" -> "archived";
            default -> "";
        };
    }

    private static String filterType(String raw) {
        String value = filterValue(raw);
        return switch (value) {
            case "bug", "suggestion", "question", "other" -> value;
            default -> "";
        };
    }

    private static String filterSeverity(String raw) {
        String value = filterValue(raw);
        return switch (value) {
            case "low", "normal", "high", "critical" -> value;
            default -> "";
        };
    }

    private static String filterValue(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return "all".equals(value) ? "" : value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean bool(String raw) {
        if (raw == null) return false;
        String value = raw.trim();
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static Long time(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int number(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
