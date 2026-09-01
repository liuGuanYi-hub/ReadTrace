package com.example.readtrace

import com.example.readtrace.model.BangumiSubject
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.AutoTagSuggestionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTagSuggestionHelperTest {

    private fun subject(
        tags: List<String> = emptyList(),
        summary: String? = null,
    ) = BangumiSubject(
        id = 1L,
        name = "Test",
        nameCn = "测试作品",
        coverUrl = null,
        summary = summary,
        tags = tags,
    )

    @Test
    fun `源标签保留热度顺序且过滤噪声词`() {
        val tags = AutoTagSuggestionHelper.suggestTags(
            subject(tags = listOf("科幻", "动画", "赛博朋克", "2010s", "剧场版")),
            MediaType.ANIME,
        )

        assertEquals(listOf("科幻", "赛博朋克", "番剧", "二次元"), tags)
    }

    @Test
    fun `源标签为空时补足媒介兜底标签`() {
        val tags = AutoTagSuggestionHelper.suggestTags(subject(), MediaType.GAME)

        assertEquals(listOf("游戏", "互动叙事"), tags)
    }

    @Test
    fun `简介关键词补足候选标签`() {
        val summary = "机器人。机器人。机器人。人工智能。人工智能。未来世界。"
        val tags = AutoTagSuggestionHelper.suggestTags(
            subject(summary = summary),
            MediaType.MOVIE,
            limit = 8,
        )

        // 2 个媒介兜底 + 3 个简介关键词；数据稀疏时允许少于 6 个，但关键词必须全部入选
        assertTrue(tags.contains("机器人"))
        assertTrue(tags.contains("人工智能"))
        assertTrue(tags.contains("未来世界"))
        assertTrue(tags.size in 5..8)
    }

    @Test
    fun `分词过滤停用词数字与超长短语`() {
        val keywords = AutoTagSuggestionHelper.deriveKeywordsFromText(
            "为了故事。故事。12345。这是一个非常长的短语不应该被当成标签。哲学。哲学。",
        )

        assertTrue(keywords.contains("哲学"))
        assertTrue(keywords.none { it == "为了" || it == "12345" })
        assertTrue(keywords.none { it.length > 6 })
    }

    @Test
    fun `结果去重且不超过上限`() {
        val tags = AutoTagSuggestionHelper.suggestTags(
            subject(tags = List(20) { "标签$it" } + listOf("标签0")),
            MediaType.BOOK,
            limit = 6,
        )

        assertEquals(6, tags.size)
        assertEquals(tags.size, tags.toSet().size)
    }
}
