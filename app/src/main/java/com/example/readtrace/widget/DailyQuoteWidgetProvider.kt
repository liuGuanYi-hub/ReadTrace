package com.example.readtrace.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.readtrace.BookDetailActivity
import com.example.readtrace.MainActivity
import com.example.readtrace.R
import com.example.readtrace.data.BookDatabaseHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyQuoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_QUOTE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, DailyQuoteWidgetProvider::class.java))
            val currentQuote = intent.getStringExtra(EXTRA_CURRENT_QUOTE)
            updateAllWidgets(context, appWidgetManager, ids, currentQuote)
        }
    }

    private fun updateAllWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        excludeQuote: String? = null,
    ) {
        val databaseHelper = BookDatabaseHelper(context)
        val (book, quote) = databaseHelper.getRandomOrNextQuote(excludeQuote)
        val todayMinutes = databaseHelper.getTodayTotalReadingMinutes()
        val dateStr = SimpleDateFormat("MM.dd", Locale.getDefault()).format(Date())

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_daily_quote_2x2)

            views.setTextViewText(R.id.widgetQuoteDate, dateStr)
            views.setTextViewText(
                R.id.widgetQuoteBookTitle,
                if (book != null) {
                    val authorPart = book.author?.let { " · $it" } ?: ""
                    "${book.mediaType.emoji} 《${book.title}》$authorPart"
                } else {
                    "📖 阅痕 ReadTrace"
                },
            )
            views.setTextViewText(R.id.widgetQuoteContent, "“$quote”")
            views.setTextViewText(
                R.id.widgetReadingTimerBadge,
                if (todayMinutes > 0) "⏱️ 今日专注 ${todayMinutes} min" else "⏱️ 开启今日专注",
            )

            // 点击卡片主体：进入该作品详情或首页
            val clickIntent = if (book != null && book.id > 0) {
                BookDetailActivity.createIntent(context, book.id)
            } else {
                Intent(context, MainActivity::class.java)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 1,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widgetQuoteRoot, pendingIntent)

            // 点击「🔄 换一句」：发送广播刷新并排除当前金句
            val refreshIntent = Intent(context, DailyQuoteWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_QUOTE
                putExtra(EXTRA_CURRENT_QUOTE, quote)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 2,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widgetQuoteRefreshBtn, refreshPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        databaseHelper.close()
    }

    companion object {
        const val ACTION_REFRESH_QUOTE = "com.example.readtrace.widget.ACTION_REFRESH_QUOTE"
        const val EXTRA_CURRENT_QUOTE = "extra_current_quote"

        fun refreshWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, DailyQuoteWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, DailyQuoteWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
