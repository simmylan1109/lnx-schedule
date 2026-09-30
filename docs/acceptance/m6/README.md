# M6 验收走查证据 — 主题与设置

环境:Android 模拟器 `test35`(API 35),`app-debug.apk`(versionName 0.1.0)。
截图均为**实机截屏**(`adb exec-out screencap -p`),未经裁剪。

模拟器系统语言是英文,所以"跟随系统"下界面为英文 —— 这本身也是 §10 双语的证据。

| 截图 | 验收条目(spec) | 说明 |
|------|-----------------|------|
| `01-onboarding-welcome-en.png` | §3.12 ① 欢迎 | lnx 标识 + 「轻快、好看的日程管理」+「开始」。此张为英文文案(系统语言英文) |
| `02-onboarding-theme-en.png` | §3.12 ② 选主题 | 4 张主题缩略卡,可点选;「跳过」 |
| `03-onboarding-permission-en.png` | §3.12 ③ 通知权限 | 说明 + 「开启提醒」/「以后再说」 |
| `04-calendar-en.png` | §3.12 收尾 | 走完引导直接进日历;`onboarding_done=true` 落盘(`/data/data/com.lnx.app/files/datastore/lnx_settings.preferences_pb` 可见),重开不再弹 |
| `05-settings-en.png` | §3.11 设置页 | 四组齐全(外观/提醒/通用/数据),英文呈现 |
| `06-settings-zh.png` | §3.11 通用② 语言 | 在设置里点「中文」后**即时**变为中文(同一页面,无需手写重启) |
| `07-appearance-mode-dark.png` | §3.11 外观② 外观模式 | 点「深色」后整页转深色 |
| `08-theme-warm-dark.png` | §3.11 外观① 主题 + §5.3 动效 | 点「暖橙活力」卡;深色方案下生效,过渡约 250ms |
| `09-general-settings-zh.png` / `09-general-settings-en.png` | §3.11 通用① 周起始日、② 语言 | 周起始日(周一/周日)、语言三选(中文 / English / 跟随系统)中英对照 |
| `10-week-start-sunday-zh.png` | §3.11 通用① 周起始日 | 设为「周日」后周视图表头首位为**周日**(UI dump 实读:`周日 周一 周二 周三 周四 周五 周六`) |
| `11-settings-data-version-zh.png` | §3.11 数据③ 版本信息 | 「版本 0.1.0」;导出/导入为 M7 占位(§3.13) |

## 本里程碑被抓出并修掉的缺口(T7 走查 + 终审)

1. **周起始日设置从未被视图消费**(走查发现):设置能存能读、页面能点,但周/月视图永远从周一开始。修复 `0ec3d1f`,并补 `WeekStartSettingTest` 端到端 + `WeekLogicTest`/`MonthGridTest` 单测。
2. **语言设置冷启动不生效**(终审 P1):界面语言在 `attachBaseContext` 就定了,而读盘原本发生在更晚的 `onCreate`,导致"上次设成 English"的用户重开 App 时界面文案是系统语言、日期却已是英文。修复:改为 `attachBaseContext` 里懒加载一次(`LocaleContext.ensureLoaded`),`LanguagePersistenceTest` 做了红→绿验证。
3. **验收项之外的问题**一并修:深色系统下主题卡预览取浅色、英文界面月视图议程仍 24 小时制、英文序数 `3 th`、通知权限行从系统设置返回后不刷新。

## 未截图的验收项及理由

- **默认提前量 / 免打扰开关与时段 / 通知权限行**:在 `05`/`06` 两张设置页截图里同框可见,且由单测(`SettingsRepositoryTest`、`DndPolicyTest`)与端到端(`AlarmReceiverTest.免打扰开着时到点通知被标静音_关掉后不静音`、`SettingsScreenTest.改了默认提醒后新建日程的提醒行跟着变`)覆盖。
- **导出 / 导入**:spec §3.13 的功能在 M7 才开放,M6 只做禁用占位(见 `11` 中「M7 开放」)。
