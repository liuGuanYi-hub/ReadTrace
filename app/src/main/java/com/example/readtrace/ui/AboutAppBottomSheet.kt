package com.example.readtrace.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.readtrace.ChangelogActivity
import com.example.readtrace.R
import com.example.readtrace.model.ChangelogRepository
import com.example.readtrace.util.CrashReporter
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.ViewAnimationHelper

/**
 * 关于阅痕 · 品牌与精神档案微视窗 (About ReadTrace Modal)
 */
object AboutAppBottomSheet {

    fun show(context: Context) {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.layout_dialog_about, null)
        dialog.setContentView(view)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val maxWidth = (420 * context.resources.displayMetrics.density).toInt()
            setLayout(
                minOf((context.resources.displayMetrics.widthPixels * 0.92).toInt(), maxWidth),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 读取真实版本信息
        val tvVersionBadge = view.findViewById<TextView>(R.id.tvAboutVersionBadge)
        runCatching {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val vName = pInfo.versionName ?: "1.0.13"
            val vCode = pInfo.longVersionCode
            "v$vName · Code $vCode"
        }.onSuccess { versionDesc ->
            tvVersionBadge.text = versionDesc
        }.onFailure {
            tvVersionBadge.text = "v1.0.13 · 正式发布版"
        }

        // T3.4：长按版本徽标导出本地崩溃日志（隐藏入口，不占面板空间；无记录时给出空态提示）
        tvVersionBadge.setOnLongClickListener {
            HapticFeedbackEngine.lightClick(context)
            val shareIntent = CrashReporter.buildShareIntent(context)
            if (shareIntent == null) {
                Toast.makeText(context, "暂无崩溃记录", Toast.LENGTH_SHORT).show()
            } else {
                context.startActivity(Intent.createChooser(shareIntent, "导出崩溃日志"))
            }
            true
        }

        // 图标弹跳微动效
        val ivLogo = view.findViewById<ImageView>(R.id.ivAboutLogo)
        ivLogo.setOnClickListener {
            HapticFeedbackEngine.lightClick(context)
            ivLogo.animate()
                .scaleX(1.18f).scaleY(1.18f)
                .setDuration(120)
                .withEndAction {
                    ivLogo.animate().scaleX(1.0f).scaleY(1.0f).setDuration(180).start()
                }.start()
        }

        val btnChangelog = view.findViewById<View>(R.id.btnAboutChangelog)
        val btnWhatsNew = view.findViewById<View>(R.id.btnAboutWhatsNew)
        val btnDismiss = view.findViewById<View>(R.id.btnAboutDismiss)

        listOf(ivLogo, btnChangelog, btnWhatsNew, btnDismiss).forEach {
            ViewAnimationHelper.attachSpringTouch(it)
        }

        btnChangelog.setOnClickListener {
            HapticFeedbackEngine.lightClick(context)
            dialog.dismiss()
            context.startActivity(ChangelogActivity.createIntent(context))
        }

        btnWhatsNew.setOnClickListener {
            HapticFeedbackEngine.lightClick(context)
            dialog.dismiss()
            // 无论版本是否已显示过，均强制打开最新版本速递
            val latest = ChangelogRepository.versionHistory.firstOrNull()
            if (latest != null) {
                // 打开新版特性视窗
                val whatsNewDialog = Dialog(context)
                val wnView = LayoutInflater.from(context).inflate(R.layout.layout_dialog_whats_new, null)
                whatsNewDialog.setContentView(wnView)
                whatsNewDialog.window?.apply {
                    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    val maxWidth = (420 * context.resources.displayMetrics.density).toInt()
                    setLayout(
                        minOf((context.resources.displayMetrics.widthPixels * 0.92).toInt(), maxWidth),
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                wnView.findViewById<TextView>(R.id.tvWhatsNewVersion).text = "阅痕 ReadTrace ${latest.versionName}"
                wnView.findViewById<TextView>(R.id.tvWhatsNewSubtitle).text = latest.tagTitle
                val listContainer = wnView.findViewById<android.widget.LinearLayout>(R.id.whatsNewListContainer)
                listContainer.removeAllViews()
                val density = context.resources.displayMetrics.density
                latest.highlights.take(4).forEach { text ->
                    val tv = TextView(context).apply {
                        this.text = "✦ $text"
                        textSize = 13f
                        setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.readtrace_ink))
                        setLineSpacing(0f, 1.2f)
                        setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
                    }
                    listContainer.addView(tv)
                }
                wnView.findViewById<View>(R.id.btnWhatsNewDismiss).setOnClickListener {
                    HapticFeedbackEngine.lightClick(context)
                    whatsNewDialog.dismiss()
                }
                wnView.findViewById<View>(R.id.btnWhatsNewFullChangelog).setOnClickListener {
                    HapticFeedbackEngine.lightClick(context)
                    whatsNewDialog.dismiss()
                    context.startActivity(ChangelogActivity.createIntent(context))
                }
                whatsNewDialog.show()
            }
        }

        btnDismiss.setOnClickListener {
            HapticFeedbackEngine.lightClick(context)
            dialog.dismiss()
        }

        dialog.show()
    }
}
