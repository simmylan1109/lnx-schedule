package com.lnx.app.core.domain

import com.lnx.app.core.domain.model.Occurrence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 标签筛选状态(spec §3.10):**会话内保持,重启恢复全选** ——
 * 刻意不持久化(进程内单例随 App 一起消失就是"重启恢复")。
 */
@Singleton
class TagFilterState @Inject constructor() {
    private val _hiddenTagIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenTagIds: StateFlow<Set<String>> = _hiddenTagIds.asStateFlow()

    private val _hideUntagged = MutableStateFlow(false)
    val hideUntagged: StateFlow<Boolean> = _hideUntagged.asStateFlow()

    fun toggle(tagId: String) = _hiddenTagIds.update { hidden ->
        if (tagId in hidden) hidden - tagId else hidden + tagId
    }

    fun toggleUntagged() = _hideUntagged.update { !it }

    fun reset() {
        _hiddenTagIds.value = emptySet()
        _hideUntagged.value = false
    }
}

/**
 * 标签筛选(spec §3.10):勾掉标签即隐藏"含该标签"的事件 —— **任一标签命中即隐藏**
 * (事件同时属于 A、B 时,勾掉 A 它也消失,即使 B 仍勾着)。
 * 没有关联记录的事件一律按"未分类"处理。
 */
fun applyTagFilter(
    occurrences: List<Occurrence>,
    eventTags: Map<String, List<String>>,
    hidden: Set<String>,
    hideUntagged: Boolean,
): List<Occurrence> {
    if (hidden.isEmpty() && !hideUntagged) return occurrences
    return occurrences.filter { occ ->
        val tagIds = eventTags[occ.event.id] ?: emptyList()
        when {
            tagIds.isEmpty() -> !hideUntagged
            else -> tagIds.none { it in hidden }
        }
    }
}
