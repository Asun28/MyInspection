---
id: T0-LICENSE-SCANNER
title: Gradle 已解析坐标图与离线执行基础（TD2 子卡 1/4）
depends_on: [T0-GATE-HARDENING]
status: in-progress
branch: T0-LICENSE-SCANNER
worktree: C:\wt\T0-LICENSE-SCANNER
allow_paths:
  - scripts/check-licenses.ps1
  - scripts/license-scanner-check.ps1
forbid:
  - android/（本卡只读取 Gradle 结果，不改产品或构建定义）
  - 联网补下载 wrapper、依赖、POM 或许可元数据
  - 为保留 PR #20 现状而继续夹带后续三卡的策略、诊断、CI 或文档改动
non_goals:
  - POM license 读取、许可分类和人工豁免表（T0-LICENSE-POLICY）
  - 日志脱敏、长度上限和注入安全（T0-LICENSE-DIAGNOSTICS）
  - CI 接线、政策文档和 TD2 总验收（T0-LICENSE-CI-INTEGRATION）
dod_command: pwsh -NoProfile -File scripts/license-scanner-check.ps1 -Suite graph
dod_exit: 0
dod_assert: 四张批准的 Gradle classpath 图在 offline 模式下各执行一次；只产出已解析的 concrete group:artifact:version，排除项目节点、约束行、重复项和被替换旧版本。Windows 选 gradlew.bat，Unix 由 sh 执行 gradlew；wrapper distribution 或 native cache 未预置时零启动并 fail-closed。逐项配删除/替换变异，必须命中 graph 专属断言。
review_gate: codex {verdict:pass}
hygiene: graph 套件独立于全量 selftest 可在 R3 沙箱快速复跑；只保留能击杀坐标解析、配置范围、offline preflight 或平台 wrapper 变异的测试
doc_sync: 本卡只把自身状态/PR 证据同步到任务卡；TD2 保持 carded，不得提前 paid
---

# T0-LICENSE-SCANNER

## 目标

把 TD2 的第一段收敛成一个可独立评审的产物：从 Gradle **真实解析结果**取得稳定的 concrete GAV 列表，且整个执行边界可离线、跨平台、fail-closed。

批准的图只有：

1. `:core:runtimeClasspath`
2. `:core:testRuntimeClasspath`
3. `:app:debugRuntimeClasspath`
4. `:app:releaseRuntimeClasspath`

编译器/插件、lint、IDE、migration、UTP、instrumentation 和 test-fixture 等非分发图不在本卡扩张范围内。

## 输入与输出

- 输入：现有 `T0-GATE-HARDENING` 的 Gradle 清单发现能力、仓库内 wrapper、已预热的本机 Gradle cache。
- 输出：四张图的去重 concrete GAV 集合；稳定的 wrapper 选择与 offline preflight；可单独运行的 `graph` 测试套件。
- 下游接口：`T0-LICENSE-POLICY` 只消费 GAV 集合，不重新解析 Gradle 文本。

## PR #20 收缩规则

PR #20 是本卡的现有执行分支，但当前净 diff 混入了四卡内容。后续不得改写历史；先保留当前完整 head 的只读恢复引用，再用 forward-only commit 让 PR #20 的**最终净 diff**只保留本卡输出。被剥离的已完成代码按后继卡逐卡移植，不能继续在 #20 上接受跨卡 R3 修复。

## 验收

运行 front matter 的 `dod_command`。评审还要确认 PR 净 diff 不含 POM/exception policy、通用诊断 hardening、CI 或文档同步。

## 根因诊断

旧卡同时拥有 Gradle 图、POM 策略、诊断安全、CI 和文档，导致 PR #20 净变更 2,422 行、R3 首屏只能覆盖约三分之一。这里按消费者边界切出第一段，不把“已经写完”当作继续合卡的理由。
