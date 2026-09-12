# 阅痕 ReadTrace · 优化开发计划

> **建立时间**：2026-09-11
> **基线版本**：v1.0.12（versionCode 48）
> **代码基线**：191 个 Kotlin 文件 / **44,115 非空行（含空行 49,275 行）**；85 个 layout；release APK 8.1 MB
> **计划性质**：本文件为「审查结论 → 可执行任务」的转化文档，与 `readtrace_project_plan.md`（原始项目规划）并行维护。

> **✅ 执行进度（2026-09-12 更新）**
> - **T0 全部完成**（7 项）：迁移守卫 / 验证码 fail-closed / 播种后台化+事务 / 封面守卫 / gradle.properties 敏感键 / allowBackup+AI Key 加密 / onDowngrade。含与计划的两处方案优化（patchCorruptedPresetCovers 不改、onDowngrade 改「备份+接受版本号」）。
> - **T1 全部完成**（5 项）：release 签名门禁（本地+CI）/ 明文流量收敛 / exported 收敛 13 项 / 微信沙盒门控 / 死权限删除。
> - **T2 完成 6 项 + 2 项改判**：
>   - 完成：T2.1 藏库零重查（缓存版本号+轻量查询）、T2.2 同步判断移后台、T2.3 四个自绘 View 热路径去分配、T2.4 v16 索引+WAL、T2.7 五处 N+1 批量预载、T2.6 精准版（藏库快速操作+速记入库后台化）。
>   - **T2.8 改判**：getMindprint 保持非空渲染语义（25 调用点依赖默认雷达是产品行为），仅修正年度人格均值的统计语义（无档案作品不计入）。
>   - **T2.5 改判为不执行**：实测 book_detail（7 层/1281 行）与 hub（7 层/1124 行）滚动卡顿率均为 0%（模拟器 95th 21~23ms，真机更快），「最痛页面」不存在，重构属高回归风险零收益。
> - **附加修复**：清库测试备份规则升级为 WAL 感知（checkpoint TRUNCATE + 清侧文件），回归测试不再影响设备真实数据。
> - **✅ UI 渲染专项审查已完成（2026-09-12，T2.9~T2.11 源）**：传感器监听 onResume/onPause 配对 ✅、广播接收配对 ✅、静态持有 Context 零命中 ✅、年份/年鉴导出 Bitmap 回收路径 ✅（runCatching 外无条件 recycle）；修复 2 项——收藏查询 2 处 Cursor 纳入 use 管理（前轮登记的泄漏点）、4 个 View 的 onDraw 残余 parseColor 常量化；登记 2 项低风险已知债——跨异步交互的大 Bitmap（详情页长图/心选长卷）依赖延迟 GC 回收（非泄漏，精确回收需逐分支跟生命周期，改错反致 crash）、45 处 Activity 级裸 Thread（T2.6 已表态按需迁移）。审查闭环。

> **⚠️ 行数口径说明**：本计划所有文件行数统一采用**非空行**口径（PowerShell `Measure-Object -Line`）。
> 若某处引用的是**含空行**口径，会显式标注。两套口径的换算参考：
> `BookDetailActivity` 非空 2,305 / 含空行 2,529；`VinylCassettePlayerActivity` 1,707 / 1,833；
> `AddBookActivity` 938 / 1,019；`DiscoverActivity` 1,018 / 符合原值。

---

## 0. 计划来源与核验状态

本计划基于三轮并行只读审查（数据层与并发 / UI 与渲染 / 工程化与安全）汇总而成。

| 审查维度 | 状态 | 说明 |
|:---|:---:|:---|
| 数据层与并发/线程模型 | ✅ 已完成并核验 | 6 项核心指控：5 项完全属实，1 项经二次复核确认；已修正其 2 处行号偏差 |
| 工程化与安全 | ✅ 已完成并核验 | 6 项核心指控全部属实 |
| UI 与渲染性能 | ⏳ 进行中 | 未纳入本计划，待补充后追加为 T2.9~T2.11 |

**核验方式**：全部结论均通过实际读取源码、`git` 元数据核查、全量 grep 交叉验证得出，每条任务均附 `文件:行号`。

**未验证事项（诚实声明）**：
- 三轮审查**均未执行任何 Gradle 构建或测试**（跑 `./gradlew` 会写 `build/` 并下载 Gradle 9.3.1 + AGP 到 C 盘，按空间保护规则需事前授权）。
- 因此：**单测是否全绿、CI 能否跑通、APK 实际体积构成** 均未经运行验证。
- 冷启动播种耗时「1.5~3.5s」为理论估算（依据：数百次独立写入各一次 fsync），**未真机 systrace/StrictMode 实测**。
- `targetSdk = 37` 是否处于 beta 轨道为审查方结论，**未联网核实**。

---

## 1. 背景：为什么需要这份计划

项目已完成 P1~P40 共 40 个功能的迭代，功能密度与视觉完成度很高。但审查发现：**功能快速迭代过程中积累了三类风险**，其中一类已经在用户设备上造成了不可逆的数据影响。

### 三类问题的性质区分

| 类别 | 问题 | 是否已造成实际后果 |
|:---|:---|:---|
| **A. 数据完整性** | `DatabaseMigrator` v14/v15 丢失 `source_type` 守卫 | ⚠️ **是**。v15 已随机改写用户评分，v14 已误删同名作品，均不可自动回滚 |
| **B. 身份与凭据安全** | 手机验证码登录为纯客户端校验且 release 走沙盒模式 | ⚠️ **是**。当前正式包输入任意手机号即可「登录」 |
| **C. 性能与工程债** | 主线程播种 DB、绘制热路径分配、仓库臃肿、CI 门禁失效 | 否。属可观测的体验与维护成本问题 |

