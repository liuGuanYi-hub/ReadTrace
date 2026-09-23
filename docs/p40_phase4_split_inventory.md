# P40 Phase 4 · BookDatabaseHelper 拆分清单（阶段 1 摸底产物）

> 生成时间：2026-09-23，基线 commit `05011b8`。
> 目标（主计划 §54 Phase 4）：`BookDatabaseHelper` 瘦身至 **< 1500 行**，且现有读写/导出/备份/统计全流程零破坏。
> 现状：**3998 行**；102 个 `fun` + 12 个扩展映射函数（`Cursor.toX` / `X.toContentValues`）+ 4 个 `override`（onCreate/onUpgrade/onOpen/onDowngrade）。
> 复算方式：按 `^    (private|internal)? fun 名字(` 提取方法区间，外部调用点 = 全仓（main+test）`名字(` 的 grep 计数（不含 helper 自身）。区间为相邻方法间的行距，含其内部的扩展映射函数。

## 一、分组总表

| 组 | 内容 | 方法数 | 约行数 | 外部调用点 | 目标文件 |
|:---:|:---|:---:|:---:|:---:|:---|
| A | 预置播种 / 富内容 / 封面修复 / wipe | 20 | 1989 | 2 | `data/PresetSeedManager.kt`（新建，§54 点名） |
| B | books CRUD、查询、导入、音频轨 | 27 | ~694（含 toBook/Book.toContentValues） | 133 | `data/dao/BookDao.kt` |
| C | notes、回收站、备份读写 | 16 | ~505（含 toNote/Note.toContentValues） | 30 | `data/dao/NoteDao.kt` |
| D | 阅读时长、人物、大纲、地点 | 14 | ~266（含 4 个 mapper） | 33 | `data/dao/BookDao.kt`（book 附属记录） |
| E | mindprint、年度人格、streak、金句 | 9 | ~367（含 toBookMindprint 等） | 40 | `data/dao/MindprintDao.kt` |
| F | 收藏 favorites | 8 | ~115 | 8 | 暂留 helper（独立域，不阻塞 <1500 验收） |
| — | 留 helper：生命周期 4 个 override、books 内存缓存、stats 委托（已走 `StatsQueries`）、tags、companion | — | ~250 | — | `BookDatabaseHelper.kt` |

拆完 helper 预计 **300~450 行**，远低于 1500 验收线；余量留给 F 组与缓存逻辑不迁。

## 二、迁移约定（阶段 2 起照此执行）

1. **Dao 形态对齐 `StatsQueries`**：`object BookDao { fun getBooks(db: SQLiteDatabase, status: BookStatus?): List<Book> }`——纯函数、显式传 db，不持有状态。helper 内保留**同签名委托转发**（`fun getBooks(status) = BookDao.getBooks(readableDatabase, status)`），**65+ 处调用点零改动**。
2. **内存缓存不迁**：`getCachedBooks` / `invalidateBookCache` 的 books 缓存是跨 Dao 的进程级状态，留在 helper；BookDao 只提供纯查询，helper 的缓存方法内部改调 BookDao。
3. **扩展映射函数随组迁**：`Cursor.toBook`/`Book.toContentValues` → BookDao 文件内 private；toNote 系 → NoteDao；toBookMindprint 系 → MindprintDao；共享的 `getNullableString/getNullableDouble/putNullable/parseTags` 提为 `data/dao/CursorExt.kt` internal 扩展。
4. **A 组抽出时不得改变调用线程**：播种目前由 `onOpen` 触发、经 `ReadTraceApplication` 的后台线程预开库完成（见记忆「SQLiteOpenHelper.onOpen 内重播种加多实例开库导致高频 ANR」）。`PresetSeedManager` 只搬代码，`runPresetSeedsOnce` 的调用点与线程模型一字不改。
5. **事务语义原样保留**：`importFullBackup` / `importParsedRecords` / `insertBooksBatch` 内部的 `db.beginTransaction` 结构随迁，不在拆分时"顺手优化"。
6. 每阶段收尾：删掉**已无外部调用者**的转发方法前先 grep 确认归零（沿用 T4.9 门禁思路）；阶段 5 统一清理。

## 三、各组明细（方法 / 行区间 / 外部调用点）

### A · PresetSeedManager（1989 行，调用点仅 2 → 风险最低、收益最大，建议第一个抽）
forceCloseForTesting 44-77(0) · backupDatabaseFile 78-97(0) · patchCorruptedPresetCovers 98-130(0) · runPresetSeedsOnce 131-194(0) · repairMissingNeteaseCovers 195-238(0) · migrateCoversToLanKeys 239-292(0) · **wipeAllUserData 293-321(1)** · assetNameForRemoteUrl 322-351(0) · populatePresetRichContent 352-378(0) · ensureRichContentSeededIfNeeded 379-397(0) · applyRichContentEntries 398-563(0) · **importRichContentJson 564-578(1)** · inferMediaFromFileName 579-589(0) · seedCuratedBookCovers 590-685(0) · autoFillMissingCovers 686-843(0) · seedUserAnimeList 844-993(0) · seedUserMovieList 994-1233(0) · seedUserGameList 1234-1428(0) · seedUserMusicList 1429-1680(0) · populatePresetBookRichData 1681-2032(0)

