package com.example.readtrace.util

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.example.readtrace.widget.BackOrbView

/**
 * 全站悬浮返回键安装器。
 *
 * 将 [BackOrbView] 以覆盖层形式挂在 Decor 内容层左上角（状态栏之下），并递归
 * 检测页面内的纵向滚动容器：任一滚离页首即让返回钮加深投影进入“附着”态，
 * 便于在长页面上辨识层级。任何页面在 `setContentView` 之后调用一次 [install]
 * 即可获得常驻可用的返回能力，无需再向滚动头部内放置返回按钮。
 *
 * 具备共享元素转场的页面（如书籍详情）可通过 [onBack] 定制退出方式。
 */
object FloatingBack {

    private const val BACK_TAG = "floating_back_orb"
    private const val MARGIN_LEFT_DP = 14f
    private const val MARGIN_TOP_DP = 10f

    /** 滚动超过该距离（dp）即视为已离开页首，进入附着态 */
    private const val AFFIX_THRESHOLD_DP = 28f

    fun install(activity: Activity, onBack: (() -> Unit)? = null) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.findViewWithTag<BackOrbView>(BACK_TAG) != null) return

        val orb = BackOrbView(activity)
        orb.tag = BACK_TAG
        content.addView(
            orb,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            )
        )

        // 先按当前窗口插值定位，再监听后续变化（旋转/刘海/系统栏）
        applyInsets(orb, ViewCompat.getRootWindowInsets(content))
        ViewCompat.setOnApplyWindowInsetsListener(orb) { view, insets ->
            applyInsets(view, insets)
            WindowInsetsCompat.CONSUMED
        }

        orb.onBackActivated = { (onBack ?: { activity.finish() })() }

        watchScrollables(content, orb)
        orb.playEnter()
    }

    private fun applyInsets(view: View, insets: WindowInsetsCompat?) {
        if (insets == null) return
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val density = view.resources.displayMetrics.density
        (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.leftMargin = (bars.left + MARGIN_LEFT_DP * density).toInt()
            params.topMargin = (bars.top + MARGIN_TOP_DP * density).toInt()
            view.layoutParams = params
        }
    }

    /**
     * 收集页面中的纵向滚动容器（ScrollView / NestedScrollView / RecyclerView），
     * 聚合任意容器的滚动位置决定附着态：任一滚离页首 => 附着；全部回到顶部 => 恢复。
     */
    private fun watchScrollables(root: ViewGroup, orb: BackOrbView) {
        val threshold = (AFFIX_THRESHOLD_DP * root.resources.displayMetrics.density).toInt()
        val scrolledAwayIds = HashSet<Int>()

        fun recompute() {
            orb.setAffixed(scrolledAwayIds.isNotEmpty())
        }

        fun stableIdOf(view: View): Int =
            if (view.id != View.NO_ID) view.id else System.identityHashCode(view)

        fun traverse(view: View) {
            when (view) {
                is ScrollView -> {
                    val id = stableIdOf(view)
                    view.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                        if (scrollY > threshold) scrolledAwayIds.add(id)
                        else scrolledAwayIds.remove(id)
                        recompute()
                    }
                    if (view.scrollY > threshold) scrolledAwayIds.add(id)
                }
                is NestedScrollView -> {
                    val id = stableIdOf(view)
                    val listener = NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                        if (scrollY > threshold) scrolledAwayIds.add(id)
                        else scrolledAwayIds.remove(id)
                        recompute()
                    }
                    view.setOnScrollChangeListener(listener)
                    if (view.scrollY > threshold) scrolledAwayIds.add(id)
                }
                is RecyclerView -> {
                    val id = stableIdOf(view)
                    view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            val away = recyclerView.computeVerticalScrollOffset() > threshold
                            if (away) scrolledAwayIds.add(id)
                            else scrolledAwayIds.remove(id)
                            recompute()
                        }
                    })
                    if (view.computeVerticalScrollOffset() > threshold) scrolledAwayIds.add(id)
                }
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    val child = view.getChildAt(index)
                    if (child.tag == BACK_TAG) continue
                    traverse(child)
                }
            }
        }

        traverse(root)
        recompute()
    }
}
