package com.example.readtrace.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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
        val databaseHelper = BookDatabaseHelper(context)
        val books = databaseHelper.getBooks()

        val featuredBook = books.firstOrNull { it.status == com.example.readtrace.model.BookStatus.READING }
            ?: books.firstOrNull()

        val featuredQuote = if (featuredBook != null) {
            val bookNotes = databaseHelper.getNotes(featuredBook.id)
            bookNotes.firstOrNull()?.content
                ?: featuredBook.shortComment?.takeIf { it.isNotBlank() }
                ?: featuredBook.review?.takeIf { it.isNotBlank() }
                ?: "“每一道心智印记，都是灵魂与文字的永恒交汇。”"
        } else {
            val allNotes = books.flatMap { databaseHelper.getNotes(it.id) }
            allNotes.firstOrNull()?.content ?: "“每一道心智印记，都是灵魂与文字的永恒交汇。”"
        }

        // 统计今日阅读时长
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val allSessions = books.flatMap { databaseHelper.getReadingSessions(it.id) }
        val todayMinutes = allSessions
            .filter { it.createdAt.startsWith(todayStr) }
            .sumOf { it.durationMinutes }

        val dateStr = SimpleDateFormat("MM.dd", Locale.getDefault()).format(Date())

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_daily_quote_2x2)

            views.setTextViewText(R.id.widgetQuoteDate, dateStr)
            views.setTextViewText(
                R.id.widgetQuoteBookTitle,
                if (featuredBook != null) "${featuredBook.mediaType.emoji} 《${featuredBook.title}》" else "📖 阅痕 ReadTrace",
            )
            views.setTextViewText(R.id.widgetQuoteContent, "“$featuredQuote”")
            views.setTextViewText(
                R.id.widgetReadingTimerBadge,
                if (todayMinutes > 0) "⏱️ 今日专注 ${todayMinutes} min" else "⏱️ 开启今日专注 ➔",
            )

            val clickIntent = if (featuredBook != null) {
                BookDetailActivity.createIntent(context, featuredBook.id)
            } else {
                Intent(context, MainActivity::class.java)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widgetQuoteRoot, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        databaseHelper.close()
    }
}