**执行顺序原则**：A > B > C。A 类问题修复成本最低（2 行）但性质最严重，不应被性能优化排到后面。

---

## 2. 任务总览

| 梯队 | 主题 | 任务数 | 预估改动量 | 建议节奏 |
|:---:|:---|:---:|:---|:---|
| **T0** | 数据与身份安全止血 | 7 | 每项 ≤ 10 行 | 逐项提交，1~2 天 |
| **T1** | 发布与安全加固 | 5 | 小 | 逐项提交，1 天 |
| **T2** | 性能优化（用户可感知） | 8 | 中 | 分批提交，3~5 天 |
| **T3** | 工程质量与仓库治理 | 5 | 中~大 | 按需分批 |

**提交规约**：遵循「完成一个可独立验证的小阶段 → 验证 → 中文 commit → push」。每项任务独立一次 commit，禁止堆积到最后统一提交。

---

## 3. T0 梯队：数据与身份安全止血

> **原则**：T0 全部为低风险小改动，但涉及用户数据与构建系统，**每项开始前需用户确认**。

### T0.1 修复 v14/v15 迁移丢失的 `source_type` 守卫 🔴 最高优先

**问题等级**：P0 — 正在静默破坏用户数据，且不可逆

**证据**：
- `app/src/main/java/com/example/readtrace/data/migrator/DatabaseMigrator.kt:88-90`（v13，**有守卫**）
  ```kotlin
  val presetMusicWhere = "$COLUMN_TITLE IN (?, ?) AND $COLUMN_MEDIA_TYPE = 'music' " +
      "AND ($COLUMN_SOURCE_TYPE IS NULL OR $COLUMN_SOURCE_TYPE = '')"
  ```
- `DatabaseMigrator.kt:123`（v14，**守卫丢失** — 同一个变量名）
  ```kotlin
  val presetMusicWhere = "$COLUMN_TITLE IN (?, ?) AND $COLUMN_MEDIA_TYPE = 'music'"
  ```
- `DatabaseMigrator.kt:155-159`（v15，**无 `source_type` 限定**）
  ```sql
  UPDATE books SET rating = ROUND(7.0 + (ABS(RANDOM()) % 11) * 0.1, 1)
  WHERE is_deleted = 0 AND media_type IN ('music','movie','game')
    AND (rating IS NULL OR rating <= 6.0)
  ```

**实际后果**：
- **v14**：硬删除任何名为《451 (华氏451)》《Blues in the Closet》的音乐条目（**含用户自建同名作品**），并级联清空 `notes` / `reading_sessions` / `book_locations` / `book_mindprints` / `book_characters` / `book_outlines` 六张子表及 `curator_favorites`。
- **v15**：用户自建或从网易云/豆瓣导入的音乐、影视、游戏条目，凡评分 ≤ 6.0 者被**随机改写为 7.0~8.0**。`ABS(RANDOM())` 导致每次迁移结果不同，**无法回滚**。
- 与 `BookDatabaseHelper.kt:123-124` 自身注释「升版重播种一律不改写评分，避免覆盖用户手填数据（数据破坏）」**直接矛盾**。

**性质判定**：同一个变量名在 v13 有守卫、v14/v15 无守卫 → 属**复制粘贴遗漏导致的回归缺陷**，非有意设计。

**修复方案**：
1. `:123` 补回 v13 的那行守卫。
2. `:157` 追加 `AND ($COLUMN_SOURCE_TYPE IS NULL OR $COLUMN_SOURCE_TYPE = '')`。

**待用户决策**：已被 v15 随机改写的评分无法自动还原。可选：
- (a) 接受现状，在发布说明中告知受影响用户；
- (b) 设计补偿逻辑（需从 WebDAV/JSON 备份中恢复，仅对已备份用户有效）；
- (c) 暂不处理，仅阻止问题扩大。

**验证方式**：新增迁移器测试，构造含 `source_type='netease'` 且 `rating=5.0` 的条目，跑完 v15 迁移后断言其评分**未变**、条目**未被删除**。

**改动量**：极小（2 行 + 测试）

---

### T0.2 手机验证码登录 release fail-closed 🔴

**问题等级**：P0 — 身份校验形同虚设，且当前正式包正在生效

**证据**：
- `app/src/main/java/com/example/readtrace/auth/PhoneAuthManager.kt:118-141`
  ```kotlin
  if (AliyunSmsClient.isConfigured()) {
      AliyunSmsClient.sendVerifyCode(phone, code) { result ->
          RequestResult.Sent(
              sandboxCode = if (result.success) null else code,  // :123 发送失败 → 明文回传
              degraded = !result.success,
          )
      }
  } else {
      Thread.sleep(SIMULATED_NETWORK_DELAY_MS)
      RequestResult.Sent(sandboxCode = code, ...)                // :136 未配置 → 明文回传
  }
  ```
- `gradle.properties` 中 `ALIYUN_SMS_ACCESS_KEY_ID` / `SECRET` / `SIGN_NAME` / `TEMPLATE_CODE` **四个键当前全部为空**（已逐键核验）→ 正式包走 `:130-141` 沙盒分支，**验证码明文显示在界面上**。
- `PhoneAuthManager.kt:148-159` 的 `verifyCode` 为**纯客户端比对进程内字段**（`pendingCode`），无任何服务端参与。

