package com.example.readtrace.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 封面内存缓存键的语义测试（Robolectric，跑在 `testDebugUnitTest` 门禁内）。
 *
 * ## 为什么需要这个测试
 *
 * `CoverImageHelper` 的内存 LRU 是**单一实例、全 App 共享键空间**，
 * 而同一张封面会在差异极大的尺寸下被请求：
 * 长卷 200×300、列表/详情 300×450、卡带/票根 720×1080、共鸣海报 1080×1620。
 *
 * 修复前缓存键**只有路径**，于是先请求的低分辨率结果会命中后请求的高分辨率调用，
 * `reqWidth/reqHeight` 被静默忽略——用户在藏库列表刷过之后，
 * 共鸣海报会拿到 300×450 并拉伸到 1080×1620（放大 3.6 倍）而发虚。
 *
 * 这组用例锁住的核心契约是：**不同解码尺寸必须落在不同的键上**。
 * 一旦有人把尺寸维度从键里去掉，这里立刻会红。
 *
 * 说明：必须用 Robolectric——`CoverImageHelper` 是 object，
 * 其初始化会构造 `LruCache` 与 `Handler(Looper.getMainLooper())`，
 * 纯 JVM 环境下类初始化即抛 `ExceptionInInitializerError`。
 * 与 [QuietModeTest] 出于同一原因的既有做法一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoverImageCacheKeyTest {

    private val cover = "https://lain.bgm.tv/pic/cover/l/ab/cd/12345.jpg"

    @Test
    fun `同一路径的不同解码尺寸生成不同的缓存键`() {
        val thumb = CoverImageHelper.memoryKeyOf(cover, 300, 450)
        val poster = CoverImageHelper.memoryKeyOf(cover, 1080, 1620)

        assertNotEquals(
            "300×450 与 1080×1620 必须落在不同键上，否则海报会复用缩略图而发虚",
            thumb,
            poster,
        )
    }

    @Test
    fun `真实调用点的全部尺寸档位互不冲突`() {
        // 取自各调用点的实际请求尺寸
        val sizes = listOf(
            200 to 300,    // LibraryScrollView / MediaTimelineScrollView
            260 to 390,    // CulturalPassportView
            300 to 450,    // loadCover 默认（THUMB）
            720 to 1080,   // 卡带 / 票根 / loadCoverBitmap 默认
            1080 to 1620,  // ResonancePosterView
        )

        val keys = sizes.map { (w, h) -> CoverImageHelper.memoryKeyOf(cover, w, h) }

        assertEquals("五个档位应产生五个互不相同的键", sizes.size, keys.toSet().size)
    }

    @Test
    fun `同一路径同一尺寸必须稳定命中同一键`() {
        val first = CoverImageHelper.memoryKeyOf(cover, 300, 450)
        val second = CoverImageHelper.memoryKeyOf(cover, 300, 450)

        assertEquals("键必须可重现，否则缓存永远无法命中", first, second)
    }

    @Test
    fun `不同路径在同一尺寸下互不冲突`() {
        val other = "https://lain.bgm.tv/pic/cover/l/ff/ee/99999.jpg"

        assertNotEquals(
            CoverImageHelper.memoryKeyOf(cover, 300, 450),
            CoverImageHelper.memoryKeyOf(other, 300, 450),
        )
    }

    @Test
    fun `键包含原始路径以便排查`() {
        val key = CoverImageHelper.memoryKeyOf(cover, 300, 450)

        assertEquals(true, key.startsWith(cover))
    }
}
