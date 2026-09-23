package com.example.readtrace.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.readtrace.data.migrator.DatabaseMigrator
import com.example.readtrace.model.ArchivedNoteItem
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookCharacter
import com.example.readtrace.model.BookLocation
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.BookOutline
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.model.MonthlyReadingStat
import com.example.readtrace.model.Note
import com.example.readtrace.model.NoteType
import com.example.readtrace.model.ReadingSession
import com.example.readtrace.util.CoverImageHelper
import org.json.JSONArray
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class BookDatabaseHelper private constructor(val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    /**
     * 单例生命周期保护：
     * 拦截各 Activity/Widget 的误调用 close()，防止全局单例底层的 SQLite 连接池被意外关闭。
     * 如需真正关闭，应随 Application 进程终止。
     */
    override fun close() {
        android.util.Log.d("BookDatabaseHelper", "Single instance close() intercepted to protect connection pool.")
    }

    /**
     * 仅供极特殊场景（如清除数据或测试重置）调用的强制关闭
     */
    fun forceCloseForTesting() {
        super.close()
        instance = null
    }

    override fun onCreate(database: SQLiteDatabase) {
        DatabaseMigrator.onCreate(database)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // T2.4：启用 WAL——写事务不再阻塞并发读，
        // importFullBackup 等大事务期间主线程读不被排队或抛 SQLiteDatabaseLockedException
        db.enableWriteAheadLogging()
    }

    /** 清除全部用户数据（作品/笔记/会话等），保留预置播种；「回收站-彻底清空」与仪器测试使用 */
    fun wipeAllUserData(): Pair<Int, Int> =
        PresetSeedManager.wipeAllUserData(writableDatabase, ::invalidateBookCache)

    /** 手机端手动导入富内容 JSON；返回匹配条数，JSON 非法时抛异常由调用方提示 */
    fun importRichContentJson(jsonText: String, sourceFileName: String? = null): Int =
        PresetSeedManager.importRichContentJson(writableDatabase, jsonText, sourceFileName, ::invalidateBookCache)

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 跨版本升级（如 v12 → v16）先做文件快照备份，迁移异常可从 bak_v{old} 手动恢复
        if (newVersion - oldVersion > 1) PresetSeedManager.backupDatabaseFile(context, oldVersion, database)
        DatabaseMigrator.onUpgrade(database, oldVersion, newVersion)
    }

    /**
     * 降级安装兜底（曾安装更高数据库版本的测试包后回退正式包时触发）。
     * 框架默认直接抛 SQLiteDowngradeFailedException 崩溃；此处改为：
     * 先快照备份原库文件，然后接受版本号继续运行——历次迁移均为加列/加表，
     * 高版本结构对低版本代码向后兼容，用户数据零丢失；
     * 一旦低版本代码真与高版本结构不兼容（如未来删除列），可从 bak_v{old} 文件恢复。
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        PresetSeedManager.backupDatabaseFile(context, oldVersion, db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        PresetSeedManager.patchCorruptedPresetCovers(db)
        PresetSeedManager.runPresetSeedsOnce(context, db, ::invalidateBookCache)
        // 保持每次开库执行：兜底用户导入的无封面作品，无缺失时仅一次轻量查询。
        // 重播种路径已整体包事务并默认由 Application 后台预热线程触发，不应在主线程首次开库。
        PresetSeedManager.autoFillMissingCovers(db)
    }

    // ---------------------------------------------------------------- books DAO 委托
    // P40 Phase 4 阶段 3：实现迁至 BookDao，此处保持同签名转发，调用点零改动；
    // books 内存缓存的失效仍由本层负责（Dao 为纯函数）。

    fun insertBook(book: Book): Long =
        BookDao.insertBook(writableDatabase, book).also { invalidateBookCache() }

    fun insertBooksBatch(books: List<Book>): Int =
        BookDao.insertBooksBatch(writableDatabase, books).also { invalidateBookCache() }

    fun findExistingSourceIds(sourceType: String, sourceIds: Collection<String>): Set<String> =
        BookDao.findExistingSourceIds(readableDatabase, sourceType, sourceIds)

    fun findBookBySource(sourceType: String, sourceId: String): Book? =
        BookDao.findBookBySource(readableDatabase, sourceType, sourceId)

    fun findQuickLogDuplicate(
        title: String,
        mediaType: MediaType,
        sourceType: String?,
        sourceId: String?,
    ): Book? = BookDao.findQuickLogDuplicate(readableDatabase, title, mediaType, sourceType, sourceId)

    fun findBooksByTitleLike(title: String, mediaType: MediaType): List<Book> =
        BookDao.findBooksByTitleLike(readableDatabase, title, mediaType)

    fun getBooks(status: BookStatus? = null): List<Book> =
        BookDao.getBooks(readableDatabase, status)

    fun getBooksForList(status: BookStatus? = null): List<Book> =
        BookDao.getBooksForList(readableDatabase, status)

    fun getBook(bookId: Long): Book? = BookDao.getBook(readableDatabase, bookId)

    fun updateBook(book: Book): Boolean =
        BookDao.updateBook(writableDatabase, book).also { invalidateBookCache() }

    fun archiveBook(bookId: Long): Boolean =
        BookDao.archiveBook(writableDatabase, bookId).also { invalidateBookCache() }

    fun getAudioTracks(bookId: Long): List<com.example.readtrace.model.AudioTrackItem> =
        BookDao.getAudioTracks(readableDatabase, bookId)

    fun insertAudioTrack(track: com.example.readtrace.model.AudioTrackItem): Long =
        BookDao.insertAudioTrack(writableDatabase, track).also { invalidateBookCache() }

    fun updateAudioTrackDuration(trackId: Long, durationMs: Long) =
        BookDao.updateAudioTrackDuration(writableDatabase, trackId, durationMs)

    fun deleteAudioTrack(trackId: Long) =
        BookDao.deleteAudioTrack(writableDatabase, trackId).also { invalidateBookCache() }

    fun restoreBook(bookId: Long): Boolean =
        BookDao.restoreBook(writableDatabase, bookId).also { invalidateBookCache() }

    fun getArchivedBooks(): List<Book> = BookDao.getArchivedBooks(readableDatabase)

    fun hardDeleteBook(bookId: Long): Boolean =
        BookDao.hardDeleteBook(writableDatabase, bookId).also { invalidateBookCache() }

    fun importParsedRecords(records: List<com.example.readtrace.util.BookCsvParser.ParsedBookRecord>): Int =
        BookDao.importParsedRecords(writableDatabase, records).also { if (it > 0) invalidateBookCache() }

    fun getMemoryBook(): Pair<Book, String>? = BookDao.getMemoryBook(readableDatabase)

    // ---------------------------------------------------------------- 阅读时长/人物/大纲/地点 委托

    fun insertReadingSession(session: ReadingSession): Long =
        BookDao.insertReadingSession(writableDatabase, session)

    fun getReadingSessions(bookId: Long): List<ReadingSession> =
        BookDao.getReadingSessions(readableDatabase, bookId)

    fun getAllReadingSessions(): List<ReadingSession> =
        BookDao.getAllReadingSessions(readableDatabase)

    fun insertCharacter(character: BookCharacter): Long =
        BookDao.insertCharacter(writableDatabase, character)

    fun getCharacters(bookId: Long): List<BookCharacter> =
        BookDao.getCharacters(readableDatabase, bookId)

    fun deleteCharacter(characterId: Long): Boolean =
        BookDao.deleteCharacter(writableDatabase, characterId)

    fun insertOutline(outline: BookOutline): Long =
        BookDao.insertOutline(writableDatabase, outline)

    fun getOutlines(bookId: Long): List<BookOutline> =
        BookDao.getOutlines(readableDatabase, bookId)

    fun deleteOutline(outlineId: Long): Boolean =
        BookDao.deleteOutline(writableDatabase, outlineId)

    fun insertLocation(location: BookLocation): Long =
        BookDao.insertLocation(writableDatabase, location)

    fun getLocations(bookId: Long): List<BookLocation> =
        BookDao.getLocations(readableDatabase, bookId)

    fun deleteLocation(locationId: Long): Boolean =
        BookDao.deleteLocation(writableDatabase, locationId)

    fun getCachedBooks(): List<Book> {
        bookListCache?.let { return it }
        synchronized(bookListCacheLock) {
            bookListCache?.let { return it }
            val fresh = getBooks()
            bookListCache = fresh
            return fresh
        }
    }

    private fun invalidateBookCache() {
        bookListCache = null
        bookListCacheVersion++
        ConceptIndexRepository.invalidate()
    }

    /**
     * 获取所有书籍的不重复标签列表及频次统计，按出现频次降序排列
     */
    // ---------------------------------------------------------------- notes / 备份 DAO 委托
    // P40 Phase 4 阶段 4：实现迁至 NoteDao / MindprintDao，同签名转发，调用点零改动。

    fun insertNote(note: Note): Long = NoteDao.insertNote(writableDatabase, note)

    fun getNotes(bookId: Long): List<Note> = NoteDao.getNotes(readableDatabase, bookId)

    fun getAllNotesLite(): List<Pair<Long, String>> = NoteDao.getAllNotesLite(readableDatabase)

    fun getNote(noteId: Long): Note? = NoteDao.getNote(readableDatabase, noteId)

    fun updateNote(note: Note): Boolean = NoteDao.updateNote(writableDatabase, note)

    fun archiveNote(noteId: Long): Boolean = NoteDao.archiveNote(writableDatabase, noteId)

    fun restoreNote(noteId: Long): Boolean = NoteDao.restoreNote(writableDatabase, noteId)

    fun getArchivedNotes(): List<ArchivedNoteItem> = NoteDao.getArchivedNotes(readableDatabase)

    fun hardDeleteNote(noteId: Long): Boolean = NoteDao.hardDeleteNote(writableDatabase, noteId)

    fun clearAllTrash(): Pair<Int, Int> =
        NoteDao.clearAllTrash(writableDatabase).also { invalidateBookCache() }

    fun getAllFullWorkBackups(): List<com.example.readtrace.util.BackupHelper.WorkBackup> =
        NoteDao.getAllFullWorkBackups(readableDatabase)

    fun importFullBackup(items: List<com.example.readtrace.util.BackupHelper.WorkBackup>): Pair<Int, Int> =
        NoteDao.importFullBackup(writableDatabase, items)

    // ---------------------------------------------------------------- 心智 / 时长 DAO 委托

    fun saveMindprint(mindprint: BookMindprint): Long =
        MindprintDao.saveMindprint(writableDatabase, mindprint)

    fun getMindprint(bookId: Long): BookMindprint = MindprintDao.getMindprint(readableDatabase, bookId)

    fun getAllMindprints(): Map<Long, BookMindprint> = MindprintDao.getAllMindprints(readableDatabase)

    fun getAnnualMindprintPersona(): com.example.readtrace.model.ReadingPersona? =
        MindprintDao.getAnnualMindprintPersona(readableDatabase)

    fun getTodayTotalReadingMinutes(): Int = MindprintDao.getTodayTotalReadingMinutes(readableDatabase)

    fun getRandomOrNextQuote(excludeQuote: String? = null): Pair<Book?, String> =
        MindprintDao.getRandomOrNextQuote(readableDatabase, excludeQuote)

    fun getLatestReadingBook(): Book? = MindprintDao.getLatestReadingBook(readableDatabase)

    fun getAllUniqueTags(): List<Pair<String, Int>> {
        val tagCountMap = mutableMapOf<String, Int>()
        // 年份类标签（如「2024年」）与「待看清单」（愿望单/想读状态已承载）不入标签统计
        val yearTagRegex = Regex("^\\d{4}年?$")
        val books = getBooks()
        books.forEach { book ->
            book.tags.forEach { tag ->
                val clean = tag.trim()
                if (clean.isNotEmpty() && !yearTagRegex.matches(clean) && clean != "待看清单") {
                    tagCountMap[clean] = (tagCountMap[clean] ?: 0) + 1
                }
            }
        }
        return tagCountMap.toList().sortedByDescending { it.second }
    }

    // --- 📊 统计查询 ---
    // P40 Phase 4：实现已抽至 StatsQueries，此处仅保留对外入口（签名不变，调用方零改动）。

    /** 获取已读完书籍总数 */
    fun getTotalFinishedBooksCount(): Int = StatsQueries.totalFinishedBooks(readableDatabase)

    /** 获取有效书籍总数 */
    fun getTotalBooksCount(): Int = StatsQueries.totalBooks(readableDatabase)

    /** 获取有效笔记总数 */
    fun getTotalNotesCount(): Int = StatsQueries.totalNotes(readableDatabase)

    /** 获取不同书籍分类总数 */
    fun getUniqueCategoriesCount(): Int = StatsQueries.uniqueCategories(readableDatabase)

    /** 获取 9.0 分及以上的高分好评书籍数量 */
    fun getHighRatingBooksCount(): Int = StatsQueries.highRatingBooks(readableDatabase)

    data class CuratorFavoriteItem(
        val id: Long,
        val book: Book,
        val mediaType: MediaType,
        val rankOrder: Int,
        val customTagline: String?,
        val createdAt: String,
    )

    fun addFavorite(bookId: Long, mediaType: MediaType, rankOrder: Int = 0, tagline: String? = null): Boolean {
        return try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put(COLUMN_FAVORITE_BOOK_ID, bookId)
                put(COLUMN_FAVORITE_MEDIA_TYPE, mediaType.name.lowercase())
                put(COLUMN_FAVORITE_RANK_ORDER, rankOrder)
                put(COLUMN_FAVORITE_CUSTOM_TAGLINE, tagline)
                put(COLUMN_CREATED_AT, currentTimestamp())
            }
            db.insertWithOnConflict(TABLE_FAVORITES, null, cv, SQLiteDatabase.CONFLICT_REPLACE) > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun removeFavorite(bookId: Long): Boolean {
        return try {
            writableDatabase.delete(TABLE_FAVORITES, "$COLUMN_FAVORITE_BOOK_ID = ?", arrayOf(bookId.toString())) > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun isFavorite(bookId: Long): Boolean {
        return try {
            val cursor = readableDatabase.query(
                TABLE_FAVORITES,
                arrayOf(COLUMN_ID),
                "$COLUMN_FAVORITE_BOOK_ID = ?",
                arrayOf(bookId.toString()),
                null, null, null,
            )
            val exists = cursor.moveToFirst()
            cursor.close()
            exists
        } catch (e: Exception) {
            false
        }
    }

    fun updateFavoriteTagline(bookId: Long, tagline: String?): Boolean {
        return try {
            val cv = ContentValues().apply {
                put(COLUMN_FAVORITE_CUSTOM_TAGLINE, tagline)
            }
            writableDatabase.update(TABLE_FAVORITES, cv, "$COLUMN_FAVORITE_BOOK_ID = ?", arrayOf(bookId.toString())) > 0
        } catch (e: Exception) {
            false
        }
    }

    fun updateFavoriteRank(bookId: Long, newRank: Int): Boolean {
        return try {
            val cv = ContentValues().apply {
                put(COLUMN_FAVORITE_RANK_ORDER, newRank)
            }
            writableDatabase.update(TABLE_FAVORITES, cv, "$COLUMN_FAVORITE_BOOK_ID = ?", arrayOf(bookId.toString())) > 0
        } catch (e: Exception) {
            false
        }
    }

    /** 一次取全部收藏（P38-P2：主页原本按媒介 5 连查合并为 1 次，调用方内存分组） */
    fun getFavorites(): List<CuratorFavoriteItem> {
        val result = mutableListOf<CuratorFavoriteItem>()
        val db = readableDatabase
        val query = """
            SELECT f.$COLUMN_ID as fav_id, f.$COLUMN_FAVORITE_BOOK_ID as fav_book_id,
                   f.$COLUMN_FAVORITE_RANK_ORDER as fav_rank, f.$COLUMN_FAVORITE_CUSTOM_TAGLINE as fav_tagline,
                   f.$COLUMN_CREATED_AT as fav_created_at, b.*
            FROM $TABLE_FAVORITES f
            INNER JOIN $TABLE_BOOKS b ON f.$COLUMN_FAVORITE_BOOK_ID = b.$COLUMN_ID
            WHERE b.$COLUMN_IS_DELETED = 0
            ORDER BY f.$COLUMN_FAVORITE_RANK_ORDER ASC, f.$COLUMN_ID ASC
        """.trimIndent()

        // T2.9：cursor 纳入 use 管理，遍历抛异常时也保证释放（前轮审查登记的泄漏点）
        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                val favId = cursor.getLong(cursor.getColumnIndexOrThrow("fav_id"))
                val rank = cursor.getInt(cursor.getColumnIndexOrThrow("fav_rank"))
                val tagline = cursor.getString(cursor.getColumnIndexOrThrow("fav_tagline"))
                val favCreatedAt = cursor.getString(cursor.getColumnIndexOrThrow("fav_created_at"))
                val book = cursor.toBook()
                val mediaType = book.mediaType
                result.add(
                    CuratorFavoriteItem(
                        id = favId,
                        book = book,
                        mediaType = mediaType,
                        rankOrder = rank,
                        customTagline = tagline,
                        createdAt = favCreatedAt,
                    )
                )
            }
        }
        return result
    }

    fun getFavoritesByMediaType(mediaType: MediaType): List<CuratorFavoriteItem> =
        getFavorites().filter { it.mediaType == mediaType }

    fun getFavoriteCount(): Int {
        // T2.9：cursor 纳入 use 管理（前轮审查登记的泄漏点）
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_FAVORITES", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    companion object {

        internal fun currentTimestamp(): String =
            OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        const val DATABASE_NAME = "readtrace.db"

        // 书籍列表内存缓存：helper 为进程级真单例，缓存全局共享以保证写后 invalidate 跨页面即时生效
        @Volatile
        private var bookListCache: List<Book>? = null
        private val bookListCacheLock = Any()

        /** 缓存代际版本号（T2.1）：每次失效递增，供列表页检测「外部页面发生过写操作」 */
        @Volatile
        private var bookListCacheVersion = 0

        fun getBookListCacheVersion(): Int = bookListCacheVersion
        const val TABLE_FAVORITES = "curator_favorites"
        const val COLUMN_FAVORITE_BOOK_ID = "book_id"
        const val COLUMN_FAVORITE_MEDIA_TYPE = "media_type"
        const val COLUMN_FAVORITE_RANK_ORDER = "rank_order"
        const val COLUMN_FAVORITE_CUSTOM_TAGLINE = "custom_tagline"
        const val TABLE_AUDIO_TRACKS = "audio_tracks"
        const val COLUMN_AUDIO_BOOK_ID = "book_id"
        const val COLUMN_AUDIO_ORDER = "track_order"
        const val COLUMN_AUDIO_TITLE = "title"
        const val COLUMN_AUDIO_URI = "file_uri"
        const val COLUMN_AUDIO_DURATION = "duration_ms"
        /**
         * 数据库版本号。注意它同时兼任「播种版本号」——`runPresetSeedsOnce` 以
         * `KEY_SEED_VERSION != DATABASE_VERSION` 作为重播种触发条件，两者被
         * `putInt(KEY_SEED_VERSION, DATABASE_VERSION)` 绑死，因此提升本值会同时
         * 触发一次数据重播种。
         *
         * v17（2026-09-14）：结构零变更，仅为触发重播种以补齐预置的 22 条心智档案
         * （movie 11 + music 11）。此前 v16 期间 `book_mindprints` 表为空，
         * 导致双生共鸣（跨媒介星弦）无数据可用。
         * 影响评估见 `docs/db_upgrade_v16_to_v17_assessment.md`：
         * `DatabaseMigrator.onUpgrade` 最高分支为 `oldVersion < 16`，16→17 全部跳过；
         * 且两个 rating 相关分支（`previousSeedVersion < 15` / `== 0`）均不命中，评分不受影响。
         *
         * v18（2026-09-15）：无表结构变更。新增 `oldVersion < 18` 数据迁移，将预置作品的
         * 陈列位置由「展厅第N层」改为「馆藏第N层」（3D 展厅已下线，隐喻不再成立）。
         * 该 UPDATE 的 WHERE 精确匹配旧预设原文，用户手改过的值不会被覆盖。
         * 同时因播种版本号与本值绑死，本次升版会额外触发一次重播种（单事务包裹，
         * 写入安全约束同 v17：不改写评分、封面仅在为空时写入）。
         */
        const val DATABASE_VERSION = 18

        @Volatile
        private var instance: BookDatabaseHelper? = null

        fun getInstance(context: Context): BookDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: BookDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }

    }
}
