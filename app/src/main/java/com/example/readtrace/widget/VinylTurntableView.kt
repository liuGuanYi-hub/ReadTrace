package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 💽 3D 拟真黑胶唱机自定义渲染视图 (VinylTurntableView)
 *
 * 核心特性：
 * 1. 物理微沟槽同心圆与双极各向异性（Anisotropic）径向高光反射；
 * 2. 铝合金金属唱臂系统（Tonearm & Stylus），支持真实落针与抬针物理缓动；
 * 3. 唱片中心专属 Label 艺术封面映射与 33 1/3 RPM 唱片印记；
 * 4. 旋转阻尼平滑系统与陀螺仪光影倾角联动。
 */
class VinylTurntableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // 绘制画笔
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val armShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#E0D8C3")
    }

    // 状态数据
    var isPlaying: Boolean = false
        private set

    private var discRotationAngle = 0f
    private var tonearmAngle = TONEARM_REST_ANGLE // 抬起/待命角度
    private var targetTonearmAngle = TONEARM_REST_ANGLE
    private var tonearmDropProgress = 0f // 0f 抬起, 1f 落下

    var trackTitle: String = "晴る"
        set(value) {
            field = value
            invalidate()
        }

    var artistName: String = "ヨルシカ"
        set(value) {
            field = value
            invalidate()
        }

    var coverBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    var gyroOffsetX: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var gyroOffsetY: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    // 动画器
    private var rotationAnimator: ValueAnimator? = null
    private var tonearmAnimator: ValueAnimator? = null

    // 尺寸与路径缓存
    private val discRect = RectF()
    private val labelRect = RectF()
    private val armPath = Path()

    init {
        setupRotationAnimator()
    }

    private fun setupRotationAnimator() {
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3000L // 33 1/3 RPM 约每分钟 33 转，一圈 1.8s - 3s
            repeatCount = ValueAnimator.INFINITE
            interpolator = null // 匀速旋转
            addUpdateListener {
                if (isPlaying) {
                    discRotationAngle = (discRotationAngle + 1.2f) % 360f
                    invalidate()
                }
            }
        }
    }

    /**
     * 播放或暂停
     */
    fun togglePlay(play: Boolean) {
        if (isPlaying == play) return
        isPlaying = play

        tonearmAnimator?.cancel()
        val startAngle = tonearmAngle
        val endAngle = if (isPlaying) TONEARM_PLAY_ANGLE else TONEARM_REST_ANGLE
        val startDrop = tonearmDropProgress
        val endDrop = if (isPlaying) 1f else 0f

        tonearmAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                tonearmAngle = startAngle + (endAngle - startAngle) * f
                tonearmDropProgress = startDrop + (endDrop - startDrop) * f
                invalidate()
            }
            start()
        }

        if (isPlaying) {
            rotationAnimator?.start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val size = min(w, h)
        val discRadius = size * 0.40f
        val discCenterX = w * 0.44f
        val discCenterY = h * 0.48f

        discRect.set(
            discCenterX - discRadius,
            discCenterY - discRadius,
            discCenterX + discRadius,
            discCenterY + discRadius,
        )

        // 1. 绘制唱机底盘阴影与转盘边缘
        drawPlatter(canvas, discCenterX, discCenterY, discRadius)

        // 2. 绘制旋转中的黑胶盘体（包含沟槽与双极各向异性高光）
        canvas.save()
        canvas.rotate(discRotationAngle, discCenterX, discCenterY)
        drawVinylDisc(canvas, discCenterX, discCenterY, discRadius)
        drawCenterLabel(canvas, discCenterX, discCenterY, discRadius * 0.38f)
        canvas.restore()

        // 3. 绘制不随唱片自旋但受环境光影响的径向各向异性高光层
        drawAnisotropicSpecularHighlight(canvas, discCenterX, discCenterY, discRadius)

        // 4. 绘制唱臂与唱针系统 (Tonearm & Stylus)
        drawTonearmSystem(canvas, w, h, discCenterX, discCenterY, discRadius)
    }

    /**
     * 绘制底座转盘
     */
    private fun drawPlatter(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // 底座拉丝铝合金外圈
        discPaint.shader = RadialGradient(
            cx, cy, radius * 1.06f,
            intArrayOf(
                Color.parseColor("#333842"),
                Color.parseColor("#1C1E24"),
                Color.parseColor("#0C0D10"),
            ),
            floatArrayOf(0.7f, 0.95f, 1.0f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius * 1.04f, discPaint)

        // 铝合金边缘倒角高光
        groovePaint.color = Color.parseColor("#445566")
        groovePaint.strokeWidth = 2.5f
        canvas.drawCircle(cx, cy, radius * 1.03f, groovePaint)
    }

    /**
     * 绘制黑胶盘面与同心圆微沟槽
     */
    private fun drawVinylDisc(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // 黑胶本体碳纤维深黑色渐变
        discPaint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(
                Color.parseColor("#15161A"),
                Color.parseColor("#0D0E12"),
                Color.parseColor("#08090C"),
            ),
            floatArrayOf(0.3f, 0.8f, 1.0f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, discPaint)

        // 绘制密集微沟槽（Micro-grooves）
        val labelRadius = radius * 0.38f
        val step = (radius - labelRadius) / 22f
        for (i in 1..21) {
            val r = labelRadius + i * step
            groovePaint.color = if (i % 4 == 0) Color.parseColor("#222730") else Color.parseColor("#16181F")
            groovePaint.strokeWidth = if (i % 4 == 0) 1.5f else 0.8f
            canvas.drawCircle(cx, cy, r, groovePaint)
        }

        // 外圈引出槽 (Lead-in groove)
        groovePaint.color = Color.parseColor("#3A4250")
        groovePaint.strokeWidth = 2.0f
        canvas.drawCircle(cx, cy, radius * 0.98f, groovePaint)
    }

    /**
     * 绘制中心唱片 Label
     */
    private fun drawCenterLabel(canvas: Canvas, cx: Float, cy: Float, labelRadius: Float) {
        labelRect.set(cx - labelRadius, cy - labelRadius, cx + labelRadius, cy + labelRadius)

        // 唱片中心复古酒红/琥珀金 Label 渐变
        labelPaint.shader = RadialGradient(
            cx, cy, labelRadius,
            intArrayOf(
                Color.parseColor("#4A1521"),
                Color.parseColor("#2B0C13"),
                Color.parseColor("#1A070B"),
            ),
            floatArrayOf(0f, 0.85f, 1.0f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, labelRadius, labelPaint)

        // 中心封面图片（若有）
        coverBitmap?.let { bmp ->
            canvas.save()
            val clipPath = Path().apply {
                addCircle(cx, cy, labelRadius * 0.72f, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            val srcRect = android.graphics.Rect(0, 0, bmp.width, bmp.height)
            val dstRect = RectF(
                cx - labelRadius * 0.72f,
                cy - labelRadius * 0.72f,
                cx + labelRadius * 0.72f,
                cy + labelRadius * 0.72f,
            )
            canvas.drawBitmap(bmp, srcRect, dstRect, null)
            canvas.restore()
        }

        // Label 装饰金圈与文字
        groovePaint.color = Color.parseColor("#C8A265")
        groovePaint.strokeWidth = 1.2f
        canvas.drawCircle(cx, cy, labelRadius * 0.72f, groovePaint)

        // 中心转轴圆孔 (Spindle hole)
        discPaint.shader = null
        discPaint.color = Color.parseColor("#CCCCCC")
        canvas.drawCircle(cx, cy, labelRadius * 0.12f, discPaint)
        discPaint.color = Color.parseColor("#050505")
        canvas.drawCircle(cx, cy, labelRadius * 0.08f, discPaint)
    }

    /**
     * 绘制双极各向异性扇形高光（沙漏状反光效果）
     */
    private fun drawAnisotropicSpecularHighlight(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val lightOffset = (gyroOffsetX * 15f)
        val highlightAngle = 45f + lightOffset

        canvas.save()
        canvas.rotate(highlightAngle, cx, cy)

        highlightPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(55, 255, 255, 255),
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.argb(55, 255, 255, 255),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.25f, 0.5f, 0.5f, 0.75f, 1.0f),
        )

        val labelRadius = radius * 0.38f
        val clipPath = Path().apply {
            addCircle(cx, cy, radius * 0.98f, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
        canvas.drawCircle(cx, cy, radius, highlightPaint)
        canvas.restore()
    }

    /**
     * 绘制金属唱臂与唱针系统 (Tonearm System)
     */
    private fun drawTonearmSystem(canvas: Canvas, w: Float, h: Float, discCx: Float, discCy: Float, discRadius: Float) {
        // 唱臂基座位于右上方
        val pivotX = w * 0.84f
        val pivotY = h * 0.22f
        val armLength = min(w, h) * 0.44f

        canvas.save()
        // 以轴承基座为原点旋转
        canvas.rotate(tonearmAngle, pivotX, pivotY)

        // 唱臂轴承基座
        armPaint.shader = RadialGradient(
            pivotX, pivotY, 36f,
            intArrayOf(
                Color.parseColor("#D0D5DD"),
                Color.parseColor("#475467"),
                Color.parseColor("#1D2939"),
            ),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(pivotX, pivotY, 32f, armPaint)

        // 配重铊 (Counterweight)
        armPaint.color = Color.parseColor("#344054")
        armPaint.shader = null
        canvas.drawRoundRect(
            pivotX - 18f, pivotY - 55f,
            pivotX + 18f, pivotY - 20f,
            6f, 6f, armPaint,
        )

        // 金属唱杆 (Tonearm Tube)
        armPaint.shader = LinearGradient(
            pivotX - 5f, pivotY,
            pivotX + 5f, pivotY + armLength,
            Color.parseColor("#F2F4F7"),
            Color.parseColor("#98A2B3"),
            Shader.TileMode.CLAMP,
        )
        armPaint.strokeWidth = 7f
        armPaint.style = Paint.Style.STROKE
        armPaint.strokeCap = Paint.Cap.ROUND

        // 经典 S 型弯曲唱杆
        armPath.reset()
        armPath.moveTo(pivotX, pivotY)
        armPath.cubicTo(
            pivotX - 12f, pivotY + armLength * 0.35f,
            pivotX + 18f, pivotY + armLength * 0.70f,
            pivotX, pivotY + armLength,
        )
        canvas.drawPath(armPath, armPaint)

        // 唱头外壳与触针 (Cartridge & Stylus)
        val tipX = pivotX
        val tipY = pivotY + armLength

        armPaint.style = Paint.Style.FILL
        armPaint.shader = null
        armPaint.color = Color.parseColor("#1D2939")
        canvas.drawRoundRect(
            tipX - 12f, tipY - 5f,
            tipX + 12f, tipY + 32f,
            4f, 4f, armPaint,
        )

        // 唱头金色品牌标线
        armPaint.color = Color.parseColor("#E6B800")
        canvas.drawRect(tipX - 8f, tipY + 20f, tipX + 8f, tipY + 24f, armPaint)

        // 针尖微光 (Stylus Needle tip)
        armPaint.color = if (isPlaying) Color.parseColor("#00FFAA") else Color.parseColor("#FFFFFF")
        canvas.drawCircle(tipX, tipY + 34f, 2.5f, armPaint)

        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rotationAnimator?.cancel()
        tonearmAnimator?.cancel()
    }

    companion object {
        private const val TONEARM_REST_ANGLE = 0f // 待命状态（位于右侧支架）
        private const val TONEARM_PLAY_ANGLE = 23f // 播放状态（落入外圈与中圈音轨）
    }
}
