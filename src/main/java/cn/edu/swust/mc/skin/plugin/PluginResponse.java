package cn.edu.swust.mc.skin.plugin;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插件路由返回该对象时，宿主直接输出二进制内容而不是统一 JSON 包装。
 * 用于证明文件、导出 ZIP 等下载场景。
 */
public record PluginResponse(int status, String contentType, byte[] body, Map<String, String> headers) {

    public static PluginResponse download(String filename, String contentType, byte[] body) {
        Map<String, String> values = new LinkedHashMap<>();
        String display = filename == null || filename.isBlank() ? "download" : filename;
        String safeDisplay = display.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        String asciiFallback = safeDisplay.replaceAll("[^\\x20-\\x7E]", "_");
        String encoded = URLEncoder.encode(display, StandardCharsets.UTF_8).replace("+", "%20");
        values.put("Content-Disposition", "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded);
        return new PluginResponse(200, contentType, body, values);
    }

    public static PluginResponse inline(String contentType, byte[] body) {
        return new PluginResponse(200, contentType, body, Map.of());
    }
}
