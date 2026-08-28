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
        resources.include("routine-v1.json", "phrases-v1.json", "README.md")
    }
}

// T3-E2E-GATE-ISOLATION：Golden Evidence 是 Gate 2 的完整闭环，不属于普通单测。
// 独立 source set 防止 :core:test/:core:check 编译或执行它；真实模板仍作为该闭环的只读 classpath resource。
val e2eTest = sourceSets.create("e2eTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
    resources.srcDir("../../data/templates")
    resources.include("e2e/**", "routine-v1.json")
}

// These tests read repository files directly instead of through their compiled runtime classpath. Keep the
// inventory exact so unrelated repository edits do not invalidate :core:test.
/*
 * Runtime-input verification receipt (2026-08-29; every temporary mutation was restored).
 * Commands below ran from the repository root; "plain" means no --rerun-tasks or --no-build-cache.
 *
 * A1 | Mutation: append "ANNUAL" to configs/compliance/nz-rules-v1.json at
 *    rules.inspection.frequencyLimit.exemptTypes. Plain command:
 *    cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test
 *      --tests "nz.myinspection.core.compliance.*"
 *    Before this declaration: :core:test UP-TO-DATE, exit 0. Adding --rerun-tasks: exit 1;
 *    failure = "expected [[INGOING, EXIT]] but found [[INGOING, EXIT, ANNUAL]]".
 * A2 | With this declaration and the same mutation, the plain command executed and exited 1 with the
 *    same failure. Restoring the JSON and repeating the plain command exited 0.
 * A3 | Inventory command:
 *    rg -n 'System\.getProperty\("user\.dir"\)|getResourceAsStream|Files\.readString|
 *      Files\.readAllBytes|\.readText\(Charsets\.UTF_8\)' android/core/src/test/kotlin/nz/myinspection/core
 *    ComplianceEngineTest -> configs/compliance/nz-rules-v1.json; ReportSourcePurityTest -> every
 *    Kotlin file in the report test package; PhotoOrphanCleanupWiringTest -> CameraPhotoIngestPipeline,
 *    PhotoImportPipeline, PhotoIngestPendingLease, PendingPhotoLease, PhotoRuntimeStorage,
 *    PhotoOrphanCleanupWorker,
 *    PhotoDirectoryDurability, PhotoAssetCleanupExecutor, NoFollowLeafDeletion,
 *    PhotoOrphanCleanupScheduler, and MainActivity; PhotoStreamingWiringTest -> PhotoJpegEncoder,
 *    CameraPhotoIngestPipeline, PhotoImportPipeline, and PhotoQualitySettings. RoutineContentTest and
 *    PhraseLibraryContentTest read routine-v1.json and phrases-v1.json from the test classpath.
 * A4 | The inventory below uses explicit files/includes. Probe: append " [A4]" to a same-line comment
 *    in unlisted app/media/PhotoBitmapScaler.kt, then run `cmd /c android\gradlew.bat -p android
 *    --offline --no-daemon -q :core:test --tests
 *    "nz.myinspection.core.media.PhotoStreamingWiringTest"`; :core:test remained UP-TO-DATE, exit 0.
 *    Restoring the comment changed no declared input.
 * A5 | RoutineContentTest hashes the sorted sequence of every stableId/textEn/textZh tuple against an
 *    independent literal. Mutation: append " [A5]" to previously unpinned LNG-WALL-01.textZh. Command:
 *    cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test
 *      --tests "nz.myinspection.core.content.*"
 *    processTestResources reran and the command exited 1; failure = "stableId/textEn/textZh tuple digest
 *    drifted expected [88e176b5442050532b520e5b561c5a77e7984069a686bf1cdeb5c6887e41ac8b]
 *    but found [ead3014b474b7edda1234b68579e4a0bff3e482df164be9c7bc3dfda9e82be24]".
 *    Restored command exit = 0.
 * A6 | cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:check exited 0 in a
 *    detached clean baseline and exited 0 again after these declarations.
 * A7 | Deletion mutation: remove the two-line inputs.file(nz-rules-v1.json) statement, establish a
 *    green baseline, then repeat A1. The plain task returned stale UP-TO-DATE, exit 0; the classifier
 *    `(exit -eq 1) -and (XML contains the A1 failure)` rejected it, exiting 1 with
 *    "[GRADLE-INPUT-A2] deletion mutation survived". Restoring the statement killed the mutation.
 * A8 | Same-line, bytecode-neutral mutation: replace the split `ReportComposer` + `.` text in
 *    ReportComposerGoldenTest.kt with `ReportComposer.Companion`. Run `cmd /c android\gradlew.bat
 *    -p android --offline --no-daemon -q :core:cleanTest`, then `cmd /c android\gradlew.bat -p android
 *    --offline --no-daemon -q :core:test --tests "nz.myinspection.core.report.*"`: :core:test was
 *    FROM-CACHE, exit 0. Repeat cleanTest, then add --no-build-cache to that test command: exit 1 with
 *    "a test names the composer's companion instead of writing the value out". With reportTestSources
 *    declared, the ordinary mutated test command executed and exited 1 with that failure.
 * A9 | A1/A2 are an undeclared file read through user.dir; A8 is a source-text assertion hidden by a
 *    bytecode-equivalent cache hit. Their reproductions and input declarations remain distinct above.
 * A10 | T3 pre-fix command (the plain report command) returned FROM-CACHE, exit 0 under the A8
 *    mutation. Exact hardened command `cmd /c android\gradlew.bat -p android --offline --no-daemon -q
 *    --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.*"` executed the
 *    same mutation and exited 1 with the A8 failure. T4 pre-fix plain command returned UP-TO-DATE,
 *    exit 0 under A1; exact hardened command `cmd /c android\gradlew.bat -p android --offline
 *    --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests
 *    "nz.myinspection.core.compliance.*"` executed it and exited 1 with the A1 failure. After restore,
 *    the exact hardened T3 and T4 commands both exited 0.
 */
