package com.example.readtrace.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.util.Random

/**
 * 🎞️ 胶片微颗粒与纸张物理噪点覆盖层 (FilmGrainOverlayView)
 *
 * P6 阶段三核心组件：
 * 1. 模拟胶片感光颗粒（Analog Film Grain）：通过程序化生成 64x64 高频微噪点瓦片，并以 BitmapShader 平铺覆盖；
 * 2. 消除数字渐变断层（Anti-Banding）：在深色背景与流体极光之上叠加 2.5%~4% 的极细物理颗粒，消除色带阶断；
 * 3. 极致性能：纯硬件加速平铺着色，0 持续 CPU 运算，0 垃圾回收抖动；
 * 4. 穿透点击（Click-Through）：不拦截任何手势事件。
 */
class FilmGrainOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var grainIntensity: Float = 0.035f // 3.5% 微噪点
        set(value) {
            field = value.coerceIn(0.01f, 0.15f)
            paint.alpha = (field * 255).toInt()
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
        isFilterBitmap = true
    }

    init {
        isClickable = false
        isFocusable = false
        paint.alpha = (grainIntensity * 255).toInt()
        generateNoiseTile()
    }

    private fun generateNoiseTile() {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val random = Random(42) // 固定种子保证颗粒均匀美观

        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            val lum = random.nextInt(256)
            // 单色噪点与微弱冷暖偏置
            pixels[i] = Color.argb(lum, 230, 235, 245)
        }

        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        paint.shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
