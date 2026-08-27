package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.example.readtrace.R
import com.example.readtrace.util.HapticFeedbackEngine

/**
 * 墨色烟玻璃悬浮返回钮。
 *
 * 设计语言：深墨渐变圆片 + 白描细边 + 左上汗珠高光。压按时箭头向左“抽弦”回拉、
 * 两道速度线扫出方向脉冲，松手以弹性曲线归位；点击时圆内扩散一圈涟漪，
 * 并触发应用统一触感引擎的轻响。日夜主题共用同一枚深墨玻璃，使其在纸感浅底
 * 与宇宙深底页面上都保持稳定对比。
 */
class BackOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onBackActivated: (() -> Unit)? = null

    private companion object {
        // 墨色烟玻璃：顶部略浮起的墨，底部沉入更深的墨
        const val GLASS_TOP = 0xF21F231E.toInt()
        const val GLASS_BOTTOM = 0xDE121514.toInt()
        const val STROKE_REST = 0x29FFFFFF
        const val STROKE_AFFIXED = 0x40FFFFFF
        const val ARROW_COLOR = Color.WHITE
        const val SHEEN_ALPHA = 0x1EFFFFFF
    }

    // ---- 动画驱动量 -------------------------------------------------------
    /** 箭头向左回拉的位移（px） */
    private var arrowPullPx = 0f
    /** 速度线拖尾相位 0..1 */
    private var trailPhase = -1f
    /** 点击脉冲相位 0..1 */
    private var pulsePhase = -1f
    /** 按压缩放系数 */
    private var pressScale = 1f
    /** 描边当前颜色 */
    private var strokeCurrent = STROKE_REST

    private var pullAnimator: ValueAnimator? = null
    private var trailAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    private var scaleAnimator: ValueAnimator? = null

    // ---- 画笔 -------------------------------------------------------------
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ARROW_COLOR
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ARROW_COLOR
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ARROW_COLOR
    }
    private val clipCircle = Path()

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.action_back)
        elevation = dpf(6f)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
            }
        }
    }

    private fun dpf(value: Float): Float = value * resources.displayMetrics.density

    // ---- 测量 -------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fallback = dpf(46f).toInt()
        setMeasuredDimension(
            resolveAdjustedSize(fallback, widthMeasureSpec),
            resolveAdjustedSize(fallback, heightMeasureSpec)
        )
    }

    private fun resolveAdjustedSize(want: Int, spec: Int): Int {
        return when (MeasureSpec.getMode(spec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(spec)
            MeasureSpec.AT_MOST -> minOf(want, MeasureSpec.getSize(spec))
            else -> want
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val r = minOf(w, h) / 2f
        clipCircle.reset()
        clipCircle.addCircle(w / 2f, h / 2f, r, Path.Direction.CW)

        glassPaint.shader = LinearGradient(
            w / 2f, 0f,
            w / 2f, h.toFloat(),
            GLASS_TOP, GLASS_BOTTOM,
            Shader.TileMode.CLAMP
        )
        sheenPaint.shader = RadialGradient(
            w * 0.34f,
            h * 0.16f,
            r * 1.5f,
            intArrayOf(SHEEN_ALPHA, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    // ---- 绘制 -------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f
        val hairline = dpf(1f)

        canvas.save()
        canvas.scale(pressScale, pressScale, cx, cy)

        // 玻璃墨片本体
        canvas.drawCircle(cx, cy, r - hairline, glassPaint)
        // 左上汗珠高光（裁剪进圆内）
        if (sheenPaint.shader != null) {
            canvas.save()
            canvas.clipPath(clipCircle)
            canvas.drawCircle(cx, cy, r, sheenPaint)
            canvas.restore()
        }
        // 白描细边
        strokePaint.strokeWidth = hairline
        strokePaint.color = strokeCurrent
        canvas.drawCircle(cx, cy, r - hairline / 2f, strokePaint)

        // 箭头（轴杆 + 雪佛龙头部），随 pull 整体向左“抽弦”
        canvas.save()
        canvas.translate(-arrowPullPx, 0f)
        arrowPaint.strokeWidth = dpf(2.2f)
        val tipX = cx - dpf(5.6f)
        val tailX = cx + dpf(6.8f)
        val headSpan = dpf(4.6f)
        val headHalf = dpf(4.4f)
        canvas.drawLine(tailX, cy, tipX, cy, arrowPaint)
        canvas.drawLine(tipX + headSpan, cy - headHalf, tipX, cy, arrowPaint)
        canvas.drawLine(tipX + headSpan, cy + headHalf, tipX, cy, arrowPaint)

        // 速度线：两道自尾部滑出并消散的短划
        if (trailPhase in 0f..1f) {
            val inv = 1f - trailPhase
            val base = tailX + dpf(4.5f)
            dashPaint.strokeWidth = dpf(2f)
            dashPaint.alpha = (inv * 110).toInt().coerceIn(0, 255)
            val d1x = base + trailPhase * dpf(7f)
            canvas.drawLine(d1x, cy, d1x + dpf(3.4f), cy, dashPaint)
            dashPaint.alpha = (inv * 70).toInt().coerceIn(0, 255)
            val d2x = base + dpf(2f) + trailPhase * dpf(10f)
            canvas.drawLine(d2x, cy, d2x + dpf(2.2f), cy, dashPaint)
        }
        canvas.restore()

        // 点击脉冲涟漪：由心向外扩张后消隐
        if (pulsePhase in 0f..1f) {
            val p = pulsePhase
            pulsePaint.alpha = ((1f - p) * 105).toInt().coerceIn(0, 255)
            pulsePaint.strokeWidth = dpf(2.4f - 1.6f * p)
            val pr = (r * (0.55f + 0.4f * p)).coerceAtMost(r - hairline / 2f)
            canvas.drawCircle(cx, cy, pr, pulsePaint)
        }

        canvas.restore()
    }

    // ---- 状态控制 ---------------------------------------------------------

    /**
     * 页面滚离顶部后进入“附着”态：描边略亮、投影抬升。
     * 幂等调用安全。
     */
    fun setAffixed(affixed: Boolean) {
        val targetStroke = if (affixed) STROKE_AFFIXED else STROKE_REST
        val targetZ = dpf(if (affixed) 13f else 6f)
        if (strokeCurrent == targetStroke && translationZ == targetZ) return
        strokeCurrent = targetStroke
        animate().translationZ(targetZ)
            .setDuration(200L)
            .start()
        invalidate()
    }

    /** 入场：自左侧半透明缩入并带轻微过冲 */
    fun playEnter() {
        alpha = 0f
        translationX = dpf(-16f)
        scaleX = 0.6f
        scaleY = 0.6f
        animate()
            .alpha(1f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(520L)
            .setStartDelay(80L)
            .setInterpolator(OvershootInterpolator(1.7f))
            .start()
    }

    private fun playPressDown() {
        cancel(pullAnimator)
        pullAnimator = animateFloat(from = arrowPullPx, to = dpf(3.2f), duration = 140L,
            interpolator = DecelerateInterpolator(1.6f)) { arrowPullPx = it }
        playScaleTo(0.87f, 150L)
        startTrail()
    }

    private fun playRelease() {
        cancel(pullAnimator)
        pullAnimator = animateFloat(from = arrowPullPx, to = 0f, duration = 420L,
            interpolator = OvershootInterpolator(2.4f)) { arrowPullPx = it }
        playScaleTo(1f, 320L)
    }

    private fun playScaleTo(target: Float, duration: Long) {
        cancel(scaleAnimator)
        scaleAnimator = ValueAnimator.ofFloat(pressScale, target).apply {
            this.duration = duration
            this.interpolator =
                if (target < pressScale) DecelerateInterpolator(1.8f) else OvershootInterpolator(2.2f)
            addUpdateListener {
                pressScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startTrail() {
        cancel(trailAnimator)
        trailPhase = 0f
        trailAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380L
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener {
                trailPhase = it.animatedFraction
                invalidate()
            }
            start()
        }
    }

    private fun playPulse() {
        cancel(pulseAnimator)
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 340L
            addUpdateListener {
                pulsePhase = it.animatedFraction
                invalidate()
            }
            start()
        }
    }

    private fun cancel(animator: ValueAnimator?) {
        animator?.cancel()
    }

    private fun animateFloat(
        from: Float,
        to: Float,
        duration: Long,
        interpolator: android.animation.TimeInterpolator,
        onUpdate: (Float) -> Unit
    ): ValueAnimator = ValueAnimator.ofFloat(from, to).apply {
        this.duration = duration
        this.interpolator = interpolator
        addUpdateListener { onUpdate(it.animatedValue as Float) }
        start()
    }

    // ---- 触摸 -------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                playPressDown()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> playRelease()
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        playPulse()
        startTrail()
        HapticFeedbackEngine.lightClick(context)
        onBackActivated?.invoke()
        return super.performClick()
    }

    override fun onDetachedFromWindow() {
        pullAnimator?.cancel()
        trailAnimator?.cancel()
        pulseAnimator?.cancel()
        scaleAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
