package com.example.readtrace.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.readtrace.BackupActivity
import com.example.readtrace.BadgesActivity
import com.example.readtrace.Gallery3DActivity
import com.example.readtrace.R
import com.example.readtrace.TrashActivity
import com.example.readtrace.community.ui.CommunityActivity
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.util.MilestoneBadgeHelper
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.MindprintRadarView

class ProfileFragment : Fragment() {

    private lateinit var databaseHelper: BookDatabaseHelper

    private lateinit var profileSummaryText: TextView
    private lateinit var annualPersonaPanel: View
    private lateinit var annualPersonaBadge: TextView
    private lateinit var annualPersonaDesc: TextView
    private lateinit var annualMindprintRadar: MindprintRadarView

    private lateinit var profileGalleryPanel: View
    private lateinit var profileGallerySummary: TextView
    private lateinit var profileCommunityPanel: View
    private lateinit var profileBadgePanel: View
    private lateinit var profileBadgeSummary: TextView
    private lateinit var profileBackupPanel: View
    private lateinit var profileTrashPanel: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        databaseHelper = BookDatabaseHelper.getInstance(requireContext())

        initViews(view)
        setupListeners()
    }

    private val refreshExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onResume() {
        super.onResume()
        refreshProfileDataAsync()
    }

    /** 个人页聚合查询较重（年度人格跨表聚合 + 画廊精选），移至后台线程，渲染回主线程 */
    private fun refreshProfileDataAsync() {
        refreshExecutor.execute {
            val allBooks = databaseHelper.getCachedBooks()
            val persona = databaseHelper.getAnnualMindprintPersona()
            val featuredCount = databaseHelper.getGalleryFeaturedWorks(24).size
            refreshHandler.post {
                if (!isAdded || view == null) return@post
                renderProfileData(allBooks, persona, featuredCount)
            }
        }
    }

    private fun renderProfileData(allBooks: List<com.example.readtrace.model.Book>, persona: com.example.readtrace.model.ReadingPersona?, featuredCount: Int) {
        profileSummaryText.text = "已沉淀 ${allBooks.size} 部文化藏品 · 记录心智演化轨迹"

        if (persona != null) {
            annualPersonaPanel.visibility = View.VISIBLE
            annualPersonaBadge.text = persona.personaTitle
            annualPersonaDesc.text = "${persona.personaDesc}（已深度量化分析 ${persona.finishedBooksCount} 部作品）"
            annualMindprintRadar.setMindprint(persona.avgMindprint, animate = false)
        } else {
            annualPersonaPanel.visibility = View.GONE
        }

        val badges = MilestoneBadgeHelper.calculateBadges(databaseHelper)
        val unlockedCount = badges.count { it.isUnlocked }
        profileBadgeSummary.text = "已解锁 $unlockedCount / ${badges.size} 枚专属精神荣誉勋章"
        profileGallerySummary.text = if (featuredCount > 0) {
            "基于 OpenGL 的 360° 环形悬浮立体展台（已精选 $featuredCount 部藏品）"
        } else {
            "基于 OpenGL 的 360° 环形悬浮立体展台与全息封面流"
        }
    }

    private fun initViews(view: View) {
        profileSummaryText = view.findViewById(R.id.profileSummaryText)
        annualPersonaPanel = view.findViewById(R.id.annualPersonaPanel)
        annualPersonaBadge = view.findViewById(R.id.annualPersonaBadge)
        annualPersonaDesc = view.findViewById(R.id.annualPersonaDesc)
        annualMindprintRadar = view.findViewById(R.id.annualMindprintRadar)

        profileGalleryPanel = view.findViewById(R.id.profileGalleryPanel)
        profileGallerySummary = view.findViewById(R.id.profileGallerySummary)
        profileCommunityPanel = view.findViewById(R.id.profileCommunityPanel)
        profileBadgePanel = view.findViewById(R.id.profileBadgePanel)
        profileBadgeSummary = view.findViewById(R.id.profileBadgeSummary)
        profileBackupPanel = view.findViewById(R.id.profileBackupPanel)
        profileTrashPanel = view.findViewById(R.id.profileTrashPanel)
        bindVersionInfo(view)
    }

    /** v4.2.15：版本信息展示（PackageManager 读取，不依赖 BuildConfig） */
    private fun bindVersionInfo(view: View) {
        val versionText = view.findViewById<TextView>(R.id.profileVersionText)
        runCatching {
            val info = requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0)
            "阅痕 ReadTrace v${info.versionName}（versionCode ${info.longVersionCode}）\n纯本地数据掌控 · 封面与条目数据来自 Bangumi / 国内 CDN"
        }.onSuccess {
            versionText.text = it
        }.onFailure {
            versionText.text = "阅痕 ReadTrace"
        }
    }

    private fun setupListeners() {
        profileGalleryPanel.setOnClickListener {
            startActivity(Gallery3DActivity.createIntent(requireContext()))
        }

        profileCommunityPanel.setOnClickListener {
            startActivity(CommunityActivity.createIntent(requireContext()))
        }

        profileBadgePanel.setOnClickListener {
            startActivity(BadgesActivity.createIntent(requireContext()))
        }

        profileBackupPanel.setOnClickListener {
            startActivity(Intent(requireContext(), BackupActivity::class.java))
        }

        profileTrashPanel.setOnClickListener {
            startActivity(Intent(requireContext(), TrashActivity::class.java))
        }

        listOfNotNull<View>(
            profileGalleryPanel, profileCommunityPanel,
            profileBadgePanel, profileBackupPanel, profileTrashPanel,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }
    }

}
