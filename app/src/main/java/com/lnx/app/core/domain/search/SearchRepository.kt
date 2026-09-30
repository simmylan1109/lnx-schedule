package com.lnx.app.core.domain.search

import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

/**
 * 搜索(spec §3.9)。与 [com.lnx.app.core.domain.EventRepository] 分开:
 * 搜索是一次性查询、结果带"下次发生"这类派生信息,不参与日历的观察流,
 * 混进去会让 EventRepository 变成什么都能干的大杂烩。
 */
interface SearchRepository {
    /**
     * 按 [query] 匹配**标题 + 备注 + 地点**,不区分大小写,覆盖所有日期(含过去)。
     * [query] 去空白后为空时直接给空列表,不发查询。
     */
    fun search(
        query: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): Flow<List<SearchResult>>
}
