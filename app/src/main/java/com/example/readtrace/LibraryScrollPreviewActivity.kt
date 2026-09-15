package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.util.FloatingBack
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.LibraryScrollView
import java.io.File
import java.io.FileOutputStream

/**
 * 全息藏书长卷预览页：先实时展示藏库当前筛选结果绘制的宣纸画卷，
 * 由用户确认后手动点击「导出分享」再离屏渲染 1080P 长图并拉起系统分享。
 */
class LibraryScrollPreviewActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var libraryScrollView: LibraryScrollView
    private lateinit var scrollMainTitle: TextView
    private lateinit var scrollSubTitle: TextView

    private var filterSummary: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library_scroll_preview)

        databaseHelper = BookDatabaseHelper.getInstance(this)
        libraryScrollView = findViewById(R.id.libraryScrollView)
        scrollMainTitle = findViewById(R.id.scrollMainTitle)
        scrollSubTitle = findViewById(R.id.scrollSubTitle)

        val btnShareScroll = findViewById<TextView>(R.id.btnShareScroll)

        listOf(
            btnShareScroll,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }

        FloatingBack.install(this)

        filterSummary = intent.getStringExtra(EXTRA_FILTER_SUMMARY).orEmpty()
        val bookIds = intent.getLongArrayExtra(EXTRA_BOOK_IDS) ?: LongArray(0)

        // 依传入顺序还原藏品（轻量列表查询即可满足长卷字段），期间被删除的藏品自动跳过
        val idSet = bookIds.toSet()
        val booksById = databaseHelper.getBooksForList()
            .filter { idSet.contains(it.id) }
            .associateBy { it.id }
        val books = bookIds.map { booksById[it] }.filterNotNull()

        if (books.isEmpty()) {
            Toast.makeText(this, "藏品数据已变化，请返回藏库重新生成", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        libraryScrollView.isDarkMode = com.example.readtrace.util.ThemeHelper.isDarkMode(this)
        libraryScrollView.setLibraryData(books, filterSummary, libraryScrollView.isDarkMode)
        scrollSubTitle.text = "${filterSummary} · 共 ${books.size} 座精神坐标"

        btnShareScroll.setOnClickListener {
            exportAndShareScroll()
        }
    }

    private fun exportAndShareScroll() {
        Toast.makeText(this, "正在离屏渲染 1080P 全息藏书长卷...", Toast.LENGTH_SHORT).show()

        Thread {
            // T4.2：离屏大位图必须无论成功还是失败都回收。放在 runCatching 外，
            // 否则 compress/FileProvider 抛异常时这张上百 MB 的位图会始终悬在堆里。
            var bitmap: Bitmap? = null
            runCatching {
                val bmp = libraryScrollView.exportUltraHdBitmap()
                bitmap = bmp
                val cacheDir = File(cacheDir, "scrolls").apply { if (!exists()) mkdirs() }
                val file = File(cacheDir, "readtrace_library_scroll_${System.currentTimeMillis()}.png")

                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val uri: Uri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    file,
                )

                runOnUiThread {
                    // T4.4：导出耗时较长时用户可能已退出本页，回主线程后必须先校验存活
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "《阅痕》全息藏书长卷")
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "✨ 这是我在《阅痕》生成的「全息藏书长卷」（$filterSummary），共沉淀 ${libraryScrollView.bookList.size} 座精神坐标，收藏即是热爱。",
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "分享我的「全息藏书长卷」"))
                }
            }.onFailure {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    Toast.makeText(this, "导出长卷失败: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
            runCatching { bitmap?.takeIf { it.isRecycled.not() }?.recycle() }
        }.start()
    }

    override fun onDestroy() {
        // T4.2：释放在导出期间逐张累积的封面位图缓存。
        // 用 isInitialized 守卫：lateinit 在 setContentView 阶段异常时可能未被赋值，
        // 而 onDestroy 仍会被调用，直访会抛 UninitializedPropertyAccessException。
        if (::libraryScrollView.isInitialized) {
            libraryScrollView.releaseCovers()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_BOOK_IDS = "extra_book_ids"
        const val EXTRA_FILTER_SUMMARY = "extra_filter_summary"

        fun createIntent(context: Context, bookIds: LongArray, filterSummary: String): Intent {
            return Intent(context, LibraryScrollPreviewActivity::class.java).apply {
                putExtra(EXTRA_BOOK_IDS, bookIds)
                putExtra(EXTRA_FILTER_SUMMARY, filterSummary)
            }
        }
    }
}
