package com.example.readtrace

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.util.BackupHelper
import com.example.readtrace.util.ElegantConfirmDialog
import com.example.readtrace.util.FloatingBack
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BackupActivity : AppCompatActivity() {

    private lateinit var databaseHelper: BookDatabaseHelper
    private lateinit var backupStatSummary: TextView

    // SAF 文件保存 Launchers
    private val createJsonLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            exportDataToFile(uri, "json")
        }
    }

    private val createMdLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri: Uri? ->
        if (uri != null) {
            exportDataToFile(uri, "md")
        }
    }

    private val createCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            exportDataToFile(uri, "csv")
        }
    }

    // SAF 文件读取 Launcher
    private val openJsonLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            importDataFromFile(uri)
        }
    }

    private val openCsvLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            importCsvFromFile(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_backup)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.backupRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        databaseHelper = BookDatabaseHelper.getInstance(this)
        backupStatSummary = findViewById(R.id.backupStatSummary)

        FloatingBack.install(this)

        findViewById<View>(R.id.exportJsonCard).setOnClickListener {
            val fileName = "readtrace_backup_${getTimestampForFile()}.json"
            createJsonLauncher.launch(fileName)
        }

        findViewById<View>(R.id.exportMarkdownCard).setOnClickListener {
            val fileName = "readtrace_notes_${getTimestampForFile()}.md"
            createMdLauncher.launch(fileName)
        }

        findViewById<View>(R.id.exportCsvCard).setOnClickListener {
            val fileName = "readtrace_works_${getTimestampForFile()}.csv"
            createCsvLauncher.launch(fileName)
        }

        findViewById<View>(R.id.webdavSyncCard).setOnClickListener {
            startActivity(android.content.Intent(this, WebDavConfigActivity::class.java))
        }

        findViewById<View>(R.id.importJsonCard).setOnClickListener {
            openJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        findViewById<View>(R.id.migrationHubCard).setOnClickListener {
            startActivity(DataMigrationActivity.createIntent(this))
        }

        findViewById<View>(R.id.wipeDataCard).setOnClickListener {
            showWipeDataDialog()
        }

        refreshStats()

        findViewById<View>(R.id.backupContent)
            .startAnimation(AnimationUtils.loadAnimation(this, R.anim.home_enter))
    }

    private fun refreshStats() {
        val works = databaseHelper.getBooks()
        val totalNotesCount = databaseHelper.getTotalNotesCount()
        backupStatSummary.text = getString(R.string.backup_stat_summary_format, works.size, totalNotesCount)
    }

    /**
     * 清空账号数据：打字二次验证对话框——必须完整输入「我确定删除账号数据」才能执行。
     * 删除操作在后台线程执行，避免大批量物理删除阻塞主线程。
     */
    private fun showWipeDataDialog() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val confirmPhrase = "我确定删除账号数据"

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(20))
            setBackgroundResource(R.drawable.bg_elegant_dialog)
        }
        container.addView(TextView(this).apply {
            text = "🗑️ 清空账号数据"
            textSize = 16.5f
            setTextColor(0xFFC62828.toInt())
            letterSpacing = 0.02f
        })
        container.addView(TextView(this).apply {
            text = "即将物理删除全部作品、笔记、打卡、角色谱、大纲、心智模型与黑胶曲目，\n操作不可恢复，且不会自动重新播种预设。\n\n如确定继续，请输入下方文字："
            textSize = 12.5f
            setTextColor(getColor(R.color.readtrace_ink))
            setPadding(0, dp(8), 0, dp(10))
        })
        container.addView(TextView(this).apply {
            text = "我确定删除账号数据"
            textSize = 13f
            setTextColor(getColor(R.color.readtrace_accent))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        })

        val input = android.widget.EditText(this).apply {
            hint = "在此输入验证文字"
            setTextIsSelectable(false)
            maxLines = 1
            setSingleLine(true)
            setTextSize(14f)
            setTextColor(getColor(R.color.readtrace_ink))
            setBackgroundResource(R.drawable.bg_form_input)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        container.addView(input)

        val btnConfirm = TextView(this).apply {
            text = "确认清空（不可恢复）"
            gravity = android.view.Gravity.CENTER
            textSize = 14f
            isAllCaps = false
            setTextColor(getColor(R.color.white))
            setBackgroundResource(R.drawable.bg_primary_button)
            isEnabled = false
            alpha = 0.35f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44),
            ).apply { topMargin = dp(14) }
        }
        container.addView(btnConfirm)

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val ok = s?.toString()?.trim() == confirmPhrase
                btnConfirm.isEnabled = ok
                btnConfirm.alpha = if (ok) 1f else 0.35f
            }
        })

        val btnCancel = TextView(this).apply {
            text = "取 消"
            gravity = android.view.Gravity.CENTER
            textSize = 14f
            isAllCaps = false
            setTextColor(getColor(R.color.chip_idle_text))
            setBackgroundResource(R.drawable.bg_chip_picker_idle)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44),
            ).apply { topMargin = dp(10) }
        }
        container.addView(btnCancel)

        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(container)
            window?.apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                setLayout(
                    (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                setGravity(android.view.Gravity.CENTER)
            }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }

        // 确认回调需引用 dialog，故在 dialog 声明之后注册；执行清空前先关掉对话框
        btnConfirm.setOnClickListener {
            if (!btnConfirm.isEnabled) return@setOnClickListener
            dialog.dismiss()
            Thread {
                val (books, notes) = databaseHelper.wipeAllUserData()
                runOnUiThread {
                    Toast.makeText(this, "已清空 $books 部作品、$notes 条笔记，可通过导入合并包重新恢复", Toast.LENGTH_LONG).show()
                    refreshStats()
                }
            }.start()
        }
        dialog.show()
    }

    private fun exportDataToFile(uri: Uri, format: String) {
        val fullWorks = databaseHelper.getAllFullWorkBackups()
        val content = when (format) {
            "json" -> BackupHelper.generateJsonBackup(fullWorks)
            "md" -> BackupHelper.generateMarkdownArchive(fullWorks)
            "csv" -> BackupHelper.generateCsvExport(fullWorks)
            else -> ""
        }

        runCatching {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            Toast.makeText(this, R.string.backup_export_success, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, R.string.backup_export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importDataFromFile(uri: Uri) {
        // P38-G3：读取/解析全量 JSON 是重活，移入后台线程；确认弹窗回主线程
        Thread {
            val parseResult = runCatching {
                val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                }
                jsonString?.let { BackupHelper.parseJsonBackup(it) }
            }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val (items, exportedAt) = parseResult ?: run {
                    Toast.makeText(this, R.string.backup_import_failed, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                if (items.isEmpty()) {
                    Toast.makeText(this, R.string.backup_import_empty, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

            val highOrderCount = items.sumOf {
                it.sessions.size + it.characters.size + it.outlines.size + it.locations.size + it.audioTracks.size +
                    (if (it.mindprint != null) 1 else 0)
            }
            val assetHint = if (highOrderCount > 0) "，以及 $highOrderCount 条打卡/人物/大纲/地标/心智/曲目高阶资产" else ""

            ElegantConfirmDialog.show(
                activity = this,
                title = "📦 确认导入备份？",
                message = "检测到来自【$exportedAt】的备份，包含 ${items.size} 部作品及 ${items.sumOf { it.notes.size }} 条笔记$assetHint。\n\n导入将自动匹配合并现有数据，是否继续？",
                confirmText = "立即合入",
                isDanger = false,
                onConfirm = {
                    Thread {
                        val (importedWorks, importedNotes) = databaseHelper.importFullBackup(items)
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            refreshStats()
                            ElegantConfirmDialog.show(
                                activity = this,
                                title = "🎉 恢复完成",
                                message = getString(R.string.backup_import_success_format, importedWorks, importedNotes),
                                confirmText = "我知道了",
                                showCancel = false,
                                onConfirm = {},
                            )
                        }
                    }.start()
                },
            )
        }
    }.start()
    }

    private fun importCsvFromFile(uri: Uri) {
        runCatching {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val records = com.example.readtrace.util.BookCsvParser.parseRecords(inputStream)
                if (records.isEmpty()) {
                    Toast.makeText(this, "未能从 CSV 解析出有效记录", Toast.LENGTH_SHORT).show()
                    return
                }

                ElegantConfirmDialog.show(
                    activity = this,
                    title = "📑 确认导入 CSV 清单？",
                    message = "检测到 CSV 清单包含 ${records.size} 部作品记录（含评分、金句与心智模型）。\n\n导入将自动匹配合并现有作品，是否继续？",
                    confirmText = "立即导入",
                    isDanger = false,
                    onConfirm = {
                        val count = databaseHelper.importParsedRecords(records)
                        refreshStats()
                        ElegantConfirmDialog.show(
                            activity = this,
                            title = "🎉 导入完成",
                            message = "成功合入/更新 $count 部作品记录！",
                            confirmText = "我知道了",
                            showCancel = false,
                            onConfirm = {},
                        )
                    },
                )
            } ?: run {
                Toast.makeText(this, "无法读取 CSV 文件", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(this, "解析 CSV 失败: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getTimestampForFile(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))

    override fun onDestroy() {
        super.onDestroy()
    }
}
