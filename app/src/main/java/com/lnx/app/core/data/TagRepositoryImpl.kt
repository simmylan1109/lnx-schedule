package com.lnx.app.core.data

import com.lnx.app.core.database.dao.TagDao
import com.lnx.app.core.database.entity.TagEntity
import com.lnx.app.core.domain.TagRepository
import com.lnx.app.core.domain.model.Tag
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TagRepositoryImpl @Inject constructor(
    private val dao: TagDao,
) : TagRepository {

    override fun observeTags(): Flow<List<Tag>> =
        dao.observeAll().map { list -> list.map(TagEntity::toTag) }

    override suspend fun createTag(name: String, colorSlot: Int): Tag {
        val now = System.currentTimeMillis()
        val entity = TagEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            colorSlot = colorSlot,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(entity)
        return entity.toTag()
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
