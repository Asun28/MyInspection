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
  - "A2 保留原始 JSON 类型与字段存在性；branch 在场必须非空字符串，reasons 为字符串数组，verdict 为精确 pass/block，sha 为40位小写十六进制字符串，根为对象，精确兼容历史 verdict/reasons/branch/sha 与规范化 run_status/axes/routed_skip；存在的扩展按下文消费契约校验且拒绝未知键；foreign branch 先于 schema/SHA/HEAD 检查过滤"
  - "A3 两个真实 Git root 的不同 HEAD 与两种 current worktree/local 组合均被自检；既有全部 selfcheck 保持，精确 PASS 且无 FAIL 才通过；原目录比较器、路径去重与来源选择器不变"
dod_command: $t=(& pwsh -NoProfile -File scripts/triage.ps1 selfcheck 2>&1 | Out-String); $x=$LASTEXITCODE; Write-Host $t; if ($x -ne 0 -or $t -cnotmatch '(?m)^triage selfcheck: PASS(?=（|[ \t\r]|$)' -or $t -cmatch '(?m)^[ \t]*(?:triage selfcheck: FAIL\b|FAIL\b)') { exit 1 }
dod_exit: 0
dod_assert: 真实双 Git root 的各自 HEAD；worktree/local Test-Path、worktree Get-ChildItem、local Get-Item 与 Get-Content 故障具名 major；JSON/字段/未知 enum/sha/HEAD 故障具名 major；真实规范化与历史格式兼容，扩展坏类型/enum/未知键具名 major；foreign 先过滤；reporter native 0 不替代精确 PASS 且无 FAIL；既有全部 selfcheck 保持
review_gate: codex {verdict:pass}
hygiene: 复用 triage selfcheck；重做7个当前字段变异、下文规范化扩展守卫变异及本卡承担的历史诊断/归属变异，按新源码 SHA 保留 raw/source/manifest；不建平行测试文件
doc_sync: docs/LOOP-ENGINEERING.md（裁决字段、逐 root HEAD 与具名失败语义）；合并后按既有 R5 更新卡片和当前阶段
---

# T0-TRIAGE-EVIDENCE-INPUTS

PR #294 的一个行为前置项。只交付既有证据读取链的失败可观测性与原始字段校验；原 #294 的最终验收不变。

1. `branch` 缺席才按既有文件名规则回退；在场必须是非空字符串。先排除精确 foreign branch，再查 schema/SHA/HEAD。
2. JSON 根必须是对象，顶层只准 verdict/reasons/branch/sha/run_status/axes/routed_skip；前三个必需行为字段为 verdict/reasons/sha，branch 与三个扩展可缺席。reasons 为字符串数组，verdict 为精确 pass/block，sha 为40位小写十六进制字符串。按下文消费契约校验在场扩展；错误具名 major，不能成为 current block。
3. 两个真实仓各自读取 HEAD，分别验证 current worktree block/local pass 及反向组合；mtime 不决定当前性。
4. 保留独立故障 oracle：4b/4c/4d/4e/4f/4g、8b Get-Item、8c worktree/local、10e/schema/foreign。暂不带入 B 新增的 local Get-ChildItem 操作或其故障用例。

## A2 规范化证据消费契约与固定样例

基线 c0afac77175786b55c61c6ebb0292a64a33431a8 的 scripts/review.ps1 Write-Verdict 直接写 `.review/<branch>.json`；triage 探针11读取同一文件和 worktree 的 `*.json`，没有四字段专用格式。specs/verdict.schema.json 是生产侧契约，本节是仅适用于 triage 的明确消费约束，不修改该 schema 或 review/task 的审批规则。

- `run_status` 可缺席；在场必须是精确字符串 success/timeout/no_output/malformed/tool_error。它仍只说明运行结果，不重新路由或软化顶层 verdict。
- `axes` 可缺席；在场必须是仅含 spec/standards 两个必需键的对象；各轴是仅含 verdict/reasons 两个必需键的对象，分别为精确 pass/block 字符串和字符串数组。这针对 Write-Verdict 的归一化输出；原始 backend 的缺省/部分 axes 由 Get-VerdictAxes 补齐，不作为另一种归一化文件。
- `routed_skip` 可缺席；在场必须是仅含 predicate/reason/changed_paths 三个必需键的对象，前两项为字符串，后一项为字符串数组。字段不授权跳过任何本卡检查。
- 所有字段名逐字精确，不把 null、布尔值、数字、数组或字符串互相强转；未知键在顶层及上述嵌套对象均拒绝。branch 缺席时保留原文件名归属规则；存在的精确 foreign branch 在其余 schema/SHA/HEAD 诊断前排除。

以下前四项由固定基线的原 Write-Verdict/Get-VerdictAxes 函数在隔离目录实际生成，覆盖质量 block/pass、无 axes 的 timeout、routed skip；后两项保留真实旧四字段结构与无 branch 历史回退。fixture sha 是隔离 Git 的实际 HEAD，branch 仅替换为本卡，不代表一次正式评审或 A 行为已交付。实现 A 时，复用这些原始值在既有 triage selfcheck 中按每个真实 root 的 HEAD/本卡 id 注入，并以实际 reporter 输出判定；扩展不改变既有 current/source 选择语义。30 个固定样例在登记 D 的正常 DoD 中只校验契约自洽，A 的真实 reader/selfcheck/R4 仍须另行交付。

