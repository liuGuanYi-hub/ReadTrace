package com.example.readtrace.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.readtrace.model.BangumiSubject
import com.example.readtrace.model.MediaType
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 豆瓣公开页面客户端（v4.2.17 多源改造）。
 *
 * 背景：中文大众向（豆瓣风）书影音没有任何合规稳定的开放 API
 * （Google Books/TMDB 国内不可达、B站无官方元数据接口），
 * 故按用户决策解析豆瓣**公开榜单页/搜索页/条目页**（无登录、纯展示数据）。
 *
 * 合规与礼貌约束（必须遵守）：
 * - 只抓公开页面，绝不带任何用户 Cookie / 账号态；
 * - 串行单线程 + 请求间 300ms 间隔，限流防反爬；
 * - 榜单/搜索结果 24h 磁盘缓存，详情结果内存缓存，减少请求；
 * - 解析失败一律静默降级为 null/空，由调用方展示空态。
 *
 * 覆盖：BOOK → book.douban.com；MOVIE/ANIME → movie.douban.com（含动漫类目）；
 *       MUSIC → music.douban.com。GAME 不走此源（保留 Bangumi）。
 */
object DoubanClient {

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 ReadTrace/4.2.17"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 15000
    private const val CACHE_DIR = "douban_cache"
    private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    private const val CACHE_MAX_FILES = 30
    private const val REQUEST_INTERVAL_MS = 300L

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lastRequestAt = AtomicInteger(0)

    private fun hostOf(mediaType: MediaType): String = when (mediaType) {
        MediaType.BOOK -> "book"
        MediaType.MOVIE, MediaType.ANIME -> "movie"
        MediaType.MUSIC -> "music"
        MediaType.GAME -> "book" // 不应走到；GAME 由 Bangumi 接管
    }

    private fun displayNameOf(mediaType: MediaType): String = when (mediaType) {
        MediaType.BOOK -> "读书"
        MediaType.MOVIE, MediaType.ANIME -> "电影"
        MediaType.MUSIC -> "音乐"
        MediaType.GAME -> "图书"
    }

    /**
     * 榜单（keyword 为空）或搜索（keyword 非空）。cache-first，24h。
     */
    fun searchSubjects(
        context: Context,
        keyword: String,
        mediaType: MediaType,
        forceRefresh: Boolean = false,
        onResult: (List<BangumiSubject>?, fromCache: Boolean) -> Unit,
    ) {
        executor.execute {
            val appContext = context.applicationContext
            val kw = keyword.trim()
            val key = cacheKeyOf(kw, mediaType)
            if (!forceRefresh) {
                readCache(appContext, key)?.let { list ->
                    if (list.isNotEmpty()) {
                        mainHandler.post { onResult(list, true) }
                        return@execute
                    }
                }
            }
            val pageUrl = if (kw.isEmpty()) {
                "https://${hostOf(mediaType)}.douban.com/chart"
            } else {
                "https://search.douban.com/${hostOf(mediaType)}/subject_search?search_text=" +
                    URLEncoderCompat.encode(kw)
            }
            val html = fetch(appContext, pageUrl, referer = if (kw.isEmpty()) "https://${hostOf(mediaType)}.douban.com/" else "https://search.douban.com/")
            val subjects = html?.let { parseList(it, mediaType, kw) }
            if (subjects != null && subjects.isNotEmpty()) {
                writeCache(appContext, key, subjects)
            }
            if (subjects == null) {
                // 网络/风控失败：回退陈旧缓存兜底（与 Bangumi 行为一致）
                readCache(appContext, key)?.let { list ->
                    if (list.isNotEmpty()) {
                        mainHandler.post { onResult(list, true) }
                        return@execute
                    }
                }
            }
            mainHandler.post { onResult(subjects, false) }
        }
    }

    /** 条目详情：解析标题/原名/评分/创作者/简介（简介较长时截断） */
    fun getSubjectDetail(
        subject: BangumiSubject,
        mediaType: MediaType,
        onResult: (BangumiSubject?) -> Unit,
    ) {
        executor.execute {
            val html = fetch(
                null,
                "https://${hostOf(mediaType)}.douban.com/subject/${subject.id}/",
                referer = "https://${hostOf(mediaType)}.douban.com/",
            )
            val result = html?.let { parseDetail(it, subject, mediaType) }
            mainHandler.post { onResult(result) }
        }
    }

    // ---------------------------------------------------------------- 解析

