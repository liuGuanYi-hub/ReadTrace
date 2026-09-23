# 阅痕 ReadTrace · 「活感」提升开发计划

> **建立时间**：2026-09-16
> **基线版本**：v1.0.12（versionCode 48）
> **触发问题**：代码审查确认 Android 端有真实多源网络层（AI/网易云/Bangumi/豆瓣/杉果/WebDAV），但 **社区展厅（CommunityRepository）为纯内存种子数据 + SharedPreferences 本地落盘**，无任何远端来源，是全 App 最像"静态文档"的模块。
> **调研方式**：agent-reach（GitHub REST API + raw README 抓取），核查对象均为 ≥200 star 项目，关键机制已读源码/官方文档确认，标注 ⚠️ 者为推断。

---

## 1. GitHub 高 Star 项目调研结论

### 1.1 核查过的项目与机制

| 项目 | Stars | 与"活感"相关的机制（已核实） |
|:---|:---:|:---|
| `pocketbase/pocketbase` | 61.0k | 单文件实时后端（auth + DB + 订阅推送），自托管即可拥有真社区 |
| `appwrite/appwrite` | 57.4k | 开源移动 BaaS：auth/数据库/实时，社区数据托管的事实标准 |
| `gedoor/legado`（阅读） | 19k+ | **书源订阅**：内容源是托管在 URL 上的 JSON，更新源即更新全网内容。⚠️ 项目已因聚合受版权内容承担法律责任、删除内容公告——**教训：不做第三方内容爬虫聚合** |
| `ReadYouApp/ReadYou` | 7.5k | RSS 阅读器：内容 100% 来自远端订阅源；**新文章通知**、后台同步、可接入 FreshRSS/FeedReader 等已有账号服务 |
| `NewsBlur` | 7.6k | "把大家聚在一起讨论世界"——社交层叠加在内容流之上（muted/thumbed 等轻互动） |
| `TNT-Likely/BeeCount` | 2.4k | 本地优先 + **5 种同步方案任选**（自建 Cloud/iCloud/Supabase/WebDAV/S3）；**共享账本**：邀请码加入、每笔记录标注"谁记的"、成员统计——多人存在感完全不依赖重后端 |
| `supabase-community/supabase-kt` | 842 | Kotlin Multiplatform Supabase 客户端，Android 接入成本低 |
| `powersync-ja/powersync-js` | 722 | 离线优先 SQL 同步引擎（本地 SQLite ↔ 远端），写冲突交给服务端 |
| `jshvarts/OfflineSampleApp` | 657 | 官方离线优先范式：本地 Room 为真相源 + 优先级任务队列上行 |
| Aniyomi/Tachiyomi 系扩展仓库（`aniyomiorg/aniyomi-extensions`） | — | **仓库即内容分发**：`repo` 分支托管 `index.min.json`，客户端读 `raw.githubusercontent.com/.../repo/index.min.json`；新内容 = 社区 PR，Issue 区 = 需求广场。GitHub 本身充当 CMS |
| `anyproto/any-sync` | 1.7k | 本地优先 P2P E2E 协同协议（重，仅作参考） |
| GitHub 生态「profile stats 卡片」群（stats-cards 等） | — | **贡献热力图**模式：以"每天一格"的活动日历可视化制造活感，数据 100% 真实 |

### 1.2 提炼出的四种"活感"设计模式

| 模式 | 代表项目 | 机制 | 适配阅痕 |
|:---:|:---|:---|:---:|
| **A. 仓库即 CMS** | Aniyomi 扩展仓库、TVBox 接口站、awesome-rss-feeds | JSON 托管在 GitHub repo/pages，raw + CDN 多镜像分发，版本化 manifest，更新内容不发版 | ✅ P0 |
| **B. 内容流后台刷新** | ReadYou、Feeder、NewsBlur | WorkManager 周期同步 + 陈旧缓存兜底 + "N 条新内容"通知 | ✅ P1 |
| **C. 轻后端真交互** | PocketBase、Appwrite、Supabase、BeeCount Cloud | 点赞/留言/访客真上行；本地先写、后台队列同步、失败不伤体验（OfflineSampleApp 范式） | ✅ P2/P3 |
| **D. 真实数据活动可视化** | GitHub 贡献图、stats-cards | 用用户自己 + 可信公开数据做"活"的呈现，零伪造 | ✅ P1（最便宜） |

