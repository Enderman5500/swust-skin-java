package cn.edu.swust.mc.skin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Page {
    private Page() {
    }

    public static Map<String, Object> of(Object items, int total, int page, int perPage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", perPage);
        result.put("perPage", perPage);
        result.put("pages", Math.max(1, (total + perPage - 1) / perPage));
        return result;
    }
}
