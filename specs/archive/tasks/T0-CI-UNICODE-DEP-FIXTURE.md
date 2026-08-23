---
id: T0-CI-UNICODE-DEP-FIXTURE
title: 补齐 license scanner 自检夹具的 Unicode helper 依赖并防假绿
depends_on: [T0-DEBT-LICENSE-SCALAR-FORMAT]
status: merged
branch: T0-CI-UNICODE-DEP-FIXTURE
worktree: C:\wt\T0-CI-UNICODE-DEP-FIXTURE
allow_paths:
  - scripts/selftest.ps1
forbid:
  - 修改 check-licenses.ps1 生产逻辑或放宽许可判定
  - 以仅断言非零退出的方式允许启动失败冒充违禁依赖命中
  - 复制 selftest.ps1 或新建平行 scanner harness
non_goals:
  - 修改 secrets consumer、Unicode helper 或依赖扫描范围
  - 修改 CI matrix、runner 或 Gradle 配置
diagnosis:
  root_cause: check-licenses.ps1 新增 dot-source _unicode.ps1 后，17p3 与 17cc scanner 的两个 scratch fixture 未同步复制该依赖，导致启动即退出；17p3 又只断言 exit != 0，因而假绿。
  same_class: 补齐两个 license scanner scratch fixture，同步 card2 改名后的两条 mutation 锚点，并把 17p3 收紧为非零且诊断点名 gpl-pkg@1.0.0。
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded
dod_exit: 0
dod_assert: 17p3 真实启动 scanner，仅在非零且诊断点名 gpl-pkg@1.0.0 时通过；17cc scanner 全矩阵真实运行；override metadata 与 diagnostic sanitizer 两条 mutation 各自翻红
review_gate: codex {verdict:pass}
hygiene: 只扩展现有 fixture copy list、17p3 行为断言与漂移 mutation marker；删除任一 helper copy 或弱化坐标断言都必须翻红
doc_sync: 合并后归档本卡，在 TASK-BOARD 记录 PR/commit/R3 证据
---

# T0-CI-UNICODE-DEP-FIXTURE

## 单一产出

license scanner 的两个 scratch fixture 具备与生产脚本一致的 dot-source 依赖，17p3 不再把启动失败当成 GPL 命中，card2 的 metadata mutation 锚点与当前生产接线对齐。

## 验收

```powershell
pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded
```

- 17p3 必须点名 `gpl-pkg@1.0.0`。
- 17cc scanner 夹具不得因 `_unicode.ps1` 缺失在启动阶段退出。
- 两条 card2 metadata mutation 必须真实命中当前接线。
