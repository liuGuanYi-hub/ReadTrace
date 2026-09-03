package com.example.readtrace.util

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 🔌 AI 对话接口客户端 (AiChatClient)
 *
 * 统一封装 OpenAI 兼容的 /chat/completions 调用，供角色大纲分析、元数据补全、
 * 个性化推荐等多处 AI 能力共用，避免各引擎重复实现网络层与超时策略。
 *
 * 为什么强制走 SSE 流式：生成类请求动辄百秒（推理模型尤甚），非流式连接在模型
 * 生成期间完全空闲，会被代理或 CDN 在约 100 秒时强制掐断
 * （curl 56: server closed abruptly）。流式下连接持续有数据流动，不会被回收。
 */
object AiChatClient {

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 300_000

    /** 推理模型的思考 token 也计入输出预算，预算过低会在可见文本产出前就被截断 */
    private const val MAX_OUTPUT_TOKENS = 8192

    /** 免费额度上游会间歇性返回 429，退避重试可显著提高成功率 */
    private const val MAX_ATTEMPTS = 3
    private const val RETRY_BACKOFF_MS = 5_000L

    /**
     * 以流式 SSE 调用 OpenAI 兼容的 /chat/completions，返回拼接完成的助手文本。
     * 失败（超时 / 限流耗尽 / 解析异常）返回 null，由调用方降级至离线兜底数据。
     *
     * 注意：本方法为阻塞调用，耗时可达数分钟，必须在子线程执行。
     */
    fun requestChatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
    ): String? {
        if (apiKey.isBlank()) return null

        val endpoint = baseUrl.trim().trimEnd('/') + "/chat/completions"
        val bodyJson = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
            put("temperature", temperature)
            put("max_tokens", MAX_OUTPUT_TOKENS)
            put("stream", true)
        }
        val payload = bodyJson.toString()

        for (attempt in 1..MAX_ATTEMPTS) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    doInput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "text/event-stream")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload) }

                val code = conn.responseCode
                if (code == 429 && attempt < MAX_ATTEMPTS) {
                    Thread.sleep(RETRY_BACKOFF_MS * attempt)
                    continue
                }
                if (code != 200) return null

                val sb = StringBuilder()
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        val event = line.trim()
                        if (!event.startsWith("data:")) continue
                        val data = event.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        runCatching {
                            val choice = JSONObject(data).getJSONArray("choices").getJSONObject(0)
                            // 主取流式 delta；若服务端忽略 stream 参数而回整包 message，同样兼容
                            val piece = choice.optJSONObject("delta")?.optString("content")
                                ?: choice.optJSONObject("message")?.optString("content")
                            if (!piece.isNullOrEmpty()) sb.append(piece)
                        }
                    }
                }
                val result = sb.toString().trim()
                if (result.isNotBlank()) return result
            } catch (e: Exception) {
                e.printStackTrace()
                if (attempt < MAX_ATTEMPTS) {
                    runCatching { Thread.sleep(RETRY_BACKOFF_MS * attempt) }
                }
            } finally {
                runCatching { conn?.disconnect() }
            }
        }
        return null
    }
}
