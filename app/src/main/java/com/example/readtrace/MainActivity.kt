package com.example.readtrace

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.readtrace.ui.fragment.ConstellationFragment
import com.example.readtrace.ui.fragment.HubFragment
import com.example.readtrace.ui.fragment.LibraryFragment
import com.example.readtrace.ui.fragment.MemoirFragment
import com.example.readtrace.ui.fragment.ProfileFragment
import com.example.readtrace.util.ViewAnimationHelper

class MainActivity : AppCompatActivity() {

    private lateinit var tabHub: LinearLayout
    private lateinit var tabLibrary: LinearLayout
    private lateinit var tabGalaxy: LinearLayout
    private lateinit var tabMemoir: LinearLayout
    private lateinit var tabProfile: LinearLayout

    private lateinit var tabHubLabel: TextView
    private lateinit var tabLibraryLabel: TextView
    private lateinit var tabGalaxyLabel: TextView
    private lateinit var tabMemoirLabel: TextView
    private lateinit var tabProfileLabel: TextView

    private val fragments = mutableMapOf<Int, Fragment>()
    private var currentTabIndex = TAB_HUB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        initTabs()
        setupBackPressHandler()

        if (savedInstanceState == null) {
            selectTab(TAB_HUB)
        }
    }

    private fun initTabs() {
        tabHub = findViewById(R.id.tabHub)
        tabLibrary = findViewById(R.id.tabLibrary)
        tabGalaxy = findViewById(R.id.tabGalaxy)
        tabMemoir = findViewById(R.id.tabMemoir)
        tabProfile = findViewById(R.id.tabProfile)

        tabHubLabel = findViewById(R.id.tabHubLabel)
        tabLibraryLabel = findViewById(R.id.tabLibraryLabel)
        tabGalaxyLabel = findViewById(R.id.tabGalaxyLabel)
        tabMemoirLabel = findViewById(R.id.tabMemoirLabel)
        tabProfileLabel = findViewById(R.id.tabProfileLabel)

        tabHub.setOnClickListener { selectTab(TAB_HUB) }
        tabLibrary.setOnClickListener { selectTab(TAB_LIBRARY) }
        tabGalaxy.setOnClickListener { selectTab(TAB_GALAXY) }
        tabMemoir.setOnClickListener { selectTab(TAB_MEMOIR) }
        tabProfile.setOnClickListener { selectTab(TAB_PROFILE) }

        listOf(tabHub, tabLibrary, tabGalaxy, tabMemoir, tabProfile).forEach {
            ViewAnimationHelper.attachSpringTouch(it, 0.92f)
        }
    }

    private fun selectTab(index: Int) {
        if (currentTabIndex == index && fragments[index]?.isAdded == true) return

        val transaction = supportFragmentManager.beginTransaction()

        // 隐藏上一个 Fragment
        fragments[currentTabIndex]?.let {
            if (it.isAdded) transaction.hide(it)
        }

        // 显示或添加当前 Fragment
        var targetFragment = fragments[index]
        if (targetFragment == null) {
            targetFragment = createFragment(index)
            fragments[index] = targetFragment
            transaction.add(R.id.fragmentContainer, targetFragment, "tag_tab_$index")
        } else {
            transaction.show(targetFragment)
        }

        transaction.commitAllowingStateLoss()
        currentTabIndex = index
        updateTabStyles(index)
    }

    private fun createFragment(index: Int): Fragment {
        return when (index) {
            TAB_HUB -> HubFragment()
            TAB_LIBRARY -> LibraryFragment()
            TAB_GALAXY -> ConstellationFragment()
            TAB_MEMOIR -> MemoirFragment()
            TAB_PROFILE -> ProfileFragment()
            else -> HubFragment()
        }
    }

    private fun updateTabStyles(selectedIndex: Int) {
        val tabViews = listOf(
            Triple(tabHub, tabHubLabel, TAB_HUB),
            Triple(tabLibrary, tabLibraryLabel, TAB_LIBRARY),
            Triple(tabGalaxy, tabGalaxyLabel, TAB_GALAXY),
            Triple(tabMemoir, tabMemoirLabel, TAB_MEMOIR),
            Triple(tabProfile, tabProfileLabel, TAB_PROFILE),
        )

        tabViews.forEach { (tabLayout, label, index) ->
            val isSelected = index == selectedIndex
            tabLayout.setBackgroundResource(if (isSelected) R.drawable.bg_nav_tab_active else 0)
            label.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (isSelected) R.color.readtrace_ink else R.color.readtrace_muted,
                ),
            )
            label.typeface = if (isSelected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTabIndex != TAB_HUB) {
                    selectTab(TAB_HUB)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    companion object {
        const val TAB_HUB = 0
        const val TAB_LIBRARY = 1
        const val TAB_GALAXY = 2
        const val TAB_MEMOIR = 3
        const val TAB_PROFILE = 4
    }
}
