package com.example.readtrace.util

import android.app.ActivityOptions
import android.content.Intent
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.example.readtrace.BookDetailActivity
import com.example.readtrace.model.Book

/**
 * 🎬 作品详情共享元素转场（Hero）发起器
 *
 * 把「某张封面 → 作品详情页封面」做成一次连续的空间移动，而不是页面跳转。
 *
 * ## 背景：为什么锚点早就埋了却一直没生效
 *
 * `activity_book_detail.xml` 与 `fragment_hub.xml` 里长期存在 `transition_book_cover`
 * 锚点，`BookDetailActivity` 里也一直用着 `supportFinishAfterTransition()`，
 * 但转场从未真正跑起来。缺的是三件事（本次一并补齐）：
 *
 * 1. **主题未开启 `android:windowActivityTransitions`** —— 不开这一项，
 *    `makeSceneTransitionAnimation` 会静默退化成普通跳转，不报任何错；
 * 2. **没有任何调用点把源 View 交给 ActivityOptions**；
 * 3. **没有等目标封面就位**，Hero 会拖着一个空占位图飞过去。
 *
 * ## 为什么名字必须动态分配，不能写死在布局里
 *
 * 列表会被复用：若把 `android:transitionName` 写死在 item 布局上，
 * 同屏多张卡片会同时持有**同一个名字**，Android 只认先找到的那个，
 * 于是"点 A 卡片却从 B 卡片飞出去"。所以这里按 `bookId` 生成唯一名字，
 * 只设在被点击的那个 View 上，并经 Intent 把同一个名字交给详情页。
 *
 * ## 为什么不看 QuietMode
 *
 * QuietMode 的语义边界是「不震动、不发声、**背景**不常驻流动」——它拦的是触觉、
 * 拟音与常驻自绘动画（`HapticFeedbackEngine` / `SpatialAudioEngine` /
 * `AuroraFluidBackgroundView` 等），**从不拦点击驱动的动效**；
 * `ViewAnimationHelper` 的 KDoc 也明写点击驱动的动画无需额外判 QuietMode。
 *
 * 共享元素转场是对用户点击的直接反馈，性质等同按钮按下的回弹，不属于"背景流动"。
 * 而且 `isQuietMode` 默认就是 `true`，若在这里判 QuietMode，
 * 全新安装的用户将永远看不到 Hero——功能等于没做。
 *
 * 真正需要尊重的无障碍偏好是**系统「移除动画」**（动画缩放设为 0）：
 * 那种情况下窗口动画由系统自身压制，`makeSceneTransitionAnimation` 不会有可见效果，
 * 代码上也无需特殊处理。
 */
object SharedCoverTransition {

    /** 按 bookId 生成源/目标两端共用的唯一名字 */
    fun nameFor(bookId: Long): String = "bookCover_$bookId"

    /**
     * 带 Hero 转场地打开作品详情。
     *
     * @param sourceCover 被点击卡片上的封面 View（**必须是实际可见的那个**，
     *   传 null 会退化为普通跳转）
     */
    fun start(activity: FragmentActivity, book: Book, sourceCover: View?) {
        val intent: Intent = BookDetailActivity.createIntent(activity, book.id)

        if (sourceCover == null) {
            activity.startActivity(intent)
            return
        }

        val sharedName = nameFor(book.id)
        sourceCover.transitionName = sharedName
        intent.putExtra(BookDetailActivity.EXTRA_SHARED_COVER_NAME, sharedName)

        val options = ActivityOptions.makeSceneTransitionAnimation(
            activity,
            sourceCover,
            sharedName,
        )
        activity.startActivity(intent, options.toBundle())
    }
}
