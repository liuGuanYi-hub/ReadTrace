package com.example.readtrace.community.model

import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType

data class CommunityExhibition(
    val id: String,
    val authorName: String,
    val authorAvatar: String = "🦉",
    val title: String,
    val themeDescription: String,
    val curatedBooks: List<Book>,
    val tags: List<String> = emptyList(),
    /**
     * **编辑部推荐指数**（0~100），由策展方给出，**不是用户点赞计数**。
     *
     * V3「访客与统计真实化」：该字段原先展示为「🔥 382 共鸣」，读起来像
     * "382 位用户点过共鸣"，但它其实是内置种子的虚构基础值 + 用户自己的 0/1 增量
     * —— 属于**无法溯源的社交证明**。现改口径为「编辑部推荐指数」并使其
     * **不随用户行为变化**；用户的共鸣状态单独由 [isLiked] 承载。
     */
    var likeCount: Int = 0,
    var isLiked: Boolean = false,
    var commentCount: Int = 0,
    val createdAt: String,
    val featuredTheme: String = "星空漫想",
)

data class CommunityComment(
    val id: String,
    val exhibitionId: String,
    val userName: String,
    val userAvatar: String = "🌿",
    val content: String,
    val createdAt: String,
)
