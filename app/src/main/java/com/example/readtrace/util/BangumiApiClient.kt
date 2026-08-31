package com.example.readtrace.util

import android.os.Handler
import android.os.Looper
import com.example.readtrace.model.BangumiSubject
import com.example.readtrace.model.MediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
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
     * 搜索条目（v0 搜索接口）。
     * keyword 为空时 sort=rank 即「热门榜单」；sort=match 用于关键词搜索。
     */
    fun searchSubjects(
        keyword: String,
        mediaType: MediaType,
        onResult: (List<BangumiSubject>?) -> Unit,
    ) {
        executor.execute {
            val result = runCatching {
                val type = subjectTypeOf(mediaType)
                val body = JSONObject().apply {
                    put("keyword", keyword.trim())
                    put("sort", if (keyword.isBlank()) "rank" else "match")
                    put("filter", JSONObject().put("type", JSONArray().put(type)))
                }
                val response = request(
                    path = "/v0/search/subjects?limit=40",
                    method = "POST",
                    body = body.toString().toByteArray(StandardCharsets.UTF_8),
                ) ?: return@runCatching null
                val root = JSONObject(response)
                val data = root.optJSONArray("data") ?: return@runCatching emptyList<BangumiSubject>()
                buildList {
                    for (i in 0 until data.length()) {
                        val subject = parseSubject(data.optJSONObject(i) ?: continue, withDetail = false)
                        if (subject != null) add(subject)
                    }
                }
            }.getOrNull()
            mainHandler.post { onResult(result) }
        }
    }

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
        return BangumiSubject(
            id = id,
            name = name,
            nameCn = json.optString("name_cn").takeIf { it.isNotBlank() },
            coverUrl = cover,
            summary = if (withDetail) json.optString("summary").takeIf { it.isNotBlank() } else null,
            ratingScore = score,
            date = json.optString("date").takeIf { it.isNotBlank() },
            tags = tags,
            creator = if (withDetail) infobox?.let { parseCreator(it) } else null,
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
            conn.disconnect()
            return@runCatching null
        }
        BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
            reader.readText()
        }.also { conn.disconnect() }
    }.getOrNull()
}
