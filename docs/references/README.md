# docs/references/ — 喂给 agent 的依赖/外部文档层（`*-llms.txt`）

> **动机**（OpenAI《Harness Engineering》第一原理）：
> > *"If something isn't in context at runtime, it doesn't exist for the agent."*
>
> 模型不知道你用的库的**当前**用法——训练数据会过时、会幻觉 API。把关键依赖的精炼文档**就地 vendoring 到本目录**，
> 让 agent 在上下文里就能查，而不是凭记忆瞎写。这是「上下文即基础设施」的落地点。

## 放什么
- 主力依赖的 **`llms.txt` / `llms-full.txt`**（许多库官方已提供，专为喂 LLM 设计的精炼文档）。
- 没有官方 llms.txt 的库：放一份你**亲手提炼**的 `<lib>-llms.txt`（核心 API + 本项目用到的惯用法 + 已知坑）。
- 命名约定：`<工具/库名>-llms.txt`，例如 `uv-llms.txt`、`fastapi-llms.txt`、`<你的设计系统>-reference-llms.txt`。

## 索引（本目录现有 reference）
> agent 不会自动读整个目录——按需**指名 Read** 下表对应文件。新增 reference 时**在此登记一行**。

| 文件 | 覆盖 | 何时读 |
|---|---|---|
| `uv-llms.txt` | uv（Python 包/项目管理器）本项目惯用法 + Windows 坑 | 建 venv / 加依赖 / 跑测试 / 调 CI 依赖时 |
| `claude-opus-5-prompting-llms.txt` | Opus 5 提示词专属细则（**相对 4.8 的五处反转** / 拒答与回退 / effort 起点 / thinking 默认开 / 对话与写盘两种冗长度 / 子代理上限 / 范围收窄 / 评审覆盖） | 给 Opus（想/架构/评审角色）调提示、或写 Opus 面向的 skill/hook/rubric 时；**拿为 4.8 写的旧 prompt 迁移时必读「五处反转」段**（那几条在 4.8 上对、在 Opus 5 上反着伤你） |
| `claude-opus-4-8-prompting-llms.txt` | Opus 4.8 提示词专属细则（effort/thinking 默认关/字面遵循/子代理偏少/**前端家风 AEFRM 正文**/computer use/评审 recall） | **兜底席位，非日常档——没有退休。** Fable 5 与 Opus 5 都带安全分类器，官方默认回退按拒答类目（5 个：`cyber`/`bio`/`frontier_llm`/`reasoning_extraction`/`general_harms`）改道到**当时推荐**的兜底模型，官方示例选中的就是这一档——**示例非契约，映射会变，读响应的 `fallback` 块别写死**。**配回退链、排查「怎么突然是另一个模型答的」、或取 AEFRM 设计规格正文时读**（opus-5 与 sonnet-5 两篇的设计节都指向这里，三页同文防重复） |
| `claude-sonnet-5-prompting-llms.txt` | Sonnet 5 提示词专属细则（adaptive thinking 默认开/无 temperature/新 tokenizer/工具触发） | 给 Sonnet（实现/验证角色）调提示、或写 Sonnet 面向的 skill/hook/rubric 时 |
| `claude-fable-5-prompting-llms.txt` | Fable 5（含 Mythos 5）提示词专属细则（长回合/安全分类器回退/记忆系统/send_to_user/自主停止） | 给 Fable 5 派长程/高难/模糊自主任务、或配其回退与记忆时 |
| `claude-prompting-best-practices-llms.txt` | 跨模型通用提示工程（清晰/示例/XML/角色/长上下文/工具/思考/agentic/迁移） | 写任何模型面向的提示、skill、评审 rubric，或不确定该用哪条通用技巧时 |
| `claude-prompting-tools-llms.txt` | Console 侧提示工具（生成器/模板变量/改进器）方法论 | 在 Console/API 侧起草或改进提示模板时（区别于 agentic「工具使用」提示） |
| `claude-guardrails-llms.txt` | 强化护栏：减幻觉 / 输出一致性 / 抗越狱与提示注入（直接+间接）/ 防 prompt 泄漏 | 给下游 LLM 功能加运行时防护、agent 代读不可信内容（网页/邮件/工具结果）、安全评审或红队排期时 |

<!-- 新 reference 追加到上表：| `<lib>-llms.txt` | 覆盖范围 | 何时读 | -->

## 怎么用
- 在 `CLAUDE.md`「权威文档」、本目录索引、或对应卡片里**指名引用**需要的 reference（agent 不会自动读整个目录）。
- 升级依赖大版本后**刷新**对应 `*-llms.txt`（过时的 reference 比没有更糟——会自信地误导）。

## 动态 reference：版本正确的实时 API 文档（Context7 MCP · 当前默认）

> 上面的 `*-llms.txt` 是**静态**层（要手动刷新、会滞后）。**动态**层解决同一问题的另一半：
> 让 agent 在写/评审库调用时，按**实际 pinned 的版本**取权威文档，而不是凭可能过时的记忆写。

