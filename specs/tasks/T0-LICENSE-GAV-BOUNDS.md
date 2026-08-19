---
id: T0-LICENSE-GAV-BOUNDS
title: Gradle/Maven GAV 分段上界与有界诊断契约（TD135；TD2 4/5）
depends_on: [T0-LICENSE-DIAGNOSTICS]
status: todo
branch: T0-LICENSE-GAV-BOUNDS
worktree: C:\wt\T0-LICENSE-GAV-BOUNDS
allow_paths:
  - scripts/check-licenses.ps1
  - scripts/license-scanner-check.ps1
forbid:
  - 把超长 GAV 截断、哈希或规范化后继续作为缓存路径、POM 身份或 exception key
  - 让超长 GAV 穿过共享验证边界进入 cache lookup、POM 比对、例外匹配或 finding
  - 改变既有四张 Gradle 图、许可分类、exact-GAV exception 的大小写语义或成功/失败 exit 语义
  - 修改 scripts/selftest.ps1、CI、政策文档或 TD2 状态
non_goals:
  - 重做 diagnostics 的脱敏、类别注入防护或通用日志框架
  - 新增许可类别、例外或 Gradle 图
  - 接线 CI/selftest 或把 TD2 标为 paid
diagnosis:
  root_cause: Get-GradleGavParts 只校验字符集和路径遍历，未限制 group/artifact/version 长度；该同一验收边界同时喂给图解析、缓存路径、POM 身份和 exception key，而 diagnostics 又承诺精确保留已接纳 GAV 且总输出有界。
  same_class: 所有接受 Maven GAV 的入口必须经 Get-GradleGavParts；不得仅在日志 sink 或某一个 parser 调用点补长度判断。
dod_command: pwsh -NoProfile -File scripts/license-scanner-check.ps1 -Suite gav-bounds
dod_exit: 0
dod_assert: group、artifact、version 每段 1–255 个既有允许字符的 GAV 仍按 Ordinal 语义精确通过；256 个字符的任一段在共享验证边界 fail-closed，不能成为缓存路径、POM/exception 身份或已解析 finding。三段均为 255 的已接纳 GAV 加 ` => ` 后为 771 字符，保留至少 200 字符 detail 预算，因而在 1,000 字符 audit 信封内与 caller-owned 类别一同精确保留且总行有界。删除每段上界或绕过共享验证的单句变异必须命中专属断言；既有 graph/policy 套件和真实已批准 GAV 集不变。
review_gate: codex {verdict:pass}
hygiene: gav-bounds 套件只保留三段边界、共享入口与 audit-envelope 的最小夹具；每个长度守卫和每个绕过入口各有一枚单句删除变异
doc_sync: 本卡 merge 后登记 TD135 PR/commit；TD2 仍为 carded，随后才允许 T0-LICENSE-CI-INTEGRATION 继续
---

# T0-LICENSE-GAV-BOUNDS

## 目标

把“exact GAV”收紧为一个可同时满足身份、缓存安全与有界诊断的合同：只有经共享 GAV 验证器接纳的
`group:artifact:version` 才是 scanner 的精确坐标；任一段最长 255 个已有允许字符。

`Get-GradleAuditText` 的总预算为 1,000 字符，并须至少为 detail 留 200 字符。三段各 255 时，
`GAV + " => "` 为 `3 × 255 + 2 + 4 = 771`，仍在 800 字符的坐标预算内。因此该上界使每个
已接纳 GAV 都能与稳定类别一起精确保留；超界原始输入仍可被有界、脱敏地报告，但绝不再被当作有效坐标。

## 单一产出

- 在 `Get-GradleGavParts` 建立唯一的、Ordinal 的 1–255 分段合同。
- 图解析、缓存/POM 路径与 exception 读取均复用该合同，超界值在接触路径或身份比较前失败。
- `gav-bounds` 专项 TDD 套件证明 255/256 三段边界、exact identity、audit 信封与 mutation；不复制 graph、policy 或 diagnostics 的其它夹具。

## 依赖门

依赖 `T0-LICENSE-DIAGNOSTICS` 已 merge：POM、分类、结构化 finding 与统一诊断边界已经冻结。
PR #27 的 R3 轮次封顶和人裁不改变 graph 接纳语义；本卡在该诊断卡之后单独收紧 GAV 的共享接纳合同，
不得反向把已实现的 diagnostics 变成等待本卡的前置。

## 非目标

本卡不修诊断脱敏或 CI 接线，也不对超界值作截断后继续扫描；超界必须被视为不安全、不可解析的 GAV。
