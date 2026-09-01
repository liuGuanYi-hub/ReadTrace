package com.example.readtrace

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.readtrace.sync.WebDavClient
import com.example.readtrace.sync.WebDavSyncEngine
import com.example.readtrace.util.ElegantConfirmDialog
import com.example.readtrace.util.FloatingBack
import com.example.readtrace.util.HapticFeedbackEngine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 🛡️ WebDAV 数据主权同步配置页 (WebDavConfigActivity)
 *
 * P12 Local-First 同步：配置坚果云 / Nextcloud / NAS 服务器后，
 * 一键双向增量同步全量藏品（含 6 大高阶资产）。
 */
class WebDavConfigActivity : AppCompatActivity() {

    private lateinit var serverInput: EditText
    private lateinit var userInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_webdav_config)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.webdavRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        FloatingBack.install(this)

        val config = WebDavSyncEngine.loadConfig(this)
        serverInput = findViewById<EditText>(R.id.webdavServerInput).apply { setText(config.serverUrl) }
        userInput = findViewById<EditText>(R.id.webdavUserInput).apply { setText(config.username) }
        passwordInput = findViewById<EditText>(R.id.webdavPasswordInput).apply { setText(config.password) }
        statusText = findViewById(R.id.webdavStatusText)

        renderLastSync()

        findViewById<TextView>(R.id.webdavTestButton).setOnClickListener {
            HapticFeedbackEngine.cartridgeSnap(this)
            saveConfig()
            statusText.text = "🔗 正在测试连接…"
            Thread {
                val result = WebDavClient.testConnection(WebDavSyncEngine.toClientConfig(WebDavSyncEngine.loadConfig(this)))
                runOnUiThread {
                    statusText.text = if (result.success) {
                        "✅ 连接成功（HTTP ${result.httpCode}）"
                    } else {
                        "❌ 连接失败：${result.message ?: "HTTP ${result.httpCode}"}"
                    }
                }
            }.start()
        }

        findViewById<TextView>(R.id.webdavSyncButton).setOnClickListener {
            HapticFeedbackEngine.stampImpact(this)
            saveConfig()
            statusText.text = "⚡ 正在双向增量同步…"
            ElegantConfirmDialog.show(
                activity = this,
                title = "🛡️ 开始同步？",
                message = "将拉取云端备份合入本地，再把本地全量数据推送至云端。两端内容自动去重合并，不会产生重复数据。",
                confirmText = "开始同步",
                isDanger = false,
                onConfirm = { startSync() },
            )
        }
    }

    private fun saveConfig() {
        WebDavSyncEngine.saveConfig(
            this,
            WebDavSyncEngine.WebDavConfig(
                serverUrl = serverInput.text.toString(),
                username = userInput.text.toString(),
                password = passwordInput.text.toString(),
            ),
        )
    }

    private fun startSync() {
        WebDavSyncEngine.performSync(this) { result ->
            statusText.text = if (result.success) "✅ ${result.message}" else "❌ ${result.message}"
            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            if (result.success) renderLastSync()
        }
    }

    private fun renderLastSync() {
        val last = WebDavSyncEngine.loadConfig(this)
        if (!last.isConfigured) {
            statusText.text = "尚未配置服务器。请填写上方信息后测试连接。"
            return
        }
        val lastAt = com.example.readtrace.data.UserPreferencesManager.getWebDavLastSyncAt(this)
        statusText.text = if (lastAt > 0) {
            "上次同步：${
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(lastAt))
            }"
        } else {
            "已配置服务器，尚未执行过同步。"
        }
    }
}
