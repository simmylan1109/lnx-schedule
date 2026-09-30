# M8 验收走查

设备:test35 模拟器,系统语言 English。App 语言跟随系统。

本里程碑做三件事:**Room 正式迁移**(v0.1 最大的欠账)、**App 启动图标**、**M7 终审的三个遗留 P2**。

## Room 正式迁移(spec §4 的数据安全底线)

之前 `DatabaseModule` 挂的是 `fallbackToDestructiveMigration()` —— 版本一升,用户的日程全没。
M8 改成 `addMigrations(*LnxMigrations.ALL)`,并**刻意不挂任何 destructive 兜底**:
漏了某条迁移路径宁可当场抛异常,也不能悄悄清库。

两条迁移的 SQL 是从 `app/schemas/.../LnxDatabase/{2,3}.json` 的 `createSql` **抄**的,不是手写的:

| 迁移 | 内容 |
|------|------|
| 1 → 2 | 加 `tags` 表(含 `name` 唯一索引)+ `event_tag_cross_ref` 表 |
| 2 → 3 | 加 `event_exceptions` 表(含 `masterEventId` 索引与 `(masterEventId, originalDate)` 唯一索引) |

都是**纯加表**,没有列变更,所以不需要"建新表→拷数据→删旧表"那套,风险最低。

### 证据 A:`MigrationTest` 4 条(真库)

`MigrationTestHelper` 按 `app/schemas` 建出真实的 v1 / v2 库,跑真迁移,然后:

1. Room 自己校验迁移后的 schema 与 json 是否一致(**这是它最大的价值**);
2. 每步都插了真实数据再迁,验"数据还在"——迁移最容易出的事故不是崩,是悄悄丢数据。

**红检验证**:故意删掉 `index_tags_name` 那条建索引语句,3 条用例立刻转红,
报 `IllegalStateException: Migration didn't properly handle: tags`,并指名是哪张表。改回后全绿。

### 证据 B:实机真升级(截图 `18-upgrade-data-survived.png`)

仪器测试再全,也不能替代"用户的库被打开"这一下。所以实机做了一遍:

1. 往当前库里插一条事件 `Upgrade survivor`(标题里埋了 `Upgrade` 方便识别);
2. 用 `run-as` + 设备上的 `sqlite3` 把库**改回 v1 的样子**:删掉三张新表、`user_version` 改成 1;
3. 冷启动 App;
4. 结果:`user_version` 自动回到 3,三张表重建,**`Upgrade survivor` 这条数据还在**,
   日历上能看到它 —— 迁移没有清库。

## App 启动图标

之前没有 mipmap,桌面上是系统默认图标。M8 加了自适应图标(渐变底 + 描边式日历前景),
`minSdk 26` 正好是自适应图标的起点,不需要 png 兜底。

| 截图 | 说明 |
|------|------|
| `lnx-icon-square.png` | 方形遮罩预览(带 66 直径安全区辅助圈) |
| `lnx-icon-circle.png` | 圆形遮罩预览 —— 启动器实际用的形状 |

**为什么截图不是"桌面截图"**:启动器会缓存图标(重装后还是老图),而且 Android 13+ 的
主题化图标会拿 monochrome 层重新着色,桌面上根本看不到真实配色。`LauncherIconRenderTest`
按系统的合成规则(108 图层里中间 72 映射到可见区)自己渲染成 png,取的是 Android 真正画出来的像素。

**造型试了三版才定稿**(前两版渲染出来才发现的问题已写进 drawable 的注释里):

1. 实心方块 + 挖一道横槽 → 缩到桌面尺寸读成**面包箱/带盖的盒子**;
2. 加长装订钉、日期点画成白色 → 白色叠白色,**圆点完全看不见**;
3. 描边方框 + 装订线 + 三个日期点 → 一眼是日历(定稿)。

## M7 终审遗留的三个 P2

| 改动 | 说明 |
|------|------|
| 跳转后自动滚到高亮位置 | 时间轴默认锚"当前时刻",搜索跳到 9 点的事件而当前是晚上时,高亮在屏幕外。补一条 `LaunchedEffect` 滚过去 |
| 导入失败区分"读不出文件"与"写库失败" | 原来一律报"文件读写失败",数据库约束失败也被说成文件问题,排查会往错的方向找 |
| `SearchScreenTest` 改用 testTag | 原来按中文 contentDescription 定位,只靠 `HiltTestRunner` 钉死进程语言才成立,换台英文模拟器就整类挂 |

滚动那条自己踩了个坑并记在代码里:`LaunchedEffect` 的 key 里**必须带 `scrollState.maxValue`**,
首帧还没测量出内容高度时它会直接 return,不带这个 key 就永远不会为"布局完成"重跑,滚动静默失效。

**红检验证**:把滚动 effect 停掉,`跳转后自动滚到那条事件的位置` 立刻转红
(`The component is not displayed!`)。用 `assertIsDisplayed` 而不是 `assertExists` ——
屏外的块也 `exists`。

## 终审整改

独立终审子代理给出 2 个 P1、5 个 P2,全部修掉:

| # | 问题 | 修法 |
|---|------|------|
| P1-1 | 导出把"读库失败"和"写文件失败"合成一个错误码,而文案又被我改窄成"读不出这个文件" —— 库坏了会误导用户 | 导出的读库与写文件**分开兜底**,库的问题报 `DATABASE` |
| P1-2 | 仪器测试都被 `TestDatabaseModule` 顶着用内存库,**`.addMigrations(...)` 那行删了也不会有任何用例变红** | 加一条"走真实 DI 打开 v1 落盘库"的用例 |
| P2-1 | `BackupError.DATABASE` 零覆盖 | 新增 `BackupErrorClassificationTest` 8 条 |
| P2-2 | 两条滚动 effect 都触发,用户会看到先瞬移到当前时刻再动画过去 | 有高亮时"滚到当前时刻"主动让位 |
| P2-3 | 圆形预览画成了 72dp 直径,系统真正保证可见的是 66dp,同一份证据两把尺子 | 两张统一用 66dp |
| P2-5 | 注释说关联表"有外键指向 events/tags",实际根本没声明外键 | 改对注释,并写明清空顺序靠约定 |

**P1-2 的红检验证**:把 `.addMigrations(*LnxMigrations.ALL)` 换成空数组,
`MigrationTest` 里只有 `DI里的数据库能从v1升上来且不清库` 这一条转红
(`A migration from 1 to 3 was required but not found`),另外 4 条全绿 ——
证明这条确实是那行接线唯一的闸门,其他测试守不住它。

`BackupErrorClassificationTest` 放在**仪器测试**而不是 JVM 单测:`Uri` 是 Android 类型,
JVM 单测里 `Uri.parse` 是没实现的桩(抛 "not mocked")。为它给整个 JVM 套件开
`isReturnDefaultValues` 不划算 —— 那个开关会让别的用例里"忘了 mock 的 Android 调用"
悄悄返回默认值,等于把真错误藏起来。

## 测试基线

232 单测 + 148 仪器测试,全绿(源码 `@Test` 380 = 232 + 148,三方一致)。
