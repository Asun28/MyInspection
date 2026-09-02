---
id: T3-REPORT-HTML-PRESENTATION
title: Responsive, printable, dark and forced-colour stylesheet for the self-contained report
depends_on: [T3-REPORT-HTML-RENDERER]
parallelizable_with: []
status: todo
branch: T3-REPORT-HTML-PRESENTATION
worktree: C:\wt\T3-REPORT-HTML-PRESENTATION
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/html/ReportHtmlStylesheet.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/html/ReportHtmlStylesheetTest.kt
forbid:
  - JavaScript, external stylesheets or fonts, @import, any url() at all (data: included: those bytes would sit outside maxTotalImageBytes, the document's only size bound), network at render or view time
  - CSS that hides, reorders or reintroduces report content (privacy is decided before ReportContent exists; display:none is never a filter)
  - New class names invented in the stylesheet instead of in the HtmlClass enum
non_goals:
  - Document structure, escaping, image embedding, redaction or fingerprint (T3-REPORT-HTML-RENDERER owns them)
  - PDF geometry, pagination or export quality; HTML quality is fixed at NONE
  - Android WebView rendering, in-app viewer, share sheet or print dialog
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 the stylesheet carries the rules a 320px viewport and 200 percent text need: a width media query, no fixed pixel width on any content container, and an overflow rule on the evidence table"
  - "A2 an @media print block targets A4 and attaches break-inside avoid to every atomic evidence group: the figure, the item row and the bilingual pair"
  - "A3 a prefers-color-scheme block and a forced-colors block both exist and neither makes colour the only carrier of a status"
  - "A4 the set of classes the stylesheet selects and HtmlClass.entries are compared as sets, over a fixture set that reaches every entry including the image-failure and free-text branches"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.html.*"
dod_exit: 0
dod_assert: stylesheet tests prove the presence and shape of the responsive, print, dark and forced-colours rules, break-inside on every atomic evidence group, absence of any url() or external reference, and set equality between the stylesheet's selectors and HtmlClass.entries
review_gate: codex {verdict:pass}
hygiene: each media-query, break-control and parity guard kills a single realistic stylesheet mutation
doc_sync: requirements + SECURITY + ADR-0007 + TASK-BOARD
---

# T3-REPORT-HTML-PRESENTATION

## Deliverable

Grow `ReportHtmlStylesheet` from the renderer card's readability baseline into the real presentation contract: responsive on a phone, printable on A4, legible in dark and forced-colour modes. The stylesheet is the only styling carrier in the file; the document keeps no inline `style` attribute.

## 拆分依据（2026-09-02 用户裁定）

`T3-REPORT-HTML-RENDERER` 原卡 A1–A5 逐文件估算约 1105 行，越 R3 的 1000 changed-lines 硬闸（L266：动手前量）。
原 A3 的判据完全落在样式表文本上，与文档结构正交，故整段拆到本卡。

拆开还换来一条原本不存在的闸：A4 的**双向 parity**。渲染器把 class 收进 `HtmlClass` 枚举当单一真相源后，
本卡可以对着一份真实渲染出的文档两头证明——每个被发出的 class 都被样式命中、每个被样式选中的 class 都在枚举里。
揉在一张卡里时这条只是「作者自己记得对齐」，拆开后是机检。

## 上下文包

### 边界
- 只改 `ReportHtmlStylesheet` 与它的测试。文档结构、转义、图片内嵌、redaction、fingerprint 都是前置卡的，
  本卡**不动**——若发现结构缺陷，按 L113 判断是回前置卡修还是开新卡，不在本卡顺手改。
- CSS 里**不得**出现 `@import`、外部 `url()`、web font、`expression()`。字体只用系统字体栈（CJK 与拉丁各给回退）。
- 隐私不由 CSS 承担：被 `ReportContent` 移除的内容根本不在文件里，样式表**不得**新增任何隐藏规则来"补"它。

### 判据形态
样式表是纯文本常量，断言按**规则形态**锚定而非整份文本比对（L165：断言面恰好等于契约）。例如
`@media print` 段内 `HtmlClass.EVIDENCE_FIGURE` 的选择器必须带 `break-inside: avoid`——只删那一句，测试必须变红。
颜色不作唯一区分手段：状态除底色外必须另有文字或形状标记，`forced-colors: active` 下仍可读。

## 体量预算（L266）

前置卡实测 994 行、只余 6 行余量，故本卡动手前先量一次：样式表正文 + 其测试 + R4 收据合计对着
1000 changed lines / 60000 字符报预算，超 800 行就在写 RED 之前提拆卡。allow_paths 已收窄到两个具体
文件，越界即被范围闸拦下——这也是 A4 双向 parity 的前提：若本卡能改 `HtmlClass.kt`，「样式表选了一个不存在的 class」就能靠**加一个枚举值**修好，而那正是 A4 要抓的漂移。

## 待用户裁定：web font 与需求 §8 的冲突

需求 §8（`[定,2026-09-02]`，line 205）写的是「CSS/字体/经归一化且有界的 raster images **内嵌**」——
字面包含**内嵌字体**。本卡目前禁 web font、只用系统字体栈，理由是一份可用的 CJK 字体动辄数 MB，
会让一份自包含 HTML 报告胖出一个量级。但代价是真实的：**没有系统 CJK 字体的设备上，中文一列会显示成
豆腐块**，而同一次巡检的 PDF 因 `T3-PDF-RENDER-DEVICE` 自带 CJK 字体资产反而正常。
这是对 `[定]` 条款的偏离，**不能由执行侧默默决定**：开工前需用户在「内嵌 CJK 子集字体」与
「接受无 CJK 字体设备上的豆腐块」之间裁一次，裁决记进本节与需求 §8。

## Rejected alternatives

- 用 JavaScript 做响应式或主题切换（ADR-0007 禁 script）。
- 引入 Tailwind/normalize 之类外部样式表（禁外链，且许可闸另有成本）。
- 只做 print CSS、把响应式留给浏览器默认（需求 §8 明确要 responsive screen CSS）。
- 在样式表里现造 class 名而不进 `HtmlClass` 枚举（双向 parity 会当场红）。
