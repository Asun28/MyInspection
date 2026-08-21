---
id: T0-CI-DOCS-FAST-PATH
title: 让纯文档 PR 保留轻量 verify 状态而跳过 Android 工具链
depends_on: []
status: todo
branch: T0-CI-DOCS-FAST-PATH
worktree: C:\wt\T0-CI-DOCS-FAST-PATH
allow_paths:
  - .github/workflows/ci.yml
  - scripts/ci-docs-scope.ps1
  - docs/DEVOPS-WORKFLOW.md
forbid:
  - 对 pull_request 使用 workflow-level paths-ignore，令 required verify 永久 Expected
  - 纯文档路径跳过 check-cards、archive projection 或 check-secrets
  - 把 workflow、脚本、源码、混合改动、空 diff 或分类失败判为纯文档
  - 放松 push 到 main/master 或 workflow_dispatch 的完整 CI
non_goals:
  - 修改 selftest、task/review/ship 或分支规则集
  - 缩短代码 PR 的 Android、许可或 verify 闸
  - 重构现有 CI action 版本、缓存或 Gradle 命令
diagnosis:
  root_cause: pull_request 无差别 provision Java/Android/Gradle 并运行完整 build；R5 纯文档 PR #111 因 archive 元数据漂移耗时三分钟后才在 E2E verify 失败。
  same_class: 本卡只建立 PR 文档快速通道；push/master 事后检测与代码 PR 保持完整。
dod_command: pwsh -NoProfile -File scripts/ci-docs-scope.ps1 -SelfTest
dod_exit: 0
dod_assert: docs/**、specs/** 与任意 Markdown 的纯集合分类为 docs-only；源码/脚本/workflow/混合/空集合均 full；PR docs-only 仍运行卡片、archive 与密钥闸并产出 verify，跳过 Python/Java/Android/Gradle/license/E2E；push 与手动触发全跑
review_gate: codex {verdict:pass}
hygiene: 使用独立 bounded classifier 自测，不运行 selftest；删除 docs-only 条件或任一保留闸的单句变异必须翻红
doc_sync: 合并后归档本卡、把 TD159 置 paid，并在 TASK-BOARD 记录 docs-only lane 证据
---

# T0-CI-DOCS-FAST-PATH

## 目标

纯文档 PR 不再启动 Java、Android SDK、Gradle cache warm-up、许可扫描或产品 verify；但 required `verify` job 仍快速返回确定结果，避免 GitHub `paths-ignore` 令必需检查长期停在 Expected。

## 分类边界

- 仅 `docs/**`、`specs/**` 或任意目录下 Markdown 的非空改动集合可进入快速通道。
- `.github/**`、`scripts/**`、`android/**`、删除/混合源码、空 diff 或 git 分类失败一律走完整 CI。
- 仅 PR 可快速；push 到默认分支与手动触发始终完整执行。

## 保留闸

快速通道仍执行任务卡校验、归档卡索引投影与普通密钥扫描；三者任一失败时 `verify` 非零。分类器以独立参数夹具自测，不依赖 GitHub runner 或网络。
