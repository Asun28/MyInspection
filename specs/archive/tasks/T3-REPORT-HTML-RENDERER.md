---
id: T3-REPORT-HTML-RENDERER
title: Self-contained accessible HTML document from shared report content
depends_on: [T3-REPORT-CONTENT-CONTRACT, T3-REPORT-HTML-CHARACTER-POLICY, T3-REPORT-HTML-EVIDENCE-PORT]
parallelizable_with: []
status: merged
branch: T3-REPORT-HTML-RENDERER
worktree: C:\wt\T3-REPORT-HTML-RENDERER
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/html/
  - android/core/src/test/kotlin/nz/myinspection/core/report/html/
# HtmlEscaping.kt 与 HtmlEscapingTest.kt 虽落在上面两个前缀内，但归 T3-REPORT-HTML-CHARACTER-POLICY；
# 该卡先行合并后本卡 rebase，届时它们不再出现在本卡 diff 里。
forbid:
  - JavaScript, external URLs or resources, raw HTML injection, CSS-only privacy hiding, network, filesystem writes, or DocumentPlan as input
  - Renderer-specific business rules, audience decisions, source paths, vendor metadata, or delivery claims
non_goals:
  - Responsive screen CSS, A4 print CSS, dark mode and forced-colour rules (T3-REPORT-HTML-PRESENTATION, 2026-09-02 用户裁定拆出，见「拆分依据」)
  - Contextual escaping and the document's character policy (T3-REPORT-HTML-CHARACTER-POLICY, 2026-09-03 用户裁定拆出)；本卡只调用它
  - 证据字节端口本体：EmbeddedImage / RejectedEvidenceException / HtmlImageBounds 与端口签名 (T3-REPORT-HTML-EVIDENCE-PORT, 2026-09-03 用户裁定拆出，见「第三次拆分」)；本卡留「文档怎么花这份预算」
  - PDF, DOCX import, database receipts, Android chooser UI, or in-app report viewer
  - Reading evidence files, decoding, re-encoding or downscaling images (the byte source is an injected port; :core never touches the filesystem)。
    **需求 §8 要求内嵌的是「经归一化且有界」的 raster image，归一化那一半没有 owner** —— `:app` 侧
    `ReportImageSource` 的实现（读文件、EXIF 旋转、降采样到 `maxBytes` 之内）归 `T3-REPORT-EXPORT-CORE`
    （其 allow_paths 已含 `app/.../export/core/`），本卡只定义端口形状与上界，不实现它
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 one ReportContent renders deterministic UTF-8 HTML with semantic heading order, tables or lists, figures, captions, language metadata, disclaimer, and integrity labels"
  - "A2 output is one offline file with embedded bounded images, no script, no event handler, no external reference, and every text or attribute context correctly escaped"
  - "A3 meaningful image alternatives and native landmark order survive image failure and non-visual reading"
  - "A4 redaction sentinels removed by ReportContent are absent at byte level and the embedded semantic fingerprint equals the input fingerprint"
  - "A5 every class the document emits is a declared HtmlClass entry and every declared entry is emitted by some document, so the presentation card inherits a contract rather than a convention"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.html.*"
dod_exit: 0
dod_assert: exact byte tests prove deterministic self-containment, contextual escaping, landmark and heading order, bounded embedded images with surviving alternatives, byte-level redaction, fingerprint preservation, and two-way HtmlClass parity over a fixture set that reaches every entry
review_gate: codex {verdict:pass}
hygiene: escaping, resource-bound, redaction, and accessibility protections each kill a single realistic renderer mutation
doc_sync: requirements + SECURITY + ADR-0007 + TASK-BOARD
---

# T3-REPORT-HTML-RENDERER

## Deliverable

Serialize the shared semantic report into one portable HTML file suitable for a system browser, save, share, and print. HTML never re-decides audience or privacy.

## 拆分依据（2026-09-02 用户裁定）

动手前按 L266 逐文件估算：escaping ~45 · image port ~60 · renderer ~300 · presentation CSS ~130 · 测试 ~570 ≈ **1105 行**，
越 R3 的 1000 changed-lines 硬闸。故把原 A3（responsive 320px / 200% text / A4 print / dark / forced-colors 的 CSS）
整段拆给承接卡 **`T3-REPORT-HTML-PRESENTATION`**（`depends_on` 本卡）。

**这一刀是真接缝、不是为压行数硬切**：原 A3 的判据完全落在样式表文本上（媒体查询存在性、`break-inside`、
`prefers-color-scheme`、`forced-colors`），与文档结构正交；而两者唯一的耦合——「CSS 选择器命中的 class 必须真的
被渲染器发出」——在拆开后反而成为承接卡里一条**显式**的双向 parity 测试（每个选择器的 class 都被发出；每个被发出的
class 都被样式命中），比揉在一张卡里更强。

原 A4/A5 顺延为本卡 A3/A4，新增 A5 把 class 契约从散文提为验收；本卡的样式表只是 baseline（可读性下限）。