**净效果**：输入任意手机号即可完成「登录」，随后 `CuratorAccountManager` 将账号写本地。所谓「策展人认证」不构成任何身份保证。

**修复方案**：
1. 沙盒分支（`:130-141`）用 `BuildConfig.DEBUG` 门控，release 下未配置即 fail-closed（返回失败，不回显）。
2. 正式通道发送失败**绝不降级回显**（`:123` 改为恒返回 `null`，仅置 `degraded = true` 供 UI 提示重试）。
3. 中期：真校验必须服务端持码，客户端只持会话票据。

**验证方式**：`assembleRelease` 后确认沙盒回显代码路径不可达；debug 构建下沙盒链路仍可用。

**改动量**：小

---

### T0.3 开库播种移出主线程 + 包裹事务 🔴

**问题等级**：P0 — 必然 ANR 路径

**证据**：
- `app/src/main/java/com/example/readtrace/data/BookDatabaseHelper.kt:57-63`
  ```kotlin
  override fun onOpen(db: SQLiteDatabase) {
      super.onOpen(db)
      patchCorruptedPresetCovers(db)
      runPresetSeedsOnce(db)
      // ...不阻塞主线程 (does not block the main thread)   ← :61 此注释与实现矛盾
      autoFillMissingCovers(db)
  }
  ```
- `onOpen` **在触发开库的那个线程上执行**。触发链：`ui/fragment/HubFragment.kt:199-203` 的 `view?.post { refreshDashboard() }` → `HubFragment.kt:406` → `BookDatabaseHelper.getCachedBooks()` → `getBooks()` → `readableDatabase.query` → **首次开库**。`view.post` 仅延至首帧后，**仍在主线程**。
- `BookDatabaseHelper.kt:99-153` 的 `runPresetSeedsOnce` 串行调用 9 个方法（`:106-113`）。
- **已独立核验**：这些播种方法**全部无事务**。全文件 `beginTransaction` 仅 9 处（`:224、256、321、528、2017、2320、2370、2716、2787`），**最靠前的播种方法 `repairMissingNeteaseCovers:156` 与 `migrateCoversToLanKeys:200` 之前一处都没有**；`compileStatement` 全文件仅 `:2019` 一处（即 `insertBooksBatch`）。
- 后果：数百次独立写入，每次一次 fsync。

**附带修正**：三处过时注释会持续误导维护者，需一并订正：
- `:61`「不阻塞主线程」— 与实现矛盾
- `:92-97`「全项目 25+ 处各自 new helper」— 实为 `:29` `private constructor` + 双检锁**真单例**，全工程仅 1 处构造
- `:3796`「BookDatabaseHelper 存在多实例」— 同上，过时

**修复方案**：
1. **止血**：`ReadTraceApplication.onCreate` 中用后台线程 / `WorkManager` 预热 `writableDatabase`，把首次开库播种挪出主线程。
2. **根治**：把播种逻辑搬进 `onCreate` / `onUpgrade`（框架自带事务包裹）。
3. 给 `runPresetSeedsOnce` 的 `synchronized(seedLock)` 外层**包一层事务** — 单此一项即可将播种从「数百次 fsync」降至「1 次 fsync」。
4. 订正上述三处过时注释。

**预期收益**：播种耗时预计下降 20~50 倍，直接消除冷启动 ANR。

**验证方式**：debug 构建开 `StrictMode`（项目当前 0 命中）跑冷启动，确认无 `diskWrite` / `diskRead` 主线程违规；用 `System.currentTimeMillis()` 埋点实测播种耗时（取真实数据以校准本计划中的估算值）。

**改动量**：小~中（2 个文件）

**注**：仓库根目录存在 `.tmp_anr_db.db`（229 KB），说明此路径此前已被实际排查过。

---

### T0.4 预设播种封面 UPDATE 加「仅当为空」守卫

**问题等级**：P0 — 静默覆盖用户数据

**证据**：
- `BookDatabaseHelper.kt:1137`（`seedUserMovieList` 的「记录已存在」分支）
  ```kotlin
  if (movie.coverUrl.isNotBlank()) put(COLUMN_COVER_URL, movie.coverUrl)
  db.update(TABLE_BOOKS, cv, "$COLUMN_ID = ?", arrayOf(bookId.toString()))
  ```
  **无任何「仅当为空」守卫** → 用户手动改过的电影封面，会在**每次数据库版本升级重播种时被静默覆盖**。
- 对照：同一分支 `:1142` 的评分 UPDATE 反而写了守卫 `AND (rating IS NULL OR rating <= 6.0)` — 说明作者知晓此模式，仅漏了封面。
- `BookDatabaseHelper.kt:65-87`（`patchCorruptedPresetCovers`）同理：`疯狂动物城` / `功夫` 两部作品的封面在**每次开库**时被改写。

**修复方案**：`seedUserAnimeList` / `seedUserMovieList` / `seedUserGameList` / `seedUserMusicList` 及 `patchCorruptedPresetCovers` 中所有封面 UPDATE，统一追加
`AND (cover_url IS NULL OR cover_url = '')`（或按 `source_type` 限定）。

**验证方式**：构造一部带自定义封面的预设作品，升版重播种后断言封面未被改动。

**改动量**：小

---

