# lnx M1「骨架」实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在模拟器上跑起 lnx 的第一版:周视图空时间轴 + 日/周/月三 Tab 切换 + Material You 主题 + 浅/深色跟随系统。

**Architecture:** 单 App 模块 + 包分层(core/feature)。Compose 单 Activity;CalendarScreen 为宿主,内部 HorizontalPager 实现翻周;设计系统以 `LnxTheme(slot, darkMode)` 包装 Material3,主题 1 实现动态取色 + 静态回退。数据层(Room/DataStore)本里程碑不引入。

**Tech Stack:** Kotlin 2.0、Jetpack Compose(BOM)、Material 3、Hilt、Gradle Kotlin DSL + Version Catalog、JUnit4 + Compose UI Test。

**Spec:** `D:\11lnx\SPEC.md`(本计划只实现 spec §8.1 的 M1;M2–M7 各自在开工时另写计划)

## Global Constraints

- minSdk 26 / targetSdk 35 / compileSdk 35;JVM target 17
- applicationId / namespace:`com.lnx.app`
- 依赖只加当前里程碑需要的;Room / DataStore / WorkManager 等到对应里程碑再加
- 周起始日:周一(spec §3.2 默认)
- 界面文案:中文(英文翻译在 M6 统一补)
- 时间处理一律 `java.time`
- 提交信息用 Conventional Commits(feat:/test:/chore:/docs:);每完成一个任务必须提交
- 图标用矢量 Icon(如 Icons.Default.Search/Menu),**禁止 emoji 当图标**(spec §5.3,产品负责人明确要求)

---

### Task 0: 工具链自检与环境准备

**Files:**
- Create: 无(只检查/安装环境;`local.properties` 视需要)

**Interfaces:**
- Produces: 可用的 JDK 17、Android SDK 35、Gradle 8.9+、可运行 — 后续所有 `gradlew` 命令的前提

- [ ] **Step 1: 检查 JDK / Android SDK / Gradle**

```powershell
java -version          # 需要 17.x
echo $env:ANDROID_HOME # 需要 SDK 路径;或检查 %LOCALAPPDATA%\Android\Sdk 是否存在
gradle -v              # 需要 8.9+(仅用于生成 wrapper,可临时)
```

- [ ] **Step 2: 缺什么装什么(JDK 17)**

```powershell
winget install Microsoft.OpenJDK.17   # 或 EclipseAdoptium.Temurin.17.JDK
```

- [ ] **Step 3: 缺什么装什么(Android SDK commandline-tools + 平台 35)**

下载 commandline-tools zip 解压到 `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`,然后:

