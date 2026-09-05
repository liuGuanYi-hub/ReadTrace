package com.example.readtrace.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 🔐 AndroidKeyStore AES-256-GCM 加密偏好仓 (SecurePrefs)
 *
 * P38-G13：WebDAV 密码等敏感凭据不再明文落盘。等价于 EncryptedSharedPreferences
 * 的目标效果（密码非明文落盘），但零新增依赖且不依赖已废弃的 androidx.security-crypto：
 * - 密钥生成于系统 Keystore（硬件-backed 时不可导出），别名为固定常量，进程内复用；
 * - 每次加密由 Keystore 随机生成 IV，随密文一并 Base64 存储（IV 非机密，首字节记录其长度）；
 * - 解密失败（换机还原、Keystore 密钥失效等）时清除损坏条目并返回空串，凭据由用户重新填写；
 * - 加密失败时同样不落盘——宁可凭据缺失走未配置分支，也不回退明文存储。
 */
object SecurePrefs {

    private const val PREFS_FILE = "readtrace_secure_prefs"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "readtrace_secure_prefs_key"
    private const val GCM_TAG_BITS = 128

    fun put(context: Context, key: String, plainText: String) {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        if (plainText.isEmpty()) {
            prefs.edit().remove(key).apply()
            return
        }
        val blob = runCatching { encryptToBase64(plainText) }.getOrNull()
        if (blob == null) {
            prefs.edit().remove(key).apply()
            return
        }
        prefs.edit().putString(key, blob).apply()
    }

    fun get(context: Context, key: String): String {
        val stored = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getString(key, null).orEmpty()
        if (stored.isEmpty()) return ""
        return runCatching { decryptFromBase64(stored) }
            .getOrElse {
                prefs(context).edit().remove(key).apply()
                ""
            }
    }

    fun remove(context: Context, key: String) {
        prefs(context).edit().remove(key).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** 密文格式：1 字节 IV 长度 + IV + GCM 密文（含 tag），整体 Base64 */
    private fun encryptToBase64(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val blob = ByteArray(1 + iv.size + encrypted.size)
        blob[0] = iv.size.toByte()
        iv.copyInto(blob, 1)
        encrypted.copyInto(blob, 1 + iv.size)
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    private fun decryptFromBase64(stored: String): String {
        val data = Base64.decode(stored, Base64.NO_WRAP)
        val ivLength = data[0].toInt() and 0xFF
        require(ivLength in 12..16 && data.size > 1 + ivLength) { "malformed secure blob" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            obtainKey(),
            GCMParameterSpec(GCM_TAG_BITS, data, 1, ivLength),
        )
        return String(cipher.doFinal(data, 1 + ivLength, data.size - 1 - ivLength), Charsets.UTF_8)
    }
}
