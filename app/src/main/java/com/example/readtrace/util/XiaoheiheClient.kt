package com.example.readtrace.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.readtrace.model.BangumiSubject
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
 * 小黑盒 (xiaoheihe.cn) 游戏公开页面客户端（v4.2.18 多源改造）。
 *
 * 选型理由：游戏分类需要国内真实玩家的热门取向（黑盒是国内最大 ACG 游戏社区
 * 公开榜单页 / 排行 / 详情无需登录）。同豆瓣方案约束：公开页面低频解析 + UA + Referer。
 *
 * 覆盖：GAME。影视/番剧/书籍/音乐仍由 DoubanClient 处理。
 *
 * ⚠️ 已知风险：本机网络环境无法实测黑盒 HTML 真实结构，初始正则可能与真实 DOM
 * 不完全匹配。运行时若 fetch 解析返回空，会自动回退磁盘缓存；建议真机首次运行
 * 后看 logcat 中 "XiaoheiheClient" 的 HTTP 非 200 / 解析日志，按真实返回微调正则。
 */
object XiaoheiheClient {

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 ReadTrace/4.2.18"
    private const val BASE_URL = "https://www.xiaoheihe.cn"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 15000
    private const val CACHE_DIR = "xiaoheihe_cache"
    private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    private const val CACHE_MAX_FILES = 20
    private const val REQUEST_INTERVAL_MS = 300L

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lastRequestAt = AtomicInteger(0)

