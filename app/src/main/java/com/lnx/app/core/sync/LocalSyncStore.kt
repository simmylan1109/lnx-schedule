package com.lnx.app.core.sync

import com.lnx.app.core.network.SyncRowDto

/**
 * 本地库在同步引擎眼里的样子(v0.3 A4)。
 *
 * 引擎只关心两件事:**现在库里有哪些行**(含墓碑,payload 现做)和**把远端的行合并进来**。
 * 具体怎么查、怎么比 LWW、payload 解不开怎么办,全是 Room 实现([RoomSyncStore])的事,
 * 这样引擎的循环逻辑可以在 JVM 上用假件测个遍。
 */
interface LocalSyncStore {

    /**
     * 当前全量行(含软删除墓碑;例外是硬删除,不在其中)。
     * payload 现做:事件行内嵌当时的 tagIds。
     */
    suspend fun currentRows(): List<SyncRowDto>

    /**
     * 合并远端行(LWW:同 id 比 updatedAt,相等时**服务端赢**;墓碑按删除应用)。
     * 返回**真正落库**的行与被跳过的行 —— 引擎据此更新镜像:
     * 落库的进镜像,跳过的(本地更新/解码失败/种类不认识)不进。
     * 注意:游标在引擎侧照常推进,被跳过的行**不会**因为没进镜像就重拉 ——
     * 解码失败的行是前向兼容问题(老客户端遇到新字段),靠 ignoreUnknownKeys
     * 挡掉绝大多数,剩下的留 skipped 里当证据。
     */
    suspend fun applyPulled(rows: List<SyncRowDto>): AppliedResult
}
