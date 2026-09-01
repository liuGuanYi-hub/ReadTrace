package com.example.readtrace

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * ⏳ 桌面 StandBy 禅意翻页伴读钟 (StandByZenDeskActivity)
 *
 * P15：让手机在不被操作时化身为书桌上的先锋艺术品——
 * 拟真大字翻页钟 + 24h 四时语境提示，屏幕常亮（FLAG_KEEP_SCREEN_ON），
 * 每 60 秒微像素位移（Pixel Shift）防 OLED 烧屏，轻触即可退出。
 */
class StandByZenDeskActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var clockText: TextView
    private var lastMinute = -1
    private var pixelShiftTick = 0

    private val tickRunnable = object : Runnable {
        override fun run() {
            renderClock()
            mainHandler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_standby_zen)

        // 常亮 + 沉浸全屏
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        clockText = findViewById(R.id.standbyClock)
        findViewById<TextView>(R.id.standbyExit).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(tickRunnable)
    }

    override fun onPause() {
        mainHandler.removeCallbacks(tickRunnable)
        super.onPause()
    }

    private fun renderClock() {
        val now = LocalTime.now()
        val text = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        clockText.text = text

        if (now.minute != lastMinute) {
            lastMinute = now.minute
            // 翻页动效：分钟变化时轻微上翻渐入
            clockText.animate().translationY(-14f).alpha(0.2f).setDuration(90L).withEndAction {
                clockText.animate().translationY(0f).alpha(1f).setDuration(150L).start()
            }.start()
            findViewById<TextView>(R.id.standbyDate).text =
                LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE"))

            // ⛑️ 防烧屏：每 60 秒整体微平移 2px 循环漂移
            pixelShiftTick = (pixelShiftTick + 1) % 4
            val dx = when (pixelShiftTick) {
                0 -> 0f; 1 -> 2f; 2 -> 0f; else -> -2f
            }
            clockText.translationX = dx
        }
    }
}
