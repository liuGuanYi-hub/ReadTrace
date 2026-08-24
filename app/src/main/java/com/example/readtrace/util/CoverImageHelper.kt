package com.example.readtrace.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

object CoverImageHelper {

    private const val COVERS_DIR = "covers"
    private const val TARGET_MAX_WIDTH = 1080
    private const val TARGET_MAX_HEIGHT = 1620
    private const val JPEG_QUALITY = 90

    private val imageExecutor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 将用户选择的图片裁剪为 2:3 比例并保存到 App 私有存储目录
     * @return 保存后的本地文件绝对路径，失败返回 null
     */
    fun cropAndSaveCover(context: Context, sourceUri: Uri): String? {
        return runCatching {
            val resolver = context.contentResolver
            val bytes = resolver.openInputStream(sourceUri)?.use { it.readBytes() } ?: return null
            cropAndSaveCoverFromBytes(context, bytes)
        }.getOrNull()
    }

    /**
     * 从输入流中读取图片并裁剪为 2:3 保存到私有目录
     */
    fun cropAndSaveCoverFromStream(context: Context, inputStream: InputStream, customFileName: String? = null): String? {
        return runCatching {
            val bytes = inputStream.use { it.readBytes() }
            cropAndSaveCoverFromBytes(context, bytes, customFileName)
        }.getOrNull()
    }

    private fun cropAndSaveCoverFromBytes(context: Context, bytes: ByteArray, customFileName: String? = null): String? {
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            val originWidth = options.outWidth
            val originHeight = options.outHeight
            if (originWidth <= 0 || originHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(originWidth, originHeight, TARGET_MAX_WIDTH, TARGET_MAX_HEIGHT)
            options.inJustDecodeBounds = false

            val decodedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            val croppedBitmap = centerCropToRatio(decodedBitmap, 2, 3)

            val dir = File(context.filesDir, COVERS_DIR).apply { if (!exists()) mkdirs() }
            val fileName = customFileName ?: "cover_${System.currentTimeMillis()}.jpg"
            val coverFile = File(dir, fileName)

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
     * 加载封面到 ImageView，支持本地文件路径与 http/https 网络图片，具备自动异步下载与磁盘缓存
     */
    fun loadCover(imageView: ImageView, path: String?, placeholderView: View? = null) {
        if (path.isNullOrBlank()) {
            imageView.visibility = View.GONE
            placeholderView?.visibility = View.VISIBLE
            return
        }

        val trimmed = path.trim()

        // 1. 如果是网络 URL (http/https)
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val context = imageView.context
            val cacheKey = md5(trimmed)
            val cacheDir = File(context.filesDir, COVERS_DIR).apply { if (!exists()) mkdirs() }
            val cacheFile = File(cacheDir, "net_$cacheKey.jpg")

            if (cacheFile.exists() && cacheFile.length() > 0) {
                // 已有缓存
                val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = View.VISIBLE
                    placeholderView?.visibility = View.GONE
                    return
                }
            }

            // 无缓存，先显示占位图，异步下载
            imageView.visibility = View.GONE
            placeholderView?.visibility = View.VISIBLE
            imageView.tag = trimmed

            imageExecutor.execute {
                runCatching {
                    val url = URL(trimmed)
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "ReadTrace/4.4 (Android; AnimePosters)")
                    }
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val bytes = connection.inputStream.use { it.readBytes() }
                        val savedPath = cropAndSaveCoverFromBytes(context, bytes, "net_$cacheKey.jpg")
                        if (savedPath != null) {
                            val downloadedBitmap = BitmapFactory.decodeFile(savedPath)
                            if (downloadedBitmap != null) {
                                mainHandler.post {
                                    if (imageView.tag == trimmed) {
                                        imageView.setImageBitmap(downloadedBitmap)
                                        imageView.visibility = View.VISIBLE
                                        placeholderView?.visibility = View.GONE
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return
        }

        // 2. 本地文件路径
        val file = File(trimmed)
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

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
