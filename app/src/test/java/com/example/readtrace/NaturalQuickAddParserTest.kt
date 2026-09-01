package com.example.readtrace

import com.example.readtrace.model.BookStatus
import com.example.readtrace.util.NaturalQuickAddParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalQuickAddParserTest {

    @Test
    fun `完整一句话速记解析`() {
        val parsed = NaturalQuickAddParser.parse("读完 仿生人会梦见电子羊吗 9分 #科幻 #赛博朋克")

        assertEquals("仿生人会梦见电子羊吗", parsed!!.title)
        assertEquals(BookStatus.FINISHED, parsed.status)
        assertEquals(9.0, parsed.rating!!, 0.001)
        assertEquals(listOf("科幻", "赛博朋克"), parsed.tags)
    }

    @Test
    fun `星评自动换算为十分制`() {
        val parsed = NaturalQuickAddParser.parse("在读 三体 4星")

        assertEquals("三体", parsed!!.title)
        assertEquals(BookStatus.READING, parsed.status)
        assertEquals(8.0, parsed.rating!!, 0.001)
    }

    @Test
    fun `仅书名也能解析出标题`() {
        val parsed = NaturalQuickAddParser.parse("斯通纳")

        assertEquals("斯通纳", parsed!!.title)
        assertNull(parsed.status)
        assertNull(parsed.rating)
        assertTrue(parsed.tags.isEmpty())
    }

    @Test
    fun `书名号包裹的标题被剥离`() {
        val parsed = NaturalQuickAddParser.parse("想读《局外人》 #存在主义")

        assertEquals("局外人", parsed!!.title)
        assertEquals(BookStatus.WISHLIST, parsed.status)
        assertEquals(listOf("存在主义"), parsed.tags)
    }

    @Test
    fun `评分钳制到1到10`() {
        assertEquals(10.0, NaturalQuickAddParser.parse("读完 测试 99分")!!.rating!!, 0.001)
    }

    @Test
    fun `信号判定区分普通搜索词与速记句`() {
        assertFalse(NaturalQuickAddParser.looksLikeQuickLog("三体"))
        assertFalse(NaturalQuickAddParser.looksLikeQuickLog("https://book.douban.com/subject/123/"))
        assertTrue(NaturalQuickAddParser.looksLikeQuickLog("读完 三体 9分"))
        assertTrue(NaturalQuickAddParser.looksLikeQuickLog("三体 9分 #科幻"))
    }
}