```powershell
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

- [ ] **Step 4: 缺什么装什么(Gradle 8.9,用于生成 wrapper)**

从 https://services.gradle.org/distributions/gradle-8.9-bin.zip 解压到 `%USERPROFILE%\gradle-8.9`,加入本次会话 PATH。

- [ ] **Step 5: 验证**

`java -version`、`sdkmanager --list | head`、`gradle -v` 全部正常即通过。

---

### Task 1: Git 仓库初始化与首次提交

**Files:**
- Create: `.gitignore`

**Interfaces:**
- Produces: 已初始化的 git 仓库(main 分支),首个提交包含 SPEC.md 与主题预览文件

- [ ] **Step 1: 初始化仓库**

```powershell
git init -b main
```

- [ ] **Step 2: 写 `.gitignore`**

```gitignore
*.iml
.gradle/
local.properties
.idea/
.DS_Store
build/
app/build/
captures/
.externalNativeBuild/
.cxx/
edge-tmp*/
```

- [ ] **Step 3: 首次提交**

```powershell
git add .gitignore SPEC.md theme-preview.html preview-light.png preview-dark.png docs/
git commit -m "docs: lnx v0.1 SPEC 与主题预览(首个提交)"
```

- [ ] **Step 4: 验证**

`git log --oneline` 显示首个提交。

---

### Task 2: Gradle 工程脚手架

**Files:**
- Create: `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`、`gradle/wrapper/*`、`gradlew.bat`
- Create: `app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`、`app/src/main/res/values/strings.xml`、`app/src/main/java/com/lnx/app/LnxApplication.kt`、`app/src/main/java/com/lnx/app/MainActivity.kt`

**Interfaces:**
- Produces: 可编译安装的最小 App(启动显示 "lnx" 文本);`gradlew.bat` 可用

- [ ] **Step 1: 写 `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
hilt = "2.53.1"
hiltNavigationCompose = "1.2.0"
ksp = "2.0.21-1.0.28"
junit = "4.13.2"
junitExt = "1.2.1"
espresso = "3.6.1"
coroutines = "1.9.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitExt" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: 写根构建文件**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "lnx"
include(":app")
```

`build.gradle.kts`(根):

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 3: 生成 wrapper 并验证**

先创建 app 模块目录(settings.gradle.kts 里 include 了它,目录不存在会让 wrapper 失败),再生成 wrapper:

```powershell
mkdir app
gradle wrapper --gradle-version 8.9
.\gradlew.bat --version
```

- [ ] **Step 4: 写 `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.lnx.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lnx.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
```

- [ ] **Step 5: 写 Manifest / 资源 / Application / 最小 MainActivity**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".LnxApplication"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:theme="@android:style/Theme.Material.Light.NoActionBar"
        android:supportsRtl="true">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">lnx</string>
</resources>
```

`app/src/main/java/com/lnx/app/LnxApplication.kt`:

```kotlin
package com.lnx.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LnxApplication : Application()
```

`app/src/main/java/com/lnx/app/MainActivity.kt`(临时最小版,Task 3 重写):

```kotlin
package com.lnx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("lnx") }
    }
}
```

- [ ] **Step 6: 编译验证**

```powershell
.\gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 提交**

```powershell
git add .
git commit -m "chore: Android 工程脚手架(Compose + Hilt + Version Catalog)"
```

---

### Task 3: 主题系统骨架(Material You + 浅/深)

**Files:**
- Create: `app/src/main/java/com/lnx/app/core/designsystem/ThemeSlot.kt`
- Create: `app/src/main/java/com/lnx/app/core/designsystem/DarkMode.kt`
- Create: `app/src/main/java/com/lnx/app/core/designsystem/ColorSchemes.kt`
- Create: `app/src/main/java/com/lnx/app/core/designsystem/LnxTheme.kt`
- Create: `app/src/main/java/com/lnx/app/core/designsystem/Type.kt`
- Modify: `app/src/main/java/com/lnx/app/MainActivity.kt`
- Test: `app/src/test/java/com/lnx/app/core/designsystem/ColorSchemesTest.kt`

**Interfaces:**
- Produces: `ThemeSlot`(4 个主题枚举)、`DarkMode`(FOLLOW_SYSTEM/LIGHT/DARK)、`@Composable fun LnxTheme(slot: ThemeSlot, darkMode: DarkMode, content: @Composable () -> Unit)`。M2+ 的所有界面都在 LnxTheme 内渲染。

- [ ] **Step 1: 写失败的单元测试**

`app/src/test/java/com/lnx/app/core/designsystem/ColorSchemesTest.kt`:

```kotlin
package com.lnx.app.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSchemesTest {
    @Test
    fun `materialYou 回退浅色方案 背景为 FEF7FF`() {
        assertEquals(Color(0xFFFEF7FF), materialYouLightScheme().background)
    }

    @Test
    fun `materialYou 回退深色方案 背景为 1D1B20`() {
        assertEquals(Color(0xFF1D1B20), materialYouDarkScheme().background)
    }

    @Test
    fun `回退主色为内置蓝紫`() {
        assertEquals(Color(0xFF6750A4), materialYouLightScheme().primary)
        assertEquals(Color(0xFFCFBCFF), materialYouDarkScheme().primary)
    }
}
```

- [ ] **Step 2: 运行确认失败**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: FAIL(函数 `materialYouLightScheme` 未定义)

- [ ] **Step 3: 实现**

`ThemeSlot.kt`:

```kotlin
package com.lnx.app.core.designsystem

/** spec §5.1:4 套主题;M1 仅实现 MATERIAL_YOU,其余槽位 M6 落地 */
enum class ThemeSlot(val label: String) {
    MATERIAL_YOU("Material You"),
    PAPER("极致留白"),
    WARM("暖橙活力"),
    SERENE("宁静冷色"),
}
```

`DarkMode.kt`:

```kotlin
package com.lnx.app.core.designsystem

