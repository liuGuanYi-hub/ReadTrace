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

    /** 预置封面在内网服务器上的相对键前缀（covers/xxx.jpg），由局域网 HTTP 服务提供，首次加载后缓存本机 */
    const val LAN_COVER_KEY_PREFIX = "covers/"
    private const val LAN_PREFS = "readtrace_lan_prefs"
    private const val KEY_LAN_COVER_BASE = "lan_cover_base_url"

    /** 判断封面路径是否为内网封面键（covers/xxx.jpg） */
    fun isLanCoverKey(path: String?): Boolean {
        return path != null && path.trim().startsWith(LAN_COVER_KEY_PREFIX, ignoreCase = true)
    }

    /** 读取内网封面服务地址（如 http://192.168.1.100:8000），未配置返回空串 */
    fun getLanCoverBaseUrl(context: Context): String {
        return context.applicationContext.getSharedPreferences(LAN_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAN_COVER_BASE, "")?.trim().orEmpty()
    }

    /** 保存内网封面服务地址：自动补 http:// 前缀、去尾部斜杠 */
    fun setLanCoverBaseUrl(context: Context, baseUrl: String) {
        var normalized = baseUrl.trim()
        if (normalized.isNotEmpty() && !normalized.startsWith("http://", ignoreCase = true) &&
            !normalized.startsWith("https://", ignoreCase = true)
        ) {
            normalized = "http://$normalized"
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }
        context.applicationContext.getSharedPreferences(LAN_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAN_COVER_BASE, normalized)
            .apply()
    }

    /** 内网封面键 → 完整下载 URL；服务地址未配置时返回 null */
    fun resolveLanCoverUrl(context: Context, key: String): String? {
        val base = getLanCoverBaseUrl(context)
        if (base.isEmpty()) return null
        return base + "/" + key.trim().trimStart('/')
    }

    /**
     * 查询封面在本机磁盘缓存中的文件（内网键或网络链接，加载过一次即缓存）。
     * 供 GL 线程、桌面小组件等不能做网络请求的同步场景使用；未缓存返回 null。
     */
    fun peekCachedCoverFile(context: Context, path: String?): File? {
        val trimmed = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.startsWith("http", ignoreCase = true) || isLanCoverKey(trimmed)) {
            val cached = File(context.applicationContext.filesDir, "$COVERS_DIR/net_${md5(trimmed)}.jpg")
            if (cached.exists() && cached.length() > 0) return cached
        }
        return null
    }

    /** 与磁盘缓存文件名一致的 MD5 摘要（供数据库迁移对齐旧缓存文件） */
    fun md5KeyOf(input: String): String = md5(input)

    // 分配应用最大可用内存的 1/8 作为 Bitmap LRU 缓存
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceIn(8 * 1024, 32 * 1024)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val threadCount = max(4, Runtime.getRuntime().availableProcessors())
    private val imageExecutor = Executors.newFixedThreadPool(threadCount)
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
     * 主线程零磁盘 I/O，全异步解码与双向 Tag 复用防串图
     */
    fun loadCover(imageView: ImageView, path: String?, placeholderView: View? = null) {
        if (path.isNullOrBlank()) {
            if (placeholderView != null) {
                imageView.visibility = View.GONE
                placeholderView.visibility = View.VISIBLE
            } else {
                imageView.setImageResource(com.example.readtrace.R.drawable.bg_book_placeholder)
                imageView.visibility = View.VISIBLE
            }
            return
        }

        val trimmed = path.trim()

        // 1. 优先从内存 LRU 缓存命中（主线程 0ms 纯内存返回）
        val cached = memoryCache.get(trimmed)
        if (cached != null && !cached.isRecycled) {
            imageView.tag = trimmed
            imageView.setImageBitmap(cached)
            imageView.visibility = View.VISIBLE
            placeholderView?.visibility = View.GONE
            return
        }

        // 2. 标记当前 View 期待加载的图片路径，用于防异步串图
        imageView.tag = trimmed

        // 3. 设置默认占位态（不阻塞主线程）
        if (placeholderView != null) {
            imageView.visibility = View.GONE
            placeholderView.visibility = View.VISIBLE
        } else {
            imageView.setImageResource(com.example.readtrace.R.drawable.bg_book_placeholder)
            imageView.visibility = View.VISIBLE
        }

        val appContext = imageView.context.applicationContext
        // LAN 键在此解析为完整下载 URL；磁盘缓存键始终取存储键，服务地址更换后缓存依然命中
        val networkUrl = when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            isLanCoverKey(trimmed) -> resolveLanCoverUrl(appContext, trimmed)
            else -> null
        }

        // 4. 将所有磁盘检查、哈希计算与解码操作完全推入后台线程池
        imageExecutor.execute {
            // View 如果已经被回收复用，直接提前终止
            if (imageView.tag != trimmed) return@execute

            var bitmap: Bitmap? = null

            if (networkUrl != null) {
                val cacheKey = md5(trimmed)
                val cacheDir = File(appContext.filesDir, COVERS_DIR).apply { if (!exists()) mkdirs() }
                val cacheFile = File(cacheDir, "net_$cacheKey.jpg")

                if (cacheFile.exists() && cacheFile.length() > 0) {
                    bitmap = decodeSampledBitmapFromFile(cacheFile.absolutePath)
                } else {
                    runCatching {
                        val url = URL(networkUrl)
                        val connection = (url.openConnection() as HttpURLConnection).apply {
                            connectTimeout = 8000
                            readTimeout = 8000
                            instanceFollowRedirects = true
                            setRequestProperty("User-Agent", "Mozilla/5.0 (Android; ReadTrace/5.3)")
                            // 豆瓣图床有防盗链，请求需携带 Referer，否则返回 418
                            if (url.host.endsWith("doubanio.com") || url.host.endsWith("douban.com")) {
                                setRequestProperty("Referer", "https://book.douban.com/")
                            }
                        }
                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                            val bytes = connection.inputStream.use { it.readBytes() }
                            val savedPath = cropAndSaveCoverFromBytes(appContext, bytes, "net_$cacheKey.jpg")
                            if (savedPath != null) {
                                bitmap = decodeSampledBitmapFromFile(savedPath)
                            }
                        }
                    }
                }
            } else {
                val file = File(trimmed)
                if (file.exists() && file.isFile) {
                    bitmap = decodeSampledBitmapFromFile(file.absolutePath)
                }
            }

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
                        if (placeholderView != null) {
                            imageView.visibility = View.GONE
                            placeholderView.visibility = View.VISIBLE
                        } else {
                            imageView.setImageResource(com.example.readtrace.R.drawable.bg_book_placeholder)
                            imageView.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    /**
     * 异步加载封面 Bitmap（简化重载）
     */
    fun loadCoverBitmap(path: String?, onLoaded: (Bitmap?) -> Unit) {
        loadCoverBitmap(context = null, path = path, reqWidth = 720, reqHeight = 1080, onLoaded = onLoaded)
    }

    /**
     * 异步加载封面 Bitmap（支持本地绝对路径、私有目录相对路径、content://、asset、网络 URL 与 LRU 内存/磁盘双重缓存）
     */
    fun loadCoverBitmap(context: Context? = null, path: String?, reqWidth: Int = 720, reqHeight: Int = 1080, onLoaded: (Bitmap?) -> Unit) {
        val trimmed = path?.trim()
        if (trimmed.isNullOrEmpty()) {
            mainHandler.post { onLoaded(null) }
            return
        }

        val cached = memoryCache.get(trimmed)
        if (cached != null && !cached.isRecycled) {
            mainHandler.post { onLoaded(cached) }
            return
        }

        imageExecutor.execute {
            var bitmap: Bitmap? = null
            val ctx = context?.applicationContext

            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                bitmap = downloadRemoteCover(ctx, trimmed, reqWidth, reqHeight)
            } else if (isLanCoverKey(trimmed) && ctx != null) {
                val resolved = resolveLanCoverUrl(ctx, trimmed)
                if (resolved != null) {
                    bitmap = downloadRemoteCover(ctx, resolved, reqWidth, reqHeight, cacheKeyFor = trimmed)
                }
            } else if (trimmed.startsWith("content://", ignoreCase = true) && ctx != null) {
                runCatching {
                    ctx.contentResolver.openInputStream(Uri.parse(trimmed))?.use { stream ->
                        val bytes = stream.readBytes()
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight)
                        options.inJustDecodeBounds = false
                        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    }
                }
            } else {
                val directFile = File(trimmed)
                if (directFile.exists() && directFile.isFile) {
                    bitmap = decodeSampledBitmapFromFile(directFile.absolutePath, reqWidth, reqHeight)
                } else if (ctx != null) {
                    val internalFile = File(ctx.filesDir, "$COVERS_DIR/$trimmed")
                    if (internalFile.exists() && internalFile.isFile) {
                        bitmap = decodeSampledBitmapFromFile(internalFile.absolutePath, reqWidth, reqHeight)
                    }
                }
            }

            if (bitmap != null) {
                memoryCache.put(trimmed, bitmap)
            }
            mainHandler.post {
                onLoaded(bitmap)
            }
        }
    }

    /**
     * 从内网/外网地址下载封面并按存储键落盘缓存（cacheKeyFor 未传时以 URL 为键）。
     * 命中磁盘缓存直接解码返回；下载失败静默返回 null，由上层回退占位图。
     */
    private fun downloadRemoteCover(context: Context?, url: String, reqWidth: Int, reqHeight: Int, cacheKeyFor: String = url): Bitmap? {
        if (context == null) return null
        val cacheDir = File(context.filesDir, COVERS_DIR).apply { if (!exists()) mkdirs() }
        val cacheFile = File(cacheDir, "net_${md5(cacheKeyFor)}.jpg")

        if (cacheFile.exists() && cacheFile.length() > 0) {
            return decodeSampledBitmapFromFile(cacheFile.absolutePath, reqWidth, reqHeight)
        }

        return runCatching {
            val host = runCatching { URL(url).host }.getOrDefault("")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; ReadTrace/5.3)")
                // 豆瓣图床有防盗链，请求需携带 Referer，否则返回 418
                if (host.endsWith("doubanio.com") || host.endsWith("douban.com")) {
                    setRequestProperty("Referer", "https://book.douban.com/")
                }
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            runCatching {
                FileOutputStream(cacheFile).use { out -> out.write(bytes) }
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
