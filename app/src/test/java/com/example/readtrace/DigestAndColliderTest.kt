package com.example.readtrace

import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.MentalColliderEngine
import com.example.readtrace.util.SmartQuoteDigestHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestAndColliderTest {

    // --- SmartQuoteDigestHelper ---

    private fun longText(sentence: String): String =
        List(15) { "这是第${it}段的普通叙述，讲述日常生活的细节与琐事，以及天气与通勤。" }.joinToString("") +
            sentence + "结尾收束全文。"

    @Test
    fun `不足300字不提炼`() {
        assertNull(SmartQuoteDigestHelper.digest("像风一样自由。太短了。"))
    }

    @Test
    fun `长文中提炼出比喻与哲思金句`() {
        val quote = SmartQuoteDigestHelper.digest(
            longText("时间仿佛像一条没有归途的河流，带着所有孤独与告别流向永恒。"),
        )

        assertNotNull(quote)
        assertTrue(quote!!.quote.contains("时间"))
        assertTrue(quote.score > 3.0)
    }

    @Test
    fun `候选按得分降序`() {
        val candidates = SmartQuoteDigestHelper.candidates(
            longText("回忆像大海。") + "命运、自由与死亡，在故事中彼此缠绕、彼此成全，仿佛灵魂找到了归宿。",
        )

        assertTrue(candidates.size >= 2)
        assertEquals(candidates, candidates.sortedByDescending { it.score })
    }

    // --- MentalColliderEngine ---

    private fun mindprint(
        depth: Double, artistry: Double, emotion: Double,
        logic: Double, difficulty: Double, healing: Double,
    ) = BookMindprint(
        bookId = 0,
        depthScore = depth, artistryScore = artistry, emotionScore = emotion,
        logicScore = logic, difficultyScore = difficulty, healingScore = healing,
    )

    private fun book(id: Long, title: String, tags: List<String>) =
        Book(id = id, title = title, mediaType = if (id == 1L) MediaType.BOOK else MediaType.GAME, tags = tags)

    @Test
    fun `六维几乎一致时契合度极高`() {
        val mp = mindprint(9.0, 8.5, 8.0, 7.5, 6.0, 7.0)
        val result = MentalColliderEngine.collide(
            book(1, "悉达多", listOf("哲学")),
            book(2, "塞尔达", listOf("哲学")),
            mp, mp.copy(),
        )

        assertTrue(result.resonance >= 95)
        assertEquals("哲学", result.trait.substringAfter("「").substringBefore("」"))
    }

    @Test
    fun `六维差异大时契合度下限守门`() {
        val a = mindprint(9.0, 9.0, 9.0, 9.0, 9.0, 9.0)
        val b = mindprint(2.0, 2.0, 2.0, 2.0, 2.0, 2.0)
        val result = MentalColliderEngine.collide(
            book(1, "A", emptyList()),
            book(2, "B", emptyList()),
            a, b,
        )

        assertEquals(62, result.resonance)
    }

    @Test
    fun `主导纽带取差异最小的维度`() {
        val a = mindprint(9.0, 5.0, 5.0, 5.0, 5.0, 5.0)
        val b = mindprint(8.8, 9.0, 2.0, 2.0, 2.0, 2.0)
        val result = MentalColliderEngine.collide(
            book(1, "A", emptyList()),
            book(2, "B", emptyList()),
            a, b,
        )

        assertEquals("思想深度", result.dominantDimension)
        assertTrue(result.trait.contains("思想深度"))
    }
}
