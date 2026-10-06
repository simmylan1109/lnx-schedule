package com.lnx.app.core.network

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 令牌密钥库存储(v0.3 A2)。**只能在设备/模拟器上跑** —— AndroidKeyStore 在 JVM 上不存在,
 * 所以这三条留在 androidTest,不进单测。
 *
 * 每条用例前都清干净(密文 + 密钥),保证用例之间不互相带状态。
 */
@RunWith(AndroidJUnit4::class)
class TokenStoreTest {

    private lateinit var store: TokenStore

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("lnx_secure", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias("lnx_session_token")) keyStore.deleteEntry("lnx_session_token")
        store = TokenStore(context)
    }

    @Test
    fun 存进去的令牌能原样读回来() {
        store.save("tok-abc-123")
        assertEquals("tok-abc-123", store.read())
    }

    @Test
    fun 落盘的是密文不是明文() {
        store.save("tok-super-secret")
        val onDisk = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("lnx_secure", Context.MODE_PRIVATE)
            .getString("token", null)
        assertNotEquals("磁盘上不能出现令牌明文", "tok-super-secret", onDisk)
        // 密文也不该包含明文的任何片段(哪怕 base64 碰巧)
        val leak = onDisk?.contains("tok-super")
        assertEquals("密文里不应有明文片段", false, leak)
    }

    @Test
    fun 清除后读到空_垃圾密文也自愈成空() {
        store.save("tok-xyz")
        store.clear()
        assertNull(store.read())

        // 模拟"备份还原到别的机器":密文还在但密钥解不开 → 必须返回 null 并清掉,不许抛
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("lnx_secure", Context.MODE_PRIVATE)
            .edit().putString("token", "bm90LXRoZS1yaWdodC1jaXBoZXJ0ZXh0").commit()
        assertNull(store.read())
        assertNull(
            "自愈后应把残留密文清掉",
            InstrumentationRegistry.getInstrumentation().targetContext
                .getSharedPreferences("lnx_secure", Context.MODE_PRIVATE)
                .getString("token", null),
        )
    }
}
