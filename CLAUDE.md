# CLAUDE.md


This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> EN: The project's resident agent contract — the always-loaded index of file-placement and naming conventions, hard boundaries, key invariants, and the R1–R5 delivery workflow that every Claude Code session must follow; details live in `docs/` and `specs/`.

**MyInspection** · MyInspection。**本文件是索引**：架构/契约/任务卡/验收细节见 `docs/` 与 `specs/`。

> **首次到岗（入口指针）**：做新活时说出启动触发语「根据脚手架，…」，会命中 `UserPromptSubmit` 钩子
> `route-new-work`（`.claude/hooks/route-new-work.ps1`）——它先和你确认规模档位（T0/T1/T2，见
> `scripts/_config.ps1` 的 `ProjectTier`），再按深度路由到对应交付链，不默认一律上全套流程。

## 文件放置约定（强制）
- **禁止在仓库根随意新建文件**；按用途归入对应目录（每个目录有 `README.md` 提示放什么）：
  `backend/`（后端 / API / 代理服务，Python 惯用 `backend/app/`）· `frontend/`（前端）· `prompts/`（应用调用 LLM 的提示词模板）·
  `context/`（给 AI 代理的持久领域上下文，入库共享）· `data/`（本地数据 / RAG 语料 / 向量索引，内容 gitignored）·
  `configs/`（应用 / 代理配置）· `tests/`（项目级集成 / 端到端测试）· `scripts/`（PowerShell 脚本）·
  `docs/`（**所有项目文档**，架构决策入 `docs/adr/`）· `specs/`（任务卡 / 契约）· `runtime/`（运行时产物，gitignored）。
  用不到的骨架目录可删（删后从 `scripts/selftest.ps1` 的 `$RootAllow` 移除对应项）。
- **任何新文档一律写进 `docs/`**（或 `specs/`），**不得**新建在根目录。新建后在「权威文档」索引登记。
- 根目录**只允许**这些顶层文件：`README.md` `AGENTS.md` `CLAUDE.md` `LICENSE` `pyproject.toml`
  `.gitignore` `.gitattributes` `uv.lock`（及上列目录与 `.github/` `.claude/`）。其余文件都属违规。
  （`AGENTS.md` 是给 Codex/OpenAI 系工具的薄指针，只指向 `CLAUDE.md`；agent 指令的真相源仍是 `CLAUDE.md`，勿双写。）
- **三类「提示词 / 上下文」别混**：`prompts/`=应用运行时喂给 LLM 的模板（入库）；`context/`=给代理的领域知识（入库）；
  `CLAUDE.md`=给开发助手 Claude 的指令；`_local/models/`=私有模型系统提示 / 输出转储（不入库）。
- **内部/工作/设计文档一律放 `_local/`**（已 gitignore，永不入任何仓库）；临时验证文件放 `.secrets/`（gitignored，用完即删）。
  例外：`task_plan.md`/`findings.md`/`progress.md` 因 planning-with-files 钩子按 cwd 读取**必须**留在根目录，已 gitignore。
- **项目内复用资产沉淀归位（验证过就沉淀）**：验证过的资产按类型落到固定位置——**页面结构/组件规范/业务判断/路由约定** → `context/`（项目约定，入库共享）；**踩过的坑** → `lessons`（`scripts/lessons.ps1 add`）；**架构/选型决策** → `docs/adr/`。**红线**：别把业务具体实现（表单/列表/结果页模板等）塞进可复用元层（CLAUDE.md / 脚手架）——元层只装**工具无关的约定/标准/清单**，项目资产沉淀到 `context`/`lessons`/`adr`。

## 命名约定（强制 · 复用既有，禁止新造）
> **给 AI 编码 agent 的确定性契约**：新建任何带名字的工件前，先在下表找它属于哪一类，**套用既有正则/形态**——
> 不要即兴发明新的命名体系，也不要为放它而新建目录。落位看上面「文件放置约定」，写入范围看任务卡 `allow_paths`。

| 工件 | 命名形态 | 机检/来源 |
|---|---|---|
| 任务卡 id（= 文件名 = branch = worktree 末段） | `T<阶段号>-<大写短横名>`，正则 `^T\d+-[A-Z0-9]+(-[A-Z0-9]+)*$`（如 `T0-SCAFFOLD`/`T2-API`） | `scripts/check-cards.ps1` |
| 经验 id | `L<序号>`，全局唯一、永不复用 | `scripts/lessons.ps1` |
| 主题经验文件 | `docs/lessons/<topic>.md`，`<topic>` 用小写短横（kebab） | `docs/LESSONS.md` |
| 参考文档 | `docs/references/<name>-llms.txt` | `docs/references/README.md` |
| 架构决策 | `docs/adr/` 下按其 `README.md` 约定 | `docs/adr/README.md` |
| 模板占位符 | 双大括号包裹的 `UPPER_SNAKE`（见 `init-scaffold.ps1` 的 token 清单） | `init-scaffold.ps1` |

- **复用 > 新造**：已有命名体系覆盖的场景，必须沿用；确需新增一类工件命名时，**先在本表登记一行 + 指明机检来源**，再使用。
- **禁止新建顶层目录**：根目录条目白名单由 `scripts/selftest.ps1` 的 `$RootAllow` 机检；要加目录先改白名单并在上表/放置约定登记。
- 分支名与 worktree 名**不另起**——一律由任务卡 id 派生（`task.ps1` 据 id 定位，卡内 branch/worktree 字段仅作展示，漂移会被 `check-cards.ps1` 拦）。

