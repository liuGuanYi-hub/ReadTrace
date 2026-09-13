package com.example.readtrace.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * T3.4 最小崩溃采集：全局未捕获异常写入本地文件，下次启动自动清理历史（仅保留最新 KEEP_COUNT 份）。
 * 导出路径：「关于阅痕」面板长按版本徽标，以纯文本系统分享带出。
 *
 * 设计约束：
 * - 崩溃现场只做一次同步小文件写入，随后必须交还系统默认处理器，保持原有崩溃行为（进程结束）；
 * - 采集全链路 runCatching，采集本身绝不能成为二次崩溃源；
 * - 无任何后端上报通道，文件仅留存于应用私有目录，随系统分享手动带出。
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val DIR_NAME = "crash_reports"
    private const val FILE_PREFIX = "crash_"
    private const val KEEP_COUNT = 5

    /** 必须在 Application.onCreate 最早时机调用，保证后续任何阶段崩溃都能被记录。 */
    fun install(appContext: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeReport(appContext, thread, throwable) }
            // 无论写入成败都交还系统默认处理，绝不吞异常
            previous?.uncaughtException(thread, throwable)
        }
        // 下次启动：清理超出保留额度的历史报告，并留下可观测日志
        runCatching {
            val kept = prune(appContext)
            if (kept > 0) {
                Log.w(TAG, "检测到 $kept 份历史崩溃报告，可在「关于阅痕」长按版本徽标导出")
            }
        }
    }

    /** 现存崩溃报告，按文件名（即时间戳）倒序，最新在前。 */
    fun reports(context: Context): List<File> =
        File(context.filesDir, DIR_NAME)
            .listFiles { file -> file.isFile && file.name.startsWith(FILE_PREFIX) }
            ?.sortedByDescending { it.name }
            .orEmpty()

    /** 合并全部报告为一份纯文本；无报告时返回 null。 */
    fun exportText(context: Context): String? {
        val files = reports(context)
        if (files.isEmpty()) return null
        return files.joinToString(separator = "\n\n${"-".repeat(48)}\n\n") { file ->
            runCatching { file.readText() }.getOrDefault("<报告 ${file.name} 读取失败>")
        }
    }

    /** 导出用系统分享 Intent；无报告时返回 null。 */
    fun buildShareIntent(context: Context): Intent? {
        val text = exportText(context) ?: return null
        return Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "阅痕 ReadTrace 崩溃日志")
            .putExtra(Intent.EXTRA_TEXT, text)
    }

    private fun writeReport(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "$FILE_PREFIX$stamp.txt")

        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
        val versionDesc = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "v${info.versionName} (code ${info.longVersionCode})"
        }.getOrDefault("版本未知")

        // 只写小文本，一次性落盘；崩溃现场没有余裕做更重的 I/O
        file.writeText(
            buildString {
                appendLine("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                appendLine("应用：阅痕 ReadTrace $versionDesc")
                appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("线程：${thread.name}")
                appendLine()
                append(stackTrace.toString())
            },
        )
    }

    /** 仅保留最新 KEEP_COUNT 份，返回保留数量。 */
    private fun prune(context: Context): Int {
        val all = reports(context)
        all.drop(KEEP_COUNT).forEach { file -> runCatching { file.delete() } }
        return all.size.coerceAtMost(KEEP_COUNT)
    }
}
