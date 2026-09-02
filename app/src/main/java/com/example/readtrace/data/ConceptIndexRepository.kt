package com.example.readtrace.data

import android.content.Context
import com.example.readtrace.util.BidirectionalConceptHelper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 🌌 全局概念与心智索引单例仓储 (ConceptIndexRepository)
 *
 * 维护全库 `[[概念]]` 倒排索引与关联作品图谱缓存，
 * 支持 O(1) 瞬时检索，数据库变更时增量失效，彻底避免多处遍历数据库重建索引。
 */
object ConceptIndexRepository {

    private val isIndexed = AtomicBoolean(false)
    private val isIndexing = AtomicBoolean(false)

    // 概念 -> 关联的作品 ID 集合
    private val conceptToWorkIds = ConcurrentHashMap<String, MutableSet<String>>()
    // 作品 ID -> 拥有的概念集合
    private val workIdToConcepts = ConcurrentHashMap<String, MutableSet<String>>()

    /** 确保全局概念索引已就绪（后台异步构建或直接复用缓存） */
    fun ensureIndexedAsync(context: Context, onComplete: ((Map<String, Set<String>>) -> Unit)? = null) {
        if (isIndexed.get()) {
            onComplete?.invoke(conceptToWorkIds)
            return
        }
        if (isIndexing.compareAndSet(false, true)) {
            Thread {
                try {
                    val db = BookDatabaseHelper.getInstance(context)
                    val books = db.getBooks().filter { !it.isDeleted }
                    val worksData: List<Pair<String, List<String>>> = books.map { b ->
                        val notes = db.getNotes(b.id).map { it.content }
                        b.id.toString() to (listOfNotNull(b.shortComment, b.review) + notes)
                    }
                    val index = BidirectionalConceptHelper.buildConceptIndex(worksData)

                    conceptToWorkIds.clear()
                    workIdToConcepts.clear()
                    index.forEach { (concept, ids) ->
                        conceptToWorkIds[concept] = ids.toMutableSet()
                        ids.forEach { id ->
                            workIdToConcepts.getOrPut(id) { ConcurrentHashMap.newKeySet() }.add(concept)
                        }
                    }
                    isIndexed.set(true)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isIndexing.set(false)
                    onComplete?.invoke(conceptToWorkIds)
                }
            }.start()
        } else {
            // 如果正在索引中，等待一下或直接返回当前可用
            onComplete?.invoke(conceptToWorkIds)
        }
    }

    /** 同步获取当前内存中的概念索引（如未建立返回空 Map） */
    fun getSnapshot(): Map<String, Set<String>> = conceptToWorkIds

    /** 获取某部作品包含的所有概念 */
    fun getConceptsForWork(workId: String): Set<String> =
        workIdToConcepts[workId].orEmpty()

    /** 获取引用了某概念的全部作品 ID 列表（支持排除指定作品自身） */
    fun getRelatedWorkIds(concept: String, excludeWorkId: String? = null): List<String> =
        conceptToWorkIds[concept].orEmpty().filter { it != excludeWorkId }

    /** 获取当前全库所有已识别的概念清单 */
    fun getAllConcepts(): Set<String> =
        conceptToWorkIds.keys

    /** 数据库产生写入/修改/删除时调用，失效当前缓存 */
    fun invalidate() {
        isIndexed.set(false)
        conceptToWorkIds.clear()
        workIdToConcepts.clear()
    }
}
