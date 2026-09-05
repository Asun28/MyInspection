---
# id = 文件名 = 分支 = worktree 末段；格式 ^T\d+-[A-Z0-9]+(-[A-Z0-9]+)*$
# T?-EXAMPLE 是待替换占位；branch/worktree 由工具按 id 派生，无须重复填写。
id: T?-EXAMPLE
title: 一句话可交付产出物
status: todo
depends_on: []
allow_paths:
  - path/to/...
dod_command: uv run python -m pytest <tests> -q
dod_exit: 0
dod_assert: <命令产出的可机检断言>
review_gate: codex {verdict:pass}

# 只添加本卡实际需要的支持字段；项目硬边界始终继承 CLAUDE.md。
# plan_ref: docs/PLAN.md#节名
# parallelizable_with: []     # 声明并行的卡 allow_paths 不得重叠
# forbid:                    # 仅补本卡特有硬边界，免重复项目合同
#   - <禁止事项>
# non_goals:                 # 仅列与本卡有关的刻意排除能力，无则省略
#   - <本次不做的能力>
# diagnosis: <bugfix 的根因与同类排查结论；非 bugfix 省略>
# acceptance:                # 可选；一条起，双引号块式字符串，严格 A1..An
#   - "A1 fixture returns exactly 3 rows"
#   - "A2 output contains ASCII sentinel [RESULT-READY]"
# requirements:              # 可选；唯一非空 R-id 需求，不限定自然语言句式
#   - "R1 返回符合筛选条件的记录"
# 有需求时，验收可写 "A1 [R1] fixture returns exactly 3 rows"；多引用写 [R1] [R2]。
# hygiene: <本卡适用的测试卫生工作；不为填模板追加变异任务>
# doc_sync: <本卡实际需要同步的文档>
---

# T?-EXAMPLE

（按需补充实现者独立开工所需的上下文、设计决定或证据；不重复 front-matter。）

DoD 工具须由 CI/verify 保证存在；新增依赖须声明并纳入 `allow_paths`。
`dod_command` 直接写 PowerShell；不要嵌套带可内插 `$var` 的 `pwsh -Command`（TD69/L95）。
