---
id: T3-PDF-ANDROID-TEXT-MEASURER
title: Android Paint/Typeface text measurement adapter and packaged CJK fallback
depends_on: [T3-PDF-TEXT-METRICS-OPS, T1-SPIKE-PLATFORM]
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

真实 `Typeface`、CJK glyph、baseline clipping 与 80 照的视觉证据由后继 `T3-PDF-DEVICE-ACCEPTANCE` 在真机取证；本卡不得把 JVM fake 当作该证据。测量器返回既有 TextMeasurer 的 snapshot；已交付的 `ReportComposer` 与前置 `T3-PDF-TEXT-METRICS-OPS` builder 将 measurement 携带到 `PdfTextOp`，执行器只消费它，不再测量、wrap 或 tail-ellipsis。

## RED、DoD 与预算

字体加载同时提供供后继执行器复用的最小 font-role → Typeface 解析边界；测量与绘制必须使用同一角色解析和同一字体来源。此处只约定职责，不预造尚未交付的方法签名。执行器不得另行加载或选择竞争字体；解析边界不进行 Canvas 绘制或重新测量。

### 实测后的测量规则（2026-09-17，RED 前裁定）

独立诊断 APK `78b92fe4ebf14340deddc962b2489315c2a6aa21a18cf9cc35de7e1c780ff100` 在 API 33 真机与 API 35 模拟器实测：Latin TITLE/CAPTION 的主字体全局范围分别约 15.9258/11.9443pt，超出 DEFAULT 最小 14/11pt 行框；CJK 的主字体范围也不能覆盖所有回退字形。不得以主字体 FontMetrics、固定角色基线或静态字体表替代本次文字边界。这只是提前诊断，不是候选测量器或 PDF 的设备验收。

选择保守的公开 `Paint.getTextBounds(String, start, end, Rect)`：在 PDF point 空间、相同 Typeface 与 Paint 文字配置下，取得本次全部最终换行字符串的整数字形边界，再无损转为 Double；不挑较小的 path 边界使检查通过。API 依据为 [AOSP Android 15 r3 Paint.java](https://android.googlesource.com/platform/frameworks/base/+/android-15.0.0_r3/graphics/java/android/graphics/Paint.java)，blob `df95a91d72d7d5fcf0c3e17202517a761a30aa3d`。不承诺此 API 结果等同矢量轮廓或最终栅格抗锯齿范围，后继真实 PDF 视觉验收仍不可省略。

一个 MeasuredText 只有一个 snapshot：先拒绝非有限或倒置的端口边界，再对本次所有最终行取 `T=min(0, line tops)`、`B=max(0, line bottoms)`。令 `H=PdfGeometry.mmToPt(selectedProfile.lineHeightMm)`；`B-T>H` 必须明确失败，否则 `baseline=(H-B-T)/2`，返回该 T/B/baseline 和本次完整请求绑定。包含零只扩大有符号范围，不截断任何字形；禁止 epsilon 放行、改字号/行高、丢行或临时改成逐行基线。Composer 的 local guard 与 OPS 的实际 placed-box guard 保留。

逐行各自能放下不等于 union 能放下；测试须覆盖两行分别 fit 而 union 不 fit、后续行扩展上下边界、精确边界/越界、不同请求不可复用 snapshot，以及全部最终行实际送入端口。空边界只有在本行明确为空白且平台证据无墨迹时才可贡献空集合；非空白的空边界明确拒绝。全部为空白时 T=B=0、baseline=H/2，仍保留已有非空字符串/非空 lines 契约，不能制造空行或将未知字形当空白。

已知保守限制须如实保留：两设备的 accented TITLE 样本整数字形范围为 [-12,3]、高 15pt，因此 DEFAULT 的 14pt 框明确拒绝；不得把其较小 path 结果用作静默回退。本卡不交付任意字体轮廓覆盖引擎，不调整 DEFAULT。fake-port 测试证明计算与拒绝规则，不能替代实际文字或回退字形证据。

此规则增加约 70–130 行/6k–11k 字符的实现、行为测试和变异预留；完整预测相应为 380–570 行/34k–51k 字符，尚非实际 diff。RED 前必须按具体设计重算并保持修复余量；达到现有 650 行/50k 停线即先拆卡，不以删除以上边界测试压缩预算。

先写 fake-port RED，覆盖 profile/language/role 的精确转交、最大宽度边界与 snapshot/linebox，随后新增薄 Android adapter；不得新引依赖。R4 与 DoD 见 front-matter。

初始估计为 310–440 changed lines / 28k–40k characters：生产 140–185、直接测试 115–160、许可登记 10–16、R4 与修复余量 45–80；当前必须加入上节实测规则，不能沿用此初始总数。默认 `git diff` 若识别同字节 asset 的 rename，字体与 189 行 NOTICE 记为 0 文本改行；3,451,900-byte 字体仍算资产审阅负担。保守回退（rename 未识别）须在当前完整预测上再计 NOTICE 删除+新增共 378 行/约21.4k 字符，已超停线，必须在 RED 前拆 asset/许可迁移，不能等到 ship。RED 前以 `review.ps1 -SizeOnly` 的实际完整 diff 重算；达到 650 行或 50k 字符先拆 adapter 与 asset/许可迁移，不删验收。首选 GPT-5.6 Terra · high；真实 Paint 度量、CJK fallback 与 wrap 边界需要该 effort；预算不因 effort 提高而扩大。若 core contract 实际形状需跨层改动则停止并回卡，不扩大 allow_paths。
