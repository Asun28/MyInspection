---
id: T3-PDF-RENDERER
title: Pure JVM PDF render program, four export qualities, geometry and per-page sampling bounds
depends_on: [T3-REPORT-CONTENT-ADAPTER]
parallelizable_with: [T3-HISTORY-COMPARE, T5-BACKUP-IO, T3-REPORT-HTML-RENDERER]
status: merged
branch: T3-PDF-RENDERER
worktree: C:\wt\T3-PDF-RENDERER
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/pdf/
  - android/core/src/test/kotlin/nz/myinspection/core/report/pdf/
forbid:
  - 布局判断混进渲染程序（plan 说画哪就画哪；发现布局缺陷回 T3-REPORT-COMPOSER 修）
  - 引入 PDF 三方库（iText=AGPL 禁；平台 PdfDocument 足够，ADR-0003）
  - 在 :core 里 import android.* / androidx.*（纯 JVM 模块，见 CLAUDE.md 架构大图）
non_goals:
  - 报告语义/受众/隐私/分页（shared content + adapter 已定）；分享、SAF、chooser 与回执（T3-REPORT-EXPORT-CORE/UI）
  - 产物路径派生与形状判定（T3-PDF-ARTIFACT-PATHS，2026-09-02 用户裁定拆出，见「拆分依据」）
  - 任何 Android 运行期渲染：PdfDocument/Canvas/Paint/BitmapFactory、字体资产、真机内存实测（T3-PDF-RENDER-DEVICE）
  - 产物落盘、关闭、重开与逐字节核验（T3-REPORT-EXPORT-CORE A2）
  - 改写或重压已存照片；承诺任意报告的绝对 MB 上限
plan_ref: context/DESIGN.md#backup-report-health-and-compliance-component-matrix
acceptance:
  - "A1 the four qualities build render programs whose drawable semantics and document identity are identical, and differ only in image sampling parameters"
  - "A2 every mm coordinate in the plan converts to points by the single stated rule, A4 pages are 595x842pt, and no emitted operation escapes its page box"
  - "A3 each image slot samples at its purpose's dpi and a page's decoded-byte bound is the maximum over that page's slots, never the sum; no slot is split, dropped or drawn twice"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.*"
dod_exit: 0
dod_assert: PdfExportQuality 的 LOW/MEDIUM/HIGH/EXTRA_HIGH 内联/附录 dpi 与需求 §8 逐档相符且默认 MEDIUM；四档程序的 identity 与可绘语义逐操作相等（仅采样参数不同）；mm→pt 换算与 A4 595x842pt 有定点断言；逐页解码字节上界取该页各槽最大值而非求和且溢出饱和不回绕；composer 既有黄金/分页/adapter 测试保持绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4），每条 acceptance 至少一枚具名单点变异被击杀
doc_sync: ADR-0007 + TASK-BOARD 备注（R5）
---

# T3-PDF-RENDERER

## 产出
`core/report/pdf`：**纯 JVM** 的 PDF 渲染程序层——四档质量契约、mm→pt 几何、逐槽采样与逐页内存上界，以及把 `DocumentPlan` 翻译成有序绘制操作的 builder。产物路径归 `T3-PDF-ARTIFACT-PATHS`。Android 侧真正拿起 `PdfDocument`/`Canvas`/`Paint` 执行这份程序的壳，连同 CJK 字体资产与真机实测，属 **T3-PDF-RENDER-DEVICE**。

## 拆分依据（2026-09-02 用户裁定）
原卡把「可被本仓闸门证明的纯数据契约」与「只有真机能跑的 Android 渲染」写在一张卡里，三处因此不可测：

1. **`:app` 单测跑不了 Android graphics**。`android/app/build.gradle.kts` 无 `testOptions { unitTests.returnDefaultValues }`，且 T0-TOOLCHAIN 的 non_goals 禁 Robolectric/仪器测试 ⇒ `PdfDocument`/`Canvas`/`Paint` 在 JVM 单测里抛 `Stub!`。
2. **原 `dod_command` 根本不跑任何 `:app` 测试**（只有 `:app:assembleDebug` 编译 + `:core:test`），`verify.ps1` 也只跑 `:core:check` 与 `:core:e2eTest` ⇒ 写在 `app/src/test/**/export/pdf/` 的测试永不执行，是结构性 vacuous pass。补救所需的「读 app 源码文本」断言要在 `android/core/build.gradle.kts` 声明 `inputs.files`，而该文件不在任何一版 allow_paths 内。
3. **A2 的重开核验另有归属**：`T3-REPORT-EXPORT-CORE` A2 已要求 "closes, reopens, verifies exact bytes, size, SHA-256, and fingerprint"；A4 的「measured memory bound」依赖尚未跑的 spike ④（`T1-SPIKE-PLATFORM`，需真机）。

体量同时越界：逐文件估算约 1660 行，R3 硬上限 1000 changed lines（L266 要求动手前量、别等 ship）。故按可证明性切一刀：本卡留纯 JVM 半边（`:core:test --tests "nz.myinspection.core.report.*"` 已实测覆盖 `report.pdf` 子包），设备半边下沉。

