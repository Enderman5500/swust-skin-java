package cn.edu.swust.mc.skin.plugins.datamigration;

import cn.edu.swust.mc.skin.Security;
import cn.edu.swust.mc.skin.Web;
import cn.edu.swust.mc.skin.migration.YudreamMigration;
import cn.edu.swust.mc.skin.plugin.PluginContext;
import cn.edu.swust.mc.skin.plugin.PluginRequest;
import cn.edu.swust.mc.skin.plugin.SkinPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * YuDream 数据迁移插件。仅管理员可用：
 *
 * <ol>
 *   <li>POST /api/plugins/data-migration/admin/analyze 上传导出包，返回预演结果与上传 token；</li>
 *   <li>POST /api/plugins/data-migration/admin/run 使用 token 与保留账号选择执行迁移；</li>
 *   <li>GET  /api/plugins/data-migration/admin/status 返回初始密码、最近一次报告与运行状态。</li>
 * </ol>
 */
public final class DataMigrationPlugin implements SkinPlugin {
    private static final int MAX_UPLOAD = 32 * 1024 * 1024;
    private static final String TOKEN_RE = "^[A-Za-z0-9_-]{16,64}$";

    private PluginContext context;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    @Override
    public void activate(PluginContext ctx) throws Exception {
        this.context = ctx;
        Path uploadDir = ctx.dataDir().resolve("uploads").normalize();
        Files.createDirectories(uploadDir);

        ctx.registerAdminRoute("GET", "/admin/status", request -> status());
        ctx.registerAdminRoute("POST", "/admin/analyze", request -> analyze(request, uploadDir));
        ctx.registerAdminRoute("POST", "/admin/run", request -> run(request, uploadDir));
        ctx.info("数据迁移插件已就绪，上传目录：" + uploadDir);
    }

    private Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("initialPassword", YudreamMigration.DEFAULT_INITIAL_PASSWORD);
        result.put("busy", busy.get());
        result.put("lastReport", context.getData("lastReport"));
        result.put("lastRunAt", context.getData("lastRunAt"));
        result.put("hostDataDir", context.hostDataDir().toString());
        result.put("uploadLimit", MAX_UPLOAD);
        return result;
    }

    private Object analyze(PluginRequest request, Path uploadDir) throws Exception {
        if (!busy.compareAndSet(false, true)) {
            throw Web.conflict("已有迁移或分析任务正在执行，请稍后再试");
        }
        try {
            byte[] archive = request.body(MAX_UPLOAD);
            if (archive.length == 0) throw Web.badRequest("请选择 YuDream 导出包");
            String token = Security.randomToken(16);
            Path target = uploadDir.resolve(token + ".zip").normalize();
            if (!target.startsWith(uploadDir)) throw Web.badRequest("上传路径不合法");
            cleanUploads(uploadDir);
            Files.write(target, archive, StandardOpenOption.CREATE_NEW);
            try {
                Map<String, Object> result = YudreamMigration.execute(
                        target,
                        context.hostDataDir(),
                        true,
                        false,
                        YudreamMigration.DEFAULT_INITIAL_PASSWORD,
                        Map.of());
                result.put("token", token);
                context.setData("lastAnalysis", result);
                return result;
            } catch (Exception e) {
                Files.deleteIfExists(target);
                throw e;
            }
        } catch (Web.HttpError e) {
            throw e;
        } catch (Exception e) {
            throw failure(e);
        } finally {
            busy.set(false);
        }
    }

    private Object run(PluginRequest request, Path uploadDir) throws Exception {
        if (!busy.compareAndSet(false, true)) {
            throw Web.conflict("已有迁移或分析任务正在执行，请稍后再试");
        }
        try {
            Map<String, Object> body = request.json();
            String token = text(body.get("token")).trim();
            if (!token.matches(TOKEN_RE)) {
                throw Web.badRequest("分析令牌无效，请重新分析导出包");
            }
            Path source = uploadDir.resolve(token + ".zip").normalize();
            if (!source.startsWith(uploadDir) || !Files.isRegularFile(source)) {
                throw Web.badRequest("上传文件已过期，请重新分析导出包");
            }
            boolean dryRun = truthy(body.get("dryRun"));
            Map<String, String> keep = keepMap(body.get("keep"));
            Map<String, Object> result = YudreamMigration.execute(
                    source,
                    context.hostDataDir(),
                    dryRun,
                    true,
                    YudreamMigration.DEFAULT_INITIAL_PASSWORD,
                    keep);
            result.put("token", token);
            if (!dryRun) {
                context.setData("lastReport", result);
                context.setData("lastRunAt", Instant.now().toString());
                try {
                    Files.deleteIfExists(source);
                } catch (IOException e) {
                    context.warn("迁移完成，但上传文件清理失败：" + e.getMessage());
                }
            }
            return result;
        } catch (Web.HttpError e) {
            throw e;
        } catch (Exception e) {
            throw failure(e);
        } finally {
            busy.set(false);
        }
    }

    private static void cleanUploads(Path uploadDir) throws IOException {
        if (!Files.isDirectory(uploadDir)) return;
        try (var stream = Files.list(uploadDir)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static Map<String, String> keepMap(Object raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String email = text(entry.getKey()).trim().toLowerCase(Locale.ROOT);
                String userId = text(entry.getValue()).trim();
                if (!email.isEmpty() && !userId.isEmpty()) result.put(email, userId);
            }
        }
        return result;
    }

    private static Web.HttpError failure(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        return new Web.HttpError(400, "migration_failed", message);
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) return bool;
        String text = text(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
