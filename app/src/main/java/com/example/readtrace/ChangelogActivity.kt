package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.model.ChangelogRepository
import com.example.readtrace.model.ChangelogVersion
import com.example.readtrace.util.FloatingBack
import com.example.readtrace.util.ViewAnimationHelper

/**
 * 版本演进纪要全景时间轴 Activity (Version Changelog Timeline)
 */
class ChangelogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_changelog)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.changelogRoot)) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        FloatingBack.install(this)
        renderTimeline()
    }

    private fun renderTimeline() {
        val container = findViewById<LinearLayout>(R.id.changelogTimelineContainer) ?: return
        container.removeAllViews()

        val history = ChangelogRepository.versionHistory
        val inflater = LayoutInflater.from(this)

        history.forEachIndexed { index, item ->
            val itemView = inflater.inflate(R.layout.item_changelog_card, container, false)

            itemView.findViewById<TextView>(R.id.tvChangelogVersion).text = item.versionName
            itemView.findViewById<TextView>(R.id.tvChangelogDate).text = item.releaseDate
            itemView.findViewById<TextView>(R.id.tvChangelogTitle).text = item.tagTitle

            val latestBadge = itemView.findViewById<View>(R.id.tvChangelogLatestBadge)
            latestBadge.visibility = if (item.isLatest) View.VISIBLE else View.GONE

            val lineView = itemView.findViewById<View>(R.id.timelineNodeLine)
            if (index == history.size - 1) {
                lineView.visibility = View.INVISIBLE
            }

            val highlightsContainer = itemView.findViewById<LinearLayout>(R.id.changelogHighlightsContainer)
            highlightsContainer.removeAllViews()

            item.highlights.forEach { text ->
                val tv = TextView(this).apply {
                    this.text = text
                    textSize = 13.5f
                    // 纳入日夜色牌：硬编码淡蓝灰在日间白卡上对比度严重不足
                    setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.readtrace_ink))
                    setLineSpacing(0f, 1.3f)
                    val mBottom = dpToPx(5)
                    setPadding(0, 0, 0, mBottom)
                }
                highlightsContainer.addView(tv)
            }

            container.addView(itemView)
            ViewAnimationHelper.staggerFadeIn(itemView, index)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, ChangelogActivity::class.java)
    }
}