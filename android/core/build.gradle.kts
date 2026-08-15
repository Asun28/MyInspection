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
// version is 1」——.sqm 按「迁移起点版本号」命名，v1 本身无需迁移文件；未来加表/改列才落新 .sqm，同时
// 生成新版本号的 .db 快照，此前各版本快照保持不动）。
// schemaOutputDirectory 必须显式设置：留空时 GenerateSchemaTask 根本不注册，verifyMigrations 挂不上
// 有效检查（实测核验，非凭记忆）。产出的 src/main/sqldelight/databases/1.db 是 version 1 的基线快照，
// 被根 .gitignore 的防泄露 `*.db` 通配盖住（那条规则防的是运行时数据库/凭据，不是这份纯结构快照）——
// 已用 `git add -f` 显式纳入，比照 .gitignore 自带的 `!data/README.md` 同类先例，未改 .gitignore 本身。
sqldelight {
    databases {
        create("MyInspectionDatabase") {
            packageName.set("nz.myinspection.core.db")
            verifyMigrations.set(true)
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
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
