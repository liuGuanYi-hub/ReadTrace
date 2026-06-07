package com.example.readtrace.model

enum class BookStatus(
    val databaseValue: String,
    val displayName: String,
) {
    WISHLIST("wishlist", "想读"),
    READING("reading", "在读"),
    FINISHED("finished", "已读"),
    PAUSED("paused", "暂停"),
    DROPPED("dropped", "弃读");

    companion object {
        fun fromDatabaseValue(value: String): BookStatus =
            values().firstOrNull { it.databaseValue == value } ?: WISHLIST
    }
}
