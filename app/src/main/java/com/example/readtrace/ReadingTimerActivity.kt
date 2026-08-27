package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.ReadingSession
import com.example.readtrace.util.FloatingBack
import java.util.Locale

class ReadingTimerActivity : AppCompatActivity() {

    private var bookId: Long = 0L
    private var bookTitle: String = ""
    private lateinit var databaseHelper: BookDatabaseHelper

    private lateinit var titleView: TextView
    private lateinit var statusTip: TextView
    private lateinit var timerDisplay: TextView
    private lateinit var minutesHint: TextView
    private lateinit var toggleBtn: TextView
    private lateinit var resetBtn: TextView
    private lateinit var pagesInput: EditText
    private lateinit var thoughtInput: EditText
    private lateinit var finishBtn: TextView

    private lateinit var ambienceStatusText: TextView
    private lateinit var ambienceRainBtn: TextView
    private lateinit var ambienceFireBtn: TextView
    private lateinit var ambienceForestBtn: TextView
    private lateinit var ambienceOceanBtn: TextView

    private enum class AmbienceType(val displayName: String, val prompt: String) {
        RAIN("🌧️ 雨夜", "🌧️ 雨声渐沥 · 窗外细雨洗涤浮尘"),
        FIRE("🔥 壁炉", "🔥 柴火噼啪 · 炉火融融暖意流淌"),
        FOREST("🌲 松林", "🌲 松涛阵阵 · 幽林清风拂过书页"),
        OCEAN("🌊 潮汐", "🌊 潮起潮落 · 随海浪律动潜入深境"),
    }

    private var currentAmbience = AmbienceType.RAIN

