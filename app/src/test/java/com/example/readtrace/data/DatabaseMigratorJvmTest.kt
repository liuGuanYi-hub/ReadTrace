package com.example.readtrace.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.readtrace.data.migrator.DatabaseMigrator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T3.2：迁移器 JVM 回归测试（Robolectric，testDebugUnitTest 门禁内运行，CI 无需模拟器）。
 * 与 androidTest 的 DatabaseMigratorGuardTest 互补：把 v13/v14/v15 守卫镜像进 JVM 套件，
 * 并补齐 v6 数据迁移、v16 索引补齐与幂等、onCreate 建表完整性覆盖。
 * 全程使用内存数据库，不触碰应用真实数据库。
 *
 * 语义约定：source_type 为空 = 预置/手动条目；非空（如 'netease'）= 外部导入条目。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigratorJvmTest {

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

    // ---------- 通用断言工具 ----------

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

    private fun indexCount(indexName: String): Int =
        db.rawQuery(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf(indexName),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun tableCount(tableName: String): Int =
        db.rawQuery(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // ---------- onCreate 建表完整性 ----------

    @Test
    fun onCreate九张业务表齐备() {
        listOf(
            TABLE_BOOKS,
            TABLE_NOTES,
            TABLE_READING_SESSIONS,
            TABLE_BOOK_CHARACTERS,
            TABLE_BOOK_OUTLINES,
            TABLE_BOOK_LOCATIONS,
            TABLE_BOOK_MINDPRINTS,
            TABLE_AUDIO_TRACKS,
            TABLE_FAVORITES,
        ).forEach { table ->
            assertEquals("建表后应存在表 $table", 1, tableCount(table))
        }
    }

    @Test
    fun onCreate性能索引与基础索引齐备() {
        listOf(
            "index_books_status_deleted",
            "index_books_source",
            "index_books_title",
            "index_books_updated_at",
            "index_audio_tracks_book",
            "index_notes_book_deleted",
            "index_sessions_book_deleted",
            "index_favorites_media",
        ).forEach { index ->
            assertEquals("建表后应存在索引 $index", 1, indexCount(index))
        }
    }

    // ---------- v14/v15 守卫（镜像 androidTest，纳入 CI 门禁） ----------

    @Test
    fun v14级联删除不得误删外部导入的同名音乐条目() {
        val presetId = insertBook("451 (华氏451)", "music", 5.0, null)
        val neteaseId = insertBook("451 (华氏451)", "music", 5.0, "netease")
        insertNote(neteaseId)

        DatabaseMigrator.onUpgrade(db, 13, 14)

        assertEquals("预置同名条目应被迁移逻辑删除", 0, countById(presetId))
        assertEquals("外部导入条目（netease）必须保留", 1, countById(neteaseId))
        assertEquals("导入条目的笔记不得被级联误删", 1, childRowsOf(TABLE_NOTES, neteaseId))
    }

    @Test
    fun v15评分迁移不得改写外部导入条目() {
        val neteaseMusic = insertBook("导入专辑A", "music", 5.0, "netease")
        val neteaseGame = insertBook("导入游戏C", "game", 3.0, "steam")
        val presetMusic = insertBook("预置专辑D", "music", 5.0, null)
        val bookEntry = insertBook("普通书籍E", "book", 5.0, null)

        DatabaseMigrator.onUpgrade(db, 14, 15)

        assertEquals("导入音乐评分不得改写", 5.0, ratingOf(neteaseMusic), 0.0001)
        assertEquals("导入游戏评分不得改写", 3.0, ratingOf(neteaseGame), 0.0001)
        val migrated = ratingOf(presetMusic)
        assertTrue("预置条目评分应迁移至 7.0~8.0，实际 $migrated", migrated in 7.0..8.0)
        assertEquals("书籍类不受 v15 评分迁移影响", 5.0, ratingOf(bookEntry), 0.0001)
    }

    // ---------- 全链路 v13→v16 ----------

    @Test
    fun v13升至v16全链路_用户数据不受损且索引补齐() {
        val neteaseId = insertBook("451 (华氏451)", "music", 5.0, "netease")
        val presetId = insertBook("451 (华氏451)", "music", 5.0, null)
        insertNote(neteaseId)

        DatabaseMigrator.onUpgrade(db, 13, 16)

        assertEquals("全链路迁移后导入条目必须存活", 1, countById(neteaseId))
        assertEquals("全链路迁移后导入条目评分不得改写", 5.0, ratingOf(neteaseId), 0.0001)
        assertEquals("全链路迁移后预置条目应被删除", 0, countById(presetId))
        assertEquals("导入条目笔记必须存活", 1, childRowsOf(TABLE_NOTES, neteaseId))
        assertEquals("v16 应补齐 books.title 索引", 1, indexCount("index_books_title"))
        assertEquals("v16 应补齐 books.updated_at 索引", 1, indexCount("index_books_updated_at"))
        assertEquals("v16 应补齐 audio_tracks.book_id 索引", 1, indexCount("index_audio_tracks_book"))
    }

    // ---------- v16 索引幂等 ----------

    @Test
    fun v16索引迁移幂等_重复执行不产生重复索引() {
        DatabaseMigrator.onUpgrade(db, 15, 16)
        DatabaseMigrator.onUpgrade(db, 15, 16)

        listOf(
            "index_books_title",
            "index_books_updated_at",
            "index_audio_tracks_book",
        ).forEach { index ->
            assertEquals("重复执行 v16 后索引 $index 仍应只有一条", 1, indexCount(index))
        }
    }

    // ---------- v6 数据迁移语义 ----------

    @Test
    fun v6播客分类并入音乐() {
        val podcastId = insertBook("播客条目", "podcast", null, null)

        DatabaseMigrator.onUpgrade(db, 5, 6)

        db.rawQuery(
            "SELECT $COLUMN_MEDIA_TYPE FROM $TABLE_BOOKS WHERE $COLUMN_ID = ?",
            arrayOf(podcastId.toString()),
        ).use {
            assertTrue("播客条目应存在", it.moveToFirst())
            assertEquals("podcast 应并入 music 分类", "music", it.getString(0))
        }
    }
}
