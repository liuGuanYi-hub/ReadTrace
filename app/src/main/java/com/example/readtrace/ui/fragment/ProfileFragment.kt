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
    private lateinit var accountManager: com.example.readtrace.auth.CuratorAccountManager

    private lateinit var profileCuratorPassCard: com.example.readtrace.widget.CuratorPassCardView
    private lateinit var btnProfileAuthAction: TextView
    private lateinit var btnProfileSyncVault: View
    private lateinit var tvProfileSyncStatusText: TextView
    private lateinit var btnProfileSyncNow: TextView

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
        accountManager = com.example.readtrace.auth.CuratorAccountManager.getInstance(requireContext())

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

        // 渲染策展人通行卡与认证状态
        val account = accountManager.currentAccount ?: com.example.readtrace.model.CuratorAccount()
        val authStatus = accountManager.authStatus
        profileCuratorPassCard.bind(account, authStatus)

        btnProfileAuthAction.text = if (authStatus == com.example.readtrace.model.AuthStatus.AUTHENTICATED) {
            "⚙️ 通行证"
        } else {
            "✦ 策展人入驻"
        }

        if (account.lastSyncTime > 0L) {
            val dateStr = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(account.lastSyncTime))
            tvProfileSyncStatusText.text = "☁️ 云端保险库 · 上次同步: $dateStr"
        } else {
            tvProfileSyncStatusText.text = "☁️ 云端保险库 · 离线优先就绪"
        }

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
        profileCuratorPassCard = view.findViewById(R.id.profileCuratorPassCard)
        btnProfileAuthAction = view.findViewById(R.id.btnProfileAuthAction)
        btnProfileSyncVault = view.findViewById(R.id.btnProfileSyncVault)
        tvProfileSyncStatusText = view.findViewById(R.id.tvProfileSyncStatusText)
        btnProfileSyncNow = view.findViewById(R.id.btnProfileSyncNow)

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
        val profileChangelogPanel = view.findViewById<View>(R.id.profileChangelogPanel)
        profileChangelogPanel?.setOnClickListener {
            startActivity(com.example.readtrace.ChangelogActivity.createIntent(requireContext()))
        }
        bindVersionInfo(view)

        listOfNotNull(btnProfileAuthAction, btnProfileSyncVault).forEach {
            ViewAnimationHelper.attachSpringTouch(it)
        }
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
        val openAuthOrEdit = {
            if (accountManager.authStatus == com.example.readtrace.model.AuthStatus.AUTHENTICATED) {
                startActivity(Intent(requireContext(), com.example.readtrace.ui.CuratorProfileEditActivity::class.java))
            } else {
                startActivity(Intent(requireContext(), com.example.readtrace.ui.CuratorAuthActivity::class.java))
            }
        }

        btnProfileAuthAction.setOnClickListener { openAuthOrEdit() }
        profileCuratorPassCard.setOnClickListener { openAuthOrEdit() }

        btnProfileSyncVault.setOnClickListener {
            btnProfileSyncNow.text = "⏳ 同步中..."
            com.example.readtrace.sync.CloudSyncEngine.performSync(requireContext()) { result ->
                if (isAdded) {
                    btnProfileSyncNow.text = "🔄 立即同步"
                    android.widget.Toast.makeText(requireContext(), result.message, android.widget.Toast.LENGTH_SHORT).show()
                    refreshProfileDataAsync()
                }
            }
        }

        profileGalleryPanel.setOnClickListener {
            startActivity(Gallery3DActivity.createIntent(requireContext()))
        }

        // P12 策展人年度精神年鉴入口
        view?.findViewById<View>(R.id.profileChroniclePanel)?.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.readtrace.AnnualChronicleStudioActivity::class.java))
        }

        // 🖤 P14 OLED 曜石真黑开关
        view?.findViewById<View>(R.id.profileOledPanel)?.setOnClickListener {
            val enabled = !com.example.readtrace.util.ObsidianPureBlackEngine.isEnabled(requireContext())
            com.example.readtrace.util.ObsidianPureBlackEngine.setEnabled(requireContext(), enabled)
            view?.findViewById<TextView>(R.id.profileOledTitle)?.text =
                if (enabled) "🖤 OLED 曜石真黑 · 开" else "🖤 OLED 曜石真黑 · 关"
            com.example.readtrace.util.HapticFeedbackEngine.cartridgeSnap(requireContext())
            requireActivity().recreate()
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

        val profileMigrationPanel = view?.findViewById<View>(R.id.profileMigrationPanel)
        profileMigrationPanel?.setOnClickListener {
            startActivity(com.example.readtrace.DataMigrationActivity.createIntent(requireContext()))
        }

        val profileChangelogPanel = view?.findViewById<View>(R.id.profileChangelogPanel)
        profileChangelogPanel?.setOnClickListener {
            startActivity(com.example.readtrace.ChangelogActivity.createIntent(requireContext()))
        }

        listOfNotNull<View>(
            profileGalleryPanel, profileCommunityPanel,
            profileBadgePanel, profileMigrationPanel, profileBackupPanel, profileTrashPanel,
            profileChangelogPanel
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }
    }

}
