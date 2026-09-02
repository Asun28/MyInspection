---
id: T3-REPORT-HTML-PRESENTATION
title: Responsive, printable, dark and forced-colour stylesheet for the self-contained report
depends_on: [T3-REPORT-HTML-RENDERER]
parallelizable_with: []
status: todo
branch: T3-REPORT-HTML-PRESENTATION
worktree: C:\wt\T3-REPORT-HTML-PRESENTATION
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/html/
  - android/core/src/test/kotlin/nz/myinspection/core/report/html/
forbid:
  - JavaScript, external stylesheets or fonts, @import, url() with any scheme other than data:, network at render or view time
  - CSS that hides, reorders or reintroduces report content (privacy is decided before ReportContent exists; display:none is never a filter)
  - New class names invented in the stylesheet instead of in the HtmlClass enum
non_goals:
  - Document structure, escaping, image embedding, redaction or fingerprint (T3-REPORT-HTML-RENDERER owns them)
  - PDF geometry, pagination or export quality; HTML quality is fixed at NONE
  - Android WebView rendering, in-app viewer, share sheet or print dialog
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 screen CSS stays readable at a 320px viewport and at 200 percent text: no horizontal overflow, no fixed pixel widths on content containers, and evidence tables reflow rather than clip"
  - "A2 print CSS targets A4 and no atomic evidence group is clipped or split: a figure with its caption, an item row, and a bilingual pair each stay whole"
  - "A3 dark mode via prefers-color-scheme and Windows/Android forced-colours mode both remain legible, with no colour-only status distinction"
  - "A4 every HtmlClass the renderer emits is targeted by the stylesheet and every class the stylesheet selects exists in HtmlClass, proven in both directions against a rendered document"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.html.*"
dod_exit: 0
dod_assert: stylesheet tests prove responsive and print media rules, unbreakable evidence groups, dark and forced-colours support, absence of external references, and two-way class parity with the rendered document
review_gate: codex {verdict:pass}
hygiene: each media-query, break-control and parity guard kills a single realistic stylesheet mutation
doc_sync: requirements + ADR-0007 + TASK-BOARD
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

## Rejected alternatives

- 用 JavaScript 做响应式或主题切换（ADR-0007 禁 script）。
- 引入 Tailwind/normalize 之类外部样式表（禁外链，且许可闸另有成本）。
- 只做 print CSS、把响应式留给浏览器默认（需求 §8 明确要 responsive screen CSS）。
- 在样式表里现造 class 名而不进 `HtmlClass` 枚举（双向 parity 会当场红）。
