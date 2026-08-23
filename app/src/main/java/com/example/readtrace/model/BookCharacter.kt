package com.example.readtrace.model

data class BookCharacter(
    val id: Long = 0,
    val bookId: Long,
    val name: String,
    val roleTitle: String? = null,
    val avatarEmoji: String = "👤",
    val description: String? = null,
    val relationship: String? = null,
    val createdAt: String = "",
    val isDeleted: Boolean = false,
)
