---
id: T6-HHC
title: Healthy Homes 五项子模块 + 独立合规快照输出
depends_on: [T3-PDF-RENDERER]
parallelizable_with: [T6-TEMPLATES-REST]
status: todo
branch: T6-HHC
worktree: C:\wt\T6-HHC
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/hhc/
  - android/core/src/test/kotlin/nz/myinspection/core/hhc/
  - data/templates/hhc-v1.json
  - android/app/src/main/kotlin/nz/myinspection/app/feature/hhc/
forbid:
  - 把 HHC 快照伪装成法定合规证明（输出注明「自查快照，非法定评估」+ 免责声明）
non_goals:
  - HHC 法定计算器（加热容量计算等，v2 再议——本版 = 检查项快照）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.hhc.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 五项子模块（加热/绝缘/通风/水汽侵入与排水/挡风）各有检查项且可独立出快照 PDF（复用 composer/renderer 管线的子报告形态）；日常巡检复核点（地板下绝缘未破坏/抽风扇运转/地面防潮层完好）在 Routine 模板引用同 stable_id（跨模板同项对齐测试）；assembleDebug 绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T6-HHC

## 产出
`core/hhc` + `data/templates/hhc-v1.json` + 独立「HHC 合规快照」入口与 PDF 输出（需求 §10 [定]）。

## 上下文包（执行模型必读）
- 五项 = NZ Healthy Homes Standards 维度：heating / insulation / ventilation / moisture ingress & drainage / draught stopping。每项 3–8 个检查快照条目（存在性/状态/照片），双语。
- **字段模型抄官方合规声明**（MB_TEN8113_01/26，synthesis NZ 节）：heating（主起居室所需 kW / 加热器类型与 kW）；insulation（每区 R 值或厚度 / 类型 / 无霉湿损缝勾选）；ventilation（开窗面积 ≥ 地板 5% / 风扇直径或排量 / 2019-07 前后）；moisture（檐槽排水 / 50% 周长遮挡测试 / 防潮布）；draught（壁炉封堵 / 缝隙 >3mm「$2 硬币测试」）。
- **阈值入 configs**（不硬编码进代码，与合规引擎同纪律）：天花 R2.9（1/2 区）/R3.3（3 区）· 地板 R1.3 · 2016-07 前天花 ≥120mm 即过 · 厨房扇 150mm/50 l/s · 浴室扇 120mm/25 l/s · 固定加热器 ≥1.5kW · 所需 >2.4kW 禁非热泵电加热。
- **快照评级标签抄官方 checklist**（MB_TEN8271_09/25）：二值「**Room to improve / You're on track**」（+N_A），别自创词。
- 快照可独立生成（不必跑整场巡检）：建一次轻量 HHC 巡检（type 复用 ANNUAL 引擎路径或独立 HHC 类型——实现者按引擎改动最小原则选，卡不锁；若加类型须走模板类型枚举的版本评审）。
- 输出 = composer 的子报告形态（房东版单版即可）；封面注明自查性质 + 免责声明（措辞复用 T3 固定文案槽）。
- Routine 里的三个日常复核点与 HHC 模板同 stable_id（跨模板对齐让历史序列连续——测试断言）。

## 验收 / 执行建议
dod 见 front-matter。首选 DeepSeek V4 Pro · high；备选 Terra。难度 M。
