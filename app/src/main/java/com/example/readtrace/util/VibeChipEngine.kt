package com.example.readtrace.util

import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.MediaType

/**
 * 🔮 美学情绪胶囊引擎 (VibeChipEngine)
 *
 * 为不同媒介（书、影、剧、游、音）预设高频美学情绪胶囊（Vibe Chips），
 * 并建立情绪标签与六维心智雷达图（思想深度、文笔意境、情感共鸣、逻辑构架、阅读门槛、心灵治愈）的动态双向映射推算：
 * 1. 选中标签时，根据预设的情绪张力与维度投影矩阵实时向心智雷达增量注入；
 * 2. 反选标签时，平滑回退影响量，支持动态实时多胶囊叠加强化；
 * 3. 一键速记时，直接依据选中的情绪胶囊矩阵生成初始心智雷达画像。
 */
object VibeChipEngine {

    data class VibeChip(
        val tag: String,              // 如 "#后劲极大"
        val label: String,            // 如 "后劲极大"
        val emoji: String,            // 如 "🌊"
        val depthDelta: Double = 0.0,
        val artistryDelta: Double = 0.0,
        val emotionDelta: Double = 0.0,
        val logicDelta: Double = 0.0,
        val difficultyDelta: Double = 0.0,
        val healingDelta: Double = 0.0,
    ) {
        val displayTag: String get() = if (tag.startsWith("#")) tag else "#$tag"
        val chipText: String get() = "$emoji $label"
    }