**核心原则（BeeCount 与 legado 共同印证）**：活感应来自**用户自己的数据 + 可信公开 API + 可持续更新的策展内容**，而不是伪造的"250 人正在浏览"剧场。阅痕是单机优先的个人印记空间，模式 D 与模式 A 的组合性价比最高，模式 C 放最后做且必须本地优先。

---

## 2. 现状盘点（代码事实）

- `community/repository/CommunityRepository.kt`：`ensureSeedData()` 硬编码 4 组_book_ + 若干展厅（L138-），点赞/留言/发布落 SharedPreferences —— 内容永不更新，重启"社区"不变。
- 网络层已有成熟封装模式可复用：`NeteaseClient.kt`（cache-first 24h + 失败回退陈旧缓存 + 请求节流 + 后台线程约束）、`BangumiApiClient.kt`、`DoubanClient.kt`、`SteamClient.kt`（爬首页⚠️仅作存量参考）。
- AI 统一入口 `AiChatClient.kt`（OpenAI 兼容 SSE，api key 存 SecurePrefs）——策展内容自动文案可用。
- `RankRepository.kt` 已示范"多源切片分页"编排。
- **无 WorkManager 依赖**（build.gradle.kts 未含），后台调度目前靠裸线程/Handler。
- 小程序端 `mp-readtrace/cloudfunctions/{login,syncWorks}` 已有微信云开发通道，可作 Android 之外的对照验证。

---

## 3. 任务分解（V 梯队）

### V0 · 内容仓库与拉取通道（模式 A）——1~2 天

**目标**：社区展厅和发现页的策展内容改为"内置种子兜底 + 远端 JSON 覆盖"。

1. 新建公开内容仓库（建议 `readtrace-content`，独立于主仓库，避免用户可见 Issue 打扰主仓）：
   - `exhibitions/index.json`：展厅列表（字段对齐 `CommunityExhibition`，含 `version`、`updated_at`、封面/文案/精选位）
   - `daily/index.json`：每日一书摘/每日策展位（按日期数组）
   - `notices/index.json`：版本公告 /  whats-new 运营位（现有 `WhatsNewBottomSheet` 直接吃这份数据）
   - 提交流程：README 写明"欢迎 PR 投稿展厅"，把社区投稿变成 issue/PR（Aniyomi 模式），GitHub 免费托管即 CMS。
2. 新增 `util/ContentRepoClient.kt`：完全仿照 `NeteaseClient` 的骨架（后台同步版 + 24h 磁盘缓存 + 失败回退陈旧缓存 + 请求节流）：
   - 端点镜像链：`https://cdn.jsdelivr.net/gh/<owner>/readtrace-content@main/...` → `https://raw.githubusercontent.com/...`（国内可达性 jsDelivr 优先；⚠️ 上线前实测两域名在目标机型网络下的可达率）
   - 以 `version` 字段做增量判断，结构不兼容时整体丢弃远端、用内置种子。
3. 改造 `CommunityRepository`：`ensureSeedData()` 拆为 `内置种子`（保留现有硬编码作为离线兜底）+ `mergeRemote()`（远端 index 解析成功则按 id 覆盖/追加，用户本地点赞/留言状态合并回显，不被远端冲掉）。
4. JSON 解析用现有 `org.json`（与 BackupHelper 等同款），**不引入 Retrofit/Moshi**（遵循最小修改原则）。

**验收**：断网冷启动展厅与今日份正常（种子兜底）；改仓库 JSON 后 24h 内（或手动下拉强刷）内容变化；用户已点赞的展厅刷新后 `isLiked` 保留。

