package com.example.readtrace.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.readtrace.model.BangumiSubject
import com.example.readtrace.model.MediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Bangumi (bgm.tv) 官方开放 API 客户端（v4.2.14 外部导入）。
 *
 * 设计要点：
 * - 零第三方依赖：HttpURLConnection + org.json（与项目「零臃肿」原则一致）；
 * - 单线程串行执行器 + 主线程回调，调用方无需关心线程切换；
 * - 必须携带自定义 User-Agent，否则会被 Bangumi 网关拒绝；
 * - 所有失败均以 null 回调（静默降级），由调用方展示空态/重试。
 */
object BangumiApiClient {

    private const val BASE_URL = "https://api.bgm.tv"
    private const val USER_AGENT = "ReadTrace/4.2.14 (Android; github.com/liuGuanYi-hub/ReadTrace)"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 15000
    private const val CACHE_DIR = "bangumi_cache"
    private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    private const val CACHE_MAX_FILES = 40

    /** Bangumi 周边接口限速较严，搜索请求串行化即可天然限流 */
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 阅痕媒介类型 → Bangumi subject_type（注意：Bangumi 无 type 5，影视归 6「三次元」） */
    fun subjectTypeOf(mediaType: MediaType): Int = when (mediaType) {
        MediaType.BOOK -> 1
        MediaType.ANIME -> 2
        MediaType.MUSIC -> 3
        MediaType.GAME -> 4
        MediaType.MOVIE -> 6
    }

    /**
     * 搜索条目（v0 搜索接口），cache-first：
     * - 24h 内有缓存直接返回（fromCache=true），不发起网络请求；
     * - forceRefresh=true（下拉刷新）跳过新鲜缓存强制联网，成功后覆写缓存；
     * - 网络失败时回退任意年龄的陈旧缓存，仍返回 fromCache=true；
     * - keyword 为空时 sort=rank 即「热门榜单」：extraPages 控制额外翻页数
     *   （Bangumi rank 每页固定 20 条，默认 4 页 → 榜单 100 条，串行带间隔礼貌抓取）；
     * - keyword 非空走 sort=match 关键词搜索，单页 20 条精准匹配。
     */
    fun searchSubjects(
        context: Context,
        keyword: String,
        mediaType: MediaType,
        forceRefresh: Boolean = false,
        extraPages: Int = 0,
        onResult: (List<BangumiSubject>?, fromCache: Boolean) -> Unit,
    ) {
        executor.execute {
            val appContext = context.applicationContext
            val key = cacheKeyOf(keyword, mediaType)
            if (!forceRefresh) {
                readCache(appContext, key, keyword.trim())?.let { subjects ->
                    if (subjects.isNotEmpty()) {
                        mainHandler.post { onResult(subjects, true) }
                        return@execute
                    }
                }
            }
            // 榜单模式：翻页预取（每页 20 条），串行 + 页间 250ms 礼貌限流，任一页失败即止
            val kw = keyword.trim()
            val isRank = kw.isEmpty()
            val pages = if (isRank) (extraPages + 1) else 1
            val mergedData = JSONArray()
            var rawAll: String? = null
            var networkOk = true
            for (page in 0 until pages) {
                val raw = runCatching {
                    val type = subjectTypeOf(mediaType)
                    val body = JSONObject().apply {
                        put("keyword", kw)
                        put("sort", if (isRank) "rank" else "match")
                        put("filter", JSONObject().put("type", JSONArray().put(type)))
                    }
                    val offset = if (isRank) "&offset=${page * 20}" else ""
                    request(
                        path = "/v0/search/subjects?limit=20$offset",
                        method = "POST",
                        body = body.toString().toByteArray(StandardCharsets.UTF_8),
                    )
                }.onFailure {
                    Log.e("BangumiApi", "search page $page failed", it)
                }.getOrNull()
                if (raw == null) {
                    if (page == 0) networkOk = false
                    break
                }
                runCatching {
                    val data = JSONObject(raw).optJSONArray("data")
                    if (data != null) {
                        for (i in 0 until data.length()) mergedData.put(data.get(i))
                    }
                }
                rawAll = JSONObject().apply { put("data", mergedData) }.toString()
                if (page < pages - 1) Thread.sleep(250)
            }
            if (networkOk && rawAll != null) writeCache(appContext, key, rawAll)
            val result = rawAll?.let { parseSearchResponse(it, keyword.trim()) }
            if (result == null) {
                // 网络失败：回退陈旧缓存兜底
                readCache(appContext, key, keyword.trim())?.let { subjects ->
                    if (subjects.isNotEmpty()) {
                        mainHandler.post { onResult(subjects, true) }
                        return@execute
                    }
                }
            }
            mainHandler.post { onResult(result, false) }
        }
    }