### 代码与接口命名（下游业务码 · 每类只认一种，禁止混用）
> 给 AI 编码 agent 的确定性契约：每个类别**只有一个**规范选择、沿用生态惯例，**不在同一项目里换着用**（命名随机漂移正是要根除的）。
> 机检：Python 由 ruff `N`（pep8-naming，见 `pyproject.toml`）确定性拦截；前端由 eslint 拦；HTTP/MCP 无通用 linter，靠本表 + R3 评审守。

**Python（`backend/`）**：模块/文件/包/目录 `snake_case`（✓`user_service.py` ✗`UserService.py`）· 函数/方法/变量 `snake_case`（✓`get_user_by_id` ✗`getUserById`）· 类/Pydantic 模型/异常 `PascalCase`（✓`OrderRequest` ✗`order_request`）· 常量 `UPPER_SNAKE`（✓`MAX_RETRIES`）· 私有成员前导单下划线 `_name`。

**HTTP API（REST）**：路径段小写短横 + 集合用复数名词（✓`/api/v1/user-profiles` ✗`/api/v1/getUserProfiles`）· 版本前缀 `/v<N>/` · 路径/查询参数 `snake_case`（✓`/orders/{order_id}?page_size=20`）· JSON 字段 `snake_case`（与 Python 全栈一致，✗ 混用 `createdAt`/`created_at`）。

**MCP（server / tools）**：server 名 `kebab-case`（✓`doc-search`）· tool 名 `snake_case` 动宾（✓`search_documents`/`create_issue` ✗`DocSearch`）· tool 入参（JSON schema 字段）`snake_case`（✓`{"query":…,"max_results":…}`）· resource URI `scheme://path` 全小写（✓`doc://reports/q3`）。

**前端（`frontend/`，TS/JS）**：组件/类型/类 `PascalCase`（✓`UserCard`）· 变量/函数/hook `camelCase`（hook 以 `use` 开头，✓`useAuth`）· 组件文件 `PascalCase.tsx`，其余 `kebab-case.ts`（✓`UserCard.tsx`/`api-client.ts`）· 常量 `UPPER_SNAKE` · CSS 类 `kebab-case`（✓`.user-card`）。

**环境变量**：一律 `UPPER_SNAKE`（✓`DATABASE_URL`）。

**默认即契约**：上表是本脚手架默认选择；项目若需不同口径（如 JSON 用 camelCase），**只在本表改这一处** + 配置对应 linter，全项目统一——**禁止逐文件即兴切换**。新增一类接口命名时先在此登记一行。

> **分语言细则可拆进 `.claude/rules/` 懒加载（给本文件瘦身）**：改某一类文件时才需要的细则（编辑期注意项、目录指针），可拆成
> `.claude/rules/<name>.md` + `paths:` frontmatter（如 `paths: backend/**/*.py`），只在 Claude **Read 到**匹配文件时才注入——
> 主 `CLAUDE.md` 回归「索引 + 每轮必须的铁律」，不当文档库。**但坑**：`paths:` 只在 **Read** 触发、**Write/创建新文件时不触发**——
> 所以**创建期必守的契约（上面这张命名表、文件落位）留在主文件 + 靠 linter 兜底**（Python=ruff `N`、前端=eslint），别只放进懒加载 rule。
> rule 只放**指针**（不复制命名表正文，免双源漂移）；只用项目级 `.claude/rules/`，别用用户级 `~/.claude/rules/`（其 paths 有 bug）。详见 `.claude/rules/README.md`。

## 当前阶段
<!-- TODO：一句话写当前进度 / 已合并的卡 / 下一步。随 R5 文档同步更新。 -->
（脚手架就绪。用 `.claude/workflows/` 的 plan-forge 把计划拆成 `specs/tasks/*.md`，再用 `scripts/task.ps1` 逐卡施工。）

