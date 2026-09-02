---
id: T3-REPORT-HTML-CHARACTER-POLICY
title: Contextual HTML escaping and the character policy the document can actually honour
depends_on: []
parallelizable_with: []
status: merged
branch: T3-REPORT-HTML-CHARACTER-POLICY
worktree: C:\wt\T3-REPORT-HTML-CHARACTER-POLICY
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/html/HtmlEscaping.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/html/HtmlEscapingTest.kt
forbid:
  - Silently substituting or deleting a character the caller supplied (that is how evidence stops matching what was recorded)
  - Escaping for a scripting or CSS context (the report document has no script and no report text ever reaches a style block)
  - Android, filesystem, network, or any dependency beyond the Kotlin and Java standard libraries
non_goals:
  - Document structure, images, redaction, fingerprint or stylesheet (T3-REPORT-HTML-RENDERER and T3-REPORT-HTML-PRESENTATION own those)
  - NFC normalisation or any canonicalisation of report text (core/canon owns the hash domain; this layer is about serialisation only)
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 element-content escaping covers & < > and attribute escaping additionally covers both quote forms, asserted against literal expected strings rather than a re-run of the escaper"
  - "A2 input that cannot survive UTF-8 encoding is refused, not mangled: an unpaired surrogate has no UTF-8 form and Kotlin substitutes '?', so two different notes would otherwise serialise to identical bytes"
  - "A3 input the HTML tokenizer would silently rewrite is refused or documented as rewritten, covering U+0000 (replaced with U+FFFD in character data) and CR (normalised to LF)"
  - "A4 the round trip is asserted on real bytes and real parsed text, not on String equality: encode to UTF-8, decode, and compare, so a claim of byte preservation is proven at the level it is claimed"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.html.*"
dod_exit: 0
dod_assert: literal expected strings pin both escaping contexts; the refusal of unpaired surrogates and of U+0000 is proven by a failing construction rather than by inspection; the preservation claim is verified over encoded UTF-8 bytes and decoded text
review_gate: codex {verdict:pass}
hygiene: each escaped character, each refusal, and the byte-level round trip kill a single realistic mutation
doc_sync: SECURITY + ADR-0007 + TASK-BOARD
---

# T3-REPORT-HTML-CHARACTER-POLICY

## Deliverable

The escaping layer every HTML report is serialised through, and the explicit statement of which characters
the document can carry faithfully. Two contexts only: element content and a double-quoted attribute value.

## 拆分依据（2026-09-03 用户裁定）

`T3-REPORT-HTML-RENDERER` 的 R3 第 1 轮出两条真 finding，其中一条整个落在这一层：`HtmlEscapingTest`
声称「无语法意义的字符逐字节存活」，但它比较的是内存里的 **Kotlin String**，而文档最终是 **UTF-8 字节**、
再被 **HTML 解析器**读回。三处因此对不上——

- **未配对代理项**（lone surrogate）没有 UTF-8 形式，`String.toByteArray(UTF_8)` 会把它换成 `?`：
  两条不同的备注可以序列化成同一串字节。`core/canon` 早就因为同一个理由**直接拒绝**它（T1-CANON-HASH）。
- **U+0000** 在 HTML 字符数据里被 tokenizer 替换成 U+FFFD。
- **CR** 被 tokenizer 归一成 LF。

原卡当时是 999/1000 changed lines，装不下「定义并强制一套字符政策 + 按真实字节与解析结果断言」。
故把这一层整段拆出**先行合并**，渲染器卡随后 rebase 到含本卡的 master 上再 ship。
接缝是真的：「文档里到底允许存在哪些字符」是一条契约，不是渲染细节。

## 上下文包

### 政策取向（实现时定稿，须在卡内留痕）
本仓既有立场是**拒绝优于静默替换**（canon 对 lone surrogate 即如此），理由是「两个不同输入序列化成同一
结果」在一个以证据完整性为卖点的产品里是数据缺陷，不是排版瑕疵。据此建议：

- **拒绝**：未配对代理项、U+0000。二者都会让「文件里的内容」与「被记录的内容」不再是一回事。
- **保留原样并如实说明**：CR。它不影响文件字节，只影响解析器恢复出的文本；把它悄悄改写成 LF 反而是
  一次静默修改。文档措辞据此收窄——保证的是**文件字节**逐字保留，不是「解析后文本与源串逐字相等」。

**收窄措辞不是弱化闸，是让文档停止说假话**（L189 同理）。原 KDoc 的「Nothing is stripped or
substituted」在编码层面根本不成立，必须改。

### 判据形态
断言面必须恰好等于契约（L165）：期望值写**字面量**、不由被测对象回拼；「逐字节保留」这条**必须**
`toByteArray(UTF_8)` 后再 `String(bytes, UTF_8)` 比较，只比 String 相等证明不了它。不可见码位
（NUL、零宽空格、代理项）一律**用数值构造**（`0x200B.toChar()`），绝不写进源文件字面量，也不写
反斜杠 u 转义——本仓的编辑工具会把后者解码成真字符，落盘后肉眼与 diff 都看不见（L193）。

## 交付记录

**merged** 2026-09-03，master `9a0cac9c`，PR #231，**R3 第 3 轮 pass 零 finding**（前两轮各出一条真 finding，
均已修；轮次到顶后经用户裁定 `ResetRounds` 再审——理由见下）。225 行、8 个测试、**17/17 变异全杀**，
`HtmlEscaping.kt` 收据 SHA-256 `2a25567ebec95620…`。

### 三轮 R3 各抓到什么（都是"我写的话与我写的代码不一致"）
- **第 1 轮 finding A**：KDoc 写「nothing is stripped, **replaced** or reordered」，而三十行下面的代码正是把
  `&` 替换成 `&amp;`。两条不同的保证被压成了一句假话。改法是拆开说：**源文本语义保留**（每个字符都被表示，
  或是它自己、或是指代它的实体，不丢字符也不换成别的字符）与**转义结果可无损编码为 UTF-8**（正因如此，
  未配对代理项只能拒绝、不能转义）。
- **第 1 轮 finding B**：CR 测试只断言「某处还有一个 0x0D」——重复、乱序、多出字节都能过。改成比对完整的
  期望字节数组。
- **第 2 轮**：改完之后测试名叫「a carriage return **and a CRLF**」，构造的却只有 CRLF；而通用往返测试拿
  转义结果与它自己的 UTF-8 往返比较，于是「只把**孤立** CR 改成 LF」这种变异仍然全绿。补独立的孤立-CR
  用例（同样比对字面量字节数组），并加 M17 专打这一形态。

### 轮次上限的处置（用户裁定）
`ReviewRoundCap = 2` 到顶。用户裁定 `ResetRounds` 后重审，理由记此备查：上限是为了止住 maker/checker
**同一争点**的拉锯，而这三轮不是——每轮提出的是**不同的、成立的**缺陷，每条都被接受并修复，且每次修复都
带来一枚新的能击杀的变异（M16、M17）。`ResetRounds` 只清计数、不跳过评审，第 3 轮仍是完整的一次真实评审。

## Rejected alternatives

- 静默把非法字符换成 U+FFFD 或 `?`（两条不同证据从此不可区分）。
- 只改注释、不定义政策（R3 明确要求 define and enforce）。
- 在这一层做 NFC 归一（哈希域是 `core/canon` 的，重复权威必然漂移）。
