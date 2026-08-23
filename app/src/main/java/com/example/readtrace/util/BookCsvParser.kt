package com.example.readtrace.util

import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object BookCsvParser {

    /**
     * 解析 CSV 输入流，返回解析出的待导入书籍列表。
     * CSV 格式要求：每一行为 "书名,作者"，第一行若是表头则自动跳过。
     */
    fun parse(inputStream: InputStream): List<Book> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val books = mutableListOf<Book>()

        reader.useLines { lines ->
            var isFirstLine = true
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                val parts = parseCsvLine(line)
                if (parts.isEmpty()) continue

                val title = parts[0].trim()
                val author = if (parts.size > 1) parts[1].trim().takeIf { it.isNotEmpty() } else null

                // 如果首行是表头（包含"书名"或"title"），则跳过
                if (isFirstLine && (title.equals("书名", ignoreCase = true) || title.equals("title", ignoreCase = true))) {
                    isFirstLine = false
                    continue
                }
                isFirstLine = false

                if (title.isNotEmpty()) {
                    books.add(
                        Book(
                            id = 0L,
                            title = title,
                            author = author,
                            coverUrl = null,
                            category = null,
                            status = BookStatus.WISHLIST,
                            rating = null,
                            tags = emptyList(),
                            shortComment = null,
                            review = null,
                            startDate = null,
                            finishDate = null,
                            createdAt = "",
                            updatedAt = "",
                            isDeleted = false,
                            deletedAt = null,
                        ),
                    )
                }
            }
        }
        return books
    }

    /**
     * 基础 CSV 单行解析，支持逗号分隔与双引号包裹
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when (ch) {
                '"' -> inQuotes = !inQuotes
                ',' -> {
                    if (inQuotes) {
                        current.append(ch)
                    } else {
                        result.add(current.toString().trim())
                        current.setLength(0)
                    }
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString().trim())
        return result
    }
}
