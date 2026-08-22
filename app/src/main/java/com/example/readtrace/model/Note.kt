package com.example.readtrace.model

data class Note(
    val id: Long = 0,
    val bookId: Long,
    val content: String,
    val noteType: NoteType = NoteType.NOTE,
    val page: String? = null,
    val chapter: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val isDeleted: Boolean = false,
    val deletedAt: String? = null,
)
