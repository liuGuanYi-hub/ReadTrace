package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.example.readtrace.util.HapticFeedbackEngine
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 🌟 鸿蒙流光寻迹与刷动磁吸底部导航栏 (Fluid Spotlight Brush & Magnetic Drag Dock)
 *
 * 核心特性：
 * 1. 【指尖流光探针】：按下/滑动时在指尖生成动态水银翡翠径向渐变流光（Radial Spotlight）；
 * 2. 【画刷式轨迹跟踪】：手指在各 Tab 间往复刷动，光晕 100% 实时紧随手指移动；
 * 3. 【临近磁吸形变】：动态计算与各 Tab 的几何距离，驱动临近 Tab 图标平滑放大与浮动；
 * 4. 【棘轮微震矩阵】：跨越 Tab 感应边界时触发清脆细腻的线性马达棘轮微震；
 * 5. 【微光粒子溢散】：快速刷动时向四周散射微型闪烁光子；
 * 6. 【松手智能吸附】：松手后光斑弹性吸附至最近 Tab，自动完成页面平滑切换。
 */
class FluidSpotlightNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val cornerRadius = dpToPx(32f)

    // 探针流光坐标与透明度
    private var spotlightX = 0f
    private var spotlightY = 0f
    private var spotlightRadius = dpToPx(68f)
    private var glowAlpha = 0f // 0f ~ 1f

    private var downX = 0f
    private var downY = 0f
    private var isDragging = false

    private var currentHoverIndex = -1
    private var lastHoverIndex = -1

    private val clipPath = Path()
    private val clipRect = RectF()

    private var cachedSpotlightGradient: RadialGradient? = null
    private var cachedSpotlightRadius = -1f
    private val spotlightMatrix = android.graphics.Matrix()
    private val spotlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particles = mutableListOf<SparkleParticle>()

    private var glowFadeAnimator: ValueAnimator? = null
    private var snapAnimator: ValueAnimator? = null

    var onTabSelectedListener: ((Int) -> Unit)? = null

    data class SparkleParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var alpha: Float,
        var size: Float,
        var life: Float,
        var maxLife: Float
    )

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipRect.set(0f, 0f, w.toFloat(), h.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(clipRect, cornerRadius, cornerRadius, Path.Direction.CW)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                isDragging = false
                spotlightX = ev.x
                spotlightY = ev.y
                animateGlowAlpha(1f, 150L)
                updateHoverState(ev.x)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (dx > touchSlop && dx > dy) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    animateGlowAlpha(0f, 250L)
                }
            }
        }
        return isDragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                spotlightX = event.x
                spotlightY = event.y
                isDragging = false
                animateGlowAlpha(1f, 150L)
                updateHoverState(event.x)
                spawnParticles(event.x, event.y, 4)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                if (dx > touchSlop) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                spotlightX = event.x
                spotlightY = event.y
                updateHoverState(event.x)
                applyProximityDistortion(event.x)

                // 刷动时产生流光微粒子
                if (Random.nextFloat() < 0.45f) {
                    spawnParticles(event.x, event.y, 1)
                }

                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val targetIndex = getTabIndexAt(event.x)
                if (targetIndex != -1) {
                    snapToTab(targetIndex)
                    onTabSelectedListener?.invoke(targetIndex)
                } else {
                    animateGlowAlpha(0f, 250L)
                    resetTabDistortion()
                }
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                animateGlowAlpha(0f, 250L)
                resetTabDistortion()
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** 检查当前光斑正悬停在哪一个 Tab 上，并触发清脆棘轮微震 */
    private fun updateHoverState(x: Float) {
        val index = getTabIndexAt(x)
        if (index != -1 && index != currentHoverIndex) {
            currentHoverIndex = index
            if (lastHoverIndex != -1) {
                HapticFeedbackEngine.dockBrushRatchetTick(context)
            }
            lastHoverIndex = currentHoverIndex
        }
    }

    /** 临近磁吸与弹性形变算法 */
    private fun applyProximityDistortion(x: Float) {
        val innerBar = getInnerNavBar() ?: return
        val count = innerBar.childCount
        if (count == 0) return

        val maxInfluenceDistance = (width.toFloat() / count) * 1.1f

        for (i in 0 until count) {
            val child = innerBar.getChildAt(i)
            val childCenterX = child.left + child.width / 2f
            val dist = abs(x - childCenterX)

            if (dist < maxInfluenceDistance) {
                val factor = (1f - (dist / maxInfluenceDistance)).coerceIn(0f, 1f)
                // 高斯加权平滑过渡 (0f ~ 1f)
                val smoothFactor = (sin((factor - 0.5) * Math.PI) * 0.5 + 0.5).toFloat()
                child.scaleX = 1f + 0.16f * smoothFactor
                child.scaleY = 1f + 0.16f * smoothFactor
                child.translationY = -dpToPx(4.5f) * smoothFactor
            } else {
                child.scaleX = 1f
                child.scaleY = 1f
                child.translationY = 0f
            }
        }
    }

    /** 弹性复原所有 Tab 的缩放与位移 */
    private fun resetTabDistortion() {
        val innerBar = getInnerNavBar() ?: return
        for (i in 0 until innerBar.childCount) {
            val child = innerBar.getChildAt(i)
            child.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(240L)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }
    }

    /** 松手时光斑平滑磁吸至目标 Tab 并优雅淡出 */
    private fun snapToTab(tabIndex: Int) {
        val innerBar = getInnerNavBar() ?: return
        if (tabIndex !in 0 until innerBar.childCount) return

        val targetChild = innerBar.getChildAt(tabIndex)
        val targetCenterX = targetChild.left + targetChild.width / 2f
        val startX = spotlightX

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(startX, targetCenterX).apply {
            duration = 260L
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener { anim ->
                spotlightX = anim.animatedValue as Float
                applyProximityDistortion(spotlightX)
                invalidate()
            }
            start()
        }

        resetTabDistortion()
        animateGlowAlpha(0f, 320L, delay = 120L)
    }

    private fun getTabIndexAt(x: Float): Int {
        val innerBar = getInnerNavBar() ?: return -1
        val count = innerBar.childCount
        if (count == 0) return -1

        for (i in 0 until count) {
            val child = innerBar.getChildAt(i)
            if (x >= child.left && x <= child.right) {
                return i
            }
        }
        // 边界保护：若滑出微量边界，吸附到首尾 Tab
        return if (x < (innerBar.getChildAt(0)?.left ?: 0)) 0 else count - 1
    }

    private fun getInnerNavBar(): LinearLayout? {
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v is LinearLayout) return v
        }
        return null
    }

    private fun animateGlowAlpha(target: Float, durationMs: Long, delay: Long = 0L) {
        glowFadeAnimator?.cancel()
        glowFadeAnimator = ValueAnimator.ofFloat(glowAlpha, target).apply {
            startDelay = delay
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                glowAlpha = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun spawnParticles(cx: Float, cy: Float, count: Int) {
        for (i in 0 until count) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val speed = Random.nextFloat() * dpToPx(1.5f) + dpToPx(0.5f)
            val life = Random.nextFloat() * 18f + 14f
            particles.add(
                SparkleParticle(
                    x = cx + (Random.nextFloat() - 0.5f) * dpToPx(16f),
                    y = cy + (Random.nextFloat() - 0.5f) * dpToPx(16f),
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat(),
                    alpha = 1f,
                    size = dpToPx(Random.nextFloat() * 2.2f + 1.2f),
                    life = life,
                    maxLife = life
                )
            )
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // 先绘制底部毛玻璃、指示胶囊及子 Tab
        super.dispatchDraw(canvas)

        // 在最上层叠加绘制流体水银极光流光
        if (glowAlpha > 0.01f) {
            canvas.save()
            canvas.clipPath(clipPath)

            // 1. 动态径向高光探针：渐变按半径缓存 + 局部矩阵平移 + paint.alpha 调制强度，每帧零分配（P38-P3）
            if (spotlightRadius != cachedSpotlightRadius) {
                cachedSpotlightRadius = spotlightRadius
                cachedSpotlightGradient = RadialGradient(
                    0f, 0f, spotlightRadius,
                    intArrayOf(
                        Color.argb(110, 255, 255, 255),
                        Color.argb(65, 90, 168, 118),
                        Color.argb(0, 58, 99, 72)
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            spotlightMatrix.setTranslate(spotlightX - spotlightRadius, spotlightY - spotlightRadius)
            cachedSpotlightGradient?.setLocalMatrix(spotlightMatrix)
            spotlightPaint.shader = cachedSpotlightGradient
            spotlightPaint.alpha = (255 * glowAlpha).toInt().coerceIn(0, 255)
            canvas.drawCircle(spotlightX, spotlightY, spotlightRadius, spotlightPaint)

            // 2. 绘制流动微光粒子
            if (particles.isNotEmpty()) {
                val iterator = particles.iterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    p.x += p.vx
                    p.y += p.vy
                    p.life -= 1f
                    p.alpha = (p.life / p.maxLife).coerceIn(0f, 1f) * glowAlpha

                    if (p.life <= 0f) {
                        iterator.remove()
                    } else {
                        particlePaint.color = Color.argb((p.alpha * 220).toInt(), 230, 255, 240)
                        canvas.drawCircle(p.x, p.y, p.size, particlePaint)
                    }
                }
            }

            canvas.restore()
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
