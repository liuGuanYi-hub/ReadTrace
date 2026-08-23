package com.example.readtrace.model

data class MilestoneBadge(
    val id: String,
    val category: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val currentProgress: Int,
    val maxProgress: Int,
    val isUnlocked: Boolean,
)