**第二次拆分（2026-09-02 用户裁定）**：R3 首轮三条 finding 全部成立并修完后（整数回绕、路径形状判定、测试落点镜像源码），diff 涨到 1139 行 / 60679 字符，同时越 1000 行与 60000 字符两道硬闸；能砍的注释约 85 行，不足以补 146 行的缺口，且 52 行变异收据必须留在 diff 里（L227：评审者只读 diff）。故把 A3 的产物路径整段拆给 `T3-PDF-ARTIFACT-PATHS`——路径命名与绘制操作本就是两件事。实现草稿存 `_local/T3-PDF-ARTIFACT-PATHS-draft/`（不入库），承接卡按 TDD 重走。

## 上下文包（执行模型必读）

### 输入与边界
- 唯一输入 = `DocumentPlan`（`core/report/DocumentPlan.kt`，**不在 allow_paths**，只读不改）+ 一份 `PdfDocumentIdentity`。plan 已定稿：**渲染程序不重排、不分页、不做布局判断**；超宽文本只标记「按框裁切并加尾部省略号」，由设备侧执行——布局缺陷回 composer 修，这是单一职责红线。
- 遍历 plan 时**每个 `TextBearingBlock` 只画它的 `textRuns`**，块上的 address/status/note/dataHash 等字段是身份元数据、**不可绘制**（`DocumentPlan.kt` 头注：跨页切块时每个 chunk 都带整份 payload，画它就会重复出现）。
- 图片只有两处来源：`ImageSlotBlock`（附录/房间内联）与 `ItemRowBlock.thumbnails`（嵌在行坐标系内）。`ImageSlotBlock.xMm/yMm` **相对所属块原点**，程序须折算成页面绝对坐标。

### 几何
- A4 = 595×842pt（1/72 inch），mm→pt = ×72/25.4。换算规则在代码里**只写一处**并被测试逐点钉住（含 `A4_WIDTH_MM`=210 / `A4_HEIGHT_MM`=297 两个端点）。
- 页盒外的操作是缺陷：builder 对越界 fail closed 并点名块与坐标，而不是静默裁掉。

### 四档质量（需求 §8 `[定,2026-08-19]`）
| 档 | 内联 dpi | 附录 dpi |
|---|---|---|
| Low | 96 | 120 |
| Medium（默认分享） | 120 | 160 |
| High（证据归档建议） | 150 | 200 |
| Extra High | 200 | 300 |

默认 `MEDIUM`。形态照抄已有的 `core/media/PhotoQualityProfile`（`storedValue` + `fromStoredValue` 回落默认，不发明新值）。**档位只改采样参数**，不改内容、布局、页脚哈希或源照片。「最终像素上限以真机为准」（需求 §8）指设备侧复核这里派生出的数：目标像素 = 槽宽/高(mm) × dpi ÷ 25.4，是算术不是猜测；spike ④ 之后若需收紧，改的是这张表而非结构。

### 采样与内存上界
- 逐槽：目标像素由槽几何 + 该槽 purpose 的 dpi 派生；`inSampleSize` 按 `BitmapFactory` 语义取**不小于目标的最大 2 的幂降采样**（即最大的 2^k 使两边都仍 ≥ 目标），程序只产出参数、不解码。
- 解码字节按 `ARGB_8888` = w×h×4 计。**逐页上界 = 该页各槽的最大值，不是求和**——这正是「一次只 startPage 一页、逐槽 decode→draw→recycle」策略的可证明形式；写成求和就等于承认没有 recycle。
- 本卡不测真机峰值内存（那是 T3-PDF-RENDER-DEVICE + spike ④），只保证上界的算法与单调性可被 JVM 测试钉死。

### 语义指纹的落点（已决，勿再重开）
`android.graphics.pdf.PdfDocument` 的公开面只有 `startPage`/`finishPage`/`writeTo`/`close`/`getPages`（2026-09-02 经 Context7 核 Android 官方 reference），**没有任何文档元数据/info dictionary API**；而把指纹画到页面上属于渲染器自造内容，既违反 plan-only 红线，也会让 PDF 与 HTML 两版内容不一致。故 `ReportContent.semanticFingerprint` 作为**程序身份**（`PdfDocumentIdentity`）随程序旅行，由 `T3-REPORT-EXPORT-CORE` 的 receipt 承担落账（该卡 A1/A2 已要求两种格式携带同一 fingerprint）。本卡的 A1 只证明：四档程序的 identity 与可绘语义**逐操作相等**。

### 落测位置
`android/core/src/test/kotlin/nz/myinspection/core/report/pdf/`。注意 `ReportSourcePurityTest` 与 `android/core/build.gradle.kts` 的 `reportTestSources` 都只扫 `report/` **顶层** `*.kt`（`listFiles` / `include("*.kt")`），子目录不在其内——本卡无需、也不能改 build 脚本。

## 验收 / 执行建议
dod 见 front-matter。首选 Sonnet 5 · max；备选 Opus 5。难度 M。
动手前按 L266 量体量：产线 + 测试 + R4 收据合计目标 ≤ 850 行，逼近 1000 就先拆再写。
