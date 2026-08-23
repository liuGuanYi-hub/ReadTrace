package com.example.readtrace.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object CoverImageHelper {

    private const val COVERS_DIR = "covers"
    private const val TARGET_MAX_WIDTH = 1080
    private const val TARGET_MAX_HEIGHT = 1620
    private const val JPEG_QUALITY = 90

    /**
     * 将用户选择的图片裁剪为 2:3 比例并保存到 App 私有存储目录
     * @return 保存后的本地文件绝对路径，失败返回 null
     */
    fun cropAndSaveCover(context: Context, sourceUri: Uri): String? {
        return runCatching {
            val resolver = context.contentResolver

            // 1. 获取图片尺寸
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val originWidth = options.outWidth
            val originHeight = options.outHeight
            if (originWidth <= 0 || originHeight <= 0) return null

            // 2. 计算采样率，避免 OOM
            options.inSampleSize = calculateInSampleSize(originWidth, originHeight, TARGET_MAX_WIDTH, TARGET_MAX_HEIGHT)
            options.inJustDecodeBounds = false

            // 3. 解码 Bitmap
            val decodedBitmap = resolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            // 4. 2:3 比例中心裁剪
            val croppedBitmap = centerCropToRatio(decodedBitmap, 2, 3)

            // 5. 保存到内部存储
            val dir = File(context.filesDir, COVERS_DIR).apply { if (!exists()) mkdirs() }
            val coverFile = File(dir, "cover_${System.currentTimeMillis()}.jpg")

            FileOutputStream(coverFile).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }

            if (croppedBitmap != decodedBitmap) {
                croppedBitmap.recycle()
            }
            decodedBitmap.recycle()

            coverFile.absolutePath
        }.getOrNull()
    }

    /**
     * 2:3 比例中心裁剪
     */
    private fun centerCropToRatio(source: Bitmap, ratioW: Int, ratioH: Int): Bitmap {
        val width = source.width
        val height = source.height

        val targetWidth: Int
        val targetHeight: Int

        if (width * ratioH > height * ratioW) {
            // 原图比 2:3 更宽，以高度为准裁剪宽度
            targetHeight = height
            targetWidth = (height * ratioW) / ratioH
        } else {
            // 原图比 2:3 更高，以宽度为准裁剪高度
            targetWidth = width
            targetHeight = (width * ratioH) / ratioW
        }

        val startX = max(0, (width - targetWidth) / 2)
        val startY = max(0, (height - targetHeight) / 2)

        return Bitmap.createBitmap(source, startX, startY, min(targetWidth, width), min(targetHeight, height))
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * 安全删除旧封面文件
     */
    fun deleteCoverFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    /**
     * 加载封面到 ImageView，如果图片不存在则展示占位图
     */
    fun loadCover(imageView: ImageView, path: String?, placeholderView: View? = null) {
        if (path.isNullOrBlank()) {
            imageView.visibility = View.GONE
            placeholderView?.visibility = View.VISIBLE
            return
        }

        val file = File(path)
        if (!file.exists() || !file.isFile) {
            imageView.visibility = View.GONE
            placeholderView?.visibility = View.VISIBLE
            return
        }

        runCatching {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                imageView.visibility = View.VISIBLE
                placeholderView?.visibility = View.GONE
            } else {
                imageView.visibility = View.GONE
                placeholderView?.visibility = View.VISIBLE
            }
        }.onFailure {
            imageView.visibility = View.GONE
            placeholderView?.visibility = View.VISIBLE
        }
    }
}
