package cn.edu.swust.mc.skin.plugin;

import cn.edu.swust.mc.skin.AuthUser;
import cn.edu.swust.mc.skin.Web;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.util.Map;

public final class PluginRequest {
    private final Web.Ctx ctx;
    private final Map<String, Object> manifest;
    private Map<String, Object> cachedJson;

    public PluginRequest(Web.Ctx ctx, Map<String, Object> manifest) {
        this.ctx = ctx;
        this.manifest = manifest;
    }

    public String method() {
        return ctx.method;
    }

    public String path() {
        return ctx.path;
    }

    public String query(String name) {
        return ctx.query(name);
    }

    public Map<String, String> query() {
        return ctx.query;
    }

    public String header(String name) {
        return ctx.exchange.getRequestHeaders().getFirst(name);
    }

    public AuthUser user() {
        return ctx.user;
    }

    public boolean admin() {
        return ctx.user != null && ctx.user.admin;
    }

    public boolean apiKey() {
        return ctx.apiKey != null;
    }

    public Map<String, Object> manifest() {
        return manifest;
    }

    public byte[] body(int limit) throws IOException {
        return ctx.body(limit);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> json() throws IOException {
        if (cachedJson == null) {
            cachedJson = ctx.json();
        }
        return cachedJson;
    }

    public Object jsonAny() throws IOException {
        return ctx.jsonAny(1024 * 1024);
    }
}
