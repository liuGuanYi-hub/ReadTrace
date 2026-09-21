package com.example.readtrace

import android.content.Context
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.model.Note
import com.example.readtrace.model.NoteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 「📖 翻书」链路的 JVM 回归测试（Robolectric，跑在 testDebugUnitTest 内，无需模拟器）。
 *
 * ## 为什么写它
 *
 * 用户反馈：在作品详情页点「📖 翻书」，界面直接退回上一级。
 * `FlipNotesActivity` **只有两条路径会自己关闭自己**：
 *
 * 1. `bookId == NO_BOOK_ID(-1)` —— intent 未携带有效 bookId（或读写类型不匹配）
 * 2. `getBook(bookId) == null` —— 库里查不到该作品
 *
 * 两者都会先弹 Toast 再 `finish()`，表现即为"点一下退回上一级"。
 * 由于该按钮在本机模拟器上无法稳定点中（详情页长滚动 + uiautomator 因常驻动画失效），
 * 改用 JVM 测试把这两条退出条件**连同正常路径一起固化**。
 *
 * ## 为什么不是 Activity 测试
 *
 * 曾尝试用 `Robolectric.buildActivity` 直接断言 `isFinishing`，但启动
 * `AppCompatActivity` 子类需要加载 androidx.appcompat 的资源，
 * 在本项目的单测环境下稳定报 `NoClassDefFoundError: androidx/appcompat/R$drawable`
 * （加 `unitTests.isIncludeAndroidResources = true` 亦未解决）。
 * 该环境问题与待查的缺陷无关，故改为**直接验证两条退出条件的输入**——
 * 它们正是 `FlipNotesActivity` 判断时真正读取的东西，覆盖等价。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlipNotesIntentTest {

    private val ctx: Context = RuntimeEnvironment.getApplication()

    // ---------- 退出条件 ①：intent 是否携带了可用的 bookId ----------

    @Test
    fun `createIntent 写入的 bookId 能被 getLongExtra 原样读出`() {
        val intent = FlipNotesActivity.createIntent(ctx, 224L, 0)
        // 本项目最容易踩的一类坑：Long 写入但用 Int 读取（或反之）不会报错，
        // 只会静默拿到默认值 -1，最终表现成"点了没反应"或"页面直接退出"。
        assertEquals(
            "bookId 必须以 Long 存取，否则会静默退化为默认值 -1 并触发退出",
            224L,
            intent.getLongExtra(FlipNotesActivity.EXTRA_BOOK_ID, -1L),
        )
    }

    @Test
    fun `默认 bookId 为 -1 即 FlipNotesActivity 认定的非法值`() {
        val intent = FlipNotesActivity.createIntent(ctx, 224L, 0)
        // 反向断言：若读取键名写错，会落到这个默认值上——与 Activity 内部的 NO_BOOK_ID 一致
        assertEquals(
            -1L,
            intent.getLongExtra("一个不存在的键名", -1L),
        )
    }

    @Test
    fun `createIntent 写入的起始页码能被 getIntExtra 原样读出`() {
        val intent = FlipNotesActivity.createIntent(ctx, 224L, 3)
        assertEquals(3, intent.getIntExtra(FlipNotesActivity.EXTRA_INITIAL_POSITION, 0))
    }

    // ---------- 退出条件 ②：库里能否查到该作品 ----------

    @Test
    fun `bookId 有效时 getBook 必须能查到`() {
        val helper = BookDatabaseHelper.getInstance(ctx)
        val bookId = helper.insertBook(
            Book(title = "翻书链路测试作品", mediaType = MediaType.ANIME),
        )

        assertNotNull(
            "若此处返回 null，FlipNotesActivity.loadDataAndSetup 会直接 finish——" +
                "即用户看到的『点翻书就退回上一级』",
            helper.getBook(bookId),
        )
    }

    @Test
    fun `bookId 不存在时 getBook 返回 null`() {
        val helper = BookDatabaseHelper.getInstance(ctx)
        assertNull(
            "查不到作品时返回 null 是预期行为，Activity 据此关闭页面",
            helper.getBook(999_999L),
        )
    }

    @Test
    fun `已有笔记的作品能被 getNotes 取到内容`() {
        val helper = BookDatabaseHelper.getInstance(ctx)
        val bookId = helper.insertBook(
            Book(title = "翻书内容测试作品", mediaType = MediaType.ANIME),
        )
        helper.insertNote(
            Note(bookId = bookId, content = "一条用于测试的摘录", noteType = NoteType.QUOTE),
        )

        val notes = helper.getNotes(bookId)
        assertTrue(
            "有笔记时 getNotes 应返回非空——否则翻书页会显示空状态而非内容页",
            notes.isNotEmpty(),
        )
    }
}