## 权威文档（按序读）
1. `docs/DEVOPS-WORKFLOW.md` — worktree+TDD+Codex评审+文档同步 闭环（操作手册）
2. `docs/LICENSE-POLICY.md` — 依赖许可硬规则
3. `docs/SECURITY.md` — 机密/边界/评审闸门的安全约定
4. `docs/LESSONS.md` — 自净化经验系统（三层 + 闭环；总账 `docs/lessons/LEDGER.md`，操作器 `scripts/lessons.ps1`，`lessons` skill）
5. `specs/` — 任务卡（计划的可执行投影；`specs/README.md` 有格式与依赖图，**及「建新项目时评估外部 spec 套件」referral**：本项目需求若需更重的独立 spec 纪律，先评估 spec-kit / OpenSpec、取契合做法作可选叠加层，保持计划=唯一真相源）
6. `docs/IDEA-TO-PLAN.md` — 想法→计划**前置漏斗**总览（3 步：1-brief 理需求 → 2-options 搜选型 → 3-plan 写计划；非技术也读得懂）+ **按规模档位表**（T0 极简/T1 标准/T2 完整，建议跳过哪些链；`_config.ps1` 的 `ProjectTier` 是软提示），配 `docs/SCOUT-OPTIONS.md`（第二步搜现成方案+评可行性+选 base，方法 harvest 自 Superpowers+gstack）；规划 harness（第三步引擎）细节见 `docs/PLAN-FORGE.md`（想法→审计→拆卡 多 Agent 流水线）
7. `docs/HANDOFF.md` — 会话交接标准（planning-with-files 三件套 + 零歧义 HANDOFF 块；和 AI agent 跨 session 接力的硬契约）
8. `docs/QUALITY-RUBRIC.md` — R3 Codex 评审的判定 rubric（反「自我夸奖/自我开脱」：不确定即 block、每条 reason 给证据）
9. `docs/HARNESS-REVIEW.md` — 随模型变强**给脚手架做减法**的仪式（逐闸门 stress-test 其假设）
10. `docs/references/` — 喂给 agent 的依赖文档层：**静态** `*-llms.txt` +（当前默认）**动态** Context7 MCP（按**实际 pinned 版本**取实时 API 文档，治「模型幻觉过时/错版本 API」；根 `.mcp.json` 已声明）；「不在上下文里=对 agent 不存在」。索引/用法见 `docs/references/README.md`；自带 `uv-llms.txt`
11. `specs/tech-debt-tracker.md` — 技术债追踪（持续小额还债，非周期大修）
12. `docs/PROJECT-BRIEF-TEMPLATE.md` — 产品简报模板（规划上游：what/why；由 `shape-idea` skill 即漏斗第一步产出，照 `docs/PLAN-TEMPLATE.md` 手工扩写成 PLAN）
13. `docs/LOOP-ENGINEERING.md` — **三层回路**（①AI 编码/分钟 ②人定方向/小时 ③真实用户/天–周；及③的两条回流正门）+ 心跳（cadence 发现+分诊，`scripts/triage.ps1`）+ 理解债闸 + judgment 经验（loop-engineering / RSI 落地）
14. `docs/EVAL.md` — 能力完成度自评方法论（opt-in stub：确定性 exit-0/1 eval 标准；四维 frontend-behavior/backend-mcp/security/functional；LLM-judge 留上游不当闸；不带 runner/套件，下游自建自接 CI）
15. `docs/DELIVERY-OPS.md` — **合并之后**交付/运维方法论（opt-in 姊妹篇：集成/e2e 测试层 · 结构化日志/可观测 · 灰度+feature-flag · CD 部署/回滚/staging；全为方法论+标准+占位、工具无关；**脚手架永不自动发布**，CD 下游接线）
16. `docs/RELEASE-CHECKLIST.md` — **发布前收口清单**（工具无关、可勾选）：整合已有闸（防泄露 `check-secrets -Strict` / `verify`）+ 授权/认证安全自查（越权 IDOR/会话固定/token 存储/CSRF/密码哈希）+ 可观测 + 灰度/回滚。小项目按需取子集
17. `docs/FRONTEND-FLOW.md` — **前端生成闭环**（T2 档 · 复杂多页前端）：四段串现有件（生成前/中/后/资产回流）+ **流程卡(页面地图)** 与 **意图卡(单页目标)** 两个模板；流程卡→喂 `plan-forge`、意图卡→`grill-design` 拷问敲定；驱动卡 `.claude/skills/frontend-flow`。**不重造引擎**，简单单页前端直接 `frontend-design`+pencil

## 开发工作流（每张任务卡，详见 docs/DEVOPS-WORKFLOW.md）
单卡闭环：`scripts\task.ps1 -TaskId <ID> -Phase start|ship|cleanup`
- **R1 worktree**：每卡建 `<WorktreeRoot>\<ID>` 隔离分支（.venv/node_modules 每树独立、gitignored）
- **R2 TDD**：先写失败测试→实现到绿→重构；契约测试 mock 必 100% 过
- **R3 PR+Codex 评审代替人工**：`review.ps1` 按 `docs/QUALITY-RUBRIC.md` 判（注入 rubric + 反自我开脱立场），出 `{verdict:pass|block}`→回贴 `codex-review` 状态；
  有 Pro 规则集则 `verify`(CI)+`codex-review` 双绿自动合并；free+private 由 review.ps1 退出码本地强制；**阻断态可诊断**——「跑完了但读不出可用裁决」分四态各带 ASCII 状态码 + 恢复路由（见 rubric §5），拒答原文另存 `.review/(分支名).raw.txt`
  - **评审者的模型/档位钉在 `scripts/_config.ps1`**（`ReviewModel`/`ReviewEffort`，留空=后端默认）：别让**用户级**
    `~/.codex/config.toml`（GUI 可改）决定本项目合并闸的生死——它一旦被改成当前 CLI 不支持的模型，R3 对所有 PR 都会 fail-closed block
- **CI 触发形态**：`ci.yml` 与 `scaffold-selftest.yml` 均 **push + pull_request**（`[main, master]`；selftest 闸 **8.2d** 锁死此形态）。push 侧各带路径过滤——`ci.yml` 用 `paths-ignore: ['**.md', 'docs/**']`（**纯文档直推不触发**，混合推送仍全跑），`scaffold-selftest.yml` 进一步**正向 `paths` 只扫权威面**（scripts/.claude/.github/configs，非 .md）——业务码推送不再空跑 selftest matrix（windows 计 2× 分钟）；**PR 侧刻意不过滤**（必需状态检查 + path filter 不相容，doc-only PR 会永远停在 Expected）。
  **push 侧是事后检测、不是 push 前强制**——提交落地后才跑；free+private 无可强制规则集时，它保证直推提交**败即显式变红**（防泄露闸尤需事后可见：发现了才能轮换密钥）。
  push 前的真强制只有两层：`gh-bootstrap.ps1` 装的本地 pre-push 钩子（仅覆盖装了钩子的克隆）、服务端规则集（需 Pro/public）
