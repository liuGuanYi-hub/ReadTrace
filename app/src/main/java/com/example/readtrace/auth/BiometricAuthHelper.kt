package com.example.readtrace.auth

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.readtrace.util.HapticFeedbackEngine
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 策展人生物识别与 Android Keystore 硬件安全认证助手
 */
object BiometricAuthHelper {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "ReadTrace_Curator_Auth_Key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /**
     * 检查设备是否支持并配置了生物识别
     */
    fun isBiometricAvailable(context: Context): Boolean {
        return try {
            val biometricManager = context.getSystemService(BiometricManager::class.java) ?: return false
            val canAuth = biometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK)
            canAuth == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * 在 Android Keystore 中生成或获取受硬件保护的私有密钥
     */
    fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val keyEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (keyEntry != null) {
                return keyEntry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // 允许应用层灵活加密，认证由 BiometricPrompt 硬件级管控
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * 拉起系统生物识别弹窗
     */
    fun authenticate(
        activity: Activity,
        title: String = "策展人生物识别",
        subtitle: String = "请验证指纹或面容以解锁您的先锋展厅与云端藏馆",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
        onFailed: () -> Unit = {},
    ): CancellationSignal {
        val cancellationSignal = CancellationSignal()

        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButton("取消", activity.mainExecutor) { _, _ ->
                onError(-1, "用户取消认证")
            }
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK)
            .build()

        prompt.authenticate(
            cancellationSignal,
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    super.onAuthenticationSucceeded(result)
                    // 触发盖印重沉打击触感
                    HapticFeedbackEngine.stampImpact(activity)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString ?: "认证异常")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
        )

        return cancellationSignal
    }
}
