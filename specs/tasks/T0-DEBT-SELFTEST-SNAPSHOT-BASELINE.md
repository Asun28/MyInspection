---
id: T0-DEBT-SELFTEST-SNAPSHOT-BASELINE
title: 让 selftest all 快照钉住调用者 HEAD 与权威 master（偿还 TD156）
depends_on: []
status: todo
branch: T0-DEBT-SELFTEST-SNAPSHOT-BASELINE
worktree: C:\wt\T0-DEBT-SELFTEST-SNAPSHOT-BASELINE
allow_paths:
  - scripts/selftest.ps1
forbid:
  - 修改 selftest 分片内容、CI 触发器/矩阵、聚合调度、core 错峰或现有失败协议
  - 更新调用者仓库的 refs、切换调用者检出、写调用者工作树，或让分片联网克隆
  - 以 detached/skip 14f、清空 DocSyncMap、放宽 doc-drift 或忽略非零退出换绿
non_goals:
  - 实现 TD9 的 skip visibility、no-git routing、mutation budget 或 load stability
  - 清理历史 worktree、优化 selftest 墙钟或修改 PR #33 的业务语义
  - 改写 `_gitbase.ps1`、doc-drift 判定或其它生产脚本
diagnosis:
  root_cause: `New-SelftestSnapshot` 直接从 linked/detached worktree 执行 `git clone`，却未在 clone 前钉住 source HEAD 与 source 的权威 master OID。Git 会把 detached HEAD 归到任一同 SHA 本地分支，并只导出源仓本地 heads；随后函数又把 clone 的 `origin/master`（实际是调用者落后的本地 master）建成本地 master。core 的 14f 因而把几十个已在远端 master 的提交误算成本次 diff，产生缺文档假红。
  same_class: 三个分片都经同一 `New-SelftestSnapshot` 建仓；修复必须在该单点同时钉 source HEAD、快照当前分支、本地 master 与远端跟踪 master，不能只给 core 特判或在 14f 里吞掉结果。
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard core
dod_exit: 0
dod_assert: 8.2e hermetic fixture 构造 detached source HEAD、同 SHA 旁支、落后本地 master、最新 origin/master 与 dirty overlay；快照须在非 detached 的隔离分支运行精确 source HEAD，本地 master 与 origin/master 均精确等于 source 解析出的权威 base OID，overlay 不丢；core PASS 且只删 HEAD/base 任一钉定语句的手工 mutation 精确 RED、还原后 SHA256 一致
review_gate: codex {verdict:pass}
hygiene: 复用 `_gitbase.ps1` 的 `Resolve-ScaffoldBaseRef`，不新增第二份 base 选择器；不把真实 all 套件嵌进 card DoD，合并前仅补跑一次 detached all 作为宿主证据
doc_sync: merge 后将 TD156 置 paid、TASK-BOARD 标 merged、归档本卡并刷新 cards-index（R5）
---

# T0-DEBT-SELFTEST-SNAPSHOT-BASELINE

## 产出

让 `selftest.ps1 -Shard all` 的独立快照只消费调用者已解析并钉死的提交身份，不再受 linked worktree、detached HEAD、同 SHA 多分支或落后本地 master 影响。

## RED-first

先只扩 8.2e fixture，制造 source `local master=A`、`origin/master=B`、detached `HEAD=B` 且另有分支指向 B。当前实现的快照会把 master 留在 A，断言必须以专属 TD156 标记 RED；若 fixture 在当前实现上通过，不得改生产 helper。

## 最小实现

clone 前从 source 解析一次 HEAD OID 与权威 master OID；clone 后先切到隔离分支并钉 HEAD，再把快照的 `refs/heads/master` 与 `refs/remotes/origin/master` 同步到同一 base OID，随后才叠加调用者 dirty 文件。任一步解析/写 ref 失败均中止，绝不退化为 detached 或 skip。

## 被否决方案

- 不更新调用者本地 master：那会污染共享仓并可能覆盖未推送工作。
- 不让快照保持 detached：14f 会把它当不可解析而 skip，正好掩盖本缺陷。
- 不从 GitHub 重克隆：selftest 的离线、确定性边界不接受运行期网络依赖。
- 不在 14f 特判 all：根因在快照身份，三个分片应共享同一修复。
