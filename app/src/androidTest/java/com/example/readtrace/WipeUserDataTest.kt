package com.example.readtrace

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.BookCsvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

/**
 * 清空账号数据（wipeAllUserData）设备端验证。
 * ⚠️ 本测试会物理清空整个应用数据库，请勿与依赖预设播种的测试混跑。
 */
@RunWith(AndroidJUnit4::class)
class WipeUserDataTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val helper = BookDatabaseHelper.getInstance(context)

    private fun db() = helper.writableDatabase

    private fun booksCount(): Int =
        db().rawQuery("SELECT COUNT(*) FROM books", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun rowsIn(table: String): Int =
        db().rawQuery("SELECT COUNT(*) FROM $table", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun seedTwoWorksWithRichContent() {
        // 作品一：CSV 骨架导入 + 富内容合并
        val csv = "标题,创作者\n清空测试之书A,测试作者\n"
        helper.importParsedRecords(
            BookCsvParser.parseRecords(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)), MediaType.BOOK),
        )
        // 作品二：纯 JSON 自动建库（验证 wipe 后重导闭环）
        val json = """
            [
              {
                "title": "清空测试之番剧B",
                "media": "anime",
                "characters": [{"name": "角色X", "desc": "待清空"}],
                "quotes": [{"content": "金句X", "source": "第一话"}]
              }
            ]
        """.trimIndent()
        assertEquals(1, helper.importRichContentJson(json, "rich_content_anime.json"))
        assertTrue("前置条件：应已有作品", booksCount() > 0)
        assertTrue("前置条件：应已有角色谱", rowsIn("book_characters") > 0)
    }

    @Test
    fun 清空后全部作品与关联维度归零() {
        seedTwoWorksWithRichContent()

        val (wipedBooks, wipedNotes) = helper.wipeAllUserData()

        assertTrue("应删除至少 2 部作品", wipedBooks >= 2)
        assertTrue("应删除至少 1 条笔记", wipedNotes >= 1)
        assertEquals("作品应全部清空", 0, booksCount())
        assertEquals("笔记应全部清空", 0, rowsIn("notes"))
        assertEquals("角色谱应全部清空", 0, rowsIn("book_characters"))
        assertEquals("大纲应全部清空", 0, rowsIn("book_outlines"))
        assertEquals("打卡记录应全部清空", 0, rowsIn("reading_sessions"))
    }

    @Test
    fun 清空后可重新导入合并内容恢复() {
        seedTwoWorksWithRichContent()
        helper.wipeAllUserData()
        assertEquals(0, booksCount())

        // 模拟用户清空后重新导入同一份 JSON → 应自动重建并带富内容
        val json = """
            [
              {
                "title": "清空测试之番剧B",
                "media": "anime",
                "characters": [{"name": "角色X", "desc": "重建"}]
              }
            ]
        """.trimIndent()
        assertEquals(1, helper.importRichContentJson(json, "rich_content_anime.json"))
        assertEquals("重建后应有 1 部作品", 1, booksCount())
        assertEquals("重建后角色谱应随建库写入", 1, rowsIn("book_characters"))

        val mediaType = db().rawQuery(
            "SELECT media_type FROM books WHERE title = ?",
            arrayOf("清空测试之番剧B"),
        ).use { if (it.moveToFirst()) it.getString(0) else null }
        assertEquals("重建作品媒介应为动漫", "anime", mediaType)
    }
}
