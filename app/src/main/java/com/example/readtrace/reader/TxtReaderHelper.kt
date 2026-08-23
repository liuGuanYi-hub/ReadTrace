package com.example.readtrace.reader

import android.content.Context
import android.net.Uri
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

object TxtReaderHelper {

    private const val DIR_BOOK_TEXTS = "book_texts"

    /**
     * 获取指定书籍的文本全文内容
     */
    fun loadBookText(context: Context, book: Book, databaseHelper: BookDatabaseHelper): String {
        // 1. 优先读取用户单独导入的私有文本文件
        val customFile = getCustomTextFile(context, book.id)
        if (customFile.exists()) {
            runCatching {
                return customFile.readText(Charsets.UTF_8)
            }
        }

        // 2. 检查 assets/books_txt/ 下是否有同名预设文本
        val assetName = findMatchingAsset(context, book.title)
        if (assetName != null) {
            runCatching {
                context.assets.open("books_txt/$assetName").use { inputStream ->
                    return readStreamWithEncoding(inputStream)
                }
            }
        }

        // 3. 若无全文文本，智能聚合当前书籍的已有摘录、评论与笔记作为精美读本
        return generateAggregatedNotesText(book, databaseHelper)
    }

    /**
     * 判断某本书是否已有内置或导入的文本
     */
    fun hasFullText(context: Context, book: Book): Boolean {
        if (getCustomTextFile(context, book.id).exists()) return true
        return findMatchingAsset(context, book.title) != null
    }

    /**
     * 导入用户选择的 TXT 文件到内部私有目录
     */
    fun importTxtFromUri(context: Context, bookId: Long, uri: Uri): Boolean {
        return runCatching {
            val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                readStreamWithEncoding(inputStream)
            } ?: return false

            val targetFile = getCustomTextFile(context, bookId)
            targetFile.parentFile?.mkdirs()
            targetFile.writeText(content, Charsets.UTF_8)
            true
        }.getOrDefault(false)
    }

    private fun getCustomTextFile(context: Context, bookId: Long): File {
        val dir = File(context.filesDir, DIR_BOOK_TEXTS)
        return File(dir, "$bookId.txt")
    }

    private fun findMatchingAsset(context: Context, title: String): String? {
        val trimmed = title.trim().lowercase()
        return runCatching {
            val list = context.assets.list("books_txt") ?: return null
            list.firstOrNull { asset ->
                val pureName = asset.removeSuffix(".txt").trim().lowercase()
                pureName == trimmed || trimmed.contains(pureName) || pureName.contains(trimmed)
            }
        }.getOrNull()
    }

    private fun readStreamWithEncoding(inputStream: InputStream): String {
        val buffer = ByteArrayOutputStream()
        val temp = ByteArray(4096)
        var read: Int
        while (inputStream.read(temp).also { read = it } != -1) {
            buffer.write(temp, 0, read)
        }
        val bytes = buffer.toByteArray()

        // 优先尝试 UTF-8 解码，若出现乱码回退到 GBK
        val utf8String = String(bytes, Charsets.UTF_8)
        return if (utf8String.contains("\uFFFD")) {
            runCatching {
                String(bytes, Charset.forName("GBK"))
            }.getOrDefault(utf8String)
        } else {
            utf8String
        }
    }

    private fun generateAggregatedNotesText(book: Book, databaseHelper: BookDatabaseHelper): String {
        val notes = databaseHelper.getNotes(book.id)
        val sb = StringBuilder()
        sb.append("【${book.title}】\n")
        sb.append("作者：${book.author ?: "佚名"}\n")
        if (book.rating != null) {
            sb.append("个人评分：★ ${book.rating}\n")
        }
        sb.append("载体类型：${book.mediaType.displayName} ${book.mediaType.emoji}\n\n")

        sb.append("第一章 作品感悟与复盘\n\n")
        if (!book.shortComment.isNullOrBlank()) {
            sb.append("短评感悟：\n“${book.shortComment}”\n\n")
        }
        if (!book.review.isNullOrBlank()) {
            sb.append("长篇随想：\n${book.review}\n\n")
        }

        if (notes.isNotEmpty()) {
            sb.append("第二章 摘录与灵感痕迹\n\n")
            notes.forEachIndexed { index, note ->
                sb.append("第 ${index + 1} 条摘录：\n")
                sb.append("“${note.content}”\n")
                if (!note.page.isNullOrBlank()) {
                    sb.append("（出处：${note.page}）\n")
                }
                if (!note.chapter.isNullOrBlank()) {
                    sb.append("（章节：${note.chapter}）\n")
                }
                sb.append("\n")
            }
        } else {
            sb.append("暂未导入外部 TXT 全文，点击右上角【导入 TXT】可一键绑定本地电子书文本。")
        }

        return sb.toString()
    }
}
