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
    var likeCount: Int = 0,
    var isLiked: Boolean = false,
    val commentCount: Int = 0,
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
