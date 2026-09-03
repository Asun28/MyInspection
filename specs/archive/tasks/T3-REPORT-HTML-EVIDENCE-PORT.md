---
id: T3-REPORT-HTML-EVIDENCE-PORT
title: The evidence byte port, what it may hand over, and the limits it is handed
depends_on: []
parallelizable_with: []
status: merged
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

## 交付记录

**merged** 2026-09-03，master `cadfa2b5`，PR #232，**R3 第 2 轮 pass 零 finding**。198 行、8 个测试、
**10/10 变异全杀**，`ReportImageSource.kt` 收据 SHA-256 `3cb5d96a74f357f2f…`。

### R3 第 1 轮抓到的：只读集合不是不可变集合
`ALLOWED_MEDIA_TYPES` 用 `setOf(...)` 公开，而 `setOf` 返回的是 JVM `LinkedHashSet`——调用方可以强转回
`MutableSet` 加进 `image/svg+xml`，再构造出这个类本该拒绝的 `EmbeddedImage`。**本仓已为同一缺陷修过两处**
（`core/template` 的 `TemplateDomains`、`core/capture` 的 `AdverseStatuses`），且 `AdverseStatuses` 给出的
定式就是答案：**只暴露谓词、不暴露集合**。故改成 `when` 表达式，底下根本不存在集合，「拿到引用去强转」
这条路不是被守住，而是**写不出来**。

补一条反射测试断言 companion 上没有任何 `Collection` 类型的可达成员，并加 **P10**（把公开集合重新发布
回去）证明这道守卫真的在起作用——它被杀了。

### 两处值得复用的判断
① **compile-kill 不算数**：P3（让拒绝类型不再继承 `IllegalArgumentException`）第一批是 COMPILE-KILL——
测试把异常按其声明类型接住，一旦父子关系断开，`error is RejectedEvidenceException` 就成了**编译期**
类型不兼容错误，编译器在任何断言跑起来之前就"杀"了变异体。两处捕获改标 `Throwable` 后该检查在任何变异
下都能编译，P3 才真正按行为失败。同 L282：**非零退出在你知道它由什么产生之前，什么都不证明**。
② **配置错误不是被拒证据**：`HtmlImageBounds` 的越界仍是普通 `IllegalArgumentException`，**故意不是**
拒绝类型——渲染器捕获的是拒绝，若把它也捕进去，一次接线错误就会伪装成"照片静默缺失"。

## Rejected alternatives

- 让端口返回 `null` 表示「类型不对」（丢掉了原因，且与「文件读不到」不可区分）。
- 在渲染器里宽泛 catch `IllegalArgumentException`（会吞掉端口的真实缺陷）。
- 在 `:core` 里嗅探 magic bytes 判定真实格式（`:core` 不解码，与 non_goals 冲突）。
