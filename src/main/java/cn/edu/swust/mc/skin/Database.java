package cn.edu.swust.mc.skin;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Database implements AutoCloseable {
    private final Connection connection;
    private final Config config;

    public Database(Config config) {
        this.config = config;
        try {
            Class.forName("org.sqlite.JDBC");
            Path file = config.dataDir.resolve("swustmc.db");
            Files.createDirectories(file.getParent());
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            exec("PRAGMA journal_mode = WAL");
            exec("PRAGMA foreign_keys = ON");
            exec("PRAGMA busy_timeout = 5000");
            exec("PRAGMA synchronous = NORMAL");
            try (InputStream input = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
                if (input == null) throw new IllegalStateException("缺少 schema.sql");
                exec(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            exec("PRAGMA user_version = 2");
        } catch (Exception e) {
            throw new IllegalStateException("数据库初始化失败", e);
        }
    }

    public synchronized void exec(String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("SQL 执行失败: " + sql, e);
        }
    }

    public synchronized int update(String sql, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("SQL 执行失败: " + sql, e);
        }
    }

    public synchronized long insert(String sql, Object... params) {
        update(sql, params);
        Map<String, Object> row = one("SELECT last_insert_rowid() AS id");
        return row == null ? 0 : ((Number) row.get("id")).longValue();
    }

    public synchronized List<Map<String, Object>> query(String sql, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet result = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData meta = result.getMetaData();
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i), result.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("SQL 查询失败: " + sql, e);
        }
    }

    public synchronized Map<String, Object> one(String sql, Object... params) {
        List<Map<String, Object>> rows = query(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    public void bootstrap() {
        Settings.seed(this);
        Authz.seed(this, config);
        ensureAdmin();
        migratePlayerUuids();
    }

    private void ensureAdmin() {
        if (Authz.activeAdminCount(this, null) > 0) return;
        Map<String, Object> admin = one("""
                SELECT * FROM users WHERE username = ? COLLATE NOCASE OR email = ? COLLATE NOCASE
                """, config.adminUsername, config.adminEmail);
        if (admin == null) {
            String now = Security.isoNow();
            String id = Security.nextId(config.snowflakeDataCenter, config.snowflakeMachine);
            update("""
                    INSERT INTO users
                    (id, username, nickname, email, phone, qq, password, avatar_file_id, email_verified, status,
                     tags, fields, version, create_time, update_time)
                    VALUES (?, ?, ?, ?, NULL, NULL, ?, NULL, 1, 'ACTIVE', '[]', '{}', 0, ?, ?)
                    """, id, config.adminUsername, "管理员", config.adminEmail,
                    Security.hashPassword(config.adminPassword), now, now);
            admin = one("SELECT * FROM users WHERE id = ?", id);
            Log.info("已创建初始管理员：" + config.adminUsername);
        }
        String id = Authz.str(admin.get("id"));
        update("INSERT OR IGNORE INTO user_roles (user_id, role_id) VALUES (?, ?)", id, Authz.SUPER_ADMIN_ID);
        Authz.ensureUserDept(this, id, Authz.ROOT_DEPT, true);
        Authz.ensureUserDept(this, id, Authz.ADMIN_DEPT, false);
        update("UPDATE users SET status = 'ACTIVE', update_time = ? WHERE id = ?", Security.isoNow(), id);
    }

    private void migratePlayerUuids() {
        String current = "swust-1";
        String stored = Settings.get(this, "player_uuid_version");
        if (current.equals(stored)) return;
        List<Map<String, Object>> players = query("SELECT id, name FROM players");
        for (Map<String, Object> player : players) {
            update("UPDATE players SET uuid = ? WHERE id = ?",
                    Security.playerUuid(Authz.str(player.get("name"))), player.get("id"));
        }
        update("DELETE FROM yggdrasil_tokens");
        update("DELETE FROM yggdrasil_joins");
        Settings.set(this, "player_uuid_version", current);
        if (!players.isEmpty()) Log.info("已更新 " + players.size() + " 个角色的 UUID 命名空间");
    }

    public Config config() {
        return config;
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