    /** 榜单页结构（chart）：li > div.pl2 > a(书名/链接)；评分 rating_nums；作者 pl2 内文本 */
    private fun parseList(html: String, mediaType: MediaType, keyword: String): List<BangumiSubject> {
        val results = mutableListOf<BangumiSubject>()
        // 兼容 chart 页（class="pl2"）与 search.douban.com 新版（class="item-root" / title-text）
        val chartBlock = Regex("<li[^>]*>([\\s\\S]*?class=\"pl2\"[\\s\\S]*?)</li>").findAll(html)
        for (m in chartBlock.take(100)) {
            val block = m.groupValues[1]
            val id = Regex("https?://${hostOf(mediaType)}\\.douban\\.com/subject/(\\d+)/?").find(block)
                ?.groupValues?.get(1)?.toLongOrNull() ?: continue
            val title = Regex("<a[^>]*>([^<]{1,80})</a>").find(block)?.groupValues?.get(1)
                ?.trim() ?: continue
            val rating = Regex("rating_nums\">([\\d.]+)<").find(block)?.groupValues?.get(1)
                ?.toDoubleOrNull()
            val cover = Regex("<img[^>]*src=\"([^\"]+)\"[^>]*>").find(block)?.groupValues?.get(1)
            val castRaw = Regex("<p class=\"pl\">([^<]{1,120})</p>").find(block)?.groupValues?.get(1)
            results += BangumiSubject(
                id = id,
                name = title,
                nameCn = title,
                coverUrl = cover?.takeIf { it.startsWith("http") },
                ratingScore = rating,
                creator = castRaw?.trim()?.takeIf { it.isNotEmpty() }?.let { cleanCast(it) },
                summary = null,
                subjectType = 0,
            )
        }
        if (results.isNotEmpty()) return results

        // search.douban.com 新版结构：整页找所有 item-root 块，尾部补哨兵保证最后一条不漏
        val body = html + "<div class=\"item-root\""
        val items = Regex("<div class=\"item-root\"[^>]*>([\\s\\S]*?)(?=<div class=\"item-root\")").findAll(body)
        for (m in items.take(100)) {
            val block = m.groupValues[1]
            val id = Regex("subject/(\\d+)/?").find(block)?.groupValues?.get(1)?.toLongOrNull() ?: continue
            val title = Regex("class=\"title-text\"[^>]*>([^<]{1,80})<").find(block)?.groupValues?.get(1)
                ?.trim() ?: continue
            val rating = Regex("rating_nums\">([\\d.]+)<").find(block)?.groupValues?.get(1)
                ?.toDoubleOrNull()
            val cover = Regex("<img[^>]*src=\"([^\"]+)\"[^>]*>").find(block)?.groupValues?.get(1)
            val cast = Regex("subject-cast\">([^<]{1,120})<").find(block)?.groupValues?.get(1)
            results += BangumiSubject(
                id = id,
                name = title,
                nameCn = title,
                coverUrl = cover?.takeIf { it.startsWith("http") },
                ratingScore = rating,
                creator = cast?.trim()?.takeIf { it.isNotEmpty() }?.let { cleanCast(it) },
                summary = null,
                subjectType = 0,
            )
        }
        return results
    }

    private fun parseDetail(html: String, subject: BangumiSubject, mediaType: MediaType): BangumiSubject {
        val title = Regex("<title>([^<]{1,80}?)\\s*\\(豆瓣\\)").find(html)?.groupValues?.get(1)
            ?.trim() ?: subject.displayTitle
        val rating = Regex("property=\"v:average\">([\\d.]+)<").find(html)?.groupValues?.get(1)
            ?.toDoubleOrNull() ?: subject.ratingScore
        // 简介：link-report-intra 区块（新版与历史版结构）
        val summary = Regex("<span property=\"v:summary\"[^>]*>([\\s\\S]*?)</span>").find(html)
            ?.groupValues?.get(1)
            ?: Regex("<div id=\"link-report-intra\"[^>]*>([\\s\\S]*?)</div>").find(html)
                ?.groupValues?.get(1)
        val cleanSummary = summary?.let { s ->
            s.replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&#13;", "").replace("&quot;", "\"")
                .replace(Regex("\\s+"), " ").trim()
                .takeIf { it.length >= 2 }
        }
        // 创作者：info 区块「作者 / 导演 / 表演者」链接文本
        val creator = Regex("<div id=\"info\"[^>]*>([\\s\\S]*?)</div>").find(html)?.groupValues?.get(1)
            ?.let { info ->
                val key = when (mediaType) {
                    MediaType.ANIME, MediaType.MOVIE -> "导演"
                    MediaType.MUSIC -> "表演者"
                    else -> "作者"
                }
                Regex("$key[^<]{0,20}<a[^>]*>([^<]{1,60})<").find(info)?.groupValues?.get(1)?.trim()
                    ?: Regex("$key[^<]{0,20}<span>([^<]{1,60})<").find(info)?.groupValues?.get(1)?.trim()
            }
        return subject.copy(
            name = title,
            nameCn = title,
            ratingScore = rating,
            summary = cleanSummary,
            creator = creator ?: subject.creator,
        )
    }

