// :core — 纯 JVM 领域模块（禁 android import，见 CLAUDE.md 架构大图 / 关键不变量）。
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(17)
}

// T2-ROUTINE-CONTENT：模板内容真相源在仓库根 `data/templates/`（`.gitignore` 排除，内容文件按
// `git add -f` 显式入库，见该目录 README.md）。这里只把它注册为 **test** resources srcDir——不是
// main/assets：构建期把模板拷进 Android assets 是后续 UI 卡的活，本卡只需测试能读到这份 JSON。
// 测试通过 `getResourceAsStream("/routine-v1.json")` 走 classpath 读取，不随 Gradle 工作目录漂移。
sourceSets {
    test {
        resources.srcDir("../../data/templates")
    }
}

// T3-E2E-GATE-ISOLATION：Golden Evidence 是 Gate 2 的完整闭环，不属于普通单测。
// 独立 source set 防止 :core:test/:core:check 编译或执行它；真实模板仍作为该闭环的只读 classpath resource。
val e2eTest = sourceSets.create("e2eTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
    resources.srcDir("../../data/templates")
}

configurations[e2eTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[e2eTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

// T1-SCHEMA-CORE：.sq 是全量 schema 真相源（version 1 = 零 .sqm，SQLDelight 官方约定「first schema
// version is 1」——.sqm 按「迁移起点版本号」命名，v1 本身无需迁移文件；未来加表/改列才落新 .sqm）。
//
// TD4：`databases/1.db` 是 version 1 的已审 schema baseline；check-secrets 只经精确路径清单放行这一文件。
// `verifyMigrations` 注册的 verifyMainMyInspectionDatabaseMigration 已由 SQLDelight 挂入 :core:check；未来任何
// schema 改动必须带连续 .sqm，使 baseline 经迁移后与当前 .sq 真相源一致。
sqldelight {
    databases {
        create("MyInspectionDatabase") {
            packageName.set("nz.myinspection.core.db")
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
            verifyMigrations.set(true)
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

tasks.register<Test>("e2eTest") {
    description = "Runs the Golden Evidence JVM Core E2E suite."
    group = "verification"
    testClassesDirs = e2eTest.output.classesDirs
    classpath = e2eTest.runtimeClasspath
    useTestNG()
    shouldRunAfter(tasks.test)
}
