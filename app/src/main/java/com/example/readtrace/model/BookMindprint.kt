package com.example.readtrace.model

data class BookMindprint(
    val id: Long = 0,
    val bookId: Long,
    val depthScore: Double = 8.0,       // 思想深度 (1.0 ~ 10.0)
    val artistryScore: Double = 8.0,    // 文笔意境 (1.0 ~ 10.0)
    val emotionScore: Double = 8.0,     // 情感共鸣 (1.0 ~ 10.0)
    val logicScore: Double = 8.0,       // 逻辑构架 (1.0 ~ 10.0)
    val difficultyScore: Double = 5.0,  // 阅读阻力 (1.0 ~ 10.0)
    val healingScore: Double = 8.0,     // 心灵治愈 (1.0 ~ 10.0)
    val updatedAt: String = "",
) {
    /**
     * 计算综合心智均分
     */
    fun averageScore(): Double =
        (depthScore + artistryScore + emotionScore + logicScore + healingScore) / 5.0
}
