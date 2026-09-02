package com.example.readtrace

import android.app.Application
import com.example.readtrace.util.ThemeHelper

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
    }
}
