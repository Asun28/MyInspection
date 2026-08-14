# 会话交接标准（HANDOFF）—— 不能模糊交接

本项目和 AI coding agent **一起跑、一起决策**。默认一个任务在单次长自主运行里推到底；交接**不是常态**，
而是**真边界的检查点 + 崩溃保险**——进程崩溃/换机器、真被只有用户能答的问题阻塞、或用户叫停时，才把状态传给下一个 run。
交接出问题 = 下个 run 误判状态、重做已做、或踩已知死路。本标准把交接从「凭记忆/凭聊天记录」变成**零歧义的机读契约**。

## 介质：planning-with-files 三件套（cwd，gitignored）
| 文件 | 角色 | 写什么 |
|---|---|---|
| `task_plan.md` | **计划/当前态** | 目标 + 有序步骤(勾选) + 当前指针 + 约束/不变量。真相源仍是项目计划与 `specs/tasks/<id>.md`；本文件是当前 session 的工作视图。 |
| `findings.md` | **沉淀** | 学到的事实 / 决策+理由 / 死路。append-only 倾向——改主意新增一条标注 supersedes，别删历史。 |
| `progress.md` | **时间线 + 交接指针** | 干了什么；**末尾必有一个 HANDOFF 块**，是交接的唯一权威指针。 |

> 三件套是**本地、单仓 checkout 内**的 session 接力带，已 gitignored。真正跨机器/跨人的持久记录是
> `specs/tasks/<id>.md`（卡 status/DoD）+ 提交历史 + `docs/lessons/`。三件套丢了不致命，但**当前 session 必须维护它**。

## HANDOFF 块（progress.md 末尾，唯一权威）
```
<!-- HANDOFF:START -->
STATUS: in-progress              # in-progress | blocked | handoff-ready | done
TASK: 一句话：本 session 在做什么
CARD: specs/tasks/T1-FOO.md      # 或 none
BRANCH: T1-FOO                   # 或 main
WORKTREE: C:\wt\T1-FOO           # 或 (main checkout)；默认 <系统盘>\wt\<id>
LAST-GREEN: a1b2c3d4 — dod_command exit 0（契约 mock 全过）
NEXT-ACTION: pwsh -NoProfile -File scripts\task.ps1 -TaskId T1-FOO -Phase ship
VERIFY: pwsh -NoProfile -File scripts\handoff.ps1 check
DO-NOT: 别动 backend/app/providers/contract.py（冻结）；schema 改法已试、被 guard 拒
OPEN-QUESTIONS: none
INVARIANTS: contract.py 冻结；测试须离线确定性
UPDATED: T1-FOO · step 4/6 · GREEN 后待 ship
<!-- HANDOFF:END -->
```

### 字段义务
- **全部 12 字段必填**，不留空、不留 `<占位>`。
- `STATUS` 取枚举值之一。
- **行动字段**（`LAST-GREEN` / `NEXT-ACTION` / `VERIFY`）必须**具体、可执行**：能复制粘贴的命令，或一个明确的首步 + 验证。
- `DO-NOT` / `OPEN-QUESTIONS` / `INVARIANTS` 可填 `none`——`none` 是**精确**的「此处无内容」，是合法交接。

### 什么叫「模糊」（被 `handoff check` 拒）
模糊 = 下个 session 还得猜。行动字段里出现这类即判失败：
`TBD`、`???`、`continue where I left off`、`where I left off`、`should work`、`figure out`、`somehow`、
`as needed`、`fix it later`、`later`、`待定`、`回头/稍后`、`大概/差不多`，以及残留的 `<占位>`。
> `none`（精确无）合法；`TBD`（待定）非法。差别就是：读的人**还需不需要再问一句**。

