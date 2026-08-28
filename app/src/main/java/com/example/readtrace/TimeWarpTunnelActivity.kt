package com.example.readtrace

import android.app.Dialog
import android.content.Context
import android.content.Intent
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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.SpatialAudioEngine
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.MindprintRadarView
import com.example.readtrace.widget.TimeWarpTunnelView
import com.example.readtrace.util.FloatingBack

/**
 * 🌌 3D 时空穿梭隧道与心智流光胶囊交互系统 (TimeWarpTunnelActivity)
 *
 * 核心特性：
 * 1. 景深无限延伸的 3D 时空虫洞与流光记忆胶囊悬浮；
 * 2. 支持 1x 慢速漫游 / 5x 曲速穿梭 / 10x 光速折跃跳跃；
 * 3. 陀螺仪视差联动与 HUD 驾驶舱全息数据看板；
 * 4. 触控胶囊悬停裂变展开与触觉/空间音频实时联动。
 */
class TimeWarpTunnelActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var timeWarpTunnelView: TimeWarpTunnelView

    private lateinit var tvTunnelSubtitle: TextView
    private lateinit var btnWarpSpeedToggle: TextView
    private lateinit var tunnelFilterContainer: LinearLayout
    private lateinit var tvStatTotalDays: TextView
    private lateinit var tvStatTotalCapsules: TextView
    private lateinit var tvStatResonanceEnergy: TextView
    private lateinit var btnToggleCruise: TextView
    private lateinit var btnHyperspaceJump: TextView

    // 传感器
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null

    // 速度档位（清新优雅漫步节奏）
    private val speeds = listOf(
        Pair(1.0f, "🍃 1x 悠然漫步"),
        Pair(2.5f, "✨ 2.5x 流光漫游"),
        Pair(4.5f, "🌌 4.5x 岁月折跃"),
    )
    private var currentSpeedIndex = 0

    // 当前选中的媒体过滤器
    private var currentFilter: MediaType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContentView(R.layout.activity_time_warp_tunnel)

        // 系统栏避让统一由 HUD 层按 WindowInsets 处理，不再写死顶栏 paddingTop；背景隧道保持全屏沉浸
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.timeWarpHudRoot)) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom + view.paddingBottom)
            insets
        }

        databaseHelper = BookDatabaseHelper.getInstance(this)
        initViews()
        initSensors()
        loadTunnelData()
        setupListeners()
    }

    private fun initViews() {
        timeWarpTunnelView = findViewById(R.id.timeWarpTunnelView)
        tvTunnelSubtitle = findViewById(R.id.tvTunnelSubtitle)
        btnWarpSpeedToggle = findViewById(R.id.btnWarpSpeedToggle)
        tunnelFilterContainer = findViewById(R.id.tunnelFilterContainer)
        tvStatTotalDays = findViewById(R.id.tvStatTotalDays)
        tvStatTotalCapsules = findViewById(R.id.tvStatTotalCapsules)
        tvStatResonanceEnergy = findViewById(R.id.tvStatResonanceEnergy)
        btnToggleCruise = findViewById(R.id.btnToggleCruise)
        btnHyperspaceJump = findViewById(R.id.btnHyperspaceJump)

        FloatingBack.install(this)

        listOfNotNull<View>(
            btnWarpSpeedToggle,
            btnToggleCruise,
            btnHyperspaceJump,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }
    }

    private fun initSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun loadTunnelData() {
        val allBooks = databaseHelper.getBooks()
        val mindprintMap = databaseHelper.getAllMindprints()

        timeWarpTunnelView.setData(allBooks, mindprintMap)

        tvTunnelSubtitle.text = "曲速虫洞 · 封存 ${allBooks.size} 颗流光记忆胶囊"
        tvStatTotalCapsules.text = "${allBooks.size}"
        tvStatTotalDays.text = "${maxOf(30, allBooks.size * 12)}+"
        tvStatResonanceEnergy.text = "98.8%"

        buildFilterChips()
    }

    private fun buildFilterChips() {
        tunnelFilterContainer.removeAllViews()

        data class FilterTab(val label: String, val type: MediaType?)
        val tabs = listOf(
            FilterTab("✦ 全时空", null),
            FilterTab("📖 纸墨篇章", MediaType.BOOK),
            FilterTab("🌸 追番印记", MediaType.ANIME),
            FilterTab("🎬 胶片光影", MediaType.MOVIE),
            FilterTab("🎮 游戏通关", MediaType.GAME),
            FilterTab("💿 音乐唱盘", MediaType.MUSIC),
        )

        tabs.forEach { tab ->
            val isSelected = tab.type == currentFilter
            val chip = TextView(this).apply {
                text = tab.label
                textSize = 12f
                setPadding(dpToPx(12f), dpToPx(6f), dpToPx(12f), dpToPx(6f))
                setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#C5BCAD"))
                setOnClickListener {
                    HapticFeedbackEngine.lightClick(context)
                    currentFilter = tab.type
                    buildFilterChips()
                    timeWarpTunnelView.applyFilter(tab.type)
                }
            }
            ViewAnimationHelper.attachSpringTouch(chip)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = dpToPx(8f)
            }
            tunnelFilterContainer.addView(chip, lp)
        }
    }

    private fun setupListeners() {
        // 胶囊点击：悬停与全息卡片检视
        timeWarpTunnelView.onCapsuleClickListener = { book, mindprint ->
            HapticFeedbackEngine.cartridgeSnap(this)
            SpatialAudioEngine.playTicketTear()
            showCapsuleDetailDialog(book, mindprint)
        }

        // 速度档位切换
        btnWarpSpeedToggle.setOnClickListener {
            currentSpeedIndex = (currentSpeedIndex + 1) % speeds.size
            val (speed, label) = speeds[currentSpeedIndex]
            timeWarpTunnelView.speedMultiplier = speed
            btnWarpSpeedToggle.text = label

            HapticFeedbackEngine.celestialResonancePulse(this)
            SpatialAudioEngine.playCelestialTone()
            Toast.makeText(this, "虫洞巡航调速至：$label", Toast.LENGTH_SHORT).show()
        }

        // 暂停 / 继续漫游
        btnToggleCruise.setOnClickListener {
            HapticFeedbackEngine.lightClick(this)
            timeWarpTunnelView.isAutoCruising = !timeWarpTunnelView.isAutoCruising
            btnToggleCruise.text = if (timeWarpTunnelView.isAutoCruising) "⏸ 暂停漫游" else "▶ 继续巡航"
        }

        // 时序折跃漫游
        btnHyperspaceJump.setOnClickListener {
            HapticFeedbackEngine.celestialResonancePulse(this)
            SpatialAudioEngine.playCelestialTone()

            timeWarpTunnelView.speedMultiplier = 6.0f
            btnHyperspaceJump.text = "✨ 岁月折跃中..."
            btnHyperspaceJump.isEnabled = false

            timeWarpTunnelView.postDelayed({
                timeWarpTunnelView.speedMultiplier = speeds[currentSpeedIndex].first
                btnHyperspaceJump.text = "🚀 时光折跃漫游"
                btnHyperspaceJump.isEnabled = true
            }, 1800)
        }
    }

    private fun showCapsuleDetailDialog(book: Book, mindprint: BookMindprint?) {
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
        ratingText.text = "★ ${book.rating ?: 5.0}"

        if (mindprint != null) {
            radarView.setMindprint(mindprint, animate = false)
        }

        btnDetail.text = "✦ 漫步进入作品"
        btnDetail.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, BookDetailActivity::class.java).apply {
                putExtra("extra_book_id", book.id)
            }
            startActivity(intent)
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

    override fun onSensorChanged(event: SensorEvent) {
        runCatching {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotMatrix, orientation)
                val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
                val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()

                timeWarpTunnelView.gyroOffsetX = (roll / 45f).coerceIn(-1f, 1f)
                timeWarpTunnelView.gyroOffsetY = (pitch / 45f).coerceIn(-1f, 1f)
            } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val ax = event.values.getOrNull(0) ?: 0f
                val ay = event.values.getOrNull(1) ?: 0f
                val az = event.values.getOrNull(2) ?: 9.8f
                val roll = Math.toDegrees(kotlin.math.atan2(ax.toDouble(), kotlin.math.sqrt((ay * ay + az * az).toDouble()))).toFloat()
                val pitch = Math.toDegrees(kotlin.math.atan2(ay.toDouble(), kotlin.math.sqrt((ax * ax + az * az).toDouble()))).toFloat()
                timeWarpTunnelView.gyroOffsetX = (roll / 45f).coerceIn(-1f, 1f)
                timeWarpTunnelView.gyroOffsetY = (pitch / 45f).coerceIn(-1f, 1f)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }
}
