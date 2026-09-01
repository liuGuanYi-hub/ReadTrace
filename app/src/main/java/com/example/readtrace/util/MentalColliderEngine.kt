package com.example.readtrace.util

import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import kotlin.math.abs

/**
 * 🧪 跨媒介思想炼金碰撞机 (MentalColliderEngine)
 *
 * P15：将书库中任意 2 部作品放入粒子引力碰撞槽，
 * 算法分析两者的六维心智重叠带与主题标签，提炼深层哲思共鸣纽带，
 * 生成「跨媒介哲学对话典藏微卡」。
 */
object MentalColliderEngine {

    /** 碰撞结果 */
    data class CollisionResult(
        val bookA: Book,
        val bookB: Book,
        val mindprintA: BookMindprint,
        val mindprintB: BookMindprint,
        /** 0~100 哲思契合度 */
        val resonance: Int,
        /** 共鸣纽带文案（如「两者都在探讨终极孤独中的自我救赎」） */
        val trait: String,
        /** 重叠度最高的心智维度名 */
        val dominantDimension: String,
    )

    private val DIMENSIONS = listOf<Pair<String, (BookMindprint) -> Double>>(
        "思想深度" to { it.depthScore },
        "文笔意境" to { it.artistryScore },
        "情感共鸣" to { it.emotionScore },
        "逻辑构架" to { it.logicScore },
        "阅读阻力" to { it.difficultyScore },
        "心灵治愈" to { it.healingScore },
    )

    /** 标签交集提取的哲思纽带（可选增强） */
    private fun tagBond(a: Book, b: Book): String? {
        val common = a.tags.intersect(b.tags.toSet())
        return when {
            common.isEmpty() -> null
            else -> "同属「${common.first()}」的精神谱系"
        }
    }

    /**
     * 碰撞两部作品的六维心智与主题标签
     */
    fun collide(
        bookA: Book,
        bookB: Book,
        mindprintA: BookMindprint,
        mindprintB: BookMindprint,
    ): CollisionResult {
        // 六维欧氏距离 → 契合度（与 BookSimilarityEngine 的归一化口径一致：最大距离 √6×10² ≈ 24.5）
        val dist = sqrtOf(
            (mindprintA.depthScore - mindprintB.depthScore),
            (mindprintA.artistryScore - mindprintB.artistryScore),
            (mindprintA.emotionScore - mindprintB.emotionScore),
            (mindprintA.logicScore - mindprintB.logicScore),
            (mindprintA.difficultyScore - mindprintB.difficultyScore),
            (mindprintA.healingScore - mindprintB.healingScore),
        )
        val mindSim = (1.0 - dist / 24.5).coerceIn(0.0, 1.0)

        // 标签契合加成
        val tagSim = if (bookA.tags.isEmpty() || bookB.tags.isEmpty()) 0.0
        else bookA.tags.intersect(bookB.tags.toSet()).size.toDouble() / maxOf(bookA.tags.size, bookB.tags.size).coerceAtLeast(1)

        val resonance = ((mindSim * 0.75 + tagSim * 0.25) * 100).toInt().coerceIn(62, 99)

        // 找出重叠带最窄（差异最小）的维度作为主导纽带
        val dominant = DIMENSIONS
            .map { (name, getter) -> Triple(name, getter(mindprintA), getter(mindprintB)) }
            .minByOrNull { abs(it.second - it.third) }
            ?.first
            ?: "思想深度"

        val trait = tagBond(bookA, bookB)
            ?: "两者都在${dominant}的维度上彼此映照，构成一场跨媒介的哲学对话"

        return CollisionResult(
            bookA = bookA,
            bookB = bookB,
            mindprintA = mindprintA,
            mindprintB = mindprintB,
            resonance = resonance,
            trait = trait,
            dominantDimension = dominant,
        )
    }

    private fun sqrtOf(vararg values: Double): Double =
        kotlin.math.sqrt(values.sumOf { it * it })
}
