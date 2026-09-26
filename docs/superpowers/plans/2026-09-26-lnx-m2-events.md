# lnx M2「事件增删改查」实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成事件数据层与增删改查全链路:三个入口建事件(时间预填/吸附)、周视图渲染彩色事件块、点块看详情、可编辑、可删除,保存时时间重叠轻提示。

**Architecture:** 引入 Room 数据层(entities/DAO/DI);领域层 `Event`/`Occurrence`/`OccurrenceExpander`(本里程碑只展开非重复事件,重复引擎 M4 替换内部实现,接口不变);`EventRepository` 作为唯一数据出口。编辑页为全屏 Composable,通过导航参数传递预填时间。

**Tech Stack:** Room 2.6.1(+KSP)、kotlinx-coroutines、Material3 Picker、既有 Compose/Hilt 体系。

**Spec:** `D:\11lnx\SPEC.md`(本计划实现 §8.1 M2;数据模型遵循 §4)

## Global Constraints

- 同 M1(minSdk 26 / target 35 / Kotlin 2.0 / Conventional Commits / 图标用矢量)
- 数据库 schema 一次建对:重复规则字段本里程碑就进表(M4 启用),避免 migration
- 全天事件时间语义:`startAt` = 起始日 00:00,`endAt` = **结束日次日的 00:00(排他)**,与时间轴/跨天渲染统一
- 所有时间换算用设备当前时区(spec §4.6 已知限制)
- 事件块点击目标最小 48dp 高(可用性),不足时扩大点击区
- 测试先行;DAO 测试用 Room in-memory(instrumented),纯逻辑用本地单测

---

### Task 1: Room 数据层

**Files:**
- Create: `app/src/main/java/com/lnx/app/core/database/entity/EventEntity.kt`
- Create: `app/src/main/java/com/lnx/app/core/database/dao/EventDao.kt`
- Create: `app/src/main/java/com/lnx/app/core/database/LnxDatabase.kt`
- Create: `app/src/main/java/com/lnx/app/core/di/DatabaseModule.kt`
- Modify: `gradle/libs.versions.toml` + `app/build.gradle.kts`(room = "2.6.1",room-runtime / room-ktx / room-compiler ksp)
- Test: `app/src/androidTest/java/com/lnx/app/core/database/EventDaoTest.kt`

**Interfaces:**
- Produces:
  - `EventEntity`(字段 = spec §4.2 全集,见下)
  - `EventDao`:`observeBetween(startMillis: Long, endMillis: Long): Flow<List<EventEntity>>`、`getById(id): EventEntity?`、`upsert(entity)`、`softDelete(id, updatedAtMillis)`、`allOnce(): List<EventEntity>`(备份用,不在本任务实现查询逻辑)
  - Hilt 单例 `LnxDatabase`

- [ ] **Step 1: 加依赖(toml + app gradle)**

toml versions 追加 `room = "2.6.1"`;libraries 追加:

```toml
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
```

plugins 追加后无需(已有 ksp);app dependencies 追加 `implementation(libs.room.runtime)`、`implementation(libs.room.ktx)`、`ksp(libs.room.compiler)`;androidTest 追加 `androidTestImplementation(libs.room.runtime)`、`kspAndroidTest(libs.room.compiler)`。

- [ ] **Step 2: 写失败的 DAO 测试**

`EventDaoTest.kt`(in-memory DB,`runBlocking`):

```kotlin
package com.lnx.app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventDaoTest {
    private lateinit var db: LnxDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), LnxDatabase::class.java
        ).allowMainThreadQueries().build()
    }
    @After fun teardown() { db.close() }

    private fun entity(id: String, start: Long, end: Long) = EventEntity(
        id = id, title = "T$id", allDay = false, startAt = start, endAt = end,
        location = null, notes = null, colorSlot = 0, priority = "P2",
        reminderLeadMinutes = null, ruleType = "NONE", ruleInterval = 1,
        ruleWeekdays = null, ruleMonthlyMode = null, ruleMonthlyDay = null,
        ruleMonthlyNth = null, ruleMonthlyWeekday = null, ruleEndType = null,
        ruleEndDate = null, ruleCount = null,
        createdAt = 0L, updatedAt = 0L, isDeleted = false,
    )

    @Test fun `区间查询命中半开重叠`() = runBlocking {
        db.eventDao().upsert(entity("a", 100L, 200L))
        db.eventDao().upsert(entity("b", 300L, 400L))
        val hit = db.eventDao().observeBetween(150L, 250L).first()
        assertEquals(listOf("a"), hit.map { it.id })
    }

    @Test fun `软删除后不再出现`() = runBlocking {
        db.eventDao().upsert(entity("a", 100L, 200L))
        db.eventDao().softDelete("a", updatedAtMillis = 9L)
        assertEquals(0, db.eventDao().observeBetween(0L, 999L).first().size)
    }
}
```

