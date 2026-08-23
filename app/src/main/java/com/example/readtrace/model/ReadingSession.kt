package com.example.readtrace.model

data class ReadingSession(
    val id: Long = 0,
    val bookId: Long,
    val durationMinutes: Int,
    val pagesRead: String? = null,
    val thought: String? = null,
    val createdAt: String = "",
    val isDeleted: Boolean = false,
)
