package com.lnx.app.core.network

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 令牌存储的抽象(v0.3 A3):AuthViewModel 依赖这个接口,JVM 测试用假件替身 ——
 * AndroidKeyStore 在 JVM 上不存在,不抽象的话整个认证流程的测试都得上模拟器。
 */
interface TokenStorage {
    fun save(token: String)
    fun read(): String?
    fun clear()
}

/**
 * 登录令牌的加密存储(v0.3 A2)。
 *
 * **为什么不用 EncryptedSharedPreferences**:androidx.security-crypto 已停止活跃维护,
 * 依赖更重,而它底下也就是 AndroidKeyStore + AES/GCM —— 这里直接做,少一层黑盒。
 * 密钥本体在 **AndroidKeyStore**(系统保护区)里,应用数据目录里只存密文:
 * 拿走备份文件、或越过 App 沙箱的进程,拿到的都是读不出的乱码。
 *
 * **换机/还原备份后的自愈**:密钥不可导出,旧设备上存的密文在新设备上解不开 ——
 * 这时 [read] 返回 null 并清掉残留(令牌作废,重新登录一次即可),
 * 而不是抛异常把登录态卡死。这是"密钥不可导出"的必然代价,写在这里免得将来当 bug 修。
 *
 * blob 布局:`IV(12B) || 密文`。GCM 的 IV 每次随机,必须跟着密文一起存。
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : TokenStorage {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    override fun save(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val blob = byteArrayOf(*cipher.iv, *cipher.doFinal(token.toByteArray(Charsets.UTF_8)))
        prefs.edit { putString(KEY_TOKEN, Base64.encodeToString(blob, Base64.NO_WRAP)) }
    }

    override fun read(): String? {
        val raw = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            val blob = Base64.decode(raw, Base64.NO_WRAP)
            val iv = blob.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = blob.copyOfRange(GCM_IV_BYTES, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            // 解不开 = 密钥换过 / 备份还原到了别的机器。清掉,让用户重新登录一次
            clear()
            null
        }
    }

    override fun clear() {
        prefs.edit { remove(KEY_TOKEN) }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
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

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "lnx_session_token"
        const val PREFS_FILE = "lnx_secure"
        const val KEY_TOKEN = "token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}