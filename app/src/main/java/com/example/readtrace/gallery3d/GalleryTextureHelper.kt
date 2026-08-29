package com.example.readtrace.gallery3d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import java.io.File

object GalleryTextureHelper {

    /**
     * 为指定作品生成或加载 OpenGL 2D 纹理，返回 OpenGL textureId
     */
    fun loadOrCreateTexture(context: Context, book: Book): Int {
        val bitmap = loadCoverBitmap(context, book) ?: generateArtCoverBitmap(book)
        val textureId = createTextureFromBitmap(bitmap)
        bitmap.recycle()
        return textureId
    }

    private fun loadCoverBitmap(context: Context, book: Book): Bitmap? {
        val path = book.coverUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        // 内网封面键（covers/…）：GL 线程不做网络请求，仅使用已缓存到本机的封面文件，未缓存则回退艺术封面
        if (CoverImageHelper.isLanCoverKey(path)) {
            val cached = CoverImageHelper.peekCachedCoverFile(context, path) ?: return null
            return runCatching {
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(cached.absolutePath, boundsOptions)
                if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null
                BitmapFactory.decodeFile(cached.absolutePath, sampledDecodeOptions(boundsOptions))
            }.getOrNull()
        }

        val file = File(path)
        if (!file.exists()) return null

        return runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)
            BitmapFactory.decodeFile(path, sampledDecodeOptions(options))
        }.getOrNull()
    }

    /**
     * 依据边界信息采样至 512x768 左右，供 OpenGL 纹理使用
     */
    private fun sampledDecodeOptions(bounds: BitmapFactory.Options): BitmapFactory.Options {
        var inSampleSize = 1
        while (bounds.outWidth / (inSampleSize * 2) >= 512 && bounds.outHeight / (inSampleSize * 2) >= 768) {
            inSampleSize *= 2
        }
        return BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    }

    /**
     * 当无本地封面时，动态绘制高质感艺术封面
     */
    fun generateArtCoverBitmap(book: Book): Bitmap {
        val width = 512
        val height = 768
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. 根据媒介类型配置主色调渐变
        val (topColor, bottomColor) = when (book.mediaType) {
            MediaType.BOOK -> Pair(Color.parseColor("#1C2833"), Color.parseColor("#0E1626"))
            MediaType.ANIME -> Pair(Color.parseColor("#341B2D"), Color.parseColor("#150A13"))
            MediaType.MOVIE -> Pair(Color.parseColor("#2C1820"), Color.parseColor("#120A0E"))
            MediaType.GAME -> Pair(Color.parseColor("#17252A"), Color.parseColor("#0B1316"))
            MediaType.MUSIC -> Pair(Color.parseColor("#2A1E17"), Color.parseColor("#140E0A"))
        }

        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                topColor, bottomColor, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. 绘制内嵌金色细线边框
        val borderPaint = Paint().apply {
            color = Color.parseColor("#D4AF37")
            alpha = 90
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        val innerRect = RectF(24f, 24f, width - 24f, height - 24f)
        canvas.drawRoundRect(innerRect, 12f, 12f, borderPaint)

        // 3. 绘制顶部媒介 Emoji 与类型
        val emojiPaint = Paint().apply {
            textSize = 54f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(book.mediaType.emoji, width / 2f, 110f, emojiPaint)

        val mediaTypePaint = Paint().apply {
            color = Color.parseColor("#C8D6E5")
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.2f
            isAntiAlias = true
        }
        canvas.drawText("— ${book.mediaType.displayName} · READTRACE —", width / 2f, 150f, mediaTypePaint)

        // 4. 绘制书名标题（居中自适应折行）
        val titleTextPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val textWidth = width - 80
        val staticLayout = StaticLayout.Builder.obtain(book.title, 0, book.title.length, titleTextPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .build()

        canvas.save()
        canvas.translate(40f, 260f)
        staticLayout.draw(canvas)
        canvas.restore()

        // 5. 绘制作者/创作者
        val authorText = book.author?.takeIf { it.isNotBlank() } ?: "佚名"
        val authorPaint = Paint().apply {
            color = Color.parseColor("#A0B2C6")
            textSize = 24f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("著 / $authorText", width / 2f, 480f, authorPaint)

        // 6. 绘制评分与状态底栏
        if (book.rating != null) {
            val ratingBadgePaint = Paint().apply {
                color = Color.parseColor("#D4AF37")
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val ratingRect = RectF(width / 2f - 90f, 540f, width / 2f + 90f, 595f)
            canvas.drawRoundRect(ratingRect, 28f, 28f, ratingBadgePaint)

            val ratingTextPaint = Paint().apply {
                color = Color.parseColor("#1A1A1A")
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("★ ${String.format("%.1f", book.rating / 2.0)}", width / 2f, 578f, ratingTextPaint)
        }

        // 底部状态胶囊
        val statusPaint = Paint().apply {
            color = Color.parseColor("#576574")
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("[ ${book.status.getDisplayName(book.mediaType)} ]", width / 2f, 680f, statusPaint)

        return bitmap
    }

    private fun createTextureFromBitmap(bitmap: Bitmap): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val textureId = textures[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        return textureId
    }

    fun deleteTexture(textureId: Int) {
        if (textureId > 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
    }
}
