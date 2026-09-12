package com.example.readtrace

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.readtrace.data.BookDatabaseHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 降级安装守卫（T0.7）：数据库文件版本高于代码版本时，
 * 开库不得抛 SQLiteDowngradeFailedException，且需生成 bak_v{old} 快照备份。
 */
@RunWith(AndroidJUnit4::class)
class DowngradeGuardTest {

    @Test
    fun 高版本库降级开库不崩溃且回落版本并生成备份() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = BookDatabaseHelper.getInstance(context)

        // 模拟：库文件曾被更高版本的测试包升级（user_version=99）
        helper.writableDatabase.execSQL("PRAGMA user_version = 99")
        helper.forceCloseForTesting()

        // 回退低版本包后重新开库 → 应触发 onDowngrade(99, 15) 而非抛异常
        val reopened = BookDatabaseHelper.getInstance(context).writableDatabase
        val version = reopened.rawQuery("PRAGMA user_version", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals("降级后版本号应回落到当前代码版本", 15, version)

        val bak = File(
            context.getDatabasePath(BookDatabaseHelper.DATABASE_NAME).parentFile,
            "${BookDatabaseHelper.DATABASE_NAME}.bak_v99",
        )
        assertTrue("onDowngrade 应生成快照备份 bak_v99", bak.exists())
    }
}
