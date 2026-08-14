// :app — Android 薄壳：Compose UI · CameraX · SAF · 听写 · PdfDocument 渲染 · WorkManager（见 CLAUDE.md 架构大图）。
import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "nz.myinspection.app"
    // compileSdk 钉 35（=targetSdk，见下 defaultConfig）：地板约束与核验方法见 libs.versions.toml 头注，
    // 升级 Compose/activity/CameraX 前必须先按同一方法核验新版本仍满足 minCompileSdk<=35。
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

// 合并清单回归检查（隐私硬边界：备份/D2D 排除必须落地，见 res/xml/{data_extraction_rules,backup_rules}.xml
// 注释）。non_goals 禁 Robolectric/仪器测试，改读合并清单文件断言，挂进 :app:check。超出 AGP 任务图承受范围
// 即停手改走 tech-debt（人工约定，未设硬行数闸）。
androidComponents {
    onVariants { variant ->
        val manifestCheck = tasks.register(
            "verify${variant.name.replaceFirstChar { it.uppercase() }}BackupManifest"
        ) {
            val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
            inputs.file(mergedManifest)
            doLast {
                val text = mergedManifest.get().asFile.readText()
                val required = listOf(
                    "android:allowBackup=\"false\"",
                    "android:fullBackupContent=",
                    "android:dataExtractionRules=",
                )
                val missing = required.filterNot { text.contains(it) }
                check(missing.isEmpty()) { "合并清单缺少备份/D2D 排除属性：$missing" }
            }
        }
        tasks.named("check") { dependsOn(manifestCheck) }
    }
}
