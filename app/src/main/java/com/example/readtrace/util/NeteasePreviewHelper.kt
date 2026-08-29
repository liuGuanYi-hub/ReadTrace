package com.example.readtrace.util

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 网易云试听检索助手
 *
 * 按「曲名 + 歌手」搜索网易云曲库，通过 /api/song/enhance/player/url 批量取链：
 * - 免费曲目直接返回可播放直链（15s 试听）；
 * - 会员曲目（fee=1）自动转酷狗曲库兜底取链（30s 试听）。
 */
object NeteasePreviewHelper {

    data class PreviewResult(
        val streamUrl: String,
        val songName: String,
        val isVip: Boolean,
    )

    private const val SEARCH_URL = "https://music.163.com/api/cloudsearch/pc"
    private const val PLAYER_URL_API = "https://music.163.com/api/song/enhance/player/url"
    private const val KG_SEARCH_URL = "http://mobilecdn.kugou.com/api/v3/search/song"
    private const val KG_PLAY_INFO_URL = "http://m.kugou.com/app/i/getSongInfo.php"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    /**
     * 后台检索可播放的试听直链，回调在主线程（未找到时返回 null）。
     */
    fun fetchPlayablePreview(title: String, artist: String?, onResult: (PreviewResult?) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            val result = runCatching { searchPlayablePreview(title, artist) }.getOrNull()
            handler.post { onResult(result) }
        }.start()
    }

    private fun searchPlayablePreview(title: String, artist: String?): PreviewResult? {
        // 与外部跳转网易云搜索保持一致：去掉括号注音后拼接歌手，命中率更高
        val cleanTitle = title.replace(Regex("[（(].*?[)）]"), "").trim()
        val query = if (artist.isNullOrBlank()) cleanTitle else "$cleanTitle $artist"
        val candidates = searchSongCandidates(query)
        if (candidates.isEmpty()) return null
        // 批量取链一次拿到全部候选的播放地址与会员状态
        return resolveNeteaseUrls(candidates, cleanTitle)
            ?: kugouFallback(query, cleanTitle)
    }

    /** 返回 (歌曲ID, 歌曲名) 候选列表 */
    private fun searchSongCandidates(query: String): List<Pair<Long, String>> {
        val url = "$SEARCH_URL?s=${URLEncoder.encode(query, "UTF-8")}&type=1&limit=20"
        val conn = httpGet(url, referer = "https://music.163.com/")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return emptyList()
            val json = JSONObject(readUtf8(conn))
            if (json.optInt("code") != 200) return emptyList()
            val songs = json.optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
            return (0 until songs.length()).mapNotNull { i ->
                val song = songs.optJSONObject(i) ?: return@mapNotNull null
                val id = song.optLong("id")
                if (id > 0) id to song.optString("name") else null
            }
        } finally {
            conn.disconnect()
        }
    }

    /** 批量取链：优先返回免费可播曲目；首个会员曲目暂存，待全部候选确认无免费源后作为兜底 */
    private fun resolveNeteaseUrls(
        candidates: List<Pair<Long, String>>,
        fallbackName: String,
    ): PreviewResult? {
        val ids = candidates.joinToString(",") { it.first.toString() }
        val url = "$PLAYER_URL_API?ids=%5B$ids%5D&br=128000"
        val conn = httpGet(url, referer = "https://music.163.com/")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val json = JSONObject(readUtf8(conn))
            if (json.optInt("code") != 200) return null
            val data = json.optJSONArray("data") ?: return null
            val nameOf = candidates.toMap()
            var vipFallback: PreviewResult? = null
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val songUrl = item.optString("url").takeIf { it.isNotBlank() && it != "null" }
                val songName = nameOf[item.optLong("id")] ?: fallbackName
                if (songUrl != null) {
                    return PreviewResult(songUrl, songName, isVip = false)
                }
                // 会员曲目（fee=1）无直链，记录首个作为酷狗兜底的备选
                if (vipFallback == null && item.optInt("fee") == 1) {
                    vipFallback = PreviewResult("", songName, isVip = true)
                }
            }
            return vipFallback?.let { kugouFallback(fallbackName, it.songName) }
        } finally {
            conn.disconnect()
        }
    }

    /** 会员歌兜底：转酷狗曲库搜索同名曲目并取可播放直链（按 30s 试听播放） */
    private fun kugouFallback(query: String, displayTitle: String): PreviewResult? {
        val searchUrl =
            "$KG_SEARCH_URL?keyword=${URLEncoder.encode(query, "UTF-8")}&page=1&pagesize=5"
        val searchConn = httpGet(searchUrl)
        val hashes = try {
            if (searchConn.responseCode != HttpURLConnection.HTTP_OK) return null
            val info = JSONObject(readUtf8(searchConn)).optJSONObject("data")?.optJSONArray("info")
                ?: return null
            (0 until info.length()).mapNotNull { i ->
                info.optJSONObject(i)?.optString("hash")?.takeIf { it.isNotBlank() }
            }
        } finally {
            searchConn.disconnect()
        }
        for (hash in hashes.take(3)) {
            val infoConn = httpGet("$KG_PLAY_INFO_URL?cmd=playInfo&hash=$hash")
            try {
                if (infoConn.responseCode != HttpURLConnection.HTTP_OK) continue
                val json = JSONObject(readUtf8(infoConn))
                if (json.optInt("errcode") != 0) continue
                val songUrl = json.optString("url").takeIf { it.isNotBlank() } ?: continue
                val name = json.optString("songName").takeIf { it.isNotBlank() } ?: displayTitle
                return PreviewResult(songUrl, name, isVip = true)
            } finally {
                infoConn.disconnect()
            }
        }
        return null
    }

    private fun httpGet(url: String, referer: String? = null): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            if (referer != null) setRequestProperty("Referer", referer)
        }
    }

    /** HttpURLConnection 未声明 charset 时默认 ISO-8859-1，须按 UTF-8 强制解码 */
    private fun readUtf8(conn: HttpURLConnection): String {
        return conn.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
    }
}
