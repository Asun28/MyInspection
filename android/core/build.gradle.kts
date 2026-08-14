// :core — 纯 JVM 领域模块（禁 android import，见 CLAUDE.md 架构大图 / 关键不变量）。
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // SQLDelight 运行时（数据库 schema 由后续卡 T1-SCHEMA-CORE 挂 sqldelight{} 配置块，本卡只 pin 依赖）
    implementation(libs.sqldelight.runtime)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    // JVM SQLite driver 供后续卡在 :core 单测里跑数据库迁移校验（Robolectric/仪器测试之外的确定性路径）
    testImplementation(libs.sqldelight.driver.sqlite)
}
