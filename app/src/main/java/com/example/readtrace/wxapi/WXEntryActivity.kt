package com.example.readtrace.wxapi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.readtrace.R
import com.example.readtrace.auth.WeChatAuthManager
import com.example.readtrace.util.ViewAnimationHelper
import java.lang.reflect.Proxy

/**
 * 微信授权标准回调入口
 *
 * 微信 OpenSDK 硬性要求：回调 Activity 必须位于 `{applicationId}.wxapi` 包下且类名为 `WXEntryActivity`。
 * 因此即使当前未集成 SDK，也先把这个位置占住，避免接入时再改包名结构。
 *
 * 本 Activity 同时承担两种职责：
 *
 * 1. **沙盒模式（默认）**：展示一张与微信授权页同构的确认卡，用户点「允许」后
 *    通过 `setResult` 回传一份本地生成的虚拟档案，走完和正式模式一样的业务闭环。
 * 2. **正式模式**：接收微信客户端的授权回包，反射调用 `IWXAPIEventHandler` 处理结果。
 *    由于本项目是纯本地应用、没有自建服务端，无法用 code 换取用户资料，
 *    因此只把 code 暂存起来交给上层降级处理，不做任何虚假的用户信息填充。
 */
class WXEntryActivity : AppCompatActivity() {

    private var sandboxProfile: WeChatAuthManager.WeChatProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 无论沙盒还是正式模式，用户主动返回都等同于取消授权
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithCancelled()
            }
        })

        val isSandbox = intent?.getBooleanExtra(WeChatAuthManager.EXTRA_SANDBOX_MODE, false) ?: false
        if (isSandbox) {
            setupSandboxAuthPage()
        } else {
            handleOfficialIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOfficialIntent(intent)
    }

    // ------------------------------------------------------------------ 沙盒模拟授权页

    private fun setupSandboxAuthPage() {
        setContentView(R.layout.activity_wechat_entry)

        val profile = WeChatAuthManager.generateSandboxProfile()
        sandboxProfile = profile

        val tvNickname = findViewById<TextView>(R.id.tvWeChatNicknamePreview)
        val tvAvatar = findViewById<TextView>(R.id.tvWeChatAvatarPreview)
        val tvMode = findViewById<TextView>(R.id.tvWeChatAuthMode)
        val btnAllow = findViewById<TextView>(R.id.btnWeChatAllow)
        val btnCancel = findViewById<TextView>(R.id.btnWeChatCancel)

        tvNickname.text = profile.nickname
        tvAvatar.text = profile.avatarEmoji
        tvMode.text = "沙盒模拟授权 · 未配置微信 AppID"

        ViewAnimationHelper.attachSpringTouch(btnAllow)
        ViewAnimationHelper.attachSpringTouch(btnCancel)

        btnAllow.setOnClickListener { deliverSandboxResult(profile) }
        btnCancel.setOnClickListener { finishWithCancelled() }
    }

    private fun deliverSandboxResult(profile: WeChatAuthManager.WeChatProfile) {
        val data = Intent().apply {
            putExtra(WeChatAuthManager.RESULT_NICKNAME, profile.nickname)
            putExtra(WeChatAuthManager.RESULT_AVATAR_EMOJI, profile.avatarEmoji)
            putExtra(WeChatAuthManager.RESULT_OPEN_ID, profile.openId)
            putExtra(WeChatAuthManager.RESULT_UNION_ID, profile.unionId)
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private fun finishWithCancelled() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    // ------------------------------------------------------------------ 正式模式回包

    /**
     * 处理微信客户端的授权回包
     *
     * 全程反射，未集成 OpenSDK 时静默结束，不影响主线程任何流程。
     */
    private fun handleOfficialIntent(intent: Intent?) {
        if (intent == null) {
            finishWithCancelled()
            return
        }

        try {
            val factoryClass = Class.forName("com.tencent.mm.opensdk.openapi.WXAPIFactory")
            val api = factoryClass
                .getMethod("createWXAPI", android.content.Context::class.java, String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, this, com.example.readtrace.BuildConfig.WECHAT_APP_ID, true)

            val handlerClass = Class.forName("com.tencent.mm.opensdk.openapi.IWXAPIEventHandler")
            val handler = Proxy.newProxyInstance(
                handlerClass.classLoader,
                arrayOf(handlerClass),
            ) { _, method, args ->
                when (method?.name) {
                    "onResp" -> args?.firstOrNull()?.let { onOfficialResp(it) }
                    "onReq" -> Unit
                }
                null
            }

            val handled = api.javaClass
                .getMethod("handleIntent", Intent::class.java, handlerClass)
                .invoke(api, intent, handler) as? Boolean ?: false

            if (!handled) finishWithCancelled()
        } catch (e: Exception) {
            finishWithCancelled()
        }
    }

    /**
     * 解析微信回包中的授权 code
     *
     * 纯本地应用没有服务端，无法用 code 换取 access_token 与用户资料，
     * 因此这里只暂存 code 交由上层降级（通常是回退沙盒档案），不伪造用户信息。
     */
    private fun onOfficialResp(resp: Any) {
        try {
            val errCode = resp.javaClass.getField("errCode").get(resp) as? Int ?: -1
            if (errCode != 0) {
                finishWithCancelled()
                return
            }
            val code = resp.javaClass.getField("code").get(resp) as? String
            WeChatAuthManager.pendingOfficialCode = code
            setResult(Activity.RESULT_OK)
        } catch (e: Exception) {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

}
