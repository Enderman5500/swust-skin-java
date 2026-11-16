package cn.edu.swust.mc.skin.migration;

import cn.edu.swust.mc.skin.Security;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 将 YuDream 数据导出包迁移到本平台的 SQLite 数据库。
 *
 * <p>既可独立运行，也可由「数据迁移」插件调用 {@link #execute}。</p>
 *
 * <pre>
 * java -cp target/swust-skin.jar cn.edu.swust.mc.skin.migration.YudreamMigration \
 *     --source yudream-data-export.zip [--data-dir data] [--dry-run]
 * </pre>
 *
 * <p>导出包可以直接是 zip，也可以是解压后的目录。迁移按主键/唯一键幂等执行，
 * 重复运行只补充缺失记录，不覆盖已有平台数据。同一邮箱存在多个账号时只保留一个，
 * 可通过 {@code --keep email=userId} 或插件页面指定保留账号。</p>
 */
public final class YudreamMigration {
    public static final String DEFAULT_INITIAL_PASSWORD = Security.INITIAL_PASSWORD;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DateTimeFormatter STAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());

    private static final List<String> REQUIRED_FILES = List.of(
            "manifest.json", "users.json", "roles.json", "skins.json"
    );

    private YudreamMigration() {
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        try {
            Options options = Options.parse(args);
            Map<String, Object> result = new YudreamMigration.Runner(options).run();
            System.out.println("迁移报告: " + result.get("reportFile"));
            System.out.println("导入结果: " + result.get("importedCounts"));
            Object issues = result.get("issues");
            if (issues instanceof List<?> list && !list.isEmpty()) {
                System.out.println("问题记录: " + list.size() + " 条（详见报告）");
            }
        } catch (Exception e) {
            System.err.println("迁移失败：" + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * 供插件或其它宿主代码调用。
     *
     * @param source          导出包 zip 或解压目录
     * @param dataDir         平台数据目录（包含 swustmc.db 与 textures）
     * @param dryRun          只分析，不写数据库与材质文件
     * @param writeReport     是否把 JSON 报告写入 dataDir/migration
     * @param initialPassword 迁移账号的初始密码
     * @param keepByEmail     同一邮箱只保留一个账号时的选择：email -> userId
     * @return 包含 sourceCounts、importedCounts、duplicateEmails、issues、notes 等字段的结果
     */
    public static Map<String, Object> execute(Path source, Path dataDir, boolean dryRun,
                                              boolean writeReport, String initialPassword,
                                              Map<String, String> keepByEmail) throws Exception {
        Options options = new Options();
        options.source = source.toString();
        options.dataDir = dataDir;
        options.dryRun = dryRun;
        options.writeReport = writeReport;
        options.initialPassword = initialPassword == null || initialPassword.isBlank()
                ? DEFAULT_INITIAL_PASSWORD : initialPassword;
        options.keepByEmail = keepByEmail == null ? Map.of() : new LinkedHashMap<>(keepByEmail);
        return new YudreamMigration.Runner(options).run();
    }

    // ------------------------------------------------------------------
    // 命令行参数
    // ------------------------------------------------------------------

    private static final class Options {
        String source;
        Path dataDir = Path.of("data");
        boolean dryRun;
        boolean writeReport = true;
        String initialPassword = DEFAULT_INITIAL_PASSWORD;
        Map<String, String> keepByEmail = new LinkedHashMap<>();

        static Options parse(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--source" -> options.source = requireValue(args, ++i, arg);
                    case "--data-dir" -> options.dataDir = Path.of(requireValue(args, ++i, arg));
                    case "--password" -> options.initialPassword = requireValue(args, ++i, arg);
                    case "--keep" -> {
                        String value = requireValue(args, ++i, arg);
                        int equals = value.indexOf('=');
                        if (equals <= 0 || equals == value.length() - 1) {
                            throw new IllegalArgumentException("--keep 格式应为 email=userId");
                        }
                        options.keepByEmail.put(value.substring(0, equals).trim().toLowerCase(Locale.ROOT),
                                value.substring(equals + 1).trim());
                    }
                    case "--dry-run" -> options.dryRun = true;
                    case "--no-report" -> options.writeReport = false;
                    case "--help", "-h" -> {
                        System.out.println("""
                                用法: YudreamMigration --source <导出包.zip|目录> [选项]
                                  --source       YuDream 数据导出包（zip 或解压目录）
                                  --data-dir     平台数据目录，默认 data
                                  --password     迁移账号初始密码，默认 swustmc123456
                                  --keep         email=userId，同一邮箱多个账号时保留哪个，可重复
                                  --dry-run      只分析并输出报告，不写入数据库和材质文件
                                  --no-report    不写入 data/migration 报告文件
                                """);
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException("未知参数: " + arg);
                }
            }
            if (options.source == null || options.source.isBlank()) {
                throw new IllegalArgumentException("缺少 --source 参数");
            }
            return options;
        }

        private static String requireValue(String[] args, int index, String name) {
            if (index >= args.length || args[index].startsWith("--")) {
                throw new IllegalArgumentException(name + " 缺少参数值");
            }
            return args[index];
        }
    }

    // ------------------------------------------------------------------
    // 导出包读取（目录 / zip）
    // ------------------------------------------------------------------

    private interface ExportSource extends AutoCloseable {
        byte[] read(String relativePath) throws IOException;

        boolean exists(String relativePath);

        String label();

        @Override
        void close() throws IOException;

        static ExportSource open(Path path) throws IOException {
            if (Files.isDirectory(path)) return new DirectorySource(path);
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".zip")) return new ZipSource(path);
            throw new IllegalArgumentException("导出包必须是 zip 或目录: " + path);
        }
    }

    private static final class DirectorySource implements ExportSource {
        private final Path root;

        DirectorySource(Path root) {
            this.root = root.toAbsolutePath().normalize();
        }

        @Override
        public byte[] read(String relativePath) throws IOException {
            return Files.readAllBytes(root.resolve(relativePath));
        }

        @Override
        public boolean exists(String relativePath) {
            return Files.isRegularFile(root.resolve(relativePath));
        }

        @Override
        public String label() {
            return root.toString();
        }

        @Override
        public void close() {
        }
    }

    private static final class ZipSource implements ExportSource {
        private final Path path;
        private final ZipFile zip;

        ZipSource(Path path) throws IOException {
            this.path = path.toAbsolutePath().normalize();
            this.zip = new ZipFile(this.path.toFile(), StandardCharsets.UTF_8);
        }

        @Override
        public byte[] read(String relativePath) throws IOException {
            ZipEntry entry = zip.getEntry(relativePath);
            if (entry == null) throw new IOException("导出包缺少文件: " + relativePath);
            try (InputStream input = zip.getInputStream(entry)) {
                return input.readAllBytes();
            }
        }

        @Override
        public boolean exists(String relativePath) {
            return zip.getEntry(relativePath) != null;
        }

        @Override
        public String label() {
            return path.toString();
        }

        @Override
        public void close() throws IOException {
            zip.close();
        }
    }

    // ------------------------------------------------------------------
    // 源数据模型
    // ------------------------------------------------------------------

    private record DeptRef(String id, String name, boolean isDefault) {
    }

    private record RoleRef(String id, String code, String name) {
    }

    private record SourceUser(String id, String username, String nickname, String email, String phone,
                              String qq, String status, String avatar, JsonNode tags, JsonNode messaging,
                              List<DeptRef> departments, List<RoleRef> roles) {
    }

    private record SourceRole(String id, String code, String name) {
    }

    private record SourceTexture(String hash, String name, String type, String model, boolean publicAccess,
                                 List<String> referencedByUserIds, byte[] bytes, int width, int height,
                                 boolean uploadCompatible) {
    }

    private record SourcePlayer(String ownerId, String uuid, String name, long lastModified,
                                String skinHash, String capeHash, String skinModel) {
    }

    private record SourceCloset(String userId, String hash, String name, long createdAt) {
    }

    private record SourceAsset(String code, String name, String symbol, int scale, boolean money,
                               boolean enabled, boolean transferEnabled, String minTransferAmount) {
    }

    private record SourceBalance(String userId, String assetCode, String balance, long updatedAt) {
    }

    private record SourceTransaction(String id, String businessNo, String type, String source,
                                     String assetCode, String fromUserId, String toUserId, String amount,
                                     String fromBalanceAfter, String toBalanceAfter, String remark,
                                     long createdAt) {
    }

    private record PreparedUser(SourceUser source, String username, String email, String nickname,
                                String passwordHash, String fieldsJson,
                                String tagsJson, int playerLimit) {
    }

    // ------------------------------------------------------------------
    // 迁移执行
    // ------------------------------------------------------------------

    private static final class Runner {
        private final Options options;
        private final List<String> issues = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();

        private final Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        private final Map<String, Integer> importedCounts = new LinkedHashMap<>();
        private final Set<String> importedUserIds = new LinkedHashSet<>();
        private final Map<String, String> sourceRoleToTarget = new LinkedHashMap<>();
        private final Map<String, String> roleIdByCode = new LinkedHashMap<>();
        private final Map<String, Integer> textureIds = new LinkedHashMap<>();
        private final List<SourceTexture> textures = new ArrayList<>();
        private final List<SourcePlayer> players = new ArrayList<>();
        private final List<SourceCloset> closet = new ArrayList<>();
        private final List<SourceAsset> assets = new ArrayList<>();
        private final List<SourceBalance> balances = new ArrayList<>();
        private final List<SourceTransaction> transactions = new ArrayList<>();
        private final List<PreparedUser> preparedUsers = new ArrayList<>();
        private final Map<String, Integer> playersPerUser = new HashMap<>();
        private final Map<String, Integer> closetPerUser = new HashMap<>();
        private final Map<String, Integer> walletBalancePerUser = new HashMap<>();
        private final Map<String, Integer> walletTransactionPerUser = new HashMap<>();
        private final Map<String, String> departmentNames = new LinkedHashMap<>();
        private final Map<String, SourceRole> sourceRoles = new LinkedHashMap<>();
        private final Set<String> sourceUserIds = new LinkedHashSet<>();
        private final Set<String> sourcePlayerUuids = new LinkedHashSet<>();
        private final List<Map<String, String>> renamedUsernames = new ArrayList<>();
        private final Set<String> droppedUserIds = new LinkedHashSet<>();
        private final List<Map<String, Object>> duplicateEmails = new ArrayList<>();

        private String exportId = "";
        private String batchId;
        private String now;
        private String initialPasswordHash = "";
        private int skippedUsers;
        private int skippedTextures;
        private int skippedPlayers;
        private int skippedCloset;
        private int playerFailures;
        private int closetFailures;

        Runner(Options options) {
            this.options = options;
        }

        Map<String, Object> run() throws Exception {
            Path dataDir = options.dataDir.toAbsolutePath().normalize();
            Path dbFile = dataDir.resolve("swustmc.db");
            Path textureDir = dataDir.resolve("textures");
            Path migrationDir = dataDir.resolve("migration");
            if (!Files.isRegularFile(dbFile)) {
                throw new IllegalStateException("数据库不存在，请先启动一次平台: " + dbFile);
            }
            Files.createDirectories(textureDir);
            Files.createDirectories(migrationDir);

            now = Instant.now().toString();
            batchId = "yudream-" + STAMP.format(Instant.now());
            if (!options.dryRun) {
                initialPasswordHash = Security.hashPassword(options.initialPassword);
            }

            Path sourcePath = Path.of(options.source).toAbsolutePath().normalize();
            try (ExportSource source = ExportSource.open(sourcePath)) {
                for (String file : REQUIRED_FILES) {
                    if (!source.exists(file)) {
                        throw new IllegalStateException("导出包缺少 " + file);
                    }
                }
                JsonNode manifest = MAPPER.readTree(source.read("manifest.json"));
                exportId = text(manifest, "exportId");
                sourceCounts.put("users", intValue(manifest.path("counts"), "users"));
                sourceCounts.put("roleAssignments", intValue(manifest.path("counts"), "roleAssignments"));
                sourceCounts.put("skinPlayers", intValue(manifest.path("counts"), "skinPlayers"));
                sourceCounts.put("skinClosetItems", intValue(manifest.path("counts"), "skinClosetItems"));
                sourceCounts.put("skinTextures", intValue(manifest.path("counts"), "skinTextures"));
                sourceCounts.put("walletAssets", intValue(manifest.path("counts"), "walletAssets"));
                sourceCounts.put("walletBalances", intValue(manifest.path("counts"), "walletBalances"));
                sourceCounts.put("walletTransactions", intValue(manifest.path("counts"), "walletTransactions"));

                List<SourceUser> users = parseUsers(MAPPER.readTree(source.read("users.json")));
                parseRoles(MAPPER.readTree(source.read("roles.json")), users);
                parseSkins(MAPPER.readTree(source.read("skins.json")), source);
                if (source.exists("wallets.json")) {
                    parseWallets(MAPPER.readTree(source.read("wallets.json")));
                } else {
                    notes.add("导出包不含 wallets.json，跳过钱包数据。");
                }

                sourceCounts.put("parsedUsers", users.size());
                sourceCounts.put("parsedTextures", textures.size());
                sourceCounts.put("parsedPlayers", players.size());
                sourceCounts.put("parsedClosetItems", closet.size());

                analyzeDuplicateEmails(users);
                removeDroppedUsers(users);

                sourceCounts.put("activeUsers", users.size());
                sourceCounts.put("activePlayers", players.size());
                sourceCounts.put("activeClosetItems", closet.size());

                try (Connection connection = open(dbFile)) {
                    Exists exists = loadExists(connection);
                    for (Map.Entry<String, String> role : exists.roleCodes.entrySet()) {
                        roleIdByCode.putIfAbsent(role.getValue(), role.getKey());
                    }
                    planUsers(users, exists);

                    if (options.dryRun) {
                        int pendingTextures = 0;
                        for (SourceTexture texture : textures) {
                            if (!exists.textureHashes.contains(texture.hash())) pendingTextures++;
                        }
                        int pendingPlayers = 0;
                        for (String uuid : sourcePlayerUuids) {
                            if (!exists.playerUuids.contains(uuid)) pendingPlayers++;
                        }
                        importedCounts.put("users", preparedUsers.size());
                        importedCounts.put("skinPlayers", pendingPlayers);
                        importedCounts.put("skinClosetItems", Math.max(0, closet.size() - closetFailures));
                        importedCounts.put("skinTextures", pendingTextures);
                        importedCounts.put("walletAssets", assets.size());
                        importedCounts.put("walletBalances", balances.size());
                        importedCounts.put("walletTransactions", transactions.size());
                        notes.add("dry-run：未写入数据库或材质文件，数量为预计新增上限。");
                    } else {
                        copyTextureFiles(textureDir);
                        importAll(connection, exists);
                    }
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("generatedAt", now);
            result.put("batchId", batchId);
            result.put("source", sourcePath.toString());
            result.put("exportId", exportId);
            result.put("dryRun", options.dryRun);
            result.put("database", dataDir.resolve("swustmc.db").toString());
            result.put("initialPassword", options.initialPassword);
            result.put("sourceCounts", sourceCounts);
            result.put("importedCounts", importedCounts);
            result.put("duplicateEmails", duplicateEmails);
            result.put("droppedUserIds", new ArrayList<>(droppedUserIds));
            result.put("renamedUsernames", renamedUsernames);
            result.put("issues", issues);
            result.put("notes", notes);

            Map<String, Object> skipped = new LinkedHashMap<>();
            skipped.put("users", skippedUsers);
            skipped.put("textures", skippedTextures);
            skipped.put("players", skippedPlayers);
            skipped.put("closetItems", skippedCloset);
            skipped.put("duplicateEmailUsers", droppedUserIds.size());
            result.put("skipped", skipped);

            Path reportFile = null;
            if (options.writeReport) {
                reportFile = migrationDir.resolve("yudream-import-report-" + STAMP.format(Instant.now()) + ".json");
                writeReport(reportFile, sourcePath);
            }
            result.put("reportFile", reportFile == null ? null : reportFile.toString());
            return result;
        }

        // --------------------------------------------------------------
        // 解析
        // --------------------------------------------------------------

        private List<SourceUser> parseUsers(JsonNode root) {
            List<SourceUser> users = new ArrayList<>();
            for (JsonNode node : root.path("users")) {
                List<DeptRef> departments = new ArrayList<>();
                for (JsonNode dept : node.path("departments")) {
                    String id = text(dept, "deptId");
                    if (id.isEmpty()) continue;
                    String name = text(dept, "name");
                    departments.add(new DeptRef(id, name, boolValue(dept, "default", false)));
                    departmentNames.putIfAbsent(id, name);
                }
                List<RoleRef> roles = new ArrayList<>();
                for (JsonNode role : node.path("roles")) {
                    String id = text(role, "roleId");
                    if (id.isEmpty()) continue;
                    roles.add(new RoleRef(id, text(role, "code"), text(role, "name")));
                }
                String id = text(node, "userId");
                if (id.isEmpty()) {
                    issues.add("用户记录缺少 userId，已跳过。");
                    continue;
                }
                users.add(new SourceUser(
                        id,
                        text(node, "username"),
                        text(node, "nickname"),
                        text(node, "email"),
                        text(node, "phone"),
                        text(node, "qq"),
                        text(node, "status"),
                        text(node, "avatar"),
                        node.path("tags"),
                        node.path("messagingIdentities"),
                        departments,
                        roles));
                sourceUserIds.add(id);
            }
            return users;
        }

        private void parseRoles(JsonNode root, List<SourceUser> users) {
            for (JsonNode node : root.path("userRoles")) {
                String id = text(node, "roleId");
                if (id.isEmpty()) continue;
                sourceRoles.putIfAbsent(id, new SourceRole(id, text(node, "code"), text(node, "name")));
            }
            for (SourceUser user : users) {
                for (RoleRef role : user.roles()) {
                    sourceRoles.putIfAbsent(role.id(), new SourceRole(role.id(), role.code(), role.name()));
                }
            }
        }

        private void parseSkins(JsonNode root, ExportSource source) throws IOException {
            for (JsonNode node : root.path("textures")) {
                String hash = text(node, "hash");
                if (hash.isEmpty()) {
                    issues.add("材质记录缺少 hash，已跳过。");
                    continue;
                }
                byte[] bytes;
                try {
                    bytes = source.read("textures/" + hash + ".png");
                } catch (IOException e) {
                    issues.add("材质文件缺失: textures/" + hash + ".png");
                    skippedTextures++;
                    continue;
                }
                if (!sha256(bytes).equals(hash)) {
                    issues.add("材质内容与 hash 不一致，已跳过: " + hash);
                    skippedTextures++;
                    continue;
                }
                int[] size = pngSize(bytes);
                if (size == null) {
                    issues.add("材质不是有效 PNG，已跳过: " + hash);
                    skippedTextures++;
                    continue;
                }
                List<String> refs = new ArrayList<>();
                for (JsonNode ref : node.path("referencedByUserIds")) {
                    refs.add(ref.asText());
                }
                textures.add(new SourceTexture(
                        hash,
                        text(node, "name"),
                        text(node, "type"),
                        text(node, "model"),
                        boolValue(node, "publicAccess", false),
                        refs,
                        bytes,
                        size[0],
                        size[1],
                        pngUploadCompatible(bytes, size, text(node, "type"))));
            }

            for (JsonNode userNode : root.path("users")) {
                String userId = text(userNode, "userId");
                for (JsonNode node : userNode.path("players")) {
                    String ownerId = text(node, "ownerId");
                    if (ownerId.isEmpty()) ownerId = userId;
                    JsonNode skin = node.get("skin");
                    JsonNode cape = node.get("cape");
                    String skinHash = skin == null || skin.isNull() ? "" : text(skin, "hash");
                    String capeHash = cape == null || cape.isNull() ? "" : text(cape, "hash");
                    String skinModel = skin == null || skin.isNull() ? "" : text(skin, "model");
                    String uuid = text(node, "uuid");
                    String normalizedUuid = normalizeUuid(uuid);
                    if (normalizedUuid != null) sourcePlayerUuids.add(normalizedUuid);
                    players.add(new SourcePlayer(
                            ownerId,
                            uuid,
                            text(node, "name"),
                            longValue(node, "lastModified", 0),
                            skinHash,
                            capeHash,
                            skinModel));
                    playersPerUser.merge(ownerId, 1, Integer::sum);
                }
                for (JsonNode node : userNode.path("closet")) {
                    String closetUser = text(node, "userId");
                    if (closetUser.isEmpty()) {
                        String itemId = text(node, "itemId");
                        int index = itemId.indexOf(':');
                        if (index > 0) closetUser = itemId.substring(0, index);
                    }
                    String hash = text(node, "textureHash");
                    if (closetUser.isEmpty() || hash.isEmpty()) {
                        issues.add("衣柜记录缺少 userId 或 textureHash，已跳过。");
                        skippedCloset++;
                        continue;
                    }
                    closet.add(new SourceCloset(
                            closetUser,
                            hash,
                            text(node, "itemName"),
                            longValue(node, "createdAt", 0)));
                    closetPerUser.merge(closetUser, 1, Integer::sum);
                }
            }
        }

        private void parseWallets(JsonNode root) {
            for (JsonNode node : root.path("assets")) {
                String code = text(node, "code");
                if (code.isEmpty()) continue;
                assets.add(new SourceAsset(
                        code,
                        text(node, "name"),
                        text(node, "symbol"),
                        intValue(node, "scale"),
                        boolValue(node, "money", false),
                        boolValue(node, "enabled", true),
                        boolValue(node, "transferEnabled", false),
                        text(node, "minTransferAmount")));
            }
            for (JsonNode node : root.path("balances")) {
                String userId = text(node, "userId");
                String assetCode = text(node, "assetCode");
                if (userId.isEmpty() || assetCode.isEmpty()) continue;
                balances.add(new SourceBalance(userId, assetCode, text(node, "balance"), longValue(node, "updatedAt", 0)));
                walletBalancePerUser.merge(userId, 1, Integer::sum);
            }
            for (JsonNode node : root.path("transactions")) {
                String id = text(node, "id");
                String assetCode = text(node, "assetCode");
                if (id.isEmpty() || assetCode.isEmpty()) continue;
                transactions.add(new SourceTransaction(
                        id,
                        text(node, "businessNo"),
                        text(node, "type"),
                        text(node, "source"),
                        assetCode,
                        text(node, "fromUserId"),
                        text(node, "toUserId"),
                        text(node, "amount"),
                        text(node, "fromBalanceAfter"),
                        text(node, "toBalanceAfter"),
                        text(node, "remark"),
                        longValue(node, "createdAt", 0)));
                String fromUserId = text(node, "fromUserId");
                String toUserId = text(node, "toUserId");
                if (!fromUserId.isEmpty()) walletTransactionPerUser.merge(fromUserId, 1, Integer::sum);
                if (!toUserId.isEmpty() && !toUserId.equals(fromUserId)) {
                    walletTransactionPerUser.merge(toUserId, 1, Integer::sum);
                }
            }
        }

        // --------------------------------------------------------------
        // 同邮箱账号去重
        // --------------------------------------------------------------

        private void analyzeDuplicateEmails(List<SourceUser> users) {
            Map<String, List<SourceUser>> groups = new LinkedHashMap<>();
            Map<String, Integer> order = new HashMap<>();
            for (int index = 0; index < users.size(); index++) {
                SourceUser user = users.get(index);
                order.putIfAbsent(user.id(), index);
                String email = normalizeEmail(user.email());
                if (!email.isEmpty()) {
                    groups.computeIfAbsent(email, key -> new ArrayList<>()).add(user);
                }
            }
            for (Map.Entry<String, List<SourceUser>> entry : groups.entrySet()) {
                List<SourceUser> accounts = entry.getValue();
                if (accounts.size() <= 1) continue;
                String email = entry.getKey();
                String keepId = chooseKeepUser(email, accounts, order);
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("email", email);
                group.put("recommendedUserId", keepId);
                group.put("keepUserId", keepId);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (SourceUser user : accounts) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("userId", user.id());
                    row.put("username", user.username());
                    row.put("nickname", user.nickname());
                    row.put("playerCount", playersPerUser.getOrDefault(user.id(), 0));
                    row.put("closetCount", closetPerUser.getOrDefault(user.id(), 0));
                    row.put("walletBalanceCount", walletBalancePerUser.getOrDefault(user.id(), 0));
                    row.put("walletTransactionCount", walletTransactionPerUser.getOrDefault(user.id(), 0));
                    row.put("recommended", user.id().equals(keepId));
                    row.put("kept", user.id().equals(keepId));
                    rows.add(row);
                    if (!user.id().equals(keepId)) droppedUserIds.add(user.id());
                }
                group.put("accounts", rows);
                duplicateEmails.add(group);
            }
        }

        private String chooseKeepUser(String email, List<SourceUser> accounts, Map<String, Integer> order) {
            String override = options.keepByEmail.get(email);
            if (override != null && !override.isBlank()) {
                for (SourceUser user : accounts) {
                    if (user.id().equals(override)) return user.id();
                }
                issues.add("邮箱 " + email + " 指定的保留账号不存在，已使用推荐账号: " + override);
            }
            SourceUser best = accounts.get(0);
            int bestScore = scoreUser(best.id());
            int bestOrder = order.getOrDefault(best.id(), Integer.MAX_VALUE);
            for (int index = 1; index < accounts.size(); index++) {
                SourceUser candidate = accounts.get(index);
                int score = scoreUser(candidate.id());
                int candidateOrder = order.getOrDefault(candidate.id(), Integer.MAX_VALUE);
                if (score > bestScore || (score == bestScore && candidateOrder < bestOrder)) {
                    best = candidate;
                    bestScore = score;
                    bestOrder = candidateOrder;
                }
            }
            return best.id();
        }

        private int scoreUser(String userId) {
            return playersPerUser.getOrDefault(userId, 0)
                    + closetPerUser.getOrDefault(userId, 0)
                    + walletBalancePerUser.getOrDefault(userId, 0)
                    + walletTransactionPerUser.getOrDefault(userId, 0);
        }

        private void removeDroppedUsers(List<SourceUser> users) {
            if (droppedUserIds.isEmpty()) return;
            users.removeIf(user -> droppedUserIds.contains(user.id()));
            players.removeIf(player -> droppedUserIds.contains(player.ownerId()));
            closet.removeIf(item -> droppedUserIds.contains(item.userId()));
            sourceUserIds.removeAll(droppedUserIds);
            for (String userId : droppedUserIds) playersPerUser.remove(userId);
            sourcePlayerUuids.clear();
            for (SourcePlayer player : players) {
                String uuid = normalizeUuid(player.uuid());
                if (uuid != null) sourcePlayerUuids.add(uuid);
            }
            notes.add("同一邮箱存在多个账号，已保留 " + (duplicateEmails.size())
                    + " 个账号并跳过 " + droppedUserIds.size() + " 个重复邮箱账号。");
        }

        // --------------------------------------------------------------
        // 规划用户（用户名去重、邮箱、密码）
        // --------------------------------------------------------------

        private void planUsers(List<SourceUser> users, Exists exists) throws Exception {
            Set<String> usedNames = new HashSet<>(exists.usernames);
            Set<String> usedEmails = new HashSet<>(exists.emails);
            for (SourceUser user : users) {
                if (exists.userIds.contains(user.id())) {
                    String existingName = exists.usernamesById.get(user.id());
                    if (existingName != null && !existingName.equals(user.username())) {
                        addRenamed(user.id(), user.username(), existingName);
                    }
                    skippedUsers++;
                    continue;
                }
                if (importedUserIds.contains(user.id())) {
                    skippedUsers++;
                    continue;
                }
                String username = resolveUsername(user.username(), user.id(), usedNames);
                usedNames.add(username.toLowerCase(Locale.ROOT));
                importedUserIds.add(user.id());
                if (!username.equals(user.username())) {
                    addRenamed(user.id(), user.username(), username);
                }

                String email = user.email().trim().toLowerCase(Locale.ROOT);
                if (email.isEmpty()) {
                    email = null;
                } else if (usedEmails.contains(email)) {
                    issues.add("邮箱重复，已置空: " + user.id() + " " + email);
                    email = null;
                } else {
                    usedEmails.add(email);
                }

                String nickname = truncate(user.nickname().trim(), 64);
                if (nickname.isEmpty()) nickname = username;

                String passwordHash = options.dryRun ? "" : initialPasswordHash;
                String tagsJson = user.tags().isArray() && !user.tags().isEmpty()
                        ? MAPPER.writeValueAsString(MAPPER.convertValue(user.tags(), Object.class))
                        : "[]";
                String fieldsJson = fieldsJson(user, username);
                int playerLimit = Math.max(3, playersPerUser.getOrDefault(user.id(), 0));

                preparedUsers.add(new PreparedUser(user, username, email, nickname, passwordHash,
                        fieldsJson, tagsJson, playerLimit));
            }
        }

        private void addRenamed(String userId, String sourceUsername, String importedUsername) {
            if (sourceUsername.equals(importedUsername)) return;
            for (Map<String, String> existing : renamedUsernames) {
                if (userId.equals(existing.get("userId"))) return;
            }
            Map<String, String> renamed = new LinkedHashMap<>();
            renamed.put("userId", userId);
            renamed.put("sourceUsername", sourceUsername);
            renamed.put("importedUsername", importedUsername);
            renamedUsernames.add(renamed);
        }

        private String fieldsJson(SourceUser user, String importedUsername) {
            try {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("legacySource", "yudream");
                fields.put("legacyUserId", user.id());
                fields.put("migrationBatch", batchId);
                fields.put("temporaryPassword", true);
                if (!importedUsername.equals(user.username())) {
                    fields.put("legacyUsername", user.username());
                }
                if (!user.avatar().isBlank()) fields.put("legacyAvatar", user.avatar());
                if (!"ACTIVE".equals(user.status()) && !user.status().isBlank()) {
                    fields.put("legacyStatus", user.status());
                }
                if (user.tags().isArray() && !user.tags().isEmpty()) {
                    fields.put("legacyTags", MAPPER.convertValue(user.tags(), Object.class));
                }
                if (user.messaging().isArray() && !user.messaging().isEmpty()) {
                    fields.put("messagingIdentities", MAPPER.convertValue(user.messaging(), Object.class));
                }
                if (!user.departments().isEmpty()) {
                    List<Map<String, Object>> depts = new ArrayList<>();
                    for (DeptRef dept : user.departments()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("deptId", dept.id());
                        item.put("name", dept.name());
                        item.put("default", dept.isDefault());
                        depts.add(item);
                    }
                    fields.put("sourceDepartments", depts);
                }
                return MAPPER.writeValueAsString(fields);
            } catch (Exception e) {
                throw new IllegalStateException("用户扩展字段序列化失败: " + user.id(), e);
            }
        }

        private String resolveUsername(String original, String userId, Set<String> usedNames) {
            String value = original == null ? "" : original.trim();
            if (value.isEmpty()) value = "user_" + shortHash(userId, 6);
            String key = value.toLowerCase(Locale.ROOT);
            if (!usedNames.contains(key)) return value;

            String base = value.replaceAll("[^A-Za-z0-9_]", "_").replaceAll("_+", "_");
            base = base.replaceAll("^_+|_+$", "");
            if (base.length() < 3) base = "user";
            for (int index = 2; index < 10_000; index++) {
                String suffix = "_" + index;
                int maxBase = Math.max(3, 16 - suffix.length());
                String candidateBase = base.length() > maxBase ? base.substring(0, maxBase) : base;
                String candidate = candidateBase + suffix;
                if (!candidate.matches("[A-Za-z0-9_]{3,16}")) continue;
                if (!usedNames.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
            }
            return "user_" + shortHash(userId, 8);
        }

        // --------------------------------------------------------------
        // 写入
        // --------------------------------------------------------------

        private void copyTextureFiles(Path textureDir) throws IOException {
            int copied = 0;
            for (SourceTexture texture : textures) {
                Path target = textureDir.resolve(texture.hash() + ".png");
                if (Files.exists(target)) {
                    byte[] current = Files.readAllBytes(target);
                    if (Arrays.equals(current, texture.bytes())) continue;
                }
                Files.createDirectories(target.getParent());
                Path tmp = target.resolveSibling(target.getFileName() + ".migration-tmp");
                Files.write(tmp, texture.bytes());
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                copied++;
            }
            importedCounts.put("textureFilesCopied", copied);
            importedCounts.put("textureFilesTotal", textures.size() - skippedTextures);
        }

        private void importAll(Connection connection, Exists exists) throws Exception {
            try (Statement tx = connection.createStatement()) {
                tx.execute("BEGIN IMMEDIATE");
                boolean committed = false;
                try {
                    ensureWalletTables(connection);
                    ensureUserColumns(connection);
                    importDepartments(connection, exists);
                    importRoles(connection, exists);
                    importUsers(connection);
                    resetPasswords(connection);
                    importUserRelations(connection);
                    importTextures(connection, exists);
                    importPlayers(connection, exists);
                    importCloset(connection);
                    importWallets(connection);
                    tx.execute("COMMIT");
                    committed = true;
                } finally {
                    if (!committed) {
                        try {
                            tx.execute("ROLLBACK");
                        } catch (SQLException ignored) {
                        }
                    }
                }
            }
            verify(connection);
        }

        private void ensureUserColumns(Connection connection) throws SQLException {
            boolean found = false;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("PRAGMA table_info(users)")) {
                while (result.next()) {
                    if ("must_change_password".equalsIgnoreCase(result.getString("name"))) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(
                            "ALTER TABLE users ADD COLUMN must_change_password INTEGER NOT NULL DEFAULT 0");
                }
            }
        }

        private void ensureWalletTables(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS wallet_assets (
                          code TEXT PRIMARY KEY,
                          name TEXT NOT NULL,
                          symbol TEXT,
                          scale INTEGER NOT NULL DEFAULT 0,
                          money INTEGER NOT NULL DEFAULT 0,
                          enabled INTEGER NOT NULL DEFAULT 1,
                          transfer_enabled INTEGER NOT NULL DEFAULT 0,
                          min_transfer_amount TEXT NOT NULL DEFAULT '1',
                          create_time TEXT NOT NULL,
                          update_time TEXT NOT NULL
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS wallet_balances (
                          user_id TEXT NOT NULL,
                          asset_code TEXT NOT NULL,
                          balance TEXT NOT NULL DEFAULT '0',
                          updated_at TEXT,
                          PRIMARY KEY (user_id, asset_code)
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS wallet_transactions (
                          id TEXT PRIMARY KEY,
                          business_no TEXT,
                          type TEXT NOT NULL,
                          source TEXT,
                          asset_code TEXT NOT NULL,
                          from_user_id TEXT,
                          to_user_id TEXT,
                          amount TEXT NOT NULL,
                          from_balance_after TEXT,
                          to_balance_after TEXT,
                          remark TEXT,
                          created_at TEXT NOT NULL
                        )
                        """);
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_wallet_balances_asset "
                        + "ON wallet_balances(asset_code)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_wallet_transactions_from "
                        + "ON wallet_transactions(from_user_id, created_at)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_wallet_transactions_to "
                        + "ON wallet_transactions(to_user_id, created_at)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_wallet_transactions_asset "
                        + "ON wallet_transactions(asset_code, created_at)");
            }
        }

        private void importDepartments(Connection connection, Exists exists) throws SQLException {
            int imported = 0;
            int sort = 10;
            for (Map.Entry<String, String> entry : departmentNames.entrySet()) {
                String id = entry.getKey();
                if (exists.deptIds.contains(id)) continue;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT OR IGNORE INTO departments
                        (id, name, description, parent_id, sort_order, dept_type, status, color, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, 'NORMAL', 'ACTIVE', ?, ?, ?)
                        """)) {
                    statement.setString(1, id);
                    statement.setString(2, entry.getValue().isBlank() ? "迁移部门 " + id : entry.getValue());
                    statement.setString(3, "由 YuDream 数据导出包迁移");
                    statement.setString(4, "1");
                    statement.setInt(5, sort++);
                    statement.setString(6, "#3b82f6");
                    statement.setString(7, now);
                    statement.setString(8, now);
                    imported += statement.executeUpdate();
                }
                exists.deptIds.add(id);
            }
            importedCounts.put("departments", imported);
        }

        private void importRoles(Connection connection, Exists exists) throws SQLException {
            int imported = 0;
            for (SourceRole role : sourceRoles.values()) {
                String code = role.code().isBlank() ? "yudream_" + shortHash(role.id(), 8) : role.code();
                if (roleIdByCode.containsKey(code)) {
                    sourceRoleToTarget.put(role.id(), roleIdByCode.get(code));
                    continue;
                }
                boolean standard = Set.of("super_admin", "admin", "user", "guest").contains(code);
                String id = role.id().isBlank() ? code : role.id();
                if (exists.roleIds.contains(id)) {
                    String existingCode = exists.roleCodes.get(id);
                    if (existingCode != null && !existingCode.equals(code)) {
                        issues.add("角色 ID 冲突，已跳过: " + id + "（源 " + code + "，现有 " + existingCode + "）");
                        continue;
                    }
                    sourceRoleToTarget.put(role.id(), id);
                    roleIdByCode.put(code, id);
                    continue;
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT OR IGNORE INTO roles
                        (id, name, dept_id, code, level, system_role, system_type, icon, color, permissions,
                         status, version, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 0, ?, ?)
                        """)) {
                    statement.setString(1, id);
                    statement.setString(2, role.name().isBlank() ? code : role.name());
                    statement.setString(3, standard ? "3" : null);
                    statement.setString(4, code);
                    statement.setString(5, standard ? standardLevel(code) : "USER");
                    statement.setInt(6, standard ? 1 : 0);
                    statement.setString(7, standard ? standardType(code) : null);
                    statement.setString(8, standard ? "morph:shield" : "morph:user");
                    statement.setString(9, standard ? "#f59e0b" : "#64748b");
                    statement.setString(10, "[]");
                    statement.setString(11, now);
                    statement.setString(12, now);
                    imported += statement.executeUpdate();
                }
                sourceRoleToTarget.put(role.id(), id);
                roleIdByCode.put(code, id);
                exists.roleIds.add(id);
            }
            importedCounts.put("roles", imported);
        }

        private void importUsers(Connection connection) throws SQLException {
            int imported = 0;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT OR IGNORE INTO users
                    (id, username, nickname, email, phone, qq, password, avatar_file_id, email_verified,
                     must_change_password, status, player_limit, tags, fields, version, create_time,
                     update_time, last_login_time, password_changed_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 0, 1, ?, ?, ?, ?, 0, ?, ?, NULL, NULL)
                    """)) {
                for (PreparedUser user : preparedUsers) {
                    SourceUser source = user.source();
                    statement.setString(1, source.id());
                    statement.setString(2, user.username());
                    statement.setString(3, user.nickname());
                    statement.setString(4, user.email());
                    statement.setString(5, blankToNull(source.phone()));
                    statement.setString(6, blankToNull(source.qq()));
                    statement.setString(7, user.passwordHash());
                    statement.setString(8, "DISABLED".equals(source.status()) ? "DISABLED" : "ACTIVE");
                    statement.setInt(9, user.playerLimit());
                    statement.setString(10, user.tagsJson());
                    statement.setString(11, user.fieldsJson());
                    statement.setString(12, now);
                    statement.setString(13, now);
                    imported += statement.executeUpdate();
                }
            }
            importedCounts.put("users", imported);
        }

        /**
         * 迁移账号统一使用初始密码。已自行修改过密码的账号（password_changed_time 非空）不会被覆盖。
         */
        private void resetPasswords(Connection connection) throws SQLException {
            if (initialPasswordHash.isEmpty() || sourceUserIds.isEmpty()) return;
            int reset = 0;
            List<String> ids = new ArrayList<>(sourceUserIds);
            for (int start = 0; start < ids.size(); start += 400) {
                List<String> chunk = ids.subList(start, Math.min(ids.size(), start + 400));
                String placeholders = String.join(",", chunk.stream().map(id -> "?").toList());
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users SET password = ?, password_changed_time = NULL, "
                                + "must_change_password = 1, email_verified = 0, version = version + 1 "
                                + "WHERE id IN (" + placeholders + ") "
                                + "AND (password_changed_time IS NULL OR password_changed_time = '')")) {
                    statement.setString(1, initialPasswordHash);
                    for (int index = 0; index < chunk.size(); index++) {
                        statement.setString(index + 2, chunk.get(index));
                    }
                    reset += statement.executeUpdate();
                }
            }
            importedCounts.put("passwordsReset", reset);
        }

        private void importUserRelations(Connection connection) throws SQLException {
            int roles = 0;
            int depts = 0;
            try (PreparedStatement roleStatement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO user_roles (user_id, role_id) VALUES (?, ?)");
                 PreparedStatement deptStatement = connection.prepareStatement(
                         "INSERT OR IGNORE INTO user_depts (user_id, dept_id, is_default) VALUES (?, ?, ?)")) {
                for (PreparedUser prepared : preparedUsers) {
                    SourceUser user = prepared.source();
                    for (RoleRef role : user.roles()) {
                        String targetRole = sourceRoleToTarget.get(role.id());
                        if (targetRole == null) {
                            issues.add("用户 " + user.id() + " 的角色未映射: " + role.code());
                            continue;
                        }
                        roleStatement.setString(1, user.id());
                        roleStatement.setString(2, targetRole);
                        roles += roleStatement.executeUpdate();
                    }
                    if (user.departments().isEmpty()) {
                        deptStatement.setString(1, user.id());
                        deptStatement.setString(2, "1");
                        deptStatement.setInt(3, 1);
                        depts += deptStatement.executeUpdate();
                    } else {
                        for (DeptRef dept : user.departments()) {
                            deptStatement.setString(1, user.id());
                            deptStatement.setString(2, dept.id());
                            deptStatement.setInt(3, dept.isDefault() ? 1 : 0);
                            depts += deptStatement.executeUpdate();
                        }
                    }
                }
            }
            importedCounts.put("userRoles", roles);
            importedCounts.put("userDepts", depts);
        }

        private void importTextures(Connection connection, Exists exists) throws SQLException {
            int imported = 0;
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT OR IGNORE INTO textures
                    (hash, type, model, name, width, height, is_public, uploaded_by, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
                 PreparedStatement find = connection.prepareStatement("SELECT id FROM textures WHERE hash = ?")) {
                for (SourceTexture texture : textures) {
                    String type = "cape".equals(texture.type()) ? "cape" : "skin";
                    String model = "slim".equals(texture.model()) ? "slim" : "classic";
                    if ("cape".equals(type)) model = "classic";
                    String name = truncate(texture.name().trim(), 48);
                    if (name.isEmpty()) name = texture.hash().substring(0, 8);
                    String uploader = firstKnownUser(texture.referencedByUserIds(), exists, importedUserIds);
                    insert.setString(1, texture.hash());
                    insert.setString(2, type);
                    insert.setString(3, model);
                    insert.setString(4, name);
                    insert.setInt(5, texture.width());
                    insert.setInt(6, texture.height());
                    insert.setInt(7, texture.publicAccess() ? 1 : 0);
                    insert.setString(8, uploader);
                    insert.setString(9, now);
                    imported += insert.executeUpdate();
                    find.setString(1, texture.hash());
                    try (ResultSet result = find.executeQuery()) {
                        if (result.next()) textureIds.put(texture.hash(), result.getInt(1));
                    }
                }
            }
            importedCounts.put("skinTextures", imported);
            if (!textureIds.isEmpty()) importedCounts.put("textureRowsAvailable", textureIds.size());
            int nonStandard = 0;
            for (SourceTexture texture : textures) {
                if (!standardTexture(texture)) nonStandard++;
            }
            if (nonStandard > 0) {
                notes.add("有 " + nonStandard + " 个材质不符合平台上传校验（索引色/4 位色深或超过 256px），已按原文件保留，重新上传时需转码。");
            }
        }

        private void importPlayers(Connection connection, Exists exists) throws SQLException {
            int imported = 0;
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT OR IGNORE INTO players
                    (user_id, name, uuid, model, skin_texture_id, cape_texture_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (SourcePlayer player : players) {
                    String ownerId = player.ownerId();
                    if (!exists.userIds.contains(ownerId) && !importedUserIds.contains(ownerId)) {
                        issues.add("角色所属用户不存在，已跳过: " + player.name() + " / " + ownerId);
                        skippedPlayers++;
                        playerFailures++;
                        continue;
                    }
                    String uuid = normalizeUuid(player.uuid());
                    if (uuid == null) {
                        issues.add("角色 UUID 不合法，已跳过: " + player.name() + " / " + player.uuid());
                        skippedPlayers++;
                        playerFailures++;
                        continue;
                    }
                    String name = player.name().trim();
                    if (name.isEmpty()) {
                        issues.add("角色名为空，已跳过: " + uuid);
                        skippedPlayers++;
                        playerFailures++;
                        continue;
                    }
                    String nameKey = name.toLowerCase(Locale.ROOT);
                    if (exists.playerUuids.contains(uuid)) {
                        skippedPlayers++;
                        continue;
                    }
                    if (exists.playerNames.contains(nameKey)) {
                        issues.add("角色名与现有数据冲突，已跳过: " + name + " / " + uuid);
                        skippedPlayers++;
                        playerFailures++;
                        continue;
                    }
                    Integer skinId = textureIds.get(player.skinHash());
                    Integer capeId = textureIds.get(player.capeHash());
                    if (!player.skinHash().isEmpty() && skinId == null) {
                        issues.add("角色皮肤材质缺失，已置空: " + name + " / " + player.skinHash());
                    }
                    if (!player.capeHash().isEmpty() && capeId == null) {
                        issues.add("角色披风材质缺失，已置空: " + name + " / " + player.capeHash());
                    }
                    String model = "slim".equals(player.skinModel()) ? "slim" : "classic";
                    insert.setString(1, ownerId);
                    insert.setString(2, name);
                    insert.setString(3, uuid);
                    insert.setString(4, model);
                    if (skinId == null) insert.setNull(5, java.sql.Types.INTEGER);
                    else insert.setInt(5, skinId);
                    if (capeId == null) insert.setNull(6, java.sql.Types.INTEGER);
                    else insert.setInt(6, capeId);
                    insert.setString(7, isoFromMillis(player.lastModified(), now));
                    int changed = insert.executeUpdate();
                    imported += changed;
                    exists.playerNames.add(nameKey);
                    exists.playerUuids.add(uuid);
                    exists.importedUuids.add(uuid);
                }
            }
            importedCounts.put("skinPlayers", imported);
        }

        private void importCloset(Connection connection) throws SQLException {
            int imported = 0;
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT OR IGNORE INTO closet_items (user_id, texture_id, name, created_at)
                    VALUES (?, ?, ?, ?)
                    """)) {
                for (SourceCloset item : closet) {
                    if (!sourceUserIds.contains(item.userId())) {
                        issues.add("衣柜所属用户不存在，已跳过: " + item.userId() + " / " + item.hash());
                        skippedCloset++;
                        closetFailures++;
                        continue;
                    }
                    Integer textureId = textureIds.get(item.hash());
                    if (textureId == null) {
                        issues.add("衣柜材质缺失，已跳过: " + item.userId() + " / " + item.hash());
                        skippedCloset++;
                        closetFailures++;
                        continue;
                    }
                    String name = item.name().trim();
                    insert.setString(1, item.userId());
                    insert.setInt(2, textureId);
                    if (name.isEmpty()) insert.setNull(3, java.sql.Types.VARCHAR);
                    else insert.setString(3, name);
                    insert.setString(4, isoFromMillis(item.createdAt(), now));
                    imported += insert.executeUpdate();
                }
            }
            importedCounts.put("skinClosetItems", imported);
        }

        private void importWallets(Connection connection) throws SQLException {
            int assetCount = 0;
            int balanceCount = 0;
            int transactionCount = 0;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT OR IGNORE INTO wallet_assets
                    (code, name, symbol, scale, money, enabled, transfer_enabled, min_transfer_amount,
                     create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (SourceAsset asset : assets) {
                    statement.setString(1, asset.code());
                    statement.setString(2, asset.name().isBlank() ? asset.code() : asset.name());
                    statement.setString(3, asset.symbol());
                    statement.setInt(4, asset.scale());
                    statement.setInt(5, asset.money() ? 1 : 0);
                    statement.setInt(6, asset.enabled() ? 1 : 0);
                    statement.setInt(7, asset.transferEnabled() ? 1 : 0);
                    statement.setString(8, asset.minTransferAmount().isBlank() ? "1" : asset.minTransferAmount());
                    statement.setString(9, now);
                    statement.setString(10, now);
                    assetCount += statement.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT OR IGNORE INTO wallet_balances (user_id, asset_code, balance, updated_at)
                    VALUES (?, ?, ?, ?)
                    """)) {
                for (SourceBalance balance : balances) {
                    statement.setString(1, balance.userId());
                    statement.setString(2, balance.assetCode());
                    statement.setString(3, balance.balance());
                    statement.setString(4, isoFromMillis(balance.updatedAt(), now));
                    balanceCount += statement.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT OR IGNORE INTO wallet_transactions
                    (id, business_no, type, source, asset_code, from_user_id, to_user_id, amount,
                     from_balance_after, to_balance_after, remark, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (SourceTransaction transaction : transactions) {
                    statement.setString(1, transaction.id());
                    statement.setString(2, blankToNull(transaction.businessNo()));
                    statement.setString(3, transaction.type().isBlank() ? "UNKNOWN" : transaction.type());
                    statement.setString(4, transaction.source());
                    statement.setString(5, transaction.assetCode());
                    statement.setString(6, blankToNull(transaction.fromUserId()));
                    statement.setString(7, blankToNull(transaction.toUserId()));
                    statement.setString(8, transaction.amount());
                    statement.setString(9, blankToNull(transaction.fromBalanceAfter()));
                    statement.setString(10, blankToNull(transaction.toBalanceAfter()));
                    statement.setString(11, transaction.remark());
                    statement.setString(12, isoFromMillis(transaction.createdAt(), now));
                    transactionCount += statement.executeUpdate();
                }
            }
            importedCounts.put("walletAssets", assetCount);
            importedCounts.put("walletBalances", balanceCount);
            importedCounts.put("walletTransactions", transactionCount);
        }

        // --------------------------------------------------------------
        // 校验与报告
        // --------------------------------------------------------------

        private void verify(Connection connection) throws Exception {
            int expectedUsers = sourceUserIds.size();
            int actualUsers = countIds(connection, "users", "id", sourceUserIds);
            if (actualUsers != expectedUsers) {
                issues.add("校验数量不一致: users 期望 " + expectedUsers + "，实际 " + actualUsers);
            }

            Set<String> hashes = new LinkedHashSet<>();
            for (SourceTexture texture : textures) hashes.add(texture.hash());
            int expectedTextures = textures.size();
            int actualTextures = countIds(connection, "textures", "hash", hashes);
            if (actualTextures != expectedTextures) {
                issues.add("校验数量不一致: skinTextures 期望 " + expectedTextures + "，实际 " + actualTextures);
            }

            int expectedPlayers = Math.max(0, sourcePlayerUuids.size() - playerFailures);
            int actualPlayers = countIds(connection, "players", "uuid", sourcePlayerUuids);
            if (actualPlayers != expectedPlayers) {
                issues.add("校验数量不一致: skinPlayers 期望 " + expectedPlayers + "，实际 " + actualPlayers);
            }

            int expectedCloset = Math.max(0, closet.size() - closetFailures);
            int actualCloset = countCloset(connection, sourceUserIds);
            if (actualCloset < expectedCloset) {
                issues.add("校验数量不一致: skinClosetItems 期望至少 " + expectedCloset + "，实际 " + actualCloset);
            }

            int actualAssets = countTable(connection, "wallet_assets");
            int actualBalances = countTable(connection, "wallet_balances");
            int actualTransactions = countTable(connection, "wallet_transactions");
            if (actualAssets < assets.size()) {
                issues.add("校验数量不一致: walletAssets 期望 " + assets.size() + "，实际 " + actualAssets);
            }
            if (actualBalances < balances.size()) {
                issues.add("校验数量不一致: walletBalances 期望 " + balances.size() + "，实际 " + actualBalances);
            }
            if (actualTransactions < transactions.size()) {
                issues.add("校验数量不一致: walletTransactions 期望 " + transactions.size() + "，实际 " + actualTransactions);
            }

            importedCounts.put("verifiedUsers", actualUsers);
            importedCounts.put("verifiedTextures", actualTextures);
            importedCounts.put("verifiedPlayers", actualPlayers);
            importedCounts.put("verifiedClosetItems", actualCloset);
            importedCounts.put("verifiedWalletAssets", actualAssets);
            importedCounts.put("verifiedWalletBalances", actualBalances);
            importedCounts.put("verifiedWalletTransactions", actualTransactions);

            int files = 0;
            int badFiles = 0;
            Path textureDir = options.dataDir.toAbsolutePath().normalize().resolve("textures");
            for (SourceTexture texture : textures) {
                Path file = textureDir.resolve(texture.hash() + ".png");
                if (!Files.isRegularFile(file)) {
                    badFiles++;
                    continue;
                }
                if (!sha256(Files.readAllBytes(file)).equals(texture.hash())) {
                    badFiles++;
                    continue;
                }
                files++;
            }
            importedCounts.put("verifiedTextureFiles", files);
            if (badFiles > 0) issues.add("有 " + badFiles + " 个材质文件缺失或校验失败。");
        }

        private int countIds(Connection connection, String table, String column, Set<String> ids) throws SQLException {
            if (ids.isEmpty()) return 0;
            int total = 0;
            List<String> values = new ArrayList<>(ids);
            for (int start = 0; start < values.size(); start += 400) {
                List<String> chunk = values.subList(start, Math.min(values.size(), start + 400));
                String placeholders = String.join(",", chunk.stream().map(id -> "?").toList());
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM " + table + " WHERE " + column + " IN (" + placeholders + ")")) {
                    for (int i = 0; i < chunk.size(); i++) statement.setString(i + 1, chunk.get(i));
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) total += result.getInt(1);
                    }
                }
            }
            return total;
        }

        private int countTable(Connection connection, String table) throws SQLException {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                return result.next() ? result.getInt(1) : 0;
            }
        }

        private int countCloset(Connection connection, Set<String> userIds) throws SQLException {
            if (userIds.isEmpty()) return 0;
            int total = 0;
            List<String> ids = new ArrayList<>(userIds);
            for (int start = 0; start < ids.size(); start += 400) {
                List<String> chunk = ids.subList(start, Math.min(ids.size(), start + 400));
                String placeholders = String.join(",", chunk.stream().map(id -> "?").toList());
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM closet_items WHERE user_id IN (" + placeholders + ")")) {
                    for (int i = 0; i < chunk.size(); i++) statement.setString(i + 1, chunk.get(i));
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) total += result.getInt(1);
                    }
                }
            }
            return total;
        }

        private void writeReport(Path file, Path sourcePath) throws IOException {
            ObjectNode report = MAPPER.createObjectNode();
            report.put("generatedAt", now);
            report.put("batchId", batchId);
            report.put("source", sourcePath.toString());
            report.put("exportId", exportId);
            report.put("dryRun", options.dryRun);
            report.put("database", options.dataDir.toAbsolutePath().normalize().resolve("swustmc.db").toString());
            report.put("initialPassword", options.initialPassword);
            report.set("sourceCounts", MAPPER.valueToTree(sourceCounts));
            report.set("importedCounts", MAPPER.valueToTree(importedCounts));
            report.set("duplicateEmails", MAPPER.valueToTree(duplicateEmails));
            report.set("droppedUserIds", MAPPER.valueToTree(droppedUserIds));
            report.set("renamedUsernames", MAPPER.valueToTree(renamedUsernames));
            report.put("skipped", MAPPER.createObjectNode()
                    .put("users", skippedUsers)
                    .put("textures", skippedTextures)
                    .put("players", skippedPlayers)
                    .put("closetItems", skippedCloset)
                    .put("duplicateEmailUsers", droppedUserIds.size()));
            ArrayNode issueArray = report.putArray("issues");
            for (String issue : issues) issueArray.add(issue);
            ArrayNode noteArray = report.putArray("notes");
            noteArray.add("迁移账号初始密码统一为 " + options.initialPassword + "，请提醒用户首次登录后尽快修改。");
            noteArray.add("迁移账号首次登录必须完成邮箱验证并修改初始密码；已完成改密的账号不会被再次强制。");
            noteArray.add("导出包不含密码，已改过密码的账号不会被重置；password_changed_time 为空的历史迁移账号会设置为初始密码。");
            noteArray.add("同一邮箱存在多个账号时只保留一个；其余账号及其角色、角色档案、衣柜数据不会导入，钱包记录按原用户 ID 保留。");
            noteArray.add("重复邮箱账号的保留选择在首次导入后应保持一致；迁移不会删除平台上已存在的账号。");
            noteArray.add("钱包数据写入 wallet_assets / wallet_balances / wallet_transactions，平台当前没有钱包界面。");
            noteArray.add("用户头像只保留了源地址（fields.legacyAvatar），导出包不含头像文件。");
            noteArray.add("平台用户名规则为 3–16 位字母、数字或下划线；源数据中不合规但唯一的用户名按原样保留。");
            noteArray.add("源数据材质没有单一上传者，私有材质的 uploaded_by 取第一个引用用户，用于保持私有材质的可访问性。");
            if (!renamedUsernames.isEmpty()) {
                noteArray.add("有 " + renamedUsernames.size() + " 个用户名因大小写或完全重复被改名，映射见 renamedUsernames。");
            }
            for (String note : notes) noteArray.add(note);
            Files.writeString(file, MAPPER.writeValueAsString(report), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------
    // 数据库辅助
    // ------------------------------------------------------------------

    private record Exists(Set<String> userIds, Set<String> usernames, Set<String> emails,
                          Set<String> roleIds, Map<String, String> roleCodes,
                          Set<String> deptIds, Set<String> textureHashes,
                          Set<String> playerUuids, Set<String> playerNames,
                          Set<String> importedUuids, Map<String, String> usernamesById) {
    }

    private static Exists loadExists(Connection connection) throws SQLException {
        Set<String> userIds = new HashSet<>();
        Set<String> usernames = new HashSet<>();
        Set<String> emails = new HashSet<>();
        Map<String, String> usernamesById = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id, username, email FROM users")) {
            while (result.next()) {
                String userId = result.getString(1);
                userIds.add(userId);
                String username = result.getString(2);
                if (username != null) {
                    usernames.add(username.toLowerCase(Locale.ROOT));
                    usernamesById.put(userId, username);
                }
                String email = result.getString(3);
                if (email != null) emails.add(email.toLowerCase(Locale.ROOT));
            }
        }
        Set<String> roleIds = new HashSet<>();
        Map<String, String> roleCodes = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id, code FROM roles")) {
            while (result.next()) {
                roleIds.add(result.getString(1));
                roleCodes.put(result.getString(1), result.getString(2));
            }
        }
        Set<String> deptIds = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id FROM departments")) {
            while (result.next()) deptIds.add(result.getString(1));
        }
        Set<String> textureHashes = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT hash FROM textures")) {
            while (result.next()) textureHashes.add(result.getString(1));
        }
        Set<String> playerUuids = new HashSet<>();
        Set<String> playerNames = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT uuid, name FROM players")) {
            while (result.next()) {
                playerUuids.add(androidLikeLower(result.getString(1)));
                String name = result.getString(2);
                if (name != null) playerNames.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return new Exists(userIds, usernames, emails, roleIds, roleCodes, deptIds,
                textureHashes, playerUuids, playerNames, new HashSet<>(), usernamesById);
    }

    private static Connection open(Path dbFile) throws Exception {
        Class.forName("org.sqlite.JDBC");
        Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 15000");
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
        }
        return connection;
    }

    // ------------------------------------------------------------------
    // 通用辅助
    // ------------------------------------------------------------------

    private static JsonNode json(ExportSource source, String name) throws IOException {
        return MAPPER.readTree(source.read(name));
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return "";
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return "";
        return value.asText("");
    }

    private static int intValue(JsonNode node, String field) {
        if (node == null) return 0;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? 0 : value.asInt(0);
    }

    private static long longValue(JsonNode node, String field, long fallback) {
        if (node == null) return fallback;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asLong(fallback);
    }

    private static boolean boolValue(JsonNode node, String field, boolean fallback) {
        if (node == null) return fallback;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asBoolean(fallback);
    }

    private static String firstKnownUser(List<String> candidates, Exists exists, Set<String> importedUserIds) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            if (exists.userIds.contains(candidate) || importedUserIds.contains(candidate)) return candidate;
        }
        return null;
    }

    private static String standardLevel(String code) {
        return switch (code) {
            case "super_admin" -> "SUPER_ADMIN";
            case "admin" -> "ADMIN";
            case "guest" -> "GUEST";
            default -> "USER";
        };
    }

    private static String standardType(String code) {
        return switch (code) {
            case "super_admin" -> "SUPER_ADMIN";
            case "admin" -> "ADMIN";
            case "guest" -> "GUEST";
            default -> "USER";
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String shortHash(String value, int length) {
        String hash = sha256((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        return hash.substring(0, Math.min(length, hash.length()));
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeUuid(String value) {
        if (value == null) return null;
        String uuid = value.replace("-", "").trim().toLowerCase(Locale.ROOT);
        return uuid.matches("^[0-9a-f]{32}$") ? uuid : null;
    }

    private static String androidLikeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String isoFromMillis(long millis, String fallback) {
        return millis > 0 ? Instant.ofEpochMilli(millis).toString() : fallback;
    }

    private static boolean standardTexture(SourceTexture texture) {
        return texture.uploadCompatible();
    }

    private static boolean pngUploadCompatible(byte[] buffer, int[] size, String type) {
        if (buffer.length < 26 || size == null) return false;
        int bitDepth = buffer[24] & 0xff;
        int colorType = buffer[25] & 0xff;
        if (bitDepth != 8 || (colorType != 2 && colorType != 6)) return false;
        int width = size[0];
        int height = size[1];
        if (width > 256 || height > 256) return false;
        boolean scaled = (width == 64 || width == 128 || width == 256)
                && (height == width || height * 2 == width);
        if ("skin".equals(type)) return scaled;
        if ("cape".equals(type)) return (width == 22 && height == 17) || scaled;
        return false;
    }

    private static int[] pngSize(byte[] buffer) {
        if (buffer == null || buffer.length < 33) return null;
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        for (int i = 0; i < signature.length; i++) {
            if (buffer[i] != signature[i]) return null;
        }
        int width = readInt(buffer, 16);
        int height = readInt(buffer, 20);
        if (width <= 0 || height <= 0) return null;
        return new int[]{width, height};
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 24)
                | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8)
                | (data[offset + 3] & 0xff);
    }

    private static String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(value);
            StringBuilder builder = new StringBuilder(result.length * 2);
            for (byte b : result) builder.append(String.format("%02x", b));
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
