<p align="center">
  <img src="public/assets/brand/badge.png" width="150" alt="西南科技大学 Minecraft 星空社徽章" />
</p>

<h1 align="center">西南科技大学 Minecraft 星空社皮肤站</h1>

<p align="center">
  SWUSTMC Skin · Java 独立皮肤站、3D 衣柜与 authlib-injector 联机认证
</p>

<p align="center">
  Java 21 · SQLite · WebGL 3D · Yggdrasil · Java JAR 插件
</p>

---

## 这是什么

这是西南科技大学 Minecraft 星空社的独立皮肤站。社员可以使用**用户名或邮箱**注册登录，创建多个游戏角色，上传皮肤与披风，在网页里用 3D 查看和换装，再通过 **authlib-injector / Yggdrasil** 把外观带进社团服务器。

项目已完整迁移到 Java：使用 JDK 自带的 `com.sun.net.httpserver` 与虚拟线程处理请求，SQLite 持久化，Jackson 处理 JSON，BCrypt 存储密码，不依赖 Spring、PHP、MongoDB、Redis 或对象存储。前端仍是无需构建的原生 ES Module 单页应用。

## 功能

| 模块 | 能力 |
| --- | --- |
| 账号 | 用户名 / 邮箱登录、邮箱验证码注册、会话、CSRF、登录限流、停用 / 启用、改邮箱、改密码 |
| 权限 | 系统角色、部门、权限目录、等级权重、系统角色保护、角色分配、用户额外权限 |
| 角色 | 一人多角色（默认 3 个，管理员可按用户调整，上限 100）、32 位角色 UUID、经典 / 纤细模型、重命名、删除、装备皮肤与披风 |
| 材质 | PNG 结构与尺寸校验、SHA-256 去重、公开 / 私有、上传、下载、编辑、删除 |
| 皮肤库 | 公开浏览、搜索、类型筛选、排序、分页、3D 详情、收藏到衣柜 |
| 衣柜 | 收藏、重命名、移除、装备、下载 |
| 3D | 纯 WebGL 角色渲染，支持皮肤、披风、叠层、旋转、缩放、自动旋转、2D 降级 |
| 联机 | Yggdrasil 认证、刷新、校验、登出、session join / hasJoined、RSA 材质签名、authlib-injector 元数据 |
| 插件 | 目录扫描、JAR / ZIP 安装、启用 / 停用、热重载、卸载、Java 服务端路由与事件钩子、前端远程模块 |
| 管理 | 白色工作台布局与可折叠分类侧栏；用户、角色权限、额外权限、角色档案、材质、插件、API 密钥、站点文案与开关；用户数据导出 |
| 外部集成 | 用户级 API 密钥，权限范围、有效期、最近使用时间、随时撤销；支持 Bearer / X-API-Key |
| 品牌 | 星空社徽章、深海军蓝 / 星金配色、响应式首页、社团文案与联机指引 |
| 首页 | 管理后台可上传多张首页主图（PNG / JPG / WebP，最多 8 张）并自动循环滚动、支持左右箭头切换；未上传时使用社团徽章占位；登录后展示可拖入 PCL2 / HMCL 的认证卡片 |

内置四个示例插件：

- **服务器状态**：Java 插件，真实 TCP 探测服务器在线状态与延迟，管理员可在页面内维护服务器地址。
- **随机皮肤**：纯前端插件，从公开皮肤库随机抽 9 件作品。
- **MC 百科**：Java 插件，从 Mojang 官方客户端 / 服务端 JAR 导入物品、方块、生物与合成配方，支持多版本发布、3D 物品图标与后台导入任务。
- **社团活动**：Java 插件，活动发布与报名、参与核验、活动身份信息管理，支持 DOCX 证明模板与多活动联动导出。

## 快速开始

要求：**JDK 21+**、**Maven 3.9+**。

```bash
# 1. 构建
mvn -DskipTests package

# 2. 可选：准备环境变量
cp .env.example .env

# 3. 启动
java -jar target/swust-skin.jar
```

