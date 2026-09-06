package com.example.readtrace

import com.example.readtrace.model.MediaType
import com.example.readtrace.util.ThoughtPolisherEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThoughtPolisherEngineTest {

    @Test
    fun `离线三大文风润色均能产生非空内容与合法金句`() {
        val title = "哈姆莱特"
        val author = "莎士比亚"
        val raw = "结局太震撼了，生存还是毁灭让人深思。"

        for (style in ThoughtPolisherEngine.PolishingStyle.entries) {
            val result = ThoughtPolisherEngine.generateOfflinePolishedThought(
                rawThought = raw,
                bookTitle = title,
                author = author,
                mediaType = MediaType.BOOK,
                style = style,
            )

            assertTrue(result.isFromOffline)
            assertEquals(style, result.style)
            assertTrue("润色正文应包含作品名或核心内容", result.polishedText.contains(title) || result.polishedText.length > 20)
            assertTrue("金句字数应严格 <= 15", result.goldenQuote.length in 1..15)
        }
    }

    @Test
    fun `草稿为空时离线能自主生成策展级评语`() {
        val title = "星际穿越"
        val result = ThoughtPolisherEngine.generateOfflinePolishedThought(
            rawThought = "",
            bookTitle = title,
            author = "诺兰",
            mediaType = MediaType.MOVIE,
            style = ThoughtPolisherEngine.PolishingStyle.PHILOSOPHICAL,
        )

        assertTrue(result.polishedText.isNotBlank())
        assertTrue("金句不应为空且长度 <= 15", result.goldenQuote.isNotEmpty() && result.goldenQuote.length <= 15)
    }

    @Test
    fun `JSON输出解析能正确过滤markdown代码块与截断超长金句`() {
        val jsonString = """
            ```json
            {
              "polishedText": "在荒谬的世界中，加缪以冰冷而清醒的笔调勾勒了局外人的存在困境。",
              "goldenQuote": "这是一个超过十五个汉字的非常非常长的超长金句测试用例"
            }
            ```
        """.trimIndent()

        val parsed = ThoughtPolisherEngine.parseJsonResult(jsonString, ThoughtPolisherEngine.PolishingStyle.CRITICAL)
        assertNotNull(parsed)
        assertEquals("在荒谬的世界中，加缪以冰冷而清醒的笔调勾勒了局外人的存在困境。", parsed!!.polishedText)
        assertEquals(15, parsed.goldenQuote.length)
        assertTrue(parsed.goldenQuote.startsWith("这是一个超过十五个"))
    }

    @Test
    fun `金句兜底提取器能从段落中截取合适首句`() {
        val longText = "爱是荒原里唯一的火种。即便世界终将归于冷寂，我们依然在星辰之下相拥。"
        val quote = ThoughtPolisherEngine.extractQuoteFallback(longText)
        assertEquals("爱是荒原里唯一的火种", quote)
        assertTrue(quote.length <= 15)
    }
}
