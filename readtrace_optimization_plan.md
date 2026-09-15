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
> - **✅ T3 全部完成（2026-09-13）**：
>   - **T3.1**：`lintDebug` 接入 CI（原「Run Lint & Unit Tests」步骤名为 Lint 实际只有单测）；存量 2 errors + 2116 warnings 登记 `app/lint-baseline.xml` 豁免，此后仅对新增告警把关。
>   - **T3.2**：新增 `DatabaseMigratorJvmTest`（Robolectric 4.17，JVM 门禁内跑，CI 无需模拟器）：镜像 v14/v15 守卫 + v13→v16 全链路数据保全 + v16 索引补齐与重复执行幂等 + onCreate 九表八索引完整性 + v6 播客并入音乐数据迁移；全量单测 62 → **69 个全绿**。android-all 运行时（145MB）经 `maven.repo.local` 重定向至 D 盘 `build/robolectric-maven-repo`，不触碰 C 盘。
>   - **T3.3**：`cover_server/` 整目录删除（README 出库 + 磁盘 36.5MB 原图存档清除，历史 blob 仍可从 git 历史找回）；`.workbuddy/memory/` 6 个「既跟踪又命中忽略」文件解除跟踪（磁盘保留）；README 失效 docs 链接与 LICENSE 缺失已在更早的 `148cca8` 完成，本轮核验 11 个现存链接全部有效。
>   - **T3.4**：新增 `util/CrashReporter`——Application 最早时机安装全局未捕获异常处理器，崩溃现场同步落盘 `filesDir/crash_reports/`（应用版本/设备/线程/堆栈）后交还系统默认处理器，下次启动自动清理仅留最新 5 份；「关于阅痕」长按版本徽标以系统分享导出，无记录时空态提示。

> **✅ 第三轮审查已完成（2026-09-15）→ 新增 T4 梯队**
> T0~T3 的结论本身**依然成立**，但复查发现两件事：① **T3 收官之后新增的功能**（藏库导出长卷、全息长卷预览、年鉴工作室）引入了 3 项 P1 级稳定性缺陷，从未进入任何一轮审查；② 历史功能删除后**残留 42 处文本不一致**，其中 4 处会真实显示给用户。（本轮曾把社区展厅的「🏛️ 3D 漫游」误判为死按钮，已由编译报错揭穿并推翻，详见 T4.5-A #2）。此外本文档的基线数据已漂移，详见 **T4.0**。
> **另有一项机制性失败必须记录**：`app/lint-baseline.xml` 的问题清单中**包含 `UnusedResources`** —— 即 T3.1 生成 baseline 时，那 23 条僵尸字符串**已被 lint 识别**，随后被永久豁免。门禁建成了，但把这一项的告警吞掉了。详见 T4.9。
>
> **🔬 T4.9 数据采集轮已完成（同日）**：临时移出 baseline 跑全量 `lintDebug` 交叉校验后，**人工 23 条字符串结论被 100% 确认（零假阳性）**，但 lint 额外暴露了 **112 项未使用资源**（人工仅覆盖 23）与 **12 个孤儿布局/drawable 文件**。因此本文件头的「无悬空布局/drawable 引用」一类结论**已被推翻并回改**，详见 T4.5-E 与 §11。baseline 本身未被修改（SHA256 前后比对一致）。

> **⚠️ 行数口径说明**：本计划所有文件行数统一采用**非空行**口径（PowerShell `Measure-Object -Line`）。
> 若某处引用的是**含空行**口径，会显式标注。两套口径的换算参考：
> `BookDetailActivity` 非空 2,305 / 含空行 2,529；`VinylCassettePlayerActivity` 1,707 / 1,833；
> `AddBookActivity` 938 / 1,019；`DiscoverActivity` 1,018 / 符合原值。

---

## 0. 计划来源与核验状态

本计划基于三轮并行只读审查（数据层与并发 / UI 与渲染 / 工程化与安全）汇总而成；2026-09-15 追加第四轮专项审查（新增功能稳定性 / 文本一致性）。

| 审查维度 | 状态 | 说明 |
|:---|:---:|:---|
| 数据层与并发/线程模型 | ✅ 已完成并核验 | 6 项核心指控：5 项完全属实，1 项经二次复核确认；已修正其 2 处行号偏差 |
| 工程化与安全 | ✅ 已完成并核验 | 6 项核心指控全部属实 |
| UI 与渲染性能 | ✅ 已完成并核验 | 原「⏳ 进行中」为陈旧状态；已于 2026-09-12 闭环，结论见 §5 末与本文件头。**但该次闭环早于两个长卷功能的诞生**，未覆盖 T4.1~T4.4 |

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
| **T4** | 新增功能稳定性 + 文本一致性 | 12 | 每项 ≤ 10 行（T4.5 为大清单除外） | 分批，1~2 天 |

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

## 7. T4 梯队：新增功能稳定性与文本一致性

> **来源**：2026-09-15 第三轮只读审查。本梯队问题**全部产生于 T0~T3 收官之后**——要么是新增功能引入（藏库导出长卷 `b3225e8`/`8421a19`/`595354b`、全息长卷预览、年鉴工作室），要么是历史功能删除后的文案残骸。
> **核验声明**：本轮 40 条文本不一致与 8 项性能/架构缺陷，**每一条均经当前代码实测复核**（`Select-String` 统计引用数 + `Test-Path` 确认文件存在 + 实读上下文）。未采信任何仅来自旧文档或仅来自子代理的行号与数字；子代理报的 `getInstance` 51 处已按实测 65 处修正。

### T4.0 基线漂移订正（先读这条，避免误用旧数据）

| 本文档原有记录 | 2026-09-15 实测 | 影响 |
|:---|:---|:---|
| `BookDatabaseHelper` 3,805 非空行 | **4,047 非空行 / 120 个 `fun`** | T3.5 的拆分量化清单需按新值重估 |
| 「45 处 Activity 级裸 `Thread`」 | **71 处** | 且新增实例连守卫都没带（见 T4.4） |
| `Color.parseColor` 512 处 | **518 处** | 增量集中在 T2.3 之后新增的两个长卷 View |
| T2.7「消灭五处 N+1」 | 存在**第 6 处**（`AnnualChronicleStudioActivity.kt:101`） | 见 T4.6 |
| T2.9~T2.11「大 Bitmap 依赖延迟 GC，非泄漏，登记为已知债」 | 对**详情页长图 / 心选长卷**仍成立；对**新增两个长卷不成立** | 无上限 + 不 recycle + 无守卫 = 即时 OOM，单列 T4.1~T4.2 |
| T3.5「建议做薄 `BookRepository`」 | 至今引用数 **0**；UI 层 `BookDatabaseHelper.getInstance(` 达 **65 处** | 降级为触发条件制（见 T4.11） |
| `DATABASE_VERSION = 15`（T0.7 记录） | 现为 **17**，但 `DatabaseSchema.kt:8` 仍停在 15 | 见 T4.8 |
| 技术栈现状 | `ViewModel` `LiveData` `StateFlow` `Room` `@Dao` 命中数**全部为 0**；`Dispatchers.` 仅 4 处 | 与 T3.5 判断一致，不作为任务 |

