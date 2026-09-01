package com.example.readtrace

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.util.BookCsvParser
import com.example.readtrace.util.FloatingBack
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.MultiSourceMigrationHelper
import com.example.readtrace.util.ViewAnimationHelper
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 🚚 多源 0 门槛资产搬家中心 (Data Migration Activity)
 */
class DataMigrationActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper

    private lateinit var tabDouban: TextView
    private lateinit var tabBangumi: TextView
    private lateinit var tabSteam: TextView
    private lateinit var tabCsv: TextView

    private lateinit var tvGuideTitle: TextView
    private lateinit var tvGuideContent: TextView
    private lateinit var etRawContent: EditText
    private lateinit var btnPasteClipboard: View
    private lateinit var btnPickLocalFile: View

    private lateinit var previewCard: View
    private lateinit var tvPreviewSummary: TextView
    private lateinit var tvPreviewDetails: TextView
    private lateinit var btnExecuteMigration: TextView

    private var currentPlatform: MultiSourceMigrationHelper.SourcePlatform = MultiSourceMigrationHelper.SourcePlatform.DOUBAN
    private var parsedRecords: List<BookCsvParser.ParsedBookRecord> = emptyList()

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            readTextFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_data_migration)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.migrationRoot)) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        databaseHelper = BookDatabaseHelper.getInstance(this)
        FloatingBack.install(this)

        initViews()
        setupListeners()
        updateTabSelection()
    }

    private fun initViews() {
        tabDouban = findViewById(R.id.tabMigrateDouban)
        tabBangumi = findViewById(R.id.tabMigrateBangumi)
        tabSteam = findViewById(R.id.tabMigrateSteam)
        tabCsv = findViewById(R.id.tabMigrateCsv)

        tvGuideTitle = findViewById(R.id.tvMigrationGuideTitle)
        tvGuideContent = findViewById(R.id.tvMigrationGuideContent)
        etRawContent = findViewById(R.id.etMigrationRawContent)
        btnPasteClipboard = findViewById(R.id.btnPasteClipboard)
        btnPickLocalFile = findViewById(R.id.btnPickLocalFile)

        previewCard = findViewById(R.id.migrationPreviewCard)
        tvPreviewSummary = findViewById(R.id.tvMigrationPreviewSummary)
        tvPreviewDetails = findViewById(R.id.tvMigrationPreviewDetails)
        btnExecuteMigration = findViewById(R.id.btnExecuteMigration)

        listOf(btnPasteClipboard, btnPickLocalFile, btnExecuteMigration).forEach {
            ViewAnimationHelper.attachSpringTouch(it)
        }
    }

    private fun setupListeners() {
        tabDouban.setOnClickListener { switchPlatform(MultiSourceMigrationHelper.SourcePlatform.DOUBAN) }
        tabBangumi.setOnClickListener { switchPlatform(MultiSourceMigrationHelper.SourcePlatform.BANGUMI) }
        tabSteam.setOnClickListener { switchPlatform(MultiSourceMigrationHelper.SourcePlatform.STEAM) }
        tabCsv.setOnClickListener { switchPlatform(MultiSourceMigrationHelper.SourcePlatform.NOTION_CSV) }

        btnPasteClipboard.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipboard?.primaryClip?.getItemAt(0)
            val text = item?.text?.toString() ?: ""
            if (text.isNotBlank()) {
                etRawContent.setText(text)
                Toast.makeText(this, "已粘贴剪贴板内容", Toast.LENGTH_SHORT).show()
                triggerParseAsync(text)
            } else {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
            }
        }

        btnPickLocalFile.setOnClickListener {
            pickFileLauncher.launch("*/*")
        }

        etRawContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                triggerParseAsync(s?.toString() ?: "")
            }
        })

        btnExecuteMigration.setOnClickListener {
            executeImport()
        }
    }

    private fun switchPlatform(platform: MultiSourceMigrationHelper.SourcePlatform) {
        currentPlatform = platform
        HapticFeedbackEngine.lightClick(this)
        updateTabSelection()
        triggerParseAsync(etRawContent.text.toString())
    }

    private fun updateTabSelection() {
        val tabs = listOf(
            tabDouban to MultiSourceMigrationHelper.SourcePlatform.DOUBAN,
            tabBangumi to MultiSourceMigrationHelper.SourcePlatform.BANGUMI,
            tabSteam to MultiSourceMigrationHelper.SourcePlatform.STEAM,
            tabCsv to MultiSourceMigrationHelper.SourcePlatform.NOTION_CSV,
        )

        tabs.forEach { (tab, p) ->
            if (p == currentPlatform) {
                tab.setBackgroundResource(R.drawable.bg_status_chip_selected)
                tab.setTextColor(Color.WHITE)
                tab.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                tab.setBackgroundResource(0)
                tab.setTextColor(Color.parseColor("#90FFFFFF"))
                tab.typeface = android.graphics.Typeface.DEFAULT
            }
        }

        when (currentPlatform) {
            MultiSourceMigrationHelper.SourcePlatform.DOUBAN -> {
                tvGuideTitle.text = "💡 豆瓣书影音导入指南"
                tvGuideContent.text = "支持直接粘贴豆瓣导出的 CSV/文本，或通过「选取文件」载入。自动提取标题、评分、读过日期与短评，并智能转化为六维心智模型。"
            }
            MultiSourceMigrationHelper.SourcePlatform.BANGUMI -> {
                tvGuideTitle.text = "🌸 Bangumi 番组计划导入指南"
                tvGuideContent.text = "支持直接粘贴 Bangumi 收藏 API 返回的 JSON 数据。自动解析动画/漫画、中文译名、原名与在看/补完状态。"
            }
            MultiSourceMigrationHelper.SourcePlatform.STEAM -> {
                tvGuideTitle.text = "🎮 Steam 游戏库导入指南"
                tvGuideContent.text = "支持粘贴 Steam GetOwnedGames 接口返回的 JSON。自动将游戏游玩时长换算为在读/通关状态并生成心智模型。"
            }
            MultiSourceMigrationHelper.SourcePlatform.NOTION_CSV -> {
                tvGuideTitle.text = "📋 Notion / 通用表格导入指南"
                tvGuideContent.text = "支持粘贴任意第三方 CSV 表格，智能探测书名、作者、评分、状态与标签列。"
            }
        }
    }

    private fun triggerParseAsync(content: String) {
        if (content.isBlank()) {
            previewCard.visibility = View.GONE
            parsedRecords = emptyList()
            return
        }

        Thread {
            val results = MultiSourceMigrationHelper.parseContent(content, currentPlatform)
            runOnUiThread {
                parsedRecords = results
                if (results.isNotEmpty()) {
                    previewCard.visibility = View.VISIBLE
                    tvPreviewSummary.text = "✨ 已检测到  部待导入作品"
                    val finishedCount = results.count { it.book.status == com.example.readtrace.model.BookStatus.FINISHED }
                    tvPreviewDetails.text = "已完成  部 · 在读  部 | 自动生成六维心智"
                } else {
                    previewCard.visibility = View.GONE
                }
            }
        }.start()
    }

    private fun executeImport() {
        if (parsedRecords.isEmpty()) {
            Toast.makeText(this, "未检测到有效作品记录，请检查数据格式", Toast.LENGTH_SHORT).show()
            return
        }

        btnExecuteMigration.isEnabled = false
        btnExecuteMigration.text = "⏳ 正在批量写入藏库..."

        Thread {
            val affected = databaseHelper.importParsedRecords(parsedRecords)
            runOnUiThread {
                HapticFeedbackEngine.stampImpact(this)
                Toast.makeText(this, "🎉 成功搬家入库  部藏品！", Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
        }.start()
    }

    private fun readTextFromUri(uri: Uri) {
        runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    val text = reader.readText()
                    etRawContent.setText(text)
                    triggerParseAsync(text)
                }
            }
        }.onFailure {
            Toast.makeText(this, "读取文件失败: ", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, DataMigrationActivity::class.java)
    }
}