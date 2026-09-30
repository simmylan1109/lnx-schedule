package com.lnx.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 正式迁移(v0.1 开发期一直是 `fallbackToDestructiveMigration()`,M8 补上)。
 *
 * 三条铁律:
 * 1. **SQL 必须与 Room 从实体生成的建表语句逐字一致** —— 迁移跑完 Room 会拿实际 schema
 *    跟 `app/schemas/` 下那些 json 比对,差一个 NOT NULL 或索引名,打开数据库时直接抛
 *    `IllegalStateException: Migration didn't properly handle`。下面的语句是从
 *    `schemas/com.lnx.app.core.database.LnxDatabase/{2,3}.json` 的 `createSql` 抄的,
 *    不是手写的。
 *    (注:这条注释里别写通配路径的 json 文件名 —— Kotlin 的块注释是可嵌套的,
 *    `斜杠星号` 会被当成嵌套注释开头,把外层注释吃掉。踩过一次。)
 * 2. **只加不改**:1→2 加两张表、2→3 加一张表,都是新表,没有列变更 —— 于是不需要
 *    "建新表 + 拷数据 + 删旧表"那套,风险最低。将来真出现列变更,别在这里偷懒。
 * 3. **不写 `fallbackToDestructiveMigration`**:宁可让升级失败抛异常,也不能悄悄清库。
 *
 * 另外:日后若删掉某个历史版本号,用 `fallbackToDestructiveMigrationFrom(那些版本)` 显式
 * 说明"这些版本我不管了",而不是全局退化成清库。
 */
object LnxMigrations {

    /** v1 → v2:标签表 + 事件↔标签关联表(M3) */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS tags (id TEXT NOT NULL, name TEXT NOT NULL, " +
                    "colorSlot INTEGER NOT NULL, createdAt INTEGER NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, isDeleted INTEGER NOT NULL, PRIMARY KEY(id))",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags (name)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS event_tag_cross_ref (eventId TEXT NOT NULL, " +
                    "tagId TEXT NOT NULL, PRIMARY KEY(eventId, tagId))",
            )
        }
    }

    /** v2 → v3:重复事件的例外表(M4) */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS event_exceptions (id TEXT NOT NULL, " +
                    "masterEventId TEXT NOT NULL, originalDate INTEGER NOT NULL, " +
                    "isCancelled INTEGER NOT NULL, overrideTitle TEXT, overrideAllDay INTEGER, " +
                    "overrideStartAt INTEGER, overrideEndAt INTEGER, overrideLocation TEXT, " +
                    "overrideNotes TEXT, overrideColorSlot INTEGER, overridePriority TEXT, " +
                    "overrideReminderLeadMinutes INTEGER, createdAt INTEGER NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, PRIMARY KEY(id))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_event_exceptions_masterEventId " +
                    "ON event_exceptions (masterEventId)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_event_exceptions_masterEventId_originalDate " +
                    "ON event_exceptions (masterEventId, originalDate)",
            )
        }
    }

    /** 按序登记,`DatabaseModule` 直接 addMigrations(*ALL) */
    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
