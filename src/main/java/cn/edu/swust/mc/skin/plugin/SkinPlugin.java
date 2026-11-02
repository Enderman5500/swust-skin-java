package cn.edu.swust.mc.skin.plugin;

import java.util.Map;

/**
 * Java 插件入口。实现类必须提供 public 无参构造器。
 */
public interface SkinPlugin {
    default void activate(PluginContext context) throws Exception {
    }

    default void deactivate() throws Exception {
    }

    /**
     * 注册服务端路由。返回的对象会作为 data 写入统一 JSON 响应。
     */
    interface RouteHandler {
        Object handle(PluginRequest request) throws Exception;
    }

    interface EventHandler {
        void handle(Map<String, Object> payload) throws Exception;
    }
}