- **R4 测试卫生**：mutation-survivor 法剪枝冗余测试（每卡 `hygiene` 字段）
- **R5 文档同步**：合并后立刻更新 CLAUDE.md/README/卡片 status（每卡 `doc_sync` 字段）
- 一次性建仓：`scripts\gh-bootstrap.ps1`（建仓 + main 规则集加固；**仅 `_config.ps1` 配置的个人账号**；推送前转调防泄露闸）
- 变 public 前防泄露：`pwsh scripts\check-secrets.ps1 -Strict` 须全绿——核心数据库/密钥/凭据须既被 gitignore、又**未被 git 追踪**（已追踪 → `git rm --cached`，gitignore 救不了已追踪文件）。模式集单一真相源，`gh-bootstrap` 复用之；见 `docs/SECURITY.md`

### 交付层（脚本 + Skill + Hook，详见 docs/DEVOPS-WORKFLOW.md §7）
- **脚本**=确定性 substrate（CI/人/Claude 同闸门）；**`.claude/skills/task-loop`**=自动触发并驱动闭环（包装脚本）；
  **`.claude/hooks/guard-frozen.ps1`**（PreToolUse）=拒绝编辑冻结物（见 `scripts/_config.ps1` FrozenPaths）；
  **codex** 插件=唯一 R3 评审者。说「ship <ID> / 做下一张卡」即触发 task-loop skill。
- **`.claude/skills/shape-idea`**=想法→计划漏斗**第一步（1-brief）**：AI 自驱**发散→收敛**——发散(做加法：web 搜+社交/论坛 挖痛点/竞品/跨域/What-if → 机会地图)，收敛(做减法：KANO/MoSCoW 排序 → 砍伪需求 → MVP → 用户故事+验收标准) → 写 `_local/1-brief.md`。**适配 AI 不适配人**：AI 干活、人只在两个闸口定方向。说「我有个想法 / 帮我理需求 / brainstorm」即触发；批准后进第二步 `scout-options`。总览见 `docs/IDEA-TO-PLAN.md`。
- **`.claude/skills/grill-design`**=漏斗**第三步写 PLAN 前**的设计决策交互式拷问:沿设计决策树**一次一问、每问给推荐、消解依赖**,把技术设计(数据模型/契约/状态机/模块边界/错误路径)敲定再落 `PLAN-TEMPLATE` → `plan-forge`。介于 shape-idea(需求层)与 plan-forge(自动审计)之间,减少 `fix-first` 往返。说「grill / 拷问设计 / 写计划前敲定设计」即触发。
- **`.claude/skills/lessons`**=自净化经验系统（遇复发问题先查、解决后入账、自动提纯/封顶）；说「复盘/这个坑/经验」即触发。
  配套 **Stop hook `.claude/hooks/lessons-reminder.ps1`**（会话结束打印经验捕获模板）。
- **`.claude/skills/planning-with-files`**=文件化规划 + **不能模糊交接**（和 AI agent 跨 session 接力）。跨会话/交接/多 agent 接力**必用** cwd 三件套
  `task_plan.md`/`findings.md`/`progress.md` 跟踪（同 session 内按判断，>5 步仅启发非硬闸）；离场前写 `progress.md` 末尾 HANDOFF 块并 `pwsh scripts\handoff.ps1 check`（必 PASS）。
  **SessionStart hook `handoff-resume.ps1`** 到岗即打印续接指针、**Stop hook `handoff-reminder.ps1`** 离场前提醒校验。标准见 `docs/HANDOFF.md`。
- **`.claude/skills/triage`**=脚手架心跳（loop-engineering）：`scripts/triage.ps1 scan` 只读 cadence 扫描各子系统 → `_local/triage-inbox.md`，
  **只发现不行动**，act 走既有交付链；说「triage / 扫一遍待办 / 心跳」或 `/loop` 即触发。理解债自检
  （别盲信 loop 产出）标准/动机见 `docs/LOOP-ENGINEERING.md`。
