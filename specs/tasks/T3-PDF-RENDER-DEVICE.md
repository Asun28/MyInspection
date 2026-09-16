---
id: T3-PDF-RENDER-DEVICE
title: Android PdfDocument executor for the render program, CJK font asset and tested lifecycle
depends_on: [T3-PDF-RENDERER, T1-SPIKE-PLATFORM, T3-PDF-TYPOGRAPHY-CONTRACT]
parallelizable_with: []
status: todo
branch: T3-PDF-RENDER-DEVICE
worktree: C:\wt\T3-PDF-RENDER-DEVICE
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/export/pdf/
  - android/app/src/main/assets/fonts/
  - docs/LICENSE-POLICY.md
  - android/app/src/test/kotlin/nz/myinspection/app/export/pdf/
forbid:
  - 执行器重排、重新分页或任何布局判断（程序说画哪就画哪；布局缺陷回 T3-REPORT-COMPOSER，采样/几何缺陷回 T3-PDF-RENDERER）
  - 引入 PDF 三方库（iText=AGPL 禁；平台 PdfDocument 足够，ADR-0003）
  - 未过 check-licenses 的字体资产
non_goals:
  - 质量档位、dpi、采样算术、mm→pt 几何、产物路径派生（T3-PDF-RENDERER 已定，只消费不重判）
  - 落盘位置、原子发布、重开逐字节核验与 receipt（T3-REPORT-EXPORT-CORE）
  - 导出 UI、系统分享、chooser、应用内阅读（T3-REPORT-EXPORT-UI）
  - 改写或重压已存照片；承诺任意报告的绝对 MB 上限
plan_ref: context/DESIGN.md#backup-report-health-and-compliance-component-matrix
acceptance:
  - "A1 the executor consumes a PdfRenderProgram and issues one page start, its ops, and one page finish per page, deriving no geometry, dpi or path of its own"
  - "A2 a started page is always finished and the document is always closed before any success is returned, and a failure anywhere leaves no completed-artifact claim"
  - "A3 每槽先读取 source bounds，再仅调用既有 PdfImageSampling.inSampleSize(source,target) 取得采样值后 decode；不复制采样算术，draw 后立刻 recycle，同一时刻至多一张已解码位图存活"
  - "A4 AndroidReportTextMeasurer uses the selected typography and font role to produce wrapped lines and actual metric snapshots; JVM tests exercise its logic through a narrow metrics port, while the executor consumes the resulting PdfTextOp without remeasurement"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest
dod_exit: 0
dod_assert: assembleDebug 绿；:app:testDebugUnitTest 绿且覆盖不依赖 Android 运行期的执行器状态机（页开/页闭配对、失败不宣称成功、位图存活计数），并覆盖 AndroidReportTextMeasurer 的 profile 绑定、换行和 snapshot/linebox 校验；正式 ship 的独立 check-licenses 闸必须绿且保存可追溯输出，PR 附字体许可来源链接；字体精确来源及 SHA-256 登记入许可政策；完整真机验收由 T3-PDF-DEVICE-ACCEPTANCE 承接，本卡通过不代表真机或完整导出已验收
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4），页开/页闭配对与「失败不宣称成功」各配一枚具名单点变异，另以一枚变异证明测量适配器不能丢失 profile/snapshot 绑定
doc_sync: ADR-0007 + TASK-BOARD 备注（R5）
---

# T3-PDF-RENDER-DEVICE

## 产出
`app/export/pdf`：把 `T3-PDF-RENDERER` 产出的 `PdfRenderProgram` 用 `android.graphics.pdf.PdfDocument` + `Canvas`/`Paint`/`BitmapFactory` **执行**出来的薄壳，加上 CJK 字体资产与执行器生命周期测试。它**不判断**任何几何、dpi、路径或采样参数——那些全部由程序给定，本卡只负责按序发出平台调用并守住资源生命周期。

## 拆分依据（2026-09-02 用户裁定）
承接 `T3-PDF-RENDERER` 的设备半边。原合卡的三处不可测（`:app` 单测无 Android graphics 运行期、原 DoD 不跑任何 `:app` 测试、重开核验归 `T3-REPORT-EXPORT-CORE`）与约 1660 行的体量，见 `specs/tasks/T3-PDF-RENDERER.md`「拆分依据」。

**本卡的 DoD 因此显式加了 `:app:testDebugUnitTest`**（形态照抄 `T3-REPORT-EXPORT-CORE`），否则 `app/src/test/**` 里的测试永不执行。

## 上下文包（执行模型必读）

