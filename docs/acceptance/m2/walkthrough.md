# M2 验收走查记录（spec §8.1 M2）

- 日期：2026-09-28
- 设备：emulator-5554（AVD test35，API 35，1080×2340，动画已关）
- 构建：main @ statusBarsPadding 修复后的 debug APK
- 自动化测试：`testDebugUnitTest` + `connectedDebugAndroidTest` 全绿（76 例，见 `.superpowers/sdd/2026-09-26-lnx-m2-events/t6-full1.log`）

## 逐条验收（全过）

| # | 验收标准 | 结果 | 证据 | 观察值 |
|---|----------|------|------|--------|
| ① | 三入口时间预填正确（含 30 分钟吸附） | ✅ | 02-fab-prefill.png、04-tap-empty-snap.png | FAB：时钟 11:44 → 预填 12:00（严格下一个半点）、结束 13:00；点空白：点周三 13:39 → 预填 13:30（所在半点格开头，向下取整）、日期列正确。月视图「＋」入口属 M3，不在本里程碑范围。另验证了第 4 条路径：详情卡「编辑」载入既有事件（EventDetailFlowTest） |
| ② | 事件块位置与时间吻合 | ✅ | 03-block-position.png、05-overlap-snackbar.png | 事件 A（12:00–13:00）块上沿与 12:00 刻度线齐平、下沿到 13:00；2 个重叠事件并排 2 车道、3 个重叠（A/B/C）按峰值并发分 3 车道 |
| ③ | 重叠轻提示 | ✅ | 05-overlap-snackbar.png | 保存与 A/B/C 重叠的事件 E 后，Snackbar 显示 `与"A"时间重叠`，保存未被阻止（E 出现在周视图） |
| ④ | 删除有确认 | ✅ | 06-detail-sheet.png、07-delete-confirm.png、08-after-delete.png | 点块 → 底部详情卡（标题/时间/重复/优先级 + 编辑/删除）→ 删除 → 「确定删除「A」吗?」确认弹窗 → 确认后块即时消失、剩余事件重排；空态文案回归（09-cleanup-empty.png） |

## 详情卡字段核对（spec §3.6）

标题、时间（定时同日 `9月28日 周一 12:00 – 13:00`）、重复（不重复）、优先级（P2）均显示；地点/备注为空时不显示行。标签行属 M3（数据模型 M2 尚无标签）。重复事件三选一删除弹窗属 M4。

## 走查发现并已修复

1. **编辑页顶栏未做 statusBarsPadding（major，真 bug）**：取消/保存画进状态栏区域；本机 AVD 状态栏高 136px 且为 tappableElement，顶部 0–136px 的点击进不了应用，「保存」连点无响应（`dumpsys window displays` 查证）。M1 的 CalendarTopBar 有 statusBarsPadding，编辑页漏了。已修，修复后重新走查通过。仪器测试发现不了此类遮挡（compose 点击不经过系统窗口）——此 bug 由走查抓到，验证了走查环节的价值。
2. 观察项（不改代码）：点空白偶发一次无响应（11:46，第二次点击即正常，未能复现；疑与模拟器 2fps 软渲染下编辑页关闭动画未完成有关）；FAB 底缘与 taskbar 重叠约 22px（点击不受影响，记 M3 打磨）。

## 走查后清理

走查产生的 A/B/C/E 五个事件已通过 UI 全部删除（顺带复验删除链路 4 次），模拟器恢复空态。