- [ ] **Step 3: 运行确认失败** `.\gradlew.bat connectedDebugAndroidTest`(编译失败 = 类不存在,即预期的"失败")

- [ ] **Step 4: 实现**

`EventEntity.kt`(spec §4.2/§4.3 字段全集;重复字段 M4 启用):

```kotlin
package com.lnx.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val allDay: Boolean,
    val startAt: Long,          // 普通事件:epoch millis;全天:起始日本地 00:00 的 millis
    val endAt: Long,            // 排他:全天 = 结束日次日 00:00
    val location: String?,
    val notes: String?,
    val colorSlot: Int,         // 0..7(spec §5.4)
    val priority: String,       // P0..P3
    val reminderLeadMinutes: Int?,
    // —— 重复规则(spec §3.7,M4 启用)——
    val ruleType: String,       // NONE / DAILY / WEEKLY / MONTHLY / YEARLY
    val ruleInterval: Int,      // 每 X 天/周/月/年
    val ruleWeekdays: String?,  // WEEKLY 用,csv of ISO 值(1=周一..7=周日)
    val ruleMonthlyMode: String?, // BY_MONTHDAY / BY_NTH_WEEKDAY
    val ruleMonthlyDay: Int?,     // 第 N 天(1..31)
    val ruleMonthlyNth: Int?,     // 第 N 个(1..5)
    val ruleMonthlyWeekday: Int?, // ISO weekday 值
    val ruleEndType: String?,     // NEVER / UNTIL / COUNT
    val ruleEndDate: Long?,       // UNTIL:epochDay
    val ruleCount: Int?,          // COUNT:N 次
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean,
)
```

`EventDao.kt`:

```kotlin
package com.lnx.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lnx.app.core.database.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE isDeleted = 0 AND startAt < :endMillis AND endAt > :startMillis ORDER BY startAt")
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id AND isDeleted = 0")
    suspend fun getById(id: String): EventEntity?

    @Upsert suspend fun upsert(entity: EventEntity)

    @Query("UPDATE events SET isDeleted = 1, updatedAt = :updatedAtMillis WHERE id = :id")
    suspend fun softDelete(id: String, updatedAtMillis: Long)
}
```

`LnxDatabase.kt`:

```kotlin
package com.lnx.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.database.entity.EventEntity

@Database(entities = [EventEntity::class], version = 1, exportSchema = true)
abstract class LnxDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
}
```

`DatabaseModule.kt`:

```kotlin
package com.lnx.app.core.di

import android.content.Context
import androidx.room.Room
import com.lnx.app.core.database.LnxDatabase
import com.lnx.app.core.database.dao.EventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LnxDatabase =
        Room.databaseBuilder(context, LnxDatabase::class.java, "lnx.db").build()

    @Provides fun provideEventDao(db: LnxDatabase): EventDao = db.eventDao()
}
```

- [ ] **Step 5: 运行确认通过** `.\gradlew.bat connectedDebugAndroidTest` → PASS(2 个)

- [ ] **Step 6: 提交** `git add . && git commit -m "feat: Room 数据层(事件表 + DAO + DI,含重复规则字段)"`

---

### Task 2: 领域模型 + 展开器(非重复)+ 仓库

**Files:**
- Create: `app/src/main/java/com/lnx/app/core/domain/model/Event.kt`
- Create: `app/src/main/java/com/lnx/app/core/domain/model/Occurrence.kt`
- Create: `app/src/main/java/com/lnx/app/core/domain/OccurrenceExpander.kt`
- Create: `app/src/main/java/com/lnx/app/core/domain/EventRepository.kt`
- Create: `app/src/main/java/com/lnx/app/core/data/EventRepositoryImpl.kt`
- Modify: `app/src/main/java/com/lnx/app/core/di/DatabaseModule.kt`(绑定仓库)
- Test: `app/src/test/java/com/lnx/app/core/domain/OccurrenceExpanderTest.kt`