### T0.5 `gradle.properties` 移出版本控制

**问题等级**：P0（潜在）— 下一次提交即爆雷

**证据**：
- `gradle.properties` **确实在 `git ls-files` 中**（已核验）。
- `app/build.gradle.kts:17-25, 44-48` 通过 `buildConfigField` 将 `ALIYUN_SMS_ACCESS_KEY_SECRET` 注入 BuildConfig → **编译进 Dex**，`strings` / jadx 一条命令即可取出（主账号级 AK/SK，可发短信、可计费）。
- 而 `gradle.properties` 内的注释正指引用户「在这里填」→ **下一个照做并 commit 的人，会把密钥永久写进 git 历史**。
- **缓解现状（已核验）**：`git log --all -p -- gradle.properties` 中非空敏感赋值行数 = **0**；五个键当前也全部为空。`keystore.properties` / `local.properties` / `*.jks` 历史上从未被跟踪。**风险是「下一次提交」，不是「已泄漏」**。

**修复方案**：
1. `git rm --cached gradle.properties`，新增 `gradle.properties.example`（占位符 `YOUR_ALIYUN_SMS_ACCESS_KEY_ID` 等）。
2. 敏感值改由**用户级** `~/.gradle/gradle.properties` 提供（不入库）。
3. 把 `gradle.properties` 加入 `.gitignore`。
4. 中期：短信下发改为自建代理 / STS 临时凭证，客户端绝不放长期 AK。

**⚠️ 需用户确认**：此项属**构建系统改动**，会影响 CI（CI 需通过 secrets 注入这些值）。执行前需明确同意。

**验证方式**：本地 `assembleDebug` 通过；`git status` 确认 `gradle.properties` 不再被跟踪；CI 用 secrets 注入后仍能构建。

**改动量**：中

---

### T0.6 `allowBackup` 收敛 + AI Key 改走 `SecurePrefs`

**问题等级**：P1 — 凭据上云 + 换机解密失败

**证据**：
- `app/src/main/AndroidManifest.xml:15` `android:allowBackup="true"`。
- `app/src/main/res/xml/data_extraction_rules.xml:6-12` — Google 模板注释与 `<!-- TODO: Use <include> and <exclude> -->` **原封未动**。
- `app/src/main/res/xml/backup_rules.xml:8-13` — 空的 `<full-backup-content>`。
- 后果：`readtrace.db`（全部笔记/评分/书单）、`readtrace_ai_prefs`（**AI API Key，明文**）、`readtrace_webdav_prefs` 全部进 Google 云备份。而 Keystore 密钥不随备份迁移 → 换机后 `SecurePrefs` 必然解密失败。
- `app/src/main/java/com/example/readtrace/data/UserPreferencesManager.kt:174-181`：**AI Key 存普通 SharedPreferences 明文**；而同一文件 `:112-131` 的 WebDAV 密码走 `SecurePrefs`（AndroidKeyStore AES-256-GCM，实现正确）。**同一个类里两种策略，而 AI Key 价值更高**（`util/AiChatClient.kt:79` 以 `Authorization: Bearer` 发出，可计费）。

**修复方案**：
1. `allowBackup="false"`（本应用已有自建 JSON / WebDAV 全量备份能力，系统备份价值不高）；或在两个规则文件中补全 `<exclude domain="sharedpref" path="readtrace_ai_prefs"/>` 等。
2. `getAiApiKey` / `setAiApiKey` 改走 `SecurePrefs`，照抄 WebDAV 密码的「旧明文首读自动迁移并抹除」写法。

**验证方式**：断点确认 AI Key 以密文落盘、旧明文被抹除；`adb shell bmgr` 或备份规则静态检查确认凭据不在备份集内。

**改动量**：小

---

### T0.7 实现 `onDowngrade`

**问题等级**：P1 — 降级安装直接崩溃

**证据**：全文件 0 命中 `onDowngrade` / `onConfigure` / `enableWriteAheadLogging` / `setForeignKeyConstraintsEnabled`（已独立 grep 确认）。`BookDatabaseHelper.kt` 中 `DATABASE_VERSION = 15`（`:3811`）。

**后果**：用户装过更高版本（如 v16 测试包）后回退到当前正式包 → `SQLiteDowngradeFailedException` 崩溃。

**修复方案**：实现 `onDowngrade`，行为为「删表重建」或「备份后重建」。同步补 `onUpgrade` 前置备份（版本跨度 > 1 时先复制 `readtrace.db.bak_v{old}`）。

**验证方式**：手动把 `DATABASE_VERSION` 调高安装、再调回低版本安装，确认不崩溃且有明确数据策略。

**改动量**：小

---

## 4. T1 梯队：发布与安全加固

### T1.1 release 无正式签名时构建失败

**证据**：
- `app/build.gradle.kts:58-60`：缺 `keystore.properties` 时 `else { initWith(getByName("debug")) }` — release 包用**公开口令（`android`）的 debug key** 签名。
- `.github/workflows/release.yml:40-62`：未配置 secrets 时自动生成 debug.keystore 并打印 `falling back to debug signing`；`:104-106` 无条件上传发布。

**后果**：签名无效的包会被正常发布，用户无法验证包来源。

**修复方案**：release 构建在无正式签名配置时 `throw GradleException`；debug **不要**复用 release signingConfig（`build.gradle.kts:68-70`）；`assembleRelease` 后加 `apksigner verify --print-certs` 校验指纹。

**改动量**：小

