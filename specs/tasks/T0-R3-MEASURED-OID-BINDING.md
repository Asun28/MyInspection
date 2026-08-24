---
id: T0-R3-MEASURED-OID-BINDING
title: 让被测量的提交就是被 push、被评审、被合并的那一个
depends_on: [T0-R3-DIFF-BUDGET]
status: todo
branch: T0-R3-MEASURED-OID-BINDING
worktree: C:\wt\T0-R3-MEASURED-OID-BINDING
allow_paths:
  - scripts/review.ps1
  - scripts/task.ps1
  - scripts/selftest.ps1
  - docs/QUALITY-RUBRIC.md
forbid:
  - 以分支名（而非提交 OID）作为「同一个产物」的证明
  - 在预算测量之后、发布之前留下任何不复核身份的对外副作用
  - 用 --force / 改写历史来「对齐」身份不符
non_goals:
  - 改预算数值、边界或度量口径（属 T0-R3-DIFF-BUDGET / T0-R3-DIFF-INPUT-TRUST）
  - 并发 ship 的锁机制或多会话协调
  - PR base 分支漂移（已由既有 Assert-RemotePrBase 覆盖）
acceptance:
  # 作者声明的验收清单：以下是本卡认为「完成」所需的事实，每条应有可证伪测试。
  # **这是一份声明，不改变任何评审语义**——裁决仍完全按 docs/QUALITY-RUBRIC.md 现行 rubric 判，
  # 清单未列到的问题照常按现行 rubric 处理（含其现行的 [FOLLOW-UP] 适用条件）。
  # 「把清单当排他性判据、清单外一律 FOLLOW-UP」是上游提案 Asun28/claude-devops-scaffold#203
  # 的内容，**上游落地前本仓不采用**。
  - "A1 -SizeOnly 以机器可读形式交回本次实际测量的完整 40 位 OID，一枚夹具断言其等于测量时的 HEAD"
  - "A2 task.ps1 解析该 OID 并钉住；解析不到即以专属码 fail-closed（不得默默继续），一枚缺失夹具证明之"
  - "A3 分支引用漂移即停：SizeOnly 之后任务分支引用被移动，下一个对外副作用之前以 [R3-DIFF-TIP-MOVED] 阻断"
  - "A4 工作树 HEAD 漂移即停：**只动 HEAD、不动分支引用**（git checkout --detach）后同样阻断——这是 A3 查不出来的那一半，需独立夹具"
  - "A5 review.ps1 自带身份闸：调用方传入的 -ExpectHead 与捕获到的 HEAD 不符时以 [R3-HEAD-MISMATCH] 阻断，且**早于**轮次计数与 reviewer 调用（断言轮次未增加、评审者未启动）"
  - "A6 -ExpectHead 形态校验：非 40 位十六进制值被拒，不得当作「没传」而静默跳过"
  - "A7 task.ps1 的两处 review 调用（-Local 与远端）都显式传 -ExpectHead；静态断言两处皆在，缺一即红"
  - "A8 push 按 OID 发布：以「OID → 分支」显式 refspec 推送，并在 push 前复核身份；一枚夹具证明 push 之前发生漂移时零 push"
  - "A9 上游跟踪不被破坏：显式 refspec 推送后分支的上游仍指向 origin 上的同名分支（`git push -u` 在源为裸 OID 时静默不设上游，故须显式补设并检查退出码）"
  - "A10 合并按 OID 收口：远端 squash 合并把已测量 OID 传给 --match-head-commit；本地合并并入该 OID 而非分支名；各一条静态或行为断言"
  - "A11 四个对外副作用（push / 建 PR 前的复核 / R3 / 合并）各自在动作**之前**复核身份，任一处删掉复核，其专属夹具变红"
  - "A12 状态码文档：[R3-DIFF-TIP-MOVED] 与 [R3-HEAD-MISMATCH] 在 QUALITY-RUBRIC §5 状态表各有一行，闸 17t(doc) 的码↔行一一对应成立"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/review.ps1 -SimpleMatch '[R3-HEAD-MISMATCH]') -and (Select-String -Path scripts/task.ps1 -SimpleMatch '[R3-DIFF-TIP-MOVED]') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'head-detach-not-ref'))) { exit 1 }"
dod_exit: 0
dod_assert: 验收集合 A1–A12 每条都有可证伪测试；A4 的夹具必须**只移动 HEAD、不移动分支引用**（否则它测的是 A3，A4 等于没覆盖），其机检锚点是夹具哨兵 head-detach-not-ref。强制点：CI 与 ship 跑 selftest.ps1 -Shard workflow 须 exit 0。
review_gate: codex {verdict:pass}
hygiene: 身份漂移的注入点用评审 stub（测量后、合并前的窗口），不改生产码即可施压；每条复核各配单句删除变异
doc_sync: QUALITY-RUBRIC 补齐两个状态码行；DEVOPS-WORKFLOW 的 ship 流程说明标注「发布的是被测量的那个提交」
---

# T0-R3-MEASURED-OID-BINDING

## 问题

预算闸测量的是**一个具体提交**，但测完之后流程一路用的是**分支名**：push 推分支、R3 审工作树 HEAD、合并合 PR head。三者可以指向不同提交，而每一步看起来都正常。

R3 第 3 轮复现了其中一条：`Assert-MeasuredTip` 只校验任务分支引用（`refs/heads` 下的同名分支），而 `git checkout --detach` 只移动 HEAD、**不移动**该引用——于是守卫全过，评审读的却是另一个提交。「被测量的」「被合并的」是同一个，「被评审的」是另一个。

## 决策

把 OID 当作产物身份，贯穿到每一个对外副作用之前：

- `-SizeOnly` 交回它实际测量的 OID；
- `task.ps1` 钉住它，并在 push / R3 / 本地合并 / 远端合并**之前**各复核一次；复核同时覆盖**分支引用与工作树 HEAD**；
- `review.ps1` 自己也收 `-ExpectHead` 并 fail-closed——因为调用方断言与被调方执行之间有时间窗，且手工直接调 `review.ps1` 时根本不经过 `task.ps1`；
- 发布与合并按 OID 而非分支名执行（显式 refspec、`--match-head-commit`）。

## 为什么两个指针都要钉

它们是两个独立可动的指针，且**下游读的不是同一个**：范围闸按卡 id 取分支引用，评审与构建读工作树 HEAD。只钉一个，另一个就是敞口——这正是第 3 轮 finding 的形状（另见经验 L238）。
