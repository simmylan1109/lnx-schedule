# M9 验收证据(按惯例不入库)

**这版装的是签名 release 包**(R8 混淆 + 资源压缩 + 正式密钥),不是调试包。
`app-release.apk` 1.7 MB(debug 包 11.6 MB),`versionCode=1 / versionName=0.1.0`,
API 35 模拟器(test35)。

测试基线:**236 单测 + 155 仪器测试,全绿**(M8 是 232 + 148)。

---

## 一、发布构建本身(最要紧的一组)

| 图 | 说明 |
|---|---|
| `01-release-onboarding.png` | release 包冷启动,三页引导第 1 页 |
| `02-release-theme-pick.png` / `03-release-theme-scroll.png` | 选主题页。**顺带发现**:第 4 个主题(宁静冷色)在首屏被右边缘切掉,往左滑能到 —— 不是死链,但没有任何"还能滑"的提示 |
| `04-release-onboarding-notif.png` → `24-final-perm-dialog.png` | 引导第 3 页点「开启提醒」→ 系统通知权限弹框 |
| `06-release-calendar-serene.png` | 宁静冷色主题:渐变头部、超大日期、周视图,渲染正常 |
| `31-final-calendar.png` | 最终发布包的周视图(空态 + 当前时刻红线) |
| `33-final-editor.png` → `34-final-month-with-event.png` → `36-final-month-final.png` | 建事件 → 保存 → 月视图出现,当天格带事件彩点 |
| `39-final-settings.png` | 设置页四段完整(此前 M6 的"页面透明"问题没有复发) |
| `40-final-search.png` | 搜索命中刚建的事件 |

### R8 没裁坏东西的硬证据

- **导出**(`19-export-share-sheet.png`):`lnx-backup-20260930.json` 生成并拉起系统分享面板
  → Room 查询、kotlinx.serialization 编码、FileProvider 三段在混淆下全部活着。
- **导入**(`20-import-picker.png` → `21-import-summary.png` → `22-import-merged.png` → `23-month-after-import.png`):
  文件选择器里还留着 **M7 时期导出的 521 字节备份**,把它导入新的混淆 release 包,
  弹出"Contains 1 event and 0 tags",合并后在月视图正确显示 ——
  **旧版本导出的文件,新版本读得懂**,这是兼容性最硬的一条。
- **闹钟**:建事件后 `dumpsys alarm` 出现 `RTC_WAKEUP … tag=*walarm*:com.lnx.app.action.REMINDER_CONTINUE`。
- **资源**:`aapt2 dump resources` 里 `ic_notification` / `ic_launcher_*` / `backup_paths` 都在,
  资源压缩没吃掉它们。
- **权限**:通知权限弹框正常、授予成功(`POST_NOTIFICATIONS: granted=true`)。

### 走查时差点误报的一件事(记下来)

引导页点「开启提醒」后没弹权限框,一度怀疑是 R8 把权限申请裁掉了。
对照实验:`10-debug-onboarding-perm.png` / `11-debug-onboarding-perm2.png`(debug 包弹了)
vs `07-perm-dialog-immediately.png`(release 包没弹)—— 最后发现是**自动化的点击坐标偏了**:
按钮中心在 y=1989,而脚本点的 y=2084 落在按钮和「以后再说」之间的空隙。
用解析出的真实坐标重试(`12-release-perm-resolved-coords.png`),弹框正常。
**教训:自动化走查的"没反应"先查坐标,再怀疑代码。**

---

## 二、无障碍(M9 新增)

修掉的是读屏用户**完全用不了**的地方,证据是 uiautomator dump 的前后对比:

