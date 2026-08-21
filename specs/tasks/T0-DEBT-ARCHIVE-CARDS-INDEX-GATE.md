---
id: T0-DEBT-ARCHIVE-CARDS-INDEX-GATE
title: 让归档任务卡索引保持为可验证的真实投影（偿还 TD146）
depends_on: []
status: todo
branch: T0-DEBT-ARCHIVE-CARDS-INDEX-GATE
worktree: C:\wt\T0-DEBT-ARCHIVE-CARDS-INDEX-GATE
allow_paths:
  - scripts/archive.ps1
  - scripts/selftest.ps1
  - scripts/verify.ps1
  - specs/archive/cards-index.md
forbid:
  - 检查模式移动任务卡、改 tracker、写索引或产生任何仓库副作用
  - 自动归档仍为 todo/in-progress/in-review 的活卡
  - 扩大到 tech-debt/lessons 索引或重写 archive 搬运协议
  - 运行 full selftest；只跑 core 分片与项目 verify
non_goals:
  - 通用文档生成框架
  - 修改 R5、task、review 或 GitHub 合并流程
  - 修复历史归档卡的入站引用
diagnosis: R5 可手工把 merged 卡移到 specs/archive/tasks/，而现有 selftest 只证明 archive.ps1 在隔离夹具中会生成正确索引；没有普通 PR 闸比较仓库当前 cards-index.md 与真实归档目录，故索引已静默停在 36 行而归档目录有 39 张卡。
dod_command: pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet
dod_exit: 0
dod_assert: archive.ps1 的只读检查复用正常生成路径的同一投影函数，当前 39/36 漂移先 RED；缺行、多行、错标题、错状态或头注计数均以 [ARCHIVE-CARDS-INDEX-DRIFT] 非零失败且零写盘；正常 archive 重建后检查 GREEN；verify 必须调用该检查，删除检查接线或放宽 exact 比较均被现有 12e 隔离夹具杀死。
review_gate: codex {verdict:pass}
hygiene: 只提取一份卡索引文本生成函数；测试运行真实 archive.ps1，不镜像其排序/格式逻辑；不新增第二个 checker 脚本
doc_sync: merge 后将 TD146 置 paid、TASK-BOARD 标 merged、归档本卡并用同一 archive 投影重建 cards-index.md（R5）
---

# T0-DEBT-ARCHIVE-CARDS-INDEX-GATE

## 产出

把“`specs/archive/cards-index.md` 是 `specs/archive/tasks/` 的真实投影”从注释承诺升级为普通 `verify` 会执行的只读合同。检查模式只比较，不搬卡、不改文件。

## RED-first

先扩展 12e 的真实 `archive.ps1` 隔离夹具：生成正确索引后删去一条卡行，要求只读检查精确非零并输出稳定状态码；再恢复正确索引，要求检查通过且所有文件哈希不变。当前脚本没有检查参数，测试须先因能力缺失而 RED。

## 最小实现

从现有 section 6 提取唯一的卡索引文本生成函数，正常归档与只读检查共用。`verify.ps1` 在 Android/项目测试之前调用只读检查；漂移直接计红，不自动修复。

## 被否决方案

- 不只手工刷新当前索引：下一张 R5 卡仍会复发。
- 不新增独立 checker：复制排序/格式规则会造第二真相源。
- 不把检查塞进 GitHub 专属 workflow：本地 verify 与 CI 应执行同一合同。
