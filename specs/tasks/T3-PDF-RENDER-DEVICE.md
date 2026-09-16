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
  - 执行器重排、重新分页、测量、换行、tail-ellipsis 或改变布局；唯一绘图例外是按已解码位图尺寸在既定图片外框内推导固定 FIT_CENTER 目标框，不改 placement、slot、caption、dpi 或采样目标
  - 引入 PDF 三方库（iText=AGPL 禁；平台 PdfDocument 足够，ADR-0003）
  - 修改/复制字体资产、NOTICE 或 LICENSE-POLICY；这些只归前置测量器
non_goals:
  - 字体加载、Paint/Typeface metrics、文本 wrap/ellipsis、资产许可登记（T3-PDF-ANDROID-TEXT-MEASURER）
  - 质量档位、dpi、采样算术、mm→pt 几何、产物路径派生（T3-PDF-RENDERER 已定，只消费不重判）
  - 落盘位置、原子发布、重开逐字节核验与 receipt（T3-REPORT-EXPORT-CORE）；完整真机验收（T3-PDF-DEVICE-ACCEPTANCE）
  - 导出 UI、系统分享、chooser、应用内阅读（T3-REPORT-EXPORT-UI）
plan_ref: context/DESIGN.md#backup-report-health-and-compliance-component-matrix
acceptance:
  - "A1 the executor consumes a PdfRenderProgram and issues one page start, its ops, and one page finish per page; its only geometry derivation is the fixed FIT_CENTER drawing rectangle inside each unchanged image placement, with no layout, dpi, path or text measurement decisions"
  - "A2 every successfully started page receives a finish attempt and every created document receives a close attempt, including when drawing or finishing fails; success requires page finalization, write and document close to complete, and any failure leaves no completed-artifact claim"
  - "A3 每槽先读取 source bounds，再仅调用既有 PdfImageSampling.inSampleSize(source,target) 取得采样值后 decode；以实际位图宽高将整张图等比居中画进原 placement，无裁剪或拉伸，不改原框采样目标；draw 后立刻 recycle，同一时刻至多一张已解码位图存活"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest
dod_exit: 0
dod_assert: assembleDebug 绿；:app:testDebugUnitTest 绿且覆盖不依赖 Android 运行期的执行器状态机（页开/页闭配对、失败不宣称成功、位图存活计数）；执行器仅画前置已测量的 PdfTextOp，任何 remeasurement/wrap/ellipsis 均失败。完整真机验收由 T3-PDF-DEVICE-ACCEPTANCE 承接，本卡通过不代表真机或完整导出已验收。
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4），页开/页闭配对、「失败不宣称成功」、单位图存活、禁止重测量及用拉伸或裁剪替换 FIT_CENTER 各配具名单点变异
doc_sync: ADR-0007 + TASK-BOARD 备注（R5）
---

# T3-PDF-RENDER-DEVICE

## 产出与边界

`app/export/pdf` 的薄执行器把 `T3-PDF-RENDERER` 产出的 `PdfRenderProgram` 用 `android.graphics.pdf.PdfDocument`、`Canvas`、`BitmapFactory` 按序执行。它不判断布局、dpi、路径、文本度量或 wrap：`PdfTextOp` 由 `ReportComposer`/builder 携带前置 measurement 后准备，执行器直接 draw。除图片框内固定 FIT_CENTER 绘制外，只守住平台调用顺序、页/文档关闭和单张位图生命周期。

FIT_CENTER 的唯一规则是以实际解码后的正宽高计算 `scale=min(frameWidth/bitmapWidth, frameHeight/bitmapHeight)`，将完整源图等比居中于给定 placement，剩余区域留白。不得裁剪、拉伸、改外框或按绘图区重算 targetPixels/采样。测试覆盖横、竖、方、极端长图与偏移外框，验证比例、居中、完整源框及框内边界；无效尺寸或解码失败进入失败路径。像素采样的取整可能使各档绘图矩形略有差异，不声称目标浮点坐标逐位相同。正式产品媒体已由照片入库流程转正，执行器不追加 EXIF 旋转；批准 real80 的只读检查为 67 个无方向标记、13 个方向 1，实际 Android 解码仍由后续设备验收证明。

`:app` 单测为纯 JVM，直接 Android graphics API 会 `Stub!`；故页开闭、失败与位图存活状态机必须针对可替换窄端口测试，真正 PdfDocument adapter 保持直调薄层。一次只 start 一页；每个 ImageOp 读取 bounds，仅调用 `PdfImageSampling.inSampleSize`，decode → draw → 立即 recycle。成功开始的页必须尝试 finish，创建的 document 必须尝试 close；即使 finish 自身抛错也必须尝试 close，但不把抛错的关闭调用声称为已成功释放。只有页结束、write 和 close 均成功才返回成功。无资产、字体、许可文件、落盘/发布或真机视觉义务。

## RED、DoD 与预算

执行器接收调用方拥有的输出流或等价窄 write 端口：所有已开始页结束后 writeTo，随后关闭文档，再返回成功；不能先关闭 PdfDocument 才尝试写出。路径选择与输出流的打开/关闭归调用方，原子发布和 receipt 仍归 EXPORT-CORE。字体按 PdfTextOp 的 font role 复用前置测量器交付的同一解析者，不另行选择、加载字体或测量。

先以 fake port 写页配对、关闭顺序、失败路径、采样转交和位图计数 RED，再接平台 thin adapter。DoD/R4 见 front-matter；真实 80 照、CJK、High 铭牌、内存、大小行为和附录证据完整保留给 `T3-PDF-DEVICE-ACCEPTANCE`。

原完整 diff 预测为 430–580 changed lines / 30k–44k characters：生产 180–250、直接测试 170–230、R4 与修复余量 80–100。FIT_CENTER 另计生产 30–60、行为测试 45–80 和 R4/修复 20–40 行，新的完整预测为 525–760 行，尚不是实现证据。NOTICE、字体与许可证已归前置，不能借此卡压缩或更改。RED 前须根据具体端口和文件方案重算；预估达到 650 行或 45k 字符即按生命周期与 image bridge 再拆，不削弱 A1–A3，也不先写完再等 R3 拦截。首选 GPT-5.6 Terra · high 处理平台端口与异常关闭；若拆出单独纯图片桥接，可由 medium 承接。
