package com.example.readtrace.ui

import android.app.Activity
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.readtrace.R
import com.example.readtrace.auth.CuratorAccountManager
import com.example.readtrace.auth.PhoneAuthManager
import com.example.readtrace.model.LoginType
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.OtpInputView

/**
 * 手机号 6 位先锋验证码速登页
 *
 * 完整链路：输入号码 → 获取验证码（60s 冷却）→ 输入 6 位码 → 校验 → 绑定通行证。
 * 校验通过后直接写入 [CuratorAccountManager]，并回传结果通知发起页刷新。
 */
class PhoneAuthActivity : AppCompatActivity() {

    private lateinit var etPhoneNumber: EditText
    private lateinit var btnRequestCode: TextView
    private lateinit var layoutCodeSection: LinearLayout
    private lateinit var otpInputView: OtpInputView
    private lateinit var tvSentTo: TextView
    private lateinit var btnResendCode: TextView
    private lateinit var tvSandboxCodeHint: TextView
    private lateinit var btnVerifyAndLogin: TextView
    private lateinit var tvPhoneAuthStatus: TextView
    private lateinit var btnPhoneAuthBack: View

    private lateinit var phoneAuthManager: PhoneAuthManager
    private lateinit var accountManager: CuratorAccountManager

    /** 当前正在验证的手机号，仅在内存中存活 */
    private var pendingPhone: String = ""
    private var countdownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_auth)

        phoneAuthManager = PhoneAuthManager.getInstance()
        accountManager = CuratorAccountManager.getInstance(this)

        initViews()
        setupListeners()
        restoreCooldownIfNeeded()
    }

    private fun initViews() {
        btnPhoneAuthBack = findViewById(R.id.btnPhoneAuthBack)
        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        btnRequestCode = findViewById(R.id.btnRequestCode)
        layoutCodeSection = findViewById(R.id.layoutCodeSection)
        otpInputView = findViewById(R.id.otpInputView)
        tvSentTo = findViewById(R.id.tvSentTo)
        btnResendCode = findViewById(R.id.btnResendCode)
        tvSandboxCodeHint = findViewById(R.id.tvSandboxCodeHint)
        btnVerifyAndLogin = findViewById(R.id.btnVerifyAndLogin)
        tvPhoneAuthStatus = findViewById(R.id.tvPhoneAuthStatus)

        listOf(btnPhoneAuthBack, btnRequestCode, btnVerifyAndLogin, btnResendCode).forEach {
            ViewAnimationHelper.attachSpringTouch(it)
        }
    }

    private fun setupListeners() {
        btnPhoneAuthBack.setOnClickListener { finish() }

        etPhoneNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvPhoneAuthStatus.text = ""
                // 换了号码就作废上一次的验证码，避免拿旧码去验新号
                if (s.toString() != pendingPhone) {
                    resetCodeSection()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnRequestCode.setOnClickListener { requestVerifyCode() }
        btnResendCode.setOnClickListener { requestVerifyCode() }

        otpInputView.onChanged = { tvPhoneAuthStatus.text = "" }
        otpInputView.onComplete = { code -> verifyAndLogin(code) }

        btnVerifyAndLogin.setOnClickListener {
            val code = otpInputView.getCode()
            if (code.length == PhoneAuthManager.CODE_LENGTH) {
                verifyAndLogin(code)
            } else {
                showStatus("请输入完整的 6 位验证码")
            }
        }
    }

    private fun requestVerifyCode() {
        val phone = etPhoneNumber.text.toString().trim()
        if (!phoneAuthManager.isValidPhone(phone)) {
            showStatus("请输入正确的 11 位手机号")
            return
        }
        pendingPhone = phone
        setRequestButtonEnabled(false)
        showStatus("正在发送验证码…")

        phoneAuthManager.requestCode(phone) { result ->
            when (result) {
                is PhoneAuthManager.RequestResult.Sent -> {
                    layoutCodeSection.visibility = View.VISIBLE
                    btnVerifyAndLogin.visibility = View.VISIBLE
                    tvSentTo.text = "验证码已发送至 +86 ${phoneAuthManager.maskPhone(phone)}"
                    showStatus("")

                    if (result.sandboxCode != null) {
                        tvSandboxCodeHint.text = "沙盒模式：验证码 ${result.sandboxCode}（未接入短信平台，仅本机可见）"
                        tvSandboxCodeHint.visibility = View.VISIBLE
                    } else {
                        tvSandboxCodeHint.visibility = View.GONE
                    }

                    otpInputView.clear()
                    otpInputView.requestInputFocus()
                    startCountdown(result.cooldownSeconds)
                }
                is PhoneAuthManager.RequestResult.Rejected -> {
                    setRequestButtonEnabled(true)
                    showStatus(result.reason)
                }
            }
        }
    }

    private fun verifyAndLogin(code: String) {
        if (pendingPhone.isEmpty()) {
            showStatus("请先获取验证码")
            return
        }
        when (val result = phoneAuthManager.verifyCode(pendingPhone, code)) {
            is PhoneAuthManager.VerifyResult.Success -> {
                val masked = phoneAuthManager.maskPhone(pendingPhone)
                accountManager.login(
                    email = "phone@readtrace.space",
                    nickname = phoneAuthManager.buildDefaultNickname(pendingPhone),
                    avatarKey = "time_capsule",
                    loginType = LoginType.PHONE,
                    phoneMasked = masked,
                    curatorTitle = "星链认证馆长",
                    onSuccess = { account ->
                        accountManager.updateAccount(
                            account.copy(bio = "以 ${masked} 为凭，跨越设备的精神档案守护者。")
                        )
                        HapticFeedbackEngine.stampImpact(this)
                        Toast.makeText(this, "✦ 手机号绑定成功，通行证已激活", Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                )
            }
            is PhoneAuthManager.VerifyResult.Failure -> {
                otpInputView.flashError()
                otpInputView.clear()
                otpInputView.requestInputFocus()
                HapticFeedbackEngine.lightClick(this)
                showStatus(result.reason)
            }
        }
    }

    private fun startCountdown(seconds: Int) {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remain = (millisUntilFinished / 1000).toInt()
                btnResendCode.text = "${remain}s 后重发"
                btnResendCode.isEnabled = false
                btnResendCode.setTextColor(ContextCompat.getColor(this@PhoneAuthActivity, R.color.readtrace_muted))
            }

            override fun onFinish() {
                btnResendCode.text = "重新获取"
                btnResendCode.isEnabled = true
                btnResendCode.setTextColor(ContextCompat.getColor(this@PhoneAuthActivity, R.color.readtrace_accent))
                setRequestButtonEnabled(true)
            }
        }.start()
    }

    /**
     * 页面重建（如旋转屏幕）时恢复冷却状态，避免靠重进页面绕过 60s 限制
     */
    private fun restoreCooldownIfNeeded() {
        val remain = phoneAuthManager.remainingCooldownSeconds()
        if (remain > 0) {
            setRequestButtonEnabled(false)
            startCountdown(remain)
        }
    }

    private fun resetCodeSection() {
        layoutCodeSection.visibility = View.GONE
        btnVerifyAndLogin.visibility = View.GONE
        tvSandboxCodeHint.visibility = View.GONE
        otpInputView.clear()
        pendingPhone = ""
    }

    private fun setRequestButtonEnabled(enabled: Boolean) {
        btnRequestCode.isEnabled = enabled
        btnRequestCode.alpha = if (enabled) 1f else 0.5f
    }

    private fun showStatus(message: String) {
        tvPhoneAuthStatus.text = message
    }

    override fun onDestroy() {
        countdownTimer?.cancel()
        countdownTimer = null
        super.onDestroy()
    }
}
