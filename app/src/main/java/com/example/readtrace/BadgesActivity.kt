package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.MilestoneBadge
import com.example.readtrace.util.MilestoneBadgeHelper
import com.example.readtrace.util.FloatingBack

class BadgesActivity : AppCompatActivity() {
    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var badgeOverallCount: TextView
    private lateinit var badgeOverallProgressBar: ProgressBar
    private lateinit var badgeOverallPrompt: TextView
    private lateinit var badgesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_badges)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.badgesRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper.getInstance(this)
        badgeOverallCount = findViewById(R.id.badgeOverallCount)
        badgeOverallProgressBar = findViewById(R.id.badgeOverallProgressBar)
        badgeOverallPrompt = findViewById(R.id.badgeOverallPrompt)
        badgesContainer = findViewById(R.id.badgesContainer)

        FloatingBack.install(this)

        renderBadges()

        findViewById<View>(R.id.badgesContent)
            .startAnimation(AnimationUtils.loadAnimation(this, R.anim.home_enter))
    }

    private fun renderBadges() {
        badgesContainer.removeAllViews()
        val badges = MilestoneBadgeHelper.calculateBadges(databaseHelper)
        val unlockedCount = badges.count { it.isUnlocked }
        val totalCount = badges.size

        badgeOverallCount.text = "$unlockedCount / $totalCount"
        val progressPercent = if (totalCount > 0) (unlockedCount * 100) / totalCount else 0
        badgeOverallProgressBar.progress = progressPercent

        badgeOverallPrompt.text = if (unlockedCount == totalCount) {
            "恭喜！你已达成全部阅读里程碑，卓越的阅读者！"
        } else {
            "已点亮 $unlockedCount 枚勋章，还有 ${totalCount - unlockedCount} 项等待探索。"
        }

        badges.forEach { badge ->
            val card = LayoutInflater.from(this).inflate(R.layout.item_badge_card, badgesContainer, false)
            card.findViewById<TextView>(R.id.badgeIconEmoji).text = badge.iconEmoji
            card.findViewById<TextView>(R.id.badgeTitle).text = badge.title
            card.findViewById<TextView>(R.id.badgeCategory).text = badge.category
            card.findViewById<TextView>(R.id.badgeDescription).text = badge.description

            val progressBar = card.findViewById<ProgressBar>(R.id.badgeProgressBar)
            val progressText = card.findViewById<TextView>(R.id.badgeProgressText)
            val statusPill = card.findViewById<TextView>(R.id.badgeStatusPill)

            val itemPercent = if (badge.maxProgress > 0) {
                (badge.currentProgress * 100) / badge.maxProgress
            } else 0
            progressBar.progress = itemPercent
            progressText.text = "${badge.currentProgress} / ${badge.maxProgress}"

            if (badge.isUnlocked) {
                statusPill.setText(R.string.badge_status_unlocked)
                statusPill.setBackgroundResource(R.drawable.bg_status_pill)
                statusPill.setTextColor(ContextCompat.getColor(this, R.color.readtrace_accent))
                card.alpha = 1.0f
            } else {
                statusPill.setText(R.string.badge_status_locked)
                statusPill.setBackgroundResource(R.drawable.bg_secondary_button)
                statusPill.setTextColor(ContextCompat.getColor(this, R.color.readtrace_muted))
                card.alpha = 0.85f
            }

            badgesContainer.addView(card)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, BadgesActivity::class.java)
    }
}
