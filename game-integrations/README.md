# Minecraft Server Report

服务端游戏插件/模组，用于向皮肤站插件 `minecraft-server` 上报：

- 当前在线玩家和最大人数
- 每名玩家的总在线时长
- 挂机时长（默认 5 分钟无移动/视角/交互视为挂机）
- 客户端版本和加载器类型

## 站点配置

1. 管理后台 → **MC 服务器** → 添加服务器，得到服务器 ID。
2. 管理后台 → **API 密钥** → 新建密钥：
   - 所属用户：社团管理员或专用服务账号
   - 权限：只勾选 **使用 Minecraft 服务器**
   - 复制 `sk_...` 密钥
3. 游戏端配置：

```properties
base-url=https://www.swustmc.com
server-id=srv_xxxxxxxx
api-key=sk_xxxxxxxxx
report-interval-seconds=30
afk-timeout-minutes=5
http-timeout-seconds=10
debug=false
```

论文/公告里不要把 API Key 贴出来。API Key 只应放在游戏服务器的本地配置文件中。

## Paper

构建：

```bash
cd game-integrations
mvn -q -DskipTests package
```

产物：

```text
game-integrations/dist/paper/universal/minecraft-server-report-paper.jar
```

Fabric 代表产物：

```text
game-integrations/dist/fabric/1.18/minecraft-server-report-fabric-1.18-1.0.0.jar
game-integrations/dist/fabric/1.19/minecraft-server-report-fabric-1.19-1.0.0.jar
game-integrations/dist/fabric/1.20/minecraft-server-report-fabric-1.20-1.0.0.jar
game-integrations/dist/fabric/1.21/minecraft-server-report-fabric-1.21-1.0.0.jar
game-integrations/dist/fabric/26/minecraft-server-report-fabric-26-1.0.0.jar
```

Forge 代表产物：

```text
game-integrations/dist/forge/1.18/minecraft-server-report-forge-1.18-1.0.0.jar
game-integrations/dist/forge/1.19/minecraft-server-report-forge-1.19-1.0.0.jar
game-integrations/dist/forge/1.20/minecraft-server-report-forge-1.20-1.0.0.jar
```

NeoForge 1.21 代表产物：

```text
game-integrations/dist/neoforge/1.21/minecraft-server-report-neoforge-1.21-1.0.0.jar
```

Fabric 26.x 使用 `fabric26/` 的 official-name 适配源；1.18–1.21 使用 Yarn 映射。Fabric 构建需要可访问的 Fabric Maven；受限网络可将 `maven.fabricmc.net` 替换为 CreeperHost 镜像。Forge 1.18 / 1.19 在 Windows 上可能触发 ForgeGradle 的映射产物注册问题，推荐在 Linux/WSL 中使用 `forge5/`、Gradle 7.6.4 和 JDK 17 构建；Forge 1.20 可直接使用 `forge/`。NeoForge 1.21 使用 JDK 21。受限网络下 Forge / NeoForge 依赖可通过 `https://maven.creeperhost.net` 获取。

安装到 `plugins/`，首次启动会生成：

```text
plugins/MinecraftServerReport/config.properties
plugins/MinecraftServerReport/playtime.properties
```

单个 Paper JAR 使用 Bukkit 稳定 API，面向 1.18–26.3；编译目标 Java 17，可在更高 Java 版本运行。

## Fabric / Forge / NeoForge

共享核心位于 `core/`，各加载器只负责事件和配置目录适配。

- Fabric：`fabric/`
- Forge：`forge/`
- NeoForge：`neoforge/`

版本矩阵在 `versions.json`。每个 minor family 生成一个 JAR，并通过 loader 的版本范围覆盖该 family 的所有已发布小版本：

- Fabric：1.18.x / 1.19.x / 1.20.x / 1.21.x / 26.x
- Forge：1.18.x / 1.19.x / 1.20.1
- NeoForge：1.20.2–1.20.6 / 1.21.x / 26.x（仅加载器已发布时）
- 1.21+ 不使用 Forge

构建矩阵：

```powershell
./build-matrix.ps1 -DryRun
./build-matrix.ps1
```

Linux/macOS 使用 `build-matrix.sh`（同样先构建 `core` 和 `paper`）。需要：

- JDK 17（Forge 1.18–1.20）
- JDK 21/25（现代 Fabric/NeoForge）
- 各加载器自己的 Gradle 环境和网络依赖

26.3 的 NeoForge 是否已发布由构建前元数据检查决定；未发布的组合不会生成 JAR。Fabric 26.x 使用未混淆官方名，若当前 Yarn 不提供映射，需要单独的 official-name 适配源；矩阵会跳过而不是产出不可用文件。

## 数据与隐私

- 普通访客只能看到服务器在线人数和在线状态。
- 登录用户只能看到自己的总在线、活跃和挂机时长。
- 管理员可在后台看到全部玩家和未匹配记录。
- 游戏玩家先按 UUID 匹配站点角色，未匹配再按角色名匹配。
- 重复上报使用最大累计值，避免网络重试重复计时。
- 游戏服务器断网时，本地 `playtime.properties` 继续累计，恢复后下个心跳补报。

## 上报协议

```http
POST /api/plugins/minecraft-server/report
Authorization: Bearer sk_...
Content-Type: application/json
```

```json
{
  "serverId": "srv_xxxxxxxx",
  "observedAt": "2026-11-07T12:00:00Z",
  "sequence": 123,
  "meta": {
    "version": "1.20.1",
    "loader": "paper",
    "maxPlayers": 20
  },
  "online": [
    {
      "uuid": "0123456789abcdef0123456789abcdef",
      "name": "Steve",
      "afk": false,
      "sessionStartedAt": "2026-11-07T11:00:00Z"
    }
  ],
  "players": [
    {
      "uuid": "0123456789abcdef0123456789abcdef",
      "name": "Steve",
      "totalMillis": 7200000,
      "afkMillis": 1200000,
      "lastSeenAt": "2026-11-07T12:00:00Z"
    }
  ]
}
```
