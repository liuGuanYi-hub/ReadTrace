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
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
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

    // ===== 网易云 15s 试听状态 =====
    private var previewActive = false
    private var previewElapsedMs = 0L
    private var previewResumeAtMs = 0L
    private var isFetchingPreview = false
    private var previewRetryAfterError = false

    private val previewStopRunnable = Runnable {
        if (!previewActive) return@Runnable
        previewActive = false
        mediaPlayer?.runCatching { if (isPlaying) pause() }
        isPlaying = false
        setPlayingUi(false)
        val track = currentAudioTracks.getOrNull(currentAudioIndex)
        val secs = if (track != null && isVipPreviewTrack(track)) 30 else 15
        Toast.makeText(this, "💽 $secs 秒试听结束，唱针归位", Toast.LENGTH_SHORT).show()
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
                        val keepPlaying = isPlaying
                        currentIndex = idx
                        renderCurrentTrack()
                        switchWork(keepPlaying)
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

        // 上一曲 / 下一曲：切到新作品时立即释放旧音源，避免继续播放上一首
        btnPrevTrack.setOnClickListener {
            triggerHapticClick()
            currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
            renderCurrentTrack()
            switchWork(isPlaying)
        }

        btnNextTrack.setOnClickListener {
            triggerHapticClick()
            currentIndex = (currentIndex + 1) % playlist.size
            renderCurrentTrack()
            switchWork(isPlaying)
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
        // 长按绑定网易云会员 Cookie：VIP 曲目可直接取完整直链持续播放
        btnOpenNetease.setOnLongClickListener {
            triggerHapticClick()
            showVipBindingDialog()
            true
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
                pausePreviewTimer()
                false
            } else {
                mp.start()
                requestAudioFocus()
                handler.post(playRunnable)
                resumePreviewTimerIfAny()
                true
            }
            setPlayingUi(isPlaying)
        } else {
            // 尚未加载音频：加载当前作品曲目（无曲目时自动联网取试听）
            isPlaying = true
            startPlaybackOfCurrentWork(0)
        }
    }

    /** 加载当前音乐作品的曲目并从 fromIndex 开始播放；无曲目时自动联网检索试听源 */
    private fun startPlaybackOfCurrentWork(fromIndex: Int) {
        val work = playlist.getOrNull(currentIndex) ?: return
        val tracks = databaseHelper.getAudioTracks(work.id)
        if (tracks.isEmpty()) {
            fetchNeteasePreview(work)
            return
        }
        currentAudioTracks = tracks
        playAudioAt(fromIndex.coerceIn(0, tracks.lastIndex))
    }

    /**
     * 切换到新作品：立即释放旧音源并重置进度，避免切歌后仍播放上一首；
     * 若切歌前处于播放状态，则自动开始检索播放新作品。
     */
    private fun switchWork(autoPlay: Boolean) {
        releaseMediaPlayer()
        currentAudioTracks = emptyList()
        currentAudioIndex = 0
        totalSecondsMs = 0L
        isPlaying = autoPlay
        setPlayingUi(false)
        tvPlayPauseLabel.text = "▶ 开始放唱"
        tvCurrentTime.text = formatMs(0)
        tvTotalTime.text = formatMs(0)
        playerSeekBar.progress = 0
        cassetteDeckView.progress = 0f
        if (autoPlay) startPlaybackOfCurrentWork(0)
    }

    /** 自动联网检索对应歌曲的可播放试听源（网易云，会员歌自动转酷狗兜底；绑定会员 Cookie 后 VIP 曲可完整播放） */
    private fun fetchNeteasePreview(work: Book) {
        if (isFetchingPreview) return
        isFetchingPreview = true
        tvPlayPauseLabel.text = "⏳ 取曲中..."
        Toast.makeText(this, "正在为《${work.title}》联网检索试听片段...", Toast.LENGTH_SHORT).show()
        com.example.readtrace.util.NeteasePreviewHelper.fetchPlayablePreview(this, work.title, work.author) { result ->
            isFetchingPreview = false
            if (isDestroyed) return@fetchPlayablePreview
            if (result == null) {
                Toast.makeText(this, "《${work.title}》暂无可播放试听源", Toast.LENGTH_LONG).show()
                tvPlayPauseLabel.text = "▶ 开始放唱"
                isPlaying = false
                setPlayingUi(false)
                return@fetchPlayablePreview
            }
            val order = databaseHelper.getAudioTracks(work.id).size
            val suffix = when {
                result.isFullSong -> "完整播放"
                result.isVip -> "30s VIP 试听"
                else -> "15s 试听"
            }
            databaseHelper.insertAudioTrack(
                com.example.readtrace.model.AudioTrackItem(
                    bookId = work.id,
                    trackOrder = order,
                    title = "${result.songName} · $suffix",
                    fileUri = result.streamUrl,
                ),
            )
            currentAudioTracks = databaseHelper.getAudioTracks(work.id)
            playAudioAt(currentAudioTracks.lastIndex)
        }
    }

    /**
     * 长按「去网易云播放」：绑定/解除网易云会员 Cookie（MUSIC_U）。
     * 绑定后会员曲目可直接取完整直链持续播放；Cookie 仅存应用私有目录，不上传不落日志。
     */
    private fun showVipBindingDialog() {
        val helper = com.example.readtrace.util.NeteasePreviewHelper
        val current = helper.getMusicUCookie(this)
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(current)
            hint = "粘贴 MUSIC_U 的值"
            setSingleLine(true)
        }
        val bound = !current.isNullOrBlank()
        AlertDialog.Builder(this)
            .setTitle(if (bound) "🎵 会员已绑定（长按管理）" else "🎵 绑定网易云会员")
            .setMessage(
                "电脑浏览器登录 music.163.com → F12 打开开发者工具 → Application → Cookies → "
                    + "复制 MUSIC_U 的值粘贴到下面。\n绑定后 VIP 曲目可直接完整播放，不再限 15s/30s 试听。\n"
                    + "Cookie 只保存在本机应用私有目录，不会上传或写日志。\n清空内容点保存即解除绑定。"
            )
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isEmpty()) {
                    helper.setMusicUCookie(this, null)
                    Toast.makeText(this, "已解除会员绑定", Toast.LENGTH_SHORT).show()
                } else {
                    helper.setMusicUCookie(this, value)
                    Toast.makeText(this, "会员绑定成功，VIP 曲目将完整播放 🎧", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 曲目是否为在线试听（外链带时间戳，失效后应删除重取） */
    private fun isNeteasePreviewTrack(track: com.example.readtrace.model.AudioTrackItem): Boolean {
        return track.title.endsWith("15s 试听") || track.title.endsWith("30s VIP 试听")
    }

    /** 曲目是否为会员歌兜底试听（限播 30 秒） */
    private fun isVipPreviewTrack(track: com.example.readtrace.model.AudioTrackItem): Boolean {
        return track.title.endsWith("30s VIP 试听")
    }

    private fun previewLimitFor(track: com.example.readtrace.model.AudioTrackItem): Long {
        return if (isVipPreviewTrack(track)) PREVIEW_LIMIT_VIP_MS else PREVIEW_LIMIT_MS
    }

    private fun startPreviewTimer() {
        val track = currentAudioTracks.getOrNull(currentAudioIndex) ?: return
        previewActive = true
        previewResumeAtMs = SystemClock.elapsedRealtime()
        val remaining = (previewLimitFor(track) - previewElapsedMs).coerceAtLeast(0L)
        handler.removeCallbacks(previewStopRunnable)
        handler.postDelayed(previewStopRunnable, remaining)
    }

    private fun pausePreviewTimer() {
        if (previewActive) {
            previewElapsedMs += SystemClock.elapsedRealtime() - previewResumeAtMs
            handler.removeCallbacks(previewStopRunnable)
        }
    }

    /** 试听时间耗尽后再按播放：从头重放本曲的试听片段 */
    private fun resumePreviewTimerIfAny() {
        val track = currentAudioTracks.getOrNull(currentAudioIndex) ?: return
        if (!isNeteasePreviewTrack(track)) return
        if (previewElapsedMs >= previewLimitFor(track)) {
            previewElapsedMs = 0L
            mediaPlayer?.seekTo(0)
        }
        startPreviewTimer()
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
                    if (isNeteasePreviewTrack(track)) {
                        previewElapsedMs = 0L
                        startPreviewTimer()
                    }
                } else {
                    setPlayingUi(false)
                }
            }
            setOnCompletionListener {
                playNextAuto()
            }
            setOnErrorListener { _, what, extra ->
                Toast.makeText(this@VinylCassettePlayerActivity, "播放出错 (code $what/$extra)，文件可能已失效", Toast.LENGTH_LONG).show()
                // 试听外链带时间戳会过期失效：自动移除旧链接并重新联网取试听，避免反复报错
                if (isNeteasePreviewTrack(track)) {
                    databaseHelper.deleteAudioTrack(track.id)
                    val work = playlist.getOrNull(currentIndex)
                    if (work != null) fetchNeteasePreview(work)
                }
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
        handler.removeCallbacks(previewStopRunnable)
        previewActive = false
        previewElapsedMs = 0L
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
        handler.removeCallbacks(previewStopRunnable)
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

        /** 免费曲目试听片段的限播时长（约 15 秒） */
        const val PREVIEW_LIMIT_MS = 15_000L

        /** 会员曲目兜底试听片段的限播时长（约 30 秒） */
        const val PREVIEW_LIMIT_VIP_MS = 30_000L

        fun createIntent(context: Context, bookId: Long): Intent {
            return Intent(context, VinylCassettePlayerActivity::class.java).apply {
                putExtra(EXTRA_BOOK_ID, bookId)
            }
        }
    }
}
