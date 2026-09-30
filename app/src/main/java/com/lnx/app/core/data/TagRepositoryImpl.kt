package com.lnx.app.core.data

import com.lnx.app.core.database.dao.TagDao
import com.lnx.app.core.database.entity.TagEntity
import com.lnx.app.core.domain.TagRepository
import com.lnx.app.core.domain.model.Tag
import com.lnx.app.core.domain.model.TagNameError
import com.lnx.app.core.domain.model.TagNameException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TagRepositoryImpl @Inject constructor(
    private val dao: TagDao,
) : TagRepository {

    override fun observeTags(): Flow<List<Tag>> =
        dao.observeAll().map { list -> list.map(TagEntity::toTag) }

    override suspend fun createTag(name: String, colorSlot: Int): Result<Tag> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(TagNameException(TagNameError.EMPTY))
        }
        val now = System.currentTimeMillis()
        // 必须先按名字查:name 上有唯一索引,重名时 @Upsert 会吞掉约束异常再按 id 更新(0 行),
        // 表面上"成功"返回一个不存在的 id,后面事件就会挂上永远看不见也删不掉的幽灵关联。
        val existing = dao.findByName(trimmed)
        if (existing != null && !existing.isDeleted) {
            return Result.failure(TagNameException(TagNameError.DUPLICATE, trimmed))
        }
        val entity = (existing ?: TagEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            colorSlot = colorSlot,
            createdAt = now,
            updatedAt = now,
            isDeleted = false,
        )).let { base ->
            // 复活软删行:沿用 id 与创建时间,只改颜色和删除标记
            base.copy(colorSlot = colorSlot, updatedAt = now, isDeleted = false)
        }
        dao.upsert(entity)
        return Result.success(entity.toTag())
    }

    override suspend fun renameTag(id: String, name: String) {
        val target = dao.getById(id) ?: return
        dao.upsert(target.copy(name = name.trim(), updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteTag(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
        dao.clearTagRefs(id) // 解除关联,事件本身不动(spec §3.10)
    }

    override suspend fun setEventTags(eventId: String, tagIds: List<String>) {
        dao.setEventTags(eventId, tagIds)
    }

    override fun observeTagsOfEvent(eventId: String): Flow<List<Tag>> =
        dao.observeTagsOfEvent(eventId).map { list -> list.map(TagEntity::toTag) }

    override fun observeEventTagIds(): Flow<Map<String, List<String>>> =
        dao.observeEventTagPairs().map { pairs ->
            pairs.groupBy(keySelector = { it.eventId }, valueTransform = { it.tagId })
        }
}

private fun TagEntity.toTag() = Tag(id = id, name = name, colorSlot = colorSlot)