    private val MEDIA_VIBE_CHIPS: Map<MediaType, List<VibeChip>> = mapOf(
        MediaType.BOOK to listOf(
            VibeChip("#硬核烧脑", "硬核烧脑", "🧠", depthDelta = 1.4, artistryDelta = 0.2, emotionDelta = 0.0, logicDelta = 1.6, difficultyDelta = 1.6, healingDelta = -0.6),
            VibeChip("#枕边治愈", "枕边治愈", "🌿", depthDelta = -0.2, artistryDelta = 0.6, emotionDelta = 1.0, logicDelta = -0.4, difficultyDelta = -1.6, healingDelta = 1.8),
            VibeChip("#字字珠玑", "字字珠玑", "🖋️", depthDelta = 1.0, artistryDelta = 1.8, emotionDelta = 0.5, logicDelta = 0.6, difficultyDelta = 0.4, healingDelta = 0.2),
            VibeChip("#直击灵魂", "直击灵魂", "⚡", depthDelta = 1.6, artistryDelta = 0.8, emotionDelta = 1.6, logicDelta = 0.4, difficultyDelta = 0.6, healingDelta = 0.4),
            VibeChip("#荡气回肠", "荡气回肠", "📜", depthDelta = 1.0, artistryDelta = 1.2, emotionDelta = 1.6, logicDelta = 0.8, difficultyDelta = 0.5, healingDelta = 0.3),
            VibeChip("#轻松下饭", "轻松下饭", "🍵", depthDelta = -0.8, artistryDelta = 0.0, emotionDelta = 0.4, logicDelta = -0.6, difficultyDelta = -2.0, healingDelta = 1.4),
        ),
        MediaType.MOVIE to listOf(
            VibeChip("#后劲极大", "后劲极大", "🌊", depthDelta = 1.2, artistryDelta = 0.8, emotionDelta = 1.8, logicDelta = 0.4, difficultyDelta = 0.4, healingDelta = -0.6),
            VibeChip("#神级反转", "神级反转", "🎭", depthDelta = 0.8, artistryDelta = 0.6, emotionDelta = 0.8, logicDelta = 1.8, difficultyDelta = 0.5, healingDelta = -0.2),
            VibeChip("#视觉盛宴", "视觉盛宴", "🌌", depthDelta = 0.4, artistryDelta = 2.0, emotionDelta = 0.8, logicDelta = 0.2, difficultyDelta = -0.2, healingDelta = 0.5),
            VibeChip("#催泪暴击", "催泪暴击", "💧", depthDelta = 0.6, artistryDelta = 0.6, emotionDelta = 2.0, logicDelta = -0.2, difficultyDelta = 0.0, healingDelta = 0.6),
            VibeChip("#哲学隐喻", "哲学隐喻", "👁️", depthDelta = 2.0, artistryDelta = 1.2, emotionDelta = 0.6, logicDelta = 1.2, difficultyDelta = 1.8, healingDelta = -0.4),
            VibeChip("#全程高能", "全程高能", "⚡", depthDelta = 0.2, artistryDelta = 0.8, emotionDelta = 1.4, logicDelta = 1.0, difficultyDelta = -0.4, healingDelta = 0.4),
        ),
        MediaType.ANIME to listOf(
            VibeChip("#致郁神作", "致郁神作", "🥀", depthDelta = 1.5, artistryDelta = 1.0, emotionDelta = 1.8, logicDelta = 0.6, difficultyDelta = 0.8, healingDelta = -2.0),
            VibeChip("#热血燃爆", "热血燃爆", "🔥", depthDelta = 0.4, artistryDelta = 0.8, emotionDelta = 2.0, logicDelta = 0.2, difficultyDelta = -0.6, healingDelta = 1.2),
            VibeChip("#治愈日常", "治愈日常", "🌸", depthDelta = -0.4, artistryDelta = 0.8, emotionDelta = 1.0, logicDelta = -0.5, difficultyDelta = -1.8, healingDelta = 2.0),
            VibeChip("#作画封神", "作画封神", "🎨", depthDelta = 0.4, artistryDelta = 2.0, emotionDelta = 1.0, logicDelta = 0.2, difficultyDelta = 0.0, healingDelta = 0.6),
            VibeChip("#神展开", "神展开", "🌀", depthDelta = 1.0, artistryDelta = 0.6, emotionDelta = 1.2, logicDelta = 1.8, difficultyDelta = 0.6, healingDelta = -0.4),
            VibeChip("#青春共鸣", "青春共鸣", "🚲", depthDelta = 0.6, artistryDelta = 0.8, emotionDelta = 1.8, logicDelta = 0.2, difficultyDelta = -0.4, healingDelta = 1.4),
        ),
        MediaType.GAME to listOf(
            VibeChip("#电子阳痿解药", "电子阳痿解药", "💊", depthDelta = 0.8, artistryDelta = 1.2, emotionDelta = 1.8, logicDelta = 1.0, difficultyDelta = -0.6, healingDelta = 1.6),
            VibeChip("#神作跪拜", "神作跪拜", "👑", depthDelta = 1.8, artistryDelta = 1.8, emotionDelta = 1.6, logicDelta = 1.6, difficultyDelta = 0.8, healingDelta = 0.5),
            VibeChip("#硬核受苦", "硬核受苦", "⚔️", depthDelta = 0.8, artistryDelta = 0.8, emotionDelta = 1.0, logicDelta = 1.4, difficultyDelta = 2.6, healingDelta = -1.4),
            VibeChip("#叙事天花板", "叙事天花板", "📖", depthDelta = 1.6, artistryDelta = 1.6, emotionDelta = 1.8, logicDelta = 1.2, difficultyDelta = 0.4, healingDelta = 0.4),
            VibeChip("#沉浸感拉满", "沉浸感拉满", "🎧", depthDelta = 0.6, artistryDelta = 1.6, emotionDelta = 1.6, logicDelta = 1.0, difficultyDelta = 0.2, healingDelta = 0.8),
            VibeChip("#爽快解压", "爽快解压", "💥", depthDelta = -0.6, artistryDelta = 0.4, emotionDelta = 1.2, logicDelta = -0.4, difficultyDelta = -2.0, healingDelta = 1.6),
        ),
        MediaType.MUSIC to listOf(
            VibeChip("#颅内共潮", "颅内共潮", "🌊", depthDelta = 0.6, artistryDelta = 2.0, emotionDelta = 1.8, logicDelta = 0.2, difficultyDelta = 0.2, healingDelta = 0.8),
            VibeChip("#深夜emo", "深夜emo", "🌙", depthDelta = 0.8, artistryDelta = 1.2, emotionDelta = 2.0, logicDelta = -0.2, difficultyDelta = 0.4, healingDelta = -0.8),
            VibeChip("#温柔抚慰", "温柔抚慰", "☕", depthDelta = 0.2, artistryDelta = 0.8, emotionDelta = 1.4, logicDelta = -0.2, difficultyDelta = -1.6, healingDelta = 2.0),
            VibeChip("#单曲循环", "单曲循环", "🔁", depthDelta = 0.4, artistryDelta = 1.6, emotionDelta = 1.6, logicDelta = 0.4, difficultyDelta = -0.4, healingDelta = 1.2),
            VibeChip("#先锋实验", "先锋实验", "🧪", depthDelta = 1.2, artistryDelta = 2.0, emotionDelta = 0.6, logicDelta = 1.4, difficultyDelta = 1.8, healingDelta = -0.4),
            VibeChip("#热血鼓点", "热血鼓点", "🥁", depthDelta = 0.2, artistryDelta = 0.8, emotionDelta = 1.8, logicDelta = 0.4, difficultyDelta = -0.6, healingDelta = 1.4),
        ),
    )