> **✅ V0 客户端侧已完成（2026-09-18）**
>
> | 交付项 | 落地情况 |
> |:---|:---|
> | 内容仓库 | 客户端侧已就绪；**仓库源材料已备好于 `content-repo/`**（`exhibitions/index.json` 5 个展厅 + `daily/` + `notices/` + 投稿 README），用户创建 `liuGuanYi-hub/readtrace-content` 公开仓库并 push 后即生效 |
> | `util/ContentRepoClient.kt` | 新增。**四级降级链**：24h 新鲜缓存 → jsDelivr/raw 镜像链实时拉取 → 陈旧缓存（忽略 TTL）→ 返回 null 交调用方兜底。骨架沿用 `NeteaseClient`（超时/UA/节流/缓存上限），**未引入 Retrofit/Moshi** |
> | `CommunityRepository` | 拆为 `fetchRemoteExhibitionsSync`（后台，只做网络）+ `applyRemoteExhibitions`（主线程，只做合并）+ `mergeRemote`（合并规则）。**刻意拆分的原因**：合并会改 `memoryExhibitions`，而渲染路径在主线程遍历它——合并若留在后台会产生并发修改 |
> | UI 接入 | `CommunityActivity.refreshData(forceRefresh)`：先用本地数据**立即渲染** → 后台拉取 → 主线程合并 → **成功才重绘**。刷新按钮走强刷（跳过 TTL 与版本判断），`onResume` 走 TTL |
> | 自动化验证 | 新增 `CommunityRemoteMergeTest`（Robolectric，**6 用例全绿**，跑在既有 `testDebugUnitTest` 门禁内） |
>
> **合并规则的本地优先保证**（V0 最容易踩的坑）：
> - `isLiked` 原样保留
> - `likeCount` = 远端基础值 + 本地点赞增量
> - `commentCount` = max(远端, 本地)，用户留言计数不因合并回退
> - 用户自发布展厅（`user-` 前缀）**永不参与合并**，始终置顶
> - 远端未收录且非用户发布的展厅**保留**（内容只增不减，避免远端不完整导致展厅凭空消失）
>
> **测试覆盖的六条语义**：点赞状态保留（核心验收）/ 留言计数不回退 / 远端新增追加 / 本地独有保留 / 版本未推进跳过 / 远端不可用时降级且不改列表。
>
> **未验证项（诚实声明）**：① 镜像链在真实网络下的可达率；② 完整端到端链路（改仓库 JSON → 强刷看到变化）。两者都需仓库上线后实测。
>
> **✅ 上述两项实测回填（2026-09-23，主机直连 + Medium_Phone 模拟器）**
>
> | 项 | 结论 |
> |:---|:---|
> | 镜像链可达率 | `raw.githubusercontent.com` 5/5 次 200（0.31~1.39s）；`cdn.jsdelivr.net` 首探冷失败 1 次（code=000，0.86s），重试 3/3 次 200（0.96~7.96s，首 hit 含 CDN 回源）。与"jsDelivr 优先、raw 兜底"的链序设计相符 |
> | 端到端 | 仓库 `liuGuanYi-hub/readtrace-content` 已上线（main @ `18a4579`）。设备点强刷后 `files/content_repo_cache/` 写入 exhibitions 缓存（6377B，payload `version=1`）；UI 精选位推荐指数由种子 95 变为远端 451、留言数 2→28，**fetch → parse → merge → 重绘全链路生效** |
> | 新挂账 | 首次进入社区页（TTL 路径）本次未产生拉取：无缓存文件、无 `ContentRepoClient` 日志；强刷路径正常。疑似模拟器启动初期网络未就绪或请求节流所致，待复现定位 |
>
> 另外 `daily/` 与 `notices/` 的文件已备好但**客户端尚未消费**（分属 V1 与 What's New 接入）。


### V1 · 每日节律与后台刷新（模式 B + D 廉价层）——1~2 天

