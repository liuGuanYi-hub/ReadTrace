package com.example.readtrace

import com.example.readtrace.model.MediaType
import com.example.readtrace.util.BookCsvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class BookCsvParserTest {

    private fun csv(vararg lines: String): ByteArrayInputStream =
        ByteArrayInputStream(lines.joinToString("\n").toByteArray(Charsets.UTF_8))

    @Test
    fun `带表头的CSV按列名解析`() {
        val records = BookCsvParser.parseRecords(
            csv(
                "标题,创作者,状态,评分,标签,短评",
                "三体,刘慈欣,finished,9.5,科幻;硬核,给岁月以文明",
            ),
        )

        assertEquals(1, records.size)
        val record = records[0]
        assertEquals("三体", record.book.title)
        assertEquals("刘慈欣", record.book.author)
        assertEquals(9.5, record.book.rating!!, 0.001)
        assertEquals(listOf("科幻", "硬核"), record.book.tags)
        assertEquals("给岁月以文明", record.book.shortComment)
    }

    @Test
    fun `无表头CSV按位置解析`() {
        val records = BookCsvParser.parseRecords(
            csv("伊豆的舞女,川端康成,8.5,如同大学般转瞬即逝"),
        )

        assertEquals(1, records.size)
        assertEquals("伊豆的舞女", records[0].book.title)
    }

    @Test
    fun `含引号与逗号的字段完整保留`() {
        val records = BookCsvParser.parseRecords(
            csv(
                "标题,短评",
                "\"局外人\",\"简短评价, 包含\"\"引号\"\"与逗号\"",
            ),
        )

        assertEquals(1, records.size)
        assertEquals("简短评价, 包含\"引号\"与逗号", records[0].book.shortComment)
    }

    @Test
    fun `UTF8 BOM 与空行自动跳过`() {
        val content = "\uFEFF标题\n\n《无题》,未知\n"
        val records = BookCsvParser.parseRecords(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))

        assertEquals(1, records.size)
        assertEquals("《无题》", records[0].book.title)
    }

    @Test
    fun `作品类型按中文别名映射`() {
        val records = BookCsvParser.parseRecords(
            csv(
                "标题,类型",
                "攻壳机动队,动漫",
                "银翼杀手,电影",
                "塞尔达,游戏",
            ),
        )

        assertEquals(3, records.size)
        assertEquals(MediaType.ANIME, records[0].book.mediaType)
        assertEquals(MediaType.MOVIE, records[1].book.mediaType)
        assertEquals(MediaType.GAME, records[2].book.mediaType)
    }

    @Test
    fun `标题为空的行被丢弃`() {
        val records = BookCsvParser.parseRecords(
            csv(
                "标题,创作者",
                ",无名作者",
            ),
        )

        assertTrue(records.isEmpty())
    }
}
