---
id: T3-PDF-RENDER-DEVICE
title: Android PdfDocument executor for the render program and tested lifecycle
depends_on: [T3-PDF-RENDERER, T1-SPIKE-PLATFORM, T3-PDF-TEXT-METRICS-OPS, T3-PDF-ANDROID-TEXT-MEASURER]
parallelizable_with: []
status: todo
branch: T3-PDF-RENDER-DEVICE
worktree: C:\wt\T3-PDF-RENDER-DEVICE
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/export/pdf/
  - android/app/src/test/kotlin/nz/myinspection/app/export/pdf/
forbid:
  - 执行器重排、重新分页、测量、换行、tail-ellipsis 或任何布局判断（程序说画哪就画哪；布局缺陷回 T3-REPORT-COMPOSER，测量缺陷回 T3-PDF-ANDROID-TEXT-MEASURER，采样/几何缺陷回 T3-PDF-RENDERER）
  - 引入 PDF 三方库（iText=AGPL 禁；平台 PdfDocument 足够，ADR-0003）
  - 修改/复制字体资产、NOTICE 或 LICENSE-POLICY；这些只归前置测量器
non_goals:
  - 字体加载、Paint/Typeface metrics、文本 wrap/ellipsis、资产许可登记（T3-PDF-ANDROID-TEXT-MEASURER）
  - 质量档位、dpi、采样算术、mm→pt 几何、产物路径派生（T3-PDF-RENDERER 已定，只消费不重判）
  - 落盘位置、原子发布、重开逐字节核验与 receipt（T3-REPORT-EXPORT-CORE）；完整真机验收（T3-PDF-DEVICE-ACCEPTANCE）
  - 导出 UI、系统分享、chooser、应用内阅读（T3-REPORT-EXPORT-UI）
plan_ref: context/DESIGN.md#backup-report-health-and-compliance-component-matrix
acceptance:
  - "A1 the executor consumes a PdfRenderProgram and issues one page start, its ops, and one page finish per page, deriving no geometry, dpi, path or text measurement of its own"
  - "A2 a started page is always finished and the document is always closed before any success is returned, and a failure anywhere leaves no completed-artifact claim"
  - "A3 每槽先读取 source bounds，再仅调用既有 PdfImageSampling.inSampleSize(source,target) 取得采样值后 decode；不复制采样算术，draw 后立刻 recycle，同一时刻至多一张已解码位图存活"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest
dod_exit: 0
dod_assert: assembleDebug 绿；:app:testDebugUnitTest 绿且覆盖不依赖 Android 运行期的执行器状态机（页开/页闭配对、失败不宣称成功、位图存活计数）；执行器仅画前置已测量的 PdfTextOp，任何 remeasurement/wrap/ellipsis 均失败。完整真机验收由 T3-PDF-DEVICE-ACCEPTANCE 承接，本卡通过不代表真机或完整导出已验收。
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4），页开/页闭配对、「失败不宣称成功」、单位图存活及禁止重测量各配一枚具名单点变异
doc_sync: ADR-0007 + TASK-BOARD 备注（R5）
---

# T3-PDF-RENDER-DEVICE

## 产出与边界

`app/export/pdf` 的薄执行器把 `T3-PDF-RENDERER` 产出的 `PdfRenderProgram` 用 `android.graphics.pdf.PdfDocument`、`Canvas`、`BitmapFactory` 按序执行。它不判断几何、dpi、路径、文本度量或 wrap：`PdfTextOp` 由 `ReportComposer`/builder 携带前置 measurement 后准备，执行器直接 draw。它只守住平台调用顺序、页/文档关闭和单张位图生命周期。

`:app` 单测为纯 JVM，直接 Android graphics API 会 `Stub!`；故页开闭、失败与位图存活状态机必须针对可替换窄端口测试，真正 PdfDocument adapter 保持直调薄层。一次只 start 一页；每个 ImageOp 读取 bounds，仅调用 `PdfImageSampling.inSampleSize`，decode → draw → 立即 recycle；已开始页面总会 finish，document 在任何成功前 close，失败不宣称产物完成。无资产、字体、许可文件、落盘/发布或真机视觉义务。

## RED、DoD 与预算

先以 fake port 写页配对、关闭顺序、失败路径、采样转交和位图计数 RED，再接平台 thin adapter。DoD/R4 见 front-matter；真实 80 照、CJK、High 铭牌、内存、大小行为和附录证据完整保留给 `T3-PDF-DEVICE-ACCEPTANCE`。

完整 diff 目标 430–580 changed lines / 30k–44k characters：生产 180–250、直接测试 170–230、R4 与修复余量 80–100。NOTICE、字体与许可证已归前置，不能借此卡压缩或更改。RED 前按完整 diff 重算；预估达到 650 行或 45k 字符即按生命周期与 image bridge 再拆，不削弱 A1–A3。首选 GPT-5.6 Terra · medium；平台端口或异常关闭边界复杂时升为 high。
