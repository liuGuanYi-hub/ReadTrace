package com.example.readtrace.util

import com.example.readtrace.model.MediaType
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 📐 跨媒介多维度加权微评分计算引擎 (DimensionalScoringEngine)
 *
 * 将粗暴的单一评分拆解为媒介对应的 4 大核心维度，自动加权计算出精确到 0.1 分的高精评分。
 */
object DimensionalScoringEngine {

    data class DimensionItem(
        val key: String,
        val name: String,
        val weight: Double,
        var score: Double = 8.0, // 默认 8.0 (0.0 ~ 10.0)
    )

    data class DimensionalScoreResult(
        val totalScore: Double,
        val tierLabel: String,
        val shortTierLabel: String,
        val dimensions: List<DimensionItem>,
    )

    /**
     * 根据媒介类型获取 4 大核心评估维度及权重
     */
    fun getDimensionsForMediaType(mediaType: MediaType, initialScore: Double = 8.0): List<DimensionItem> {
        val baseScore = initialScore.coerceIn(0.0, 10.0)
        return when (mediaType) {
            MediaType.ANIME -> listOf(
                DimensionItem("visual", "视听画风", 0.25, baseScore),
                DimensionItem("script", "剧情编排", 0.30, baseScore),
                DimensionItem("character", "人设塑造", 0.25, baseScore),
                DimensionItem("emotion", "情绪后劲", 0.20, baseScore),
            )
            MediaType.BOOK -> listOf(
                DimensionItem("writing", "文笔表达", 0.25, baseScore),
                DimensionItem("depth", "思想深度", 0.30, baseScore),
                DimensionItem("rhythm", "结构节奏", 0.25, baseScore),
                DimensionItem("resonance", "情感共鸣", 0.20, baseScore),
            )
            MediaType.MOVIE -> listOf(
                DimensionItem("aesthetic", "镜头美学", 0.25, baseScore),
                DimensionItem("script", "剧本叙事", 0.30, baseScore),
                DimensionItem("acting", "演技演出", 0.25, baseScore),
                DimensionItem("audio", "视听配乐", 0.20, baseScore),
            )
            MediaType.GAME -> listOf(
                DimensionItem("gameplay", "核心玩法", 0.35, baseScore),
                DimensionItem("art", "音画美工", 0.25, baseScore),
                DimensionItem("narrative", "剧情演出", 0.20, baseScore),
                DimensionItem("immersion", "综合沉浸", 0.20, baseScore),
            )
            MediaType.MUSIC -> listOf(
                DimensionItem("melody", "旋律编曲", 0.35, baseScore),
                DimensionItem("lyrics", "意境词作", 0.30, baseScore),
                DimensionItem("vocal", "人声演绎", 0.20, baseScore),
                DimensionItem("loop", "循环耐听", 0.15, baseScore),
            )
        }
    }

    /**
     * 加权合成总分（四舍五入精确到 0.1 分）
     */
    fun calculateWeightedScore(dimensions: List<DimensionItem>): Double {
        if (dimensions.isEmpty()) return 8.0
        var weightedSum = 0.0
        var totalWeight = 0.0
        for (item in dimensions) {
            weightedSum += item.score * item.weight
            totalWeight += item.weight
        }
        val raw = if (totalWeight > 0.0) weightedSum / totalWeight else 8.0
        // 精确到 0.1
        return (raw * 10.0).roundToInt() / 10.0
    }

    fun evaluate(dimensions: List<DimensionItem>): DimensionalScoreResult {
        val score = calculateWeightedScore(dimensions)
        return DimensionalScoreResult(
            totalScore = score,
            tierLabel = getTierLabel(score),
            shortTierLabel = getShortTierLabel(score),
            dimensions = dimensions,
        )
    }

    /**
     * 10 分制感官定性详细评语
     */
    fun getTierLabel(score: Double): String = when {
        score >= 9.6 -> "✦ 传世殿堂 · 改变心智维度的精神瑰宝"
        score >= 9.0 -> "✦ 破圈神作 · 触及同类题材天花板"
        score >= 8.5 -> "✦ 惊艳佳作 · 完成度极高，极度推荐"
        score >= 8.0 -> "✦ 扎实良作 · 亮点鲜明，值得品味与重温"
        score >= 7.0 -> "✦ 尚可一览 · 偶有闪光点但存在明显短板"
        score >= 6.0 -> "✦ 平庸乏味 · 略显欠缺或硬伤较重"
        else -> "✦ 极度失望 · 叙事崩塌或粗制滥造"
    }

    /**
     * 10 分制感官定性简短标签
     */
    fun getShortTierLabel(score: Double): String = when {
        score >= 9.6 -> "传世殿堂"
        score >= 9.0 -> "破圈神作"
        score >= 8.5 -> "惊艳佳作"
        score >= 8.0 -> "扎实良作"
        score >= 7.0 -> "尚可一览"
        score >= 6.0 -> "平庸乏味"
        else -> "极度失望"
    }

    fun formatScore(score: Double?): String {
        if (score == null || score <= 0.0) return "未评分"
        return String.format(Locale.getDefault(), "%.1f", score)
    }
}
