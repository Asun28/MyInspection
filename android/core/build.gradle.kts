// :core — 纯 JVM 领域模块（禁 android import，见 CLAUDE.md 架构大图 / 关键不变量）。
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(17)
}

// T1-SCHEMA-CORE：.sq 是全量 schema 真相源（version 1 = 零 .sqm，SQLDelight 官方约定「first schema
// version is 1」——.sqm 按「迁移起点版本号」命名，v1 本身无需迁移文件；未来加表/改列才落新 .sqm）。
//
// `verifyMigrations` 故意不开：它需要一份已提交的 `<version>.db` 快照作对照基线，而
// `scripts/check-secrets.ps1` 无条件 fatal-block 任何被追踪的 `*.db` 文件、无按文件豁免机制——两者结构性
// 不兼容，非本卡实现问题。完整复现步骤与两条候选修法见 TD4（specs/tech-debt-tracker.md）与卡片「验收」说明。
// 影响面很小：version 1 零 .sqm 本无迁移可验，真正开始起作用是第一次加表/改列（第一份 .sqm）起，届时先还清 TD4。
sqldelight {
    databases {
        create("MyInspectionDatabase") {
            packageName.set("nz.myinspection.core.db")
        }
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // SQLDelight 运行时：schema 由本卡（T1-SCHEMA-CORE）落地，见上方 sqldelight{} 配置块。
    implementation(libs.sqldelight.runtime)

    testImplementation(libs.kotlin.test)
    // 运行器用 TestNG（Apache-2.0）不用 kotlin-test-junit：后者传递拉 junit:junit（EPL-1.0，许可禁列）。
    // 见 libs.versions.toml 该条目注释的完整许可核验链路。
    testImplementation(libs.kotlin.test.testng)
    // JVM SQLite driver 供后续卡在 :core 单测里跑数据库迁移校验（Robolectric/仪器测试之外的确定性路径）
    testImplementation(libs.sqldelight.driver.sqlite)
}

tasks.test {
    useTestNG()
}
