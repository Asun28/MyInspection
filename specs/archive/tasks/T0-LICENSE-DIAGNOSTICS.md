---
id: T0-LICENSE-DIAGNOSTICS
title: Gradle 许可扫描统一诊断边界与脱敏套件（TD2 收口卡 3/5）
depends_on: [T0-LICENSE-POLICY]
status: merged
branch: T0-LICENSE-DIAGNOSTICS
worktree: C:\wt\T0-LICENSE-DIAGNOSTICS
allow_paths:
  - scripts/check-licenses.ps1
  - scripts/license-scanner-check.ps1
forbid:
  - 在日志中输出 URI userinfo、token、凭据、绝对用户目录或未清洗的外部元数据
  - 为美化错误而把任何 graph/policy 失败降级为 warning 或成功退出
  - 改变前两卡冻结的图范围、GAV、POM、分类或 exception 语义
non_goals:
  - 新增许可策略或例外
  - 为 GAV segment 添加上界或改变 graph 接纳语义（TD135 follow-up）
  - 修改 CI、政策文档或 TD2 状态
  - 通用化为全仓日志框架
dod_command: pwsh -NoProfile -File scripts/license-scanner-check.ps1 -Suite diagnostics
dod_exit: 0
dod_assert: wrapper/POM/exception/Gradle 子进程的敌意输出均经过同一诊断边界；输出按行数和字符数有界，URI userinfo、secret-like 值和用户路径被替换，ANSI/control/newline 注入不能伪造新 finding。清洗前后错误类别与非零退出保持不变；删除任一 redaction、bound 或 category-preservation 守卫时 diagnostics 套件必须命中专属断言。
review_gate: codex {verdict:pass}
hygiene: diagnostics 套件以敌意输入表驱动，保留各类边界的一枚最小 survivor；不重复证明许可分类本身
doc_sync: 本卡登记 PR #20 已合并基线与自身 PR #27 人裁结果；TD135 另卡承接超长 GAV 合同，TD2 仍为 carded
---

# T0-LICENSE-DIAGNOSTICS

## 目标

在 graph 与 policy 行为稳定后，只处理失败信息的安全性和可操作性。输入是前两卡的结构化结果，输出是有界、脱敏、不可注入的诊断文本；成功/失败判定不得改变。

PR #20 已在 master `b0a76d0` 合入部分 sanitizer，但并未形成统一出口或独立 diagnostics DoD。已知基线缺口包括绝对用户目录从 wrapper/POM/启动异常路径旁路输出。本卡以这些真实缺口铸造 RED，再集中修复；不得把“已有部分实现”当成完成证据。

## 单一产出

- 所有外部文本进入日志前走同一个 sanitizer。
- 对当前诊断信封内的 finding，保留稳定类别和 exact GAV，但不泄露机器路径或凭据；超长 GAV 的 exact+bound 合同由 TD135 另卡承接。
- 长 stderr、嵌入换行、ANSI/control、Windows/Unix 路径和 URI userinfo 都有行为夹具。

## 依赖门

依赖 `T0-LICENSE-POLICY` merge，因为只有错误类别和结构化 finding 冻结后，才能证明 sanitizer 没有吞掉或改写安全语义。

## R3 轮次封顶与人裁边界

PR #27 的 R3 达到 round cap 后已按仓库规则完成人裁并合并。本卡只拥有诊断出口的脱敏、有界、抗注入与失败语义；不得修改
`Get-GradleGavParts`、图接纳/拒绝、缓存路径、POM 身份或 exception key。超长 GAV 同时要求 exact GAV 与固定诊断上界的
合同冲突已登记为 TD135 follow-up，由随后 `T0-LICENSE-GAV-BOUNDS` 的共享解析边界解决；本卡不以修改 graph 接纳语义来解决该冲突。

Round 2 后，PR #27 head `a750056` 已修复真实的 multiline credential/configured-home。对单一 Output record 内
`scheme://user:...\n...@host` 与普通两行输出不可判定的歧义，人裁选择安全优先：识别到 credential、Authorization 或 userinfo 前缀后，
余下 continuation 整体脱敏；外部进程本来逐行输出时，每行仍是独立 array element。该歧义不再拆卡；TD135 仅承接超长 GAV 合同。

## 根因诊断

PR #20 多轮 R3 在 parser/policy 尚变化时反复追日志泄露和平台差异，reviewer 每轮都要重读同一大文件。本卡把诊断作为单独安全边界，允许一次集中评审。

## 合并记录

PR #27 以 master `e3b52c7` squash 合并；最终实现提交为 `a750056`。diagnostics 专用套件覆盖 18 个语义变异，graph/policy 回归套件分别覆盖 23/30 个变异，真实 strict 离线扫描覆盖 150 个唯一 GAV，`verify`、`check-secrets` 与 PR CI 均通过。R3 达到 `ReviewRoundCap=2` 后按仓库恢复路由转人裁：单条输出记录内一旦识别到 credential、Authorization 或 userinfo 前缀，其 continuation 按安全优先整体脱敏；超长 GAV 的 exact+bound 合同则登记为 TD135，由后续 `T0-LICENSE-GAV-BOUNDS` 独立偿还。TD2 完成第 3/5 张卡，保持 `carded`。
