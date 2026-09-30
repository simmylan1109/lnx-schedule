plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // M7:备份 JSON 的编解码(spec §3.13)
    alias(libs.plugins.kotlin.serialization)
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
        testInstrumentationRunner = "com.lnx.app.HiltTestRunner"
    }

    buildTypes {
        release { isMinifyEnabled = false }
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
    kspAndroidTest(libs.room.compiler)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
