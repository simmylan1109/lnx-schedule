package com.lnx.app.core.sync

import com.lnx.app.core.network.LnxApiException
import com.lnx.app.core.network.SyncLimits
import com.lnx.app.core.network.SyncApi
import com.lnx.app.core.network.SyncReject
import com.lnx.app.core.network.SyncRowDto
import com.lnx.app.core.network.lnxJson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 一次同步的结局。Failure 的 code 语义同 LnxApiException(unreachable / http_5xx / …) */
sealed interface SyncOutcome {
    data class Ok(
        val pulledRows: Int,
        val pushedRows: Int,
        val rejected: List<String>,
        /** 本地合并不了的行(payload 坏/种类不认识),给诊断用 */
        val skipped: List<String>,
    ) : SyncOutcome
    data class Failure(val code: String, val message: String) : SyncOutcome
}

/**
 * 同步引擎(v0.3 A4)。一次 [syncNow] 的形状是协议定死的"拉 → 推 → 再拉":
 *
 * 1. **拉**:带着上次的服务端游标分页拉,落一页应用一页(标签先、事件次、例外最后);
 * 2. **推**:本地当前内容与镜像的**差集**分批推(每批 ≤ 服务端上限);
 *    被拒且"服务端更新"的行,服务端原样带回,当场合并进本地;
 * 3. **再拉**:协议刻意让推完之后游标不变 —— 并发落在中间的行必须靠再拉补上;
 * 4. 全部成功才落账(游标 + 镜像 + 上次同步时间),中途任何失败半账不留。
 *
 * **脏检测靠镜像不靠时钟**:本地行内容与镜像比差异(内容相等 = 干净)。
 * 两台设备的时钟不齐,比时间戳会静默漏推;比内容永远不会。
 * 硬删除(例外)靠"镜像里有、本地没有"识别,推一条墓碑。
 */
