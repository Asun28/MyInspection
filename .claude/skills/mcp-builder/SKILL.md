---
name: mcp-builder
description: >-
  Use when building an MCP (Model Context Protocol) server, exposing this project's capabilities as
  LLM-callable tools, or wrapping an API/service as MCP — in Python (FastMCP) or Node/TypeScript (MCP
  SDK). Triggers on: "build an MCP server", "expose this as MCP / as tools", "MCP tool", "FastMCP",
  "把它做成 MCP", "暴露成工具给 LLM". Do NOT use for plain HTTP API design unrelated to MCP, or for
  consuming existing MCP servers.
---

# mcp-builder — 建 MCP server 路由（配下游 providers/pipeline/工具层）

> **路由卡 + 项目约定,非 vendored 专有正文。** Claude Code 内置 `mcp-builder`(Anthropic 第一方、专有);本仓许可洁净
> 铁律下**不拷其正文**,这里只放原创路由 + 本仓约定。用于下游把能力暴露成 MCP 工具(你 CLAUDE.template.md 的
> 「代码与接口命名」已为 MCP/HTTP API 留了命名规约)。

## 先用哪个
1. **Claude Code 内置 `mcp-builder`**(若本环境有,常以 `document-skills:mcp-builder` 提供):就地用它取 FastMCP / MCP SDK
   的脚手架与最佳实践。**别把它的正文拷进本仓。**
2. **本卡通用纪律 + 本仓约定**(下节)。

## 通用纪律（设计好用的工具)
- **工具即接口契约**:每个工具一个清晰职责、**描述写清何时用/输入输出语义**(描述是 LLM 的触发契约,等同本仓 skill 的 description)。
- **schema 严谨**:参数用明确类型 + 约束;必填/可选分清;返回结构稳定、可被调用方解析。
- **错误可读、可恢复**:失败返回结构化错误 + 下一步提示,别抛裸异常或空结果。
- **最小工具面(YAGNI)**:只暴露真需要的工具,别把内部实现细节做成一堆碎工具(配 `ponytail` 审「这工具需要存在吗」)。
- **确定性 + 无副作用默认**:读类工具无副作用;写类工具显式、可审计。

## 本仓约定（重要）
- **许可洁净**:MCP 依赖(Python/npm)仍走许可硬规则(MIT/BSD/Apache;GPL/AGPL/SSPL/非商用禁),提交前过 `scripts/check-licenses.ps1`。
- **命名复用**:工具/模块命名遵循 `CLAUDE.template.md`「命名约定 / 代码与接口命名」节(Python 由 ruff `N` 机检),别另造体系。
- **契约是一等资产**:MCP 工具的 schema 属冻结契约——按计划在契约卡冻结、登记进 `_config.ps1` 的 `FrozenPaths`;冻结后改 = 版本评审 + 下游返工。
- **进交付链**:MCP server 当普通任务卡走 R1–R5(worktree→TDD→DoD→Codex 评审→CI);`dod_command` 用可机检命令(如 MCP inspector / 协议级测试)。
- **机密不入库**:token/密钥只从环境读,不硬编码、不进仓。

## 红线
- 不 vendor 内置/专有技能正文,要用就就地引用。
- 不绕过许可闸、命名规约、契约冻结这些既有闸门。
