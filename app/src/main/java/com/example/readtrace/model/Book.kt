package com.example.readtrace.model

data class Book(
    val id: Long = 0,
    val title: String,
    val author: String? = null,
    val coverUrl: String? = null,
    val category: String? = null,
    val status: BookStatus = BookStatus.WISHLIST,
    val rating: Double? = null,
    val tags: List<String> = emptyList(),
    val shortComment: String? = null,
    val review: String? = null,
    val startDate: String? = null,
    val finishDate: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val isDeleted: Boolean = false,
    val deletedAt: String? = null,
)
