package com.lnx.app.core.domain

import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.Occurrence
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

/** 事件的唯一数据出口(M2 起所有 UI 只经由它读写) */
interface EventRepository {
    /** 观察可见区间内的发生;区间为半开 [start, end) */
    fun observeOccurrences(start: LocalDateTime, end: LocalDateTime): Flow<List<Occurrence>>

    /** 观察可见区间内的原始事件(编辑/详情用,不做展开) */
    fun observeEvents(start: LocalDateTime, end: LocalDateTime): Flow<List<Event>>

    suspend fun getEvent(id: String): Event?

    /** 新增或覆盖(按 id) */
    suspend fun save(event: Event)

    /** 软删除(spec §4.5:保留墓碑供未来同步) */
    suspend fun delete(id: String)

    // —— 重复事件的例外(spec §4.3/§4.4)——

    /** 写/覆盖一条例外("仅本次"的修改;取消那次用 [cancelOccurrence]) */
    suspend fun upsertException(exception: EventException)

    /** 取消某一次发生("仅本次"的删除) */
    suspend fun cancelOccurrence(masterId: String, originalDate: LocalDate)

    /** 删除母事件的全部例外("全部删除"时母事件已删,例外跟着清) */
    suspend fun deleteExceptionsFor(masterId: String)

    /** 删除某日起的全部例外("本次及以后"剪断后,那些槽位归属新系列) */
    suspend fun deleteExceptionsFrom(masterId: String, from: LocalDate)
}
