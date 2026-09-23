package com.example.readtrace.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookCharacter
import com.example.readtrace.model.BookLocation
import com.example.readtrace.model.BookOutline
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.model.MonthlyReadingStat
import com.example.readtrace.model.ReadingSession
import com.example.readtrace.util.CoverImageHelper
import org.json.JSONArray
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * books 表与 book 附属记录（阅读时长/人物/大纲/地点）的数据访问层
 * （P40 Phase 4 阶段 3 自 BookDatabaseHelper 抽出）。
 * 纯函数 + 显式传 db；books 内存缓存与缓存失效仍留在 helper 委托层。
 */
object BookDao {

    private fun currentTimestamp(): String = BookDatabaseHelper.currentTimestamp()

    fun insertBook(db: SQLiteDatabase, book: Book): Long {
        val now = currentTimestamp()
        val values = book.toContentValues().apply {
            put(COLUMN_CREATED_AT, book.createdAt.ifBlank { now })
            put(COLUMN_UPDATED_AT, book.updatedAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
            putNull(COLUMN_DELETED_AT)
        }
        val newId = db.insertOrThrow(TABLE_BOOKS, null, values)
        return newId
    }

    /** 外部导入批量写入：单事务 + 预编译 SQLiteStatement 极速落盘 + 末尾一次缓存失效 */
    fun insertBooksBatch(db: SQLiteDatabase, books: List<Book>): Int {
        if (books.isEmpty()) return 0
        val now = currentTimestamp()
        val db = db
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
        return books.size
    }

    /** 外部导入单批批量查重：一次性查出已入库 source_id 集合（含已删除作品，防回收站复活），杜绝 N 次循环查库 */
    fun findExistingSourceIds(db: SQLiteDatabase, sourceType: String, sourceIds: Collection<String>): Set<String> {
        if (sourceIds.isEmpty()) return emptySet()
        val found = mutableSetOf<String>()
        val idList = sourceIds.toList()
        val chunkSize = 400
        val db = db
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
    fun findBookBySource(db: SQLiteDatabase, sourceType: String, sourceId: String): Book? =
        db.query(
            TABLE_BOOKS,
            null,
            "$COLUMN_SOURCE_TYPE = ? AND $COLUMN_SOURCE_ID = ?",
            arrayOf(sourceType, sourceId),
            null, null, null, "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toBook() else null
        }

    /** 速记入库查重（P38-G7）：同来源精确命中，或同媒介同名（忽略 ASCII 大小写）的未删除作品；回收站作品不拦截重录 */
    fun findQuickLogDuplicate(db: SQLiteDatabase, 
        title: String,
        mediaType: MediaType,
        sourceType: String?,
        sourceId: String?,
    ): Book? {
        if (!sourceType.isNullOrBlank() && !sourceId.isNullOrBlank()) {
            db.query(
                TABLE_BOOKS,
                null,
                "$COLUMN_IS_DELETED = ? AND $COLUMN_SOURCE_TYPE = ? AND $COLUMN_SOURCE_ID = ?",
                arrayOf("0", sourceType, sourceId),
                null, null, null, "1",
            ).use { cursor ->
                if (cursor.moveToFirst()) return cursor.toBook()
            }
        }
        return db.query(
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
    fun findBooksByTitleLike(db: SQLiteDatabase, title: String, mediaType: MediaType): List<Book> {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return emptyList()
        return db.query(
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

    fun getBooks(db: SQLiteDatabase, status: BookStatus? = null): List<Book> {
        val selectionParts = mutableListOf("$COLUMN_IS_DELETED = ?")
        val selectionArgs = mutableListOf("0")
        if (status != null) {
            selectionParts += "$COLUMN_STATUS = ?"
            selectionArgs += status.databaseValue
        }

        return db.query(
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
    fun getBooksForList(db: SQLiteDatabase, status: BookStatus? = null): List<Book> {
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

        return db.query(
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

    fun getBook(db: SQLiteDatabase, bookId: Long): Book? =
        db.query(
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

    fun updateBook(db: SQLiteDatabase, book: Book): Boolean {
        if (book.id <= 0) return false
        val values = book.toContentValues().apply {
            put(COLUMN_UPDATED_AT, currentTimestamp())
        }
        val rows = db.update(
            TABLE_BOOKS,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(book.id.toString(), "0"),
        )
        return rows > 0
    }

    fun archiveBook(db: SQLiteDatabase, bookId: Long): Boolean {
        if (bookId <= 0) return false
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, now)
            put(COLUMN_UPDATED_AT, now)
        }
        val rows = db.update(
            TABLE_BOOKS,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(bookId.toString(), "0"),
        )
        return rows > 0
    }

    fun getAudioTracks(db: SQLiteDatabase, bookId: Long): List<com.example.readtrace.model.AudioTrackItem> {
        val tracks = mutableListOf<com.example.readtrace.model.AudioTrackItem>()
        db.query(
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

    fun insertAudioTrack(db: SQLiteDatabase, track: com.example.readtrace.model.AudioTrackItem): Long {
        return db.insert(
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

    fun updateAudioTrackDuration(db: SQLiteDatabase, trackId: Long, durationMs: Long) {
        db.update(
            TABLE_AUDIO_TRACKS,
            android.content.ContentValues().apply { put(COLUMN_AUDIO_DURATION, durationMs) },
            "$COLUMN_ID = ?",
            arrayOf(trackId.toString()),
        )
    }

    fun deleteAudioTrack(db: SQLiteDatabase, trackId: Long) {
        db.delete(TABLE_AUDIO_TRACKS, "$COLUMN_ID = ?", arrayOf(trackId.toString()))
    }

    fun deleteAudioTracksOfBook(db: SQLiteDatabase, bookId: Long) {
        db.delete(TABLE_AUDIO_TRACKS, "$COLUMN_AUDIO_BOOK_ID = ?", arrayOf(bookId.toString()))
    }

    /**
     * 恢复已归档的书籍
     */
    fun restoreBook(db: SQLiteDatabase, bookId: Long): Boolean {
        if (bookId <= 0) return false
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 0)
            putNull(COLUMN_DELETED_AT)
            put(COLUMN_UPDATED_AT, now)
        }
        val rows = db.update(
            TABLE_BOOKS,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(bookId.toString(), "1"),
        )
        return rows > 0
    }

    /**
     * 获取所有已归档的书籍（回收站）
     */
    fun getArchivedBooks(db: SQLiteDatabase, ): List<Book> {
        return db.query(
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
    fun hardDeleteBook(db: SQLiteDatabase, bookId: Long): Boolean {
        if (bookId <= 0) return false
        val db = db
        db.beginTransaction()
        try {
            // 1. 查询并清理关联的本地封面文件
            val book = getBookAny(db, bookId)
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
            return deleted
        } finally {
            db.endTransaction()
        }
    }

    internal fun getBookAny(db: SQLiteDatabase, bookId: Long): Book? =
        db.query(
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
    fun importParsedRecords(db: SQLiteDatabase, records: List<com.example.readtrace.util.BookCsvParser.ParsedBookRecord>): Int {
        if (records.isEmpty()) return 0
        val db = db
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
        return affectedCount
    }

    /**
     * 批量导入书籍列表（用于旧版兼容与快速文本导入）。
     */
    fun importBooks(db: SQLiteDatabase, books: List<Book>): Int {
        if (books.isEmpty()) return 0
        val records = books.map { com.example.readtrace.util.BookCsvParser.ParsedBookRecord(it, null) }
        return importParsedRecords(db, records)
    }

    internal fun isBookExists(db: SQLiteDatabase, title: String, author: String?): Boolean {
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
    fun getMemoryBook(db: SQLiteDatabase, ): Pair<Book, String>? {
        val today = LocalDate.now()
        val monthDayPattern = String.format("%%-%02d-%02d", today.monthValue, today.dayOfMonth)

        // 1. 查询历史同月同日完成的作品（非当年今天）
        val todayBooks = db.query(
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
        val finishedBooks = db.query(
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
    fun getMonthlyFinishedStats(db: SQLiteDatabase, limit: Int = 6): List<MonthlyReadingStat> {
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

        return db.rawQuery(sql, arrayOf(limit.toString())).use { cursor ->
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

    internal fun bookIndexKey(title: String, author: String?): String =
        "${title.trim()}\u0001${author?.trim().orEmpty()}"

    internal fun findBookId(db: SQLiteDatabase, title: String, author: String?): Long? {
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

    fun insertReadingSession(db: SQLiteDatabase, session: ReadingSession): Long {
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_BOOK_ID, session.bookId)
            put(COLUMN_DURATION_MINUTES, session.durationMinutes)
            putNullable(COLUMN_PAGES_READ, session.pagesRead)
            putNullable(COLUMN_THOUGHT, session.thought)
            put(COLUMN_CREATED_AT, session.createdAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
        }
        return db.insertOrThrow(TABLE_READING_SESSIONS, null, values)
    }

    fun getReadingSessions(db: SQLiteDatabase, bookId: Long): List<ReadingSession> =
        db.query(
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

    fun getTotalReadingMinutes(db: SQLiteDatabase, bookId: Long): Int {
        val cursor = db.rawQuery(
            "SELECT SUM($COLUMN_DURATION_MINUTES) FROM $TABLE_READING_SESSIONS WHERE $COLUMN_BOOK_ID = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(bookId.toString()),
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    // 一次查询返回全部未删除的阅读记录，供 Widget 等场景批量统计使用，避免逐书 N+1 查询
    fun getAllReadingSessions(db: SQLiteDatabase, ): List<ReadingSession> =
        db.query(
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

    fun deleteReadingSession(db: SQLiteDatabase, sessionId: Long): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, currentTimestamp())
        }
        return db.update(
            TABLE_READING_SESSIONS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(sessionId.toString()),
        ) > 0
    }

    // --- 👥 人物角色谱 (Book Characters) ---

    fun insertCharacter(db: SQLiteDatabase, character: BookCharacter): Long {
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
        return db.insertOrThrow(TABLE_BOOK_CHARACTERS, null, values)
    }

    fun getCharacters(db: SQLiteDatabase, bookId: Long): List<BookCharacter> =
        db.query(
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

    fun deleteCharacter(db: SQLiteDatabase, characterId: Long): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, currentTimestamp())
        }
        return db.update(
            TABLE_BOOK_CHARACTERS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(characterId.toString()),
        ) > 0
    }

    // --- 🗺️ 章节大纲与脑图 (Book Outlines) ---

    fun insertOutline(db: SQLiteDatabase, outline: BookOutline): Long {
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
        return db.insertOrThrow(TABLE_BOOK_OUTLINES, null, values)
    }

    fun getOutlines(db: SQLiteDatabase, bookId: Long): List<BookOutline> =
        db.query(
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

    fun deleteOutline(db: SQLiteDatabase, outlineId: Long): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, currentTimestamp())
        }
        return db.update(
            TABLE_BOOK_OUTLINES,
            values,
            "$COLUMN_ID = ?",
            arrayOf(outlineId.toString()),
        ) > 0
    }

    // --- 🗺️ 空间地标与叙事足迹 (Book Locations) ---

    fun insertLocation(db: SQLiteDatabase, location: BookLocation): Long {
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
        return db.insertOrThrow(TABLE_BOOK_LOCATIONS, null, values)
    }

    fun getLocations(db: SQLiteDatabase, bookId: Long): List<BookLocation> =
        db.query(
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

    fun deleteLocation(db: SQLiteDatabase, locationId: Long): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, currentTimestamp())
        }
        return db.update(
            TABLE_BOOK_LOCATIONS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(locationId.toString()),
        ) > 0
    }

    // --- 🕸️ 六维心智评分雷达 (Book Mindprints) ---

}

internal fun Cursor.toBook(): Book =
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

internal fun Cursor.toReadingSession(): ReadingSession =
    ReadingSession(
        id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
        bookId = getLong(getColumnIndexOrThrow(COLUMN_BOOK_ID)),
        durationMinutes = getInt(getColumnIndexOrThrow(COLUMN_DURATION_MINUTES)),
        pagesRead = getNullableString(COLUMN_PAGES_READ),
        thought = getNullableString(COLUMN_THOUGHT),
        createdAt = getString(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
        isDeleted = getInt(getColumnIndexOrThrow(COLUMN_IS_DELETED)) == 1,
    )

internal fun Cursor.toBookCharacter(): BookCharacter =
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

internal fun Cursor.toBookOutline(): BookOutline =
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

internal fun Cursor.toBookLocation(): BookLocation =
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

internal fun Cursor.getNullableString(columnName: String): String? {
    // T2.1：容忍缺失列——轻量列表投影（getBooksForList）不含 description/review 长文本，
    // 缺列即视为 null，与 Book 模型的可空语义一致
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

internal fun Cursor.getNullableDouble(columnName: String): Double? {
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

internal fun ContentValues.putNullable(key: String, value: String?) {
    val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
    if (normalized == null) putNull(key) else put(key, normalized)
}

internal fun Book.toContentValues(): ContentValues =
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

