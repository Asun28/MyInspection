# specs/ — 任务卡（计划的可机检投影）

> **计划仍是唯一真相源**（见 CLAUDE.md / 你落在 `_local/` 的计划）。本目录不是第二份计划，而是把计划任务章节变成
> **可被 `scripts\task.ps1` 直接执行**的薄投影：每张卡的 `dod_command` 就是 CI/本地实际跑的验收命令。
> **默认不引入** spec-kit / OpenSpec / GSD / ECC 作为**与计划竞争的第二真相源**（见 docs\DEVOPS-WORKFLOW.md §0）——
> 计划权威、卡是薄投影这条红线不变。

## 建新项目时：先评估外部 spec 套件是否合适（referral）
> 默认形态（计划真相源 + 薄卡投影）适合绝大多数项目。但**新项目立项时**，若其需求确实需要更重的、可独立交付的
> spec 纪律，请**先评估**下面两个来源，挑出**适配本项目需求**的做法，作为**可选的项目级叠加层**引入——
> 而非默认全盘照搬（照搬即违反上面的红线）。评估结论可记一条 lesson（`scripts\lessons.ps1 add`）。
>
> - **GitHub spec-kit** <https://github.com/github/spec-kit> —— Spec-Driven Development：`constitution`(治理原则) →
>   `specify`(只写 what/why) → `clarify`(消歧) → `plan`(技术 how) → `tasks` → `analyze`(跨产物一致性) → `implement`；
>   Given/When/Then 验收场景、`[NEEDS CLARIFICATION]` 标记。**适用信号**：对外交付的正式验收规格、需求方非工程师、要把"是什么"与"怎么做"强制分离。
> - **Fission-AI OpenSpec** <https://github.com/Fission-AI/OpenSpec> —— `changes/`(变更提案 delta) vs `specs/`(活的能力规格) +
>   归档工作流；面向 brownfield。**适用信号**：需要长期维护一份"当前能力的活规格"、按变更增量演进、完成即归档。
>
> **引入边界**：无论选哪套，本仓不变量优先——计划（人拥有/人批准）仍是唯一真相源，任何 spec 文档都按**投影**处理；
> 不得引入与 `task.ps1`/`verify.ps1`/codex 评审冲突的并行真相源或并行闸门。仅取**契合需求**的局部做法
> （如 spec-kit 的 `clarify` 消歧纪律、Given/When/Then 验收），别整框架搬。

## 校验
`pwsh -File scripts\check-cards.ps1`（单卡 `-TaskId T1-FOO`）静态校验所有真实卡：id=文件名、`status` 枚举、
`branch`/`worktree` 不与 id 漂移、`dod_command`/`allow_paths` 完整、卡文无模板占位符 token 字面量（双大括号大写蛇形，L61/TD111；selftest 闸10g 回归）。`task.ps1 -Phase start` 会前置自动跑它，
selftest 闸 ⑩ 与 CI 也跑——卡写错在动手前即暴露，而非拖到 `ship`。`_TEMPLATE.md` 跳过（占位故意违规）。

**卡片即代码（信任边界）**：`check-cards.ps1` 只校验**形态**（no-op/block-scalar/嵌套包裹等语法坑），不校验**内容意图**——
`dod_command` 经 `pwsh -Command` 按原文执行，本质是代码。不得对未经审查的外部 PR、第三方来源、或下载来的模板卡片
直接跑 `task.ps1 -Phase red` 或 `-Phase ship`（两者均执行 `dod_command`）；先读一遍 `dod_command` 内容再执行任何会跑该命令的相位。详见 `docs/SECURITY.md` §1.2。

## ID 命名规范（机检 · agent 可派生）
任务卡 `id` 是工作流的主键：`id` == 文件名 == `branch` == `worktree` 末段。命名**唯一规范**——
> **`T<阶段号>-<大写短横名>`**，正则 `^T\d+-[A-Z0-9]+(-[A-Z0-9]+)*$`。

- **合规**：`T0-SCAFFOLD` · `T2-API` · `T0-SAMPLE-ASSET` · `T3-REVIEW-GATE` · `T3-E2E`
- **不合规**：`t1-foo`（小写）· `T1_FOO`（下划线）· `T1 FOO`（空白）· `my-task`（缺 `T<阶段号>` 前缀）

此规范是**给 AI 编码 agent 的确定性契约**：agent 不靠看示例猜形状，而是按正则直接派生新卡名、并在 `task start` 前自检。
`check-cards.ps1` 机检它（selftest 闸 ⑩ / CI 同跑），`decompose-cards.mjs` 生成卡时也按此正则产出 id——三处同源，写错在动手前即暴露。

## 卡片格式
见 `tasks\_TEMPLATE.md`。front-matter 字段：

