package com.lnx.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lnx.app.core.database.entity.EventExceptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventExceptionDao {
    /** 活着的例外流(展开器/搜索按 masterId 分组消费;**墓碑不参与显示**) */
    @Query("SELECT * FROM event_exceptions WHERE isDeleted = 0")
    fun observeAll(): Flow<List<EventExceptionEntity>>

    @Query("SELECT * FROM event_exceptions WHERE masterEventId = :masterId AND originalDate = :epochDay")
    suspend fun getFor(masterId: String, epochDay: Long): EventExceptionEntity?

    /** 例外 id 是确定性的(母事件 + 原本发生日),重复写同一例外 = 覆盖 */
    @Upsert
    suspend fun upsert(entity: EventExceptionEntity)

    /**
     * 删除母事件的全部例外 —— **打墓碑而不是硬删**(v0.3 例外墓碑补做):
     * 删除要靠同步流传到别的设备,硬删的行永远传不出去。
     */
    @Query("UPDATE event_exceptions SET isDeleted = 1, updatedAt = :now WHERE masterEventId = :masterId")
    suspend fun tombstoneForMaster(masterId: String, now: Long)

    /** "本次及以后"剪断:剪断日起的槽位归属新系列,旧例外一并**打墓碑**清出显示 */
    @Query(
        "UPDATE event_exceptions SET isDeleted = 1, updatedAt = :now " +
            "WHERE masterEventId = :masterId AND originalDate >= :fromEpochDay",
    )
    suspend fun tombstoneFrom(masterId: String, fromEpochDay: Long, now: Long)

    // —— M7 备份导入导出 / 同步(spec §3.13、v0.3)——
    // 全量:**含墓碑** —— 备份要带走"已删除"这个事实,同步要推送删除通知。

    @Query("SELECT * FROM event_exceptions ORDER BY id")
    suspend fun allOnce(): List<EventExceptionEntity>

    @Upsert
    suspend fun upsertAll(entities: List<EventExceptionEntity>)

    @Query("DELETE FROM event_exceptions")
    suspend fun clearAll()
}
