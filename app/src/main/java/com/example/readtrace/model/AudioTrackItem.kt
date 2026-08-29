package com.example.readtrace.model

/**
 * 本地音频曲目：与某部音乐作品（Book, mediaType=MUSIC）关联的真实可播放文件
 */
data class AudioTrackItem(
    val id: Long = 0,
    val bookId: Long,
    val trackOrder: Int,
    val title: String,
    val fileUri: String,
    val durationMs: Long = 0,
)
