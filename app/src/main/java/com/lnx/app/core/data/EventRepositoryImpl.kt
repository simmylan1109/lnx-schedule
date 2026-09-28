package com.lnx.app.core.data

import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.OccurrenceExpander
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.Occurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepositoryImpl @Inject constructor(
    private val dao: EventDao,
    private val expander: OccurrenceExpander,
) : EventRepository {

    override fun observeOccurrences(
        start: LocalDateTime,
        end: LocalDateTime,
    ): Flow<List<Occurrence>> = observeEvents(start, end).map { events ->
        expander.expand(events, start, end)
    }

    override fun observeEvents(start: LocalDateTime, end: LocalDateTime): Flow<List<Event>> =
        dao.observeBetween(start.toMillis(), end.toMillis()).map { list ->
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

    private fun LocalDateTime.toMillis(): Long =
        atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
