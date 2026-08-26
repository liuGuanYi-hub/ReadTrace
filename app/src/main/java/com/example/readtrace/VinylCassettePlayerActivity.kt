package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.widget.AudioVisualizerParticleView
import com.example.readtrace.widget.CassetteDeckView
import com.example.readtrace.widget.VinylTurntableView

/**
 * 💽 3D 拟真黑胶唱机与磁带卡座播放系统 (VinylCassettePlayerActivity)
 *
 * 核心特性：
 * 1. 💽 黑胶唱机 (Vinyl) 与 📼 复古透明磁带 (Cassette) 双模式一键无缝切换；
 * 2. 唱针落针/抬针物理缓动、各向异性同心圆反射高光与双轮磁带卷带；
 * 3. 随曲目自动流淌的高光金句与深度心境解析；
 * 4. 支持夜鹿 (Yorushika) 与真夜中 (ZUTOMAYO) 等全量曲目顺畅切歌；
 * 5. 陀螺仪重力感应全息光斑倾角联动与线性马达触觉反馈。
 */
class VinylCassettePlayerActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var databaseHelper: BookDatabaseHelper
    private var playlist = listOf<Book>()
    private var currentIndex = 0
    private var isPlaying = false
    private var isCassetteMode = false // false 为黑胶模式, true 为磁带模式

    // 控件引用
    private lateinit var particleBackgroundView: AudioVisualizerParticleView
    private lateinit var vinylTurntableView: VinylTurntableView
    private lateinit var cassetteDeckView: CassetteDeckView
    private lateinit var tvPlayerTitle: TextView
    private lateinit var tvPlayerSubtitle: TextView
    private lateinit var btnToggleMode: TextView
    private lateinit var tvQuoteLyrics: TextView
    private lateinit var tvTrackArtistInfo: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var playerSeekBar: SeekBar
    private lateinit var btnPlayPausePill: View
    private lateinit var tvPlayPauseLabel: TextView
    private lateinit var btnPrevTrack: ImageButton
    private lateinit var btnNextTrack: ImageButton
    private lateinit var btnSpeedToggle: TextView
    private lateinit var btnAmbientSound: TextView

    // 传感器与震动
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var vibrator: Vibrator? = null

    // 模拟播放时间循环
    private val handler = Handler(Looper.getMainLooper())
    private var currentSeconds = 0
    private val totalSeconds = 232 // 03:52

    private val playRunnable = object : Runnable {
        override fun run() {
            if (isPlaying) {
                currentSeconds = (currentSeconds + 1) % totalSeconds
                updatePlaybackProgress()
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private val ambientModes = listOf("🌧️ 雨夜", "🌲 松林", "☕ 咖啡馆", "🔥 柴火", "🔇 静音")
    private var currentAmbientIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vinyl_cassette_player)

        databaseHelper = BookDatabaseHelper(this)
        initViews()
        initSensorsAndVibrator()
        loadPlaylist()
        setupListeners()
    }

    private fun initViews() {
        particleBackgroundView = findViewById(R.id.particleBackgroundView)
        vinylTurntableView = findViewById(R.id.vinylTurntableView)
        cassetteDeckView = findViewById(R.id.cassetteDeckView)
        tvPlayerTitle = findViewById(R.id.tvPlayerTitle)
        tvPlayerSubtitle = findViewById(R.id.tvPlayerSubtitle)
        btnToggleMode = findViewById(R.id.btnToggleMode)
        tvQuoteLyrics = findViewById(R.id.tvQuoteLyrics)
        tvTrackArtistInfo = findViewById(R.id.tvTrackArtistInfo)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        playerSeekBar = findViewById(R.id.playerSeekBar)
        btnPlayPausePill = findViewById(R.id.btnPlayPausePill)
        tvPlayPauseLabel = findViewById(R.id.tvPlayPauseLabel)
        btnPrevTrack = findViewById(R.id.btnPrevTrack)
        btnNextTrack = findViewById(R.id.btnNextTrack)
        btnSpeedToggle = findViewById(R.id.btnSpeedToggle)
        btnAmbientSound = findViewById(R.id.btnAmbientSound)

        findViewById<View>(R.id.btnPlayerBack).setOnClickListener {
            finish()
        }
    }

    private fun initSensorsAndVibrator() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun loadPlaylist() {
        val initialBookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        val allBooks = databaseHelper.getBooks()
        playlist = allBooks.filter { it.mediaType == MediaType.PODCAST }
        if (playlist.isEmpty()) {
            playlist = allBooks
        }

        if (initialBookId != -1L) {
            val foundIdx = playlist.indexOfFirst { it.id == initialBookId }
            if (foundIdx != -1) {
                currentIndex = foundIdx
            }
        }

        renderCurrentTrack()
    }

    private fun renderCurrentTrack() {
        if (playlist.isEmpty()) return
        val track = playlist[currentIndex]

        tvPlayerTitle.text = if (isCassetteMode) "📼 80s 复古磁带卡座" else "💽 拟真黑胶唱机"
        tvPlayerSubtitle.text = "《${track.title}》· Hi-Res 模拟声场"

        tvQuoteLyrics.text = if (!track.shortComment.isNullOrBlank()) {
            "“${track.shortComment}”"
        } else {
            "“把所有的不安塞进夜色深处，在美妙的旋律里独自起舞。”"
        }
        tvTrackArtistInfo.text = "—— ${track.author ?: "未知创作者"} 《${track.title}》"

        vinylTurntableView.trackTitle = track.title
        vinylTurntableView.artistName = track.author ?: "声音宇宙"

        cassetteDeckView.trackTitle = track.title
        cassetteDeckView.artistName = track.author ?: "声音宇宙"

        // 使用 CoverImageHelper 异步加载专辑封面到黑胶唱片中心
        CoverImageHelper.loadCoverBitmap(track.coverUrl) { bmp ->
            vinylTurntableView.coverBitmap = bmp
        }
    }

    private fun setupListeners() {
        // 模式切换：黑胶 vs 磁带
        btnToggleMode.setOnClickListener {
            triggerHapticClick()
            isCassetteMode = !isCassetteMode
            if (isCassetteMode) {
                btnToggleMode.text = "💽 切换黑胶"
                vinylTurntableView.visibility = View.GONE
                cassetteDeckView.visibility = View.VISIBLE
                cassetteDeckView.togglePlay(isPlaying)
            } else {
                btnToggleMode.text = "📼 切换磁带"
                cassetteDeckView.visibility = View.GONE
                vinylTurntableView.visibility = View.VISIBLE
                vinylTurntableView.togglePlay(isPlaying)
            }
            renderCurrentTrack()
        }

        // 播放 / 暂停胶囊大键
        btnPlayPausePill.setOnClickListener {
            togglePlayState()
        }

        // 上一曲 / 下一曲
        btnPrevTrack.setOnClickListener {
            triggerHapticClick()
            if (playlist.isNotEmpty()) {
                currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
                currentSeconds = 0
                renderCurrentTrack()
            }
        }

        btnNextTrack.setOnClickListener {
            triggerHapticClick()
            if (playlist.isNotEmpty()) {
                currentIndex = (currentIndex + 1) % playlist.size
                currentSeconds = 0
                renderCurrentTrack()
            }
        }

        // 33 RPM / 45 RPM 转速切换
        btnSpeedToggle.setOnClickListener {
            triggerHapticClick()
            val is33 = btnSpeedToggle.text.contains("33")
            btnSpeedToggle.text = if (is33) "45 RPM" else "33 RPM"
            Toast.makeText(this, if (is33) "切换至 45 RPM 典藏高保真转速" else "切换至 33 1/3 RPM 标准密纹转速", Toast.LENGTH_SHORT).show()
        }

        // 氛围白噪音切换
        btnAmbientSound.setOnClickListener {
            triggerHapticClick()
            currentAmbientIndex = (currentAmbientIndex + 1) % ambientModes.size
            val ambient = ambientModes[currentAmbientIndex]
            btnAmbientSound.text = ambient
            Toast.makeText(this, "伴随声场：$ambient", Toast.LENGTH_SHORT).show()
        }

        // 进度拖动
        playerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentSeconds = (totalSeconds * (progress / 100f)).toInt()
                    updatePlaybackProgress()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun togglePlayState() {
        isPlaying = !isPlaying

        if (isPlaying) {
            tvPlayPauseLabel.text = "⏸ 暂停聆听"
            if (!isCassetteMode) {
                com.example.readtrace.util.HapticFeedbackEngine.needleDropCrackle(this)
                com.example.readtrace.util.SpatialAudioEngine.playNeedleDrop(vinylTurntableView.gyroOffsetX)
            } else {
                com.example.readtrace.util.HapticFeedbackEngine.cartridgeSnap(this)
                com.example.readtrace.util.SpatialAudioEngine.playCartridgeSnap()
            }
            vinylTurntableView.togglePlay(true)
            cassetteDeckView.togglePlay(true)
            particleBackgroundView.setPlaying(true)
            com.example.readtrace.util.AudioReactiveAuroraEngine.startAudioSync()
            handler.post(playRunnable)
        } else {
            com.example.readtrace.util.HapticFeedbackEngine.lightClick(this)
            tvPlayPauseLabel.text = "▶ 开始放唱"
            vinylTurntableView.togglePlay(false)
            cassetteDeckView.togglePlay(false)
            particleBackgroundView.setPlaying(false)
            com.example.readtrace.util.AudioReactiveAuroraEngine.stopAudioSync()
            handler.removeCallbacks(playRunnable)
        }
    }

    private fun updatePlaybackProgress() {
        val curMin = currentSeconds / 60
        val curSec = currentSeconds % 60
        tvCurrentTime.text = String.format("%02d:%02d", curMin, curSec)

        val prog = currentSeconds.toFloat() / totalSeconds.toFloat()
        playerSeekBar.progress = (prog * 100).toInt()
        cassetteDeckView.progress = prog
    }

    private fun triggerHapticClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(25L)
        }
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
        handler.removeCallbacks(playRunnable)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotMatrix, orientation)
            val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
            val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()

            vinylTurntableView.gyroOffsetX = (roll / 45f).coerceIn(-1f, 1f)
            vinylTurntableView.gyroOffsetY = (pitch / 45f).coerceIn(-1f, 1f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"

        fun createIntent(context: Context, bookId: Long): Intent {
            return Intent(context, VinylCassettePlayerActivity::class.java).apply {
                putExtra(EXTRA_BOOK_ID, bookId)
            }
        }
    }
}