- `32-final-editor-a11y-dump.xml`:编辑器里 8 个颜色圆点
  **修复前**是 `class=android.view.View, NAF="true", content-desc=""`(读屏听到三个沉默的"按钮");
  **修复后**是 `class=android.widget.RadioButton, content-desc="Red/Orange/Yellow/Green/Blue/Purple/Pink/Gray", checkable="true", checked="true/false"`
  —— 能念出颜色名、能念出选中状态。
  同一份 dump 里,All-day 行从"文字 + 一个没名字的开关"合并成一个
  `checkable="true"` 且带 "All-day" 文字的节点。
- `37-final-month-a11y-dump.xml`:月历格子朗读
  **修复前**只有孤零零一个数字"30";**修复后**今天这格读
  `Sep 30, Wed,Today,1 event`(完整日期 + 今天标记 + 日程数,单复数正确)。
- `38-final-drawer.png`:抽屉筛选行,修复前末尾的 Checkbox 是独立无标签节点,
  修复后整行是一个 `checkable=true checked=true` 且带 "Untagged" 文字的节点。
- 时间网格事件块读 `Final release walkthrough,10:30 AM – 11:30 AM`(标题 + 完整时间段)。

闸门:`AccessibilityGateTest` 6 条进了仪器测试,以后再出现"可点 + 有面积 + 念不出内容"
的节点,测试自己变红。

---

## 三、终审整改(1 个 P1 + 1 个 P2 + 7 个 P3)

| 级别 | 问题 | 处理 |
|---|---|---|
| P1 | **`docs/RELEASE.md` 把真实签名口令写了进去**,而手册是入库的 —— 等于把"密钥放仓库外 + 凭据 gitignore"那套设计从侧门作废 | 换新密钥(随机 24 位,只落在 keystore.properties);文档里删掉口令,改成"见 keystore.properties";并写明旧口令仍在历史里但已是死口令、以及将来真要清理时怎么办 |
| P2 | keystore.properties 存在但少字段时,`rootProject.file(null)` 抛看不懂的空指针 | 配置期逐字段校验,中文报错点名缺哪一项 |
| P2 | "没验到"清单漏了"系统备份/换机迁移没实测过" | 补上,并给出 `bmgr` 验证命令 |
| P3×7 | 过时引用 `[LnxDatabase.VERSION]`、挂在合并节点上查不到的 testTag、静默吞异常的 `runCatching`、死注释、proguard 注释措辞不准、两个色板缺 `selectableGroup`、月历格子缺 `selected` 语义、示例版本号不自洽、闸门注释漏记"手势点击绕开闸门" | 全部改掉 |

终审独立验证过、**没有**问题的几处(记下来免得重复怀疑):
两份备份规则文件内容一致且合法;`cache` 域系统本来就不备份属实;
`onCheckedChange = null` + 整行 `toggleable` 是官方写法、状态同源,不存在"整行可点但状态不同步";
`MonthCell.eventCount` 取的是真实数量而不是画点用的截断值;
`mergeDescendants = true` 不会丢掉 testTag 和点击行为。

### 换密钥的代价为什么是零

这个 App **还没发给任何人**:仓库没有远端,发布包只在开发用模拟器上装过。
所以换密钥不需要通知任何用户、不需要迁移任何已安装的 App,直接换即可。
旧密钥留档在 `D:\lnx-release\lnx-release-old-unusable.jks`,不删。

**如果这件事发生在已经发过版的阶段,处理方式完全不同**(那意味着已装用户必须卸载重装、
日程数据会丢)。这也是为什么"口令别入库"要从第一次生成密钥就做到,而不是等出事再补。

---

## 四、没验到的(诚实记录)

- **提醒真的弹出通知**这一段没在发布包上验过(要等到第二天才能自然到点)。
  已验到的:闹钟排进系统、权限弹框正常、通知图标资源在包里。
  补验方法见 `docs/RELEASE.md` 第五节。
- **系统备份 / 换机迁移**没实测过:规则合法(被 `lintVitalRelease` 验过),
  但没有真的备份过、也没有恢复过。
- 主题选择页第 4 个主题要往左滑才看得到,没有滑动提示。
