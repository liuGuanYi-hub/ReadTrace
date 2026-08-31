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
 * 网易云音乐公开 Web API 客户端（v4.2.19 多源改造）。
 *
 * 选型理由：音乐分类需要国内真实听众的热门取向；music.163.com 的公开搜索/榜单
 * Web 接口（无需登录、结构化 JSON）实测可用，比网页解析稳定得多。
 *
 * - 搜索：/api/search/get/web?s={kw}&type=1&limit=50 → result.songs[]
 * - 热榜：/api/playlist/detail?id=3778678（云音乐飙升榜，约 100 首）→ result.tracks[]
 * - 封面：album.picUrl（网易云 CDN）；创作者：artists[].name
 *
 * 覆盖：MUSIC。影视/番剧/书籍仍由豆瓣，游戏由小黑盒，番剧回切 Bangumi 官方 API。
 * 合规约束：只调公开接口、不带任何用户 Cookie（用户的 MUSIC_U 仅用于站内播放，绝不用于公开榜单）。
 */
object NeteaseClient {

    private const val API = "https://music.163.com/api"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 ReadTrace/4.2.19"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 15000
    private const val CACHE_DIR = "netease_cache"
    private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    private const val CACHE_MAX_FILES = 20
    private const val REQUEST_INTERVAL_MS = 300L

    /** 云音乐飙升榜 playlist id */
    private const val TOPLIST_ID = "3778678"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lastRequestAt = AtomicInteger(0)

    /**
     * 榜单（keyword 为空 → 飙升榜）或搜索。cache-first，24h。
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
            val url = if (kw.isEmpty()) {
                "$API/playlist/detail?id=$TOPLIST_ID"
            } else {
                "$API/search/get/web?s=${java.net.URLEncoder.encode(kw, "UTF-8").replace("+", "%20")}&type=1&limit=50"
            }
            val json = fetch(appContext, url, if (kw.isEmpty()) "$API/playlist/detail" else "$API/search/get/web")
            val subjects = json?.let { parse(it, kw) }
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

    /** 详情：歌曲无简介，搜索结果已含创作者/封面，直接返回自身（不发网络） */
    fun getSubjectDetail(subject: BangumiSubject, onResult: (BangumiSubject?) -> Unit) {
        mainHandler.post { onResult(subject) }
    }

    // ---------------------------------------------------------------- 解析

    private fun parse(json: String, keyword: String): List<BangumiSubject>? = runCatching {
        val root = JSONObject(json)
        val result = root.optJSONObject("result") ?: return@runCatching null
        // 热榜接口字段为 tracks，搜索接口为 songs
        val arr = result.optJSONArray("tracks")
            ?: result.optJSONArray("songs")
            ?: return@runCatching emptyList<BangumiSubject>()
        buildList {
            for (i in 0 until arr.length()) {
                val song = arr.optJSONObject(i) ?: continue
                val id = song.optLong("id", 0L)
                val name = song.optString("name").trim()
                if (id <= 0L || name.isEmpty()) continue
                val artists = song.optJSONArray("artists")
                val creator = buildList {
                    artists?.let { a ->
                        for (j in 0 until a.length()) {
                            val n = a.optJSONObject(j)?.optString("name")?.trim()
                            if (!n.isNullOrEmpty()) add(n)
                        }
                    }
                }.take(3).joinToString(" / ").takeIf { it.isNotEmpty() }
                val album = song.optJSONObject("album")
                val cover = album?.optString("picUrl")?.takeIf { it.startsWith("http") }
                val albumName = album?.optString("name")?.takeIf { it.isNotBlank() }
                add(
                    BangumiSubject(
                        id = id,
                        name = name,
                        nameCn = name,
                        coverUrl = cover,
                        ratingScore = null,
                        creator = creator,
                        summary = albumName,
                        subjectType = 3,
                    ),
                )
            }
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    // ---------------------------------------------------------------- 缓存

    private fun cacheKeyOf(keyword: String): String {
        val normalized = "netease:search|music|${keyword.lowercase()}"
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
            setRequestProperty("Accept", "application/json,text/plain,*/*")
            setRequestProperty("Referer", referer)
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
        }
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            Log.w("NeteaseClient", "HTTP $code for $url")
            conn.disconnect()
            return@runCatching null
        }
        BufferedReader(
            InputStreamReader(conn.inputStream, StandardCharsets.UTF_8),
        ).use { it.readText() }.also { conn.disconnect() }
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