Windows 可以直接双击或在终端运行：

```bat
run.bat
```

Linux / macOS：

```bash
./run.sh
```

打开 <http://localhost:3000>。首次启动会自动创建 SQLite 数据库、管理员与基础权限数据。

Windows 下如果需要让局域网启动器访问，请把 `.env` 里的 `BASE_URL` 改成电脑的局域网地址，例如：

```env
BASE_URL=http://192.168.1.20:3000
```

### 默认账号

| 角色 | 用户名 | 邮箱 | 密码 |
| --- | --- | --- | --- |
| 管理员 | `admin` | `admin@example.com` | `swustmc2026` |
| 演示社员 | `xingkong` | `xingkong@example.com` | `swust123` |
| 演示社员 | `beichen` | `beichen@example.com` | `swust123` |
| 演示社员 | `yunduo` | `yunduo@example.com` | `swust123` |
| 演示社员 | `orion` | `orion@example.com` | `swust123` |

> 正式部署前务必修改管理员密码，并删除或停用演示账号。Git 仓库不包含 `data/`、数据库、RSA 密钥或用户上传文件；全新克隆首次启动只会创建管理员与系统角色。上表演示账号来自现有部署数据。

## 首页主图与联机卡片

管理员可在「管理后台 → 站点设置 → 首页主图」上传多张 PNG / JPG / WebP 图片（单张最大 8MB，最多 8 张），首页右侧会按上传顺序自动循环滚动播放，也可用左右箭头手动切换；全部移除后自动回退为社团徽章。文件按内容哈希存放在 `data/branding/`，移除时会清理对应文件。

登录后，首页按钮下方会显示 authlib-injector 认证卡片。把卡片直接拖到 PCL2 / HMCL 窗口即可添加认证服务器；也可以点击复制按钮手动填写。

## 系统日志

管理后台的「系统 → 系统日志」可以实时查看并下载以下日志：

- **全部系统日志**：systemd journald 中的全部系统日志。
- **本站服务**：仅 `swust-skin` 服务的 journald 输出，包含 JVM 与插件终端输出。
- **应用日志**：`data/logs/` 下的应用日志文件。
- **Nginx 访问 / 错误日志**：当前文件及轮转文件；`.gz` 归档可直接下载。
- journald 日志支持按最近 1 小时、24 小时、7 天、30 天导出。
- systemd journald 与 Nginx 轮转日志均保留 30 天。
- 游戏端心跳不会逐条记录玩家明细，只在玩家上线、离线时写一条日志。

应用日志文件存放在 `data/logs/`：

- `swust-skin.log`：当前运行日志，每行写入后立即刷新。
- `swust-skin-YYYYMMDD-HHmmss.log`：每日、超过 8MB 或服务重启时自动归档的历史日志。
- 存档默认保留 30 天，最多保留 500 个文件。
- 日志目录和当前日志在 Linux 上使用仅服务账号可读写的权限。

在 Linux 上读取 journald 与 Nginx 日志需要给服务账号授权：

