package cn.edu.swust.mc.skin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Web {
    private Web() {
    }

    public static final class HttpError extends RuntimeException {
        public final int status;
        public final String code;
        public final Object details;

        public HttpError(int status, String code, String message) {
            this(status, code, message, null);
        }

        public HttpError(int status, String code, String message, Object details) {
            super(message);
            this.status = status;
            this.code = code;
            this.details = details;
        }
    }

    public static HttpError badRequest(String message) {
        return new HttpError(400, "bad_request", message);
    }

    public static HttpError unauthorized(String message) {
        return new HttpError(401, "unauthorized", message);
    }

    public static HttpError forbidden(String message) {
        return new HttpError(403, "forbidden", message);
    }

    public static HttpError notFound(String message) {
        return new HttpError(404, "not_found", message);
    }

    public static HttpError conflict(String message) {
        return new HttpError(409, "conflict", message);
    }

    public static HttpError tooManyRequests(String message) {
        return new HttpError(429, "too_many_requests", message);
    }

    public interface Handler {
        void handle(Ctx ctx) throws Exception;
    }

    public static final class Route {
        final String method;
        final String pattern;
        final Pattern regex;
        final List<String> keys;
        final Handler handler;

        Route(String method, String pattern, Handler handler) {
            this.method = method;
            this.pattern = pattern;
            this.handler = handler;
            List<String> names = new ArrayList<>();
            StringBuilder source = new StringBuilder("^/");
            String normalized = pattern.startsWith("/") ? pattern : "/" + pattern;
            String[] segments = normalized.split("/");
            boolean first = true;
            for (String segment : segments) {
                if (segment.isEmpty()) continue;
                if (!first) source.append("/");
                first = false;
                if ("*".equals(segment)) {
                    names.add("rest");
                    source.append("(.*)");
                } else if (segment.startsWith(":")) {
                    names.add(segment.substring(1));
                    source.append("([^/]+)");
                } else {
                    source.append(Pattern.quote(segment));
                }
            }
            source.append("/?$");
            this.keys = names;
            this.regex = Pattern.compile(source.toString());
        }
    }

    public record RouteMatch(Route route, Map<String, String> params) {
    }

    public static final class Router {
        private final List<Route> routes = new ArrayList<>();

        public Router add(String method, String pattern, Handler handler) {
            routes.add(new Route(method.toUpperCase(), pattern, handler));
            return this;
        }

        public Router get(String pattern, Handler handler) {
            return add("GET", pattern, handler);
        }

        public Router post(String pattern, Handler handler) {
            return add("POST", pattern, handler);
        }

        public Router put(String pattern, Handler handler) {
            return add("PUT", pattern, handler);
        }

        public Router patch(String pattern, Handler handler) {
            return add("PATCH", pattern, handler);
        }

        public Router delete(String pattern, Handler handler) {
            return add("DELETE", pattern, handler);
        }

        public RouteMatch match(String method, String path) {
            String normalizedMethod = method.toUpperCase();
            for (Route route : routes) {
                boolean methodMatches = route.method.equals(normalizedMethod)
                        || ("HEAD".equals(normalizedMethod) && route.method.equals("GET"));
                if (!methodMatches) continue;
                Matcher matcher = route.regex.matcher(path);
                if (!matcher.matches()) continue;
                Map<String, String> params = new LinkedHashMap<>();
                for (int i = 0; i < route.keys.size(); i++) {
                    params.put(route.keys.get(i), decode(matcher.group(i + 1)));
                }
                return new RouteMatch(route, params);
            }
            return null;
        }
    }

    public static final class Session {
        public String tokenHash;
        public String csrf;
        public String expiresAt;
        public AuthUser user;
    }

    public static final class Ctx {
        public final App app;
        public final HttpExchange exchange;
        public final String method;
        public final String path;
        public final Map<String, String> query;
        public Map<String, String> params = Map.of();
        public AuthUser user;
        public Session session;
        public String token;
        public ApiKeys.AuthResult apiKey;

        Ctx(App app, HttpExchange exchange, String path, Map<String, String> query) {
            this.app = app;
            this.exchange = exchange;
            this.method = exchange.getRequestMethod().toUpperCase();
            this.path = path;
            this.query = query;
        }

        public Database db() {
            return app.db;
        }

        public Config config() {
            return app.config;
        }

        public String param(String name) {
            return params.getOrDefault(name, "");
        }

        public String query(String name) {
            return query.get(name);
        }

        public String query(String name, String fallback) {
            return query.getOrDefault(name, fallback);
        }

        public byte[] body(int limit) throws IOException {
            String declared = exchange.getRequestHeaders().getFirst("Content-Length");
            if (declared != null) {
                try {
                    if (Long.parseLong(declared) > limit) {
                        throw new HttpError(413, "payload_too_large", "上传内容过大");
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            try (InputStream input = exchange.getRequestBody()) {
                byte[] data = input.readNBytes(limit + 1);
                if (data.length > limit) throw new HttpError(413, "payload_too_large", "上传内容过大");
                return data;
            }
        }

        public Object jsonAny(int limit) throws IOException {
            byte[] body = body(limit);
            if (body.length == 0) return Map.of();
            try {
                return Json.MAPPER.readValue(body, new TypeReference<Object>() {
                });
            } catch (Exception e) {
                throw new HttpError(400, "invalid_json", "请求内容不是有效的 JSON");
            }
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> json() throws IOException {
            return json(1024 * 1024);
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> json(int limit) throws IOException {
            Object value = jsonAny(limit);
            if (!(value instanceof Map<?, ?> map)) {
                throw new HttpError(400, "invalid_json", "请求内容不是有效的 JSON");
            }
            return (Map<String, Object>) map;
        }

        public void ok(Object data) throws IOException {
            ok(data, 200);
        }

        public void ok(Object data, int status) throws IOException {
            ok(data, status, Map.of());
        }

        public void ok(Object data, int status, Map<String, String> headers) throws IOException {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", true);
            payload.put("data", data);
            sendJson(status, payload, headers);
        }

        public void sendJson(int status, Object payload) throws IOException {
            sendJson(status, payload, Map.of());
        }

        public void sendJson(int status, Object payload, Map<String, String> headers) throws IOException {
            byte[] body = Json.MAPPER.writeValueAsBytes(payload);
            Headers response = exchange.getResponseHeaders();
            response.set("Content-Type", "application/json; charset=utf-8");
            response.set("Cache-Control", "no-store");
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                response.set(entry.getKey(), entry.getValue());
            }
            boolean head = "HEAD".equals(method);
            exchange.sendResponseHeaders(status, head ? -1 : body.length);
            if (!head) {
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            } else {
                exchange.close();
            }
        }

        public void noContent() throws IOException {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        }

        public void sendBytes(int status, byte[] data, String contentType, String cacheControl,
                              Map<String, String> headers) throws IOException {
            Headers response = exchange.getResponseHeaders();
            response.set("Content-Type", contentType);
            if (cacheControl != null) response.set("Cache-Control", cacheControl);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                response.set(entry.getKey(), entry.getValue());
            }
            boolean head = "HEAD".equals(method);
            exchange.sendResponseHeaders(status, head ? -1 : data.length);
            if (!head) {
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(data);
                }
            } else {
                exchange.close();
            }
        }
    }

    public static Map<String, String> parseQuery(URI uri) {
        Map<String, String> query = new LinkedHashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) return query;
        for (String part : raw.split("&")) {
            int index = part.indexOf('=');
            String key = index < 0 ? part : part.substring(0, index);
            String value = index < 0 ? "" : part.substring(index + 1);
            query.put(decode(key), decode(value));
        }
        return query;
    }

    public static Map<String, String> parseCookies(String header) {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (header == null || header.isEmpty()) return cookies;
        for (String part : header.split(";")) {
            int index = part.indexOf('=');
            if (index < 0) continue;
            String name = part.substring(0, index).trim();
            String value = part.substring(index + 1).trim();
            if (name.isEmpty()) continue;
            cookies.put(name, decode(value));
        }
        return cookies;
    }

    public static String cookie(String name, String value, long maxAgeSeconds, boolean secure) {
        return name + "=" + URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                + "; Max-Age=" + maxAgeSeconds + "; Path=/; HttpOnly"
                + (secure ? "; Secure" : "") + "; SameSite=Lax";
    }

    public static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    public static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String clientIp(HttpExchange exchange, boolean trustProxy) {
        Headers headers = exchange.getRequestHeaders();
        if (trustProxy) {
            String forwarded = headers.getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String real = headers.getFirst("X-Real-IP");
            if (real != null && !real.isBlank()) return real.trim();
        }
        return exchange.getRemoteAddress() == null ? "" : exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    public static final class RateLimiter {
        private final Map<String, long[]> buckets = new LinkedHashMap<>();

        public synchronized void hit(String key, int max, long windowMs) {
            hit(key, max, windowMs, "操作过于频繁，请稍后再试");
        }

        public synchronized void hit(String key, int max, long windowMs, String message) {
            long now = System.currentTimeMillis();
            long[] bucket = buckets.get(key);
            if (bucket == null || bucket[1] <= now) {
                bucket = new long[]{0, now + windowMs};
                buckets.put(key, bucket);
            }
            bucket[0]++;
            if (bucket[0] > max) throw tooManyRequests(message);
            if (buckets.size() > 5000) {
                buckets.entrySet().removeIf(entry -> entry.getValue()[1] <= now);
            }
        }

        public synchronized void reset(String key) {
            buckets.remove(key);
        }
    }

    public static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        int index = name.lastIndexOf('.');
        String ext = index < 0 ? "" : name.substring(index);
        return switch (ext) {
            case ".html" -> "text/html; charset=utf-8";
            case ".css" -> "text/css; charset=utf-8";
            case ".js", ".mjs" -> "text/javascript; charset=utf-8";
            case ".json", ".map" -> "application/json; charset=utf-8";
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".svg" -> "image/svg+xml";
            case ".webp" -> "image/webp";
            case ".ico" -> "image/x-icon";
            case ".txt" -> "text/plain; charset=utf-8";
            case ".woff2" -> "font/woff2";
            default -> "application/octet-stream";
        };
    }

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US)
                    .withZone(ZoneOffset.UTC);

    public static boolean serveFile(Ctx ctx, Path file, String cacheControl) throws IOException {
        if (file == null || !Files.isRegularFile(file)) return false;
        long size = Files.size(file);
        Instant modified = Files.getLastModifiedTime(file).toInstant();
        String etag = "W/\"" + Long.toHexString(size) + "-" + Long.toHexString(modified.toEpochMilli()) + "\"";
        String ifNoneMatch = ctx.exchange.getRequestHeaders().getFirst("If-None-Match");
        if (etag.equals(ifNoneMatch)) {
            ctx.exchange.getResponseHeaders().set("ETag", etag);
            ctx.exchange.getResponseHeaders().set("Cache-Control", cacheControl);
            ctx.exchange.sendResponseHeaders(304, -1);
            ctx.exchange.close();
            return true;
        }
        byte[] data = Files.readAllBytes(file);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("ETag", etag);
        headers.put("Last-Modified", HTTP_DATE.format(modified));
        ctx.sendBytes(200, data, contentType(file), cacheControl, headers);
        return true;
    }

    public static Path resolveInside(Path base, String relative) {
        try {
            String clean = relative.replace('\\', '/');
            if (clean.contains("\0")) return null;
            Path target = base.resolve(clean).normalize();
            if (!target.startsWith(base.normalize())) return null;
            return target;
        } catch (Exception e) {
            return null;
        }
    }

    public static String base64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }
}
