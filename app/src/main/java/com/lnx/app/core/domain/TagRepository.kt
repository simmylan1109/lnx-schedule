package com.lnx.app.core.domain

import com.lnx.app.core.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/** 标签的唯一数据出口(标签管理、编辑器选择、抽屉筛选都经由它) */
interface TagRepository {
    /** 未删除标签,按创建顺序 */
    fun observeTags(): Flow<List<Tag>>

    /**
     * 新建标签:同名唯一。
     * - 名字为空 → 失败
     * - 已有同名**未删**标签 → 失败(调用方需提示用户改名,不能悄悄建出第二个同名标签)
     * - 有同名**已软删**标签 → 复活那一行(沿用原 id 与创建时间),返回成功
     */
    suspend fun createTag(name: String, colorSlot: Int): Result<Tag>

    suspend fun renameTag(id: String, name: String)

    /** 删除标签:软删 + 解除全部关联;事件本身不受影响(spec §3.10) */
    suspend fun deleteTag(id: String)

    /** 重设事件标签(清后插) */
    suspend fun setEventTags(eventId: String, tagIds: List<String>)

    fun observeTagsOfEvent(eventId: String): Flow<List<Tag>>

    /** 事件 → 标签 id 列表 的关联快照(抽屉筛选、列表过滤用) */
    fun observeEventTagIds(): Flow<Map<String, List<String>>>
}
