package com.example.readtrace.util

import java.time.LocalDate

/**
 * V1 每日节律：以「日期」为种子做**稳定轮换**。
 *
 * ## 为什么要它
 *
 * 目标是**不依赖任何后端**就制造出"每天打开都不一样"的节律感。
 * 关键是「稳定」二字——不能用随机数：
 *
 * - 同一天内多次调用，结果**完全一致**（否则用户在同一分钟内进出两次页面就看到不同内容，
 *   会觉得这个 App 在乱跳，反而显得不可靠）
 * - 跨天则顺序与"今日主打"自然变化
 *
 * 配合 V0 的远端内容覆盖后，效果即为真正的「每日更新」。
 */
object DailyRotationHelper {

    /** 以本地日期生成的稳定种子，如 2026-09-19 → 20260919 */
    private fun todaySeed(): Int {
        val d = LocalDate.now()
        return d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
    }

    /**
     * 把列表按今日种子做**稳定轮换**（整体左移若干位）。
     *
     * @param offset 同一天内需要多处展示时用它错开（例如「今日主打」与「今日备选」不应该撞同一个）
     */
    fun <T> rotateForToday(items: List<T>, offset: Int = 0): List<T> {
        if (items.size < 2) return items
        val shift = Math.floorMod(todaySeed() + offset, items.size)
        return items.drop(shift) + items.take(shift)
    }

    /**
     * 取今日主打条目（列表为空时返回 null）。
     */
    fun <T> pickToday(items: List<T>, offset: Int = 0): T? {
        if (items.isEmpty()) return null
        return items[Math.floorMod(todaySeed() + offset, items.size)]
    }

    /**
     * 今日日期键（`yyyy-MM-dd`）。用于「今天这一份看过了没」这类红点/去重判断，
     * 与轮换种子同源，保证两者永远对得上。
     */
    fun todayKey(): String {
        val d = LocalDate.now()
        return "%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)
    }
}
