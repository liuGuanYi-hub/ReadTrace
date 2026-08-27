package com.example.readtrace

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.SpatialAudioEngine
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.MindprintRadarView
import com.example.readtrace.widget.MindprintTopologyView
import com.example.readtrace.util.FloatingBack

/**
 * 🗺️ 3D 情绪拓扑与等高线心智地形图系统 (MindprintTopologyActivity)
 *
 * 核心特性：
 * 1. 28x28 网格多峰高斯势能地形构建，将多媒介心智数据三维降维可视化；
 * 2. 3D 发光等高线、空间网格、能量热力图三大渲染模式一键切换；
 * 3. 精神海拔等高切片推杆 (Elevation Slicer) 与多指 3D 俯仰旋转；
 * 4. 巅峰作品水晶信标触控聚焦与触觉/空间音频联动。
 */
class MindprintTopologyActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var mindprintTopologyView: MindprintTopologyView

    private lateinit var btnTopologyModeToggle: TextView
    private lateinit var tvSliceLabel: TextView
    private lateinit var sliderAltitudeSlice: com.example.readtrace.widget.HapticTickSlider
    private lateinit var btnResetView: TextView
    private lateinit var tvStatHighestPeak: TextView
    private lateinit var tvStatBeaconCount: TextView
    private lateinit var tvStatDominantDomain: TextView

    // 传感器
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null

    // 渲染模式列表
    private val modes = MindprintTopologyView.RenderMode.values()
    private var currentModeIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mindprint_topology)

        databaseHelper = BookDatabaseHelper(this)
        initViews()
        initSensors()
        loadTopologyData()
        setupListeners()
    }

    private fun initViews() {
        mindprintTopologyView = findViewById(R.id.mindprintTopologyView)
        btnTopologyModeToggle = findViewById(R.id.btnTopologyModeToggle)
        tvSliceLabel = findViewById(R.id.tvSliceLabel)
        sliderAltitudeSlice = findViewById(R.id.sliderAltitudeSlice)
        sliderAltitudeSlice.progress = 0f
        btnResetView = findViewById(R.id.btnResetView)
        tvStatHighestPeak = findViewById(R.id.tvStatHighestPeak)
        tvStatBeaconCount = findViewById(R.id.tvStatBeaconCount)
        tvStatDominantDomain = findViewById(R.id.tvStatDominantDomain)

        listOfNotNull<View>(
            btnTopologyModeToggle,
            btnResetView,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }
    }

    private fun initSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun loadTopologyData() {
        val allBooks = databaseHelper.getBooks()
        val mindprintMap = mutableMapOf<Long, BookMindprint>()
        var highestScore = 0.0
        var highestBook: Book? = null

        allBooks.forEach { book ->
            databaseHelper.getMindprint(book.id)?.let { mp ->
                mindprintMap[book.id] = mp
                if (mp.averageScore() > highestScore) {
                    highestScore = mp.averageScore()
                    highestBook = book
                }
            }
        }

        mindprintTopologyView.setData(allBooks, mindprintMap)

        val focusId = intent.getLongExtra(EXTRA_FOCUS_BOOK_ID, -1L)
        if (focusId != -1L) {
            mindprintTopologyView.focusBook(focusId)
        }

        val peakTitle = highestBook?.title ?: "百年孤独"
        tvStatHighestPeak.text = "8848m 《$peakTitle》"
        tvStatBeaconCount.text = "${minOf(16, allBooks.size)} 座"
        tvStatDominantDomain.text = "浪漫与哲思"
    }

    private fun setupListeners() {
        // 模式切换：等高线 vs 立体网格 vs 热力引力场
        btnTopologyModeToggle.setOnClickListener {
            currentModeIndex = (currentModeIndex + 1) % modes.size
            val targetMode = modes[currentModeIndex]
            mindprintTopologyView.renderMode = targetMode
            btnTopologyModeToggle.text = targetMode.displayName

            HapticFeedbackEngine.lightClick(this)
            Toast.makeText(this, "地貌渲染模式切换至：${targetMode.displayName}", Toast.LENGTH_SHORT).show()
        }

        // 精神海拔等高切片磁吸阻尼推杆 (Elevation Slicer)
        sliderAltitudeSlice.onProgressChanged = { threshold ->
            mindprintTopologyView.sliceThreshold = threshold
            val meters = (threshold * 8848).toInt()
            tvSliceLabel.text = "🏔️ 精神海拔切片: ${meters}m"
        }

        // 视角复位
        btnResetView.setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            mindprintTopologyView.sliceThreshold = 0f
            sliderAltitudeSlice.progress = 0f
            Toast.makeText(this, "3D 地形视角与切片已复位", Toast.LENGTH_SHORT).show()
        }

        // 导出 1080P 拓扑图谱海报
        findViewById<View>(R.id.btnTopologyShare)?.setOnClickListener {
            exportAndShareTopologyPoster()
        }

        // 点击水晶地标信标
        mindprintTopologyView.onBeaconClickListener = { book, mindprint ->
            HapticFeedbackEngine.celestialResonancePulse(this)
            SpatialAudioEngine.playCelestialTone()
            showBeaconDetailDialog(book, mindprint)
        }
    }

    private fun exportAndShareTopologyPoster() {
        runCatching {
            Toast.makeText(this, "正在生成 1080P 心智拓扑图谱海报...", Toast.LENGTH_SHORT).show()
            val bitmap = mindprintTopologyView.create1080pPosterBitmap()
            val cacheFile = java.io.File(cacheDir, "readtrace_topology_${System.currentTimeMillis()}.png")
            java.io.FileOutputStream(cacheFile).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", cacheFile)
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "《阅痕》3D 情绪等高线地形图谱")
                putExtra(android.content.Intent.EXTRA_TEXT, "🗺️ 阅痕心智大陆 · 1024 km² 精神海拔拓扑全景图")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(shareIntent, "分享心智拓扑图谱"))
        }.onFailure {
            Toast.makeText(this, "生成海报失败: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBeaconDetailDialog(book: Book, mindprint: BookMindprint) {
        val dialog = Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_book_hologram_peek, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        val coverImage = view.findViewById<ImageView>(R.id.peekCoverImage)
        val titleText = view.findViewById<TextView>(R.id.peekBookTitle)
        val authorText = view.findViewById<TextView>(R.id.peekBookAuthor)
        val mediaBadge = view.findViewById<TextView>(R.id.peekMediaBadge)
        val statusBadge = view.findViewById<TextView>(R.id.peekStatusBadge)
        val ratingText = view.findViewById<TextView>(R.id.peekRating)
        val radarView = view.findViewById<MindprintRadarView>(R.id.peekMindprintRadar)
        val btnDetail = view.findViewById<TextView>(R.id.peekActionDetail)

        CoverImageHelper.loadCover(coverImage, book.coverUrl)
        mediaBadge.text = book.mediaType.emoji
        titleText.text = book.title
        authorText.text = "${book.category.orEmpty()} · ${book.author.orEmpty()}"
        statusBadge.text = book.status.getDisplayName(book.mediaType)
        val altitudeMeters = (mindprint.averageScore() * 884.8).toInt()
        ratingText.text = "★ ${book.rating ?: 5.0} · 精神海拔 ${altitudeMeters}m"

        radarView.setMindprint(mindprint, animate = false)

        btnDetail.text = "🏔️ 穿透进入精神高地"
        btnDetail.setOnClickListener {
            dialog.dismiss()
            startActivity(BookDetailActivity.createIntent(this, book.id))
        }

        view.findViewById<View>(R.id.peekActionTimer).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.peekActionPoster).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        databaseHelper.close()
    }

    override fun onSensorChanged(event: SensorEvent) {
        // 部分机型传感器数据异常（如 values 长度不足）会使 getRotationMatrixFromVector 抛出异常，统一兜底避免闪退
        runCatching {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotMatrix, orientation)
                val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
                val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()

                mindprintTopologyView.gyroOffsetX = (roll / 45f).coerceIn(-1f, 1f)
                mindprintTopologyView.gyroOffsetY = (pitch / 45f).coerceIn(-1f, 1f)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        const val EXTRA_FOCUS_BOOK_ID = "extra_focus_book_id"

        fun createIntent(context: Context, focusBookId: Long = -1L): android.content.Intent {
            return android.content.Intent(context, MindprintTopologyActivity::class.java).apply {
                if (focusBookId != -1L) {
                    putExtra(EXTRA_FOCUS_BOOK_ID, focusBookId)
                }
            }
        }
    }
}
