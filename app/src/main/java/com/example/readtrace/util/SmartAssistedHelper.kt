package com.example.readtrace.util

import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.MediaType
import kotlin.math.roundToLong

/**
 * 🎛️ 全智能辅助填写引擎 (SmartAssistedHelper)
 *
 * P11 极简心流 Phase 2：
 * 1. 六维心智等比智能推导——根据用户给出的总评分与媒介类别，推导一组差异化的
 *    六维初始参数，无需用户手动拉动 6 个滑块；
 * 2. 高频标签词云——按全库标签频次与媒介语境给出点击即选的候选标签。
 */
object SmartAssistedHelper {

    /** 各媒介六维偏向表（在评分基线上叠加的语境偏移量） */
    private data class MediaBias(
        val depth: Double,
        val artistry: Double,
        val emotion: Double,
        val logic: Double,
        val difficulty: Double,
        val healing: Double,
    )

    private val MEDIA_BIAS = mapOf(
        MediaType.BOOK to MediaBias(depth = 1.0, artistry = 0.8, emotion = 0.6, logic = 0.5, difficulty = 0.8, healing = 0.0),
        MediaType.ANIME to MediaBias(depth = 0.8, artistry = 0.8, emotion = 0.8, logic = 0.0, difficulty = -0.5, healing = 0.8),
        MediaType.MOVIE to MediaBias(depth = 0.5, artistry = 0.5, emotion = 0.5, logic = 0.3, difficulty = -0.5, healing = 0.3),
        MediaType.GAME to MediaBias(depth = 0.0, artistry = 0.2, emotion = 0.2, logic = 1.0, difficulty = 1.5, healing = -0.5),
        MediaType.MUSIC to MediaBias(depth = -0.5, artistry = 0.6, emotion = 1.2, logic = -0.5, difficulty = -0.8, healing = 1.0),
    )

    private fun clamp1to10(v: Double): Double = v.coerceIn(1.0, 10.0)

    private fun round1(v: Double): Double = (v * 10).roundToLong() / 10.0

    /**
     * 六维心智一键智能生成
     * @param rating 用户给出的总评分（1.0 ~ 10.0）
     * @param mediaType 作品媒介类别
     */
    fun deriveMindprint(rating: Double, mediaType: MediaType): BookMindprint {
        val r = rating.coerceIn(1.0, 10.0)
        val bias = MEDIA_BIAS.getValue(mediaType)

        // 评分越高，各维整体水位越高；媒介偏向做差异化拉伸
        val depth = clamp1to10(r * 0.9 + bias.depth)
        val artistry = clamp1to10(r * 0.9 + bias.artistry)
        val emotion = clamp1to10(r * 0.9 + bias.emotion)
        val logic = clamp1to10(r * 0.9 + bias.logic)
        // 阅读阻力以 5.0 为中性锚点：评分提升略增阻力感知，媒介偏向主导
        val difficulty = clamp1to10(5.0 + bias.difficulty + (r - 5.0) * 0.3)
        val healing = clamp1to10(r * 0.8 + bias.healing + 1.0)

        return BookMindprint(
            bookId = 0,
            depthScore = round1(depth),
            artistryScore = round1(artistry),
            emotionScore = round1(emotion),
            logicScore = round1(logic),
            difficultyScore = round1(difficulty),
            healingScore = round1(healing),
        )
    }

    /**
     * 高频标签词云候选：从全库标签频次统计中取 Top N，
     * 媒介语境标签（与当前媒介同类高频）优先排前。
     * @param tagStats 全库标签频次（降序或不排序均可）
     * @param mediaType 当前媒介，用于语境加权
     * @param limit 候选数量上限
     */
    fun suggestFrequentTags(
        tagStats: List<Pair<String, Int>>,
        mediaType: MediaType,
        limit: Int = 10,
    ): List<String> {
        if (tagStats.isEmpty()) return emptyList()
        val contextKeywords = when (mediaType) {
            MediaType.BOOK -> listOf("文学", "小说", "科幻", "历史", "哲学")
            MediaType.ANIME -> listOf("番剧", "治愈", "热血", "奇幻")
            MediaType.MOVIE -> listOf("电影", "悬疑", "剧情", "经典")
            MediaType.GAME -> listOf("游戏", "RPG", "独立游戏", "冒险")
            MediaType.MUSIC -> listOf("音乐", "摇滚", "民谣", "电子")
        }

        return tagStats
            .asSequence()
            .map { it.first.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { tag ->
                // 语境标签加权置顶，其余按原始频次
                (if (contextKeywords.any { tag.contains(it) }) 1_000_000 else 0) + (tagStats.firstOrNull { it.first == tag }?.second ?: 0)
            }
            .take(limit)
            .toList()
    }
}
