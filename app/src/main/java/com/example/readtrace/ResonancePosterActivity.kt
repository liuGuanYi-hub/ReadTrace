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
        val allWorks = databaseHelper.getBooks()
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
                Toast.makeText(this, "已生成《${workA.title}》与《${workB.title}》的双生共鸣", Toast.LENGTH_SHORT).show()
            },
        )
    }

    private fun calculateSimilarity(mpA: BookMindprint?, mpB: BookMindprint?): Int {
        if (mpA == null || mpB == null) return 92
        val diff1 = kotlin.math.abs(mpA.depthScore - mpB.depthScore)
        val diff2 = kotlin.math.abs(mpA.artistryScore - mpB.artistryScore)
        val diff3 = kotlin.math.abs(mpA.emotionScore - mpB.emotionScore)
        val diff4 = kotlin.math.abs(mpA.logicScore - mpB.logicScore)
        val diff5 = kotlin.math.abs(mpA.healingScore - mpB.healingScore)
        val avgDiff = (diff1 + diff2 + diff3 + diff4 + diff5) / 5.0
        return (100.0 - avgDiff * 8.0).toInt().coerceIn(65, 99)
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
        return "$catA × $catB · 灵魂合璧"
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

    private fun loadData() {
        val allBooks = databaseHelper.getBooks()
        if (allBooks.size < 2) {
            Toast.makeText(this, "书库中作品不足 2 部，无法生成双生共鸣微卡", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (bookAId <= 0 || bookBId <= 0 || bookAId == bookBId) {
            bookAId = allBooks[0].id
            bookBId = allBooks[1].id
            val mpA = databaseHelper.getMindprint(bookAId)
            val mpB = databaseHelper.getMindprint(bookBId)
            similarity = calculateSimilarity(mpA, mpB)
            resonanceTrait = determineResonanceTrait(allBooks[0], allBooks[1], mpA, mpB)
        }

        val bookA = databaseHelper.getBook(bookAId)
        val bookB = databaseHelper.getBook(bookBId)

        if (bookA == null || bookB == null) {
            Toast.makeText(this, "未找到双生共鸣作品数据", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val mpA = databaseHelper.getMindprint(bookAId)
        val mpB = databaseHelper.getMindprint(bookBId)

        resonancePosterView.setData(
            bookA = bookA,
            mindprintA = mpA,
            bookB = bookB,
            mindprintB = mpB,
            similarity = similarity,
            resonanceTrait = resonanceTrait,
        )
    }

    private fun savePosterToGallery() {
        runCatching {
            Toast.makeText(this, "正在保存 1080P 超清微卡至相册...", Toast.LENGTH_SHORT).show()
            val bitmap = resonancePosterView.exportUltraHdBitmap()
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
                    Toast.makeText(this, "✨ 成功保存至相册 /Pictures/ReadTrace！", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "保存至相册失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "ReadTrace").apply { if (!exists()) mkdirs() }
                val imageFile = File(appDir, filename)
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                android.media.MediaScannerConnection.scanFile(this, arrayOf(imageFile.absolutePath), arrayOf("image/png"), null)
                Toast.makeText(this, "✨ 成功保存至相册 /Pictures/ReadTrace！", Toast.LENGTH_LONG).show()
            }
        }.onFailure {
            Toast.makeText(this, "保存相册失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportAndSharePoster() {
        runCatching {
            Toast.makeText(this, "正在生成 1080P 双生共鸣超清微卡...", Toast.LENGTH_SHORT).show()
            val bitmap = resonancePosterView.exportUltraHdBitmap()
            val cacheFile = File(cacheDir, "readtrace_twin_resonance_${System.currentTimeMillis()}.png")
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", cacheFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "《阅痕》双生共鸣精神微卡")
                putExtra(Intent.EXTRA_TEXT, "✨ 跨媒介灵魂共鸣：$resonanceTrait · $similarity% 契合度")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享双生共鸣微卡"))
        }.onFailure {
            Toast.makeText(this, "导出分享失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_BOOK_A_ID = "extra_book_a_id"
        const val EXTRA_BOOK_B_ID = "extra_book_b_id"
        const val EXTRA_SIMILARITY = "extra_similarity"
        const val EXTRA_RESONANCE_TRAIT = "extra_resonance_trait"

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
