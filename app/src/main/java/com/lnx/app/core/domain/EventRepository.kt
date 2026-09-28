package com.lnx.app.core.domain

import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.Occurrence
import kotlinx.coroutines.flow.Flow
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
}
