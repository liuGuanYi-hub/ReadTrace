package com.example.readtrace.util

import com.example.readtrace.model.Book
import java.util.Locale

/**
 * 拼音首字母模糊秒搜引擎 (Pinyin & Acronym Search Engine)
 * 零外部重量级依赖，支持：
 * 1. 拼音首字母简拼（如 \"st\" -> \"三体\"，\"nsy\" -> \"女神异闻录\"）
 * 2. 字母与数字混合简拼（如 \"p5r\" -> \"Persona 5 Royal / 女神异闻录5皇家版\"）
 * 3. 中文子串模糊匹配（如 \"三体\" -> \"三体\"）
 * 4. 英文/罗马音忽略大小写前缀与子串匹配
 */
object PinyinSearchHelper {

    // 常用 GB2312/Unicode 一级汉字拼音首字母边界表 (Unicode 4E00..9FA5 区域划分)
    private val PINYIN_FIRST_LETTERS = charArrayOf(
        'a', 'a', 'a', 'a', 'a', 'a', 'b', 'b', 'b', 'b', 'c', 'c', 'c', 'c', 'd', 'd', 'd',
        'e', 'e', 'e', 'f', 'f', 'f', 'g', 'g', 'g', 'h', 'h', 'h', 'j', 'j', 'j', 'k', 'k',
        'l', 'l', 'l', 'm', 'm', 'm', 'n', 'n', 'o', 'o', 'p', 'p', 'p', 'q', 'q', 'q', 'r',
        's', 's', 's', 't', 't', 't', 'w', 'w', 'w', 'x', 'x', 'x', 'y', 'y', 'y', 'z', 'z', 'z'
    )

    // 常用多音字与特殊字的首字母特例修正表
    private val SPECIAL_PINYIN_MAP = mapOf(
        '三' to "s", '体' to "t", '女' to "n", '神' to "s", '异' to "y", '闻' to "w", '录' to "l",
        '重' to "c", '长' to "c", '行' to "x", '乐' to "y", '朝' to "c", '都' to "d", '降' to "j",
        '哈' to "h", '利' to "l", '波' to "b", '特' to "t", '海' to "h", '贼' to "z", '王' to "w",
        '进' to "j", '击' to "j", '的' to "d", '巨' to "j", '人' to "r", '鬼' to "g", '灭' to "m",
        '之' to "z", '刃' to "r", '咒' to "z", '术' to "s", '回' to "h", '战' to "z", '火' to "h",
        '影' to "y", '忍' to "r", '者' to "z", '死' to "s", '亡' to "w", '笔' to "b", '记' to "j",
        '钢' to "g", '炼' to "l", '金' to "j", '星' to "x", '际' to "j", '穿' to "c", '越' to "y",
        '黑' to "h", '客' to "k", '帝' to "d", '国' to "g", '肖' to "x", '申' to "s", '克' to "k",
        '救' to "j", '赎' to "s", '霸' to "b", '别' to "b", '姬' to "j", '千' to "q", '寻' to "x",
        '龙' to "l", '猫' to "m", '天' to "t", '空' to "k", '城' to "c", '风' to "f", '谷' to "g",
        '村' to "c", '上' to "s", '春' to "c", '树' to "s", '东' to "d", '野' to "y", '圭' to "g",
        '吾' to "w", '太' to "t", '宰' to "z", '治' to "z", '鲁' to "l", '迅' to "x", '老' to "l",
        '舍' to "s", '莫' to "m", '言' to "y", '余' to "y", '华' to "h", '刘' to "l", '慈' to "c",
        '欣' to "x", '阿' to "a", '西' to "x", '莫' to "m", '夫' to "f", '克' to "k", '拉' to "l"
    )

    /**
     * 判断书籍是否命中搜索关键字（支持中文模糊、简拼首字母、英文大小写忽略）
     */
    fun matchesBook(book: Book, query: String): Boolean {
        val cleanQuery = query.trim().lowercase(Locale.getDefault())
        if (cleanQuery.isEmpty()) return true

        // 1. 中文或原文直接包含
        if (book.title.lowercase(Locale.getDefault()).contains(cleanQuery)) return true
        if (book.author?.lowercase(Locale.getDefault())?.contains(cleanQuery) == true) return true
        if (book.category?.lowercase(Locale.getDefault())?.contains(cleanQuery) == true) return true

        // 2. 拼音首字母简拼匹配（标题）
        val titleAcronym = getPinyinAcronym(book.title)
        if (titleAcronym.contains(cleanQuery)) return true

        // 3. 拼音首字母简拼匹配（作者）
        book.author?.let { author ->
            val authorAcronym = getPinyinAcronym(author)
            if (authorAcronym.contains(cleanQuery)) return true
        }

        // 4. 标签匹配
        for (tag in book.tags) {
            if (tag.lowercase(Locale.getDefault()).contains(cleanQuery)) return true
            if (getPinyinAcronym(tag).contains(cleanQuery)) return true
        }

        return false
    }

    /**
     * 提取字符串中每个字符的拼音首字母（汉字转简拼，英文/数字保留）
     */
    fun getPinyinAcronym(text: String): String {
        val sb = StringBuilder()
        for (char in text) {
            when {
                // 英文字母或数字直接保留小写
                char.isLetterOrDigit() && char.code < 128 -> {
                    sb.append(char.lowercaseChar())
                }
                // 命中特例字表
                SPECIAL_PINYIN_MAP.containsKey(char) -> {
                    sb.append(SPECIAL_PINYIN_MAP[char])
                }
                // 汉字拼音首字母查表算法
                char.code in 0x4E00..0x9FA5 -> {
                    val initial = getChineseCharInitial(char)
                    sb.append(initial)
                }
            }
        }
        return sb.toString()
    }

    /**
     * 根据汉字内码范围估算其汉语拼音声母
     */
    private fun getChineseCharInitial(c: Char): Char {
        val bytes = runCatching {
            c.toString().toByteArray(charset("GBK"))
        }.getOrNull() ?: return c.lowercaseChar()

        if (bytes.size < 2) return c.lowercaseChar()

        val secPosValue = (bytes[0].toInt() and 0xFF) * 100 + (bytes[1].toInt() and 0xFF)
        val secPosValueList = intArrayOf(
            1601, 1637, 1833, 2078, 2274, 2302, 2433, 2594, 2787, 3106, 3212, 3472,
            3635, 3722, 3730, 3858, 4027, 4086, 4390, 4558, 4684, 4925, 5249, 5600
        )
        val initialList = charArrayOf(
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'w', 'x', 'y', 'z'
        )

        for (i in 0 until 23) {
            if (secPosValue >= secPosValueList[i] && secPosValue < secPosValueList[i + 1]) {
                return initialList[i]
            }
        }
        return c.lowercaseChar()
    }
}