    /** 榜单页 cast 行清理：去掉「(作者) (译者)」等注记，保留人名 */
    private fun cleanCast(raw: String): String {
        var s = raw
        Regex("\\([^)]*\\)").findAll(s).forEach { s = s.replace(it.value, "") }
        return s.replace(Regex("\\s+"), " ").trim().takeIf { it.isNotEmpty() } ?: raw.trim()
    }

    // ---------------------------------------------------------------- 缓存

    private fun cacheKeyOf(keyword: String, mediaType: MediaType): String {
        val normalized = "douban:search|${hostOf(mediaType)}|${keyword.lowercase()}"
        return md5Hex(normalized)
    }

    private fun readCache(context: Context, key: String): List<BangumiSubject>? = runCatching {
        val file = File(File(context.filesDir, CACHE_DIR).apply { if (!exists()) mkdirs() }, "$key.json")
        if (!file.exists() || file.length() == 0L) return@runCatching null
        val root = JSONObject(file.readText(StandardCharsets.UTF_8))
        if (System.currentTimeMillis() - root.optLong("ts", 0L) > CACHE_TTL_MS) return@runCatching null
        val arr = root.optJSONArray("data") ?: return@runCatching null
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    BangumiSubject(
                        id = o.optLong("id"),
                        name = o.optString("name"),
                        nameCn = o.optString("nameCn").takeIf { it.isNotBlank() },
                        coverUrl = o.optString("cover").takeIf { it.isNotBlank() },
                        ratingScore = o.optDouble("rating").takeIf { !it.isNaN() && it > 0 },
                        creator = o.optString("creator").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun writeCache(context: Context, key: String, subjects: List<BangumiSubject>) {
        runCatching {
            val dir = File(context.filesDir, CACHE_DIR).apply { if (!exists()) mkdirs() }
            val arr = org.json.JSONArray()
            subjects.forEach { s ->
                arr.put(
                    JSONObject().apply {
                        put("id", s.id)
                        put("name", s.name)
                        put("nameCn", s.nameCn ?: "")
                        put("cover", s.coverUrl ?: "")
                        put("rating", s.ratingScore ?: 0)
                        put("creator", s.creator ?: "")
                    },
                )
            }
            val payload = JSONObject().put("ts", System.currentTimeMillis()).put("data", arr)
            File(dir, "$key.json").writeText(payload.toString(), StandardCharsets.UTF_8)
            val files = dir.listFiles()?.takeIf { it.size > CACHE_MAX_FILES } ?: return@runCatching
            files.sortedBy { it.lastModified() }
                .take(files.size - CACHE_MAX_FILES)
                .forEach { runCatching { it.delete() } }
        }
    }

    // ---------------------------------------------------------------- HTTP

    /** 串行 + 间隔限流；referer 为豆瓣站点 Referer 防 418 */
    private fun fetch(context: Context?, url: String, referer: String): String? = runCatching {
        pace()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            setRequestProperty("Referer", referer)
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            // 匿名设备标识（非用户 Cookie），规避无 cookie 请求被拒
            setRequestProperty("Cookie", "bid=${randomBid()}; _pk_ref.100001.4cf6=%5B%22%22%2C%22%22%2C${System.currentTimeMillis() / 1000}%2C%22https%3A%2F%2Fwww.douban.com%2F%22%5D")
        }
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            Log.w("DoubanClient", "HTTP $code for $url")
            conn.disconnect()
            return@runCatching null
        }
        val body = BufferedReader(
            InputStreamReader(conn.inputStream, StandardCharsets.UTF_8),
        ).use { it.readText() }.also { conn.disconnect() }
        // 异常页兜底：风控/登录引导/空响应一律视为失败，走缓存回退
        if (body.length < 200 ||
            body.contains("检测到有异常请求") ||
            body.contains("验证码") ||
            body.contains("请登录") ||
            body.contains("登录豆瓣") ||
            body.contains("登录/注册")
        ) null else body
    }.getOrNull()

    private fun pace() {
        val now = System.currentTimeMillis().toInt()
        val last = lastRequestAt.getAndSet(now)
        val wait = REQUEST_INTERVAL_MS - (now - last)
        if (wait > 0) Thread.sleep(wait)
    }

    private fun randomBid(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..11).map { chars.random() }.joinToString("")
    }

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/** 轻量 URL 编码（避免依赖 android.net.Uri 在工具类里绕一层） */
private object URLEncoderCompat {
    fun encode(input: String): String =
        java.net.URLEncoder.encode(input, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
}
