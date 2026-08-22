package com.example.readtrace.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.Note
import com.example.readtrace.model.NoteType
import org.json.JSONArray
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class BookDatabaseHelper(context: Context) :
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
                $COLUMN_RATING REAL,
                $COLUMN_TAGS TEXT NOT NULL DEFAULT '[]',
                $COLUMN_SHORT_COMMENT TEXT,
                $COLUMN_REVIEW TEXT,
                $COLUMN_START_DATE TEXT,
                $COLUMN_FINISH_DATE TEXT,
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
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v1.1：新增 notes 表，已有 books 数据保持不变。
            createNotesTable(database)
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
            rating = getNullableDouble(COLUMN_RATING),
            tags = parseTags(getNullableString(COLUMN_TAGS)),
            shortComment = getNullableString(COLUMN_SHORT_COMMENT),
            review = getNullableString(COLUMN_REVIEW),
            startDate = getNullableString(COLUMN_START_DATE),
            finishDate = getNullableString(COLUMN_FINISH_DATE),
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
            if (rating == null) putNull(COLUMN_RATING) else put(COLUMN_RATING, rating)
            put(COLUMN_TAGS, JSONArray(tags).toString())
            putNullable(COLUMN_SHORT_COMMENT, shortComment)
            putNullable(COLUMN_REVIEW, review)
            putNullable(COLUMN_START_DATE, startDate)
            putNullable(COLUMN_FINISH_DATE, finishDate)
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
        const val DATABASE_VERSION = 2

        private const val TABLE_BOOKS = "books"
        private const val TABLE_NOTES = "notes"
        private const val COLUMN_ID = "id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_AUTHOR = "author"
        private const val COLUMN_COVER_URL = "cover_url"
        private const val COLUMN_CATEGORY = "category"
        private const val COLUMN_STATUS = "status"
        private const val COLUMN_RATING = "rating"
        private const val COLUMN_TAGS = "tags"
        private const val COLUMN_SHORT_COMMENT = "short_comment"
        private const val COLUMN_REVIEW = "review"
        private const val COLUMN_START_DATE = "start_date"
        private const val COLUMN_FINISH_DATE = "finish_date"
        private const val COLUMN_CREATED_AT = "created_at"
        private const val COLUMN_UPDATED_AT = "updated_at"
        private const val COLUMN_IS_DELETED = "is_deleted"
        private const val COLUMN_DELETED_AT = "deleted_at"
        private const val COLUMN_BOOK_ID = "book_id"
        private const val COLUMN_CONTENT = "content"
        private const val COLUMN_NOTE_TYPE = "note_type"
        private const val COLUMN_PAGE = "page"
        private const val COLUMN_CHAPTER = "chapter"
    }
}
