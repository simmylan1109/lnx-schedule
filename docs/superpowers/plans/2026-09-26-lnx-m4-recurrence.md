# lnx M4「重复事件」实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完整重复事件引擎(每天/每周/每月/每年 + 间隔 + 三种结束条件)、规则编辑器、例外表(单次改/删)、以及「仅本次 / 本次及以后 / 全部」三选一语义。

**Architecture:** `RecurrenceEngine` 为纯 JVM 函数(输入规则+系列起点+可见区间,输出发生时刻列表),M2 展开器接口不变、内部接入引擎;`EventExceptionEntity` 存单次例外(取消或覆盖),展开后应用;三选一由 `RecurrenceEditHandler` 落库(写例外 / 改母 / 剪断拆二)。

**Tech Stack:** 既有体系,无新依赖。

**Spec:** `D:\11lnx\SPEC.md`(本计划实现 §8.1 M4;语义遵循 §3.7/§4.4)

## Global Constraints

- 同前;展开语义:**半开区间** [start, end);"N 次"从系列第一次发生计数;例外按"原本发生日"匹配
- 月规则 BY_MONTHDAY 在月长不足时**跳过该月**(如 31 号在 2 月不发生)——此为显式决定
- 每周多选星期几时,系列起点不在所选星期也照常按各星期发生
- 引擎必须纯函数(不依赖系统时间),便于测试与 M5 复用

---

### Task 1: RecurrenceEngine(纯函数)

**Files:**
- Create: `app/src/main/java/com/lnx/app/core/domain/recurrence/RecurrenceEngine.kt`
- Test: `app/src/test/java/com/lnx/app/core/domain/recurrence/RecurrenceEngineTest.kt`(≥20 个用例,下为节选)

**Interfaces:**
- Produces: `object RecurrenceEngine { fun expand(rule: EventRule, seriesStart: LocalDateTime, occurrenceDuration: Duration, rangeStart: LocalDateTime, rangeEnd: LocalDateTime): List<Occurrence> }`
  - 全天事件按 LocalDate 维度展开(以本地 00:00/次日 00:00 的 Occurrence 返回,与 M2 语义一致)

- [ ] **Step 1: 写失败单测(核心用例集)**

