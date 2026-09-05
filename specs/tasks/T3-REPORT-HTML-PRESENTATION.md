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

## 字体：裁决与其前提（2026-09-05，codex `gpt-5.6-sol` 独立评议）

**结论：本卡按系统字体栈实现，字体内嵌不进本卡；但理由不是「体积太大」，而是前提尚未成立。**

### 三处事实更正（评议中发现，均已逐条核对源文件）
先前把这件事记成「对 `[定]` 条款的偏离」，三处站不住：

1. **需求 §8 line 205 本身没有 `[定]` 标记**。带标记的是 line 203（`[定,2026-09-02]`，讲两种格式共用
   一次 `ReportContent`）；line 205 是其后的**无标记段落**。它是否被 203 的标记覆盖属解释问题，不能
   直接当作已冻结条款引用。
2. **平行双语（中英并列）在 line 190 仍是 `[待]`**，不是 `[定]`。中文列是否必然存在这件事本身没定，
   而「必须内嵌 CJK 字体」正建立在它之上。
3. **`T3-PDF-RENDER-DEVICE` 仍是 `todo`，`android/app/src/main/assets/fonts/` 目录并不存在**。
   所以「同一次巡检的 PDF 正常、HTML 豆腐块」这个不对称**当前并不成立**——两种格式都还没有 CJK
   字体资产，都渲染不出中文。此前记录里的这条对比是错的。

### 若将来要做，评议给出的形态（供承接卡直接采用）
- **不是内嵌整份字体，而是按报告确定性裁剪子集**：字形集只从**最终 audience/privacy 过滤后**的
  `ReportContent` 加渲染器固定文案收集——在过滤前收集会让被排除的内容通过字形集留下痕迹。
- 覆盖不足、子集产物损坏、超字节上限一律**导出失败**，不得静默回落成豆腐块。
- CSP 增 `font-src data:`；样式表 hash 必须覆盖含 `@font-face` 的完整生成文本。
- 字体给**独立**上限 `maxEmbeddedFontBytes`，不并入 `maxTotalImageBytes`：图片可以省略而保留 caption，
  字体不能省略而不毁可读性。另需一个覆盖 text+CSS+font+images 的 `maxHtmlBytes`——Base64 会把字节撑大
  约三分之一，只卡原始字节管不住真正外发的那份文件。
- `@font-face` 里**禁 `local(...)`**：它不触发网络，但会让渲染依赖宿主机字体，等于废掉「自包含」这条保证。
- 子集化须确定性、剥掉时间戳与 name-table 元数据，否则会泄露 app / 子集化工具版本。
- 覆盖测试要测**实际字形渲染**，不能只测码位在不在表里：复合字形与变体选择符可以「存在」但渲染错。
- `check-licenses.ps1` 对字体/资产要求人工登记；依赖扫描绿**不等于**字体与子集化工具已清；Apache-2.0
  的 NOTICE 要留，新引入的子集化依赖要单独过许可闸。

### 为什么不在本卡做
评议同样判定这不属于本卡：它要动渲染器与 CSP、要在 `:core` 新增一个字体端口与上限、要接 Android 资产、
要引子集化依赖并补许可证据——与本卡「只改样式表与其测试」的 allow_paths 和 1000 行硬闸都不相容。
**且它有两个前置未决**：双语是否 `[定]`（§8 line 190），以及 PDF 侧字体资产由 `T3-PDF-RENDER-DEVICE`
先落地。两者定了之后再开承接卡，届时按上面的形态写，并同步修 §8 与 `docs/SECURITY.md` 的措辞。

本卡因此**不再阻塞**：按系统字体栈实现，`forbid` 保持禁一切 `url()`（含 `data:`），并在此记录理由是
「前提未成立 + 归属另一张卡」，而不是「与需求冲突但我们选择违反」。

## Rejected alternatives## Rejected alternatives

- 用 JavaScript 做响应式或主题切换（ADR-0007 禁 script）。
- 引入 Tailwind/normalize 之类外部样式表（禁外链，且许可闸另有成本）。
- 只做 print CSS、把响应式留给浏览器默认（需求 §8 明确要 responsive screen CSS）。
- 在样式表里现造 class 名而不进 `HtmlClass` 枚举（双向 parity 会当场红）。