enum class DarkMode { FOLLOW_SYSTEM, LIGHT, DARK }
```

`ColorSchemes.kt`(色值来自 spec §5.2 主题 1):

```kotlin
package com.lnx.app.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8DEF8),
    onPrimaryContainer = Color(0xFF1D192B),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    error = Color(0xFFB3261E),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4A4458),
    onPrimaryContainer = Color(0xFFE8DDFF),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DDFF),
    background = Color(0xFF1D1B20),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF1D1B20),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF36343B),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF948F99),
    error = Color(0xFFF2B8B5),
)

fun materialYouLightScheme(): ColorScheme = LightScheme
fun materialYouDarkScheme(): ColorScheme = DarkScheme
```

`Type.kt`:

```kotlin
package com.lnx.app.core.designsystem

import androidx.compose.material3.Typography

/** spec §5.3 字号梯度在具体组件中细化;此处先取 Material3 默认 */
val LnxTypography = Typography()
```

`LnxTheme.kt`:

```kotlin
package com.lnx.app.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun LnxTheme(
    slot: ThemeSlot,
    darkMode: DarkMode,
    content: @Composable () -> Unit,
) {
    val dark = when (darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }
    val scheme = when (slot) {
        ThemeSlot.MATERIAL_YOU -> {
            val context = LocalContext.current
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (dark) materialYouDarkScheme() else materialYouLightScheme()
            }
        }
        // PAPER / WARM / SERENE 在 M6 实现;先回退主题 1 静态方案
        else -> if (dark) materialYouDarkScheme() else materialYouLightScheme()
    }
    MaterialTheme(colorScheme = scheme, typography = LnxTypography, content = content)
}
```

`MainActivity.kt` 重写:

```kotlin
package com.lnx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.LnxTheme
import com.lnx.app.core.designsystem.ThemeSlot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LnxTheme(slot = ThemeSlot.MATERIAL_YOU, darkMode = DarkMode.FOLLOW_SYSTEM) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text("lnx")
                }
            }
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: PASS(3 个测试)

- [ ] **Step 5: 提交**

```powershell
git add app/src
git commit -m "feat: 主题系统骨架(Material You 动态取色 + 回退方案 + 浅深三态)"
```

---

### Task 4: 日期格式化 + 日历主屏骨架(顶栏 + 三 Tab)

**Files:**
- Create: `app/src/main/java/com/lnx/app/core/common/DateFormatter.kt`
- Create: `app/src/main/java/com/lnx/app/feature/calendar/CalendarViewModel.kt`
- Create: `app/src/main/java/com/lnx/app/feature/calendar/CalendarScreen.kt`
- Create: `app/src/main/java/com/lnx/app/feature/calendar/components/CalendarTopBar.kt`
- Create: `app/src/main/java/com/lnx/app/feature/calendar/components/ViewModeTabs.kt`
- Create: `app/src/main/java/com/lnx/app/feature/calendar/PlaceholderScreen.kt`
- Modify: `app/src/main/java/com/lnx/app/MainActivity.kt`(CalendarScreen 替换 Text)
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/lnx/app/core/common/DateFormatterTest.kt`
- Test: `app/src/androidTest/java/com/lnx/app/feature/calendar/CalendarScreenTest.kt`

**Interfaces:**
- Consumes: `LnxTheme`(Task 3)
- Produces:
  - `fun formatTitle(date: LocalDate): String` → `"9月 · 30日 周三"` 格式
  - `enum class ViewMode(val label: String) { DAY("日"), WEEK("周"), MONTH("月") }`
  - `data class CalendarUiState(val selectedDate: LocalDate, val viewMode: ViewMode)`
  - `CalendarViewModel`: StateFlow<CalendarUiState> + `selectDate(LocalDate)` / `selectViewMode(ViewMode)` / `backToToday()`
  - `@Composable fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel())`
  - `@Composable fun CalendarTopBar(title: String, onTodayClick: () -> Unit)`(含 testTag `top_bar` / `today_button`)
  - `@Composable fun ViewModeTabs(current: ViewMode, onSelect: (ViewMode) -> Unit)`(testTag `tab_日/周/月` 用 `Modifier.testTag("tab_" + mode.name)`)

- [ ] **Step 1: 写失败的单元测试(DateFormatter)**

`app/src/test/java/com/lnx/app/core/common/DateFormatterTest.kt`:

```kotlin
package com.lnx.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DateFormatterTest {
    @Test
    fun `标题格式为 M月 DD日 周X`() {
        assertEquals("9月 · 30日 周三", formatTitle(LocalDate.of(2026, 9, 30)))
    }

    @Test
    fun `周一到周日的中文映射`() {
        assertEquals("周一", formatTitle(LocalDate.of(2026, 9, 28)).takeLast(2))
        assertEquals("周日", formatTitle(LocalDate.of(2026, 10, 4)).takeLast(2))
    }

    @Test
    fun `年份变化不影响格式`() {
        assertEquals("1月 · 1日 周四", formatTitle(LocalDate.of(2026, 1, 1)))
    }
}
```

- [ ] **Step 2: 运行确认失败**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: FAIL(`formatTitle` 未定义)

- [ ] **Step 3: 实现 DateFormatter**

```kotlin
package com.lnx.app.core.common

