package com.example.readtrace.model

data class BookOutline(
    val id: Long = 0,
    val bookId: Long,
    val chapterOrder: Int = 1,
    val title: String,
    val summary: String,
    val keyTakeaways: String? = null,
    val createdAt: String = "",
    val isDeleted: Boolean = false,
)
