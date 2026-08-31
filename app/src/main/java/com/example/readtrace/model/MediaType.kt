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
    ANIME(
        databaseValue = "anime",
        displayName = "番剧",
        emoji = "🌸",
        creatorLabel = "监督 / 制作社 / 声优",
        creatorHint = "例如：庵野秀明、京阿尼、Ufotable、MAPPA",
        progressLabel = "话数 / 季度",
        wishlistLabel = "想追",
        ongoingLabel = "追番中",
        finishedLabel = "补完",
        pausedLabel = "搁置",
        droppedLabel = "弃番",
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
    MUSIC(
        databaseValue = "music",
        displayName = "音乐",
        emoji = "💿",
        creatorLabel = "歌手 / 乐队 / 厂牌",
        creatorHint = "例如：ヨルシカ、ずっと真夜中でいいのに。",
        progressLabel = "曲目 / 专辑",
        wishlistLabel = "想听",
        ongoingLabel = "在听",
        finishedLabel = "听完",
        pausedLabel = "搁置",
        droppedLabel = "弃听",
    );

    fun getStatusLabel(status: BookStatus): String = when (status) {
        BookStatus.WISHLIST -> wishlistLabel
        BookStatus.READING -> ongoingLabel
        BookStatus.FINISHED -> finishedLabel
        BookStatus.PAUSED -> pausedLabel
        BookStatus.DROPPED -> droppedLabel
    }

    fun getFinishedPastMemory(years: Long): String = when (this) {
        BOOK -> "${years} 年前的今天，你读完了这本书"
        ANIME -> "${years} 年前的今天，你补完了这部番剧"
        MOVIE -> "${years} 年前的今天，你看完了这部影视"
        GAME -> "${years} 年前的今天，你通关了这款游戏"
        MUSIC -> "${years} 年前的今天，你听完了这首音乐"
    }

    fun getFinishedTodayMemory(): String = when (this) {
        BOOK -> "今天读完的书籍，愿余味长存"
        ANIME -> "今天补完的番剧，愿余味长存"
        MOVIE -> "今天看完的影视，愿余味长存"
        GAME -> "今天通关的游戏，愿余味长存"
        MUSIC -> "今天听完的音乐，愿余音长存"
    }

    fun getRandomMemory(): String = when (this) {
        BOOK -> "时光漫忆 · 曾留在心里的书籍"
        ANIME -> "时光漫忆 · 曾留在心里的番剧"
        MOVIE -> "时光漫忆 · 曾留在心里的影视"
        GAME -> "时光漫忆 · 曾留在心里的游戏"
        MUSIC -> "时光漫忆 · 曾留在心里的音乐"
    }

    fun getDefaultQuote(): String = when (this) {
        BOOK -> "在这个快节奏的世界里，书籍是灵魂的避风港。"
        ANIME -> "在虚构的光影里，番剧给予我们真实的心动与力量。"
        MOVIE -> "电影发明了以后，人类的生命比起以前至少延长了三倍。"
        GAME -> "游戏是第九艺术，带我们体验未曾设想的人生。"
        MUSIC -> "当语言停滞之时，音乐才刚刚开始。"
    }

    companion object {
        fun fromDatabaseValue(value: String?): MediaType = when {
            // 历史版本中夜鹿/真夜中曲目曾存为 podcast，统一归入音乐，避免落到默认书籍
            value.equals("podcast", ignoreCase = true) -> MUSIC
            else -> values().firstOrNull { it.databaseValue.equals(value, ignoreCase = true) } ?: BOOK
        }
    }
}