**正面确认（避免后续误报）**：以下项实测**确已按 plan 修好**，不在 T4 范围——`CoverImageHelper` 有 LruCache + `inSampleSize` + `RGB_565` 降采样 + 有界线程池；全工程 `.commit()` 为 **0**（均 `apply()`）；五个网络客户端均有超时且无重复建 client；`data` 包**不 import 任何 ui/widget**（无循环依赖）；单例持 `applicationContext`（**无 Context 泄漏**）；`getAllFullWorkBackups()` 已批量；AndroidManifest 无悬空组件。

> ❌ **本行原写「无失效 `@layout/`/`@drawable/` 引用」，已被 lint 实测推翻** —— 确实存在 3 个孤儿布局与 9 个孤儿 drawable。详见 T4.5-E。

---

### T4.1 两个长卷导出 Bitmap 无尺寸上限 🔴 最高优先

**问题等级**：P1 — 大库必然 OOM

**证据**：
- `widget/LibraryScrollView.kt:114-121`
  ```kotlin
  val targetHeight = calculateContentHeight(targetWidth.toFloat()).toInt().coerceAtLeast(800)  // 只有下限
  val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
  ```
- `widget/MediaTimelineScrollView.kt:816-817` 同构，且**连 `coerceAtLeast` 下限都没有**。
- 高度完全由藏品数量累加决定，无 MAX 封顶。ARGB_8888 单像素 4 字节，1080×30000 ≈ **123MB 单张 Bitmap**，超 Canvas 上限即 OOM。
- **同项目内即有正确范例**：`AnnualChronicleStudioActivity.kt:319-325` 写了 `val maxDimension = 4096f` → `val scale = if (rawHeight > maxDimension) maxDimension / rawHeight else 1.0f` 后按比例缩放，且 `:358` 显式 `bitmap.recycle()`。两个长卷缺的正是这两步。

**修复方案**：在两处 `exportUltraHdBitmap()` 内引入 `maxHeightPx`（建议 8192；如与年鉴一致取 4096 则长卷可读性下降，需权衡），`val scale = (maxHeightPx.toFloat() / targetHeight).coerceAtMost(1f)`，`createBitmap` 用缩放后高度，`drawScrollContent` 前 `canvas.scale(scale, scale)`。与年鉴对齐。

**验证方式**：构造超大规模藏书（≥300 条）走一次导出，确认不崩、且 `bitmap.byteCount` 在预期量级；小库场景确认缩放为 1.0（画质无回退）。

**改动量**：每文件 6~8 行，低风险

---

### T4.2 长卷 Bitmap 不 recycle + `releaseCovers()` 死方法 🔴

**问题等级**：P1 — 与 T4.1 叠加构成即时 OOM（非良性的延迟 GC）

**证据**：
- `LibraryScrollPreviewActivity.kt` 实测 grep `recycle|isFinishing|isDestroyed|onDestroy|releaseCovers` → **命中 0 条**。生成大位图、压缩写盘后直接丢弃不回收，连 `onDestroy` 都未重写。
- `MediaTimelineScrollActivity.kt:223-224` 的 `onDestroy` 为**空实现**（仅 `super`）。
- `widget/LibraryScrollView.kt:106-108` 的 `releaseCovers()` 全项目命中数 **1（仅定义处）** → 导出后封面 LruCache（该 View 自建，`:42` 堆 1/8）永不释放。
- 对照正确范例：`QuotePosterActivity.kt:252/285`、`AnnualChronicleStudioActivity.kt:358` 均显式 recycle。

**修复方案**：两处 `exportAndShareScroll()` 写盘完成后无条件 `bitmap.recycle()`（放 `runCatching` 外，照年鉴写法）；两个 Activity 的 `onDestroy()` 调 `scrollView.releaseCovers()`。

**验证方式**：连续导出两次不崩；Profiler 看导出后堆内存回落。

**改动量**：4~6 行，低风险

---

### T4.3 备份导出全程在主线程 🔴

**问题等级**：P1 — 大库必然 ANR

**证据**：`BackupActivity.kt:232-249` 的 `exportDataToFile()` 由 `:27`/`:33`/`:39` 三个 `registerForActivityResult(CreateDocument)` 回调**直接调用 → 主线程**。函数内串行：`:233` `getAllFullWorkBackups()`（全表 + 六阶维度）→ `:235-237` 三种格式纯 CPU 序列化 → `:243` `content.toByteArray()`（整包再拷一份）→ `:242` 写盘。**零线程切换**。
- **同文件内即有正确范例**：`importDataFromFile()` 从 `:254` 起已正确 `Thread{}` 后台化并带 `:262` 守卫。**导入导出一边做了一遍没做**。

**修复方案**：将 `:233-249` 整体包进 `Thread { … runOnUiThread { if (isFinishing || isDestroyed) return@runOnUiThread; Toast } }`，照抄导入范式。SAF 回调内即时取到的 `uri` 无需 `takePersistableUriPermission`。

**验证方式**：大库导出时不弹 ANR；失败 Toast 仍在主线程弹出。

**改动量**：约 8 行包裹，低~中风险（需把 Toast 回主线程）

---

### T4.4 新增长卷 `runOnUiThread` 无生命周期守卫 🟡

**问题等级**：P1 — 特定路径崩溃

**证据**：`LibraryScrollPreviewActivity.kt:93`（内含 `:104` `startActivity(shareIntent)`）与 `:107`、`MediaTimelineScrollActivity.kt:203` —— 后台导出完成后**无条件**回主线程 `startActivity`，**无 `isFinishing || isDestroyed` 判断**。对比 `ResonancePosterActivity.kt:279/311`、`AnnualChronicleStudioActivity.kt:360`、`BackupActivity.kt:262` 均有守卫。

**净效果**：导出耗时较长时用户退出页面 → Activity 销毁后回调 `startActivity`。

**说明**：plan 登记的「45 处裸 Thread 按需迁移」已涨至 **71 处**，且**新增实例连守卫都没带上**——即「按需」部分在新增代码上未落实。

**修复方案**：每处 `runOnUiThread {` 首行补 `if (isFinishing || isDestroyed) return@runOnUiThread`。

**改动量**：每处 1 行，极低风险

---

### T4.5 僵尸文本清理（人工 42 处 + lint 追加 112 项）🔴 清单型任务

**问题等级**：P1（用户可感知的错误信息）+ P2（维护误导）

**判定基准**（已 `Test-Path` 确认**已物理删除**）：`Gallery3DActivity.kt`・`SpatialParallaxGalleryActivity.kt`・`BookDetailActivity 内 3D 阅读器入口`・`Book3DReaderActivity.kt`・`ReadingTimerActivity.kt`・`ObsidianPureBlackEngine.kt`・`activity_gallery_3d.xml`。即 **3D 私人展厅 + 2.5D 视差展厅 + 3D 翻书阅读器三者全部不存在**。

#### 🚨 红线：以下「展厅」存活，不得连带清理

