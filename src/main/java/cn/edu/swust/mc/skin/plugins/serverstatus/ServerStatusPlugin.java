package cn.edu.swust.mc.skin.plugins.serverstatus;

import cn.edu.swust.mc.skin.Security;
import cn.edu.swust.mc.skin.Web;
import cn.edu.swust.mc.skin.plugin.PluginContext;
import cn.edu.swust.mc.skin.plugin.PluginRequest;
import cn.edu.swust.mc.skin.plugin.SkinPlugin;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ServerStatusPlugin implements SkinPlugin {
    @Override
    public void activate(PluginContext ctx) {
        ctx.on("texture.uploaded", payload -> {
            Object current = ctx.getData("uploads");
            int count = current instanceof Number number ? number.intValue() + 1 : 1;
            ctx.setData("uploads", count);
            Object texture = payload.get("texture");
            Map<String, Object> last = new LinkedHashMap<>();
            if (texture instanceof Map<?, ?> map) {
                last.put("name", map.get("name") == null ? "" : String.valueOf(map.get("name")));
            } else {
                last.put("name", "");
            }
            last.put("at", Security.isoNow());
            ctx.setData("lastUpload", last);
        });

        ctx.registerRoute("GET", "/status", request -> {
            Map<String, Object> config = ctx.getConfig();
            List<Map<String, Object>> servers = normalize(config.get("servers"), false);
            List<Map<String, Object>> results = pingAll(servers);
            long online = results.stream().filter(item -> Boolean.TRUE.equals(item.get("online"))).count();
            Object uploads = ctx.getData("uploads");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("servers", results);
            result.put("online", online);
            result.put("uploadedTextures", uploads instanceof Number number ? number.intValue() : 0);
            result.put("lastUpload", ctx.getData("lastUpload"));
            result.put("checkedAt", Security.isoNow());
            return result;
        });

        ctx.registerAdminRoute("POST", "/config", request -> {
            Map<String, Object> body = request.json();
            List<Map<String, Object>> servers = normalize(body.get("servers"), true);
            ctx.setConfig(Map.of("servers", servers));
            return Map.of("servers", servers);
        });
    }

    private static List<Map<String, Object>> normalize(Object input, boolean strict) {
        List<Map<String, Object>> servers = new ArrayList<>();
        if (!(input instanceof List<?> list)) {
            if (strict) throw new Web.HttpError(400, "bad_request", "服务器列表格式不正确");
            return servers;
        }
        for (Object raw : list.subList(0, Math.min(list.size(), 8))) {
            if (!(raw instanceof Map<?, ?> item)) continue;
            String name = truncate(String.valueOf(item.get("name") == null ? "" : item.get("name")), 32);
            String host = truncate(String.valueOf(item.get("host") == null ? "" : item.get("host")), 128);
            int port;
            try {
                port = Integer.parseInt(String.valueOf(item.get("port") == null ? "0" : item.get("port")));
            } catch (NumberFormatException e) {
                port = 0;
            }
            if (name.isBlank() || host.isBlank() || port < 1 || port > 65535) {
                if (strict) throw new Web.HttpError(400, "bad_request", "每台服务器都需要名称、地址和 1–65535 端口");
                continue;
            }
            Map<String, Object> server = new LinkedHashMap<>();
            server.put("name", name);
            server.put("host", host);
            server.put("port", port);
            servers.add(server);
        }
        if (strict && servers.isEmpty()) throw new Web.HttpError(400, "bad_request", "请至少保留一台服务器");
        return servers;
    }

    private static List<Map<String, Object>> pingAll(List<Map<String, Object>> servers) {
        if (servers.isEmpty()) return List.of();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Map<String, Object>>> futures = servers.stream()
                    .map(server -> CompletableFuture.supplyAsync(() -> ping(server), pool))
                    .toList();
            return futures.stream().map(CompletableFuture::join).toList();
        }
    }

    private static Map<String, Object> ping(Map<String, Object> server) {
        long started = System.currentTimeMillis();
        boolean online = false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(String.valueOf(server.get("host")),
                    ((Number) server.get("port")).intValue()), 1800);
            online = true;
        } catch (Exception ignored) {
        }
        Map<String, Object> result = new LinkedHashMap<>(server);
        result.put("online", online);
        result.put("latency", online ? System.currentTimeMillis() - started : null);
        return result;
    }

    private static String truncate(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
