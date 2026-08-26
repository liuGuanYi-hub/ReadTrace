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
import android.widget.FrameLayout

/**
 * 🎴 建筑学破壁层叠与非对称卡片容器 (OverlappingBentoCard)
 *
 * P6 阶段二核心组件：
 * 1. 破壁越界渲染（Overlapping / Overhanging）：允许内部封面、唱针、电影票根、浮雕胶囊溢出外边界 12~18dp，打破生硬刚性边框；
 * 2. 物理厚度亚克力背板与 1px 倒角光（Inner Inset Light Rim）：左上角 1px 高光勾边模拟厚板物理折射；
 * 3. 漫反射环境彩色落影（Ambient Colored Drop Shadow）：在卡片与越界物下方投射柔和分层弥散阴影；
 * 4. 完美支持硬件加速与陀螺仪 3D 浮动。
 */
class OverlappingBentoCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var cornerRadiusDp: Float = 22f
        set(value) {
            field = value
            invalidate()
        }

    var cardBgColor: Int = Color.parseColor("#1C1E26")
        set(value) {
            field = value
            bgPaint.color = value
            invalidate()
        }

    var showInsetHighlight: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var ambientGlowColor: Int = Color.parseColor("#224DEEEA")
        set(value) {
            field = value
            invalidate()
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1C1E26")
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#22FFFFFF")
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bgRect = RectF()
    private val shadowRect = RectF()
    private val cardPath = Path()

    init {
        // 关键：允许子 View 溢出绘制，实现 Awwwards 级破壁层叠
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val density = resources.displayMetrics.density
        val r = cornerRadiusDp * density

        bgRect.set(paddingLeft.toFloat(), paddingTop.toFloat(), (w - paddingRight).toFloat(), (h - paddingBottom).toFloat())
        shadowRect.set(bgRect.left + 4f * density, bgRect.top + 8f * density, bgRect.right - 4f * density, bgRect.bottom + 12f * density)

        cardPath.reset()
        cardPath.addRoundRect(bgRect, r, r, Path.Direction.CW)

        // 顶层 1px 线性渐变内发光
        borderPaint.shader = LinearGradient(
            bgRect.left, bgRect.top,
            bgRect.right, bgRect.bottom,
            intArrayOf(Color.parseColor("#40FFFFFF"), Color.parseColor("#10FFFFFF"), Color.parseColor("#05FFFFFF")),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )

        shadowPaint.shader = LinearGradient(
            shadowRect.left, shadowRect.top,
            shadowRect.left, shadowRect.bottom,
            intArrayOf(ambientGlowColor, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val r = cornerRadiusDp * density

        // 1. 绘制底层漫反射弥散落影
        canvas.drawRoundRect(shadowRect, r * 1.1f, r * 1.1f, shadowPaint)

        // 2. 绘制卡片本体背景
        canvas.drawRoundRect(bgRect, r, r, bgPaint)

        // 3. 绘制 1px 倒角物理高光内勾边
        if (showInsetHighlight) {
            canvas.drawRoundRect(bgRect, r, r, borderPaint)
        }

        super.onDraw(canvas)
    }
}
