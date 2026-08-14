---
# id 命名规范（机检）：T<阶段号>-<大写短横名>，正则 ^T\d+-[A-Z0-9]+(-[A-Z0-9]+)*$
#   合规 T0-SCAFFOLD / T2-API / T3-REVIEW-GATE ｜ 不合规 t1-foo / T1_FOO / my-task
#   id 必须 == 文件名 == branch == worktree 末段。本模板的 T?-EXAMPLE 是占位故意违规，check-cards 跳过。
id: T?-EXAMPLE
title: 一句话可交付产出物
depends_on: []
plan_ref: docs/PLAN.md#节名        # 可选。本卡对应计划节 —— 实现 agent 的最小上下文指针（免读全计划）
parallelizable_with: []           # 可选。可并行卡 id；并行卡 allow_paths 必须互不重叠（check-cards 机检）
status: todo            # todo | in-progress | in-review | merged
branch: T?-EXAMPLE
worktree: C:\wt\T?-EXAMPLE   # = <WorktreeRoot>\<id>（默认 <系统盘>\wt），WorktreeRoot 见 scripts/_config.ps1
allow_paths:            # 允许改动的路径（ship 范围闸机检越界 + 评审判质量）
  # 若 dod_command 引入新依赖/工具，allow_paths 须含其清单文件（pyproject.toml(+lock) / package.json）；
  # 否则「评审按 allow_paths 判越界」与「DoD 需装该依赖」自相矛盾（见 docs/PLAN-TEMPLATE.md §8）。
  - path/to/...
forbid:                 # 禁止事项（按项目硬边界填）
  - 未授权的运行期出站网络 / 写登录态 / 自动发布
  - 改动冻结契约或 schema（除非走版本评审）
non_goals:              # 本卡能力级「不做」：从计划「本版砍掉/推迟」下沉到本卡；评审 #14 据此判「能力级越界（顺手多做）」。无则填 none
  # 与 forbid 的区别：forbid=横切硬边界（网络/登录态/冻结契约）；non_goals=这个功能本次刻意不做的能力（如 图片评价/商家回复/编辑）
  - <本卡刻意不做的能力，逐条；none 表示无>
# diagnosis:            # 仅 bugfix 卡（防线②DEBUG / 十纪律#7 · 评审 #17）：修根因非表象。非 bugfix 卡免填、保持注释态。
#   root_cause: <真因（为何会坏），非失败表象>
#   same_class: <同类 caller/site 是否已排查并覆盖（一处 patch 于 N 处缺陷 → 评审 #17 lean flag）>
dod_command: uv run python -m pytest <tests> -q   # 改成你项目的 DoD 命令；只用 CI 已预装/verify 链保证存在的工具(如 uv)；其余工具(bandit/npm 等)须在卡内声明安装并把清单纳入 allow_paths（否则 CI 上「工具缺失」会被误判为「DoD 失败」，污染完成度信号）
# TD69/L95：dod_command 别嵌套 `pwsh -Command "…$var…"`——task.ps1 已在 pwsh 下执行本字段，双层包裹会把内层
#   $var 内插成空串、铸成 vacuous RED（-Phase red 把 ParserError 当合法 RED）。直接写 PowerShell、用**无变量**写法：
#   把判断内联进 if，如 pwsh -NoProfile -Command "if (-not ((Select-String …) -and …)) { exit 1 }"（check-cards 机检拒绝违例）。
dod_exit: 0
dod_assert: <命令产出的可机检断言>
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: CLAUDE.md 当前阶段 + README（R5）
---

# T?-EXAMPLE

## 产出
（单一可交付产出物，对应计划该卡。）

## 禁止
（按项目硬边界：确定性/离线、不碰冻结契约、仅原创实现 …）

## 非目标（本卡刻意不做的能力）
（从计划「本版砍掉/推迟」承接到本卡的**能力级**排除，防 AI「顺手多做」；无则写 none。评审 #14 查 diff 有没有实现这里排除的能力。与「禁止」区别：禁止=横切硬边界，非目标=这个功能本次不做的能力。）

## 验收（DoD = 命令 + 退出码 + 断言）
```powershell
<dod_command>
```
- 期望退出码：0
- 断言：<...>
