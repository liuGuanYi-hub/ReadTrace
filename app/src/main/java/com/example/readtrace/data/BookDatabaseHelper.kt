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

    fun insertBook(book: Book): Long {
        val now = currentTimestamp()
        val values = book.toContentValues().apply {
            put(COLUMN_CREATED_AT, book.createdAt.ifBlank { now })
            put(COLUMN_UPDATED_AT, book.updatedAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
            putNull(COLUMN_DELETED_AT)
        }
        val newId = writableDatabase.insertOrThrow(TABLE_BOOKS, null, values)
        invalidateBookCache()
        return newId
    }

    /** 外部导入批量写入：单事务 + 预编译 SQLiteStatement 极速落盘 + 末尾一次缓存失效 */
    fun insertBooksBatch(books: List<Book>): Int {
        if (books.isEmpty()) return 0
        val now = currentTimestamp()
        val db = writableDatabase
        val sql = """
            INSERT INTO $TABLE_BOOKS (
                $COLUMN_TITLE, $COLUMN_AUTHOR, $COLUMN_COVER_URL, $COLUMN_CATEGORY,
                $COLUMN_STATUS, $COLUMN_MEDIA_TYPE, $COLUMN_RATING, $COLUMN_TAGS,
                $COLUMN_SHORT_COMMENT, $COLUMN_REVIEW, $COLUMN_START_DATE, $COLUMN_FINISH_DATE,
                $COLUMN_BUY_CHANNEL, $COLUMN_SHELF_LOCATION, $COLUMN_BINDING_TYPE, $COLUMN_BUY_PRICE,
                $COLUMN_SOURCE_TYPE, $COLUMN_SOURCE_ID, $COLUMN_REMOTE_RATING, $COLUMN_DESCRIPTION,
                $COLUMN_CREATED_AT, $COLUMN_UPDATED_AT, $COLUMN_IS_DELETED, $COLUMN_DELETED_AT
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL)
        """.trimIndent()

        db.beginTransaction()
        try {
            val statement = db.compileStatement(sql)
            for (book in books) {
                statement.clearBindings()
                statement.bindString(1, book.title.trim())
                book.author?.let { statement.bindString(2, it) } ?: statement.bindNull(2)
                book.coverUrl?.let { statement.bindString(3, it) } ?: statement.bindNull(3)
                book.category?.let { statement.bindString(4, it) } ?: statement.bindNull(4)
                statement.bindString(5, book.status.databaseValue)
                statement.bindString(6, book.mediaType.databaseValue)
                book.rating?.let { statement.bindDouble(7, it) } ?: statement.bindNull(7)
                statement.bindString(8, org.json.JSONArray(book.tags).toString())
                book.shortComment?.let { statement.bindString(9, it) } ?: statement.bindNull(9)
                book.review?.let { statement.bindString(10, it) } ?: statement.bindNull(10)
                book.startDate?.let { statement.bindString(11, it) } ?: statement.bindNull(11)
                book.finishDate?.let { statement.bindString(12, it) } ?: statement.bindNull(12)
                book.buyChannel?.let { statement.bindString(13, it) } ?: statement.bindNull(13)
                book.shelfLocation?.let { statement.bindString(14, it) } ?: statement.bindNull(14)
                book.bindingType?.let { statement.bindString(15, it) } ?: statement.bindNull(15)
                book.buyPrice?.let { statement.bindDouble(16, it) } ?: statement.bindNull(16)
                book.sourceType?.let { statement.bindString(17, it) } ?: statement.bindNull(17)
                book.sourceId?.let { statement.bindString(18, it) } ?: statement.bindNull(18)
                book.remoteRating?.let { statement.bindDouble(19, it) } ?: statement.bindNull(19)
                book.description?.let { statement.bindString(20, it) } ?: statement.bindNull(20)
                statement.bindString(21, book.createdAt.ifBlank { now })
                statement.bindString(22, book.updatedAt.ifBlank { now })
                statement.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        invalidateBookCache()
        return books.size
    }

    /** 外部导入单批批量查重：一次性查出已入库 source_id 集合（含已删除作品，防回收站复活），杜绝 N 次循环查库 */
    fun findExistingSourceIds(sourceType: String, sourceIds: Collection<String>): Set<String> {
        if (sourceIds.isEmpty()) return emptySet()
        val found = mutableSetOf<String>()
        val idList = sourceIds.toList()
        val chunkSize = 400
        val db = readableDatabase
        for (chunk in idList.chunked(chunkSize)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val args = arrayOf(sourceType) + chunk.toTypedArray()
            db.query(
                TABLE_BOOKS,
                arrayOf(COLUMN_SOURCE_ID),
                "$COLUMN_SOURCE_TYPE = ? AND $COLUMN_SOURCE_ID IN ($placeholders)",
                args,
                null, null, null,
            ).use { cursor ->
                val idx = cursor.getColumnIndexOrThrow(COLUMN_SOURCE_ID)
                while (cursor.moveToNext()) {
                    cursor.getString(idx)?.let { found.add(it) }
                }
            }
        }
        return found
    }

    /** 外部导入第一层精确查重：同一来源的同一条目是否已导入过（含已删除作品，避免回收站复活） */
    fun findBookBySource(sourceType: String, sourceId: String): Book? =
        readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COLUMN_SOURCE_TYPE = ? AND $COLUMN_SOURCE_ID = ?",
            arrayOf(sourceType, sourceId),
            null, null, null, "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toBook() else null
        }

    /** 速记入库查重（P38-G7）：同来源精确命中，或同媒介同名（忽略 ASCII 大小写）的未删除作品；回收站作品不拦截重录 */
    fun findQuickLogDuplicate(
        title: String,
        mediaType: MediaType,
        sourceType: String?,
        sourceId: String?,
    ): Book? {
        if (!sourceType.isNullOrBlank() && !sourceId.isNullOrBlank()) {
            readableDatabase.query(
                TABLE_BOOKS,
                null,
                "$COLUMN_IS_DELETED = ? AND $COLUMN_SOURCE_TYPE = ? AND $COLUMN_SOURCE_ID = ?",
                arrayOf("0", sourceType, sourceId),
                null, null, null, "1",
            ).use { cursor ->
                if (cursor.moveToFirst()) return cursor.toBook()
            }
        }
        return readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COLUMN_IS_DELETED = ? AND $COLUMN_MEDIA_TYPE = ? AND UPPER($COLUMN_TITLE) = ?",
            arrayOf("0", mediaType.databaseValue, title.trim().uppercase()),
            null, null, null, "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toBook() else null
        }
    }

    /** 外部导入第二层模糊查重：标题近似 + 同一媒介类型的手动录入作品，命中时提示「可能已存在」 */
    fun findBooksByTitleLike(title: String, mediaType: MediaType): List<Book> {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return emptyList()
        return readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COLUMN_IS_DELETED = ? AND $COLUMN_MEDIA_TYPE = ? AND " +
                "($COLUMN_TITLE LIKE ? OR ? LIKE ('%' || $COLUMN_TITLE || '%'))",
            arrayOf("0", mediaType.databaseValue, "%$trimmed%", trimmed),
            null, null,
            "$COLUMN_UPDATED_AT DESC",
            "5",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toBook())
            }
        }
    }

    fun getBooks(status: BookStatus? = null): List<Book> {
        val selectionParts = mutableListOf("$COLUMN_IS_DELETED = ?")
        val selectionArgs = mutableListOf("0")
        if (status != null) {
            selectionParts += "$COLUMN_STATUS = ?"
            selectionArgs += status.databaseValue
        }

        return readableDatabase.query(
            TABLE_BOOKS,
            null,
            selectionParts.joinToString(" AND "),
            selectionArgs.toTypedArray(),
            null,
            null,
            "$COLUMN_UPDATED_AT DESC, $COLUMN_ID DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBook())
                }
            }
        }
    }

    /**
     * 藏库列表轻量查询（T2.1）：与 getBooks 同序同过滤，但排除 description / review
     * 两个长文本列——列表与搜索（标题/作者/分类/标签/拼音）均不消费长文，
     * 每次切 Tab、搜索过滤不再把全站简介与书评拖出 SQLite。
     * 详情页、备份导出等需要完整字段的场景仍使用 getBooks()。
     */
    fun getBooksForList(status: BookStatus? = null): List<Book> {
        val projection = arrayOf(
            COLUMN_ID, COLUMN_TITLE, COLUMN_AUTHOR, COLUMN_COVER_URL, COLUMN_CATEGORY,
            COLUMN_STATUS, COLUMN_MEDIA_TYPE, COLUMN_RATING, COLUMN_TAGS, COLUMN_SHORT_COMMENT,
            COLUMN_START_DATE, COLUMN_FINISH_DATE, COLUMN_BUY_CHANNEL, COLUMN_SHELF_LOCATION,
            COLUMN_BINDING_TYPE, COLUMN_BUY_PRICE, COLUMN_CREATED_AT, COLUMN_UPDATED_AT,
            COLUMN_IS_DELETED, COLUMN_DELETED_AT, COLUMN_SOURCE_TYPE, COLUMN_SOURCE_ID,
            COLUMN_REMOTE_RATING,
        )
        val selectionParts = mutableListOf("$COLUMN_IS_DELETED = ?")
        val selectionArgs = mutableListOf("0")
        if (status != null) {
            selectionParts += "$COLUMN_STATUS = ?"
            selectionArgs += status.databaseValue
        }

        return readableDatabase.query(
            TABLE_BOOKS,
            projection,
            selectionParts.joinToString(" AND "),
            selectionArgs.toTypedArray(),
            null,
            null,
            "$COLUMN_UPDATED_AT DESC, $COLUMN_ID DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBook())
                }
            }
        }
    }

    fun getBook(bookId: Long): Book? =
        readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(bookId.toString(), "0"),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toBook() else null
        }

    fun updateBook(book: Book): Boolean {
        if (book.id <= 0) return false
        val values = book.toContentValues().apply {
            put(COLUMN_UPDATED_AT, currentTimestamp())
        }
        val rows = writableDatabase.update(
            TABLE_BOOKS,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(book.id.toString(), "0"),
        )
        invalidateBookCache()
        return rows > 0
    }

    fun archiveBook(bookId: Long): Boolean {
        if (bookId <= 0) return false
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, now)
            put(COLUMN_UPDATED_AT, now)
        }
        val rows = writableDatabase.update(
            TABLE_BOOKS,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(bookId.toString(), "0"),
        )
        invalidateBookCache()
        return rows > 0
    }

    fun getAudioTracks(bookId: Long): List<com.example.readtrace.model.AudioTrackItem> {
        val tracks = mutableListOf<com.example.readtrace.model.AudioTrackItem>()
        readableDatabase.query(
            TABLE_AUDIO_TRACKS,
            null,
            "$COLUMN_AUDIO_BOOK_ID = ?",
            arrayOf(bookId.toString()),
            null, null,
            "$COLUMN_AUDIO_ORDER ASC, $COLUMN_ID ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tracks += com.example.readtrace.model.AudioTrackItem(
                    id = cursor.getLong(0),
                    bookId = cursor.getLong(1),
                    trackOrder = cursor.getInt(2),
                    title = cursor.getString(3) ?: "未命名曲目",
                    fileUri = cursor.getString(4) ?: continue,
                    durationMs = cursor.getLong(5),
                )
            }
        }
        return tracks
    }

    fun insertAudioTrack(track: com.example.readtrace.model.AudioTrackItem): Long {
        invalidateBookCache()
        return writableDatabase.insert(
            TABLE_AUDIO_TRACKS,
            null,
            android.content.ContentValues().apply {
                put(COLUMN_AUDIO_BOOK_ID, track.bookId)
                put(COLUMN_AUDIO_ORDER, track.trackOrder)
                put(COLUMN_AUDIO_TITLE, track.title)
                put(COLUMN_AUDIO_URI, track.fileUri)
                put(COLUMN_AUDIO_DURATION, track.durationMs)
            },
        )
    }

    fun updateAudioTrackDuration(trackId: Long, durationMs: Long) {
        writableDatabase.update(
            TABLE_AUDIO_TRACKS,
            android.content.ContentValues().apply { put(COLUMN_AUDIO_DURATION, durationMs) },
            "$COLUMN_ID = ?",
            arrayOf(trackId.toString()),
        )
    }

    fun deleteAudioTrack(trackId: Long) {
        invalidateBookCache()
        writableDatabase.delete(TABLE_AUDIO_TRACKS, "$COLUMN_ID = ?", arrayOf(trackId.toString()))
    }

    fun deleteAudioTracksOfBook(bookId: Long) {
        invalidateBookCache()
        writableDatabase.delete(TABLE_AUDIO_TRACKS, "$COLUMN_AUDIO_BOOK_ID = ?", arrayOf(bookId.toString()))
    }

    /**
     * 恢复已归档的书籍
     */
    fun restoreBook(bookId: Long): Boolean {
        if (bookId <= 0) return false
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 0)
            putNull(COLUMN_DELETED_AT)
            put(COLUMN_UPDATED_AT, now)
        }
        val rows = writableDatabase.update(
            TABLE_BOOKS,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(bookId.toString(), "1"),
        )
        invalidateBookCache()
        return rows > 0
    }

    /**
     * 获取所有已归档的书籍（回收站）
     */
    fun getArchivedBooks(): List<Book> {
        return readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COLUMN_IS_DELETED = ?",
            arrayOf("1"),
            null,
            null,
            "$COLUMN_DELETED_AT DESC, $COLUMN_ID DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBook())
                }
            }
        }
    }

    /**
     * 彻底物理删除书籍（物理清理关联封面文件、关联笔记及书籍本体）
     * 必须在用户二次确认后调用
     */
    fun hardDeleteBook(bookId: Long): Boolean {
        if (bookId <= 0) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            // 1. 查询并清理关联的本地封面文件
            val book = getBookAny(bookId)
            book?.coverUrl?.let { path ->
                CoverImageHelper.deleteCoverFile(path)
            }

            // 2. 级联物理删除关联的笔记与 6 大高阶维度数据
            val bookIdStr = arrayOf(bookId.toString())
            db.delete(TABLE_NOTES, "$COLUMN_BOOK_ID = ?", bookIdStr)
            db.delete(TABLE_READING_SESSIONS, "$COLUMN_BOOK_ID = ?", bookIdStr)
            db.delete(TABLE_BOOK_CHARACTERS, "$COLUMN_BOOK_ID = ?", bookIdStr)
            db.delete(TABLE_BOOK_OUTLINES, "$COLUMN_BOOK_ID = ?", bookIdStr)
            db.delete(TABLE_BOOK_LOCATIONS, "$COLUMN_BOOK_ID = ?", bookIdStr)
            db.delete(TABLE_BOOK_MINDPRINTS, "$COLUMN_BOOK_ID = ?", bookIdStr)
            db.delete(TABLE_AUDIO_TRACKS, "$COLUMN_AUDIO_BOOK_ID = ?", bookIdStr)

            // 3. 删除书籍记录
            val deleted = db.delete(TABLE_BOOKS, "$COLUMN_ID = ?", bookIdStr) > 0
            db.setTransactionSuccessful()
            invalidateBookCache()
            return deleted
        } finally {
            db.endTransaction()
        }
    }

    private fun getBookAny(bookId: Long): Book? =
        readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(bookId.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toBook() else null
        }

    /**
     * 批量导入多维度 CSV 解析记录（包含作品属性与六维心智模型）。
     * 若已存在同名作品，则智能补充完善其评分、短评、长评、封面与六维心智；若不存在则新增插入。
     */
    fun importParsedRecords(records: List<com.example.readtrace.util.BookCsvParser.ParsedBookRecord>): Int {
        if (records.isEmpty()) return 0
        val db = writableDatabase
        var affectedCount = 0
        db.beginTransaction()
        try {
            val now = currentTimestamp()
            for (record in records) {
                val book = record.book
                val cleanTitle = book.title.trim()
                if (cleanTitle.isEmpty()) continue

                val cursor = if (book.author.isNullOrEmpty()) {
                    db.query(
                        TABLE_BOOKS,
                        arrayOf(COLUMN_ID),
                        "$COLUMN_TITLE = ? AND ($COLUMN_AUTHOR IS NULL OR $COLUMN_AUTHOR = '') AND $COLUMN_IS_DELETED = 0",
                        arrayOf(cleanTitle),
                        null, null, null, "1"
                    )
                } else {
                    db.query(
                        TABLE_BOOKS,
                        arrayOf(COLUMN_ID),
                        "$COLUMN_TITLE = ? AND $COLUMN_AUTHOR = ? AND $COLUMN_IS_DELETED = 0",
                        arrayOf(cleanTitle, book.author.trim()),
                        null, null, null, "1"
                    )
                }

                val existingId = cursor.use {
                    if (it.moveToFirst()) it.getLong(0) else null
                }

                val targetBookId: Long
                if (existingId != null) {
                    targetBookId = existingId
                    val updateCv = ContentValues().apply {
                        if (!book.coverUrl.isNullOrBlank()) put(COLUMN_COVER_URL, book.coverUrl)
                        if (!book.category.isNullOrBlank()) put(COLUMN_CATEGORY, book.category)
                        if (book.rating != null) put(COLUMN_RATING, book.rating)
                        if (book.tags.isNotEmpty()) put(COLUMN_TAGS, org.json.JSONArray(book.tags).toString())
                        if (!book.shortComment.isNullOrBlank()) put(COLUMN_SHORT_COMMENT, book.shortComment)
                        if (!book.review.isNullOrBlank()) put(COLUMN_REVIEW, book.review)
                        put(COLUMN_MEDIA_TYPE, book.mediaType.databaseValue)
                        put(COLUMN_UPDATED_AT, now)
                    }
                    db.update(TABLE_BOOKS, updateCv, "$COLUMN_ID = ?", arrayOf(targetBookId.toString()))
                    affectedCount++
                } else {
                    val values = book.toContentValues().apply {
                        put(COLUMN_TITLE, cleanTitle)
                        put(COLUMN_CREATED_AT, book.createdAt.ifBlank { now })
                        put(COLUMN_UPDATED_AT, book.updatedAt.ifBlank { now })
                        put(COLUMN_IS_DELETED, 0)
                        putNull(COLUMN_DELETED_AT)
                    }
                    val rowId = db.insert(TABLE_BOOKS, null, values)
                    if (rowId > 0) {
                        targetBookId = rowId
                        affectedCount++
                    } else {
                        targetBookId = -1L
                    }
                }

                // 写入或更新六维心智雷达
                if (targetBookId > 0 && record.mindprint != null) {
                    val mp = record.mindprint
                    val mpCv = ContentValues().apply {
                        put(COLUMN_BOOK_ID, targetBookId)
                        put(COLUMN_DEPTH_SCORE, mp.depthScore)
                        put(COLUMN_ARTISTRY_SCORE, mp.artistryScore)
                        put(COLUMN_EMOTION_SCORE, mp.emotionScore)
                        put(COLUMN_LOGIC_SCORE, mp.logicScore)
                        put(COLUMN_DIFFICULTY_SCORE, mp.difficultyScore)
                        put(COLUMN_HEALING_SCORE, mp.healingScore)
                        put(COLUMN_UPDATED_AT, now)
                    }
                    db.insertWithOnConflict(TABLE_BOOK_MINDPRINTS, null, mpCv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (affectedCount > 0) invalidateBookCache()
        return affectedCount
    }

    /**
     * 批量导入书籍列表（用于旧版兼容与快速文本导入）。
     */
    fun importBooks(books: List<Book>): Int {
        if (books.isEmpty()) return 0
        val records = books.map { com.example.readtrace.util.BookCsvParser.ParsedBookRecord(it, null) }
        return importParsedRecords(records)
    }

    private fun isBookExists(db: SQLiteDatabase, title: String, author: String?): Boolean {
        val selection: String
        val args: Array<String>
        if (author.isNullOrEmpty()) {
            selection = "$COLUMN_TITLE = ? AND ($COLUMN_AUTHOR IS NULL OR $COLUMN_AUTHOR = '') AND $COLUMN_IS_DELETED = 0"
            args = arrayOf(title)
        } else {
            selection = "$COLUMN_TITLE = ? AND $COLUMN_AUTHOR = ? AND $COLUMN_IS_DELETED = 0"
            args = arrayOf(title, author)
        }
        return db.query(TABLE_BOOKS, arrayOf(COLUMN_ID), selection, args, null, null, null, "1").use { cursor ->
            cursor.moveToFirst()
        }
    }

    /**
     * 获取「那年今日」回忆作品与描述信息。
     * 优先匹配历史年份今日读完/看完/通关的作品，次之随机精选一部已完成作品作为时光漫忆。
     */
    fun getMemoryBook(): Pair<Book, String>? {
        val today = LocalDate.now()
        val monthDayPattern = String.format("%%-%02d-%02d", today.monthValue, today.dayOfMonth)

        // 1. 查询历史同月同日完成的作品（非当年今天）
        val todayBooks = readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COLUMN_FINISH_DATE LIKE ? AND $COLUMN_IS_DELETED = 0 AND $COLUMN_STATUS = ?",
            arrayOf(monthDayPattern, BookStatus.FINISHED.databaseValue),
            null,
            null,
            "$COLUMN_FINISH_DATE DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBook())
                }
            }
        }

        // 寻找非今天（往年）或今天完成的作品
        for (book in todayBooks) {
            val finishDateStr = book.finishDate ?: continue
            val finishDate = runCatching { LocalDate.parse(finishDateStr) }.getOrNull() ?: continue
            val years = ChronoUnit.YEARS.between(finishDate, today)
            if (years > 0) {
                return Pair(book, book.mediaType.getFinishedPastMemory(years))
            } else if (finishDate == today) {
                return Pair(book, book.mediaType.getFinishedTodayMemory())
            }
        }

        // 2. 如果无今日匹配，则优选一部最近或评分较高的已完成作品作为时光漫忆
        val finishedBooks = readableDatabase.query(
            TABLE_BOOKS,
            null,
            "$COLUMN_STATUS = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(BookStatus.FINISHED.databaseValue, "0"),
            null,
            null,
            "$COLUMN_RATING DESC, $COLUMN_UPDATED_AT DESC",
            "10",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBook())
                }
            }
        }

        if (finishedBooks.isNotEmpty()) {
            val randomBook = finishedBooks.random()
            return Pair(randomBook, randomBook.mediaType.getRandomMemory())
        }

        return null
    }

    /**
     * 获取月度读完统计（按完成日期月份统计）
     */
    fun getMonthlyFinishedStats(limit: Int = 6): List<MonthlyReadingStat> {
        val sql = """
            SELECT SUBSTR($COLUMN_FINISH_DATE, 1, 7) AS month_str, COUNT(*) AS count_num
            FROM $TABLE_BOOKS
            WHERE $COLUMN_IS_DELETED = 0 
              AND $COLUMN_STATUS = '${BookStatus.FINISHED.databaseValue}'
              AND $COLUMN_FINISH_DATE IS NOT NULL 
              AND length($COLUMN_FINISH_DATE) >= 7
            GROUP BY month_str
            ORDER BY month_str DESC
            LIMIT ?
        """.trimIndent()

        return readableDatabase.rawQuery(sql, arrayOf(limit.toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val month = cursor.getString(0) ?: ""
                    val count = cursor.getInt(1)
                    if (month.isNotEmpty()) {
                        add(MonthlyReadingStat(month, count))
                    }
                }
            }
        }
    }

    fun insertNote(note: Note): Long {
        val now = currentTimestamp()
        val values = note.toContentValues().apply {
            put(COLUMN_CREATED_AT, note.createdAt.ifBlank { now })
            put(COLUMN_UPDATED_AT, note.updatedAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
            putNull(COLUMN_DELETED_AT)
        }
        val id = writableDatabase.insertOrThrow(TABLE_NOTES, null, values)
        ConceptIndexRepository.invalidate()
        return id
    }

    fun getNotes(bookId: Long): List<Note> =
        readableDatabase.query(
            TABLE_NOTES,
            null,
            "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(bookId.toString(), "0"),
            null,
            null,
            "$COLUMN_CREATED_AT ASC, $COLUMN_ID ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toNote())
                }
            }
        }

    /**
     * T4.6-b：批量取出全部有效笔记的 (bookId, createdAt) 轻量对，供跨作品的时间维度聚合使用
     * （如年度年鉴的笔记计数），替代「逐作品 getNotes(b.id)」的 N+1 查询。
     * 只取两列，不携带笔记正文。
     */
    fun getAllNotesLite(): List<Pair<Long, String>> =
        readableDatabase.query(
            TABLE_NOTES,
            arrayOf(COLUMN_BOOK_ID, COLUMN_CREATED_AT),
            "$COLUMN_IS_DELETED = ?",
            arrayOf("0"),
            null,
            null,
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getLong(0) to (cursor.getString(1) ?: ""))
                }
            }
        }

    fun getNote(noteId: Long): Note? =
        readableDatabase.query(
            TABLE_NOTES,
            null,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(noteId.toString(), "0"),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toNote() else null
        }

    fun updateNote(note: Note): Boolean {
        if (note.id <= 0) return false
        val values = note.toContentValues().apply {
            put(COLUMN_UPDATED_AT, currentTimestamp())
        }
        val count = writableDatabase.update(
            TABLE_NOTES,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(note.id.toString(), "0"),
        )
        if (count > 0) ConceptIndexRepository.invalidate()
        return count > 0
    }

    fun archiveNote(noteId: Long): Boolean {
        if (noteId <= 0) return false
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, now)
            put(COLUMN_UPDATED_AT, now)
        }
        val count = writableDatabase.update(
            TABLE_NOTES,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(noteId.toString(), "0"),
        )
        if (count > 0) ConceptIndexRepository.invalidate()
        return count > 0
    }

    /**
     * 恢复已归档的笔记
     */
    fun restoreNote(noteId: Long): Boolean {
        if (noteId <= 0) return false
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 0)
            putNull(COLUMN_DELETED_AT)
            put(COLUMN_UPDATED_AT, now)
        }
        val count = writableDatabase.update(
            TABLE_NOTES,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(noteId.toString(), "1"),
        )
        if (count > 0) ConceptIndexRepository.invalidate()
        return count > 0
    }

    /**
     * 获取所有已归档的笔记（带所属书名）
     */
    fun getArchivedNotes(): List<ArchivedNoteItem> {
        val sql = """
            SELECT n.$COLUMN_ID, n.$COLUMN_BOOK_ID, n.$COLUMN_CONTENT, n.$COLUMN_NOTE_TYPE,
                   n.$COLUMN_PAGE, n.$COLUMN_CHAPTER, n.$COLUMN_CREATED_AT, n.$COLUMN_UPDATED_AT,
                   n.$COLUMN_IS_DELETED, n.$COLUMN_DELETED_AT, b.$COLUMN_TITLE AS book_title
            FROM $TABLE_NOTES n
            LEFT JOIN $TABLE_BOOKS b ON n.$COLUMN_BOOK_ID = b.$COLUMN_ID
            WHERE n.$COLUMN_IS_DELETED = 1
            ORDER BY n.$COLUMN_DELETED_AT DESC, n.$COLUMN_ID DESC
        """.trimIndent()

        return readableDatabase.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val note = cursor.toNote()
                    val titleIndex = cursor.getColumnIndex("book_title")
                    val bookTitle = if (titleIndex != -1 && !cursor.isNull(titleIndex)) cursor.getString(titleIndex) else null
                    add(ArchivedNoteItem(note, bookTitle))
                }
            }
        }
    }

    /**
     * 彻底物理删除笔记
     * 必须在用户二次确认后调用
     */
    fun hardDeleteNote(noteId: Long): Boolean {
        if (noteId <= 0) return false
        return writableDatabase.delete(
            TABLE_NOTES,
            "$COLUMN_ID = ?",
            arrayOf(noteId.toString()),
        ) > 0
    }

    /**
     * 彻底清空回收站中的所有书籍与笔记，并清理所有相关封面图片
     * 必须在用户二次确认后调用
     * @return Pair(删除的书籍数量, 删除的笔记数量)
     */
    fun clearAllTrash(): Pair<Int, Int> {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // 1. 获取所有归档书籍的封面并清理文件
            val archivedBooks = getArchivedBooks()
            archivedBooks.forEach { book ->
                book.coverUrl?.let { CoverImageHelper.deleteCoverFile(it) }
            }

            // 2. 获取回收站中所有被删除的书籍 ID 列表，物理级联删除 6 张关联子表
            val trashBookIds = archivedBooks.map { it.id.toString() }
            if (trashBookIds.isNotEmpty()) {
                val placeholders = trashBookIds.joinToString(",") { "?" }
                val args = trashBookIds.toTypedArray()
                db.delete(TABLE_READING_SESSIONS, "$COLUMN_BOOK_ID IN ($placeholders)", args)
                db.delete(TABLE_BOOK_CHARACTERS, "$COLUMN_BOOK_ID IN ($placeholders)", args)
                db.delete(TABLE_BOOK_OUTLINES, "$COLUMN_BOOK_ID IN ($placeholders)", args)
                db.delete(TABLE_BOOK_LOCATIONS, "$COLUMN_BOOK_ID IN ($placeholders)", args)
                db.delete(TABLE_BOOK_MINDPRINTS, "$COLUMN_BOOK_ID IN ($placeholders)", args)
                db.delete(TABLE_AUDIO_TRACKS, "$COLUMN_AUDIO_BOOK_ID IN ($placeholders)", args)
            }

            // 3. 物理删除所有归档书籍关联的笔记及单独归档的笔记
            val deletedNotesCount = db.delete(TABLE_NOTES, "$COLUMN_IS_DELETED = 1", null)
            val deletedBooksCount = db.delete(TABLE_BOOKS, "$COLUMN_IS_DELETED = 1", null)

            db.setTransactionSuccessful()
            invalidateBookCache()
            return Pair(deletedBooksCount, deletedNotesCount)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * 获取全部作品及各自关联的笔记（用于全量备份与多格式导出）
     */
    fun getAllWorksWithNotes(): List<Pair<Book, List<Note>>> {
        val books = getBooks()
        return books.map { book ->
            val notes = getNotes(book.id)
            Pair(book, notes)
        }
    }

    /**
     * 全量深度查询：书籍 + 笔记 + 6 大高阶资产（打卡/人物/大纲/地标/心智/曲目）
     */
    fun getAllFullWorkBackups(): List<com.example.readtrace.util.BackupHelper.WorkBackup> {
        val books = getBooks()
        val mindprints = getAllMindprints()
        // T2.7：六张子表各一次全量查询 + 内存分组，替代每部作品 6 次查询的 N+1（500 部作品 = 7 次查询替代 3500 次）
        val notesByBook = queryWorksTableGroupedByBook(TABLE_NOTES, includeDeleted = false, { it.toNote() }, { it.bookId })
        val sessionsByBook = queryWorksTableGroupedByBook(TABLE_READING_SESSIONS, includeDeleted = false, { it.toReadingSession() }, { it.bookId })
        val charactersByBook = queryWorksTableGroupedByBook(TABLE_BOOK_CHARACTERS, includeDeleted = false, { it.toBookCharacter() }, { it.bookId })
        val outlinesByBook = queryWorksTableGroupedByBook(TABLE_BOOK_OUTLINES, includeDeleted = false, { it.toBookOutline() }, { it.bookId })
        val locationsByBook = queryWorksTableGroupedByBook(TABLE_BOOK_LOCATIONS, includeDeleted = false, { it.toBookLocation() }, { it.bookId })
        val tracksByBook = queryAllAudioTracksGroupedByBook()
        return books.map { book ->
            com.example.readtrace.util.BackupHelper.WorkBackup(
                book = book,
                notes = notesByBook[book.id].orEmpty(),
                sessions = sessionsByBook[book.id].orEmpty(),
                characters = charactersByBook[book.id].orEmpty(),
                outlines = outlinesByBook[book.id].orEmpty(),
                locations = locationsByBook[book.id].orEmpty(),
                mindprint = mindprints[book.id],
                audioTracks = tracksByBook[book.id].orEmpty(),
            )
        }
    }

    /**
     * T2.7：一次性拉取整张子表并按 book_id 分组，替代「每部作品一次查询」的 N+1 模式。
     * includeDeleted=false 时过滤 is_deleted=0，与各单项查询（getNotes 等）语义一致。
     */
    private inline fun <T> queryWorksTableGroupedByBook(
        table: String,
        includeDeleted: Boolean,
        crossinline mapRow: (Cursor) -> T,
        crossinline bookIdOf: (T) -> Long,
    ): Map<Long, MutableList<T>> {
        val selection = if (includeDeleted) null else "$COLUMN_IS_DELETED = 0"
        val grouped = linkedMapOf<Long, MutableList<T>>()
        readableDatabase.query(table, null, selection, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val item = mapRow(cursor)
                grouped.getOrPut(bookIdOf(item)) { mutableListOf() }.add(item)
            }
        }
        return grouped
    }

    /** T2.7：audio_tracks 专用分组（该表无 is_deleted 列，与 getAudioTracks 语义一致） */
    private fun queryAllAudioTracksGroupedByBook(): Map<Long, MutableList<com.example.readtrace.model.AudioTrackItem>> {
        val grouped = linkedMapOf<Long, MutableList<com.example.readtrace.model.AudioTrackItem>>()
        readableDatabase.query(
            TABLE_AUDIO_TRACKS, null, null, null, null, null,
            "$COLUMN_AUDIO_ORDER ASC, $COLUMN_ID ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                // 与 getAudioTracks 的 `?: continue` 语义一致：无 URI 的行跳过（非 inline lambda 内用 if 包裹）
                val fileUri = cursor.getString(4)
                if (fileUri != null) {
                    grouped.getOrPut(cursor.getLong(1)) { mutableListOf() }.add(
                        com.example.readtrace.model.AudioTrackItem(
                            id = cursor.getLong(0),
                            bookId = cursor.getLong(1),
                            trackOrder = cursor.getInt(2),
                            title = cursor.getString(3) ?: "未命名曲目",
                            fileUri = fileUri,
                            durationMs = cursor.getLong(5),
                        )
                    )
                }
            }
        }
        return grouped
    }

    /**
     * 导入全量备份数据（含作品、笔记与 6 大高阶资产），单事务级联合入并按内容去重
     * @return Pair(成功导入的新增作品数, 成功导入的笔记数)
     *
     * T2.7：循环前一次性预载「(标题,作者) → 作品 id」索引与全部子表分组，
     * 替代循环内每部作品 7 次查询的 N+1；新插入的数据同步写回内存索引/分组，
     * 与原先「实时查询」的去重语义完全一致。
     */
    fun importFullBackup(items: List<com.example.readtrace.util.BackupHelper.WorkBackup>): Pair<Int, Int> {
        if (items.isEmpty()) return Pair(0, 0)
        val db = writableDatabase
        db.beginTransaction()
        var importedWorks = 0
        var importedNotes = 0
        try {
            val existingBookIds = linkedMapOf<String, Long>()
            getBooksForList().forEach { b ->
                val key = bookIndexKey(b.title, b.author)
                val current = existingBookIds[key]
                if (current == null || b.id < current) existingBookIds[key] = b.id
            }
            val notesByBook = queryWorksTableGroupedByBook(TABLE_NOTES, includeDeleted = false, { it.toNote() }, { it.bookId })
            val sessionsByBook = queryWorksTableGroupedByBook(TABLE_READING_SESSIONS, includeDeleted = false, { it.toReadingSession() }, { it.bookId })
            val charactersByBook = queryWorksTableGroupedByBook(TABLE_BOOK_CHARACTERS, includeDeleted = false, { it.toBookCharacter() }, { it.bookId })
            val outlinesByBook = queryWorksTableGroupedByBook(TABLE_BOOK_OUTLINES, includeDeleted = false, { it.toBookOutline() }, { it.bookId })
            val locationsByBook = queryWorksTableGroupedByBook(TABLE_BOOK_LOCATIONS, includeDeleted = false, { it.toBookLocation() }, { it.bookId })
            val tracksByBook = queryAllAudioTracksGroupedByBook()
            val allMindprints = getAllMindprints().toMutableMap()

            items.forEach { work ->
                val book = work.book
                // 1. 查找是否存在同名且同作者/创作者的作品
                val indexKey = bookIndexKey(book.title, book.author)
                val existingBookId = existingBookIds[indexKey]
                val targetBookId = if (existingBookId != null) {
                    // 全量同步备份中作品的状态与全部详情（状态、评分、长评、短评、标签、起止日期、软删除等）
                    val updateValues = book.toContentValues().apply {
                        if (book.updatedAt.isNotBlank()) put(COLUMN_UPDATED_AT, book.updatedAt)
                        put(COLUMN_IS_DELETED, if (book.isDeleted) 1 else 0)
                        putNullable(COLUMN_DELETED_AT, book.deletedAt ?: if (book.isDeleted) currentTimestamp() else null)
                    }
                    db.update(
                        TABLE_BOOKS,
                        updateValues,
                        "$COLUMN_ID = ?",
                        arrayOf(existingBookId.toString()),
                    )
                    existingBookId
                } else {
                    val values = book.toContentValues().apply {
                        put(COLUMN_CREATED_AT, if (book.createdAt.isNotBlank()) book.createdAt else currentTimestamp())
                        put(COLUMN_UPDATED_AT, if (book.updatedAt.isNotBlank()) book.updatedAt else currentTimestamp())
                        put(COLUMN_IS_DELETED, if (book.isDeleted) 1 else 0)
                        putNullable(COLUMN_DELETED_AT, book.deletedAt)
                    }
                    val newId = db.insert(TABLE_BOOKS, null, values)
                    if (newId > 0) {
                        importedWorks++
                        existingBookIds[indexKey] = newId
                        newId
                    } else null
                }

                if (targetBookId != null) {
                    // 2. 导入关联的笔记（避免重复内容）
                    val existingNotes = notesByBook[targetBookId] ?: mutableListOf()
                    work.notes.forEach { note ->
                        val isDuplicate = existingNotes.any { it.content.trim() == note.content.trim() }
                        if (!isDuplicate && note.content.isNotBlank()) {
                            val noteValues = note.copy(bookId = targetBookId).toContentValues().apply {
                                put(COLUMN_CREATED_AT, if (note.createdAt.isNotBlank()) note.createdAt else currentTimestamp())
                                put(COLUMN_UPDATED_AT, if (note.updatedAt.isNotBlank()) note.updatedAt else currentTimestamp())
                                put(COLUMN_IS_DELETED, if (note.isDeleted) 1 else 0)
                                putNullable(COLUMN_DELETED_AT, note.deletedAt)
                            }
                            if (db.insert(TABLE_NOTES, null, noteValues) > 0) {
                                importedNotes++
                                existingNotes += note.copy(bookId = targetBookId)
                            }
                        }
                    }

                    // 3. 阅读打卡记录（按 创建时间 + 时长 去重）
                    val existingSessions = sessionsByBook[targetBookId] ?: mutableListOf()
                    work.sessions.forEach { session ->
                        val isDuplicate = existingSessions.any {
                            it.createdAt == session.createdAt && it.durationMinutes == session.durationMinutes
                        }
                        if (!isDuplicate && session.durationMinutes > 0) {
                            insertReadingSession(
                                session.copy(bookId = targetBookId, isDeleted = false),
                            )
                            existingSessions += session.copy(bookId = targetBookId, isDeleted = false)
                        }
                    }

                    // 4. 人物角色谱（按姓名去重）
                    val existingCharacters = charactersByBook[targetBookId] ?: mutableListOf()
                    work.characters.forEach { character ->
                        val isDuplicate = existingCharacters.any { it.name.trim() == character.name.trim() }
                        if (!isDuplicate && character.name.isNotBlank()) {
                            insertCharacter(character.copy(bookId = targetBookId, isDeleted = false))
                            existingCharacters += character.copy(bookId = targetBookId, isDeleted = false)
                        }
                    }

                    // 5. 章节大纲（按章节标题去重）
                    val existingOutlines = outlinesByBook[targetBookId] ?: mutableListOf()
                    work.outlines.forEach { outline ->
                        val isDuplicate = existingOutlines.any { it.title.trim() == outline.title.trim() }
                        if (!isDuplicate && outline.title.isNotBlank() && outline.summary.isNotBlank()) {
                            insertOutline(outline.copy(bookId = targetBookId, isDeleted = false))
                            existingOutlines += outline.copy(bookId = targetBookId, isDeleted = false)
                        }
                    }

                    // 6. 空间地标（按名称去重）
                    val existingLocations = locationsByBook[targetBookId] ?: mutableListOf()
                    work.locations.forEach { location ->
                        val isDuplicate = existingLocations.any { it.name.trim() == location.name.trim() }
                        if (!isDuplicate && location.name.isNotBlank()) {
                            insertLocation(location.copy(bookId = targetBookId, isDeleted = false))
                            existingLocations += location.copy(bookId = targetBookId, isDeleted = false)
                        }
                    }

                    // 7. 六维心智模型：仅当备份版本比本地新（或本地没有）时覆盖，
                    // 防止导入旧备份把本地较新的六维评分抹掉（P38-G11）
                    work.mindprint?.let { mindprint ->
                        val incoming = mindprint.copy(bookId = targetBookId)
                        val existingMindprint = allMindprints[targetBookId]
                        val localUpdatedAt = existingMindprint?.updatedAt.orEmpty()
                        val shouldReplace = existingMindprint == null ||
                            (incoming.updatedAt.isNotBlank() && incoming.updatedAt > localUpdatedAt)
                        if (shouldReplace) {
                            saveMindprint(incoming)
                            allMindprints[targetBookId] = incoming
                        }
                    }

                    // 8. 黑胶关联曲目（按 标题 + 序号 去重；跨机恢复时 content:// 指向的本地文件可能失效，播放层已兜底）
                    val existingTracks = tracksByBook[targetBookId] ?: mutableListOf()
                    work.audioTracks.forEach { track ->
                        val isDuplicate = existingTracks.any {
                            it.title.trim() == track.title.trim() && it.trackOrder == track.trackOrder
                        }
                        if (!isDuplicate && track.title.isNotBlank() && track.fileUri.isNotBlank()) {
                            insertAudioTrack(track.copy(bookId = targetBookId))
                            existingTracks += track.copy(bookId = targetBookId)
                        }
                    }
                }
            }
            db.setTransactionSuccessful()
            invalidateBookCache()
            return Pair(importedWorks, importedNotes)
        } finally {
            db.endTransaction()
        }
    }

    /** T2.7：导入去重索引键——与 findBookId 的「trim 后标题 + trim 后作者（null/空等价）」匹配语义一致 */
    private fun bookIndexKey(title: String, author: String?): String =
        "${title.trim()}\u0001${author?.trim().orEmpty()}"

    private fun findBookId(db: SQLiteDatabase, title: String, author: String?): Long? {
        val trimmedTitle = title.trim()
        val trimmedAuthor = author?.trim()
        val selection: String
        val args: Array<String>
        if (trimmedAuthor.isNullOrEmpty()) {
            selection = "$COLUMN_TITLE = ? AND ($COLUMN_AUTHOR IS NULL OR $COLUMN_AUTHOR = '') AND $COLUMN_IS_DELETED = 0"
            args = arrayOf(trimmedTitle)
        } else {
            selection = "$COLUMN_TITLE = ? AND $COLUMN_AUTHOR = ? AND $COLUMN_IS_DELETED = 0"
            args = arrayOf(trimmedTitle, trimmedAuthor)
        }
        return db.query(TABLE_BOOKS, arrayOf(COLUMN_ID), selection, args, null, null, null, "1").use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    /**
     * 获取所有书籍的不重复标签列表及频次统计，按出现频次降序排列
     */
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

    // --- ⏱️ 阅读打卡日志 (Reading Sessions) ---

    fun insertReadingSession(session: ReadingSession): Long {
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_BOOK_ID, session.bookId)
            put(COLUMN_DURATION_MINUTES, session.durationMinutes)
            putNullable(COLUMN_PAGES_READ, session.pagesRead)
            putNullable(COLUMN_THOUGHT, session.thought)
            put(COLUMN_CREATED_AT, session.createdAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
        }
        return writableDatabase.insertOrThrow(TABLE_READING_SESSIONS, null, values)
    }

    fun getReadingSessions(bookId: Long): List<ReadingSession> =
        readableDatabase.query(
            TABLE_READING_SESSIONS,
            null,
            "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(bookId.toString()),
            null,
            null,
            "$COLUMN_CREATED_AT DESC, $COLUMN_ID DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toReadingSession())
                }
            }
        }

    fun getTotalReadingMinutes(bookId: Long): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT SUM($COLUMN_DURATION_MINUTES) FROM $TABLE_READING_SESSIONS WHERE $COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(bookId.toString()),
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    // 一次查询返回全部未删除的阅读记录，供 Widget 等场景批量统计使用，避免逐书 N+1 查询
    fun getAllReadingSessions(): List<ReadingSession> =
        readableDatabase.query(
            TABLE_READING_SESSIONS,
            null,
            "$COLUMN_IS_DELETED = 0",
            null,
            null,
            null,
            "$COLUMN_CREATED_AT DESC, $COLUMN_ID DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toReadingSession())
                }
            }
        }

    fun deleteReadingSession(sessionId: Long): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, currentTimestamp())
        }
        return writableDatabase.update(
            TABLE_READING_SESSIONS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(sessionId.toString()),
        ) > 0
    }

    // --- 👥 人物角色谱 (Book Characters) ---

    fun insertCharacter(character: BookCharacter): Long {
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_BOOK_ID, character.bookId)
            put(COLUMN_NAME, character.name.trim())
            putNullable(COLUMN_ROLE_TITLE, character.roleTitle)
            put(COLUMN_AVATAR_EMOJI, character.avatarEmoji.ifBlank { "👤" })
            putNullable(COLUMN_DESCRIPTION, character.description)
            putNullable(COLUMN_RELATIONSHIP, character.relationship)
            put(COLUMN_CREATED_AT, character.createdAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
        }
        return writableDatabase.insertOrThrow(TABLE_BOOK_CHARACTERS, null, values)
    }

    fun getCharacters(bookId: Long): List<BookCharacter> =
        readableDatabase.query(
            TABLE_BOOK_CHARACTERS,
            null,
            "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(bookId.toString()),
            null,
            null,
            "$COLUMN_ID ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBookCharacter())
                }
            }
        }

    fun deleteCharacter(characterId: Long): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, currentTimestamp())
        }
        return writableDatabase.update(
            TABLE_BOOK_CHARACTERS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(characterId.toString()),
        ) > 0
    }

    // --- 🗺️ 章节大纲与脑图 (Book Outlines) ---

    fun insertOutline(outline: BookOutline): Long {
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_BOOK_ID, outline.bookId)
            put(COLUMN_CHAPTER_ORDER, outline.chapterOrder)
            put(COLUMN_TITLE, outline.title.trim())
            put(COLUMN_SUMMARY, outline.summary.trim())
            putNullable(COLUMN_KEY_TAKEAWAYS, outline.keyTakeaways)
            put(COLUMN_CREATED_AT, outline.createdAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
        }
        return writableDatabase.insertOrThrow(TABLE_BOOK_OUTLINES, null, values)
    }

    fun getOutlines(bookId: Long): List<BookOutline> =
        readableDatabase.query(
            TABLE_BOOK_OUTLINES,
            null,
            "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(bookId.toString()),
            null,
            null,
            "$COLUMN_CHAPTER_ORDER ASC, $COLUMN_ID ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBookOutline())
                }
            }
        }

    fun deleteOutline(outlineId: Long): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, currentTimestamp())
        }
        return writableDatabase.update(
            TABLE_BOOK_OUTLINES,
            values,
            "$COLUMN_ID = ?",
            arrayOf(outlineId.toString()),
        ) > 0
    }

    // --- 🗺️ 空间地标与叙事足迹 (Book Locations) ---

    fun insertLocation(location: BookLocation): Long {
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_BOOK_ID, location.bookId)
            put(COLUMN_NAME, location.name.trim())
            put(COLUMN_LOCATION_TYPE, location.locationType.ifBlank { "🏙️ 现实都市" })
            putNullable(COLUMN_DESCRIPTION, location.description)
            putNullable(COLUMN_SIGNIFICANCE, location.significance)
            putNullable(COLUMN_COORDINATES, location.coordinates)
            put(COLUMN_CREATED_AT, location.createdAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
        }
        return writableDatabase.insertOrThrow(TABLE_BOOK_LOCATIONS, null, values)
    }

    fun getLocations(bookId: Long): List<BookLocation> =
        readableDatabase.query(
            TABLE_BOOK_LOCATIONS,
            null,
            "$COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(bookId.toString()),
            null,
            null,
            "$COLUMN_ID ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBookLocation())
                }
            }
        }

    fun deleteLocation(locationId: Long): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, currentTimestamp())
        }
        return writableDatabase.update(
            TABLE_BOOK_LOCATIONS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(locationId.toString()),
        ) > 0
    }

    // --- 🕸️ 六维心智评分雷达 (Book Mindprints) ---

    fun saveMindprint(mindprint: BookMindprint): Long {
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_BOOK_ID, mindprint.bookId)
            put(COLUMN_DEPTH_SCORE, mindprint.depthScore)
            put(COLUMN_ARTISTRY_SCORE, mindprint.artistryScore)
            put(COLUMN_EMOTION_SCORE, mindprint.emotionScore)
            put(COLUMN_LOGIC_SCORE, mindprint.logicScore)
            put(COLUMN_DIFFICULTY_SCORE, mindprint.difficultyScore)
            put(COLUMN_HEALING_SCORE, mindprint.healingScore)
            put(COLUMN_UPDATED_AT, now)
        }
        return writableDatabase.insertWithOnConflict(
            TABLE_BOOK_MINDPRINTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun getMindprint(bookId: Long): BookMindprint =
        readableDatabase.query(
            TABLE_BOOK_MINDPRINTS,
            null,
            "$COLUMN_BOOK_ID = ?",
            arrayOf(bookId.toString()),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toBookMindprint() else BookMindprint(bookId = bookId)
        }

    fun getAllMindprints(): Map<Long, BookMindprint> {
        val map = mutableMapOf<Long, BookMindprint>()
        runCatching {
            readableDatabase.query(
                TABLE_BOOK_MINDPRINTS,
                null,
                null,
                null,
                null,
                null,
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val mp = cursor.toBookMindprint()
                    map[mp.bookId] = mp
                }
            }
        }
        return map
    }

    fun getAnnualMindprintPersona(): com.example.readtrace.model.ReadingPersona? {
        val finishedBooks = getBooks(BookStatus.FINISHED)
        if (finishedBooks.isEmpty()) return null

        // T2.7 + T2.8：一次取全部心智档案后内存索引（替代逐书查询 N+1）；
        // 无心智档案的作品不计入均值——原逻辑把它们按默认六维（8.0/5.0 假对象）计入，
        // 导致年度人格被系统性拉向默认值。全部无档案时直接返回 null。
        val allMindprints = getAllMindprints()
        val mindprints = finishedBooks.mapNotNull { allMindprints[it.id] }
        if (mindprints.isEmpty()) return null
        val count = mindprints.size.toDouble()

        val avgDepth = mindprints.sumOf { it.depthScore } / count
        val avgArtistry = mindprints.sumOf { it.artistryScore } / count
        val avgEmotion = mindprints.sumOf { it.emotionScore } / count
        val avgLogic = mindprints.sumOf { it.logicScore } / count
        val avgDifficulty = mindprints.sumOf { it.difficultyScore } / count
        val avgHealing = mindprints.sumOf { it.healingScore } / count

        val avgMindprint = com.example.readtrace.model.BookMindprint(
            bookId = 0L,
            depthScore = avgDepth,
            artistryScore = avgArtistry,
            emotionScore = avgEmotion,
            logicScore = avgLogic,
            difficultyScore = avgDifficulty,
            healingScore = avgHealing,
        )

        val dims = listOf(
            "depth" to avgDepth,
            "artistry" to avgArtistry,
            "emotion" to avgEmotion,
            "logic" to avgLogic,
            "difficulty" to avgDifficulty,
            "healing" to avgHealing,
        )
        val maxDim = dims.maxByOrNull { it.second }?.first ?: "depth"

        val (title, desc, dominant) = when (maxDim) {
            "depth" -> Triple("🧠 深邃哲思探索者", "沉醉于对世界本质与生命哲理的深度审视，在思想高原上自由漫步。", "思想深度")
            "artistry" -> Triple("🖋️ 唯美文学审美家", "对文字的韵律美、诗性意境与修辞质感具有极高的审美敏锐度。", "文笔意境")
            "emotion" -> Triple("❤️ 细腻共鸣共情家", "在字里行间捕获最真挚的人性温热，以心感应万千生灵的喜怒哀乐。", "情感共鸣")
            "logic" -> Triple("📐 严密理性格局派", "追求严丝合缝的因果规律与宏大世界构建，崇尚清晰有力的理性推演。", "逻辑构架")
            "difficulty" -> Triple("⛰️ 硬核学术攀登者", "敢于直面深奥晦涩的经典大作与思想峻岭，在攀登中享受智识蜕变。", "思想门槛")
            else -> Triple("🌿 纯粹心灵疗愈者", "在静谧的书海中寻找灵魂的安顿与精神绿洲，温和而坚定地被文字抚慰。", "心灵治愈")
        }

        return com.example.readtrace.model.ReadingPersona(
            personaTitle = title,
            personaDesc = desc,
            dominantDimension = dominant,
            avgMindprint = avgMindprint,
            finishedBooksCount = finishedBooks.size,
        )
    }

    // --- 📱 桌面小组件专享数据支持 (AppWidgets Data Providers) ---

    fun getTodayTotalReadingMinutes(): Int {
        val todayStr = currentTimestamp().substringBefore("T")
        val cursor = readableDatabase.rawQuery(
            """
            SELECT SUM($COLUMN_DURATION_MINUTES) 
            FROM $TABLE_READING_SESSIONS 
            WHERE $COLUMN_CREATED_AT LIKE ? AND $COLUMN_IS_DELETED = 0
            """.trimIndent(),
            arrayOf("$todayStr%"),
        )
        return cursor.use { if (it.moveToFirst() && !it.isNull(0)) it.getInt(0) else 0 }
    }

    fun getConsecutiveReadingDays(): Int {
        val cursor = readableDatabase.rawQuery(
            """
            SELECT DISTINCT substr($COLUMN_CREATED_AT, 1, 10) as session_date 
            FROM $TABLE_READING_SESSIONS 
            WHERE $COLUMN_IS_DELETED = 0 
            ORDER BY session_date DESC
            """.trimIndent(),
            null,
        )
        val dates = cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(it.getString(0))
                }
            }
        }
        if (dates.isEmpty()) return 0

        val today = LocalDate.now()
        var streak = 0
        var checkDate = today

        val latestDateStr = dates.first()
        val latestDate = runCatching { LocalDate.parse(latestDateStr) }.getOrNull() ?: return 0
        if (latestDate != today && latestDate != today.minusDays(1)) {
            return 0
        }
        checkDate = latestDate

        for (dateStr in dates) {
            val d = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: break
            if (d == checkDate) {
                streak++
                checkDate = checkDate.minusDays(1)
            } else if (d < checkDate) {
                break
            }
        }
        return streak
    }

    fun getRandomOrNextQuote(excludeQuote: String? = null): Pair<Book?, String> {
        val quotesCursor = readableDatabase.rawQuery(
            """
            SELECT b.$COLUMN_ID, b.$COLUMN_TITLE, b.$COLUMN_AUTHOR, b.$COLUMN_COVER_URL, b.$COLUMN_MEDIA_TYPE,
                   n.$COLUMN_CONTENT
            FROM $TABLE_NOTES n
            JOIN $TABLE_BOOKS b ON n.$COLUMN_BOOK_ID = b.$COLUMN_ID
            WHERE n.$COLUMN_IS_DELETED = 0 AND b.$COLUMN_IS_DELETED = 0
            ORDER BY RANDOM()
            LIMIT 10
            """.trimIndent(),
            null,
        )
        val quotes = quotesCursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val book = Book(
                        id = c.getLong(0),
                        title = c.getString(1),
                        author = c.getString(2),
                        coverUrl = c.getString(3),
                        mediaType = MediaType.fromDatabaseValue(c.getString(4)),
                        status = BookStatus.READING,
                    )
                    val content = c.getString(5)
                    add(book to content)
                }
            }
        }

        val selected = quotes.firstOrNull { it.second != excludeQuote } ?: quotes.firstOrNull()
        if (selected != null) {
            return selected
        }

        val books = getBooks()
        val commentBooks = books.filter { !it.shortComment.isNullOrBlank() || !it.review.isNullOrBlank() }
        val randomCommentBook = commentBooks.shuffled().firstOrNull()
        if (randomCommentBook != null) {
            val quote = randomCommentBook.shortComment?.takeIf { it.isNotBlank() }
                ?: randomCommentBook.review?.takeIf { it.isNotBlank() }
                ?: "每一道心智印记，都是灵魂与文字的永恒交汇。"
            return randomCommentBook to quote
        }

        val defaultQuotes = listOf(
            "生命中真正重要的不是你遭遇了什么，而是你记住了哪些事，又是如何铭记的。",
            "给岁月以文明，而不是给文明以岁月。",
            "世界上只有一种真正的英雄主义，那就是认清生活的真相后依然热爱生活。",
            "一个人并不是生来要给打败的，你尽可以把他消灭掉，可就是打不败他。",
            "你所热爱的，就是你的生活；你所铭记的，就是你的痕迹。",
        )
        val defaultBook = books.firstOrNull()
        val quote = defaultQuotes.filter { it != excludeQuote }.randomOrNull() ?: defaultQuotes.first()
        return defaultBook to quote
    }

    fun getLatestReadingBook(): Book? {
        val books = getBooks(status = BookStatus.READING)
        if (books.isNotEmpty()) {
            return books.maxByOrNull { it.updatedAt } ?: books.first()
        }
        val allBooks = getBooks()
        return allBooks.firstOrNull()
    }

    fun quickRecordReadingSession(bookId: Long, minutes: Int = 15): Long {
        val session = ReadingSession(
            bookId = bookId,
            durationMinutes = minutes,
            thought = "⚡ 桌面小组件快捷打卡 +${minutes}min",
            createdAt = currentTimestamp(),
        )
        return insertReadingSession(session)
    }

    private fun Cursor.toBook(): Book =
        Book(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            title = getString(getColumnIndexOrThrow(COLUMN_TITLE)),
            author = getNullableString(COLUMN_AUTHOR),
            coverUrl = getNullableString(COLUMN_COVER_URL),
            category = getNullableString(COLUMN_CATEGORY),
            status = BookStatus.fromDatabaseValue(
                getString(getColumnIndexOrThrow(COLUMN_STATUS)),
            ),
            mediaType = MediaType.fromDatabaseValue(
                getNullableString(COLUMN_MEDIA_TYPE),
            ),
            rating = getNullableDouble(COLUMN_RATING),
            tags = parseTags(getNullableString(COLUMN_TAGS)),
            shortComment = getNullableString(COLUMN_SHORT_COMMENT),
            review = getNullableString(COLUMN_REVIEW),
            startDate = getNullableString(COLUMN_START_DATE),
            finishDate = getNullableString(COLUMN_FINISH_DATE),
            buyChannel = getNullableString(COLUMN_BUY_CHANNEL),
            shelfLocation = getNullableString(COLUMN_SHELF_LOCATION),
            bindingType = getNullableString(COLUMN_BINDING_TYPE),
            buyPrice = getNullableDouble(COLUMN_BUY_PRICE),
            createdAt = getString(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
            updatedAt = getString(getColumnIndexOrThrow(COLUMN_UPDATED_AT)),
            isDeleted = getInt(getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1,
            deletedAt = getNullableString(COLUMN_DELETED_AT),
            sourceType = getNullableString(COLUMN_SOURCE_TYPE),
            sourceId = getNullableString(COLUMN_SOURCE_ID),
            remoteRating = getNullableDouble(COLUMN_REMOTE_RATING),
            description = getNullableString(COLUMN_DESCRIPTION),
        )

    private fun Cursor.toNote(): Note =
        Note(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            bookId = getLong(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
            content = getString(getColumnIndexOrThrow(COLUMN_CONTENT)),
            noteType = NoteType.fromDatabaseValue(
                getString(getColumnIndexOrThrow(COLUMN_NOTE_TYPE)),
            ),
            page = getNullableString(COLUMN_PAGE),
            chapter = getNullableString(COLUMN_CHAPTER),
            createdAt = getString(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
            updatedAt = getString(getColumnIndexOrThrow(COLUMN_UPDATED_AT)),
            isDeleted = getInt(getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1,
            deletedAt = getNullableString(COLUMN_DELETED_AT),
        )

    private fun Cursor.toReadingSession(): ReadingSession =
        ReadingSession(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            bookId = getLong(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
            durationMinutes = getInt(getColumnIndexOrThrow(COLUMN_DURATION_MINUTES)),
            pagesRead = getNullableString(COLUMN_PAGES_READ),
            thought = getNullableString(COLUMN_THOUGHT),
            createdAt = getString(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
            isDeleted = getInt(getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1,
        )

    private fun Cursor.toBookCharacter(): BookCharacter =
        BookCharacter(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            bookId = getLong(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
            name = getString(getColumnIndexOrThrow(COLUMN_NAME)),
            roleTitle = getNullableString(COLUMN_ROLE_TITLE),
            avatarEmoji = getNullableString(COLUMN_AVATAR_EMOJI) ?: "👤",
            description = getNullableString(COLUMN_DESCRIPTION),
            relationship = getNullableString(COLUMN_RELATIONSHIP),
            createdAt = getString(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
            isDeleted = getInt(getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1,
        )

    private fun Cursor.toBookOutline(): BookOutline =
        BookOutline(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            bookId = getLong(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
            chapterOrder = getInt(getColumnIndexOrThrow(COLUMN_CHAPTER_ORDER)),
            title = getString(getColumnIndexOrThrow(COLUMN_TITLE)),
            summary = getString(getColumnIndexOrThrow(COLUMN_SUMMARY)),
            keyTakeaways = getNullableString(COLUMN_KEY_TAKEAWAYS),
            createdAt = getString(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
            isDeleted = getInt(getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1,
        )

    private fun Cursor.toBookLocation(): BookLocation =
        BookLocation(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            bookId = getLong(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
            name = getString(getColumnIndexOrThrow(COLUMN_NAME)),
            locationType = getString(getColumnIndexOrThrow(COLUMN_LOCATION_TYPE)),
            description = getNullableString(COLUMN_DESCRIPTION),
            significance = getNullableString(COLUMN_SIGNIFICANCE),
            coordinates = getNullableString(COLUMN_COORDINATES),
            createdAt = getString(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
            isDeleted = getInt(getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1,
        )

    private fun Cursor.toBookMindprint(): BookMindprint =
        BookMindprint(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            bookId = getLong(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
            depthScore = getDouble(getColumnIndexOrThrow(COLUMN_DEPTH_SCORE)),
            artistryScore = getDouble(getColumnIndexOrThrow(COLUMN_ARTISTRY_SCORE)),
            emotionScore = getDouble(getColumnIndexOrThrow(COLUMN_EMOTION_SCORE)),
            logicScore = getDouble(getColumnIndexOrThrow(COLUMN_LOGIC_SCORE)),
            difficultyScore = getDouble(getColumnIndexOrThrow(COLUMN_DIFFICULTY_SCORE)),
            healingScore = getDouble(getColumnIndexOrThrow(COLUMN_HEALING_SCORE)),
            updatedAt = getString(getColumnIndexOrThrow(COLUMN_UPDATED_AT)),
        )

    private fun Cursor.getNullableString(columnName: String): String? {
        // T2.1：容忍缺失列——轻量列表投影（getBooksForList）不含 description/review 长文本，
        // 缺列即视为 null，与 Book 模型的可空语义一致
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.getNullableDouble(columnName: String): Double? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getDouble(index)
    }

    private fun parseTags(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index)
                        .trim()
                        .takeIf { it.isNotEmpty() }
                        ?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun ContentValues.putNullable(key: String, value: String?) {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized == null) putNull(key) else put(key, normalized)
    }

    private fun Book.toContentValues(): ContentValues =
        ContentValues().apply {
            put(COLUMN_TITLE, title.trim())
            putNullable(COLUMN_AUTHOR, author)
            putNullable(COLUMN_COVER_URL, coverUrl)
            putNullable(COLUMN_CATEGORY, category)
            put(COLUMN_STATUS, status.databaseValue)
            put(COLUMN_MEDIA_TYPE, mediaType.databaseValue)
            if (rating == null) putNull(COLUMN_RATING) else put(COLUMN_RATING, rating)
            put(COLUMN_TAGS, JSONArray(tags).toString())
            putNullable(COLUMN_SHORT_COMMENT, shortComment)
            putNullable(COLUMN_REVIEW, review)
            putNullable(COLUMN_START_DATE, startDate)
            putNullable(COLUMN_FINISH_DATE, finishDate)
            putNullable(COLUMN_BUY_CHANNEL, buyChannel)
            putNullable(COLUMN_SHELF_LOCATION, shelfLocation)
            putNullable(COLUMN_BINDING_TYPE, bindingType)
            if (buyPrice == null) putNull(COLUMN_BUY_PRICE) else put(COLUMN_BUY_PRICE, buyPrice)
            putNullable(COLUMN_SOURCE_TYPE, sourceType)
            putNullable(COLUMN_SOURCE_ID, sourceId)
            if (remoteRating == null) putNull(COLUMN_REMOTE_RATING) else put(COLUMN_REMOTE_RATING, remoteRating)
            putNullable(COLUMN_DESCRIPTION, description)
        }

    private fun Note.toContentValues(): ContentValues =
        ContentValues().apply {
            put(COLUMN_BOOK_ID, bookId)
            put(COLUMN_CONTENT, content.trim())
            put(COLUMN_NOTE_TYPE, noteType.databaseValue)
            putNullable(COLUMN_PAGE, page)
            putNullable(COLUMN_CHAPTER, chapter)
        }

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
