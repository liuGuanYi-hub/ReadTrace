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
     * 单部作品（书籍/番剧/影视/游戏/音乐）联网搜索并获取封面海报
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
     * 批量抓取所有缺失封面的作品海报
     */
    fun batchFetchAnimeCovers(
        context: Context,
        dbHelper: BookDatabaseHelper,
        targetMediaType: MediaType? = null,
        onProgress: (current: Int, total: Int, title: String) -> Unit,
        onComplete: (successCount: Int, totalCount: Int) -> Unit,
    ) {
        scraperExecutor.execute {
            val allBooks = dbHelper.getBooks()
            val booksWithoutCovers = allBooks.filter { book ->
                val typeMatches = targetMediaType == null || book.mediaType == targetMediaType
                val needsCover = book.coverUrl.isNullOrBlank() || (!book.coverUrl.startsWith("http") && !File(book.coverUrl).exists())
                typeMatches && needsCover
            }

            val total = booksWithoutCovers.size
            var successCount = 0

            booksWithoutCovers.forEachIndexed { index, book ->
                mainHandler.post {
                    onProgress(index + 1, total, book.title)
                }

                val savedPath = queryAndDownloadCover(context, book, dbHelper)
                if (savedPath != null) {
                    successCount++
                }

                // 适度休眠 250ms 避免请求频控
                Thread.sleep(250)
            }

            mainHandler.post {
                onComplete(successCount, total)
            }
        }
    }

    private fun queryAndDownloadCover(context: Context, book: Book, dbHelper: BookDatabaseHelper): String? {
        return runCatching {
            val cleanTitle = cleanTitleForSearch(book.title)
            val bgmType = when (book.mediaType) {
                MediaType.BOOK -> 1
                MediaType.ANIME -> 2
                MediaType.GAME -> 4
                MediaType.MOVIE -> 6
                MediaType.MUSIC -> 3
            }

            // 1. 优先尝试 Bangumi 开放 API (覆盖超 30 万书籍与海量 ACG/影视作品)
            var imageUrl = queryBangumiCover(cleanTitle, bgmType)

            // 2. 若为书籍且 Bangumi 未命中，尝试 Google Books API
            if (imageUrl == null && book.mediaType == MediaType.BOOK) {
                imageUrl = queryGoogleBooksCover(cleanTitle, book.author.orEmpty())
            }

            if (imageUrl.isNullOrBlank()) {
                return null
            }

            // 升级 http 为 https
            val secureImageUrl = if (imageUrl.startsWith("http://")) imageUrl.replace("http://", "https://") else imageUrl

            val imgConn = (URL(secureImageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "ReadTrace/4.5 (Android; Covers; GitHub-liuGuanYi-hub)")
            }

            if (imgConn.responseCode != HttpURLConnection.HTTP_OK) return null

            val customName = "cover_${book.mediaType.databaseValue}_${book.id}_${System.currentTimeMillis()}.jpg"
            val savedPath = CoverImageHelper.cropAndSaveCoverFromStream(context, imgConn.inputStream, customName)
                ?: return null

            // 更新数据库记录
            val updatedBook = book.copy(coverUrl = savedPath)
            dbHelper.updateBook(updatedBook)

            savedPath
        }.getOrNull()
    }

    private fun queryBangumiCover(cleanTitle: String, bgmType: Int): String? {
        return runCatching {
            val encodedKeyword = URLEncoder.encode(cleanTitle, "UTF-8")
            val searchApi = "https://api.bgm.tv/search/subject/$encodedKeyword?type=$bgmType&responseGroup=small"

            val url = URL(searchApi)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "ReadTrace/4.5 (Android; Covers; GitHub-liuGuanYi-hub)")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val list = json.optJSONArray("list") ?: return null
            if (list.length() == 0) return null

            var fallbackImage: String? = null
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val name = item.optString("name")
                val nameCn = item.optString("name_cn")
                val images = item.optJSONObject("images")
                val img = images?.optString("large")?.takeIf { it.isNotBlank() }
                    ?: images?.optString("common")?.takeIf { it.isNotBlank() }
                    ?: images?.optString("medium")?.takeIf { it.isNotBlank() }

                if (img != null) {
                    if (name.equals(cleanTitle, ignoreCase = true) || nameCn.equals(cleanTitle, ignoreCase = true)) {
                        return img
                    }
                    if (fallbackImage == null) {
                        fallbackImage = img
                    }
                }
            }
            fallbackImage
        }.getOrNull()
    }

    private fun queryGoogleBooksCover(cleanTitle: String, author: String): String? {
        return runCatching {
            val query = if (author.isNotBlank()) "$cleanTitle $author" else cleanTitle
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchApi = "https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=1"

            val url = URL(searchApi)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "ReadTrace/4.5 (Android; Covers; GitHub-liuGuanYi-hub)")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val items = json.optJSONArray("items") ?: return null
            if (items.length() == 0) return null

            val volumeInfo = items.getJSONObject(0).optJSONObject("volumeInfo")
            val imageLinks = volumeInfo?.optJSONObject("imageLinks")
            val thumb = imageLinks?.optString("thumbnail") ?: imageLinks?.optString("smallThumbnail")
            thumb?.replace("zoom=1", "zoom=2")?.replace("&edge=curl", "")
        }.getOrNull()
    }

    private fun cleanTitleForSearch(title: String): String {
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
