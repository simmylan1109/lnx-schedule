package com.lnx.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lnx.app.core.database.entity.EventExceptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventExceptionDao {
    /** 全量例外流(展开器按 masterId 分组消费;v0.1 数据量小,不按窗口裁剪) */
    @Query("SELECT * FROM event_exceptions")
    fun observeAll(): Flow<List<EventExceptionEntity>>

    @Query("SELECT * FROM event_exceptions WHERE masterEventId = :masterId AND originalDate = :epochDay")
    suspend fun getFor(masterId: String, epochDay: Long): EventExceptionEntity?

    /** 例外 id 是确定性的(母事件 + 原本发生日),重复写同一例外 = 覆盖 */
    @Upsert
    suspend fun upsert(entity: EventExceptionEntity)

    /** 删除母事件时清掉它的全部例外(例外离开母事件没有意义) */
    @Query("DELETE FROM event_exceptions WHERE masterEventId = :masterId")
    suspend fun deleteForMaster(masterId: String)

    /** "本次及以后"剪断:剪断日起的槽位归属新系列,旧例外一并清掉 */
    @Query("DELETE FROM event_exceptions WHERE masterEventId = :masterId AND originalDate >= :fromEpochDay")
    suspend fun deleteFrom(masterId: String, fromEpochDay: Long)

    // —— M7 备份导入导出(spec §3.13)——

    @Query("SELECT * FROM event_exceptions ORDER BY id")
    suspend fun allOnce(): List<EventExceptionEntity>

    @Upsert
    suspend fun upsertAll(entities: List<EventExceptionEntity>)

    @Query("DELETE FROM event_exceptions")
    suspend fun clearAll()
}