---

### T1.2 收敛明文流量

**证据**：`AndroidManifest.xml:22` `android:usesCleartextTraffic="true"` 为全局；真实明文请求位于 `util/NeteasePreviewHelper.kt:47-48`（`http://mobilecdn.kugou.com`、`http://m.kugou.com`）。

**修复方案**：移除全局 `usesCleartextTraffic`，新增 `res/xml/network_security_config.xml` 仅对这两个酷狗域名放行；或直接改用其 https 域名。

**改动量**：小

---

### T1.3 收敛 `exported` 组件

**证据**：`AndroidManifest.xml` 中共 16 个 `exported="true"` 的 Activity，其中仅 `:98 BookDetailActivity`（`readtrace://` 深链）与 `:136 MainActivity`（LAUNCHER）有 `<intent-filter>`。其余 14 个（`:28, 37, 40, 43, 46, 52, 112, 121, 133, 184, 189, 194, 197`）**无 filter、无 `android:permission`**。

**后果**：任意第三方应用可用显式 Intent 拉起 `AddBookActivity`、`DiscoverActivity`、`CuratorAuthActivity`、`CuratorProfileEditActivity` 等内部页面，绕过应用自身导航与前置校验。`VinylCassettePlayerActivity`(`:112`) 还是 `singleTask`。

**修复方案**：除 `MainActivity`、`BookDetailActivity`（深链）、`WXEntryActivity`（微信 SDK 必需）外全部改 `exported="false"`。

**改动量**：小

---

### T1.4 `WXEntryActivity` 沙盒档案加门控

**证据**：`wxapi/WXEntryActivity.kt:115-155` 接受沙盒模式 extra；`auth/WeChatAuthManager.kt:145-153` 用 `Random(System.currentTimeMillis())` 生成格式合法（28 位十六进制）的 openId/unionId。`parseOfficialProfile`（`:202-206`）因无服务端换 token **恒返回 null**，即正式模式实际不可用但静默降级沙盒。

**修复方案**：沙盒档案生成加 `BuildConfig.DEBUG` 门控；校验 `getCallingPackage()`；正式模式下明确提示「微信登录暂不可用」而非静默降级。

**改动量**：中

---

### T1.5 删除死权限

**证据**：`AndroidManifest.xml:9-10` 的 `WRITE_EXTERNAL_STORAGE maxSdkVersion=28`、`READ_EXTERNAL_STORAGE maxSdkVersion=32` — 项目 `minSdk=31`，两条**永远不会生效**。

**修复方案**：删除；封面选择改用 `PickVisualMedia`（Photo Picker）可一并免去 `READ_MEDIA_IMAGES`。

**改动量**：小

---

## 5. T2 梯队：性能优化（用户可感知）

### T2.1 藏库 Tab 去掉强制重查 ✅ 高收益低风险

**证据**：`ui/fragment/LibraryFragment.kt:138-141`
```kotlin
override fun onResume() {
    super.onResume()
    refreshLibrary(forceDbReload = true)   // 每次切回该 Tab 都绕过缓存
}
```
`LibraryFragment.kt:432-435` 中 `forceDbReload=true` 会绕过 `getCachedBooks()` 缓存：
```kotlin
if (forceDbReload || cachedAllBooks.isEmpty()) {
    cachedAllBooks = databaseHelper.getCachedBooks()   // 实为 getBooks()
}
```
`BookDatabaseHelper.kt:2149` 的 `getBooks()` 用 `null` 作 columns → **`SELECT *`，连 `description` 长正文一起读出**，主线程逐行 `toBook()` 反序列化，再全量 filter（含 `PinyinSearchHelper.matchesBook`）+ `renderDynamicTags` 重建所有标签 Chip。

**修复方案**：`onResume` 改用 `forceDbReload = false`（写操作已通过 `invalidateBookCache()` 精确失效缓存，见 `BookDatabaseHelper.kt:1983`）；`getBooks()` 改列白名单，列表场景不带 `description`，详情页按 id 单独取。

**验证方式**：`adb shell` 埋点对比切 Tab 耗时；确认写操作后列表仍正确刷新。

**改动量**：小

---

### T2.2 冷启动同步判断移入后台

**证据**：`ReadTraceApplication.kt:19` 在 `onCreate` 直接调用 `WebDavSyncEngine.performAutoSyncIfDue(this)`；其中 `sync/WebDavSyncEngine.kt:83` 的 `loadConfig()` 会读加密凭据（`UserPreferencesManager.kt:112` → AndroidKeyStore 解密，数十至数百毫秒），**该前置判断在主线程**（真正的网络同步已在 `WebDavSyncEngine.kt:90` 的 executor 内）。

**修复方案**：把「是否该同步」的前置判断整体移入后台线程。

**改动量**：小

---

### T2.3 绘制热路径去分配

**证据**：
- **`Color.parseColor` 全项目 512 处调用**，其中大量位于 `onDraw`：`MindprintRadarView.kt`(24)、`VinylTurntableView.kt`(24)、`CassetteDeckView.kt`(21)、`MindprintConstellationView.kt`(20)、`MindprintTopologyView.kt`(10) 等。
- `widget/MindprintConstellationView.kt:482` — `LinearGradient` 在 `onDraw` 的 `forEach` 内**逐边 new**；`:537` 同处 `RadialGradient`。配合该文件 8 处 `invalidate()` / `postInvalidateOnAnimation`，构成**每帧 O(边数) 次对象分配**。
- 同类：`widget/MindprintRadarView.kt:225,288,289,313,314`（`Path()`、`mutableListOf`、`Pair`）、`widget/InfiniteMarqueeView.kt:162`（`Rect()`）、`widget/MindprintTopologyView.kt:246`（`LinearGradient`）。

