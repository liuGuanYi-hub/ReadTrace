# DATABASE_VERSION 16 → 17 升级影响评估

> 评估时间：2026-09-14 14:30
> 目的：触发一次重播种，补齐预置 mindprint（当前 `book_mindprints` 表为 0 行）
> 评估方式：静态代码分析 + 线上库快照（`.tmp_emu_check/rt.db`，207 部作品）

---

## 一、结论摘要

**结论：升级安全，可以执行。所有风险项均已排除或降级为"可接受"。**

| 评估项 | 结果 |
|:---|:---|
| 表结构变更 | **无**（`onUpgrade` 最高分支为 `oldVersion < 16`，16→17 全部跳过） |
| 影响作品数量 | **仅 22 部预置作品**（movie 11 + music 11），其余 185 部零接触 |
| 可能被覆盖的用户数据 | **无**（R1 经实测排除：库中 0 部作品带馆藏信息） |
| 评分数据 | **完全安全**（两个 rating 相关分支均不命中） |
| 自动备份 | 无（`newVersion - oldVersion = 1` 不触发），建议手动快照 |
| 可回滚 | **可**（文件级恢复数据库快照） |
| 事务保护 | **有**（整个重播种包在单事务内，异常即回滚且不记版本号） |

---

## 二、版本号机制说明（重要，与直觉不同）

项目有**两个独立的版本号**，职责完全不同：

| 常量 | 位置 | 当前值 | 作用 |
|:---|:---|:---|:---|
| `DATABASE_VERSION` | `BookDatabaseHelper:4013` | **16** | 管**表结构迁移**（`onUpgrade` / `onCreate`） |
| `KEY_SEED_VERSION` | `DatabaseSchema:12` | 持久化值 **16** | 管**数据播种**（`runPresetSeedsOnce`） |

**但代码把两者绑死了**：`putInt(KEY_SEED_VERSION, DATABASE_VERSION)`（第 185 行）。

因此想触发重播种，**必须改 `DATABASE_VERSION`**——没有独立提升 seed 版本的入口。这正是本次要升到 17 的原因。

> 附带发现：`DatabaseSchema.DATABASE_VERSION = 15` 与 `BookDatabaseHelper.DATABASE_VERSION = 16` **不一致**。
> 但 `BookDatabaseHelper` 引用的是自己的 16，`DatabaseSchema` 的那个 15 实际未被使用，属于**死代码**，本次不动它（最小修改原则）。

---

## 三、迁移路径分析

### 3.1 onUpgrade 分支命中情况

`DatabaseMigrator.onUpgrade` 共有 14 个分支，最高到：

```
156:  if (oldVersion < 15) { ... }
167:  if (oldVersion < 16) { ... }
```

**升级时 `oldVersion = 16`，两个条件均为 false → 所有分支跳过。**

结论：**16 → 17 的 `onUpgrade` 是空操作，SQL 结构零变更。**

### 3.2 备份触发条件

```kotlin
// BookDatabaseHelper:62
if (newVersion - oldVersion > 1) backupDatabaseFile(oldVersion, database)
```

`17 - 16 = 1`，**不大于 1** → **本次不会自动生成 bak_v16 备份文件**。

> ⚠️ 这是唯一的保护缺口。但考虑到 `onUpgrade` 为空操作、且重播种包在事务内，风险仍可控。
> 若需要额外保险，可手动执行一次库快照（见第五节）。

### 3.3 重播种链路执行顺序

`runPresetSeedsOnce`（第 138-183 行，整体单事务）：

```
① populatePresetBookRichData(db)   ← 5 部预置经典书
② seedUserAnimeList(db)            ← 71 部番剧
③ seedUserMovieList(db)            ← 11 部电影（带 mindprint）
④ seedUserGameList(db)             ← 69 款游戏
⑤ seedUserMusicList(db)            ← 11 首音乐（带 mindprint）
⑥ populatePresetRichContent(db)    ← 富内容
⑦ seedCuratedBookCovers(db)        ← 策展封面
⑧ migrateCoversToLanKeys(db)       ← 封面键迁移
⑨ 条件迁移（仅 previousSeedVersion < 15 时执行评分迁移）
⑩ 条件初始化（仅 previousSeedVersion == 0 时执行评分初始化）
```

**本次 `previousSeedVersion = 16`**，因此：

- ⑨ `previousSeedVersion < 15` → **false，跳过**（不会重写 music/movie/game 的评分）
- ⑩ `previousSeedVersion == 0` → **false，跳过**（不会初始化/覆盖任何评分）

**这是最关键的安全保证：所有涉及 rating 的批量 UPDATE 都不会执行。**

---

