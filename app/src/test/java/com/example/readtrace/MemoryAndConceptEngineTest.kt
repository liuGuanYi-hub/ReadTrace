package com.example.readtrace

import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.BidirectionalConceptHelper
import com.example.readtrace.util.MemoryFlashbackEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MemoryAndConceptEngineTest {

    private fun book(
        id: Long,
        title: String,
        startDate: String? = null,
        finishDate: String? = null,
        shortComment: String? = null,
        review: String? = null,
    ) = Book(
        id = id,
        title = title,
        mediaType = MediaType.BOOK,
        startDate = startDate,
        finishDate = finishDate,
        shortComment = shortComment,
        review = review,
    )

    // --- MemoryFlashbackEngine ---

    @Test
    fun `识别一年前完读的作品`() {
        val today = LocalDate.of(2026, 9, 2)
        val memories = MemoryFlashbackEngine.findFlashbacks(
            listOf(
                book(1, "百年孤独", finishDate = "2025-09-02", shortComment = "回忆没有归途"),
                book(2, "局外人", finishDate = "2025-09-03"),
            ),
            today,
        )

        assertEquals(1, memories.size)
        assertEquals("百年孤独", memories[0].book.title)
        assertEquals(1, memories[0].yearsAgo)
        assertTrue(memories[0].isFinished)
        assertEquals("回忆没有归途", memories[0].quote)
    }

    @Test
    fun `同日开读也能唤醒且当年记录不算回忆`() {
        val today = LocalDate.of(2026, 9, 2)
        val memories = MemoryFlashbackEngine.findFlashbacks(
            listOf(
                book(1, "雪国", startDate = "2024-09-02"),
                book(2, "今天刚开的书", startDate = "2026-09-02"),
            ),
            today,
        )

        assertEquals(1, memories.size)
        assertEquals("雪国", memories[0].book.title)
        assertEquals(2, memories[0].yearsAgo)
        assertTrue(!memories[0].isFinished)
    }

    @Test
    fun `最久远的记忆排在前面且文案正确`() {
        val today = LocalDate.of(2026, 6, 3)
        val memories = MemoryFlashbackEngine.findFlashbacks(
            listOf(
                book(1, "近作", finishDate = "2025-06-03"),
                book(2, "旧作", finishDate = "2021-06-03", review = "如同大学般转瞬即逝。\n第二行"),
            ),
            today,
        )

        assertEquals("旧作", memories[0].book.title)
        assertEquals(5, memories[0].yearsAgo)
        val text = MemoryFlashbackEngine.formatRibbonText(memories[0])
        assertTrue(text.contains("五年前的今天"))
        assertTrue(text.contains("《旧作》"))
        assertTrue(text.contains("如同大学般转瞬即逝。"))
    }

    // --- BidirectionalConceptHelper ---

    @Test
    fun `双链语法提取概念`() {
        val concepts = BidirectionalConceptHelper.extractConcepts(
            "这本书探讨 [[存在主义]] 与 [[虚无与救赎]]，也顺带提到 [[存在主义]]。",
        )

        assertEquals(listOf("存在主义", "虚无与救赎"), concepts)
    }

    @Test
    fun `无双链文本返回空`() {
        assertTrue(BidirectionalConceptHelper.extractConcepts("普通长评没有双链").isEmpty())
        assertTrue(BidirectionalConceptHelper.extractConcepts(null).isEmpty())
    }

    @Test
    fun `倒排索引跨媒介关联`() {
        val index = BidirectionalConceptHelper.buildConceptIndex(
            listOf(
                "1" to listOf("探讨 [[存在主义]] 的巨作", "再看 [[荒诞]]"),
                "2" to listOf("笔记：[[存在主义]] 与自由"),
                "3" to listOf("毫无概念链接"),
            ),
        )

        assertEquals(setOf("1", "2"), index["存在主义"])
        assertEquals(listOf("1"), BidirectionalConceptHelper.relatedWorkIds(index, "荒诞"))
        // 排除自身
        assertEquals(listOf("2"), BidirectionalConceptHelper.relatedWorkIds(index, "存在主义", excludeWorkId = "1"))
    }
}
