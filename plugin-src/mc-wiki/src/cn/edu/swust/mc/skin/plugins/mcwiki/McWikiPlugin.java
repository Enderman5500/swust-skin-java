package cn.edu.swust.mc.skin.plugins.mcwiki;

import cn.edu.swust.mc.skin.Web;
import cn.edu.swust.mc.skin.plugin.PluginContext;
import cn.edu.swust.mc.skin.plugin.PluginRequest;
import cn.edu.swust.mc.skin.plugin.SkinPlugin;

import java.net.URL;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft 百科插件入口。
 * 宿主插件路由是精确匹配，所有动态参数都通过查询串传递（?version=&id=&afterSeq=）。
 */
public final class McWikiPlugin implements SkinPlugin {
    private static volatile PluginContext context;
    private WikiStore store;
    private MojangClient mojang;
    private WikiCatalog catalog;
    private RenderAssets renders;
    private WikiQuery query;
    private JobService jobs;
    private HttpClient http;

    @Override
    public void activate(PluginContext ctx) throws Exception {
        context = ctx;
        init(ctx);
    }

    @Override
    public void deactivate() {
        if (jobs != null) jobs.shutdown();
        if (http != null) http.close();
        jobs = null;
        http = null;
    }

    private void init(PluginContext ctx) throws Exception {
        Path pluginDir = pluginDir();
        Path dataDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
                .resolve("data").resolve("plugin-data").resolve("mc-wiki");
        Path assetsDir = pluginDir == null ? null : pluginDir.resolve("assets");
        store = new WikiStore(dataDir, assetsDir);
        int cleaned = store.cleanupUntranslatedItems();
        if (cleaned > 0) ctx.info("已清理 " + cleaned + " 个无英文名的历史占位物品");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        mojang = new MojangClient(http, WikiStore.MAPPER);
        catalog = new WikiCatalog(store);
        renders = new RenderAssets(store, mojang);
        query = new WikiQuery(store, catalog, renders, mojang);
        WikiImporter importer = new WikiImporter(store, mojang, catalog);
        jobs = new JobService(store, (version, control) -> {
            if (RenderAssets.JOB_VERSION.equals(version)) renders.update(control);
            else importer.run(version, control);
        });

        ctx.info("数据目录：" + dataDir);
        if (pluginDir == null) {
            ctx.warn("无法定位插件目录，渲染资产功能不可用");
        }

        // 公开百科
        ctx.registerRoute("GET", "/meta", request -> query.meta(request.query("version")));
        ctx.registerRoute("GET", "/items", request -> query.items(
                request.query("version"), request.query("keyword"), request.query("kind"),
                number(request, "page", 1), number(request, "size", 24)));
        ctx.registerRoute("GET", "/item", request -> query.itemDetail(
                request.query("version"), required(request.query("id"), "id")));
        ctx.registerRoute("GET", "/versions", request -> query.importedVersions());
        ctx.registerRoute("GET", "/compare", request -> query.compare(
                request.query("from"), request.query("to")));
        ctx.registerRoute("GET", "/tag", request -> query.tag(
                request.query("version"), required(request.query("id"), "id")));
        ctx.registerRoute("GET", "/recipes", request -> query.recipes(
                request.query("version"), request.query("keyword"),
                number(request, "page", 1), number(request, "size", 12)));

        // 后台管理
        ctx.registerAdminRoute("GET", "/admin/compare", request -> query.compare(
                request.query("from"), request.query("to")));
        ctx.registerAdminRoute("GET", "/admin/state", request -> query.adminState(
                request.query("keyword"), request.query("type"), request.query("status"),
                number(request, "page", 1), number(request, "size", 20)));
        ctx.registerAdminRoute("POST", "/admin/refresh", request -> {
            int count = query.refreshVersions();
            return Map.of("count", count, "message", "已刷新 " + count + " 个版本");
        });
        ctx.registerAdminRoute("POST", "/admin/import", request -> {
            String version = required(text(request.json().get("version")), "version");
            JobService.Job job = jobs.create(version);
            return Map.of("jobId", job.jobId(), "job", job);
        });
        ctx.registerAdminRoute("POST", "/admin/publish", request -> {
            String version = required(text(request.json().get("version")), "version");
            if (store.importRecord(version) == null) throw Web.badRequest("请先导入该版本再发布");
            query.publish(version);
            return Map.of("published", true, "version", version);
        });
        ctx.registerAdminRoute("POST", "/admin/unpublish", request -> {
            String version = required(text(request.json().get("version")), "version");
            query.unpublish(version);
            return Map.of("published", false, "version", version);
        });
        ctx.registerAdminRoute("POST", "/admin/delete", request -> {
            String version = required(text(request.json().get("version")), "version");
            return query.deleteVersionData(version);
        });
        ctx.registerAdminRoute("POST", "/admin/renders/update", request -> {
            JobService.Job job = jobs.create(RenderAssets.JOB_VERSION);
            return Map.of("jobId", job.jobId(), "job", job);
        });
        ctx.registerAdminRoute("GET", "/admin/jobs", request -> {
            List<JobService.Job> records = jobs.jobs();
            return Map.of("records", records, "total", records.size());
        });
        ctx.registerAdminRoute("GET", "/admin/job", request -> {
            String id = required(request.query("id"), "id");
            JobService.Job job = jobs.get(id);
            if (job == null) throw Web.notFound("任务不存在");
            long after = number(request, "afterSeq", 0);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("job", job);
            result.put("events", jobs.events(id, after));
            return result;
        });
        ctx.registerAdminRoute("POST", "/admin/job/pause", request ->
                jobs.pause(required(text(request.json().get("id")), "id")));
        ctx.registerAdminRoute("POST", "/admin/job/resume", request ->
                jobs.resume(required(text(request.json().get("id")), "id")));
        ctx.registerAdminRoute("POST", "/admin/job/cancel", request ->
                jobs.cancel(required(text(request.json().get("id")), "id")));
        ctx.registerAdminRoute("POST", "/admin/job/delete", request -> {
            jobs.delete(required(text(request.json().get("id")), "id"));
            return Map.of("deleted", true);
        });

        ctx.info("Minecraft 百科已就绪（已导入 " + store.imports().size() + " 个版本）");
    }

    /** 插件类从插件目录加载时，CodeSource 即插件目录，用于写入可被 /plugin-assets 托管的渲染图。 */
    private static Path pluginDir() {
        try {
            URL location = McWikiPlugin.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) return null;
            Path path = Path.of(location.toURI());
            if (Files.isDirectory(path)) return path;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw Web.badRequest("缺少参数：" + name);
        return value.trim();
    }

    private static int number(PluginRequest request, String name, int fallback) {
        try {
            String value = request.query(name);
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
