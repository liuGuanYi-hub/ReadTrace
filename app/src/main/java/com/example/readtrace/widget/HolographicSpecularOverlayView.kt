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
import android.view.View

class HolographicSpecularOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val bounds = RectF()

    private var currentPitch = 0f
    private var currentRoll = 0f
    var cornerRadius = 24f * resources.displayMetrics.density

    // 优雅全息烫金/彩虹覆膜高光色彩带
    private val rainbowColors = intArrayOf(
        Color.parseColor("#00FFFFFF"),
        Color.parseColor("#12E0C3FC"), // 极浅紫霞
        Color.parseColor("#208EC5FC"), // 晨曦冰蓝
        Color.parseColor("#38FFFFFF"), // 核心聚光纯白
        Color.parseColor("#20FED6E3"), // 浅樱粉
        Color.parseColor("#12FFF6B7"), // 浅金曜
        Color.parseColor("#00FFFFFF"),
    )

    private val colorPositions = floatArrayOf(
        0.0f, 0.20f, 0.40f, 0.50f, 0.60f, 0.80f, 1.0f,
    )

    init {
        setWillNotDraw(false)
    }

    fun updateAngles(pitch: Float, roll: Float) {
        this.currentPitch = pitch
        this.currentRoll = roll
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        canvas.save()
        canvas.clipPath(clipPath)

        val w = width.toFloat()
        val h = height.toFloat()

        // 1. 正向全息彩虹扫光
        val centerOffsetX = currentRoll * (w * 0.85f)
        val centerOffsetY = -currentPitch * (h * 0.85f)

        val startX = -w * 0.4f + centerOffsetX
        val startY = -h * 0.4f + centerOffsetY
        val endX = w * 1.4f + centerOffsetX
        val endY = h * 1.4f + centerOffsetY

        paint.shader = LinearGradient(
            startX,
            startY,
            endX,
            endY,
            rainbowColors,
            colorPositions,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(bounds, paint)

        // 2. 反向差速全息烫金高光层（裸眼 3D 浮雕深层反光）
        val reverseOffsetX = -currentRoll * (w * 0.45f)
        val reverseOffsetY = currentPitch * (h * 0.45f)

        paint.shader = LinearGradient(
            w * 0.2f + reverseOffsetX,
            -h * 0.2f + reverseOffsetY,
            w * 0.8f + reverseOffsetX,
            h * 1.2f + reverseOffsetY,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#18E0A96D"), Color.parseColor("#30FFFFFF"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(bounds, paint)

        canvas.restore()
    }
}