| 项 | 状态 |
|:---|:---|
| `community/ui/CommunityGalleryActivity.kt` | ✅ **实测存在**，社区展厅是活功能 |
| `gallery3d/Gallery3DRenderer.kt` | ✅ **实测存在**，被社区展厅使用（OpenGL ES 渲染未删） |
| `fragment_profile.xml:259`「漫游探访同频策展人的 **3D 虚拟展厅**」 | ✅ **正常文案**，指向社区展厅 |
| `activity_community_gallery.xml` | ✅ 存活 |
| 「防 OLED 烧屏」（`StandByZenDeskActivity:21`）、主题「曜石黑金」、「Obsidian Markdown 导出」、`DioramaBoxView` 标本盒、伴读钟 | ✅ 均存活，**不在清理范围** |

#### A 组：会真实显示给用户的 4 处（原 5 处，#2 已推翻）

| # | 位置 | 用户看到 | 实际行为 | 改法 |
|:--:|:---|:---|:---|:---|
| 1 | `ui/fragment/HubFragment.kt:509-515` | 主页首屏 Hero 大按钮 **「📖 3D 沉浸翻阅」**（`MediaType.BOOK` 分支） | `:329` 实跳 `BookDetailActivity` | 改中性文案（如「📖 进入详情」）。**注：此为硬编码字面量，不走 string 资源，故躲过了 lint 的 `UnusedResources`** |
| 2 | ~~`res/layout/activity_exhibition_detail.xml:32-46`~~ | ❌ **本条已推翻，不得改** | 原报「`detail3DExploreBtn` 全项目引用数 0 → 点击无反应」。**错**。它实现在 `community/ui/ExhibitionDetailActivity.kt:75-77`，跳转到 `CommunityGalleryActivity`（社区 3D 展厅，存活）。因此「🏛️ 3D 漫游」文案**成立且功能正常** | **不动**。保留原样 |
| 3 | `data/BookDatabaseHelper.kt:966/1206/1401/1999` + `assets/preset_all.json` 约 200 条 | 详情页「陈列位置」显示 **「展厅第5层 · 电子游戏神作馆」** | 经 `BookDetailActivity:258` 的 `detailShelfLocation` 真实渲染。**展厅已不存在，却告知用户藏品在展厅第几层** | 改中性表述（如「馆藏第5层」）。**⚠️ 涉及存量数据，见 §10 决策 #8** |
| 4 | `model/ChangelogData.kt:107/109` | App 内「版本演进纪要」：**「OLED 曜石真黑熄屏模式上线」**、**「2.5D visionOS 空间标本盒展厅，与经典 3D 展厅双模共存」**（另 `:147` 3D 陀螺仪视差画廊） | 三功能均已删。纪要在向用户介绍**不存在的能力** | 历史条目保留但需标注「已于 vX 移除」，见 §10 决策 #9 |
| 5 | `res/xml/widget_currently_reading_info.xml:3` | 桌面小部件选择器：**「支持一键直达 3D 拟真翻阅」** | 3D 翻阅阅读器已删 | 去掉「3D 拟真翻阅」描述 |

#### B 组：`strings.xml` 零引用死文案 23 条

已用脚本逐个统计 `R.string.x` 与 `@string/x` 两种引用，**以下 23 条引用数确认为 0**：
- **已删 3D 私人展厅专用（11 条）** `strings.xml:253-263`：`home_gallery_title`（🏛️ 3D 私人展厅）、`home_gallery_desc`、`home_gallery_badge_format`、`gallery_activity_title`、`gallery_activity_subtitle`（360° 环视…精神殿堂）、`gallery_theme_midnight/warm/zen`、`gallery_focus_view_detail`、`gallery_empty_title`（展厅尚在虚席以待）、`gallery_empty_desc`
- **已删 3D 翻书阅读器专用（10 条）** `:264-273`：`reader_activity_title`、`reader_action_import_txt`（导入 TXT 全文）、`reader_import_txt_success`、`reader_import_txt_failed`、`reader_theme_parchment/mint/night`、`reader_btn_add_excerpt`、`reader_excerpt_added_toast`、`reader_page_indicator_format`
- **已删阅读计时器小组件专用（2 条）** `:279-280`：`widget_reading_timer_name`（今日专注打卡）、`widget_reading_timer_desc`

另有 1 条**被引用但永不可见**：`:274 action_3d_read`（📖 3D 沉浸阅览）—— 仅作 `activity_book_detail.xml:208` `detailRead3DButton` 默认值。已实测 `BookDetailActivity:1399-1486` 五个分支：BOOK 走 `visibility=GONE`（`:1402`），其余四个均运行时改 `.text`（`:1419/1442/1463/1486`）→ **该默认值永不呈现**，属纯僵尸资源。

#### C 组：死代码与孤儿资产（7 处）

**历史成因（2026-09-15 补录）**：后 3 项来自 **2026-09-14 那次未提交的「双生共鸣算法重构」会话**（HEAD 为 `5d4b4af` 于 09-13 11:50，四个文件 mtime 均在 09-14 13:22~14:41）。该次会话把数据来路从 4 个分媒介 CSV 改为单一 `preset_all.json`（因 CSV 格式无法携带六维心智档案），**在文档中有完整记录**，但**没同步清理旧链路的残留** —— 正好命中 T4.10 清单的第 1、5 两面。

| 位置 | 实测证据 |
|:---|:---|
| `data/BookDatabaseHelper.kt:3117-3120` | `getGalleryFeaturedWorks()` 及其 KDoc「获取 3D 私人展厅陈列精选作品」—— 全项目**调用点 0**，为已删展厅取数 |
| `widget/LibraryScrollView.kt:106-108` | `releaseCovers()` **命中 1（仅定义）** → 零调用（T4.2 会接线，接线后不再是死代码） |
| `MainActivity.kt:218` | `preloadRemainingTabs()` **零调用** —— T0.x 播种后台化后的配套 Tab 预热根本没接线。**需决策：接线还是删除** |
| `data/UserPreferencesManager.kt:216-227` + `BookDatabaseHelper.kt:3165-3173` | `getReadingPage`/`saveReadingPage` 阅读页码存取，为已删 3D 阅读器服务，无外部调用方（仅内部互相委托） |
| ⬅ 新 `ui/fragment/HubFragment.kt:696` | `importAssetCsv()` 现**仅剩定义、零调用**（四个调用点已在 09-14 被删除）。当时的判断是「保留定义未删，避免过度改动」——**现在它正式成为待清项** |
| ⬅ 新 `assets/preset_books.csv` / `preset_anime.csv` / `preset_movies.csv` / `preset_games.csv` | 四个旧数据源（共 205 部）在全部 Kotlin 中**引用数 0** → **整体孤儿资产**。⚠️ **lint 不扫 `assets/`**，这类只能人工发现（再次印证 T4.5-E 的「混合信号桶」结论）。处置时需决：删文件 / 还是保留作 CSV 导入的格式范例 |
| ⬅ 新 仓库根 `912.json` / `912_filled.json` | 均为**未跟踪**调试产物（前者是原始导出，后者是 `tools/backfill_mindprint.py` 的输出，已 `cp` 为 `preset_all.json`）。中间产物无需长期留在根目录 |

#### D 组：文档层

