---
id: T0-APP-TEST-SOURCE-INPUTS
title: Declare the source files that :app source-reading tests read as Gradle test inputs
status: todo
depends_on: [T3-PDF-IMAGE-BRIDGE]
parallelizable_with: []
allow_paths:
  - android/app/build.gradle.kts
forbid:
  - Disabling or bypassing incremental builds or the build cache (--rerun-tasks is the workaround, not the fix)
  - Declaring the whole source tree or unrelated directories as test inputs
  - Changing any test, production source or the tests' file lookup
non_goals:
  - Moving the pinned sources to classpath resources
  - The same audit for android/core, already done by T0-GRADLE-RUNTIME-FILE-INPUTS (PR #188)
acceptance:
  - "A1 RED first: before the declaration, a comment-only edit to android/app/src/main/kotlin/nz/myinspection/app/export/pdf/AndroidPdfImagePort.kt followed by :app:testDebugUnitTest --tests nz.myinspection.app.export.pdf.AndroidPdfImagePortTest without --rerun-tasks exits 0 with the task UP-TO-DATE or FROM-CACHE; the same edit with --rerun-tasks --no-build-cache exits 1. Both exit codes and the task outcome are recorded in the card"
  - "A2 After the declaration the same edit without either flag exits 1, and restoring the file returns exit 0"
  - "A3 The declared set comes from a full search of android/app/src/test for tests that read repository files at run time (user.dir walks and src/main/kotlin paths); every hit and its target file is listed in the card, and each target is declared"
  - "A4 Targets are declared as specific files with relative path sensitivity, so an edit to an undeclared source file does not invalidate the test task (shown with a dry run or an up-to-date check)"
  - "A5 A single-statement deletion mutant that removes the declaration makes A2 pass again (exit 0), proving the declaration is what turns the edit red"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest; if ($LASTEXITCODE -ne 0) { exit 1 }; if (-not (Select-String -Path android/app/build.gradle.kts -SimpleMatch 'AndroidPdfImagePort.kt' -Quiet)) { exit 1 }
dod_exit: 0
dod_assert: debug assembly and every app JVM test pass on a forced rerun, and android/app/build.gradle.kts declares the adapter source as a test input; A1, A2 and A5 exit codes are recorded evidence
review_gate: codex {verdict:pass}
hygiene: one single-statement deletion mutant for the declaration (A5); no new plugin or test framework
doc_sync: TASK-BOARD
---

# T0-APP-TEST-SOURCE-INPUTS

Follow-up from the R3 of `T3-PDF-IMAGE-BRIDGE` (2026-09-23), opened by user decision. `AndroidPdfImagePortTest`
pins the exact text of the device-only adapter because no JVM test can run BitmapFactory or Canvas (L280). The test
finds the file by walking up from `user.dir`, so Gradle does not know it is an input. An edit that leaves the
compiled bytecode identical, such as a comment, can leave `:app:testDebugUnitTest` UP-TO-DATE or served from the
build cache, and the pin then reports green on text nobody reviewed. `android/core` closed the same gap for its
source-reading tests in PR #188 (`T0-GRADLE-RUNTIME-FILE-INPUTS`); this card does it for `:app`.
