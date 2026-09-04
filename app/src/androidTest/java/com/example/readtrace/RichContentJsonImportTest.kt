package com.example.readtrace

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.BookCsvParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

/**
 * 富内容 JSON 本地导入（importRichContentJson）设备端验证：
 * 验证「导入一份即可按标题合并」与「重复导入幂等不覆盖」两条核心语义。
 */
@RunWith(AndroidJUnit4::class)
class RichContentJsonImportTest {

    private val title = "富内容导入测试之书"

    private fun db() = BookDatabaseHelper.getInstance(
        InstrumentationRegistry.getInstrumentation().targetContext,
    ).writableDatabase

    @Before
    fun setUp() {
        // 清理历史运行残留，保证测试环境干净
        cleanUp()
    }

    @After
    fun tearDown() {
        cleanUp()
    }

    private fun cleanUp() {
        val database = db()
        // 覆盖本测试类可能产生的所有作品（含自动建库用例）
        database.execSQL(
            "DELETE FROM book_characters WHERE book_id IN (SELECT id FROM books WHERE title LIKE ?)",
            arrayOf("富内容%"),
        )
        database.execSQL(
            "DELETE FROM notes WHERE book_id IN (SELECT id FROM books WHERE title LIKE ?)",
            arrayOf("富内容%"),
        )
        database.execSQL(
            "DELETE FROM book_outlines WHERE book_id IN (SELECT id FROM books WHERE title LIKE ?)",
            arrayOf("富内容%"),
        )
        database.execSQL("DELETE FROM books WHERE title LIKE ?", arrayOf("富内容%"))
    }

    private fun countRows(table: String, bookTitle: String = title): Int =
        db().rawQuery(
            "SELECT COUNT(*) FROM $table WHERE book_id IN (SELECT id FROM books WHERE title = ?)",
            arrayOf(bookTitle),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun importSkeleton() {
        val csv = "标题,创作者\n$title,测试作者\n"
        val records = BookCsvParser.parseRecords(
            ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)),
            MediaType.BOOK,
        )
        assertEquals(1, BookDatabaseHelper.getInstance(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).importParsedRecords(records))
    }

    private fun richJson(): String = """
        [
          {
            "title": "$title",
            "characters": [
              {"name": "角色甲", "role": "主角", "emoji": "🧪", "desc": "测试角色一"},
              {"name": "角色乙", "role": "配角", "emoji": "🔬", "desc": "测试角色二"}
            ],
            "quotes": [
              {"content": "测试金句一", "source": "第一章"},
              {"content": "测试金句二", "source": "第二章"}
            ],
            "outline": [
              {"phase": "第一幕", "title": "开端", "summary": "测试大纲开端"},
              {"phase": "第二幕", "title": "发展", "summary": "测试大纲发展"}
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun 导入一份JSON即合并入库且重复导入幂等() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = BookDatabaseHelper.getInstance(context)

        // 第一步：藏库中已有同名作品（模拟用户先导入的 CSV 骨架）
        importSkeleton()

        // 第二步：导入一份富内容 JSON → 应合并入库
        val matchedFirst = helper.importRichContentJson(richJson())
        assertEquals("首次导入应匹配 1 部作品", 1, matchedFirst)
        assertEquals("角色应写入 2 条", 2, countRows("book_characters"))
        assertEquals("语录应写入 2 条", 2, countRows("notes"))
        assertEquals("大纲应写入 2 章", 2, countRows("book_outlines"))

        // 第三步：再次导入同一份 → 幂等，不得重复写入
        val matchedSecond = helper.importRichContentJson(richJson())
        assertEquals("重复导入仍应匹配 1 部", 1, matchedSecond)
        assertEquals("重复导入后角色不得翻倍", 2, countRows("book_characters"))
        assertEquals("重复导入后语录不得翻倍", 2, countRows("notes"))
        assertEquals("重复导入后大纲不得翻倍", 2, countRows("book_outlines"))
    }

    @Test
    fun 无匹配作品时返回零且不写入() {
        val helper = BookDatabaseHelper.getInstance(context = InstrumentationRegistry.getInstrumentation().targetContext)
        val orphanJson = """
            [
              {"title": "不存在的书名XYZ", "characters": [{"name": "孤儿角色", "desc": "无主"}]}
            ]
        """.trimIndent()

        val matched = helper.importRichContentJson(orphanJson)
        assertEquals("无匹配作品应返回 0", 0, matched)
    }

    @Test
    fun 真实games文件全量匹配率验证() {
        // 需先 adb push 到应用外部私有目录；文件不存在时跳过（真机无此文件不误报）
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.getExternalFilesDir(null), "rich_content_games.json")
        org.junit.Assume.assumeTrue("未推送真实数据文件，跳过", file.exists())

        val helper = BookDatabaseHelper.getInstance(context)
        val matched = helper.importRichContentJson(file.readText(Charsets.UTF_8))
        android.util.Log.i("RichContentImportTest", "真实 games 文件匹配 $matched 部")
        assertEquals("真实文件应全量匹配预设游戏", 69, matched)
    }

    private fun countBookRows(): Int =
        db().rawQuery(
            "SELECT COUNT(*) FROM books WHERE title = ? AND is_deleted = 0",
            arrayOf("富内容自动建库之番剧"),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    @Test
    fun 导入一份JSON自动建库并带媒介与富内容() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = BookDatabaseHelper.getInstance(context)
        assertEquals(0, countBookRows())

        val json = """
            [
              {
                "title": "富内容自动建库之番剧",
                "characters": [{"name": "自动建库角色", "desc": "随骨架一并写入"}],
                "quotes": [{"content": "自动建库金句", "source": "第一话"}],
                "outline": [{"phase": "第一幕", "title": "起点", "summary": "自动建库大纲"}]
              }
            ]
        """.trimIndent()

        val matched = helper.importRichContentJson(json, "rich_content_anime.json")
        assertEquals("应处理 1 部", 1, matched)
        assertEquals("缺失作品应自动建库", 1, countBookRows())

        val mediaType = db().rawQuery(
            "SELECT media_type FROM books WHERE title = ?",
            arrayOf("富内容自动建库之番剧"),
        ).use { if (it.moveToFirst()) it.getString(0) else null }
        assertEquals("媒介应由文件名推断为动漫", "anime", mediaType)
        assertEquals("富内容应随建库一并写入", 1, countRows("book_characters", "富内容自动建库之番剧"))
        assertEquals(1, countRows("book_outlines", "富内容自动建库之番剧"))

        // 幂等：再导入不得重复建库或重复写入
        assertEquals(1, helper.importRichContentJson(json, "rich_content_anime.json"))
        assertEquals("重复导入不得重复建库", 1, countBookRows())
        assertEquals(1, countRows("book_characters", "富内容自动建库之番剧"))
    }

    @Test
    fun 音乐文件名推断为音乐媒介() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = BookDatabaseHelper.getInstance(context)
        val json = """
            [
              {"title": "富内容自动建库之乐曲", "characters": [], "quotes": []}
            ]
        """.trimIndent()

        helper.importRichContentJson(json, "rich_content_music.json")
        val mediaType = db().rawQuery(
            "SELECT media_type FROM books WHERE title = ? AND is_deleted = 0",
            arrayOf("富内容自动建库之乐曲"),
        ).use { if (it.moveToFirst()) it.getString(0) else null }
        assertEquals("music 文件应推断为音乐媒介", "music", mediaType)
    }
}
