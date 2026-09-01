package com.example.readtrace.util

/**
 * 🌌 双向概念脉络网 (BidirectionalConceptWeb)
 *
 * 参考 Obsidian / Roam Research 的思想链接：在长评、短评、笔记中书写
 * `[[存在主义]]`、`[[赛博朋克]]` 双链语法，本地建立「概念 → 藏品」倒排索引，
 * 点击任意概念即可展开所有引用该概念的跨媒介作品。
 */
object BidirectionalConceptHelper {

    private val CONCEPT_LINK = Regex("""\[\[([^\[\]【]{1,24})]]""")

    /** 从一段文本中提取全部 [[概念]]（保持出现顺序、已去重） */
    fun extractConcepts(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return CONCEPT_LINK.findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    /**
     * 构建「概念 → 藏品」倒排索引
     * @param works 每部作品的 (workId, 文本列表)，文本可为读后感/短评/多条笔记
     */
    fun buildConceptIndex(works: List<Pair<String, List<String>>>): Map<String, Set<String>> {
        val index = LinkedHashMap<String, MutableSet<String>>()
        works.forEach { (workId, texts) ->
            texts.forEach { text ->
                extractConcepts(text).forEach { concept ->
                    index.getOrPut(concept) { LinkedHashSet() }.add(workId)
                }
            }
        }
        return index
    }

    /** 查询某概念关联的藏品 id（不含指定作品自身时用于「其它共读此概念的作品」展示） */
    fun relatedWorkIds(index: Map<String, Set<String>>, concept: String, excludeWorkId: String? = null): List<String> =
        index[concept].orEmpty()
            .filter { it != excludeWorkId }
            .toList()
}
