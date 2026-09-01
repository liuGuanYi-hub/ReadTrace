package com.example.readtrace.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.readtrace.R
import com.example.readtrace.auth.BiometricAuthHelper
import com.example.readtrace.auth.CuratorAccountManager
import com.example.readtrace.auth.WeChatAuthManager
import com.example.readtrace.model.AuthStatus
import com.example.readtrace.model.CuratorAccount
import com.example.readtrace.model.CuratorCardTheme
import com.example.readtrace.model.LoginType
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.CuratorPassCardView

class CuratorAuthActivity : AppCompatActivity() {

    private lateinit var authPassPreviewCard: CuratorPassCardView
    private lateinit var layoutAvatarList: LinearLayout
    private lateinit var etCuratorNickname: EditText
    private lateinit var etCuratorEmail: EditText
    private lateinit var etCuratorBio: EditText
    private lateinit var layoutBiometricQuickLogin: View
    private lateinit var btnConfirmAuth: TextView
    private lateinit var btnContinueAsGuest: TextView
    private lateinit var btnAuthBack: View
    private lateinit var layoutWeChatLogin: View
    private lateinit var layoutPhoneLogin: View
    private lateinit var tvWeChatLoginHint: TextView

    private var selectedAvatarKey: String = "statue_david"
    private var selectedTheme: CuratorCardTheme = CuratorCardTheme.OBSIDIAN_GOLD
    private lateinit var accountManager: CuratorAccountManager

    /**
     * 微信授权结果回调
     *
     * 沙盒模式下 WXEntryActivity 通过 setResult 回传本地生成的档案；
     * 正式模式下微信客户端不会走这条链路，结果改由 onResume 消费 pendingOfficialCode。
     */
    private val weChatAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(this, "已取消微信授权", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val data = result.data
        val nickname = data?.getStringExtra(WeChatAuthManager.RESULT_NICKNAME)
        if (data == null || nickname.isNullOrEmpty()) {
            // 正式模式回包：只带 code，没有用户资料
            Toast.makeText(this, "已收到微信授权凭证", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val profile = WeChatAuthManager.WeChatProfile(
            nickname = nickname,
            avatarEmoji = data.getStringExtra(WeChatAuthManager.RESULT_AVATAR_EMOJI) ?: "🌊",
            openId = data.getStringExtra(WeChatAuthManager.RESULT_OPEN_ID) ?: "",
            unionId = data.getStringExtra(WeChatAuthManager.RESULT_UNION_ID) ?: "",
        )
        completeWeChatLogin(profile)
    }

    /** 手机号速登结果：成功后刷新页，让通行证显示新的绑定状态 */
    private val phoneAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            refreshFromAccount()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_curator_auth)

        accountManager = CuratorAccountManager.getInstance(this)

        initViews()
        setupAvatarSelector()
        setupListeners()
        updatePreview()
        checkBiometricAvailability()
    }

    private fun initViews() {
        authPassPreviewCard = findViewById(R.id.authPassPreviewCard)
        layoutAvatarList = findViewById(R.id.layoutAvatarList)
        etCuratorNickname = findViewById(R.id.etCuratorNickname)
        etCuratorEmail = findViewById(R.id.etCuratorEmail)
        etCuratorBio = findViewById(R.id.etCuratorBio)
        layoutBiometricQuickLogin = findViewById(R.id.layoutBiometricQuickLogin)
        btnConfirmAuth = findViewById(R.id.btnConfirmAuth)
        btnContinueAsGuest = findViewById(R.id.btnContinueAsGuest)
        btnAuthBack = findViewById(R.id.btnAuthBack)
        layoutWeChatLogin = findViewById(R.id.layoutWeChatLogin)
        layoutPhoneLogin = findViewById(R.id.layoutPhoneLogin)
        tvWeChatLoginHint = findViewById(R.id.tvWeChatLoginHint)

        if (accountManager.authStatus == AuthStatus.AUTHENTICATED) {
            refreshFromAccount()
        }

        listOfNotNull(
            btnConfirmAuth, btnContinueAsGuest, btnAuthBack,
            layoutBiometricQuickLogin, layoutWeChatLogin, layoutPhoneLogin,
        ).forEach {
            ViewAnimationHelper.attachSpringTouch(it)
        }

        updateWeChatEntryHint()
    }

