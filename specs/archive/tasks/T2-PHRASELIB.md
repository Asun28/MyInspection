---
id: T2-PHRASELIB
title: 双语短语库种子内容 + 查询接口
depends_on: [T1-TEMPLATE-ENGINE]
parallelizable_with: [T2-ROUTINE-CONTENT, T2-PHOTO-PIPELINE, T2-CAPTURE-CORE]
status: merged
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
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.phrase.*"
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
  **v1 契约澄清（2026-08-17 R3 仲裁）**：`suggestFor` 的 v1 语义=**按状态过滤**（本行括注即原意）；`stableId` 是为消费端
  item-context 预留的接口缝——item→分类映射需要模板内容数据（在本卡 `allow_paths` 之外）且属选择器逻辑（本卡 `non_goals`
  排除的 T2-CAPTURE-UI 消费面），**不在本卡实现**，KDoc 记明预留意图即可；评审按此判，勿再以「stableId 未使用」block。
- 可选字段 `shortcut`（如 "FWT" → "Fair wear and tear / 正常损耗"——Property Inspect Dictionary 先例，docs/research/property-inspect.md H.2）：备注输入框命中 shortcut 即展开；数据层本卡带上，UI 消费在 T2-CAPTURE-UI 顺手接（不接也不算失败）。
- 双模复核：**独立第二模型**（非同作者、非 Claude）逐条复读双语对应与客观性。席位按 L26 工具无关：默认 Luna Max，未接入本 harness 时以其他独立模型（如 DeepSeek / MiMo）替代并在 PR 记录标注（同 T2-ROUTINE-CONTENT 2026-08-16 仲裁：评审按「独立第二模型复核已做」判，不追字面模型名）。**复核记录落位（L227）**：R3 评审者只读 diff、看不见 PR body——记录除附 PR 外，须在 **diff 内**留摘要（内容测试类头注：复核模型/日期/逐条结论条数/已修正项），评审以 diff 内摘要为准。

## 验收 / 执行建议
dod 见 front-matter。首选 DeepSeek V4 Pro · low；备选/复核 Luna Max。难度 S。
