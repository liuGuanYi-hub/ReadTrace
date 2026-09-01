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
import com.example.readtrace.ResonancePosterActivity
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class MindprintDashboardWidgetProvider : AppWidgetProvider() {

    data class TwinResonancePreset(
        val titleA: String,
        val titleB: String,
        val trait: String,
        val similarity: Int,
    )

    private val classicTwinPairs = listOf(
        TwinResonancePreset("百年孤独", "EVA", "存在主义思辨 · 终极孤独", 94),
        TwinResonancePreset("小王子", "紫罗兰永恒花园", "爱的驯服 · 治愈救赎", 96),
        TwinResonancePreset("三体", "魔法少女小圆", "宇宙宿命 · 哲学神域", 95),
        TwinResonancePreset("挪威的森林", "孤独摇滚", "青春迷茫 · 孤独摇滚", 93),
        TwinResonancePreset("老人与海", "强风吹拂", "坚韧意志 · 极限超越", 94),
        TwinResonancePreset("解忧杂货店", "夏目友人帐", "人情温暖 · 羁绊守候", 95),
        TwinResonancePreset("罪与罚", "灵能百分百", "正义边界 · 灵魂受难", 92),
        TwinResonancePreset("白夜行", "来自深渊", "深渊互持 · 残酷救赎", 94),
        TwinResonancePreset("基督山伯爵", "JOJO", "黄金精神 · 意志传承", 93),
        TwinResonancePreset("边城", "夏日重现", "纯美爱恋 · 故乡清冽", 91),
        TwinResonancePreset("晴る (Haru)", "紫罗兰永恒花园", "物哀音律 · 澄澈释怀", 98),
        TwinResonancePreset("嘘じゃない (No Lie)", "孤独摇滚！", "疾走真夜中 · 放克共鸣", 97),
        TwinResonancePreset("アポリア (Aporia)", "三体", "宇宙求索 · 真理追寻", 96),
        TwinResonancePreset("花一匁 (Hanaichimonme)", "女神异闻录5皇家版", "叛逆放克 · 潮酷心声", 95),
    )

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val databaseHelper = BookDatabaseHelper.getInstance(context)
        val allBooks = databaseHelper.getBooks()

        // 每半小时轮换一次双生共鸣卡片与当前在读书籍
        val rotationSlot = ((System.currentTimeMillis() / (1000 * 60 * 30)) % (classicTwinPairs.size + 1)).toInt()
        val isTwinMode = rotationSlot > 0 && allBooks.isNotEmpty()

        val dateStr = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val allSessions = databaseHelper.getAllReadingSessions()
        val todayMinutes = allSessions
            .filter { it.createdAt.startsWith(todayStr) }
            .sumOf { it.durationMinutes }

        var twinBookA: Book? = null
        var twinBookB: Book? = null
        var twinMpA: BookMindprint? = null
        var twinMpB: BookMindprint? = null
        var twinTrait = ""
        var twinSimilarity = 94

        var singleBook: Book? = null
        var singleMindprint = BookMindprint(bookId = -1)
        var singleQuote = "“每一道心智印记，都是灵魂与文字的永恒交汇。”"

        if (isTwinMode) {
            val preset = classicTwinPairs[(rotationSlot - 1) % classicTwinPairs.size]
            twinTrait = preset.trait
            twinSimilarity = preset.similarity

            twinBookA = allBooks.firstOrNull { it.title.contains(preset.titleA) } ?: allBooks.firstOrNull()
            twinBookB = allBooks.firstOrNull { it.title.contains(preset.titleB) } ?: allBooks.lastOrNull()

            if (twinBookA != null) twinMpA = databaseHelper.getMindprint(twinBookA.id)
            if (twinBookB != null) twinMpB = databaseHelper.getMindprint(twinBookB.id)
        } else {
            singleBook = allBooks.firstOrNull { it.status == com.example.readtrace.model.BookStatus.READING }
                ?: allBooks.firstOrNull()

            if (singleBook != null) {
                singleMindprint = databaseHelper.getMindprint(singleBook.id)
                val bookNotes = databaseHelper.getNotes(singleBook.id)
                singleQuote = bookNotes.firstOrNull()?.content
                    ?: singleBook.shortComment?.takeIf { it.isNotBlank() }
                    ?: singleBook.review?.takeIf { it.isNotBlank() }
                    ?: "“给岁月以文明，而不是给文明以岁月。”"
            }
        }

        val radarBitmap = if (isTwinMode && twinMpA != null && twinMpB != null) {
            drawDualMiniRadarBitmap(twinMpA, twinMpB)
        } else {
            drawSingleMiniRadarBitmap(singleMindprint)
        }

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_mindprint_dashboard_4x2)

            views.setTextViewText(R.id.widgetDashboardDate, dateStr)
            views.setImageViewBitmap(R.id.widgetRadarImage, radarBitmap)

            if (isTwinMode && twinBookA != null && twinBookB != null) {
                views.setTextViewText(R.id.widgetDashboardHeaderTitle, "✨ 跨媒介双生共鸣 · ${twinSimilarity}%")
                views.setTextViewText(
                    R.id.widgetDashboardBookTitle,
                    "${twinBookA.mediaType.emoji}《${twinBookA.title}》⇋ ${twinBookB.mediaType.emoji}《${twinBookB.title}》",
                )
                views.setTextViewText(
                    R.id.widgetDashboardBookAuthor,
                    "★ $twinTrait ★",
                )

                val quoteA = twinBookA.shortComment?.takeIf { it.isNotBlank() } ?: "沉思于此"
                val quoteB = twinBookB.shortComment?.takeIf { it.isNotBlank() } ?: "共鸣于心"
                views.setTextViewText(
                    R.id.widgetDashboardQuote,
                    "“$quoteA” ⇋ “$quoteB”",
                )

                views.setTextViewText(
                    R.id.widgetDashboardTimerBadge,
                    if (todayMinutes > 0) "⏱️ 今日 ${todayMinutes} min" else "⏱️ 开启专注",
                )

                // 点击卡片直接进入双生微卡生成器
                val posterIntent = ResonancePosterActivity.createIntent(
                    context = context,
                    bookAId = twinBookA.id,
                    bookBId = twinBookB.id,
                    similarity = twinSimilarity,
                    resonanceTrait = twinTrait,
                )
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    posterIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widgetDashboardRoot, pendingIntent)
            } else {
                views.setTextViewText(R.id.widgetDashboardHeaderTitle, "✨ 阅痕 · 全息心智看板")
                views.setTextViewText(
                    R.id.widgetDashboardBookTitle,
                    if (singleBook != null) "${singleBook.mediaType.emoji} 《${singleBook.title}》" else "📖 阅痕全息书房",
                )
                views.setTextViewText(
                    R.id.widgetDashboardBookAuthor,
                    if (singleBook != null) "${singleBook.author ?: "未知作者"} · ${singleBook.category ?: "典藏"}" else "暂无在读书籍",
                )
                views.setTextViewText(R.id.widgetDashboardQuote, "“$singleQuote”")
                views.setTextViewText(
                    R.id.widgetDashboardTimerBadge,
                    if (todayMinutes > 0) "⏱️ 今日专注 ${todayMinutes} min" else "⏱️ 今日专注打卡",
                )

                val clickIntent = if (singleBook != null) {
                    BookDetailActivity.createIntent(context, singleBook.id)
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
            }

            // 点击星系快捷按钮进入心智星系
            val constellationIntent = MindprintConstellationActivity.createIntent(context)
            val constellationPending = PendingIntent.getActivity(
                context,
                appWidgetId + 1000,
                constellationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widgetActionConstellation, constellationPending)

            // 点击双生微卡快捷按钮
            val targetAId = twinBookA?.id ?: allBooks.firstOrNull()?.id ?: -1L
            val targetBId = twinBookB?.id ?: allBooks.lastOrNull()?.id ?: -1L
            val resonanceIntent = ResonancePosterActivity.createIntent(
                context = context,
                bookAId = targetAId,
                bookBId = targetBId,
                similarity = if (isTwinMode) twinSimilarity else 94,
                resonanceTrait = if (isTwinMode) twinTrait else "存在主义思辨 · 终极孤独",
            )
            val resonancePending = PendingIntent.getActivity(
                context,
                appWidgetId + 2000,
                resonanceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widgetActionResonance, resonancePending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun drawSingleMiniRadarBitmap(mindprint: BookMindprint): Bitmap {
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

        for (i in 0 until 6) {
            val angle = (Math.PI / 3 * i - Math.PI / 2).toFloat()
            val lx = cx + (maxR + 18f) * cos(angle.toDouble()).toFloat()
            val ly = cy + (maxR + 18f) * sin(angle.toDouble()).toFloat() + 7f
            canvas.drawText(labels[i], lx, ly, labelPaint)
        }

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

    private fun drawDualMiniRadarBitmap(mpA: BookMindprint, mpB: BookMindprint): Bitmap {
        val size = 260
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cx = size / 2f
        val cy = size / 2f
        val maxR = size * 0.38f

        val webPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#338C7F70")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7A7265")
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

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

        val labels = arrayOf("思", "文", "情", "逻", "难", "愈")
        for (i in 0 until 6) {
            val angle = (Math.PI / 3 * i - Math.PI / 2).toFloat()
            val lx = cx + (maxR + 18f) * cos(angle.toDouble()).toFloat()
            val ly = cy + (maxR + 18f) * sin(angle.toDouble()).toFloat() + 7f
            canvas.drawText(labels[i], lx, ly, labelPaint)
        }

        // 多边形 A (琥珀橙)
        val scoresA = floatArrayOf(
            mpA.depthScore.toFloat(),
            mpA.artistryScore.toFloat(),
            mpA.emotionScore.toFloat(),
            mpA.logicScore.toFloat(),
            mpA.difficultyScore.toFloat(),
            mpA.healingScore.toFloat(),
        )
        drawRadarPolygon(canvas, cx, cy, maxR, scoresA, Color.parseColor("#F4A261"), 80)

        // 多边形 B (幻夜紫)
        val scoresB = floatArrayOf(
            mpB.depthScore.toFloat(),
            mpB.artistryScore.toFloat(),
            mpB.emotionScore.toFloat(),
            mpB.logicScore.toFloat(),
            mpB.difficultyScore.toFloat(),
            mpB.healingScore.toFloat(),
        )
        drawRadarPolygon(canvas, cx, cy, maxR, scoresB, Color.parseColor("#9B5DE5"), 80)

        return bitmap
    }

    private fun drawRadarPolygon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        maxR: Float,
        scores: FloatArray,
        color: Int,
        alphaFill: Int,
    ) {
        val poly = Path()
        for (i in 0 until 6) {
            val angle = (Math.PI / 3 * i - Math.PI / 2).toFloat()
            val r = maxR * (scores[i] / 10f).coerceIn(0.15f, 1f)
            val px = cx + r * cos(angle.toDouble()).toFloat()
            val py = cy + r * sin(angle.toDouble()).toFloat()
            if (i == 0) poly.moveTo(px, py) else poly.lineTo(px, py)
        }
        poly.close()

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(alphaFill, Color.red(color), Color.green(color), Color.blue(color))
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }

        canvas.drawPath(poly, fillPaint)
        canvas.drawPath(poly, strokePaint)

        for (i in 0 until 6) {
            val angle = (Math.PI / 3 * i - Math.PI / 2).toFloat()
            val r = maxR * (scores[i] / 10f).coerceIn(0.15f, 1f)
            val px = cx + r * cos(angle.toDouble()).toFloat()
            val py = cy + r * sin(angle.toDouble()).toFloat()
            canvas.drawCircle(px, py, 4f, dotPaint)
        }
    }
}