    /** 用当前账号回填表单，第三方登录后用它刷新通行证与资料区 */
    private fun refreshFromAccount() {
        val current = accountManager.currentAccount ?: return
        if (accountManager.authStatus != AuthStatus.AUTHENTICATED) return
        etCuratorNickname.setText(current.nickname)
        etCuratorEmail.setText(current.email)
        etCuratorBio.setText(current.bio)
        selectedAvatarKey = current.avatarKey
        selectedTheme = current.cardTheme
        updatePreview()
    }

    private fun setupAvatarSelector() {
        layoutAvatarList.removeAllViews()
        CuratorAccount.PRESET_AVATARS.forEachIndexed { index, avatar ->
            val isSelected = avatar.key == selectedAvatarKey
            val itemView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val pad = dpToPx(8)
                setPadding(pad, pad, pad, pad)
                val params = LinearLayout.LayoutParams(dpToPx(64), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (index > 0) marginStart = dpToPx(8)
                }
                layoutParams = params
                isClickable = true
                isFocusable = true
                setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_tag_outline_chip)
                setOnClickListener {
                    selectedAvatarKey = avatar.key
                    setupAvatarSelector()
                    updatePreview()
                }
            }

            val tvEmoji = TextView(this).apply {
                text = avatar.emoji
                textSize = 22f
                gravity = Gravity.CENTER
            }
            val tvName = TextView(this).apply {
                text = avatar.name
                textSize = 10f
                setTextColor(ContextCompat.getColor(this@CuratorAuthActivity, if (isSelected) R.color.white else R.color.readtrace_muted))
                gravity = Gravity.CENTER
                val mTop = dpToPx(3)
                setPadding(0, mTop, 0, 0)
            }
            itemView.addView(tvEmoji)
            itemView.addView(tvName)
            layoutAvatarList.addView(itemView)
        }
    }

    private fun setupListeners() {
        btnAuthBack.setOnClickListener { finish() }
        btnContinueAsGuest.setOnClickListener { finish() }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        etCuratorNickname.addTextChangedListener(textWatcher)
        etCuratorEmail.addTextChangedListener(textWatcher)
        etCuratorBio.addTextChangedListener(textWatcher)

        btnConfirmAuth.setOnClickListener {
            val nickname = etCuratorNickname.text.toString().trim()
            val email = etCuratorEmail.text.toString().trim()
            val bio = etCuratorBio.text.toString().trim()

            if (nickname.isEmpty()) {
                Toast.makeText(this, "请输入您的策展人昵称", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            accountManager.login(
                email = if (email.isEmpty()) "curator@readtrace.space" else email,
                nickname = nickname,
                avatarKey = selectedAvatarKey,
                onSuccess = { account ->
                    val updated = account.copy(bio = if (bio.isEmpty()) account.bio else bio, cardTheme = selectedTheme)
                    accountManager.updateAccount(updated)
                    HapticFeedbackEngine.stampImpact(this@CuratorAuthActivity)
                    Toast.makeText(this, "✦ 欢迎入驻！先锋策展人通行证已激活", Toast.LENGTH_SHORT).show()
                    finish()
                }
            )
        }

        layoutWeChatLogin.setOnClickListener { startWeChatAuth() }

        layoutPhoneLogin.setOnClickListener {
            phoneAuthLauncher.launch(Intent(this, PhoneAuthActivity::class.java))
        }

        layoutBiometricQuickLogin.setOnClickListener {
            BiometricAuthHelper.authenticate(
                activity = this,
                title = "策展人生物识别速登",
                subtitle = "验证指纹或面容以解锁您的专属展厅",
                onSuccess = {
                    Toast.makeText(this, "✦ 生物识别验证通过，已登入展厅", Toast.LENGTH_SHORT).show()
                    finish()
                },
                onError = { _, err ->
                    Toast.makeText(this, "生物认证失败: $err", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun updatePreview() {
        val nickname = etCuratorNickname.text.toString().trim()
        val email = etCuratorEmail.text.toString().trim()
        val bio = etCuratorBio.text.toString().trim()

        // 已绑定第三方账号时，预览卡沿用绑定信息展示徽章与脱敏手机号
        val bound = accountManager.currentAccount
            ?.takeIf { accountManager.authStatus == AuthStatus.AUTHENTICATED }

        val previewAccount = CuratorAccount(
            userId = accountManager.currentAccount?.userId ?: "RT-8848-2026",
            email = if (email.isEmpty()) "curator@readtrace.space" else email,
            nickname = if (nickname.isEmpty()) "先锋策展人" else nickname,
            bio = if (bio.isEmpty()) "在书海与光影中，雕刻精神的永恒轮廓。" else bio,
            avatarKey = selectedAvatarKey,
            curatorTitle = bound?.curatorTitle ?: "特约星河馆长",
            cardTheme = selectedTheme,
            joinedDate = "2026-09-01",
            lastSyncTime = System.currentTimeMillis(),
            loginType = bound?.loginType ?: LoginType.MANUAL,
            wechatOpenId = bound?.wechatOpenId.orEmpty(),
            phoneMasked = bound?.phoneMasked.orEmpty(),
            thirdPartyAvatarEmoji = bound?.thirdPartyAvatarEmoji.orEmpty(),
        )
        authPassPreviewCard.bind(previewAccount, AuthStatus.AUTHENTICATED)
    }

    // ------------------------------------------------------------------ 微信一键授权

    /**
     * 根据当前模式选择授权链路
     *
     * 沙盒模式直接调起本地模拟授权页；正式模式通过反射拉起微信客户端，
     * 成功后微信会把结果送到 WXEntryActivity，再由 onResume 消费。
     */
    private fun startWeChatAuth() {
        when (WeChatAuthManager.currentMode()) {
            WeChatAuthManager.Mode.SANDBOX -> {
                weChatAuthLauncher.launch(WeChatAuthManager.buildSandboxAuthIntent(this))
            }
            WeChatAuthManager.Mode.OFFICIAL -> {
                when (val result = WeChatAuthManager.launchOfficialAuth(this)) {
                    WeChatAuthManager.LaunchResult.Sent -> {
                        Toast.makeText(this, "正在唤起微信…", Toast.LENGTH_SHORT).show()
                    }
                    is WeChatAuthManager.LaunchResult.Failed -> {
                        Toast.makeText(this, result.reason, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /** 用微信档案完成登录并写入通行证 */
    private fun completeWeChatLogin(profile: WeChatAuthManager.WeChatProfile) {
        accountManager.login(
            email = "wechat@readtrace.space",
            nickname = profile.nickname,
            avatarKey = "cosmic_star",
            loginType = LoginType.WECHAT,
            wechatOpenId = profile.openId,
            thirdPartyAvatarEmoji = profile.avatarEmoji,
            curatorTitle = "星河互联馆长",
            onSuccess = { account ->
                accountManager.updateAccount(
                    account.copy(
                        bio = "微信互联 #${WeChatAuthManager.maskOpenId(profile.openId)} · 在书海与光影中雕刻精神的轮廓。"
                    )
                )
                HapticFeedbackEngine.stampImpact(this)
                Toast.makeText(this, "✦ 微信授权成功，通行证已激活", Toast.LENGTH_SHORT).show()
                refreshFromAccount()
                finish()
            }
        )
    }

    /** 副标题区分沙盒与正式模式，避免把模拟授权误当成真实微信登录 */
    private fun updateWeChatEntryHint() {
        tvWeChatLoginHint.text = when (WeChatAuthManager.currentMode()) {
            WeChatAuthManager.Mode.SANDBOX -> "沙盒模拟授权 · 未配置微信 AppID"
            WeChatAuthManager.Mode.OFFICIAL -> "用微信昵称与头像生成通行证"
        }
    }

    /**
     * 正式模式下微信的回调不走 ActivityResult，而是系统拉起 WXEntryActivity，
     * 因此在这里消费它暂存下来的 code。
     */
    override fun onResume() {
        super.onResume()
        val code = WeChatAuthManager.consumePendingOfficialCode()
        if (!code.isNullOrEmpty()) {
            handleOfficialWeChatCode(code)
        }
    }

    /**
     * 处理正式模式的授权 code
     *
     * 换取用户资料需要服务端参与（用 code 换 access_token 再拉用户信息），
     * 本项目是纯本地应用、没有自建服务端，所以这里明确告知用户当前能力的边界，
     * 用本地档案兜底完成登录，而不是假装拿到了真实微信昵称。
     */
    private fun handleOfficialWeChatCode(code: String) {
        val profile = WeChatAuthManager.parseOfficialProfile(code)
            ?: WeChatAuthManager.generateSandboxProfile()
        Toast.makeText(
            this,
            "已收到微信授权凭证，本地应用暂无法换取昵称头像，已生成互联档案",
            Toast.LENGTH_LONG
        ).show()
        completeWeChatLogin(profile)
    }

    private fun checkBiometricAvailability() {
        if (BiometricAuthHelper.isBiometricAvailable(this)) {
            layoutBiometricQuickLogin.visibility = View.VISIBLE
        } else {
            layoutBiometricQuickLogin.visibility = View.GONE
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