**Interfaces:**
- Consumes: `EventDao`(Task 1)
- Produces(后续里程碑全部依赖,签名冻结):
  - `data class Event(id, title, allDay, start: LocalDateTime, end: LocalDateTime, location: String?, notes: String?, colorSlot: Int, priority: Priority, reminderLeadMinutes: Int?, rule: EventRule, createdAt: Long, updatedAt: Long)`;`enum class Priority(val label: String) { P0("P0"), P1("P1"), P2("P2"), P3("P3") }`;`data class EventRule(type: RuleType, interval: Int, weekdays: Set<DayOfWeek>, monthlyMode: MonthlyMode?, monthlyDay: Int?, monthlyNth: Int?, monthlyWeekday: DayOfWeek?, end: RuleEnd)`,`enum class RuleType { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }`,`enum class MonthlyMode { BY_MONTHDAY, BY_NTH_WEEKDAY }`,`sealed interface RuleEnd { object Never; data class Until(val date: LocalDate); data class Count(val times: Int) }`
  - `data class Occurrence(val event: Event, val start: LocalDateTime, val end: LocalDateTime)`
  - `interface OccurrenceExpander { fun expand(events: List<Event>, rangeStart: LocalDateTime, rangeEnd: LocalDateTime): List<Occurrence> }`(半开区间语义:start < rangeEnd && end > rangeStart)
  - `interface EventRepository { fun observeOccurrences(start: LocalDateTime, end: LocalDateTime): Flow<List<Occurrence>>; suspend fun getEvent(id: String): Event?; suspend fun save(event: Event); suspend fun delete(id: String) }`
  - `Event.toEntity() / EventEntity.toDomain()` 映射函数(全天 = 本地 00:00/次日 00:00)

- [ ] **Step 1: 写失败的展开器单测**

```kotlin
package com.lnx.app.core.domain

import com.lnx.app.core.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class OccurrenceExpanderTest {
    private val expander = OccurrenceExpander.Basic()
    private fun timed(id: String, s: String, e: String) = Event(
        id = id, title = id, allDay = false,
        start = LocalDateTime.parse(s), end = LocalDateTime.parse(e),
        location = null, notes = null, colorSlot = 0, priority = Priority.P2,
        reminderLeadMinutes = null,
        rule = EventRule(RuleType.NONE, 1, emptySet(), null, null, null, null, RuleEnd.Never),
        createdAt = 0, updatedAt = 0,
    )
    private val rangeS = LocalDateTime.parse("2026-09-28T00:00")
    private val rangeE = LocalDateTime.parse("2026-10-05T00:00")

    @Test fun `完全落在区间内`() {
        val list = expander.expand(listOf(timed("a", "2026-09-30T09:00", "2026-09-30T10:00")), rangeS, rangeE)
        assertEquals(1, list.size)
    }
    @Test fun `半开区间_贴边不算重叠`() {
        // 事件 09-27 结束于 09-28T00:00 → 与 rangeStart 贴边,不命中
        val list = expander.expand(listOf(timed("a", "2026-09-27T22:00", "2026-09-28T00:00")), rangeS, rangeE)
        assertEquals(0, list.size)
    }
    @Test fun `区间外不命中`() {
        val list = expander.expand(listOf(timed("a", "2026-10-06T09:00", "2026-10-06T10:00")), rangeS, rangeE)
        assertEquals(0, list.size)
    }
    @Test fun `重复事件本里程碑只返回首例`() {
        val daily = timed("a", "2026-09-28T09:00", "2026-09-28T10:00")
            .copy(rule = EventRule(RuleType.DAILY, 1, emptySet(), null, null, null, null, RuleEnd.Never))
        val list = expander.expand(listOf(daily), rangeS, rangeE)
        assertEquals(1, list.size) // M4 替换为完整引擎后改为 7
    }
    @Test fun `全天事件跨多天_区间各命中一天`() {
        val allDay = timed("a", "2026-09-28T00:00", "2026-09-30T00:00")
            .copy(allDay = true)
        val list = expander.expand(listOf(allDay), rangeS, rangeE)
        assertEquals(1, list.size) // 作为整块返回(渲染层负责跨天展示)
    }
}
```

- [ ] **Step 2: 确认失败**(类不存在)

- [ ] **Step 3: 实现**

