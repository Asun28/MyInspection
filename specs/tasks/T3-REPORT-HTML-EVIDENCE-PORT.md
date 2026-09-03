---
id: T3-REPORT-HTML-EVIDENCE-PORT
title: The evidence byte port, what it may hand over, and the limits it is handed
depends_on: []
parallelizable_with: []
status: todo
branch: T3-REPORT-HTML-EVIDENCE-PORT
worktree: C:\wt\T3-REPORT-HTML-EVIDENCE-PORT
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/html/ReportImageSource.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/html/ReportImageSourceTest.kt
forbid:
  - Reading files, decoding, re-encoding or downscaling (`:core` has no filesystem; the implementation lives in `:app`)
  - Android, network, or any dependency beyond the Kotlin and Java standard libraries
  - SVG or any other scriptable format in the allowed media types
non_goals:
  - How a document spends its byte budget, renders a figure, or handles a refusal (T3-REPORT-HTML-RENDERER owns all three)
  - The `:app` implementation of the port, including EXIF rotation and downscaling (T3-REPORT-EXPORT-CORE)
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 an EmbeddedImage cannot exist with a media type outside the raster allowlist or with no bytes, and SVG is excluded by name because it is a scriptable document rather than a picture"
  - "A2 a refusal is its own exception type, distinct from an ordinary argument error, so a caller can catch exactly the refusal and let a real defect in the port surface"
  - "A3 the port signature carries the byte ceiling the caller will accept, so an implementation can decline before reading rather than materialise and be turned away"
  - "A4 HtmlImageBounds refuses a document bound below its own per-image bound, which is a caller misconfiguration and deliberately NOT the refusal type"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.html.*"
dod_exit: 0
dod_assert: constructor-level tests prove each refusal and each accepted media type; the refusal type is asserted to be catchable narrowly and distinct from the misconfiguration error; the ceiling is part of the port signature
review_gate: codex {verdict:pass}
hygiene: each refusal, the type distinction and the bounds invariant kill a single realistic mutation
doc_sync: SECURITY + ADR-0007 + TASK-BOARD
---

# T3-REPORT-HTML-EVIDENCE-PORT

## Deliverable

The one way evidence bytes enter an HTML report: what a piece of embeddable evidence *is*, what the
document refuses to carry, and the ceiling the port is told before it reads anything.

## 拆分依据（2026-09-03 用户裁定 · 本卡是 T3-REPORT-HTML-RENDERER 的第三次拆分）

渲染器卡在 R3 第 3 轮修完（补 `docs/SECURITY.md` 要求的 CSP + 对应测试 + 两处过度声称的 KDoc）后正好
**1000/1000 changed lines**，而把 CSP 的两枚变异写进收据就会越界。按该卡自己写下的政策——「R3 若提出任何
需要补守卫 + 补测试的 finding，按 L266 直接再拆卡，不靠删注释腾地方」——这里就是拆点。

接缝：**「证据字节是什么、有哪些上限」** 与 **「文档怎么花这份预算」** 是两件事。本卡拿走前者
（`EmbeddedImage` / `RejectedEvidenceException` / `HtmlImageBounds` / 端口签名），渲染器留下后者
（`ceiling = min(perImage, remaining)` 的算术、figure 与 caption、被拒证据的降级路径）及其测试。
本卡**先行合并**，渲染器卡随后 rebase。实际释放约 65 行（原估 115 偏高：被拒证据的渲染级测试断言的是
渲染器行为，仍留在渲染器卡）。

## 上下文包

### 为什么拒绝要有专属类型
`EmbeddedImage` 的校验发生在**端口实现内部**（`read` 执行期间），所以它是以异常、而不是以 `null` 的形态
到达渲染器的。R3 第 2 轮抓到的正是这一点：当时它抛的是普通 `IllegalArgumentException`，渲染器没有处理，
于是一张类型不合法的照片会**中止整份报告**，而卡片声称的是「被拒的照片仍然发出编号 figure 与 caption」。

修法必须让渲染器能**只**捕获这一类：宽泛地 `catch (IllegalArgumentException)` 会把端口自身的真实缺陷
一并吞掉，渲染出一份静默残缺的报告。故 `RejectedEvidenceException` 独立成型，而 `HtmlImageBounds` 的
配置错误**仍然**是普通 `IllegalArgumentException`——它是调用方配错了渲染器，不是一张被拒的照片。

### 为什么上界要写进签名
先把 `maxBytes` 告诉端口，端口才有机会「读之前就拒」。等端口交出整个 `ByteArray` 再拒，这道上界只能在
分配**之后**生效，而它存在的意义正是阻止那次分配（R3 第 1 轮 finding）。

### 判据形态
期望值写字面量、不由被测对象回拼（L165）。SVG 必须**按名**在测试里排除并给出理由，否则「允许集恰好不含它」
读起来像巧合而非决定。

## Rejected alternatives

- 让端口返回 `null` 表示「类型不对」（丢掉了原因，且与「文件读不到」不可区分）。
- 在渲染器里宽泛 catch `IllegalArgumentException`（会吞掉端口的真实缺陷）。
- 在 `:core` 里嗅探 magic bytes 判定真实格式（`:core` 不解码，与 non_goals 冲突）。
