---
id: T2-PHRASELIB
title: 双语短语库种子内容 + 查询接口
depends_on: [T1-TEMPLATE-ENGINE]
parallelizable_with: [T2-ROUTINE-CONTENT, T2-PHOTO-PIPELINE, T2-CAPTURE-CORE]
status: todo
branch: T2-PHRASELIB
worktree: C:\wt\T2-PHRASELIB
allow_paths:
  - data/templates/phrases-v1.json
  - android/core/src/main/kotlin/nz/myinspection/core/phrase/
  - android/core/src/test/kotlin/nz/myinspection/core/phrase/
forbid:
  - 短语进模板文件混编（短语库独立文件，与检查项模板分开演进）
non_goals:
  - 选择器 UI（T2-CAPTURE-UI 消费）；用户自定义短语管理界面（v1.1）
dod_command: cmd /c android\gradlew.bat --offline --no-daemon -q :core:test --tests "nz.myinspection.core.phrase.*"
dod_exit: 0
dod_assert: phrases-v1.json 过校验（双语齐全、分类合法、无重复）；按分类/按检查项上下文查询接口测试绿；种子 ≥ 60 条且覆盖全部分类
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T2-PHRASELIB

## 产出
`data/templates/phrases-v1.json`（双语短语种子）+ `core/phrase` 加载/查询接口。

## 上下文包（执行模型必读）
- 定位（需求 §7）：80% 备注是重复话——「Minor wear consistent with age of property / 轻微磨损，与房龄相符」这类一点即用；语音留给具体描述。
- 分类建议：condition-general（通用状况）/ wear（磨损类）/ damage（损坏类）/ cleaning（清洁类）/ action-needed（待处理类）/ hhc（Healthy Homes 复核话术）。每条：en、zh、category、sort、可选 appliesToStatuses（如 wear 类只在 FAIR 时推荐）。
- 种子来源：竞品调研 `docs/research/` 的常用 condition 措辞 + NZ condition report 惯用语；措辞客观中性（房客版报告会引用备注，别写内部判断——需求 §8 报告分版立场）。
- 查询接口：`phrasesFor(category)` + `suggestFor(stableId, status)`（按状态过滤推荐）；纯 :core，数据经与模板同构的 resources 加载路径。
- 可选字段 `shortcut`（如 "FWT" → "Fair wear and tear / 正常损耗"——Property Inspect Dictionary 先例，docs/research/property-inspect.md H.2）：备注输入框命中 shortcut 即展开；数据层本卡带上，UI 消费在 T2-CAPTURE-UI 顺手接（不接也不算失败）。
- 双模复核：Luna Max 逐条复读双语对应与客观性。

## 验收 / 执行建议
dod 见 front-matter。首选 DeepSeek V4 Pro · low；备选/复核 Luna Max。难度 S。
