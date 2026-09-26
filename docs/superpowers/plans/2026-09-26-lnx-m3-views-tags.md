# lnx M3「全视图 + 标签」实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐日视图与月视图(上日历下列表)、标签体系(自建/多选/筛选/未分类)、事件色位与优先级的真实视觉、全天/跨天事件的全天条渲染。

**Architecture:** 复用 M1 的 TimeGrid(日视图 = 单列时间轴 + 日期条);月视图为独立 `MonthView`(上半 6×7 网格 + 下半当日列表);标签数据层进 Room(TagEntity + 关联表,同一 LnxDatabase version 2,`fallbackToDestructiveMigration` 仅限 v0.1 开发期);筛选状态为进程内单例 `TagFilterState`(spec §3.10:重启恢复全选)。

**Tech Stack:** 既有体系,无新依赖。

**Spec:** `D:\11lnx\SPEC.md`(本计划实现 §8.1 M3;交互遵循 §3.3/§3.4/§3.10)

## Global Constraints

- 同 M1/M2;数据库 version 升 2,开发期允许 destructive migration,发布前改为正式 Migration(记入 M7 打磨)
- 月格每日期最多显示 3 个彩点(spec §3.4);今天强调圈
- 抽屉筛选勾掉标签即隐藏;未打标签事件归「未分类」行
- 标签色取事件色板同 8 色位(spec §3.10)

---

### Task 1: 标签数据层

**Files:**
- Create: `app/src/main/java/com/lnx/app/core/database/entity/TagEntity.kt`
- Create: `app/src/main/java/com/lnx/app/core/database/dao/TagDao.kt`
- Modify: `LnxDatabase.kt`(version 2 + 两张新表)
- Create: `app/src/main/java/com/lnx/app/core/domain/model/Tag.kt`(`data class Tag(id, name, colorSlot)`)
- Create: `app/src/main/java/com/lnx/app/core/domain/TagRepository.kt` + `core/data/TagRepositoryImpl.kt`
- Modify: `EventEntity.kt` 增加标签无关(关联走第三表);`DatabaseModule` 绑定
- Test: `app/src/androidTest/java/com/lnx/app/core/database/TagDaoTest.kt`

**Interfaces:**
- Produces:
  - `TagEntity(id PK, name UNIQUE, colorSlot, createdAt, updatedAt, isDeleted)`
  - `EventTagCrossRef(eventId, tagId)`(复合主键)
  - `TagDao`:`observeAll(): Flow<List<TagEntity>>`、`upsert`、`softDelete(id)`、`setEventTags(eventId, tagIds: List<String>)`(事务:清后插)、`tagsOfEvent(eventId)`、`observeEventTagPairs(): Flow<List<EventTagCrossRef>>`
  - `TagRepository`:`observeTags(): Flow<List<Tag>>`、`createTag(name, colorSlot): Tag`、`renameTag`、`deleteTag(id)`、`setEventTags(eventId, ids)`、`observeTagsOfEvent(eventId): Flow<List<Tag>>`

- [ ] **Step 1: 失败 DAO 测试**(建标签→关联事件→查事件标签→删标签后关联消失、事件仍在)
- [ ] **Step 2: FAIL 确认 → Step 3: 实现(建表 SQL 由 Room 生成;LnxDatabase version = 2)→ Step 4: PASS**
- [ ] **Step 5: 提交** `git commit -m "feat: 标签数据层(标签表 + 事件关联表 + 仓库)"`

---

### Task 2: 事件色位映射 + 优先级视觉

**Files:**
- Create: `app/src/main/java/com/lnx/app/core/designsystem/EventColors.kt`
- Modify: `WeekView.kt` 的 `OccurrenceBlock`(用真实色位色 + P0/P1 优先级色点)
- Test: `app/src/test/java/com/lnx/app/core/designsystem/EventColorsTest.kt`

**Interfaces:**
- Produces: `@Composable fun eventColor(slot: Int): Color` 与 `@Composable fun onEventColor(slot: Int): Color` —— 内部按 `ThemeSlot` 当前值映射 spec §5.4 色表(M1 只实现主题 1,其余回退);`@Composable fun priorityColor(priority: Priority): Color`(P0 红/P1 橙/P2 蓝/P3 灰)

- [ ] **Step 1: 失败单测**(slot 0..7 返回值 = spec §5.4 主题 1 列色值;slot 越界回退灰色)→ Step 2 FAIL → Step 3 实现(色值直接抄 spec 表)→ Step 4 PASS。
- [ ] **Step 5: 提交** `git commit -m "feat: 事件色位/优先级主题色映射"`

---

### Task 3: 全天 / 跨天渲染 + 编辑器标签选择

**Files:**
- Modify: `WeekView.kt` 的 `AllDayStrip`(真实渲染:按天列排布全天/跨天块,跨天块横向连续延伸;超过 1 行折叠 `+N`)
- Modify: `EventEditScreen.kt` + 新 `TagPickerSection.kt`(多选 chips + 「新建标签」对话框:名称 + 8 色位选择)
- Test: `app/src/test/java/com/lnx/app/feature/event/AllDaySpanTest.kt`(跨天块在 9-28~10-01 的列跨度计算)

**Interfaces:**
- Produces: `object AllDaySpan { fun layout(occurrences: List<Occurrence>, weekStart: LocalDate): List<AllDayBar> }`(`AllDayBar(occurrence, startCol, endCol, row)`;跨天裁剪到周内)

