package com.example.readtrace.model

data class BookLocation(
    val id: Long = 0,
    val bookId: Long,
    val name: String,
    val locationType: String = "🏙️ 现实都市",
    val description: String? = null,
    val significance: String? = null,
    val coordinates: String? = null,
    val createdAt: String = "",
    val isDeleted: Boolean = false,
)
