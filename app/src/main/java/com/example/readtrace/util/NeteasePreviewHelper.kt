package com.example.readtrace.util

import android.content.Context
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
 * - 会员曲目（fee=1）自动转酷狗曲库兜底取链（30s 试听）；
 * - 绑定用户网易云 MUSIC_U Cookie 后，会员账号可直接取完整曲目的完整直链。
 *
 * 相关性保证：只接受「曲名与请求标题匹配」的候选（归一化后全等或互相包含），
 * 避免把搜索结果里排前的无关免费歌当成目标曲播放，导致页面标题与实际音频不一致。
 *
 * 隐私约定：MUSIC_U Cookie 仅存于应用私有 SharedPreferences，只随请求头发往 music.163.com，
 * 不落日志、不入 Git。
 */
object NeteasePreviewHelper {

    data class PreviewResult(
        val streamUrl: String,
        val songName: String,
        val isVip: Boolean,
        /** true = 完整曲目直链（非 15s/30s 试听片段） */
        val isFullSong: Boolean = false,
    )

    private data class Candidate(val id: Long, val name: String, val artists: String, val album: String)

    private const val SEARCH_URL = "https://music.163.com/api/cloudsearch/pc"
    private const val PLAYER_URL_API = "https://music.163.com/api/song/enhance/player/url"
    private const val KG_SEARCH_URL = "http://mobilecdn.kugou.com/api/v3/search/song"
    private const val KG_PLAY_INFO_URL = "http://m.kugou.com/app/i/getSongInfo.php"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    private const val PREFS_NAME = "readtrace_player_prefs"
    private const val KEY_MUSIC_U = "netease_music_u"
    /** 包含匹配允许的最大长度差比例（防短词被长标题误命中） */
    private const val MAX_NAME_LENGTH_RATIO = 2f