`Event.kt` / `Occurrence.kt` / `OccurrenceExpander.kt` 按上面的 Interfaces 精确实现(Basic 版:遍历事件,`rule.type == NONE` 且半开重叠 → 返回 `Occurrence(event, event.start, event.end)`;否则只返回首例,带 `// M4: RecurrenceEngine 替换` 注释)。`EventRepositoryImpl` 做 `LocalDateTime ↔ millis` 换算(`ZoneId.systemDefault()`),`save` 时 `updatedAt = System.currentTimeMillis()`。

- [ ] **Step 4: 单测通过** `.\gradlew.bat testDebugUnitTest` → PASS(5 个)

- [ ] **Step 5: 提交** `git commit -am "feat: 领域模型 + 展开器(非重复)+ 事件仓库"`

---

### Task 3: 周视图渲染事件块(并排车道布局)

**Files:**
- Create: `app/src/main/java/com/lnx/app/feature/calendar/week/LaneLayout.kt`
- Modify: `app/src/main/java/com/lnx/app/feature/calendar/week/WeekView.kt`(TimeGrid 增加每列事件块渲染)
- Modify: `app/src/main/java/com/lnx/app/feature/calendar/CalendarViewModel.kt`(注入 `EventRepository`,UiState 增加 `occurrences: List<Occurrence>`;观察当前周范围)
- Test: `app/src/test/java/com/lnx/app/feature/calendar/week/LaneLayoutTest.kt`

**Interfaces:**
- Consumes: `OccurrenceExpander`/`EventRepository`(Task 2)
- Produces: `object LaneLayout { fun assign(spans: List<TimeSpan>): List<LaneSlot> }`,`data class TimeSpan(val id: String, val startMinute: Float, val endMinute: Float)`,`data class LaneSlot(val id: String, val lane: Int, val lanes: Int)`(lane 从 0 起,lanes = 该重叠组总道数;同组内各事件 lanes 相同)

- [ ] **Step 1: 写失败单测**

```kotlin
class LaneLayoutTest {
    @Test fun `不重叠_各自独占且总道数为1`() {
        val r = LaneLayout.assign(listOf(
            TimeSpan("a", 540f, 600f), TimeSpan("b", 660f, 720f)))
        assertEquals(listOf(0, 0), r.map { it.lane })
        assertEquals(listOf(1, 1), r.map { it.lanes })
    }
    @Test fun `两个重叠_各占一半`() {
        val r = LaneLayout.assign(listOf(
            TimeSpan("a", 540f, 660f), TimeSpan("b", 600f, 720f)))
        assertEquals(setOf(0, 1), r.map { it.lane }.toSet())
        assertEquals(listOf(2, 2), r.map { it.lanes })
    }
    @Test fun `三连叠_三条道_首尾两条不占第三道`() {
        val r = LaneLayout.assign(listOf(
            TimeSpan("a", 540f, 660f), TimeSpan("b", 560f, 700f), TimeSpan("c", 680f, 800f)))
        assertEquals(3, r.first { it.id == "b" }.lanes) // b 与 a、c 都叠 → 组内 3 道仅当 c 与 a 也同组
    }
}
```

- [ ] **Step 2: 确认失败 → Step 3: 实现**(贪心:按 start 排序,扫描线维护活跃集;连通分量内 lanes = 分量内峰值道数;无重叠则 1 道)。Step 4: PASS → Step 5: `git commit -m "feat: 事件块并排车道布局算法"`。

- [ ] **Step 6: 周视图渲染接入**:`CalendarViewModel` 观察"当前选中周"的 `observeOccurrences(周一00:00, 下周一00:00)`,按天分组传入 `TimeGrid`;事件块渲染为 `OccurrenceBlock`(色 = `MaterialTheme.colorScheme.primaryContainer`/onPrimaryContainer,M3 换真实色位映射;标题 1 行 + 时间 1 行,圆角随主题);点击块 → `onEventClick(occurrence)`;同时把 spec §3.5 的「点空白 → 新建(30 分钟吸附)」回调接到网格(封装 `snappedSlot(y)` 已在 M1 网格有坐标基础,本任务补 `onEmptyClick(LocalDateTime)`)。UI 测试:预置一条事件后 `week_grid` 内出现标题节点。

- [ ] **Step 7: 全量测试通过 + 提交** `git commit -m "feat: 周视图渲染事件块 + 点空白新建入口"`

---

### Task 4: 新建 / 编辑页

