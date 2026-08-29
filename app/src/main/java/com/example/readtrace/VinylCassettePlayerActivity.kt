package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.ElegantFormDialog
import com.example.readtrace.widget.AudioVisualizerParticleView
import com.example.readtrace.widget.CassetteDeckView
import com.example.readtrace.widget.VinylTurntableView
import com.example.readtrace.util.FloatingBack
import com.example.readtrace.util.ViewAnimationHelper

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
    private lateinit var btnOpenNetease: TextView

    // 传感器
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null

    // ===== 真实播放引擎状态 =====
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioTracks: List<com.example.readtrace.model.AudioTrackItem> = emptyList()
    private var currentAudioIndex = 0
    private var totalSecondsMs = 0L
    private var wasPlayingBeforeSeek = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private lateinit var audioManager: AudioManager

    private val importAudioLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            importAudioFiles(uris)
        }
    }

    private val playRunnable = object : Runnable {
        override fun run() {
            val mp = mediaPlayer
            if (isPlaying && mp != null) {
                updatePlaybackProgress()
                handler.postDelayed(this, 500L)
            }
        }
    }

    private val ambientModes = listOf("🌧️ 雨夜", "🌲 松林", "☕ 咖啡馆", "🔥 柴火", "🔇 静音")
    private var currentAmbientIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContentView(R.layout.activity_vinyl_cassette_player)

        // 系统栏避让统一由内容层按 WindowInsets 处理，不再写死顶栏 paddingTop；粒子背景保持全屏沉浸
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.vinylPlayerHudRoot)) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom + view.paddingBottom)
            insets
        }

        databaseHelper = BookDatabaseHelper(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initViews()
        initSensors()
        loadPlaylist()
        setupListeners()
        setupImportAudioButton()
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
        btnOpenNetease = findViewById(R.id.btnOpenNetease)

        FloatingBack.install(this)
    }

    private fun initSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun loadPlaylist() {
        val initialBookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        val allBooks = databaseHelper.getBooks()
        playlist = allBooks.filter { it.mediaType == MediaType.MUSIC }
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
        CoverImageHelper.loadCoverBitmap(this, track.coverUrl) { bmp ->
            vinylTurntableView.coverBitmap = bmp
        }
    }

    private fun setupListeners() {
        // 选曲歌单列表
        val btnTrackList = findViewById<TextView>(R.id.btnTrackList)
        btnTrackList?.setOnClickListener {
            val allMusic = databaseHelper.getBooks().filter { it.mediaType == MediaType.MUSIC }.ifEmpty { databaseHelper.getBooks() }
            com.example.readtrace.ui.bottomsheet.WorkPickerBottomSheet.show(
                fragmentManager = supportFragmentManager,
                title = "💿 选择播放音乐与原声",
                works = allMusic,
                selectedWorkId = playlist.getOrNull(currentIndex)?.id,
                onSelected = { track ->
                    playlist = allMusic
                    val idx = playlist.indexOfFirst { it.id == track.id }
                    if (idx != -1) {
                        currentIndex = idx
                        renderCurrentTrack()
                        if (!isPlaying) {
                            togglePlayState()
                        } else {
                            if (isCassetteMode) {
                                cassetteDeckView.togglePlay(true)
                            } else {
                                vinylTurntableView.togglePlay(true)
                            }
                        }
                        Toast.makeText(this, "正在播放: 《${track.title}》", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
        if (btnTrackList != null) {
            ViewAnimationHelper.attachSpringTouch(btnTrackList)
        }

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
            if (currentAudioTracks.size > 1 && currentAudioIndex > 0) {
                playAudioAt(currentAudioIndex - 1)
            } else {
                currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
                renderCurrentTrack()
                if (isPlaying) startPlaybackOfCurrentWork(0)
            }
        }

        btnNextTrack.setOnClickListener {
            triggerHapticClick()
            if (currentAudioIndex < currentAudioTracks.size - 1) {
                playAudioAt(currentAudioIndex + 1)
            } else {
                currentIndex = (currentIndex + 1) % playlist.size
                renderCurrentTrack()
                if (isPlaying) startPlaybackOfCurrentWork(0)
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

        // 外部真实播放：拉起网易云音乐搜索当前曲目（无版权音频内置，跳转外部播放）
        btnOpenNetease.setOnClickListener {
            triggerHapticClick()
            openInNeteaseMusic()
        }

        // 进度拖动
        playerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && totalSecondsMs > 0) {
                    tvCurrentTime.text = formatMs((totalSecondsMs * progress / 100f).toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                wasPlayingBeforeSeek = mediaPlayer?.isPlaying == true
                mediaPlayer?.pause()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (totalSecondsMs > 0) {
                    val targetMs = (totalSecondsMs * (seekBar?.progress ?: 0) / 100f).toLong()
                    mediaPlayer?.seekTo(targetMs.toInt())
                }
                if (wasPlayingBeforeSeek) mediaPlayer?.start()
            }
        })
    }

    private fun openInNeteaseMusic() {
        if (playlist.isEmpty()) {
            Toast.makeText(this, "暂无曲目可跳转", Toast.LENGTH_SHORT).show()
            return
        }
        val track = playlist[currentIndex]
        // 去除括号注音（如 晴る (Haru) → 晴る）后拼接歌手作为搜索词，命中率更高
        val cleanTitle = track.title.replace(Regex("[（(].*?[)）]"), "").trim()
        val query = if (track.author.isNullOrBlank()) cleanTitle else "$cleanTitle ${track.author}"
        val searchUrl = "https://music.163.com/#/search/s/?s=${java.net.URLEncoder.encode(query, "UTF-8")}&type=1"
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(searchUrl)))
        }.onFailure {
            Toast.makeText(this, "无法打开网易云音乐，请手动搜索《$cleanTitle》", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupImportAudioButton() {
        findViewById<TextView>(R.id.btnImportAudio)?.setOnClickListener {
            triggerHapticClick()
            com.example.readtrace.util.ElegantChoiceDialog.show(
                this,
                title = "🎵 添加音频到当前作品",
                choices = listOf(
                    com.example.readtrace.util.ElegantChoiceDialog.Choice(
                        "添加在线音频链接",
                        "粘贴音频直链 URL（网盘 / 自建服务器 / NAS）",
                        "🌐",
                    ),
                    com.example.readtrace.util.ElegantChoiceDialog.Choice(
                        "导入本地音频文件",
                        "从手机存储选择 mp3 / flac / wav / m4a",
                        "📁",
                    ),
                ),
            ) { which ->
                if (which == 0) showOnlineUrlDialog() else openAudioFilePicker()
            }
        }
    }

    /** 在线音频：关联直链 URL，联网即流式播放 */
    private fun showOnlineUrlDialog() {
        com.example.readtrace.util.ElegantFormDialog.show(
            this,
            title = "🌐 添加在线音频",
            confirmText = "保存并播放",
            fields = listOf(
                ElegantFormDialog.Field("url", "🔗 音频直链 URL", "http(s):// 开头的音频文件地址", required = true),
                ElegantFormDialog.Field("name", "🎵 曲目名称 (选填)", "如：夜航星"),
            ),
        ) { v ->
            val url = v.getValue("url").trim()
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                Toast.makeText(this, "URL 需以 http(s):// 开头", Toast.LENGTH_SHORT).show()
                showOnlineUrlDialog()
                return@show
            }
            val work = playlist.getOrNull(currentIndex) ?: return@show
            val name = v.getValue("name").trim().ifBlank { url.substringAfterLast('/') }
            val order = databaseHelper.getAudioTracks(work.id).size
            databaseHelper.insertAudioTrack(
                com.example.readtrace.model.AudioTrackItem(
                    bookId = work.id,
                    trackOrder = order,
                    title = name,
                    fileUri = url,
                ),
            )
            currentAudioTracks = databaseHelper.getAudioTracks(work.id)
            Toast.makeText(this, "已添加《$name》，缓冲后开始播放", Toast.LENGTH_SHORT).show()
            isPlaying = true
            playAudioAt(order.coerceAtMost(currentAudioTracks.lastIndex))
        }
    }

    private fun setPlayingUi(playing: Boolean) {
        if (playing) {
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
        } else {
            com.example.readtrace.util.HapticFeedbackEngine.lightClick(this)
            tvPlayPauseLabel.text = "▶ 开始放唱"
            vinylTurntableView.togglePlay(false)
            cassetteDeckView.togglePlay(false)
            particleBackgroundView.setPlaying(false)
            com.example.readtrace.util.AudioReactiveAuroraEngine.stopAudioSync()
        }
    }

    private fun togglePlayState() {
        if (mediaPlayer != null && currentAudioTracks.isNotEmpty()) {
            val mp = mediaPlayer!!
            isPlaying = if (mp.isPlaying) {
                mp.pause()
                false
            } else {
                mp.start()
                requestAudioFocus()
                handler.post(playRunnable)
                true
            }
            setPlayingUi(isPlaying)
        } else {
            // 尚未加载本地音频：加载当前作品曲目（无曲目时自动弹文件选择器）
            isPlaying = true
            startPlaybackOfCurrentWork(0)
        }
    }

    /** 加载当前音乐作品的本地曲目并从 fromIndex 开始播放；无曲目时自动弹导入 */
    private fun startPlaybackOfCurrentWork(fromIndex: Int) {
        val work = playlist.getOrNull(currentIndex) ?: return
        val tracks = databaseHelper.getAudioTracks(work.id)
        if (tracks.isEmpty()) {
            Toast.makeText(this, "《${work.title}》还未关联音频，选择本地文件导入", Toast.LENGTH_LONG).show()
            openAudioFilePicker()
            return
        }
        currentAudioTracks = tracks
        playAudioAt(fromIndex.coerceIn(0, tracks.lastIndex))
    }

    private fun playAudioAt(index: Int) {
        val track = currentAudioTracks.getOrNull(index) ?: return
        currentAudioIndex = index
        releaseMediaPlayer()

        if (!requestAudioFocus()) {
            Toast.makeText(this, "未能获取音频焦点，可能有其他应用正在播放", Toast.LENGTH_SHORT).show()
        }

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            setDataSource(this@VinylCassettePlayerActivity, Uri.parse(track.fileUri))
            setOnPreparedListener { mp ->
                totalSecondsMs = mp.duration.toLong()
                if (track.durationMs <= 0) {
                    databaseHelper.updateAudioTrackDuration(track.id, totalSecondsMs)
                }
                updatePlaybackProgress()
                if (isPlaying) {
                    mp.start()
                    setPlayingUi(true)
                    handler.post(playRunnable)
                } else {
                    setPlayingUi(false)
                }
            }
            setOnCompletionListener {
                playNextAuto()
            }
            setOnErrorListener { _, what, extra ->
                Toast.makeText(this@VinylCassettePlayerActivity, "播放出错 (code $what/$extra)，文件可能已失效", Toast.LENGTH_LONG).show()
                true
            }
            prepareAsync()
            tvPlayPauseLabel.text = "⏳ 缓冲中..."
        }

        if (!isPlaying) {
            isPlaying = true
            setPlayingUi(true)
        }
        // 曲目信息联动
        tvTrackArtistInfo.text = "—— 正在播放 ${index + 1}/${currentAudioTracks.size} · ${track.title}"
    }

    /** 单部作品曲目播完：自动切下一部音乐作品并续播 */
    private fun playNextAuto() {
        currentIndex = (currentIndex + 1) % playlist.size
        renderCurrentTrack()
        startPlaybackOfCurrentWork(0)
    }

    private fun openAudioFilePicker() {
        runCatching {
            importAudioLauncher.launch(arrayOf("audio/*", "application/ogg"))
        }.onFailure {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importAudioFiles(uris: List<Uri>) {
        if (playlist.isEmpty()) return
        val work = playlist[currentIndex]
        var order = databaseHelper.getAudioTracks(work.id).size
        val startIndex = order
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                )
            }
            val name = queryAudioDisplayName(uri) ?: "曲目 ${order + 1}"
            databaseHelper.insertAudioTrack(
                com.example.readtrace.model.AudioTrackItem(
                    bookId = work.id,
                    trackOrder = order,
                    title = name,
                    fileUri = uri.toString(),
                ),
            )
            order++
        }
        Toast.makeText(this, "已导入 ${uris.size} 首曲目到《${work.title}》", Toast.LENGTH_SHORT).show()
        // 导入后自动开始播放新导入的第一首
        currentAudioTracks = databaseHelper.getAudioTracks(work.id)
        isPlaying = true
        playAudioAt(startIndex.coerceAtMost(currentAudioTracks.lastIndex))
    }

    private fun queryAudioDisplayName(uri: Uri): String? {
        return runCatching {
            var name: String? = null
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(idx)
                }
            }
            name?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun requestAudioFocus(): Boolean {
        val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .build()
            .also { audioFocusRequest = it }
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.run {
            runCatching { stop() }
            runCatching { release() }
        }
        mediaPlayer = null
        handler.removeCallbacks(playRunnable)
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        return String.format("%02d:%02d", totalSec / 60, totalSec % 60)
    }

    private fun updatePlaybackProgress() {
        val curMs = (mediaPlayer?.currentPosition ?: 0).toLong()
        val totalMs = if (totalSecondsMs > 0) totalSecondsMs else 1L
        tvCurrentTime.text = formatMs(curMs)
        tvTotalTime.text = formatMs(totalMs)
        val prog = (curMs * 100f / totalMs).toInt().coerceIn(0, 100)
        playerSeekBar.progress = prog
        cassetteDeckView.progress = prog / 100f
    }

    private fun triggerHapticClick() {
        com.example.readtrace.util.HapticFeedbackEngine.lightClick(this)
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
        releaseMediaPlayer()
        abandonAudioFocus()
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

                vinylTurntableView.gyroOffsetX = (roll / 45f).coerceIn(-1f, 1f)
                vinylTurntableView.gyroOffsetY = (pitch / 45f).coerceIn(-1f, 1f)
            }
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
