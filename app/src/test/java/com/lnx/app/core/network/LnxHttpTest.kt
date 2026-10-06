package com.lnx.app.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 健康应答的编解码(v0.3 A2)。**字面量取自真服务器 2026-10-05 的实际返回**,
 * 不是自己编的 —— 服务器加字段、老客户端不崩,是靠 ignoreUnknownKeys 这一层。
 */
class LnxHttpTest {

    private val sample = """
        {"status":"ok","service":"lnx-server","version":"0.1.0",
         "serverTime":1791199812989,"schemaVersion":1,"database":"ok"}
    """.trimIndent().replace("\n", "")

    @Test
    fun `真服务器的健康应答能解出来`() {
        val h = lnxJson().decodeFromString<HealthResponse>(sample)
        assertEquals("ok", h.status)
        assertEquals("lnx-server", h.service)
        assertEquals("0.1.0", h.version)
        assertEquals(1791199812989L, h.serverTime)
        assertEquals(1, h.schemaVersion)
        assertEquals("ok", h.database)
    }

    @Test
    fun `服务器以后新增的字段不会让老客户端崩`() {
        val withExtra = sample.dropLast(1) + ",\"newFieldFromServer\":true}"
        val h = lnxJson().decodeFromString<HealthResponse>(withExtra)
        assertEquals("ok", h.status)
    }

    @Test
    fun `状态串是 ok 才算健康_调用方自己判`() {
        // 解析和"是否健康"分开:坏状态照样是合法应答,不该在解码层炸
        val down = """{"status":"degraded","service":"lnx-server","version":"0.1.0",
            "serverTime":1,"schemaVersion":1,"database":"ok"}""".trimIndent().replace("\n", "")
        assertEquals("degraded", Json { ignoreUnknownKeys = true }.decodeFromString<HealthResponse>(down).status)
    }
}
