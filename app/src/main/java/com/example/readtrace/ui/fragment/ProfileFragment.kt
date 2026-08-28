package com.example.readtrace.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.readtrace.BackupActivity
import com.example.readtrace.BadgesActivity
import com.example.readtrace.Gallery3DActivity
import com.example.readtrace.R
import com.example.readtrace.TrashActivity
import com.example.readtrace.community.ui.CommunityActivity
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.MilestoneBadgeHelper
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.widget.MindprintRadarView
import java.io.File

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
    private lateinit var profileLanCoverPanel: View
    private lateinit var profileLanCoverSummary: TextView
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

    override fun onResume() {
        super.onResume()
        refreshProfileData()
        refreshLanCoverSummary()
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
        profileLanCoverPanel = view.findViewById(R.id.profileLanCoverPanel)
        profileLanCoverSummary = view.findViewById(R.id.profileLanCoverSummary)
        profileTrashPanel = view.findViewById(R.id.profileTrashPanel)
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

        profileLanCoverPanel.setOnClickListener {
            showLanCoverServerDialog()
        }

        profileTrashPanel.setOnClickListener {
            startActivity(Intent(requireContext(), TrashActivity::class.java))
        }

        listOfNotNull<View>(
            profileGalleryPanel, profileCommunityPanel,
            profileBadgePanel, profileBackupPanel, profileLanCoverPanel, profileTrashPanel,
        ).forEach { ViewAnimationHelper.attachSpringTouch(it) }
    }

    /** 内网封面服务地址配置弹窗：填电脑的内网 IP + 端口即可秒连 */
    private fun showLanCoverServerDialog() {
        val context = requireContext()
        val current = CoverImageHelper.getLanCoverBaseUrl(context)
        val input = EditText(context).apply {
            hint = "http://192.168.1.100:8000"
            setText(current)
            setSelection(text.length)
            setSingleLine()
            val pad = (resources.displayMetrics.density * 20).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }

        AlertDialog.Builder(context)
            .setTitle("🖥️ 内网封面服务")
            .setMessage(
                "在电脑上进入项目 cover_server 目录，双击「启动封面服务器.bat」，\n" +
                    "再用 ipconfig 查看电脑内网 IP，填入下方地址（手机需与电脑同一 Wi-Fi）：\n\n" +
                    "http://电脑内网IP:8000\n\n" +
                    "封面每张只需从内网加载一次，之后永久缓存在手机本地、离线秒开。"
            )
            .setView(input)
            .setPositiveButton("保存") { dialog, _ ->
                CoverImageHelper.setLanCoverBaseUrl(context, input.text.toString())
                refreshLanCoverSummary()
                dialog.dismiss()
            }
            .setNeutralButton("清空") { dialog, _ ->
                CoverImageHelper.setLanCoverBaseUrl(context, "")
                refreshLanCoverSummary()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 刷新内网封面服务状态摘要：已配置展示地址与本地缓存张数，未配置给出引导 */
    private fun refreshLanCoverSummary() {
        if (!this::profileLanCoverSummary.isInitialized) return
        val context = context ?: return
        val base = CoverImageHelper.getLanCoverBaseUrl(context)
        profileLanCoverSummary.text = if (base.isEmpty()) {
            "未配置——在电脑上启动封面服务并填入地址，预置封面即可内网秒开"
        } else {
            val cached = File(context.filesDir, "covers").listFiles { f -> f.name.startsWith("net_") }?.size ?: 0
            "服务地址：$base（已缓存 $cached 张，离线可看）"
        }
    }

    private fun refreshProfileData() {
        val allBooks = databaseHelper.getBooks()
        profileSummaryText.text = "已沉淀 ${allBooks.size} 部文化藏品 · 记录心智演化轨迹"

        val persona = databaseHelper.getAnnualMindprintPersona()
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

        val featuredCount = databaseHelper.getGalleryFeaturedWorks(24).size
        profileGallerySummary.text = if (featuredCount > 0) {
            "基于 OpenGL 的 360° 环形悬浮立体展台（已精选 $featuredCount 部藏品）"
        } else {
            "基于 OpenGL 的 360° 环形悬浮立体展台与全息封面流"
        }
    }
}