**修复方案**：Paint / Path / Shader 提为字段，仅在 `onSizeChanged` 重建；颜色改为 `@ColorInt` 常量或预解析 `IntArray`。

**验证方式**：Android Studio Profiler 对比 GC 频率与掉帧数。

**改动量**：中

---

### T2.4 补齐缺失索引 + 启用 WAL

**证据**：
- 现有 9 个索引：`books(status,is_deleted)`、`books(source_type,source_id)`、`notes(book_id,is_deleted)`、`reading_sessions(book_id,is_deleted)`、`book_characters(book_id,is_deleted)`、`book_outlines(book_id,is_deleted)`、`book_locations(book_id,is_deleted)`、`book_mindprints(book_id)`、`curator_favorites(media_type,rank_order)`。
- **无索引但被热查询命中**：`title`（播种期每条 3 次全表扫）、`is_deleted`、`updated_at`（`getBooks` 的 ORDER BY 全表扫 + 临时 B-tree）、`finish_date`、`category`；`audio_tracks` 表**零索引**但 `getAudioTracks` 按其 `book_id` 查。
- **WAL 未启用**（`enableWriteAheadLogging` 全项目 0 命中）→ 大事务（`importFullBackup`）持写锁期间主线程读可能被阻塞或抛 `SQLiteDatabaseLockedException`。

**修复方案**：新增 v16 迁移补索引；`onConfigure` 中 `enableWriteAheadLogging()`。

**验证方式**：`EXPLAIN QUERY PLAN` 确认关键查询由 `SCAN` 变 `SEARCH ... USING INDEX`。

**改动量**：中

---

### T2.5 布局减层

**证据**：85 个 layout 中，最深处达 7 层：`activity_book_detail.xml`（**1281 行** / 7 层）、`fragment_hub.xml`（**1124 行** / 7 层）、`activity_add_book.xml`（595 行 / 7 层）、`activity_media_hub.xml`、`fragment_library.xml`、`activity_phone_auth.xml` 等 6 层。

**修复方案**：深层 `LinearLayout` 嵌套改 `ConstraintLayout`；折叠区改 `ViewStub`；标题栏等复用区改 `merge` + `include`。

**验证方式**：`Layout Inspector` 对比层级数与 measure/layout 耗时。

**改动量**：中

---

### T2.6 主线程 DB 写操作后台化

**证据**：写操作在点击回调同步落盘，例如 `LibraryFragment.kt:823,830,837,844,968,975`（`updateBook` / `archiveBook`）；`QuickLogBottomSheet.kt:76,213,494`；`AddNoteActivity.kt:108,156,204,206,243`；`TrashActivity.kt:191,193,203,221,240,259`。

**修复方案**：给 `BookDatabaseHelper` 套一层薄 `BookRepository`，把**写操作**统一移入单线程 executor（项目已有此模式，见 `BookDetailActivity.kt:215-245`、`TrashActivity.kt:106,146` 等已正确后台化的范例）。不改表结构、不改调用语义。

**改动量**：中

---

### T2.7 消灭 N+1 查询

**证据**：`BookDatabaseHelper.kt:3314`（`getAnnualMindprintPersona` 逐书查询）、`:2766-2776`（`getAllFullWorkBackups` 每部 6 次子表查）、`:2825-2901`（`importFullBackup` 每部 7 读 + O(N×M) 内存去重）；`MindprintTopologyActivity.kt:117-125`（`onCreate` 内 for 循环 `getMindprint`）；`sync/WechatMinappSyncProtocol.kt:52`（已有 `getAllMindprints()` 却逐书查）。

**修复方案**：改为批量查询 + 内存分组。

**改动量**：中

---

### T2.8 修正 `getMindprint` 的空值语义

**证据**：`BookDatabaseHelper.kt:3276-3287` — `getMindprint` 为非空签名，**无数据时返回六维默认 8.0/5.0 的假对象**。后果：
1. `:3314 getAnnualMindprintPersona` 把无档案作品按 8.0 计入均值 → 年度人格系统性偏移；
2. `sync/WechatMinappSyncProtocol.kt:53` 的 `if (mindprint != null)` **恒真** → 导出包为所有作品注入伪造六维，污染小程序端。

**修复方案**：返回值改可空（约 20 处调用点需同步处理）。

**改动量**：中

---

### T2.9~T2.11 待补充

UI 与渲染专项审查仍在进行，返回后追加（预期覆盖：传感器监听注销、动画未在 `onPause` 停止、RecyclerView Adapter 绑定重活、Bitmap 导出未 recycle 等）。

---

## 6. T3 梯队：工程质量与仓库治理

### T3.1 CI 真正跑 lint

**证据**：`.github/workflows/ci.yml:35-36`
```yaml
- name: Run Lint & Unit Tests
  run: ./gradlew testDebugUnitTest --continue
```
**步骤名叫 Lint，命令里没有 lint**。全项目亦无 `lint { }` 配置或 baseline。

