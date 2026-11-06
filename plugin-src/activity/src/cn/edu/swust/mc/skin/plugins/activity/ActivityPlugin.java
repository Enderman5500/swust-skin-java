package cn.edu.swust.mc.skin.plugins.activity;

import cn.edu.swust.mc.skin.AuthUser;
import cn.edu.swust.mc.skin.Web;
import cn.edu.swust.mc.skin.plugin.PluginContext;
import cn.edu.swust.mc.skin.plugin.PluginRequest;
import cn.edu.swust.mc.skin.plugin.PluginResponse;
import cn.edu.swust.mc.skin.plugin.SkinPlugin;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 活动插件入口。宿主路由精确匹配，所有动态参数通过查询串或 JSON body 传递。
 * 证明文件等二进制内容通过 PluginResponse 直接输出。
 */
public final class ActivityPlugin implements SkinPlugin {
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String ZIP = "application/zip";

    private ActivityStore store;
    private ActivityService service;

    @Override
    public void activate(PluginContext ctx) throws Exception {
        Path dataDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
                .resolve("data").resolve("plugin-data").resolve("activity");
        store = new ActivityStore(dataDir);
        service = new ActivityService(store);
        ctx.info("数据目录：" + dataDir);

        // ---------------- 活动广场（公开/登录） ----------------
        ctx.registerRoute("GET", "/activities", request -> service.publicActivities(
                request.user(), request.query("keyword"), text(request.query("status"))));
        ctx.registerRoute("GET", "/activity", request -> service.publicActivity(
                required(request.query("id"), "id"), request.user()));
        ctx.registerRoute("POST", "/signup", request -> service.signup(
                requireUser(request), required(text(request.json().get("activityId")), "activityId")));
        ctx.registerRoute("POST", "/cancel", request -> service.cancel(
                requireUser(request), required(text(request.json().get("activityId")), "activityId")));
        ctx.registerRoute("GET", "/me", request -> service.me(requireUser(request)));
        ctx.registerRoute("POST", "/identity", request -> service.saveIdentity(
                requireUser(request), request.json()));
        ctx.registerRoute("POST", "/my/proof", request -> {
            AuthUser user = requireUser(request);
            List<String> ids = ActivityService.stringList(request.json().get("activityIds"));
            byte[] docx = service.userProof(user, ids);
            return PluginResponse.download(service.userProofFilename(user), DOCX, docx);
        });

        // ---------------- 管理端 ----------------
        ctx.registerAdminRoute("GET", "/admin/activities", request -> service.adminActivities(
                request.query("keyword"), text(request.query("status")),
                number(request, "page", 1), number(request, "size", 20)));
        ctx.registerAdminRoute("POST", "/admin/activity/save", request ->
                service.saveActivity(request.json(), requireUser(request)));
        ctx.registerAdminRoute("POST", "/admin/activity/status", request -> service.setStatus(
                required(text(request.json().get("id")), "id"),
                required(text(request.json().get("status")), "status")));
        ctx.registerAdminRoute("POST", "/admin/activity/delete", request -> {
            service.deleteActivity(required(text(request.json().get("id")), "id"));
            return Map.of("deleted", true);
        });
        ctx.registerAdminRoute("GET", "/admin/activity", request -> service.adminActivity(
                required(request.query("id"), "id"), request.query("keyword"),
                text(request.query("verify")), number(request, "page", 1), number(request, "size", 20)));
        ctx.registerAdminRoute("POST", "/admin/verify", request -> {
            Map<String, Object> body = request.json();
            List<Map<String, Object>> updated = service.verify(
                    required(text(body.get("activityId")), "activityId"),
                    ActivityService.stringList(body.get("userIds")),
                    required(text(body.get("verify")), "verify"),
                    text(body.get("note")), requireUser(request));
            return Map.of("updated", updated, "count", updated.size());
        });
        ctx.registerAdminRoute("GET", "/admin/identities", request -> service.identities(
                request.query("keyword"), text(request.query("eligibility")),
                number(request, "page", 1), number(request, "size", 20)));
        ctx.registerAdminRoute("GET", "/admin/settings", request -> service.settings());
        ctx.registerAdminRoute("POST", "/admin/settings", request -> service.saveSettings(request.json()));
        ctx.registerAdminRoute("POST", "/admin/template", request -> service.saveTemplate(
                request.query("name"), request.body(12 * 1024 * 1024)));
        ctx.registerAdminRoute("GET", "/admin/template/download", request ->
                PluginResponse.download(service.templateName(), DOCX, service.templateBytes()));
        ctx.registerAdminRoute("GET", "/admin/export/preview", request -> service.exportPreview(
                ActivityService.stringList(request.query("activityIds")), text(request.query("mode"))));
        ctx.registerAdminRoute("POST", "/admin/export", request ->
                service.exportProofs(request.json(), requireUser(request)));
        ctx.registerAdminRoute("GET", "/admin/exports", request -> service.exports());
        ctx.registerAdminRoute("POST", "/admin/export/delete", request -> {
            service.deleteExport(required(text(request.json().get("id")), "id"));
            return Map.of("deleted", true);
        });
        ctx.registerAdminRoute("GET", "/admin/export/download", request -> {
            String id = required(request.query("id"), "id");
            String filename = service.exportFilename(id);
            String type = filename.toLowerCase(java.util.Locale.ROOT).endsWith(".docx") ? DOCX : ZIP;
            return PluginResponse.download(filename, type, service.exportFile(id));
        });
        ctx.registerAdminRoute("GET", "/admin/template/paragraphs", request -> service.templateParagraphs());
        ctx.registerAdminRoute("POST", "/admin/template/paragraphs", request -> {
            Object paragraphs = request.json().get("paragraphs");
            if (!(paragraphs instanceof List<?> list)) throw Web.badRequest("缺少段落内容");
            List<Map<String, Object>> rows = new java.util.ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((key, value) -> row.put(String.valueOf(key), value));
                rows.add(row);
            }
            return service.saveTemplateParagraphs(rows);
        });
        ctx.registerAdminRoute("GET", "/admin/fixed/candidates", request ->
                service.fixedCandidates(request.query("keyword")));
        ctx.registerAdminRoute("GET", "/admin/fixed", request -> {
            Map<String, Object> result = new LinkedHashMap<>();
            List<Map<String, Object>> rows = service.fixedParticipants();
            result.put("items", rows);
            result.put("total", rows.size());
            return result;
        });
        ctx.registerAdminRoute("POST", "/admin/fixed/save", request ->
                service.saveFixedParticipant(request.json()));
        ctx.registerAdminRoute("POST", "/admin/fixed/delete", request -> {
            service.deleteFixedParticipant(required(text(request.json().get("id")), "id"));
            return Map.of("deleted", true);
        });
        ctx.registerAdminRoute("POST", "/admin/stamped/prepare", request ->
                service.prepareStamped(request.json(), requireUser(request)));
        ctx.registerAdminRoute("POST", "/admin/stamped/upload", request ->
                service.uploadStamped(required(request.query("id"), "id"), request.query("name"),
                        request.body(20 * 1024 * 1024)));
        ctx.registerAdminRoute("GET", "/admin/stamped", request -> service.stampedList());
        ctx.registerAdminRoute("POST", "/admin/stamped/delete", request -> {
            service.deleteStamped(required(text(request.json().get("id")), "id"));
            return Map.of("deleted", true);
        });
        ctx.registerAdminRoute("GET", "/admin/stamped/download", request -> {
            String id = required(request.query("id"), "id");
            return PluginResponse.download(service.stampedFilename(id), "application/pdf", service.stampedFile(id));
        });
        ctx.registerRoute("GET", "/my/stamped", request -> service.myStamped(requireUser(request)));
        ctx.registerRoute("GET", "/my/stamped/download", request -> {
            AuthUser user = requireUser(request);
            String id = required(request.query("id"), "id");
            return PluginResponse.download(service.stampedFilename(id), "application/pdf",
                    service.myStampedFile(user, id));
        });
        ctx.registerAdminRoute("POST", "/admin/sync", request -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("available", false);
            result.put("message", "服务器自动报名需要服务器插件提供在线玩家数据，当前尚未接入；可先使用自助报名。");
            return result;
        });

        ctx.info("活动插件已就绪（活动 " + store.activities().size() + " 个）");
    }

    private static AuthUser requireUser(PluginRequest request) {
        if (request.user() == null) throw Web.unauthorized("请先登录");
        return request.user();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw Web.badRequest("缺少参数：" + name);
        return value.trim();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int number(PluginRequest request, String name, int fallback) {
        try {
            String value = request.query(name);
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
