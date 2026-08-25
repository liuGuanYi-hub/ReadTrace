package com.example.readtrace.util

import android.app.Activity
import android.transition.ChangeBounds
import android.transition.ChangeClipBounds
import android.transition.ChangeImageTransform
import android.transition.ChangeTransform
import android.transition.TransitionSet
import android.view.View
import android.view.animation.Interpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import androidx.core.view.animation.PathInterpolatorCompat

object TransitionHelper {

    const val TRANSITION_COVER = "transition_book_cover"
    const val TRANSITION_TITLE = "transition_book_title"

    // 优雅的 Apple/Material 快速展开物理阻尼曲线
    private val fluidCubicBezier: Interpolator = PathInterpolatorCompat.create(0.2f, 0.0f, 0.0f, 1.0f)

    /**
     * 为 Activity 装载高级连续几何形变转场集合
     */
    fun setupActivityTransitions(activity: AppCompatActivity, durationMs: Long = 380L) {
        val sharedTransition = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(ChangeBounds())
            addTransition(ChangeTransform())
            addTransition(ChangeImageTransform())
            addTransition(ChangeClipBounds())
            duration = durationMs
            interpolator = fluidCubicBezier
        }

        activity.window.sharedElementEnterTransition = sharedTransition
        activity.window.sharedElementReturnTransition = sharedTransition
    }

    /**
     * 创建单元素破壁转场启动选项
     */
    fun createTransitionOptions(
        activity: Activity,
        sharedView: View,
        transitionName: String = TRANSITION_COVER,
    ): ActivityOptionsCompat {
        return ActivityOptionsCompat.makeSceneTransitionAnimation(
            activity,
            sharedView,
            transitionName,
        )
    }

    /**
     * 创建双元素（封面 + 标题）复合转场启动选项
     */
    fun createDualTransitionOptions(
        activity: Activity,
        coverView: View,
        titleView: View,
    ): ActivityOptionsCompat {
        val pair1 = Pair.create(coverView, TRANSITION_COVER)
        val pair2 = Pair.create(titleView, TRANSITION_TITLE)
        return ActivityOptionsCompat.makeSceneTransitionAnimation(activity, pair1, pair2)
    }
}
