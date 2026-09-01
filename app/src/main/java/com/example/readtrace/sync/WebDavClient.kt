package com.example.readtrace.sync

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * 🛡️ 轻量 WebDAV 客户端 (WebDavClient)
 *
 * 标准 WebDAV 协议最小子集（PUT/GET），兼容坚果云、Nextcloud、
 * 群晖/威联通 NAS、阿里云盘 WebDAV 等一切标准实现。
 * 仅使用 JDK HttpURLConnection，零第三方依赖；Basic 认证 + 15s 超时。
 */
object WebDavClient {

    data class Config(
        val serverUrl: String,
        val username: String,
        val password: String,
    )

    data class WebDavResult(
        val success: Boolean,
        val httpCode: Int,
        val body: String?,
        val message: String? = null,
    )

    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 30_000

    private fun basicAuthHeader(config: Config): String =
        "Basic " + Base64.getEncoder().encodeToString(
            "${config.username}:${config.password}".toByteArray(StandardCharsets.UTF_8),
        )

    private fun normalizeUrl(serverUrl: String, path: String): String {
        val base = serverUrl.trim().trimEnd('/')
        val file = path.trim().trimStart('/')
        return "$base/$file"
    }

    private fun open(config: Config, path: String, method: String): HttpURLConnection {
        val conn = URL(normalizeUrl(config.serverUrl, path)).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.setRequestProperty("Authorization", basicAuthHeader(config))
        return conn
    }

    /** PUT 上传文本；目标父目录不存在时部分服务器返回 409，调用方可先 mkdir 级路径 */
    fun putText(config: Config, path: String, content: String): WebDavResult = runCatching {
        val conn = open(config, path, "PUT")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.outputStream.use { it.write(content.toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        WebDavResult(code in 200..299, code, null)
    }.getOrElse { WebDavResult(false, -1, null, it.message) }

    /** GET 下载文本；404 视为远端不存在（返回 success=true, body=null） */
    fun getText(config: Config, path: String): WebDavResult = runCatching {
        val conn = open(config, path, "GET")
        val code = conn.responseCode
        if (code == 404) {
            WebDavResult(true, code, null)
        } else if (code in 200..299) {
            val body = BufferedReader(
                InputStreamReader(conn.inputStream, StandardCharsets.UTF_8),
            ).use { it.readText() }
            WebDavResult(true, code, body)
        } else {
            WebDavResult(false, code, null, "HTTP $code")
        }
    }.getOrElse { WebDavResult(false, -1, null, it.message) }

    /** MKCOL 创建远端目录（已存在视为成功） */
    fun makeCollection(config: Config, path: String): WebDavResult = runCatching {
        val conn = open(config, path, "MKCOL")
        val code = conn.responseCode
        WebDavResult(code in 200..299 || code == 405, code, null)
    }.getOrElse { WebDavResult(false, -1, null, it.message) }

    /** 连通性测试：尝试读取清单文件（404 也算连通） */
    fun testConnection(config: Config): WebDavResult =
        getText(config, "/readtrace/manifest.json")
}