import java.time.DayOfWeek
import java.time.LocalDate

private val DOW_CN = mapOf(
    DayOfWeek.MONDAY to "周一",
    DayOfWeek.TUESDAY to "周二",
    DayOfWeek.WEDNESDAY to "周三",
    DayOfWeek.THURSDAY to "周四",
    DayOfWeek.FRIDAY to "周五",
    DayOfWeek.SATURDAY to "周六",
    DayOfWeek.SUNDAY to "周日",
)

fun dayOfWeekCn(date: LocalDate): String = DOW_CN.getValue(date.dayOfWeek)

fun formatTitle(date: LocalDate): String =
    "${date.monthValue}月 · ${date.dayOfMonth}日 ${dayOfWeekCn(date)}"
```

- [ ] **Step 4: 运行确认单元测试通过**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: PASS

- [ ] **Step 5: 写失败的 UI 测试(Tab 切换)**

`app/src/androidTest/java/com/lnx/app/feature/calendar/CalendarScreenTest.kt`:

```kotlin
package com.lnx.app.feature.calendar

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `默认周视图且标题存在`() {
        rule.onNodeWithTag("top_bar").assertExists()
        rule.onNodeWithTag("tab_WEEK").assertExists()
    }

    @Test
    fun `点击月Tab显示月占位 再点周Tab回到周视图`() {
        rule.onNodeWithTag("tab_MONTH").performClick()
        rule.onNodeWithText("月视图将在后续里程碑提供").assertExists()
        rule.onNodeWithTag("tab_WEEK").performClick()
        rule.onNodeWithTag("week_grid").assertExists()
    }

    @Test
    fun `点击今天按钮不崩溃`() {
        rule.onNodeWithTag("today_button").performClick()
        rule.onNodeWithTag("top_bar").assertExists()
    }
}
```

- [ ] **Step 6: 运行确认 UI 测试失败**

启动模拟器(android-emulator 工具),然后:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected: FAIL(`top_bar` 等 testTag 不存在)

- [ ] **Step 7: 实现主屏骨架**

`CalendarViewModel.kt`:

```kotlin
package com.lnx.app.feature.calendar

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class ViewMode(val label: String) { DAY("日"), WEEK("周"), MONTH("月") }

data class CalendarUiState(
    val selectedDate: LocalDate,
    val viewMode: ViewMode,
)

@HiltViewModel
class CalendarViewModel @Inject constructor() : ViewModel() {
    private val today: LocalDate = LocalDate.now()

