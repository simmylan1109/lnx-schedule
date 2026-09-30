package com.lnx.app.core.data

import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.database.dao.EventExceptionDao
import com.lnx.app.core.database.entity.toDomain
import com.lnx.app.core.domain.search.SearchEngine
import com.lnx.app.core.domain.search.SearchRepository
import com.lnx.app.core.domain.search.SearchResult
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val dao: EventDao,
    private val exceptionDao: EventExceptionDao,
    private val engine: SearchEngine,
) : SearchRepository {

    /**
     * 例外表一起带上:重复事件的「下次发生」要把已取消/已改期的那几次算进去,
     * 只查事件表的话,用户删掉的下一场站会照样出现在搜索结果里。
     */
    override fun search(query: String, now: LocalDateTime): Flow<List<SearchResult>> {
        if (query.isBlank()) return flowOf(emptyList())
        val pattern = SearchEngine.likePattern(query)
        return combine(
            dao.observeMatching(pattern),
            exceptionDao.observeAll(),
        ) { rows, exceptions ->
            engine.search(
                events = rows.map { it.toEvent() },
                exceptions = exceptions.map { it.toDomain() },
                now = now,
            )
        }
    }
}
