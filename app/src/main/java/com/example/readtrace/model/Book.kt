package com.example.readtrace.model

data class Book(
    val id: Long = 0,
    val title: String,
    val author: String? = null,
    val coverUrl: String? = null,
    val category: String? = null,
    val status: BookStatus = BookStatus.WISHLIST,
    val mediaType: MediaType = MediaType.BOOK,
    val rating: Double? = null,
    val tags: List<String> = emptyList(),
    val shortComment: String? = null,
    val review: String? = null,
    val startDate: String? = null,
    val finishDate: String? = null,
    val buyChannel: String? = null,
    val shelfLocation: String? = null,
    val bindingType: String? = null,
    val buyPrice: Double? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val isDeleted: Boolean = false,
    val deletedAt: String? = null,
    // 外部导入来源（v4.2.14）：'bangumi' 等，NULL 视为手动录入；source_id 为来源条目唯一 ID
    val sourceType: String? = null,
    val sourceId: String? = null,
    // 远程评分（如 Bangumi 全站评分 0~10），与个人评分 rating 分离互不覆盖
    val remoteRating: Double? = null,
    // 简介正文（外部导入填充，用户可自行修改）
    val description: String? = null,
)
