// :app — Android 薄壳：Compose UI · CameraX · SAF · 听写 · PdfDocument 渲染 · WorkManager（见 CLAUDE.md 架构大图）。
import com.android.build.api.artifact.SingleArtifact
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
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

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.testng)
}

tasks.withType<Test>().configureEach {
    useTestNG()
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
                // XXE 加固（T0-TOOLCHAIN R3 finding）：默认 DocumentBuilderFactory 在 JDK 17 上会解析外部
                // DTD/实体——三份待解析 XML 虽出自本仓受控目录，但供应链投毒/误改仍可能在其中植入恶意 DOCTYPE，
                // 届时离线 gradlew build 期间即可被诱发任意文件读取/出站网络请求，直接破本仓「测试/CI 走确定性/
                // 离线路径，禁出站网络」硬边界（CLAUDE.md）。按 OWASP XXE 防御清单：全面拒绝 DOCTYPE 声明
                // （最强项，覆盖内部/外部实体两类）+ 显式关闭外部 DTD/schema/XInclude/实体展开，多层兜底。
                val dbf = DocumentBuilderFactory.newInstance().apply {
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                    setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                    setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                    setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                    isXIncludeAware = false
                    isExpandEntityReferences = false
                }

                // XXE 回归探针：确认上面的加固真生效、不是摆设注释（同 L165 精神——断言须配一枚能让它翻红的
                // 变异）。探针实体指向的路径本就不存在，测试只断言「解析器在碰到 <!DOCTYPE 那一刻就拒绝」，
                // 不依赖该路径真实存在/可达（disallow-doctype-decl=true 会抢在任何实体解析尝试之前抛出），
                // 离线可跑、跨平台一致。三份真实 XML 均无 DOCTYPE 声明，加固不影响它们的正常解析。
                val maliciousXml = """
                    <?xml version="1.0"?>
                    <!DOCTYPE probe [<!ENTITY xxe SYSTEM "file:///xxe-hardening-probe-should-never-resolve">]>
                    <probe>&xxe;</probe>
                """.trimIndent()
                val rejectedMaliciousDoctype = try {
                    dbf.newDocumentBuilder().parse(InputSource(StringReader(maliciousXml)))
                    false
                } catch (e: Exception) {
                    true
                }
                check(rejectedMaliciousDoctype) {
                    "XML 解析器未拒绝 DOCTYPE/外部实体——XXE 加固失效（见本 doLast 开头 dbf 的 setFeature/setAttribute）"
                }

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