**第三次拆分（2026-09-03 用户裁定）**：R3 第 3 轮三条 finding 全部成立——① 文档缺 `docs/SECURITY.md`
明文要求的 CSP（「并以 CSP 禁网络/导航/主动内容」），也没挡 `meta refresh`；② 自包含测试只扫标签名，
看不进 `<style>` 正文（里面的 `@import` 会全绿通过），也没要求 CSP；③ `HtmlClass` 与样式表两处 KDoc
声称「没人 style 的 class 不可表达」，而基线样式表实际只 style 了 2 个 entry、其余 26 个都没有——
声称超出代码所能兑现。全部修完后正好 **1000/1000 changed lines**，连把 CSP 的两枚变异写进收据都放不下。
按本卡自己的政策（下方「不靠删注释腾地方」）拆：证据字节端口本体归 `T3-REPORT-HTML-EVIDENCE-PORT`
先行合并，本卡留 `ceiling = min(perImage, remaining)` 的算术、figure/caption 与被拒证据的降级路径。
实际释放约 65 行。

**第二次拆分（2026-09-03 用户裁定）**：R3 第 1 轮两条 finding 都成立——① `embed()` 无论文档预算还剩多少，
都把 `maxImageBytes` 报给端口，于是「先告知上界、让端口不必先分配」这件事在预算将尽时失效，被拒的图片
照样被 materialize；② `HtmlEscapingTest` 声称的逐字节保留只在内存 String 上成立，编码成 UTF-8 再被
HTML 解析器读回时，未配对代理项会变 `?`、U+0000 变 U+FFFD、CR 变 LF。两条合计约 +34 行，而当时是
**999/1000**。故把转义层与字符政策整段拆给 `T3-REPORT-HTML-CHARACTER-POLICY` 先行合并，本卡随后
rebase 再 ship；finding ① 留在本卡修。

**首次拆分后实测 994 changed lines / 49599 字符**（`git diff --cached --numstat`，与 R3 闸同一把尺），对 1000 行硬闸只剩 6 行余量，含 26 行 R4 收据（L227：评审者只读 diff，收据必须在里面）。
余量这么薄意味着：**R3 若提出任何需要「补一道守卫 + 补一条测试」的 finding，按 L266 直接再拆卡，不靠删注释腾地方**（T3-PDF-RENDERER 就是删了 85 行注释仍补不上 146 行缺口）。

## 上下文包

### 输入与边界
- 唯一输入 = `ReportContent`（`core/report/content/`，**不在 allow_paths**，只读不改）+ 一个注入的图片字节端口。
  受众与隐私过滤在它诞生前就完成了：渲染器**不得**重判、不得回查数据库、不得把被移除的 bytes 用 CSS 藏起来。
- **`:core` 是纯 JVM，不碰文件系统**。照片只带 `reference`/`contentHash`，不带 bytes；bytes 由 `:app` 侧实现的
  `ReportImageSource` 端口交进来。渲染器不解码、不缩放、不重编码——只做 base64 内嵌与**上界拒绝**。
- 不接受 `DocumentPlan`：A4 几何与分页属 PDF，HTML 的分页归浏览器。

### class 契约（单一真相源）
渲染器发出的每一个 class 都来自枚举 `HtmlClass`，**没有裸字符串 class**。承接卡的样式表按同一枚举写选择器，
于是「加了一个 class 却没人给它样式」「样式指着一个不存在的 class」两种漂移都不可表达（同 `PdfArtifactPaths`
用 `Audience.entries`/`PdfExportQuality.entries` 拼正则之理）。原子证据组（图 + 说明）用 `HtmlClass.EVIDENCE_FIGURE`，
承接卡在它上面挂 `break-inside: avoid`。

### 转义
所有属性值一律双引号包裹。文本上下文转义 `& < >`；属性上下文另加 `" '`。**不静默删改任何字符**——
坏字节静默替换正是 T1-TEMPLATE-ENGINE 抓到的缺陷类型。用户自由文本**永不**进入 `<style>`；本文件没有 `<script>`。
`<title>` 是 RCDATA，用文本转义即可。

### 状态不靠颜色
评级本身以**文字**写在单元格里（`<td class="item-status">POOR</td>`），不是只有一个待上色的 class。
承接卡 A3 的「不得以颜色为唯一区分手段」因此有可依赖的载体，不必靠 `::before { content: … }` 现造内容
（那会撞上它自己「CSS 不得重新引入报告内容」的 forbid）。

### 语言标注
`<html lang="en">`；模板双语对用 `<span lang="en">` / `<span lang="zh">` 分别标注。自由文本（听写备注、
wear/damage）**不猜语言**：只标 `HtmlClass.TEXT_ORIGINAL`，不写 `lang`——写一个猜出来的 `lang` 会让屏幕阅读器
用错语音，比不写更坏。

### 图片与失败路径
每张照片渲染成 `<figure>`：`<img alt>` + `<figcaption>` 携带编号 `reference`、来源 `source`、拍摄时间。
`ReportImageSource` 返回 null、媒体类型不在允许集、单张超 `maxImageBytes` 或本文档累计超
`maxTotalImageBytes` 时——**figure 仍然发出**，caption 与编号完整，只是没有 `<img>`，并带一条明示「证据未内嵌」
的文字。这既是 A3 的「survive image failure」，也让上界成为资源闸而非尽力而为。

