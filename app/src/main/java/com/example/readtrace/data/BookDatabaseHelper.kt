package com.example.readtrace.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class BookDatabaseHelper(val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE $TABLE_BOOKS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_AUTHOR TEXT,
                $COLUMN_COVER_URL TEXT,
                $COLUMN_CATEGORY TEXT,
                $COLUMN_STATUS TEXT NOT NULL DEFAULT 'wishlist',
                $COLUMN_MEDIA_TYPE TEXT NOT NULL DEFAULT 'book',
                $COLUMN_RATING REAL,
                $COLUMN_TAGS TEXT NOT NULL DEFAULT '[]',
                $COLUMN_SHORT_COMMENT TEXT,
                $COLUMN_REVIEW TEXT,
                $COLUMN_START_DATE TEXT,
                $COLUMN_FINISH_DATE TEXT,
                $COLUMN_BUY_CHANNEL TEXT,
                $COLUMN_SHELF_LOCATION TEXT,
                $COLUMN_BINDING_TYPE TEXT,
                $COLUMN_BUY_PRICE REAL,
                $COLUMN_CREATED_AT TEXT NOT NULL,
                $COLUMN_UPDATED_AT TEXT NOT NULL,
                $COLUMN_IS_DELETED INTEGER NOT NULL DEFAULT 0,
                $COLUMN_DELETED_AT TEXT
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX index_books_status_deleted ON $TABLE_BOOKS " +
                "($COLUMN_STATUS, $COLUMN_IS_DELETED)",
        )
        createNotesTable(database)
        createReadingSessionsTable(database)
        createCharactersTable(database)
        createOutlinesTable(database)
        createLocationsTable(database)
        createMindprintsTable(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v1.1：新增 notes 表，已有 books 数据保持不变。
            createNotesTable(database)
        }
        if (oldVersion < 3) {
            // v1.5：新增 media_type 字段，已有书籍平滑迁移为默认 'book' 类型。
            database.execSQL(
                "ALTER TABLE $TABLE_BOOKS ADD COLUMN $COLUMN_MEDIA_TYPE TEXT NOT NULL DEFAULT 'book'",
            )
        }
        if (oldVersion < 4) {
            // v3.1：增加实体馆藏字段与阅读打卡/角色谱/大纲脑图表
            runCatching { database.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COLUMN_BUY_CHANNEL TEXT") }
            runCatching { database.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COLUMN_SHELF_LOCATION TEXT") }
            runCatching { database.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COLUMN_BINDING_TYPE TEXT") }
            runCatching { database.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COLUMN_BUY_PRICE REAL") }
            createReadingSessionsTable(database)
            createCharactersTable(database)
            createOutlinesTable(database)
        }
        if (oldVersion < 5) {
            // v3.2：增加空间地标足迹表与六维心智评分表
            createLocationsTable(database)
            createMindprintsTable(database)
        }
    }

    private fun createNotesTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NOTES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BOOK_ID INTEGER NOT NULL,
                $COLUMN_CONTENT TEXT NOT NULL,
                $COLUMN_NOTE_TYPE TEXT NOT NULL DEFAULT 'note',
                $COLUMN_PAGE TEXT,
                $COLUMN_CHAPTER TEXT,
                $COLUMN_CREATED_AT TEXT NOT NULL,
                $COLUMN_UPDATED_AT TEXT NOT NULL,
                $COLUMN_IS_DELETED INTEGER NOT NULL DEFAULT 0,
                $COLUMN_DELETED_AT TEXT,
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS($COLUMN_ID)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_notes_book_deleted ON $TABLE_NOTES " +
                "($COLUMN_BOOK_ID, $COLUMN_IS_DELETED)",
        )
    }

    private fun createReadingSessionsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_READING_SESSIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BOOK_ID INTEGER NOT NULL,
                $COLUMN_DURATION_MINUTES INTEGER NOT NULL,
                $COLUMN_PAGES_READ TEXT,
                $COLUMN_THOUGHT TEXT,
                $COLUMN_CREATED_AT TEXT NOT NULL,
                $COLUMN_IS_DELETED INTEGER NOT NULL DEFAULT 0,
                $COLUMN_DELETED_AT TEXT,
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS($COLUMN_ID)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sessions_book_deleted ON $TABLE_READING_SESSIONS " +
                "($COLUMN_BOOK_ID, $COLUMN_IS_DELETED)",
        )
    }

    private fun createCharactersTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_BOOK_CHARACTERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BOOK_ID INTEGER NOT NULL,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_ROLE_TITLE TEXT,
                $COLUMN_AVATAR_EMOJI TEXT NOT NULL DEFAULT '👤',
                $COLUMN_DESCRIPTION TEXT,
                $COLUMN_RELATIONSHIP TEXT,
                $COLUMN_CREATED_AT TEXT NOT NULL,
                $COLUMN_IS_DELETED INTEGER NOT NULL DEFAULT 0,
                $COLUMN_DELETED_AT TEXT,
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS($COLUMN_ID)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_chars_book_deleted ON $TABLE_BOOK_CHARACTERS " +
                "($COLUMN_BOOK_ID, $COLUMN_IS_DELETED)",
        )
    }

    private fun createOutlinesTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_BOOK_OUTLINES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BOOK_ID INTEGER NOT NULL,
                $COLUMN_CHAPTER_ORDER INTEGER NOT NULL DEFAULT 1,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_SUMMARY TEXT NOT NULL,
                $COLUMN_KEY_TAKEAWAYS TEXT,
                $COLUMN_CREATED_AT TEXT NOT NULL,
                $COLUMN_IS_DELETED INTEGER NOT NULL DEFAULT 0,
                $COLUMN_DELETED_AT TEXT,
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS($COLUMN_ID)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_outlines_book_deleted ON $TABLE_BOOK_OUTLINES " +
                "($COLUMN_BOOK_ID, $COLUMN_IS_DELETED)",
        )
    }

    private fun createLocationsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_BOOK_LOCATIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BOOK_ID INTEGER NOT NULL,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_LOCATION_TYPE TEXT NOT NULL DEFAULT '🏙️ 现实都市',
                $COLUMN_DESCRIPTION TEXT,
                $COLUMN_SIGNIFICANCE TEXT,
                $COLUMN_COORDINATES TEXT,
                $COLUMN_CREATED_AT TEXT NOT NULL,
                $COLUMN_IS_DELETED INTEGER NOT NULL DEFAULT 0,
                $COLUMN_DELETED_AT TEXT,
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS($COLUMN_ID)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_locations_book_deleted ON $TABLE_BOOK_LOCATIONS " +
                "($COLUMN_BOOK_ID, $COLUMN_IS_DELETED)",
        )
    }

    private fun createMindprintsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_BOOK_MINDPRINTS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BOOK_ID INTEGER NOT NULL UNIQUE,
                $COLUMN_DEPTH_SCORE REAL NOT NULL DEFAULT 8.0,
                $COLUMN_ARTISTRY_SCORE REAL NOT NULL DEFAULT 8.0,
                $COLUMN_EMOTION_SCORE REAL NOT NULL DEFAULT 8.0,
                $COLUMN_LOGIC_SCORE REAL NOT NULL DEFAULT 8.0,
                $COLUMN_DIFFICULTY_SCORE REAL NOT NULL DEFAULT 5.0,
                $COLUMN_HEALING_SCORE REAL NOT NULL DEFAULT 8.0,
                $COLUMN_UPDATED_AT TEXT NOT NULL,
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS($COLUMN_ID)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_mindprints_book ON $TABLE_BOOK_MINDPRINTS ($COLUMN_BOOK_ID)",
        )
    }

    fun insertBook(book: Book): Long {
        val now = currentTimestamp()
        val values = book.toContentValues().apply {
            put(COLUMN_CREATED_AT, book.createdAt.ifBlank { now })
            put(COLUMN_UPDATED_AT, book.updatedAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
            putNull(COLUMN_DELETED_AT)
        }
        return writableDatabase.insertOrThrow(TABLE_BOOKS, null, values)
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
        return writableDatabase.update(
            TABLE_BOOKS,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(book.id.toString(), "0"),
        ) > 0
    }

    fun archiveBook(bookId: Long): Boolean {
        if (bookId <= 0) return false
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, now)
            put(COLUMN_UPDATED_AT, now)
        }
        return writableDatabase.update(
            TABLE_BOOKS,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(bookId.toString(), "0"),
        ) > 0
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
        return writableDatabase.update(
            TABLE_BOOKS,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(bookId.toString(), "1"),
        ) > 0
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

            // 2. 删除关联的笔记
            db.delete(TABLE_NOTES, "$COLUMN_BOOK_ID = ?", arrayOf(bookId.toString()))

            // 3. 删除书籍记录
            val deleted = db.delete(TABLE_BOOKS, "$COLUMN_ID = ?", arrayOf(bookId.toString())) > 0
            db.setTransactionSuccessful()
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
     * 批量导入书籍，利用事务提升性能，并检查标题和作者去重
     * @return 实际成功插入的新书籍数量
     */
    fun importBooks(books: List<Book>): Int {
        if (books.isEmpty()) return 0
        val db = writableDatabase
        var insertedCount = 0
        db.beginTransaction()
        try {
            val now = currentTimestamp()
            for (book in books) {
                val cleanTitle = book.title.trim()
                if (cleanTitle.isEmpty()) continue

                // 查重：同名且同作者（未归档）则跳过
                val exists = isBookExists(db, cleanTitle, book.author?.trim())
                if (!exists) {
                    val values = book.toContentValues().apply {
                        put(COLUMN_TITLE, cleanTitle)
                        put(COLUMN_CREATED_AT, book.createdAt.ifBlank { now })
                        put(COLUMN_UPDATED_AT, book.updatedAt.ifBlank { now })
                        put(COLUMN_IS_DELETED, 0)
                        putNull(COLUMN_DELETED_AT)
                    }
                    val rowId = db.insert(TABLE_BOOKS, null, values)
                    if (rowId > 0) {
                        insertedCount++
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return insertedCount
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
     * 获取「那年今日」回忆书籍与描述信息。
     * 优先匹配历史年份今日读完的书籍，次之随机精选一本已读书籍作为时光漫忆。
     */
    fun getMemoryBook(): Pair<Book, String>? {
        val today = LocalDate.now()
        val monthDayPattern = String.format("%%-%02d-%02d", today.monthValue, today.dayOfMonth)

        // 1. 查询历史同月同日读完的书籍（非当年今天）
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

        // 寻找非今天（往年）读完的书
        for (book in todayBooks) {
            val finishDateStr = book.finishDate ?: continue
            val finishDate = runCatching { LocalDate.parse(finishDateStr) }.getOrNull() ?: continue
            val years = ChronoUnit.YEARS.between(finishDate, today)
            if (years > 0) {
                return Pair(book, "${years} 年前的今天，你读完了这本书")
            } else if (finishDate == today) {
                return Pair(book, "今天读完的书籍，愿余味长存")
            }
        }

        // 2. 如果无今日匹配，则优选一本最近或评分较高的已读书籍作为时光漫忆
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
            return Pair(randomBook, "时光漫忆 · 曾留在心里的作品")
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
        return writableDatabase.insertOrThrow(TABLE_NOTES, null, values)
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
        return writableDatabase.update(
            TABLE_NOTES,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(note.id.toString(), "0"),
        ) > 0
    }

    fun archiveNote(noteId: Long): Boolean {
        if (noteId <= 0) return false
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, now)
            put(COLUMN_UPDATED_AT, now)
        }
        return writableDatabase.update(
            TABLE_NOTES,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(noteId.toString(), "0"),
        ) > 0
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
        return writableDatabase.update(
            TABLE_NOTES,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(noteId.toString(), "1"),
        ) > 0
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

            // 2. 物理删除所有归档书籍关联的笔记及单独归档的笔记
            val deletedNotesCount = db.delete(TABLE_NOTES, "$COLUMN_IS_DELETED = 1", null)
            val deletedBooksCount = db.delete(TABLE_BOOKS, "$COLUMN_IS_DELETED = 1", null)

            db.setTransactionSuccessful()
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
     * 导入全量备份数据（含作品与笔记）
     * @return Pair(成功导入的新增作品数, 成功导入的笔记数)
     */
    fun importFullBackup(items: List<Pair<Book, List<Note>>>): Pair<Int, Int> {
        if (items.isEmpty()) return Pair(0, 0)
        val db = writableDatabase
        db.beginTransaction()
        var importedWorks = 0
        var importedNotes = 0
        try {
            items.forEach { (book, notes) ->
                // 1. 查找是否存在同名且同作者/创作者的作品
                val existingBookId = findBookId(db, book.title, book.author)
                val targetBookId = if (existingBookId != null) {
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
                        newId
                    } else null
                }

                if (targetBookId != null) {
                    // 2. 导入关联的笔记（避免重复内容）
                    val existingNotes = getNotes(targetBookId)
                    notes.forEach { note ->
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
                            }
                        }
                    }
                }
            }
            db.setTransactionSuccessful()
            return Pair(importedWorks, importedNotes)
        } finally {
            db.endTransaction()
        }
    }

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
     * 获取 3D 私人展厅陈列精选作品（优先高分 rating >= 8.0 或 已完成 finished，若不足则补充最新收录，上限 24 部）
     */
    fun getGalleryFeaturedWorks(limit: Int = 24): List<Book> {
        val allBooks = getBooks()
        if (allBooks.isEmpty()) return emptyList()

        // 优先筛选高分 (>= 8.0) 或已读完作品
        val featured = allBooks.filter {
            (it.rating != null && it.rating >= 8.0) || it.status == BookStatus.FINISHED
        }.sortedWith(
            compareByDescending<Book> { it.rating ?: 0.0 }
                .thenByDescending { it.updatedAt },
        )

        if (featured.size >= limit) {
            return featured.take(limit)
        }

        // 如果不足 limit 部，则从其余作品中按最新更新时间补充
        val remaining = allBooks.filterNot { featured.contains(it) }
            .sortedByDescending { it.updatedAt }

        return (featured + remaining).take(limit)
    }

    /**
     * 获取所有书籍的不重复标签列表及频次统计，按出现频次降序排列
     */
    fun getAllUniqueTags(): List<Pair<String, Int>> {
        val tagCountMap = mutableMapOf<String, Int>()
        val books = getBooks()
        books.forEach { book ->
            book.tags.forEach { tag ->
                val clean = tag.trim()
                if (clean.isNotEmpty()) {
                    tagCountMap[clean] = (tagCountMap[clean] ?: 0) + 1
                }
            }
        }
        return tagCountMap.toList().sortedByDescending { it.second }
    }

    /**
     * 获取书籍上次阅读的页码
     */
    fun getReadingPage(bookId: Long): Int {
        val sp = context.getSharedPreferences("readtrace_reader_prefs", Context.MODE_PRIVATE)
        return sp.getInt("book_page_$bookId", 0)
    }

    /**
     * 保存书籍当前阅读页码
     */
    fun saveReadingPage(bookId: Long, pageIndex: Int) {
        val sp = context.getSharedPreferences("readtrace_reader_prefs", Context.MODE_PRIVATE)
        sp.edit().putInt("book_page_$bookId", pageIndex).apply()
    }

    /**
     * 获取已读完书籍总数
     */
    fun getTotalFinishedBooksCount(): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_BOOKS WHERE $COLUMN_STATUS = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(BookStatus.FINISHED.databaseValue),
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /**
     * 获取有效书籍总数
     */
    fun getTotalBooksCount(): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_BOOKS WHERE $COLUMN_IS_DELETED = 0",
            null,
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /**
     * 获取有效笔记总数
     */
    fun getTotalNotesCount(): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_NOTES WHERE $COLUMN_IS_DELETED = 0",
            null,
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /**
     * 获取不同书籍分类总数
     */
    fun getUniqueCategoriesCount(): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT $COLUMN_CATEGORY) FROM $TABLE_BOOKS WHERE $COLUMN_CATEGORY IS NOT NULL AND TRIM($COLUMN_CATEGORY) != '' AND $COLUMN_IS_DELETED = 0",
            null,
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /**
     * 获取 9.0 分及以上的高分好评书籍数量
     */
    fun getHighRatingBooksCount(): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_BOOKS WHERE $COLUMN_RATING >= 9.0 AND $COLUMN_IS_DELETED = 0",
            null,
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

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
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getString(index)
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
        }

    private fun Note.toContentValues(): ContentValues =
        ContentValues().apply {
            put(COLUMN_BOOK_ID, bookId)
            put(COLUMN_CONTENT, content.trim())
            put(COLUMN_NOTE_TYPE, noteType.databaseValue)
            putNullable(COLUMN_PAGE, page)
            putNullable(COLUMN_CHAPTER, chapter)
        }

    private fun currentTimestamp(): String =
        OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    companion object {
        const val DATABASE_NAME = "readtrace.db"
        const val DATABASE_VERSION = 5

        private const val TABLE_BOOKS = "books"
        private const val TABLE_NOTES = "notes"
        private const val TABLE_READING_SESSIONS = "reading_sessions"
        private const val TABLE_BOOK_CHARACTERS = "book_characters"
        private const val TABLE_BOOK_OUTLINES = "book_outlines"
        private const val TABLE_BOOK_LOCATIONS = "book_locations"
        private const val TABLE_BOOK_MINDPRINTS = "book_mindprints"

        private const val COLUMN_ID = "id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_AUTHOR = "author"
        private const val COLUMN_COVER_URL = "cover_url"
        private const val COLUMN_CATEGORY = "category"
        private const val COLUMN_STATUS = "status"
        private const val COLUMN_MEDIA_TYPE = "media_type"
        private const val COLUMN_RATING = "rating"
        private const val COLUMN_TAGS = "tags"
        private const val COLUMN_SHORT_COMMENT = "short_comment"
        private const val COLUMN_REVIEW = "review"
        private const val COLUMN_START_DATE = "start_date"
        private const val COLUMN_FINISH_DATE = "finish_date"
        private const val COLUMN_BUY_CHANNEL = "buy_channel"
        private const val COLUMN_SHELF_LOCATION = "shelf_location"
        private const val COLUMN_BINDING_TYPE = "binding_type"
        private const val COLUMN_BUY_PRICE = "buy_price"
        private const val COLUMN_CREATED_AT = "created_at"
        private const val COLUMN_UPDATED_AT = "updated_at"
        private const val COLUMN_IS_DELETED = "is_deleted"
        private const val COLUMN_DELETED_AT = "deleted_at"

        private const val COLUMN_BOOK_ID = "book_id"
        private const val COLUMN_CONTENT = "content"
        private const val COLUMN_NOTE_TYPE = "note_type"
        private const val COLUMN_PAGE = "page"
        private const val COLUMN_CHAPTER = "chapter"

        private const val COLUMN_DURATION_MINUTES = "duration_minutes"
        private const val COLUMN_PAGES_READ = "pages_read"
        private const val COLUMN_THOUGHT = "thought"

        private const val COLUMN_NAME = "name"
        private const val COLUMN_ROLE_TITLE = "role_title"
        private const val COLUMN_AVATAR_EMOJI = "avatar_emoji"
        private const val COLUMN_DESCRIPTION = "description"
        private const val COLUMN_RELATIONSHIP = "relationship"

        private const val COLUMN_CHAPTER_ORDER = "chapter_order"
        private const val COLUMN_SUMMARY = "summary"
        private const val COLUMN_KEY_TAKEAWAYS = "key_takeaways"

        private const val COLUMN_LOCATION_TYPE = "location_type"
        private const val COLUMN_SIGNIFICANCE = "significance"
        private const val COLUMN_COORDINATES = "coordinates"

        private const val COLUMN_DEPTH_SCORE = "depth_score"
        private const val COLUMN_ARTISTRY_SCORE = "artistry_score"
        private const val COLUMN_EMOTION_SCORE = "emotion_score"
        private const val COLUMN_LOGIC_SCORE = "logic_score"
        private const val COLUMN_DIFFICULTY_SCORE = "difficulty_score"
        private const val COLUMN_HEALING_SCORE = "healing_score"
    }
}