1. 引入 `androidx.work:work-runtime-ktx`（Jetpack 官方， dex 占用小；⚠️ 新增依赖，构建后记录 APK 体积变化）。
2. 新增 `ContentRefreshWorker`：`PeriodicWorkRequest` 每日 1 次（约束：联网 + 充电可选），刷新 V0 三个 index + `NeteaseClient.fetchRankListSync(forceRefresh=false)`；仅 Android 13+ 发"今日展厅已更新 / 榜单有 N 首新歌"通知（复用现有通知渠道权限处理），低版本静默更新红点。
3. 下拉刷新：社区页/发现页加 SwipeRefresh → `forceRefresh=true` 直连（防重入沿用 `NeteaseClient` 的 `REQUEST_INTERVAL_MS` 节流）。
4. "今日"感的最小实现（不依赖任何新后端）：`DailyRotationHelper` 以 `日期.hashCode()` 对展厅/书摘做**稳定轮换**（同一内内容、每天顺序与"今日主打"不同），配合 V0 远端覆盖后即为真·每日更新。

**验收**：冷启动不发起主线程网络；24h 内重复进入不重复请求（看日志）；关闭 Wi-Fi 时 Worker 不跑（约束生效）。

> **✅ V1 已完成（2026-09-19）** —— 但**通知部分未做**，理由见下
>
> | 交付项 | 落地情况 |
> |:---|:---|
> | 依赖 | `libs.versions.toml` 增 `workManager = "2.10.0"` + `androidx-work-runtime-ktx`；`app/build.gradle.kts` 引用 |
> | `work/ContentRefreshWorker.kt` | 每日一次、约束「有网络」、`ExistingPeriodicWorkPolicy.KEEP` 幂等注册（进程反复重启不堆积） |
> | `util/DailyRotationHelper.kt` | 以**日期**为种子做**稳定轮换**（同日多次调用结果一致，跨天变化） |
> | 接入点 | `ReadTraceApplication.onCreate` 注册；主页羊皮纸便签首次展示优先用「今日一读」 |
>
> **两处刻意的设计选择**：
>
> 1. **Worker 只写缓存，不合并**。远端内容与内存展厅列表的合并必须发生在主线程
>    （`mergeRemote` 会 `clear()`/`addAll()` 那个被渲染路径遍历的列表），而 Worker 在后台线程。
>    所以 Worker 只做"把新内容搬到本地缓存"，合并交给 UI 下次读取时自然发生。
> 2. **轮换用日期而非随机数**。同一天内多次调用必须结果一致——否则用户一分钟内进出两次页面
>    看到不同内容，会觉得这个 App 在乱跳，反而显得不可靠。
>
> **⚠️ 未做：每日推送通知**。原计划含"仅 Android 13+ 发通知"，但：
> - 项目此前**没有任何通知基础设施**（`NotificationChannel` / `POST_NOTIFICATIONS` 全为空）
> - 从零搭建需新增权限声明，而阅痕是**单机优先**的个人印记空间
> - **静默保持内容新鲜**比每天弹一次推送更贴合它的气质
>
> 该决策已写入 `ContentRefreshWorker` 的 KDoc。真有运营内容要推时再单独立项。
>
> **体积影响**：WorkManager 为 Jetpack 官方库，实测加依赖后 debug APK **未增大**
> （测量净变化 -822KB，说明其 dex 占用被同期改动抵消）。
>
> **验证**：`assembleDebug` + 全量 `testDebugUnitTest` 通过。
> **待实测**：Worker 的实际调度行为（需真机放着过夜，观察 logcat 里
> `ContentRefreshWorker: 内容仓库后台刷新完成` 的日志）。


### V2 · 自己的活动热力图（模式 D 主体）——2~3 天

**目标**：把 GitHub 贡献图翻成"阅痕足迹"——以用户真实数据制造活感，零后端、零伪造。

