package com.example.readtrace.util

import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SimilarBookRecommendation(
    val book: Book,
    val mindprint: BookMindprint,
    val similarityPercent: Int,
    val matchReason: String,
)

object BookSimilarityEngine {

    fun findSimilarBooks(
        targetBook: Book,
        databaseHelper: BookDatabaseHelper,
        limit: Int = 2,
    ): List<SimilarBookRecommendation> {
        val allBooks = databaseHelper.getCachedBooks().filter { it.id != targetBook.id }
        if (allBooks.isEmpty()) return emptyList()

        val allMindprints = databaseHelper.getAllMindprints()
        val targetMp = allMindprints[targetBook.id] ?: databaseHelper.getMindprint(targetBook.id)
        val targetRegion = detectRegion(targetBook)

        val scored = allBooks.map { candidate ->
            val candidateMp = allMindprints[candidate.id] ?: BookMindprint(bookId = candidate.id)
            val candidateRegion = detectRegion(candidate)

            // 1. 六维心智欧氏空间距离相似度 (40% 权重)
            val dDepth = targetMp.depthScore - candidateMp.depthScore
            val dArt = targetMp.artistryScore - candidateMp.artistryScore
            val dEmo = targetMp.emotionScore - candidateMp.emotionScore
            val dLog = targetMp.logicScore - candidateMp.logicScore
            val dDiff = targetMp.difficultyScore - candidateMp.difficultyScore
            val dHeal = targetMp.healingScore - candidateMp.healingScore

            val dist = sqrt(dDepth * dDepth + dArt * dArt + dEmo * dEmo + dLog * dLog + dDiff * dDiff + dHeal * dHeal)
            val mindSim = (1.0 - (dist / 24.5)).coerceIn(0.0, 1.0)

            // 2. 媒介类别与标签相似度 (35% 权重)
            var categorySim = 0.0
            if (candidate.mediaType == targetBook.mediaType) categorySim += 0.5
            if (!targetBook.category.isNullOrBlank() && candidate.category.equals(targetBook.category, ignoreCase = true)) {
                categorySim += 0.5
            } else {
                val commonTags = targetBook.tags.intersect(candidate.tags.toSet())
                if (commonTags.isNotEmpty()) categorySim += 0.3
            }

            // 3. 作者国籍与文化源流相似度 (25% 权重)
            val regionSim = if (candidateRegion == targetRegion) 1.0 else 0.2

            val totalScore = (mindSim * 0.40) + (categorySim * 0.35) + (regionSim * 0.25)
            val percent = (totalScore * 100).roundToInt().coerceIn(65, 99)

            val reason = buildString {
                if (candidateRegion == targetRegion && targetRegion != "世界经典") {
                    append("同属${targetRegion} · ")
                } else if (candidate.mediaType == targetBook.mediaType) {
                    append("同为${targetBook.mediaType.displayName} · ")
                }
                if (mindSim >= 0.85) {
                    append("六维心智特质极度契合")
                } else if (dEmo * dEmo <= 1.0) {
                    append("情感共鸣与心灵共振高度同频")
                } else {
                    append("思辨哲理与审美构架同频共振")
                }
            }

            SimilarBookRecommendation(
                book = candidate,
                mindprint = candidateMp,
                similarityPercent = percent,
                matchReason = reason,
            )
        }

        return scored.sortedByDescending { it.similarityPercent }.take(limit)
    }

    fun detectRegion(book: Book): String {
        val author = book.author ?: ""
        val tags = book.tags.joinToString(" ")
        val category = book.category ?: ""
        val all = "$author $tags $category ${book.title}"

        return when {
            listOf("村上春树", "川端康成", "东野圭吾", "太宰治", "三岛由纪夫", "夏目漱石", "日本").any { all.contains(it) } -> "日本文学"
            listOf("马尔克斯", "加西亚", "博尔赫斯", "科塔萨尔", "略萨", "拉美", "哥伦比亚", "阿根廷").any { all.contains(it) } -> "拉美文学"
            listOf("鲁迅", "老舍", "莫言", "余华", "钱钟书", "刘慈欣", "王小波", "史铁生", "中国", "华语").any { all.contains(it) } -> "华语经典"
            listOf("托尔斯泰", "陀思妥耶夫斯基", "契诃夫", "高尔基", "俄国", "俄罗斯").any { all.contains(it) } -> "俄苏文学"
            listOf("海明威", "菲茨杰拉德", "乔治·奥威尔", "毛姆", "卡夫卡", "莎士比亚", "欧美", "法国", "英国", "美国", "德国").any { all.contains(it) } -> "欧美名著"
            else -> "世界经典"
        }
    }
}
