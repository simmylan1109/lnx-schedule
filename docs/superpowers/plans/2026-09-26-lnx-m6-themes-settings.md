# lnx M6「主题系统 + 设置 + 引导」实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 4 套主题全部落地并可在设置页即时切换(约 250ms 过渡)、外观浅/深/跟随系统、设置页 4 组齐全、3 页首启引导、中英双语。

**Architecture:** `SettingsRepository`(DataStore Preferences)为唯一偏好源;`MainActivity.setContent` 收集设置流后传给 `LnxTheme`,主题切换用 `Crossfade(250ms)`;三套非动态主题以静态 `ColorScheme`(spec §5.2/§5.4 色值)实现;引导完成标记存 DataStore。

**Tech Stack:** androidx.datastore:datastore-preferences:1.1.1;既有体系。

**Spec:** `D:\11lnx\SPEC.md`(本计划实现 §8.1 M6;色值遵循 §5.2–§5.4;设置与引导遵循 §3.11/§3.12)

## Global Constraints

- 同前;DataStore key 命名蛇形(`theme_slot` 等);**不得**出现设置默认值双源——常量集中在 `SettingsDefaults`
- 每套主题深色版独立设计(spec §5.3),文字对比度 AA;主题 4 动效放慢至 400–600ms(动效常量集中在 `LnxMotion`)
- 全部用户可见文案抽到 strings.xml;默认 zh,values-en 提供英文(spec §2.1)
- M5 的提醒默认值/免打扰从常量切换为读设置

---

### Task 1: SettingsRepository(DataStore)

**Files:**
- Modify: `gradle/libs.versions.toml` + `app/build.gradle.kts`(datastore-preferences 1.1.1)
- Create: `app/src/main/java/com/lnx/app/core/settings/SettingsDefaults.kt`
- Create: `app/src/main/java/com/lnx/app/core/settings/SettingsRepository.kt` + `core/data/SettingsRepositoryImpl.kt`
- Modify: `DatabaseModule.kt`(或新 `SettingsModule`)绑定
- Test: `app/src/test/java/.../SettingsRepositoryTest.kt`(用 DataStore 测试件 `PreferenceDataStoreFactory` + tmp 文件;或接口假件测默认值合并逻辑)

**Interfaces:**
- Produces(签名冻结,设置页/主题/提醒共用):
  - `data class LnxSettings(themeSlot: ThemeSlot, darkMode: DarkMode, reminderLeadMinutes: Int? , dndEnabled: Boolean, dndStartMinute: Int, dndEndMinute: Int, weekStartMonday: Boolean, language: String /* "system"|"zh"|"en" */, onboardingDone: Boolean)`
  - `interface SettingsRepository { val settings: Flow<LnxSettings>; suspend fun setThemeSlot(...); setDarkMode(...); setReminderLead(...); setDnd(enabled, startMinute, endMinute); setWeekStartMonday(...); setLanguage(...); setOnboardingDone() }`
  - 默认值:主题 1 / 跟随系统 / 提醒 15 / 免打扰关 + 22:00–08:00(1320,480)/ 周一 / system / 引导未完成

- [ ] **Step 1: 失败单测(默认值 + set 后读回)→ Step 2 FAIL → Step 3 实现 → Step 4 PASS → Step 5: 提交** `git commit -m "feat: DataStore 设置仓库(主题/外观/提醒/免打扰/通用)"`

---

### Task 2: 其余三套主题 ColorScheme

**Files:**
- Modify: `core/designsystem/ColorSchemes.kt`(增 `paperLight/DarkScheme()`、`warmLight/DarkScheme()`、`sereneLight/DarkScheme()`)
- Modify: `LnxTheme.kt`(PAPER/WARM/SERENE 接真实方案,去掉回退)
- Test: `app/src/test/java/.../ColorSchemesTest.kt`(补断言)