    /** 读取用户绑定的网易云 MUSIC_U Cookie（未绑定为 null） */
    fun getMusicUCookie(context: Context?): String? {
        val c = context?.applicationContext ?: return null
        return c.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MUSIC_U, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** 保存/清除（传空即清除）用户绑定的 MUSIC_U Cookie */
    fun setMusicUCookie(context: Context, value: String?) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_MUSIC_U, value?.trim()?.takeIf { it.isNotEmpty() }).apply()
    }

    /**
     * 从用户粘贴的任意文本中提取 MUSIC_U 值。
     * 支持三种输入：裸值（32~ 位字母数字）/ "MUSIC_U=xxx" 键值对 / 整段 cookies.txt 或 Cookie 头。
     * 提取不出时返回 null。
     */
    fun extractMusicU(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        Regex("MUSIC_U[=: ]+([A-Za-z0-9]{16,})").find(text)?.let { return it.groupValues[1] }
        // 无键名的整段文本：取最长的纯字母数字连续段兜底
        return Regex("[A-Za-z0-9]{16,}").findAll(text).maxByOrNull { it.value.length }?.value
    }

    /**
     * 后台检索可播放的试听直链，回调在主线程（未找到时返回 null）。
     * 传入 context 时自动携带用户绑定的会员 Cookie，VIP 曲目可直接返回完整直链。
     */
    fun fetchPlayablePreview(
        context: Context? = null,
        title: String,
        artist: String?,
        onResult: (PreviewResult?) -> Unit,
    ) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            val result = runCatching { searchPlayablePreview(context, title, artist) }.getOrNull()
            handler.post { onResult(result) }
        }.start()
    }

    private fun searchPlayablePreview(context: Context?, title: String, artist: String?): PreviewResult? {
        // 与外部跳转网易云搜索保持一致：去掉括号注音后拼接歌手，命中率更高
        val cleanTitle = title.replace(Regex("[（(].*?[)）]"), "").trim()
        val query = if (artist.isNullOrBlank()) cleanTitle else "$cleanTitle $artist"
        val musicU = getMusicUCookie(context)
        val all = searchSongCandidates(query, musicU)
        // 三级匹配兜底（防错别字/专辑无名曲等场景，宁可宽一格也不放无关歌）：
        // ① 曲名匹配（最准）② 专辑名匹配（作品按专辑录入时用）③ 歌手名强匹配（错别字如"沉/沈"时兜底）
        // T2/T3 属于弱信号，必须有歌手信息作约束，否则宁可不播
        val hasArtist = stripArtistAlias(artist) != null
        val matched = when {
            all.any { nameMatches(it.name, cleanTitle) } ->
                all.filter { nameMatches(it.name, cleanTitle) }
                    .sortedByDescending { artistBonus(it.artists, artist) }
            hasArtist && all.any { nameMatches(it.album, cleanTitle) } ->
                all.filter { nameMatches(it.album, cleanTitle) }
                    .sortedByDescending { artistBonus(it.artists, artist) }
            artistStrongMatches(artist, all) ->
                all.filter { artistStrongMatches(artist, it) }
            else -> emptyList()
        }
        if (matched.isEmpty()) return null
        // 批量取链一次拿到全部候选的播放地址与会员状态
        return resolveNeteaseUrls(matched, cleanTitle, musicU)
            ?: kugouFallback(query, cleanTitle, artist)
    }

    /** 歌手名强匹配：归一化后与候选歌手互相包含（兼容"ずっと真夜中でいい。"vs"ずっと真夜中でいいのに。"这类写法差异） */
    private fun artistStrongMatches(wantArtist: String?, candidates: List<Candidate>): Boolean {
        val want = stripArtistAlias(wantArtist) ?: return false
        return candidates.any { artistBonus(it.artists, want) == 1 }
    }

    private fun artistStrongMatches(wantArtist: String?, candidate: Candidate): Boolean {
        val want = stripArtistAlias(wantArtist) ?: return false
        return artistBonus(candidate.artists, want) == 1
    }

    /** 去掉歌手字段里的括号别名（如"(ZUTOMAYO)"）并归一化，空则返回 null */
    private fun stripArtistAlias(artist: String?): String? {
        val cleaned = artist?.replace(Regex("[（(].*?[)）]"), "")?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        return normalize(cleaned).takeIf { it.isNotEmpty() }
    }

    /** 返回候选列表（含曲名/专辑/歌手，用于相关性过滤） */
    private fun searchSongCandidates(query: String, musicU: String?): List<Candidate> {
        val url = "$SEARCH_URL?s=${URLEncoder.encode(query, "UTF-8")}&type=1&limit=20"
        val conn = httpGet(url, referer = "https://music.163.com/", musicU = musicU)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return emptyList()
            val json = JSONObject(readUtf8(conn))
            if (json.optInt("code") != 200) return emptyList()
            val songs = json.optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
            return (0 until songs.length()).mapNotNull { i ->
                val song = songs.optJSONObject(i) ?: return@mapNotNull null
                val id = song.optLong("id")
                if (id <= 0) return@mapNotNull null
                val artists = song.optJSONArray("ar")?.let { ar ->
                    (0 until ar.length()).mapNotNull { j ->
                        ar.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }
                    }.joinToString("/")
                } ?: ""
                val album = song.optJSONObject("al")?.optString("name") ?: ""
                Candidate(id, song.optString("name"), artists, album)
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 批量取链：按候选顺序返回首个可播曲目。
     * - 响应含 freeTrialInfo = 试听片段（15s），否则为完整直链（免费歌或已绑定会员 Cookie）；
     * - 会员曲目（fee=1）无直链时记录首个，待全部候选确认无源后转酷狗兜底。
     */
    private fun resolveNeteaseUrls(
        candidates: List<Candidate>,
        fallbackName: String,
        musicU: String?,
    ): PreviewResult? {
        val ids = candidates.joinToString(",") { it.id.toString() }
        val url = "$PLAYER_URL_API?ids=%5B$ids%5D&br=320000"
        val conn = httpGet(url, referer = "https://music.163.com/", musicU = musicU)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val json = JSONObject(readUtf8(conn))
            if (json.optInt("code") != 200) return null
            val data = json.optJSONArray("data") ?: return null
            val byId = candidates.associateBy { it.id }
            var vipFallback: PreviewResult? = null
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val songUrl = item.optString("url").takeIf { it.isNotBlank() && it != "null" }
                val songName = byId[item.optLong("id")]?.name ?: fallbackName
                if (songUrl != null) {
                    val isTrial = item.has("freeTrialInfo") && !item.isNull("freeTrialInfo")
                    return PreviewResult(songUrl, songName, isVip = false, isFullSong = !isTrial)
                }
                // 会员曲目（fee=1）无直链，记录首个作为酷狗兜底的备选
                if (vipFallback == null && item.optInt("fee") == 1) {
                    vipFallback = PreviewResult("", songName, isVip = true)
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** 会员歌兜底：转酷狗曲库搜索同名曲目并取可播放直链（按 30s 试听播放），与网易云侧同套三级匹配 */
    private fun kugouFallback(query: String, displayTitle: String, artist: String?): PreviewResult? {
        val searchUrl =
            "$KG_SEARCH_URL?keyword=${URLEncoder.encode(query, "UTF-8")}&page=1&pagesize=5"
        val searchConn = httpGet(searchUrl)
        val matched = try {
            if (searchConn.responseCode != HttpURLConnection.HTTP_OK) return null
            val info = JSONObject(readUtf8(searchConn)).optJSONObject("data")?.optJSONArray("info")
                ?: return null
            data class KgCandidate(val hash: String, val name: String, val album: String, val singer: String)
            val items = (0 until info.length()).mapNotNull { i ->
                val item = info.optJSONObject(i) ?: return@mapNotNull null
                val hash = item.optString("hash").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                KgCandidate(
                    hash,
                    item.optString("songname").ifBlank { item.optString("songName") },
                    item.optString("AlbumName"),
                    item.optString("singername"),
                )
            }
            val wantArtist = stripArtistAlias(artist)
            val tiers = listOf(
                items.filter { nameMatches(it.name, displayTitle) },
                items.filter { nameMatches(it.album, displayTitle) },
                if (wantArtist != null) items.filter { artistBonus(it.singer, wantArtist) == 1 } else emptyList(),
            )
            // 三级依次取第一个非空的，再按歌手加权排序
            (tiers.firstOrNull { it.isNotEmpty() } ?: emptyList())
                .sortedByDescending { artistBonus(it.singer, wantArtist) }
                .map { it.hash to it.name }
        } finally {
            searchConn.disconnect()
        }
        for ((hash, _) in matched.take(3)) {
            val infoConn = httpGet("$KG_PLAY_INFO_URL?cmd=playInfo&hash=$hash")
            try {
                if (infoConn.responseCode != HttpURLConnection.HTTP_OK) continue
                val json = JSONObject(readUtf8(infoConn))
                if (json.optInt("errcode") != 0) continue
                val songUrl = json.optString("url").takeIf { it.isNotBlank() } ?: continue
                val name = json.optString("songName").takeIf { it.isNotBlank() } ?: displayTitle
                return PreviewResult(songUrl, name, isVip = true, isFullSong = false)
            } finally {
                infoConn.disconnect()
            }
        }
        return null
    }

    /**
     * 曲名/专辑名相关性判断：归一化（去空格/括号/大小写/常见标点）后全等，
     * 或长度比例 ≤2 的前提下互相包含——比例守卫用于挡掉
     * "沉香学" 被包含在 "豫剧(秦雪梅)--老爹爹莫动怒你暂且息愤-----凤立沉香学唱" 这类误命中。
     */
    private fun nameMatches(songName: String, title: String): Boolean {
        val n = normalize(songName)
        val t = normalize(title)
        if (n.isEmpty() || t.isEmpty()) return false
        if (n == t) return true
        val ratio = maxOf(n.length, t.length).toFloat() / minOf(n.length, t.length).toFloat()
        if (ratio > MAX_NAME_LENGTH_RATIO) return false
        return n.contains(t) || t.contains(n)
    }

    /** 歌手匹配加权：1 = 双方任一包含，0 = 不匹配或歌手缺失（仅排序用，不作硬过滤） */
    private fun artistBonus(songArtists: String, wantArtist: String?): Int {
        if (wantArtist.isNullOrBlank() || songArtists.isBlank()) return 0
        val a = normalize(songArtists)
        val w = normalize(wantArtist)
        return if (a.contains(w) || w.contains(a)) 1 else 0
    }

    /** 归一化：小写化并去掉空格、括号注音与常见中英文标点 */
    private fun normalize(input: String): String {
        return input.lowercase()
            .replace(Regex("[\\s（()）\\[\\]【】・·、，,。.！!？?\\-—~～'\"“”‘’]"), "")
    }

    private fun httpGet(url: String, referer: String? = null, musicU: String? = null): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            if (referer != null) setRequestProperty("Referer", referer)
            // 用户绑定的会员 Cookie：仅发往网易云域名，用于 VIP 曲目取完整直链
            if (musicU != null) setRequestProperty("Cookie", "MUSIC_U=$musicU")
        }
    }

    /** HttpURLConnection 未声明 charset 时默认 ISO-8859-1，须按 UTF-8 强制解码 */
    private fun readUtf8(conn: HttpURLConnection): String {
        return conn.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
    }
}
