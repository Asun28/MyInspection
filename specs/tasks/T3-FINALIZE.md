---
id: T3-FINALIZE
title: finalize 事务：完备性校验 → canonical 哈希落库 → 只读强制 + Supplement 哈希链
depends_on: [T1-CANON-HASH]
parallelizable_with: [T2-CAPTURE-UI, T3-REPORT-COMPOSER]
status: merged
branch: T3-FINALIZE
worktree: C:\wt\T3-FINALIZE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/finalize/
  - android/core/src/test/kotlin/nz/myinspection/core/finalize/
forbid:
  - finalize 后任何原始条目可变路径（append-only Supplement 是唯一出口）
non_goals:
  - 报告生成（composer 消费 finalize 后快照）；补充说明 UI（并入 T3-HISTORY-COMPARE 或后续微卡）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.finalize.*"
dod_exit: 0
dod_assert: finalize 事务测试绿（缺强制照片/缺状态 → 拒并列清单；通过 → finalized_at+data_hash 原子写入）；finalize 后写原条目计 0 行（谓词强制）；Supplement 链测试绿（prev_hash 锚 data_hash、逐条链接、乱序插入被拒）；重复 finalize 幂等拒绝
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T3-FINALIZE

## 产出
`core/finalize`：finalize 用例（校验→物化快照→哈希→原子置位）+ Supplement 追加用例（哈希链）。

## 上下文包（执行模型必读）
- **事务序**（ADR-0003）：① 完备性校验 = capture 核的 missingPhotos + 全项有状态（缺则拒，返回逐项清单给 UI）；② 物化 InspectionSnapshot（T1-CANON 投影）；③ `data_hash = sha256Hex(canonicalJson(snapshot))`；④ 同一 DB 事务写 finalized_at + data_hash。任一步败=全回滚。
- 只读强制已在 SQL 层预埋（T1-SCHEMA 的谓词）；本卡在用例层再挡一道（进攻性测试：直接调写接口对 FINALIZED 巡检 → 0 行 + 显式错误）。
- Supplement（需求 §5：finalize 后只可追加带独立时间戳的补充说明）：`addSupplement(inspectionId, text, clock)` → prev_hash = 上一条 chain_hash（首条锚 data_hash）→ chain_hash 由 core/canon 的 supplementChainHash 算。链校验函数 `verifyChain(inspectionId)` 给报告/备份复用。
- 时钟注入；哈希可复验测试：finalize 后改「排除域」字段（如 updated_at）重算哈希不变；改哈希域任一字段（测试直连 SQL 绕谓词模拟腐坏）→ verifyChain/复算即红。

## 验收 / 执行建议
dod 见 front-matter。首选 DeepSeek V4 Pro · high；备选 Sonnet 5 max。难度 M。
