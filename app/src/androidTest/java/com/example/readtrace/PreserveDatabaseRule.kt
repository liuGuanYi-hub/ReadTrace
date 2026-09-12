package com.example.readtrace

import com.example.readtrace.data.BookDatabaseHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

/**
 * 🛡️ 破坏性数据库测试的备份/恢复规则 (Preserve Database Rule)
 *
 * 应用于会物理清空/改写应用数据库的测试（如 WipeUserDataTest）：
 * - 测试前：强制断开数据库连接池，把 readtrace.db 及其 journal 整份备份；
 * - 测试后：再次断开连接池，删除测试期间的库文件并还原备份。
 *
 * 由此 wipe 类测试只作用于临时时效窗口，设备上的真实用户数据不再被
 * `connectedDebugAndroidTest` 日常回归清空。
 */
class PreserveDatabaseRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val dbFile = context.getDatabasePath(BookDatabaseHelper.DATABASE_NAME)
                val dbBackup = File(dbFile.parentFile, "${BookDatabaseHelper.DATABASE_NAME}.preserve_bak")
                val journal = File(dbFile.parentFile, "${BookDatabaseHelper.DATABASE_NAME}-journal")
                val journalBackup = File(dbFile.parentFile, "${BookDatabaseHelper.DATABASE_NAME}-journal.preserve_bak")

                // 备份前断开连接池，确保库文件处于静止一致状态
                runCatching { BookDatabaseHelper.getInstance(context).forceCloseForTesting() }
                runCatching {
                    if (dbFile.exists()) dbFile.copyTo(dbBackup, overwrite = true)
                    if (journal.exists()) journal.copyTo(journalBackup, overwrite = true) else journalBackup.delete()
                }

                try {
                    base.evaluate()
                } finally {
                    // 还原前再次断开连接池（测试期间重建的连接），避免旧句柄把测试数据写回
                    runCatching { BookDatabaseHelper.getInstance(context).forceCloseForTesting() }
                    runCatching {
                        if (dbBackup.exists()) {
                            dbFile.delete()
                            journal.delete()
                            dbBackup.copyTo(dbFile, overwrite = true)
                            if (journalBackup.exists()) journalBackup.copyTo(journal, overwrite = true)
                        }
                        dbBackup.delete()
                        journalBackup.delete()
                    }
                }
            }
        }
    }
}
