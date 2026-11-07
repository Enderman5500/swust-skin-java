package cn.edu.swust.mc.skin.plugin;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 传给 Java 插件的上下文。插件可以注册路由、监听事件并读写自己的配置。
 */
public interface PluginContext {
    String id();

    Map<String, Object> manifest();

    void info(String message);

    void warn(String message);

    void error(String message, Throwable error);

    Map<String, Object> getConfig();

    Map<String, Object> setConfig(Map<String, Object> patch);

    Object getData(String key);

    void setData(String key, Object value);

    void deleteData(String key);

    void registerRoute(String method, String path, SkinPlugin.RouteHandler handler);

    void registerAdminRoute(String method, String path, SkinPlugin.RouteHandler handler);

    void on(String event, SkinPlugin.EventHandler handler);

    Map<String, Object> settings();

    Map<String, Object> stats();

    Map<String, Object> listPublicTextures(Map<String, String> query);

    /**
     * 插件私有数据目录（data/plugin-data/&lt;plugin-id&gt;/），用于独立数据库和持久化文件。
     */
    Path dataDir();

    /**
     * 按游戏 UUID 优先、角色名兜底匹配站点角色；未匹配返回 null。
     */
    Map<String, Object> findPlayer(String uuid, String name);
}
