package com.example.readtrace

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.readtrace.data.migrator.DatabaseMigrator
import com.example.readtrace.data.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 迁移器 v14/v15 守卫回归测试（回归缺陷：v13 有 source_type 守卫，v14/v15 复制粘贴时丢失）。
 * 全程使用内存数据库，不触碰应用真实数据库。
 *
 * 语义约定：source_type 为空 = 预置/手动条目；非空（如 'netease'）= 外部导入条目。
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigratorGuardTest {

    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        db = SQLiteDatabase.create(null)
        DatabaseMigrator.onCreate(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun insertBook(
        title: String,
        mediaType: String,
        rating: Double?,
        sourceType: String?,
    ): Long {
        val cv = ContentValues().apply {
            put(COLUMN_TITLE, title)
            put(COLUMN_MEDIA_TYPE, mediaType)
            if (rating != null) put(COLUMN_RATING, rating)
            if (sourceType != null) put(COLUMN_SOURCE_TYPE, sourceType)
            put(COLUMN_CREATED_AT, "2026-01-01 00:00:00")
            put(COLUMN_UPDATED_AT, "2026-01-01 00:00:00")
        }
        return db.insert(TABLE_BOOKS, null, cv)
    }

    private fun insertNote(bookId: Long) {
        val cv = ContentValues().apply {
            put(COLUMN_BOOK_ID, bookId)
            put(COLUMN_CONTENT, "迁移守卫测试笔记")
            put(COLUMN_CREATED_AT, "2026-01-01 00:00:00")
            put(COLUMN_UPDATED_AT, "2026-01-01 00:00:00")
        }
        db.insert(TABLE_NOTES, null, cv)
    }

    private fun insertAudioTrack(bookId: Long) {
        val cv = ContentValues().apply {
            put(COLUMN_AUDIO_BOOK_ID, bookId)
            put(COLUMN_AUDIO_TITLE, "迁移守卫测试曲目")
            put(COLUMN_AUDIO_URI, "content://test/track")
        }
        db.insert(TABLE_AUDIO_TRACKS, null, cv)
    }

    private fun countById(id: Long): Int =
        db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_BOOKS WHERE $COLUMN_ID = ?",
            arrayOf(id.toString()),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun ratingOf(id: Long): Double =
        db.rawQuery(
            "SELECT $COLUMN_RATING FROM $TABLE_BOOKS WHERE $COLUMN_ID = ?",
            arrayOf(id.toString()),
        ).use {
            assertTrue("bookId=$id 应存在", it.moveToFirst())
            it.getDouble(0)
        }

    private fun childRowsOf(table: String, bookId: Long): Int =
        db.rawQuery(
            "SELECT COUNT(*) FROM $table WHERE $COLUMN_BOOK_ID = ?",
            arrayOf(bookId.toString()),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    @Test
    fun v14级联删除不得误删外部导入的同名音乐条目() {
        val presetId = insertBook("451 (华氏451)", "music", 5.0, null)
        val neteaseId = insertBook("451 (华氏451)", "music", 5.0, "netease")
        val neteaseClosetId = insertBook("Blues in the Closet", "music", 4.5, "netease")
        insertNote(neteaseId)
        insertAudioTrack(neteaseId)

        DatabaseMigrator.onUpgrade(db, 13, 14)

        assertEquals("预置同名条目应被迁移逻辑删除", 0, countById(presetId))
        assertEquals("外部导入条目（netease）必须保留", 1, countById(neteaseId))
        assertEquals("外部导入条目（netease）必须保留", 1, countById(neteaseClosetId))
        assertEquals("导入条目的笔记不得被级联误删", 1, childRowsOf(TABLE_NOTES, neteaseId))
        assertEquals("导入条目的音频曲目不得被级联误删", 1, childRowsOf(TABLE_AUDIO_TRACKS, neteaseId))
    }

    @Test
    fun v15评分迁移不得改写外部导入条目() {
        val neteaseMusic = insertBook("导入专辑A", "music", 5.0, "netease")
        val neteaseMovie = insertBook("导入电影B", "movie", 4.5, "douban")
        val neteaseGame = insertBook("导入游戏C", "game", 3.0, "steam")
        val presetMusic = insertBook("预置专辑D", "music", 5.0, null)
        val bookEntry = insertBook("普通书籍E", "book", 5.0, null)

        DatabaseMigrator.onUpgrade(db, 14, 15)

        assertEquals("导入音乐评分不得改写", 5.0, ratingOf(neteaseMusic), 0.0001)
        assertEquals("导入影视评分不得改写", 4.5, ratingOf(neteaseMovie), 0.0001)
        assertEquals("导入游戏评分不得改写", 3.0, ratingOf(neteaseGame), 0.0001)
        val migrated = ratingOf(presetMusic)
        assertTrue("预置条目评分应迁移至 7.0~8.0，实际 $migrated", migrated in 7.0..8.0)
        assertEquals("书籍类不受 v15 评分迁移影响", 5.0, ratingOf(bookEntry), 0.0001)
    }

    @Test
    fun v13升至v15全链路用户数据不受损() {
        val neteaseId = insertBook("451 (华氏451)", "music", 5.0, "netease")
        val presetId = insertBook("451 (华氏451)", "music", 5.0, null)
        insertNote(neteaseId)

        DatabaseMigrator.onUpgrade(db, 13, 15)

        assertEquals("全链路迁移后导入条目必须存活", 1, countById(neteaseId))
        assertEquals("全链路迁移后导入条目评分不得改写", 5.0, ratingOf(neteaseId), 0.0001)
        assertEquals("全链路迁移后预置条目应被删除", 0, countById(presetId))
        assertEquals("导入条目笔记必须存活", 1, childRowsOf(TABLE_NOTES, neteaseId))
    }
}