**修复方案**：拆为两步，先加 `./gradlew lintDebug` 并生成 baseline 作存量豁免，之后只允许新增零告警。同时补 `android-actions/setup-android` 或 `sdkmanager --licenses` 步骤（当前 CI 缺 SDK 许可接受，AGP 自动下载 SDK 可能失败 — **此项未经运行验证**，建议 `workflow_dispatch` 手动触发确认）。

**改动量**：小

---

### T3.2 补 `DatabaseMigrator` 测试

**证据**：当前单测 13 个文件 / 62 个 `@Test` / 952 行，**全部为纯 JVM 纯函数测试**（解析器、算法引擎、JSON 往返）。`BookDatabaseHelper.kt`（3831 行）、`DatabaseMigrator.kt`（381 行，v1→v15）、`sync/`、`auth/`（含 `SecurePrefs` 加解密）**零测试**。覆盖率 ≈ 1266 / 44115 ≈ **2.9%**，无 jacoco 配置。

**为什么优先补迁移器**：v14/v15 已暴露两处破坏用户数据的缺陷却零测试覆盖（见 T0.1）。

**修复方案**：用 Robolectric 或 `androidTest` 覆盖 v1→v15 全链路，并断言「用户数据不被篡改」。后续按风险倒序补 `DatabaseSchema`/CRUD 关键路径、`SecurePrefs`、`BackupHelper` 文件 IO。

**改动量**：大

---

### T3.3 仓库瘦身

**证据（均已核验）**：

| 项 | 数据 | 说明 |
|:---|:---|:---|
| `cover_server/covers/` | **225 张图 / 36.3 MB，已被 git 跟踪** | 其 `README.md` 自述「不打包进 APK」，纯仓库负担 |
| 历史中的 `ReadTrace_v4.2.1.apk` | **19.5 MB** 单个 blob | `.gitignore` 已含 `*.apk`，但历史里还在 |
| `2k图片.jpeg` | 1.6 MB，**全仓库零引用却被跟踪** | 建议移出跟踪 |
| Git pack 总量 | **78.83 MiB** | 以上三项约占 57 MB |
| `mp-readtrace/`（小程序端） | 被 `.gitignore:41` 忽略，**完全不在版本控制** | README 宣称有小程序端，代码无版本备份 |
| `.gitignore:6` | `不要放进git/` — **漏了 `#`**，被当成一条忽略路径 | 一行修复 |
| `.workbuddy/memory/*.md` | 6 个文件**既被跟踪、又命中 `.gitignore` 的 `.workbuddy/`** | `git ls-files -i -c --exclude-standard` 可复现 |
| 根目录调试产物 | `.tmp_anr_db.db`(229KB)、`_verify.db`(0B)、`device_readtrace.db` / `emulator_readtrace.db`(各 848KB)、`ReadTrace_1.0.12.apk`(8.3MB) | 均未被跟踪 |
| `docs/` 目录 | commit `388c944` 已删除，但 README 中 3 个链接 + 9 张截图画廊**全部 404** | `LICENSE` 文件亦不存在，而 README 声明 Apache-2.0 |

**⚠️ 涉及删除/历史重写，每一项都需用户明确确认后再执行。**

**修复方案**：
1. 一行修复 `.gitignore` 第 6 行（补 `#`）。
2. 修复 README 的 404 链接与失效画廊（恢复 `docs/` 或摘除链接）。
3. 决定 `cover_server/`、`2k图片.jpeg`、`mp-readtrace/` 的去留（移出跟踪 / Git LFS / 补忽略规则）。
4. 历史瘦身（需 `filter-repo` 等重写操作）— 属高风险，单独评估。

**改动量**：小~中（不含历史重写）

---

### T3.4 补全局未捕获异常采集

**证据**：`ReadTraceApplication.kt:14-36` 中无 `Thread.setDefaultUncaughtExceptionHandler`，无任何崩溃上报。线上崩溃**完全不可观测**。

**修复方案**：加最小崩溃采集（写入文件 + 下次启动上报或导出）。

**改动量**：中

---

### T3.5 架构演进判断（明确不建议的部分）

**现状**：`ViewModel` / `LiveData` / `StateFlow` 全项目 **0 命中**，`Room` 0 命中。本地 DB 无 Repository 边界，Activity/Fragment 直接同步调用 `BookDatabaseHelper.getInstance()`。异步靠裸 `Thread{}`（约 26 处，全部不可取消）、`Executors`（约 13 处）、`Handler(mainLooper)`（约 20 处）；仅 `DiscoverActivity.kt:628` 一处使用协程。

**判断**：
1. **建议做**：薄 `BookRepository` 层（见 T2.6），先把写操作移出主线程。收益/成本比最高。
2. **建议做**：仅在**状态会被配置变更打断**的页面引入 ViewModel — `BookDetailActivity`(2305 行)、`DiscoverActivity`(1018 行)。
3. **暂不做 — Room**：需替换 3831 行 helper + 381 行手写迁移 + 所有 `rawQuery` 调用点，要引入 KSP/kapt（构建时间上升，且 Kotlin 内置编译器路径正是当前 `app/build.gradle.kts:88-95,113-123` 单测 hack 的来源），APK 增约 0.5~1MB，还需为存量用户写 Room 侧迁移。**只有当表结构继续增长、手写迁移缺陷再出现第二次时，收益才盖过代价。**
4. **明确不建议**：现阶段引入 Compose / Hilt / Navigation — 43 个自绘 View 与 37 个 Activity 的迁移成本远超收益。

---

## 7. 执行顺序建议