| 位置 | 残留 |
|:---|:---|
| `README.md:119`、`:158` | 「Web 微卡 / DeepLink」「+ Web 微卡深链」—— `InteractiveWebCardExporter` 已删（`910a84a`），仅 `readtrace://` 深链存活 |
| `readtrace_project_plan.md:1073-1074` | 「3D 展厅到阅读器的破壁穿梭转场…在 3D 私人展厅中点击某部作品」—— 展厅与阅读器均已删 |
| `readtrace_project_plan.md:1477/1480/1497/1514-1518/1537` | 整节描述已删的 `ClipboardSnifferHelper`（`da6855b` 删）、`IsbnScannerActivity`（`b00e19d` 删）、ML Kit 本地扫码 |
| `readtrace_project_plan.md:72-83/523-599` | 「Python + FastAPI」「GET /api/books」等后端章节 —— 描述已归档的 `archive/fastapi-backend`；现架构为 Local-First SQLite + WebDAV |
| `ui/QuickLogBottomSheet.kt:168` | 注释「剪贴板嗅探预填」—— `ClipboardSnifferHelper` 已删，实际用入参 `prefillTitle`。命名残留，**低优先** |

#### E 组：lint 全量交叉校验揭示的额外僵尸资源（112 项）🔴

**本节为 T4.9 先于 T4.5-B 执行的直接回报**。方法：不改动 21,181 行的 baseline，而是**临时将其移出**（SHA256 校验已确认逐字节还原），跑 `lintDebug` 取全量报告后解析。

**可信度前提（已实测）**：全工程 `getIdentifier` / `resources.get*` 反射取资源**零命中** → lint 的 `UnusedResources` 在本项目**不存在误报机制**，112 项均为真未引用。

| 类别 | 数量 | 明细 |
|:---|:--:|:---|
| `strings.xml` | **94** | 含人工清单的全部 23 条，另 **71 条为人工漏网** |
| 孤儿布局 | **3** | `layout_dialog_clipboard_sniffer.xml`（对应已删的 `da6855b`）、`item_reading_session.xml`（对应已删的阅读计时/3D 阅读器）、`activity_anime_timeline_scroll.xml`（已被通用 `MediaTimelineScrollActivity` 取代） |
| 孤儿 drawable | **9** | `bg_cloud_music_icon_circle_dark` / `bg_cloud_music_picker_sheet` / `bg_cloud_music_picker_item`（云音乐选单三件套）、`bg_stat_bar`、`bg_scrollbar_thumb`、`bg_picker_selected_pill`、`bg_widget_progress_track`、`default_curator_avatar.png`、`ic_launcher_foreground_bitmap.png` |
| 孤儿 xml 规则 | **2** | ⚠️ `backup_rules.xml` + `data_extraction_rules.xml` —— **这是 T0.6 将 `allowBackup` 改 `false` 后直接变死的资源**。即 T0 自己制造了 2 个僵尸，且其「TODO 模板未改」证据注释仍留在文件里 |
| 孤儿 color / style | **3 + 1** | `R.color.black`、`readtrace_scrollbar_thumb`、`readtrace_soft_shadow`；`R.style.StarPickItem` |
| **合计** | **112** | 与 baseline 中被豁免的 `UnusedResources` 条目数（112）**完全一致** → 印证这批告警自 T3.1 起就被整批吞掉 |

**对我上轮结论的修正**：我曾报「Manifest/布局/drawable 未发现问题」—— **错**。硬引用（`@layout/xxx` 这样的声明式引用）确实干净，但**整个文件本身已无人加载**的孤儿布局/drawable 有 12 个，这类只有脱离 baseline 跑全量 lint 才能发现。

**本轮方法论收获（写进 §9 作为后续依据）**：「未使用资源」是一个**混合信号桶**，里面既有真垃圾，也有「**写了但忘接线**」的缺陷。只靠人工按功能删改历史去查（我的 23 条），**必然错过后者** —— 因为 `widget_*_name` 与任何已删功能都无关，不在推理链上。这正是门禁优于人工排查的地方。

**额外发现（人工模式天然漏）**：71 条漏网 string 中包含整族筛选标签（`status_all/reading/finished/paused/dropped/wishlist`、`tag_all`、`media_type_all`）与整族主页标题（`home_stat_*`、`home_badge_*`、`home_shelf_*`）—— 说明**历次 UI 重写把文案改成了布局内硬编码**，资源定义被整体抛弃。这同时是一笔 `HardcodedText` 债，不属本轮范围但应登记。

**🚨 关键分类：112 项不等于「全删」**。本轮顺带挖出一个**真实存在的用户可见缺陷**：

> `strings.xml:277/279/281` 定义了三个友好名称 —— 「阅痕 · 每日金句书签」、「阅痕 · 今日专注打卡」、「阅痕 · 在读书目卡片」，但实测 `@string/widget_*_name` 与 `R.string.widget_*_name` **全工程引用数为 0**，且 `AndroidManifest.xml:145/158/170` 三个 `<receiver>` **均无 `android:label`**。
> **净效果**：用户在桌面小组件选择器里看到的是**自动生成的类名**（如 `DailyQuoteWidgetProvider`），而不是已经写好的中文名。
> 这三个资源**不是垃圾，是漏接线**。因此：

| 处置 | 项 | 动作 |
|:---|:---|:---|
| **接线，不删** | `widget_daily_quote_name`、`widget_currently_reading_name` | 给 `AndroidManifest.xml:145`/`:158` 两个 `<receiver>` 补 `android:label="@string/widget_…_name"`（共 2 行，可直接用户感知收益） |
| **删** | `widget_reading_timer_name` / `_desc` | 对应的 `ReadingTimerWidgetProvider` 已删且 Manifest 未注册，**无 receiver 可接**，属真僵尸 |
| **待定** | `backup_rules.xml` / `data_extraction_rules.xml` | 因 `allowBackup="false"` 而失效。删之干净，留之无害；**建议保留**并在文件头加一行注释说明为何保留，避开下次又被当成遗漏 |

**全量 94 条未使用 string（按名排序，可直接作为删除清单）**：

```
action_backup, action_cancel, archive_failed, archive_success, badge_progress_format,
book_detail_hint, book_meta_format, book_save_failed, book_saved, book_update_failed,
book_updated, decorative_book, discover_batch_done, discover_cache_note,
discover_selected_mark, discover_summary_source, edit_book_subtitle, edit_book_title,
empty_filter_body, empty_filter_title, empty_shelf_action, empty_shelf_body, empty_shelf_title,
error_rating, field_creator, gallery_activity_subtitle, gallery_activity_title,
gallery_empty_desc, gallery_empty_title, gallery_focus_view_detail, gallery_theme_midnight,
gallery_theme_warm, gallery_theme_zen, hard_delete_book_failed, hard_delete_book_success,
hard_delete_confirm_message, hard_delete_confirm_title, hint_category, hint_rating, home_add,
home_average_empty, home_badge_summary_format, home_badge_title, home_badge_view_all,
home_gallery_badge_format, home_gallery_desc, home_gallery_title, home_insight_title,
home_memory_title, home_monthly_stat_empty, home_monthly_stat_title, home_search_hint,
home_shelf_count_format, home_shelf_filtered_count_format, home_shelf_section,
home_stat_average, home_stat_finished, home_stat_reading, home_stat_total, home_subtitle,
import_failed, import_no_new_books, import_preset_confirm_message, import_preset_confirm_title,
import_success_format, media_type_all, reader_action_import_txt, reader_activity_title,
reader_btn_add_excerpt, reader_excerpt_added_toast, reader_import_txt_failed,
reader_import_txt_success, reader_page_indicator_format, reader_theme_mint, reader_theme_night,
reader_theme_parchment, restore_book_failed, restore_book_success, search_clear, search_hint,
search_no_results, status_all, status_dropped, status_finished, status_paused, status_reading,
status_wishlist, tag_all, today_reflection_body, today_reflection_title,
widget_currently_reading_name, widget_daily_quote_name, widget_reading_timer_desc,
widget_reading_timer_name
```

