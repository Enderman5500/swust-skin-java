package cn.edu.swust.mc.skin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Main {
    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "img-src 'self' data: blob:",
            "style-src 'self' 'unsafe-inline'",
            "script-src 'self'",
            "connect-src 'self'",
            "font-src 'self' data:",
            "media-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");
    private static final Pattern PLUGIN_ASSET =
            Pattern.compile("^/plugin-assets/([a-z][a-z0-9-]{2,31})/(.+)$");

    public static void main(String[] args) throws Exception {
        Config config = new Config();
        Log.info("正在启动 SWUSTMC Skin（Java）…");
        Database db = new Database(config);
        db.bootstrap();

        App app = new App(config, db);
        app.yggdrasil = new Yggdrasil(app);
        app.plugins = new Plugins(app);
        app.plugins.init();

        HomeRoutes.register(app);
        AuthRoutes.register(app);
        SkinRoutes.register(app);
        AdminRoutes.register(app);
        ApiKeyRoutes.register(app);
        PluginRoutes.register(app);
        YggdrasilRoutes.register(app);

        HttpServer server = HttpServer.create(new InetSocketAddress(config.host, config.port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", exchange -> handle(app, exchange));
        server.start();

        Log.info("SWUSTMC Skin 已启动：" + config.baseUrl);
        Log.info("Yggdrasil / authlib-injector：" + config.baseUrl + "/api/yggdrasil");
        Log.info("数据目录：" + config.dataDir);
        Log.info("Java 版本：" + System.getProperty("java.version"));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.info("正在关闭服务…");
            server.stop(1);
            db.close();
        }));
    }

    private static void handle(App app, HttpExchange exchange) {
        try {
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
            exchange.getResponseHeaders().set("Referrer-Policy", "strict-origin-when-cross-origin");
            exchange.getResponseHeaders().set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            exchange.getResponseHeaders().set("Content-Security-Policy", CSP);

            String path = exchange.getRequestURI().getPath();
            Map<String, String> query = Web.parseQuery(exchange.getRequestURI());
            Web.Ctx ctx = new Web.Ctx(app, exchange, path, query);

            if ("OPTIONS".equals(ctx.method)) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS");
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String apiToken = extractApiToken(exchange);
            if (apiToken != null && (path.startsWith("/api/") || path.startsWith("/textures/"))) {
                ApiKeys.AuthResult key = ApiKeys.authenticate(app.db, apiToken);
                if (key == null) throw Web.unauthorized("API 密钥无效或已过期");
                if (!ApiKeys.allowedPath(ctx.method, path)) {
                    throw Web.forbidden("该 API 密钥不能访问此接口");
                }
                ctx.apiKey = key;
                ctx.user = key.user();
                ctx.session = null;
                ctx.token = null;
            } else {
                String cookie = exchange.getRequestHeaders().getFirst("Cookie");
                Map<String, String> cookies = Web.parseCookies(cookie);
                String token = cookies.get(app.config.sessionCookie);
                ctx.token = token;
                ctx.session = Auth.loadSession(app.db, token);
                ctx.user = ctx.session == null ? null : ctx.session.user;
            }

            boolean mutation = !"GET".equals(ctx.method) && !"HEAD".equals(ctx.method);
            if (ctx.apiKey == null && ctx.user != null && mutation && path.startsWith("/api/")
                    && !path.startsWith("/api/yggdrasil")
                    && !path.equals("/api/auth/login") && !path.equals("/api/auth/register")) {
                Auth.assertCsrf(ctx);
            }

            Matcher asset = PLUGIN_ASSET.matcher(path);
            if (asset.matches()) {
                if (app.plugins.serveAsset(ctx, asset.group(1), asset.group(2))) return;
                throw Web.notFound("插件资源不存在");
            }

            Web.RouteMatch matched = app.router.match(ctx.method, path);
            if (matched != null) {
                ctx.params = matched.params();
                matched.route().handler.handle(ctx);
                return;
            }

            if (app.plugins.handle(ctx)) return;

            if ("GET".equals(ctx.method) || "HEAD".equals(ctx.method)) {
                String relative = path.replaceFirst("^/+", "");
                if (relative.isEmpty()) relative = "index.html";
                Path file = Web.resolveInside(app.config.publicDir, relative);
                if (file != null && Files.isRegularFile(file)) {
                    if (Web.serveFile(ctx, file, "no-cache")) return;
                }
                if (!path.startsWith("/api/") && !path.startsWith("/textures/")
                        && !containsExtension(path)) {
                    Path index = app.config.publicDir.resolve("index.html");
                    if (Web.serveFile(ctx, index, "no-cache")) return;
                }
            }

            if (path.startsWith("/api/")) throw Web.notFound("接口不存在");
            byte[] body = "404 Not Found".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(404, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        } catch (Yggdrasil.YggdrasilError e) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("error", e.code);
                payload.put("errorMessage", e.getMessage());
                Json.send(exchange, e.status, payload);
            } catch (IOException ignored) {
            }
        } catch (Web.HttpError e) {
            try {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("code", e.code);
                error.put("message", e.getMessage());
                if (e.details != null) error.put("details", e.details);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("ok", false);
                payload.put("error", error);
                Json.send(exchange, e.status, payload);
            } catch (IOException ignored) {
            }
        } catch (Throwable e) {
            Log.error(e);
            try {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("code", "internal_error");
                error.put("message", "服务器内部错误");
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("ok", false);
                payload.put("error", error);
                Json.send(exchange, 500, payload);
            } catch (IOException ignored) {
            }
        } finally {
            exchange.close();
        }
    }

    private static String extractApiToken(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization != null) {
            String value = authorization.trim();
            if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
                String token = value.substring(7).trim();
                return token.isEmpty() ? null : token;
            }
        }
        String header = exchange.getRequestHeaders().getFirst("X-API-Key");
        return header == null || header.isBlank() ? null : header.trim();
    }

    private static boolean containsExtension(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash;
    }
}
