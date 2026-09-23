package com.example.readtrace.util

import android.content.Context
import android.os.VibratorManager
import com.example.readtrace.data.UserPreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 「安静模式总闸」的语义测试（Robolectric，跑在 `testDebugUnitTest` 门禁内）。
 *
 * 守的是三条容易被后续改动破坏的契约：
 * 1. **默认安静**——新装用户第一次进来就不该被震动、拟音和常驻动画打扰；
 * 2. **引擎层收口**——147 个触觉调用点不必各自判断，`HapticFeedbackEngine` 一处早退即可全静；
 * 3. **开关可回退且能通知**——用户关掉安静模式后，已创建的自绘 View 要立刻恢复，
 *    而不是等退出页面再进来（这正是 `QuietMode` 需要监听器列表的原因）。
 *
 * 说明：`QuietMode` 是 object 单例，`@Volatile` 字段跨用例残留，
 * 故 `@Before` 里统一用 `init` 从（Robolectric 每个用例全新的）偏好重读，保证起点确定。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuietModeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        QuietMode.init(context)
    }

    @Test
    fun `首次安装默认安静`() {
        assertTrue("气质基线要求默认安静，不该让用户先被震一下再去关", QuietMode.isQuiet())
        assertTrue("偏好层默认值同样应为安静", UserPreferencesManager.isQuietMode(context))
    }

    @Test
    fun `安静模式下触觉引擎完全不触达马达`() {
        val shadow = shadowOfVibrator()

        QuietMode.setQuiet(context, true)
        HapticFeedbackEngine.lightClick(context)
        HapticFeedbackEngine.stampImpact(context)
        assertFalse("安静模式下任何触感入口都不得驱动马达", shadow.isVibrating())
    }

    @Test
    fun `关掉安静模式后触感恢复`() {
        val shadow = shadowOfVibrator()

        QuietMode.setQuiet(context, false)
        assertFalse(QuietMode.isQuiet())
        HapticFeedbackEngine.stampImpact(context)
        assertTrue("用户主动关掉安静模式后，触感必须照常生效", shadow.isVibrating())
    }

    /** Robolectric 默认不认为设备有马达，需显式打开，否则两个用例都会"假通过" */
    private fun shadowOfVibrator(): org.robolectric.shadows.ShadowVibrator {
        val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator
        return shadowOf(vibrator).apply { setHasVibrator(true) }
    }

    @Test
    fun `开关状态跨进程重启保留`() {
        QuietMode.setQuiet(context, false)
        // 模拟进程被回收后重启：只重读偏好，不依赖内存态
        QuietMode.init(context)
        assertFalse("用户关掉安静模式后，重启不应又变回安静", QuietMode.isQuiet())
    }

    @Test
    fun `开关变更通知监听器且同值不重复通知`() {
        var notified = 0
        val listener: () -> Unit = { notified++ }
        QuietMode.addListener(listener)
        try {
            QuietMode.setQuiet(context, false)
            assertEquals("状态真的变了就该通知一次", 1, notified)

            QuietMode.setQuiet(context, false)
            assertEquals("同值重复写入不该触发无谓重绘", 1, notified)

            QuietMode.setQuiet(context, true)
            assertEquals(2, notified)
        } finally {
            QuietMode.removeListener(listener)
        }
    }

    @Test
    fun `移除监听器后不再收到通知`() {
        var notified = 0
        val listener: () -> Unit = { notified++ }
        QuietMode.addListener(listener)
        QuietMode.removeListener(listener)

        QuietMode.setQuiet(context, false)
        QuietMode.setQuiet(context, true)
        assertEquals("View detach 后必须停止接收通知，否则会泄漏", 0, notified)
    }
}