## 闸门与机械化
- **写之前（Stop）**：`.claude/hooks/handoff-reminder.ps1` 在 session 结束时提醒更新 HANDOFF 块并自检。
- **校验**：`pwsh -NoProfile -File scripts\handoff.ps1 check` —— 缺字段/空值/残留占位/枚举非法/行动字段模糊 => **非零退出**。
  把它放进你交接前的 checklist；CI 不强制（三件套 gitignored），但本地交接前必须 PASS。
- **存活性校验**（C31）：STATUS 为**可续接态**（非 `done`）时，`check` 还验续接环境仍在——`WORKTREE`（非 `(main checkout)` 哨兵）
  须在磁盘上存在、`BRANCH`（非 main/master）须仍在 git 中，否则非零退出。防「照 HANDOFF 指针 cd 进已被 cleanup 拆除的 worktree /
  checkout 到已合并删除的分支」。`done` 态跳过（cleanup 拆 worktree、删分支属预期）；非 git 仓/git 不可用时分支校验优雅跳过。
- **读（SessionStart）**：`.claude/hooks/handoff-resume.ps1` 在新 session 起始打印 HANDOFF 块——**到岗即见续接指针**，
  无需翻聊天记录。这就是 planning-with-files 的「`/clear` 后自动恢复」在本仓的落地。

## 与卡闭环的关系
- 一张 `specs/tasks/<id>.md` 卡的施工若跨 session，HANDOFF 的 `CARD`/`BRANCH`/`WORKTREE` 必须指向该卡的派生物
  （task-loop / `task.ps1` 以卡 id 派生分支/worktree，见 `specs/README.md`）。
- `LAST-GREEN` 用卡的 `dod_command` 退出码或 `selftest`/`verify` 的结果背书——**别用「我觉得好了」**。
- 交接发现的非平凡坑 → 同时入 `docs/lessons/`（`scripts\lessons.ps1 add`），别只写在 findings 里随三件套丢失。
- **卡的大小按子代理形态定**：**一次性即抛子代理**跑到返回就丢原上下文、无法中途接力，所以「卡拆到一个 agent 一次能干完」是减少交接的上游手段；**长命可续接子代理**（harness 已支持，如 `SendMessage` 带原上下文续接）则能中途被接力/纠偏，交接压力小得多。sizing 启发式见 `docs/PLAN-FORGE.md`「卡的大小」，原理见 `docs/LOOP-ENGINEERING.md`「上下文当内存」。

## 长自主运行的异步进度上报（run 之内 · 不打断运行）
> HANDOFF 块管「session 之间」；本节管「一个长 run 之内」——用户不实时盯屏时，关键进展如何送达。
- **何时报**：完成里程碑步骤 / 某道闸 PASS·FAIL / 产出部分交付物 / 被只有用户能答的问题阻塞。
- **怎么报（工具无关 L26）**：默认 = `progress.md` 时间线追加一行（既有约定，兼作异步通道）；若 harness 提供
  用户即时可见通道（如 send_to_user 类工具、推送/消息工具——例，非强制），部分交付物与阻塞问题**原样转发**（verbatim，不摘要）。
- **报什么**：每条进度声明须指向本 session 的工具结果（命令+退出码 / 文件路径 / diff）；未验证的写「未验证」，
  测试挂了贴输出——**不虚构进度**（源：docs/references/claude-fable-5-prompting-llms.txt「兜底事实」节）。

## 最小流程
1. 进场：读 SessionStart 打印的 HANDOFF（或 `handoff show`）→ 照 `NEXT-ACTION` 续，先跑 `VERIFY` 确认态。
   卡在 worktree 内施工时三件套在**那个 worktree 的 cwd**；从主检出续接用 `pwsh -NoProfile -File scripts\handoff.ps1 show -Path <WorktreeRoot>\<id>\progress.md` 指过去（triage 心跳的 handoff 探针也会浮出该路径）。
2. 干活：随手更新 `task_plan.md` 勾选、`findings.md` 决策/死路、`progress.md` 时间线。
3. 离场：重写 HANDOFF 块 → `handoff check` 必须 PASS → 才算交接完成。
