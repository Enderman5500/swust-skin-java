package cn.edu.swust.mc.report;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class ReportClient {
    private final ReportConfig config;
    private final HttpClient client;

    public ReportClient(ReportConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.httpTimeoutSeconds))
                .build();
    }

    public boolean send(List<PlayerTime> players, int maxPlayers, String version, String loader, long sequence) {
        if (!config.valid()) {
            if (config.debug) {
                System.out.println("[minecraft-server-report] server-id 或 api-key 未配置，跳过上报");
            }
            return false;
        }
        String payload = json(players, maxPlayers, version, loader, sequence);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl + "/api/plugins/minecraft-server/report"))
                .timeout(Duration.ofSeconds(config.httpTimeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            if (!ok || config.debug) {
                System.out.println("[minecraft-server-report] HTTP " + response.statusCode() + " " + response.body());
            }
            return ok;
        } catch (Exception e) {
            if (config.debug) {
                System.out.println("[minecraft-server-report] 上报失败：" + e.getMessage());
            }
            return false;
        }
    }

    private String json(List<PlayerTime> players, int maxPlayers, String version, String loader, long sequence) {
        StringBuilder json = new StringBuilder(512);
        json.append('{')
                .append("\"serverId\":\"").append(escape(config.serverId)).append("\",")
                .append("\"observedAt\":\"").append(Instant.now()).append("\",")
                .append("\"sequence\":").append(sequence).append(',')
                .append("\"meta\":{")
                .append("\"version\":\"").append(escape(version)).append("\",")
                .append("\"loader\":\"").append(escape(loader)).append("\",")
                .append("\"maxPlayers\":").append(Math.max(0, maxPlayers))
                .append("},")
                .append("\"online\":[");
        boolean first = true;
        for (PlayerTime player : players) {
            if (!player.online()) continue;
            if (!first) json.append(',');
            first = false;
            json.append('{')
                    .append("\"uuid\":\"").append(escape(player.uuid())).append("\",")
                    .append("\"name\":\"").append(escape(player.name())).append("\",")
                    .append("\"afk\":").append(player.afk())
                    .append(",\"sessionStartedAt\":");
            appendString(json, player.sessionStartedAt());
            json.append('}');
        }
        json.append("],\"players\":[");
        first = true;
        for (PlayerTime player : players) {
            if (!first) json.append(',');
            first = false;
            json.append('{')
                    .append("\"uuid\":\"").append(escape(player.uuid())).append("\",")
                    .append("\"name\":\"").append(escape(player.name())).append("\",")
                    .append("\"totalMillis\":").append(Math.max(0, player.totalMillis())).append(',')
                    .append("\"afkMillis\":").append(Math.max(0, player.afkMillis())).append(',')
                    .append("\"lastSeenAt\":");
            appendString(json, player.lastSeenAt());
            json.append('}');
        }
        json.append("]}");
        return json.toString();
    }

    private static void appendString(StringBuilder json, String value) {
        if (value == null || value.isBlank()) json.append("null");
        else json.append('"').append(escape(value)).append('"');
    }

    private static String escape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