- **`.claude/skills/frontend-flow`**=前端生成闭环**串联驱动卡**（T2 档 · 复杂多页前端才用）：把现有件串成「生成前→生成中→生成后→资产回流」四段——PRD 复用 `shape-idea`/`PROJECT-BRIEF`(+前端补充节)、生成规则复用 `frontend/README` 5 闸+design tokens 真相源；生成中产出**流程卡(页面地图)→喂 `plan-forge`** 投影任务卡、**意图卡(单页)→`grill-design`** 拷问敲定；生成后路由 **pencil MCP**/Claude Design/v0(L26 不绑死单一)+`frontend-design`/`taste-skill` 局部改；验证过的区块回流 `context/frontend-assets/`。**不重造拷问/审计/编辑器引擎**（边界见 `docs/FRONTEND-FLOW.md`）；T0/T1 简单前端别上、直接 `frontend-design`+pencil。说「前端生成 / 做前端页面 / 页面地图 / 意图卡 / 前端闭环」即触发。
- **`.claude/skills/{frontend-design,taste-skill}`**=前端/UI 视觉设计层（配 `frontend/` 骨架）：`taste-skill`（vendored MIT）是反 slop 前端技能（brief inference / 三档配置 / "AI tells" 黑名单 / redesign 协议 / 起飞前检查）；`frontend-design` 是**原创路由卡**（非 vendored），把 UI 活路由到 taste-skill、Claude Code 内置 frontend-design、以及**可按需安装的 MIT 插件 `ui-ux-pro-max`**（161 调色板/67 风格/按栈组件的可搜索 DB，约 10MB 故不 vendor、用 `/plugin install ui-ux-pro-max` 按需装）（**均就地引用、不拷专有正文**，保模板纯宽松），并带通用设计纪律。说「做个页面 / UI 太模板 / 重设计 / 让它好看点」即触发。
- **`.claude/skills/{webapp-testing,mcp-builder,skill-creator}`**=Claude Code 内置 skill 的**原创 pointer 卡**（就地引用内置、**不拷专有正文**，保模板纯宽松，并编码本仓特定约定）：`webapp-testing`=UI 真跑验收（Playwright 驱浏览器 + 截图，**上游探索首选 Playwright MCP `@playwright/mcp` 按需接入**，补 TDD/verify 之外的真浏览器路径；模型在回路=非确定，只探索不当闸；产物落 `_local/`）；`mcp-builder`=建 MCP server/工具（守许可闸 + 命名规约 + 契约冻结）；`skill-creator`=在本仓加/改 skill（vendor vs pointer 判许可、同步三处索引、跑 selftest）。说「验证前端 / 截图」「做个 MCP」「加个 skill」即触发。
- **`.claude/skills/database-design`**=关系型 schema 设计纪律（原创卡，配 `backend/` 骨架）：建模在前 / DDL 在后；主键策略、审计+软删（唯一索引含 `deleted`）、状态机、逻辑外键默认、业务逻辑不进触发器/存储过程、索引来自查询场景、反过度（别过早分片）。配 plan-forge `data-model` lens（设计期审）+ rubric#13（评审期）+ `docs/lessons/database.md`（陷阱真相源）。说「设计数据库 / 表结构 / schema / 数据模型 / ER 图」即触发。
- **`.claude/skills/pr-recap`**=diff→可视化高空摘要（原创卡，辅助非闸）：把 `git diff`(branch/commit/PR) 投影成 变更鸟瞰 + mermaid 架构delta/文件触达图 + 评审注意点，贴进 GitHub PR(原生渲染 mermaid)或落 `_local/`；纯 markdown + git/gh、零运行时依赖、自包含。补 codex R3(文字裁决)之外给**人**看的高空视图；**模型在回路 = 非确定，只辅助不当闸**(L25)。要 annotated-diff UI / 可分享链接按需接 Builder `visual-recap`(opt-in，L26 不绑死)。说「recap / PR 摘要 / 变更鸟瞰 / 把 diff 变可读」即触发。
- **`.claude/skills/improve-prompt`**=按模型改写提示词（原创卡，辅助非闸）：贴一段 prompt（可选给目标模型/用途）→ 推断或确认目标模型（规划/评审→Opus 5 · 规格清晰实现→Sonnet 5 · 长自主/多卡→Fable 5）→ 指名 Read `docs/references/claude-<model>-prompting-llms.txt` + 跨模型最佳实践 → **两面都过**：既补该补的，也**删该删的**（为旧模型缺陷写的补偿性脚手架在新模型上常反过来伤你；每条删除须能在 reference 指出依据，指不出就不删只提问）→ 输出可复制改进版 + 逐条溯源解释、删除项单独标出；三模型之外走跨模型通用篇。另有 `opus-4-8` = 拒答回退兜底档（配回退链时才路由）。真相源 = reference 文件（本卡只编排、不复制提示技巧正文）。说「优化提示词 / 改进 prompt / improve this prompt」即触发。
- **`.claude/skills/ponytail{,-review}`**=on-demand 的 YAGNI **设计层**透镜（vendored，MIT）：写测试前审「要不要建/需不需要这层抽象或依赖」（`ponytail-review` 含 diff / 全仓两模式）。
  与代码层的 `/simplify` 分两个高度互补（task-loop 步骤 1.5 vs 3.5）；说「ponytail / 精简 / 是不是过度设计」或 `/ponytail` 即触发。不装其常驻钩子。
- **双评审流水线**（docs/DEVOPS-WORKFLOW.md §8，正交不重复）：`/security-review-local`（安全·commit 前）→ commit → codex PR（契约/工程）。

## 命令（Windows / PowerShell）
<!-- TODO：按你项目填实际命令。下面是常见骨架。 -->
- 装环境（若 Python 后端）：`scripts\setup.ps1`（或 `uv venv --python 3.13 .venv; uv sync`）
- **验收总闸门**：`scripts\verify.ps1`（确定性、无网络跑通最小闭环）
- **工作流脚本自检**（改 `scripts/*.ps1` / `.claude/hooks/*.ps1` 后）：`pwsh -File scripts\selftest.ps1`（来自脚手架、随下游保留；push/PR 也由 `.github/workflows/scaffold-selftest.yml` 在 CI 跑）
- **范围检查**（核「改动 ∈ 卡 allow_paths」；与 ship 范围闸共用判定核 `scripts/_scope.ps1`，越界/不可判即非零退出，**不自动 fetch**）：**诊断式**（不承担绑定）`pwsh -NoProfile -File scripts\check-scope.ps1 -TaskId T1-FOO -Base master`（`-Local` 判本地那棵）；**已推送状态的手工恢复必须用完整式**——跑**主检出**那份 checker（相对自身位置加载判定核，从被审工作树跑＝被审分支自己判自己，同 L86 之理）、`-Path` 指被审树，先 `git fetch origin master T1-FOO`（**fetch/gh 非零即中止**——陈旧 `origin/*` 会让 allow_paths 都取自旧卡，空 head 会把绑定静默关掉）、**核 PR 的 `baseRefName` == 本次判定的 base**（判定前 + 合并前各一次；PR 被 retarget 会「按 A 判往 B 合」）、**合并前再复核基线 OID 未前移**（名没变但 base 前移时，合并落到新基线而 allow_paths 取自基线那份卡 ⇒ 判定依据已变，须重跑），再把两侧 OID 都钉进闸 `pwsh -File <主检出>\scripts\check-scope.ps1 -TaskId T1-FOO -Base master -Path <被审树> -ExpectTip $head -ExpectBase $baseOid`，合并配 `gh pr merge --match-head-commit`（权威序列含退出码检查见 `docs/DEVOPS-WORKFLOW.md`）
- 全部测试：`uv run python -m pytest backend/tests -q`
- 依赖许可扫描（加/升级依赖后必跑）：`pwsh -File scripts\check-licenses.ps1`
> ⚠️ 本机 `uv run <console-script>`（`uv run pytest` / `uv run uvicorn`）在 Windows 可能报 `Failed to canonicalize script path`——改用 `uv run python -m <module>`（见 L16）。