    private var isRunning = false
    private var elapsedSeconds = 0
    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                elapsedSeconds++
                updateTimerDisplay()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_reading_timer)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.timerRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 裸 Intent 启动（如桌面小组件）不会携带书籍 extra，将无效 ID 归一为 -1 表示独立专注模式，不再依赖查询返回 null 的隐式行为
        val rawBookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        bookId = if (rawBookId > 0) rawBookId else -1L
        bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE).orEmpty()
        databaseHelper = BookDatabaseHelper(this)

        bindViews()
        selectAmbience(AmbienceType.RAIN)
    }

    private fun bindViews() {
        titleView = findViewById(R.id.timerBookTitle)
        statusTip = findViewById(R.id.timerStatusTip)
        timerDisplay = findViewById(R.id.timerDisplay)
        minutesHint = findViewById(R.id.timerMinutesHint)
        toggleBtn = findViewById(R.id.timerToggleBtn)
        resetBtn = findViewById(R.id.timerResetBtn)
        pagesInput = findViewById(R.id.timerPagesInput)
        thoughtInput = findViewById(R.id.timerThoughtInput)
        finishBtn = findViewById(R.id.timerFinishAndSaveBtn)

        ambienceStatusText = findViewById(R.id.ambienceStatusText)
        ambienceRainBtn = findViewById(R.id.ambienceRain)
        ambienceFireBtn = findViewById(R.id.ambienceFire)
        ambienceForestBtn = findViewById(R.id.ambienceForest)
        ambienceOceanBtn = findViewById(R.id.ambienceOcean)

        val book = if (bookId > 0) databaseHelper.getBook(bookId) else null
        val mediaType = book?.mediaType ?: com.example.readtrace.model.MediaType.BOOK
        titleView.text = if (bookTitle.isNotBlank()) {
            when (mediaType) {
                com.example.readtrace.model.MediaType.BOOK -> "《$bookTitle》· 专注阅读时光"
                com.example.readtrace.model.MediaType.ANIME -> "《$bookTitle》· 追番沉浸时光"
                com.example.readtrace.model.MediaType.MOVIE -> "《$bookTitle》· 观影沉浸时光"
                com.example.readtrace.model.MediaType.GAME -> "《$bookTitle》· 游戏专注时光"
                com.example.readtrace.model.MediaType.MUSIC -> "《$bookTitle》· 音乐聆听时光"
            }
        } else {
            "专注沉浸时光"
        }
        pagesInput.hint = when (mediaType) {
            com.example.readtrace.model.MediaType.BOOK -> "本次阅读页数（例如：25）"
            com.example.readtrace.model.MediaType.ANIME -> "本次观看话数（例如：2）"
            com.example.readtrace.model.MediaType.MOVIE -> "本次观看时长（分钟）"
            com.example.readtrace.model.MediaType.GAME -> "本次通关关卡/进度"
            com.example.readtrace.model.MediaType.MUSIC -> "本次收听曲目数/时长"
        }
        thoughtInput.hint = when (mediaType) {
            com.example.readtrace.model.MediaType.BOOK -> "随手记下这刻的思考或金句..."
            com.example.readtrace.model.MediaType.ANIME -> "随手记下本集的名场面或心境..."
            com.example.readtrace.model.MediaType.MOVIE -> "随手记下本片的触动时刻..."
            com.example.readtrace.model.MediaType.GAME -> "随手记下本段通关心得或战报..."
            com.example.readtrace.model.MediaType.MUSIC -> "随手记下本曲的灵感与感悟..."
        }

        FloatingBack.install(this) {
            if (isRunning) {
                Toast.makeText(this, "计时仍在后台运行，可随时返回继续", Toast.LENGTH_SHORT).show()
            }
            finish()
        }

        toggleBtn.setOnClickListener {
            if (isRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }

        resetBtn.setOnClickListener {
            resetTimer()
        }

        ambienceRainBtn.setOnClickListener { selectAmbience(AmbienceType.RAIN) }
        ambienceFireBtn.setOnClickListener { selectAmbience(AmbienceType.FIRE) }
        ambienceForestBtn.setOnClickListener { selectAmbience(AmbienceType.FOREST) }
        ambienceOceanBtn.setOnClickListener { selectAmbience(AmbienceType.OCEAN) }

        finishBtn.setOnClickListener {
            saveSessionAndFinish()
        }
    }

    private fun selectAmbience(ambience: AmbienceType) {
        currentAmbience = ambience
        ambienceStatusText.text = ambience.prompt
        if (isRunning) {
            statusTip.text = "📖 ${ambience.prompt}..."
        }

        val buttons = listOf(
            ambienceRainBtn to AmbienceType.RAIN,
            ambienceFireBtn to AmbienceType.FIRE,
            ambienceForestBtn to AmbienceType.FOREST,
            ambienceOceanBtn to AmbienceType.OCEAN,
        )
        buttons.forEach { (btn, type) ->
            val isSelected = currentAmbience == type
            btn.setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
            btn.setTextColor(getColor(if (isSelected) R.color.white else R.color.readtrace_ink))
        }
    }

    private fun startTimer() {
        isRunning = true
        toggleBtn.text = "❚❚ 暂停专注"
        toggleBtn.setBackgroundResource(R.drawable.bg_secondary_button)
        toggleBtn.setTextColor(getColor(R.color.readtrace_ink))
        statusTip.text = "📖 呼吸渐缓，静享纯粹文字之美..."
        handler.post(timerRunnable)
    }

    private fun pauseTimer() {
        isRunning = false
        toggleBtn.text = "▶ 继续专注"
        toggleBtn.setBackgroundResource(R.drawable.bg_primary_button)
        toggleBtn.setTextColor(getColor(R.color.white))
        statusTip.text = "⏸️ 专注已暂停，稍作歇息"
        handler.removeCallbacks(timerRunnable)
    }

    private fun resetTimer() {
        pauseTimer()
        elapsedSeconds = 0
        updateTimerDisplay()
        toggleBtn.text = "▶ 开始专注"
        statusTip.text = "🕯️ 心绪渐定，沉入字里行间"
    }

    private fun updateTimerDisplay() {
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        timerDisplay.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        minutesHint.text = "已专注 $minutes 分钟"
    }

    private fun saveSessionAndFinish() {
        val minutes = elapsedSeconds / 60
        if (minutes < 1 && elapsedSeconds < 10) {
            Toast.makeText(this, "阅读时间过短，至少专注 1 分钟再打卡吧！", Toast.LENGTH_SHORT).show()
            return
        }
        val finalMinutes = if (minutes < 1) 1 else minutes
        val pages = pagesInput.text.toString().trim().ifBlank { null }
        val thought = thoughtInput.text.toString().trim().ifBlank { null }

        val session = ReadingSession(
            bookId = bookId,
            durationMinutes = finalMinutes,
            pagesRead = pages,
            thought = thought,
        )

        databaseHelper.insertReadingSession(session)
        com.example.readtrace.widget.ReadingTimerWidgetProvider.refreshWidgets(this)
        com.example.readtrace.widget.DailyQuoteWidgetProvider.refreshWidgets(this)
        com.example.readtrace.widget.CurrentlyReadingWidgetProvider.refreshWidgets(this)
        Toast.makeText(this, "🎉 专注打卡成功！已记录本次 $finalMinutes 分钟阅读时光", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerRunnable)
        databaseHelper.close()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_BOOK_ID = "extra_book_id"
        private const val EXTRA_BOOK_TITLE = "extra_book_title"

        fun createIntent(context: Context, bookId: Long, bookTitle: String): Intent =
            Intent(context, ReadingTimerActivity::class.java).apply {
                putExtra(EXTRA_BOOK_ID, bookId)
                putExtra(EXTRA_BOOK_TITLE, bookTitle)
            }
    }
}