> ⚠️ 其中 `widget_currently_reading_name` 与 `widget_daily_quote_name` **属于上表「接线」类，不得删**。剔除这 2 条后实删 **92 条**。

**取全量清单（重建报告时用，名单已固化在上方，无需重跑即可执行）**：
```powershell
# 临时移出 baseline → 跑 lint → 还原后解析
[xml]$x = Get-Content app/build/reports/lint-results-debug.xml -Encoding UTF8 -Raw
$x.issues.issue | Where-Object { $_.id -eq "UnusedResources" } | ForEach-Object { $_.message }
```

**验证方式**：`assembleDebug` 通过；逐一手动过主页 Hero / 任意预设作品详情页 / 版本纪要 / 小部件选择器四处，确认无指向不存在功能的描述。E 组删除后跑 `./gradlew :app:testDebugUnitTest` 确认无测试通过资源名引用这些项。

**⚠️ 检索方法论告警（本轮实际踩的坑）**：A 组 #2 这条假阳性，根因是用 `readtrace\*.kt` 这种**非递归通配符**统计引用数，漏掉了 `community\ui\` 等嵌套目录，导致把一个正常控件误判为死代码，**且已差点被删除** —— 最终由 `compileDebugKotlin` 报 `Unresolved reference 'detail3DExploreBtn'` 才暴露。同类结论已全部改用递归检索（ripgrep / `Get-ChildItem -Recurse`）重测。**规则：任何「引用数 = 0」的断言必须来自递归检索，且删除前必须跑一次编译。**

**⚠️ E 组执行约束**：lint 的 `UnusedResources` 不扫 `androidTest`/`test` 源码目录，且若资源被 `values-night`/`-land`/`-sw600dp` 等限定符变体引用，删主定义前必须逐个确认变体。**每删一组必跑 `assembleDebug`**。

**改动量**：A 组约 10 行；B 组 23 条 → **应扩至 E 组的 94 条一并处理**（同源同性质，分两次做纯浪费）；C 组 7 处；D 组文档编辑；E 组为 12 个文件删除 + 94 条资源。
**建议拆为 4 次 commit**：A（用户可见）/ B+E（全量僵尸资源，含孤儿文件）/ C（死代码）/ D（文档）。

---

### T4.6 数据访问主线程残余（合并 3 项）🟡

| 子项 | 位置 | 证据 |
|:---|:---|:---|
| a. 主线程 `getBooks()`（`SELECT *` 含长正文） | `MediaTimelineScrollActivity.kt:129`、`ResonancePosterActivity.kt:197`、`BackupActivity.kt:112` | `getBooks()`（`BookDatabaseHelper.kt:2210`）projection 传 `null` = SELECT *。**`BackupActivity:112` 拖全表只为取 `works.size`（`:114`），而 `getTotalBooksCount()` 早已存在** |
| b. 第 6 处 N+1 | `AnnualChronicleStudioActivity.kt:101` | `allBooks.sumOf { dbHelper.getNotes(b.id)… }` 每书一次 SQL；T2.7 列举的五处**不含此处**（`:143` 已置后台，不 ANR 但仍慢） |
| c. CSV 导入写库在主线程 | `BackupActivity.kt:306-340` | 与 `:252-304` 已后台化的 JSON 导入路径**不对称** |

**修复方案**：a 项——`BackupActivity:112` 改调 `getTotalBooksCount()`；另两处改用已有的 `getBooksForList()`（`:2233` 列白名单）并移出主线程。b 项——新增 `getAllNotesLite()` 批量取 `book_id, created_at` 后内存聚合。c 项——照抄 JSON 导入范式。

**改动量**：每子项 1~10 行，低风险

---

### T4.7 长卷预览 O(N²) 重绘 与 PDF 后台绘制 View 树 🟡

- **预览重绘**：`widget/LibraryScrollView.kt:238-241`（`onDraw` → `drawScrollContent`）内 `:308` 逐卡 `cardLayoutFor`、`:195-204` `titleLayoutFor` 每次 `StaticLayout.Builder.build()`，叠加 `:358/364/382/396` 多次 `Color.parseColor` 字面量；`setLibraryData:89-99` 每张封面异步回调各 `postInvalidate()` 一次 → **N 张封面 = N 次整卷重绘**，单次成本又含 N 次 StaticLayout 构建。这两个 View 晚于 UI 专项审查（`d0cc750`）诞生，故逃过 T2.3。改法：`LinkedHashMap` 缓存 layout、颜色提为成员预解析常量、封面回改用 `postInvalidateOnAnimation()` 合并。
- **PDF 导出**：`AnnualChronicleStudioActivity.kt:382` `Thread {` 内 `:401` `content.draw(canvas)`，`content` 是**已 attach 的屏幕 View 树**（含 `CulturalTreeRingsView`/`MindprintRadarView`）。非 UI 线程绘制 View 树属未定义行为（可能空白/错乱/「Only the original thread…」）。改法：取图阶段回主线程离屏绘成 Bitmap，再后台写 PDF 流。

**改动量**：预览约 30~50 行/文件（中）；PDF 需重排 `:382-405`（中）

---

### T4.8 `DATABASE_VERSION` 双真值（17 vs 15）🟡

**问题等级**：P1 — 平时不炸，一旦误用即静默的版本判断错误

**证据**（实测全量定义）：
- `data/BookDatabaseHelper.kt:4026` → `const val DATABASE_VERSION = 17` ✅ **实际生效**（`:29-30` 构造解析到 companion）
- `data/DatabaseSchema.kt:8` → 顶级 `const val DATABASE_VERSION = 15` ❌ 陈旧
- `data/DatabaseSchema.kt:105` → 门面 `const val DATABASE_VERSION = com.example.readtrace.data.DATABASE_VERSION`（= 15）❌
- `DatabaseSchema.DATABASE_VERSION` 的**引用数为 0** → 死代码，但它是**名义上的「schema 门面」**，新代码很可能优先取它。

**修复方案**：删除 `DatabaseSchema.kt:8` 与 `:105` 的 15，或由门面代理到 `BookDatabaseHelper.DATABASE_VERSION`，保证**单一真值**。

