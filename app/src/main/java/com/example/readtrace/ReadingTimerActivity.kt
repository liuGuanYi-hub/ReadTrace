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

        bookId = intent.getLongExtra(EXTRA_BOOK_ID, 0L)
        bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE).orEmpty()
        databaseHelper = BookDatabaseHelper(this)

        bindViews()
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

        titleView.text = if (bookTitle.isNotBlank()) "《$bookTitle》· 专注阅读时光" else "专注阅读时光"

        findViewById<View>(R.id.timerBackBtn).setOnClickListener {
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

        finishBtn.setOnClickListener {
            saveSessionAndFinish()
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
