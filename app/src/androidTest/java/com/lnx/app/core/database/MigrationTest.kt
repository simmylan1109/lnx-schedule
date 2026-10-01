package com.lnx.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lnx.app.core.di.DatabaseModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 正式迁移的真库验证(M8)。**不是**"跑一遍不崩就算过 ——
 * `MigrationTestHelper` 会在迁移后自动调用 Room 的 schema 校验,
 * SQL 与实体差一个字就直接抛 `Migration didn't properly handle`,那才是这条测试的价值。
 *
 * 另外每步都插了真实数据再迁,验"数据还在"——迁移最容易出的事故不是崩,是**悄悄丢数据**。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LnxDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    /** v1 的事件表长这样(从 schemas/1.json 抄的列),插一条真实事件 */
    private fun insertEventV1(db: androidx.sqlite.db.SupportSQLiteDatabase, id: String, title: String) {
        db.execSQL(
            "INSERT INTO events (id, title, allDay, startAt, endAt, location, notes, colorSlot, " +
                "priority, reminderLeadMinutes, ruleType, ruleInterval, ruleWeekdays, " +
                "ruleMonthlyMode, ruleMonthlyDay, ruleMonthlyNth, ruleMonthlyWeekday, " +
                "ruleEndType, ruleEndDate, ruleCount, createdAt, updatedAt, isDeleted) " +
                "VALUES ('$id', '$title', 0, 1757000000000, 1757003600000, NULL, NULL, 0, " +
                "'P2', NULL, 'NONE', 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 0)",
        )
    }

    @Test
    fun v1到v2_加标签表且事件数据不丢() {
        helper.createDatabase(dbName, 1).use { db ->
            insertEventV1(db, "e1", "迁移前的会")
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 2, true, LnxMigrations.MIGRATION_1_2)

        migrated.query("SELECT title FROM events WHERE id = 'e1'").use { c ->
            assertTrue("事件表的数据必须还在", c.moveToFirst())
            assertEquals("迁移前的会", c.getString(0))
        }
        // 新表建出来了(能插能查)
        migrated.execSQL(
            "INSERT INTO tags (id, name, colorSlot, createdAt, updatedAt, isDeleted) " +
                "VALUES ('t1', '工作', 1, 0, 0, 0)",
        )
        migrated.query("SELECT name FROM tags WHERE id = 't1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("工作", c.getString(0))
        }
    }

    @Test
    fun v1到v2_同名标签的唯一索引真的建上了() {
        helper.createDatabase(dbName, 1).use { }
        val migrated = helper.runMigrationsAndValidate(dbName, 2, true, LnxMigrations.MIGRATION_1_2)

        migrated.execSQL(
            "INSERT INTO tags (id, name, colorSlot, createdAt, updatedAt, isDeleted) " +
                "VALUES ('t1', '工作', 1, 0, 0, 0)",
        )
        // 唯一索引没建上的话下面这条会插进去,标签系统会出现两个"工作"
        var threw = false
        try {
            migrated.execSQL(
                "INSERT INTO tags (id, name, colorSlot, createdAt, updatedAt, isDeleted) " +
                    "VALUES ('t2', '工作', 2, 0, 0, 0)",
            )
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            threw = true
        }
        assertTrue("index_tags_name 必须是 UNIQUE", threw)
    }

    @Test
    fun v2到v3_加例外表且标签数据不丢() {
        helper.createDatabase(dbName, 2).use { db ->
            insertEventV1(db, "e1", "重复事件的母事件")
            db.execSQL(
                "INSERT INTO tags (id, name, colorSlot, createdAt, updatedAt, isDeleted) " +
                    "VALUES ('t1', '工作', 1, 0, 0, 0)",
            )
            db.execSQL("INSERT INTO event_tag_cross_ref (eventId, tagId) VALUES ('e1', 't1')")
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 3, true, LnxMigrations.MIGRATION_2_3)

        migrated.query("SELECT name FROM tags WHERE id = 't1'").use { c ->
            assertTrue("标签数据必须还在", c.moveToFirst())
            assertEquals("工作", c.getString(0))
        }
        migrated.query("SELECT tagId FROM event_tag_cross_ref WHERE eventId = 'e1'").use { c ->
            assertTrue("事件↔标签关联必须还在", c.moveToFirst())
            assertEquals("t1", c.getString(0))
        }
        // 例外表建出来了:同一母事件的同一原本发生日只能有一条(唯一索引)
        migrated.execSQL(
            "INSERT INTO event_exceptions (id, masterEventId, originalDate, isCancelled, " +
                "createdAt, updatedAt) VALUES ('x1', 'e1', 20000, 1, 0, 0)",
        )
        var threw = false
        try {
            migrated.execSQL(
                "INSERT INTO event_exceptions (id, masterEventId, originalDate, isCancelled, " +
                    "createdAt, updatedAt) VALUES ('x2', 'e1', 20000, 0, 0, 0)",
            )
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            threw = true
        }
        assertTrue("(masterEventId, originalDate) 必须是 UNIQUE", threw)
    }

    @Test
    fun v3到v4_加课表四张表且原有数据不丢() {
        helper.createDatabase(dbName, 3).use { db ->
            insertEventV1(db, "e1", "升级前的会")
            db.execSQL(
                "INSERT INTO tags (id, name, colorSlot, createdAt, updatedAt, isDeleted) " +
                    "VALUES ('t1', '工作', 1, 0, 0, 0)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 4, true, LnxMigrations.MIGRATION_3_4)

        // 老数据必须还在 —— 课表是"加表",不是"重建库"
        migrated.query("SELECT title FROM events WHERE id = 'e1'").use { c ->
            assertTrue("事件表的数据必须还在", c.moveToFirst())
            assertEquals("升级前的会", c.getString(0))
        }
        migrated.query("SELECT name FROM tags WHERE id = 't1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("工作", c.getString(0))
        }
        // 四张新表都建出来了,而且能真的写进去(能建不能写等于没建)
        listOf("terms", "periods", "courses", "course_sessions").forEach { table ->
            migrated.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
                .use { c -> assertTrue("$table 应该存在", c.moveToFirst()) }
        }
        migrated.execSQL(
            "INSERT INTO terms (id, name, startDate, weekCount, createdAt, updatedAt, isDeleted) " +
                "VALUES ('term1', '2026 秋', 20592, 20, 0, 0, 0)",
        )
        migrated.execSQL(
            "INSERT INTO periods (id, termId, periodIndex, startMinute, endMinute, " +
                "createdAt, updatedAt, isDeleted) VALUES ('p1', 'term1', 1, 480, 525, 0, 0, 0)",
        )
        migrated.execSQL(
            "INSERT INTO courses (id, termId, name, teacher, location, colorSlot, notes, " +
                "createdAt, updatedAt, isDeleted) " +
                "VALUES ('c1', 'term1', '高等数学', '王老师', '教三 401', 2, NULL, 0, 0, 0)",
        )
        migrated.execSQL(
            "INSERT INTO course_sessions (id, courseId, dayOfWeek, periodId, weekFrom, weekTo, " +
                "createdAt, updatedAt, isDeleted) VALUES ('s1', 'c1', 3, 'p1', 1, 16, 0, 0, 0)",
        )
        migrated.query("SELECT name FROM courses WHERE id = 'c1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("高等数学", c.getString(0))
        }
    }

    @Test
    fun v1一路迁到v4_全链路不丢数据() {
        // 真实用户可能从很老的版本升上来,一次要跨三步
        helper.createDatabase(dbName, 1).use { db ->
            insertEventV1(db, "e1", "老用户的事件")
        }

        val migrated = helper.runMigrationsAndValidate(
            dbName,
            4,
            true,
            *LnxMigrations.ALL,
        )

        migrated.query("SELECT title FROM events WHERE id = 'e1'").use { c ->
            assertTrue("跨三步迁移后事件还得在", c.moveToFirst())
            assertEquals("老用户的事件", c.getString(0))
        }
        // 七张表都在
        listOf(
            "events", "tags", "event_tag_cross_ref", "event_exceptions",
            "terms", "periods", "courses", "course_sessions",
        ).forEach { table ->
            migrated.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
                .use { c -> assertTrue("$table 应该存在", c.moveToFirst()) }
        }
    }

    /**
     * 走**真实 DI 那一行**的升级路径(终审 P1-2)。
     *
     * 上面几条验的是 `LnxMigrations.ALL` 这个数组本身;而所有仪器测试都被
     * `TestDatabaseModule` 顶着用的是内存库 —— 谁把 `DatabaseModule` 里的
     * `.addMigrations(*LnxMigrations.ALL)` 删掉,或者把 destructive 兜底加回来,
     * 371 条用例照样全绿。去掉 destructive 之后那行是整个 App 最重的一行,必须有个闸门。
     *
     * 做法:先用 helper 造一个真正的 v1 落盘库(就叫 lnx.db),再用
     * `DatabaseModule.provideDatabase` 打开它 —— 第一次 DAO 调用时 Room 才真正开库跑迁移。
     */
    @Test
    fun DI里的数据库能从v1升上来且不清库() = runBlocking {
        val realName = "lnx.db"
        helper.createDatabase(realName, 1).use { db ->
            insertEventV1(db, "e-di", "DI 路径的老事件")
        }

        val db = DatabaseModule.provideDatabase(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        try {
            // Room 是懒加载的:这一句才是真正"打开库 → 跑迁移"的动作
            val titles = db.eventDao().allOnce().map { it.title }
            assertEquals(listOf("DI 路径的老事件"), titles)
            // 迁移把新表建起来了(查得到,而不是库被重建)
            assertTrue(
                "标签表应当存在(空表),而不是整库被清掉",
                db.tagDao().allOnce().isEmpty(),
            )
            // 课表的四张表也必须经由 DI 这一行走完迁移建出来 —— v3→v4 是唯一能证明
            // "这条路线真的把 MIGRATION_3_4 登记进去了"的地方(其余仪器测试都跑在
            // TestDatabaseModule 顶掉的内存库上,压根不碰迁移)
            val names = mutableListOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type='table'", emptyArray()).use { c ->
                while (c.moveToNext()) names += c.getString(0)
            }
            listOf("terms", "periods", "courses", "course_sessions").forEach { table ->
                assertTrue("DI 路径迁移后 $table 应当存在", table in names)
            }
        } finally {
            db.close()
            // 别把测试库留给下一次运行
            InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(realName)
        }
    }
}
