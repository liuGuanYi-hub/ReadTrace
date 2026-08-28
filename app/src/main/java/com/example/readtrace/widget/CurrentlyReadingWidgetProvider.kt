package com.example.readtrace.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.readtrace.BookDetailActivity
import com.example.readtrace.MainActivity
import com.example.readtrace.R
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.BookStatus
import com.example.readtrace.util.CoverImageHelper

class CurrentlyReadingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllWidgets(context, appWidgetManager, appWidgetIds)
    }

    private fun updateAllWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val databaseHelper = BookDatabaseHelper(context)
        val book = databaseHelper.getLatestReadingBook()

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_currently_reading)

            if (book != null) {
                views.setTextViewText(R.id.widgetReadingBookTitle, "《${book.title}》")
                views.setTextViewText(
                    R.id.widgetReadingBookAuthor,
                    book.author?.ifBlank { "未知作者" } ?: "未知作者",
                )
                views.setTextViewText(
                    R.id.widgetReadingStatusChip,
                    when (book.status) {
                        BookStatus.READING -> "📖 在读中"
                        BookStatus.FINISHED -> "🏆 已读完"
                        BookStatus.WISHLIST -> "✨ 想读"
                        BookStatus.PAUSED -> "⏸️ 暂停"
                        BookStatus.DROPPED -> "📦 弃读"
                    },
                )

                // 计算已读页码与进度
                val pageIndex = databaseHelper.getReadingPage(book.id)
                val displayPage = pageIndex + 1
                val totalReadingMinutes = databaseHelper.getTotalReadingMinutes(book.id)
                val progressPercent = (pageIndex * 3).coerceIn(10, 95) // 动态展示阅读刻度

                views.setTextViewText(
                    R.id.widgetReadingProgressText,
                    if (totalReadingMinutes > 0) "第 $displayPage 页 · 专注 ${totalReadingMinutes}m" else "第 $displayPage 页",
                )
                views.setProgressBar(R.id.widgetReadingProgressBar, 100, progressPercent, false)

                // 封面加载 (支持本地图片与占位符)
                var coverLoaded = false
                val coverUrl = book.coverUrl
                if (!coverUrl.isNullOrBlank() &&
                    (coverUrl.startsWith("/") || coverUrl.startsWith("file:") || CoverImageHelper.isLanCoverKey(coverUrl))
                ) {
                    // 内网封面键（covers/…）在小组件主线程不做网络，仅读取已缓存到本机的封面文件
                    val bitmap = if (CoverImageHelper.isLanCoverKey(coverUrl)) {
                        CoverImageHelper.peekCachedCoverFile(context, coverUrl)?.let {
                            CoverImageHelper.decodeSampledBitmapFromFile(it.absolutePath, 140, 200)
                        }
                    } else {
                        CoverImageHelper.decodeSampledBitmapFromFile(coverUrl, 140, 200)
                    }
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widgetReadingCover, bitmap)
                        views.setViewVisibility(R.id.widgetReadingCover, View.VISIBLE)
                        views.setViewVisibility(R.id.widgetReadingCoverPlaceholder, View.GONE)
                        coverLoaded = true
                    }
                }

                if (!coverLoaded) {
                    views.setViewVisibility(R.id.widgetReadingCover, View.GONE)
                    views.setViewVisibility(R.id.widgetReadingCoverPlaceholder, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widgetReadingCoverPlaceholder,
                        "${book.mediaType.emoji}\n${book.title.take(4)}",
                    )
                }

                // 点击「📖 继续阅读 ➔」：进入作品详情
                val readIntent = BookDetailActivity.createIntent(context, book.id)
                val readPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 1,
                    readIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widgetContinueReadingBtn, readPendingIntent)

                // 点击卡片整体：进入书籍详情
                val detailIntent = BookDetailActivity.createIntent(context, book.id)
                val detailPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 2,
                    detailIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widgetCurrentlyReadingRoot, detailPendingIntent)
            } else {
                views.setTextViewText(R.id.widgetReadingBookTitle, "书架虚席以待")
                views.setTextViewText(R.id.widgetReadingBookAuthor, "点击开启第一段阅读痕迹")
                views.setTextViewText(R.id.widgetReadingStatusChip, "✨ 开启阅读")
                views.setTextViewText(R.id.widgetReadingProgressText, "0%")
                views.setProgressBar(R.id.widgetReadingProgressBar, 100, 0, false)
                views.setViewVisibility(R.id.widgetReadingCover, View.GONE)
                views.setViewVisibility(R.id.widgetReadingCoverPlaceholder, View.VISIBLE)
                views.setTextViewText(R.id.widgetReadingCoverPlaceholder, "📖\n阅痕")

                val mainIntent = Intent(context, MainActivity::class.java)
                val mainPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + 3,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widgetContinueReadingBtn, mainPendingIntent)
                views.setOnClickPendingIntent(R.id.widgetCurrentlyReadingRoot, mainPendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        databaseHelper.close()
    }

    companion object {
        fun refreshWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, CurrentlyReadingWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, CurrentlyReadingWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
