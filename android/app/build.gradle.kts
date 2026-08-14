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
// 两处均**解析**而非子串匹配（T0-TOOLCHAIN R3 finding #6a/#6b）：
//   #6a：合并清单不再只查三个属性名是否在场（注释里出现同名字符串也会满足）——改解析 <application> 元素，
//        断言三个属性的**精确值**（allowBackup=false、fullBackupContent=@xml/backup_rules、
//        dataExtractionRules=@xml/data_extraction_rules），而不只是「存在」。
//   #6b：排除规则不再只查 domain 是否出现——改断言 domain **与** path 的精确配对（要求 path="."，即整个
//        该域），把 27 个 path="." 收窄成任何更窄路径都会被判缺失。
// non_goals 禁 Robolectric/仪器测试，改读文件断言，挂进 :app:check。
data class BackupExclude(val domain: String, val path: String)

val requiredBackupDomains = listOf(
    "root", "file", "database", "sharedpref", "external",
    "device_root", "device_file", "device_database", "device_sharedpref",
)
val requiredBackupExcludes = requiredBackupDomains.map { BackupExclude(it, ".") }.toSet()
val requiredApplicationAttrs = mapOf(
    "android:allowBackup" to "false",
    "android:fullBackupContent" to "@xml/backup_rules",
    "android:dataExtractionRules" to "@xml/data_extraction_rules",
)

fun excludedDomains(scope: Element): Set<BackupExclude> =
    (0 until scope.childNodes.length)
        .mapNotNull { scope.childNodes.item(it) as? Element }
        .filter { it.tagName == "exclude" }
        .map { BackupExclude(it.getAttribute("domain"), it.getAttribute("path")) }
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
                val dbf = DocumentBuilderFactory.newInstance()

                val manifestRoot = dbf.newDocumentBuilder().parse(mergedManifest.get().asFile).documentElement
                val applicationEl = childElement(manifestRoot, "application")
                val badAttrs = requiredApplicationAttrs.filterNot { (name, expected) ->
                    applicationEl.getAttribute(name) == expected
                }
                check(badAttrs.isEmpty()) {
                    "合并清单 <application> 属性值不符（期望 $requiredApplicationAttrs，未命中：$badAttrs）"
                }

                val extractionRoot = dbf.newDocumentBuilder().parse(dataExtractionRules.asFile).documentElement
                for (channel in listOf("cloud-backup", "device-transfer")) {
                    val found = excludedDomains(childElement(extractionRoot, channel))
                    val missing = requiredBackupExcludes - found
                    check(missing.isEmpty()) { "data_extraction_rules.xml 的 <$channel> 缺排除域/路径配对：$missing" }
                }

                val backupRoot = dbf.newDocumentBuilder().parse(backupRules.asFile).documentElement
                val foundBackup = excludedDomains(backupRoot)
                val missingBackup = requiredBackupExcludes - foundBackup
                check(missingBackup.isEmpty()) { "backup_rules.xml 缺排除域/路径配对：$missingBackup" }
            }
        }
        tasks.named("check") { dependsOn(manifestCheck) }
    }
}
