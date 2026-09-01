package com.example.readtrace.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.example.readtrace.AddBookActivity
import com.example.readtrace.R
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.BangumiSubject
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.AutoTagSuggestionHelper
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.RankRepository
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.time.LocalDate

/**
 * ⚡ 3秒极速速记半屏弹窗 (QuickLogBottomSheet)
 *
 * P11 极简心流：主页点「+」弹出半屏速记 Sheet，无需跳转全屏大页面——
 * 顶部搜索框边输边搜（300ms 防抖联想公开元数据），选中条目后自动提炼候选标签胶囊，
 * 单手点选状态大按键瞬间落库 + 触感反馈，全程 2~3 秒完成一次记录。
 */
object QuickLogBottomSheet {

    private const val SEARCH_DEBOUNCE_MS = 300L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var sessionToken = 0L

    private var currentMedia = MediaType.BOOK
    private var pickedSubject: BangumiSubject? = null
    private val pickedTags = mutableSetOf<String>()

    fun show(activity: Activity) {
        val dialog = Dialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.layout_dialog_quick_log, null)
        dialog.setContentView(view)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            setGravity(Gravity.BOTTOM)
            setWindowAnimations(R.anim.quick_log_slide_up)
        }

        val databaseHelper = BookDatabaseHelper.getInstance(activity)
        val searchInput = view.findViewById<EditText>(R.id.quickLogSearchInput)
        val resultScroll = view.findViewById<ScrollView>(R.id.quickLogResultScroll)
        val resultList = view.findViewById<LinearLayout>(R.id.quickLogResultList)
        val confirmSection = view.findViewById<LinearLayout>(R.id.quickLogConfirmSection)
        val mediaRow = view.findViewById<LinearLayout>(R.id.quickLogMediaRow)
        val tagGroup = view.findViewById<ChipGroup>(R.id.quickLogTagGroup)
        val statusRow = view.findViewById<LinearLayout>(R.id.quickLogStatusRow)
        val ratingSeek = view.findViewById<SeekBar>(R.id.quickLogRatingSeek)
        val ratingText = view.findViewById<TextView>(R.id.quickLogRatingText)

        // --- 媒介切换胶囊 ---
        val mediaChips = mutableListOf<TextView>()
        MediaType.entries.forEach { media ->
            val chip = TextView(activity).apply {
                text = "${media.emoji} ${media.displayName}"
                textSize = 12f
                setPadding(22, 10, 22, 10)
                setOnClickListener {
                    HapticFeedbackEngine.cartridgeSnap(activity)
                    currentMedia = media
                    refreshMediaChips(mediaChips)
                    pickedSubject = null
                    confirmSection.visibility = View.GONE
                    searchInput.setText("")
                    resultList.removeAllViews()
                    resultScroll.visibility = View.GONE
                    triggerSearch(activity, searchInput.text.toString(), resultScroll, resultList, confirmSection)
                }
            }
            mediaChips.add(chip)
            mediaRow.addView(chip)
        }
        refreshMediaChips(mediaChips)

        // --- 边输边搜（300ms 防抖） ---
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchRunnable?.let(mainHandler::removeCallbacks)
                searchRunnable = Runnable {
                    triggerSearch(activity, s.toString().trim(), resultScroll, resultList, confirmSection)
                }
                mainHandler.postDelayed(searchRunnable!!, SEARCH_DEBOUNCE_MS)
            }
        })

        // --- 评分滑块（0~10 分，步进 0.5） ---
        ratingSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, progress: Int, fromUser: Boolean) {
                ratingText.text = if (progress == 0) "未评分" else String.format(java.util.Locale.US, "%.1f", progress / 2.0)
            }

            override fun onStartTrackingTouch(seek: SeekBar?) {}
            override fun onStopTrackingTouch(seek: SeekBar?) {
                HapticFeedbackEngine.cartridgeSnap(activity)
            }
        })

        // --- 五态大按键 ---
        BookStatus.entries.forEach { status ->
            val btn = TextView(activity).apply {
                text = status.getDisplayName(currentMedia)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(4, 26, 4, 26)
                background = activity.getDrawable(R.drawable.bg_quick_status_idle)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginEnd = 6
                }
                setOnClickListener {
                    val subject = pickedSubject ?: return@setOnClickListener
                    HapticFeedbackEngine.stampImpact(activity)
                    val selectedTags = tagGroup.checkedChipIds
                        .mapNotNull { tagGroup.findViewById<Chip>(it).text.toString() }
                        .ifEmpty { pickedTags.toList() }
                    insertQuickWork(
                        activity,
                        databaseHelper,
                        subject,
                        status,
                        selectedTags,
                        ratingSeek.progress.takeIf { it > 0 }?.let { it / 2.0 },
                    )
                    dialog.dismiss()
                }
            }
            statusRow.addView(btn)
        }

        view.findViewById<TextView>(R.id.quickLogClose).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.quickLogAdvancedEntry).setOnClickListener {
            dialog.dismiss()
            activity.startActivity(android.content.Intent(activity, AddBookActivity::class.java))
        }

        dialog.show()
    }

    /** 刷新媒介胶囊选中态 */
    private fun refreshMediaChips(chips: List<TextView>) {
        chips.forEachIndexed { index, chip ->
            val media = MediaType.entries[index]
            val selected = media == currentMedia
            chip.setBackgroundResource(if (selected) R.drawable.bg_dark_chip_selected else R.drawable.bg_dark_chip)
            chip.setTextColor(
                Color.parseColor(if (selected) "#FFFFFF" else "#99FFFFFF"),
            )
        }
    }

    /** 发起联想搜索（新会话隔离，杜绝旧请求串扰） */
    private fun triggerSearch(
        context: Context,
        keyword: String,
        resultScroll: ScrollView,
        resultList: LinearLayout,
        confirmSection: LinearLayout,
    ) {
        resultList.removeAllViews()
        if (keyword.isEmpty()) {
            resultScroll.visibility = View.GONE
            return
        }
        sessionToken = RankRepository.startSession(currentMedia, keyword)
        val token = sessionToken
        RankRepository.loadPage(context, token, forceRefresh = false) { page ->
            if (token != sessionToken) return@loadPage // 会话已过期
            val items = page?.items.orEmpty()
            if (items.isEmpty()) {
                resultScroll.visibility = View.VISIBLE
                resultList.addView(makeHintRow(context, "未找到相关作品，试试换个关键词"))
                return@loadPage
            }
            resultScroll.visibility = View.VISIBLE
            items.take(15).forEach { subject ->
                resultList.addView(makeResultRow(context, subject, resultScroll, confirmSection))
            }
        }
    }

    /** 构造单条联想结果行 */
    private fun makeResultRow(
        context: Context,
        subject: BangumiSubject,
        resultScroll: ScrollView,
        confirmSection: LinearLayout,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 10, 12, 10)
            background = context.getDrawable(R.drawable.bg_quick_status_idle)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = 8 }
        }

        val cover = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(44, 62).apply { marginEnd = 12 }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        subject.coverUrl?.let { url ->
            CoverImageHelper.loadCoverBitmap(context, url, 100, 150) { bmp ->
                if (bmp != null) cover.setImageBitmap(bmp)
            }
        }
        row.addView(cover)

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(
            TextView(context).apply {
                text = subject.displayTitle
                textSize = 14f
                setTextColor(Color.WHITE)
                maxLines = 1
            },
        )
        textCol.addView(
            TextView(context).apply {
                text = listOfNotNull(
                    subject.creator,
                    subject.ratingScore?.let { "★%.1f".format(it) },
                    subject.date?.take(4),
                ).joinToString(" · ").ifBlank { "—" }
                textSize = 11f
                setTextColor(Color.parseColor("#99FFFFFF"))
                maxLines = 1
            },
        )
        row.addView(textCol)

        row.setOnClickListener {
            HapticFeedbackEngine.cartridgeSnap(context)
            pickedSubject = subject
            resultScroll.visibility = View.GONE
            confirmSection.visibility = View.VISIBLE
            bindConfirmSection(context, subject, confirmSection)
        }
        return row
    }

    /** 选中作品 → 展示标题 + 自动提炼候选标签 */
    private fun bindConfirmSection(context: Context, subject: BangumiSubject, confirmSection: LinearLayout) {
        confirmSection.findViewById<TextView>(R.id.quickLogPickedTitle).text = "《${subject.displayTitle}》"
        confirmSection.findViewById<TextView>(R.id.quickLogPickedCreator).text =
            listOfNotNull(subject.creator, subject.date).joinToString(" · ").ifBlank { "—" }

        pickedTags.clear()
        val suggestions = AutoTagSuggestionHelper.suggestTags(subject, currentMedia)
        pickedTags.addAll(suggestions.take(2)) // 默认自动勾选最契合的前 2 个

        val tagGroup = confirmSection.findViewById<ChipGroup>(R.id.quickLogTagGroup)
        tagGroup.removeAllViews()
        suggestions.forEach { tag ->
            val chip = Chip(context).apply {
                text = tag
                isCheckable = true
                isChecked = pickedTags.contains(tag)
                setChipBackgroundColorResource(R.color.chip_quick_log_bg)
                setTextColor(Color.WHITE)
                chipStrokeWidth = 1f
                setChipStrokeColorResource(R.color.chip_quick_log_stroke)
                chipCornerRadius = 20f * context.resources.displayMetrics.density
                textSize = 12f
                setOnCheckedChangeListener { _, checked ->
                    if (checked) pickedTags.add(tag) else pickedTags.remove(tag)
                    HapticFeedbackEngine.cartridgeSnap(context)
                }
            }
            tagGroup.addView(chip)
        }
    }

    /** 一键落库：状态 + 标签 + 评分 → insertBook */
    private fun insertQuickWork(
        context: Context,
        databaseHelper: BookDatabaseHelper,
        subject: BangumiSubject,
        status: BookStatus,
        tags: List<String>,
        rating: Double?,
    ) {
        val today = LocalDate.now().toString()
        val book = Book(
            title = subject.displayTitle,
            author = subject.creator,
            coverUrl = subject.coverUrl,
            category = tags.firstOrNull(),
            status = status,
            mediaType = currentMedia,
            rating = rating,
            tags = tags,
            shortComment = null,
            startDate = if (status == BookStatus.READING) today else null,
            finishDate = if (status == BookStatus.FINISHED) today else null,
            createdAt = "",
            updatedAt = "",
            sourceType = subject.source,
            sourceId = subject.id.toString(),
            remoteRating = subject.ratingScore,
            description = subject.summary,
        )
        val newId = databaseHelper.insertBook(book)
        if (newId > 0) {
            Toast.makeText(
                context,
                "⚡ 已入库《${subject.displayTitle}》· ${status.getDisplayName(currentMedia)}",
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            Toast.makeText(context, "入库失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    private fun makeHintRow(context: Context, text: String): View =
        TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.parseColor("#80FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
        }
}
