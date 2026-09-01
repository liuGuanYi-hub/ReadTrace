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
import com.example.readtrace.ChangelogActivity
import com.example.readtrace.R
import com.example.readtrace.model.ChangelogRepository
import com.example.readtrace.util.HapticFeedbackEngine

/**
 * 新版本首次启动微视窗 (What\'s New Sheet)
 */
object WhatsNewBottomSheet {

    private const val PREF_NAME = "readtrace_version_prefs"
    private const val KEY_LAST_SHOWN_VERSION = "key_last_shown_version"

    /**
     * 检查并仅在升级到新版本后首次弹出
     */
    fun showIfUpdated(context: Context) {
        val latest = ChangelogRepository.versionHistory.firstOrNull() ?: return
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastShown = prefs.getString(KEY_LAST_SHOWN_VERSION, "")

        if (lastShown != latest.versionName) {
            showDialog(context, latest)
            prefs.edit().putString(KEY_LAST_SHOWN_VERSION, latest.versionName).apply()
        }
    }

    private fun showDialog(context: Context, version: com.example.readtrace.model.ChangelogVersion) {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.layout_dialog_whats_new, null)
        dialog.setContentView(view)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        view.findViewById<TextView>(R.id.tvWhatsNewVersion).text = "阅痕 ReadTrace "
        view.findViewById<TextView>(R.id.tvWhatsNewSubtitle).text = version.tagTitle

        val listContainer = view.findViewById<LinearLayout>(R.id.whatsNewListContainer)
        listContainer.removeAllViews()

        // 只展示前 4 个最重要的亮点
        val density = context.resources.displayMetrics.density
        version.highlights.take(4).forEach { text ->
            val tv = TextView(context).apply {
                this.text = "✦ "
                textSize = 13f
                setTextColor(Color.parseColor("#E0E6ED"))
                setLineSpacing(0f, 1.2f)
                setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
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