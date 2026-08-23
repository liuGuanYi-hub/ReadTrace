package com.example.readtrace.util

import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.MilestoneBadge
import kotlin.math.min

object MilestoneBadgeHelper {

    fun calculateBadges(databaseHelper: BookDatabaseHelper): List<MilestoneBadge> {
        val finishedCount = databaseHelper.getTotalFinishedBooksCount()
        val totalBooksCount = databaseHelper.getTotalBooksCount()
        val notesCount = databaseHelper.getTotalNotesCount()
        val categoriesCount = databaseHelper.getUniqueCategoriesCount()
        val highRatingCount = databaseHelper.getHighRatingBooksCount()

        return listOf(
            // 维度一：书卷浩瀚 (已读书籍)
            createBadge(
                id = "finish_1",
                category = "书卷浩瀚",
                title = "初涉书海",
                description = "读完第 1 本书，迈出阅读旅程的第一步",
                iconEmoji = "📖",
                current = finishedCount,
                max = 1,
            ),
            createBadge(
                id = "finish_5",
                category = "书卷浩瀚",
                title = "渐入佳境",
                description = "累计读完 5 本书籍，沉浸于阅读心流",
                iconEmoji = "📚",
                current = finishedCount,
                max = 5,
            ),
            createBadge(
                id = "finish_10",
                category = "书卷浩瀚",
                title = "卷帙浩繁",
                description = "累计读完 10 本书籍，知识蔚然成林",
                iconEmoji = "🏛️",
                current = finishedCount,
                max = 10,
            ),
            createBadge(
                id = "finish_30",
                category = "书卷浩瀚",
                title = "阅尽沧桑",
                description = "累计读完 30 本经典，见天地与众生",
                iconEmoji = "👑",
                current = finishedCount,
                max = 30,
            ),

            // 维度二：吉光片羽 (摘录笔记)
            createBadge(
                id = "notes_1",
                category = "吉光片羽",
                title = "第一声回响",
                description = "记录第 1 条阅读摘录或随想",
                iconEmoji = "✍️",
                current = notesCount,
                max = 1,
            ),
            createBadge(
                id = "notes_10",
                category = "吉光片羽",
                title = "妙笔留痕",
                description = "累计摘录与沉淀 10 条深刻字句",
                iconEmoji = "📝",
                current = notesCount,
                max = 10,
            ),
            createBadge(
                id = "notes_50",
                category = "吉光片羽",
                title = "思想宝库",
                description = "累计沉淀 50 条摘录随想，汇聚智慧海洋",
                iconEmoji = "💡",
                current = notesCount,
                max = 50,
            ),

            // 维度三：见微知著 (品味广度)
            createBadge(
                id = "books_10",
                category = "见微知著",
                title = "藏书万卷",
                description = "书架收录超过 10 本书籍",
                iconEmoji = "🔖",
                current = totalBooksCount,
                max = 10,
            ),
            createBadge(
                id = "categories_3",
                category = "见微知著",
                title = "百家争鸣",
                description = "涉猎覆盖 3 个及以上不同书籍分类",
                iconEmoji = "🧭",
                current = categoriesCount,
                max = 3,
            ),
            createBadge(
                id = "high_rating_5",
                category = "见微知著",
                title = "慧眼识珠",
                description = "为 5 本书籍打出 9.0 分以上的至臻好评",
                iconEmoji = "🌟",
                current = highRatingCount,
                max = 5,
            ),
        )
    }

    private fun createBadge(
        id: String,
        category: String,
        title: String,
        description: String,
        iconEmoji: String,
        current: Int,
        max: Int,
    ): MilestoneBadge {
        val safeCurrent = min(current, max)
        return MilestoneBadge(
            id = id,
            category = category,
            title = title,
            description = description,
            iconEmoji = iconEmoji,
            currentProgress = safeCurrent,
            maxProgress = max,
            isUnlocked = current >= max,
        )
    }
}
