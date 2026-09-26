# lnx M5「提醒通知」实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 事件开始前 N 分钟的本地提醒:精确到分钟、点击通知跳详情、免打扰时段静默、重复事件逐次排期、过期不补发、权限拒绝不崩并有引导。

**Architecture:** `NextReminderCalculator`(纯函数:给定事件/例外/区间 → 待提醒列表)做决策;`ReminderScheduler`(AlarmManager `setExactAndAllowWhileIdle`,Android 12+ 无精确权限时降级 `setWindow`)做执行;`AlarmReceiver`(Hilt `@AndroidEntryPoint` BroadcastReceiver)到点发通知;`BOOT_COMPLETED` 重排。数据不加表——调度清单实时从 Room 计算,保存/删除事件后重排。

**Tech Stack:** AlarmManager、NotificationCompat、kotlinx.serialization 不涉及;既有 Hilt/Room。

**Spec:** `D:\11lnx\SPEC.md`(本计划实现 §8.1 M5;行为遵循 §3.8)

## Global Constraints

- 同前;通知渠道 id `lnx_reminders`,IMPORTANCE_DEFAULT(用户在系统里管理响铃/震动)
- 通知文案(spec §3.8):标题 = 事件标题;正文 = `HH:mm 开始`(有地点:`HH:mm 开始 · 地点`)
- 点击通知 → MainActivity 单例跳转,携带 `eventId + occurrenceStart` 打开详情卡
- 免打扰(spec §3.8):时段内通知**照发但静默**(`setSilent`)——策略纯函数 `DndPolicy.isSilent(reminderAt, window)`
- 过期不补发:重排时只调度 `reminderAt > now` 的项;单次重排上限 50 条(防重复海啸)

---

### Task 1: NextReminderCalculator + DndPolicy(纯函数)

**Files:**
- Create: `app/src/main/java/com/lnx/app/core/notification/NextReminderCalculator.kt`
- Create: `app/src/main/java/com/lnx/app/core/notification/DndPolicy.kt`
- Test: `app/src/test/java/com/lnx/app/core/notification/NextReminderCalculatorTest.kt`、`DndPolicyTest.kt`

**Interfaces:**
- Produces:
  - `data class ScheduledReminder(val eventId: String, val occurrenceStart: LocalDateTime, val remindAt: LocalDateTime)`
  - `object NextReminderCalculator { fun calculate(events: List<Event>, exceptions: Map<String, List<EventException>>, rangeStart: LocalDateTime, rangeEnd: LocalDateTime, now: LocalDateTime): List<ScheduledReminder> }`( occurrence 在 range 内 + lead 后仍 > now 才计入;重复事件用引擎逐次展开)
  - `object DndPolicy { fun isSilent(at: LocalDateTime, windowStartMinute: Int, windowEndMinute: Int): Boolean }`(跨午夜窗口正确,如 22:00–08:00)

- [ ] **Step 1: 失败单测**:普通事件 lead 15;lead 后早于 now 的剔除;重复每周 → 每次一条;免打扰 23:30 in 22:00–08:00 = true、12:00 = false、跨午夜边界 08:00 = false(结束排他)。**Step 2 FAIL → Step 3 实现 → Step 4 PASS**
- [ ] **Step 5: 提交** `git commit -m "feat: 提醒时刻计算器 + 免打扰静默策略(纯函数)"`

---

### Task 2: 调度器 + 接收器 + 通知

**Files:**
- Create: `app/src/main/java/com/lnx/app/core/notification/ReminderScheduler.kt`
- Create: `app/src/main/java/com/lnx/app/core/notification/AlarmReceiver.kt`
- Create: `app/src/main/java/com/lnx/app/core/notification/ReminderNotifier.kt`
- Create: `app/src/main/java/com/lnx/app/core/notification/ReminderPlanner.kt`(粘合:读仓库 → Calculator → Scheduler;供 VM/Receiver/Boot 复用)
- Modify: `AndroidManifest.xml`(RECEIVE_BOOT_COMPLETED、SCHEDULE_EXACT_ALARM、POST_NOTIFICATIONS;receiver 声明)
- Modify: `EventRepositoryImpl.save/delete`(成功后触发 `ReminderPlanner.reschedule()`)
- Test: `app/src/androidTest/java/com/lnx/app/core/notification/ReminderSchedulerTest.kt`(Robolectric 不可用时以插桩验证:计划 → adb dumpsys alarm 断言存在)

