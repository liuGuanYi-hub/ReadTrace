package com.example.readtrace.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

object AnimeCoverScraperHelper {

    private val scraperExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 单部番剧联网搜索并获取封面海报
     */
    fun fetchAndSaveAnimeCover(
        context: Context,
        book: Book,
        dbHelper: BookDatabaseHelper,
        onResult: (isSuccess: Boolean, coverPath: String?) -> Unit,
    ) {
        scraperExecutor.execute {
            val result = queryAndDownloadCover(context, book, dbHelper)
            mainHandler.post {
                if (result != null) {
                    onResult(true, result)
                } else {
                    onResult(false, null)
                }
            }
        }
    }

    /**
     * 批量抓取所有缺失封面的番剧海报
     */
    fun batchFetchAnimeCovers(
        context: Context,
        dbHelper: BookDatabaseHelper,
        onProgress: (current: Int, total: Int, title: String) -> Unit,
        onComplete: (successCount: Int, totalCount: Int) -> Unit,
    ) {
        scraperExecutor.execute {
            val allBooks = dbHelper.getBooks()
            val animeWithoutCovers = allBooks.filter {
                it.mediaType == MediaType.ANIME && (it.coverUrl.isNullOrBlank() || !File(it.coverUrl).exists())
            }

            val total = animeWithoutCovers.size
            var successCount = 0

            animeWithoutCovers.forEachIndexed { index, book ->
                mainHandler.post {
                    onProgress(index + 1, total, book.title)
                }

                val savedPath = queryAndDownloadCover(context, book, dbHelper)
                if (savedPath != null) {
                    successCount++
                }

                // 适度休眠 300ms 避免过于频繁触发请求频控
                Thread.sleep(300)
            }

            mainHandler.post {
                onComplete(successCount, total)
            }
        }
    }

    private fun queryAndDownloadCover(context: Context, book: Book, dbHelper: BookDatabaseHelper): String? {
        return runCatching {
            // 清理番剧标题中的括号、季度等便于提升 Bangumi 搜索命中率
            val cleanTitle = cleanAnimeTitleForSearch(book.title)
            val encodedKeyword = URLEncoder.encode(cleanTitle, "UTF-8")
            val searchApi = "https://api.bgm.tv/search/subject/$encodedKeyword?type=2&responseGroup=small"

            val url = URL(searchApi)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "ReadTrace/4.4 (Android; AnimePosters; GitHub-liuGuanYi-hub)")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val list = json.optJSONArray("list") ?: return null
            if (list.length() == 0) return null

            val firstResult = list.getJSONObject(0)
            val images = firstResult.optJSONObject("images")
            val imageUrl = images?.optString("large")?.takeIf { it.isNotBlank() }
                ?: images?.optString("common")?.takeIf { it.isNotBlank() }
                ?: images?.optString("medium")?.takeIf { it.isNotBlank() }
                ?: return null

            // 升级 http 为 https
            val secureImageUrl = if (imageUrl.startsWith("http://")) imageUrl.replace("http://", "https://") else imageUrl

            val imgConn = (URL(secureImageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "ReadTrace/4.4 (Android; AnimePosters)")
            }

            if (imgConn.responseCode != HttpURLConnection.HTTP_OK) return null

            val customName = "cover_anime_${book.id}_${System.currentTimeMillis()}.jpg"
            val savedPath = CoverImageHelper.cropAndSaveCoverFromStream(context, imgConn.inputStream, customName)
                ?: return null

            // 更新数据库记录
            val updatedBook = book.copy(coverUrl = savedPath)
            dbHelper.updateBook(updatedBook)

            savedPath
        }.getOrNull()
    }

    private fun cleanAnimeTitleForSearch(title: String): String {
        return title
            .replace(Regex("（.*?）|\\(.*?\\)"), "") // 移除括号内容
            .replace("新剧场版", "")
            .replace("第二季", "")
            .replace("第三季", "")
            .replace("第四季", "")
            .replace("第1季", "")
            .replace("第2季", "")
            .replace("第3季", "")
            .replace("第4季", "")
            .replace(" 2", "")
            .replace(" 3", "")
            .replace(" 4", "")
            .replace(" 5", "")
            .replace(" 6", "")
            .replace(" 伍", "")
            .replace(" 陆", "")
            .replace(" 终", "")
            .trim()
    }
}
