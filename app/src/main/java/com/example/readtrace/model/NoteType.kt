package com.example.readtrace.model

enum class NoteType(
    val databaseValue: String,
    val displayName: String,
) {
    QUOTE("quote", "摘录"),
    NOTE("note", "随想");

    companion object {
        fun fromDatabaseValue(value: String): NoteType =
            entries.firstOrNull { it.databaseValue == value } ?: NOTE
    }
}
