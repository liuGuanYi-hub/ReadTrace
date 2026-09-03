package com.example.readtrace.ui

import android.graphics.Color
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
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.example.readtrace.R
import com.example.readtrace.auth.CuratorAccountManager
import com.example.readtrace.model.AuthStatus
import com.example.readtrace.model.CuratorAccount
import com.example.readtrace.model.CuratorCardTheme
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.CuratorPassCardView

class CuratorProfileEditActivity : AppCompatActivity() {

    private lateinit var editPassPreviewCard: CuratorPassCardView
    private lateinit var layoutThemeRow: LinearLayout
    private lateinit var layoutEditAvatarList: LinearLayout
    private lateinit var etEditNickname: EditText
    private lateinit var etEditBio: EditText
    private lateinit var switchBiometric: SwitchCompat
    private lateinit var btnSaveProfile: TextView
    private lateinit var btnLogout: TextView
    private lateinit var btnEditBack: View

    private var selectedAvatarKey: String = "statue_david"
    private var selectedTheme: CuratorCardTheme = CuratorCardTheme.OBSIDIAN_GOLD
    private lateinit var accountManager: CuratorAccountManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_curator_profile_edit)

        accountManager = CuratorAccountManager.getInstance(this)

        initViews()
        setupThemePicker()
        setupAvatarSelector()
        setupListeners()
        updatePreview()
    }

    private fun initViews() {
        editPassPreviewCard = findViewById(R.id.editPassPreviewCard)
        layoutThemeRow = findViewById(R.id.layoutThemeRow)
        layoutEditAvatarList = findViewById(R.id.layoutEditAvatarList)
        etEditNickname = findViewById(R.id.etEditNickname)
        etEditBio = findViewById(R.id.etEditBio)
        switchBiometric = findViewById(R.id.switchBiometric)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)
        btnLogout = findViewById(R.id.btnLogout)
        btnEditBack = findViewById(R.id.btnEditBack)

        val account = accountManager.currentAccount ?: CuratorAccount()
        selectedAvatarKey = account.avatarKey
        selectedTheme = account.cardTheme
        etEditNickname.setText(account.nickname)
        etEditBio.setText(account.bio)
        switchBiometric.isChecked = account.isBiometricEnabled

        listOfNotNull(btnSaveProfile, btnLogout, btnEditBack).forEach {
            ViewAnimationHelper.attachSpringTouch(it)
        }
    }

    private fun setupThemePicker() {
        layoutThemeRow.removeAllViews()
        CuratorCardTheme.values().forEach { theme ->
            val isSelected = theme == selectedTheme
            val chip = TextView(this).apply {
                text = theme.title
                textSize = 12f
                gravity = Gravity.CENTER
                val params = LinearLayout.LayoutParams(0, dpToPx(34), 1f).apply {
                    val m = dpToPx(3)
                    setMargins(m, 0, m, 0)
                }
                layoutParams = params
                isClickable = true
                isFocusable = true
                setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_segmented_container)
                setTextColor(if (isSelected) Color.WHITE else ContextCompat.getColor(this@CuratorProfileEditActivity, R.color.readtrace_ink))
                setOnClickListener {
                    selectedTheme = theme
                    setupThemePicker()
                    updatePreview()
                }
            }
            layoutThemeRow.addView(chip)
        }
    }

    private fun setupAvatarSelector() {
        // 头像卡片几何规格统一由 AvatarChipBuilder 维护，与认证页共用同一实现
        com.example.readtrace.util.AvatarChipBuilder.setup(
            this,
            layoutEditAvatarList,
            selectedAvatarKey,
        ) { key ->
            selectedAvatarKey = key
            setupAvatarSelector()
            updatePreview()
        }
    }

    private fun setupListeners() {
        btnEditBack.setOnClickListener { finish() }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        etEditNickname.addTextChangedListener(watcher)
        etEditBio.addTextChangedListener(watcher)

        switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            accountManager.setBiometricEnabled(isChecked)
        }

        btnSaveProfile.setOnClickListener {
            val nickname = etEditNickname.text.toString().trim()
            val bio = etEditBio.text.toString().trim()

            val current = accountManager.currentAccount ?: CuratorAccount()
            val updated = current.copy(
                nickname = if (nickname.isEmpty()) current.nickname else nickname,
                bio = if (bio.isEmpty()) current.bio else bio,
                avatarKey = selectedAvatarKey,
                cardTheme = selectedTheme,
                isBiometricEnabled = switchBiometric.isChecked,
            )
            accountManager.updateAccount(updated)
            HapticFeedbackEngine.stampImpact(this@CuratorProfileEditActivity)
            Toast.makeText(this, "✦ 通行证档案已保存", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnLogout.setOnClickListener {
            accountManager.logout()
            Toast.makeText(this, "已退出登录，恢复自由漫游模式", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updatePreview() {
        val nickname = etEditNickname.text.toString().trim()
        val bio = etEditBio.text.toString().trim()
        val current = accountManager.currentAccount ?: CuratorAccount()

        val preview = current.copy(
            nickname = if (nickname.isEmpty()) current.nickname else nickname,
            bio = if (bio.isEmpty()) current.bio else bio,
            avatarKey = selectedAvatarKey,
            cardTheme = selectedTheme,
        )
        editPassPreviewCard.bind(preview, accountManager.authStatus)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
