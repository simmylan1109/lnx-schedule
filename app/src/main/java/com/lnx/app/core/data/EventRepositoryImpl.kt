package com.lnx.app.core.data

import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.database.dao.EventExceptionDao
import com.lnx.app.core.database.entity.toDomain
import com.lnx.app.core.database.entity.toEntity
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.OccurrenceExpander
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.Occurrence
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class EventRepositoryImpl @Inject constructor(
    private val dao: EventDao,
    private val exceptionDao: EventExceptionDao,
    private val expander: OccurrenceExpander,
) : EventRepository {

    /**
     * 发生 = 母事件展开(现场计算,不落库)+ 例外修正。
     * 事件查询用 observeForExpansion:重复母事件哪怕起点在窗口前也要带上,
     * 否则"永不结束的每日事件"在翻到下个月时就整条消失了。
     */
    override fun observeOccurrences(
        start: LocalDateTime,
        end: LocalDateTime,
    ): Flow<List<Occurrence>> = combine(
        dao.observeForExpansion(start.toEpochMillis(), end.toEpochMillis()),
        exceptionDao.observeAll(),
    ) { events, exceptions ->
        expander.expand(
            events.map { it.toEvent() },
            exceptions.map { it.toDomain() },
            start,
            end,
        )
    }

    override fun observeEvents(start: LocalDateTime, end: LocalDateTime): Flow<List<Event>> =
        dao.observeBetween(start.toEpochMillis(), end.toEpochMillis()).map { list ->
            list.map { it.toEvent() }
        }

    override suspend fun getEvent(id: String): Event? = dao.getById(id)?.toEvent()

    override suspend fun save(event: Event) {
        val now = System.currentTimeMillis()
        dao.upsert(
            event.toEntity().copy(
                createdAt = if (event.createdAt == 0L) now else event.createdAt,
                updatedAt = now,
            ),
        )
    }

    override suspend fun delete(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    override suspend fun upsertException(exception: EventException) {
        exceptionDao.upsert(exception.toEntity(System.currentTimeMillis()))
    }

    override suspend fun cancelOccurrence(masterId: String, originalDate: LocalDate) {
        upsertException(EventException(masterId = masterId, originalDate = originalDate, cancelled = true))
    }

    override suspend fun deleteExceptionsFor(masterId: String) {
        exceptionDao.deleteForMaster(masterId)
    }

    override suspend fun deleteExceptionsFrom(masterId: String, from: LocalDate) {
        exceptionDao.deleteFrom(masterId, from.toEpochDay())
    }
}
