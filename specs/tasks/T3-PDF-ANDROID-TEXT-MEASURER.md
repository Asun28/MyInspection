---
id: T3-PDF-ANDROID-TEXT-MEASURER
title: Android Paint/Typeface text measurement adapter and packaged CJK fallback
depends_on: [T3-PDF-TYPOGRAPHY-CONTRACT, T1-SPIKE-PLATFORM]
parallelizable_with: [T1-APP-STORAGE-POLICY]
status: todo
branch: T3-PDF-ANDROID-TEXT-MEASURER
worktree: C:\wt\T3-PDF-ANDROID-TEXT-MEASURER
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/export/pdf/
  - android/app/src/test/kotlin/nz/myinspection/app/export/pdf/
  - android/app/src/debug/assets/fonts/DroidSansFallback.ttf
  - android/app/src/debug/assets/fonts/NOTICE
  - android/app/src/main/assets/fonts/DroidSansFallback.ttf
  - android/app/src/main/assets/fonts/NOTICE
  - docs/LICENSE-POLICY.md
forbid:
  - PdfDocument、Canvas、BitmapFactory、FileOutputStream 或执行器生命周期；这些只归 T3-PDF-RENDER-DEVICE
  - 新 PDF/字体/测试运行期依赖，Robolectric，android.* 直接出现在 JVM 测试的被测路径
  - 新分页、图片采样、文件路径/发布策略、按内容猜测 language/font role 或第二套 typography defaults；正常 wrapping 迭代与复用既有 PdfGeometry 换算允许，但不得复制换算算法
  - 复制 debug 与 main 的同逻辑 asset 路径；必须一次 git move，保持单一来源，避免 source-set 覆盖优先级漂移
non_goals:
  - PdfRenderExecutor、页开闭、位图 decode/recycle、真实80照验收（分别归 T3-PDF-RENDER-DEVICE 与 T3-PDF-DEVICE-ACCEPTANCE）
  - 重新定义 ReportTypography、TextLanguage、TextMeasurer core contract 或重排 ReportComposer
  - export receipt、分享/UI、任何新依赖或字体替换
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug :app:assembleRelease; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -Command '$h=@{"assets/fonts/DroidSansFallback.ttf"="21B96A0377F067833A93AF3082EB28D4FFAB7A8CD46BFD513286F1D64B7B0949";"assets/fonts/NOTICE"="92D336191C9EC51CC39F0B2FC44E5153F685C661C3D1E44E088FAD4E68358AB2"}; Add-Type -AssemblyName System.IO.Compression.FileSystem; foreach($m in @("android/app/build/outputs/apk/debug/output-metadata.json","android/app/build/outputs/apk/release/output-metadata.json")){ $j=Get-Content -Raw $m|ConvertFrom-Json; $f=@($j.elements|ForEach-Object {$_.outputFile}|Where-Object {$_}); if($f.Count -ne 1){throw "expected one APK output in $m"}; $a=Join-Path (Split-Path $m) $f[0]; if(!(Test-Path -LiteralPath $a)){throw "missing APK $a"}; $z=[IO.Compression.ZipFile]::OpenRead($a); try { foreach($n in $h.Keys){ $e=$z.GetEntry($n); if($null -eq $e){throw "missing $n in $a"}; $q=$e.Open(); try { $x=(-join ([Security.Cryptography.SHA256]::Create().ComputeHash($q)|ForEach-Object ToString x2)).ToUpperInvariant(); if($x -ne $h[$n]){throw "hash mismatch $n in $a"} } finally {$q.Dispose()} } } finally {$z.Dispose()} }'
dod_exit: 0
dod_assert: assembleDebug/assembleRelease 与 :app:testDebugUnitTest 绿；JVM fake metrics port 覆盖 profile/language/role 绑定、宽度边界的换行结果以及 snapshot/linebox 原样交回，不触碰 Android Stub。资产以一次 git move 从 debug/assets/fonts/ 到 main/assets/fonts/；debug 与 release APK 均应包含同路径、同 SHA-256 的 assets/fonts/DroidSansFallback.ttf，debug probe 仍通过相同路径解析；不得有重复来源。LICENSE-POLICY 精确登记 Apache-2.0、AOSP android-15.0.0_r3 来源、字体 SHA-256 21b96a0377f067833a93af3082eb28d4ffab7a8cd46bfd513286f1d64b7b0949、打包 NOTICE SHA-256 92d336191c9ec51cc39f0b2fc44e5153f685c661c3d1e44e088fad4e68358ab2 与无新增运行时依赖。此 DoD 不声称真实 CJK glyph/clipping 或设备 Paint 结果。
review_gate: codex {verdict:pass}
hygiene: R4 以具名单点变异证明不能丢 profile/language/role 绑定、不能改宽度边界、不能替换或丢失 snapshot/linebox；资产 move 与许可登记以路径/哈希断言保留一条非模板验证
doc_sync: ADR-0007 + TASK-BOARD dependency note after merge（R5）
---