```bash
sudo usermod -aG systemd-journal,adm swust
sudo systemctl restart swust-skin
```

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `HOST` | `0.0.0.0` | 监听地址 |
| `PORT` | `3000` | 监听端口 |
| `BASE_URL` | `http://localhost:<PORT>` | 对外访问地址，**Yggdrasil 材质 URL 与签名域名依赖它** |
| `DATA_DIR` | `./data` | SQLite、材质文件、RSA 密钥目录 |
| `PLUGINS_DIR` | `./plugins` | 插件目录 |
| `ADMIN_USERNAME` | `admin` | 首次启动创建的管理员用户名 |
| `ADMIN_EMAIL` | `admin@example.com` | 首次启动创建的管理员邮箱 |
| `ADMIN_PASSWORD` | `swustmc2026` | 首次启动创建的管理员密码 |
| `SNOWFLAKE_DATA_CENTER_ID` | `1` | 雪花 ID 数据中心号（0–31） |
| `SNOWFLAKE_MACHINE_ID` | `1` | 雪花 ID 机器号（0–31）；多实例部署时需保证组合唯一 |
| `TRUST_PROXY` | `0` | 位于 Nginx / Caddy 后时设为 `1`，读取 `X-Forwarded-For` |
| `SYSTEMD_UNIT` | `swust-skin` | 系统日志页面「本站服务」使用的 systemd 单元名 |
| `NGINX_LOG_DIR` | `/var/log/nginx` | Nginx 访问 / 错误日志目录 |
| `MAIL_ENABLED` | `0` | 设为 `1` 后注册必须完成邮箱验证码 |
| `MAIL_HOST` | 空 | SMTP 服务器，例如 `smtpdm.aliyun.com` |
| `MAIL_PORT` | `465` | SMTP 端口 |
| `MAIL_SSL` | `1` | 端口 465 使用 SSL 时保持 `1` |
| `MAIL_USERNAME` | 空 | SMTP 登录用户名，通常是完整发信地址 |
| `MAIL_PASSWORD` | 空 | SMTP 专用密码，不要使用账号登录密码 |
| `MAIL_FROM` | 同 `MAIL_USERNAME` | 发信地址 |
| `MAIL_FROM_NAME` | `星空社皮肤站` | 发件人显示名称 |

`.env` 与系统环境变量同时存在时，系统环境变量优先。

启用 `MAIL_ENABLED=1` 后，注册页会发送 6 位邮箱验证码，验证码 10 分钟内有效，同一邮箱 120 秒后可重发；同一邮箱 10 分钟内最多发送 5 次。同一 IP 每个自然日最多发送 5 封验证码邮件。SMTP 密码只应保存在 `.env`，不要提交到仓库。

## 数据、备份与迁移

运行数据都在 `DATA_DIR`：

```text
data/
├── swustmc.db              SQLite 数据库
├── textures/               上传的 PNG 材质
└── keys/                   Yggdrasil RSA 私钥与公钥
```

备份时请整体备份 `data/`，尤其是 `data/keys/`。更换服务器后必须保留同一对密钥，否则启动器缓存的材质签名会失效。

### 角色 UUID 规则

角色 UUID 由站点命名空间与角色名生成：

```text
UUID.nameUUIDFromBytes(("SWUSTMCSkin:" + name.toLowerCase()).getBytes(UTF_8))
```

去掉连字符后输出 32 位小写十六进制。角色改名保留原 UUID，避免服务器玩家数据失效。启动时会把旧数据中的角色 UUID 迁移到该命名空间，并清理旧的 Yggdrasil token 与 join 记录。

### 用户数据导出

管理员可在「站点设置 → 用户数据导出」下载，或直接访问：

```text
GET /api/admin/export
```

文件包含 `sysUser`、`sysRole`、`sysDept`、`sysPermission` 四个集合；Long 类型 ID 以十进制字符串输出，密码为 BCrypt，可直接映射到使用相同字段结构的外部用户中心。

## 联机认证（authlib-injector）

站点实现了 Yggdrasil 兼容端点，入口为：

```text
${BASE_URL}/api/yggdrasil
```

在 HMCL / PCL2 等启动器中添加「认证服务器」，地址填上面的 URL，然后使用本站**用户名或邮箱 + 密码**登录。启动器会请求站点的 `authserver`、`sessionserver` 端点，并读取带 RSA-SHA256 签名的 `textures` 属性。

服务端建议保持离线模式（`online-mode=false`），玩家 ID 与站内角色名保持一致。

> `BASE_URL` 必须是启动器可访问的地址；`localhost` 只适合本机测试。公网部署请使用 HTTPS 域名，并确保服务器能访问 `/textures/<hash>.png`。

## 用户额外权限

角色权限之外，管理员可以在「用户管理 → 编辑用户 → 额外权限」中给单个用户追加权限。额外权限是**增量授权**：不会覆盖或删除角色继承的权限，用户最终权限为「角色权限 ∪ 额外权限」。角色继承的权限在弹窗中显示为勾选且不可取消，额外权限可单独勾选或取消。

