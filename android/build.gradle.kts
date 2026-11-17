// android/build.gradle.kts — 根构建脚本：只声明各子模块要用的插件版本（apply false），不承载业务逻辑。
// :app 用 AGP 内置 Kotlin（T0-AGP-BUILTIN-KOTLIN）：下面以 2.4.10 声明的 Kotlin 插件把 kotlin-gradle-plugin 2.4.10
// 放上构建 classpath，AGP 自带的 2.2.10 因此解析成 2.4.10，:app 与 :core 用同一个 Kotlin。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sqldelight) apply false
}
