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

    // --- 预编译正则常量：避免在榜单/搜索循环解析中反复构建正则语法树（提升 HTML 解析吞吐） ---
    private val RE_00 = Regex("<tr class=\"item\">([\\s\\S]*?)</tr>")
    private val RE_01 = Regex("subject/(\\d+)/?")
    private val RE_02 = Regex("<div class=\"pl2\">[\\s\\S]*?<a[^>]*title=\"([^\"]{1,80})\"")
    private val RE_03 = Regex("<a[^>]*title=\"([^\"]{1,80})\"")
    private val RE_04 = Regex("rating_nums\">([\\d.]+)<")
    private val RE_05 = Regex("<img[^>]*src=\"([^\"]+)\"")
    private val RE_06 = Regex("<div class=\"pl2\">[\\s\\S]*?<p class=\"pl\">([^<]{1,120})</p>")
    private val RE_07 = Regex("<div class=\"item\">([\\s\\S]*?)(?=<div class=\"item\")")
    private val RE_08 = Regex("<span class=\"title\">([^<]{1,80})</span>")
    private val RE_09 = Regex("rating_num\" property=\"v:average\">([\\d.]+)<")
    private val RE_10 = Regex("<p>[\\s\\S]*?(?:导演|导演:)\\s*([^<\\n]{1,40})")
    private val RE_11 = Regex("<li[^>]*>([\\s\\S]*?class=\"pl2\"[\\s\\S]*?)</li>")
    private val RE_12 = Regex("<a[^>]*>([^<]{1,80})</a>")
    private val RE_13 = Regex("<img[^>]*src=\"([^\"]+)\"[^>]*>")
    private val RE_14 = Regex("<p class=\"pl\">([^<]{1,120})</p>")
    private val RE_15 = Regex("<div class=\"item-root\"[^>]*>([\\s\\S]*?)(?=<div class=\"item-root\")")
    private val RE_16 = Regex("class=\"title-text\"[^>]*>([^<]{1,80})<")
    private val RE_17 = Regex("subject-cast\">([^<]{1,120})<")
    private val RE_18 = Regex("<title>([^<]{1,80}?)\\s*\\(豆瓣\\)")
    private val RE_19 = Regex("property=\"v:average\">([\\d.]+)<")
    private val RE_20 = Regex("<span property=\"v:summary\"[^>]*>([\\s\\S]*?)</span>")
    private val RE_21 = Regex("<div id=\"link-report-intra\"[^>]*>([\\s\\S]*?)</div>")
    private val RE_22 = Regex("<[^>]+>")
    private val RE_23 = Regex("\\s+")
    private val RE_24 = Regex("<div id=\"info\"[^>]*>([\\s\\S]*?)</div>")
    private val RE_25 = Regex("\\([^)]*\\)")

    /** 条目来源标识：落库 sourceType 与跨源防重用 */
    const val SOURCE_DOUBAN = "douban"

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

    /** 页间重试间隔（风控友好） */
    private const val PAGE_RETRY_GAP_MS = 600L

    /** v4.2.23 单页榜单 URL：书走 Top250 HTML（25/页），影视/番剧走电影 JSON 接口（20/页） */
    private fun rankUrlFor(mediaType: MediaType, page: Int): String? = when (mediaType) {
        MediaType.BOOK -> "https://book.douban.com/top250?start=${page * 25}"
        MediaType.MOVIE -> "https://movie.douban.com/j/new_search_subjects?tags=%E7%94%B5%E5%BD%B1&sort=rank&start=${page * 20}&limit=20"
        MediaType.ANIME -> "https://movie.douban.com/j/new_search_subjects?tags=%E5%8A%A8%E6%BC%AB&sort=rank&start=${page * 20}&limit=20"
        else -> null
    }

    /** 榜单最大页数：书 Top250 共 250 部（10 页×25），影视/番剧 JSON 接口保守取 10 页×20 */
    fun maxRankPages(mediaType: MediaType): Int = when (mediaType) {
        MediaType.BOOK -> 10
        MediaType.MOVIE, MediaType.ANIME -> 10
        else -> 0
    }

    /**
     * v4.2.23 分页抓取：同步抓取单页榜单（必须在后台线程调用，由 RankRepository 统一调度）。
     * 返回值语义：
     * - null = 网络失败且无任何缓存兜底（调用方可用 Bangumi 补位）；
     * - SourcePage.items 为空 = 该页合法为空或页面结构变更（已记 PARSE_MISS 日志）。
     */
    fun fetchRankPageSync(context: Context, mediaType: MediaType, page: Int, forceRefresh: Boolean): SourcePage? {
        val url = rankUrlFor(mediaType, page) ?: return null
        val appContext = context.applicationContext
        val key = rankCacheKeyOf(mediaType, page)
        if (!forceRefresh) {
            readCache(appContext, key)?.let { list -> return SourcePage(list, fromCache = true) }
        }
        var raw = fetch(appContext, url, referer = "https://${hostOf(mediaType)}.douban.com/")
        if (raw == null) {
            Thread.sleep(PAGE_RETRY_GAP_MS)
            raw = fetch(appContext, url, referer = "https://${hostOf(mediaType)}.douban.com/")
        }
        val items = when (mediaType) {
            MediaType.ANIME, MediaType.MOVIE -> raw?.let { parseRankJson(it) }.orEmpty()
            else -> raw?.let { parseRanking(it, mediaType) }.orEmpty()
        }
        if (raw != null && items.isEmpty()) {
            // HTTP 成功但解析 0 条：区分「页面合法为空」与「结构变更」只能靠日志，供排障定位
            Log.w("DoubanClient", "PARSE_MISS page=$page url=$url")
        }
        if (items.isNotEmpty()) writeCache(appContext, key, items)
        if (raw == null) {
            readCache(appContext, key)?.let { list -> return SourcePage(list, fromCache = true) }
            return null
        }
        return SourcePage(items, fromCache = false)
    }

    /**
     * v4.2.23 关键词搜索同步版（单页），由 RankRepository 调度。
     * cache-first，24h；失败回退陈旧缓存。
     */
    fun fetchSearchSync(context: Context, keyword: String, mediaType: MediaType, forceRefresh: Boolean): SourcePage? {
        val kw = keyword.trim()
        if (kw.isEmpty()) return SourcePage(emptyList(), fromCache = false)
        val appContext = context.applicationContext
        val key = cacheKeyOf(kw, mediaType)
        if (!forceRefresh) {
            readCache(appContext, key)?.let { list -> return SourcePage(list, fromCache = true) }
        }
        val pageUrl = "https://search.douban.com/${hostOf(mediaType)}/subject_search?search_text=" +
            URLEncoderCompat.encode(kw)
        val html = fetch(appContext, pageUrl, referer = "https://search.douban.com/")
        val subjects = html?.let { parseList(it, mediaType, kw) }
        if (subjects != null && subjects.isNotEmpty()) {
            writeCache(appContext, key, subjects)
        }
        if (subjects == null) {
            readCache(appContext, key)?.let { list -> return SourcePage(list, fromCache = true) }
            return null
        }
        return SourcePage(subjects, fromCache = false)
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

    /**
     * 盘点页解析（v4.2.21 校准版）：兼容两种真实 DOM——
     * 读书 Top250：<tr class="item"> + <div class="pl2"><a title="书名">
     * 电影 Top250：<div class="item"> + <span class="title">
     * 先用 table 结构试，无命中再用 grid_view 结构。
     */
    private fun parseRanking(html: String, mediaType: MediaType): List<BangumiSubject> {
        val table = parseRankTable(html)
        if (table.isNotEmpty()) return table
        return parseRankGridView(html)
    }

    /** 读书 Top250 table 结构 */
    private fun parseRankTable(html: String): List<BangumiSubject> {
        val out = mutableListOf<BangumiSubject>()
        for (m in RE_00.findAll(html).take(120)) {
            val block = m.groupValues[1]
            val id = RE_01.find(block)?.groupValues?.get(1)?.toLongOrNull() ?: continue
            val title = RE_02.find(block)
                ?.groupValues?.get(1)
                ?: RE_03.find(block)?.groupValues?.get(1)
                ?: continue
            val rating = RE_04.find(block)?.groupValues?.get(1)?.toDoubleOrNull()
            val cover = RE_05.find(block)?.groupValues?.get(1)
            val creator = RE_06.find(block)?.groupValues?.get(1)
            out += BangumiSubject(
                id = id,
                name = title.trim(),
                nameCn = title.trim(),
                coverUrl = cover?.takeIf { it.startsWith("http") },
                ratingScore = rating,
                creator = creator?.trim()?.takeIf { it.isNotEmpty() }?.let { cleanCast(it) },
                summary = null,
                subjectType = 0,
                source = SOURCE_DOUBAN,
            )
        }
        return out
    }

    /** 电影 Top250 grid_view 结构 */
    private fun parseRankGridView(html: String): List<BangumiSubject> {
        val out = mutableListOf<BangumiSubject>()
        val body = html + "<div class=\"item\""
        for (m in RE_07.findAll(body).take(120)) {
            val block = m.groupValues[1]
            val id = RE_01.find(block)?.groupValues?.get(1)?.toLongOrNull() ?: continue
            val title = RE_08.find(block)?.groupValues?.get(1) ?: continue
            val rating = RE_09.find(block)?.groupValues?.get(1)
                ?.toDoubleOrNull()
            val cover = RE_05.find(block)?.groupValues?.get(1)
            val creator = RE_10.find(block)?.groupValues?.get(1)
            out += BangumiSubject(
                id = id,
                name = title.trim(),
                nameCn = title.trim(),
                coverUrl = cover?.takeIf { it.startsWith("http") },
                ratingScore = rating,
                creator = creator?.trim()?.let { if (it.contains("主演")) it.substringBefore("主演").trim() else it },
                summary = null,
                subjectType = 0,
                source = SOURCE_DOUBAN,
            )
        }
        return out
    }

    /** 番剧/影视 JSON 榜单解析：{data:[{title,rate,url,directors,casts,cover?}]} */
    private fun parseRankJson(json: String): List<BangumiSubject>? = runCatching {
        val data = JSONObject(json).optJSONArray("data") ?: return@runCatching null
        buildList {
            for (i in 0 until data.length()) {
                val o = data.optJSONObject(i) ?: continue
                val id = RE_01.find(o.optString("url"))?.groupValues?.get(1)?.toLongOrNull()
                    ?: continue
                val title = o.optString("title").trim()
                if (title.isEmpty()) continue
                val creator = buildList {
                    val d = o.optJSONArray("directors")
                    d?.let { arr -> for (j in 0 until arr.length()) arr.optString(j).trim().takeIf { it.isNotEmpty() }?.let { add(it) } }
                    if (isEmpty()) {
                        val c = o.optJSONArray("casts")
                        c?.let { arr -> for (j in 0 until minOf(2, arr.length())) arr.optString(j).trim().takeIf { it.isNotEmpty() }?.let { add(it) } }
                    }
                }.take(3).joinToString(" / ").takeIf { it.isNotEmpty() }
                add(
                    BangumiSubject(
                        id = id,
                        name = title,
                        nameCn = title,
                        coverUrl = o.optString("cover").takeIf { it.startsWith("http") },
                        ratingScore = o.optString("rate").toDoubleOrNull(),
                        creator = creator,
                        summary = null,
                        subjectType = 0,
                        source = SOURCE_DOUBAN,
                    ),
                )
            }
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** 榜单页结构（chart）：li > div.pl2 > a(书名/链接)；评分 rating_nums；作者 pl2 内文本 */
    private fun parseList(html: String, mediaType: MediaType, keyword: String): List<BangumiSubject> {
        val results = mutableListOf<BangumiSubject>()
        // 兼容 chart 页（class="pl2"）与 search.douban.com 新版（class="item-root" / title-text）
        val chartBlock = RE_11.findAll(html)
        for (m in chartBlock.take(100)) {
            val block = m.groupValues[1]
            val id = Regex("https?://${hostOf(mediaType)}\\.douban\\.com/subject/(\\d+)/?").find(block)
                ?.groupValues?.get(1)?.toLongOrNull() ?: continue
            val title = RE_12.find(block)?.groupValues?.get(1)
                ?.trim() ?: continue
            val rating = RE_04.find(block)?.groupValues?.get(1)
                ?.toDoubleOrNull()
            val cover = RE_13.find(block)?.groupValues?.get(1)
            val castRaw = RE_14.find(block)?.groupValues?.get(1)
            results += BangumiSubject(
                id = id,
                name = title,
                nameCn = title,
                coverUrl = cover?.takeIf { it.startsWith("http") },
                ratingScore = rating,
                creator = castRaw?.trim()?.takeIf { it.isNotEmpty() }?.let { cleanCast(it) },
                summary = null,
                subjectType = 0,
                source = SOURCE_DOUBAN,
            )
        }
        if (results.isNotEmpty()) return results

        // search.douban.com 新版结构：整页找所有 item-root 块，尾部补哨兵保证最后一条不漏
        val body = html + "<div class=\"item-root\""
        val items = RE_15.findAll(body)
        for (m in items.take(100)) {
            val block = m.groupValues[1]
            val id = RE_01.find(block)?.groupValues?.get(1)?.toLongOrNull() ?: continue
            val title = RE_16.find(block)?.groupValues?.get(1)
                ?.trim() ?: continue
            val rating = RE_04.find(block)?.groupValues?.get(1)
                ?.toDoubleOrNull()
            val cover = RE_13.find(block)?.groupValues?.get(1)
            val cast = RE_17.find(block)?.groupValues?.get(1)
            results += BangumiSubject(
                id = id,
                name = title,
                nameCn = title,
                coverUrl = cover?.takeIf { it.startsWith("http") },
                ratingScore = rating,
                creator = cast?.trim()?.takeIf { it.isNotEmpty() }?.let { cleanCast(it) },
                summary = null,
                subjectType = 0,
                source = SOURCE_DOUBAN,
            )
        }
        return results
    }

    private fun parseDetail(html: String, subject: BangumiSubject, mediaType: MediaType): BangumiSubject {
        val title = RE_18.find(html)?.groupValues?.get(1)
            ?.trim() ?: subject.displayTitle
        val rating = RE_19.find(html)?.groupValues?.get(1)
            ?.toDoubleOrNull() ?: subject.ratingScore
        // 简介：link-report-intra 区块（新版与历史版结构）
        val summary = RE_20.find(html)
            ?.groupValues?.get(1)
            ?: RE_21.find(html)
                ?.groupValues?.get(1)
        val cleanSummary = summary?.let { s ->
            s.replace(RE_22, "")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&#13;", "").replace("&quot;", "\"")
                .replace(RE_23, " ").trim()
                .takeIf { it.length >= 2 }
        }
        // 创作者：info 区块「作者 / 导演 / 表演者」链接文本
        val creator = RE_24.find(html)?.groupValues?.get(1)
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
        RE_25.findAll(s).forEach { s = s.replace(it.value, "") }
        return s.replace(RE_23, " ").trim().takeIf { it.isNotEmpty() } ?: raw.trim()
    }

    // ---------------------------------------------------------------- 缓存

    private fun cacheKeyOf(keyword: String, mediaType: MediaType): String {
        // 用 mediaType.name（BOOK/MOVIE/ANIME…）而非 hostOf：ANIME 与 MOVIE 同 host 但榜单不同，缓存必须隔离
        val normalized = "douban:search|${mediaType.name}|${keyword.lowercase()}"
        return md5Hex(normalized)
    }

    /** v4.2.23 榜单页级缓存键：每页独立缓存，单页失败不影响其他页 */
    private fun rankCacheKeyOf(mediaType: MediaType, page: Int): String {
        val normalized = "douban:rank|${mediaType.name}|page=$page"
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
                        source = SOURCE_DOUBAN,
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
        // 异常页兜底：风控/验证码页极短（几 KB）且带特征文案；
        // 注意正常页面顶栏也含「登录/注册」，不能仅凭字样误杀真实内容页
        val isBlockPage = body.length < 200 ||
            (body.length < 10_000 &&
                (body.contains("检测到有异常请求") || body.contains("验证码") || body.contains("sec.douban.com")))
        if (isBlockPage) {
            Log.w("DoubanClient", "RISK_BLOCK len=${body.length} url=$url sample=${body.take(80).replace("\n", " ")}")
            null
        } else body
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
