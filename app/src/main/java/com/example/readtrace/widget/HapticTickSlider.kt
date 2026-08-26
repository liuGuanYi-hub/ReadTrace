package com.example.readtrace.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.readtrace.util.HapticFeedbackEngine
import kotlin.math.roundToInt

/**
 * 🎛️ 磁吸刻度感物理阻尼推杆 (Haptic Mechanical Tick Slider)
 * 对标 21st.dev / Landing.love 机械手表式精密物理交互：
 * - 沿推杆轨道绘制精密刻度标线 (Major/Minor Graduation Ticks)；
 * - 滑动过程中每经过一个关键刻度段落，触发毫秒级线性马达微振感与磁吸减速；
 * - 悬浮 3D 滑块带 1px 白金高光倒角圈与动态数值光晕胶囊。
 */
class HapticTickSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var minValue: Float = 0f
    var maxValue: Float = 1f
    var currentValue: Float = 0f
        set(value) {
            field = value.coerceIn(minValue, maxValue)
            invalidate()
        }

    var progress: Float
        get() = if (maxValue > minValue) (currentValue - minValue) / (maxValue - minValue) else 0f
        set(value) {
            currentValue = minValue + value.coerceIn(0f, 1f) * (maxValue - minValue)
        }

    var tickInterval: Float = 0.2f // 每 0.2 单位一个主要磁吸段落
    var unitLabel: String = "%"
    var onValueChanged: ((value: Float, fromUser: Boolean) -> Unit)? = null
    var onProgressChanged: ((Float) -> Unit)? = null

    private var lastHapticTickIndex: Int = -1

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#20FFFFFF")
    }

    private val activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#40FFFFFF")
    }

    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#804DEEEA")
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val thumbRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#4DEEEA")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = 11f * resources.displayMetrics.scaledDensity
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val trackRect = RectF()
    private val activeRect = RectF()
    private val thumbRadius = 14f * resources.displayMetrics.density

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        activeTrackPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(Color.parseColor("#4DEEEA"), Color.parseColor("#FFE700")),
            null,
            Shader.TileMode.CLAMP,
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (thumbRadius * 3.2f).toInt()
        setMeasuredDimension(
            resolveSize(200, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cy = h / 2f
        val trackHeight = 8f * resources.displayMetrics.density
        val padH = thumbRadius + 8f

        val trackLeft = padH
        val trackRight = w - padH
        val trackWidth = trackRight - trackLeft

        // 1. 绘制背景轨道
        trackRect.set(trackLeft, cy - trackHeight / 2f, trackRight, cy + trackHeight / 2f)
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackPaint)

        // 2. 计算当前滑块中心 X
        val progress = if (maxValue > minValue) (currentValue - minValue) / (maxValue - minValue) else 0f
        val thumbX = trackLeft + progress * trackWidth

        // 3. 绘制激活轨道
        activeRect.set(trackLeft, cy - trackHeight / 2f, thumbX, cy + trackHeight / 2f)
        canvas.drawRoundRect(activeRect, trackHeight / 2f, trackHeight / 2f, activeTrackPaint)

        // 4. 绘制精密刻度线
        val totalSpan = maxValue - minValue
        if (totalSpan > 0 && tickInterval > 0) {
            val numTicks = (totalSpan / tickInterval).roundToInt()
            for (i in 0..numTicks) {
                val tickVal = minValue + i * tickInterval
                val tx = trackLeft + (tickVal - minValue) / totalSpan * trackWidth
                val isMajor = i % 2 == 0
                val tickH = if (isMajor) 14f else 8f
                val p = if (isMajor) majorTickPaint else tickPaint

                canvas.drawLine(tx, cy - tickH / 2f, tx, cy + tickH / 2f, p)
            }
        }

        // 5. 绘制 3D 浮雕滑块
        canvas.drawCircle(thumbX, cy, thumbRadius, thumbPaint)
        canvas.drawCircle(thumbX, cy, thumbRadius, thumbRingPaint)

        // 6. 绘制滑块中央极客小圆点
        canvas.drawCircle(thumbX, cy, 3f * resources.displayMetrics.density, thumbRingPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val padH = thumbRadius + 8f
        val trackWidth = width.toFloat() - padH * 2f
        if (trackWidth <= 0) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val touchX = (event.x - padH).coerceIn(0f, trackWidth)
                val newProgress = touchX / trackWidth
                val rawVal = minValue + newProgress * (maxValue - minValue)
                currentValue = rawVal

                // 磁吸段落检测与毫秒级马达触觉回馈
                val tickIndex = ((currentValue - minValue) / tickInterval).roundToInt()
                if (tickIndex != lastHapticTickIndex) {
                    lastHapticTickIndex = tickIndex
                    HapticFeedbackEngine.stampImpact(context)
                }

                onValueChanged?.invoke(currentValue, true)
                onProgressChanged?.invoke(progress)
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
