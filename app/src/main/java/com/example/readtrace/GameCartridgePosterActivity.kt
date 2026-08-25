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
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.GameCartridgePosterView
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class GameCartridgePosterActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var gameCartridgePosterView: GameCartridgePosterView
    private lateinit var layoutCartridgeThemeChips: LinearLayout
    private lateinit var tvCartridgeSummary: TextView

    private var gameId: Long = -1
    private var currentGame: Book? = null
    private var currentMindprint: BookMindprint? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_cartridge_poster)

        databaseHelper = BookDatabaseHelper(this)
        gameId = intent.getLongExtra(EXTRA_GAME_ID, -1)

        initViews()
        loadData()
        buildThemeChips()

        val gyroscopeHelper = com.example.readtrace.util.GyroscopeParallaxHelper(this)
        gyroscopeHelper.bind3DParallax(gameCartridgePosterView, maxRotation = 12f, maxTranslation = 16f)
        gyroscopeHelper.bindLifecycle(lifecycle)
    }

    private fun initViews() {
        gameCartridgePosterView = findViewById(R.id.gameCartridgePosterView)
        layoutCartridgeThemeChips = findViewById(R.id.layoutCartridgeThemeChips)
        tvCartridgeSummary = findViewById(R.id.tvCartridgeSummary)

        findViewById<View>(R.id.btnCartridgeBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnCartridgeShareTop).setOnClickListener { exportAndShareCartridge() }
        findViewById<View>(R.id.btnShareCartridgeImage).setOnClickListener { exportAndShareCartridge() }
        findViewById<View>(R.id.btnSaveCartridgeAlbum).setOnClickListener { saveCartridgeToAlbum() }

        listOfNotNull(
            findViewById(R.id.btnCartridgeBack),
            findViewById(R.id.btnCartridgeShareTop),
            findViewById(R.id.btnShareCartridgeImage),
            findViewById(R.id.btnSaveCartridgeAlbum),
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }
    }

    private fun loadData() {
        if (gameId <= 0) {
            Toast.makeText(this, "无效的游戏编号", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentGame = databaseHelper.getBook(gameId)
        if (currentGame == null) {
            Toast.makeText(this, "游戏数据未找到", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentMindprint = databaseHelper.getMindprint(gameId)
        gameCartridgePosterView.setData(currentGame!!, currentMindprint)
        tvCartridgeSummary.text = "🕹️ 《${currentGame!!.title}》· 白金全息通关卡带"
    }

    private fun buildThemeChips() {
        layoutCartridgeThemeChips.removeAllViews()
        GameCartridgePosterView.CartridgeTheme.values().forEach { theme ->
            val isSelected = theme == gameCartridgePosterView.getTheme()
            val chip = TextView(this).apply {
                text = theme.displayName
                textSize = 13f
                setPadding(dpToPx(12f), dpToPx(6f), dpToPx(12f), dpToPx(6f))
                setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#8B949E"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginEnd = dpToPx(8f)
                }

                setOnClickListener {
                    gameCartridgePosterView.setTheme(theme)
                    buildThemeChips()
                }
            }
            ViewAnimationHelper.attachSpringTouch(chip)
            layoutCartridgeThemeChips.addView(chip)
        }
    }

    private fun exportAndShareCartridge() {
        runCatching {
            Toast.makeText(this, "正在生成 1080P 全息白金卡带...", Toast.LENGTH_SHORT).show()
            val bitmap = gameCartridgePosterView.create1080pPosterBitmap()
            val cacheFile = File(cacheDir, "readtrace_cartridge_${System.currentTimeMillis()}.png")
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", cacheFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "《阅痕》全息白金卡带")
                putExtra(Intent.EXTRA_TEXT, "🕹️ 《${currentGame?.title}》第九艺术通关纪念 · 阅痕白金典藏卡带")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享全息卡带"))
        }.onFailure {
            Toast.makeText(this, "导出分享失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveCartridgeToAlbum() {
        runCatching {
            val bitmap = gameCartridgePosterView.create1080pPosterBitmap()
            val filename = "ReadTrace_Cartridge_${System.currentTimeMillis()}.png"
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
                Toast.makeText(this, "✨ 已成功保存全息卡带至相册 (Pictures/ReadTrace)", Toast.LENGTH_LONG).show()
            } ?: Toast.makeText(this, "无法打开相册写入流", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "保存相册失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        const val EXTRA_GAME_ID = "extra_game_id"

        fun createIntent(context: Context, gameId: Long): Intent {
            return Intent(context, GameCartridgePosterActivity::class.java).apply {
                putExtra(EXTRA_GAME_ID, gameId)
            }
        }
    }
}
