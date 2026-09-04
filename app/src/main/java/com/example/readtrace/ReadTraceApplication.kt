package com.example.readtrace

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.readtrace.util.ThemeHelper
import com.example.readtrace.util.VinylNowPlayingFloat

/**
 * 应用入口：进程启动时恢复用户选择的日夜主题。
 * 若不在此处调用，进程被系统回收重启后夜间模式会回退为「跟随系统」，
 * 而主题图标读取的是持久化偏好，导致图标与实际主题不一致（早间模式仍显示月亮）。
 */
class ReadTraceApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ThemeHelper.applyTheme(this)
        com.example.readtrace.sync.WebDavSyncEngine.performAutoSyncIfDue(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                // 唱机在其他页面持续播放时，悬浮「返回唱机」胶囊随页面自动挂载
                VinylNowPlayingFloat.install(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                VinylNowPlayingFloat.remove(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
