---
id: T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST
title: 偿还 TD4：只豁免 SQLDelight schema 快照并恢复迁移验证
depends_on: [T0-LICENSE-SCANNER]
status: merged
branch: T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST
worktree: C:\wt\T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST
allow_paths:
  - scripts/check-secrets.ps1
  - scripts/selftest.ps1
  - configs/secrets/
  - android/core/build.gradle.kts
  - android/core/src/main/sqldelight/
forbid:
  - 放行任意运行期、用户数据或不在精确清单内的 .db 文件
  - 弱化现有 secret 模式、冻结路径闸或离线验证
  - 改业务 schema；本卡只登记基线快照并恢复 verifyMigrations
non_goals:
  - 编写 v2 业务迁移（由 T2-ROOM-REPEATABLE 的版本评审承担）
  - 通用 secret 扫描器重写或许可扫描器改造
dod_command: pwsh -NoProfile -File scripts/check-secrets.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:check
dod_exit: 0
dod_assert: 仅逐路径登记的 SQLDelight schema 基线快照可被追踪；任一同目录外或改名的 .db 变异仍被 check-secrets 指名并拒绝；verifyMigrations 已挂回 :core:check 且缺失/错误迁移会使检查翻红
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: specs/tech-debt-tracker.md 将 TD4 置 paid；T2-ROOM-REPEATABLE 解除 TD4 禁止项（R5）
---

# T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST

## 产出
一个 fail-closed 的精确路径豁免机制，只允许已审查的 SQLDelight schema 基线快照入库，并把 `verifyMigrations` 恢复到 `:core:check`。

## 安全边界
- allowlist 的键必须是仓库相对精确路径，不能使用目录、glob 或扩展名级放行。
- 每个条目要带用途说明；解析失败、路径不存在、重复条目或未知字段均失败关闭。
- 自测必须植入一枚相邻的假运行期数据库，证明一般 `*.db` 防泄露规则仍有效。

## 验收
见 front-matter。执行前先确认 `T0-LICENSE-SCANNER` 已合并，避免两卡同时修改 `scripts/selftest.ps1`。

## R3 round-cap 记录（2026-08-21）

PR #47 第二轮 R3 时，allowlist、父级 reparse 防护、重复项 mutation 与真实 migration ADDED/REMOVED 断言均已通过；唯一阻塞是 Windows 上 detached migration fixture 紧接 Gradle 负例后的清理偶发失败，且旧清理丢弃 git stderr。按两轮上限停止本卡自动评审，不再原地扩张；后续由 `T0-DEBT-MIGRATION-FIXTURE-CLEANUP` 在本卡经人裁后承接，TD4 仍保持 `carded`。