两处必须写死、否则会被读成两种意思：
- **媒体类型由端口声明、由 `EmbeddedImage` 构造器对允许集（jpeg/png/webp）校验**，允许集证明的是
  「端口声称的类型合法」，不是「字节真是该格式」——`:core` 不解码，嗅探 magic bytes 与 non_goals 冲突。
  真伪由 `:app` 侧端口实现负责，SVG 永不入集（它是可带脚本的文档，不是位图）。
- **`maxBytes` 随 `read` 一起传给端口**，而不是等端口交出整个 `ByteArray` 再拒。否则一份损坏或恶意的
  200 MB 文件会在被拒之前先被完整读进内存，这道「资源闸」就只能在 OOM 之后才生效。渲染器仍复核一次
  返回值大小（端口可能不守约），但守约的端口有能力一开始就不越界。

## 交付记录

**merged** 2026-09-03，master `054f6d58`，PR #230。977 行、28 个测试、**28/28 变异全杀**（零 compile-kill、
零 no-run），收据钉 `HtmlClass cb47e074…` / `ReportHtmlRenderer d03ad777…` / `ReportHtmlStylesheet c8ea88c4…`。

### R3 走了 6 轮、共 10 条 finding，全部成立且全部修掉
把它们并排看，形态惊人地一致：**写下的保证 / 测试名 / 卡片不变量，超出了代码或断言真正兑现的东西**。

1. `embed()` 无论文档预算还剩多少都把 `maxImageBytes` 报给端口 → 「先告知上界、让端口不必先分配」在预算将
   尽时失效。改 `ceiling = min(perImage, remaining)`、为零不调端口、按有效上界复核。
2. 逐字节保留的声明只在内存 String 上成立（未配对代理项 / U+0000 / CR）→ 拆出 `T3-REPORT-HTML-CHARACTER-POLICY`。
3. 媒体类型/空字节的拒绝**从端口内部抛出**，冒泡上来**中止整份报告**，而卡片声称「被拒的照片仍出编号 figure」
   ——该路径上这句是假的，且测试只测了构造器。改 `RejectedEvidenceException` + 窄捕获。
4. 缺 `docs/SECURITY.md` 明文要求的 CSP（「并以 CSP 禁网络/导航/主动内容」），也没挡 `meta refresh`。
   写卡时没读那一节，属硬边界漏项。
5. 自包含测试只扫标签名，看不进 `<style>` 正文——里面的 `@import` 会全绿通过。
6. 两处 KDoc 声称「没人 style 的 class 不可表达」，而基线样式表实际只 style 了 2 / 28 个 entry。
7. **`lang` 缺省不等于「未知」，而是继承 `<html lang="en">`**——中文听写备注会被屏幕阅读器用英文音朗读，
   正是那段注释声称要避免的后果。改 `lang=""`；旧测试断言该属性**缺席**，等于把 bug 写成了期望。
8. CSP 哈希的期望值由调用产线 `styleHash` 得到 → 改成 MD5 也照样绿，而浏览器会拒绝该样式表。改字面量。
9. 卡片 DoD 写「exact byte tests」，而确定性测试只比 Kotlin `String`。改成编码成 UTF-8 比字节数组。
10. 累计预算测试把总额**恰好**花光，于是第二次调用永远是「零」分支，`min(perImage, remaining)` 的中间态
    从未被测——只在零处提前返回、其余一律报 `maxImageBytes` 的实现能全绿通过。补 perImage=4/total=6 用例。

### 三次拆卡（均用户裁定），每次都由 1000 行硬闸逼出
CSS → `T3-REPORT-HTML-PRESENTATION`（动手前估算 1105 行）· 转义与字符政策 →
`T3-REPORT-HTML-CHARACTER-POLICY`（R3 第 1 轮 block 时卡在 999/1000）· 证据字节端口 →
`T3-REPORT-HTML-EVIDENCE-PORT`（修完 R3 第 3 轮正好 1000/1000，连两枚 CSP 变异的收据都放不下）。
**教训**：一处反复吃 finding 的子问题通常是独立子问题；越早拆越省轮次。

### 轮次上限三次经用户裁定 ResetRounds
每轮提的都是**互不相同**的真缺陷、每条都被接受并修复、每次修复都带来新的能击杀的变异——不属该上限
要止住的「同一争点拉锯」。`ResetRounds` 只清计数，评审本身一次没跳过。

## Rejected alternatives

- 从 `DocumentPlan` 反推 HTML（ADR-0007 已否）。
- 在渲染器里再做一次受众/隐私过滤。
- 用 `display:none` / `visibility:hidden` 隐藏被排除内容（bytes 仍在文件里 = 隐私泄露）。
- 让渲染器自己读文件、解码或压缩照片（`:core` 无文件系统依赖，且会重复 T3-PDF-RENDERER 的采样职责）。