    /**
     * 榜单（keyword 为空）或搜索（keyword 非空）。cache-first，24h。
     */
    fun searchSubjects(
        context: Context,
        keyword: String,
        forceRefresh: Boolean = false,
        onResult: (List<BangumiSubject>?, fromCache: Boolean) -> Unit,
    ) {
        executor.execute {
            val appContext = context.applicationContext
            val kw = keyword.trim()
            val key = cacheKeyOf(kw)
            if (!forceRefresh) {
                readCache(appContext, key)?.let { list ->
                    if (list.isNotEmpty()) {
                        mainHandler.post { onResult(list, true) }
                        return@execute
                    }
                }
            }
            val pageUrl = if (kw.isEmpty()) {
                "$BASE_URL/h5/rank/game"
            } else {
                "$BASE_URL/search/game?q=" + java.net.URLEncoder.encode(kw, "UTF-8").replace("+", "%20")
            }
            val html = fetch(appContext, pageUrl, referer = if (kw.isEmpty()) "$BASE_URL/h5/rank/game" else "$BASE_URL/")
            val subjects = html?.let { parseList(it, kw) }
            if (subjects != null && subjects.isNotEmpty()) {
                writeCache(appContext, key, subjects)
            }
            if (subjects == null) {
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

    /** 条目详情：解析标题/评分/创作者/简介 */
    fun getSubjectDetail(
        subject: BangumiSubject,
        onResult: (BangumiSubject?) -> Unit,
    ) {
        executor.execute {
            val html = fetch(null, "$BASE_URL/h5/game/${subject.id}", referer = "$BASE_URL/h5/rank/game")
            val result = html?.let { parseDetail(it, subject) }
            mainHandler.post { onResult(result) }
        }
    }

    // ---------------------------------------------------------------- 解析

    /**
     * 榜单/搜索结果列表解析（宽松匹配）：黑盒 H5 列表通常为 a 链接 + img + title。
     * - 链接形如 /h5/game/<id> 或 /game/<id>
     * - 标题在 a 标签文本或 img alt
     * - 封面在 a 内 img src（黑盒 CDN: bbs.xiaoheihe.cn 或 wx.sinaimg.cn）
     */
    private fun parseList(html: String, keyword: String): List<BangumiSubject> {
        val out = mutableListOf<BangumiSubject>()
        // 匹配任意含 /game/<id> 或 /h5/game/<id> 的卡片链接
        val itemRegex = Regex("<a[^>]*href=\"([^\"]*?/h?5?/game/?(\\d+))\"[^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
        for (m in itemRegex.findAll(html).take(120)) {
            val href = m.groupValues[1]
            val idStr = m.groupValues[2]
            val block = m.groupValues[3]
            val id = idStr.toLongOrNull() ?: continue
            // 标题：优先 a 标签 title 属性，其次 a 内文本/img alt
            val title = Regex("title=\"([^\"]{1,80})\"").find(block)?.groupValues?.get(1)
                ?: Regex("<img[^>]*alt=\"([^\"]{1,80})\"").find(block)?.groupValues?.get(1)
                ?: Regex("alt=['\"]([^'\"]{1,80})['\"]").find(block)?.groupValues?.get(1)
                ?: href.trim('/').let { h -> h.substringAfterLast('/').take(40) }
            if (title.isBlank()) continue
            // 封面
            val cover = Regex("<img[^>]*src=\"([^\"]+)\"").find(block)?.groupValues?.get(1)
            // 评分：黑盒常用 .score / 评分星
            val rating = Regex("(?:rating|score)[^\"]{0,15}\"?([\\d.]+)").find(html)?.let { null }  // 列表页通常无评分
                ?: Regex("([\\d.]+)\\s*分").find(html)?.groupValues?.get(1)?.toDoubleOrNull()
            out += BangumiSubject(
                id = id,
                name = title,
                nameCn = title,
                coverUrl = cover?.takeIf { it.startsWith("http") },
                ratingScore = rating,
                summary = null,
                subjectType = 4,
            )
        }
        // 去重保序
        val dedup = LinkedHashMap<Long, BangumiSubject>()
        out.forEach { dedup.putIfAbsent(it.id, it) }
        return dedup.values.toList()
    }

    private fun parseDetail(html: String, subject: BangumiSubject): BangumiSubject {
        val title = Regex("<h1[^>]*>([^<]{1,80})</h1>").find(html)?.groupValues?.get(1)?.trim()
            ?: Regex("<title>([^<|]{1,80})").find(html)?.groupValues?.get(1)?.trim()
            ?: subject.displayTitle
        val rating = Regex("(?:score|rating)[^\\d]{0,15}([\\d.]+)").find(html)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: subject.ratingScore
        val summary = Regex("<div[^>]*class=\"[^\"]*desc[^\"]*\"[^>]*>([\\s\\S]*?)</div>").find(html)
            ?: Regex("<p[^>]*class=\"[^\"]*intro[^\"]*\"[^>]*>([\\s\\S]*?)</p>").find(html)
        val cleanSummary = summary?.groupValues?.get(1)?.let { raw ->
            raw.replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace(Regex("\\s+"), " ")
                .trim()
                .takeIf { it.length >= 2 }
        }
        // 创作者：开发/发行/类型
        val creator = Regex("(?:开发商|发行商|类型)[^:：]*[:：]\\s*([^<\n]{1,60})").find(html)?.groupValues?.get(1)?.trim()
            ?: Regex("data-[\"']?developer[\"']?\\s*[:=]\\s*\"([^\"]{1,60})\"").find(html)?.groupValues?.get(1)
        return subject.copy(
            name = title,
            nameCn = title,
            ratingScore = rating,
            summary = cleanSummary,
            creator = creator ?: subject.creator,
        )
    }

    // ---------------------------------------------------------------- 缓存

    private fun cacheKeyOf(keyword: String): String {
        val normalized = "xiaoheihe:search|game|${keyword.lowercase()}"
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
                        summary = o.optString("summary").takeIf { it.isNotBlank() },
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
                        put("summary", s.summary ?: "")
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

    private fun fetch(context: Context?, url: String, referer: String): String? = runCatching {
        pace()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            setRequestProperty("Referer", referer)
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
        }
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            Log.w("XiaoheiheClient", "HTTP $code for $url")
            conn.disconnect()
            return@runCatching null
        }
        val body = BufferedReader(
            InputStreamReader(conn.inputStream, StandardCharsets.UTF_8),
        ).use { it.readText() }.also { conn.disconnect() }
        // 黑盒风控/登录页兜底
        if (body.length < 200 ||
            body.contains("请登录") || body.contains("登录小黑盒") ||
            body.contains("验证码") || body.contains("检测到有异常")
        ) null else body
    }.getOrNull()

    private fun pace() {
        val now = System.currentTimeMillis().toInt()
        val last = lastRequestAt.getAndSet(now)
        val wait = REQUEST_INTERVAL_MS - (now - last)
        if (wait > 0) Thread.sleep(wait)
    }

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}