```kotlin
class RecurrenceEngineTest {
    // 基准:2026-09-28(周一)09:00-10:00 起;range = [10-01T00:00, 11-01T00:00)
    private val rangeS = LocalDateTime.parse("2026-10-01T00:00")
    private val rangeE = LocalDateTime.parse("2026-11-01T00:00")
    private fun rule(type: RuleType, interval: Int = 1,
                     weekdays: Set<DayOfWeek> = emptySet(),
                     monthlyMode: MonthlyMode? = null, monthlyDay: Int? = null,
                     monthlyNth: Int? = null, monthlyWeekday: DayOfWeek? = null,
                     end: RuleEnd = RuleEnd.Never) =
        EventRule(type, interval, weekdays, monthlyMode, monthlyDay, monthlyNth, monthlyWeekday, end)
    private fun series(start: String, rule: EventRule, dur: Duration = Duration.ofHours(1)) =
        RecurrenceEngine.expand(rule, LocalDateTime.parse(start), dur, rangeS, rangeE)

    @Test fun `每天_31天区间命中31次`() {
        assertEquals(31, series("2026-09-28T09:00", rule(RuleType.DAILY)).size)
    }
    @Test fun `每3天_跳过间隔`() {
        assertEquals(11, series("2026-09-28T09:00", rule(RuleType.DAILY, 3)).size) // 10-01,04,...,10-31
    }
    @Test fun `每周三_区间4个周三`() {
        assertEquals(4, series("2026-09-28T09:00", rule(RuleType.WEEKLY, 1, setOf(DayOfWeek.WEDNESDAY))).size)
    }
    @Test fun `每周_周一和周五双日`() {
        assertEquals(9, series("2026-09-28T09:00", rule(RuleType.WEEKLY, 1, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))).size)
    }
    @Test fun `每2周_只有隔周发生`() {
        // 系列起点周为 9-28(含周三 9-30),隔周为 10-14、10-28
        assertEquals(2, series("2026-09-28T09:00", rule(RuleType.WEEKLY, 2, setOf(DayOfWeek.WEDNESDAY))).size) // 10-14, 10-28
    }
    @Test fun `每月第N天_9月31日不存在则跳过`() {
        val r = series("2026-01-31T09:00", rule(RuleType.MONTHLY, 1, monthlyMode = MonthlyMode.BY_MONTHDAY, monthlyDay = 31))
        assertEquals(listOf(31), r.map { it.start.dayOfMonth }) // 10 月 31 发生;9 月不在 range
    }
    @Test fun `每月第3个周五`() {
        val r = series("2026-09-18T09:00", rule(RuleType.MONTHLY, 1, monthlyMode = MonthlyMode.BY_NTH_WEEKDAY, monthlyNth = 3, monthlyWeekday = DayOfWeek.FRIDAY))
        assertEquals(listOf(16, 20), listOf(r[0].start.dayOfMonth, r[1].start.dayOfMonth)) // 10-16, 11-20
    }
    @Test fun `每年_生日场景`() {
        // 系列起点 10-15,每年一次 → 区间内恰有 2026-10-15
        val r = series("2020-10-15T09:00", rule(RuleType.YEARLY))
        assertEquals(1, r.size)
        assertEquals(2026, r[0].start.year)
        assertEquals(10, r[0].start.monthValue)
    }
    @Test fun `结束条件_UNTIL日期含当天`() {
        // 到 10-03 结束(含 10-03)→ 区间内命中 10-01/10-02/10-03
        val r = series("2026-09-28T09:00", rule(RuleType.DAILY, end = RuleEnd.Until(LocalDate.parse("2026-10-03"))))
        assertEquals(3, r.size)
    }
    @Test fun `结束条件_COUNT_N次后停止`() {
        val engine = RecurrenceEngine
        val r = engine.expand(rule(RuleType.DAILY, end = RuleEnd.Count(5)),
            LocalDateTime.parse("2026-09-28T09:00"), Duration.ofHours(1),
            LocalDateTime.parse("2026-09-28T00:00"), LocalDateTime.parse("2026-10-05T00:00"))
        assertEquals(5, r.size)
    }
    @Test fun `系列起点晚于区间_仅未来次不回填`() {
        val r = series("2026-10-20T09:00", rule(RuleType.DAILY))
        assertEquals(listOf(20, 21), r.take(2).map { it.start.dayOfMonth })
    }
}
```

- [ ] **Step 2: FAIL** → **Step 3: 实现**(逐类型 while 迭代;每次发生构造 Occurrence;UNTIL 含当天、COUNT 计满停;性能上限保护:单次 expand 迭代 ≤ 100,000 步)。**Step 4: PASS(全部用例)** → **Step 5: 提交** `git commit -m "feat: 重复事件展开引擎(四种规则 + 三种结束条件)"`

---

### Task 2: 例外表 + 展开应用

**Files:**
- Create: `app/src/main/java/com/lnx/app/core/database/entity/EventExceptionEntity.kt`(spec §4.3:`id, masterEventId, originalDate(epochDay), isCancelled, overrideEventJson(String, kotlinx.serialization 序列化的 Event)`)
- Create: `app/src/main/java/com/lnx/app/core/database/dao/EventExceptionDao.kt`(`byMaster(masterId)`, `upsert`, `deleteForMaster(masterId)`)
- Modify: `LnxDatabase.kt` version 3;`OccurrenceExpander` 实现接入引擎与例外;`EventRepository` 增加 `upsertException(...)` / `cancelOccurrence(...)`
- Test: `app/src/test/java/com/lnx/app/core/domain/ExceptionApplyTest.kt` + `app/src/androidTest/java/com/lnx/app/core/database/EventExceptionDaoTest.kt`

**Interfaces:**
- Produces: `data class EventException(val masterId: String, val originalDate: LocalDate, val cancelled: Boolean, val override: Event?)`;展开语义:命中 originalDate 的例外 → cancelled 则不出现在结果,否则以 override 的 start/end/title 等替换