    private val _uiState = MutableStateFlow(
        CalendarUiState(selectedDate = today, viewMode = ViewMode.WEEK)
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    fun selectDate(date: LocalDate) = _uiState.update { it.copy(selectedDate = date) }

    fun selectViewMode(mode: ViewMode) = _uiState.update { it.copy(viewMode = mode) }

    fun backToToday() = _uiState.update { it.copy(selectedDate = today) }
}
```

`CalendarTopBar.kt`:

```kotlin
package com.lnx.app.feature.calendar.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun CalendarTopBar(
    title: String,
    onMenuClick: () -> Unit,
    onTodayClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("top_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "菜单",
            modifier = Modifier.padding(8.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onTodayClick, modifier = Modifier.testTag("today_button")) {
                Text("今天")
            }
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索",
                modifier = Modifier.padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
```

`ViewModeTabs.kt`:

```kotlin
package com.lnx.app.feature.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lnx.app.feature.calendar.ViewMode

@Composable
fun ViewModeTabs(
    current: ViewMode,
    onSelect: (ViewMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ViewMode.entries.forEach { mode ->
            val selected = mode == current
            Text(
                text = mode.label,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .testTag("tab_${mode.name}"),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

`PlaceholderScreen.kt`:

```kotlin
package com.lnx.app.feature.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PlaceholderScreen(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

`CalendarScreen.kt`:

```kotlin
package com.lnx.app.feature.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lnx.app.core.common.formatTitle
import com.lnx.app.feature.calendar.components.CalendarTopBar
import com.lnx.app.feature.calendar.components.ViewModeTabs

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        CalendarTopBar(
            title = formatTitle(state.selectedDate),
            onMenuClick = { /* 抽屉在 M3 接入 */ },
            onTodayClick = viewModel::backToToday,
            onSearchClick = { /* 搜索在 M7 接入 */ },
        )
        ViewModeTabs(
            current = state.viewMode,
            onSelect = viewModel::selectViewMode,
        )
        when (state.viewMode) {
            ViewMode.DAY -> PlaceholderScreen("日视图将在后续里程碑提供")
            ViewMode.WEEK -> Box(modifier = Modifier.testTag("week_grid")) { // Task 5 起由 WeekView 承接此 tag
                PlaceholderScreen("周视图将在本里程碑内实现")
            }
            ViewMode.MONTH -> PlaceholderScreen("月视图将在后续里程碑提供")
        }
    }
}
```

`MainActivity.kt` 的 `setContent` 改为调用 `CalendarScreen()`。

(CalendarScreen 需补 import:`androidx.compose.foundation.layout.Box`、`androidx.compose.ui.platform.testTag`。)

- [ ] **Step 8: 运行确认 UI 测试通过**

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected: PASS(3 个测试)

- [ ] **Step 9: 提交**

```powershell
git add app/src
git commit -m "feat: 日历主屏骨架(顶栏 + 日/周/月 Tab + 视图切换 + 日期格式化)"
```

---

### Task 5: 周头部 + 翻周手势 + 标题联动

**Files:**
- Create: `app/src/main/java/com/lnx/app/feature/calendar/week/WeekView.kt`(本任务先含 WeekHeader)
- Modify: `app/src/main/java/com/lnx/app/feature/calendar/CalendarScreen.kt`(WEEK 分支换 WeekView)
- Modify: `app/src/main/java/com/lnx/app/feature/calendar/CalendarViewModel.kt`(补 `pageToDate` 静态映射)
- Test: `app/src/test/java/com/lnx/app/core/common/WeekLogicTest.kt`
- Test: `app/src/androidTest/java/com/lnx/app/feature/calendar/WeekPagerTest.kt`

**Interfaces:**
- Consumes: `CalendarViewModel`(Task 4)、`formatTitle`
- Produces:
  - `fun weekStartOf(date: LocalDate): LocalDate`(ISO 周一)
  - `fun dateToPage(date: LocalDate): Int` / `fun pageToDate(page: Int): LocalDate`(以 1970-01-05 所在周为第 0 页)
  - `@Composable fun WeekView(state: CalendarUiState, onSelectDate: (LocalDate) -> Unit, modifier: Modifier)`(本任务渲染星期头;Task 6 填充时间轴)

- [ ] **Step 1: 写失败的单元测试(周逻辑)**

`app/src/test/java/com/lnx/app/core/common/WeekLogicTest.kt`:

```kotlin
package com.lnx.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekLogicTest {
    @Test
    fun `任意日期归到所在周的周一`() {
        // 2026-09-30 是周三
        assertEquals(LocalDate.of(2026, 9, 28), weekStartOf(LocalDate.of(2026, 9, 30)))
        // 周日 2026-10-04 属于 9-28 开始的那一周
        assertEquals(LocalDate.of(2026, 9, 28), weekStartOf(LocalDate.of(2026, 10, 4)))
        // 周一自己
        assertEquals(LocalDate.of(2026, 9, 28), weekStartOf(LocalDate.of(2026, 9, 28)))
    }

    @Test
    fun `页面与日期互转 间隔一周`() {
        val base = LocalDate.of(2026, 9, 28)
        val p0 = dateToPage(base)
        assertEquals(base.plusWeeks(3), pageToDate(p0 + 3))
        assertEquals(base.minusWeeks(2), pageToDate(p0 - 2))
    }
}
```

- [ ] **Step 2: 运行确认失败**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: FAIL(`weekStartOf` 未定义)

- [ ] **Step 3: 实现周逻辑(放入 DateFormatter.kt 同文件)**

```kotlin
import java.time.temporal.ChronoUnit

/** spec §3.2:周一起始(ISO) */
fun weekStartOf(date: LocalDate): LocalDate =
    date.with(java.time.DayOfWeek.MONDAY)

private val PAGE_EPOCH: LocalDate = LocalDate.of(1970, 1, 5) // 周一

fun dateToPage(date: LocalDate): Int =
    ChronoUnit.WEEKS.between(PAGE_EPOCH, weekStartOf(date)).toInt()

fun pageToDate(page: Int): LocalDate = PAGE_EPOCH.plusWeeks(page.toLong())
```

- [ ] **Step 4: 运行确认单元测试通过**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: PASS

- [ ] **Step 5: 写失败的 UI 测试(翻周)**

`app/src/androidTest/java/com/lnx/app/feature/calendar/WeekPagerTest.kt`:

```kotlin
package com.lnx.app.feature.calendar

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.common.formatTitle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WeekPagerTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun titleOf(date: LocalDate) = formatTitle(date.with(java.time.DayOfWeek.MONDAY))

    @Test
    fun `左滑翻到下周 右滑翻回`() {
        val nextWeek = titleOf(LocalDate.now().plusWeeks(1))
        rule.onNodeWithTag("week_pager").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("week_header_${nextWeek}").assertExists()
        rule.onNodeWithTag("week_pager").performTouchInput { swipeRight() }
    }
}
```

注意:标题显示的是**选中日**的格式,翻页后选中日为该周一;星期头 testTag 带上周一日期以便断言。

- [ ] **Step 6: 运行确认 UI 测试失败**

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected: FAIL(`week_pager` 不存在)

- [ ] **Step 7: 实现 WeekHeader 与 Pager**

`WeekView.kt`(本任务版本):

```kotlin
package com.lnx.app.feature.calendar.week

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lnx.app.core.common.dateToPage
import com.lnx.app.core.common.pageToDate
import com.lnx.app.feature.calendar.CalendarUiState
import java.time.LocalDate
import java.time.DayOfWeek

private val DOW_HEADER = listOf("一", "二", "三", "四", "五", "六", "日")

fun dayOfWeekCnShort(dow: DayOfWeek): String = DOW_HEADER[dow.value - 1]

@Composable
fun WeekView(
    state: CalendarUiState,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = dateToPage(state.selectedDate),
        pageCount = { 40001 },
    )
    // 选中日期变化(如"今天"按钮)时同步翻页
    LaunchedEffect(state.selectedDate) {
        val target = dateToPage(state.selectedDate)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }
    // 翻页 → 选中日 = 该周周一
    LaunchedEffect(pagerState.currentPage) {
        onSelectDate(pageToDate(pagerState.currentPage))
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.testTag("week_pager"),
    ) { page ->
        val weekStart = pageToDate(page)
        Column(modifier = Modifier.testTag("week_header_${weekStart}")) {
            WeekHeader(weekStart = weekStart, selectedDate = state.selectedDate, today = today)
            Box(modifier = Modifier.weight(1f).testTag("week_grid")) // Task 6 起由 TimeGrid 承接此 tag
        }
    }
}

@Composable
private fun WeekHeader(
    weekStart: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        (0..6).forEach { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val isToday = date == today
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = dayOfWeekCnShort(date.dayOfWeek),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (date == selectedDate) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            width = if (isToday) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
```

`CalendarScreen.kt` 的 WEEK 分支改为:

```kotlin
ViewMode.WEEK -> WeekView(
    state = state,
    today = remember { LocalDate.now() },
    onSelectDate = viewModel::selectDate,
    modifier = Modifier.weight(1f),
)
```

(把周占位 `PlaceholderScreen` 移除;`week_grid` testTag 转移到 WeekView 内层,见 Task 6。)

- [ ] **Step 8: 运行确认 UI 测试通过**

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected: PASS(`week_grid` tag 已由 WeekView 内层承接,Task 4 的测试无需改动)

- [ ] **Step 9: 提交**

```powershell
git add app/src
git commit -m "feat: 周视图星期头 + 翻周手势 + 顶部标题联动"
```

---

### Task 6: 时间轴网格 + 全天条 + 当前时刻线 + 空状态

**Files:**
- Modify: `app/src/main/java/com/lnx/app/feature/calendar/week/WeekView.kt`(填充 Pager 内容:全天条 + 时间轴 + 空态 + 红线)
- Modify: `app/src/main/java/com/lnx/app/feature/calendar/CalendarScreenTest.kt`(更新 `week_grid` 断言)
- Test: `app/src/androidTest/java/com/lnx/app/feature/calendar/WeekGridTest.kt`

**Interfaces:**
- Consumes: `WeekView`(Task 5)
- Produces: 时间轴组件(内部):`HOUR_HEIGHT_DP = 56`、testTag `week_grid`、`now_line`、空态文案节点。M2 的事件块渲染、点空白新建将挂在这个网格上(通过参数注入事件列表与点击回调,本任务先留空实现)。

- [ ] **Step 1: 写失败的 UI 测试**

`app/src/androidTest/java/com/lnx/app/feature/calendar/WeekGridTest.kt`:

```kotlin
package com.lnx.app.feature.calendar

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeekGridTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `网格与刻度存在`() {
        rule.onNodeWithTag("week_grid").assertExists()
        rule.onNodeWithText("9:00").performScrollTo().assertExists()
    }

    @Test
    fun `当前时刻线存在`() {
        rule.onNodeWithTag("now_line").assertExists()
    }

    @Test
    fun `空状态文案显示`() {
        rule.onNodeWithText("今天没有日程,享受自由时光 🌤", substring = true).assertExists()
    }
}
```

- [ ] **Step 2: 运行确认失败**

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected: FAIL(`week_grid` / `now_line` 不存在)

- [ ] **Step 3: 实现(替换 Task 5 WeekView 中的空 Box)**

在 `WeekView.kt` 中新增(并替换 `Box(weight(1f))` 为 `TimeGrid(...)` + `AllDayStrip` + 空态覆盖):

```kotlin
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import java.time.LocalTime

private val HOUR_HEIGHT = 56.dp
private val GUTTER_WIDTH = 44.dp

@Composable
private fun AllDayStrip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp),
    ) // M2 渲染全天/跨天事件块
}

@Composable
private fun TimeGrid(
    selectedDate: LocalDate,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val nowState = remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowState.value = LocalTime.now()
        }
    }
    // 打开时滚到当前时刻位于上部约 1/3 处(spec §3.2)
    val hourPx = with(LocalDensity.current) { HOUR_HEIGHT.toPx() }
    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            val nowFraction = (nowState.value.hour + nowState.value.minute / 60f) / 24f
            val target = (nowFraction * 24 * hourPx - scrollState.maxValue / 3f)
                .toInt().coerceIn(0, scrollState.maxValue)
            scrollState.scrollTo(target)
        }
    }

    Box(modifier = modifier.testTag("week_grid")) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(scrollState),
        ) {
            // 左侧刻度列
            Box(modifier = Modifier.width(GUTTER_WIDTH)) {
                repeat(24) { hour ->
                    Text(
                        text = "$hour:00",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .width(GUTTER_WIDTH)
                            .offset(y = (hour * HOUR_HEIGHT.value - 6).dp),
                    )
                }
            }
            // 7 天列 + 小时横线 + 当前线
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24 * HOUR_HEIGHT),
            ) {
                Canvas(modifier = Modifier.fillMaxHeight()) {
                    val hourPx = HOUR_HEIGHT.toPx()
                    // 横线 25 条
                    repeat(25) { i ->
                        drawLine(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            start = androidx.compose.ui.geometry.Offset(0f, i * hourPx),
                            end = androidx.compose.ui.geometry.Offset(size.width, i * hourPx),
                            strokeWidth = 1f,
                        )
                    }
                    // 竖线 7 条(含首尾)
                    val colWidth = size.width / 7f
                    repeat(8) { i ->
                        drawLine(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            start = androidx.compose.ui.geometry.Offset(i * colWidth, 0f),
                            end = androidx.compose.ui.geometry.Offset(i * colWidth, size.height),
                            strokeWidth = 1f,
                        )
                    }
                }
                val now = nowState.value
                val nowY = (now.hour + now.minute / 60f) * HOUR_HEIGHT
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = nowY)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.error)
                        .testTag("now_line"),
                )
            }
        }
        // 空状态(spec §3.14;M1 恒为空)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (selectedDate == today) "今天没有日程,享受自由时光 🌤"
                       else "这天没有日程,享受自由时光 🌤",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