## 架构大图
<!-- TODO：一段话 + 一张「路径→职责」表。示例骨架： -->
| 路径 | 职责 |
|---|---|
| `backend/app/providers/` | 契约 + 注册表 + mock/real |
| `backend/app/core/` | config / storage / db |
| `backend/app/...` | … |

## 硬边界（不可违反）
<!-- TODO：按你产品填。下面是商用项目的常见基线，按需保留/删改： -->
- 无平台自动发布、不写任何登录态；`.env` 与认证文件 **永不入库**。
- 全部代码为本项目**原创实现**。
- **敏感面无人值守不动**：认证/计费/数据迁移/生产配置等高危路径登记进 `scripts/_config.ps1` 的 `FrozenPaths`（`guard-frozen` 钩子拒改）；要动走版本评审 + 人在环确认。
- **依赖许可（商用硬规则，见 `docs/LICENSE-POLICY.md`）**：禁用 GPL/AGPL/SSPL 等 copyleft 与任何
  non-commercial/research-only 代码·权重·数据·素材；仅用 MIT/BSD/Apache 等宽松；LGPL 仅限进程外/动态调用。每加依赖跑 `check-licenses.ps1`。
- （可选）测试/verify/CI 默认走确定性/离线路径（如全 mock、禁出站网络、不依赖 GPU）——若设此边界，由 `conftest.py` autouse fixture + `verify.ps1` 显式强制。

## 关键不变量
<!-- TODO：按你项目填。常见模式： -->
- 契约 `backend/app/providers/contract.py`（或你的等价物）**冻结**，演进走版本评审；冻结清单登记在 `scripts/_config.ps1` FrozenPaths。
- 运行时路径**只经** `backend/app/core/storage.py` 派生，禁各处拼路径。
- 切换 mock/real **只改** `registry.py`，不动调用方。
- 配置只经 `backend/app/core/config.py`（或你的等价物）**单点读入并注入**；业务码**禁散读** `os.environ`/`os.getenv`。

## 经验铁律（必须加载 · Tier 1 · 封顶 10 条）
> 自净化经验系统的**必须层**：踩过且会复发的硬坑，每轮都在上下文里，**同样问题不再重导**。
> 全量见 `docs/lessons/LEDGER.md`；按需细则 `docs/lessons/<topic>.md`；流程见 `docs/LESSONS.md`。
> 增删经验用 `scripts/lessons.ps1`（add/search/check/promote）；本节超上限须淘汰最不活跃项回按需层。
- **[L1] 并行工具批次**：只读诊断与写操作**分批**；首个命令非零退出会**连带取消整批**、丢失已写文件。
- **[L3] 清 gh token 用 Remove-Item**：`Remove-Item Env:GH_TOKEN, Env:GITHUB_TOKEN -EA SilentlyContinue`；赋空串仍遮蔽 keyring 致 401。
- **[L165] 断言面必须恰好等于被测契约，并用「只删那一句」的变异证明它在测**：宽于契约的断言（整份 stdout ⊃ 判定行 · 本地化文案 ⊃ ASCII 哨兵 · 关键词出现次数 ⊃ 可执行命令行 · 任一非零 ⊃ 该守卫拦下）在契约还在时照样绿，契约被摘掉后又常因别的原因满足 ⇒ 静默失效。故：只比判定行 · 机检认 ASCII 哨兵（本地化文案只给人读，编码链一变即假红/假绿）· 文档契约锚到可执行命令行形态 · 「不符」用例要让被测那句**真被执行到**。**每道守卫配一枚单句删除变异，它红了才算数**。**变异本身也会撒谎**——`exit≠0` 可能来自靶未命中/语法坏/运行时异常/更早的闸抢先中断，故变异脚本须带**判据分类器**（只有「非零 **且** 命中指定断言文本」才算 OK）。
- **[L17] `.ps1` 一律用 PowerShell 工具，不用 Bash**：Bash 工具吞反斜杠路径（exit 64）且控制台编码与 pwsh 不同源——后者会让 `selftest.ps1` 等中文断言脚本产出**假 FAIL**，连事后核验也会被误导；异常失败先用 PowerShell 工具重跑再下结论。
- **[L97] 横切纪律行为化前先 grep 出全部权威面、一次性纳入 allow_paths**：改的是「所有面都在教的那条规则」时，教它的面（CLAUDE.md · README/操作手册 · skill · 脚本头注 · 架构图 · 各校验清单枚举）**一次扫齐再开卡**，并连同**改动文件自身的注释与卡 front-matter** 一起对齐——评审每轮只报当轮最刺眼的一处，漏一处就多打一轮。**扫描清单显式含本次 diff 改到的每个文件自身**（注释 + 失败/日志文案 + 总结行）；**失败文案不写死具体病因**（那是必然过时的正面陈述），能从现场数据动态报就动态报。这类卡 allow_paths 天然大，是横切的固有形态、非 scoping 失误；check-cards「>5 告警」对它是误报但不放宽阈值，在卡标题声明式扩尺寸即可。
- **[L95] `dod_command` 里不写 `$变量`，且 `-Phase red` 的「RED 已确认」不是证据**：`task.ps1` 用 `& pwsh -Command <卡片原文>` 跑 DoD，而卡片自身又是 `pwsh -Command "…"`——双层包裹下 `$ok` 被子 shell 内插成空串，孙 shell 得到 `if (-not ) {…}` → ParserError → exit 1，`-Phase red` 遂把「语法坏了」当「测试红了」收下（vacuous RED，且该卡 GREEN 永不可达）。用无变量写法 `if (-not ((Select-String …) -and (…))) { exit 1 }`，并**读一眼 DoD 实际输出**确认非零来自断言失败。
- **[L196] 后台长批硬杀不执行 finally，会话续接第一步先核被测文件 SHA**：变异批/长批被会话拆除或进程树 kill 杀在「植入后、还原前」时，还原挂在 finally 上不会跑，被测文件跨会话停在变异态，git 只显示 M、肉眼难辨。故：批启动核基线 SHA 不符即中止；**每次会话续接第一步核被测文件 SHA==上批基线**，不符先从 `.bak` 还原再谈 diff/证据（判干净只认 SHA256）；批须落 per-mutation 日志，续跑只补缺失枚、不整批重来。

