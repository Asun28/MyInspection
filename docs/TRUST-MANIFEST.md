# 外部信任清单（TRUST-MANIFEST）

> **动机（TD78）**：脚手架运行期会接触几处**外部信任边界**——远程 MCP server、第二评审模型后端、vendored 第三方 skill。
> 它们各自的来源/版本/会外发什么数据此前散落在 `.mcp.json`、`_config.ps1`、各 skill 的 `NOTICE.md`，**没有聚合视图**。
> 本文件把这些边界集中成一张表，让「哪些外部方参与了本仓的运行、各自看得到什么」一眼可审。
>
> **这是文档 + 漂移闸，不是运行时网络守卫**：不拦截出站、不做代理。真正的机密防泄露走 `check-secrets`（见 `docs/SECURITY.md`），
> 密钥永不入库（`.mcp.json` 只放 `${ENV}` 占位）。本清单的机械保证是：`selftest.ps1` 闸 9g 断言 `.mcp.json` 的**每个** MCP server
> 都在下表登记——**加了 server 却忘了登记信任面即红**（fail-closed 反漂移）。

## 信任边界一览

| 边界 | 当前实例 | 来源 / 传输 | 版本 / pin | 会外发什么数据 | 权威指针 |
|---|---|---|---|---|---|
| **远程 MCP server** | `context7`（依赖 API 版本核验，当前默认） | HTTP `https://mcp.context7.com/mcp`，**keyless**（无密钥入库） | 服务端托管、无客户端 pin（能力随其自身版本演进，按环境实际暴露的工具名调用） | 完整出站输入面 = **库标识/版本 + agent 自填的自由文本查询/主题串**（`resolve-library-id` 的库名、`get-library-docs`/`query-docs` 的 topic）。这些查询串是自由文本，**可能夹带 agent 粘贴进去的代码/文件内容**——⚠ **勿把敏感仓库内容/机密塞进 Context7 查询**；工具本身不自动上传仓库文件，但你喂什么它就发什么 | `.mcp.json` · `docs/references/README.md`「动态 reference」节 |
| **R3 第二评审后端** | 默认 = `codex` CLI（可经 `_config.ps1` 的 `ReviewCommand` 换后端，L26 工具无关） | 本地 CLI 进程 + **模型服务出站**：默认 `codex` CLI（OpenAI，当前默认）把完整评审 prompt 经 HTTPS 送 OpenAI 模型服务推理（登录态/凭据在用户级 `~/.codex/`，不入库不进 CI）；CLI 本身以**只读沙箱** `-s read-only` 运行。换 `ReviewCommand` 后端 = 出站目的地随所配命令的提供方而变（provider 可变，权威 = `_config.ps1` `ReviewCommand`；信任面移交接入方） | `_config.ps1`：`ReviewModel`/`ReviewEffort`（钉在项目配置以免疫用户级 `~/.codex/config.toml` 漂移，见闸 17z） | 送第二模型的 prompt 完整含：**rubric（QUALITY-RUBRIC）+ 完整任务卡 + churn `--stat` 概览 + 被审 diff 正文**；且**只读沙箱的评审者可自行 `Get-Content` 打开工作树里任意其它文件**（`review.ps1` prompt 明确让它「过大处自行只读打开工作树补看」）——**read-only 只挡写、不挡把读到的内容披露给模型后端**。默认 codex 只读沙箱；**自定义 `ReviewCommand` 无沙箱**、对 `$env:REVIEW_WT` 有全读写+网络能力（接入方须自加隔离，见 TD20） | `scripts/review.ps1` · `scripts/_config.ps1`（`ReviewCommand`/`ReviewModel`/`ReviewEffort`）· `docs/QUALITY-RUBRIC.md` |
| **vendored 第三方 skill** | `.claude/skills/*/`（如 ponytail / taste-skill） | 各 skill `NOTICE.md` 记来源 URL | 各 `NOTICE.md` 记上游版本 / commit SHA + vendored 日期 | 无运行期出站（本地提示词资产）；信任面 = **供应链 provenance**（vendored 内容是否忠实上游） | 各目录 `NOTICE.md` + `LICENSE`（四要素由 `selftest.ps1` 闸 9c 强制，见 TD17/TD30）——**本清单不复制其正文，只指针**，避免双源漂移 |

## 维护约定
- **加/换远程 MCP server**：改 `.mcp.json` 后**必须**在上表加一行登记信任面，否则 `selftest.ps1` 闸 9g 红（drift guard）。
- **换 R3 评审后端**（设 `ReviewCommand`）：更新上表「R3 第二评审后端」行的沙箱/数据面描述；自定义后端无沙箱的告知见 `docs/SECURITY.md` §3 与 TD20。
- **版本/能力矩阵刻意不钉死**：Context7 工具名、codex 档位枚举都随各自版本演进，本表描述**信任面**（谁看得到什么）而非会过期的能力清单——具体版本以各自权威来源为准（L26：标准是「外部信任边界须成文可审」，具体工具是当前默认实现）。

> 相关：机密不入库与防泄露闸见 `docs/SECURITY.md`；许可（商用）边界见 `docs/LICENSE-POLICY.md`；依赖 API 版本核验（Context7）见 `docs/references/README.md`。