接口：

```text
PUT /api/admin/users/:id/permissions
{ "permissions": ["skin:texture:view", "plugin:server-status:use"] }
```

管理员只能授予自己当前拥有的权限；通配权限 `*` 不能直接授予单个用户。

## API 密钥

管理后台左侧「插件与扩展 → API 密钥」可以为用户签发外部集成密钥。密钥只在创建时显示一次，数据库只保存 SHA-256 哈希与可识别前缀。

- 请求头：`Authorization: Bearer sk_...` 或 `X-API-Key: sk_...`
- 权限范围：创建时按功能逐项勾选，固化为权限码列表；支持管理接口与插件接口。
- 插件权限会随插件安装自动出现在权限列表中，新插件无需修改 API 密钥代码即可纳入授权。
- 有效期：30 / 90 / 365 天或永久；可随时撤销。
- 可访问：已勾选权限对应的管理接口、插件接口，以及公开查询与材质读取接口。
- 不可访问：账号与登录、玩家与衣柜等用户私有接口，以及未授权权限对应的接口。
- API 密钥按用户当前有效权限求交集，角色或额外权限被收回后，密钥权限同步缩小。

## Minecraft 服务器插件

内置 `minecraft-server` 插件提供：

- 顶部「服务器」入口与公开服务器列表
- 在线人数、在线状态、客户端加载器/版本；优先通过 Minecraft Server List Ping 探测地址和端口，心跳作为在线状态与时长兜底
- 每台服务器可单独开关“在公开页面显示”；隐藏后后台仍会继续 Ping 和统计
- 每名玩家的总在线、活跃与挂机时长
- 管理后台「社团服务 → MC 服务器」的新增/编辑/删除和完整统计

统计数据保存在**独立 SQLite 数据库**：

```text
data/plugin-data/minecraft-server/minecraft-server.db
```

游戏端通过 **base-url + 服务器 ID + API 密钥** 上报。API 密钥需要勾选 `plugin:minecraft-server:use` 权限。普通访客只看到服务器在线人数；登录用户只能看到自己的时长；管理员能看到全部玩家和挂机统计。

游戏端源码、构建矩阵和代表产物位于：

```text
game-integrations/
```

详见 `game-integrations/README.md`。

## MC 百科

`plugins/mc-wiki/` 是移植后的 Minecraft 百科插件，管理员在「管理后台 → 插件页面 → 百科管理」（或直接访问 `#/plugin/mc-wiki?tab=admin`）操作：

