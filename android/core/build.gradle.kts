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
// `verifyMigrations` 故意不开（卡片原 dod_assert 要求的「verifySqlDelightMigration 挂进 :core:check 且绿」
// 在本项目做不到，非实现质量问题，实测核验如下）：该检查需要 `schemaOutputDirectory` 指向的一份**已提交**
// `<version>.db` 快照作对照基线——留空 schemaOutputDirectory 时 GenerateSchemaTask 根本不注册、verify 任务
// 运行期报「requires a database file to be present」；一旦显式配置并生成该快照，它就是一个真实 SQLite 文件，
// 被 `scripts/check-secrets.ps1` 的防泄露闸按文件名模式（`\.db$`，硬编码、无例外机制）判「已追踪的敏感文件」
// 无条件拦停——已实测复现（生成 1.db → git add -f 纳入 → check-secrets: FAIL 1 项致命）。.gitignore 里
// `确需入库…用 git add -f` 那条注释对这份文件不成立：check-secrets 的拦截独立于 gitignore、无豁免旁路，
// 且 scripts/ 属并行卡领地本卡不可改。故此把 verifyMigrations 基础设施推迟到「本项目防泄露闸支持按文件登记
// 例外」或「改存非 .db 扩展名的快照」之后再开——登记为 TD4（specs/tech-debt-tracker.md），见卡片正文「验收」说明。
// 影响面很小：本卡 schema 是 version 1（零 .sqm），漂移检测原本就无事可检；真正开始咬合是从第一次加表/
// 改列（第一份 .sqm）起，到那时须先还清 TD4 才能开工，不阻塞当下。
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
