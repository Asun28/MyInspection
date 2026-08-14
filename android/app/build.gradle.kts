// :app — Android 薄壳：Compose UI · CameraX · SAF · 听写 · PdfDocument 渲染 · WorkManager（见 CLAUDE.md 架构大图）。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "nz.myinspection.app"
    // compileSdk 按卡片钉死 35（与 targetSdk 一致，见下 defaultConfig）：libs.versions.toml 已把
    // Compose BOM / activity-compose 锁定到实测 minCompileSdk<=35 的版本（2026.06.01 / 1.10.0），
    // 不再需要抬高 compileSdk 迁就更新库版本（R3 首轮 block：SDK 地板须与卡片一致，见该文件注释核验记录）。
    compileSdk = 35

    defaultConfig {
        applicationId = "nz.myinspection.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    implementation(libs.androidx.exifinterface)
    implementation(libs.work.runtime.ktx)

    // Android 侧 SQLDelight 驱动（schema 由后续卡挂上，本卡只 pin 依赖）
    implementation(libs.sqldelight.driver.android)
}