- [ ] **Step 1: 失败单测 → Step 2 FAIL → Step 3 实现 → Step 4 PASS**
- [ ] **Step 5: UI 接线 + UI 测试**(编辑器选 2 个标签保存 → 详情卡显示 2 个色点 chips;周视图建跨天全天事件 → 横条连续)
- [ ] **Step 6: 提交** `git commit -m "feat: 全天跨天渲染 + 标签选择器"`

---

### Task 4: 日视图

**Files:**
- Create: `app/src/main/java/com/lnx/app/feature/calendar/day/DayView.kt`(日期条 `DayStrip` + 复用 `TimeGrid` 单列化)
- Modify: `CalendarScreen.kt`(DAY 分支接 DayView)
- Test: `app/src/androidTest/java/com/lnx/app/feature/calendar/DayViewTest.kt`

**Interfaces:**
- Produces: `@Composable fun DayView(state, today, onSelectDate, onEventClick, onEmptyClick, modifier)`;日期条可点选、可横滑;左右滑切换前后一天(spec §3.3)
- [ ] **Step 1: 失败 UI 测试**(切到日 Tab → 日期条今天高亮;`performTouchInput{swipeLeft()}` 后标题 +1 天)→ Step 2 FAIL → Step 3 实现(将 `TimeGrid` 泛化为 `dayCount: Int`,周=7/日=1,零新算法)→ Step 4 PASS。
- [ ] **Step 5: 提交** `git commit -m "feat: 日视图(日期条 + 单列时间轴)"`

---

### Task 5: 月视图

**Files:**
- Create: `app/src/main/java/com/lnx/app/feature/calendar/month/MonthView.kt`(MonthGrid + DayAgendaList)
- Create: `app/src/main/java/com/lnx/app/feature/calendar/month/MonthCell.kt`
- Modify: `CalendarScreen.kt`(MONTH 分支接 MonthView;ViewModel 观察当月 occurrences + 选中日列表)
- Test: `app/src/test/java/com/lnx/app/feature/calendar/month/MonthGridTest.kt`

**Interfaces:**
- Consumes: `weekStartOf`/`formatTitle`(M1)、`EventRepository`
- Produces: `fun monthCells(month: YearMonth, weekStart: DayOfWeek = MONDAY): List<LocalDate>`(6 行 × 7 列,含前后月补位);`@Composable MonthView(state, occurrences, onSelectDate, onEventClick, onCreateAt, modifier)`;列表空态 = `X月X日 · 无日程` + `＋ 新建日程`(spec §3.4)

- [ ] **Step 1: 失败单测**(2026 年 9 月:9-01 是周二 → 首格 8-31;总 42 格;10 月含 5 周时补位正确)→ Step 2 FAIL → Step 3 实现 → Step 4 PASS。
- [ ] **Step 5: UI 接线**:点日期 → 下半列表切换;点列表项 → 详情;左右滑切月;今天回当月;日期下彩点(色位色,≤3)。
- [ ] **Step 6: 失败 UI 测试 → 通过**(切月 Tab → 网格出现今天号;点任一日期 → 下方出现该日列表/空态文案)。
- [ ] **Step 7: 提交** `git commit -m "feat: 月视图(6x7 网格 + 当日列表联动 + 彩点)"`

---

### Task 6: 抽屉 + 标签筛选

**Files:**
- Create: `app/src/main/java/com/lnx/app/feature/calendar/drawer/CalendarDrawer.kt`
- Create: `app/src/main/java/com/lnx/app/core/domain/TagFilterState.kt`(`@Singleton class TagFilterState { val hiddenTagIds: StateFlow<Set<String>>; val hideUntagged: StateFlow<Boolean>; fun toggle(tagId); fun toggleUntagged(); fun reset() }`,DI 提供)
- Modify: `CalendarViewModel.kt`(combine 仓库流 + 筛选流 → 过滤 occurrences)
- Modify: `CalendarScreen.kt`(顶栏菜单 → `ModalNavigationDrawer`)
- Test: `app/src/test/java/com/lnx/app/core/domain/TagFilterLogicTest.kt`

**Interfaces:**
- Produces: 过滤纯函数 `fun applyTagFilter(occurrences: List<Occurrence>, eventTags: Map<String, List<String>>, hidden: Set<String>, hideUntagged: Boolean): List<Occurrence>`;抽屉 = 标签行(色点+名+勾选)+「未分类」行 + 底部「设置」入口(M6 接线,先禁用态)

- [ ] **Step 1: 失败单测**(隐藏 A → 含 A 的隐藏;全不选=全显;未分类行控制无标签事件)→ Step 2 FAIL → Step 3 实现 → Step 4 PASS。
- [ ] **Step 5: UI 接线 + UI 测试**(勾掉"工作" → 含该标签事件从周视图消失;重启 App → 全选恢复)。
- [ ] **Step 6: 提交** `git commit -m "feat: 抽屉标签筛选(含未分类)+ 会话内筛选状态"`

---

### Task 7: M3 验收走查

- [ ] 按 spec §8.1 M3 表走查:① 三视图数据一致(同一事件三处可见) ② 月视图点日期列表联动 ③ 抽屉勾选即隐藏 ④ 跨天事件多天显示。
- [ ] 全量测试绿;`git tag v0.1.0-m3`。
