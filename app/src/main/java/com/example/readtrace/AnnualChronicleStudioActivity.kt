package com.example.readtrace

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.HapticFeedbackEngine
import java.time.LocalDate

/**
 * 🏆 策展人年度精神年鉴 (AnnualChronicleStudioActivity)
 *
 * P12：摆脱大厂流水线式年度盘点，生成完全属于个人精神维度的艺术画册。
 * 页面架构：封面通行证 → 宏观足迹 → 巅峰海拔（六维心智雷达）→ 灵魂金句 →
 * 跨媒介星轨（年度最高分跨媒介作品）→ 护照印迹（月度完读分布）。
 * 支持一键渲染为印刷级竖版长图保存至相册。
 */
class AnnualChronicleStudioActivity : AppCompatActivity() {

    private lateinit var pagesContainer: LinearLayout
    private var year: Int = LocalDate.now().year

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_annual_chronicle)

        pagesContainer = findViewById(R.id.chroniclePages)
        findViewById<TextView>(R.id.chronicleBack).setOnClickListener { finish() }
        val tvYearToggle = findViewById<TextView>(R.id.chronicleYearToggle)
        tvYearToggle.text = year.toString()
        tvYearToggle.setOnClickListener {
            showYearPicker(tvYearToggle)
        }
        findViewById<TextView>(R.id.chronicleExport).setOnClickListener {
            HapticFeedbackEngine.stampImpact(this)
            showExportMenu()
        }

        buildChronicle()
    }

    private fun showYearPicker(anchor: TextView) {
        val currentYear = LocalDate.now().year
        val years = (currentYear downTo (currentYear - 5)).map { it.toString() }.toTypedArray()
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("选择盘点年份")
        builder.setItems(years) { _, which ->
            val selected = years[which].toInt()
            if (selected != year) {
                year = selected
                anchor.text = year.toString()
                HapticFeedbackEngine.pageTurnRustle(this)
                buildChronicle()
            }
        }
        builder.show()
    }

    // ---------------------------------------------------------------- 统计

    private data class ChronicleStats(
        val addedCount: Int,
        val finishedCount: Int,
        val sessionMinutes: Int,
        val noteCount: Int,
        val topTags: List<Pair<String, Int>>,
        val bestWorks: List<Book>,
        val monthlyFinished: IntArray, // 长度 12
        val monthlyMinutes: IntArray,  // 长度 12
    )

    private fun collectStats(): ChronicleStats {
        val dbHelper = BookDatabaseHelper.getInstance(this)
        val allBooks = dbHelper.getBooks().filter { !it.isDeleted }
        val prefix = "$year-"

        val added = allBooks.filter { it.createdAt.startsWith(prefix) }
        val finished = allBooks.filter { it.finishDate?.startsWith(prefix) == true }
        val allSessions = dbHelper.getAllReadingSessions().filter { it.createdAt.startsWith(prefix) }
        val minutes = allSessions.sumOf { it.durationMinutes }
        val notes = allBooks.sumOf { b -> dbHelper.getNotes(b.id).count { it.createdAt.startsWith(prefix) } }

        val tagStats = dbHelper.getAllUniqueTags()
        val best = finished.filter { it.rating != null }
            .sortedByDescending { it.rating!! }
            .take(3)

        val monthly = IntArray(12)
        finished.forEach { book ->
            book.finishDate?.let { date ->
                date.substring(5, 7).toIntOrNull()?.let { month ->
                    if (month in 1..12) monthly[month - 1]++
                }
            }
        }

        val monthlyMins = IntArray(12)
        allSessions.forEach { session ->
            if (session.createdAt.length >= 7) {
                session.createdAt.substring(5, 7).toIntOrNull()?.let { month ->
                    if (month in 1..12) monthlyMins[month - 1] += session.durationMinutes
                }
            }
        }

        return ChronicleStats(
            addedCount = added.size,
            finishedCount = finished.size,
            sessionMinutes = minutes,
            noteCount = notes,
            topTags = tagStats.take(6),
            bestWorks = best,
            monthlyFinished = monthly,
            monthlyMinutes = monthlyMins,
        )
    }

    // ---------------------------------------------------------------- 画册构建

    private fun buildChronicle() {
        pagesContainer.removeAllViews()
        val currentYear = year
        Thread {
            val stats = collectStats()
            val persona = BookDatabaseHelper.getInstance(this).getAnnualMindprintPersona()
            runOnUiThread {
                if (isFinishing || isDestroyed || currentYear != year) return@runOnUiThread
                pagesContainer.removeAllViews()

                // 页 1 · 封面：策展人白金通行证
                addPage(
                    title = "🏆 $year 年度精神年鉴",
                    body = "阅痕 ReadTrace · 策展人白金通行证\n\n" +
                        "这一年，你在精神的旷野中跋涉，\n" +
                        "把 ${stats.addedCount} 部作品纳入私人宇宙，\n" +
                        "在 ${stats.finishedCount} 个故事里抵达终点。\n\n" +
                        "精神海拔持续抬升，认知星系继续扩张。",
                )

                // 页 2 · 宏观足迹 + 文化年轮
                addPage(
                    title = "🗺️ 宏观足迹",
                    body = "📖 收录作品：${stats.addedCount} 部\n" +
                        "🏆 读完 / 看完 / 通关：${stats.finishedCount} 部\n" +
                        "⏳ 专注沉浸：${formatMinutes(stats.sessionMinutes)}\n" +
                        "✍️ 留下随想与笔记：${stats.noteCount} 条\n" +
                        "🏷️ 最常用标签：${stats.topTags.joinToString(" · ") { "${it.first}(${it.second})" }.ifBlank { "—行迹尚浅 —" }}",
                    monthlyFinished = stats.monthlyFinished,
                )

                // 页 3 · 巅峰海拔：六维心智雷达
                addPage(
                    title = "⛰️ 巅峰海拔 · 六维心智",
                    body = persona?.let {
                        "年度心智画像：${it.personaTitle} —— ${it.personaDesc}"
                    } ?: "完读作品后，六维心智雷达将在此点亮你的年度地貌。",
                    radar = persona?.avgMindprint,
                )

                // 页 4 · 灵魂金句（首字下沉由 DropCapTextView 承担）
                val quoteBook = stats.bestWorks.firstOrNull()
                addPage(
                    title = "📜 灵魂金句",
                    body = quoteBook?.let {
                        "「${it.shortComment ?: it.review?.lineSequence()?.firstOrNull() ?: "这一年的沉默也是一种回答。"}」\n\n" +
                            "—— 《${it.title}》 · ${it.rating ?: "-"}/10"
                    } ?: "今年尚未留下足够深刻的一句话，来年继续。",
                )

                // 页 5 · 跨媒介星轨
                val starTrail = stats.bestWorks.joinToString("\n") { book ->
                    "${book.mediaType.emoji} 《${book.title}》 · ⭐${book.rating}"
                }
                addPage(
                    title = "🌌 跨媒介星轨",
                    body = if (starTrail.isBlank()) "暂无高分跨媒介共振。" else "年度精神引力最强的三颗星：\n\n$starTrail",
                )

                // 页 6 · 护照印迹：月度完读分布
                val bar = buildMonthlyBar(stats.monthlyFinished)
                addPage(
                    title = "🛂 护照印迹 · 月度完读",
                    body = bar,
                )
            }
        }.start()
    }

    private fun addPage(
        title: String,
        body: String,
        radar: com.example.readtrace.model.BookMindprint? = null,
        monthlyFinished: IntArray? = null,
    ) {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // 画册页底色固定深色：年鉴须日夜恒定（导出一致），且避免日间玻璃白底配近白正文不可读
            setBackgroundResource(R.drawable.bg_chronicle_page)
            elevation = dp(6).toFloat()
            setPadding(dp(18), dp(18), dp(18), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(16) }
        }

        page.addView(
            TextView(this).apply {
                text = title
                textSize = 17f
                // 固定用夜间 accent 色：深色画册底上日间深绿对比度不足
                setTextColor(android.graphics.Color.parseColor("#5E9E71"))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            },
        )

        page.addView(
            com.example.readtrace.widget.DropCapTextView(this).apply {
                text = body
                textSize = 13.5f
                setTextColor(android.graphics.Color.parseColor("#E6FFFFFF"))
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(0, dp(10), 0, 0)
            },
        )

        // 🌲 P15 文化宇宙年轮：月度完读足迹转为年轮环带
        monthlyFinished?.let { monthly ->
            page.addView(
                com.example.readtrace.widget.CulturalTreeRingsView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(230),
                    ).apply {
                        topMargin = dp(12)
                        gravity = android.view.Gravity.CENTER_HORIZONTAL
                    }
                    // 完读部数 → 沉浸深度量级（每部折算 30 分钟基准）
                    setData(IntArray(12) { monthly[it] * 30 })
                },
            )
        }

        radar?.let { mp ->
            page.addView(
                com.example.readtrace.widget.MindprintRadarView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(220),
                    ).apply { topMargin = dp(12) }
                    setMindprint(mp, animate = false)
                },
            )
        }

        pagesContainer.addView(page)
    }

    private fun buildMonthlyBar(monthly: IntArray): String {
        val max = monthly.maxOrNull() ?: 0
        if (max == 0) return "这一年还没有完读记录，等待你盖上第一枚印章。"
        return monthly.mapIndexed { index, count ->
            val filled = if (max > 0) "▮".repeat((count.toFloat() / max * 10).toInt().coerceAtLeast(if (count > 0) 1 else 0)) else ""
            "%2d 月  %s %d".format(index + 1, filled.ifBlank { "·" }, count)
        }.joinToString("\n")
    }

    private fun formatMinutes(minutes: Int): String =
        if (minutes >= 60) "${minutes / 60} 小时 ${minutes % 60} 分钟" else "$minutes 分钟"

    // ---------------------------------------------------------------- 导出工坊 (4K 长图 & 多页矢量 PDF)

    private fun showExportMenu() {
        val options = arrayOf("🖼️ 导出 4K 印刷级长图 (PNG)", "📄 导出多页矢量画册 (PDF)")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("✨ 选择导出格式")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportChronicle()
                    1 -> exportChronicleAsPdf()
                }
            }
            .show()
    }

    private fun exportChronicle() {
        val scroll = findViewById<ScrollView>(R.id.chronicleScroll)
        val content = scroll.getChildAt(0)
        if (content.width <= 0) {
            Toast.makeText(this, "画册尚未渲染完成，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "正在渲染印刷级长图…", Toast.LENGTH_SHORT).show()

        // P3 内存保护：限制最大维度 4096px，动态按比例缩放，避免多页超大长图 OOM
        val maxDimension = 4096f
        val rawHeight = content.height.toFloat()
        val scale = if (rawHeight > maxDimension) maxDimension / rawHeight else 1.0f
        val targetWidth = (content.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (content.height * scale).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (scale < 1.0f) {
            canvas.scale(scale, scale)
        }
        content.draw(canvas)
        val filename = "ReadTrace_AnnualChronicle_${year}_${System.currentTimeMillis()}.png"

        Thread {
            var success = false
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val resolver = contentResolver
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/ReadTrace")
                    }
                    resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                        resolver.openOutputStream(uri)?.use { out ->
                            success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }
                } else {
                    val dir = java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
                        "ReadTrace",
                    ).apply { if (!exists()) mkdirs() }
                    java.io.FileOutputStream(java.io.File(dir, filename)).use { out ->
                        success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            }
            bitmap.recycle()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                Toast.makeText(
                    this,
                    if (success) "✨ 年鉴长图已保存至相册 /Pictures/ReadTrace" else "导出失败，请重试",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }.start()
    }

    /**
     * F8: 将 6 页年鉴按 A4 标准分页导出为多页高清矢量 PDF 画册
     */
    private fun exportChronicleAsPdf() {
        val scroll = findViewById<ScrollView>(R.id.chronicleScroll)
        val content = scroll.getChildAt(0)
        if (content.width <= 0) {
            Toast.makeText(this, "画册尚未渲染完成，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "正在生成多页矢量 PDF 画册…", Toast.LENGTH_SHORT).show()

        Thread {
            var success = false
            var fileUri: android.net.Uri? = null
            val filename = "ReadTrace_AnnualChronicle_${year}_${System.currentTimeMillis()}.pdf"
            val pdfDocument = android.graphics.pdf.PdfDocument()

            try {
                // A4 标准比例 (1 : 1.414)
                val pageWidth = content.width
                val pageHeight = (pageWidth * 1.414f).toInt().coerceAtLeast(600)
                val totalPages = Math.ceil(content.height.toDouble() / pageHeight).toInt().coerceAtLeast(1)

                for (pageIndex in 0 until totalPages) {
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    canvas.save()
                    canvas.translate(0f, -pageIndex * pageHeight.toFloat())
                    content.draw(canvas)
                    canvas.restore()

                    pdfDocument.finishPage(page)
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val resolver = contentResolver
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOCUMENTS + "/ReadTrace")
                    }
                    resolver.insert(android.provider.MediaStore.Files.getContentUri("external"), values)?.let { uri ->
                        fileUri = uri
                        resolver.openOutputStream(uri)?.use { out ->
                            pdfDocument.writeTo(out)
                            success = true
                        }
                    }
                } else {
                    val dir = java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
                        "ReadTrace",
                    ).apply { if (!exists()) mkdirs() }
                    val file = java.io.File(dir, filename)
                    java.io.FileOutputStream(file).use { out ->
                        pdfDocument.writeTo(out)
                        success = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pdfDocument.close()
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (success) {
                    Toast.makeText(this, "📄 年鉴 PDF 已保存至 /Documents/ReadTrace", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "PDF 导出失败，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