## 执行边界（AI 自主运行硬约束 · 每轮必载）
> 长自主运行里边界必须显式常驻（出处：docs/references/claude-fable-5-prompting-llms.txt「划定边界」）。经验铁律管「工具坑」，本节管「行为红线」，不重复。
- **绝对禁止（无例外）**：对共享分支 `git push --force` / `git reset --hard` / 历史改写（filter-repo/BFG；须用户明示除外）；
  用 `--no-verify`/停用钩子绕过任何闸门；把 `.env`/密钥/登录态等机密内容读出、回显、或写进提交/PR/交接文件；
  **改/删/跳过测试或弱化断言让它变绿**（那本身就是失败，永远不是修复）；**虚构机密/端点/API/约定**——真源查不到就停下问，绝不编一个填上。
- **完成与词义（防自评漂移）**：「完成」**只有一个定义 = 机检闸通过**（DoD / verify / selftest），自评「看起来好了」不算数；「清理/重构」= 行为不变且闸门前后皆绿；执行中偏离计划 → 取保守选项、记录缘由后继续；maker 与 checker（如 R3 评审）同一争点**两轮互不认可即停**、排队人裁，别无限迭代讨好评审。
- **先停下确认（难逆或范围变更）**：删远端分支/仓库、改仓库可见性（private→public）、改 GitHub 规则集、
  卡片 `allow_paths` 之外的批量删除、动 `FrozenPaths` 冻结物（走版本评审）；**新增运行时依赖**（先提案用途/许可/更简替代，过许可闸再落 lock）；**单次提交超大 diff**（约 >200 行）而任务未明示该规模。
- **反模式抑制（未经请求不做）**：不做防御性备份（`*.bak`/backup 分支/副本文件）；不重开用户已定的决策；
  任务外重构/清理见通用编码纪律 3/4。可逆且属原任务的动作直接做，别停下要许可。

## 约定
- 路径用 `pathlib` 绝对路径；subprocess 用参数列表 + 显式 UTF-8、禁拼 shell；错误分 retryable/non-retryable。
- **调用第三方库 API 前，按实际 pinned 版本核验用法**（Context7 MCP 取该版本文档 / 或读 `docs/references/*-llms.txt`），别凭记忆写过时/幻觉 API；R3 评审维度 #15 查版本不符。工具无关（L26）：标准=按 pinned 版本核验，Context7 是当前默认取文档实现，可换。详见 `docs/references/README.md`「动态 reference」。
- 提交前过 review 闸门；任务卡 DoD = 命令 + 退出码 + 断言。
- **GitHub 操作仅限 `scripts/_config.ps1` 配置的个人账号**（禁组织账号）：`scripts/_guard.ps1` 在每次 gh 操作前校验。
- 遇到反复出现/曾卡死的问题，先 `pwsh scripts\lessons.ps1 search <关键词>` 查经验；解决后 `add` 回总账。
- **提交不加任何 `Co-Authored-By` / AI 署名**；commit message 只写改动本身（含敏感字样或多行走 `git commit -F`，见 L2）。
- 不在仓库根/各处留生成物或临时文件：`.venv/`/`.pytest_cache/` 等已 gitignore；临时核验文件放 `.secrets/`（gitignored）用完即清。
- 本项目唯一 AI 工具是 Claude Code（+ codex 评审）；`/init` 与 CLAUDE.md 审查时**跳过** Cursor/Copilot 规则检查（见 L12）。