**Files:**
- Create: `app/src/main/java/com/lnx/app/feature/event/EventEditScreen.kt`
- Create: `app/src/main/java/com/lnx/app/feature/event/EventEditViewModel.kt`
- Create: `app/src/main/java/com/lnx/app/feature/event/CreateRequest.kt`
- Modify: `CalendarScreen.kt`(FAB → 编辑页;入口传参)
- Test: `app/src/test/java/com/lnx/app/feature/event/EventDefaultsTest.kt`

**Interfaces:**
- Produces:
  - `data class CreateRequest(val defaultStart: LocalDateTime)`(三个入口统一:周/日空白 = 吸附后的时刻;FAB = spec §3.5 规则「所选日期 + 今天取下一个半点、否则 09:00」;月视图列表空态 = 该日 09:00)
  - `EventEditViewModel(savedStateHandle)`:`init` 读取 `eventId`(编辑)或 `startEpoch`/`allDay`(新建),UiState 含全部字段 + `save()`;默认值:不重复、提醒 = 15(设置项 M6 前先常量)、P2、色位 0、时长 1 小时
  - 编辑页字段顺序 = spec §3.5(标题 → 全天 → 起止 → 地点 → 重复(M4 前只读显示"不重复") → 提醒 → 标签(M3 前占位) → 颜色 → 优先级 → 备注)
- [ ] **Step 1: 失败单测(默认值计算)**:`nextHalfHour(14:23)=14:30`、`(14:45)=15:00`、`非今日取 09:00`、`全天跨天 end=次日00:00`。Step 2: FAIL → Step 3: 实现 `EventDefaults.kt`(纯函数) → Step 4: PASS。
- [ ] **Step 5: 实现 UI**:标题大输入框(自动聚焦)、M3 `TimePickerDialog`/`DatePickerDialog`、全天开关切换日期选择、色板 8 圆点、优先级 4 选段、备注多行;「保存」→ 校验标题非空/结束晚于开始 → `repo.save` → 返回。
- [ ] **Step 6: UI 测试**:打开新建页(带预填参数)→ 输入标题 → 保存 → 周视图出现该事件标题。
- [ ] **Step 7: 提交** `git commit -m "feat: 事件新建/编辑页 + 三入口预填接线"`

---

### Task 5: 详情卡片(可编辑/删除)+ 重叠轻提示

**Files:**
- Modify: `app/src/main/java/com/lnx/app/feature/event/LnxDetailSheet.kt` 的调用方 → 新建 `EventDetailContent.kt`(只读字段展示 + 编辑/删除按钮)
- Modify: `EventEditViewModel.kt`(save 前 `OverlapChecker`)
- Create: `app/src/main/java/com/lnx/app/core/domain/OverlapChecker.kt`
- Test: `app/src/test/java/com/lnx/app/core/domain/OverlapCheckerTest.kt`

**Interfaces:**
- Consumes: Task 2 仓库、Task 3 点击回调、M1 `LnxDetailSheet`
- Produces: `object OverlapChecker { fun findOverlaps(target: Event, others: List<Event>): List<String> }`(返回重叠事件标题;贴边不算);详情卡显示 spec §3.6 全字段;删除 = 确认弹窗 → `repo.delete`

- [ ] **Step 1: 失败单测**(相邻不重叠、包含重叠、全天跨天重叠)→ Step 2 FAIL → Step 3 实现 → Step 4 PASS。
- [ ] **Step 5: UI 接线**:点事件块 → 弹详情;「编辑」→ 编辑页复用;「删除」→ `AlertDialog` 确认 → 删除 → 周视图即时消失(Flow 自动);保存时若有重叠 → `SnackbarHost` 显示 `与"XX"时间重叠`,不阻塞保存(spec §3.5)。
- [ ] **Step 6: UI 测试**:创建→详情可见→删除→周视图空态回归;保存与已有事件重叠时出现提示文本节点。
- [ ] **Step 7: 提交** `git commit -m "feat: 事件详情卡 + 编辑删除 + 时间重叠轻提示"`

---

### Task 6: M2 验收走查

- [ ] **Step 1:** 模拟器安装启动,按 spec §8.1 M2 表逐条走查(① 三入口时间预填正确含吸附 ② 块位置与时间吻合 ③ 重叠提示 ④ 删除确认)。
- [ ] **Step 2:** `.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest` 全绿。
- [ ] **Step 3:** `git tag v0.1.0-m2`。
