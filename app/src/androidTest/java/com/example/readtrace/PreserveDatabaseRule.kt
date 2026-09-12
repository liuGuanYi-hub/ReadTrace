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
 * - 测试前：先把 WAL 落盘（checkpoint TRUNCATE），断开连接池，把 readtrace.db 整份备份并清除 -wal/-shm；
 * - 测试后：再次落盘断开，删除测试期间的库与 -wal/-shm，还原备份。
 *
 * 关键点：应用自 T2.4 起启用 WAL——数据分散在主文件与 -wal 之间，
 * 只复制主文件的备份会丢掉 -wal 中未合入的事务（曾导致回归测试后用户数据被清空的假象），
 * 因此备份/还原都必须先做全量检查点并清理 WAL 侧文件。
 */
class PreserveDatabaseRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val dbFile = context.getDatabasePath(BookDatabaseHelper.DATABASE_NAME)
                val dbBackup = File(dbFile.parentFile, "${BookDatabaseHelper.DATABASE_NAME}.preserve_bak")

                checkpointAndClose(context)
                runCatching {
                    if (dbFile.exists()) dbFile.copyTo(dbBackup, overwrite = true)
                    deleteWalSidecars(dbFile)
                }

                try {
                    base.evaluate()
                } finally {
                    checkpointAndClose(context)
                    runCatching {
                        dbFile.delete()
                        deleteWalSidecars(dbFile)
                        if (dbBackup.exists()) dbBackup.copyTo(dbFile, overwrite = true)
                        dbBackup.delete()
                    }
                }
            }
        }
    }

    /** 显式把 WAL 中全部已提交事务合入主文件并截断 -wal，再断开连接池，保证库文件自包含 */
    private fun checkpointAndClose(context: android.content.Context) {
        runCatching {
            val db = BookDatabaseHelper.getInstance(context).writableDatabase
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        }
        runCatching { BookDatabaseHelper.getInstance(context).forceCloseForTesting() }
    }

    private fun deleteWalSidecars(dbFile: File) {
        File(dbFile.parentFile, "${dbFile.name}-wal").delete()
        File(dbFile.parentFile, "${dbFile.name}-shm").delete()
        File(dbFile.parentFile, "${dbFile.name}-journal").delete()
    }
}
