package cn.edu.swust.mc.skin;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Yggdrasil {
    private static final int TOKEN_DAYS = 15;
    private static final String TOKEN_PREFIX = "ygg_";

    public static final class YggdrasilError extends RuntimeException {
        public final int status;
        public final String code;

        public YggdrasilError(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    private final App app;
    private final Database db;
    private final Config config;
    private final PrivateKey privateKey;
    private final String publicKeyPem;

    public Yggdrasil(App app) {
        this.app = app;
        this.db = app.db;
        this.config = app.config;
        try {
            Path keyDir = config.dataDir.resolve("keys");
            Files.createDirectories(keyDir);
            Path privatePath = keyDir.resolve("yggdrasil-private.pem");
            Path publicPath = keyDir.resolve("yggdrasil-public.pem");
            if (!Files.isRegularFile(privatePath) || !Files.isRegularFile(publicPath)) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair pair = generator.generateKeyPair();
                Files.writeString(privatePath, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
                Files.writeString(publicPath, pem("PUBLIC KEY", pair.getPublic().getEncoded()));
            }
            privateKey = readPrivate(privatePath);
            publicKeyPem = Files.readString(publicPath);
        } catch (Exception e) {
            throw new IllegalStateException("Yggdrasil 密钥初始化失败", e);
        }
    }

    private static String pem(String label, byte[] data) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(data);
        return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n";
    }

    private static PrivateKey readPrivate(Path path) throws Exception {
        byte[] data = Base64.getMimeDecoder().decode(Files.readString(path)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", ""));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(data));
    }

    public boolean enabled() {
        return "1".equals(Settings.get(db, "yggdrasil_enabled"));
    }

    public Map<String, Object> metadata() {
        URI uri = URI.create(config.baseUrl);
        String host = uri.getHost() == null ? "localhost" : uri.getHost();
        Set<String> domains = new LinkedHashSet<>();
        domains.add(host);
        if (uri.getPort() > 0) domains.add(host + ":" + uri.getPort());
        domains.add("localhost");
        domains.add("127.0.0.1");
        Map<String, Object> links = new LinkedHashMap<>();
        links.put("homepage", config.baseUrl);
        links.put("register", config.baseUrl + "/#/login");
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("serverName", Settings.get(db, "site_name"));
        meta.put("implementationName", "SWUSTMC Skin");
        meta.put("implementationVersion", "1.0.0");
        meta.put("links", links);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meta", meta);
        result.put("skinDomains", new ArrayList<>(domains));
        result.put("signaturePublickey", publicKeyPem);
        return result;
    }

    private List<Map<String, Object>> playersForUser(String userId) {
        return Players.forUserRaw(db, userId);
    }

    private Map<String, Object> profileRef(Map<String, Object> player) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("id", Security.stripUuid(Authz.str(player.get("uuid"))));
        ref.put("name", Authz.str(player.get("name")));
        return ref;
    }

    private Map<String, Object> selectedPlayer(String userId, Object selectedProfile) {
        if (selectedProfile == null) return null;
        String id = "";
        String name = "";
        if (selectedProfile instanceof Map<?, ?> map) {
            if (map.get("id") != null) id = Authz.str(map.get("id"));
            if (map.get("name") != null) name = Authz.str(map.get("name"));
        } else {
            id = Authz.str(selectedProfile);
        }
        if (id.isBlank() && name.isBlank()) return null;
        id = id.trim().replace("\"", "");
        if (id.startsWith("{") && id.endsWith("}")) id = id.substring(1, id.length() - 1);
        Map<String, Object> row = id.isBlank() ? null : Players.byUuid(db, id);
        if (row == null && !id.isBlank()) row = Players.byName(db, id);
        if (row == null && id.matches("\\d{1,19}")) {
            Map<String, Object> byId = Players.row(db, id);
            if (byId != null && userId.equals(Authz.str(byId.get("user_id")))) row = byId;
        }
        if (row == null && !name.isBlank()) row = Players.byName(db, name);
        if (row == null || !userId.equals(Authz.str(row.get("user_id")))) {
            Log.warn("Yggdrasil selectedProfile 无法匹配用户 " + userId + "：" + (id.isBlank() ? name : id));
            throw new YggdrasilError(403, "ForbiddenOperationException", "Invalid profile.");
        }
        return row;
    }

    private record Token(String accessToken, String clientToken, String tokenHash, Object playerId) {
    }

    private Token createToken(String userId, Object playerId, String clientToken) {
        String accessToken = TOKEN_PREFIX + Security.randomHex(16);
        String tokenHash = Security.sha256(accessToken);
        String resolvedClient = clientToken == null ? "" : clientToken;
        if (resolvedClient.length() > 64) resolvedClient = resolvedClient.substring(0, 64);
        if (resolvedClient.isEmpty()) resolvedClient = Security.randomHex(16);
        db.update("DELETE FROM yggdrasil_tokens WHERE expires_at <= ?", Security.isoNow());
        db.update("""
                INSERT INTO yggdrasil_tokens
                (token_hash, user_id, player_id, client_token, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, tokenHash, userId, playerId, resolvedClient, Security.isoNow(),
                Instant.now().plus(TOKEN_DAYS, ChronoUnit.DAYS).toString());
        return new Token(accessToken, resolvedClient, tokenHash, playerId);
    }

    private Map<String, Object> tokenRow(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) {
            throw new YggdrasilError(403, "ForbiddenOperationException", "Invalid token.");
        }
        Map<String, Object> row = db.one("""
                SELECT t.*, u.username, u.status FROM yggdrasil_tokens t
                JOIN users u ON u.id = t.user_id
                WHERE t.token_hash = ?
                """, Security.sha256(accessToken));
        if (row == null) throw new YggdrasilError(403, "ForbiddenOperationException", "Invalid token.");
        if (Authz.str(row.get("expires_at")).compareTo(Security.isoNow()) <= 0
                || !"ACTIVE".equals(Authz.str(row.get("status")))) {
            db.update("DELETE FROM yggdrasil_tokens WHERE token_hash = ?", row.get("token_hash"));
            throw new YggdrasilError(403, "ForbiddenOperationException", "Invalid token.");
        }
        return row;
    }

    private Map<String, Object> tokenResponse(Token token, String userId) {
        List<Map<String, Object>> profiles = playersForUser(userId).stream().map(this::profileRef).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accessToken", token.accessToken());
        payload.put("clientToken", token.clientToken());
        payload.put("availableProfiles", profiles);
        if (token.playerId() != null) {
            Map<String, Object> player = Players.row(db, token.playerId());
            if (player != null && userId.equals(Authz.str(player.get("user_id")))) {
                payload.put("selectedProfile", profileRef(player));
            }
        }
        return payload;
    }

    public Map<String, Object> authenticate(Map<String, Object> body) {
        String username = Authz.str(body.get("username")).trim();
        String password = Authz.str(body.get("password"));
        if (username.isEmpty() || password.isEmpty()) throw badCredentials();
        Auth.Credentials credentials = Auth.authenticate(db, username, password);
        if (credentials == null) throw badCredentials();
        String userId = Authz.str(credentials.row().get("id"));
        List<Map<String, Object>> players = playersForUser(userId);
        Map<String, Object> player = null;
        if (body.get("selectedProfile") != null) {
            player = selectedPlayer(userId, body.get("selectedProfile"));
        } else if (players.size() == 1) {
            player = players.get(0);
        }
        Token token = createToken(userId, player == null ? null : player.get("id"),
                body.get("clientToken") == null ? null : Authz.str(body.get("clientToken")));
        return tokenResponse(token, userId);
    }

    public Map<String, Object> refresh(Map<String, Object> body) {
        Map<String, Object> row = tokenRow(body.get("accessToken") == null ? null : Authz.str(body.get("accessToken")));
        if (body.get("clientToken") != null && !Authz.str(body.get("clientToken")).equals(Authz.str(row.get("client_token")))) {
            throw new YggdrasilError(403, "ForbiddenOperationException", "Invalid token.");
        }
        Object playerId = row.get("player_id");
        if (body.get("selectedProfile") != null) {
            Map<String, Object> player = selectedPlayer(Authz.str(row.get("user_id")), body.get("selectedProfile"));
            playerId = player.get("id");
        } else if (playerId == null) {
            List<Map<String, Object>> players = playersForUser(Authz.str(row.get("user_id")));
            if (players.size() == 1) playerId = players.get(0).get("id");
        }
        db.update("UPDATE yggdrasil_tokens SET player_id = ?, expires_at = ? WHERE token_hash = ?",
                playerId, Instant.now().plus(TOKEN_DAYS, ChronoUnit.DAYS).toString(), row.get("token_hash"));
        Token token = new Token(Authz.str(body.get("accessToken")), Authz.str(row.get("client_token")),
                Authz.str(row.get("token_hash")), playerId);
        return tokenResponse(token, Authz.str(row.get("user_id")));
    }

    public boolean validate(String accessToken) {
        tokenRow(accessToken);
        return true;
    }

    public void invalidate(String accessToken) {
        if (accessToken != null && !accessToken.isEmpty()) {
            db.update("DELETE FROM yggdrasil_tokens WHERE token_hash = ?", Security.sha256(accessToken));
        }
    }

    public void signout(String username, String password) {
        Auth.Credentials credentials = Auth.authenticate(db, username == null ? "" : username.trim(),
                password == null ? "" : password);
        if (credentials == null) throw badCredentials();
        db.update("DELETE FROM yggdrasil_tokens WHERE user_id = ?", credentials.row().get("id"));
    }

    private Map<String, Object> textureProperty(Map<String, Object> player) {
        Map<String, Object> textures = new LinkedHashMap<>();
        String skinHash = Authz.str(player.get("skin_hash"));
        String playerModel = Authz.str(player.get("model"));
        String skinModel = skinHash.isEmpty()
                ? playerModel
                : Authz.str(player.get("skin_texture_model"));
        Map<String, Object> skin = new LinkedHashMap<>();
        skin.put("url", skinHash.isEmpty()
                ? config.baseUrl + "/assets/default/" + Players.defaultSkinFile(playerModel)
                : config.baseUrl + "/textures/" + skinHash + ".png");
        if (Players.slimModel(skinModel)) {
            skin.put("metadata", Map.of("model", "slim"));
        }
        textures.put("SKIN", skin);
        if (player.get("cape_hash") != null) {
            Map<String, Object> cape = new LinkedHashMap<>();
            cape.put("url", config.baseUrl + "/textures/" + player.get("cape_hash") + ".png");
            textures.put("CAPE", cape);
        }
        if (textures.isEmpty()) return null;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("profileId", Security.stripUuid(Authz.str(player.get("uuid"))));
        payload.put("profileName", Authz.str(player.get("name")));
        payload.put("textures", textures);
        String value = Base64.getEncoder().encodeToString(Json.stringify(payload).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("name", "textures");
        property.put("value", value);
        property.put("signature", sign(value));
        return property;
    }

    private String sign(String value) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("签名失败", e);
        }
    }

    public Map<String, Object> profileFor(String uuid) {
        Map<String, Object> player = Players.byUuid(db, uuid);
        if (player == null) return null;
        Map<String, Object> property = textureProperty(player);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", Security.stripUuid(Authz.str(player.get("uuid"))));
        profile.put("name", Authz.str(player.get("name")));
        profile.put("properties", property == null ? List.of() : List.of(property));
        return profile;
    }

    public void join(Map<String, Object> body) {
        Map<String, Object> row = tokenRow(body.get("accessToken") == null ? null : Authz.str(body.get("accessToken")));
        String serverId = body.get("serverId") == null ? "" : Authz.str(body.get("serverId"));
        if (serverId.isEmpty()) throw new YggdrasilError(400, "IllegalArgumentException", "serverId is required.");
        String userId = Authz.str(row.get("user_id"));
        Map<String, Object> player = selectedPlayer(userId, body.get("selectedProfile"));
        if (player == null && row.get("player_id") != null) {
            Map<String, Object> byToken = Players.row(db, row.get("player_id"));
            if (byToken != null && userId.equals(Authz.str(byToken.get("user_id")))) {
                player = byToken;
            }
        }
        if (player == null) {
            List<Map<String, Object>> players = playersForUser(userId);
            if (players.size() == 1) player = players.get(0);
        }
        if (player == null) {
            throw new YggdrasilError(403, "ForbiddenOperationException", "Invalid profile.");
        }
        db.update("DELETE FROM yggdrasil_joins WHERE created_at <= ?",
                Instant.now().minusSeconds(120).toString());
        db.update("""
                INSERT INTO yggdrasil_joins (server_id, player_id, access_token_hash, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(server_id) DO UPDATE SET
                  player_id = excluded.player_id,
                  access_token_hash = excluded.access_token_hash,
                  created_at = excluded.created_at
                """, serverId, player.get("id"), row.get("token_hash"), Security.isoNow());
    }

    public Map<String, Object> hasJoined(String username, String serverId) {
        if (username == null || serverId == null || username.isEmpty() || serverId.isEmpty()) return null;
        Map<String, Object> join = db.one("SELECT * FROM yggdrasil_joins WHERE server_id = ?", serverId);
        if (join == null) return null;
        if (Authz.str(join.get("created_at")).compareTo(Instant.now().minusSeconds(120).toString()) <= 0) {
            db.update("DELETE FROM yggdrasil_joins WHERE server_id = ?", serverId);
            return null;
        }
        Map<String, Object> player = Players.row(db, join.get("player_id"));
        if (player == null || !Authz.str(player.get("name")).equalsIgnoreCase(username)) return null;
        return profileFor(Authz.str(player.get("uuid")));
    }

    public List<Map<String, Object>> profilesByName(List<String> names) {
        List<Map<String, Object>> found = new ArrayList<>();
        for (String name : names.subList(0, Math.min(names.size(), 20))) {
            Map<String, Object> player = Players.byName(db, name);
            if (player != null) {
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("id", Security.stripUuid(Authz.str(player.get("uuid"))));
                ref.put("name", Authz.str(player.get("name")));
                found.add(ref);
            }
        }
        return found;
    }

    public List<Map<String, Object>> profileByName(String name) {
        Map<String, Object> player = Players.byName(db, name);
        if (player == null) return List.of();
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("id", Security.stripUuid(Authz.str(player.get("uuid"))));
        ref.put("name", Authz.str(player.get("name")));
        return List.of(ref);
    }

    private static YggdrasilError badCredentials() {
        return new YggdrasilError(403, "ForbiddenOperationException",
                "Invalid credentials. Invalid username or password.");
    }
}
