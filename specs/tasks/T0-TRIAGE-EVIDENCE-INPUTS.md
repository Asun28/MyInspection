---
id: T0-TRIAGE-EVIDENCE-INPUTS
title: triage 既有证据输入失败与原始字段校验
depends_on: [T0-LESSONS-CAP-TRIAGE-SPLIT]
status: todo
branch: T0-TRIAGE-EVIDENCE-INPUTS
worktree: C:\wt\T0-TRIAGE-EVIDENCE-INPUTS
allow_paths:
  - scripts/triage.ps1
  - docs/LOOP-ENGINEERING.md
  - specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md
forbid:
  - 以字符串强转接受数组或空 branch，放过未知字段、坏 reasons、未知 verdict 或无效 sha
  - 对 foreign branch 做本卡 SHA/HEAD 诊断，或以同一个 stub SHA 代替不同 root 的真实 HEAD
  - 改动目录大小写比较器、路径去重、来源选择器或新增本地目录枚举
  - 网络访问、发布动作、review schema 变更、R3 预算放宽或修改历史证据
non_goals:
  - 目录实际大小写、跨来源异拼文件名与双实际模式验收，仍由原 T0-TRIAGE-EVIDENCE-CASE 承担
  - lessons parser、探针 roster 与其它探针语义
diagnosis: 既有 discovery/read/parse/identity/HEAD 异常被吞掉或中断 reporter，且字符串强转让错误原始 JSON 类型混入 current verdict
acceptance:
  - "A1 worktree/local Test-Path、worktree Get-ChildItem、local Get-Item 与 Get-Content 的相关证据故障具名报告 major；读取、解析、身份、未知 enum 与 HEAD 失败不成为 current block，也不中断 reporter"
  - "A2 保留原始 JSON 类型与字段存在性；branch 在场必须非空字符串，reasons 为字符串数组，verdict 为精确 pass/block，sha 为40位小写十六进制字符串，根为对象且没有未知字段；foreign branch 先于 SHA/HEAD 检查过滤"
  - "A3 两个真实 Git root 的不同 HEAD 与两种 current worktree/local 组合均被自检；既有全部 selfcheck 保持，精确 PASS 且无 FAIL 才通过；原目录比较器、路径去重与来源选择器不变"
dod_command: $t=(& pwsh -NoProfile -File scripts/triage.ps1 selfcheck 2>&1 | Out-String); $x=$LASTEXITCODE; Write-Host $t; if ($x -ne 0 -or $t -cnotmatch '(?m)^triage selfcheck: PASS(?=（|[ \t\r]|$)' -or $t -cmatch '(?m)^[ \t]*(?:triage selfcheck: FAIL\b|FAIL\b)') { exit 1 }
dod_exit: 0
dod_assert: 真实双 Git root 的各自 HEAD；worktree/local Test-Path、worktree Get-ChildItem、local Get-Item 与 Get-Content 故障具名 major；JSON/字段/未知 enum/sha/HEAD 故障具名 major；foreign 先过滤；reporter native 0 不替代精确 PASS 且无 FAIL；既有全部 selfcheck 保持
review_gate: codex {verdict:pass}
hygiene: 复用 triage selfcheck；重做7个当前字段变异及本卡承担的历史诊断/归属变异，按新源码 SHA 保留 raw/source/manifest；不建平行测试文件
doc_sync: docs/LOOP-ENGINEERING.md（裁决字段、逐 root HEAD 与具名失败语义）；合并后按既有 R5 更新卡片和当前阶段
---

# T0-TRIAGE-EVIDENCE-INPUTS

PR #294 的一个行为前置项。只交付既有证据读取链的失败可观测性与原始字段校验；原 #294 的最终验收不变。

1. `branch` 缺席才按既有文件名规则回退；在场必须是非空字符串。先排除精确 foreign branch，再查 schema/SHA/HEAD。
2. JSON 根必须是对象，只准 verdict/reasons/branch/sha；reasons 为字符串数组，verdict 为精确 pass/block，sha 为40位小写十六进制字符串。错误不能产生 current block。
3. 两个真实仓各自读取 HEAD，分别验证 current worktree block/local pass 及反向组合；mtime 不决定当前性。
4. 保留独立故障 oracle：4b/4c/4d/4e/4f/4g、8b Get-Item、8c worktree/local、10e/schema/foreign。暂不带入 B 新增的 local Get-ChildItem 操作或其故障用例。

## R4 承接

当前7项：verdict、branch-presence、sha-format、sha-type、reason-shape、reason-item、extra-field。
历史11项：enum、read-content、parse、head、identity、wrong-root、owned-unknown-verdict、local-getitem-read、worktree-discovery、local-discovery、foreign-owner-before-head；另复测本卡适用的完整 Add-Finding AST 删除守卫。
每个变异在该候选副本运行；native 0、具名 FAIL、无 PASS 才算 killed，正例必须精确 PASS 且无 FAIL。完整日志与副本仍在 ignored .review；历史收据保留，不当作本次证据。