WeekView 的 Pager 内容改为:

```kotlin
Column(modifier = Modifier.testTag("week_header_${weekStart}")) {
    WeekHeader(weekStart = weekStart, selectedDate = state.selectedDate, today = today)
    AllDayStrip()
    TimeGrid(
        selectedDate = state.selectedDate,
        today = today,
        modifier = Modifier.weight(1f),
    )
}
```

(`week_grid` testTag 从 Task 5 的内层 Box 移到 TimeGrid 根部,对外断言不变。)

- [ ] **Step 4: 运行确认 UI 测试通过**

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected: PASS(3 个测试)

- [ ] **Step 5: 提交**

```powershell
git add app/src
git commit -m "feat: 周视图时间轴网格 + 全天条占位 + 当前时刻线 + 空状态"
```

---

### Task 7: 底部弹卡框架(详情卡片组件)

**Files:**
- Create: `app/src/main/java/com/lnx/app/feature/event/LnxDetailSheet.kt`
- Test: `app/src/androidTest/java/com/lnx/app/feature/event/LnxDetailSheetTest.kt`

**Interfaces:**
- Produces: `@Composable fun LnxDetailSheet(visible: Boolean, onDismiss: () -> Unit, title: String, content: @Composable ColumnScope.() -> Unit)` —— M2 的事件详情、M3 的各种底部面板都复用此组件。