| 字段 | 含义 |
|---|---|
| `id` | 任务号，对应分支名 / worktree 名 |
| `title` | 一句话产出 |
| `depends_on` | 前置任务（拓扑序，决定可并行性） |
| `plan_ref` | **可选**。本卡对应计划节（如 `docs/PLAN.md#节名`）——实现 agent 的最小上下文指针（免读全计划） |
| `parallelizable_with` | **可选**。可并行卡 id 列表；并行卡 `allow_paths` 必须互不重叠（`check-cards.ps1` 全卡模式机检，对称处理：单向声明即比对） |
| `status` | `todo` → `in-progress` → `in-review` → `merged`。已 `merged` 的卡由 `scripts/archive.ps1` 移入 `specs/archive/tasks/`（冷存；`check-cards` 非递归扫 `specs/tasks/*.md`，不再校验冻结卡）+ 精简索引 `specs/archive/cards-index.md`——活目录只留在飞卡，省新任务上下文（TD86/T28，见 `specs/archive/README.md`） |
| `allow_paths` | 本卡允许改动的路径（评审据此判越界） |
| `forbid` | 禁止事项（按项目硬边界：横切的网络/登录态/冻结契约） |
| `non_goals` | 本卡**能力级**「不做」（从计划「本版砍掉/推迟」下沉；R3 评审 #14 判「能力级越界/顺手多做」）；无则 `none`。是 `forbid` 的能力级对偶 |
| `dod_command` | **DoD = 命令**：`task.ps1 -Phase ship` 直接执行，必须退出码 0 |
| `dod_assert` | 人/评审可读的断言（与命令配套） |
| `review_gate` | `codex {verdict:pass}`（见 verdict.schema.json） |
| `hygiene` | R4 测试卫生承诺（mutation-survivor 剪枝） |
| `doc_sync` | R5 合并后要同步的文档清单 |

## 依赖图（示例 · 按你项目替换）

> 用 `.claude/workflows/decompose-cards.mjs` 从计划投影出卡集，审完写到 `tasks/*.md`，并在此画拓扑图。
> 下面是一个**示例**形状（脚手架 → 冻结契约 → 并行 mock providers → 编排 → 验收/治理）：

```
T0-SCAFFOLD ‖ T0-SAMPLE-ASSET            （depends_on:[]，可并行）
   └─ T0-CONTRACT   ★冻结点：契约/schema 冻结（仅一等资产文件级冻结）
        ├─ T1-* （契约冻结后各自 worktree 并行；实现文件互不重叠、共享测试目录仅各加自己的）
        └─ T2-PIPELINE（依赖 core/storage + 全部 T1）
             ├─ T2-API ─ T2-FRONTEND
             ├─ T2-CLI
             └─ T3-GUARDRAILS ─→ T3-E2E（依赖 CLI + 样例 + 护栏）
                 T3-REVIEW-GATE
                 T3-COMPLIANCE
```

**冻结点意识**：契约/schema 那张卡（上图 T0-CONTRACT）必须在所有依赖它的卡之前；冻结后改 = 版本评审 + 全下游返工。

## 技术债的一行怎么写（登记层契约）
> 真相源 = `specs/tech-debt-tracker.md` 自己的表头（append-only 语义、状态枚举、热冷分离）；本节只定**一行的内容形态**，
> 因为该形态此前只是口口相传，每行的可用度全看谁写的（TD128 即为此登记并偿还）。

**表是 7 列，别加列**：`id | 发现日 | 位置 | 偏离了什么 | 严重度 | 状态 | 偿还指针`。三个消费者按这个列数吃它——
`triage.ps1` 解析出 `open` 队列、`selftest.ps1` 闸 12 的夹具**写死 7 列表头**、`archive.ps1` 整行搬进冷存。
加一列 = 改脚本 + 改闸，而登记所需的信息**放「偏离了什么」那格就够**，故：**扩内容、不扩列**。

**「偏离了什么」那格按四段写**（顺序不强制，四样都要在场）：

| 段 | 写什么 | 反例 |
|---|---|---|
| **后果** | 不修会发生什么、已经发生过几次。与「严重度」分工：严重度是排序用的粗档，后果是让人**判断得了**的具体损失 | 只写「不合规范」——读的人无从判要不要现在修 |
| **修法** | 具体到文件与行的下一步。查过真源就写结论，没查过就写「须先 shape」并列出候选轴与各自代价 | 「应当重构」——等于没写 |
| **可测** | 怎么证明还清了：夹具形态 + 那一枚单句删除变异（L165/L167） | 「加个测试」——不构成验收 |
| **前置** | 依赖哪条债、哪张卡先合、哪个观测窗口先跑完（同文件在飞会撞 `allow_paths`） | 漏掉前置 ⇒ 开卡即撞冲突或污染别人的测量 |

**WHO 不进表**：谁来做、用哪个模型档、根因诊断，都归**解决层**——任务卡的 `diagnosis` 字段（R3 rubric #17 判「根因非表象」）
与 `CLAUDE.md`「模型分工」。表是**登记层**，只负责让债**找得到、判得了、排得上期**；把路由写进表里只会与卡漂移，且无闸可管。

**照着写的样板**：TD109（预算冲突 + 候选轴 + 已落地那半）· TD118（单体解剖 + 廉价自测）· TD122（三轴代价重估 + 可测夹具）·
TD124（把「以为已被别的卡偿还」核实成否 + 修法到行）。与 TD98「闸的失败信息即修法」同一条纪律，只是换到登记层。

## 一张卡的生命周期（R1–R5，详见 docs\DEVOPS-WORKFLOW.md）
```
task.ps1 -TaskId X -Phase start     # R1 worktree + 环境
  ...在 worktree 内：红→绿→重构→R4 剪枝...
task.ps1 -TaskId X -Phase ship      # R2 DoD绿 → 许可闸 → R3 Codex pass → PR → 合并
task.ps1 -TaskId X -Phase cleanup   # R1 拆 worktree → R5 文档同步
```
