package com.example.readtrace.model

import java.io.Serializable

/**
 * 策展人认证状态枚举
 */
enum class AuthStatus {
    GUEST,           // 自由策展人 (未登录/纯本地离线模式)
    AUTHENTICATED,   // 已认证馆长 (已登入/云端绑定)
    SYNCING,         // 云端数据同步中
}

/**
 * 策展人认证会话
 */
data class AuthSession(
    val status: AuthStatus = AuthStatus.GUEST,
    val token: String? = null,
    val refreshToken: String? = null,
    val account: CuratorAccount? = null,
    val isBiometricUnlocked: Boolean = false,
) : Serializable