    fun getVibeChips(mediaType: MediaType): List<VibeChip> =
        MEDIA_VIBE_CHIPS[mediaType] ?: MEDIA_VIBE_CHIPS[MediaType.BOOK].orEmpty()

    fun findChip(rawTag: String, mediaType: MediaType? = null): VibeChip? {
        val normalized = normalizeTag(rawTag)
        if (mediaType != null) {
            getVibeChips(mediaType).firstOrNull { normalizeTag(it.tag) == normalized }?.let { return it }
        }
        return MEDIA_VIBE_CHIPS.values.flatten().firstOrNull { normalizeTag(it.tag) == normalized }
    }

    fun isVibeChip(rawTag: String): Boolean {
        val normalized = normalizeTag(rawTag)
        return MEDIA_VIBE_CHIPS.values.flatten().any { normalizeTag(it.tag) == normalized }
    }

    fun normalizeTag(rawTag: String): String =
        rawTag.trim().removePrefix("#").trim()

    /**
     * 根据当前选中的全部 Vibe Chips，在基础分上重新计算出新的六维雷达分数
     */
    fun calculateAdjustedMindprint(
        base: BookMindprint,
        activeChips: Collection<String>,
        mediaType: MediaType,
    ): BookMindprint {
        var dDepth = 0.0
        var dArt = 0.0
        var dEmo = 0.0
        var dLogic = 0.0
        var dDiff = 0.0
        var dHeal = 0.0

        activeChips.forEach { raw ->
            val chip = findChip(raw, mediaType) ?: return@forEach
            dDepth += chip.depthDelta
            dArt += chip.artistryDelta
            dEmo += chip.emotionDelta
            dLogic += chip.logicDelta
            dDiff += chip.difficultyDelta
            dHeal += chip.healingDelta
        }

        // 基准分（若未定制，默认 7.5 左右基准）
        val baseDepth = if (base.depthScore in 7.9..8.1) 7.5 else base.depthScore
        val baseArt = if (base.artistryScore in 7.9..8.1) 7.5 else base.artistryScore
        val baseEmo = if (base.emotionScore in 7.9..8.1) 7.5 else base.emotionScore
        val baseLogic = if (base.logicScore in 7.9..8.1) 7.5 else base.logicScore
        val baseDiff = base.difficultyScore
        val baseHeal = if (base.healingScore in 7.9..8.1) 7.5 else base.healingScore

        return base.copy(
            depthScore = (baseDepth + dDepth).coerceIn(1.0, 10.0),
            artistryScore = (baseArt + dArt).coerceIn(1.0, 10.0),
            emotionScore = (baseEmo + dEmo).coerceIn(1.0, 10.0),
            logicScore = (baseLogic + dLogic).coerceIn(1.0, 10.0),
            difficultyScore = (baseDiff + dDiff).coerceIn(1.0, 10.0),
            healingScore = (baseHeal + dHeal).coerceIn(1.0, 10.0),
        )
    }

    /**
     * 单个胶囊勾选/反选时，对当前 Mindprint 应用单步增减
     */
    fun applyChipStep(
        current: BookMindprint,
        chipTag: String,
        isAdd: Boolean,
        mediaType: MediaType,
    ): BookMindprint {
        val chip = findChip(chipTag, mediaType) ?: return current
        val sign = if (isAdd) 1.0 else -1.0

        return current.copy(
            depthScore = (current.depthScore + chip.depthDelta * sign).coerceIn(1.0, 10.0),
            artistryScore = (current.artistryScore + chip.artistryDelta * sign).coerceIn(1.0, 10.0),
            emotionScore = (current.emotionScore + chip.emotionDelta * sign).coerceIn(1.0, 10.0),
            logicScore = (current.logicScore + chip.logicDelta * sign).coerceIn(1.0, 10.0),
            difficultyScore = (current.difficultyScore + chip.difficultyDelta * sign).coerceIn(1.0, 10.0),
            healingScore = (current.healingScore + chip.healingDelta * sign).coerceIn(1.0, 10.0),
        )
    }

    /**
     * 速记初始落库时，根据选中的情绪胶囊生成初始 Mindprint
     */
    fun createInitialMindprint(
        bookId: Long,
        activeChips: Collection<String>,
        mediaType: MediaType,
        baseRating: Double? = null,
    ): BookMindprint {
        val anchor = (baseRating ?: 8.0).coerceIn(6.0, 9.5)
        val initialBase = BookMindprint(
            bookId = bookId,
            depthScore = anchor,
            artistryScore = anchor,
            emotionScore = anchor,
            logicScore = anchor,
            difficultyScore = 5.0,
            healingScore = anchor,
        )
        return calculateAdjustedMindprint(initialBase, activeChips, mediaType)
    }
}
