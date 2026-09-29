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

    /**
     * 展开用查询(spec §4.4 现场展开):**窗口重叠 OR 规则组非 NONE**。
     * 关键在括号:重复母事件哪怕起点在窗口前(永不结束的每日事件)也要带上,
     * 否则翻到下个月整条系列就"消失";其余(非重复)事件严格按窗口,别把全表拉出来。
     * 历史教训:旧写法把"非 NONE"排除在外,重复事件只在与窗口重叠时才返回 —— 见 M4 终审。
     * 注意:本查询只提供候选,真正的窗口裁剪在展开器(occurrence 级)。
     */
    @Query(
        "SELECT * FROM events WHERE isDeleted = 0 AND " +
            "((startAt < :endMillis AND endAt > :startMillis) OR ruleType != 'NONE') " +
            "ORDER BY startAt, endAt, id",
    )
    fun observeForExpansion(startMillis: Long, endMillis: Long): Flow<List<EventEntity>>

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