### 可测边界（先读这一段再设计）
`:app` 单测是纯 JVM：`android/app/build.gradle.kts` 无 `testOptions { unitTests.returnDefaultValues }`，T0-TOOLCHAIN non_goals 禁 Robolectric 与仪器测试 ⇒ 任何直接触碰 `PdfDocument`/`Canvas`/`Paint`/`BitmapFactory` 的代码在单测里抛 `Stub!`。
**故设计必须把平台调用收进一个可替换的窄端口**（形态参考 `app/media` 的 `PhotoJpegEncoder`/`PhotoBitmapScaler` 分层）：执行器对着端口写状态机 —— 页开/页闭配对、失败路径、位图存活计数 —— 这部分在 JVM 单测里用假端口证明；真正的 `PdfDocument` 实现薄到只有直调，其平台正确性由 `T3-PDF-DEVICE-ACCEPTANCE` 的真机记录承担。**别把状态机写进直调那一层**，那等于把它移出所有闸门之外。

### 前置：V1 PDF spike
`T1-SPIKE-PLATFORM` 的 PDF 压力项（80 张占位图循环渲染 + 逐页 recycle + 中英混排 + `DroidSansFallback.ttf` 字形试，记录峰值内存与耗时）是本卡的实测基线。开工前该卡须已产出 `docs/spike/PLATFORM-SPIKE.md` 的 PDF 压力节结论（V1 三项中的第三项；听写已移 V2）。

### 字体
`assets/fonts/DroidSansFallback.ttf`（Apache-2.0）；`Typeface.createFromAsset` 加载。en 可用平台 sans，zh 一律走 fallback 字体（`TextRun.language` 已由 composer 标好，直接读，不做语言探测）。**PR 里附许可来源链接**，`check-licenses.ps1` 须绿。二进制资产进仓是本卡唯一的大文件，落位仅限 `assets/fonts/`。

### 执行纪律
- 一次只 `startPage` 一页；该页 ImageOp 逐个读取 source bounds，并仅调用核心 `PdfImageSampling.inSampleSize` 将原图尺寸与 program 目标像素转成采样值后解码 → draw → 立刻 `recycle`；页 `finishPage` 后再下一页；`writeTo` 走 `FileOutputStream` 流式。
- **已 start 的页必须 finish、document 必须 close**——`PdfDocument` 官方约束：不得在未 finish 页时 `writeTo`/`close`。失败路径同样要走完关闭，且**关闭之后才可能返回成功**；任何一步失败都不得留下「产物已完成」的声明（落盘/发布/receipt 归 `T3-REPORT-EXPORT-CORE`，本卡只负责不撒谎）。
- 超宽文本按 `PdfTextOp.widthPt` 的既有语义做尾部省略号并记 warning 日志；**不重排**（布局缺陷回 composer 修）。

### 真机验收归属（2026-09-17 用户批准拆分）
完整真实 80 照、四档体积/字形/High 铭牌/内存/附录回链验收全部移至 T3-PDF-DEVICE-ACCEPTANCE，后继 EXPORT-CORE 依赖该卡。页脚 native data_hash 对固定输入核对；semanticFingerprint 与 EXPORT-CORE 回执的一致性在后继卡验证，两者不得混比。旧合成 spike 不能替代该验收。

## 验收 / 执行建议
dod 见 front-matter。首选 Sonnet 5 · max；备选 Opus 5。难度 M。真机部分由后继验收卡中的 Agent 执行。

## 2026-09-17 前置合同修订
文字字号、基线及测量一致性由 T3-PDF-TYPOGRAPHY-CONTRACT 先交付，本卡只消费已定参数。A3 复用核心采样 helper，不自造几何或采样政策。字体复制既有 debug 资产及完整 NOTICE 到 main；不移动或删除 debug 资产。docs/LICENSE-POLICY.md 仅允许新增该字体的精确许可登记行。实现预算目标714/上限789行（含189行NOTICE和R4收据）；写RED前按实际完整diff重新估量。


本卡另以独立 AndroidReportTextMeasurer 窄适配器承接真实 Paint/Typeface 字宽及基线测量，消费前置ReportTypography，并返回一致的MeasuredText快照供composer使用。此适配器只实现既有TextMeasurer换行契约，不变更分页/布局政策；与PdfRenderExecutor分开，后者绝不测量或重排。必须用可替换metrics窄端口在JVM测真实测量适配逻辑。增加此责任后重新量完整预算，原789行估算不再作为保证；超过约800行先拆测量适配器再实现，不删测试/NOTICE降体量。