## 模型分工与交接（Opus 想 / Sonnet 做）
> 两个模型共用下面「工作准则 / 约定 / 经验铁律」，只是侧重不同——LLM 反复犯同样的错，靠这些规则拦住。
- **Opus**（`claude-opus-5`）= 想 / 架构 / 评审：规划 · 难逆决策 · 硬调试 · pre-merge 判断。陷阱 = 过度构建 → 重「工作准则」的极简 / 可追溯两关。（**`claude-opus-4-8` 未退休 = 兜底席位**：Fable 5 与 Opus 5 都带安全分类器，被拒后官方默认回退**按类目**改道，官方示例选中的就是它——但映射按类目而定且会变，**读响应里的 `fallback` 块判断接手者、别写死**。别把 4.8 当废档删。）
- **Sonnet**（`claude-sonnet-5`）= 实现 / 验证：规格清晰的活 · 批量编辑 · 测试 · 机械重构 · 对真源核 API。陷阱 = 没读就写 → 重「先想后写」「目标驱动执行」。
- **Fable 5**（`claude-fable-5`）= 长自主/多卡运行：独立子任务派并行子代理、主线继续干；ship 前 fresh-context 证据审计（见 task-loop 4.7）；经验一律落 lessons 链。**只在**多卡弧 / 耗时超单会话 / 需自主拆解的模糊 scoping 才上 Fable，规格清晰的例行活留 Sonnet（贵档，别拿它烧 Sonnet 稳做的活）；何时派/长命子代理复用见 docs/references/claude-fable-5-prompting-llms.txt。
- **大体量只读消化不进主上下文**（标准=上下文隔离，工具无关）：超长日志/构建输出/PDF/截图先在隔离上下文消化、只回传摘要/结论（**如**派子代理或第二便宜模型），别直接灌主上下文。
- **effort 杠杆**：例行模型在环步骤跑低 effort（Fable 低 effort 常超旧模型 xhigh），最吃能力的活才 xhigh；**循环/重试体内封顶 high**——xhigh 只给一次性评审/最难单点，别放进 loop 反复烧。
- **交接**：Sonnet→Opus 遇真歧义 / 重大决策 / 两次没修好的 bug；Opus→Sonnet 计划已清、活变机械；Fable→Opus 撞真难逆架构判断、Fable→Sonnet 把规格清晰子任务派子代理，活长成多卡弧则 Opus/Sonnet→Fable 上交。solo 时先按 Opus 想、再按 Sonnet 做，活超单会话/多卡就升 Fable 自驱。
- **独立评审闸仍是 codex R3**（第二独立模型，非 Opus 自评 · L26；实现见 `review.ps1`）。**子代理模型路由按任务性质选档，别一档到底**：`gpt-5.6-sol` = 商用级实现 · 架构决策 · 安全评审 · 终审（**R3 合并闸即钉此档**，安全面不降档）；`gpt-5.6-terra` = 仓库探索 · 文档通读 · 大体量只读消化（配合上条「只回传结论、不灌主上下文」）。**不设小模型快档**：小模型核验深度不适配闸门/安全面，小而明确的活留 Sonnet 低 effort，或仍走 `gpt-5.6-sol`。派工形态（含 Windows 上 `.ps1` shim 坑）见 `docs/DELIVERY-CHAINS.md`「Codex 子代理派工」。
- **模型专属提示词细则（按需 Read，非每轮常驻）**：给某个具体 Claude 模型调提示 / 写它面向的 skill·hook·rubric 时，**指名 Read** `docs/references/claude-<model>-prompting-llms.txt`（`opus-5` / `sonnet-5` / `fable-5`；`opus-4-8` = 回退兜底档，配回退链或排查换模型作答时读）+ 跨模型 `claude-prompting-best-practices-llms.txt`；Console 侧模板/改进器见 `claude-prompting-tools-llms.txt`。这是「动态按对应模型加载对应提示词」的落地——静态 vendored、按需取，随模型出新版刷新（索引见 `docs/references/README.md`）。会话式改 prompt 入口见 `.claude/skills/improve-prompt`（贴 prompt 即改）。

## 工作准则
*（非琐碎改动一律稳健优先于求快；一行错别字之类按判断处理。）*
- **先想后写**：显式声明假设；不确定就先问，别猜着往下做；有更简单的做法就直说。
- **方法论/标准优先于工具**：定义能力/流程时写**方法论 + 标准**（工具无关），具体 skill/agent/工具作**举例 / 按需 / 当前默认**（如·若装·可选），可随时搜更优替代而不破坏定义、并把新选择记进经验库。自检：工具没了这条标准还成立吗？
- **目标驱动执行**：把命令式任务改写成可验证目标（「修 bug」→先写能复现的失败测试再让它通过）；多步任务先列 `[步骤]→验证:[检查]` 再逐步推进。
- **声明完成前先过两关**：① 每行改动可追溯到需求（顺手的无关编辑删掉）；② 资深工程师会不会嫌过度设计（会就简化）。
- 默认走 R1–R5 闭环与 `task-loop` skill；编码在 worktree 内做，权威闸门是 `task.ps1`+`review.ps1`+`verify.ps1`。
- 并行工具调用时把只读诊断与写操作分批（L1）；触碰冻结契约会被 `guard-frozen` 钩子拒绝（需演进走版本评审）。

---
<sub>脚手架溯源：**MyInspection** 由 devops-scaffold **v0.29.0** 生成。回填上游脚手架改进时对照此版本；当前版本见 `scripts/_config.ps1` 的 `ScaffoldVersion`。</sub>
