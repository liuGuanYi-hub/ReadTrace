package com.example.readtrace.util

import com.example.readtrace.model.Book
import java.time.LocalDate

/**
 * 🕯️ 那年今日 · 时光回溯引擎 (MemoryFlashbackEngine)
 *
 * 扫描全库 `finish_date` / `start_date`，每天计算「N 年前的今天」读完、
 * 开读或观影的作品，以典藏羊皮纸卡片在主页静谧唤醒历史精神印记。
 */
object MemoryFlashbackEngine {

    /** 一条历史记忆唤醒 */
    data class MemoryFlashback(
        val book: Book,
        val yearsAgo: Int,
        val isFinished: Boolean,
        /** 当时留下的高光短评（可能为空） */
        val quote: String?,
    )

    /** 截取日期的 MM-dd 部分；容忍 `2026-06-03` 与 ISO 时间戳两种存储格式 */
    private fun monthDayOf(date: String?): String? {
        val clean = date?.trim().orEmpty()
        if (clean.length < 10) return null
        val mmdd = clean.substring(5, 10)
        return if (Regex("""\d{2}-\d{2}""").matches(mmdd)) mmdd else null
    }

    private fun yearOf(date: String?): Int? = date?.trim()?.take(4)?.toIntOrNull()

    /**
     * 检索「那年今日」记忆：优先完读里程碑，其次开读足迹；
     * 按年份跨度降序（最久远的记忆最先被唤醒）。
     */
    fun findFlashbacks(books: List<Book>, today: LocalDate = LocalDate.now()): List<MemoryFlashback> {
        val todayMmDd = "%02d-%02d".format(today.monthValue, today.dayOfMonth)
        val thisYear = today.year

        val memories = mutableListOf<MemoryFlashback>()
        books.forEach { book ->
            if (book.isDeleted) return@forEach

            val finishMmDd = monthDayOf(book.finishDate)
            val finishYear = yearOf(book.finishDate)
            if (finishMmDd == todayMmDd && finishYear != null && finishYear < thisYear) {
                memories.add(
                    MemoryFlashback(
                        book = book,
                        yearsAgo = thisYear - finishYear,
                        isFinished = true,
                        quote = highlightQuote(book),
                    ),
                )
                return@forEach // 同一部作品只保留最有分量的一条记忆
            }

            val startMmDd = monthDayOf(book.startDate)
            val startYear = yearOf(book.startDate)
            if (startMmDd == todayMmDd && startYear != null && startYear < thisYear) {
                memories.add(
                    MemoryFlashback(
                        book = book,
                        yearsAgo = thisYear - startYear,
                        isFinished = false,
                        quote = book.shortComment?.takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        return memories.sortedWith(compareByDescending<MemoryFlashback> { it.yearsAgo }.thenBy { it.book.title })
    }

    /** 高光随想：优先短评；否则从 300+ 字长评中提炼金句（P14 SmartQuoteDigestHelper）；再退回首行 */
    private fun highlightQuote(book: Book): String? {
        book.shortComment?.takeIf { it.isNotBlank() }?.let { return it }
        SmartQuoteDigestHelper.digest(book.review)?.let { return it.quote }
        return book.review?.lineSequence()?.firstOrNull { it.isNotBlank() }
    }

    /** 生成羊皮纸便签文案，例如：一年前的今天，你读完了《百年孤独》，当时你说：… */
    fun formatRibbonText(memory: MemoryFlashback): String {
        val yearText = when (memory.yearsAgo) {
            1 -> "一年"
            2 -> "两年"
            3 -> "三年"
            4 -> "四年"
            5 -> "五年"
            6 -> "六年"
            7 -> "七年"
            8 -> "八年"
            9 -> "九年"
            10 -> "十年"
            else -> "${memory.yearsAgo} 年"
        }
        val action = if (memory.isFinished) "读完了" else "开始了"
        val quoteSuffix = memory.quote?.let { "，当时你说：「${it.take(40)}」" } ?: ""
        return "${yearText}前的今天，你${action}《${memory.book.title}》$quoteSuffix"
    }
}
