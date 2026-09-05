package com.example.readtrace

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.ui.bottomsheet.CloudMusicPickerBottomSheet
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
    private var sessionStartTimeMs: Long = SystemClock.elapsedRealtime()

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
    private lateinit var btnOpenNetease: TextView
    private lateinit var btnCloudPlaylist: TextView

    // 网易云我的歌单播放态（有值时上一曲/下一曲/连播都在歌单内进行）
    private var cloudTracks: List<com.example.readtrace.util.NeteasePreviewHelper.PlaylistTrack> = emptyList()
    private var cloudIndex = -1
    private var cloudPlaylistName: String? = null

    // insets 幂等基准：首次分发时记录 XML 初始 padding，此后不再累加
    private var hudInitialPaddingTop = -1
    private var hudInitialPaddingBottom = -1

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

    // ===== 音频焦点与拔耳机外放防护 =====
    /** 瞬时焦点丢失（来电/导航）时是否在焦点恢复后自动续播 */
    private var focusResumeOnGain = false

    /** 当前是否处于闪避压低音量状态（可闪避焦点丢失） */
    private var duckedVolume = false

    /** BECOMING_NOISY 接收器是否已注册（随播放态启停，覆盖最小化到后台的全局播放期间） */
    private var noisyReceiverRegistered = false

    // ===== 播放准备态与失败恢复 =====
    /** prepareAsync 进行中；releaseMediaPlayer 与超时兜底据此判断是否存在非法状态调用 */
    private var isPreparing = false

    /** 正在准备的云歌单曲目（null 表示本地曲路径），供 prepare 超时兜底区分恢复分支 */
    private var preparingCloudTrack: com.example.readtrace.util.NeteasePreviewHelper.PlaylistTrack? = null

    /** 同一次云播失败后是否已自动重新取链重试过（直链现取现用，允许一次重试） */
    private var cloudRetryUsed = false

    /** prepare 15 秒超时兜底：弱网下避免永久「缓冲中」，用户无从区分卡住与失败 */
    private val prepareTimeoutRunnable = Runnable {
        if (!isPreparing) return@Runnable
        isPreparing = false
        val cloudTrack = preparingCloudTrack
        if (cloudTrack != null) {
            handleCloudPlaybackFailure(cloudTrack, "⏳ 音源准备超时，正在重新取链…")
            return@Runnable
        }
        // 本地曲路径也可能是过期的在线缓存直链（含「完整播放」）：自动删链重取，避免死循环缓冲
        val staleTrack = currentAudioTracks.getOrNull(currentAudioIndex)
        if (staleTrack != null && isOnlineCachedTrack(staleTrack)) {
            databaseHelper.deleteAudioTrack(staleTrack.id)
            releaseMediaPlayer()
            val work = playlist.getOrNull(currentIndex)
            if (work != null) {
                Toast.makeText(this, "⏳ 直链已过期，正在重新取链…", Toast.LENGTH_SHORT).show()
                fetchNeteasePreview(work)
                return@Runnable
            }
        }
        releaseMediaPlayer()
        isPlaying = false
        setPlayingUi(false)
        Toast.makeText(this, "⏳ 音源准备超时，请点击重试", Toast.LENGTH_LONG).show()
    }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContentView(R.layout.activity_vinyl_cassette_player)

        // targetSdk 35+ 默认启用预测性返回，override onBackPressed 不再被调用；
        // 必须走 OnBackPressedDispatcher 才能拦截返回键实现「退后台保活」
        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    minimizeOrFinish()
                }
            },
        )

        // 系统栏避让统一由内容层按 WindowInsets 处理；粒子背景保持全屏沉浸。
        // insets 回调会被多次触发（重新 attach / 配置变化），必须以 XML 初始 padding 为基准
        // 幂等计算，否则 paddingBottom 会在每次分发时单调累加，把底部控制卡片顶出屏幕。
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.vinylPlayerHudRoot)) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            if (hudInitialPaddingTop < 0) {
                hudInitialPaddingTop = view.paddingTop
                hudInitialPaddingBottom = view.paddingBottom
            }
            view.setPadding(
                systemBars.left,
                hudInitialPaddingTop + systemBars.top,
                systemBars.right,
                hudInitialPaddingBottom + systemBars.bottom,
            )
            insets
        }

        databaseHelper = BookDatabaseHelper.getInstance(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initViews()
        initSensors()
        loadPlaylist()
        setupListeners()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // launchMode=singleTask 复用存活实例时，外部「去唱机」点的新作品经此投递（onCreate 不会重走），
        // 不接住会被静默忽略、旧实例继续播原曲；主页悬浮胶囊的跳回 intent 不带 extra，直接忽略。
        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        if (bookId == -1L) return
        val allBooks = databaseHelper.getBooks()
        val newPlaylist = allBooks.filter { it.mediaType == MediaType.MUSIC }.ifEmpty { allBooks }
        val idx = newPlaylist.indexOfFirst { it.id == bookId }
        if (idx == -1 || newPlaylist.getOrNull(currentIndex)?.id == bookId) return
        // 明确切到本地藏库作品：退出云队列接管，上一曲/下一曲与播放键恢复本地语义
        cloudTracks = emptyList()
        cloudIndex = -1
        cloudRetryUsed = false
        playlist = newPlaylist
        currentIndex = idx
        renderCurrentTrack()
        val keepPlaying = isPlaying
        switchWork(keepPlaying)
        newPlaylist.getOrNull(idx)?.let {
            Toast.makeText(this, "正在播放: 《${it.title}》", Toast.LENGTH_SHORT).show()
        }
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
        btnOpenNetease = findViewById(R.id.btnOpenNetease)
        btnCloudPlaylist = findViewById(R.id.btnCloudPlaylist)


        FloatingBack.install(this) { minimizeOrFinish() }
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
        // 选曲歌单列表：云歌单播放中展示当前队列（▶ 标记当前曲，点击跳转）；否则展示藏库作品选择
        val btnTrackList = findViewById<TextView>(R.id.btnTrackList)
        btnTrackList?.setOnClickListener {
            if (cloudTracks.isNotEmpty()) {
                val items = cloudTracks.mapIndexed { i, t ->
                    CloudMusicPickerBottomSheet.PickerItem(
                        id = t.id,
                        title = t.name,
                        subtitle = "${i + 1}. ${t.artists.ifBlank { "未知歌手" }}",
                        emoji = if (i == cloudIndex) "▶️" else if (i % 2 == 0) "🎵" else "💿",
                    )
                }
                CloudMusicPickerBottomSheet.show(
                    fragmentManager = supportFragmentManager,
                    title = "🎶 当前播放队列（${cloudIndex + 1}/${cloudTracks.size}）",
                    items = items,
                    onSelected = { which ->
                        cloudIndex = which
                        playCloudTrack()
                    },
                )
                return@setOnClickListener
            }
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
            // 云歌单模式：在歌单内前后切歌
            if (cloudTracks.isNotEmpty()) {
                cloudIndex = if (cloudIndex - 1 < 0) cloudTracks.size - 1 else cloudIndex - 1
                playCloudTrack()
                return@setOnClickListener
            }
            currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
            renderCurrentTrack()
            switchWork(isPlaying)
        }

        btnNextTrack.setOnClickListener {
            triggerHapticClick()
            if (cloudTracks.isNotEmpty()) {
                cloudIndex = (cloudIndex + 1) % cloudTracks.size
                playCloudTrack()
                return@setOnClickListener
            }
            currentIndex = (currentIndex + 1) % playlist.size
            renderCurrentTrack()
            switchWork(isPlaying)
        }

        // 33 RPM / 45 RPM 转速切换
        btnSpeedToggle.setOnClickListener {
            triggerHapticClick()
            val is33 = btnSpeedToggle.text.contains("33")
            btnSpeedToggle.text = if (is33) "45" else "33"
            Toast.makeText(this, if (is33) "切换至 45 RPM 典藏高保真转速" else "切换至 33 1/3 RPM 标准密纹转速", Toast.LENGTH_SHORT).show()
        }

        // 我的歌单：绑定会员 Cookie 后直接播自己歌单里的完整曲目
        btnCloudPlaylist.setOnClickListener {
            triggerHapticClick()
            showCloudPlaylistPicker()
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
                // Preparing 态下调 pause()/seekTo() 是非法状态调用（IllegalStateException），
                // 缓冲未完成或实例已释放时忽略本次拖动
                if (isPreparing || mediaPlayer == null) return
                wasPlayingBeforeSeek = mediaPlayer?.isPlaying == true
                mediaPlayer?.pause()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (isPreparing || mediaPlayer == null) return
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
        // 全域播放態標記：其他頁面據此顯示「返回唱機」懸浮膠囊
        isEnginePlaying = playing
        // 拔耳机暂停接收器随播放态启停（含最小化到后台的全局播放期间）
        setNoisyReceiverActive(playing)
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
        } else if (cloudTracks.isNotEmpty()) {
            // 云歌单模式尚未加载音源（或取链失败后重试）
            isPlaying = true
            playCloudTrack()
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
        // 网易云歌单导入的作品自带曲目 ID：优先按 ID 直取链，跳过搜索与相关性过滤，
        // 且清洗掉标题尾注（如《One Last Kiss (cover 宇多田ヒカル)》的括号），避免匹配被拒导致永久缓冲
        if (work.sourceType == "netease" && !work.sourceId.isNullOrBlank()) {
            val directId = work.sourceId.toLongOrNull() ?: -1L
            if (directId > 0) {
                val cleanTitle = work.title.replace(Regex("[（(].*?[)）]"), "").trim()
                val directTrack = com.example.readtrace.util.NeteasePreviewHelper.PlaylistTrack(
                    id = directId,
                    name = cleanTitle,
                    artists = work.author ?: "",
                    album = "",
                )
                com.example.readtrace.util.NeteasePreviewHelper.fetchTrackStreamResult(this, directTrack) { result ->
                    isFetchingPreview = false
                    if (isDestroyed) return@fetchTrackStreamResult
                    if (result != null && result.streamUrl.isNotBlank()) {
                        applyFetchedPreview(work, result)
                        return@fetchTrackStreamResult
                    }
                    // 按 ID 直取失败（版权变动/未绑 Cookie）：回退搜索匹配路径
                    performNeteasePreviewSearch(work)
                }
                return
            }
        }
        performNeteasePreviewSearch(work)
    }

    /** 搜索匹配路径：按「曲名+歌手」搜网易云曲库取可播放源（导入歌 ID 直取失败时的兑底） */
    private fun performNeteasePreviewSearch(work: Book) {
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
            applyFetchedPreview(work, result)
        }
    }

    /** 取链成功落库并续播：完整播放/30s VIP/15s 试听分类标记，供限播与过期重取识别 */
    private fun applyFetchedPreview(
        work: Book,
        result: com.example.readtrace.util.NeteasePreviewHelper.PreviewResult,
    ) {
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

    // ------------------------------------------------------------ 网易云「我的歌单」播放

    /** 选择自创歌单 → 选择曲目 → 在唱机内完整播放，支持上一曲/下一曲与自动连播 */
    private fun showCloudPlaylistPicker() {
        val helper = com.example.readtrace.util.NeteasePreviewHelper
        if (!helper.isBound(this)) {
            Toast.makeText(this, "请先绑定网易云会员 Cookie（长按右侧搜索键）", Toast.LENGTH_LONG).show()
            showVipBindingDialog()
            return
        }
        Toast.makeText(this, "正在载入我的歌单...", Toast.LENGTH_SHORT).show()
        helper.fetchUserPlaylists(this) { playlists ->
            if (isDestroyed) return@fetchUserPlaylists
            if (playlists.isNullOrEmpty()) {
                Toast.makeText(this, "未读到自创歌单，请确认 Cookie 有效或已重新登录", Toast.LENGTH_LONG).show()
                return@fetchUserPlaylists
            }
            val items = playlists.mapIndexed { index, pl ->
                CloudMusicPickerBottomSheet.PickerItem(
                    id = pl.id,
                    title = pl.name,
                    subtitle = "${pl.trackCount} 首 · 歌单 #${index + 1}",
                    emoji = "☁️",
                )
            }
            CloudMusicPickerBottomSheet.show(
                fragmentManager = supportFragmentManager,
                title = "☁️ 我的歌单（${playlists.size}）",
                items = items,
                onSelected = { which ->
                    val pl = playlists[which]
                    // App 风格深夜选片器替代系统 AlertDialog：与唱机页氛围统一
                    CloudMusicPickerBottomSheet.show(
                        fragmentManager = supportFragmentManager,
                        title = "☁️ ${pl.name} · ${pl.trackCount} 首",
                        items = listOf(
                            CloudMusicPickerBottomSheet.PickerItem(id = 0, title = "在唱机播放", subtitle = "加载歌单内全部曲目连续播放", emoji = "▶️"),
                            CloudMusicPickerBottomSheet.PickerItem(id = 1, title = "选择曲目导入藏品", subtitle = "勾选心仪曲目收藏入藏库", emoji = "📥"),
                        ),
                        onSelected = { which2 ->
                            if (which2 == 0) loadCloudTrackList(pl) else importCloudPlaylistToLibrary(pl)
                        },
                    )
                },
            )
        }
    }

    /** 选择歌单曲目导入为音乐藏品：弹出多选清单（默认全不选），只导入勾选曲目；按网易云曲目 ID 去重，只追加新歌，绝不覆盖已入库条目 */
    private fun importCloudPlaylistToLibrary(playlist: com.example.readtrace.util.NeteasePreviewHelper.UserPlaylist) {
        Toast.makeText(this, "正在读取《${playlist.name}》曲目...", Toast.LENGTH_SHORT).show()
        com.example.readtrace.util.NeteasePreviewHelper.fetchPlaylistTracks(this, playlist.id) { tracks ->
            if (isDestroyed) return@fetchPlaylistTracks
            if (tracks.isNullOrEmpty()) {
                Toast.makeText(this, "《${playlist.name}》没有可导入的曲目", Toast.LENGTH_LONG).show()
                return@fetchPlaylistTracks
            }
            val names = tracks.map { it.name }.toTypedArray()
            val checked = BooleanArray(tracks.size)
            // Material 对话框才会读取 M3 colorSurfaceContainer 色牌（AppCompat 版不认，弹白底系统框）
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.Theme_ReadTrace_PlayerAlertDialog)
                .setTitle("📥 选择要导入的曲目（共 ${tracks.size} 首）")
                .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setPositiveButton("导入所选") { _, _ ->
                    val picked = tracks.filterIndexed { index, _ -> checked[index] }
                    if (picked.isEmpty()) {
                        Toast.makeText(this, "未勾选任何曲目", Toast.LENGTH_SHORT).show()
                    } else {
                        importPickedTracksToLibrary(playlist, picked)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun importPickedTracksToLibrary(
        playlist: com.example.readtrace.util.NeteasePreviewHelper.UserPlaylist,
        picked: List<com.example.readtrace.util.NeteasePreviewHelper.PlaylistTrack>,
    ) {
        Thread {
            val fresh = mutableListOf<com.example.readtrace.model.Book>()
            picked.forEach { t ->
                if (t.name.isNotBlank() &&
                    databaseHelper.findBookBySource("netease", t.id.toString()) == null
                ) {
                    fresh += com.example.readtrace.model.Book(
                        title = t.name,
                        author = t.artists.ifBlank { null },
                        category = "网易云歌单 · ${playlist.name}",
                        status = com.example.readtrace.model.BookStatus.FINISHED,
                        mediaType = com.example.readtrace.model.MediaType.MUSIC,
                        sourceType = "netease",
                        sourceId = t.id.toString(),
                    )
                }
            }
            val inserted = if (fresh.isEmpty()) 0 else databaseHelper.insertBooksBatch(fresh)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val skipped = picked.size - fresh.size
                Toast.makeText(
                    this,
                    "📥 已导入勾选曲目：新增 $inserted / 勾选 ${picked.size} 首" +
                        if (skipped > 0) "（已在藏库，跳过 $skipped 首）" else "",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }.start()
    }

    /** 云歌单曲目无本地短评：羊皮纸笺轮换的星空系占位金句 */
    private val CLOUD_LYRIC_PLACEHOLDERS = listOf(
        "“把耳朵交给夜色，让星星替我们记住这段旋律。”",
        "“银河不语，音符自有回声。”",
        "“在云端的每一拍，都是夜空写给我们的信。”",
        "“唱针落下，星光亮起。”",
        "“旋律穿过大气层，落在今夜的窗台。”",
    )

    private var starfieldCoverCache: android.graphics.Bitmap? = null

    /**
     * 星空占位封面：深空径向渐变 + 随机星点与光晕，契合唱机页深夜氛围；
     * 固定随机种子保证同一会话内占位图稳定，单例缓存避免重复绘制。
     */
    private fun starfieldCoverBitmap(size: Int = 480): android.graphics.Bitmap {
        starfieldCoverCache?.let { return it }
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.RadialGradient(
            size / 2f, size / 2f, size / 1.4f,
            intArrayOf(0xFF1B2A4A.toInt(), 0xFF0B1026.toInt()),
            floatArrayOf(0f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null
        val rnd = java.util.Random(20260904L)
        repeat(96) {
            val x = rnd.nextFloat() * size
            val y = rnd.nextFloat() * size
            val radius = 0.8f + rnd.nextFloat() * 2.2f
            paint.color = if (rnd.nextInt(6) == 0) 0xFFFFD98A.toInt() else 0xFFF2F6FF.toInt()
            paint.alpha = 120 + rnd.nextInt(135)
            canvas.drawCircle(x, y, radius, paint)
        }
        // 主星与光晕
        paint.color = 0xFF9FC4FF.toInt()
        paint.alpha = 40
        canvas.drawCircle(size * 0.5f, size * 0.42f, size * 0.16f, paint)
        paint.color = 0xFFEAF2FF.toInt()
        paint.alpha = 255
        canvas.drawCircle(size * 0.5f, size * 0.42f, size * 0.035f, paint)
        starfieldCoverCache = bmp
        return bmp
    }

    private fun loadCloudTrackList(playlist: com.example.readtrace.util.NeteasePreviewHelper.UserPlaylist) {
        Toast.makeText(this, "正在载入《${playlist.name}》...", Toast.LENGTH_SHORT).show()
        com.example.readtrace.util.NeteasePreviewHelper.fetchPlaylistTracks(this, playlist.id) { tracks ->
            if (isDestroyed) return@fetchPlaylistTracks
            if (tracks.isNullOrEmpty()) {
                Toast.makeText(this, "《${playlist.name}》没有可读曲目", Toast.LENGTH_LONG).show()
                return@fetchPlaylistTracks
            }
            val items = tracks.mapIndexed { i, t ->
                val artist = t.artists.ifBlank { "" }
                CloudMusicPickerBottomSheet.PickerItem(
                    id = t.id,
                    title = t.name,
                    subtitle = if (artist.isNotBlank()) "${i + 1}. $artist" else "${i + 1}. 未知艺术家",
                    emoji = if (i % 2 == 0) "🎵" else "💿",
                )
            }
            CloudMusicPickerBottomSheet.show(
                fragmentManager = supportFragmentManager,
                title = "《${playlist.name}》共 ${tracks.size} 首",
                items = items,
                onSelected = { which ->
                    cloudTracks = tracks
                    cloudPlaylistName = playlist.name
                    cloudIndex = which
                    playCloudTrack()
                },
            )
        }
    }

    /** 播放当前云歌单曲目（直链现取现用，避免过期） */
    private fun playCloudTrack() {
        val track = cloudTracks.getOrNull(cloudIndex)
        if (track == null) {
            Toast.makeText(this, "歌单曲目已失效，请重新选择", Toast.LENGTH_SHORT).show()
            return
        }
        // 云歌单曲目无本地短评与封面：切换为星空占位，避免残留上一部本地作品的歌词与封面
        tvQuoteLyrics.text = CLOUD_LYRIC_PLACEHOLDERS[
            kotlin.math.abs(track.name.hashCode()) % CLOUD_LYRIC_PLACEHOLDERS.size,
        ]
        vinylTurntableView.coverBitmap = starfieldCoverBitmap()
        // 新的一次用户/连播动作重置自动重试额度
        cloudRetryUsed = false
        releaseMediaPlayer()
        tvPlayPauseLabel.text = "⏳ 取曲中..."
        // 云歌单曲目信息全量联动：磁带/黑胶卡面、顶栏副标题与底部播报统一为当前歌曲，
        // 避免磁带仍残留上一部本地作品的标题（如《不法侵入》）与实际播放内容不一致
        val artist = track.artists.ifBlank { "未知歌手" }
        cassetteDeckView.trackTitle = track.name
        cassetteDeckView.artistName = artist
        vinylTurntableView.trackTitle = track.name
        vinylTurntableView.artistName = artist
        tvPlayerSubtitle.text = "《${track.name}》· $artist"
        tvTrackArtistInfo.text = "—— 正在播放 ${cloudIndex + 1}/${cloudTracks.size} · ${track.name}${if (track.artists.isNotBlank()) " - ${track.artists}" else ""}"
        com.example.readtrace.util.NeteasePreviewHelper.fetchTrackStreamUrl(this, track) { url ->
            if (isDestroyed) return@fetchTrackStreamUrl
            if (url.isNullOrBlank()) {
                Toast.makeText(this, "《${track.name}》暂无可播放源", Toast.LENGTH_SHORT).show()
                tvPlayPauseLabel.text = "▶ 开始放唱"
                setPlayingUi(false)
                return@fetchTrackStreamUrl
            }
            playCloudUrl(url, track)
        }
    }

    private fun playCloudUrl(url: String, track: com.example.readtrace.util.NeteasePreviewHelper.PlaylistTrack) {
        if (!requestAudioFocus()) {
            Toast.makeText(this, "未能获取音频焦点，可能有其他应用正在播放", Toast.LENGTH_SHORT).show()
        }
        // 直链请求头与取链请求保持一致：网易云 CDN 常校验 Referer/UA，裸请求会被 403/302 拒导致 prepare 失败
        val headers = com.example.readtrace.util.NeteasePreviewHelper.buildPlaybackHeaders(this)
        val player = MediaPlayer()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        val dataSourceOk = runCatching {
            player.setDataSource(this@VinylCassettePlayerActivity, Uri.parse(url), headers)
        }.isSuccess
        if (!dataSourceOk) {
            runCatching { player.release() }
            handleCloudPlaybackFailure(track, "播放源无法访问，已尝试重新取链")
            return
        }
        isPreparing = true
        preparingCloudTrack = track
        handler.removeCallbacks(prepareTimeoutRunnable)
        handler.postDelayed(prepareTimeoutRunnable, PREPARE_TIMEOUT_MS)
        player.setOnPreparedListener { mp ->
            isPreparing = false
            preparingCloudTrack = null
            handler.removeCallbacks(prepareTimeoutRunnable)
            totalSecondsMs = mp.duration.toLong()
            updatePlaybackProgress()
            // isPlaying 在 prepared 确认后才置真：prepare 失败时不再残留「假播放」状态误导 UI
            isPlaying = true
            mp.start()
            setPlayingUi(true)
            handler.post(playRunnable)
        }
        player.setOnCompletionListener {
            // 云歌单内自动连播下一首
            if (cloudTracks.isNotEmpty()) {
                cloudIndex = (cloudIndex + 1) % cloudTracks.size
                playCloudTrack()
            } else {
                playNextAuto()
            }
        }
        player.setOnInfoListener { _, what, _ ->
            when (what) {
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> tvPlayPauseLabel.text = "⏳ 缓冲中..."
                MediaPlayer.MEDIA_INFO_BUFFERING_END -> if (isPlaying) tvPlayPauseLabel.text = "⏸ 暂停聆听"
            }
            false
        }
        player.setOnBufferingUpdateListener { _, percent ->
            if (isPreparing) tvPlayPauseLabel.text = "⏳ 缓冲中 $percent%"
        }
        player.setOnErrorListener { _, what, extra ->
            handleCloudPlaybackFailure(track, "播放出错 (code $what/$extra)，正在尝试恢复…")
            true
        }
        mediaPlayer = player
        player.prepareAsync()
        tvPlayPauseLabel.text = "⏳ 缓冲中..."
        // 曲目标题已在 playCloudTrack 统一联动，此处不再覆盖，避免顶栏副标题被歌单名顶掉
    }

    /**
     * 长按「去网易云播放」：绑定/解除网易云会员 Cookie（MUSIC_U）。
     * 绑定后会员曲目可直接取完整直链持续播放；Cookie 仅存应用私有目录，不上传不落日志。
     */
    private fun showVipBindingDialog() {
        val helper = com.example.readtrace.util.NeteasePreviewHelper
        val current = helper.getMusicUCookie(this)
        val bound = !current.isNullOrBlank()

        ElegantFormDialog.show(
            this,
            title = if (bound) "🎵 网易云会员配置" else "🎵 绑定网易云会员",
            confirmText = "保存配置",
            fields = listOf(
                ElegantFormDialog.Field(
                    key = "cookie",
                    label = "🔑 MUSIC_U Cookie 凭据",
                    hint = "粘贴 MUSIC_U 值或整段 Cookie 文本（清空并保存即可解绑）",
                    preset = current.orEmpty(),
                    minLines = 3,
                ),
            ),
        ) { values ->
            val raw = values.getValue("cookie")
            val value = helper.extractMusicU(raw)
            if (value.isNullOrEmpty()) {
                helper.setMusicUCookie(this, null)
                Toast.makeText(this, "已解除会员绑定", Toast.LENGTH_SHORT).show()
            } else {
                helper.setMusicUCookie(this, value)
                Toast.makeText(this, "会员绑定成功，VIP 曲目将完整播放 🎧", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** 曲目是否为在线试听（外链带时间戳，失效后应删除重取） */
    private fun isNeteasePreviewTrack(track: com.example.readtrace.model.AudioTrackItem): Boolean {
        return track.title.endsWith("15s 试听") || track.title.endsWith("30s VIP 试听")
    }

    /** 曲目是否为在线缓存直链（含「完整播放」缓存）：直链现取现用会过期，失效后应删除重取 */
    private fun isOnlineCachedTrack(track: com.example.readtrace.model.AudioTrackItem): Boolean {
        return isNeteasePreviewTrack(track) || track.fileUri.startsWith("http")
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
            // 在线缓存直链（网易云 CDN）重放必须带 Referer/UA（及可选 MUSIC_U）请求头，
            // 与 playCloudUrl 同口径，否则 CDN 校验拒绝 → onError → 删链重取，
            // 缓存命中路径形同虚设；本地文件 URI 不需要请求头
            if (track.fileUri.startsWith("http")) {
                setDataSource(
                    this@VinylCassettePlayerActivity,
                    Uri.parse(track.fileUri),
                    com.example.readtrace.util.NeteasePreviewHelper.buildPlaybackHeaders(this@VinylCassettePlayerActivity),
                )
            } else {
                setDataSource(this@VinylCassettePlayerActivity, Uri.parse(track.fileUri))
            }
            setOnPreparedListener { mp ->
                isPreparing = false
                handler.removeCallbacks(prepareTimeoutRunnable)
                totalSecondsMs = mp.duration.toLong()
                if (track.durationMs <= 0) {
                    databaseHelper.updateAudioTrackDuration(track.id, totalSecondsMs)
                }
                updatePlaybackProgress()
                if (this@VinylCassettePlayerActivity.isPlaying) {
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
                isPreparing = false
                handler.removeCallbacks(prepareTimeoutRunnable)
                Toast.makeText(this@VinylCassettePlayerActivity, "播放出错 (code $what/$extra)，文件可能已失效", Toast.LENGTH_LONG).show()
                // 复位播放态与按钮文案，避免 UI 死锁在「缓冲中」无法重试（须显式限定 Activity 字段，
                // 否则 isPlaying 会解析到 MediaPlayer 自身的 val 属性导致赋值失败）
                this@VinylCassettePlayerActivity.isPlaying = false
                setPlayingUi(false)
                // 在线缓存直链（试听/完整播放）会过期失效：自动移除旧链接并重新联网取链，避免反复报错
                if (isOnlineCachedTrack(track)) {
                    databaseHelper.deleteAudioTrack(track.id)
                    val work = playlist.getOrNull(currentIndex)
                    if (work != null) fetchNeteasePreview(work)
                }
                true
            }
            isPreparing = true
            handler.removeCallbacks(prepareTimeoutRunnable)
            handler.postDelayed(prepareTimeoutRunnable, PREPARE_TIMEOUT_MS)
            prepareAsync()
            tvPlayPauseLabel.text = "⏳ 缓冲中..."
        }

        isPlaying = true
        // 取曲/缓冲中也视为「播放会话中」，其他页面的悬浮胶囊即时可见可跳回
        isEnginePlaying = true
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
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
            .also { audioFocusRequest = it }
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    /**
     * 音频焦点变化：来电/导航等瞬时丢失 → 暂停并在焦点恢复后自动续播；
     * 可闪避丢失（提示音等）→ 压低音量继续；永久丢失（用户转投其他播放器且未返回）→ 停播并释放。
     */
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                focusResumeOnGain = false
                duckedVolume = false
                abandonAudioFocus()
                stopEngineForFocusLoss()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isPlaying) {
                    focusResumeOnGain = true
                    pausePlaybackForFocus()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isPlaying) {
                    duckedVolume = true
                    mediaPlayer?.setVolume(0.18f, 0.18f)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (duckedVolume) {
                    duckedVolume = false
                    mediaPlayer?.setVolume(1f, 1f)
                } else if (focusResumeOnGain) {
                    focusResumeOnGain = false
                    resumePlaybackForFocus()
                }
            }
        }
    }

    /** 与手动暂停同口径：暂停引擎与 UI；Preparing 态调 pause 非法，直接释放复位到可重试态 */
    private fun pausePlaybackForFocus() {
        if (isPreparing) {
            stopEngineForFocusLoss()
            return
        }
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) mp.pause()
        isPlaying = false
        pausePreviewTimer()
        setPlayingUi(false)
    }

    private fun resumePlaybackForFocus() {
        val mp = mediaPlayer ?: return
        mp.start()
        isPlaying = true
        setPlayingUi(true)
        handler.post(playRunnable)
        resumePreviewTimerIfAny()
    }

    /** 焦点永久丢失或缓冲中遭焦点抢占：释放引擎并复位 UI 到可重试态 */
    private fun stopEngineForFocusLoss() {
        releaseMediaPlayer()
        isPlaying = false
        setPlayingUi(false)
        tvPlayPauseLabel.text = "▶ 开始放唱"
        updatePlaybackProgress()
    }

    /** 拔耳机/断开蓝牙（AUDIO_BECOMING_NOISY）立即暂停，避免音乐突然外放 */
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && isPlaying) {
                focusResumeOnGain = false
                pausePlaybackForFocus()
            }
        }
    }

    private fun setNoisyReceiverActive(active: Boolean) {
        if (active && !noisyReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyReceiverRegistered = true
        } else if (!active && noisyReceiverRegistered) {
            runCatching { unregisterReceiver(becomingNoisyReceiver) }
            noisyReceiverRegistered = false
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.run {
            // 先解绑回调：旧实例的 onError/onPrepared 若异步打到新会话上，会出现「新曲缓冲却弹播放出错」
            runCatching { setOnErrorListener(null) }
            runCatching { setOnPreparedListener(null) }
            runCatching { setOnCompletionListener(null) }
            runCatching { setOnInfoListener(null) }
            runCatching { setOnBufferingUpdateListener(null) }
            // stop() 仅在 Prepared/Started/Paused/Stopped/Completed 合法；实例常仍在 Preparing 态，
            // 会触发 native -38。reset() 在任意状态合法，用它替代。
            runCatching { reset() }
            runCatching { release() }
        }
        mediaPlayer = null
        isPreparing = false
        preparingCloudTrack = null
        // 清零时长：避免切歌间隙进度条沿用上一曲的总时长
        totalSecondsMs = 0L
        wasPlayingBeforeSeek = false
        handler.removeCallbacks(playRunnable)
        handler.removeCallbacks(previewStopRunnable)
        handler.removeCallbacks(prepareTimeoutRunnable)
        previewActive = false
        previewElapsedMs = 0L
    }

    /**
     * 云歌单播放失败统一恢复：复位播放态与按钮文案，并对同一次播放
     * 自动重新取链重试一次（直链现取现用，取链失败或重试仍败则停在可重试态）。
     */
    private fun handleCloudPlaybackFailure(
        track: com.example.readtrace.util.NeteasePreviewHelper.PlaylistTrack,
        toastMsg: String?,
    ) {
        releaseMediaPlayer()
        isPlaying = false
        setPlayingUi(false)
        if (toastMsg != null) {
            Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show()
        }
        if (!cloudRetryUsed) {
            cloudRetryUsed = true
            tvPlayPauseLabel.text = "⏳ 重新取链..."
            com.example.readtrace.util.NeteasePreviewHelper.fetchTrackStreamUrl(this, track) { url ->
                if (isDestroyed) return@fetchTrackStreamUrl
                if (url.isNullOrBlank()) {
                    tvPlayPauseLabel.text = "▶ 开始放唱"
                } else {
                    playCloudUrl(url, track)
                }
            }
        } else {
            cloudRetryUsed = false
            tvPlayPauseLabel.text = "▶ 开始放唱"
        }
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
        val elapsedMinutes = ((SystemClock.elapsedRealtime() - sessionStartTimeMs) / 60000L).toInt()
        val currentBook = playlist.getOrNull(currentIndex)
        if (elapsedMinutes >= 1 && currentBook != null) {
            databaseHelper.insertReadingSession(
                com.example.readtrace.model.ReadingSession(
                    bookId = currentBook.id,
                    durationMinutes = elapsedMinutes,
                    thought = "🎧 伴读沉浸 ${elapsedMinutes} 分钟 · 拟真黑胶唱机",
                    createdAt = java.time.LocalDate.now().toString(),
                ),
            )
        }
        handler.removeCallbacks(playRunnable)
        handler.removeCallbacks(previewStopRunnable)
        releaseMediaPlayer()
        abandonAudioFocus()
        setNoisyReceiverActive(false)
        focusResumeOnGain = false
        duckedVolume = false
        if (isFinishing) isEnginePlaying = false
    }

    /**
     * 播放中返回不销毁页面：把主页带到唱机之上（唱机只 stop 不销毁），
     * 音乐在 App 内任意页面持续播放，悬浮胶囊（VinylNowPlayingFloat）可随时跳回；
     * 无任何音源时正常退出。
     */
    private fun minimizeOrFinish() {
        if (isPlaying || mediaPlayer != null || cloudTracks.isNotEmpty()) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        } else {
            finish()
        }
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

        /** 引擎是否正在出聲（含試聽/緩衝後的播放態）：其他頁面據此顯示「返回唱機」懸浮膠囊 */
        var isEnginePlaying = false
            private set

        /** 免费曲目试听片段的限播时长（约 15 秒） */
        const val PREVIEW_LIMIT_MS = 15_000L

        /** 云直链 prepare 超时兜底：弱网下避免永久「缓冲中」 */
        const val PREPARE_TIMEOUT_MS = 15_000L

        /** 会员曲目兜底试听片段的限播时长（约 30 秒） */
        const val PREVIEW_LIMIT_VIP_MS = 30_000L

        fun createIntent(context: Context, bookId: Long): Intent {
            return Intent(context, VinylCassettePlayerActivity::class.java).apply {
                putExtra(EXTRA_BOOK_ID, bookId)
            }
        }
    }
}
