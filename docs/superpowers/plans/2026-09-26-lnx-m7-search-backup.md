# lnx M7「搜索 + 备份 + 打磨」实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搜索页、JSON 导入导出(合并/覆盖)、应用图标与名称、空态/错误打磨、Android 8.0 回归,达成 v0.1 总验收。

**Architecture:** 搜索走 Room `LIKE` 查询 + 重复事件"下次发生"计算(复用 M4 引擎);备份层 `BackupManager`:kotlinx.serialization 定义版本化 schema,导出走 SAF `CreateDocument`、导入 `OpenDocument`,导入前展示摘要并区分合并/覆盖。

**Tech Stack:** kotlinx-serialization-json 1.7.3;activity-compose SAF 契约;既有体系。

**Spec:** `D:\11lnx\SPEC.md`(本计划实现 §8.1 M7 与 §8.2 总验收;备份遵循 §3.13)

## Global Constraints

- 同前;备份 schema 带 `"version": 1`(向后兼容口);导入失败不得改动现有数据(先全量解析校验,再落库)
- 合并 = 按 UUID 去重、冲突保留 `updatedAt` 新者;覆盖 = 清空后导入,需二次警告(spec §3.13)
- 发布前把 DB destructive migration 换成 1→2→3 正式 Migration(M2/M3 欠账)

---

### Task 1: 搜索

**Files:**
- Modify: `EventDao.kt`(`search(query): Flow<List<EventEntity>>`:`title LIKE %q% OR notes LIKE %q% OR location LIKE %q%` AND `isDeleted=0`)
- Create: `app/src/main/java/com/lnx/app/feature/search/SearchScreen.kt` + `SearchViewModel.kt`
- Create: `app/src/main/java/com/lnx/app/core/domain/NextOccurrenceCalculator.kt`(重复事件下次发生)
- Modify: 顶栏搜索图标 → 导航 `search`
- Test: `app/src/test/java/.../NextOccurrenceCalculatorTest.kt`、`app/src/androidTest/.../SearchScreenTest.kt`

**Interfaces:**
- Consumes: M1 `formatTitle`、M4 引擎
- Produces: `fun nextOccurrence(event: Event, after: LocalDateTime): LocalDateTime?`;结果列表条目 = 标题 + 日期时间 + 标签色点;重复条目显示 `下次发生:M月D日`;点结果 → 切到日视图并定位(传 extra 回主屏);空结果 `未找到相关日程`

- [ ] **Step 1: 失败单测(下次发生:普通=自身 start;重复=after 后第一次;结束=Null)→ Step 2 FAIL → Step 3 实现 → Step 4 PASS**
- [ ] **Step 5: UI + UI 测试**(输入"周会"命中;输入乱串显示空态;点结果跳日视图)→ **Step 6: 提交** `git commit -m "feat: 搜索页(标题/备注/地点 + 下次发生 + 跳转定位)"`

---

### Task 2: 备份导出

**Files:**
- Modify: `gradle` 依赖(kotlinx-serialization-json + plugin org.jetbrains.kotlin.plugin.serialization)
- Create: `app/src/main/java/com/lnx/app/feature/backup/BackupModels.kt`(`@Serializable data class BackupFile(version:Int, exportedAt:Long, events:List<EventDto>, exceptions:List<ExceptionDto>, tags:List<TagDto>, eventTags:List<PairDto>)`)
- Create: `app/src/main/java/com/lnx/app/feature/backup/BackupManager.kt`(export:全量读 → JSON 字符串;import 校验入口)
- Modify: 设置页数据组「导出」→ SAF `CreateDocument("application/json")`,文件名 `lnx-backup-YYYYMMDD.json`
- Test: `app/src/test/java/.../BackupRoundTripTest.kt`(事件+标签+例外 → 导出 → 解析回 → 字段一致)

**Interfaces:**
- Produces: `interface BackupManager { suspend fun exportJson(): String; suspend fun parse(json: String): BackupFile; suspend fun import(file: BackupFile, mode: ImportMode, replaceAll: suspend () -> Unit) }`,`enum class ImportMode { MERGE, OVERWRITE }`
- [ ] **Step 1: 失败往返单测 → Step 2 FAIL → Step 3 实现 → Step 4 PASS → Step 5: 提交** `git commit -m "feat: 备份模型 + JSON 导出(SAF)"`

