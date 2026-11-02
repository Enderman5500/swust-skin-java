package cn.edu.swust.mc.skin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Settings {
    public static final Map<String, String> DEFAULTS = Map.of(
            "site_name", "西南科技大学 Minecraft 星空社",
            "site_short_name", "星空社",
            "tagline", "让每一块方块，都映出你的星图",
            "announcement", "欢迎来到星空社皮肤站。注册账号、创建角色并上传皮肤，在支持 authlib-injector 的启动器中登录，即可让小伙伴看到你的专属外观。",
            "footer_text", "西南科技大学 Minecraft 星空社 · 由社团技术组维护",
            "registration_open", "1",
            "yggdrasil_enabled", "1",
            "home_banner", "",
            "home_banners", "[]"
    );

    public static final List<String> PUBLIC_KEYS = List.of(
            "site_name", "site_short_name", "tagline", "announcement", "footer_text",
            "registration_open", "yggdrasil_enabled"
    );

    private Settings() {
    }

    public static void seed(Database db) {
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            db.update("INSERT OR IGNORE INTO settings (key, value) VALUES (?, ?)", entry.getKey(), entry.getValue());
        }
    }

    public static Map<String, String> all(Database db) {
        Map<String, String> values = new LinkedHashMap<>(DEFAULTS);
        for (Map<String, Object> row : db.query("SELECT key, value FROM settings")) {
            values.put(Authz.str(row.get("key")), Authz.str(row.get("value")));
        }
        return values;
    }

    public static String get(Database db, String key) {
        Map<String, Object> row = db.one("SELECT value FROM settings WHERE key = ?", key);
        return row == null ? DEFAULTS.get(key) : Authz.str(row.get("value"));
    }

    public static void set(Database db, String key, String value) {
        db.update("""
                INSERT INTO settings (key, value) VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """, key, value);
    }

    public static Map<String, Object> publicMap(Database db) {
        Map<String, String> values = all(db);
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : PUBLIC_KEYS) map.put(key, values.get(key));
        map.put("registrationOpen", "1".equals(values.get("registration_open")));
        map.put("yggdrasilEnabled", "1".equals(values.get("yggdrasil_enabled")));
        java.util.List<String> banners = Branding.list(db);
        map.put("homeBannerUrls", banners.stream().map(Branding::publicUrl).filter(java.util.Objects::nonNull).toList());
        map.put("homeBannerUrl", banners.isEmpty() ? null : Branding.publicUrl(banners.get(0)));
        return map;
    }

    public static void update(Database db, Map<String, Object> patch) {
        for (String key : PUBLIC_KEYS) {
            if (!patch.containsKey(key)) continue;
            Object value = patch.get(key);
            String text = value instanceof Boolean b ? (b ? "1" : "0") : String.valueOf(value);
            set(db, key, text);
        }
    }
}
