---
id: T0-LICENSE-POLICY
title: Gradle POM 许可策略与 exact-GAV 豁免（TD2 子卡 2/4）
depends_on: [T0-LICENSE-SCANNER]
status: todo
branch: T0-LICENSE-POLICY
worktree: C:\wt\T0-LICENSE-POLICY
allow_paths:
  - scripts/check-licenses.ps1
  - scripts/license-scanner-check.ps1
  - configs/licenses/
forbid:
  - 联网查询 POM、许可数据库或远端仓库
  - 未知许可、损坏 POM、坐标不一致或畸形豁免 fail-open
  - 把 PyPI/npm 的共享 Scan 语义顺带改成 Gradle 专用策略
non_goals:
  - 修改 Gradle 图/配置枚举和 wrapper 执行语义（由前置卡冻结）
  - 通用日志脱敏与 bounded diagnostics（T0-LICENSE-DIAGNOSTICS）
  - CI 接线、政策文档和 TD2 paid（T0-LICENSE-CI-INTEGRATION）
dod_command: pwsh -NoProfile -File scripts/license-scanner-check.ps1 -Suite policy
dod_exit: 0
dod_assert: 对前置卡输出的每个 concrete GAV，只从对应 Gradle cache 坐标目录读取禁用 DTD/外部实体的 POM；POM 坐标必须匹配请求坐标，每个 license 名称非空并保留多许可项。许可按明确 allow/yellow/forbidden 分类；未知或损坏元数据非零。豁免仅接受 schema 完整、exact-GAV、规范许可值和完整证据字段；任一宽匹配/别名/畸形值变异必须由 policy 专属断言击杀。
review_gate: codex {verdict:pass}
hygiene: policy 套件只覆盖 POM、分类与豁免边界；复用 graph 套件产物，不重复构造 wrapper/图解析测试
doc_sync: 本卡只登记自身 PR 证据；TD2 仍为 carded
---

# T0-LICENSE-POLICY

## 目标

消费 `T0-LICENSE-SCANNER` 冻结的 concrete GAV 集合，为每个坐标产生可审计的许可判定；本卡不再拥有“如何跑 Gradle”。

## 单一产出

1. 安全读取 cache 中与 exact GAV 对应的 POM `<licenses>`。
2. 将许可分为 permissive、yellow、forbidden 或 unknown；unknown 与元数据错误一律 fail-closed。
3. 提供最小 exact-GAV exception schema。例外必须带许可、证据 URL、登记人和日期，不能用 group/artifact 通配。
4. 输出结构化 finding；文本呈现与脱敏留给下一卡。

## 依赖门

只有前置卡的 graph DoD、R3 和 merge 证据齐全后才能开工。原因是策略测试必须消费唯一的 concrete GAV 合同，不能自己再实现一套解析器。

## 根因诊断

旧 PR 在坐标提取尚不稳定时同时修 POM、GPL/EPL 分类和 exception schema，后续每次 R3 都跨层返工。本卡把“得到坐标”和“判定坐标”分开，使策略 review 能只看许可正确性。