```json
{"profile":{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","additionalProperties":false,"required":["verdict","reasons","sha"],"properties":{"verdict":{"type":"string","enum":["pass","block"]},"reasons":{"type":"array","items":{"type":"string"}},"branch":{"type":"string","pattern":"\\S"},"sha":{"type":"string","pattern":"^[0-9a-f]{40}$"},"run_status":{"type":"string","enum":["success","timeout","no_output","malformed","tool_error"]},"axes":{"type":"object","additionalProperties":false,"required":["spec","standards"],"properties":{"spec":{"type":"object","additionalProperties":false,"required":["verdict","reasons"],"properties":{"verdict":{"type":"string","enum":["pass","block"]},"reasons":{"type":"array","items":{"type":"string"}}}},"standards":{"type":"object","additionalProperties":false,"required":["verdict","reasons"],"properties":{"verdict":{"type":"string","enum":["pass","block"]},"reasons":{"type":"array","items":{"type":"string"}}}}}},"routed_skip":{"type":"object","additionalProperties":false,"required":["predicate","reason","changed_paths"],"properties":{"predicate":{"type":"string"},"reason":{"type":"string"},"changed_paths":{"type":"array","items":{"type":"string"}}}}}},
"samples":[
{"name":"normalized-block","valid":true,"record":{"run_status":"success","verdict":"block","branch":"T0-TRIAGE-EVIDENCE-INPUTS","sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","axes":{"spec":{"verdict":"block","reasons":["[standards] #17 Bugfix root cause @ specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md:20,33 — A2 permits only verdict/reasons/branch/sha, but the current R3 contract also includes axes and run_status in normalized review records. This would reject valid evidence. Allow those fields and add a fixture using an actual normalized record, or provide evidence that triage reads a separate four-field format."]},"standards":{"verdict":"block","reasons":["[standards] #17 Bugfix root cause @ specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md:20,33 — A2 permits only verdict/reasons/branch/sha, but the current R3 contract also includes axes and run_status in normalized review records. This would reject valid evidence. Allow those fields and add a fixture using an actual normalized record, or provide evidence that triage reads a separate four-field format."]}},"reasons":["[standards] #17 Bugfix root cause @ specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md:20,33 — A2 permits only verdict/reasons/branch/sha, but the current R3 contract also includes axes and run_status in normalized review records. This would reject valid evidence. Allow those fields and add a fixture using an actual normalized record, or provide evidence that triage reads a separate four-field format."]}},
{"name":"normalized-pass","valid":true,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","axes":{"spec":{"verdict":"pass","reasons":[]},"standards":{"verdict":"pass","reasons":[]}},"reasons":[]}},
{"name":"normalized-timeout","valid":true,"record":{"branch":"T0-TRIAGE-EVIDENCE-INPUTS","verdict":"block","run_status":"timeout","reasons":["Producer fixture: reviewer timeout; no quality decision."],"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227"}},
{"name":"normalized-routed-skip","valid":true,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","routed_skip":{"changed_paths":["specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md"],"reason":"Producer fixture: markdown-only route","predicate":"ReviewSkipWhen"},"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","reasons":[]}},
{"name":"legacy-four","valid":true,"record":{"branch":"T0-TRIAGE-EVIDENCE-INPUTS","verdict":"block","reasons":["[standards] #17 Bugfix root cause @ specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md:20,33 — A2 permits only verdict/reasons/branch/sha, but the current R3 contract also includes axes and run_status in normalized review records. This would reject valid evidence. Allow those fields and add a fixture using an actual normalized record, or provide evidence that triage reads a separate four-field format."],"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227"}},
{"name":"legacy-branchless","valid":true,"record":{"verdict":"block","reasons":["[standards] #17 Bugfix root cause @ specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md:20,33 — A2 permits only verdict/reasons/branch/sha, but the current R3 contract also includes axes and run_status in normalized review records. This would reject valid evidence. Allow those fields and add a fixture using an actual normalized record, or provide evidence that triage reads a separate four-field format."],"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227"}},
{"name":"verdict-enum","valid":false,"record":{"branch":"T0-TRIAGE-EVIDENCE-INPUTS","verdict":"BLOCK","reasons":["fixture"],"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227"}},
{"name":"branch-array","valid":false,"record":{"branch":["T0-TRIAGE-EVIDENCE-INPUTS"],"verdict":"block","reasons":["fixture"],"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227"}},
{"name":"sha-format","valid":false,"record":{"branch":"T0-TRIAGE-EVIDENCE-INPUTS","verdict":"block","reasons":["fixture"],"sha":"bad"}},
{"name":"reasons-scalar","valid":false,"record":{"branch":"T0-TRIAGE-EVIDENCE-INPUTS","verdict":"block","reasons":"fixture","sha":"a42bb21aaee148dea36ec3e69b857bb42289d227"}},
{"name":"reason-boolean","valid":false,"record":{"branch":"T0-TRIAGE-EVIDENCE-INPUTS","verdict":"block","reasons":[false],"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227"}},
{"name":"top-extra-key","valid":false,"record":{"branch":"T0-TRIAGE-EVIDENCE-INPUTS","verdict":"block","reasons":["fixture"],"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","unexpected":true}},
{"name":"run-status-enum","valid":false,"record":{"branch":"T0-TRIAGE-EVIDENCE-INPUTS","verdict":"block","reasons":["fixture"],"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","run_status":"SUCCESS"}},
{"name":"run-status-boolean","valid":false,"record":{"branch":"T0-TRIAGE-EVIDENCE-INPUTS","verdict":"block","reasons":["fixture"],"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","run_status":true}},
{"name":"axes-boolean","valid":false,"record":{"branch":"T0-TRIAGE-EVIDENCE-INPUTS","verdict":"block","reasons":["fixture"],"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","axes":false}},
{"name":"axes-extra-key","valid":false,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","axes":{"spec":{"verdict":"pass","reasons":[]},"standards":{"verdict":"pass","reasons":[]},"other":{}},"reasons":[]}},
{"name":"axis-enum","valid":false,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","axes":{"spec":{"verdict":"unknown","reasons":[]},"standards":{"verdict":"pass","reasons":[]}},"reasons":[]}},
{"name":"axis-boolean","valid":false,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","axes":{"spec":{"verdict":true,"reasons":[]},"standards":{"verdict":"pass","reasons":[]}},"reasons":[]}},
{"name":"axis-reasons-scalar","valid":false,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","axes":{"spec":{"verdict":"pass","reasons":"x"},"standards":{"verdict":"pass","reasons":[]}},"reasons":[]}},
{"name":"axis-reason-boolean","valid":false,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","axes":{"spec":{"verdict":"pass","reasons":[]},"standards":{"verdict":"pass","reasons":[false]}},"reasons":[]}},
{"name":"axis-extra-key","valid":false,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","axes":{"spec":{"verdict":"pass","reasons":[],"unexpected":0},"standards":{"verdict":"pass","reasons":[]}},"reasons":[]}},
{"name":"routed-skip-boolean","valid":false,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","routed_skip":true,"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","reasons":[]}},
{"name":"routed-extra-key","valid":false,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","routed_skip":{"changed_paths":["specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md"],"reason":"Producer fixture: markdown-only route","predicate":"ReviewSkipWhen","unexpected":1},"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","reasons":[]}},
{"name":"routed-predicate-boolean","valid":false,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","routed_skip":{"changed_paths":["specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md"],"reason":"Producer fixture: markdown-only route","predicate":false},"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","reasons":[]}},
{"name":"routed-reason-array","valid":false,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","routed_skip":{"changed_paths":["specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md"],"reason":["x"],"predicate":"ReviewSkipWhen"},"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","reasons":[]}},
{"name":"routed-paths-scalar","valid":false,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","routed_skip":{"changed_paths":"x","reason":"Producer fixture: markdown-only route","predicate":"ReviewSkipWhen"},"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","reasons":[]}},
{"name":"routed-path-item-boolean","valid":false,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","routed_skip":{"changed_paths":[true],"reason":"Producer fixture: markdown-only route","predicate":"ReviewSkipWhen"},"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","reasons":[]}},
{"name":"axis-missing-spec","valid":false,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","axes":{"standards":{"verdict":"pass","reasons":[]}},"reasons":[]}},
{"name":"axis-missing-reasons","valid":false,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","axes":{"spec":{"verdict":"pass","reasons":[]},"standards":{"verdict":"pass"}},"reasons":[]}},
{"name":"routed-missing-paths","valid":false,"record":{"run_status":"success","verdict":"pass","branch":"T0-TRIAGE-EVIDENCE-INPUTS","routed_skip":{"reason":"Producer fixture: markdown-only route","predicate":"ReviewSkipWhen"},"sha":"a42bb21aaee148dea36ec3e69b857bb42289d227","reasons":[]}}
]}
```

R4 追加定向删除/放宽 run_status 类型与 enum、axes 容器/必需键/子 verdict/子 reasons、routed_skip 容器/必需键/各字段类型、各层未知键守卫，并做扩展字段错误的 foreign-owner-before-head 负控；每个 mutant 仍须原 selfcheck native0 + 具名 FAIL + 无 PASS。历史7+11项与 Add-Finding AST 删除守卫完整保留。不得把本节 JSON Schema 的样例通过当作实际 triage 修复或变异已完成。

## R4 承接

当前7项：verdict、branch-presence、sha-format、sha-type、reason-shape、reason-item、extra-field。
历史11项：enum、read-content、parse、head、identity、wrong-root、owned-unknown-verdict、local-getitem-read、worktree-discovery、local-discovery、foreign-owner-before-head；另复测本卡适用的完整 Add-Finding AST 删除守卫。
每个变异在该候选副本运行；native 0、具名 FAIL、无 PASS 才算 killed，正例必须精确 PASS 且无 FAIL。完整日志与副本仍在 ignored .review；历史收据保留，不当作本次证据。
