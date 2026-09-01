package com.example.readtrace.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * 封面色彩与极光氛围提取引擎 (Palette & Aurora Ambient Engine)
 * 采用轻量低内存缩放下采样 + 颜色直方图聚类，快速提取作品代表色，
 * 并生成专属的极光漫射背景。
 */
object PaletteExtractorHelper {

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    data class PaletteResult(
        val primaryColor: Int,
        val secondaryColor: Int,
        val backgroundColor: Int,
        val isDarkTheme: Boolean,
    )

    /**
     * 异步从图片文件路径提取调色板
     */
    fun extractFromPathAsync(
        imagePath: String?,
        onSuccess: (PaletteResult) -> Unit,
    ) {
        if (imagePath.isNullOrBlank()) {
            onSuccess(getDefaultFallbackPalette())
            return
        }

        executor.execute {
            val result = extractFromPath(imagePath)
            mainHandler.post { onSuccess(result) }
        }
    }

    /**
     * 同步提取（耗时一般 3~8ms）
     */
    fun extractFromPath(imagePath: String): PaletteResult {
        val file = File(imagePath)
        if (!file.exists() || !file.canRead()) {
            return getDefaultFallbackPalette()
        }

        return runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)

            // 下采样到 48x48 极小尺寸进行颜色提取，内存与耗时极低
            val targetSize = 48
            options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            val sampledBitmap = BitmapFactory.decodeFile(imagePath, options)
                ?: return@runCatching getDefaultFallbackPalette()

            val palette = extractFromBitmap(sampledBitmap)
            if (!sampledBitmap.isRecycled) {
                sampledBitmap.recycle()
            }
            palette
        }.getOrDefault(getDefaultFallbackPalette())
    }

    /**
     * 从采样 Bitmap 中计算主要色彩
     */
    fun extractFromBitmap(bitmap: Bitmap): PaletteResult {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var validCount = 0

        val colorBuckets = mutableMapOf<Int, Int>()

        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            // 过滤极度接近纯黑或纯白的高对比杂色
            val brightness = (0.299 * r + 0.587 * g + 0.114 * b)
            if (brightness < 20 || brightness > 240) continue

            // 色彩量化（取前 4 位高位）以合并相似颜色
            val quantized = Color.rgb(r and 0xF0, g and 0xF0, b and 0xF0)
            colorBuckets[quantized] = (colorBuckets[quantized] ?: 0) + 1

            totalR += r
            totalG += g
            totalB += b
            validCount++
        }

        if (validCount == 0 || colorBuckets.isEmpty()) {
            return getDefaultFallbackPalette()
        }

        // 挑选出现频次最高的两个主要色调
        val sortedColors = colorBuckets.toList().sortedByDescending { it.second }
        val primaryRaw = sortedColors.first().first
        val secondaryRaw = if (sortedColors.size > 1) sortedColors[1].first else sortedColors.first().first

        // 为深邃 UI 调优色彩（适度压暗并保留饱和度）
        val primary = tuneColorForAmbient(primaryRaw, targetAlpha = 0.55f)
        val secondary = tuneColorForAmbient(secondaryRaw, targetAlpha = 0.35f)
        val deepBackground = blendToDarkBase(primaryRaw)

        return PaletteResult(
            primaryColor = primary,
            secondaryColor = secondary,
            backgroundColor = deepBackground,
            isDarkTheme = true,
        )
    }

    /**
     * 构建双色极光漫射渐变 Drawable
     */
    fun createAuroraGradient(palette: PaletteResult): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                palette.primaryColor,
                palette.secondaryColor,
                palette.backgroundColor,
            ),
        ).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
    }

    private fun tuneColorForAmbient(color: Int, targetAlpha: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        // 提升饱和度，限制明度为 0.30~0.60，营造电影感漫射氛围
        hsv[1] = min(1.0f, hsv[1] * 1.25f)
        hsv[2] = hsv[2].coerceIn(0.30f, 0.60f)
        val adjusted = Color.HSVToColor(hsv)
        val alphaInt = (targetAlpha.coerceIn(0f, 1f) * 255).toInt()
        return Color.argb(alphaInt, Color.red(adjusted), Color.green(adjusted), Color.blue(adjusted))
    }

    private fun blendToDarkBase(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = 0.08f // 极暗底色 (8% 明度)
        return Color.HSVToColor(hsv)
    }

    private fun getDefaultFallbackPalette(): PaletteResult {
        return PaletteResult(
            primaryColor = Color.parseColor("#402B1B4D"),
            secondaryColor = Color.parseColor("#26192A4A"),
            backgroundColor = Color.parseColor("#0C0E14"),
            isDarkTheme = true,
        )
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }
}