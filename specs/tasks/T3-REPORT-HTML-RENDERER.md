---
id: T3-REPORT-HTML-RENDERER
title: Self-contained accessible HTML document from shared report content
depends_on: [T3-REPORT-CONTENT-CONTRACT]
parallelizable_with: []
status: todo
branch: T3-REPORT-HTML-RENDERER
worktree: C:\wt\T3-REPORT-HTML-RENDERER
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/html/
  - android/core/src/test/kotlin/nz/myinspection/core/report/html/
forbid:
  - JavaScript, external URLs or resources, raw HTML injection, CSS-only privacy hiding, network, filesystem writes, or DocumentPlan as input
  - Renderer-specific business rules, audience decisions, source paths, vendor metadata, or delivery claims
non_goals:
  - Responsive screen CSS, A4 print CSS, dark mode and forced-colour rules (T3-REPORT-HTML-PRESENTATION, 2026-09-02 用户裁定拆出，见「拆分依据」)
  - PDF, DOCX import, database receipts, Android chooser UI, or in-app report viewer
  - Reading evidence files, decoding, re-encoding or downscaling images (the byte source is an injected port; :core never touches the filesystem)
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 one ReportContent renders deterministic UTF-8 HTML with semantic heading order, tables or lists, figures, captions, language metadata, disclaimer, and integrity labels"
  - "A2 output is one offline file with embedded bounded images, no script, no event handler, no external reference, and every text or attribute context correctly escaped"
  - "A3 meaningful image alternatives and native landmark order survive image failure and non-visual reading"
  - "A4 redaction sentinels removed by ReportContent are absent at byte level and the embedded semantic fingerprint equals the input fingerprint"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.html.*"
dod_exit: 0
dod_assert: exact byte tests prove deterministic self-containment, contextual escaping, landmark and heading order, bounded embedded images with surviving alternatives, byte-level redaction, and fingerprint preservation
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

原 A4/A5 顺延为本卡 A3/A4；本卡的样式表只是 baseline（可读性下限）与 class 契约本体。

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

### 语言标注
`<html lang="en">`；模板双语对用 `<span lang="en">` / `<span lang="zh">` 分别标注。自由文本（听写备注、
wear/damage）**不猜语言**：只标 `HtmlClass.TEXT_ORIGINAL`，不写 `lang`——写一个猜出来的 `lang` 会让屏幕阅读器
用错语音，比不写更坏。

### 图片与失败路径
每张照片渲染成 `<figure>`：`<img alt>` + `<figcaption>` 携带编号 `reference`、来源 `source`、拍摄时间。
`ReportImageSource` 返回 null、媒体类型不在允许集（jpeg/png/webp）、单张超 `maxImageBytes` 或本文档累计超
`maxTotalImageBytes` 时——**figure 仍然发出**，caption 与编号完整，只是没有 `<img>`，并带一条明示「证据未内嵌」
的文字。这既是 A3 的「survive image failure」，也让上界成为**不可绕过**的资源闸而非尽力而为。

## Rejected alternatives

- 从 `DocumentPlan` 反推 HTML（ADR-0007 已否）。
- 在渲染器里再做一次受众/隐私过滤。
- 用 `display:none` / `visibility:hidden` 隐藏被排除内容（bytes 仍在文件里 = 隐私泄露）。
- 让渲染器自己读文件、解码或压缩照片（`:core` 无文件系统依赖，且会重复 T3-PDF-RENDERER 的采样职责）。
