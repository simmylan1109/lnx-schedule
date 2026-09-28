package com.lnx.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lnx.app.core.database.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    /** 半开区间:startAt < endMillis 且 endAt > startMillis(spec §4.4) */
    @Query(
        "SELECT * FROM events WHERE isDeleted = 0 AND startAt < :endMillis AND endAt > :startMillis " +
            "ORDER BY startAt",
    )
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id AND isDeleted = 0")
    suspend fun getById(id: String): EventEntity?

    @Upsert
    suspend fun upsert(entity: EventEntity)

    @Query("UPDATE events SET isDeleted = 1, updatedAt = :updatedAtMillis WHERE id = :id")
    suspend fun softDelete(id: String, updatedAtMillis: Long)

    /** 备份导出用(M7) */
    @Query("SELECT * FROM events ORDER BY startAt")
    suspend fun allOnce(): List<EventEntity>
}