---

### Task 3: 备份导入(合并/覆盖 + 摘要与二次警告)

**Files:**
- Modify: `SettingsScreen.kt` 数据组:导入 → `OpenDocument` → 解析 → 摘要 Dialog(`包含 N 个事件、M 个标签` + 合并/覆盖选择)→ 覆盖需二次确认(`将清空现有 N 条数据,且不可恢复`)
- Modify: `BackupManager.import`:MERGE 按 id 去重保 `updatedAt` 新者;OVERWRITE = 事务内软清空 + 全量插入
- Test: `app/src/test/java/.../BackupMergeTest.kt`(同 id 冲突保新;不同 id 并存;覆盖后仅剩导入集)

**Interfaces:**
- Produces: `suspend fun merge(existing: List<EventEntity>, incoming: List<EventEntity>): List<EventEntity>`(纯函数可测)
- [ ] **Step 1: 失败单测 → Step 2 FAIL → Step 3 实现 → Step 4 PASS**
- [ ] **Step 5: 端到端实测**:导出 → 卸载重装 → 导入(覆盖)→ 数据完整;损坏文件导入 → 报错且数据不变。
- [ ] **Step 6: 提交** `git commit -m "feat: 备份导入(合并/覆盖 + 摘要 + 二次警告)"`

---

### Task 4: 图标 / 名称 / 空态打磨 / DB Migration 正式化

**Files:**
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`(adaptive icon:主题 1 主色底 + 白色 "L" 字标 vector)、`drawable/ic_launcher_foreground.xml`、`values/ic_launcher_background.xml`
- Modify: `AndroidManifest.xml`(roundIcon 同源);strings(显示名 lnx)
- Create: `app/src/main/java/com/lnx/app/core/database/Migrations.kt`(MIGRATION_1_2 建标签两表、MIGRATION_2_3 建例外表;`LnxDatabase` 挂接,移除 destructive)
- Modify: 全局空态/错误文案按 spec §3.14 核对(grep 硬编码中文 = 0,双语齐)
- Test: `app/src/androidTest/.../MigrationTest.kt`(Room MigrationTestHelper,1→3 直通)

- [ ] **Step 1: Migration 失败测试 → Step 2 FAIL → Step 3 实现 → Step 4 PASS**
- [ ] **Step 5: 图标截图确认 + 空态走查 → Step 6: 提交** `git commit -m "feat: 应用图标 + 正式 DB Migration + 空态打磨"`

---

### Task 5: 兼容回归 + v0.1 总验收

**Files:** 无新文件

- [ ] **Step 1: Android 8.0(API 26)模拟器**全流程回归:建"每周重复 + 提醒 + 标签"事件 → 修改其中一次 → 按全部删除,零崩溃;动态取色回退蓝紫(`LnxTheme` 走静态分支)。
- [ ] **Step 2: spec §8.2 总验收清单逐项打勾**(完整剧本零崩溃 / 单测覆盖三核心 / 双模拟器通过 / 4 主题 × 深浅目测)。
- [ ] **Step 3: 全量测试** `.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest` 全绿。
- [ ] **Step 4: 收尾** `git tag v0.1.0` + CHANGELOG.md(v0.1.0 功能清单,中文)。

---

## v0.1 里程碑与计划文件索引

| 里程碑 | 计划文件 | 状态 |
|--------|----------|------|
| M1 骨架 | `2026-09-26-lnx-m1-skeleton.md` | 待执行 |
| M2 事件增删改查 | `2026-09-26-lnx-m2-events.md` | 待执行 |
| M3 全视图 + 标签 | `2026-09-26-lnx-m3-views-tags.md` | 待执行 |
| M4 重复事件 | `2026-09-26-lnx-m4-recurrence.md` | 待执行 |
| M5 提醒 | `2026-09-26-lnx-m5-reminders.md` | 待执行 |
| M6 主题 + 设置 + 引导 | `2026-09-26-lnx-m6-themes-settings.md` | 待执行 |
| M7 搜索 + 备份 + 打磨 | `2026-09-26-lnx-m7-search-backup.md` | 待执行 |

> 执行提示:里程碑必须按序执行(后置计划引用前置的 Interfaces);执行中发现计划与实际冲突时,先更新对应计划文件再继续,保持计划与代码同步。
