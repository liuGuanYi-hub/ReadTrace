package com.example.readtrace.reader

import java.util.regex.Pattern

data class ReaderPage(
    val pageIndex: Int,
    val totalPages: Int,
    val chapterTitle: String,
    val content: String,
    val progressRatio: Float,
)

data class ChapterInfo(
    val title: String,
    val startCharIndex: Int,
)

object TxtPageSplitter {

    private val CHAPTER_PATTERN = Pattern.compile(
        "^(第[0-9一二三四五六七八九十百千]+[章节回卷部篇]|Chapter\\s*[0-9]+|引言|序言|楔子|尾声|结语|后记).*",
        Pattern.MULTILINE or Pattern.CASE_INSENSITIVE
    )

    /**
     * 智能文本分页
     * @param fullText 原始文本全文
     * @param charsPerPage 依据屏幕尺寸和字号估算的每页字符基数（默认约 380 字/页）
     */
    fun splitTextIntoPages(fullText: String, charsPerPage: Int = 380): List<ReaderPage> {
        val cleanText = fullText.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (cleanText.isEmpty()) {
            return listOf(ReaderPage(1, 1, "正文", "暂无文本内容", 1.0f))
        }

        val chapters = parseChapters(cleanText)
        val rawParagraphs = cleanText.split("\n")
        val pagesContent = mutableListOf<Pair<String, String>>() // Pair(chapterTitle, pageContent)

        var currentChapter = chapters.firstOrNull()?.title ?: "正文"
        val currentBuffer = StringBuilder()
        var currentLen = 0
        var totalProcessedChars = 0

        rawParagraphs.forEach { rawPara ->
            val para = rawPara.trim()
            if (para.isEmpty()) return@forEach

            // 检查该段落是否是新章节标题
            if (CHAPTER_PATTERN.matcher(para).matches()) {
                // 若当前页已有内容，先保存当前页
                if (currentBuffer.isNotEmpty()) {
                    pagesContent.add(Pair(currentChapter, currentBuffer.toString().trim()))
                    currentBuffer.clear()
                    currentLen = 0
                }
                currentChapter = para
            }

            val formattedPara = "    $para\n\n"
            val paraLen = formattedPara.length

            if (currentLen + paraLen > charsPerPage && currentLen > 120) {
                pagesContent.add(Pair(currentChapter, currentBuffer.toString().trim()))
                currentBuffer.clear()
                currentBuffer.append(formattedPara)
                currentLen = paraLen
            } else {
                currentBuffer.append(formattedPara)
                currentLen += paraLen
            }

            totalProcessedChars += para.length
        }

        if (currentBuffer.isNotEmpty()) {
            pagesContent.add(Pair(currentChapter, currentBuffer.toString().trim()))
        }

        val totalPages = pagesContent.size
        return pagesContent.mapIndexed { index, pair ->
            val pageNum = index + 1
            val ratio = pageNum.toFloat() / totalPages.toFloat()
            ReaderPage(
                pageIndex = pageNum,
                totalPages = totalPages,
                chapterTitle = pair.first,
                content = pair.second,
                progressRatio = ratio,
            )
        }
    }

    private fun parseChapters(text: String): List<ChapterInfo> {
        val matcher = CHAPTER_PATTERN.matcher(text)
        val list = mutableListOf<ChapterInfo>()
        while (matcher.find()) {
            list.add(ChapterInfo(matcher.group().trim(), matcher.start()))
        }
        return list
    }
}
