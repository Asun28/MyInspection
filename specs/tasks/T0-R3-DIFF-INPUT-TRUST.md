---
id: T0-R3-DIFF-INPUT-TRUST
title: 让 diff 预算只信 git 自己的输出，被审仓库改不动它
depends_on: [T0-R3-DIFF-BUDGET]
status: todo
branch: T0-R3-DIFF-INPUT-TRUST
worktree: C:\wt\T0-R3-DIFF-INPUT-TRUST
allow_paths:
  - scripts/review.ps1
  - scripts/selftest.ps1
  - docs/QUALITY-RUBRIC.md
forbid:
  - 提高或放宽 1000 changed lines / 60000 chars 的默认预算
  - 让被审分支的任何文件（.gitattributes / .gitconfig / hook）能缩小被度量的体量
  - 用「通常没人这么配」当作不加防护的理由
non_goals:
  - 重新设计预算数值、边界语义或 ship 接线（属 T0-R3-DIFF-BUDGET）
  - 被测提交的身份绑定（属 T0-R3-MEASURED-OID-BINDING）
  - 处理 git 之外的 diff 工具或非 git 仓库
acceptance:
  # 作者声明的验收清单：以下是本卡认为「完成」所需的事实，每条应有可证伪测试。
  # **这是一份声明，不改变任何评审语义**——裁决仍完全按 docs/QUALITY-RUBRIC.md 现行 rubric 判，
  # 清单未列到的问题照常按现行 rubric 处理（含其现行的 [FOLLOW-UP] 适用条件）。
  # 「把清单当排他性判据、清单外一律 FOLLOW-UP」是上游提案 Asun28/claude-devops-scaffold#203
  # 的内容，**上游落地前本仓不采用**。
  - "A1 三处权威 diff 调用（--stat / --numstat / --unified）全部显式带 --no-ext-diff 与 --no-textconv；静态断言逐调用点检查，缺任一参数即红"
  - "A2 成功的 diff.external 伪装：配一个 exit 0 且只吐几行的 helper，超限改动仍必须以 [R3-DIFF-TOO-LARGE] 阻断并报出真实字符数"
  - "A3 成功的 textconv 伪装：经 .gitattributes 的 diff driver 配 textconv，同样必须仍被阻断"
  - "A4 A2/A3 各带负控：去掉防护参数时该伪装必须真的能把体量压到线下，否则判本例无效（防止「本机没执行外部 diff」被误当成防护生效）"
  - "A5 属性强制二进制不可绕过：被审分支用 .gitattributes 把一个纯文本文件标成 -diff（或 binary、或自定义 binary driver），其中放 1001 行内容，预算必须仍然阻断——**当前实现在此处 changedLines=1、diffChars≈288，是已复现的真实绕过**"
  - "A6 自动判定为二进制的文件同样不可绕过：不写 .gitattributes、直接放入含 NUL 字节且超限的大文件，预算必须阻断"
  - "A7 二进制条目的度量口径成文并可证伪：`-/-` numstat 行不得静默计 0——要么以底层 blob 字节数计入体量，要么以专属状态码 fail-closed；无论选哪条，A5/A6 的夹具都必须变红才算实现"
  - "A8 合法的小二进制不被误伤：一个真实的小二进制文件（远小于预算）仍能放行，且诊断里如实报告它是二进制"
  - "A9 每条防护各配一枚单句删除变异：删掉该防护后，其专属夹具以专属状态码变红（非零且命中指定断言文本，不接受「红在别处」）"
  - "A10 状态码文档：本卡新增或改动的每个状态码在 QUALITY-RUBRIC §5 状态表各有一行，闸 17t(doc) 的码↔行一一对应成立"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/review.ps1 -SimpleMatch '--no-ext-diff') -and (Select-String -Path scripts/review.ps1 -SimpleMatch '--no-textconv') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'attr-binary-bypass'))) { exit 1 }"
dod_exit: 0
dod_assert: 验收集合 A1–A10 每条都有可证伪测试；A5 的夹具在实现前必须先红（RED-first 证据由 task.ps1 -Phase red 落在该卡的 .review 收据里），证明它复现的是真实绕过而非假想。夹具哨兵 attr-binary-bypass 即 A5 的机检锚点。强制点：CI 与 ship 跑 selftest.ps1 -Shard seeded 须 exit 0。
review_gate: codex {verdict:pass}
hygiene: 伪装类夹具一律带负控（无防护时必须真能压低体量）；注入 diff 失败/竞态用独立 git shim，不与 diff 配置混用同一注入点
doc_sync: QUALITY-RUBRIC 补齐本卡状态码行；若度量口径改变（blob 字节计入），在 §5 与预算说明处同步口径
---

# T0-R3-DIFF-INPUT-TRUST

## 问题

预算闸度量的是 `git diff` 的输出，而 **git diff 的输出有一半由被审仓库自己决定**：

- `diff.external` 可以把 diff 交给任意程序（成功退出即被采信）；
- gitattributes 的 `textconv` 可以在比对前改写文件内容；
- gitattributes 的 `-diff` / `binary` / 自定义 binary driver 可以让 git 只输出一行 `Binary files differ`，而 `--numstat` 对该文件输出 `-  -`。

第三条已在 R3 第 4 轮**被复现**：一行 `.gitattributes`（`payload.txt -diff`）＋ 1001 行内容，预算实测 `changedLines=1 / diffChars=288`，两个上限双双放行，且评审者根本看不到那 1001 行。前两条已在本卡拆出前修掉（`--no-ext-diff` / `--no-textconv`），第三条未修——它是同一个病在低一层的再现。

## 决策

**被审对象不得参与决定「自己有多大」。** 度量输入一律取自 git 自身实现，且对 git 自身也无法给出体量的条目（二进制/被标成二进制）**fail-closed 或按 blob 字节计**，不得静默计 0。

两种实现取向都可接受，但必须二选一并成文：

1. **按 blob 计**：对 `-/-` 条目取两侧 blob 的字节数（`git cat-file -s`）计入字符预算——度量更真实，但需定义二进制的字符/行折算口径；
2. **fail-closed**：`-/-` 条目一律以专属状态码阻断，要求提交者拆卡或说明——实现简单、语义保守。

无论选哪条，A5/A6 的夹具必须在实现前红、实现后绿。

## 为什么不靠「通常没人这么配」

这正是预算闸要防的那类改动会做的事。一个想让超大 diff 溜过评审的分支，只需要在自己的 `.gitattributes` 里加一行——这行还会顺带让评审者看不见 payload。防护成本是几行代码，绕过成本是一行配置。