- [ ] **Step 1: 失败单测**(取消 10-07 那次 → 结果无 10-07;改 10-07 为 14:00 → 该次 14:00 其余 09:00)
- [ ] **Step 2 FAIL → Step 3 实现(例外加载按 masterId 批量;JSON 用 kotlinx.serialization `Event` 可序列化 DTO)→ Step 4 PASS**
- [ ] **Step 5: 提交** `git commit -m "feat: 例外表 + 展开应用(单次取消/单次覆盖)"`

---

### Task 3: 规则编辑器 UI + 中文描述

**Files:**
- Create: `app/src/main/java/com/lnx/app/feature/event/RuleEditorSection.kt`
- Create: `app/src/main/java/com/lnx/app/core/domain/recurrence/RuleDescription.kt`
- Modify: `EventEditScreen.kt`(接入;替换 M2 的只读"不重复")
- Test: `app/src/test/java/com/lnx/app/core/domain/recurrence/RuleDescriptionTest.kt`

**Interfaces:**
- Produces: `fun ruleDescription(rule: EventRule): String` → 例:`每周三重复,永不结束`、`每 2 周的 周一、周五 重复,重复 5 次后结束`、`每月第 3 个周五重复,到 2027 年 1 月 1 日结束`;编辑器:类型单选(不重复/每天/每周/每月/每年)→ 参数(间隔数字、星期几多选 chip、月模式二选一)→ 结束条件(永不/日期/N 次)

- [ ] **Step 1: 失败单测(≥6 种描述文案)→ Step 2 FAIL → Step 3 实现 → Step 4 PASS**
- [ ] **Step 5: UI + UI 测试**(选"每周三"保存 → 周视图连续两周都出现;详情卡显示描述文案)
- [ ] **Step 6: 提交** `git commit -m "feat: 重复规则编辑器 + 中文规则描述"`

---

### Task 4: 三选一编辑/删除语义

**Files:**
- Create: `app/src/main/java/com/lnx/app/core/domain/recurrence/RecurrenceEditHandler.kt`
- Modify: `EventDetailContent.kt`(重复事件删除/编辑时弹三选一 `AlertDialog`)
- Modify: `EventEditViewModel.kt`(保存时若是重复实例的"改这次"→写例外)
- Test: `app/src/test/java/.../RecurrenceEditHandlerTest.kt`(用 in-memory 仓库假件验证落库结果)

**Interfaces:**
- Consumes: `EventRepository`(M2)、`EventExceptionDao`(Task 2)
- Produces: `enum class EditScope { THIS_ONLY, THIS_AND_FUTURE, ALL }`;`suspend fun applyEdit(master: Event, originalDate: LocalDate, edited: Event?, scope: EditScope)`(edited=null 表示删除):
  - `THIS_ONLY`:edited=null → 写 cancelled 例外;否则写 override 例外
  - `ALL`:更新/软删母事件(+ 清理其例外仅在删除时)
  - `THIS_AND_FUTURE`:母事件 `RuleEnd.Until(originalDate - 1天)`(原结束条件取更近者),新建母事件(新 UUID)以 originalDate 为系列起点、字段取 edited
- [ ] **Step 1: 失败单测**(三 scope × 改/删 = 6 断言:母事件条数、例外条数、新母起点)→ **Step 2 FAIL → Step 3 实现 → Step 4 PASS**
- [ ] **Step 5: UI 接线 + UI 测试**(删"本次"后该日消失、其余周仍在;"本次及以后"改时间 → 下一周起全变)
- [ ] **Step 6: 提交** `git commit -m "feat: 重复事件三选一编辑/删除语义"`

---

### Task 5: M4 验收走查

- [ ] spec §8.1 M4 四条:① 每周三事件连续翻 8 周都在 ② 改单次只影响该次 ③ "本次及以后"正确剪断 ④ N 次结束准确。
- [ ] 全量测试绿;`git tag v0.1.0-m4`。
