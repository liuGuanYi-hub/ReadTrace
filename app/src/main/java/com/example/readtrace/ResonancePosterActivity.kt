package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.ResonancePosterView
import com.example.readtrace.util.FloatingBack
import java.io.File
import java.io.FileOutputStream

class ResonancePosterActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var resonancePosterView: ResonancePosterView

    private var bookAId: Long = -1L
    private var bookBId: Long = -1L
    private var similarity: Int = 94
    private var resonanceTrait: String = "存在主义思辨 · 终极孤独"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContentView(R.layout.activity_resonance_poster)

        // 系统栏避让统一交给根布局按 WindowInsets 处理，顶栏只保留对称的视觉留白
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.resonancePosterRoot)) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper.getInstance(this)

        bookAId = intent.getLongExtra(EXTRA_BOOK_A_ID, -1L)
        bookBId = intent.getLongExtra(EXTRA_BOOK_B_ID, -1L)
        similarity = intent.getIntExtra(EXTRA_SIMILARITY, 94)
        resonanceTrait = intent.getStringExtra(EXTRA_RESONANCE_TRAIT) ?: "存在主义思辨 · 终极孤独"

        initViews()
        loadData()
        // 已移除陀螺仪 3D 视差：倾斜手机时票券会被平移/旋转，顶部露出深色底，被误认为空白（导出图不受影响）
    }

    private fun initViews() {
        resonancePosterView = findViewById(R.id.resonancePosterView)
        resonancePosterView.setOnClickListener {
            com.example.readtrace.util.HapticFeedbackEngine.celestialResonancePulse(this)
            com.example.readtrace.util.SpatialAudioEngine.playCelestialTone()
        }

        FloatingBack.install(this)

        val btnSelectWorks = findViewById<TextView>(R.id.btnSelectWorks)
        btnSelectWorks?.setOnClickListener { showSelectWorkADialog() }
        ViewAnimationHelper.attachSpringTouch(btnSelectWorks)

        val btnShareTop = findViewById<TextView>(R.id.btnPosterShareTop)
        btnShareTop.setOnClickListener { exportAndSharePoster() }
        ViewAnimationHelper.attachSpringTouch(btnShareTop)

        val btnSaveAlbum = findViewById<TextView>(R.id.btnSaveResonanceAlbum)
        btnSaveAlbum?.setOnClickListener { savePosterToGallery() }
        ViewAnimationHelper.attachSpringTouch(btnSaveAlbum)

        val btnShareImage = findViewById<TextView>(R.id.btnShareResonanceImage)
        btnShareImage?.setOnClickListener { exportAndSharePoster() }
        ViewAnimationHelper.attachSpringTouch(btnShareImage)

        setupThemeTabs()
    }

    private fun showSelectWorkADialog() {
        // T4.6-a（本轮核验新发现的第 4 处同类项，计划原只列 3 处）：
        // 选择器只消费标题/作者/分类/标签/封面/评分/状态，不涉及 description/review，
        // 故同样改用列白名单。此处保持同步调用——点击触发的低频操作，
        // 且弹窗需在主线程即时呈现。
        val allWorks = databaseHelper.getBooksForList()
        if (allWorks.size < 2) {
            Toast.makeText(this, "书库中至少需要 2 部作品才能生成双生共鸣微卡", Toast.LENGTH_SHORT).show()
            return
        }

        com.example.readtrace.ui.bottomsheet.WorkPickerBottomSheet.show(
            fragmentManager = supportFragmentManager,
            title = "✨ 选择第 1 部共鸣作品 (Work A)",
            works = allWorks,
            selectedWorkId = bookAId,
            onSelected = { workA ->
                showSelectWorkBDialog(workA, allWorks)
            },
        )
    }

    private fun showSelectWorkBDialog(workA: Book, allWorks: List<Book>) {
        val availableWorksB = allWorks.filter { it.id != workA.id }

        com.example.readtrace.ui.bottomsheet.WorkPickerBottomSheet.show(
            fragmentManager = supportFragmentManager,
            title = "✨ 选择第 2 部共鸣作品 (Work B)",
            works = availableWorksB,
            selectedWorkId = bookBId,
            onSelected = { workB ->
                bookAId = workA.id
                bookBId = workB.id

                val mpA = databaseHelper.getMindprint(bookAId)
                val mpB = databaseHelper.getMindprint(bookBId)
                similarity = calculateSimilarity(mpA, mpB)
                resonanceTrait = determineResonanceTrait(workA, workB, mpA, mpB)

                com.example.readtrace.util.HapticFeedbackEngine.celestialResonancePulse(this)
                com.example.readtrace.util.SpatialAudioEngine.playCelestialTone()
                loadData()
            },
        )
    }

    private fun calculateSimilarity(mpA: BookMindprint?, mpB: BookMindprint?): Int {
        // 至少一方持有真实心智档案才计算；双方均无档案时不给出"假共鸣"数值
        val aHas = mpA != null && hasMindprintData(mpA)
        val bHas = mpB != null && hasMindprintData(mpB)
        if (!aHas && !bHas) return NO_DATA_SIMILARITY
        // 仅一方有档案：以有档案的一方为基准，给一个中性偏低的可信区间
        if (!aHas || !bHas) return ONE_SIDE_SIMILARITY

        val ma = mpA!!
        val mb = mpB!!
        val diff1 = kotlin.math.abs(ma.depthScore - mb.depthScore)
        val diff2 = kotlin.math.abs(ma.artistryScore - mb.artistryScore)
        val diff3 = kotlin.math.abs(ma.emotionScore - mb.emotionScore)
        val diff4 = kotlin.math.abs(ma.logicScore - mb.logicScore)
        val diff5 = kotlin.math.abs(ma.healingScore - mb.healingScore)
        val avgDiff = (diff1 + diff2 + diff3 + diff4 + diff5) / 5.0
        // 满量程 2.6：与星系视图 dynamicTrait 保持同一套刻度，避免同一对作品在两处给出不同数字
        return (99.0 - (avgDiff / 2.6) * 30.0).toInt().coerceIn(65, 99)
    }

    /** 判断心智档案是否携带真实数据（五维全为默认 8.0 且难度为 5.0 时视为未录入） */
    private fun hasMindprintData(mp: BookMindprint): Boolean {
        val defaults = mp.depthScore == 8.0 && mp.artistryScore == 8.0 &&
            mp.emotionScore == 8.0 && mp.logicScore == 8.0 &&
            mp.difficultyScore == 5.0 && mp.healingScore == 8.0
        return !defaults
    }

    private fun determineResonanceTrait(bookA: Book, bookB: Book, mpA: BookMindprint?, mpB: BookMindprint?): String {
        val commonTag = bookA.tags.firstOrNull { tA ->
            bookB.tags.any { tB -> tB.contains(tA, ignoreCase = true) || tA.contains(tB, ignoreCase = true) }
        }
        if (commonTag != null) {
            return "跨媒介共鸣 · $commonTag"
        }
        val catA = bookA.category?.takeIf { it.isNotBlank() } ?: "精神"
        val catB = bookB.category?.takeIf { it.isNotBlank() } ?: "心智"
        return "$catA × $catB · 心智合璧"
    }

    private fun setupThemeTabs() {
        val themes = listOf(
            findViewById<TextView>(R.id.themeObsidian) to ResonancePosterView.PosterTheme.OBSIDIAN,
            findViewById<TextView>(R.id.themeRicePaper) to ResonancePosterView.PosterTheme.RICE_PAPER,
            findViewById<TextView>(R.id.themeCyber) to ResonancePosterView.PosterTheme.CYBER,
            findViewById<TextView>(R.id.themeSunset) to ResonancePosterView.PosterTheme.SUNSET,
        )

        themes.forEach { (tab, theme) ->
            tab.setOnClickListener {
                themes.forEach { (t, th) ->
                    val isSelected = th == theme
                    t.setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_dark_chip)
                    t.setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#EAE2D5"))
                }
                resonancePosterView.setTheme(theme)
            }
            ViewAnimationHelper.attachSpringTouch(tab)
        }
    }

    /**
     * T4.6-a：原在主线程做全表 getBooks()（SELECT *，含 description/review 长文），
     * 而本页对该结果只消费两项——「作品数 ≥ 2」与「默认取前两部作品 id」。
     * 遂改用列白名单 getBooksForList() 并把查询移出主线程。
     * 作品详情仍由 getBook(id) 单查（含 review，ResonancePosterView 确实消费该字段），语义不变。
     * 守卫：以 requestedA/requestedB 与当前 bookAId/bookBId 比对，用户在选择器里改了作品时丢弃过期结果。
     */
    private fun loadData() {
        val requestedA = bookAId
        val requestedB = bookBId

        Thread {
            val allBooks = databaseHelper.getBooksForList()

            val needDefault = requestedA <= 0 || requestedB <= 0 || requestedA == requestedB
            val idA = if (needDefault) allBooks.getOrNull(0)?.id ?: -1L else requestedA
            val idB = if (needDefault) allBooks.getOrNull(1)?.id ?: -1L else requestedB

            // 注：原实现在 needDefault 分支与后文各取一次心智档案，同 id 重复查询，此处合并为一次
            val mpA = if (idA > 0) databaseHelper.getMindprint(idA) else null
            val mpB = if (idB > 0) databaseHelper.getMindprint(idB) else null
            val bookA = if (idA > 0) databaseHelper.getBook(idA) else null
            val bookB = if (idB > 0) databaseHelper.getBook(idB) else null

            val computedSimilarity = calculateSimilarity(mpA, mpB)
            val computedTrait = if (needDefault && allBooks.size >= 2) {
                determineResonanceTrait(allBooks[0], allBooks[1], mpA, mpB)
            } else {
                null
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (requestedA != bookAId || requestedB != bookBId) return@runOnUiThread

                if (allBooks.size < 2) {
                    Toast.makeText(this, "书库中作品不足 2 部，无法生成双生共鸣微卡", Toast.LENGTH_SHORT).show()
                    finish()
                    return@runOnUiThread
                }
                if (bookA == null || bookB == null) {
                    Toast.makeText(this, "未找到双生共鸣作品数据", Toast.LENGTH_SHORT).show()
                    finish()
                    return@runOnUiThread
                }

                // 仅默认选片路径改写状态；由选择器指定作品的路径保留调用方（onSelected）已算好的值
                if (needDefault) {
                    bookAId = idA
                    bookBId = idB
                    similarity = computedSimilarity
                    computedTrait?.let { resonanceTrait = it }
                }

                resonancePosterView.setData(
                    bookA = bookA,
                    mindprintA = mpA,
                    bookB = bookB,
                    mindprintB = mpB,
                    similarity = similarity,
                    resonanceTrait = resonanceTrait,
                )
            }
        }.start()
    }

    private fun savePosterToGallery() {
        Toast.makeText(this, "正在保存 1080P 超清微卡至相册...", Toast.LENGTH_SHORT).show()
        // 主线程仅做 View 离屏绘制采样，PNG 压缩与磁盘 I/O 移交后台线程，主线程零卡顿
        val bitmap = try {
            resonancePosterView.exportUltraHdBitmap()
        } catch (e: Exception) {
            Toast.makeText(this, "保存相册失败: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            val result = runCatching {
                val filename = "ReadTrace_TwinResonance_${System.currentTimeMillis()}.png"
                val resolver = contentResolver

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/ReadTrace")
                    }
                    val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (imageUri != null) {
                        resolver.openOutputStream(imageUri)?.use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException("保存至相册失败"))
                    }
                } else {
                    val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                    val appDir = File(picturesDir, "ReadTrace").apply { if (!exists()) mkdirs() }
                    val imageFile = File(appDir, filename)
                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    android.media.MediaScannerConnection.scanFile(this, arrayOf(imageFile.absolutePath), arrayOf("image/png"), null)
                    Result.success(Unit)
                }.also {
                    bitmap.recycle()
                }
            }.getOrElse { Result.failure(it) }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.fold(
                    onSuccess = {
                        Toast.makeText(this, "✨ 成功保存至相册 /Pictures/ReadTrace！", Toast.LENGTH_LONG).show()
                    },
                    onFailure = {
                        Toast.makeText(this, "保存相册失败: ${it.message}", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }.start()
    }

    private fun exportAndSharePoster() {
        Toast.makeText(this, "正在生成 1080P 双生共鸣超清微卡...", Toast.LENGTH_SHORT).show()
        val bitmap = try {
            resonancePosterView.exportUltraHdBitmap()
        } catch (e: Exception) {
            Toast.makeText(this, "导出分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            val result = runCatching {
                val cacheFile = File(cacheDir, "readtrace_twin_resonance_${System.currentTimeMillis()}.png")
                FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()
                cacheFile
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.fold(
                    onSuccess = { cacheFile ->
                        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", cacheFile)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "《阅痕》双生共鸣精神微卡")
                            putExtra(Intent.EXTRA_TEXT, "✨ 跨媒介关联：$resonanceTrait · $similarity% 契合度")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "分享双生共鸣微卡"))
                    },
                    onFailure = {
                        Toast.makeText(this, "导出分享失败: ${it.message}", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }.start()
    }

    companion object {
        const val EXTRA_BOOK_A_ID = "extra_book_a_id"
        const val EXTRA_BOOK_B_ID = "extra_book_b_id"
        const val EXTRA_SIMILARITY = "extra_similarity"
        const val EXTRA_RESONANCE_TRAIT = "extra_resonance_trait"

        /** 双方均未录入心智档案：不给虚构契合度，用 0 表示"暂无可比数据" */
        private const val NO_DATA_SIMILARITY = 0

        /** 仅一方有档案：给中性偏低值，明确暗示数据不完整 */
        private const val ONE_SIDE_SIMILARITY = 78

        fun createIntent(
            context: Context,
            bookAId: Long,
            bookBId: Long,
            similarity: Int,
            resonanceTrait: String,
        ): Intent {
            return Intent(context, ResonancePosterActivity::class.java).apply {
                putExtra(EXTRA_BOOK_A_ID, bookAId)
                putExtra(EXTRA_BOOK_B_ID, bookBId)
                putExtra(EXTRA_SIMILARITY, similarity)
                putExtra(EXTRA_RESONANCE_TRAIT, resonanceTrait)
            }
        }
    }
}
