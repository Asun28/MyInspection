---
id: T3-PDF-RENDER-DEVICE
title: Android PdfDocument executor for the render program, CJK font asset and real-device receipt
depends_on: [T3-PDF-RENDERER, T1-SPIKE-PLATFORM]
parallelizable_with: []
status: todo
branch: T3-PDF-RENDER-DEVICE
worktree: C:\wt\T3-PDF-RENDER-DEVICE
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/export/pdf/
  - android/app/src/main/assets/fonts/
  - android/app/src/test/kotlin/nz/myinspection/app/export/pdf/
forbid:
  - 重排、重新分页或任何布局判断（程序说画哪就画哪；布局缺陷回 T3-REPORT-COMPOSER，采样/几何缺陷回 T3-PDF-RENDERER）
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
  - "A3 每槽 decode 走 program 给出的 inSampleSize、draw 后立刻 recycle，同一时刻至多一张已解码位图存活"
  - "A4 真机 80 照固定夹具四档全部导出成功，输出大小总体单调，中文字形完整、High 档铭牌小字可读、峰值内存不 OOM、附录编号回链正确——逐档记录附 PR"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest
dod_exit: 0
dod_assert: assembleDebug 绿；:app:testDebugUnitTest 绿且覆盖不依赖 Android 运行期的执行器状态机（页开/页闭配对、失败不宣称成功、位图存活计数）；check-licenses 绿且 PR 附字体许可来源链接；PR 正文附真机四档逐档记录（截图或参数），四项人工核验各下二值结论
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4），页开/页闭配对与「失败不宣称成功」各配一枚具名单点变异
doc_sync: ADR-0007 + TASK-BOARD 备注（R5）
---

# T3-PDF-RENDER-DEVICE

## 产出
`app/export/pdf`：把 `T3-PDF-RENDERER` 产出的 `PdfRenderProgram` 用 `android.graphics.pdf.PdfDocument` + `Canvas`/`Paint`/`BitmapFactory` **执行**出来的薄壳，加上 CJK 字体资产与真机验收记录。它**不判断**任何几何、dpi、路径或采样参数——那些全部由程序给定，本卡只负责按序发出平台调用并守住资源生命周期。

## 拆分依据（2026-09-02 用户裁定）
承接 `T3-PDF-RENDERER` 的设备半边。原合卡的三处不可测（`:app` 单测无 Android graphics 运行期、原 DoD 不跑任何 `:app` 测试、重开核验归 `T3-REPORT-EXPORT-CORE`）与约 1660 行的体量，见 `specs/tasks/T3-PDF-RENDERER.md`「拆分依据」。

**本卡的 DoD 因此显式加了 `:app:testDebugUnitTest`**（形态照抄 `T3-REPORT-EXPORT-CORE`），否则 `app/src/test/**` 里的测试永不执行。

## 上下文包（执行模型必读）

### 可测边界（先读这一段再设计）
`:app` 单测是纯 JVM：`android/app/build.gradle.kts` 无 `testOptions { unitTests.returnDefaultValues }`，T0-TOOLCHAIN non_goals 禁 Robolectric 与仪器测试 ⇒ 任何直接触碰 `PdfDocument`/`Canvas`/`Paint`/`BitmapFactory` 的代码在单测里抛 `Stub!`。
**故设计必须把平台调用收进一个可替换的窄端口**（形态参考 `app/media` 的 `PhotoJpegEncoder`/`PhotoBitmapScaler` 分层）：执行器对着端口写状态机 —— 页开/页闭配对、失败路径、位图存活计数 —— 这部分在 JVM 单测里用假端口证明；真正的 `PdfDocument` 实现薄到只有直调，其正确性由 A4 的真机记录承担。**别把状态机写进直调那一层**，那等于把它移出所有闸门之外。

### 前置：spike ④
`T1-SPIKE-PLATFORM` 的第四项（80 张占位图循环渲染 + 逐页 recycle + 中英混排 + `DroidSansFallback.ttf` 字形试，记录峰值内存与耗时）是本卡的实测基线。开工前该卡须已产出 `docs/spike/PLATFORM-SPIKE.md` 的第四节结论。

### 字体
`assets/fonts/DroidSansFallback.ttf`（Apache-2.0）；`Typeface.createFromAsset` 加载。en 可用平台 sans，zh 一律走 fallback 字体（`TextRun.language` 已由 composer 标好，直接读，不做语言探测）。**PR 里附许可来源链接**，`check-licenses.ps1` 须绿。二进制资产进仓是本卡唯一的大文件，落位仅限 `assets/fonts/`。

### 执行纪律
- 一次只 `startPage` 一页；该页 ImageOp 逐个按 program 给的 `inSampleSize` 解码 → draw → 立刻 `recycle`；页 `finishPage` 后再下一页；`writeTo` 走 `FileOutputStream` 流式。
- **已 start 的页必须 finish、document 必须 close**——`PdfDocument` 官方约束：不得在未 finish 页时 `writeTo`/`close`。失败路径同样要走完关闭，且**关闭之后才可能返回成功**；任何一步失败都不得留下「产物已完成」的声明（落盘/发布/receipt 归 `T3-REPORT-EXPORT-CORE`，本卡只负责不撒谎）。
- 超宽文本按 program 标记做尾部省略号并记 warning 日志；**不重排**（布局缺陷回 composer 修）。

### 真机核验（A4，需用户设备）
固定夹具 80 照，须含房间全景、铭牌小字、低光与高熵图，不能只测易压缩样图。四档逐档导出，记录：文件大小（证总体单调，相邻档若因内容熵例外须有逐图证据，**不伪造绝对 MB 上限**）、中文字形完整性、High 档铭牌小字可读性、峰值内存、附录编号回链、页脚哈希与 `T3-REPORT-EXPORT-CORE` 侧 fingerprint 的一致性。

## 验收 / 执行建议
dod 见 front-matter。首选 Sonnet 5 · max；备选 Opus 5。难度 M。真机部分需用户参与。