    // ---------------------------------------------------------------- v4.2.23 分页同步接口

    /**
     * 分页搜索同步版（单页 20 条），必须在后台线程调用，由 RankRepository 统一调度。
     * 空关键词 → sort=rank 榜单；非空 → sort=match。
     * 返回 Pair(items, total)；null = 网络失败且无缓存兜底。
     */
    fun fetchRankPageSync(
        context: Context,
        mediaType: MediaType,
        keyword: String,
        offset: Int,
        forceRefresh: Boolean,
    ): Pair<List<BangumiSubject>, Int?>? {
        val appContext = context.applicationContext
        val kw = keyword.trim()
        val key = pageCacheKeyOf(kw, mediaType, offset)
        if (!forceRefresh) {
            readPageCache(appContext, key, kw)?.let { return it }
        }
        val raw = runCatching {
            val body = JSONObject().apply {
                put("keyword", kw)
                put("sort", if (kw.isEmpty()) "rank" else "match")
                put("filter", JSONObject().put("type", JSONArray().put(subjectTypeOf(mediaType))))
            }
            request(
                path = "/v0/search/subjects?limit=20&offset=$offset",
                method = "POST",
                body = body.toString().toByteArray(StandardCharsets.UTF_8),
            )
        }.onFailure {
            Log.e("BangumiApi", "fetchRankPageSync offset=$offset failed", it)
        }.getOrNull()
        if (raw == null) {
            readPageCache(appContext, key, kw)?.let { return it }
            return null
        }
        writeCache(appContext, key, raw)
        return parsePage(raw, kw)
    }

    private fun parsePage(response: String, keyword: String): Pair<List<BangumiSubject>, Int?> {
        val total = runCatching { JSONObject(response).optInt("total", -1) }.getOrDefault(-1)
            .takeIf { it >= 0 }
        return Pair(parseSearchResponse(response, keyword).orEmpty(), total)
    }

    private fun pageCacheKeyOf(keyword: String, mediaType: MediaType, offset: Int): String =
        md5Hex("page|${mediaType.databaseValue}|${keyword.trim().lowercase()}|offset=$offset")

    private fun readPageCache(
        context: Context,
        key: String,
        keyword: String,
    ): Pair<List<BangumiSubject>, Int?>? = runCatching {
        val file = File(File(context.filesDir, CACHE_DIR).apply { if (!exists()) mkdirs() }, "$key.json")
        if (!file.exists() || file.length() == 0L) return@runCatching null
        val root = JSONObject(file.readText(StandardCharsets.UTF_8))
        if (System.currentTimeMillis() - root.optLong("ts", 0L) > CACHE_TTL_MS) return@runCatching null
        parsePage(root.optString("response"), keyword)
    }.getOrNull()

    private fun parseSearchResponse(response: String, keyword: String = ""): List<BangumiSubject>? = runCatching {
        val data = JSONObject(response).optJSONArray("data")
            ?: return emptyList<BangumiSubject>()
        buildList {
            for (i in 0 until data.length()) {
                val subject = parseSubject(data.optJSONObject(i) ?: continue, withDetail = false)
                if (subject != null) add(subject)
            }
        }.let { sortByRelevance(it, keyword) }
    }.getOrNull()

    /**
     * 相关性重排：Bangumi sort=match 会把同字/近义的条目混在前面，
     * 这里按「标题完全匹配 > 开头匹配 > 包含 > 其他」再排一次，
     * 让搜「百年孤独」时真正的目标排到前面。
     */
    private fun sortByRelevance(subjects: List<BangumiSubject>, keyword: String): List<BangumiSubject> {
        val kw = keyword.trim().lowercase()
        if (kw.isEmpty()) return subjects
        fun score(subject: BangumiSubject): Int {
            val cn = subject.nameCn?.lowercase().orEmpty()
            val raw = subject.name.lowercase()
            return when {
                cn == kw || raw == kw -> 0
                cn.startsWith(kw) || raw.startsWith(kw) -> 1
                cn.contains(kw) || raw.contains(kw) -> 2
                else -> 3
            }
        }
        return subjects.sortedWith(compareBy({ score(it) }, { -(it.ratingScore ?: 0.0) }))
    }