- [ ] **Step 1: 写失败的 UI 测试**

`app/src/androidTest/java/com/lnx/app/feature/event/LnxDetailSheetTest.kt`:

```kotlin
package com.lnx.app.feature.event

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.LnxTheme
import com.lnx.app.core.designsystem.ThemeSlot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LnxDetailSheetTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `visible 为 true 时展示内容`() {
        rule.setContent {
            LnxTheme(ThemeSlot.MATERIAL_YOU, DarkMode.LIGHT) {
                LnxDetailSheet(visible = true, onDismiss = {}, title = "测试详情") {
                    Text("测试内容行")
                }
            }
        }
        rule.onNodeWithText("测试详情").assertExists()
        rule.onNodeWithText("测试内容行").assertExists()
    }
}
```

- [ ] **Step 2: 运行确认失败**

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected: FAIL(`LnxDetailSheet` 未定义)

- [ ] **Step 3: 实现**

```kotlin
package com.lnx.app.feature.event

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LnxDetailSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected: PASS

- [ ] **Step 5: 提交**

```powershell
git add app/src
git commit -m "feat: 底部弹卡框架组件 LnxDetailSheet"
```

---

### Task 8: M1 验收走查(模拟器实测)

**Files:**
- 无新文件;产出验收记录(提交到 commit message / 汇报文本)

**Interfaces:**
- Consumes: 前 7 个任务的全部产出

- [ ] **Step 1: 启动模拟器并安装启动**

用 android-emulator 工具(或 `avdmanager`/`emulator` 命令)启动 API 35 模拟器,`.\gradlew.bat installDebug` 后启动 `com.lnx.app`。

- [ ] **Step 2: 逐条验收(spec §8.1 M1)**

| # | 验收项 | 操作 |
|---|--------|------|
| 1 | 三 Tab 切换流畅 | 依次点 日/周/月/周,观察无卡顿 |
| 2 | 翻周手势正常 | 左右滑动两次,星期头与标题跟随变化 |
| 3 | 空状态文案显示 | 周视图中部出现"今天没有日程,享受自由时光 🌤" |
| 4 | 浅/深色跟随系统 | `adb shell cmd uimode night yes` → 界面变深色;`night no` → 变浅色;截图留档 |

- [ ] **Step 3: 跑全量测试**

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
```

Expected: 全部 PASS

- [ ] **Step 4: 打标签收尾**

```powershell
git tag v0.1.0-m1
```

---

## 后续里程碑

M2(事件增删改查)开工时,以当时实际代码为基准另写 `2026-MM-DD-lnx-m2-events.md`;同样处理 M3–M7。每份计划复用本文件的 Global Constraints。
