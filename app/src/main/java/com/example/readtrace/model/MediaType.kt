package com.example.readtrace.model

enum class MediaType(
    val databaseValue: String,
    val displayName: String,
    val emoji: String,
    val creatorLabel: String,
    val creatorHint: String,
    val progressLabel: String,
    val wishlistLabel: String,
    val ongoingLabel: String,
    val finishedLabel: String,
    val pausedLabel: String,
    val droppedLabel: String,
) {
    BOOK(
        databaseValue = "book",
        displayName = "书籍",
        emoji = "📖",
        creatorLabel = "作者 / 译者",
        creatorHint = "例如：村上春树、加缪",
        progressLabel = "页码 / 章节",
        wishlistLabel = "想读",
        ongoingLabel = "在读",
        finishedLabel = "已读",
        pausedLabel = "暂停",
        droppedLabel = "弃读",
    ),
    MOVIE(
        databaseValue = "movie",
        displayName = "影视",
        emoji = "🎬",
        creatorLabel = "导演 / 主演",
        creatorHint = "例如：克里斯托弗·诺兰、宫崎骏",
        progressLabel = "季集 / 时长",
        wishlistLabel = "想看",
        ongoingLabel = "在看",
        finishedLabel = "已看",
        pausedLabel = "搁置",
        droppedLabel = "弃剧",
    ),
    GAME(
        databaseValue = "game",
        displayName = "游戏",
        emoji = "🎮",
        creatorLabel = "制作人 / 开发商",
        creatorHint = "例如：宫崎英高、任天堂",
        progressLabel = "游玩进度 / 成就",
        wishlistLabel = "想玩",
        ongoingLabel = "在玩",
        finishedLabel = "通关",
        pausedLabel = "封盘",
        droppedLabel = "弃坑",
    ),
    PODCAST(
        databaseValue = "podcast",
        displayName = "播客",
        emoji = "🎙️",
        creatorLabel = "主播 / 频道",
        creatorHint = "例如：忽左忽右、声东击西",
        progressLabel = "单集 / 时间点",
        wishlistLabel = "想听",
        ongoingLabel = "在听",
        finishedLabel = "听完",
        pausedLabel = "暂停",
        droppedLabel = "弃听",
    );

    fun getStatusLabel(status: BookStatus): String = when (status) {
        BookStatus.WISHLIST -> wishlistLabel
        BookStatus.READING -> ongoingLabel
        BookStatus.FINISHED -> finishedLabel
        BookStatus.PAUSED -> pausedLabel
        BookStatus.DROPPED -> droppedLabel
    }

    companion object {
        fun fromDatabaseValue(value: String?): MediaType =
            values().firstOrNull { it.databaseValue.equals(value, ignoreCase = true) } ?: BOOK
    }
}