> 注：wipeAllUserData / importRichContentJson 是**仪器测试直接覆盖的公开面**（`WipeUserDataTest`、`RichContentJsonImportTest`），迁走后 helper 保留委托，仪器测试不改。

### B · BookDao（~694 行，调用点 133 → 委托转发必须同签名）
getCachedBooks 2033(14) · invalidateBookCache 2043(0,私有) · insertBook 2049(15) · insertBooksBatch 2063(2) · findExistingSourceIds 2116(1) · findBookBySource 2142(3) · findQuickLogDuplicate 2154(1) · findBooksByTitleLike 2183(1) · **getBooks 2202(36)** · getBooksForList 2233(7) · **getBook 2266(16)** · **updateBook 2280(14)** · archiveBook 2295(5) · getAudioTracks 2313(4) · insertAudioTrack 2337(1) · updateAudioTrackDuration 2352(1) · deleteAudioTrack 2361(3) · deleteAudioTracksOfBook 2366(0) · restoreBook 2374(4) · getArchivedBooks 2395(1) · hardDeleteBook 2417(1) · getBookAny 2448(0,私有) · importParsedRecords 2466(2) · importBooks 2559(0) · isBookExists 2565(0,私有) · getMemoryBook 2584(1) · getMonthlyFinishedStats 2646(0)

### C · NoteDao（~505 行，调用点 30）
insertNote 2672(5) · **getNotes 2685(9)** · getAllNotesLite 2707(1) · getNote 2724(1) · updateNote 2738(1) · archiveNote 2753(2) · restoreNote 2774(3) · getArchivedNotes 2795(1) · hardDeleteNote 2822(1) · clearAllTrash 2836(1) · getAllWorksWithNotes 2874(0) · getAllFullWorkBackups 2885(2) · queryAllAudioTracksGroupedByBook 2931(0,私有) · **importFullBackup 2965-3118(3，154 行事务块)** · bookIndexKey 3119(0,私有) · findBookId 3122(0,私有)

### D · 并入 BookDao（~266 行，调用点 33）
insertReadingSession 3178(1) · getReadingSessions 3191(2) · getTotalReadingMinutes 3208(0) · getAllReadingSessions 3217(2) · deleteReadingSession 3234(0) · insertCharacter 3249(3) · getCharacters 3264(6) · deleteCharacter 3281(1) · insertOutline 3296(3) · **getOutlines 3310(8)** · deleteOutline 3327(1) · insertLocation 3342(1) · getLocations 3357(4) · deleteLocation 3374(1)

### E · MindprintDao（~367 行，调用点 40）
saveMindprint 3389(4) · **getMindprint 3409(24)** · getAllMindprints 3422(6) · getAnnualMindprintPersona 3443(2) · getTodayTotalReadingMinutes 3502(1) · getConsecutiveReadingDays 3515(0) · getRandomOrNextQuote 3557(2) · getLatestReadingBook 3614(1) · quickRecordReadingSession 3623(0)

### F · 暂留 helper（~115 行，调用点 8）
addFavorite 3818(2) · removeFavorite 3835(2) · isFavorite 3844(2) · updateFavoriteTagline 3861(0) · updateFavoriteRank 3872(0) · getFavorites 3884(1) · getFavoritesByMediaType 3921(1) · getFavoriteCount 3924(0)

### 留 helper / 共享工具
getAllUniqueTags 3142(2) · stats 委托 5 个 3162-3177(各 1~3，已走 StatsQueries) · parseTags 3756(5) · putNullable/getNullableString/getNullableDouble（共享，提 CursorExt） · companion（TAG/实例/缓存字段）

## 四、护栏与每阶段验证

| 层 | 内容 | 何时跑 |
|:---|:---|:---|
| JVM 门禁 | `testDebugUnitTest` 16 个测试文件（CI 跑）；`DatabaseMigratorJvmTest` 覆盖迁移守卫 | 每阶段 |
| 仪器测试 | `WipeUserDataTest`、`RichContentJsonImportTest`、`DowngradeGuardTest`（androidTest，CI 不跑）直接覆盖 A 组公开面与降级守卫 | 阶段 2（A 组抽出后）与阶段 5 各跑一次 |
| 模拟器冒烟 | 主页 / 藏库 / 详情 / 备份导出 / 社区 五路径手测 | 每阶段 |
| 行数复核 | `wc -l BookDatabaseHelper.kt` | 每阶段收尾 |