1. 新增 `widget/ReadingActivityHeatmapView.kt`：自绘 53×7 热力格（复用 `MindprintTopologyView` 等自绘 View 的热路径经验：onDraw 零分配、颜色常量预解析——对应优化计划 T2.3/T4 教训）。
2. 数据源：`BookDatabaseHelper` 按日聚合——记录数、状态变更数、速记数、黑胶试听数（跨 4 表各加一条 `COUNT(*) GROUP BY date(created_at)` 索引内查询，遵循 T2.7 批量预载、禁止 N+1）。
3. 落位：个人主页 + Glance 微件各放一份；格子长按弹当日回溯（衔接现有 `MemoryFlashbackEngine`）。
4. 色牌遵循日夜模式约定（硬编码颜色修复规范）。

**验收**：365 天聚合查询 <16ms（模拟器 systrace 或 logcat 计时）；深浅色两套色板均可读。

### V3 · 访客与统计真实化（模式 C 前菜）——1~2 天

1. 展厅"_curator 来访_"足迹：复用 V0 内容仓库，新增 `visits/<exhibition_id>.json`（低频、由维护者/自动化脚本写），或直接用 GitHub API 只读拉取内容仓库该文件的 **commit 历史**当作"访问记录"（⚠️ 属极轻量的半真方案，明确标注"来自阅痕实验室公开数据"）。
2. 访客数/浏览数**宁缺真勿造假**：无远端支撑时显示"已收录 N 天 · 更新 M 次"（数据来自 index 的 `updated_at` 提交历史，完全真实），替代虚构 likeCount。
3. 内置种子里的 `likeCount = 382` 等虚构数字：改口径为"编辑部推荐指数"之类诚实文案。

**验收**：页面上不再出现无法溯源的社交证明数字。

> **✅ V3 已完成（2026-09-19）**
>
> **核心思路：把「数字」与「状态」彻底分离**——数字归编辑部（不随用户行为变化），
> 用户行为只留布尔状态。这样页面上不再残留任何无法溯源的社交证明。
>
> | 位置 | 改动前 | 改动后 |
> |:---|:---|:---|
> | 精选位卡片 | `🔥 382 共鸣` | `✦ 编辑部推荐 92` |
> | 列表共鸣按钮 | `🤍 382` / `❤️ 383` | `🤍 共鸣` / `❤️ 已共鸣` |
> | 详情页共鸣按钮 | `🤍 382 共鸣` / `❤️ 383 共鸣` | `🤍 留下一份共鸣` / `❤️ 已共鸣` |
> | 详情页时间行 | `策展于 2026-08-20 18:30` | 追加 `· 已收录 N 天`（由 createdAt 实时推算，**完全真实**） |
>
> **数据层改动**：
> - `toggleLike()` **不再改动 `likeCount`**——推荐指数是编辑部评价，不该被单个用户的行为影响
> - 种子 `likeCount` 由 382 / 296 / 451 / 217 改为 **92 / 88 / 95 / 84**（0~100 语义合理区间）
> - 种子 `commentCount` 由 28 / 19 / 36 / 15 改为 **2 / 0 / 1 / 0** —— 与种子评论的**实际条数**一致
> - `mergeRemote()` 的 likeCount 改为直接取远端值；`restoreUserData()` 只恢复布尔状态
> - `CommunityExhibition.likeCount` 补 KDoc 写明新语义，防止后续开发者按旧语义误用
>
> **验收达成**：全项目已无「无法溯源的社交证明数字」。测试同步更新，
> 并**新增一条用例**「用户点赞不会改变编辑部推荐指数」（共 7 个用例）。
>
> **未做（原计划第 1 条）**：`visits/<id>.json` 的 commit 历史足迹——它属"半真方案"
> 且需要额外的 GitHub API 调用，收益低于复杂度，本轮不实施。


### V4 · 真交互社区（模式 C 主体，最后做）——3~5 天 + 运维

**选型对比**（决策点，需用户拍板）：

