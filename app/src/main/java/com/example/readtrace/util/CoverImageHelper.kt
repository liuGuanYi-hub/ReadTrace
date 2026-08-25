package com.example.readtrace.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.ImageView
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
    private const val TARGET_MAX_WIDTH = 720
    private const val TARGET_MAX_HEIGHT = 1080
    private const val THUMB_WIDTH = 300
    private const val THUMB_HEIGHT = 450
    private const val JPEG_QUALITY = 85

    // 分配应用最大可用内存的 1/8 作为 Bitmap LRU 缓存
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceIn(8 * 1024, 32 * 1024)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val imageExecutor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 将用户选择的图片裁剪为 2:3 比例并保存到 App 私有存储目录
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
            targetHeight = height
            targetWidth = (height * ratioW) / ratioH
        } else {
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
     * 安全解码本地文件为下采样缩略图，杜绝 OOM
     */
    fun decodeSampledBitmapFromFile(path: String, reqWidth: Int = THUMB_WIDTH, reqHeight: Int = THUMB_HEIGHT): Bitmap? {
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565 // 节省 50% 内存

            BitmapFactory.decodeFile(path, options)
        }.getOrElse {
            memoryCache.evictAll()
            null
        }
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
     * 加载封面到 ImageView，支持内存 LRU 缓存、下采样防 OOM、异步下载与自动磁盘缓存
     */
    fun loadCover(imageView: ImageView, path: String?, placeholderView: View? = null) {
        if (path.isNullOrBlank()) {
            imageView.visibility = View.GONE
            placeholderView?.visibility = View.VISIBLE
            return
        }

        val trimmed = path.trim()

        // 1. 优先从内存 LRU 缓存命中
        val cached = memoryCache.get(trimmed)
        if (cached != null && !cached.isRecycled) {
            imageView.setImageBitmap(cached)
            imageView.visibility = View.VISIBLE
            placeholderView?.visibility = View.GONE
            return
        }

        imageView.tag = trimmed

        // 2. 如果是网络 URL (http/https)
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val context = imageView.context.applicationContext
            val cacheKey = md5(trimmed)
            val cacheDir = File(context.filesDir, COVERS_DIR).apply { if (!exists()) mkdirs() }
            val cacheFile = File(cacheDir, "net_$cacheKey.jpg")

            if (cacheFile.exists() && cacheFile.length() > 0) {
                imageExecutor.execute {
                    val bitmap = decodeSampledBitmapFromFile(cacheFile.absolutePath)
                    if (bitmap != null) {
                        memoryCache.put(trimmed, bitmap)
                        mainHandler.post {
                            if (imageView.tag == trimmed) {
                                imageView.setImageBitmap(bitmap)
                                imageView.visibility = View.VISIBLE
                                placeholderView?.visibility = View.GONE
                            }
                        }
                    }
                }
                return
            }

            // 无磁盘缓存，异步下载
            imageView.visibility = View.GONE
            placeholderView?.visibility = View.VISIBLE

            imageExecutor.execute {
                runCatching {
                    val url = URL(trimmed)
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 6000
                        readTimeout = 6000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "ReadTrace/5.3 (Android)")
                    }
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val bytes = connection.inputStream.use { it.readBytes() }
                        val savedPath = cropAndSaveCoverFromBytes(context, bytes, "net_$cacheKey.jpg")
                        if (savedPath != null) {
                            val downloadedBitmap = decodeSampledBitmapFromFile(savedPath)
                            if (downloadedBitmap != null) {
                                memoryCache.put(trimmed, downloadedBitmap)
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
                }.onFailure {
                    // 网络失败静默忽略
                }
            }
            return
        }

        // 3. 本地文件路径
        val file = File(trimmed)
        if (!file.exists() || !file.isFile) {
            imageView.visibility = View.GONE
            placeholderView?.visibility = View.VISIBLE
            return
        }

        imageExecutor.execute {
            val bitmap = decodeSampledBitmapFromFile(file.absolutePath)
            if (bitmap != null) {
                memoryCache.put(trimmed, bitmap)
                mainHandler.post {
                    if (imageView.tag == trimmed) {
                        imageView.setImageBitmap(bitmap)
                        imageView.visibility = View.VISIBLE
                        placeholderView?.visibility = View.GONE
                    }
                }
            } else {
                mainHandler.post {
                    if (imageView.tag == trimmed) {
                        imageView.visibility = View.GONE
                        placeholderView?.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    /**
     * 异步加载封面 Bitmap（优先读内存缓存，其次读文件与网络）
     */
    fun loadCoverBitmap(path: String?, onLoaded: (Bitmap?) -> Unit) {
        val trimmed = path?.trim()
        if (trimmed.isNullOrEmpty()) {
            onLoaded(null)
            return
        }

        val cached = memoryCache.get(trimmed)
        if (cached != null) {
            onLoaded(cached)
            return
        }

        imageExecutor.execute {
            val bitmap = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                runCatching {
                    val url = URL(trimmed)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                        instanceFollowRedirects = true
                    }
                    val bytes = conn.inputStream.use { it.readBytes() }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            } else {
                val file = File(trimmed)
                if (file.exists() && file.isFile) {
                    decodeSampledBitmapFromFile(file.absolutePath, 300, 300)
                } else null
            }

            if (bitmap != null) {
                memoryCache.put(trimmed, bitmap)
            }
            mainHandler.post {
                onLoaded(bitmap)
            }
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