## 四、逐函数写库影响盘点

| 函数 | insert | update | execSQL | 触及作品 | 用户数据风险 |
|:---|:---:|:---:|:---:|:---|:---|
| `populatePresetBookRichData` | 7 | 1 | 0 | **5 部经典书** | **R1：覆盖馆藏四字段** |
| `seedUserAnimeList` | 2 | 0 | 0 | 71 部番剧 | 按标题匹配，已存在则跳过插入 |
| `seedUserMovieList` | 2 | 1 | 2 | 11 部电影 | 封面仅在为空时补（有守卫） |
| `seedUserGameList` | 2 | 1 | 2 | 69 款游戏 | 同上 |
| `seedUserMusicList` | 2 | 1 | 2 | 11 首音乐 | 同上 |
| `seedCuratedBookCovers` | 1 | 1 | 0 | 策展书 | 封面类 |
| `migrateCoversToLanKeys` | 0 | 0 | 1 | 全局封面列 | 封面类 |
| `populatePresetRichContent` | 0 | 0 | 0 | — | 无 |
| `ensureRichContentSeededIfNeeded` | 0 | 0 | 0 | — | 无 |

**合计受影响：22 部预置作品 + 5 部经典书。**

---

## 五、风险清单

### R1（已排除）：5 部预置经典书的馆藏四字段会被覆盖

```kotlin
// BookDatabaseHelper:1691-1698
if (bookId != null) {
    val cv = ContentValues().apply {
        put(COLUMN_BUY_CHANNEL, buyChannel)      // ← 覆盖
        put(COLUMN_SHELF_LOCATION, shelfLocation) // ← 覆盖
        put(COLUMN_BINDING_TYPE, bindingType)     // ← 覆盖
        put(COLUMN_BUY_PRICE, buyPrice)           // ← 覆盖
    }
    db.update(TABLE_BOOKS, cv, "$COLUMN_ID = ?", arrayOf(bookId.toString()))
}
```

- **触发条件**：标题 `LIKE '%关键词%'` 匹配到已有作品（`is_deleted = 0`）
- **影响面**：仅这 5 部书，且仅这 4 个字段
- **✅ 实测排除**：查询线上库快照
  ```sql
  SELECT id,title,media_type,buy_channel,shelf_location,binding_type,buy_price
  FROM books WHERE is_deleted=0
    AND ((buy_channel IS NOT NULL AND buy_channel!='')
      OR (shelf_location IS NOT NULL AND shelf_location!='')
      OR (binding_type IS NOT NULL AND binding_type!=''))
  ```
  **返回 0 部** —— 库中没有任何作品带馆藏信息，覆盖操作无数据损失。
- **涉及书籍**：5 部预置经典（含番剧 1 部），其馆藏信息为出厂硬编码的虚构数据
  （如「上海独立书店 · 季风书园」「书架第1层 · 治愈精神馆」），非用户手填

### R2（低）：评分不会被改动

如 3.3 节所述，两个评分相关分支均不命中。**评分安全。**

### R3（低）：无自动备份

`newVersion - oldVersion = 1`，不触发 `backupDatabaseFile`。建议手动快照一次。

### R4（低）：合并而非替换

若导入过 `912_filled.json`，库中作品数可能≠218（去重键为 `标题+作者`）。重播种不解决此问题，但也不加剧。

---

## 六、执行前建议

1. **手动快照数据库**（补上 R3 缺口）：
   ```bash
   adb exec-out "run-as com.example.readtrace cat /data/data/com.example.readtrace/databases/readtrace.db" \
     > backup_before_v17.db
   ```

2. **确认 R1 的 5 部书**是否含你手填的馆藏信息——这是唯一需要你做判断的地方

3. **执行后验证**：
   ```sql
   SELECT COUNT(*) FROM book_mindprints;              -- 预期 ≥ 22
   SELECT media_type, COUNT(*) FROM books
     WHERE is_deleted = 0 GROUP BY media_type;        -- 预期 music = 11
   ```

---

## 七、待确认事项

- [x] ~~R1 涉及的 5 部预置书，你是否手动填过馆藏四字段？~~ → **已实测排除，库中 0 部带馆藏信息**
- [ ] 是否需要在升级前先导出一次库快照？（建议需要，用于回滚保险）
- [ ] 确认执行范围：本次一并完成三件事
  1. `DATABASE_VERSION` 16 → 17（触发重播种，补齐预置 mindprint）
  2. 批量导入弹窗删除 4 个 CSV 分类入口，仅保留 3 项
  3. 「一键全量合入」数据源从 4 个 preset CSV（205 部）改为 `912_filled.json`（218 部，含完整 mindprint）