@Singleton
class SyncEngine @Inject constructor(
    private val api: SyncApi,
    private val store: LocalSyncStore,
    private val state: SyncStateStore,
) {

    /** 两次 syncNow 并发会互相覆盖账本(推重复的行是小麻烦,账本写坏是大麻烦),互斥住 */
    private val mutex = Mutex()

    suspend fun syncNow(baseUrl: String, token: String, nowMillis: Long = System.currentTimeMillis()): SyncOutcome =
        mutex.withLock { syncLocked(baseUrl, token, nowMillis) }

    private suspend fun syncLocked(baseUrl: String, token: String, nowMillis: Long): SyncOutcome {
        return try {
            val cursorStart = state.cursorOnce()
            val mirrorStart = state.mirrorOnce()

            // —— 1. 先拉(分页拉完为止)——
            var cursor = cursorStart
            var pulled = 0
            val skipped = mutableListOf<String>()
            // 镜像的活副本:拉下来应用成功的行**立刻**算作"已同步",
            // 否则它们会被当成本地改动再推回去一轮(无害但纯浪费)
            val mirrorNow = mirrorStart.toMutableMap()
            var page = pullAll(baseUrl, token, cursor, skipped, mirrorNow)
            pulled += page.pulledRows
            cursor = page.cursor

            // —— 2. 推本地改动(与镜像的差集)——
            val current = store.currentRows()
            val dirty = dirtyRows(current, mirrorNow, nowMillis)
            var pushed = 0
            val rejected = mutableListOf<String>()
            dirty.chunked(SyncLimits.MAX_ROWS_PER_PUSH).forEach { chunk ->
                val resp = api.push(baseUrl, token, chunk)
                resp.results.forEach { result ->
                    pushed += 1
                    val key = "${result.entity}:${result.id}"
                    if (result.accepted) {
                        // 服务端已存的就是我们刚推的这份 → 镜像跟进
                        val sent = chunk.firstOrNull { it.entity == result.entity && it.id == result.id }
                        if (sent != null) mirrorNow[key] = sent.payload.toString()
                        return@forEach
                    }
                    when (result.reason) {
                        // 服务端那份更新:原样合并进本地,镜像也记服务端那份
                        SyncReject.SERVER_HAS_NEWER -> result.server?.let { server ->
                            store.applyPulled(listOf(server))
                            mirrorNow[key] = server.payload.toString()
                        }

                        // 永久性拒绝(行太大/id 不合法/种类不认识):镜像不动,
                        // 这行下次还会算脏、还会推 —— 对 too_large 是死循环重试,
                        // 但至少**不会静默丢数据**;rejected 会把它亮出来
                        else -> rejected += "${result.entity}/${result.id}: ${result.reason}"
                    }
                }
            }

            // —— 3. 推完照旧再拉(游标没变,补上并发落在中间的行)——
            page = pullAll(baseUrl, token, cursor, skipped, mirrorNow)
            pulled += page.pulledRows
            cursor = page.cursor

            // —— 4. 落账:游标、镜像(增量拼出来的,不是重扫全库)、时间 ——
            // 镜像绝不能用"同步后的 currentRows"重建:同步窗口里用户又改的行会被记成
            // "已同步"却根本没推,下次就不脏了 —— 静默漏推。增量拼(拉到的+推成功的)
            // 让窗口内的改动留在脏区,下轮再推。
            state.finishSync(cursor = cursor, lastSyncAt = nowMillis, mirror = mirrorNow)
            SyncOutcome.Ok(pulled, pushed, rejected, skipped)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // 协程要退出就退出,别把它翻译成"同步失败"
        } catch (e: LnxApiException) {
            state.setLastError("${e.code}: ${e.message}")
            SyncOutcome.Failure(e.code, e.message)
        } catch (e: Exception) {
            state.setLastError("unreachable: ${e.message}")
            SyncOutcome.Failure("unreachable", e.message ?: "sync failed")
        }
    }

    /** 分页拉完一整轮;应用一页记一页的账(镜像增量在 [syncNow] 末尾统一落) */
    private data class PageResult(val pulledRows: Int, val cursor: Long)

    private suspend fun pullAll(
        baseUrl: String,
        token: String,
        since: Long,
        skipped: MutableList<String>,
        mirrorNow: MutableMap<String, String>,
    ): PageResult {
        var cursor = since
        var pulled = 0
        var guard = 0
        while (true) {
            val resp = api.pull(baseUrl, token, cursor)
            val result = store.applyPulled(resp.rows)
            skipped += result.skipped
            // 应用成功的行即时入镜像(见 syncNow 里 mirrorNow 的说明)
            result.applied.forEach { row ->
                mirrorNow["${row.entity}:${row.id}"] = row.payload.toString()
            }
            pulled += resp.rows.size
            cursor = resp.cursor
            if (!resp.hasMore) return PageResult(pulled, cursor)
            // 防御:服务端一页 500 行,200 页 = 十万行还没完,只可能是游标逻辑出问题了
            if (++guard > 200) throw LnxApiException("too_many_pages", "pull did not converge")
        }
    }

    /**
     * 差集三问:本地有镜像无 → 新增;两边都有但 payload 不同 → 改过;
     * 镜像有本地无 → **本地硬删了**(只有例外会这样),用镜像里的旧 payload 发墓碑。
     *
     * **墓碑的 updatedAt 必须往前推**(取旧值与 now 的较大者):服务端 LWW 拒绝
     * `updatedAt <= 已存值`,拿旧时间戳发的墓碑会被当成旧数据拒掉、原行原样带回、
     * 本地又合回去 —— 删除就永远传播不出去了。终审 2026-10-06 抓到的 P0。
     */
    private fun dirtyRows(current: List<SyncRowDto>, mirror: Map<String, String>, nowMillis: Long): List<SyncRowDto> {
        val currentKeys = current.associateBy({ "${it.entity}:${it.id}" }, { it })
        val dirty = mutableListOf<SyncRowDto>()
        current.forEach { row ->
            val key = "${row.entity}:${row.id}"
            if (mirror[key] != row.payload.toString()) dirty += row
        }
        mirror.forEach { (key, payloadJson) ->
            if (key !in currentKeys) {
                val (entity, id) = splitKey(key)
                val oldUpdatedAt = payloadUpdatedAt(payloadJson)
                dirty += SyncRowDto(
                    entity = entity,
                    id = id,
                    payload = runCatching { lnxJson().parseToJsonElement(payloadJson) }
                        .getOrDefault(kotlinx.serialization.json.JsonObject(emptyMap())),
                    updatedAt = maxOf(oldUpdatedAt, nowMillis),
                    deleted = true,
                )
            }
        }
        // 标签先推、事件后推:纯粹为了日志好读,服务端对各行的处理互不依赖
        return dirty.sortedBy { SyncPayloads.order(it.entity) }
    }

    private fun splitKey(key: String): Pair<String, String> {
        val idx = key.indexOf(':')
        return if (idx <= 0) key to "" else key.substring(0, idx) to key.substring(idx + 1)
    }

    private fun payloadUpdatedAt(payloadJson: String): Long = runCatching {
        (lnxJson().parseToJsonElement(payloadJson)
            as? kotlinx.serialization.json.JsonObject)
            ?.get("updatedAt")?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.content?.toLongOrNull()
    }.getOrNull() ?: 0L
}