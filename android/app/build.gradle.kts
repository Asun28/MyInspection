// :app — Android 薄壳：Compose UI · CameraX · SAF · 听写 · PdfDocument 渲染 · WorkManager（见 CLAUDE.md 架构大图）。
import com.android.build.api.artifact.SingleArtifact
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

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

// 合并清单 + 备份/D2D 排除规则回归检查（隐私硬边界，见 res/xml/{data_extraction_rules,backup_rules}.xml 注释）。
// 不只查合并清单里三个属性名是否在场（那样删光 XML 里的 <exclude> 仍会绿），改**解析** XML 文件本身，断言
// 两个文件、data_extraction_rules.xml 的 <cloud-backup>/<device-transfer> 两条通道，各自真列出全部必须域。
// non_goals 禁 Robolectric/仪器测试，改读文件断言，挂进 :app:check。
val requiredBackupDomains = listOf(
    "root", "file", "database", "sharedpref", "external",
    "device_root", "device_file", "device_database", "device_sharedpref",
)

fun excludedDomains(scope: Element): Set<String> =
    (0 until scope.childNodes.length)
        .mapNotNull { scope.childNodes.item(it) as? Element }
        .filter { it.tagName == "exclude" }
        .map { it.getAttribute("domain") }
        .toSet()

fun childElement(root: Element, tag: String): Element =
    (0 until root.childNodes.length)
        .mapNotNull { root.childNodes.item(it) as? Element }
        .first { it.tagName == tag }

androidComponents {
    onVariants { variant ->
        val manifestCheck = tasks.register(
            "verify${variant.name.replaceFirstChar { it.uppercase() }}BackupManifest"
        ) {
            val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
            val dataExtractionRules = layout.projectDirectory.file("src/main/res/xml/data_extraction_rules.xml")
            val backupRules = layout.projectDirectory.file("src/main/res/xml/backup_rules.xml")
            inputs.file(mergedManifest)
            inputs.file(dataExtractionRules)
            inputs.file(backupRules)
            doLast {
                val manifestText = mergedManifest.get().asFile.readText()
                val requiredAttrs = listOf(
                    "android:allowBackup=\"false\"",
                    "android:fullBackupContent=",
                    "android:dataExtractionRules=",
                )
                val missingAttrs = requiredAttrs.filterNot { manifestText.contains(it) }
                check(missingAttrs.isEmpty()) { "合并清单缺少备份/D2D 排除属性：$missingAttrs" }

                val dbf = DocumentBuilderFactory.newInstance()
                val extractionRoot = dbf.newDocumentBuilder().parse(dataExtractionRules.asFile).documentElement
                for (channel in listOf("cloud-backup", "device-transfer")) {
                    val found = excludedDomains(childElement(extractionRoot, channel))
                    val missing = requiredBackupDomains - found
                    check(missing.isEmpty()) { "data_extraction_rules.xml 的 <$channel> 缺排除域：$missing" }
                }

                val backupRoot = dbf.newDocumentBuilder().parse(backupRules.asFile).documentElement
                val foundBackup = excludedDomains(backupRoot)
                val missingBackup = requiredBackupDomains - foundBackup
                check(missingBackup.isEmpty()) { "backup_rules.xml 缺排除域：$missingBackup" }
            }
        }
        tasks.named("check") { dependsOn(manifestCheck) }
    }
}
