---
id: T3-PDF-RENDER-DEVICE
title: Android PdfDocument executor for the render program and tested lifecycle
depends_on: [T3-PDF-RENDERER, T1-SPIKE-PLATFORM, T3-PDF-TEXT-METRICS-OPS, T3-PDF-ANDROID-TEXT-MEASURER, T3-PDF-IMAGE-BRIDGE]
parallelizable_with: []
status: todo
branch: T3-PDF-RENDER-DEVICE
worktree: C:\wt\T3-PDF-RENDER-DEVICE
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/export/pdf/PdfExecutionPort.kt
  - android/app/src/main/kotlin/nz/myinspection/app/export/pdf/PdfRenderExecutor.kt
  - android/app/src/main/kotlin/nz/myinspection/app/export/pdf/AndroidPdfExecutionPort.kt
  - android/app/src/test/kotlin/nz/myinspection/app/export/pdf/PdfRenderExecutorTest.kt
forbid:
  - 执行器重排、重新分页、测量、换行、tail-ellipsis 或改变布局；不复制或更改已交付 IMAGE-BRIDGE 的采样、FIT_CENTER 或资源逻辑
  - 引入 PDF 三方库（iText=AGPL 禁；平台 PdfDocument 足够，ADR-0003）
  - 修改/复制字体资产、NOTICE 或 LICENSE-POLICY；这些只归前置测量器
non_goals:
  - 字体加载、Paint/Typeface metrics、文本 wrap/ellipsis、资产许可登记（T3-PDF-ANDROID-TEXT-MEASURER）
  - bounds/decode、固定 FIT_CENTER、图片源流与位图生命周期实现（T3-PDF-IMAGE-BRIDGE）；本卡只接线并验证跨页集成
  - 质量档位、dpi、采样算术、mm→pt 几何、产物路径派生（T3-PDF-RENDERER 已定，只消费不重判）
  - 落盘位置、原子发布、重开逐字节核验与 receipt（T3-REPORT-EXPORT-CORE）；完整真机验收（T3-PDF-DEVICE-ACCEPTANCE）
  - 导出 UI、系统分享、chooser、应用内阅读（T3-REPORT-EXPORT-UI）
plan_ref: context/DESIGN.md#backup-report-health-and-compliance-component-matrix
acceptance:
  - "A1 the executor consumes a PdfRenderProgram and issues one page start, its ops, and one page finish per page, forwarding text metrics unchanged and delegating each image op to the delivered IMAGE-BRIDGE without deriving layout, dpi, path, fit or text measurements"
  - "A2 every successfully started page receives a finish attempt and every created document receives a close attempt, including when drawing or finishing fails; success requires page finalization, write and document close to complete, and any failure leaves no completed-artifact claim"
  - "A3 every ImageOp invokes the delivered real image bridge before the next op; an integration test uses that bridge with fake native image resources to prove consecutive and cross-page cleanup and propagation of image failures into page/document cleanup"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest
dod_exit: 0
dod_assert: assembleDebug 绿；:app:testDebugUnitTest 绿且覆盖不依赖 Android 运行期的执行器状态机（页开/页闭配对、失败不宣称成功、位图存活计数）；执行器仅画前置已测量的 PdfTextOp，任何 remeasurement/wrap/ellipsis 均失败。完整真机验收由 T3-PDF-DEVICE-ACCEPTANCE 承接，本卡通过不代表真机或完整导出已验收。
review_gate: codex {verdict:pass}
hygiene: named compiling mutations omit page/document cleanup, return before write/close, alter text metric forwarding or bypass the delivered image bridge; sampling/fitting/bitmap mutation evidence remains owned by IMAGE-BRIDGE
doc_sync: ADR-0007 + TASK-BOARD 备注（R5）
---

# T3-PDF-RENDER-DEVICE

## 产出与边界

`app/export/pdf` 的薄执行器把 `T3-PDF-RENDERER` 产出的 `PdfRenderProgram` 用 `android.graphics.pdf.PdfDocument`、`Canvas` 按序执行。它不判断布局、dpi、路径、文本度量或 wrap：`PdfTextOp` 由 `ReportComposer`/builder 携带前置 measurement 后准备，执行器直接 draw。每个 ImageOp 调用已交付的 IMAGE-BRIDGE；本卡负责平台调用顺序、页/文档关闭和真实桥接的集成。

FIT_CENTER、bounds→既有采样→decode→draw→recycle 及图片源流失败路径已在 RED 前拆给 T3-PDF-IMAGE-BRIDGE，不得在执行器中重写。保留一项使用真实 bridge 加 fake native resources 的跨页/连续图片集成；不能用所有图片调用均为 fake 的测试冒称真实接线。

`:app` 单测为纯 JVM，直接 Android graphics API 会 `Stub!`；故页开闭和失败状态机必须针对可替换窄端口测试，真正 PdfDocument adapter 保持直调薄层。一次只 start 一页；每个 ImageOp 等待 bridge 完成或抛错再前进。成功开始的页必须尝试 finish，创建的 document 必须尝试 close；即使 finish 自身抛错也必须尝试 close，但不把抛错的关闭调用声称为已成功释放。只有页结束、write 和 close 均成功才返回成功。无资产、字体、许可文件、落盘/发布或真机视觉义务。

## RED、DoD 与预算

执行器接收调用方拥有的输出流或等价窄 write 端口：所有已开始页结束后 writeTo，随后关闭文档，再返回成功；不能先关闭 PdfDocument 才尝试写出。路径选择与输出流的打开/关闭归调用方，原子发布和 receipt 仍归 EXPORT-CORE。字体按 PdfTextOp 的 font role 复用前置测量器交付的同一解析者，不另行选择、加载字体或测量。

先以 fake port 写页配对、关闭顺序、失败路径、文本参数转交及真实 image bridge 集成 RED，再接平台 thin adapter。覆盖 create/start/draw/finish/write/close 的独立失败、draw+finish 和 finish+close 组合失败、空页、多页混合指令以及调用方输出流不被关闭。只要求必要清理均有尝试且失败不宣称成功，不新增通用异常聚合框架或主异常优先级契约。DoD/R4 见 front-matter；真实 80 照、CJK、High 铭牌、内存、大小行为和附录证据完整保留给 `T3-PDF-DEVICE-ACCEPTANCE`。

具体端口与行为测试清点把未拆完整预算修正为 688–894 行，故现在提前拆 IMAGE-BRIDGE。本卡剩余文件预测：PdfExecutionPort.kt30–40，PdfRenderExecutor.kt45–60，AndroidPdfExecutionPort.kt65–85，PdfRenderExecutorTest.kt120–155，真实 bridge 集成15–25，R4摘要15–20；候选290–385加修复73–96为363–481行/24k–35k字符。仍须在前置 resolver/OPS 实际交付后、RED 前重算；650行或45k字符即停下重划，不压缩测试或遗漏验收。作者 GPT-5.6 Terra high；独立正式 R3 GPT-5.6 Sol high。
