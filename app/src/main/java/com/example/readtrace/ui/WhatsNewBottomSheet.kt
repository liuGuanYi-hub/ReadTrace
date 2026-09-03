package com.example.readtrace.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.readtrace.ChangelogActivity
import com.example.readtrace.R
import com.example.readtrace.data.UserPreferencesManager
import com.example.readtrace.model.ChangelogRepository
import com.example.readtrace.util.HapticFeedbackEngine

/**
 * 新版本首次启动微视窗 (What\'s New Sheet)
 */
object WhatsNewBottomSheet {

    /**
     * 检查并仅在升级到新版本后首次弹出
     */
    fun showIfUpdated(context: Context) {
        val latest = ChangelogRepository.versionHistory.firstOrNull() ?: return

        if (UserPreferencesManager.getLastShownVersion(context) != latest.versionName) {
            showDialog(context, latest)
            UserPreferencesManager.setLastShownVersion(context, latest.versionName)
        }
    }

    private fun showDialog(context: Context, version: com.example.readtrace.model.ChangelogVersion) {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.layout_dialog_whats_new, null)
        dialog.setContentView(view)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // 大屏/平板限宽 420dp，避免按屏宽比例拉伸过度
            val maxWidth = (420 * context.resources.displayMetrics.density).toInt()
            setLayout(
                minOf((context.resources.displayMetrics.widthPixels * 0.92).toInt(), maxWidth),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        view.findViewById<TextView>(R.id.tvWhatsNewVersion).text = "阅痕 ReadTrace ${version.versionName}"
        view.findViewById<TextView>(R.id.tvWhatsNewSubtitle).text = version.tagTitle

        val listContainer = view.findViewById<LinearLayout>(R.id.whatsNewListContainer)
        listContainer.removeAllViews()

        // 只展示前 4 个最重要的亮点
        val density = context.resources.displayMetrics.density
        version.highlights.take(4).forEach { text ->
            val tv = TextView(context).apply {
                this.text = "✦ $text"
                textSize = 13f
                // 纳入日夜色牌：硬编码浅灰白在日间玻璃底上对比度仅 1.06:1，几乎不可读
                setTextColor(ContextCompat.getColor(context, R.color.readtrace_ink))
                setLineSpacing(0f, 1.2f)
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            }
            listContainer.addView(tv)
        }

        view.findViewById<View>(R.id.btnWhatsNewDismiss).setOnClickListener {
            HapticFeedbackEngine.lightClick(context)
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btnWhatsNewFullChangelog).setOnClickListener {
            HapticFeedbackEngine.lightClick(context)
            dialog.dismiss()
            context.startActivity(ChangelogActivity.createIntent(context))
        }

        dialog.show()
    }
}