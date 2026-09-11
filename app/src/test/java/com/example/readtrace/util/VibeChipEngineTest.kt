package com.example.readtrace.util

import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class VibeChipEngineTest {

    @Test
    fun testVibeChipsCoverageForAllMediaTypes() {
        val mediaTypes = listOf(
            MediaType.BOOK,
            MediaType.MOVIE,
            MediaType.ANIME,
            MediaType.GAME,
            MediaType.MUSIC
        )

        mediaTypes.forEach { mediaType ->
            val chips = VibeChipEngine.getVibeChips(mediaType)
            assertEquals("每个媒介类型应具备 6 个预设美学情绪胶囊", 6, chips.size)
            chips.forEach { chip ->
                assertTrue("胶囊标签应以#开头", chip.tag.startsWith("#"))
                assertTrue("胶囊文本应非空", chip.label.isNotBlank())
                assertTrue("胶囊emoji应非空", chip.emoji.isNotBlank())
            }
        }
    }

    @Test
    fun testFindChipAndNormalize() {
        val chipWithHash = VibeChipEngine.findChip("#硬核烧脑", MediaType.BOOK)
        val chipWithoutHash = VibeChipEngine.findChip("硬核烧脑", MediaType.BOOK)

        assertNotNull("带#应能检索到胶囊", chipWithHash)
        assertNotNull("不带#应能归一化检索到胶囊", chipWithoutHash)
        assertEquals(chipWithHash?.tag, chipWithoutHash?.tag)

        assertTrue("应当识别为合法的 VibeChip", VibeChipEngine.isVibeChip("#后劲极大"))
        assertTrue("不带#亦应识别为合法 VibeChip", VibeChipEngine.isVibeChip("神作跪拜"))
        assertFalse("非预设标签不应识别为 VibeChip", VibeChipEngine.isVibeChip("普通标签"))
    }

    @Test
    fun testCalculateAdjustedMindprintAndClamping() {
        val base = BookMindprint(
            bookId = 1L,
            depthScore = 7.0,
            artistryScore = 7.0,
            emotionScore = 7.0,
            logicScore = 7.0,
            difficultyScore = 5.0,
            healingScore = 7.0
        )

        // 注入电影分类的「#后劲极大」和「#催泪暴击」
        val active = listOf("#后劲极大", "#催泪暴击")
        val adjusted = VibeChipEngine.calculateAdjustedMindprint(base, active, MediaType.MOVIE)

        // 后劲极大: depthDelta 1.2, emotionDelta 1.8, healingDelta -0.6
        // 催泪暴击: depthDelta 0.6, emotionDelta 2.0, healingDelta 0.6
        // emotionDelta 合计 +3.8 -> 7.0 + 3.8 = 10.8 -> 应该被截断至 10.0
        assertEquals(10.0, adjusted.emotionScore, 0.001)
        assertEquals(8.8, adjusted.depthScore, 0.001)

        // 验证下限截断：应用多次负向标签不能低于 1.0
        val negativeChips = List(10) { "#致郁神作" } // healingDelta = -2.0
        val extremeNegative = VibeChipEngine.calculateAdjustedMindprint(base, negativeChips, MediaType.ANIME)
        assertTrue("下界应被截断在 1.0", extremeNegative.healingScore >= 1.0)
    }

    @Test
    fun testApplyChipStepSymmetry() {
        val base = BookMindprint(
            bookId = 2L,
            depthScore = 6.0,
            artistryScore = 6.0,
            emotionScore = 6.0,
            logicScore = 6.0,
            difficultyScore = 5.0,
            healingScore = 6.0
        )

        val afterAdd = VibeChipEngine.applyChipStep(base, "#硬核烧脑", isAdd = true, mediaType = MediaType.BOOK)
        assertNotEquals(base.logicScore, afterAdd.logicScore)

        val afterRemove = VibeChipEngine.applyChipStep(afterAdd, "#硬核烧脑", isAdd = false, mediaType = MediaType.BOOK)
        assertEquals(base.logicScore, afterRemove.logicScore, 0.001)
        assertEquals(base.difficultyScore, afterRemove.difficultyScore, 0.001)
    }

    @Test
    fun testCreateInitialMindprintAnchor() {
        val mindprint = VibeChipEngine.createInitialMindprint(
            bookId = 42L,
            activeChips = listOf("#枕边治愈"),
            mediaType = MediaType.BOOK,
            baseRating = 9.0
        )

        assertEquals(42L, mindprint.bookId)
        assertTrue("治愈标签应使 healingScore 显著提升", mindprint.healingScore > 9.0)
    }
}
