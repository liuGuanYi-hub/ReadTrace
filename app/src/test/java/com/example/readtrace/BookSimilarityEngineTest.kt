package com.example.readtrace

import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.BookSimilarityEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class BookSimilarityEngineTest {

    private fun book(
        title: String,
        author: String? = null,
        tags: List<String> = emptyList(),
        category: String? = null,
        mediaType: MediaType = MediaType.BOOK,
    ) = Book(
        id = 0,
        title = title,
        author = author,
        mediaType = mediaType,
        tags = tags,
        category = category,
    )

    @Test
    fun `日本作家识别为日本文学`() {
        assertEquals("日本文学", BookSimilarityEngine.detectRegion(book("雪国", author = "川端康成")))
        assertEquals("日本文学", BookSimilarityEngine.detectRegion(book("1Q84", tags = listOf("日本"))))
    }

    @Test
    fun `拉美作家识别为拉美文学`() {
        assertEquals("拉美文学", BookSimilarityEngine.detectRegion(book("百年孤独", author = "马尔克斯")))
        assertEquals("拉美文学", BookSimilarityEngine.detectRegion(book("小径分岔的花园", author = "博尔赫斯")))
    }

    @Test
    fun `中国作家识别为华语经典`() {
        assertEquals("华语经典", BookSimilarityEngine.detectRegion(book("活着", author = "余华")))
        assertEquals("华语经典", BookSimilarityEngine.detectRegion(book("三体", author = "刘慈欣")))
    }

    @Test
    fun `欧美作家识别为欧美名著`() {
        assertEquals("欧美名著", BookSimilarityEngine.detectRegion(book("老人与海", author = "海明威")))
        assertEquals("欧美名著", BookSimilarityEngine.detectRegion(book("变形记", author = "卡夫卡")))
    }

    @Test
    fun `未知来源归入世界经典`() {
        assertEquals("世界经典", BookSimilarityEngine.detectRegion(book("某种未知来源的作品", author = "无名氏")))
    }

    @Test
    fun `标题与分类同样参与识别`() {
        assertEquals("华语经典", BookSimilarityEngine.detectRegion(book("中国哲学简史", category = "中国思想")))
        assertEquals("欧美名著", BookSimilarityEngine.detectRegion(book("某某作品", category = "英国文学")))
    }
}