**验证方式**：`assembleDebug` 通过 + 现有 `DatabaseMigratorGuardTest`/`DatabaseMigratorJvmTest` 全绿。

**改动量**：2 行，极低风险

---

### T4.9 机制项：把 `UnusedResources` 从 baseline 豁免中摘出 🔵

**问题等级**：P1（治本）— 不修这条，T4.5 做完三个月后同样问题必然复发

**根因**：四个**手工维护的文字面**（`strings.xml` / `ChangelogData.kt` / `README.md` / `assets/preset_all.json`）与代码之间**零约束**。任何功能删改都不会触发它们的告警。

**关键事实**：`app/lint-baseline.xml` 的问题清单中**已包含 `UnusedResources`**（与 `HardcodedText`、`DrawAllocation`、`UselessParent` 等共 38 类）。即 T3.1 生成 baseline 时 lint **已经知道**那 23 条字符串未使用，随后被永久豁免。**门禁建成了，但把这一项的告警吞进了 baseline。**

**修复方案（采保守版）**：
1. **不跑** `updateLintBaseline`（那会把 T3.1 之后新积累的存量告警一并豁免）。改为**手工从 `lint-baseline.xml` 中删除 `UnusedResources` 那一节**，使其单独以告警形式生效。
2. `app/build.gradle.kts:109-113` 的 `lint { }` 中补 `error += "UnusedResources"`（或 `warningsAsErrors` 仅对该项收敛），确保 CI `lintDebug` 真能拦。
3. **顺带修一处构建脚本僵尸注释**：`app/build.gradle.kts:123` `// P14 Web 微卡二维码生成（纯 JVM 核心，无额外传递依赖）` 现错挂在 `:124` `testImplementation(libs.junit)` 上方。实测 `zxing|QRCode|BitMatrix` 在全部 Kotlin 中**零命中** → 二维码依赖已随 `InteractiveWebCardExporter` 删除，**注释留下并误导读者以为 junit 是二维码库**。删除该注释。

**⚠️ 涉及构建系统改动，执行前需用户明确同意。**

**验证方式**：~~先跑 `./gradlew lintDebug` 取得全量清单并与 B 组交叉比对~~ → **✅ 已于 2026-09-15 完成**，结果见 T4.5-E：lint 确认了人工 23 条的全部（**零假阳性**），并额外暴露 **89 项人工漏网**（含 12 个孤儿布局/drawable/xml 文件）。本任务的**数据采集部分已闭环**，剩余仅余门禁配置。

**本任务尚余两步（待 §10 决策 #11 授权）**：
1. 从 `lint-baseline.xml` 中删除那 112 条 `UnusedResources` 豁免条目（使该项恢复告警）；
2. 清完 T4.5-B+E 后再跑 `lintDebug` 确认零告警，并把 `UnusedResources` 固化进 §9 DoD。

**⚠️ 方法建议**：删豁免时**不要跑 `updateLintBaseline`**（会把 T3.1 之后新积累的存量告警一并豁免）。本轮采集用的是更安全的做法：**不动 baseline 内容**，而是 `Move-Item` 临时移出→跑 lint→`finally` 移回，并用 **SHA256 前后比对**证明逐字节还原（实测 `HASH_MATCH=True`）。建议沿用此法。

**收益**：做完这一条，B 组 23 条僵尸字符串**以后根本不需要人工排查**，CI 直接报。另 `HardcodedText` 也值得看一眼——主页那个「📖 3D 沉浸翻阅」是硬编码字面量，正因如此才躲过了所有静态检查。

---

### T4.10 提交规约：功能删除六面同步清单 🔵

追加至 §9「禁止事项」之后，作为**正向规约**（成本 6 行）：

> **删除任何功能时，必须同步清理以下 6 个面**，否则视为未完成：
> 1. 类文件与 Manifest 注册；2. `strings.xml`（含 `values-night`）与布局内硬编码文案；3. `model/ChangelogData.kt` 条目（需标「已移除」而非直接删，保留历史真实性）；4. `README.md` 功能列表与架构图节点；5. `assets/preset_all.json` 与 `BookDatabaseHelper` 播种数据；6. `app/build.gradle.kts` 依赖行及其上方注释。

**依据**：本轮 40 处僵尸正好大致均匀地落在这 6 个面上，而**硬引用（import/调用/Manifest/布局悬空）已清理干净**——说明删除动作本身做得好，缺的只是「同步清理清单」这一纸约定。

---

### T4.11 明确暂不做（触发条件制）

沿用 T3.5 的写法：**只记条件，不记任务**。以下三项已知但该做，**不列入 T4 执行范围**：

| 项 | 实测规模 | 重启条件 |
|:---|:---|:---|
| 抽 `object ImageExporter` 统一导出/存相册/分享 | `Bitmap.createBitmap(` 散在 **17 个文件**、`getUriForFile` 在 **11 个**、MediaStore 存图 **6 个** | **T4.1~T4.4 修完后紧迫性大幅下降**（四个 P1 本质是同一条缺陷的不同症状）。当出现**第 4 份**分叉实现时应重启，而非继续加点 |
| 薄 `BookRepository` | UI 层 `getInstance(` **65 处** | 当需要替换存储（Room）或需对数据层做单测时重启。目前 `data` 包无循环依赖、无 Context 泄漏，分层缺失只付「维护成本」，未付稳定性代价 |
| 拆 `PresetSeeder` / `StatsQueries` | `BookDatabaseHelper` **4,047 行**；13 个播种方法签名全为 `fun x(db: SQLiteDatabase)` 且**不依赖实例字段**（8 个统计方法同理） | **拆分条件极低成本高**（可纯机械搬移减 1000+ 行）。仅在下一次必须改播种逻辑时顺手做，不单独立项 |

---

### T4.12 桌面小组件名称未接线 🟡（T4.5-E 副产品，真实缺陷）

**问题等级**：P2 — 用户可见的粗糙观感，与功能无关，**2 行可修**

**证据**：
- `res/values/strings.xml:277` `widget_daily_quote_name` = 阅痕 · 每日金句书签
- `:281` `widget_currently_reading_name` = 阅痕 · 在读书目卡片
- 两个名称的全工程引用数 = **0**（`@string/widget_*_name` 与 `R.string.widget_*_name` 均零命中）
- `AndroidManifest.xml:145`（`DailyQuoteWidgetProvider`）与 `:158`（`CurrentlyReadingWidgetProvider`）两个 `<receiver>` **均无 `android:label`**；全 Manifest 仅 `:15` 应用级 `android:label="@string/app_name"` 一处

**净效果**：长按桌面 → 小组件选择器时，这两个组件显示的是**自动推导名**而非已写好的中文名。

**修复方案**：给两个 `<receiver>` 各补一行 `android:label="@string/widget_…_name"`。建议**先于 T4.5-B 的批量删除执行**，否则名称资源会被当作未使用而删掉，缺陷反而永久固化。

**验证方式**：`assembleDebug` → 安装 → 长按桌面图标→ 小组件→ 确认列表里显示「阅痕 · 每日金句书签」与「阅痕 · 在读书目卡片」。