1. **刷新版本清单**：从 Mojang `version_manifest_v2.json` 拉取全部版本。
2. **导入版本**：下载该版本的客户端 / 服务端 JAR，解析中英文名称、配方、标签、实体与方块状态；1.13 起配方数据来自服务端 JAR。
3. **更新渲染资产**：从 [Owen1212055/mc-assets](https://github.com/Owen1212055/mc-assets) 拉取 256px 背包渲染图，所有版本共用。
4. **发布版本**：只有已发布的版本会出现在前台「MC 百科」页，可同时发布多个版本并在页面切换。

数据与产物位置：

```text
data/plugin-data/mc-wiki/     版本清单、物品、配方、标签、方块状态、发布状态与任务日志
plugins/mc-wiki/assets/       渲染图（由 /plugin-assets/mc-wiki/... 静态托管，不纳入版本控制）
```

导入与渲染资产更新都在单线程后台任务中执行。后台「任务中心」页集中显示任务列表、实时进度与日志，可暂停、继续、取消或删除记录；从版本管理启动的任务会自动跳转到任务中心。版本列表默认把快照折叠到对应正式版下（名称带版本号的 `-pre/-rc/-snapshot` 直接归属，周快照按发布时间归属到之后发布的正式版，晚于全部正式版的进入「未发布快照」分组），点击分组前的箭头展开或收起。

- 物品详情支持直接下载 3D 图标（仅当该物品有渲染图）。
- 配方九宫格中的标签格（如 `#minecraft:bamboo_blocks`）带 `#` 角标，悬浮可查看该标签包含的全部物品。
- 「版本比对」对普通用户开放：选择两个已导入版本，按物品 / 方块 / 生物 / 配方查看新增、修改与删除差异，支持汇总计数快捷切换。

公开接口：`GET /api/plugins/mc-wiki/meta|items|item|recipes|tag|versions|compare`；后台接口：`GET/POST /api/plugins/mc-wiki/admin/*`（仅管理员）。

重新构建插件：

```bash
plugin-src/mc-wiki/build.sh      # Windows: plugin-src\mc-wiki\build.bat
```

产物为 `plugins/mc-wiki/`（可直接运行）和 `plugin-src/mc-wiki/mc-wiki.jar`（可在插件管理页上传安装）。源码位于 `plugin-src/mc-wiki/`。

## 社团活动

`plugins/activity/` 提供活动广场、参与核验与活动证明导出，入口为 `#/plugin/activity`，管理员入口为 `#/plugin/activity?tab=admin`。

**活动流程**

1. 管理员在「活动管理」中新建活动，支持草稿 / 开放报名 / 已结束三种状态、报名时间窗口、名额上限与证明开关。
2. 社员在「活动广场」报名；管理员在「参与核验」中按人通过、拒绝或批量核验。
3. 社员在「我的活动」中开启「参与活动证明」，填写姓名、学号、专业班级、学院与专业。未开启时不会进入证明名单，信息也可以留空。
4. 管理员上传 DOCX 证明模板后，在「证明导出」中选择一个或多个活动，预览并修改名单后生成一份包含全部参与者的 DOCX。

**学号与年级**

学号格式为 `51 + 4 位入学年份 + 4 位数字`（例如 `5120241234`），开启证明时姓名、学号和专业班级为必填。系统按每年 9 月划分学年，根据入学年份与「预计毕业年份」判断年级；预计毕业年份默认按入学年份 + 4 自动推算，可手动修改（五年制、延期毕业等），取值范围限制在入学年份至入学年份 + 10 年。默认参与范围为大一大二大三；大四及已毕业默认不再进入证明名单，可在「我的活动」中点击「继续参与」手动保留参与资格；未填写身份、未开启证明或学号无效同样会单独列出。

**固定参与人员**

「证明导出」中可以维护固定参与人员（工作人员、外校同学等未在网站报名的人员），支持手动填写，也可以从已填写身份信息的现有用户直接导入。固定人员会随每次导出加入名单，同样需要填写符合规则的学号和专业班级，并按默认参与范围（大一至大三）判断是否进入名单。

**多活动联动**

证明导出支持一次选择多个活动：

- 「任一活动通过」：参与过所选活动之一且核验通过的社员都会进入名单，正文中的 `{{activities}}` 列出所选活动。
- 「需全部活动通过」：只有所选活动全部核验通过的人才会进入名单。

**模板与预览**

- 管理员可以上传任意 `.docx` 模板，并可在「在线编辑模板」中直接修改正文段落文字，表格中的参与者名单由系统自动重建（每行两人：姓名 / 专业班级 / 学号）。
- 「预览并生成」会展示可编辑的证明编号、签发日期、签发单位、正文活动名称和参与者表格；可以增删人员、修改姓名/班级/学号后再生成。
- 模板正文支持以下占位符，渲染时会合并被 Word 拆分的文本片段后替换：

```text
{{proofNo}} {{issueDate}} {{issuer}} {{activities}} {{activityCount}} {{generatedAt}}
{{name}} {{studentNo}} {{college}} {{className}} {{major}} {{enrollmentYear}} {{graduationYear}}
```

社员也可以在「我的活动」中为已核验的活动单独下载自己的证明 DOCX。

**已盖章 PDF**

管理员在「证明导出 → 已盖章 PDF」上传盖章扫描件，有两种方式：

- 根据某次导出记录上传：自动使用该记录的参与者名单匹配，名单内用户均可下载。
- 独立上传：上传时从已填写身份信息的用户中手动勾选参与名单。

参与者在「我的活动 → 已盖章证明」中下载对应的盖章 PDF；下载权限按用户 ID 或身份表学号匹配，未在名单内的用户无法下载。管理员可下载或删除已上传的盖章文件。

数据位置：

```text
data/plugin-data/activity/     activities.json、participations.json、identities.json、
                               settings.json、exports.json、stamped.json、template.docx、
                               exports/*.docx、stamped/*.pdf
```

服务器在线自动报名预留了活动级开关与 `/admin/sync` 接口，需要接入服务器插件提供的在线玩家数据后启用，当前返回「暂未接入」提示。

重新构建插件：

```bash
plugin-src/activity/build.sh      # Windows: plugin-src\activity\build.bat
```

## 问题反馈

`plugins/feedback/` 移植自 YuDream 问题反馈插件，入口为 `#/plugin/feedback`，管理员入口为 `#/plugin/feedback?tab=admin`。

- 社员提交缺陷反馈 / 功能建议 / 使用咨询，可附最多 6 张截图，并可选择一个由管理员维护的自定义分类，在「我的反馈」中跟进进度、追加说明、修改未完结的内容或撤回。
- 管理员在「反馈管理」中跨用户检索与筛选反馈，回复用户、填写仅管理员可见的内部备注、指派处理人（支持多人协作）、修正严重程度，并流转「待处理 / 处理中 / 已解决 / 不予处理 / 已归档」状态。
- 「分类管理」页支持新增、编辑（重命名、设置默认处理人，默认处理人可多选）、启用/停用与删除分类；用户提交该分类的反馈时会自动指派给这些默认处理人。停用或删除后不再出现在提交表单中，历史反馈仍保留原分类名称。
- 管理员给出结论（已解决 / 不予处理）后，提交人可归档；结论超过 7 天仍未归档的，在下次读取反馈时由插件自动归档。
- 全部处理过程留痕，用户端不会看到内部备注。

数据位置：

```text
data/plugin-data/feedback/     feedbacks.json、categories.json、attachments/*
```

重新构建插件：

```bash
plugin-src/feedback/build.sh      # Windows: plugin-src\feedback\build.bat
```
## 启动器域插件

内置 `launcher-domain` 插件为 SWUSTMCL 提供“站点型域”：

- 公开页「社团域」展示域资料、公告、官方服务器与资源，并提供启动器邀请；
- 管理后台「插件页面 → 域管理」维护域资料、公告、服务器和资源；
- 清单接口：`GET /api/plugins/launcher-domain/manifest`；
- 启动器邀请：`swustmcl:domain:<编码后的站址>` 或 `swustmcl://add-domain?url=...`；
- 启动器把域作为当前环境：添加站点型域后，首页公告、服务器、资源与账号统一由该域接管，不新增单独的域页面；
- 域内只允许登录该域对应的皮肤站账号；切换域会自动匹配账号，无匹配账号时禁止启动；
- 服务器可关联 `minecraft-server` 插件中的服务器 ID，网页清单直接显示心跳在线人数；
- 资源支持整合包、资源包、光影包和其他文件，启动器可下载，资源包/光影可直接安装到实例；
- 服务器整合包：管理员在启动器「版本管理 → 发布到域」中选择版本隔离实例发布；发布时绑定服务器（一个服务器只能绑定一个包）、选择必装/原版增强和最低 Java 版本；
- 增量发布：站点按 SHA-256 保存文件块和完整清单，同一实例再次发布只上传新增/修改文件，移动复用已有文件块，删除只改清单；
- 发布认证：在「域管理 → 启动器发布令牌」生成令牌，启动器用系统凭据加密保存；令牌可设有效期和吊销；
- CurseForge 中转：站点在「域管理 → CurseForge API Key」配置（或设置环境变量 `CURSEFORGE_API_KEY` / `CF_API_KEY`）后，处于本站域的启动器直接经站点搜索 CurseForge，玩家无需填写 Key，站点 Key 不会下发；
- 中转接口只放行 `/mods/search`、`/mods/<id>`、`/mods/<id>/files`、`/mods/<id>/files/<fileId>` 等只读接口。

域数据保存在：

```text
data/plugin-data/launcher-domain/launcher-domain.db
```

一个皮肤站只维护一个域；插件首次启用时会导入 `minecraft-server` 中已有的服务器。

## 插件权限

每个已安装插件会自动注册一个权限码：

```text
plugin:<插件 id>:use
```

它会出现在「角色管理 → 权限 → 插件权限」中，用于控制插件页面与插件接口的访问。新插件首次注册时默认授予管理员、普通用户与访客角色，保持安装后立即可用；管理员可以按角色收回。插件被卸载时，对应权限会从权限目录、所有角色和用户额外权限中移除。

### 后台侧栏分类

管理后台左侧栏按「工作台 / 用户与权限 / 皮肤站 / 插件与扩展 / 系统」分类；已启用插件会自动出现在「插件页面」分类，也可以由插件清单声明自己的后台入口与分类：

```json
{
  "adminNav": [
    {
      "title": "服务器管理",
      "category": "社团服务",
      "icon": "server",
      "path": "#/admin/plugin/server-status",
      "order": 10
    }
  ]
}
```

`path` 必须是以 `#/` 开头的站内路由。带 `client` 的插件会直接在管理后台外壳内打开（不再跳到前台插件页），`path` 中的查询参数会原样传给插件（例如 `?tab=admin`）；省略时默认传入 `tab=admin`。没有前端模块的插件按 `path` 跳转。

## Java 插件开发

插件放在 `plugins/<id>/`，也可以由管理员在「管理后台 → 插件管理」上传 **JAR** 或 ZIP。上传相同 `id` 的插件包会原地更新：保留插件私有数据、权限、API 密钥、配置与启用状态；新版本加载失败会自动回滚到旧版本。插件包根目录需要 `plugin.json`：

```json
{
  "id": "my-plugin",
  "name": "我的插件",
  "version": "1.0.0",
  "description": "插件说明",
  "author": "作者",
  "mainClass": "com.example.MyPlugin",
  "client": "client.mjs",
  "nav": { "title": "页面", "icon": "sparkles", "order": 50 }
}
```

- `mainClass`：实现 `cn.edu.swust.mc.skin.plugin.SkinPlugin` 的 Java 类，必须有无参构造器。
- `client`：可选的浏览器端 ES Module，导出 `mount(root, ctx)` 后即可出现在导航与插件页面。
- `nav`：可选导航项；没有 `nav` 的插件只在管理后台显示。
- `enabledByDefault`：首次扫描时是否默认启用。

服务端入口示例：

```java
package com.example;

import cn.edu.swust.mc.skin.plugin.PluginContext;
import cn.edu.swust.mc.skin.plugin.SkinPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MyPlugin implements SkinPlugin {
    @Override
    public void activate(PluginContext ctx) {
        ctx.registerRoute("GET", "/hello", request -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("message", "hello");
            data.put("user", request.user() == null ? null : request.user().username);
            return data;
        });
    }
}
```

可用能力：

- `ctx.registerRoute(method, path, handler)`：注册 `/api/plugins/<id><path>`。
- `ctx.registerAdminRoute(...)`：仅管理员可访问。
- 路由返回 `PluginResponse.download(filename, contentType, bytes)` 时直接输出文件下载，适合导出的 DOCX / ZIP 等二进制内容。
- `ctx.on(event, handler)`：监听 `texture.uploaded`、`player.created`、`player.equipped` 等事件。
- `ctx.getConfig()` / `ctx.setConfig(patch)`：读取与保存插件配置。
- `ctx.getData(key)` / `ctx.setData(key, value)`：插件私有持久化数据。
- `ctx.stats()` / `ctx.settings()` / `ctx.listPublicTextures(query)`：只读访问站点数据。

构建 JAR：

```bash
javac --release 21 -cp target/swust-skin.jar -d build src/com/example/MyPlugin.java
cp plugin.json build/
cd build && jar --create --file ../my-plugin.jar .
```

`plugin-src/hello-plugin/` 提供了一个可安装的最小示例，构建产物为 `plugin-src/hello-plugin/hello-plugin.jar`。

> 插件拥有宿主进程内的执行权限，只安装可信来源的插件。需要第三方库时请打成 shaded JAR，或把依赖随插件一起放入插件目录。

## 部署

### 直接运行

```bash
mvn -DskipTests package
BASE_URL=https://skin.example.com PORT=3000 java -jar target/swust-skin.jar
```

建议用 systemd 托管：

```ini
[Unit]
Description=SWUSTMC Skin
After=network.target

[Service]
WorkingDirectory=/opt/swust-skin
Environment=BASE_URL=https://skin.example.com
Environment=PORT=3000
Environment=TRUST_PROXY=1
ExecStart=/usr/bin/java -jar /opt/swust-skin/target/swust-skin.jar
Restart=always
User=swustskin

[Install]
WantedBy=multi-user.target
```

### Nginx 反向代理

```nginx
server {
    listen 443 ssl http2;
    server_name skin.example.com;

    ssl_certificate     /etc/letsencrypt/live/skin.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/skin.example.com/privkey.pem;

    client_max_body_size 40m;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

同时设置 `TRUST_PROXY=1`，并把 `BASE_URL` 写成 `https://skin.example.com`。

### 域名与备案

- 中国大陆服务器使用 80 / 443 对外提供网站服务通常需要完成 ICP 备案；未备案域名可能被接入商拦截。
- 没有备案时，可把站点部署到香港或海外服务器，或先完成备案后再切换。
- 用 8443 等高位端口只能作为临时测试方案，不适合作为长期公开服务。
- 无论部署在哪里，都建议启用 HTTPS，并定期备份 `data/`。

### Docker

```bash
docker build -t swust-skin .
docker run -d --name swust-skin \
  -p 3000:3000 \
  -e BASE_URL=https://skin.example.com \
  -v "$PWD/data:/app/data" \
  -v "$PWD/plugins:/app/plugins" \
  swust-skin
```

## 安全清单

1. 修改默认管理员密码，停用演示账号。
2. 设置正确的 `BASE_URL`，公网使用 HTTPS。
3. 不要泄露 `data/keys/yggdrasil-private.pem`。
4. 只安装可信插件；插件拥有服务端执行权限。
5. 定期备份 `data/`，升级前先备份数据库与密钥。
6. 反向代理后设置 `TRUST_PROXY=1`，不要直接把应用端口暴露到公网。

## 项目结构

```text
src/main/java/cn/edu/swust/mc/skin/
├── Main.java                HTTP 服务与请求分发
├── Web.java                 路由、请求/响应、静态资源、限流
├── Auth.java                会话、CSRF、权限校验
├── Authz.java               角色、部门、权限模型
├── Database.java            SQLite 初始化与迁移
├── Textures.java            PNG 校验、材质与衣柜
├── Players.java             角色与装备
├── Yggdrasil.java           联机认证与 RSA 签名
├── Plugins.java             Java JAR 插件运行时
├── Branding.java            首页主图校验、存储与切换
├── ApiKeys.java             API 密钥签发、校验与权限收敛
├── routes 相关类            认证、皮肤、管理、插件、首页
├── plugins/serverstatus/    服务器状态示例插件
└── plugin/                  插件 API

public/                      无需构建的前端
plugins/                     运行中的示例插件
plugin-src/                  示例插件源码
data/                        SQLite、材质与密钥
```

## 第三方组件

3D 皮肤预览使用 [skinview3d](https://github.com/bs-community/skinview3d) 3.4.2（MIT License），以 UMD bundle 形式随站点发布，许可证见 `public/assets/vendor/skinview3d.LICENSE`。

## 许可证

见 [LICENSE](LICENSE)。
