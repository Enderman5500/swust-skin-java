package example.hello;

import cn.edu.swust.mc.skin.plugin.PluginContext;
import cn.edu.swust.mc.skin.plugin.SkinPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HelloPlugin implements SkinPlugin {
    @Override
    public void activate(PluginContext ctx) {
        ctx.registerRoute("GET", "/hello", request -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("message", "hello from a Java JAR plugin");
            data.put("user", request.user() == null ? null : request.user().username);
            return data;
        });
    }
}
