package cn.edu.swust.mc.skin.plugins.mcwiki;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mojang 官方接口：版本清单、版本详情与资源下载。 */
final class MojangClient {
    private static final URI MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private final HttpClient http;
    private final ObjectMapper mapper;

    MojangClient(HttpClient http, ObjectMapper mapper) {
        this.http = http;
        this.mapper = mapper;
    }

    List<Map<String, Object>> manifest() {
        try {
            JsonNode root = mapper.readTree(get(MANIFEST));
            String latest = root.path("latest").path("release").asText("");
            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode node : root.path("versions")) {
                String id = node.path("id").asText();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("type", node.path("type").asText());
                row.put("releaseTime", node.path("releaseTime").asText(null));
                row.put("url", node.path("url").asText(null));
                row.put("latest", latest.equals(id));
                result.add(row);
            }
            return result;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("无法获取 Mojang 版本清单", e);
        }
    }

    byte[] download(String url) {
        try {
            return get(URI.create(url));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("无法下载 Minecraft 资源", e);
        }
    }

    JsonNode versionDetails(String url) {
        try {
            return mapper.readTree(get(URI.create(url)));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("无法获取 Minecraft 版本详情", e);
        }
    }

    private byte[] get(URI uri) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = http.send(
                HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(10))
                        .header("User-Agent", "swust-skin-mc-wiki/1.0").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + "：" + uri.getHost());
        }
        return response.body();
    }
}
