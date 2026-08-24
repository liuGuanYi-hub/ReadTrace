package com.example.readtrace.model

data class ReadingPersona(
    val personaTitle: String,
    val personaDesc: String,
    val dominantDimension: String,
    val avgMindprint: BookMindprint,
    val finishedBooksCount: Int,
)