**能力（标准 · 工具无关 L26）**：`依赖 API 版本核验` —— 对第三方库编码或评审前，**先读该库在本项目实际 pinned 的版本**
（`pyproject.toml`/`uv.lock` 或 `package.json`/lockfile），再对照**那个版本**的权威文档核验用法；用版本正确的文档，
不用模型记忆（训练数据会滞后、会幻觉已废弃/尚不存在的 API）。**自检**：工具没了这条标准还成立吗？成立——标准是「按 pinned 版本核验 API」，Context7 只是当前取文档的默认实现。

**当前默认工具 = Context7 MCP**（根 `.mcp.json` 已声明 `context7` HTTP 服务器）：
1. `resolve-library-id`：库名 → Context7 库 id（形如 `/vercel/next.js`、`/mongodb/docs`）。
2. 取库文档：喂库 id **带上你 pinned 的版本**（形如 `/vercel/next.js/v14.3.0`）——拿回的就是该版本的 API，不是最新版臆测。
   （注：Context7 工具名会随其自身版本演进——本仓当前会话见 `resolve-library-id` / `get-library-docs`，upstream README 曾用 `query-docs`；按你环境里**实际暴露**的工具名调用，别硬记。这本身就是「按当前实际核验、别凭记忆」的现场例子。）
- **配置**：项目级 `.mcp.json` 用 keyless HTTP remote（`https://mcp.context7.com/mcp`），**不入库任何密钥**。要更高频率限额：去 context7.com 拿免费 key，设环境变量 `CONTEXT7_API_KEY`（或 `claude mcp add --scope user --header "CONTEXT7_API_KEY: <key>" --transport http context7 https://mcp.context7.com/mcp`）——密钥走 env / 用户级配置，**绝不写进 `.mcp.json` 提交**（selftest 闸 ⑨ 拦明文密钥）。
- **可换后端（L26）**：没有 Context7 时退回本目录的 `*-llms.txt`（静态但离线）、或官方 docs 直取；换了记一条 lesson/ADR。
- **信任面**：本 MCP server（`context7`）与 R3 评审后端的来源/传输/出站数据聚合登记在 `docs/TRUST-MANIFEST.md`（外部信任清单）——加/换远程 MCP server 须同步登记那张表，否则 `selftest.ps1` 闸 9g 红。

**何时用（挂在工作流哪一档）**：
- **R2 实现**（主）：写任何第三方库调用前，按 pinned 版本取文档核验用法——这是「对真源核 API」（见 `CLAUDE.md` 模型分工 Sonnet 一节）的落地点。
- **R3 评审**：`QUALITY-RUBRIC.md` 维度 #15「API 版本正确性」——评审者查 diff 有没有用 pinned 版本里**不存在/已废弃**的 API（幻觉/过时用法）。

## 来源与引用基准（provenance · 所有 `claude-*-llms.txt` 适用）

> ⚖️ **工程约束，非法律意见**——与 `docs/LICENSE-POLICY.md` 同口径，商用发布前由法务终审。

本目录的 `claude-*-prompting-llms.txt` / `claude-guardrails-llms.txt` 是**对 Anthropic 公开文档的提炼**，
不是原样镜像。三条硬约束：

1. **正文一律自行提炼**（中文改写 + 本仓语境注解），**不整页拷贝**——见下方「不放什么」第一条。
2. **提示语片段按功能必要性最小引用**：这些片段是**要照字面喂给模型才生效的功能性字符串**（改一个词行为就变），
   故以**短引用**形式保留并**逐条标注出处**；**不保留大段散文/整节叙述**。**超出功能必要长度的整块示例一律改写为
   要点复述 + 原文链接**（已执行：4.8 篇的 AEFRM 设计示例原为约 20 行整段照录，现改为要点 + 链接）。
3. **著作权归 Anthropic**：每份文件头注须含 `源：<官方 URL>` + `© Anthropic` + 校核日期。本目录内容**仅供本仓
   及其下游项目的 agent 在上下文中查阅**，不构成再授权；下游若要对外分发含本目录的产物，**须自行复核**。

**若判定某片段不宜保留** → 换成「要点复述 + 官方链接」，别删掉整条知识（那会让 agent 退回凭记忆写，
正是 vendoring 要治的病）。新增任何 vendored reference 时按本节三条自检一遍。

## 不放什么
- 不放整本上游文档的原样拷贝（体积大、稀释上下文）——提炼成「本项目实际会用到的子集」。
- 不放有版权/许可限制不允许再分发的内容（见 `docs/LICENSE-POLICY.md`）。
- 不放机密/凭据（本目录入库共享）。

> 注：`*.txt` 不在 `init-scaffold.ps1` 的 token 替换清单内——reference 里别用 init 的双花括号占位符语法（`{{...}}`，不会被替换）。
