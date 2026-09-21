package com.example.readtrace.data

import android.database.sqlite.SQLiteDatabase
import com.example.readtrace.model.BookStatus

/**
 * P40 Phase 4：从 `BookDatabaseHelper` 巨石类抽出的**统计查询**。
 *
 * ## 为什么能抽
 *
 * 这组方法原本就满足"可机械搬移"的全部条件：
 * - 签名只依赖数据库句柄（不碰实例字段、不碰 Context）
 * - 用的 `TABLE_*` / `COLUMN_*` 常量都是 [DatabaseSchema] 文件里的**顶层常量**，同包直接可见
 *
 * 因此搬移过程**零语义改动**：原来怎么写 SQL，现在还是怎么写。
 *
 * ## 设计约定
 *
 * - **入参显式传 `SQLiteDatabase`**，而不是像原来那样读 `readableDatabase` 属性——
 *   这样本对象彻底无状态，也便于将来单独做单元测试（可以直接传入内存库）。
 * - 调用方 `BookDatabaseHelper` 保留原方法名与签名不变，内部转发到这里。
 *   对外 API **100% 零破坏**，这是本次解耦的硬约束。
 */
internal object StatsQueries {

    /** 已读完（status = finished）的作品总数 */
    fun totalFinishedBooks(db: SQLiteDatabase): Int {
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_BOOKS WHERE $COLUMN_STATUS = ? AND $COLUMN_IS_DELETED = 0",
            arrayOf(BookStatus.FINISHED.databaseValue),
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /** 有效（未删除）作品总数 */
    fun totalBooks(db: SQLiteDatabase): Int {
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_BOOKS WHERE $COLUMN_IS_DELETED = 0",
            null,
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /** 有效（未删除）笔记总数 */
    fun totalNotes(db: SQLiteDatabase): Int {
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_NOTES WHERE $COLUMN_IS_DELETED = 0",
            null,
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /** 不同作品分类总数（忽略空串与纯空白） */
    fun uniqueCategories(db: SQLiteDatabase): Int {
        val cursor = db.rawQuery(
            "SELECT COUNT(DISTINCT $COLUMN_CATEGORY) FROM $TABLE_BOOKS " +
                "WHERE $COLUMN_CATEGORY IS NOT NULL AND TRIM($COLUMN_CATEGORY) != '' " +
                "AND $COLUMN_IS_DELETED = 0",
            null,
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /** 评分 ≥ 9.0 的高分作品数量 */
    fun highRatingBooks(db: SQLiteDatabase): Int {
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_BOOKS WHERE $COLUMN_RATING >= 9.0 AND $COLUMN_IS_DELETED = 0",
            null,
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }
}
