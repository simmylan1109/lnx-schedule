package com.lnx.app.feature.event

import com.lnx.app.core.domain.EventRepository
import kotlinx.coroutines.flow.first

/**
 * 时间重叠检查(spec §3.5:保存时若与已有事件重叠,给轻提示但不阻止保存)。
 * 半开区间:结束等于他人开始不算重叠;编辑时排除自己。
 */
object OverlapChecker {
    suspend fun find(
        repository: EventRepository,
        draft: EventDraft,
        excludeId: String? = draft.id,
    ): List<String> {
        // 重叠必定发生在同一天,只查这一天
        val dayStart = draft.start.toLocalDate().atStartOfDay()
        val existing = repository.observeEvents(dayStart, dayStart.plusDays(1)).first()
        return existing
            .filter { it.id != excludeId }
            .filter { it.start < draft.end && it.end > draft.start }
            .map { it.title }
    }
}
