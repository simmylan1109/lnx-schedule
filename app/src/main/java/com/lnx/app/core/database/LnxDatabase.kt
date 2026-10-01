package com.lnx.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.database.dao.EventExceptionDao
import com.lnx.app.core.database.dao.TagDao
import com.lnx.app.core.database.entity.CourseEntity
import com.lnx.app.core.database.entity.CourseSessionEntity
import com.lnx.app.core.database.entity.EventEntity
import com.lnx.app.core.database.entity.EventExceptionEntity
import com.lnx.app.core.database.entity.EventTagCrossRef
import com.lnx.app.core.database.entity.PeriodEntity
import com.lnx.app.core.database.entity.TagEntity
import com.lnx.app.core.database.entity.TermEntity

@Database(
    entities = [
        EventEntity::class,
        EventExceptionEntity::class,
        TagEntity::class,
        EventTagCrossRef::class,
        // v0.2 课表:四张新表,与日历那四张互不引用(用户拍板"课表独立一套数据")
        TermEntity::class,
        PeriodEntity::class,
        CourseEntity::class,
        CourseSessionEntity::class,
    ],
    version = DbVersion.CURRENT,
    exportSchema = true,
)
abstract class LnxDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    abstract fun eventExceptionDao(): EventExceptionDao

    abstract fun tagDao(): TagDao
}
