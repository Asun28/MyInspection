---
id: T2-ROUTINE-CONTENT
title: Routine 双语模板内容（80–120 项）+ schema 校验绿
depends_on: [T1-TEMPLATE-ENGINE]
parallelizable_with: [T2-PHOTO-PIPELINE, T2-CAPTURE-CORE, T2-PHRASELIB]
status: todo
branch: T2-ROUTINE-CONTENT
worktree: C:\wt\T2-ROUTINE-CONTENT
allow_paths:
  - data/templates/routine-v1.json
  - android/core/src/test/kotlin/nz/myinspection/core/content/
forbid:
  - 改模板引擎/schema 迁就内容（内容错就改内容）
non_goals:
  - Ingoing/Exit/Annual 内容（T6-TEMPLATES-REST）
  - 短语库（T2-PHRASELIB）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.content.*"
dod_exit: 0
dod_assert: routine-v1.json 过引擎全量校验；项数在 80–120；stable_id 全唯一；每项双语齐全；每房间有 ROOM_PANORAMA 项；照 docs/research/ 调研报告的房间/条目覆盖清单无缺漏
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T2-ROUTINE-CONTENT

## 产出
`data/templates/routine-v1.json`：NZ 租赁 Routine 巡检模板正文（80–120 项、双语、带 photoRule），过引擎校验 + 内容完备性测试。

## 开卡前须先定一件事：测试怎么读到这份 json
`T1-TEMPLATE-ENGINE` 的上下文包允许它改 `android/core/build.gradle.kts`「把 `data/templates/` 注册为测试 resources srcDir」，但该卡**未动用**——它在 `data/templates/` 下只放得了 `README.md`，注册一个没有模板文件的 srcDir 等于落一段没有测试盯住的构建配置（详见该卡「实现说明」段）。于是本卡的 `core/content/` 测试要读 `data/templates/routine-v1.json`，只有两条路，**开卡时二选一并写进本卡**：
1. 把 `android/core/build.gradle.kts` 加进本卡 `allow_paths`，注册 srcDir，测试走 `getResourceAsStream("/routine-v1.json")`（推荐：路径不随工作目录漂移）；
2. 测试按相对路径读（`:core` 的 Gradle test 工作目录 = `android/core`，故为 `../../data/templates/routine-v1.json`），不动构建文件。

另注：`data/*` 被 `.gitignore` 排除，`routine-v1.json` 入库须 `git add -f`（同 `data/templates/README.md`）。

## 上下文包（执行模型必读）
- **这是抄写+编纂卡：抄错不报错，故双模复核强制**（Codex 风险 #5）——作者产出后由 Luna Max 逐项复读（英文措辞、中文对应、条目归属房间是否合理），复核记录附 PR。
- 结构/字段语义见 `data/templates/README.md`（T1-TEMPLATE-ENGINE 产出）与该卡上下文包。
- **权威骨架 = NZ 官方 Property Inspection Report 表**（MB_TEN0004_10/25，调研 synthesis「官方模板即默认模板」节）：房间 LOUNGE/KITCHEN-DINING/BATHROOM/LAUNDRY/BEDROOM（repeatable）/GENERAL；每房间重复条目组（Wall-Doors/Lights-Power points/Floors-Coverings/Windows/Blinds-Curtains）+ 房间专属（Kitchen: Cupboards/Sinks-Benches/Oven/Refrigerator/Ventilation；Bathroom: Mirror-Cabinet/Bath/Shower/Basin/Toilet/Ventilation；Laundry: Washing machine/Wash tub；Lounge: Heater）；GENERAL 含 Rubbish bins/Locks/Garage-Carport/Grounds/Keys supplied 数量/Insulation/Gutters-downpipes/Ground moisture barrier。**另加：7 点烟雾报警器声明条目组（官方表照抄）+ 水表读数记录项**。在官方表之上按调研补充 Exterior 围护细分与 Healthy Homes 日常复核点（地板下绝缘未破坏/抽风扇运转/防潮布完好——与 T6-HHC 同 stable_id）。
- 竞品模板（docs/research/ 各篇）只作条目措辞与覆盖度交叉核对，不推翻官方骨架。
- 措辞立场：条目文案 = 客观检查对象（「Walls & ceiling condition」），不是判断句；判断进 status/note。英文为准、中文并列（术语如 flashing/ground moisture barrier 保英文加注——需求 §8）。
- stable_id 起名：房间缩写-对象-两位序号（KIT-SINK-01）；**v1 定稿后永不改 id**。

## 验收 / 执行建议
dod 见 front-matter（内容完备性测试 = 房间清单/项数区间/双语/唯一性断言，写进 content 测试包）。
首选 DeepSeek V4 Pro · medium；备选 Luna Max；**复核 Luna Max 强制**。难度 S（量大但机械）。
