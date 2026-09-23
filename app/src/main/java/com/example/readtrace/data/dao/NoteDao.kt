package com.example.readtrace.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.model.Note
import com.example.readtrace.model.NoteType
import com.example.readtrace.model.ArchivedNoteItem
import com.example.readtrace.util.BackupHelper
import com.example.readtrace.util.CoverImageHelper

/**
 * notes 表、回收站与整库备份读写的数据访问层（P40 Phase 4 阶段 4 自 BookDatabaseHelper 抽出）。
 * 纯函数 + 显式传 db；books 缓存失效仍由 helper 委托层负责。
 */
object NoteDao {

    private fun currentTimestamp(): String = BookDatabaseHelper.currentTimestamp()

    fun insertNote(db: SQLiteDatabase, note: Note): Long {
        val now = currentTimestamp()
        val values = note.toContentValues().apply {
            put(COLUMN_CREATED_AT, note.createdAt.ifBlank { now })
            put(COLUMN_UPDATED_AT, note.updatedAt.ifBlank { now })
            put(COLUMN_IS_DELETED, 0)
            putNull(COLUMN_DELETED_AT)
        }
        val id = db.insertOrThrow(TABLE_NOTES, null, values)
        ConceptIndexRepository.invalidate()
        return id
    }

    fun getNotes(db: SQLiteDatabase, bookId: Long): List<Note> =
        db.query(
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
    fun getAllNotesLite(db: SQLiteDatabase): List<Pair<Long, String>> =
        db.query(
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

    fun getNote(db: SQLiteDatabase, noteId: Long): Note? =
        db.query(
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

    fun updateNote(db: SQLiteDatabase, note: Note): Boolean {
        if (note.id <= 0) return false
        val values = note.toContentValues().apply {
            put(COLUMN_UPDATED_AT, currentTimestamp())
        }
        val count = db.update(
            TABLE_NOTES,
            values,
            "$COLUMN_ID = ? AND $COLUMN_IS_DELETED = ?",
            arrayOf(note.id.toString(), "0"),
        )
        if (count > 0) ConceptIndexRepository.invalidate()
        return count > 0
    }

    fun archiveNote(db: SQLiteDatabase, noteId: Long): Boolean {
        if (noteId <= 0) return false
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 1)
            put(COLUMN_DELETED_AT, now)
            put(COLUMN_UPDATED_AT, now)
        }
        val count = db.update(
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
    fun restoreNote(db: SQLiteDatabase, noteId: Long): Boolean {
        if (noteId <= 0) return false
        val now = currentTimestamp()
        val values = ContentValues().apply {
            put(COLUMN_IS_DELETED, 0)
            putNull(COLUMN_DELETED_AT)
            put(COLUMN_UPDATED_AT, now)
        }
        val count = db.update(
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
    fun getArchivedNotes(db: SQLiteDatabase): List<ArchivedNoteItem> {
        val sql = """
            SELECT n.$COLUMN_ID, n.$COLUMN_BOOK_ID, n.$COLUMN_CONTENT, n.$COLUMN_NOTE_TYPE,
                   n.$COLUMN_PAGE, n.$COLUMN_CHAPTER, n.$COLUMN_CREATED_AT, n.$COLUMN_UPDATED_AT,
                   n.$COLUMN_IS_DELETED, n.$COLUMN_DELETED_AT, b.$COLUMN_TITLE AS book_title
            FROM $TABLE_NOTES n
            LEFT JOIN $TABLE_BOOKS b ON n.$COLUMN_BOOK_ID = b.$COLUMN_ID
            WHERE n.$COLUMN_IS_DELETED = 1
            ORDER BY n.$COLUMN_DELETED_AT DESC, n.$COLUMN_ID DESC
        """.trimIndent()

        return db.rawQuery(sql, null).use { cursor ->
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
    fun hardDeleteNote(db: SQLiteDatabase, noteId: Long): Boolean {
        if (noteId <= 0) return false
        return db.delete(
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
    fun clearAllTrash(db: SQLiteDatabase): Pair<Int, Int> {
        val db = db
        db.beginTransaction()
        try {
            // 1. 获取所有归档书籍的封面并清理文件
            val archivedBooks = BookDao.getArchivedBooks(db)
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
            return Pair(deletedBooksCount, deletedNotesCount)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * 获取全部作品及各自关联的笔记（用于全量备份与多格式导出）
     */
    fun getAllWorksWithNotes(db: SQLiteDatabase): List<Pair<Book, List<Note>>> {
        val books = BookDao.getBooks(db)
        return books.map { book ->
            val notes = getNotes(db, book.id)
            Pair(book, notes)
        }
    }

    /**
     * 全量深度查询：书籍 + 笔记 + 6 大高阶资产（打卡/人物/大纲/地标/心智/曲目）
     */
    fun getAllFullWorkBackups(db: SQLiteDatabase): List<com.example.readtrace.util.BackupHelper.WorkBackup> {
        val books = BookDao.getBooks(db)
        val mindprints = MindprintDao.getAllMindprints(db)
        // T2.7：六张子表各一次全量查询 + 内存分组，替代每部作品 6 次查询的 N+1（500 部作品 = 7 次查询替代 3500 次）
        val notesByBook = queryWorksTableGroupedByBook(db, TABLE_NOTES, includeDeleted = false, { it.toNote() }, { it.bookId })
        val sessionsByBook = queryWorksTableGroupedByBook(db, TABLE_READING_SESSIONS, includeDeleted = false, { it.toReadingSession() }, { it.bookId })
        val charactersByBook = queryWorksTableGroupedByBook(db, TABLE_BOOK_CHARACTERS, includeDeleted = false, { it.toBookCharacter() }, { it.bookId })
        val outlinesByBook = queryWorksTableGroupedByBook(db, TABLE_BOOK_OUTLINES, includeDeleted = false, { it.toBookOutline() }, { it.bookId })
        val locationsByBook = queryWorksTableGroupedByBook(db, TABLE_BOOK_LOCATIONS, includeDeleted = false, { it.toBookLocation() }, { it.bookId })
        val tracksByBook = queryAllAudioTracksGroupedByBook(db)
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
        db: SQLiteDatabase,
        table: String,
        includeDeleted: Boolean,
        crossinline mapRow: (Cursor) -> T,
        crossinline bookIdOf: (T) -> Long,
    ): Map<Long, MutableList<T>> {
        val selection = if (includeDeleted) null else "$COLUMN_IS_DELETED = 0"
        val grouped = linkedMapOf<Long, MutableList<T>>()
        db.query(table, null, selection, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val item = mapRow(cursor)
                grouped.getOrPut(bookIdOf(item)) { mutableListOf() }.add(item)
            }
        }
        return grouped
    }

    /** T2.7：audio_tracks 专用分组（该表无 is_deleted 列，与 getAudioTracks 语义一致） */
    private fun queryAllAudioTracksGroupedByBook(db: SQLiteDatabase): Map<Long, MutableList<com.example.readtrace.model.AudioTrackItem>> {
        val grouped = linkedMapOf<Long, MutableList<com.example.readtrace.model.AudioTrackItem>>()
        db.query(
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
    fun importFullBackup(db: SQLiteDatabase, items: List<com.example.readtrace.util.BackupHelper.WorkBackup>): Pair<Int, Int> {
        if (items.isEmpty()) return Pair(0, 0)
        val db = db
        db.beginTransaction()
        var importedWorks = 0
        var importedNotes = 0
        try {
            val existingBookIds = linkedMapOf<String, Long>()
            BookDao.getBooksForList(db, ).forEach { b ->
                val key = BookDao.bookIndexKey(b.title, b.author)
                val current = existingBookIds[key]
                if (current == null || b.id < current) existingBookIds[key] = b.id
            }
            val notesByBook = queryWorksTableGroupedByBook(db, TABLE_NOTES, includeDeleted = false, { it.toNote() }, { it.bookId })
            val sessionsByBook = queryWorksTableGroupedByBook(db, TABLE_READING_SESSIONS, includeDeleted = false, { it.toReadingSession() }, { it.bookId })
            val charactersByBook = queryWorksTableGroupedByBook(db, TABLE_BOOK_CHARACTERS, includeDeleted = false, { it.toBookCharacter() }, { it.bookId })
            val outlinesByBook = queryWorksTableGroupedByBook(db, TABLE_BOOK_OUTLINES, includeDeleted = false, { it.toBookOutline() }, { it.bookId })
            val locationsByBook = queryWorksTableGroupedByBook(db, TABLE_BOOK_LOCATIONS, includeDeleted = false, { it.toBookLocation() }, { it.bookId })
            val tracksByBook = queryAllAudioTracksGroupedByBook(db)
            val allMindprints = MindprintDao.getAllMindprints(db).toMutableMap()

            items.forEach { work ->
                val book = work.book
                // 1. 查找是否存在同名且同作者/创作者的作品
                val indexKey = BookDao.bookIndexKey(book.title, book.author)
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
                            BookDao.insertReadingSession(db, 
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
                            BookDao.insertCharacter(db, character.copy(bookId = targetBookId, isDeleted = false))
                            existingCharacters += character.copy(bookId = targetBookId, isDeleted = false)
                        }
                    }

                    // 5. 章节大纲（按章节标题去重）
                    val existingOutlines = outlinesByBook[targetBookId] ?: mutableListOf()
                    work.outlines.forEach { outline ->
                        val isDuplicate = existingOutlines.any { it.title.trim() == outline.title.trim() }
                        if (!isDuplicate && outline.title.isNotBlank() && outline.summary.isNotBlank()) {
                            BookDao.insertOutline(db, outline.copy(bookId = targetBookId, isDeleted = false))
                            existingOutlines += outline.copy(bookId = targetBookId, isDeleted = false)
                        }
                    }

                    // 6. 空间地标（按名称去重）
                    val existingLocations = locationsByBook[targetBookId] ?: mutableListOf()
                    work.locations.forEach { location ->
                        val isDuplicate = existingLocations.any { it.name.trim() == location.name.trim() }
                        if (!isDuplicate && location.name.isNotBlank()) {
                            BookDao.insertLocation(db, location.copy(bookId = targetBookId, isDeleted = false))
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
                            MindprintDao.saveMindprint(db, incoming)
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
                            BookDao.insertAudioTrack(db, track.copy(bookId = targetBookId))
                            existingTracks += track.copy(bookId = targetBookId)
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

}

internal fun Cursor.toNote(): Note =
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

internal fun Note.toContentValues(): ContentValues =
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