    // ---------------------------------------------------------------- 磁盘缓存

    private fun cacheKeyOf(keyword: String, mediaType: MediaType): String {
        val normalized = "search|${mediaType.databaseValue}|${keyword.trim().lowercase()}"
        return md5Hex(normalized)
    }

    /** 缓存读取会同步带上关键词排序，保证缓存与联网结果顺序一致 */
    private fun readCache(context: Context, key: String, keyword: String = ""): List<BangumiSubject>? =
        runCatching {
            val file = File(File(context.filesDir, CACHE_DIR).apply { if (!exists()) mkdirs() }, "$key.json")
            if (!file.exists() || file.length() == 0L) return@runCatching null
            val root = JSONObject(file.readText(StandardCharsets.UTF_8))
            if (System.currentTimeMillis() - root.optLong("ts", 0L) > CACHE_TTL_MS) return@runCatching null
            parseSearchResponse(root.optString("response"), keyword)
        }.getOrNull()

    private fun writeCache(context: Context, key: String, rawResponse: String) {
        runCatching {
            val dir = File(context.filesDir, CACHE_DIR).apply { if (!exists()) mkdirs() }
            val payload = JSONObject()
                .put("ts", System.currentTimeMillis())
                .put("response", rawResponse)
            File(dir, "$key.json").writeText(payload.toString(), StandardCharsets.UTF_8)
            trimCache(dir)
        }
    }

    /** 缓存规模控制：超过上限时按最后修改时间淘汰最旧的，避免私有空间无限增长 */
    private fun trimCache(dir: File) {
        val files = dir.listFiles()?.takeIf { it.size > CACHE_MAX_FILES } ?: return
        files.sortedBy { it.lastModified() }
            .take(files.size - CACHE_MAX_FILES)
            .forEach { runCatching { it.delete() } }
    }

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** 条目详情（含 infobox 创作者解析、简介、评分） */
    fun getSubjectDetail(subjectId: Long, onResult: (BangumiSubject?) -> Unit) {
        executor.execute {
            val result = runCatching {
                val response = request(path = "/v0/subjects/$subjectId", method = "GET")
                    ?: return@runCatching null
                parseSubject(JSONObject(response), withDetail = true)
            }.getOrNull()
            mainHandler.post { onResult(result) }
        }
    }

    // ---------------------------------------------------------------- JSON 解析

