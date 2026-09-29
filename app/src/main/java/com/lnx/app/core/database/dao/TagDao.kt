package com.lnx.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.lnx.app.core.database.entity.EventTagCrossRef
import com.lnx.app.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    /** 未删除标签,按创建顺序(抽屉清单、标签选择器都用这个序) */
    @Query("SELECT * FROM tags WHERE isDeleted = 0 ORDER BY createdAt, id")
    fun observeAll(): Flow<List<TagEntity>>

    @Upsert
    suspend fun upsert(entity: TagEntity)

    @Query("SELECT * FROM tags WHERE id = :id AND isDeleted = 0")
    suspend fun getById(id: String): TagEntity?

    /**
     * 按名字查(**含软删行**)。name 上有唯一索引且软删不删行,
     * 所以建重名标签时唯一索引照样会撞——必须先查这一条,否则 @Upsert 会吞掉约束异常、
     * 回退去按新 id 做 UPDATE(影响 0 行),返回一个库里根本不存在的"幽灵标签 id"。
     */
    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query("UPDATE tags SET isDeleted = 1, updatedAt = :updatedAtMillis WHERE id = :id")
    suspend fun softDelete(id: String, updatedAtMillis: Long)

    /** 重设事件标签 = 清掉旧关联再插入(事务内两步,失败一起回滚) */
    @Transaction
    suspend fun setEventTags(eventId: String, tagIds: List<String>) {
        clearEventTags(eventId)
        tagIds.forEach { tagId -> insertCrossRef(EventTagCrossRef(eventId, tagId)) }
    }

    @Query("DELETE FROM event_tag_cross_ref WHERE eventId = :eventId")
    suspend fun clearEventTags(eventId: String)

    @Insert
    suspend fun insertCrossRef(ref: EventTagCrossRef)

    @Query(
        "SELECT t.* FROM tags t INNER JOIN event_tag_cross_ref c ON t.id = c.tagId " +
            "WHERE c.eventId = :eventId AND t.isDeleted = 0 ORDER BY t.createdAt, t.id",
    )
    suspend fun tagsOfEvent(eventId: String): List<TagEntity>

    @Query(
        "SELECT t.* FROM tags t INNER JOIN event_tag_cross_ref c ON t.id = c.tagId " +
            "WHERE c.eventId = :eventId AND t.isDeleted = 0 ORDER BY t.createdAt, t.id",
    )
    fun observeTagsOfEvent(eventId: String): Flow<List<TagEntity>>

    /** 全部关联对(筛选/月视图聚合用);含已删标签的关联,消费方按需过滤 */
    @Query("SELECT * FROM event_tag_cross_ref")
    fun observeEventTagPairs(): Flow<List<EventTagCrossRef>>

    /** 删除标签时解除其全部关联(spec §3.10:删除标签不影响事件本身) */
    @Query("DELETE FROM event_tag_cross_ref WHERE tagId = :tagId")
    suspend fun clearTagRefs(tagId: String)
}
