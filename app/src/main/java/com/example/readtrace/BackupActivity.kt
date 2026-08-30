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

        databaseHelper = BookDatabaseHelper(this)
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

        findViewById<View>(R.id.importJsonCard).setOnClickListener {
            openJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        findViewById<View>(R.id.importCsvCard).setOnClickListener {
            openCsvLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "application/csv", "*/*"))
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

    private fun exportDataToFile(uri: Uri, format: String) {
        val worksWithNotes = databaseHelper.getAllWorksWithNotes()
        val content = when (format) {
            "json" -> BackupHelper.generateJsonBackup(worksWithNotes)
            "md" -> BackupHelper.generateMarkdownArchive(worksWithNotes)
            "csv" -> BackupHelper.generateCsvExport(worksWithNotes)
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
        runCatching {
            val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).readText()
            } ?: return

            val (items, exportedAt) = BackupHelper.parseJsonBackup(jsonString)
            if (items.isEmpty()) {
                Toast.makeText(this, R.string.backup_import_empty, Toast.LENGTH_SHORT).show()
                return
            }

            ElegantConfirmDialog.show(
                activity = this,
                title = "📦 确认导入备份？",
                message = "检测到来自【$exportedAt】的备份，包含 ${items.size} 部作品及 ${items.sumOf { it.second.size }} 条笔记。\n\n导入将自动匹配合并现有数据，是否继续？",
                confirmText = "立即合入",
                isDanger = false,
                onConfirm = {
                    val (importedWorks, importedNotes) = databaseHelper.importFullBackup(items)
                    refreshStats()
                    ElegantConfirmDialog.show(
                        activity = this,
                        title = "🎉 恢复完成",
                        message = getString(R.string.backup_import_success_format, importedWorks, importedNotes),
                        confirmText = "我知道了",
                        showCancel = false,
                        onConfirm = {},
                    )
                },
            )
        }.onFailure {
            Toast.makeText(this, R.string.backup_import_failed, Toast.LENGTH_LONG).show()
        }
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
        databaseHelper.close()
        super.onDestroy()
    }
}
