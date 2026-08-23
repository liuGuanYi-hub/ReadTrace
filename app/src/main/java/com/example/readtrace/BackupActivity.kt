package com.example.readtrace

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.util.BackupHelper
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

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

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

            AlertDialog.Builder(this)
                .setTitle("确认导入备份？")
                .setMessage("检测到来自【$exportedAt】的备份，包含 ${items.size} 部作品及 ${items.sumOf { it.second.size }} 条笔记。\n\n导入将自动匹配合并现有数据，是否继续？")
                .setPositiveButton("立即合入") { _, _ ->
                    val (importedWorks, importedNotes) = databaseHelper.importFullBackup(items)
                    refreshStats()
                    AlertDialog.Builder(this)
                        .setTitle("🎉 恢复完成")
                        .setMessage(getString(R.string.backup_import_success_format, importedWorks, importedNotes))
                        .setPositiveButton("确定", null)
                        .show()
                }
                .setNegativeButton("取消", null)
                .show()
        }.onFailure {
            Toast.makeText(this, R.string.backup_import_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun getTimestampForFile(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }
}