**Interfaces:**
- Consumes: Task 1、`EventRepository`
- Produces:
  - `class ReminderScheduler @Inject constructor(@ApplicationContext ctx)`: `fun schedule(reminders: List<ScheduledReminder>)`、`fun cancelAll()`(用 `Action` + requestCode = occurrenceStart.toEpochSecond 取模防冲突)
  - `AlarmReceiver`:onReceive → 取参数 → `ReminderNotifier.show(...)` + `ReminderPlanner.reschedule()`(补排下一次)
  - `ReminderPlanner.reschedule(): suspend`(读未来 7 天窗口)

- [ ] **Step 1: 失败插桩测试**(保存 1 分钟后事件 → `dumpsys alarm | grep lnx` 出现精确闹钟)→ **Step 2 FAIL → Step 3 实现 → Step 4 PASS**
- [ ] **Step 5: 提交** `git commit -m "feat: 提醒调度器 + 接收器 + 通知构建"`

---

### Task 3: 权限流 + 点击跳转 + 免打扰接线

**Files:**
- Modify: `MainActivity.kt`(onNewIntent/onCreate 读 `eventId` extra → 弹详情卡;`POST_NOTIFICATIONS` 请求工具)
- Create: `app/src/main/java/com/lnx/app/core/notification/NotificationPermission.kt`
- Modify: `AlarmReceiver.kt`(静默标志来自 DndPolicy + 设置;设置 M6 前用常量关)
- Test: `app/src/androidTest/.../NotificationTapTest.kt`(发射带 extra 的 Intent → 详情卡标题可见)

**Interfaces:**
- Produces: `fun Context.canPostNotifications(): Boolean`(API 33+ 检查)、`fun requestNotifications(activity)`;深链 extra key `EXTRA_EVENT_ID` / `EXTRA_OCCURRENCE_START`

- [ ] **Step 1: 失败 UI 测试(点击通知路径:直接以 Intent 驱动)→ Step 2 FAIL → Step 3 实现 → Step 4 PASS**
- [ ] **Step 5: 提交** `git commit -m "feat: 通知权限流 + 点击跳转详情 + 免打扰接线"`

---

### Task 4: 重复事件逐次排期 + 开机重排

**Files:**
- Create: `app/src/main/java/com/lnx/app/core/notification/BootReceiver.kt`(BOOT_COMPLETED → goAsync + Planner.reschedule)
- Modify: `AndroidManifest.xml`(BootReceiver intent-filter)
- Modify: `ReminderPlanner`(重复事件:为未来 7 天内每次发生单独 `schedule`;其余靠 receiver 链式补排)
- Test: 单测覆盖「链式补排」计算(第 N 次发生后下一次提醒时刻 = 该次 start − lead)

- [ ] **Step 1: 失败单测 → Step 2 FAIL → Step 3 实现 → Step 4 PASS**
- [ ] **Step 5: UI 实测**:建"每天 09:00、提前 15 分钟"重复事件,`adb shell su 0 setprop` 不可靠则改时间到 2 分钟后实测收到;删除事件 → `dumpsys` 无残留闹钟。
- [ ] **Step 6: 提交** `git commit -m "feat: 重复事件逐次排期 + 开机重排"`

---

### Task 5: M5 验收走查

- [ ] spec §8.1 M5 四条:① 提前量准确 ±30 秒(建 2 分钟后事件、lead 15s 不可选——用 lead 1 分钟自建测试事件实测) ② 拒绝通知权限不崩、设置页有引导入口(占位行, M6 接线) ③ 免打扰时段静默(临时把窗口设为"全天"实测通知无声音) ④ 过期不补发(把系统时间回拨场景记录为人工验证项)。
- [ ] 全量测试绿;`git tag v0.1.0-m5`。