# T3-PDF-ANDROID-TEXT-MEASURER

## 产出与边界

本卡实现 `AndroidReportTextMeasurer`：它消费前置的 `ReportTypography`、`TextLanguage` 和 font role，以 `Paint`/`Typeface` 通过窄 `FontMetricsPort` 产生既有 `TextMeasurer` 所需的换行与 `TextMetricSnapshot`。生产 Android adapter 只把平台调用封进端口；纯 JVM 测试用 fake port 验证适配逻辑，避免 Android Stub。它不修改 core contract，不做分页、几何或 PDF 执行；只跟随前置最终交付的 profile，不把 TITLE/BODY/CAPTION 默认值写入 app。

字体资产必须以一次 git move 将现有 `android/app/src/debug/assets/fonts/DroidSansFallback.ttf` 与完整 `NOTICE` 移到同名 `src/main/assets/fonts/`。Android debug 变体包含 main assets，因此既有 `PdfStressProbe` 仍从完全相同 `fonts/DroidSansFallback.ttf` 加载；保留 debug 副本会制造两个可覆盖来源，禁止复制；迁移的目的是单一来源而非假定某一构建工具报错。`docs/LICENSE-POLICY.md` 只增加这一个精确 Apache-2.0 资产行：来源 AOSP `android-15.0.0_r3`、Git blob `1099b177c881c96bf298989dcdca5fda7841133d`、字体 SHA-256 `21b96a0377f067833a93af3082eb28d4ffab7a8cd46bfd513286f1d64b7b0949`、打包 NOTICE SHA-256 `92d336191c9ec51cc39f0b2fc44e5153f685c661c3d1e44e088fad4e68358ab2`，并声明没有 Gradle/runtime 依赖。

真实 `Typeface`、CJK glyph、baseline clipping 与 80 照的视觉证据由后继 `T3-PDF-DEVICE-ACCEPTANCE` 在真机取证；本卡不得把 JVM fake 当作该证据。测量器返回既有 TextMeasurer 的 snapshot；后继 `ReportComposer`/builder 才把此前置 measurement 携带到 `PdfTextOp`，执行器只消费它，不再测量、wrap 或 tail-ellipsis。

## RED、DoD 与预算

先写 fake-port RED，覆盖 profile/language/role 的精确转交、最大宽度边界与 snapshot/linebox，随后新增薄 Android adapter；不得新引依赖。R4 与 DoD 见 front-matter。

实现目标 310–440 changed lines / 28k–40k characters：生产 140–185、直接测试 115–160、许可登记 10–16、R4 与修复余量 45–80。默认 `git diff` 若识别同字节 asset 的 rename，字体与 189 行 NOTICE 记为 0 文本改行；3,451,900-byte 字体仍算资产审阅负担。保守回退（rename 未识别）把 NOTICE 删除+新增共 378 行/约21.4k 字符计入，完整预测为 688–818 行 / 49.4k–61.4k characters；超过约650行或50k字符时必须在 RED 前拆 asset/许可迁移，不能等到 ship。RED 前以 `review.ps1 -SizeOnly` 的实际完整 diff 重算；达到 650 行或 50k 字符先拆 adapter 与 asset/许可迁移，不删验收。首选 GPT-5.6 Terra · high；真实 Paint 度量、CJK fallback 与 wrap 边界需要该 effort；预算不因 effort 提高而扩大。若 core contract 实际形状需跨层改动则停止并回卡，不扩大 allow_paths。