**改动量**：2 行，极低风险。**这是本轮唯一「新增收益型」修复，建议排在 T4 第一批做。**

---

## 8. 执行顺序建议

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
  ↓
T4.1 长卷尺寸封顶 → T4.2 recycle → T4.4 补守卫   ← 三项同为即时 OOM/崩溃，改动极小
T4.3 备份导出后台化                                ← ANR
T4.5-A 用户可见僵尸文案（10 行）                  ← 用户可直接感知，零功能风险
T4.12 小组件名称接线（2 行）                      ← 唯一新增收益型修复；**必须先于 T4.5-B**，否则名称资源会被连带删除
  ↓
T4.5-B+C 删 23 条僵尸 string 与 4 处死代码（建议与 T4.5-A 分开 commit）
T4.6 数据访问主线程残余（3 子项）
T4.8 DATABASE_VERSION 双真值（2 行）
  ↓
T4.9 lint UnusedResources 摘出 baseline（需确认）→ 再清 T4.5-B，顺序不可颠倒
T4.10 提交规约六面同步（6 行）
T4.5-D 文档层・T4.7 预览重绘与 PDF（中改动，可后排）
```

**为什么 T0.1 排第一**：改动仅 2 行、可独立验证，但性质是**正在静默破坏用户数据且不可逆**，比性能问题严重一个量级。

**T4 内部为何是这个顺序**：
1. **T4.1 → T4.2 → T4.4 必须连做**：它们叠加才构成即时 OOM/崩溃，单修任何一个都不完整。
2. **T4.5-A 先于其余全部僵尸清理**：那 5 处是**用户正在看到的错误信息**（含一个点了没反应的按钮），且只需改文案；而 B/C 组是纯内部卫生，用户无感。
3. **T4.9 必须在 T4.5-B 之前**：先解除豁免才拿得到 lint 的**全量未使用资源清单**，用它交叉校对我人工统计的 23 条（预期 lint 命中集 ⊇ 23，能暴露本轮漏网项），再统一清。顺序颠倒则 B 组只能靠人工，且无法防复发。
   → **已验证**：实际执行后 lint 命中集确实 ⊇ 23，且额外暴露 89 项（含 12 个孤儿文件与 2 个应接线而非删的名称资源）。见 T4.5-E。
4. **T4.12 必须先于 T4.5-B**：它两个名称资源在 lint 眼里就是「未使用」，先批量删除会把缺陷永久固化。
5. **T4.11 不占顺序**：只记条件，不执行。

---

## 9. 验证与提交规约

### 每项任务的完成定义（DoD）
1. 代码修改完成，且**仅修改本任务相关文件**（遵循最小修改原则）。
2. 执行与本任务相关的最小验证：
   - **T0.1 / T0.4 / T0.7**：迁移器测试（构造受影响数据 → 跑迁移 → 断言用户数据未被改动）+ `assembleDebug`
   - **T0.3**：StrictMode 冷启动检查 + 埋点计时
   - **T1.x**：`assembleRelease` 通过 + 签名校验
   - **T2.x**：Profiler 对比 / `EXPLAIN QUERY PLAN` / 布局层级对比
   - **T4.1 / T4.2**：构造≥300 条藏书库走一次长卷导出，确认不崩、`bitmap.byteCount` 在预期量级、导出后堆内存回落；**必须在模拟器上实跑**，不得只靠静态推导结案
   - **T4.3 / T4.4 / T4.6**：大库导出/导入无 ANR；导出期间退出页面不崩溃
   - **T4.5**：`assembleDebug` 通过 + **逐一手验五处**（主页 Hero / 社区展览详情 / 预设作品详情页 / 版本纪要 / 小部件选择器）；B 组删除后必须确认 `values-night` 无同名残留
   - **T4.8 / T4.9**：`assembleDebug` + 现有 `DatabaseMigratorGuardTest`/`DatabaseMigratorJvmTest` 全绿 + `lintDebug` 通过
   - ⚠️ **T4 的性能数字均为静态分析得出，未经 Profiler/systrace 实测**；带 🔴 的项不得以「编译通过」作为完成证明
3. 全量 `./gradlew testDebugUnitTest` 通过（**首次运行会往 C 盘写 Gradle 发行包约 150 MB 及 `build/` 产物，需用户事先授权**）。
4. 中文 commit（格式 `类型：修改内容`，如 `修复：解决迁移器丢失source_type守卫导致用户评分被篡改`）。
5. Push 到当前远程分支。

### 禁止事项
- 禁止为了「顺手」重构无关代码、格式化整个项目、升级无关依赖。
- 禁止使用 `git reset --hard` / `git clean -fd` / `git push --force` / `git rebase`。
- 禁止在没有验证的情况下声称「已完全解决」。
- 任何涉及**删除、覆盖、历史重写**的操作，执行前必须获得用户明确允许。

### 正向规约：功能删除六面同步清单（T4.10）

> 本规约为 T4.5 排查的直接产出。**删除任何功能时，必须同步清理以下 6 个面**，否则视为未完成：
>
> | # | 同步面 | 本轮对应的漏清实例 |
> |:--:|:---|:---|
> | 1 | 类文件与 `AndroidManifest` 注册 | ✅ 这一面**已清理干净**（无 import/调用/组件残留） |
> | 2 | `strings.xml`（含 `values-night`）与布局内**硬编码文案** | ❌ 23 条零引用 string + `activity_exhibition_detail.xml:43` 死按钮 + `HubFragment:510` 硬编码字面量 |
> | 3 | `model/ChangelogData.kt` 条目 | ❌ `:107/:109/:147` 仍向用户介绍已删能力。**注：应标「已于 vX 移除」而非直接删，保留历史真实性** |
> | 4 | `README.md` 功能列表与架构图节点 | ❌ `:119/:158` 仍写「Web 微卡」 |
> | 5 | `assets/preset_all.json` 与 `BookDatabaseHelper` 播种数据 | ❌ 约 200 条 `shelfLocation` 仍写「展厅第N层」 |
> | 6 | `app/build.gradle.kts` 依赖行**及其上方注释** | ❌ `:123` 二维码注释已孤儿化，错挂在 `:124` junit 上 |
>
> **为何有效**：40 处僵尸大致均匀落在这 6 个面上，而硬引用完全干净——说明**删除动作本身做得好，缺的只是这一纸清单**。属流程问题而非能力问题，故可用规约收敛。

---

## 10. 待用户决策清单

> **2026-09-13 落实结果**：#3 已解决（用户授权「计划内完成」后 Gradle 测试/构建常态化执行，全量单测 69 个全绿）；#4 已解决（`cover_server/` 经用户确认整目录删除）；#5 已解决（README 失效链接与 LICENSE 已在 `148cca8` 处理，本轮核验通过）；#6 已解决（小程序端已有独立仓库，保持独立版本控制，不入主库）；#7 已解决（核验发现 `targetSdk` 实际已为 36，且 Android 17 (API 37) 已于 2026-06-16 发布稳定版，原「beta 轨道」顾虑失效）；#2 维持 T0 阶段方案（敏感键值留空 + example 模板，`gradle.properties` 模板文件继续入库）。**#1 仍待决策**：已被 v15 随机改写评分的存量数据是否做补偿。

| # | 决策项 | 关联任务 | 影响 | 状态 |
|:--:|:--|:--|:--|:--:|
| 1 | 是否授权修改迁移逻辑？是否需要对已被 v15 改写的评分做补偿？ | T0.1 | 历史数据 | 迁移逻辑已修，**补偿待决策** |
| 2 | 是否授权把 `gradle.properties` 移出版本控制？ | T0.5 | 构建系统 + CI | ✅ 以敏感键留空+模板方案替代 |
| 3 | 是否授权运行 `./gradlew testDebugUnitTest`（C 盘约 150 MB）？ | 全部 | 验证能力 | ✅ 已解决 |
| 4 | `cover_server/`(36.3MB)、`2k图片.jpeg`(1.6MB) 如何处置？ | T3.3 | 仓库体积 | ✅ 已删除 |
| 5 | `docs/` 目录是恢复还是摘除 README 链接？是否补 `LICENSE` 文件？ | T3.3 | 文档完整性 | ✅ 已解决 |
| 6 | `mp-readtrace/` 小程序端是否纳入版本控制？ | T3.3 | 代码备份 | ✅ 已有独立仓库，维持现状 |
| 7 | `targetSdk = 37` 是否回退到稳定版 36？ | 未列入 T1 | 发布策略 | ✅ 已是 36，无需回退 |
| 8 | 「展厅第N层」是否**迁移存量数据**？仅改新增文案需一次 `UPDATE` 才能影响已入库记录，而该字段用户可手改，UPDATE 会覆盖 | T4.5-A #3 | 用户数据 + 预设 CSV/JSON | ⚠️ **待决策** |
| 9 | `ChangelogData` 历史条目如何处理？（它们是**当时真实上过线**的记录，直接删 = 抹历史；保留 = 向用户介绍不存在的能力） | T4.5-A #4 | 版本纪要可信度 | ⚠️ **待决策**（本计划建议：保留 + 标「已移除」） |
| 10 | `MainActivity.preloadRemainingTabs()` 是**接线**还是**删除**？（零调用，但它是 T0.3 播种后台化后配套的冷启动预热，接线可能仍有收益） | T4.5-C | 冷启动性能 | ⚠️ **待决策** |
| 11 | 是否授权改动构建系统（`build.gradle.kts` 的 `lint {}` 块 + 手工改 `lint-baseline.xml`）？ | T4.9 | CI 门禁行为 | ⚠️ **待决策**（建议：**不跑** `updateLintBaseline`，只手工删 `UnusedResources` 一节） |

---

## 11. 附：本次审查的核验记录

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

### 第三轮审查（2026-09-15）核验记录

| 审查结论 | 核验方式与结果 |
|:---|:---|
| 3D 私人展厅 / 2.5D 视差展厅 / 3D 翻书阅读器 均已不存在 | ✅ `Test-Path` 逐个确认文件不存在；`git log` 定位到 `0543c4c`、`1339482`、`4d31a77`、`910a84a`、`b00e19d`、`da6855b` |
| 23 条 string 零引用 | ✅ **属实**。脚本逐个统计 `R.string.x` 与 `@string/x` 两种形式，命中数均为 0 |
| `detail3DExploreBtn`（🏛️ 3D 漫游）是死按钮 | ❌ **假阳性，已推翻**。本条原用的是 `readtrace\*.kt` 非递归通配，漏了嵌套目录。实现在 `community/ui/ExhibitionDetailActivity.kt:75-77`，跳向存活的 `CommunityGalleryActivity` —— **控件正常，文案成立**。差点被删，由 `compileDebugKotlin` 报 `Unresolved reference` 拦下。已全量改用递归检索重测同类结论 |
| 主页「📖 3D 沉浸翻阅」名实不符 | ✅ 属实（`HubFragment:510` 设文案，`:329` 实跳 `BookDetailActivity`） |
| 「展厅第N层」会显示给用户 | ✅ 属实（`BookDetailActivity:258` → `detailShelfLocation`） |
| `action_3d_read` 永不可见 | ✅ 属实（`BookDetailActivity:1399-1486` 五分支：BOOK→`GONE`，其余四个均运行时改 `.text`） |
| **社区 3D 展厅仍存活** | ✅ `CommunityGalleryActivity.kt` 与 `gallery3d/Gallery3DRenderer.kt` 均存在 → `fragment_profile.xml:259` 的「3D 虚拟展厅」是**正常文案，不得清理** |
| 长卷无尺寸封顶 | ✅ 属实，且**同项目内即有正确范例**（年鉴 `:319` `maxDimension=4096f`） |
| 备份导出在主线程 | ✅ 属实（`exportDataToFile` 由 `:27/:33/:39` 三个 SAF 回调直调） |
| `DATABASE_VERSION` 双真值 | ✅ 属实（17 vs 15）；且 `DatabaseSchema.DATABASE_VERSION` **引用数 0** → 死代码 |
| 裸 `Thread` 45 → 71 处 | ✅ 属实（实测 `Thread \{` 计数 71） |
| `getInstance` UI 层 51 处 | ⚠️ **子代理报 51，实测为 65**，已按实测值修正；`ViewModel`/`LiveData`/`StateFlow`/`Room`/`@Dao` 均为 **0** |
| `UnusedResources` 已在 baseline 中被豁免 | ✅ 属实（`lint-baseline.xml` 含 38 类 issue，其中包含此项） |
| T4 各项性能数字的实际影响幅度 | ⚠️ **未实测**（本轮全程只读，未跑 Gradle 构建、未上 Profiler、未连设备）——所有 🔴 项的严重程度基于代码结构与同项目对照推断 |

### T4.9 数据采集轮（2026-09-15）核验记录

| 结论 | 核验方式与结果 |
|:---|:---|
| 人工 23 条全部为真未使用 | ✅ **lint 全量报告 100% 确认，零假阳性**（「在我清单中但未被 lint 报出」= 空集） |
| lint 可用作本项目僵尸资源的**单一可信源** | ✅ 属实。前提已实测：全工程 `getIdentifier`/`resources.get*` **零命中** → 不存在反射访问导致的误报 |
| 上轮「无失效 `@layout/`/`@drawable/` 引用」结论 | ❌ **被推翻**。存在 3 个孤儿布局 + 9 个孤儿 drawable + 2 个孤儿 xml 规则。已回改 T4.0 正面确认清单并在该处留修正痕迹 |
| 孤儿布局确实无人加载 | ✅ `layout_dialog_clipboard_sniffer` / `item_reading_session` / `activity_anime_timeline_scroll` 在全部 `.kt`+`.xml` 的**内容**中引用数为 0 |
| 豁免数与实际告警数的关系 | ✅ **完全相等（112 = 112）** → 印证这批告警自 T3.1 生成 baseline 起就被整批吞掉，未发生漏抓 |
| 用户数据与配置安全 | ✅ baseline **未被修改**（移出/还原 + SHA256 比对）；`build.gradle.kts` 未动；本轮无构建产物写入 C 盘（均在各模块 `build/` 下，属既有目录） |

---

**文档结束**
