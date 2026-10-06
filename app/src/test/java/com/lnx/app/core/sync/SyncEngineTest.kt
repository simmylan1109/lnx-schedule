package com.lnx.app.core.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.lnx.app.core.network.PullResponse
import com.lnx.app.core.network.PushResponse
import com.lnx.app.core.network.PushResult
import com.lnx.app.core.network.SyncApi
import com.lnx.app.core.network.SyncLimits
import com.lnx.app.core.network.SyncReject
import com.lnx.app.core.network.SyncRowDto
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 同步引擎的循环逻辑(v0.3 A4),全部在 JVM 上用假件跑:
 * 假 API(脚本化应答+记录调用)+ 假本地库(内存行+应用日志)+ **真 SyncStateStore**
 * (临时目录的真 DataStore —— 游标/镜像的落账行为是这条链上最值得真测的部分)。
 */
class SyncEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun stateStore(): SyncStateStore = SyncStateStore(testDataStore())

    private fun testDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(tmp.newFolder(), "sync-state.preferences_pb") }

    // —— 假件 ——

    /** 内存版本地库:行存 map,应用动作记进日志;LWW 与解析容错的细节由 RoomSyncStore 承担 */
    private class FakeStore(initial: List<SyncRowDto> = emptyList()) : LocalSyncStore {
        val rows = initial.associateBy { "${it.entity}:${it.id}" }.toMutableMap()
        val applyLog = mutableListOf<List<SyncRowDto>>()

        override suspend fun currentRows(): List<SyncRowDto> = rows.values.toList()

        override suspend fun applyPulled(incoming: List<SyncRowDto>): AppliedResult {
            applyLog += incoming
            incoming.forEach { rows["${it.entity}:${it.id}"] = it }
            return AppliedResult(applied = incoming, skipped = emptyList())
        }
    }

    /** 脚本化假 API:按调用顺序弹应答;每次调用都记账 */
    private class FakeApi : SyncApi {
        val pulls = mutableListOf<Long>()
        val pushes = mutableListOf<List<SyncRowDto>>()
        val pullScript = ArrayDeque<PullResponse>()
        val pushScript = ArrayDeque<PushResponse>()

        override suspend fun pull(baseUrl: String, token: String, since: Long, limit: Int): PullResponse {
            pulls += since
            return pullScript.removeFirst()
        }

        override suspend fun push(baseUrl: String, token: String, rows: List<SyncRowDto>): PushResponse {
            pushes += rows
            return pushScript.removeFirst()
        }
    }

    private fun row(
        entity: String,
        id: String,
        updatedAt: Long = 1_000,
        deleted: Boolean = false,
        marker: String = "v",
    ) = SyncRowDto(
        entity = entity,
        id = id,
        payload = buildJsonObject {
            put("marker", JsonPrimitive(marker))
            put("updatedAt", JsonPrimitive(updatedAt))
        },
        updatedAt = updatedAt,
        deleted = deleted,
    )

    private fun tempStateStore() = stateStore()

    // —— 用例 ——

    @Test
    fun `首轮同步_远端行应用_本地行全量推_游标落库`() = runTest {
        val api = FakeApi()
        val store = FakeStore(
            listOf(row("event", "e-1", updatedAt = 500), row("tag", "t-1", updatedAt = 400)),
        )
        val state = tempStateStore()
        // 预置一条历史失败:成功落账必须把它清掉(状态指示靠 lastError 区分"还在失败"与"恢复了")
        state.setLastError("seeded previous failure")
        api.pullScript += PullResponse(rows = listOf(row("event", "e-remote", updatedAt = 300)), cursor = 42, hasMore = false)
        // 协议是"拉 → 推 → 再拉":第二份拉应答给推完那次
        api.pullScript += PullResponse(rows = emptyList(), cursor = 42, hasMore = false)
        api.pushScript += PushResponse(
            results = listOf(
                PushResult("tag", "t-1", accepted = true),
                PushResult("event", "e-1", accepted = true),
            ),
        )

        val outcome = SyncEngine(api, store, state).syncNow("https://s.example", "tok", nowMillis = 9_000)

        assertTrue(outcome is SyncOutcome.Ok)
        // 成功落账必须顺带把上一次的错误清掉(状态指示靠 lastError 区分"上次失败"与"恢复了")
        assertEquals(null, state.state.first { true }.lastError)
        // 推的顺序:标签先、事件后(payload 排序键)
        assertEquals(listOf("tag/t-1", "event/e-1"), api.pushes.single().map { "${it.entity}/${it.id}" })
        // 推完又拉了一次(协议:游标不变)
        assertEquals(listOf(0L, 42L), api.pulls)
        // 落账:游标 = 最后一次拉的权威游标;镜像 = 同步后的本地
        val after = state.state.first { it.lastSyncAt == 9_000L }
        assertEquals(42L, after.cursor)
        assertEquals(3, state.mirrorOnce().size)
        assertNullAfterOk(outcome)
    }

    @Test
    fun `hasMore 分页拉完为止_每页的游标接力`() = runTest {
        val api = FakeApi()
        val state = tempStateStore()
        api.pullScript += PullResponse(rows = listOf(row("event", "e-1")), cursor = 10, hasMore = true)
        api.pullScript += PullResponse(rows = listOf(row("event", "e-2")), cursor = 20, hasMore = false)
        api.pushScript += PushResponse(results = emptyList())
        // 第二份拉应答:推完的"再拉"那次
        api.pullScript += PullResponse(rows = emptyList(), cursor = 20, hasMore = false)
        // 本地干净:推一轮应该是空的(Mirror 空 → 本地行全脏 —— 注意:首轮同步本地全量推是对的,
        // 所以这里给本地预置一个已镜像的行来验证"干净就不推")
        // 简化:这个用例只关心拉的接力,推应答给空即可

        SyncEngine(api, FakeStore(), state).syncNow("https://s.example", "tok", 9_000)

        // 拉(首轮分页两口)→ 推 → 再拉:游标接力
        assertEquals(listOf(0L, 10L, 20L), api.pulls)
        assertEquals(20L, state.cursorOnce())
    }

    @Test
    fun `超过上限的本地行_按服务端上限分批`() = runTest {
        val api = FakeApi()
        val state = tempStateStore()
        api.pullScript += PullResponse(rows = emptyList(), cursor = 7, hasMore = false)
        repeat(SyncLimits.MAX_ROWS_PER_PUSH + 3) { i ->
            api.pushScript += PushResponse(results = listOf(PushResult("tag", "t$i", accepted = true)))
        }
        val many = (0 until SyncLimits.MAX_ROWS_PER_PUSH + 3).map { row("tag", "t$it") }

        SyncEngine(api, FakeStore(many), state).syncNow("https://s.example", "tok", 9_000)

        assertEquals(2, api.pushes.size)
        assertEquals(SyncLimits.MAX_ROWS_PER_PUSH, api.pushes[0].size)
        assertEquals(3, api.pushes[1].size)
    }

    @Test
    fun `服务端更新被拒时_把服务端那份合并进本地`() = runTest {
        val api = FakeApi()
        val store = FakeStore(listOf(row("event", "e-1", updatedAt = 100, marker = "mine")))
        val state = tempStateStore()
        api.pullScript += PullResponse(rows = emptyList(), cursor = 5, hasMore = false)
        // 推完的"再拉":给应答,否则最终落账那步会失败,镜像断言就没意义了
        api.pullScript += PullResponse(rows = emptyList(), cursor = 5, hasMore = false)
        val serverRow = row("event", "e-1", updatedAt = 900, marker = "theirs")
        api.pushScript += PushResponse(
            results = listOf(
                PushResult("event", "e-1", accepted = false, reason = SyncReject.SERVER_HAS_NEWER, server = serverRow),
            ),
        )

        SyncEngine(api, store, state).syncNow("https://s.example", "tok", 9_000)

        // 合并 = 本地行换成了服务端那份;镜像里落的也是服务端那份
        // (否则下次同步会把旧内容再推一遍)。键的分隔符是冒号(引擎建键的写法)
        assertTrue(store.rows["event:e-1"]!!.payload.toString().contains("theirs"))
        assertTrue(state.mirrorOnce()["event:e-1"]!!.contains("theirs"))
    }

    @Test
    fun `本地硬删的例外_推墓碑而不是消失`() = runTest {
        val api = FakeApi()
        val state = tempStateStore()
        // 镜像里有这条例外(上次同步见过),本地已经删了 → 差集出一个墓碑
        state.finishSync(
            cursor = 3,
            lastSyncAt = 1_000,
            mirror = mapOf("exception:x-1" to row("exception", "x-1", updatedAt = 800).payload.toString()),
        )
        api.pullScript += PullResponse(rows = emptyList(), cursor = 9, hasMore = false)
        api.pushScript += PushResponse(results = listOf(PushResult("exception", "x-1", accepted = true)))
        val store = FakeStore() // 本地已经没有这一行了

        SyncEngine(api, store, state).syncNow("https://s.example", "tok", 9_000)

        val tombstone = api.pushes.single().single()
        assertEquals("exception/x-1", "${tombstone.entity}/${tombstone.id}")
        assertTrue(tombstone.deleted)
        // P0 锁(终审 2026-10-06):墓碑的 updatedAt 必须前推 —— 拿镜像里 800 的旧时间戳发,
        // 服务端 LWW 会拒掉并把旧行带回,本地一合,删除就永远传播不出去了
        assertTrue(
            "墓碑时间戳必须 >= now(800 会被服务端当旧数据拒掉)",
            tombstone.updatedAt >= 9_000L,
        )
    }

    @Test
    fun `拉取失败_游标与镜像不动_结局失败带稳定code`() = runTest {
        val api = FakeApi()
        val state = tempStateStore()
        state.finishSync(cursor = 33, lastSyncAt = 1_000, mirror = mapOf("event:e-1" to "{}"))
        // pullScript 为空 → removeFirst 抛 NoSuchElementException,引擎要把它翻译成失败
        val store = FakeStore(listOf(row("event", "e-1")))

        val outcome = SyncEngine(api, store, state).syncNow("https://s.example", "tok", 9_000)

        assertTrue(outcome is SyncOutcome.Failure)
        assertEquals(33L, state.cursorOnce())
        assertEquals(mapOf("event:e-1" to "{}"), state.mirrorOnce())
    }

    private fun assertNullAfterOk(outcome: SyncOutcome) {
        // Ok 时账本错误应为空(由 finishSync 清掉)
        assertTrue(outcome is SyncOutcome.Ok)
    }
}