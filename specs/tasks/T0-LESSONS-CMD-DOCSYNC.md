---
id: T0-LESSONS-CMD-DOCSYNC
title: 把 lessons.ps1 纳入 doc-drift 机检，并同步 archive 子命令到三处命令清单
depends_on: [T0-LESSONS-COLD-RECALL]
parallelizable_with: []
status: todo
branch: T0-LESSONS-CMD-DOCSYNC
worktree: C:\wt\T0-LESSONS-CMD-DOCSYNC
allow_paths:
  - scripts/_config.ps1
  - scripts/selftest.ps1
  - docs/LESSONS.md
  - docs/DELIVERY-CHAINS.md
  - .claude/skills/lessons/SKILL.md
  - CLAUDE.md
forbid:
  - 改 lessons.ps1 的任何行为（本卡只补机检与文档，子命令本体归 T0-LESSONS-COLD-RECALL）
  - 用通配把整个 scripts/ 塞进 DocSyncMap（那会让每次脚本改动都要求触碰文档，闸变成噪音后必被绕过）
  - 放宽闸 14f/17 的既有判定或给它加跳过开关
non_goals:
  - 把 triage.ps1 / archive.ps1 等其它脚本也纳入 DocSyncMap（同类面各自评估，本卡只做 lessons.ps1 这一处）
  - 重新设计 doc-drift 闸的耦合模型
acceptance:
  # 作者声明的验收清单：以下是本卡认为「完成」所需的事实，每条应有可证伪测试。
  # **这是一份声明，不改变任何评审语义**——裁决仍完全按 docs/QUALITY-RUBRIC.md 现行 rubric 判，
  # 清单未列到的问题照常按现行 rubric 处理（含其现行的 [FOLLOW-UP] 适用条件）。
  # 「把清单当排他性判据、清单外一律 FOLLOW-UP」是上游提案 Asun28/claude-devops-scaffold#203
  # 的内容，**上游落地前本仓不采用**。
  - "A1 机检缺口先证其存在（RED 先行）：在补 DocSyncMap 之前，构造一次只改 scripts/lessons.ps1、完全不碰任何文档的提交，跑闸 14f/17 得 **PASS**——记录该退出码作为「今天这条耦合不存在」的证据"
  - "A2 补上耦合后同一场景必红：`scripts/_config.ps1` 的 DocSyncMap 新增 `'scripts/lessons\\.ps1'` 键，值至少含 `docs/LESSONS.md`；A1 的同一次「只改脚本不改文档」必须变为**非零**，且失败文案点名 lessons.ps1 与未被触及的文档路径"
  - "A3 正例不误伤：同时改 scripts/lessons.ps1 与 docs/LESSONS.md 的提交，闸 14f/17 仍 PASS（否则闸变成「改脚本必须改文档」的噪音，必被绕过）"
  - "A4 键的正则形态与既有四条一致：新键用仓库相对、正斜杠、点号转义的形态（对照既有 `scripts/task\\.ps1` / `scripts/review\\.ps1` / `scripts/check-licenses\\.ps1` / `scripts/check-scope\\.ps1`），一枚断言证明新键能匹配 `scripts/lessons.ps1` 且**不**匹配 `scripts/_lessons.ps1`（前缀下划线的同名邻居，误匹配会把判定核的改动也拖进文档要求）"
  - "A5 三处命令清单补 archive：`docs/DELIVERY-CHAINS.md` 的能力表、`.claude/skills/lessons/SKILL.md` 的命令行、`CLAUDE.md` 经验铁律节的命令枚举，三处的子命令集合须**逐一等于** lessons.ps1 实际实现的子命令集合（真相源 = 脚本内 switch/param 的分支名，不是手抄清单）"
  - "A6 A5 是机检不是人工核对：新增一条断言从 lessons.ps1 源码解析出实际子命令名集合，再与三处文档各自列出的集合求差，任一处缺项或多项即红；删掉任一处的 `archive` 即让该处专属断言变红（三处各一条，不共用一条聚合断言）"
  - "A7 单句删除变异：A2 与 A6 各配一枚只删一句的变异（删 DocSyncMap 新键 / 删三处比对中的一处），删后其专属断言变红（判据分类器：非零**且**命中该断言文本才算击杀）"
  - "A8 selftest 两分片绿：`-Shard core` 与 `-Shard workflow` 均 exit 0"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/_config.ps1 -SimpleMatch 'scripts/lessons' -Quiet) -and (Select-String -Path docs/DELIVERY-CHAINS.md -SimpleMatch 'archive' -Quiet))) { exit 1 }"
dod_exit: 0
dod_assert: DocSyncMap 含 lessons.ps1 键；DELIVERY-CHAINS 能力表含 archive。A1–A8 每条都有可证伪证据，其中 A1 的 PASS 退出码为实测记录。
review_gate: codex {verdict:pass}
hygiene: A2/A6 各留一枚最小夹具 + 一枚单句删除变异；不为三处文档各写一套独立解析器，子命令真相源只解析一次
doc_sync: 本卡改动本体即文档；合并后无额外同步项

---

# T0-LESSONS-CMD-DOCSYNC

## 起因

`T0-LESSONS-COLD-RECALL`（PR #51）给 `lessons.ps1` 加了 `archive` 子命令。其 pre-R3 复核顺带扫出：
三处权威面仍在教旧的六条命令清单——`docs/DELIVERY-CHAINS.md` 的能力表、`.claude/skills/lessons/SKILL.md`、
`CLAUDE.md` 经验铁律节——**而且没有任何机检会发现这件事**。

## 真正的缺口不是那三行文档

是 `scripts/_config.ps1:67` 的 `DocSyncMap`。它今天只有四个键：

```
'scripts/task\.ps1'           -> docs/DEVOPS-WORKFLOW.md
'scripts/review\.ps1'         -> docs/QUALITY-RUBRIC.md
'scripts/check-licenses\.ps1' -> docs/LICENSE-POLICY.md
'scripts/check-scope\.ps1'    -> docs/DEVOPS-WORKFLOW.md
```

`scripts/lessons.ps1` 不在其中，所以闸 14f/17（doc-drift 耦合）对它完全沉默。补三行文档只修这一次；
补这个键才让下一次也红。**只做前者等于把同一个坑留在原地**。

## 为什么不顺手在 PR #51 里改

那三处文档与 `_config.ps1` 都不在 `T0-LESSONS-COLD-RECALL` 的 `allow_paths` 内。按 rubric 立场
「不得给卡加范围」，越界的修法只能记 `[FOLLOW-UP]` 另开卡。

## 为什么不把整个 scripts/ 通配进去

`forbid` 明写禁止。耦合闸的价值在于**信噪比**：一旦每次脚本改动都要求触碰某份文档，作者会开始
为了过闸而做无意义的文档改动，闸就死了。逐个评估、只连真有权威耦合的那几对——这也是既有四条的形态。

## 一个容易踩的匹配坑

新键必须匹配 `scripts/lessons.ps1` 而**不**匹配 `scripts/_lessons.ps1`。后者是判定核（纯函数库），
改它不该要求触碰面向用户的 `docs/LESSONS.md`。A4 把这条钉成断言，因为一个漏写的锚点在这里不会报错，
只会让闸悄悄变宽。
