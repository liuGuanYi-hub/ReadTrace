package com.example.readtrace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.Note
import com.example.readtrace.model.NoteType
import com.example.readtrace.util.CoverImageHelper
import java.text.DecimalFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class BookDetailActivity : AppCompatActivity() {
    private lateinit var databaseHelper: BookDatabaseHelper
    private var bookId: Long = NO_BOOK_ID
    private var currentBook: Book? = null

    private val importTxtLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri: android.net.Uri? ->
        if (uri != null && bookId != NO_BOOK_ID) {
            val success = com.example.readtrace.reader.TxtReaderHelper.importTxtFromUri(this, bookId, uri)
            if (success) {
                Toast.makeText(this, R.string.reader_import_txt_success, Toast.LENGTH_SHORT).show()
                startActivity(com.example.readtrace.reader.Book3DReaderActivity.createIntent(this, bookId))
            } else {
                Toast.makeText(this, R.string.reader_import_txt_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

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

        findViewById<View>(R.id.detailBackButton).setOnClickListener { finish() }
        findViewById<View>(R.id.detailRead3DButton).setOnClickListener {
            startActivity(com.example.readtrace.reader.Book3DReaderActivity.createIntent(this, bookId))
        }
        findViewById<View>(R.id.detailImportTxtButton).setOnClickListener {
            importTxtLauncher.launch(arrayOf("text/plain", "*/*"))
        }
        findViewById<View>(R.id.detailEditButton).setOnClickListener {
            startActivity(AddBookActivity.createEditIntent(this, bookId))
        }
        findViewById<View>(R.id.detailArchiveButton).setOnClickListener {
            confirmArchive()
        }
        findViewById<View>(R.id.detailNotesAddButton).setOnClickListener {
            startActivity(AddNoteActivity.createAddIntent(this, bookId))
        }
        findViewById<View>(R.id.detailStartTimerButton).setOnClickListener {
            currentBook?.let { book ->
                startActivity(ReadingTimerActivity.createIntent(this, book.id, book.title))
            }
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
        findViewById<View>(R.id.detailQuotePosterButton).setOnClickListener {
            currentBook?.let { book ->
                val notes = databaseHelper.getNotes(book.id)
                val quote = book.shortComment?.takeIf { it.isNotBlank() }
                    ?: notes.firstOrNull()?.content
                    ?: "字句有痕，岁月有温。在文字的世界里，每一次阅读都是灵魂的漫游。"
                val source = if (book.shortComment.isNullOrBlank() && notes.isNotEmpty()) {
                    listOfNotNull(notes.first().chapter, notes.first().page?.let { "P.$it" }).joinToString(" · ")
                } else "一句话感悟"
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
        }

        // 📑 轻量组件化多板块快捷导航条
        val navOverview = findViewById<TextView>(R.id.navTabOverview)
        val navTimeline = findViewById<TextView>(R.id.navTabTimeline)
        val navCharacters = findViewById<TextView>(R.id.navTabCharacters)
        val navNotes = findViewById<TextView>(R.id.navTabNotes)
        val mainScrollView = findViewById<android.widget.ScrollView>(R.id.detailContent)

        fun updateNavTabs(activeTab: TextView) {
            listOf(navOverview, navTimeline, navCharacters, navNotes).forEach {
                val isSelected = it == activeTab
                it.setBackgroundResource(if (isSelected) R.drawable.bg_status_chip_selected else R.drawable.bg_status_chip)
                it.setTextColor(getColor(if (isSelected) R.color.white else R.color.readtrace_ink))
            }
        }

        navOverview.setOnClickListener {
            updateNavTabs(navOverview)
            mainScrollView.smoothScrollTo(0, 0)
        }
        navTimeline.setOnClickListener {
            updateNavTabs(navTimeline)
            val y = findViewById<View>(R.id.detailTimelineSection).top
            mainScrollView.smoothScrollTo(0, y)
        }
        navCharacters.setOnClickListener {
            updateNavTabs(navCharacters)
            val y = findViewById<View>(R.id.detailCharsContainer).parent?.let { (it as View).top } ?: 0
            mainScrollView.smoothScrollTo(0, y)
        }
        navNotes.setOnClickListener {
            updateNavTabs(navNotes)
            val y = findViewById<View>(R.id.detailNotesContainer).parent?.let { (it as View).top } ?: 0
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
        findViewById<View>(R.id.detailCompareMindprintBtn).setOnClickListener {
            showCompareMindprintDialog()
        }

        // 注入 iOS 级 Q 弹手势触觉反馈
        listOfNotNull(
            findViewById(R.id.detailBackButton),
            findViewById(R.id.detailEditButton),
            findViewById(R.id.detailArchiveButton),
            findViewById(R.id.detailStartTimerButton),
            findViewById(R.id.detailQuotePosterButton),
            findViewById(R.id.detailRead3DButton),
            findViewById(R.id.detailImportTxtButton),
            findViewById(R.id.detailAddCharButton),
            findViewById(R.id.detailAddOutlineButton),
            findViewById(R.id.detailAddLocationBtn),
            findViewById(R.id.detailEditMindprintBtn),
            findViewById(R.id.detailCompareMindprintBtn),
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
        renderReadingSessions(databaseHelper.getReadingSessions(bookId))
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
    }

    private fun renderCollection(book: Book) {
        val hasCollectionInfo = !book.buyChannel.isNullOrBlank() ||
            !book.shelfLocation.isNullOrBlank() ||
            !book.bindingType.isNullOrBlank() ||
            book.buyPrice != null

        findViewById<View>(R.id.detailCollectionCard).visibility =
            if (hasCollectionInfo) View.VISIBLE else View.GONE

        findViewById<TextView>(R.id.detailBuyChannel).text = valueOrFallback(book.buyChannel)
        findViewById<TextView>(R.id.detailShelfLocation).text = valueOrFallback(book.shelfLocation)
        findViewById<TextView>(R.id.detailBindingType).text = valueOrFallback(book.bindingType)
        findViewById<TextView>(R.id.detailBuyPrice).text = book.buyPrice?.let {
            String.format(Locale.getDefault(), "¥ %.2f", it)
        } ?: getString(R.string.not_recorded)
    }

    private fun renderReadingSessions(sessions: List<com.example.readtrace.model.ReadingSession>) {
        val container = findViewById<LinearLayout>(R.id.detailSessionsContainer)
        val emptyView = findViewById<TextView>(R.id.detailSessionsEmpty)
        val totalTimeView = findViewById<TextView>(R.id.detailTotalReadingTime)
        container.removeAllViews()

        val totalMinutes = databaseHelper.getTotalReadingMinutes(bookId)
        val hours = totalMinutes / 60
        val remainingMins = totalMinutes % 60
        val timeFormatted = if (hours > 0) "${hours} 小时 ${remainingMins} 分钟" else "${remainingMins} 分钟"
        totalTimeView.text = "⌛ 已累计阅读 $timeFormatted · 打卡 ${sessions.size} 次"

        if (sessions.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        container.visibility = View.VISIBLE

        sessions.forEach { session ->
            val item = layoutInflater.inflate(R.layout.item_reading_session, container, false)
            item.findViewById<TextView>(R.id.sessionDurationBadge).text = "⏱️ 专注 ${session.durationMinutes} 分钟"
            item.findViewById<TextView>(R.id.sessionPagesText).text = session.pagesRead ?: "阅读打卡"
            item.findViewById<TextView>(R.id.sessionTimeText).text = formatTimestamp(session.createdAt)

            val thoughtView = item.findViewById<TextView>(R.id.sessionThoughtText)
            if (session.thought.isNullOrBlank()) {
                thoughtView.visibility = View.GONE
            } else {
                thoughtView.visibility = View.VISIBLE
                thoughtView.text = session.thought
            }

            item.findViewById<View>(R.id.sessionDeleteBtn).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("删除打卡记录")
                    .setMessage("确定要删除本次 ${session.durationMinutes} 分钟的阅读打卡吗？")
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton("删除") { _, _ ->
                        databaseHelper.deleteReadingSession(session.id)
                        renderReadingSessions(databaseHelper.getReadingSessions(bookId))
                    }
                    .show()
            }

            container.addView(item)
        }
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
                AlertDialog.Builder(this)
                    .setTitle("删除角色")
                    .setMessage("确定要从人物谱中移除「${char.name}」吗？")
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton("删除") { _, _ ->
                        databaseHelper.deleteCharacter(char.id)
                        renderCharacters(databaseHelper.getCharacters(bookId))
                    }
                    .show()
            }
            com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(item, 0.97f)
            com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(delBtn)

            container.addView(item)
        }
    }

    private fun showAddCharacterDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val nameInput = EditText(this).apply {
            hint = "角色姓名 (如：小狐狸)"
            textSize = 14f
        }
        val roleInput = EditText(this).apply {
            hint = "身份/阵营/头衔 (如：启蒙导师 · 麦田守望者)"
            textSize = 13f
        }
        val emojiInput = EditText(this).apply {
            hint = "角色 Emoji (如：🦊 / 👑 / 🌹)"
            setText("👤")
            textSize = 14f
        }
        val descInput = EditText(this).apply {
            hint = "人物生平与性格简述..."
            textSize = 13f
        }
        val relInput = EditText(this).apply {
            hint = "与主角或其他人物的核心羁绊关系..."
            textSize = 13f
        }

        dialogView.addView(nameInput)
        dialogView.addView(roleInput)
        dialogView.addView(emojiInput)
        dialogView.addView(descInput)
        dialogView.addView(relInput)

        AlertDialog.Builder(this)
            .setTitle("👥 添加人物角色")
            .setView(dialogView)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("保存角色") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    val character = com.example.readtrace.model.BookCharacter(
                        bookId = bookId,
                        name = name,
                        roleTitle = roleInput.text.toString().trim().ifBlank { null },
                        avatarEmoji = emojiInput.text.toString().trim().ifBlank { "👤" },
                        description = descInput.text.toString().trim().ifBlank { null },
                        relationship = relInput.text.toString().trim().ifBlank { null },
                    )
                    databaseHelper.insertCharacter(character)
                    renderCharacters(databaseHelper.getCharacters(bookId))
                }
            }
            .show()
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
                AlertDialog.Builder(this)
                    .setTitle("删除章节大纲")
                    .setMessage("确定要删除「${outline.title}」的大纲吗？")
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton("删除") { _, _ ->
                        databaseHelper.deleteOutline(outline.id)
                        renderOutlines(databaseHelper.getOutlines(bookId))
                    }
                    .show()
            }
            com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(item, 0.97f)
            com.example.readtrace.util.ViewAnimationHelper.attachSpringTouch(delBtn)

            container.addView(item)
        }
    }

    private fun showAddOutlineDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val orderInput = EditText(this).apply {
            hint = "章节序号 (如：1)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("${databaseHelper.getOutlines(bookId).size + 1}")
            textSize = 14f
        }
        val titleInput = EditText(this).apply {
            hint = "章节名称 (如：荒原中的相遇与羊的肖像)"
            textSize = 14f
        }
        val summaryInput = EditText(this).apply {
            hint = "核心情节 / 大纲概要..."
            textSize = 13f
        }
        val takeawaysInput = EditText(this).apply {
            hint = "思想精髓 / 脑图脉络要点 (选填)..."
            textSize = 13f
        }

        dialogView.addView(orderInput)
        dialogView.addView(titleInput)
        dialogView.addView(summaryInput)
        dialogView.addView(takeawaysInput)

        AlertDialog.Builder(this)
            .setTitle("🗺️ 添加章节大纲与脑图")
            .setView(dialogView)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("保存大纲") { _, _ ->
                val title = titleInput.text.toString().trim()
                val summary = summaryInput.text.toString().trim()
                val order = orderInput.text.toString().trim().toIntOrNull() ?: 1
                if (title.isNotEmpty() && summary.isNotEmpty()) {
                    val outline = com.example.readtrace.model.BookOutline(
                        bookId = bookId,
                        chapterOrder = order,
                        title = title,
                        summary = summary,
                        keyTakeaways = takeawaysInput.text.toString().trim().ifBlank { null },
                    )
                    databaseHelper.insertOutline(outline)
                    renderOutlines(databaseHelper.getOutlines(bookId))
                }
            }
            .show()
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
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        fun createScoreInput(label: String, initScore: Double): Pair<TextView, EditText> {
            val tv = TextView(this).apply {
                text = "$label (当前: $initScore)"
                textSize = 13f
                setTextColor(getColor(R.color.readtrace_ink))
                setPadding(0, 12, 0, 4)
            }
            val et = EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(String.format(Locale.getDefault(), "%.1f", initScore))
                textSize = 14f
            }
            return tv to et
        }

        val (depthTv, depthEt) = createScoreInput("🧠 思想深度 (1~10)", current.depthScore)
        val (artistryTv, artistryEt) = createScoreInput("🖋️ 文笔意境 (1~10)", current.artistryScore)
        val (emotionTv, emotionEt) = createScoreInput("❤️ 情感共鸣 (1~10)", current.emotionScore)
        val (logicTv, logicEt) = createScoreInput("📐 逻辑构架 (1~10)", current.logicScore)
        val (difficultyTv, difficultyEt) = createScoreInput("⛰️ 阅读门槛 (1~10)", current.difficultyScore)
        val (healingTv, healingEt) = createScoreInput("🌿 心灵治愈 (1~10)", current.healingScore)

        val scroll = android.widget.ScrollView(this).apply {
            addView(dialogView.apply {
                addView(depthTv); addView(depthEt)
                addView(artistryTv); addView(artistryEt)
                addView(emotionTv); addView(emotionEt)
                addView(logicTv); addView(logicEt)
                addView(difficultyTv); addView(difficultyEt)
                addView(healingTv); addView(healingEt)
            })
        }

        AlertDialog.Builder(this)
            .setTitle("🕸️ 调整六维心智评分")
            .setView(scroll)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("保存评分") { _, _ ->
                fun parseScore(et: EditText, fallback: Double): Double =
                    et.text.toString().trim().toDoubleOrNull()?.coerceIn(1.0, 10.0) ?: fallback

                val newMindprint = current.copy(
                    depthScore = parseScore(depthEt, current.depthScore),
                    artistryScore = parseScore(artistryEt, current.artistryScore),
                    emotionScore = parseScore(emotionEt, current.emotionScore),
                    logicScore = parseScore(logicEt, current.logicScore),
                    difficultyScore = parseScore(difficultyEt, current.difficultyScore),
                    healingScore = parseScore(healingEt, current.healingScore),
                )
                databaseHelper.saveMindprint(newMindprint)
                renderMindprint(databaseHelper.getMindprint(bookId))
                Toast.makeText(this, "六维心智评分已更新", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showCompareMindprintDialog() {
        val allBooks = databaseHelper.getBooks().filter { it.id != bookId }
        if (allBooks.isEmpty()) {
            Toast.makeText(this, "书架中暂无其他作品可用于对比", Toast.LENGTH_SHORT).show()
            return
        }

        val items = mutableListOf("✦ 取消对比 (恢复单作品雷达)")
        items.addAll(allBooks.map { "《${it.title}》· ${it.author ?: "未知作者"}" })

        AlertDialog.Builder(this)
            .setTitle("🔍 选择要对比的心智作品")
            .setItems(items.toTypedArray()) { _, which ->
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
            .show()
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
                AlertDialog.Builder(this)
                    .setTitle("删除空间地标")
                    .setMessage("确定要移除地标「${loc.name}」吗？")
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton("删除") { _, _ ->
                        databaseHelper.deleteLocation(loc.id)
                        renderLocations(databaseHelper.getLocations(bookId))
                    }
                    .show()
            }

            container.addView(item)
        }
    }

    private fun showAddLocationDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val nameInput = EditText(this).apply {
            hint = "地标名称 (如：B-612小行星 / 贝克街221B)"
            textSize = 14f
        }
        val typeInput = EditText(this).apply {
            hint = "空间类型 (如：🪐 架空星际 / 🏙️ 现实都市 / 🌲 荒原自然)"
            setText("🏙️ 现实都市")
            textSize = 13f
        }
        val descInput = EditText(this).apply {
            hint = "空间环境与景观描写..."
            textSize = 13f
        }
        val sigInput = EditText(this).apply {
            hint = "核心情节发生与象征意义..."
            textSize = 13f
        }
        val coordInput = EditText(this).apply {
            hint = "空间方位 / 坐标 (选填，如：撒哈拉沙漠腹地)..."
            textSize = 13f
        }

        dialogView.addView(nameInput)
        dialogView.addView(typeInput)
        dialogView.addView(descInput)
        dialogView.addView(sigInput)
        dialogView.addView(coordInput)

        AlertDialog.Builder(this)
            .setTitle("🗺️ 添加空间叙事地标")
            .setView(dialogView)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("保存地标") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    val location = com.example.readtrace.model.BookLocation(
                        bookId = bookId,
                        name = name,
                        locationType = typeInput.text.toString().trim().ifBlank { "🏙️ 现实都市" },
                        description = descInput.text.toString().trim().ifBlank { null },
                        significance = sigInput.text.toString().trim().ifBlank { null },
                        coordinates = coordInput.text.toString().trim().ifBlank { null },
                    )
                    databaseHelper.insertLocation(location)
                    renderLocations(databaseHelper.getLocations(bookId))
                    currentBook?.let { refreshTimelineOnly(it) }
                }
            }
            .show()
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

        // 1. 初读起点
        if (!book.startDate.isNullOrBlank()) {
            events.add(
                com.example.readtrace.model.TimelineEvent(
                    id = "start_${book.id}",
                    type = com.example.readtrace.model.TimelineEventType.START_READING,
                    timestamp = book.startDate + "T08:00:00",
                    title = "🏁 启程 · 翻开扉页",
                    subtitle = "当前状态：${book.status.displayName} · 载体：${book.mediaType.displayName}",
                    content = "初读期待与相遇",
                    extraMeta = book.category?.let { "分类：$it" },
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
                    title = "⏱️ 专注阅读 ${session.durationMinutes} 分钟",
                    subtitle = session.pagesRead?.takeIf { it.isNotBlank() }?.let { "读至：$it" },
                    content = session.thought,
                    extraMeta = "打卡印记",
                    rawId = session.id,
                ),
            )
        }

        // 3. 灵感与高光摘录
        notes.forEach { note ->
            val positionMeta = listOfNotNull(
                note.chapter?.trim()?.takeIf { it.isNotEmpty() },
                note.page?.trim()?.takeIf { it.isNotEmpty() }?.let { "P.$it" },
            ).joinToString(" · ")

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
                    title = "📖 第 ${outline.chapterOrder} 章：${outline.title}",
                    subtitle = "全书大纲脉络",
                    content = outline.summary,
                    extraMeta = outline.keyTakeaways?.let { "💡 思想精髓：$it" },
                    rawId = outline.id,
                ),
            )
        }

        // 6. 完读与深度复盘
        if (!book.finishDate.isNullOrBlank() || book.status == BookStatus.FINISHED) {
            events.add(
                com.example.readtrace.model.TimelineEvent(
                    id = "finish_${book.id}",
                    type = com.example.readtrace.model.TimelineEventType.FINISH_REVIEW,
                    timestamp = (book.finishDate ?: book.updatedAt) + "T23:59:59",
                    title = "🌟 终章 · 全书完读复盘",
                    subtitle = book.rating?.let { "个人评分：★ $it / 5.0" } ?: "已读完",
                    content = book.review?.takeIf { it.isNotBlank() } ?: book.shortComment,
                    extraMeta = "精神沉淀与思想烙印",
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
            item.findViewById<TextView>(R.id.timelineTimeText).text = formatTimestamp(event.timestamp)

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
                AlertDialog.Builder(this)
                    .setTitle("🎉 时间轴长图已生成")
                    .setMessage("全息心路长图已成功保存至系统相册！是否立即分享给书友？")
                    .setNegativeButton("稍后再说", null)
                    .setPositiveButton("🔗 立即分享") { _, _ ->
                        shareTimelineBitmap(bitmap)
                    }
                    .show()
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

    private fun renderBook(book: Book) {
        val coverImage = findViewById<ImageView>(R.id.detailCoverImage)
        CoverImageHelper.loadCover(coverImage, book.coverUrl)

        findViewById<TextView>(R.id.detailMediaBadge).text = "${book.mediaType.emoji} ${book.mediaType.displayName}"
        findViewById<TextView>(R.id.detailBookTitle).text = book.title
        findViewById<TextView>(R.id.detailBookAuthor).text = valueOrFallback(book.author)
        findViewById<TextView>(R.id.detailHeroMeta).text = buildHeroMeta(book)
        findViewById<TextView>(R.id.detailCategory).text = valueOrFallback(book.category)
        findViewById<TextView>(R.id.detailCoverUrl).text = valueOrFallback(book.coverUrl)
        findViewById<TextView>(R.id.detailStatus).text = book.status.getDisplayName(book.mediaType)
        findViewById<TextView>(R.id.detailRating).text = book.rating?.let {
            getString(R.string.rating_format, RATING_FORMAT.format(it))
        } ?: getString(R.string.not_recorded)
        findViewById<TextView>(R.id.detailTags).text =
            if (book.tags.isEmpty()) {
                getString(R.string.not_recorded)
            } else {
                book.tags.joinToString(" · ")
            }
        findViewById<TextView>(R.id.detailStartDate).text = valueOrFallback(book.startDate)
        findViewById<TextView>(R.id.detailFinishDate).text = valueOrFallback(book.finishDate)
        findViewById<TextView>(R.id.detailShortComment).text =
            valueOrFallback(book.shortComment)
        findViewById<TextView>(R.id.detailReview).text = valueOrFallback(book.review)
        findViewById<TextView>(R.id.detailCreatedAt).text = formatTimestamp(book.createdAt)
        findViewById<TextView>(R.id.detailUpdatedAt).text = formatTimestamp(book.updatedAt)
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
            val positionMeta = buildList {
                note.page?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    add(getString(R.string.note_page_format, it))
                }
                note.chapter?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            }.joinToString(" · ")
            item.findViewById<TextView>(R.id.notePositionMeta).apply {
                text = positionMeta
                visibility = if (positionMeta.isEmpty()) View.GONE else View.VISIBLE
            }
            val params = item.layoutParams as LinearLayout.LayoutParams
            params.topMargin = dpToPx(if (index == 0) 14 else 10)
            item.layoutParams = params
            item.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("笔记印记操作")
                    .setItems(arrayOf("🎨 生成专属金句海报", "✏️ 编辑笔记", "📦 归档至回收站")) { _, which ->
                        when (which) {
                            0 -> {
                                currentBook?.let { book ->
                                    val source = listOfNotNull(note.chapter, note.page?.let { "P.$it" }).joinToString(" · ")
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
                    .show()
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
        AlertDialog.Builder(this)
            .setTitle(R.string.note_archive_confirm_title)
            .setMessage(R.string.note_archive_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_archive) { _, _ ->
                val archived = runCatching {
                    databaseHelper.archiveNote(note.id)
                }.getOrDefault(false)
                if (archived) {
                    Toast.makeText(this, R.string.note_archive_success, Toast.LENGTH_SHORT).show()
                    renderNotes(databaseHelper.getNotes(bookId))
                } else {
                    Toast.makeText(this, R.string.note_archive_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun dpToPx(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun confirmArchive() {
        val book = currentBook ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.archive_confirm_title)
            .setMessage(R.string.archive_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_archive) { _, _ ->
                archiveBook(book.id)
            }
            .show()
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

    private fun buildHeroMeta(book: Book): String {
        val ratingLabel = book.rating?.let {
            getString(R.string.rating_format, RATING_FORMAT.format(it))
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
        private val RATING_FORMAT = DecimalFormat("0.#")
        private val DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun createIntent(context: Context, bookId: Long): Intent =
            Intent(context, BookDetailActivity::class.java)
                .putExtra(EXTRA_BOOK_ID, bookId)
    }
}
