package cn.edu.swust.mc.skin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private Json() {
    }

    public static String stringify(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(byte[] body) {
        if (body == null || body.length == 0) return Map.of();
        try {
            Object value = MAPPER.readValue(body, Object.class);
            if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
            throw new IllegalArgumentException("JSON 必须是对象");
        } catch (Exception e) {
            throw new IllegalArgumentException("请求内容不是有效的 JSON");
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(byte[] body) {
        try {
            Object value = MAPPER.readValue(body, Object.class);
            if (value instanceof List<?> list) return (List<Object>) list;
            throw new IllegalArgumentException("JSON 必须是数组");
        } catch (Exception e) {
            throw new IllegalArgumentException("请求内容不是有效的 JSON 数组");
        }
    }

    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    public static void send(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] body = MAPPER.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        boolean head = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
        exchange.sendResponseHeaders(status, head ? -1 : body.length);
        if (!head) {
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }
    }
}
