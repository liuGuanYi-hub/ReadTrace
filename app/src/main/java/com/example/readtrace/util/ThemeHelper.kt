package com.example.readtrace.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.example.readtrace.data.UserPreferencesManager

object ThemeHelper {

    fun applyTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(UserPreferencesManager.getNightMode(context))
    }

    fun isDarkMode(context: Context): Boolean {
        val mode = getNightMode(context)
        return if (mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        } else {
            mode == AppCompatDelegate.MODE_NIGHT_YES
        }
    }

    fun getNightMode(context: Context): Int = UserPreferencesManager.getNightMode(context)

    fun toggleDarkMode(context: Context): Boolean {
        val currentDark = isDarkMode(context)
        val newMode = if (currentDark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        UserPreferencesManager.setNightMode(context, newMode)
        AppCompatDelegate.setDefaultNightMode(newMode)
        return !currentDark
    }
}
