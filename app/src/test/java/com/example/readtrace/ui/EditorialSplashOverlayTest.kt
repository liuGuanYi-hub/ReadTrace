package com.example.readtrace.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorialSplashOverlayTest {

    @Test
    fun testCuratedQuotesValidity() {
        val quotes = EditorialSplashOverlay.EditorialQuote::class.java
        assertNotNull(quotes)

        // 测试 EditorialQuote 数据模型与代表性名句
        val sample = EditorialSplashOverlay.EditorialQuote(
            text = "“我心里一直在暗暗设想，天堂应该是图书馆的模样。”",
            author = "—— 豪尔赫·路易斯·博尔赫斯"
        )
        assertTrue("金句内容应不为空", sample.text.isNotBlank())
        assertTrue("作者应不为空", sample.author.isNotBlank())
        assertTrue("金句应包含书卷气息双引号", sample.text.startsWith("“") && sample.text.endsWith("”"))
    }

    @Test
    fun testResetProcessState() {
        // 验证重置冷启动状态无异常抛出
        EditorialSplashOverlay.resetProcessStateForTesting()
        // 再次重置保证幂等性
        EditorialSplashOverlay.resetProcessStateForTesting()
    }
}
