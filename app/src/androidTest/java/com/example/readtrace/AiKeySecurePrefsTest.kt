package com.example.readtrace

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.readtrace.data.UserPreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AI Key 安全存储验证：写入即入 AndroidKeyStore 加密仓（明文字段不落盘），
 * 旧版本遗留的明文 Key 首次读取时自动迁入加密仓并抹掉痕迹。
 */
@RunWith(AndroidJUnit4::class)
class AiKeySecurePrefsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun legacyPrefs() = context.getSharedPreferences("readtrace_ai_prefs", android.content.Context.MODE_PRIVATE)

    @Test
    fun 写入后明文字段不存在且可读回() {
        UserPreferencesManager.setAiApiKey(context, "sk-test-123456")
        try {
            assertFalse("明文 ai_api_key 不得残留于普通 SharedPreferences", legacyPrefs().contains("ai_api_key"))
            assertEquals("sk-test-123456", UserPreferencesManager.getAiApiKey(context))
        } finally {
            UserPreferencesManager.setAiApiKey(context, "")
        }
    }

    @Test
    fun 旧明文Key首读时自动迁入加密仓并抹除() {
        legacyPrefs().edit().putString("ai_api_key", "sk-legacy-abcdef").commit()
        try {
            assertEquals("旧明文应可读出", "sk-legacy-abcdef", UserPreferencesManager.getAiApiKey(context))
            assertFalse("迁移后明文字段必须被抹除", legacyPrefs().contains("ai_api_key"))
            assertEquals("迁移后应从加密仓读取", "sk-legacy-abcdef", UserPreferencesManager.getAiApiKey(context))
        } finally {
            UserPreferencesManager.setAiApiKey(context, "")
        }
    }
}
