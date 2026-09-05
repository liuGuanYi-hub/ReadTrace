package com.example.readtrace.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.readtrace.model.AuthStatus
import com.example.readtrace.model.CuratorAccount
import com.example.readtrace.model.CuratorCardTheme
import com.example.readtrace.util.ViewAnimationHelper

/**
 * 3D 全息先锋策展人通行证卡片 (Curator Holographic Pass)
 * 具备四色主题渐变着色、防伪编号、星系雷达徽记与极光光泽
 */
class CuratorPassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.2f)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(0.8f)
    }
    private val cardBounds = RectF()
    private val cardCornerRadius = dpToPx(18f)

    private val tvHeader: TextView
    private val tvAvatar: TextView
    private val tvNickname: TextView
    private val tvTitle: TextView
    private val tvBindingBadge: TextView
    private val tvPassId: TextView
    private val tvBio: TextView
    private val tvSyncStatus: TextView
    private val tvJoinedDate: TextView

    private var currentAccount: CuratorAccount = CuratorAccount()
    private var currentAuthStatus: AuthStatus = AuthStatus.GUEST

    init {
        setWillNotDraw(false)
        ViewAnimationHelper.attachSpringTouch(this)

        // 内部布局构建
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dpToPx(18f).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // 顶栏：通行证标题 + 防伪编号
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tvHeader = TextView(context).apply {
            text = "CURATOR PASS ✦ 策展人通行证"
            textSize = 10f
            letterSpacing = 0.15f
            setTextColor(Color.parseColor("#80FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvPassId = TextView(context).apply {
            text = "#RT-8848-2026"
            textSize = 10.5f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.parseColor("#C0FFFFFF"))
        }
        headerRow.addView(tvHeader)
        headerRow.addView(tvPassId)
        contentLayout.addView(headerRow)

        // 中间主体：头像 + 昵称 + 阶位徽徽
        val middleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val topMargin = dpToPx(14f).toInt()
            setPadding(0, topMargin, 0, 0)
        }
        tvAvatar = TextView(context).apply {
            text = "🏛️"
            textSize = 34f
            val size = dpToPx(52f).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(14f).toInt()
            }
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1AFFFFFF"))
        }
        // 头像圆角背景
        val avatarBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.parseColor("#20FFFFFF"))
            setStroke(dpToPx(1f).toInt(), Color.parseColor("#40FFFFFF"))
        }
        tvAvatar.background = avatarBg

        val infoCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvNickname = TextView(context).apply {
            text = "阅读策展人"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        tvTitle = TextView(context).apply {
            text = "✦ 特约星河馆长"
            textSize = 11.5f
            setTextColor(Color.parseColor("#E0C9A050"))
            val mTop = dpToPx(3f).toInt()
            setPadding(0, mTop, 0, 0)
        }
        tvBindingBadge = TextView(context).apply {
            text = ""
            textSize = 9.5f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.parseColor("#99FFFFFF"))
            letterSpacing = 0.06f
            val mTop = dpToPx(2f).toInt()
            setPadding(0, mTop, 0, 0)
            visibility = GONE
        }
        infoCol.addView(tvNickname)
        infoCol.addView(tvTitle)
        infoCol.addView(tvBindingBadge)
        middleRow.addView(tvAvatar)
        middleRow.addView(infoCol)
        contentLayout.addView(middleRow)

        // 策展签名座右铭
        tvBio = TextView(context).apply {
            text = "在书海与光影中，雕刻精神的永恒轮廓。"
            textSize = 12f
            setTextColor(Color.parseColor("#B0FFFFFF"))
            maxLines = 2
            val mTop = dpToPx(12f).toInt()
            setPadding(0, mTop, 0, 0)
        }
        contentLayout.addView(tvBio)

        // 底部栏：同步状态 + 加入日期
        val footerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val mTop = dpToPx(14f).toInt()
            setPadding(0, mTop, 0, 0)
        }
        tvSyncStatus = TextView(context).apply {
            text = "● 纯本地就绪"
            textSize = 10.5f
            setTextColor(Color.parseColor("#80FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvJoinedDate = TextView(context).apply {
            text = "SINCE 2026.09"
            textSize = 10f
            letterSpacing = 0.1f
            setTextColor(Color.parseColor("#70FFFFFF"))
        }
        footerRow.addView(tvSyncStatus)
        footerRow.addView(tvJoinedDate)
        contentLayout.addView(footerRow)

        addView(contentLayout)
    }

    fun bind(account: CuratorAccount, status: AuthStatus) {
        currentAccount = account
        currentAuthStatus = status
        applyTextPalette(account.cardTheme)

        tvAvatar.text = account.displayAvatarEmoji()
        tvNickname.text = account.nickname

        // 绑定徽章只在第三方登录时出现，手写入驻时保持卡面干净
        val badge = account.bindingBadge()
        tvBindingBadge.visibility = if (badge.isBlank()) GONE else VISIBLE
        tvBindingBadge.text = badge
        tvTitle.text = if (status == AuthStatus.AUTHENTICATED) "✦ ${account.curatorTitle}" else "✦ 未认证自由旅人"
        tvPassId.text = "#${account.userId}"
        tvBio.text = account.bio
        tvJoinedDate.text = "SINCE ${account.joinedDate.replace("-", ".")}"

        tvSyncStatus.text = when (status) {
            AuthStatus.AUTHENTICATED -> "● 云端已同步"
            AuthStatus.SYNCING -> "✦ 正在云端同步..."
            AuthStatus.GUEST -> "○ 纯本地漫游"
        }
        val syncColor = when (status) {
            AuthStatus.AUTHENTICATED -> Color.parseColor("#48C78E")
            AuthStatus.SYNCING -> Color.parseColor("#FFD700")
            AuthStatus.GUEST -> Color.parseColor("#A0A0A0")
        }
        tvSyncStatus.setTextColor(syncColor)

        invalidate()
    }

    /**
     * 按卡面主题明暗套用文字色板：羊皮纸（PARCHMENT_WOOD）浅底配墨色系文字，
     * 其余深底卡面保持白色系历史观感。修复日间浅底白字混淆不可读。
     */
    private fun applyTextPalette(theme: CuratorCardTheme) {
        val light = theme == CuratorCardTheme.PARCHMENT_WOOD
        val ink = { alpha: String -> Color.parseColor("#${alpha}1A1C19") }
        tvHeader.setTextColor(if (light) ink("99") else Color.parseColor("#80FFFFFF"))
        tvPassId.setTextColor(if (light) ink("C0") else Color.parseColor("#C0FFFFFF"))
        tvNickname.setTextColor(if (light) Color.parseColor("#FF1A1C19") else Color.WHITE)
        tvTitle.setTextColor(if (light) Color.parseColor("#CC8A6D3B") else Color.parseColor("#E0C9A050"))
        tvBindingBadge.setTextColor(if (light) ink("99") else Color.parseColor("#99FFFFFF"))
        tvBio.setTextColor(if (light) ink("B0") else Color.parseColor("#B0FFFFFF"))
        tvJoinedDate.setTextColor(if (light) ink("70") else Color.parseColor("#70FFFFFF"))
        // 头像圆片底与描边同步反色
        val avatarBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.parseColor(if (light) "#1F000000" else "#20FFFFFF"))
            setStroke(dpToPx(1f).toInt(), Color.parseColor(if (light) "#40000000" else "#40FFFFFF"))
        }
        tvAvatar.background = avatarBg
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cardBounds.set(dpToPx(2f), dpToPx(2f), w - dpToPx(2f), h - dpToPx(2f))
    }

    override fun onDraw(canvas: Canvas) {
        val theme = currentAccount.cardTheme
        val startColor = Color.parseColor(theme.surfaceGradientStart)
        val endColor = Color.parseColor(theme.surfaceGradientEnd)
        val accentColor = Color.parseColor(theme.accentColorHex)

        // 绘制卡面背景线性渐变
        cardPaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            startColor, endColor, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(cardBounds, cardCornerRadius, cardCornerRadius, cardPaint)

        // 绘制边框
        strokePaint.color = Color.parseColor(if (theme == CuratorCardTheme.PARCHMENT_WOOD) "#30000000" else "#26FFFFFF")
        canvas.drawRoundRect(cardBounds, cardCornerRadius, cardCornerRadius, strokePaint)

        // 绘制顶部高光微弧线
        glowPaint.color = accentColor
        glowPaint.alpha = 100
        canvas.drawRoundRect(
            cardBounds.left + dpToPx(1f),
            cardBounds.top + dpToPx(1f),
            cardBounds.right - dpToPx(1f),
            cardBounds.top + dpToPx(24f),
            cardCornerRadius,
            cardCornerRadius,
            glowPaint
        )

        super.onDraw(canvas)
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