**Interfaces:**
- Produces: 六个新 `ColorScheme` 工厂;色值来源 = spec §5.2 + `theme-preview.html`(PAPER:浅 #FFFFFF/#37352F/强调 #2383E2,深 #191919/#E1E1E1/#4D9CE4;WARM:#F86B3D/#FFFAF5,深 #1A1A1A 主色 #FF7A50;SERENE:头部渐变由组件层处理,scheme 主色 #4A7DBF,深 #8FB4DC、背景 #F8F6F0/#1A2A3A)

- [ ] **Step 1: 失败单测**(每主题至少断言 background/primary 两个色值,深浅共 12 断言)→ **Step 2 FAIL → Step 3 实现 → Step 4 PASS**
- [ ] **Step 5: 提交** `git commit -m "feat: 极致留白/暖橙活力/宁静冷色 三套完整配色(深浅)"`

---

### Task 3: 主题接线 + 250ms 过渡 + 设置驱动

**Files:**
- Modify: `MainActivity.kt`(`collectAsState(settings)` → `LnxTheme(slot, darkMode)`;外层 `Crossfade(targetState = slot, animationSpec = tween(250))`)
- Modify: `LnxTheme.kt`(主题 4 的 `LnxMotion`:提供 `object LnxMotion { val normal = tween(300); val slow = tween(500) }`,SERENE 下组件引用)
- Test: `app/src/androidTest/.../ThemeSwitchTest.kt`

**Interfaces:**
- Consumes: Task 1/2
- Produces: 切换主题无需重启;深色跟随实时变化(配置变更由系统重建自然处理)

- [ ] **Step 1: 失败 UI 测试**(DataStore 写入 WARM → Compose 树 primary 色变化;可用 semantics 断言 `theme_slot` tag)→ **Step 2 FAIL → Step 3 实现 → Step 4 PASS**
- [ ] **Step 5: 提交** `git commit -m "feat: 主题即时切换(250ms 过渡)+ 深色跟随系统"`

---

### Task 4: 设置页(4 组)

**Files:**
- Create: `app/src/main/java/com/lnx/app/feature/settings/SettingsScreen.kt` + `SettingsViewModel.kt` + `ThemePickerSection.kt` + `ReminderSettingsSection.kt` + `GeneralSettingsSection.kt` + `DataSettingsSection.kt`(导入导出 M7 接线,先禁用)
- Modify: 抽屉「设置」入口 → 导航到设置页(引入 `navigation-compose`,首条 nav 路线 `calendar`、`settings`)
- Test: `app/src/androidTest/.../SettingsScreenTest.kt`

**Interfaces:**
- Consumes: Task 1/2/3;M5 的提醒设置项
- Produces: 设置页 = 外观(4 主题卡片 + 模式三选)、提醒(默认提前:不/5/15/30/60;免打扰开关 + 起止时间选择器;通知权限状态行 + 「去开启」)、通用(周起始日;语言三选)、数据(导出/导入禁用 + 版本号 `BuildConfig.VERSION_NAME`)

- [ ] **Step 1: 失败 UI 测试**(打开设置 → 4 组标题存在;点主题 3 卡片 → 返回主屏背景色变化;改提醒默认 → DataStore 读回 30)→ **Step 2 FAIL → Step 3 实现 → Step 4 PASS**
- [ ] **Step 5: 提交** `git commit -m "feat: 设置页(外观/提醒/通用/数据四组)+ 导航接入"`

---

### Task 5: 首启引导(3 页)

**Files:**
- Create: `app/src/main/java/com/lnx/app/feature/onboarding/OnboardingScreen.kt`(3 页 `HorizontalPager` + 页点)
- Modify: `MainActivity.kt`(onboardingDone=false 时先渲染引导)
- Modify: `strings.xml`
- Test: `app/src/androidTest/.../OnboardingTest.kt`

**Interfaces:**
- Consumes: Task 1(onboardingDone / setThemeSlot)、Task 3 主题预览
- Produces: 页 1 欢迎 + 「开始」;页 2 四主题缩略卡(点选即 `setThemeSlot`,可「跳过」);页 3 通知权限说明 + 「开启提醒」(`requestNotifications`) / 「以后再说」;完成 → `setOnboardingDone()` 进主屏;仅首启出现

- [ ] **Step 1: 失败 UI 测试**(首次 → 显示欢迎页;连点下一步到完成 → 主屏;重启 App → 不再出现;跳过路径同样进主屏)→ **Step 2 FAIL → Step 3 实现 → Step 4 PASS**
- [ ] **Step 5: 提交** `git commit -m "feat: 首启三页引导(欢迎/选主题/通知权限)"`

---

### Task 6: 中英文化

**Files:**
- Modify: `app/src/main/res/values/strings.xml`(中文全量,含 M1–M5 硬编码文案迁移)
- Create: `app/src/main/res/values-en/strings.xml`(英文对译)
- Modify: 各 Screen 把硬编码中文改为 `stringResource`;周头/星期/规则描述等程序化文案走 `Locale` 感知工具(`WeekdaysCn` → `WeekdayNames(locale)`)
- Test: 单测 `RuleDescriptionTest` 参数化 zh/en 两套

**Interfaces:**
- Produces: 语言设置 = `AppCompatDelegate.setApplicationLocales`(引入 appcompat 1.7.0 或用 per-app language API 33+/回退 locale 手动刷新)——采用:33+ 系统接口,<33 重启生效并在设置页注明
- [ ] **Step 1: 抽串清单核对(≤ 硬编码中文 grep = 0)→ Step 2: 双语资源 → Step 3: UI 测试(改 locale 后 `formatTitle` 英文模式输出 "Sep 30 · Wed")→ Step 4 PASS**
- [ ] **Step 5: 提交** `git commit -m "feat: 中英双语 + 语言设置(per-app locale)"`

---

### Task 7: M6 验收走查

- [ ] spec §8.1 M6 四条:① 4 主题即时切换 ~250ms ② 各主题深色版 AA(现场目测 + 对比度抽查) ③ 重启记住主题 ④ 引导仅首启一次。
- [ ] M5 回归:提醒默认值/免打扰改由设置驱动后,原 M5 验收项重跑。
- [ ] 全量测试绿;`git tag v0.1.0-m6`。
