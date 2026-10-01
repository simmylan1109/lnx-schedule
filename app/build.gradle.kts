import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // M7:备份 JSON 的编解码(spec §3.13)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * 发布签名凭据。文件在仓库根、且已 gitignore;密钥本体在仓库外。
 * 文件不存在时 release 退回未签名(能编出 APK,只是装不上)——
 * 这样别人克隆下来跑 `assembleRelease` 不会因为缺凭据直接炸。
 * 文件在、但少写了某个字段时点名报错,而不是让 `rootProject.file(null)` 抛一句看不懂的
 * 空指针 —— 手抄这个文件时漏一行是最可能发生的事(终审 P2-1)。
 */
// 用简单名 Properties 而不是 java.util.Properties:后者里的 `java` 会被
// Gradle Kotlin DSL 的 java 扩展抢掉,报 "Unresolved reference: util"
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun keystoreField(name: String): String = keystoreProperties.getProperty(name)
    ?: throw GradleException(
        "keystore.properties 里缺少 $name。" +
            "照着仓库里的说明把 storeFile/storePassword/keyAlias/keyPassword 四项都写全;",
    )
if (keystoreProperties.isNotEmpty()) {
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").forEach {
        keystoreField(it) // 配置期就把四项验一遍,别等签名那一刻才发现
    }
}

android {
    namespace = "com.lnx.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lnx.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
        testInstrumentationRunner = "com.lnx.app.HiltTestRunner"
    }

    /**
     * 两条互不相干的版本线,别混:
     * - **这里**(`versionCode` / `versionName`)是 App 版本,每次发版都涨,系统判断
     *   "要不要替换安装"就看它。规则见 `docs/RELEASE.md`。
     * - **数据库版本**是 `core/database/DbVersion.kt` 里的 `CURRENT`,只在表结构变了时才涨。
     *   两者**不需要**同步涨:App 可以发 0.1.1 而库还是 v3,也可以发 0.2.0 而库才从 v3 升到 v4。
     */

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreField("storeFile"))
                storePassword = keystoreField("storePassword")
                keyAlias = keystoreField("keyAlias")
                keyPassword = keystoreField("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 混淆 + 资源压缩。风险在"库靠反射/字符串活着的地方会被裁掉",
            // 所以 proguard-rules.pro 只补官方 consumer rules 覆盖不到的,
            // 并且**靠真机装 release 包跑一遍走查来验**,不靠读规则自证。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        // 设置页底部版本号(BuildConfig.VERSION_NAME);AGP 8 起默认不生成
        buildConfig = true
    }
    sourceSets {
        // MigrationTestHelper 运行时要从 assets 读 schemas/*.json 建"旧版本库"(M8)
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

// Room schema 落盘位置:exportSchema = true 依赖它,否则 schema JSON 不会生成,
// M3 起要写 Migration / MigrationTestHelper 时就没有基线可比对。
// 注:只配主编译;kspAndroidTest 是依赖配置(给依赖用的),没有 arg 扩展,且
// MigrationTestHelper 读的是主编译导出的 schema,不依赖 androidTest 侧再导一次。
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

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
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    // M6:设置唯一偏好源(spec §3.11 主题/外观/提醒/免打扰/通用)
    implementation(libs.androidx.datastore.preferences)
    // M7:导入/导出 JSON(spec §3.13)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(libs.room.testing)
    kspAndroidTest(libs.room.compiler)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
