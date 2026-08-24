package com.example.readtrace.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.widget.RemoteViews
import com.example.readtrace.BookDetailActivity
import com.example.readtrace.MainActivity
import com.example.readtrace.MindprintConstellationActivity
import com.example.readtrace.R
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.BookMindprint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class MindprintDashboardWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val databaseHelper = BookDatabaseHelper(context)
        val books = databaseHelper.getBooks()

        val featuredBook = books.firstOrNull { it.status == com.example.readtrace.model.BookStatus.READING }
            ?: books.firstOrNull()

        val mindprint = if (featuredBook != null) {
            databaseHelper.getMindprint(featuredBook.id)
        } else {
            BookMindprint(bookId = -1)
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val allSessions = books.flatMap { databaseHelper.getReadingSessions(it.id) }
        val todayMinutes = allSessions
            .filter { it.createdAt.startsWith(todayStr) }
            .sumOf { it.durationMinutes }

        val dateStr = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())

        val featuredQuote = if (featuredBook != null) {
            val bookNotes = databaseHelper.getNotes(featuredBook.id)
            bookNotes.firstOrNull()?.content
                ?: featuredBook.shortComment?.takeIf { it.isNotBlank() }
                ?: featuredBook.review?.takeIf { it.isNotBlank() }
                ?: "“给岁月以文明，而不是给文明以岁月。”"
        } else {
            "“每一道心智印记，都是灵魂与文字的永恒交汇。”"
        }

        val radarBitmap = drawMiniRadarBitmap(mindprint)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_mindprint_dashboard_4x2)

            views.setTextViewText(R.id.widgetDashboardDate, dateStr)
            views.setTextViewText(
                R.id.widgetDashboardBookTitle,
                if (featuredBook != null) "${featuredBook.mediaType.emoji} 《${featuredBook.title}》" else "📖 阅痕全息书房",
            )
            views.setTextViewText(
                R.id.widgetDashboardBookAuthor,
                if (featuredBook != null) "${featuredBook.author ?: "未知作者"} · ${featuredBook.category ?: "典藏"}" else "暂无在读书籍",
            )
            views.setTextViewText(R.id.widgetDashboardQuote, "“$featuredQuote”")
            views.setTextViewText(
                R.id.widgetDashboardTimerBadge,
                if (todayMinutes > 0) "⏱️ 今日专注 ${todayMinutes} min" else "⏱️ 今日专注打卡",
            )

            views.setImageViewBitmap(R.id.widgetRadarImage, radarBitmap)

            // 点击卡片进入详情或主页
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
            views.setOnClickPendingIntent(R.id.widgetDashboardRoot, pendingIntent)

            // 点击星系快捷按钮进入心智星系
            val constellationIntent = MindprintConstellationActivity.createIntent(context)
            val constellationPending = PendingIntent.getActivity(
                context,
                appWidgetId + 1000,
                constellationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widgetActionConstellation, constellationPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        databaseHelper.close()
    }

    private fun drawMiniRadarBitmap(mindprint: BookMindprint): Bitmap {
        val size = 260
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cx = size / 2f
        val cy = size / 2f
        val maxR = size * 0.38f

        val webPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#443A6348")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#553A6348")
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3A6348")
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#264530")
            style = Paint.Style.FILL
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3A6348")
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        // 绘制 3 层正六边形蛛网
        for (level in 1..3) {
            val r = maxR * (level / 3f)
            val path = Path()
            for (i in 0 until 6) {
                val angle = (Math.PI / 3 * i - Math.PI / 2).toFloat()
                val px = cx + r * cos(angle.toDouble()).toFloat()
                val py = cy + r * sin(angle.toDouble()).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            canvas.drawPath(path, webPaint)
        }

        val scores = floatArrayOf(
            mindprint.depthScore.toFloat(),
            mindprint.artistryScore.toFloat(),
            mindprint.emotionScore.toFloat(),
            mindprint.logicScore.toFloat(),
            mindprint.difficultyScore.toFloat(),
            mindprint.healingScore.toFloat(),
        )
        val labels = arrayOf("思", "文", "情", "逻", "难", "愈")

        // 绘制标签
        for (i in 0 until 6) {
            val angle = (Math.PI / 3 * i - Math.PI / 2).toFloat()
            val lx = cx + (maxR + 18f) * cos(angle.toDouble()).toFloat()
            val ly = cy + (maxR + 18f) * sin(angle.toDouble()).toFloat() + 7f
            canvas.drawText(labels[i], lx, ly, labelPaint)
        }

        // 绘制心智数据多边形
        val poly = Path()
        for (i in 0 until 6) {
            val angle = (Math.PI / 3 * i - Math.PI / 2).toFloat()
            val r = maxR * (scores[i] / 10f).coerceIn(0.15f, 1f)
            val px = cx + r * cos(angle.toDouble()).toFloat()
            val py = cy + r * sin(angle.toDouble()).toFloat()
            if (i == 0) poly.moveTo(px, py) else poly.lineTo(px, py)
        }
        poly.close()
        canvas.drawPath(poly, fillPaint)
        canvas.drawPath(poly, strokePaint)

        for (i in 0 until 6) {
            val angle = (Math.PI / 3 * i - Math.PI / 2).toFloat()
            val r = maxR * (scores[i] / 10f).coerceIn(0.15f, 1f)
            val px = cx + r * cos(angle.toDouble()).toFloat()
            val py = cy + r * sin(angle.toDouble()).toFloat()
            canvas.drawCircle(px, py, 4.5f, dotPaint)
        }

        return bitmap
    }
}
