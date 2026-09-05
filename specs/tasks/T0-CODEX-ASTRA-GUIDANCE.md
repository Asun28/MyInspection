---
id: T0-CODEX-ASTRA-GUIDANCE
title: Add project-scoped Codex and GPT-6 Astra collaboration guidance
status: in-progress
depends_on: []
allow_paths:
  - AGENTS.md
acceptance:
  - "A1 Add the six user-approved collaboration sections above the existing AGENTS.md pointer, preserving the original pointer and minimum conventions."
  - "A2 Keep CLAUDE.md authoritative for project contracts; retain required verification, product compliance disclaimers and tool permission boundaries."
  - "A3 Include the official documentation references already verified on 2026-09-05; introduce no application code, runtime dependencies or model-routing changes."
non_goals:
  - Migrate project authority from CLAUDE.md or change global agent preferences
  - Change project gates, reviewer model, review caps or application behavior
dod_command: $ErrorActionPreference = 'Stop'; $doc = [IO.File]::ReadAllText('AGENTS.md').Replace([string][char]13, '').TrimEnd(); $old = @(& git show 04e0c46e:AGENTS.md); if ($LASTEXITCODE -ne 0) { throw 'Cannot read pinned original' }; $original = ($old -join [string][char]10).TrimEnd(); $marker = '> **本文件是给'; $start = $doc.IndexOf($marker); $oldStart = $original.IndexOf($marker); if ($start -lt 0 -or $oldStart -lt 0 -or $doc.Substring($start) -cne $original.Substring($oldStart)) { throw 'Original instructions changed' }; foreach ($heading in @('## Codex / GPT-6 Astra 协作补充', '### 沟通', '### 指令优先级', '### 执行方式', '### 测试与验证', '### 工具与并行', '### 规则来源')) { if (-not $doc.Substring(0, $start).Contains($heading)) { throw ('Missing guidance section ' + $heading) } }; git diff --check; if ($LASTEXITCODE -ne 0) { throw 'Whitespace check failed' }; Write-Output 'ASTRA-GUIDANCE-DOD PASS: six sections present; original instructions preserved'
dod_exit: 0
dod_assert: All six collaboration sections precede the original pointer; the original pointer and minimum conventions match pinned baseline 04e0c46e after line-ending normalization; whitespace validation passes. A2/A3 receive content review against the actual diff.
review_gate: codex {verdict:pass}
hygiene: Documentation-only addition; use the supported SkipRed path, retain content/scope checks and all mandatory ship gates; no application unit tests or mutation suite added.
doc_sync: Orchestrator records PR and merge evidence, marks this card merged and archives it on master after successful ship; these task metadata updates are outside the AGENTS.md feature diff (L18/L212).
---

# T0-CODEX-ASTRA-GUIDANCE

User request: verify the supplied guidance against OpenAI's official documentation, add the project-compatible version above the existing AGENTS.md instructions, then raise a new PR using task-loop.

The content was prepared and approved for this project before this delivery card. This is a documentation adoption, not a behavioral code change. Preserve the existing CLAUDE.md pointer verbatim and treat the added text as project-scoped collaboration preferences. Do not migrate rule ownership or modify global configuration.

Implementation: AGENTS.md only, approximately 50 added lines; well below the existing R3 limits. Task card registration and R5 metadata are managed by the orchestrator on the baseline per the existing repository workflow.

Verification: run the declared DoD, inspect the actual diff under docs/QUALITY-RUBRIC.md, then use the official ship pipeline with SkipRed. Do not claim runtime validation until the pipeline reports it.
