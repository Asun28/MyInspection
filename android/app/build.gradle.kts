// :app — Android 薄壳：Compose UI · CameraX · SAF · 听写 · PdfDocument 渲染 · WorkManager（见 CLAUDE.md 架构大图）。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "nz.myinspection.app"
    // compileSdk 走当前 AGP 9.3.1 支持的最高位（37）：pin 的 Compose BOM 2026.08.00 / activity-compose 1.13.0 /
    // androidx.core 1.18.0 等库要求编译期至少 compileSdk 36-37（编译期常态领先于 targetSdk，不影响运行时行为）。
    // targetSdk 仍按卡片要求钉 35（见下 defaultConfig，运行时兼容行为以此为准）。
    compileSdk = 37

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