```
T0.1 迁移守卫（2 行）        ← 最优先，正在破坏用户数据
  ↓
T0.2 验证码 fail-closed      ← 身份校验形同虚设
T0.3 播种移出主线程 + 包事务  ← 必然 ANR
T0.4 封面 UPDATE 守卫         ← 静默覆盖用户数据
  ↓
T0.5 gradle.properties（需确认）
T0.6 allowBackup + AI Key（需确认）
T0.7 onDowngrade
  ↓
T1.1~T1.5 发布与安全加固（各项独立）
  ↓
T2.1 藏库强制重查（高收益低风险）
T2.2~T2.8 性能纵深
  ↓
T3.1~T3.4 工程治理
```

**为什么 T0.1 排第一**：改动仅 2 行、可独立验证，但性质是**正在静默破坏用户数据且不可逆**，比性能问题严重一个量级。

---

## 8. 验证与提交规约

### 每项任务的完成定义（DoD）
1. 代码修改完成，且**仅修改本任务相关文件**（遵循最小修改原则）。
2. 执行与本任务相关的最小验证：
   - **T0.1 / T0.4 / T0.7**：迁移器测试（构造受影响数据 → 跑迁移 → 断言用户数据未被改动）+ `assembleDebug`
   - **T0.3**：StrictMode 冷启动检查 + 埋点计时
   - **T1.x**：`assembleRelease` 通过 + 签名校验
   - **T2.x**：Profiler 对比 / `EXPLAIN QUERY PLAN` / 布局层级对比
3. 全量 `./gradlew testDebugUnitTest` 通过（**首次运行会往 C 盘写 Gradle 发行包约 150 MB 及 `build/` 产物，需用户事先授权**）。
4. 中文 commit（格式 `类型：修改内容`，如 `修复：解决迁移器丢失source_type守卫导致用户评分被篡改`）。
5. Push 到当前远程分支。

### 禁止事项
- 禁止为了「顺手」重构无关代码、格式化整个项目、升级无关依赖。
- 禁止使用 `git reset --hard` / `git clean -fd` / `git push --force` / `git rebase`。
- 禁止在没有验证的情况下声称「已完全解决」。
- 任何涉及**删除、覆盖、历史重写**的操作，执行前必须获得用户明确允许。

---

## 9. 待用户决策清单

| # | 决策项 | 关联任务 | 影响 |
|:---:|:---|:---|:---|
| 1 | 是否授权修改迁移逻辑？是否需要对已被 v15 改写的评分做补偿？ | T0.1 | 历史数据 |
| 2 | 是否授权把 `gradle.properties` 移出版本控制？ | T0.5 | 构建系统 + CI |
| 3 | 是否授权运行 `./gradlew testDebugUnitTest`（C 盘约 150 MB）？ | 全部 | 验证能力 |
| 4 | `cover_server/`(36.3MB)、`2k图片.jpeg`(1.6MB) 如何处置？ | T3.3 | 仓库体积 |
| 5 | `docs/` 目录是恢复还是摘除 README 链接？是否补 `LICENSE` 文件？ | T3.3 | 文档完整性 |
| 6 | `mp-readtrace/` 小程序端是否纳入版本控制？ | T3.3 | 代码备份 |
| 7 | `targetSdk = 37` 是否回退到稳定版 36？ | 未列入 T1 | 发布策略 |

---

## 10. 附：本次审查的核验记录

| 审查方结论 | 核验结果 |
|:---|:---|
| 数据层：冷启动播种在主线程 | ✅ 属实（`BookDatabaseHelper.kt:57-63` + `HubFragment.kt:199-203`） |
| 数据层：4 个种子方法无事务 | ✅ 属实（`beginTransaction` 全文件仅 9 处，播种方法之前为 0） |
| 数据层：`SQLiteStatement` 未关闭 | ✅ 属实（`compileStatement` 全文件仅 `:2019`，无 `.use{}`） |
| 数据层：2 处 Cursor 泄漏 | ✅ 属实（`getFavorites:3757`、`getFavoriteCount:3784` 不在 `finally`） |
| 数据层：`onDowngrade`/WAL/外键未实现 | ✅ 属实（全部 0 命中） |
| 数据层：播种封面覆盖用户数据 | ✅ 属实（行号修正为 `:1137`） |
| 工程化：`gradle.properties` 被跟踪 | ✅ 属实（`git ls-files --error-unmatch` 返回该文件；非空密钥历史 0 条） |
| 工程化：手机号沙盒降级 | ✅ 属实（`PhoneAuthManager.kt:118-141`） |
| 工程化：空备份模板 | ✅ 属实（两个 xml 均为未改动的模板） |
| 工程化：AI Key 明文落盘 | ✅ 属实（`UserPreferencesManager.kt:174-181`） |
| 工程化：CI 假 lint | ✅ 属实（`ci.yml:35-36`） |
| 工程化：`.workbuddy` 跟踪与忽略冲突 | ✅ 属实（`git ls-files -i -c --exclude-standard` 可复现） |
| **追加发现**：v14/v15 丢失 `source_type` 守卫 | ✅ 对照 v13 实现确认为**回归缺陷** |
| 工程化：`targetSdk=37` 处 beta 轨道 | ⚠️ **未核验**（需联网确认 Android 17 发布状态） |
| 全部：单测是否全绿、CI 能否跑通 | ⚠️ **未核验**（未执行构建） |
| 全部：播种耗时 1.5~3.5s | ⚠️ **未实测**（理论估算） |

---

**文档结束**