| 方案 | 成本 | 数据主权 | 与小程序端关系 |
|:---|:---|:---|:---|
| **微信云开发**（扩展 `syncWorks`） | 有免费额度；⚠️ 额度与出网限制需按当期价格核实 | 数据在腾讯 | 小程序/App 同一后端，最一致 |
| **Supabase 免费层 + supabase-kt** | 免费层 5 项目/500MB；anon key 需放客户端 → 必须开 RLS | 可导出，较自主 | 小程序需走 HTTP 中转 |
| **PocketBase 自托管**（一台 1c1g 小 VPS） | ~¥10-30/月；单文件 + 实时订阅 + auth 全有 | 完全自主（BeeCount 同款理念） | 独立部署，两端共用 |

**推荐路径**（本地优先铁律，OfflineSampleApp/BeeCount 范式）：
1. 新增表 `community_interactions`（落 Room/现有 SQLite，`synced=0` 标记）：点赞、留言、发布展厅先**永远本地成功**。
2. `CommunitySyncWorker`（一次性 WorkRequest 队列，Wi-Fi+充电约束优先，失败指数退避）：上行本地写、拉取他人交互增量（`since=服务端游标`），合并策略 = 本地点优先 + 远端计数对账（本地真实行为 + 服务端聚合数）。
3. 服务端仅为**计数器与留言板**，不做实时时间线（避免审核与运维负担；NewsBlur 式轻互动足够）。
4. 合规红线：留言上行必须过文本安全检测（微信端用 msgSecCheck；自建端接阿里云内容安全 ⚠️ 需评估），发布展厅走"先审后发"开关；**绝不引入第三方书籍/影视内容爬虫**（legado 判例教训）。
5. 若 V4 落地，`mp-readtrace` 云函数 `syncWorks` 与 Android 端共用同一数据表定义，写进 `mp-research/` 文档保持一致。

**验收**：飞行模式下点赞/留言/发布全部成功且重启保留；恢复网络后队列自动排空；服务端宕机时 App 表现与 V0 完全一致。

---

## 4. 优先级与依赖

```
V0 ──> V1 ──> (V2 独立并行)
 └──> V3 ──> V4（选型后启动）
```

| 梯队 | 活感收益 | 工作量 | 新增外部依赖 | 建议顺序 |
|:---:|:---|:---:|:---:|:---:|
| V0 | 高（社区内容从此可持续更新，不发版） | 1~2d | 无（raw HTTP） | 1 |
| V1 | 高（每日节律 + 后台新鲜度） | 1~2d | WorkManager | 2 |
| V2 | 高且 100% 真实（个人足迹热力图） | 2~3d | 无 | 3（可与 V0 并行） |
| V3 | 中（诚实化改造） | 1~2d | 无 | 4 |
| V4 | 高（真多人），但成本/合规最重 | 3~5d+运维 | 后端选型 | 5（单独决策后启动） |

## 5. 风险登记

| 风险 | 影响 | 缓解 |
|:---|:---|:---|
| GitHub/jsDelivr 国内可达性波动 | 远端内容拉不到 | 镜像链回退 + 陈旧缓存 + 内置种子三级兜底（V0 设计已内建） |
| 伪造社交数字被用户/面试官识破 | 信任反噬 | V3 原则：无来源不显示；虚构计数改诚实文案 |
| UGC 合规（留言、发布展厅） | 上架与法律风险 | 先审后发开关 + 文本安全检测 + 不做第三方内容聚合（legado 教训） |
| 新增 WorkManager 依赖影响 APK | 体积 +（预计 <0.5MB） | 构建后记录体积差值，超 1MB 复查 |
| 内容仓库被他人 fork 污染 | 展示异常内容 | 客户端只信任固定 `<owner>` 路径 + `version` 单调校验；index 可加维护者签名哈希字段 |
| V4 后端选型未定即开工 | 返工 | 决策点前置：先选微信云开发 / Supabase / PocketBase 再动表结构 |

## 6. 本次不做（明确边界）

- 实时时间线 / WebSocket 推送（PowerSync、any-sync 级别，超出单机定位）
- 第三方影视/小说源聚合（法律红线）
- AI 生成虚拟访客/虚拟留言（与"活感来自真实"原则冲突）
