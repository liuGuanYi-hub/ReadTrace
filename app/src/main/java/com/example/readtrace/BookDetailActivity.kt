package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextUtils
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.model.Note
import com.example.readtrace.model.NoteType
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.ViewAnimationHelper
import com.example.readtrace.util.ElegantChoiceDialog
import com.example.readtrace.util.ElegantConfirmDialog
import com.example.readtrace.util.ElegantFormDialog
import com.example.readtrace.util.FloatingBack
import java.text.DecimalFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class BookDetailActivity : AppCompatActivity() {
    private lateinit var databaseHelper: BookDatabaseHelper
    private var bookId: Long = NO_BOOK_ID
    private var currentBook: Book? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_book_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper(this)
        bookId = intent.getLongExtra(EXTRA_BOOK_ID, NO_BOOK_ID)
        if (bookId == NO_BOOK_ID) {
            showMissingBookAndClose()
            return
        }

        val coverContainer = findViewById<View>(R.id.detailCoverContainer)
        if (coverContainer != null) {
            val gyroscopeHelper = com.example.readtrace.util.GyroscopeParallaxHelper(this)
            gyroscopeHelper.bind3DParallax(coverContainer, maxRotation = 14f, maxTranslation = 18f)
            gyroscopeHelper.bindLifecycle(lifecycle)
        }

        FloatingBack.install(this) { supportFinishAfterTransition() }
        findViewById<View>(R.id.detailEditButton).setOnClickListener {
            startActivity(AddBookActivity.createEditIntent(this, bookId))
        }
        findViewById<View>(R.id.detailArchiveButton).setOnClickListener {
            confirmArchive()
        }
        findViewById<View>(R.id.detailNotesAddButton).setOnClickListener {
            startActivity(AddNoteActivity.createAddIntent(this, bookId))
        }
        findViewById<View>(R.id.detailAddCharButton).setOnClickListener {
            showAddCharacterDialog()
        }
        findViewById<View>(R.id.detailAddOutlineButton).setOnClickListener {
            showAddOutlineDialog()
        }
        findViewById<View>(R.id.detailEditMindprintBtn).setOnClickListener {
            showEditMindprintDialog()
        }
        findViewById<View>(R.id.detailAddLocationBtn).setOnClickListener {
            showAddLocationDialog()
        }

        // 📑 轻量组件化多板块快捷导航条
        val navOverview = findViewById<TextView>(R.id.navTabOverview)
        val navTimeline = findViewById<TextView>(R.id.navTabTimeline)
        val navCharacters = findViewById<TextView>(R.id.navTabCharacters)
        val navNotes = findViewById<TextView>(R.id.navTabNotes)
        val mainScrollView = findViewById<android.widget.ScrollView>(R.id.detailScrollView)

        fun updateNavTabs(activeTab: TextView) {
            listOf(navOverview, navTimeline, navCharacters, navNotes).forEach {
                val isSelected = it == activeTab
                it.setBackgroundResource(if (isSelected) R.drawable.bg_detail_tab_selected else R.drawable.bg_status_chip)
                it.setTextColor(getColor(if (isSelected) R.color.white else R.color.readtrace_ink))
                if (isSelected) ViewAnimationHelper.playCardBounce(it)
            }
        }

        // 计算目标视图相对滚动内容根布局的绝对纵向偏移（跨越任意嵌套层级与横向滚动容器）
        val scrollContentRoot = mainScrollView.getChildAt(0)
        fun View.offsetTopToRoot(): Int {
            var offset = 0
            var current: View? = this
            while (current != null && current !== scrollContentRoot) {
                offset += current.top
                current = current.parent as? View
            }
            return offset
        }

        navOverview.setOnClickListener {
            updateNavTabs(navOverview)
            mainScrollView.smoothScrollTo(0, 0)
        }
        navTimeline.setOnClickListener {
            updateNavTabs(navTimeline)
            val y = findViewById<View>(R.id.detailTimelineSection).offsetTopToRoot()
            mainScrollView.smoothScrollTo(0, y)
        }
        navCharacters.setOnClickListener {
            updateNavTabs(navCharacters)
            val y = findViewById<View>(R.id.detailSectionCharTitle).offsetTopToRoot()
            mainScrollView.smoothScrollTo(0, y)
        }
        navNotes.setOnClickListener {
            updateNavTabs(navNotes)
            val y = findViewById<View>(R.id.detailSectionNotesTitle).offsetTopToRoot()
            mainScrollView.smoothScrollTo(0, y)
        }

        // ⏳ 时间轴筛选按钮
        val filterAll = findViewById<TextView>(R.id.detailTimelineFilterAll)
        val filterSessions = findViewById<TextView>(R.id.detailTimelineFilterSessions)
        val filterNotes = findViewById<TextView>(R.id.detailTimelineFilterNotes)

        fun updateFilterUi(filter: TimelineFilter) {
            currentTimelineFilter = filter
            val map = listOf(
                filterAll to TimelineFilter.ALL,
                filterSessions to TimelineFilter.SESSIONS_ONLY,
                filterNotes to TimelineFilter.NOTES_ONLY,
            )
            map.forEach { (view, type) ->
                val isSel = type == filter
                view.setBackgroundResource(if (isSel) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
                view.setTextColor(getColor(if (isSel) R.color.white else R.color.readtrace_ink))
            }
            currentBook?.let { refreshTimelineOnly(it) }
        }

        filterAll.setOnClickListener { updateFilterUi(TimelineFilter.ALL) }
        filterSessions.setOnClickListener { updateFilterUi(TimelineFilter.SESSIONS_ONLY) }
        filterNotes.setOnClickListener { updateFilterUi(TimelineFilter.NOTES_ONLY) }

        findViewById<View>(R.id.detailTimelineExportBtn).setOnClickListener {
            exportTimelineAsLongImage()
        }
        findViewById<View>(R.id.detailTopologyLocateBtn)?.setOnClickListener {
            startActivity(MindprintConstellationActivity.createIntent(this, bookId))
        }
        findViewById<View>(R.id.detailCompareMindprintBtn).setOnClickListener {
            showCompareMindprintDialog()
        }
        findViewById<View>(R.id.detailExportWidgetBtn).setOnClickListener {
            showSelectWidgetThemeDialog()
        }

        // 注入 iOS 级 Q 弹手势触觉反馈
        listOfNotNull<View>(
            findViewById(R.id.detailEditButton),
            findViewById(R.id.detailArchiveButton),
            findViewById(R.id.detailQuotePosterButton),
            findViewById(R.id.detailRead3DButton),
            findViewById(R.id.detailAddCharButton),
            findViewById(R.id.detailAddOutlineButton),
            findViewById(R.id.detailAddLocationBtn),
            findViewById(R.id.detailTopologyLocateBtn),
            findViewById(R.id.detailEditMindprintBtn),
            findViewById(R.id.detailCompareMindprintBtn),
            findViewById(R.id.detailExportWidgetBtn),
            findViewById(R.id.detailTimelineExportBtn),
            navOverview, navTimeline, navCharacters, navNotes,
            filterAll, filterSessions, filterNotes,
        ).forEach { com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(it) }

        findViewById<View>(R.id.detailContent)
            .startAnimation(AnimationUtils.loadAnimation(this, R.anim.home_enter))
    }

    override fun onResume() {
        super.onResume()
        if (bookId == NO_BOOK_ID) return
        val book = databaseHelper.getBook(bookId)
        if (book == null) {
            showMissingBookAndClose()
            return
        }
        currentBook = book
        renderBook(book)
        renderCollection(book)
        renderNotes(databaseHelper.getNotes(bookId))
        renderCharacters(databaseHelper.getCharacters(bookId))
        renderOutlines(databaseHelper.getOutlines(bookId))
        renderMindprint(databaseHelper.getMindprint(bookId))
        renderLocations(databaseHelper.getLocations(bookId))
        renderTimeline(
            book,
            databaseHelper.getReadingSessions(bookId),
            databaseHelper.getNotes(bookId),
            databaseHelper.getLocations(bookId),
            databaseHelper.getOutlines(bookId),
        )
        renderSimilarBooks(book)
    }

    private fun renderCollection(book: Book) {
        val hasCollectionInfo = !book.buyChannel.isNullOrBlank() ||
            !book.shelfLocation.isNullOrBlank() ||
            !book.bindingType.isNullOrBlank() ||
            book.buyPrice != null

        findViewById<View>(R.id.detailCollectionCard).visibility =
            if (book.mediaType == MediaType.BOOK && hasCollectionInfo) View.VISIBLE else View.GONE

        findViewById<TextView>(R.id.detailBuyChannel).text = valueOrFallback(book.buyChannel)
        findViewById<TextView>(R.id.detailShelfLocation).text = valueOrFallback(book.shelfLocation)
        findViewById<TextView>(R.id.detailBindingType).text = valueOrFallback(book.bindingType)
        findViewById<TextView>(R.id.detailBuyPrice).text = book.buyPrice?.let {
            String.format(Locale.getDefault(), "¥ %.2f", it)
        } ?: getString(R.string.not_recorded)
    }


    private fun renderCharacters(characters: List<com.example.readtrace.model.BookCharacter>) {
        val container = findViewById<LinearLayout>(R.id.detailCharsContainer)
        val emptyView = findViewById<TextView>(R.id.detailCharsEmpty)
        container.removeAllViews()

        if (characters.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        container.visibility = View.VISIBLE

        characters.forEach { char ->
            val item = layoutInflater.inflate(R.layout.item_character_card, container, false)
            item.findViewById<TextView>(R.id.charAvatarEmoji).text = char.avatarEmoji
            item.findViewById<TextView>(R.id.charName).text = char.name
            item.findViewById<TextView>(R.id.charRoleTitle).text = char.roleTitle ?: "人物角色"

            val descView = item.findViewById<TextView>(R.id.charDescription)
            if (char.description.isNullOrBlank()) {
                descView.visibility = View.GONE
            } else {
                descView.visibility = View.VISIBLE
                descView.text = char.description
            }

            val relView = item.findViewById<TextView>(R.id.charRelationship)
            if (char.relationship.isNullOrBlank()) {
                relView.visibility = View.GONE
            } else {
                relView.visibility = View.VISIBLE
                relView.text = "🔗 核心羁绊：${char.relationship}"
            }

            val delBtn = item.findViewById<View>(R.id.charDeleteBtn)
            delBtn.setOnClickListener {
                ElegantConfirmDialog.show(
                    activity = this,
                    title = "👤 删除人物角色",
                    message = "确定要从人物谱中移除「${char.name}」吗？",
                    confirmText = "删除",
                    isDanger = true,
                    onConfirm = {
                        databaseHelper.deleteCharacter(char.id)
                        renderCharacters(databaseHelper.getCharacters(bookId))
                    },
                )
            }
            com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(item, 0.97f)
            com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(delBtn)

            container.addView(item)
        }
    }

    private fun showAddCharacterDialog() {
        ElegantFormDialog.show(
            this,
            title = "👥 添加人物角色",
            confirmText = "保存角色",
            fields = listOf(
                ElegantFormDialog.Field("name", "👤 角色姓名", "如：小狐狸", required = true),
                ElegantFormDialog.Field("role", "🎭 身份 / 阵营 / 头衔", "如：启蒙导师 · 麦田守望者"),
                ElegantFormDialog.Field("emoji", "✨ 角色 Emoji", "如：🦊 / 👑 / 🌹", preset = "👤"),
                ElegantFormDialog.Field("desc", "📖 人物生平与性格简述", "他们是谁，经历了什么...", minLines = 3),
                ElegantFormDialog.Field("rel", "🔗 与主角或其他人物的核心羁绊", "如：亦师亦友 / 宿命之敌...", minLines = 2),
            ),
        ) { v ->
            val name = v.getValue("name")
            if (name.isNotEmpty()) {
                val character = com.example.readtrace.model.BookCharacter(
                    bookId = bookId,
                    name = name,
                    roleTitle = v.getValue("role").ifBlank { null },
                    avatarEmoji = v.getValue("emoji").ifBlank { "👤" },
                    description = v.getValue("desc").ifBlank { null },
                    relationship = v.getValue("rel").ifBlank { null },
                )
                databaseHelper.insertCharacter(character)
                renderCharacters(databaseHelper.getCharacters(bookId))
            }
        }
    }

    private fun renderOutlines(outlines: List<com.example.readtrace.model.BookOutline>) {
        val container = findViewById<LinearLayout>(R.id.detailOutlinesContainer)
        val emptyView = findViewById<TextView>(R.id.detailOutlinesEmpty)
        container.removeAllViews()

        if (outlines.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        container.visibility = View.VISIBLE

        outlines.forEachIndexed { idx, outline ->
            val item = layoutInflater.inflate(R.layout.item_outline_card, container, false)
            item.findViewById<TextView>(R.id.outlineOrderBadge).text = "第 ${outline.chapterOrder} 章节"
            item.findViewById<TextView>(R.id.outlineTitle).text = outline.title
            item.findViewById<TextView>(R.id.outlineSummary).text = outline.summary

            val mindMapContainer = item.findViewById<View>(R.id.outlineMindMapContainer)
            val keyTakeawaysView = item.findViewById<TextView>(R.id.outlineKeyTakeaways)
            if (outline.keyTakeaways.isNullOrBlank()) {
                mindMapContainer.visibility = View.GONE
            } else {
                mindMapContainer.visibility = View.VISIBLE
                keyTakeawaysView.text = "脑图要点：${outline.keyTakeaways}"
            }

            val delBtn = item.findViewById<View>(R.id.outlineDeleteBtn)
            delBtn.setOnClickListener {
                ElegantConfirmDialog.show(
                    activity = this,
                    title = "📖 删除章节大纲",
                    message = "确定要删除「${outline.title}」的大纲吗？",
                    confirmText = "删除",
                    isDanger = true,
                    onConfirm = {
                        databaseHelper.deleteOutline(outline.id)
                        renderOutlines(databaseHelper.getOutlines(bookId))
                    },
                )
            }
            com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(item, 0.97f)
            com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(delBtn)

            container.addView(item)
        }
    }

    private fun showAddOutlineDialog() {
        ElegantFormDialog.show(
            this,
            title = "🗺️ 添加章节大纲与脑图",
            confirmText = "保存大纲",
            fields = listOf(
                ElegantFormDialog.Field(
                    "order", "🔢 章节序号", "如：1",
                    preset = "${databaseHelper.getOutlines(bookId).size + 1}",
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER,
                ),
                ElegantFormDialog.Field("title", "📖 章节名称", "如：荒原中的相遇与羊的肖像", required = true),
                ElegantFormDialog.Field("summary", "🧭 核心情节 / 大纲概要", "这一章发生了什么，推进了什么...", minLines = 3, required = true),
                ElegantFormDialog.Field("takeaways", "💡 思想精髓 / 脑图脉络要点 (选填)", "值得反复回味的要点...", minLines = 2),
            ),
        ) { v ->
            val title = v.getValue("title")
            val summary = v.getValue("summary")
            val order = v.getValue("order").toIntOrNull() ?: 1
            if (title.isNotEmpty() && summary.isNotEmpty()) {
                val outline = com.example.readtrace.model.BookOutline(
                    bookId = bookId,
                    chapterOrder = order,
                    title = title,
                    summary = summary,
                    keyTakeaways = v.getValue("takeaways").ifBlank { null },
                )
                databaseHelper.insertOutline(outline)
                renderOutlines(databaseHelper.getOutlines(bookId))
            }
        }
    }

    private fun renderMindprint(mindprint: com.example.readtrace.model.BookMindprint) {
        val avg = mindprint.averageScore()
        val tag = when {
            avg >= 9.0 -> "殿堂神作 · 精神灯塔"
            avg >= 8.0 -> "深刻思辨 · 高度共鸣"
            avg >= 7.0 -> "优秀佳作 · 值得品读"
            else -> "个性小众 · 独特印记"
        }
        findViewById<TextView>(R.id.detailMindprintSummary).text =
            String.format(Locale.getDefault(), "🌟 综合认知深度指数：%.1f / 10 · %s", avg, tag)

        // 驱动原生 Canvas 蛛网雷达图绘制
        findViewById<com.example.readtrace.widget.MindprintRadarView>(R.id.detailMindprintRadar)
            ?.setMindprint(mindprint, animate = true)

        findViewById<TextView>(R.id.detailScoreDepth).text = String.format(Locale.getDefault(), "%.1f / 10", mindprint.depthScore)
        findViewById<TextView>(R.id.detailScoreArtistry).text = String.format(Locale.getDefault(), "%.1f / 10", mindprint.artistryScore)
        findViewById<TextView>(R.id.detailScoreEmotion).text = String.format(Locale.getDefault(), "%.1f / 10", mindprint.emotionScore)
        findViewById<TextView>(R.id.detailScoreLogic).text = String.format(Locale.getDefault(), "%.1f / 10", mindprint.logicScore)
        findViewById<TextView>(R.id.detailScoreDifficulty).text = String.format(Locale.getDefault(), "%.1f / 10 (门槛)", mindprint.difficultyScore)
        findViewById<TextView>(R.id.detailScoreHealing).text = String.format(Locale.getDefault(), "%.1f / 10", mindprint.healingScore)
    }

    private fun showEditMindprintDialog() {
        val current = databaseHelper.getMindprint(bookId)
        val density = resources.displayMetrics.density
        fun px(v: Int) = (v * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(22), px(18), px(22), px(20))
            setBackgroundResource(R.drawable.bg_elegant_dialog)
        }
        container.addView(TextView(this).apply {
            text = "🕸️ 调整六维心智评分"
            textSize = 16.5f
            setTextColor(getColor(R.color.readtrace_ink))
            setPadding(0, 0, 0, px(4))
        })
        container.addView(TextView(this).apply {
            text = "拖动滑条即可，松手即保存"
            textSize = 11.5f
            setTextColor(getColor(R.color.readtrace_muted))
            setPadding(0, 0, 0, px(6))
        })

        data class Row(val seek: android.widget.SeekBar, val valueView: TextView, val label: String)
        val rows = mutableListOf<Row>()

        fun addRow(label: String, score: Double) {
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, px(10), 0, px(2))
            }
            head.addView(TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(getColor(R.color.readtrace_ink))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val valueView = TextView(this).apply {
                text = String.format(java.util.Locale.getDefault(), "%.1f", score)
                textSize = 13.5f
                setTextColor(getColor(R.color.readtrace_accent))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            head.addView(valueView)
            container.addView(head)

            val seek = android.widget.SeekBar(this).apply {
                max = 90
                progress = ((score - 1.0) * 10).toInt().coerceIn(0, 90)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) valueView.text = String.format(java.util.Locale.getDefault(), "%.1f", 1.0 + progress / 10.0)
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })
            container.addView(seek)
            rows += Row(seek, valueView, label)
        }

        addRow("🧠 思想深度", current.depthScore)
        addRow("🖋️ 文笔意境", current.artistryScore)
        addRow("❤️ 情感共鸣", current.emotionScore)
        addRow("📐 逻辑构架", current.logicScore)
        addRow("⛰️ 阅读门槛", current.difficultyScore)
        addRow("🌿 心灵治愈", current.healingScore)

        // 保存按钮（金色胶囊，松手即存即生效）
        val btnSave = TextView(this).apply {
            text = "保存评分"
            gravity = android.view.Gravity.CENTER
            textSize = 14f
            isAllCaps = false
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_chip_picker_selected)
            setTextColor(getColor(R.color.chip_selected_text))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(44),
            ).apply { topMargin = px(16) }
        }
        container.addView(btnSave)

        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(android.widget.ScrollView(this@BookDetailActivity).apply {
                addView(container)
            })
            window?.apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                setGravity(android.view.Gravity.CENTER)
            }
        }

        container.alpha = 0f
        container.translationY = px(28).toFloat()
        container.animate().alpha(1f).translationY(0f).setDuration(300L).start()

        btnSave.setOnClickListener {
            fun scoreOf(row: Row): Double = 1.0 + row.seek.progress / 10.0
            val newMindprint = current.copy(
                depthScore = scoreOf(rows[0]),
                artistryScore = scoreOf(rows[1]),
                emotionScore = scoreOf(rows[2]),
                logicScore = scoreOf(rows[3]),
                difficultyScore = scoreOf(rows[4]),
                healingScore = scoreOf(rows[5]),
            )
            databaseHelper.saveMindprint(newMindprint)
            renderMindprint(databaseHelper.getMindprint(bookId))
            Toast.makeText(this@BookDetailActivity, "六维心智评分已更新", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showCompareMindprintDialog() {
        val allBooks = databaseHelper.getBooks().filter { it.id != bookId }
        if (allBooks.isEmpty()) {
            Toast.makeText(this, "书架中暂无其他作品可用于对比", Toast.LENGTH_SHORT).show()
            return
        }

        val choices = mutableListOf(
            ElegantChoiceDialog.Choice("取消对比", "恢复单作品心智雷达", "✦"),
        )
        choices.addAll(allBooks.map { ElegantChoiceDialog.Choice("《${it.title}》", it.author ?: "未知作者") })

        ElegantChoiceDialog.show(
            this,
            title = "🔍 选择要对比的心智作品",
            choices = choices,
        ) { which ->
            val currentMindprint = databaseHelper.getMindprint(bookId)
            val currentTitle = currentBook?.title ?: "当前作品"
            val radarView = findViewById<com.example.readtrace.widget.MindprintRadarView>(R.id.detailMindprintRadar)

            if (which == 0) {
                radarView?.setMindprint(currentMindprint, animate = true)
                Toast.makeText(this, "已恢复单作品心智雷达", Toast.LENGTH_SHORT).show()
            } else {
                val targetBook = allBooks[which - 1]
                val targetMindprint = databaseHelper.getMindprint(targetBook.id)
                radarView?.setComparison(
                    currentTitle,
                    currentMindprint,
                    targetBook.title,
                    targetMindprint,
                    animate = true,
                )
                Toast.makeText(this, "已开启《$currentTitle》与《${targetBook.title}》双书心智对照", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderLocations(locations: List<com.example.readtrace.model.BookLocation>) {
        val container = findViewById<LinearLayout>(R.id.detailLocationsContainer)
        val emptyView = findViewById<TextView>(R.id.detailLocationsEmpty)
        container.removeAllViews()

        if (locations.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        container.visibility = View.VISIBLE

        locations.forEach { loc ->
            val item = layoutInflater.inflate(R.layout.item_location_card, container, false)
            item.findViewById<TextView>(R.id.locationTypeBadge).text = loc.locationType
            item.findViewById<TextView>(R.id.locationName).text = loc.name

            val descView = item.findViewById<TextView>(R.id.locationDescription)
            if (loc.description.isNullOrBlank()) {
                descView.visibility = View.GONE
            } else {
                descView.visibility = View.VISIBLE
                descView.text = loc.description
            }

            val sigView = item.findViewById<TextView>(R.id.locationSignificance)
            if (loc.significance.isNullOrBlank()) {
                sigView.visibility = View.GONE
            } else {
                sigView.visibility = View.VISIBLE
                sigView.text = "📍 叙事象征：${loc.significance}"
            }

            val coordView = item.findViewById<TextView>(R.id.locationCoordinates)
            if (loc.coordinates.isNullOrBlank()) {
                coordView.visibility = View.GONE
            } else {
                coordView.visibility = View.VISIBLE
                coordView.text = "🌐 空间方位：${loc.coordinates}"
            }

            item.findViewById<View>(R.id.locationDeleteBtn).setOnClickListener {
                ElegantConfirmDialog.show(
                    activity = this,
                    title = "🗺️ 删除空间地标",
                    message = "确定要移除地标「${loc.name}」吗？",
                    confirmText = "删除",
                    isDanger = true,
                    onConfirm = {
                        databaseHelper.deleteLocation(loc.id)
                        renderLocations(databaseHelper.getLocations(bookId))
                    },
                )
            }

            container.addView(item)
        }
    }

    private fun renderSimilarBooks(book: Book) {
        val container = findViewById<LinearLayout>(R.id.detailSimilarContainer) ?: return
        val emptyView = findViewById<TextView>(R.id.detailSimilarEmpty) ?: return
        container.removeAllViews()

        val recommendations = com.example.readtrace.util.BookSimilarityEngine.findSimilarBooks(book, databaseHelper, limit = 2)

        if (recommendations.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        container.visibility = View.VISIBLE

        recommendations.forEach { rec ->
            val card = layoutInflater.inflate(R.layout.item_similar_book_card, container, false)
            val coverImg = card.findViewById<ImageView>(R.id.similarCoverImage)
            CoverImageHelper.loadCover(coverImg, rec.book.coverUrl)

            card.findViewById<TextView>(R.id.similarMediaBadge).text = rec.book.mediaType.emoji
            card.findViewById<TextView>(R.id.similarTitle).text = rec.book.title
            card.findViewById<TextView>(R.id.similarAuthor).text = rec.book.author ?: getString(R.string.unknown_author)
            card.findViewById<TextView>(R.id.similarMatchBadge).text = "${rec.similarityPercent}% 灵犀契合"
            card.findViewById<TextView>(R.id.similarMatchReason).text = "✨ ${rec.matchReason}"

            card.setOnClickListener {
                startActivity(createIntent(this, rec.book.id))
            }
            com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(card, 0.97f)

            container.addView(card)
        }
    }

    private fun showAddLocationDialog() {
        ElegantFormDialog.show(
            this,
            title = "🗺️ 添加空间叙事地标",
            confirmText = "保存地标",
            fields = listOf(
                ElegantFormDialog.Field("name", "📍 地标名称", "如：B-612小行星 / 贝克街221B", required = true),
                ElegantFormDialog.Field("type", "🪐 空间类型", "如：🪐 架空星际 / 🏙️ 现实都市 / 🌲 荒原自然", preset = "🏙️ 现实都市"),
                ElegantFormDialog.Field("desc", "📖 空间环境与景观描写", "描述这里的风貌与氛围...", minLines = 2),
                ElegantFormDialog.Field("sig", "💡 核心情节发生与象征意义", "在这里发生了什么重要事件...", minLines = 2),
                ElegantFormDialog.Field("coord", "🌐 空间方位 / 坐标 (选填)", "如：撒哈拉沙漠腹地 / 经纬度"),
            ),
        ) { v ->
            val name = v.getValue("name")
            if (name.isNotEmpty()) {
                val location = com.example.readtrace.model.BookLocation(
                    bookId = bookId,
                    name = name,
                    locationType = v.getValue("type").ifBlank { "🏙️ 现实都市" },
                    description = v.getValue("desc").ifBlank { null },
                    significance = v.getValue("sig").ifBlank { null },
                    coordinates = v.getValue("coord").ifBlank { null },
                )
                databaseHelper.insertLocation(location)
                renderLocations(databaseHelper.getLocations(bookId))
                currentBook?.let { refreshTimelineOnly(it) }
            }
        }
    }

    private enum class TimelineFilter {
        ALL, SESSIONS_ONLY, NOTES_ONLY
    }

    private var currentTimelineFilter = TimelineFilter.ALL

    private fun refreshTimelineOnly(book: Book) {
        renderTimeline(
            book,
            databaseHelper.getReadingSessions(book.id),
            databaseHelper.getNotes(book.id),
            databaseHelper.getLocations(book.id),
            databaseHelper.getOutlines(book.id),
        )
    }

    private fun renderTimeline(
        book: Book,
        sessions: List<com.example.readtrace.model.ReadingSession>,
        notes: List<Note>,
        locations: List<com.example.readtrace.model.BookLocation>,
        outlines: List<com.example.readtrace.model.BookOutline>,
    ) {
        val container = findViewById<LinearLayout>(R.id.detailTimelineContainer)
        val emptyView = findViewById<TextView>(R.id.detailTimelineEmpty)
        container.removeAllViews()

        val events = mutableListOf<com.example.readtrace.model.TimelineEvent>()

        // 媒介适配文案：番剧/影视/游戏/音乐不再使用"全书/阅读"类书籍字眼
        val startTitle = when (book.mediaType) {
            MediaType.BOOK -> "🏁 启程 · 翻开扉页"
            MediaType.ANIME -> "🏁 启程 · 点开第一话"
            MediaType.MOVIE -> "🏁 启程 · 熄灯开场"
            MediaType.GAME -> "🏁 启程 · 开机启程"
            MediaType.MUSIC -> "🏁 启程 · 戴上耳机开听"
        }
        val startContent = when (book.mediaType) {
            MediaType.BOOK -> "初读期待与相遇"
            MediaType.ANIME -> "初看期待与相遇"
            MediaType.MOVIE -> "初看期待与相遇"
            MediaType.GAME -> "初玩期待与相遇"
            MediaType.MUSIC -> "初听期待与相遇"
        }
        val focusVerb = when (book.mediaType) {
            MediaType.BOOK -> "阅读"
            MediaType.ANIME -> "追番"
            MediaType.MOVIE -> "观影"
            MediaType.GAME -> "游玩"
            MediaType.MUSIC -> "聆听"
        }
        val progressPrefix = if (book.mediaType == MediaType.BOOK) "读至" else "进度"
        val outlineIcon = when (book.mediaType) {
            MediaType.BOOK -> "📖"
            MediaType.ANIME -> "🌸"
            MediaType.MOVIE -> "🎬"
            MediaType.GAME -> "🎮"
            MediaType.MUSIC -> "💿"
        }
        val outlineUnit = when (book.mediaType) {
            MediaType.BOOK -> "章"
            MediaType.ANIME -> "话"
            MediaType.MOVIE -> "幕"
            MediaType.GAME -> "章"
            MediaType.MUSIC -> "首"
        }
        val outlineSubtitle = when (book.mediaType) {
            MediaType.BOOK -> "全书大纲脉络"
            MediaType.ANIME -> "全剧集脉络"
            MediaType.MOVIE -> "全片脉络"
            MediaType.GAME -> "全程流程脉络"
            MediaType.MUSIC -> "全曲目脉络"
        }
        val finishTitle = when (book.mediaType) {
            MediaType.BOOK -> "🌟 终章 · 全书完读复盘"
            MediaType.ANIME -> "🌟 终章 · 全剧补完复盘"
            MediaType.MOVIE -> "🌟 落幕 · 观影终章复盘"
            MediaType.GAME -> "🌟 终章 · 全作通关复盘"
            MediaType.MUSIC -> "🌟 终章 · 全曲听完复盘"
        }

        // 1. 初读起点（日期为占位文本如「待整理」时不再拼接时间后缀）
        if (!book.startDate.isNullOrBlank()) {
            val startDate = book.startDate.trim()
            val startValid = TIMELINE_DATE_PATTERN.matches(startDate)
            events.add(
                com.example.readtrace.model.TimelineEvent(
                    id = "start_${book.id}",
                    type = com.example.readtrace.model.TimelineEventType.START_READING,
                    timestamp = if (startValid) startDate + "T08:00:00" else book.createdAt,
                    title = startTitle,
                    subtitle = "当前状态：${book.mediaType.getStatusLabel(book.status)} · 载体：${book.mediaType.displayName}",
                    content = startContent,
                    extraMeta = book.category?.let { "分类：$it" },
                    pendingTime = !startValid,
                ),
            )
        }

        // 2. 专注阅读打卡
        sessions.forEach { session ->
            events.add(
                com.example.readtrace.model.TimelineEvent(
                    id = "session_${session.id}",
                    type = com.example.readtrace.model.TimelineEventType.READING_SESSION,
                    timestamp = session.createdAt,
                    title = "⏱️ 专注$focusVerb ${session.durationMinutes} 分钟",
                    subtitle = session.pagesRead?.takeIf { it.isNotBlank() }?.let { "$progressPrefix：$it" },
                    content = session.thought,
                    extraMeta = "打卡印记",
                    rawId = session.id,
                ),
            )
        }

        // 3. 灵感与高光摘录
        notes.forEach { note ->
            val positionMeta = notePositionLabel(note.page, note.chapter, book.mediaType)

            events.add(
                com.example.readtrace.model.TimelineEvent(
                    id = "note_${note.id}",
                    type = com.example.readtrace.model.TimelineEventType.NOTE_QUOTE,
                    timestamp = note.createdAt,
                    title = "💬 [${note.noteType.displayName}] 灵感火花",
                    subtitle = positionMeta.takeIf { it.isNotEmpty() },
                    content = note.content,
                    extraMeta = "🎨 点击制作金句海报",
                    rawId = note.id,
                ),
            )
        }

        // 4. 空间叙事地标
        locations.forEach { loc ->
            events.add(
                com.example.readtrace.model.TimelineEvent(
                    id = "loc_${loc.id}",
                    type = com.example.readtrace.model.TimelineEventType.LOCATION_DISCOVERED,
                    timestamp = loc.createdAt,
                    title = "🗺️ 空间漫游：${loc.name}",
                    subtitle = loc.locationType + (loc.coordinates?.let { " · $it" } ?: ""),
                    content = loc.description,
                    extraMeta = loc.significance?.let { "📍 象征：$it" },
                    rawId = loc.id,
                ),
            )
        }

        // 5. 章节大纲脑图
        outlines.forEach { outline ->
            events.add(
                com.example.readtrace.model.TimelineEvent(
                    id = "outline_${outline.id}",
                    type = com.example.readtrace.model.TimelineEventType.OUTLINE_CHAPTER,
                    timestamp = outline.createdAt,
                    title = "$outlineIcon 第 ${outline.chapterOrder} $outlineUnit：${outline.title}",
                    subtitle = outlineSubtitle,
                    content = outline.summary,
                    extraMeta = outline.keyTakeaways?.let { "💡 思想精髓：$it" },
                    rawId = outline.id,
                ),
            )
        }

        // 6. 完读与深度复盘（日期为占位文本时退回更新时间排序，并标记待整理）
        if (!book.finishDate.isNullOrBlank() || book.status == BookStatus.FINISHED) {
            val finishDate = book.finishDate?.trim()
            val finishValid = finishDate != null && TIMELINE_DATE_PATTERN.matches(finishDate)
            events.add(
                com.example.readtrace.model.TimelineEvent(
                    id = "finish_${book.id}",
                    type = com.example.readtrace.model.TimelineEventType.FINISH_REVIEW,
                    timestamp = if (finishValid) finishDate + "T23:59:59" else book.updatedAt,
                    title = finishTitle,
                    subtitle = book.rating?.let { "个人评分：★ ${RATING_FORMAT.format(it / 2.0)} / 5.0" } ?: book.mediaType.finishedLabel,
                    content = book.review?.takeIf { it.isNotBlank() } ?: book.shortComment,
                    extraMeta = "精神沉淀与思想烙印",
                    pendingTime = book.finishDate != null && !finishValid,
                ),
            )
        }

        // 按发生时间排序
        events.sort()

        val filteredEvents = when (currentTimelineFilter) {
            TimelineFilter.ALL -> events
            TimelineFilter.SESSIONS_ONLY -> events.filter { it.type == com.example.readtrace.model.TimelineEventType.READING_SESSION }
            TimelineFilter.NOTES_ONLY -> events.filter { it.type == com.example.readtrace.model.TimelineEventType.NOTE_QUOTE }
        }

        if (filteredEvents.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        container.visibility = View.VISIBLE

        filteredEvents.forEachIndexed { index, event ->
            val item = layoutInflater.inflate(R.layout.item_timeline_node, container, false)
            item.findViewById<TextView>(R.id.timelineNodeIcon).text = event.type.icon
            item.findViewById<TextView>(R.id.timelineTitle).text = event.title
            item.findViewById<TextView>(R.id.timelineTimeText).text =
                if (event.pendingTime) "时间待整理" else formatTimestamp(event.timestamp)

            if (index == 0) {
                item.findViewById<View>(R.id.timelineTopLine).visibility = View.INVISIBLE
            }
            if (index == filteredEvents.size - 1) {
                item.findViewById<View>(R.id.timelineBottomLine).visibility = View.INVISIBLE
            }

            val subView = item.findViewById<TextView>(R.id.timelineSubtitle)
            if (event.subtitle.isNullOrBlank()) {
                subView.visibility = View.GONE
            } else {
                subView.visibility = View.VISIBLE
                subView.text = event.subtitle
            }

            val contentView = item.findViewById<TextView>(R.id.timelineContent)
            if (event.content.isNullOrBlank()) {
                contentView.visibility = View.GONE
            } else {
                contentView.visibility = View.VISIBLE
                contentView.text = event.content
            }

            val extraView = item.findViewById<TextView>(R.id.timelineExtraMeta)
            if (event.extraMeta.isNullOrBlank()) {
                extraView.visibility = View.GONE
            } else {
                extraView.visibility = View.VISIBLE
                extraView.text = event.extraMeta
            }

            if (event.type == com.example.readtrace.model.TimelineEventType.NOTE_QUOTE) {
                item.setOnClickListener {
                    startActivity(
                        QuotePosterActivity.createIntent(
                            this,
                            book.id,
                            book.title,
                            book.author,
                            book.coverUrl,
                            event.content.orEmpty(),
                            event.subtitle,
                        ),
                    )
                }
            }

            container.addView(item)
        }
    }

    private fun exportTimelineAsLongImage() {
        val timelineView = findViewById<View>(R.id.detailTimelineSection)
        if (timelineView == null || timelineView.width <= 0 || timelineView.height <= 0) {
            Toast.makeText(this, "时间轴尚未渲染完毕，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val bitmap = android.graphics.Bitmap.createBitmap(
                timelineView.width,
                timelineView.height,
                android.graphics.Bitmap.Config.ARGB_8888,
            )
            val canvas = android.graphics.Canvas(bitmap)
            timelineView.draw(canvas)

            val bookName = currentBook?.title?.replace(" ", "_") ?: "book"
            val filename = "ReadTrace_Timeline_${bookName}_${System.currentTimeMillis()}.png"
            var fos: java.io.OutputStream? = null
            var success = false

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/ReadTrace")
                }
                val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri)
                    success = bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos!!)
                }
            } else {
                val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).toString() + "/ReadTrace"
                val file = java.io.File(imagesDir)
                if (!file.exists()) file.mkdirs()
                val imageFile = java.io.File(file, filename)
                fos = java.io.FileOutputStream(imageFile)
                success = bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
            }
            fos?.close()

            if (success) {
                ElegantConfirmDialog.show(
                    activity = this,
                    title = "🎉 时间轴长图已生成",
                    message = "全息心路长图已成功保存至系统相册！是否立即分享给书友？",
                    confirmText = "🔗 立即分享",
                    cancelText = "稍后再说",
                    isDanger = false,
                    onConfirm = { shareTimelineBitmap(bitmap) },
                )
            } else {
                Toast.makeText(this, "导出长图失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "生成长图出现异常: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareTimelineBitmap(bitmap: android.graphics.Bitmap) {
        try {
            val cachePath = java.io.File(cacheDir, "images")
            cachePath.mkdirs()
            val file = java.io.File(cachePath, "share_timeline.png")
            val stream = java.io.FileOutputStream(file)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val contentUri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "《${currentBook?.title}》全息心路历程 · 阅痕 ReadTrace")
                putExtra(Intent.EXTRA_TEXT, "这是我在《${currentBook?.title}》中留下的全息阅读印记与时光时间轴。")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享心路全息长图"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "分享长图失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openQuotePoster(book: Book) {
        val notes = databaseHelper.getNotes(book.id)
        val quote = book.shortComment?.takeIf { it.isNotBlank() }
            ?: notes.firstOrNull()?.content
            ?: when (book.mediaType) {
                MediaType.MOVIE -> "光影定格瞬间，回声穿透岁月。在银幕的光斑里，看见未曾经历的人生。"
                MediaType.ANIME -> "若你跨越次元与时光，我们终将在热血与泪水的彼岸重逢。"
                MediaType.GAME -> "每个按键的跃动，都是灵魂在异世界的无畏出征。"
                MediaType.MUSIC -> "旋律划过耳畔，思维在无垠空间激荡起共鸣的涟漪。"
                else -> "字句有痕，岁月有温。在文字的世界里，每一次阅读都是灵魂的漫游。"
            }
        val source = if (book.shortComment.isNullOrBlank() && notes.isNotEmpty()) {
            notePositionLabel(notes.first().page, notes.first().chapter, book.mediaType)
        } else when (book.mediaType) {
            MediaType.MOVIE -> "🎬 光影名台词"
            MediaType.ANIME -> "🌸 高光台词"
            MediaType.GAME -> "🎮 通关寄语"
            MediaType.MUSIC -> "💿 声音印记"
            else -> "📖 一句话感悟"
        }
        startActivity(
            QuotePosterActivity.createIntent(
                this,
                book.id,
                book.title,
                book.author,
                book.coverUrl,
                quote,
                source,
            ),
        )
    }

    private fun renderBook(book: Book) {
        val coverImage = findViewById<ImageView>(R.id.detailCoverImage)
        CoverImageHelper.loadCover(coverImage, book.coverUrl)

        coverImage.setOnClickListener {
            val mediaLabel = book.mediaType.displayName
            Toast.makeText(this, "正在联网检索《${book.title}》官方高清海报...", Toast.LENGTH_SHORT).show()
            com.example.readtrace.util.AnimeCoverScraperHelper.fetchAndSaveAnimeCover(this, book, databaseHelper) { success, path ->
                if (success && path != null) {
                    Toast.makeText(this, "✨ 已成功匹配并下载《${book.title}》官方海报！", Toast.LENGTH_SHORT).show()
                    CoverImageHelper.loadCover(coverImage, path)
                    currentBook = databaseHelper.getBook(book.id)
                } else {
                    Toast.makeText(this, "未找到匹配的高清海报，可手动在编辑中添加图片", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 🏷️ 顶栏与核心分类严格区分
        val headerTitle = findViewById<TextView>(R.id.detailHeaderTitle)
        val headerSubtitle = findViewById<TextView>(R.id.detailHeaderSubtitle)
        val read3DBtn = findViewById<TextView>(R.id.detailRead3DButton)
        val quotePosterBtn = findViewById<TextView>(R.id.detailQuotePosterButton)
        val sectionIdentityTitle = findViewById<TextView>(R.id.detailSectionIdentityTitle)
        val labelCategory = findViewById<TextView>(R.id.detailLabelCategory)
        val sectionReadingTitle = findViewById<TextView>(R.id.detailSectionReadingTitle)
        val sectionThoughtsTitle = findViewById<TextView>(R.id.detailSectionThoughtsTitle)
        val shortCommentLabel = findViewById<TextView>(R.id.detailShortCommentLabel)
        val reviewLabel = findViewById<TextView>(R.id.detailReviewLabel)
        val sectionNotesTitle = findViewById<TextView>(R.id.detailSectionNotesTitle)
        val collectionTitle = findViewById<TextView>(R.id.detailCollectionTitle)
        val sectionCharTitle = findViewById<TextView>(R.id.detailSectionCharTitle)
        val navTabNotes = findViewById<TextView>(R.id.navTabNotes)

        when (book.mediaType) {
            MediaType.BOOK -> {
                headerTitle.text = "书籍详情"
                headerSubtitle.text = "慢慢回看这本书留下的痕迹。"
                read3DBtn.visibility = View.GONE
                quotePosterBtn.text = "🎨 生成金句印记海报"
                quotePosterBtn.setOnClickListener { openQuotePoster(book) }
                sectionIdentityTitle.text = "关于这本书"
                labelCategory.text = "书籍分类"
                sectionReadingTitle.text = "阅读印记"
                sectionThoughtsTitle.text = "个人感悟"
                shortCommentLabel.text = "一句话感悟"
                reviewLabel.text = "长篇书评"
                sectionNotesTitle.text = "摘录与笔记"
                collectionTitle.text = "💰 实体馆藏与藏书印记"
                sectionCharTitle.text = "👥 人物角色谱"
                navTabNotes.text = "💬 痕迹与摘录"
            }
            MediaType.ANIME -> {
                headerTitle.text = "番剧详情"
                headerSubtitle.text = "重温这部番剧带来的感动与热血。"
                read3DBtn.text = "🌸 追番编年画卷"
                read3DBtn.visibility = View.VISIBLE
                read3DBtn.setOnClickListener {
                    startActivity(MediaTimelineScrollActivity.createIntent(this, MediaType.ANIME))
                }
                quotePosterBtn.text = "🛂 追番入境签证"
                quotePosterBtn.setOnClickListener {
                    startActivity(CulturalPassportActivity.createIntent(this, MediaType.ANIME))
                }
                sectionIdentityTitle.text = "关于这部番剧"
                labelCategory.text = "番剧题材"
                sectionReadingTitle.text = "追番印记"
                sectionThoughtsTitle.text = "追番心境"
                shortCommentLabel.text = "经典台词 / 金句"
                reviewLabel.text = "完结长评"
                sectionNotesTitle.text = "高光台词与名场面"
                collectionTitle.text = "💰 周边特典与实体盘片"
                sectionCharTitle.text = "🌸 登场角色与声优谱"
                navTabNotes.text = "💬 经典台词"
            }
            MediaType.MOVIE -> {
                headerTitle.text = "影视详情"
                headerSubtitle.text = "回味这部光影作品留下的回响。"
                read3DBtn.text = "🎟️ 复古透光电影票根"
                read3DBtn.visibility = View.VISIBLE
                read3DBtn.setOnClickListener {
                    startActivity(MovieTicketPosterActivity.createIntent(this, book.id))
                }
                quotePosterBtn.text = "🎨 生成光影名台词海报"
                quotePosterBtn.setOnClickListener { openQuotePoster(book) }
                sectionIdentityTitle.text = "关于这部影视"
                labelCategory.text = "影视类型"
                sectionReadingTitle.text = "观影印记"
                sectionThoughtsTitle.text = "观影心境"
                shortCommentLabel.text = "经典台词"
                reviewLabel.text = "深度影评"
                sectionNotesTitle.text = "光影名句与长评"
                collectionTitle.text = "💰 实体票根与蓝光收藏"
                sectionCharTitle.text = "🎬 演职人员与角色谱"
                navTabNotes.text = "💬 光影名句"
            }
            MediaType.GAME -> {
                headerTitle.text = "游戏详情"
                headerSubtitle.text = "重温这段通关冒险与高光时刻。"
                read3DBtn.text = "🕹️ 白金全息实体卡带"
                read3DBtn.visibility = View.VISIBLE
                read3DBtn.setOnClickListener {
                    startActivity(GameCartridgePosterActivity.createIntent(this, book.id))
                }
                quotePosterBtn.text = "🛂 游戏白金通关签证"
                quotePosterBtn.setOnClickListener {
                    startActivity(CulturalPassportActivity.createIntent(this, MediaType.GAME))
                }
                sectionIdentityTitle.text = "关于这款游戏"
                labelCategory.text = "游戏类型"
                sectionReadingTitle.text = "游玩印记"
                sectionThoughtsTitle.text = "通关心得"
                shortCommentLabel.text = "通关寄语 / 金句"
                reviewLabel.text = "深度评测"
                sectionNotesTitle.text = "高光战报与心得"
                collectionTitle.text = "💰 实体卡带与典藏周边"
                sectionCharTitle.text = "🎮 主要角色与NPC谱"
                navTabNotes.text = "💬 战报心得"
            }
            MediaType.MUSIC -> {
                headerTitle.text = "音乐与唱片详情"
                headerSubtitle.text = "倾听旋律心跳、声音印记与黑胶律动。"
                read3DBtn.text = "💽 3D 拟真黑胶唱机"
                read3DBtn.visibility = View.VISIBLE
                read3DBtn.setOnClickListener {
                    startActivity(VinylCassettePlayerActivity.createIntent(this, book.id))
                }
                quotePosterBtn.text = "🎴 跨媒介双生微卡"
                quotePosterBtn.setOnClickListener {
                    startActivity(Intent(this, ResonancePosterActivity::class.java))
                }
                sectionIdentityTitle.text = "关于这张作品"
                labelCategory.text = "曲目 / 类别"
                sectionReadingTitle.text = "收听印记"
                sectionThoughtsTitle.text = "听后感悟"
                shortCommentLabel.text = "灵感歌词 / 金句"
                reviewLabel.text = "深度听感"
                sectionNotesTitle.text = "灵感火花与速记"
                collectionTitle.text = "💰 实体唱片与录音带"
                sectionCharTitle.text = "💿 创作者与声乐谱"
                navTabNotes.text = "💬 灵感速记"
                findViewById<View>(R.id.detailOutlineSection).visibility = View.GONE
            }
        }

        findViewById<TextView>(R.id.detailExLibrisButton)?.setOnClickListener {
            com.example.readtrace.util.HapticFeedbackEngine.lightClick(this)
            startActivity(com.example.readtrace.ExLibrisStudioActivity.createIntent(this, book.id))
        }

        findViewById<com.example.readtrace.widget.EditorialBadgeView>(R.id.detailEditorialBadge)?.setBadgeContent(
            "NO." + (1000 + (book.id % 9000)),
            book.mediaType.name
        )
        findViewById<TextView>(R.id.detailMediaBadge).text = "${book.mediaType.emoji} ${book.mediaType.displayName}"
        val titleScrambleView = findViewById<com.example.readtrace.widget.ScrambleTextView>(R.id.detailBookTitle)
        if (titleScrambleView != null) {
            titleScrambleView.setScrambleText(book.title)
            titleScrambleView.setOnClickListener {
                com.example.readtrace.util.HapticFeedbackEngine.pageTurnRustle(this)
                titleScrambleView.triggerScramble()
            }
        } else {
            findViewById<TextView>(R.id.detailBookTitle).text = book.title
        }

        findViewById<TextView>(R.id.detailBookAuthor).text = valueOrFallback(book.author)
        findViewById<com.example.readtrace.widget.HolographicRatingView>(R.id.detailHolographicRating)?.setRating(book.rating, animate = true)
        findViewById<com.example.readtrace.widget.HolographicRatingView>(R.id.detailRatingHolo)?.setRating(book.rating, animate = true)
        findViewById<TextView>(R.id.detailHeroMeta).text = buildHeroMeta(book)
        findViewById<TextView>(R.id.detailCategory).text = valueOrFallback(book.category)
        findViewById<TextView>(R.id.detailCoverUrl).text =
            if (CoverImageHelper.isLanCoverKey(book.coverUrl)) "国内图源封面（联网自动加载）" else valueOrFallback(book.coverUrl)
        findViewById<TextView>(R.id.detailStatus).text = book.status.getDisplayName(book.mediaType)
        findViewById<TextView>(R.id.detailRating).text = book.rating?.let {
            val stars = (it / 2.0)
            val full = stars.toInt()
            val starText = "★".repeat(full) + "☆".repeat((5 - full).coerceAtLeast(0))
            "$starText  ${RATING_FORMAT.format(stars)} / 5"
        } ?: getString(R.string.not_recorded)
        findViewById<TextView>(R.id.detailTags).text =
            if (book.tags.isEmpty()) {
                getString(R.string.not_recorded)
            } else {
                book.tags.joinToString(" · ")
            }
        findViewById<TextView>(R.id.detailStartDate).text = valueOrFallback(book.startDate)
        findViewById<TextView>(R.id.detailFinishDate).text = valueOrFallback(book.finishDate)

        val shortCommentText = valueOrFallback(book.shortComment)
        (findViewById<com.example.readtrace.widget.DropCapTextView>(R.id.detailShortComment))?.setEditorialText(shortCommentText)
            ?: run { findViewById<TextView>(R.id.detailShortComment).text = shortCommentText }

        val reviewText = valueOrFallback(book.review)
        (findViewById<com.example.readtrace.widget.DropCapTextView>(R.id.detailReview))?.setEditorialText(reviewText)
            ?: run { findViewById<TextView>(R.id.detailReview).text = reviewText }

        findViewById<TextView>(R.id.detailCreatedAt).text = formatTimestamp(book.createdAt)
        findViewById<TextView>(R.id.detailUpdatedAt).text = formatTimestamp(book.updatedAt)
        renderDescription(book)
        renderThoughtsHint(book)
    }

    // ---------------------------------------------------------------- 作品简介（外部导入收尾）

    private var descriptionExpanded = false

    /**
     * 作品简介展示：默认折叠 3 行，超长时点击正文或「展开全文」切换；
     * 底部按来源显示小字标注（手动录入的简介不标注来源）。
     */
    private fun renderDescription(book: Book) {
        val label = findViewById<TextView>(R.id.detailDescriptionLabel)
        val body = findViewById<TextView>(R.id.detailDescription)
        val toggle = findViewById<TextView>(R.id.detailDescriptionToggle)
        val source = findViewById<TextView>(R.id.detailDescriptionSource)
        val desc = book.description?.trim().orEmpty()
        if (desc.isEmpty()) {
            label.visibility = View.GONE
            body.visibility = View.GONE
            toggle.visibility = View.GONE
            source.visibility = View.GONE
            return
        }
        label.visibility = View.VISIBLE
        body.visibility = View.VISIBLE
        val sourceLabel = descriptionSourceLabel(book.sourceType)
        source.visibility = if (sourceLabel.isNullOrBlank()) View.GONE else View.VISIBLE
        source.text = sourceLabel
        descriptionExpanded = false
        body.text = desc
        val toggleDescription: () -> Unit = {
            com.example.readtrace.util.HapticFeedbackEngine.lightClick(this)
            descriptionExpanded = !descriptionExpanded
            applyDescriptionState(body, toggle)
        }
        body.setOnClickListener { toggleDescription() }
        toggle.setOnClickListener { toggleDescription() }
        applyDescriptionState(body, toggle)
    }

    private fun applyDescriptionState(body: TextView, toggle: TextView) {
        if (descriptionExpanded) {
            body.maxLines = Int.MAX_VALUE
            body.ellipsize = null
            toggle.text = getString(R.string.detail_description_collapse)
        } else {
            body.maxLines = DESCRIPTION_COLLAPSED_LINES
            body.ellipsize = TextUtils.TruncateAt.END
            toggle.text = getString(R.string.detail_description_expand)
        }
        // 等本轮 layout 完成后测量：只有真实溢出才露出切换按钮
        body.post {
            toggle.visibility = if (isDescriptionOverflowing(body)) View.VISIBLE else View.GONE
        }
    }

    /**
     * 用 StaticLayout 全量测量真实行数。
     * 折叠态下 TextView.layout 已被 maxLines 截断，不能直接数；
     * 测量画笔必须保持 LEFT 对齐（CENTER 会把每行再居中，行数计算失真）。
     */
    private fun isDescriptionOverflowing(body: TextView): Boolean {
        val text = body.text ?: return false
        if (text.isEmpty()) return false
        val width = body.width - body.paddingLeft - body.paddingRight
        if (width <= 0) return false
        return runCatching {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, body.paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(body.lineSpacingExtra, body.lineSpacingMultiplier)
                .setIncludePad(body.includeFontPadding)
                .build()
            layout.lineCount > DESCRIPTION_COLLAPSED_LINES
        }.getOrDefault(false)
    }

    /** 简介来源标注：与落库的 sourceType 对应；手动录入（null/空）不标注 */
    private fun descriptionSourceLabel(sourceType: String?): String? = when (sourceType) {
        "bangumi" -> getString(R.string.discover_summary_source_bangumi)
        "douban" -> getString(R.string.discover_summary_source_douban)
        "steam" -> getString(R.string.discover_summary_source_steam)
        "netease" -> getString(R.string.discover_summary_source_netease)
        null, "" -> null
        else -> getString(R.string.discover_summary_source_generic)
    }

    /** 骨架档案（外部导入）且短评/长评皆空 → 「还没有留下你的想法」轻提示，点击直达编辑页 */
    private fun renderThoughtsHint(book: Book) {
        val hint = findViewById<TextView>(R.id.detailThoughtsEmptyHint) ?: return
        val imported = !book.sourceType.isNullOrBlank()
        val thoughtsEmpty = book.shortComment.isNullOrBlank() && book.review.isNullOrBlank()
        if (imported && thoughtsEmpty) {
            hint.visibility = View.VISIBLE
            hint.setOnClickListener {
                com.example.readtrace.util.HapticFeedbackEngine.lightClick(this)
                startActivity(AddBookActivity.createEditIntent(this, book.id))
            }
        } else {
            hint.visibility = View.GONE
            hint.setOnClickListener(null)
        }
    }

    private fun renderNotes(notes: List<Note>) {
        val container = findViewById<LinearLayout>(R.id.detailNotesContainer)
        val emptyView = findViewById<TextView>(R.id.detailNotesEmpty)
        val countView = findViewById<TextView>(R.id.detailNotesCount)
        val flipButton = findViewById<TextView>(R.id.detailNotesFlipButton)
        container.removeAllViews()
        if (notes.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            countView.visibility = View.GONE
            container.visibility = View.GONE
            flipButton.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        countView.visibility = View.VISIBLE
        countView.text = getString(R.string.notes_count_format, notes.size)
        flipButton.visibility = View.VISIBLE
        flipButton.setOnClickListener {
            startActivity(FlipNotesActivity.createIntent(this, bookId, 0))
        }
        container.visibility = View.VISIBLE
        notes.forEachIndexed { index, note ->
            val item = layoutInflater.inflate(R.layout.item_detail_note, container, false)
            item.findViewById<TextView>(R.id.noteTypeBadge).apply {
                text = note.noteType.displayName
                setTextColor(
                    getColor(
                        if (note.noteType == NoteType.QUOTE) {
                            R.color.readtrace_accent
                        } else {
                            R.color.readtrace_muted
                        },
                    ),
                )
            }
            item.findViewById<TextView>(R.id.noteCreatedAt).text = formatTimestamp(note.createdAt)
            item.findViewById<TextView>(R.id.noteContent).text = note.content
            val positionMeta = notePositionLabel(
                note.page,
                note.chapter,
                currentBook?.mediaType ?: MediaType.BOOK,
            )
            item.findViewById<TextView>(R.id.notePositionMeta).apply {
                text = positionMeta
                visibility = if (positionMeta.isEmpty()) View.GONE else View.VISIBLE
            }
            val params = item.layoutParams as LinearLayout.LayoutParams
            params.topMargin = dpToPx(if (index == 0) 14 else 10)
            item.layoutParams = params
            item.setOnClickListener {
                ElegantChoiceDialog.show(
                    this,
                    title = "✦ 笔记印记操作",
                    choices = listOf(
                        ElegantChoiceDialog.Choice("生成专属金句海报", "把这段文字做成可分享的纪念海报", "🎨"),
                        ElegantChoiceDialog.Choice("编辑笔记", "修改这条笔记的内容与出处", "✏️"),
                        ElegantChoiceDialog.Choice("归档至回收站", "移入回收站，可随时恢复", "📦"),
                    ),
                ) { which ->

                        when (which) {
                            0 -> {
                                currentBook?.let { book ->
                                    val source = notePositionLabel(note.page, note.chapter, book.mediaType)
                                    startActivity(
                                        QuotePosterActivity.createIntent(
                                            this,
                                            book.id,
                                            book.title,
                                            book.author,
                                            book.coverUrl,
                                            note.content,
                                            source.ifBlank { null },
                                        ),
                                    )
                                }
                            }
                            1 -> openNoteEditor(note.id)
                            2 -> confirmArchiveNote(note)
                        }
                    }
            }
            item.setOnLongClickListener {
                confirmArchiveNote(note)
                true
            }
            container.addView(item)
        }
    }

    private fun openNoteEditor(noteId: Long) {
        startActivity(AddNoteActivity.createEditIntent(this, noteId))
    }

    private fun confirmArchiveNote(note: Note) {
        ElegantConfirmDialog.show(
            activity = this,
            title = "📦 " + getString(R.string.note_archive_confirm_title),
            message = getString(R.string.note_archive_confirm_message),
            confirmText = getString(R.string.action_archive),
            isDanger = true,
            onConfirm = {
                val archived = runCatching {
                    databaseHelper.archiveNote(note.id)
                }.getOrDefault(false)
                if (archived) {
                    Toast.makeText(this, R.string.note_archive_success, Toast.LENGTH_SHORT).show()
                    renderNotes(databaseHelper.getNotes(bookId))
                } else {
                    Toast.makeText(this, R.string.note_archive_failed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun dpToPx(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun confirmArchive() {
        val book = currentBook ?: return
        ElegantConfirmDialog.show(
            activity = this,
            title = "📦 " + getString(R.string.archive_confirm_title),
            message = getString(R.string.archive_confirm_message),
            confirmText = getString(R.string.action_archive),
            isDanger = true,
            onConfirm = {
                archiveBook(book.id)
            },
        )
    }

    private fun archiveBook(id: Long) {
        val archived = runCatching { databaseHelper.archiveBook(id) }.getOrDefault(false)
        if (archived) {
            Toast.makeText(this, R.string.archive_success, Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, R.string.archive_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun valueOrFallback(value: String?): String =
        value?.trim()?.takeIf { it.isNotEmpty() } ?: getString(R.string.not_recorded)

    /**
     * 笔记/台词定位文案：不同媒介不再统一标「第 X 页」（不同出版社页数不统一），
     * 数字页码按媒介转换为章/集；非数字内容（如出处角色名）原样展示。
     */
    private fun notePositionLabel(page: String?, chapter: String?, mediaType: MediaType): String =
        buildList {
            val pageText = page?.trim()
            if (!pageText.isNullOrEmpty()) {
                val pageNum = pageText.toIntOrNull()
                if (pageNum != null) {
                    when (mediaType) {
                        MediaType.BOOK -> add("第 $pageNum 章")
                        MediaType.ANIME -> add("第 $pageNum 集")
                        else -> Unit // 影视/游戏/音乐等不标记定位刻度
                    }
                } else {
                    add(pageText)
                }
            }
            val chapterText = chapter?.trim()
            if (!chapterText.isNullOrEmpty()) add(chapterText)
        }.joinToString(" · ")

    private fun buildHeroMeta(book: Book): String {
        val ratingLabel = book.rating?.let {
            getString(R.string.rating_format, RATING_FORMAT.format(it / 2.0))
        } ?: getString(R.string.not_recorded)
        return listOfNotNull(
            book.status.getDisplayName(book.mediaType),
            ratingLabel,
            book.category?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" · ")
    }

    private fun formatTimestamp(value: String): String =
        runCatching {
            OffsetDateTime.parse(value).format(DISPLAY_TIME_FORMAT)
        }.getOrDefault(valueOrFallback(value))

    private fun showSelectWidgetThemeDialog() {
        val themes = com.example.readtrace.model.WidgetCardTheme.values()
        ElegantChoiceDialog.show(
            this,
            title = "🎨 选择锁屏微卡 / 小组件主题",
            choices = themes.map { ElegantChoiceDialog.Choice(it.displayName, it.description) },
        ) { which ->
            exportMindprintWidgetCard(themes[which])
        }

    }

    private fun exportMindprintWidgetCard(theme: com.example.readtrace.model.WidgetCardTheme = com.example.readtrace.model.WidgetCardTheme.ALABASTER_PAPER) {
        val book = currentBook ?: return
        val mindprint = databaseHelper.getMindprint(book.id)

        try {
            val width = 1080
            val height = 1080
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)

            val bgColor = theme.bgColor
            val cardBgColor = theme.cardBgColor
            val strokeColor = theme.strokeColor
            val textPrimaryColor = theme.textPrimaryColor
            val textMutedColor = theme.textMutedColor
            val accentColor = theme.accentColor

            // 1. 背景底色
            canvas.drawColor(bgColor)

            // 2. 居中微卡 (Margin 48px, CornerRadius 44px)
            val cardRect = android.graphics.RectF(48f, 48f, width - 48f, height - 48f)
            val cardPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = cardBgColor
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawRoundRect(cardRect, 44f, 44f, cardPaint)

            val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = strokeColor
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRoundRect(cardRect, 44f, 44f, strokePaint)

            // 3. 顶部 Header
            val headerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                textSize = 30f
                isFakeBoldText = true
            }
            canvas.drawText("✨ 阅痕 · 灵魂心智全息微卡", 96f, 126f, headerPaint)

            val subHeaderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = textMutedColor
                textSize = 24f
                textAlign = android.graphics.Paint.Align.RIGHT
            }
            val dateStr = java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.getDefault()).format(java.util.Date())
            canvas.drawText(dateStr, width - 96f, 126f, subHeaderPaint)

            // 4. 左侧作品档案信息
            val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = textPrimaryColor
                textSize = 46f
                isFakeBoldText = true
            }
            val shortTitle = if (book.title.length > 12) book.title.take(11) + "…" else book.title
            canvas.drawText("《$shortTitle》", 96f, 210f, titlePaint)

            val authorPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = textMutedColor
                textSize = 28f
            }
            val authorStr = "${book.mediaType.emoji} ${book.author ?: "未知作者"} · ${book.category ?: "精选"}"
            canvas.drawText(authorStr, 96f, 260f, authorPaint)

            // 左侧心智评语与指标徽章
            val badgeBoxPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.pillBgColor
                style = android.graphics.Paint.Style.FILL
            }
            val badgeTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                textSize = 24f
                isFakeBoldText = true
            }

            val p1 = "🧠 思想深度 ${mindprint.depthScore} · 🖋️ 文笔意境 ${mindprint.artistryScore}"
            val p2 = "❤️ 情感共鸣 ${mindprint.emotionScore} · 📐 逻辑构架 ${mindprint.logicScore}"
            val p3 = "⛰️ 阅读阻力 ${mindprint.difficultyScore} · 🌿 心灵治愈 ${mindprint.healingScore}"

            canvas.drawRoundRect(android.graphics.RectF(96f, 310f, 520f, 368f), 18f, 18f, badgeBoxPaint)
            canvas.drawText(p1, 114f, 348f, badgeTextPaint)

            canvas.drawRoundRect(android.graphics.RectF(96f, 388f, 520f, 446f), 18f, 18f, badgeBoxPaint)
            canvas.drawText(p2, 114f, 426f, badgeTextPaint)

            canvas.drawRoundRect(android.graphics.RectF(96f, 466f, 520f, 524f), 18f, 18f, badgeBoxPaint)
            canvas.drawText(p3, 114f, 504f, badgeTextPaint)

            // 左下方短评金句
            val quoteContent = book.shortComment?.takeIf { it.isNotBlank() }
                ?: book.review?.takeIf { it.isNotBlank() }
                ?: "“每一道心智印记，都是灵魂与文字的永恒交汇。”"
            val quotePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = textMutedColor
                textSize = 26f
            }
            val qLine = if (quoteContent.length > 16) quoteContent.take(15) + "…" else quoteContent
            canvas.drawText("💬 阅痕寄语：", 96f, 600f, badgeTextPaint)
            canvas.drawText(qLine, 96f, 646f, quotePaint)

            // 底部水印
            val footerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = textMutedColor
                textSize = 22f
                letterSpacing = 0.1f
            }
            canvas.drawText("READTRACE · ${theme.name}", 96f, 980f, footerPaint)

            // 5. 右侧原生绘制六维心智雷达
            val radarCenterX = 780f
            val radarCenterY = 560f
            val radarRadius = 210f

            val webPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.radarWebColor
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            val axisPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.radarAxisColor
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.radarFillColor
                style = android.graphics.Paint.Style.FILL
            }
            val strokeRadarPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.radarStrokeColor
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 5f
            }
            val dotPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.radarDotColor
                style = android.graphics.Paint.Style.FILL
            }
            val labelRadarPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = textPrimaryColor
                textSize = 24f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }

            val scores = floatArrayOf(
                mindprint.depthScore.toFloat(),
                mindprint.artistryScore.toFloat(),
                mindprint.emotionScore.toFloat(),
                mindprint.logicScore.toFloat(),
                mindprint.difficultyScore.toFloat(),
                mindprint.healingScore.toFloat(),
            )
            val labels = arrayOf("🧠 思想", "🖋️ 文笔", "❤️ 情感", "📐 逻辑", "⛰️ 门槛", "🌿 治愈")

            // 绘制 4 层蛛网多边形
            for (level in 1..4) {
                val r = radarRadius * (level / 4f)
                val path = android.graphics.Path()
                for (i in 0 until 6) {
                    val angle = (Math.PI / 3 * i - Math.PI / 2).toFloat()
                    val px = radarCenterX + r * Math.cos(angle.toDouble()).toFloat()
                    val py = radarCenterY + r * Math.sin(angle.toDouble()).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, webPaint)
            }

            // 绘制 6 条放射轴线及标签
            for (i in 0 until 6) {
                val angle = (Math.PI / 3 * i - Math.PI / 2).toFloat()
                val ax = radarCenterX + radarRadius * Math.cos(angle.toDouble()).toFloat()
                val ay = radarCenterY + radarRadius * Math.sin(angle.toDouble()).toFloat()
                canvas.drawLine(radarCenterX, radarCenterY, ax, ay, axisPaint)

                val lx = radarCenterX + (radarRadius + 44f) * Math.cos(angle.toDouble()).toFloat()
                val ly = radarCenterY + (radarRadius + 44f) * Math.sin(angle.toDouble()).toFloat() + 8f
                canvas.drawText(labels[i], lx, ly, labelRadarPaint)
            }

            // 绘制心智数据多边形
            val polyPath = android.graphics.Path()
            for (i in 0 until 6) {
                val angle = (Math.PI / 3 * i - Math.PI / 2).toFloat()
                val r = radarRadius * (scores[i] / 10f).coerceIn(0.1f, 1f)
                val px = radarCenterX + r * Math.cos(angle.toDouble()).toFloat()
                val py = radarCenterY + r * Math.sin(angle.toDouble()).toFloat()
                if (i == 0) polyPath.moveTo(px, py) else polyPath.lineTo(px, py)
            }
            polyPath.close()
            canvas.drawPath(polyPath, fillPaint)
            canvas.drawPath(polyPath, strokeRadarPaint)

            for (i in 0 until 6) {
                val angle = (Math.PI / 3 * i - Math.PI / 2).toFloat()
                val r = radarRadius * (scores[i] / 10f).coerceIn(0.1f, 1f)
                val px = radarCenterX + r * Math.cos(angle.toDouble()).toFloat()
                val py = radarCenterY + r * Math.sin(angle.toDouble()).toFloat()
                canvas.drawCircle(px, py, 9f, dotPaint)
            }

            // 6. 保存至相册与分享
            val bookName = book.title.replace(" ", "_")
            val filename = "ReadTrace_Widget_${theme.name}_${bookName}_${System.currentTimeMillis()}.png"
            var fos: java.io.OutputStream? = null
            var imageUri: android.net.Uri? = null

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/ReadTrace")
                }
                imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri)
                }
            } else {
                val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).toString() + "/ReadTrace"
                val file = java.io.File(imagesDir)
                if (!file.exists()) file.mkdirs()
                val imgFile = java.io.File(imagesDir, filename)
                fos = java.io.FileOutputStream(imgFile)
                imageUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    imgFile,
                )
            }

            if (fos != null && imageUri != null) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
                fos.close()
                Toast.makeText(this, "📱 已导出「${theme.displayName}」锁屏微卡至相册！", Toast.LENGTH_SHORT).show()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    putExtra(Intent.EXTRA_TEXT, "这是我在《阅痕 ReadTrace》为《${book.title}》定制的「${theme.displayName}」灵魂心智全息微卡。")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "分享「${theme.displayName}」锁屏微卡"))
            } else {
                Toast.makeText(this, "保存微卡失败，请重试", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "生成微卡异常: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMissingBookAndClose() {
        Toast.makeText(this, R.string.book_not_found, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_BOOK_ID = "com.example.readtrace.extra.BOOK_ID"
        private const val NO_BOOK_ID = -1L
        private const val DESCRIPTION_COLLAPSED_LINES = 3
        private val RATING_FORMAT = DecimalFormat("0.#")
        private val TIMELINE_DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
        private val DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun createIntent(context: Context, bookId: Long): Intent =
            Intent(context, BookDetailActivity::class.java)
                .putExtra(EXTRA_BOOK_ID, bookId)
    }
}
