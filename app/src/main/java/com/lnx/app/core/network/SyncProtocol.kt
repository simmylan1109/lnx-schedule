package com.lnx.app.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 同步协议的客户端拷贝(v0.3 A4)。
 *
 * **与服务端 `D:\lnx-server\...\sync\SyncProtocol.kt` 同名同字段,逐字对齐**——
 * 两边靠约定不共享编译产物,改任何一边的字段名都是破坏性变更。
 * 服务端那份文件里有最完整的语义注释,这里只留客户端视角的说明。
 */

/** 行 = (种类, id, 整行 JSON, 客户端改的时间戳, 是否已删除)。服务端只存不解释 payload */
@Serializable
data class SyncRowDto(
    /** event / exception / tag(客户端只用这三种;关联不单独成行,见 SyncPayloads 的 event.tagIds) */
    val entity: String,
    val id: String,
    val payload: JsonElement,
    /** 只用来比新旧,不当作同步位置(手机时钟不可信) */
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class PullResponse(
    val rows: List<SyncRowDto>,
    /** 下次拉取带回来的游标,**服务端权威**;没有变化时等于传进来的值 */
    val cursor: Long,
    /** 还有下一页时,客户端拿 [cursor] 立刻再拉一次 */
    val hasMore: Boolean,
)

@Serializable
data class PushRequest(val rows: List<SyncRowDto>)

/** 单行结果:一行失败不影响同批其他行 */
@Serializable
data class PushResult(
    val entity: String,
    val id: String,
    val accepted: Boolean,
    val reason: String? = null,
    val seq: Long = 0L,
    /** 被拒(服务端更新)时原样带回服务端那份,客户端可直接合并,不必再拉一次 */
    val server: SyncRowDto? = null,
)

@Serializable
data class PushResponse(
    val results: List<PushResult>,
    /** 刻意不返回游标:推完之后同步位置不变,客户端还得照旧拉一次 */
    val cursorUnchanged: Boolean = true,
)

/** 行被拒的稳定原因码 */
object SyncReject {
    const val UNKNOWN_ENTITY = "unknown_entity"
    const val SERVER_HAS_NEWER = "server_has_newer"
    const val TOO_LARGE = "too_large"
    const val BAD_ID = "bad_id"
}

/** 服务端硬上限,客户端照着分批/裁剪(安全边界,不是调优参数) */
object SyncLimits {
    const val MAX_ROWS_PER_PUSH = 200
    const val PULL_PAGE_SIZE = 500
    const val MAX_PAYLOAD_BYTES = 16L * 1024
    const val MAX_PUSH_BODY_BYTES = 4L * 1024 * 1024
}