## 五、阶段 2~5 执行顺序（待逐段授权）

1. **阶段 2**：A 组 → `PresetSeedManager`（1989 行，调用点 2，风险最低收益最大）
2. **阶段 3**：B+D 组 → `BookDao`（含缓存改接）
3. **阶段 4**：C 组 → `NoteDao`；E 组 → `MindprintDao`；共享 mapper → `CursorExt.kt`
4. **阶段 5**：清理零调用转发方法、行数复核 <1500、仪器测试复跑、回填主计划 §54 执行记录

> **✅ 阶段 2 已完成（2026-09-24）**
>
> | 项 | 结果 |
> |:---|:---|
> | 抽出 | `data/PresetSeedManager.kt` 1981 行（object，纯函数 + 显式传 db/context）；helper **3998 → 2050 行** |
> | 接口 | `backupDatabaseFile / runPresetSeedsOnce / wipeAllUserData / importRichContentJson / patchCorruptedPresetCovers / autoFillMissingCovers` 为 internal，helper 保留同签名公开委托；缓存失效以 `onBooksChanged: () -> Unit` 回调注入，**播种调用线程与事务结构零改动** |
> | 验证 | `compileDebugKotlin` 干净；`testDebugUnitTest` 全绿；仪器测试 `WipeUserDataTest / RichContentJsonImportTest / DowngradeGuardTest` 10/10 过；模拟器冷启动重新播种成功（主页 55/78/11/69/11）、藏库/我的/备份页打开、logcat 零崩溃 |
> | 顺手修复（既有缺陷，与拆分无关） | `DowngradeGuardTest` 断言写死 `16`（注释还停在 onDowngrade(99,15)），而 `DATABASE_VERSION` 已是 18——在 HEAD 上同样红。改为引用 `BookDatabaseHelper.DATABASE_VERSION`，门禁恢复有效 |

> **✅ 阶段 3 已完成（2026-09-24）**
>
> | 项 | 结果 |
> |:---|:---|
> | 抽出 | `data/dao/BookDao.kt` 1011 行（object + 顶层 internal 扩展）；helper **2050 → 1176 行** |
> | 范围 | B 组 books CRUD/查询/导入/音频轨 + `bookIndexKey`/`findBookId` + D 组阅读时长/人物/大纲/地点 + 映射函数 `toBook`/`toReadingSession`/`toBookCharacter`/`toBookOutline`/`toBookLocation`/`Book.toContentValues` |
> | 委托 | helper 保留 37 条同签名转发；**缓存失效在委托层补回**（9 条无条件 `.also { invalidateBookCache() }`，`importParsedRecords`/`importBooks` 按返回条数条件失效），与 HEAD 的失效时机等价 |
> | 与清单的偏差 | 共享扩展（`parseTags`/`getNullable*`/`putNullable`）未按清单另建 `CursorExt.kt`，而是作为 dao 包顶层 internal 函数放在 `BookDao.kt` 尾部——同包可见，helper 的 `toNote`/`Note.toContentValues` 直接复用，少一个文件 |
> | 验证 | 编译干净；`testDebugUnitTest` 全绿；仪器测试 10/10；模拟器冒烟：藏库 224 部完整渲染（getBooks/toBook/parseTags 路径）、备份页打开、logcat 零崩溃 |

> **✅ 阶段 4 已完成（2026-09-24）**
>
> | 项 | 结果 |
> |:---|:---|
> | 抽出 | `data/dao/NoteDao.kt`（notes/回收站/整库备份读写 + `toNote`/`Note.toContentValues`）、`data/dao/MindprintDao.kt`（六维心智/年度人格/时长/streak/金句 + `toBookMindprint`）；helper **1176 → 502 行** |
> | 委托 | helper 保留 23 条同签名转发；`clearAllTrash` 的 books 缓存失效在委托层补回（与 HEAD 时机一致）；`importFullBackup` 保持原语义**不**失效缓存 |
> | 跨 Dao 调用 | `NoteDao`/`MindprintDao` 内部对 books 的查询与写入改走 `BookDao.*`，年度人格对心智档案的批量取数改走 `MindprintDao.getAllMindprints(db)` |
> | 过程中的险情（已闭环） | 删除区间一度吞掉 favorites 的 `CuratorFavoriteItem` 数据类（它夹在 `Note.toContentValues` 与 `addFavorite` 之间），编译期发现后从备份 `/tmp/helper_backup_stage4.kt` 取回原位 |
> | 验证 | 编译干净；`testDebugUnitTest` 全绿；仪器测试 10/10；模拟器冒烟：我的页雷达与年度人格渲染一致、番剧详情打开、logcat 零崩溃 |
