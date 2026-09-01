package com.example.readtrace.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.readtrace.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 阿里云短信服务客户端（v1.0.3 认证正式化）
 *
 * 实现 Dysmsapi SendSms 的 RPC 调用，用于手机号验证码速登的正式短信通道。
 *
 * 设计要点：
 * - 零第三方依赖：HttpURLConnection + 手写 HMAC-SHA1 签名（与项目「零臃肿」原则一致）；
 * - 单线程串行执行器 + 主线程回调，调用方无需关心线程切换；
 * - 未配置 AccessKey 时 [isConfigured] 返回 false，调用方（PhoneAuthManager）自动回退沙盒；
 * - 失败不抛异常，统一以 [SmsResult] 结构化返回。
 *
 * 安全说明：AccessKey 经 gradle.properties → BuildConfig 编译进包内，
 * 仅建议授予「发送短信」最小权限；如需更高安全性，应改为自建服务端代理下发。
 */
object AliyunSmsClient {

    private const val TAG = "AliyunSmsClient"

    private const val ENDPOINT = "https://dysmsapi.aliyuncs.com/"
    private const val API_VERSION = "2017-05-25"
    private const val REGION_ID = "cn-hangzhou"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 15000

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 短信下发结果 */
    data class SmsResult(
        val success: Boolean,
        /** 阿里云返回的业务 Code：OK 表示成功 */
        val code: String? = null,
        /** 可读原因，失败时展示给用户 */
        val message: String? = null,
    )

    /** 是否已配置完整的阿里云短信参数 */
    fun isConfigured(): Boolean {
        return BuildConfig.ALIYUN_SMS_ACCESS_KEY_ID.isNotBlank() &&
            BuildConfig.ALIYUN_SMS_ACCESS_KEY_SECRET.isNotBlank() &&
            BuildConfig.ALIYUN_SMS_SIGN_NAME.isNotBlank() &&
            BuildConfig.ALIYUN_SMS_TEMPLATE_CODE.isNotBlank()
    }

    /**
     * 发送 6 位验证码短信
     *
     * @param phone 11 位手机号
     * @param code 6 位验证码
     * @param onResult 主线程回调
     */
    fun sendVerifyCode(phone: String, code: String, onResult: (SmsResult) -> Unit) {
        if (!isConfigured()) {
            mainHandler.post {
                onResult(
                    SmsResult(
                        success = false,
                        code = "NOT_CONFIGURED",
                        message = "未配置阿里云短信参数，请检查 gradle.properties",
                    )
                )
            }
            return
        }
        executor.execute {
            val result = doSend(phone, code)
            mainHandler.post { onResult(result) }
        }
    }

    // ------------------------------------------------------------------ 核心请求

    private fun doSend(phone: String, code: String): SmsResult {
        return try {
            // 模板参数：阿里云模板占位符形如 ${code}，这里填充 JSON
            val templateParam = JSONObject().put("code", code).toString()

            val params = linkedMapOf<String, String>()
            params["AccessKeyId"] = BuildConfig.ALIYUN_SMS_ACCESS_KEY_ID
            params["Action"] = "SendSms"
            params["Format"] = "JSON"
            params["PhoneNumbers"] = phone
            params["RegionId"] = REGION_ID
            params["SignName"] = BuildConfig.ALIYUN_SMS_SIGN_NAME
            params["SignatureMethod"] = "HMAC-SHA1"
            params["SignatureNonce"] = UUID.randomUUID().toString()
            params["SignatureVersion"] = "1.0"
            params["TemplateCode"] = BuildConfig.ALIYUN_SMS_TEMPLATE_CODE
            params["TemplateParam"] = templateParam
            params["Timestamp"] = utcTimestamp()
            params["Version"] = API_VERSION

            // 生成签名并追加到参数
            val signature = sign(BuildConfig.ALIYUN_SMS_ACCESS_KEY_SECRET, params)
            params["Signature"] = signature

            val query = buildCanonicalQuery(params)
            val conn = URL(ENDPOINT + "?" + query).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")

            val status = conn.responseCode
            val body = if (status in 200..299) {
                conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            conn.disconnect()

            parseResponse(status, body)
        } catch (e: Exception) {
            Log.w(TAG, "send sms failed: ${e.message}")
            SmsResult(success = false, code = "NETWORK_ERROR", message = "短信发送失败：${e.message ?: "网络异常"}")
        }
    }

    private fun parseResponse(status: Int, body: String): SmsResult {
        return try {
            val json = JSONObject(body)
            val code = json.optString("Code", "")
            val message = json.optString("Message", "")
            if (code == "OK") {
                SmsResult(success = true, code = code, message = "验证码已发送")
            } else {
                SmsResult(success = false, code = code.ifBlank { "HTTP_$status" }, message = "短信下发失败：$message")
            }
        } catch (e: Exception) {
            SmsResult(success = false, code = "HTTP_$status", message = "短信下发失败（HTTP $status）")
        }
    }

    // ------------------------------------------------------------------ RPC 签名

    /**
     * 阿里云 RPC 签名算法（HMAC-SHA1）：
     * stringToSign = "GET&%2F&" + percentEncode(canonicalizedQueryString)
     * signature = Base64(HMAC-SHA1(secret + "&", stringToSign))
     */
    private fun sign(secret: String, params: Map<String, String>): String {
        val canonical = buildCanonicalQuery(params)
        val stringToSign = "GET&%2F&" + percentEncode(canonical)

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec((secret + "&").toByteArray(StandardCharsets.UTF_8), "HmacSHA1"))
        val digest = mac.doFinal(stringToSign.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }

    /** 参数按 Key 字典序拼接为 key=value&key=value（值做 percentEncode） */
    private fun buildCanonicalQuery(params: Map<String, String>): String {
        return params.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${percentEncode(it.value)}" }
    }

    /**
     * 阿里云 percentEncode：
     * 对 URLEncoder 输出做三处修正：+ → %20、* → %2A、%7E → ~
     */
    private fun percentEncode(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    /** UTC ISO8601 时间戳，如 2026-09-01T05:00:00Z */
    private fun utcTimestamp(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
