package com.example.readtrace.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.readtrace.R
import com.example.readtrace.auth.BiometricAuthHelper
import com.example.readtrace.auth.CuratorAccountManager
import com.example.readtrace.model.AuthStatus
import com.example.readtrace.model.CuratorAccount
import com.example.readtrace.model.CuratorCardTheme
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

    private var selectedAvatarKey: String = "statue_david"
    private var selectedTheme: CuratorCardTheme = CuratorCardTheme.OBSIDIAN_GOLD
    private lateinit var accountManager: CuratorAccountManager

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

        val current = accountManager.currentAccount
        if (current != null && accountManager.authStatus == AuthStatus.AUTHENTICATED) {
            etCuratorNickname.setText(current.nickname)
            etCuratorEmail.setText(current.email)
            etCuratorBio.setText(current.bio)
            selectedAvatarKey = current.avatarKey
            selectedTheme = current.cardTheme
        }

        listOfNotNull(btnConfirmAuth, btnContinueAsGuest, btnAuthBack, layoutBiometricQuickLogin).forEach {
            ViewAnimationHelper.attachSpringTouch(it)
        }
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

        val previewAccount = CuratorAccount(
            userId = accountManager.currentAccount?.userId ?: "RT-8848-2026",
            email = if (email.isEmpty()) "curator@readtrace.space" else email,
            nickname = if (nickname.isEmpty()) "先锋策展人" else nickname,
            bio = if (bio.isEmpty()) "在书海与光影中，雕刻精神的永恒轮廓。" else bio,
            avatarKey = selectedAvatarKey,
            curatorTitle = "特约星河馆长",
            cardTheme = selectedTheme,
            joinedDate = "2026-09-01",
            lastSyncTime = System.currentTimeMillis(),
        )
        authPassPreviewCard.bind(previewAccount, AuthStatus.AUTHENTICATED)
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
