package com.example.readtrace.auth

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * 手机号 6 位先锋验证码速登管理器
 *
 * 与微信模块一致，采用「沙盒模拟 + 正式通道预留」的双轨设计：
 *
 * - **沙盒模式（当前默认）**：验证码在本地生成，并通过回调直接返回给调用方展示。
 *   这样在没有短信平台账号的情况下，整条「获取验证码 → 输入 → 校验 → 登录」链路
 *   依然可以在真机上完整跑通并验证。
 * - **正式模式**：`requestCode` 内部已预留短信平台（阿里云 / 腾讯云）与
 *   SMS Retriever API 的接入位置，接入后只需把 `dispatchSms()` 换成真实 HTTP 请求，
 *   上层调用方无需改动。
 *
 * 隐私约束：手机号只在本地参与校验，完整号码不写进任何持久化存储，
 * 落库的只有 `maskPhone()` 产出的脱敏串（如 138****8848）。
 */
class PhoneAuthManager private constructor() {

    /** 验证码请求结果 */
    sealed class RequestResult {
        /**
         * 验证码已发出
         * @param sandboxCode 沙盒模式下回传的明文验证码，正式模式恒为 null
         * @param cooldownSeconds 重新获取需要等待的秒数
         */
        data class Sent(val sandboxCode: String?, val cooldownSeconds: Int) : RequestResult()

        /** 请求被拒绝 */
        data class Rejected(val reason: String) : RequestResult()
    }

    /** 校验结果 */
    sealed class VerifyResult {
        object Success : VerifyResult()
        data class Failure(val reason: String) : VerifyResult()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 最近一次下发的验证码会话 */
    private var pendingPhone: String = ""
    private var pendingCode: String = ""
    private var pendingExpireAt: Long = 0L
    private var lastRequestAt: Long = 0L

    /**
     * 是否为合法的中国大陆手机号
     *
     * 规则：11 位数字，1 开头，第二位 3-9。
     */
    fun isValidPhone(phone: String): Boolean {
        return PHONE_REGEX.matches(phone)
    }

    /**
     * 手机号脱敏：保留前 3 位与后 4 位，中间以星号遮蔽
     */
    fun maskPhone(phone: String): String {
        if (phone.length != 11) return phone
        return "${phone.substring(0, 3)}****${phone.substring(7)}"
    }

    /**
     * 距离下次可重新获取验证码还需等待的秒数，0 表示立即可用
     */
    fun remainingCooldownSeconds(now: Long = System.currentTimeMillis()): Int {
        val elapsed = (now - lastRequestAt) / 1000
        val remain = COOLDOWN_SECONDS - elapsed
        return if (remain > 0) remain.toInt() else 0
    }

    /**
     * 请求下发 6 位验证码
     *
     * @param phone 11 位手机号
     * @param onResult 主线程回调
     */
    fun requestCode(phone: String, onResult: (RequestResult) -> Unit) {
        if (!isValidPhone(phone)) {
            onResult(RequestResult.Rejected("请输入正确的 11 位手机号"))
            return
        }
        val cooldown = remainingCooldownSeconds()
        if (cooldown > 0) {
            onResult(RequestResult.Rejected("请等待 ${cooldown}s 后再试"))
            return
        }

        executor.execute {
            val code = generateCode()
            val now = System.currentTimeMillis()
            pendingPhone = phone
            pendingCode = code
            pendingExpireAt = now + CODE_VALID_MILLIS
            lastRequestAt = now

            // 模拟短信通道下发耗时，让加载态在本地也能被真实观察到
            Thread.sleep(SIMULATED_NETWORK_DELAY_MS)

            // 正式模式接入点：把 generateCode() 的结果交给短信平台下发，
            // 并把 dispatchSandboxCode 的 true 改为 false，即可关闭明文回传。
            val deliverPlaintext = dispatchSms(phone, code)

            mainHandler.post {
                onResult(
                    RequestResult.Sent(
                        sandboxCode = if (deliverPlaintext) code else null,
                        cooldownSeconds = COOLDOWN_SECONDS,
                    )
                )
            }
        }
    }

    /**
     * 校验验证码
     */
    fun verifyCode(phone: String, code: String): VerifyResult {
        if (!isValidPhone(phone)) return VerifyResult.Failure("手机号格式不正确")
        if (pendingPhone != phone || pendingCode.isEmpty()) {
            return VerifyResult.Failure("请先获取验证码")
        }
        if (System.currentTimeMillis() > pendingExpireAt) {
            clearPending()
            return VerifyResult.Failure("验证码已过期，请重新获取")
        }
        if (!pendingCode.equals(code, ignoreCase = true)) {
            return VerifyResult.Failure("验证码不正确")
        }
        clearPending()
        return VerifyResult.Success
    }

    /**
     * 生成登录成功后写入通行证的兜底昵称
     *
     * 不直接用手机号，避免通行证上出现任何明文手机号。
     */
    fun buildDefaultNickname(phone: String): String {
        return "旅人 ${phone.takeLast(4)}"
    }

    private fun clearPending() {
        pendingPhone = ""
        pendingCode = ""
        pendingExpireAt = 0L
    }

    private fun generateCode(): String {
        val random = Random(System.nanoTime())
        return buildString {
            repeat(CODE_LENGTH) { append(random.nextInt(10)) }
        }
    }

    /**
     * 短信下发通道
     *
     * @return true 表示沙盒模式（明文回传验证码供本地验证），false 表示正式通道已发出
     *
     * 接入真实短信平台时在此处发起 HTTP 请求：
     * ```
     * // 阿里云 / 腾讯云短信 SDK 调用位置
     * // SmsClient.send(phone, templateId, mapOf("code" to code))
     * ```
     * 同时 Android 端可配合 SMS Retriever API 做验证码自动填充。
     */
    private fun dispatchSms(phone: String, code: String): Boolean {
        // 当前未接入任何短信平台，走沙盒模式
        return true
    }

    companion object {
        private val PHONE_REGEX = Regex("^1[3-9]\\d{9}$")

        const val CODE_LENGTH = 6
        const val COOLDOWN_SECONDS = 60
        private const val CODE_VALID_MILLIS = 5 * 60 * 1000L
        private const val SIMULATED_NETWORK_DELAY_MS = 700L

        @Volatile
        private var instance: PhoneAuthManager? = null

        fun getInstance(): PhoneAuthManager {
            return instance ?: synchronized(this) {
                instance ?: PhoneAuthManager().also { instance = it }
            }
        }
    }
}
