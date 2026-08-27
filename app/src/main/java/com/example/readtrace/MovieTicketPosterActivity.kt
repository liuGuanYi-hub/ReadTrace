package com.example.readtrace

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.util.ConfettiBurstHelper
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.SpatialAudioEngine
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.MovieTicketPosterView
import com.example.readtrace.util.FloatingBack
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MovieTicketPosterActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var movieTicketPosterView: MovieTicketPosterView
    private lateinit var layoutTicketThemeChips: LinearLayout
    private lateinit var tvTicketSummary: TextView
    private lateinit var btnToggleTicketTear: TextView

    private var movieId: Long = -1
    private var currentMovie: Book? = null
    private var currentMindprint: BookMindprint? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movie_ticket_poster)

        databaseHelper = BookDatabaseHelper(this)
        movieId = intent.getLongExtra(EXTRA_MOVIE_ID, -1)

        initViews()
        loadData()
        buildThemeChips()

        val gyroscopeHelper = com.example.readtrace.util.GyroscopeParallaxHelper(this)
        gyroscopeHelper.bind3DParallax(movieTicketPosterView, maxRotation = 12f, maxTranslation = 16f)
        gyroscopeHelper.bindLifecycle(lifecycle)
    }

    private fun initViews() {
        movieTicketPosterView = findViewById(R.id.movieTicketPosterView)
        layoutTicketThemeChips = findViewById(R.id.layoutTicketThemeChips)
        tvTicketSummary = findViewById(R.id.tvTicketSummary)
        btnToggleTicketTear = findViewById(R.id.btnToggleTicketTear)

        FloatingBack.install(this)
        findViewById<View>(R.id.btnTicketShareTop).setOnClickListener { exportAndShareTicket() }
        findViewById<View>(R.id.btnShareTicketImage).setOnClickListener { exportAndShareTicket() }
        findViewById<View>(R.id.btnSaveTicketAlbum).setOnClickListener { saveTicketToAlbum() }

        btnToggleTicketTear.setOnClickListener {
            movieTicketPosterView.toggleTear(animate = true)
        }

        movieTicketPosterView.onTicketTearListener = { isTorn, seamX, seamY ->
            if (isTorn) {
                HapticFeedbackEngine.ticketTearRipped(this)
                SpatialAudioEngine.playTicketTear()
                ConfettiBurstHelper.burst(this, seamX, seamY)
                btnToggleTicketTear.text = "✨ 磁吸复原票根"
                Toast.makeText(this, "🎟️ 已完成撕票入场 · 齿轮顿挫与微粒裂变", Toast.LENGTH_SHORT).show()
            } else {
                HapticFeedbackEngine.cartridgeSnap(this)
                SpatialAudioEngine.playCartridgeSnap()
                btnToggleTicketTear.text = "🎟️ 模拟撕票入场"
                Toast.makeText(this, "✨ 票根已磁吸复原完整", Toast.LENGTH_SHORT).show()
            }
        }

        listOfNotNull<View>(
            findViewById(R.id.btnTicketShareTop),
            findViewById(R.id.btnShareTicketImage),
            findViewById(R.id.btnSaveTicketAlbum),
            btnToggleTicketTear,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }
    }

    private fun loadData() {
        if (movieId <= 0) {
            Toast.makeText(this, "无效的电影编号", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentMovie = databaseHelper.getBook(movieId)
        if (currentMovie == null) {
            Toast.makeText(this, "电影数据未找到", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentMindprint = databaseHelper.getMindprint(movieId)
        movieTicketPosterView.setData(currentMovie!!, currentMindprint)
        tvTicketSummary.text = "🎟️ 《${currentMovie!!.title}》· 典藏打孔透光票根"
    }

    private fun buildThemeChips() {
        layoutTicketThemeChips.removeAllViews()
        MovieTicketPosterView.TicketTheme.values().forEach { theme ->
            val isSelected = theme == movieTicketPosterView.getTheme()
            val chip = TextView(this).apply {
                text = theme.displayName
                textSize = 13f
                setPadding(dpToPx(12f), dpToPx(6f), dpToPx(12f), dpToPx(6f))
                setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#C5BCAD"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginEnd = dpToPx(8f)
                }

                setOnClickListener {
                    movieTicketPosterView.setTheme(theme)
                    buildThemeChips()
                }
            }
            ViewAnimationHelper.attachSpringTouch(chip)
            layoutTicketThemeChips.addView(chip)
        }
    }

    private fun exportAndShareTicket() {
        runCatching {
            Toast.makeText(this, "正在生成 1080P 复古电影票根...", Toast.LENGTH_SHORT).show()
            val bitmap = movieTicketPosterView.create1080pPosterBitmap()
            val cacheFile = File(cacheDir, "readtrace_ticket_${System.currentTimeMillis()}.png")
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", cacheFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "《阅痕》复古电影票根")
                putExtra(Intent.EXTRA_TEXT, "🎟️ 《${currentMovie?.title}》光影印记 · 阅痕影院典藏票根")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享电影票根"))
        }.onFailure {
            Toast.makeText(this, "导出分享失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveTicketToAlbum() {
        runCatching {
            val bitmap = movieTicketPosterView.create1080pPosterBitmap()
            val filename = "ReadTrace_Ticket_${System.currentTimeMillis()}.png"
            var fos: OutputStream? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ReadTrace")
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/ReadTrace"
                val file = File(imagesDir)
                if (!file.exists()) file.mkdirs()
                val image = File(imagesDir, filename)
                fos = FileOutputStream(image)
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Toast.makeText(this, "✨ 已成功保存电影票根至相册 (Pictures/ReadTrace)", Toast.LENGTH_LONG).show()
            } ?: Toast.makeText(this, "无法打开相册写入流", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "保存相册失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        const val EXTRA_MOVIE_ID = "extra_movie_id"

        fun createIntent(context: Context, movieId: Long): Intent {
            return Intent(context, MovieTicketPosterActivity::class.java).apply {
                putExtra(EXTRA_MOVIE_ID, movieId)
            }
        }
    }
}
