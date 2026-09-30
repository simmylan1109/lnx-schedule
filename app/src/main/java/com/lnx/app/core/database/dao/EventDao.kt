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

    /**
     * 搜索命中(spec §3.9):标题 / 备注 / 地点任一命中即返回,**不限日期**。
     * `ESCAPE '\'` 与 [SearchEngine.likePattern] 配对 —— 通配符必须当普通字符搜,
     * 少了它 `100%` 会退化成"以 100 开头"的前缀匹配,`a_b` 会把任意字符算进去。
     * 排序与 [observeBetween] 同序(LIKE 查询没有全序保证,不定序会让结果每查一次顺序都在跳)。
     * 大小写:SQLite 的 LIKE 只对 ASCII 折叠大小写,中文/日文无大小写概念,实际够用。
     * `IFNULL` 是防御性的:OR 链里 NULL 臂并不影响其余臂求值,但把可空列显式补成空串,
     * 免得日后有人把 OR 改成 AND 时静默失效。
     */
    @Query(
        "SELECT * FROM events WHERE isDeleted = 0 AND (" +
            "title LIKE :pattern ESCAPE '\\' OR " +
            "IFNULL(notes, '') LIKE :pattern ESCAPE '\\' OR " +
            "IFNULL(location, '') LIKE :pattern ESCAPE '\\' " +
            ") ORDER BY startAt, endAt, id",
    )
    fun observeMatching(pattern: String): Flow<List<EventEntity>>

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

    // —— M7 备份导入导出(spec §3.13)——

    @Upsert
    suspend fun upsertAll(entities: List<EventEntity>)

    @Query("DELETE FROM events")
    suspend fun clearAll()
}
