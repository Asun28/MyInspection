# 按需经验：PowerShell / gh / Windows 工具链（Tier 2）

> 按需层：在涉及 PowerShell 脚本、gh/git 操作、Windows 工具链时由 `lessons` skill 触发加载。
> 源自 `docs/lessons/LEDGER.md` 的精选晋升；勿在此另起真相源，账本变更后同步本页。

## 工具调用 / 批处理
- **并行批次**：只读诊断与写操作**分批**。首个命令非零退出会**连带取消整批**，已写文件丢失。（L1）
- 预期可能非零的探测命令（`gh auth status`、存在性检查）**单独跑**，别和写操作混批。

## git / 提交
- 含敏感字样（`Remove-Item`、`rm -rf` 等）或多行的 commit message **走文件**：`git commit -F <file>`；harness 会扫描命令文本并拦截。（L2）
- 历史改写后用 orphan 分支压成单提交；reflog expire + gc 清旧 blob（去敏感历史）。重置前先确认已知修复都在当前工作树（L10）。
- **`.gitignore` 救不了已追踪文件**：变 public 前必跑 `scripts/check-secrets.ps1 -Strict`（须 0 FATAL）；核心数据库/密钥/隐私须在 `.gitignore` 且**未被追踪**，已追踪的先 `git rm --cached` 再 commit；建仓 `gh-bootstrap` 默认 private 且预检调用 `check-secrets`。（L27，已被 `check-secrets.ps1` + `gh-bootstrap.ps1` 机械守卫）

## gh CLI / 认证
- **彻底清 token**：`Remove-Item Env:GH_TOKEN, Env:GITHUB_TOKEN -EA SilentlyContinue`。空串 token 仍遮蔽 keyring。（L3）
- **free + private 仓不支持服务端规则集**（403 Upgrade to Pro）。强制靠客户端 `review.ps1`，或升级 Pro / 转 public。（L5）
- 本项目 gh 写操作**仅限配置的个人账号**（见 scripts/_config.ps1 `GhAccount`）：`scripts/_guard.ps1` 的 `Assert-PersonalAccount` 前置校验。
- worktree 内 `gh pr merge` 不加 `--delete-branch`（main 被主工作树占用会 fatal）。（L13）

## task-loop / 评审 / ship
- **卡感知评审 + 卡元数据走 main**：`review.ps1` 须读卡 `allow_paths`/边界例外按卡判，勿用通用硬边界误 block 必要的跨路径改动；卡自身的 `allow_paths`/`status` 改动属**规划**，走 main 的 docs 提交、**别塞进功能分支 PR**（否则 codex block「该路径不在本卡 allow_paths」）；卡外必要附带改动单独提交 main 再 rebase 分支，使卡 diff 纯 `allow_paths`。（L18）

## 输出 / 核验
- 关键结论别靠肉眼读控制台（长 JSON / 多字节会渲染损坏）。写文件再 `Read`，或 `gh api --jq` 取确定字段判定。（L6）

## PowerShell 语法（对比 bash）
- 无 heredoc：用 here-string `@'`…`'@`（字面）/ `@"`…`"@`（插值），闭合标记**顶格**。（L7）
- 清环境变量用 `Remove-Item Env:X`，不是 `unset`；判存在用 `Test-Path`；`$null` 不是 `/dev/null`（用 `2>$null`）。
- 脚本统一 `#requires -Version 7` + `Set-StrictMode -Version Latest` + `$ErrorActionPreference='Stop'`。
- 让 dod 内**任一** native 命令非零即失败：`$PSNativeCommandUseErrorActionPreference=$true`（否则只取末句退出码）。
- 勿用自动变量名（`$home/$host/$pwd/$pid/$input/$args` 等）作自定义变量/函数参数，否则静默空跑。（L8）
- 后台拉起 .cmd/.bat（npm/vite 等）经 `cmd.exe /c` 间接，勿 `-FilePath 'npm' -NoNewWindow`。（L9）

## venv / uv（Windows）
- `uv run <console-script>`（uvicorn/pytest 的 .exe）在 Windows 可能报 "Failed to canonicalize script path" → 一律改 `uv run python -m <module>`。（L16）
- .ps1 一律用 PowerShell 工具调用，勿经 Bash 工具（反斜杠被吞）。（L17）