val repositoryRoot = layout.projectDirectory.dir("../..")
val reportTestSources = fileTree("src/test/kotlin/nz/myinspection/core/report") {
    include("*.kt")
}
val coreMediaWiringSources = fileTree("src/main/kotlin/nz/myinspection/core/media") {
    include("PendingPhotoLease.kt", "NoFollowLeafDeletion.kt")
}
val appMediaWiringSources = fileTree("../app/src/main/kotlin/nz/myinspection/app/media") {
    include(
        "CameraPhotoIngestPipeline.kt",
        "PhotoAssetCleanupExecutor.kt",
        "PhotoDirectoryDurability.kt",
        "PhotoImportPipeline.kt",
        "PhotoIngestPendingLease.kt",
        "PhotoJpegEncoder.kt",
        "PhotoOrphanCleanupScheduler.kt",
        "PhotoOrphanCleanupWorker.kt",
        "PhotoRuntimeStorage.kt",
    )
}
val otherAppWiringSources = files(
    "../app/src/main/kotlin/nz/myinspection/app/MainActivity.kt",
    "../app/src/main/kotlin/nz/myinspection/app/feature/settings/media/PhotoQualitySettings.kt",
)
val templateContractSources = files(
    "src/main/sqldelight/nz/myinspection/core/db/TemplateVersion.sq",
    "src/main/sqldelight/nz/myinspection/core/db/Supplement.sq",
)

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
    inputs.file(repositoryRoot.file("configs/compliance/nz-rules-v1.json"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(
        reportTestSources,
        coreMediaWiringSources,
        appMediaWiringSources,
        otherAppWiringSources,
        templateContractSources,
    )
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.register<Test>("e2eTest") {
    description = "Runs the Golden Evidence JVM Core E2E suite."
    group = "verification"
    testClassesDirs = e2eTest.output.classesDirs
    classpath = e2eTest.runtimeClasspath
    useTestNG()
    shouldRunAfter(tasks.test)
}
