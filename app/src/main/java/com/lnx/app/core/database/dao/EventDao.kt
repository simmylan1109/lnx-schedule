package com.lnx.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lnx.app.core.database.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    /**
     * 半开区间:startAt < endMillis 且 endAt > startMillis(spec §4.4)。
     * 排序必须全序:并列开始时间若顺序不定,车道分配会左右抖动(spec §3.2 并排错开)。
     */
    @Query(
        "SELECT * FROM events WHERE isDeleted = 0 AND startAt < :endMillis AND endAt > :startMillis " +
            "ORDER BY startAt, endAt, id",
    )
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id AND isDeleted = 0")
    suspend fun getById(id: String): EventEntity?

    @Upsert
    suspend fun upsert(entity: EventEntity)

    @Query("UPDATE events SET isDeleted = 1, updatedAt = :updatedAtMillis WHERE id = :id")
    suspend fun softDelete(id: String, updatedAtMillis: Long)

    /**
     * 备份导出用(M7)。**故意不过滤软删除**:spec §3.13 的合并导入按 updatedAt 取舍、
     * 覆盖导入需还原删除态,墓碑(isDeleted)必须一起走,否则换机后删掉的事件会复活。
     */
    @Query("SELECT * FROM events ORDER BY startAt, endAt, id")
    suspend fun allOnce(): List<EventEntity>
}
