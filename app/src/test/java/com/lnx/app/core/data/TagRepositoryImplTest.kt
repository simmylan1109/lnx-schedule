package com.lnx.app.core.data

import com.lnx.app.core.database.dao.TagDao
import com.lnx.app.core.database.entity.EventTagCrossRef
import com.lnx.app.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标签仓库契约(spec §3.10),用假 DAO 覆盖三条容易悄悄坏掉的语义:
 * 同名不许建第二个、软删后同名要能复活、删标签要解除关联。
 */
class TagRepositoryImplTest {

    private class FakeTagDao : TagDao {
        val rows = linkedMapOf<String, TagEntity>()
        val upserted = mutableListOf<TagEntity>()
        val clearedTagRefs = mutableListOf<String>()

        private fun emit() = MutableStateFlow(rows.values.filter { !it.isDeleted })

        override fun observeAll(): Flow<List<TagEntity>> = emit()

        override suspend fun upsert(entity: TagEntity) {
            upserted += entity
            // 唯一索引(name)只认未删的行不行——真实 schema 是含软删行都算冲突,
            // 这里照抄真实行为,免得测试通过而线上撞索引
            check(rows.values.none { it.name == entity.name && it.id != entity.id }) {
                "唯一索引冲突:重名 ${entity.name}"
            }
            rows[entity.id] = entity
        }

        override suspend fun getById(id: String): TagEntity? = rows[id]?.takeIf { !it.isDeleted }

        override suspend fun findByName(name: String): TagEntity? = rows.values.firstOrNull { it.name == name }

        override suspend fun softDelete(id: String, updatedAtMillis: Long) {
            rows[id]?.let { rows[id] = it.copy(isDeleted = true, updatedAt = updatedAtMillis) }
        }

        override suspend fun setEventTags(eventId: String, tagIds: List<String>) = Unit

        override suspend fun clearEventTags(eventId: String) = Unit

        override suspend fun insertCrossRef(ref: EventTagCrossRef) = Unit

        override suspend fun tagsOfEvent(eventId: String): List<TagEntity> = emptyList()

        override fun observeTagsOfEvent(eventId: String): Flow<List<TagEntity>> = emit()

        override fun observeEventTagPairs(): Flow<List<EventTagCrossRef>> = MutableStateFlow(emptyList())

        override suspend fun clearTagRefs(tagId: String) {
            clearedTagRefs += tagId
        }

        // 备份导入导出(M7)走真 Room,这里只求能编译
        override suspend fun allOnce(): List<TagEntity> = rows.values.toList()

        override suspend fun allEventTags(): List<EventTagCrossRef> = emptyList()

        override suspend fun upsertAll(entities: List<TagEntity>) {
            entities.forEach { upsert(it) }
        }

        override suspend fun insertCrossRefs(refs: List<EventTagCrossRef>) = Unit

        override suspend fun clearAll() {
            rows.clear()
        }

        override suspend fun clearAllRefs() = Unit
    }

    @Test
    fun `同名再建一次判失败且不写库`() = runTest {
        val dao = FakeTagDao()
        val repo = TagRepositoryImpl(dao)

        val first = repo.createTag("工作", 2).getOrThrow()
        val rowsAfterFirst = dao.upserted.size
        val second = repo.createTag("工作", 5)

        assertTrue(first.id.isNotEmpty())
        assertTrue(second.isFailure)
        assertEquals(rowsAfterFirst, dao.upserted.size) // 没有第二次写库
    }

    @Test
    fun `名字去空格后仍算同名`() = runTest {
        val dao = FakeTagDao()
        val repo = TagRepositoryImpl(dao)
        repo.createTag("工作", 2).getOrThrow()
        assertTrue(repo.createTag("  工作  ", 5).isFailure)
    }

    @Test
    fun `空名判失败`() = runTest {
        val repo = TagRepositoryImpl(FakeTagDao())
        assertTrue(repo.createTag("   ", 0).isFailure)
    }

    @Test
    fun `软删后同名重建复活原行`() = runTest {
        val dao = FakeTagDao()
        val repo = TagRepositoryImpl(dao)
        val first = repo.createTag("工作", 2).getOrThrow()
        repo.deleteTag(first.id)

        val revived = repo.createTag("工作", 6).getOrThrow()

        assertEquals(first.id, revived.id) // 沿用原 id,不会留下两行同名
        assertEquals(6, revived.colorSlot)
        assertEquals(1, dao.rows.size)
        assertFalse(dao.rows.getValue(first.id).isDeleted)
    }

    @Test
    fun `删标签会解除它的全部关联`() = runTest {
        val dao = FakeTagDao()
        val repo = TagRepositoryImpl(dao)
        val tag = repo.createTag("工作", 2).getOrThrow()
        repo.deleteTag(tag.id)
        assertEquals(listOf(tag.id), dao.clearedTagRefs)
    }
}
