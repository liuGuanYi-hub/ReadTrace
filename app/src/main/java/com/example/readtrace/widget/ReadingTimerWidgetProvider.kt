package com.example.readtrace.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.example.readtrace.R
import com.example.readtrace.ReadingTimerActivity
import com.example.readtrace.data.BookDatabaseHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReadingTimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_QUICK_PUNCH) {
            val databaseHelper = BookDatabaseHelper(context)
            val latestBook = databaseHelper.getLatestReadingBook()
            val bookId = latestBook?.id ?: 1L
            databaseHelper.quickRecordReadingSession(bookId, 15)
            databaseHelper.close()

            Toast.makeText(context, "⏱️ 已快捷记录 15 分钟专注时光！", Toast.LENGTH_SHORT).show()

            // 刷新所有打卡小组件
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, ReadingTimerWidgetProvider::class.java))
            updateAllWidgets(context, appWidgetManager, ids)

            // 同时刷新每日金句与在读小组件（同步最新打卡数据）
            DailyQuoteWidgetProvider.refreshWidgets(context)
            CurrentlyReadingWidgetProvider.refreshWidgets(context)
        }
    }

    private fun updateAllWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val databaseHelper = BookDatabaseHelper(context)
        val todayMinutes = databaseHelper.getTodayTotalReadingMinutes()
        val streakDays = databaseHelper.getConsecutiveReadingDays()
        val latestBook = databaseHelper.getLatestReadingBook()
        val dateStr = SimpleDateFormat("MM.dd", Locale.getDefault()).format(Date())

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_reading_timer)

            views.setTextViewText(R.id.widgetTimerDate, dateStr)
            views.setTextViewText(R.id.widgetTimerMinutes, todayMinutes.toString())
            views.setTextViewText(
                R.id.widgetTimerStreakBadge,
                if (streakDays > 0) "🔥 连读 ${streakDays} 天" else "🌱 开启今日阅读",
            )
            views.setTextViewText(
                R.id.widgetTimerBookTitle,
                if (latestBook != null) "📖 专注《${latestBook.title}》" else "📖 阅痕 · 留下一刻痕迹",
            )

            // 点击「⏱️ 专注计时」或整个卡片：启动计时器页面
            val startIntent = if (latestBook != null) {
                ReadingTimerActivity.createIntent(context, latestBook.id, latestBook.title)
            } else {
                Intent(context, ReadingTimerActivity::class.java)
            }
            val startPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 1,
                startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widgetTimerStartBtn, startPendingIntent)
            views.setOnClickPendingIntent(R.id.widgetTimerRoot, startPendingIntent)

            // 点击「⚡ +15m 快捷打卡」：发送广播就地记录
            val punchIntent = Intent(context, ReadingTimerWidgetProvider::class.java).apply {
                action = ACTION_QUICK_PUNCH
            }
            val punchPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 2,
                punchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widgetTimerQuickPunchBtn, punchPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        databaseHelper.close()
    }

    companion object {
        const val ACTION_QUICK_PUNCH = "com.example.readtrace.widget.ACTION_QUICK_PUNCH"

        fun refreshWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, ReadingTimerWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, ReadingTimerWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
