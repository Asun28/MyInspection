---
id: T0-HANDOFF-REVALIDATE
title: 续接旧 HANDOFF 前重验下一动作仍成立
depends_on: []
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
status: merged
branch: T0-HANDOFF-REVALIDATE
worktree: C:\wt\T0-HANDOFF-REVALIDATE
allow_paths:
  - .claude/hooks/handoff-resume.ps1
  - scripts/handoff.ps1
  - docs/HANDOFF.md
forbid:
  - 新增第 13 个 HANDOFF 字段、改变字段枚举或弱化既有 check/liveness 拒绝
  - 修改 scripts/selftest.ps1、其它共享 harness 脚本、产品代码或 schema
  - 把重验提醒升级为自动执行、自动改卡或新的阻断闸
non_goals:
  - 解决 HANDOFF 指向的业务任务或替用户决定旧动作是否仍正确
  - 修改 planning-with-files 三件套结构、Stop hook 或任务依赖图
diagnosis:
  root_cause: HANDOFF 的 NEXT-ACTION 是写入时快照，但当前续接入口只要求先跑 VERIFY 后照做；卡长期阻塞或并发基线前移后，环境仍存活不代表旧动作仍是正确下一步。
  same_class: 覆盖三个真实消费面——init 生成模板、handoff check 成功输出与 SessionStart resume hook；不只改人读文档。
dod_command: pwsh -NoProfile -Command "Remove-Item -LiteralPath .secrets/T0-HANDOFF-REVALIDATE -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Path .secrets/T0-HANDOFF-REVALIDATE -Force | Out-Null; try { Push-Location .secrets/T0-HANDOFF-REVALIDATE; try { & pwsh -NoProfile -File ..\..\scripts\handoff.ps1 init | Out-Null; if (-not (Select-String -LiteralPath progress.md -SimpleMatch '[HANDOFF-REVALIDATE]')) { throw 'init template omitted HANDOFF-REVALIDATE' } } finally { Pop-Location }; @((([char]60)+'!-- HANDOFF:START --'+([char]62)),'STATUS: in-progress','TASK: verify stale handoff action','CARD: specs/tasks/T0-HANDOFF-REVALIDATE.md','BRANCH: T0-HANDOFF-REVALIDATE','WORKTREE: (main checkout)','LAST-GREEN: fixture seed','NEXT-ACTION: pwsh -NoProfile -File scripts/handoff.ps1 check','VERIFY: pwsh -NoProfile -File scripts/handoff.ps1 check','DO-NOT: none','OPEN-QUESTIONS: none','INVARIANTS: preserve existing handoff contract','UPDATED: fixture',((([char]60)+'!-- HANDOFF:END --'+([char]62)))) | Set-Content -LiteralPath .secrets/T0-HANDOFF-REVALIDATE/progress.md -Encoding utf8; if (-not ((& pwsh -NoProfile -File scripts/handoff.ps1 check -Path .secrets/T0-HANDOFF-REVALIDATE/progress.md 2>&1 | Out-String).Contains('[HANDOFF-REVALIDATE]'))) { throw 'handoff check omitted HANDOFF-REVALIDATE' }; Push-Location .secrets/T0-HANDOFF-REVALIDATE; try { if (-not ((& pwsh -NoProfile -File ..\..\.claude\hooks\handoff-resume.ps1 | Out-String).Contains('[HANDOFF-REVALIDATE]'))) { throw 'resume hook omitted HANDOFF-REVALIDATE' } } finally { Pop-Location } } finally { Remove-Item -LiteralPath .secrets/T0-HANDOFF-REVALIDATE -Recurse -Force -ErrorAction SilentlyContinue }"
dod_exit: 0
dod_assert: init 生成的 progress 模板、合法 HANDOFF 的 check 输出及 SessionStart resume hook 均显示同一 [HANDOFF-REVALIDATE] 哨兵；既有 12 字段校验仍通过；删除任一真实输出面都会由对应行为臂精确失败。
review_gate: codex {verdict:pass}
hygiene: DoD 运行真实 handoff 脚本与 hook，不以源码 grep 或 mock 代替；三个消费面各自独立断言，失败信息点名缺失面。
doc_sync: 合并后把本卡标 merged 并在 TASK-BOARD 记录 PR/commit；无需新增技术债行。
---

# T0-HANDOFF-REVALIDATE

## 产出

让接手 session 在执行旧 `NEXT-ACTION` 前先问三件事：记录的阻塞前提是否仍在、卡是否已被覆盖或作废、是否已有更小方案。该义务附着在现有字段上，不扩充 HANDOFF schema。

## 验收边界

- `handoff.ps1 init` 生成的模板把重验义务带进新的交接块。
- `handoff.ps1 check` 成功后先显示旧下一动作，再显示重验提醒；不改变通过/失败判定。
- SessionStart hook 打印旧指针后显示同一提醒；无有效 HANDOFF 时仍保持原行为。
- `docs/HANDOFF.md` 是三句重验标准的权威正文，脚本只保留短提示与指针。

## 禁止

不执行旧动作、不自动重写交接、不新增阻断状态；本卡只防止“环境仍在”被误读为“旧决定仍正确”。
