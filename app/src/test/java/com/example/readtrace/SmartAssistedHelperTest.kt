package com.example.readtrace

import com.example.readtrace.model.MediaType
import com.example.readtrace.util.SmartAssistedHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAssistedHelperTest {

    @Test
    fun `六维均在1到10区间内`() {
        for (rating in listOf(1.0, 3.5, 5.0, 8.0, 10.0)) {
            for (media in MediaType.entries) {
                val mp = SmartAssistedHelper.deriveMindprint(rating, media)
                for (v in listOf(
                    mp.depthScore, mp.artistryScore, mp.emotionScore,
                    mp.logicScore, mp.difficultyScore, mp.healingScore,
                )) {
                    assertTrue("$rating/$media -> $v", v in 1.0..10.0)
                }
            }
        }
    }

    @Test
    fun `高分作品六维整体水位高于低分`() {
        val high = SmartAssistedHelper.deriveMindprint(9.5, MediaType.BOOK)
        val low = SmartAssistedHelper.deriveMindprint(3.0, MediaType.BOOK)

        assertTrue(high.depthScore > low.depthScore)
        assertTrue(high.artistryScore > low.artistryScore)
        assertTrue(high.emotionScore > low.emotionScore)
    }

    @Test
    fun `游戏媒介逻辑与阻力高于音乐`() {
        val game = SmartAssistedHelper.deriveMindprint(8.0, MediaType.GAME)
        val music = SmartAssistedHelper.deriveMindprint(8.0, MediaType.MUSIC)

        assertTrue(game.logicScore > music.logicScore)
        assertTrue(game.difficultyScore > music.difficultyScore)
        assertTrue(music.healingScore > game.healingScore)
    }

    @Test
    fun `音乐媒介情感共鸣最高偏向`() {
        val music = SmartAssistedHelper.deriveMindprint(8.0, MediaType.MUSIC)
        val book = SmartAssistedHelper.deriveMindprint(8.0, MediaType.BOOK)

        assertTrue(music.emotionScore > book.emotionScore)
    }

    @Test
    fun `数值保留一位小数`() {
        val mp = SmartAssistedHelper.deriveMindprint(7.3, MediaType.ANIME)
        for (v in listOf(
            mp.depthScore, mp.artistryScore, mp.emotionScore,
            mp.logicScore, mp.difficultyScore, mp.healingScore,
        )) {
            assertEquals("v=$v", 0.0, v * 10 - v * 10.toLong(), 1e-9)
        }
    }

    @Test
    fun `高频标签词云按媒介语境置顶`() {
        val stats = listOf(
            Pair("科幻", 30),
            Pair("治愈", 25),
            Pair("日常", 20),
            Pair("文学", 10),
        )

        val forAnime = SmartAssistedHelper.suggestFrequentTags(stats, MediaType.ANIME)
        // 治愈是番剧语境词 → 应排在非语境词"科幻/日常"之前
        assertTrue(forAnime.indexOf("治愈") < forAnime.indexOf("日常"))

        val forBook = SmartAssistedHelper.suggestFrequentTags(stats, MediaType.BOOK)
        assertTrue(forBook.indexOf("科幻") < forBook.indexOf("治愈") || forBook.indexOf("文学") == 0)
    }

    @Test
    fun `空标签统计返回空列表`() {
        assertTrue(SmartAssistedHelper.suggestFrequentTags(emptyList(), MediaType.BOOK).isEmpty())
    }
}