    private fun parseSubject(json: JSONObject, withDetail: Boolean): BangumiSubject? {
        val id = json.optLong("id", 0L)
        if (id <= 0L) return null
        val name = json.optString("name").trim()
        if (name.isEmpty()) return null
        val images = json.optJSONObject("images")
        val cover = images?.optString("large")?.takeIf { it.isNotBlank() }
            ?: images?.optString("common")?.takeIf { it.isNotBlank() }
            ?: images?.optString("medium")?.takeIf { it.isNotBlank() }
            ?: images?.optString("grid")?.takeIf { it.isNotBlank() }
        val rating = json.optJSONObject("rating")
        val score = rating?.optDouble("score")?.takeIf { !it.isNaN() && it > 0.0 }
        val tags = buildList {
            json.optJSONArray("tags")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val tag = arr.optJSONObject(i)?.optString("name")?.trim()
                    if (!tag.isNullOrEmpty()) add(tag)
                }
            }
        }
        val infobox = json.optJSONArray("infobox")
        // Bangumi type → 阅痕媒介（决定创作者键名优先级，如书取「作者」、动画取「导演」）
        val creatorMediaType = when (json.optInt("type", 0)) {
            1 -> MediaType.BOOK
            2 -> MediaType.ANIME
            3 -> MediaType.MUSIC
            4 -> MediaType.GAME
            6 -> MediaType.MOVIE
            else -> null
        }
        return BangumiSubject(
            id = id,
            name = name,
            nameCn = json.optString("name_cn").takeIf { it.isNotBlank() },
            coverUrl = cover,
            // 搜索结果本身也带 summary，直接取用，避免预览还要干等详情接口
            summary = json.optString("summary").takeIf { it.isNotBlank() },
            ratingScore = score,
            date = json.optString("date").takeIf { it.isNotBlank() },
            tags = tags,
            creator = infobox?.let { parseCreator(it, creatorMediaType) },
            subjectType = json.optInt("type", 0),
        )
    }

    /**
     * 从 infobox 解析创作者/主创。Bangumi 的 value 可能是字符串，也可能是
     * [{v:"..."},...] 数组，两种形态都要兼容。按媒介类型定义键名优先级。
     */
    fun parseCreator(infobox: JSONArray, mediaType: MediaType? = null): String? {
        // 各媒介的键名优先级（Bangumi 各类型条目的常见 infobox 键）
        val keyPriority: List<List<String>> = when (mediaType) {
            MediaType.ANIME -> listOf(
                listOf("导演"), listOf("动画制作", "制作"), listOf("原作"),
            )
            MediaType.MOVIE -> listOf(
                listOf("导演"), listOf("主演", "主演:"), listOf("编剧"),
            )
            MediaType.GAME -> listOf(
                listOf("开发", "开发商", "制作"), listOf("发行", "发行商"), listOf("制作"),
            )
            MediaType.MUSIC -> listOf(
                listOf("艺术家", "艺人", "表演者", "歌手", "艺术家/乐队"), listOf("作曲"),
            )
            else -> listOf( // BOOK 及未指明类型
                listOf("作者", "作者·画家", "作者/画师"), listOf("插画", "画师"), listOf("译者"),
            )
        }
        val map = mutableMapOf<String, String>()
        for (i in 0 until infobox.length()) {
            val entry = infobox.optJSONObject(i) ?: continue
            val key = entry.optString("key").trim()
            if (key.isEmpty()) continue
            val value = when (val raw = entry.opt("value")) {
                is String -> raw.trim()
                is JSONArray -> buildList {
                    for (j in 0 until raw.length()) {
                        val item = raw.optJSONObject(j)?.optString("v")?.trim()
                        if (!item.isNullOrEmpty()) add(item)
                        else raw.optString(j).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }.joinToString("、")
                else -> null
            } ?: continue
            map[key] = value
        }
        for (keys in keyPriority) {
            for (key in keys) {
                map[key]?.let { return it }
            }
        }
        // 兜底：取第一个键值对，尽量别让创作者栏空白
        return map.entries.firstOrNull()?.value
    }

    // ---------------------------------------------------------------- HTTP

    /** 返回响应体字符串；HTTP 非 200 或异常返回 null */
    /**
     * P15 深度链接直取：按 Bangumi subject id 拉取官方元数据（GET /v0/subjects/{id}）。
     * 需在后台线程调用；null = 网络失败或条目不存在。
     */
    fun fetchSubjectByIdSync(id: Long): BangumiSubject? {
        val raw = request(path = "/v0/subjects/$id", method = "GET") ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            BangumiSubject(
                id = id,
                name = obj.optString("name"),
                nameCn = obj.optString("name_cn").takeIf { it.isNotBlank() },
                coverUrl = obj.optJSONObject("images")?.optString("large")
                    ?.takeIf { it.isNotBlank() },
                summary = obj.optString("summary").takeIf { it.isNotBlank() },
                ratingScore = obj.optJSONObject("rating")?.optDouble("score")?.takeIf { !it.isNaN() },
                date = obj.optString("date").takeIf { it.isNotBlank() },
                tags = obj.optJSONArray("tags")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        arr.optJSONObject(i)?.optString("name")?.takeIf { t -> t.isNotBlank() }
                    }
                }.orEmpty(),
                creator = null,
                source = "bangumi",
            )
        }.getOrNull()
    }

    private fun request(path: String, method: String, body: ByteArray? = null): String? = runCatching {
        val conn = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
                outputStream.use { it.write(body) }
            }
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            Log.w("BangumiApi", "HTTP $code for $path")
            conn.disconnect()
            return@runCatching null
        }
        BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
            reader.readText()
        }.also { conn.disconnect() }
    }.getOrNull()
}
