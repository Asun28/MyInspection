# 经验总账 (LEDGER) — 项目总经验（Tier 3）

> **自净化经验系统的唯一真相源**。所有踩过的坑、定过的型、不该重导的结论，先落这里。
> 必须层（CLAUDE.md「经验铁律」）与按需层（`docs/lessons/<topic>.md`）都是从本账本**精选晋升**而来。
>
> **维护**：`scripts/lessons.ps1` 增删查改；流程见 `docs/LESSONS.md`。
> **安全**：只记**工程结论**，禁记 token/密钥/组织名/客户名/具体事故隐私（公开仓可见）。
> **方向**：会话级原始记录（`progress.md` / claude-mem）── 精选 ──▶ 本账本 ── 晋升 ──▶ 按需层 / 必须层。**单向，不回灌。**
>
> 本账本随模板**预置了一批工具链通用经验**（PowerShell / gh / git / worktree / codex / guard-frozen / TDD / CI）——
> 大多与项目领域无关，新项目直接受用。**少数是「示例性领域经验」**（如 L35/L36 的 WebGL/three.js、
> L32/L34 的数据摄取/后台编排细节）——它们既是真实经验、也示范了入账格式，**与你项目无关就直接删**
> （`ledger` 层、不进每轮上下文，留着只占总账篇幅、不占 token）。项目特定经验随开发用 `lessons.ps1 add` 累积。

## 字段约定（每条一块）
- `id`：`L<序号>`，全局唯一，永不复用。
- `date`：YYYY-MM-DD（发现日）。
- `tags`：检索关键词（小写、逗号分隔）。
- `tier`：`must`(铁律/必须层) | `ondemand`(主题/按需层) | `ledger`(仅账本)。
- `kind`：`pitfall`(工具链/方法的坑，可升级为 `enforced_by` 机械守卫) | `judgment`(方向/决策的失手，喂 `docs/HARNESS-REVIEW.md` judgment-feed 复审)。
  缺省视作 `pitfall`（旧条目无此字段，向后兼容）。两类正交于 tier/severity；见 `docs/LOOP-ENGINEERING.md`。
- `severity`：`blocking`(会卡死/返工) | `major` | `minor`。
- `recurrence`：复发次数（≥2 即满足晋升必须层的客观门槛之一）。
- `symptom`：触发场景 / 症状（"什么时候会撞上"）。
- `root_cause`：根因（"为什么"）。
- `rule`：**可直接照做的结论**（"下次怎么办"）——这是全条目的价值所在。
- `enforced_by`：该经验的**机械守卫**（确定性脚本/闸门路径，如 `scripts/review.ps1`），或 `none（理由）`。
  `severity=blocking` 的经验**必填**（`lessons.ps1 check` 强制）——把会卡死/返工的坑从「上下文提醒」升级为「机器拦截」，
  即 OpenAI《Harness Engineering》的「让同一错误不可复发」。无纯机械守卫时显式记 `none（理由）`，逼出一次有意识的取舍。
- `cost`：**犯错成本 / 浪费时间**（可选，如 `浪费40分钟`/`半天返工`）——提 Gotcha 信噪比，帮判断该坑值不值得晋升/加机械守卫。
  仅当 `lessons.ps1 add -Cost ...` 给了才追加到 meta 行末尾（`… ｜ recurrence: N ｜ cost: …`）；旧条目无此字段，缺失不报错、不影响任何校验（向后兼容）。
- `refs`：相关文件/命令（可选）。

---

## L1
- date: 2026-06-01 ｜ tags: powershell, tooling, parallel, claude-code ｜ tier: must ｜ severity: blocking ｜ recurrence: 3
- symptom: 一批并行工具调用里，若**首个**命令非零退出，整批后续调用被**连带取消**，已写的文件丢失。
- root_cause: 并行批次共享失败传播；非零退出触发整批 abort。
- rule: **只读诊断与写操作分批**；写操作（Write/Edit/提交）单独成批或串行；预期可能非零的探测命令单独跑。
- enforced_by: none（行为纪律；CLAUDE.md 工作准则 + 本铁律每轮在上下文，无确定性守卫）
- refs:

## L2
- date: 2026-06-01 ｜ tags: powershell, git, commit, safety-guard ｜ tier: ondemand ｜ severity: blocking ｜ recurrence: 2
- symptom: `git commit -m "...Remove-Item Env:..."` 被 harness 的危险命令守卫拦下（消息里的字面量被当成要执行的命令）。
- root_cause: 命令文本扫描器误判 commit message 内的 `Remove-Item` 等字样为破坏性操作。
- rule: 多行/含敏感字样的 commit message **走文件**：`git commit -F <msgfile>`（或 PowerShell 单引号 here-string）。
- enforced_by: none（harness 危险命令守卫会拦截违反者 → 即时反馈；走文件是规避手法，非可机检的仓内守卫）
- refs:

## L3
- date: 2026-06-01 ｜ tags: github, gh-cli, auth, env, powershell ｜ tier: must ｜ severity: blocking ｜ recurrence: 2
- symptom: `gh` 始终 401，即使 keyring 已登录；`$env:GITHUB_TOKEN=''` 也没用。
- root_cause: **空字符串** token 仍被 gh 视为「已设置」，从而**遮蔽** keyring 凭据。
- rule: 用 `Remove-Item Env:GH_TOKEN, Env:GITHUB_TOKEN -ErrorAction SilentlyContinue` **彻底清除**，不要赋空串。所有 gh 脚本开头已这样做。
- enforced_by: scripts/review.ps1 + scripts/_guard.ps1 + task.ps1 + gh-bootstrap.ps1（各 gh 脚本开头已 Remove-Item 清 token）
- refs: scripts/_guard.ps1, task.ps1, review.ps1, gh-bootstrap.ps1。

## L4
- date: 2026-06-02 ｜ tags: codex,review,stdin,powershell,background ｜ tier: ondemand ｜ severity: blocking ｜ recurrence: 1
- symptom: review.ps1 的 codex exec 在后台/非交互运行时停在 "Reading additional input from stdin..."，裁决永不回传，task.ps1 ship 整条闭环挂死。
- root_cause: codex exec 即便 prompt 以 arg 传入，仍会读 stdin 取 additional input；继承到一个永不 EOF 的管道（harness 后台进程）时会永久阻塞。
- rule: 调用 codex exec 一律前置一个已 EOF 的 stdin：`'' | & codex exec ...`；勿依赖继承的 stdin。已落 scripts/review.ps1。
- enforced_by: scripts/review.ps1（`'' | & codex exec` 前置 EOF stdin，已落地）
- refs:

## L5
- date: 2026-06-01 ｜ tags: github, ruleset, branch-protection, billing ｜ tier: ondemand ｜ severity: major ｜ recurrence: 1
- symptom: 对 **free + private** 仓库应用分支规则集 → GitHub 返回 **403 "Upgrade to Pro or make this repository public"**。
- root_cause: 免费账户的私有仓不支持服务端规则集/分支保护。
- rule: free+private 下**别指望服务端强制**；R3「Codex 代人工」靠**客户端** `review.ps1`（block 即不合并）+ task-loop skill。要服务端强制：升级 Pro 或转 public。`gh-bootstrap.ps1` 已探测 403 并优雅跳过。
- refs: docs/DEVOPS-WORKFLOW.md；scripts/gh-bootstrap.ps1。

## L6
- date: 2026-06-01 ｜ tags: powershell, terminal, json, verification ｜ tier: ondemand ｜ severity: major ｜ recurrence: 1
- symptom: 终端里 `gh api ... | ConvertFrom-Json` 的输出被渲染损坏，误导判断（曾把"规则集已建"误读为成功）。
- root_cause: 控制台对长 JSON / 多字节的渲染不可靠。
- rule: 关键结论**别靠肉眼读控制台**：写入文件再 `Read`，或用 `--jq` 取确定字段，做数值/布尔判定。
- refs:

## L7
- date: 2026-06-01 ｜ tags: powershell, bash, heredoc, portability ｜ tier: ondemand ｜ severity: minor ｜ recurrence: 1
- symptom: bash heredoc（`<<'EOF' ... EOF`）在 PowerShell 里解析错误。
- root_cause: heredoc 是 bash 语法，PowerShell 不支持。
- rule: PowerShell 里用 here-string：`@'`…`'@`（字面）或 `@"`…`"@`（插值）；闭合 `'@`/`"@` 必须顶格。
- refs:

## L8
- date: 2026-06-08 ｜ tags: powershell,gotcha,automatic-variables ｜ tier: ondemand ｜ severity: minor ｜ recurrence: 1
- symptom: PowerShell 脚本/函数用 `$home`(或 `$host/$pwd/$pid`) 作变量名或**函数参数名** → "Cannot overwrite variable home because it is read-only or constant"，整段静默不执行核心逻辑。
- root_cause: `$HOME` 等是 PowerShell 只读/常量自动变量；用作自定义变量名或函数参数名即赋值/绑定失败，且常被 ErrorActionPreference=Continue 吞成静默空跑。
- rule: PowerShell 勿用自动变量名(`$home/$host/$pwd/$pid/$input/$args/$error/$true/$null`)作自定义变量或**函数参数**；home 路径一律加前缀名（如 `$AppHome/$modelHome`）。
- refs:

## L9
- date: 2026-06-05 ｜ tags: powershell,windows,start-process,npm,launch ｜ tier: ondemand ｜ severity: major ｜ recurrence: 1
- symptom: 用 `Start-Process -FilePath 'npm' -NoNewWindow` 报 "%1 is not a valid Win32 application"；在 `$ErrorActionPreference='Stop'` 下整脚本中止。
- root_cause: `-NoNewWindow` 令 Start-Process 走 CreateProcess（非 ShellExecute），而 CreateProcess 不能直接执行 .cmd/.bat；npm 在 Windows 是 npm.cmd。
- rule: PowerShell 后台拉起 .cmd/.bat（npm/yarn/pnpm/vite 等）经 cmd.exe 间接：`Start-Process -FilePath 'cmd.exe' -ArgumentList '/c','npm','run','dev' -NoNewWindow`。另：.ps1 不能双击启动且受执行策略限制 → 配一个 .cmd 封装（`pwsh -NoProfile -ExecutionPolicy Bypass -File ...`）。
- refs:

## L10
- date: 2026-06-01 ｜ tags: git,history-rewrite,scripts,regression ｜ tier: ledger ｜ severity: major ｜ recurrence: 1
- symptom: orphan/单提交重置历史后，之前在旧历史里修过的脚本 bug 复活，因为单提交用的是修复前版本。
- root_cause: 历史压缩/重置只保留工作树当前内容；若修复发生在被丢弃的旧 commit 且未落到当前工作树，即随历史一起消失。
- rule: 做历史重置(orphan/reset)前，先确认所有已知修复都在当前工作树；重置后对关键脚本重跑 parse+冒烟，核对没有已修 bug 回归。
- refs:

## L11
- date: 2026-06-01 ｜ tags: tooling,evaluation,windows,anti-sprawl ｜ tier: ledger ｜ severity: minor ｜ recurrence: 1
- symptom: 评估外部 Claude Code 工具栈(如 spec-kit/BMAD/GSD/ECC)是否引入。
- root_cause: 多数生态工具是 bash/Bun/macOS 绑定且与本仓 PS7 原生闭环(task-loop/codex/guard-frozen/lessons)功能重叠。
- rule: 默认不装外部工具栈：先核 Windows 原生性 + 是否与现有原语重复 + 许可。可有可无即跳过(参考可以)。唯一已采纳的是 anthropics/claude-code-security-review 的本地 slash command(纯本地/无 API key/正交于 codex)。
- refs: docs/DEVOPS-WORKFLOW.md §8 + §0

## L12
- date: 2026-06-01 ｜ tags: project-convention,init,tooling ｜ tier: ledger ｜ severity: minor ｜ recurrence: 1
- symptom: 运行 /init 或审查 CLAUDE.md 时，例行去查 Cursor(.cursorrules/.cursor/) 与 Copilot(.github/copilot-instructions.md) 规则。
- root_cause: 本项目不使用 Cursor / GitHub Copilot；查它们的规则是无用步骤。
- rule: 若本项目唯一 AI 工具是 Claude Code（+ codex 评审），/init 与 CLAUDE.md 审查时跳过 Cursor/Copilot 规则检查。
- refs: CLAUDE.md 约定段

## L13
- date: 2026-06-02 ｜ tags: gh,worktree,merge,task ｜ tier: ledger ｜ severity: major ｜ recurrence: 1
- symptom: `gh pr merge --delete-branch` 在 worktree 内报 'main' is already used by worktree；task.ps1 ship 在合并已成功后仍 throw（误报失败）。
- root_cause: --delete-branch 删本地分支前需 checkout base(main)，而 main 被主工作树占用。
- rule: worktree 内 `gh pr merge` 不加 --delete-branch：远端分支靠 repo delete_branch_on_merge，本地分支靠 task.ps1 cleanup。已落 task.ps1。
- refs:

## L14
- date: 2026-06-02 ｜ tags: guard-frozen,hook,false-positive,codex ｜ tier: ledger ｜ severity: minor ｜ recurrence: 1
- symptom: 给 codex 写提示词或临时文件时，若命令串里同时出现「冻结物文件名字符串 + 重定向符」，guard-frozen 钩子会误 deny，尽管实际写的是非冻结路径。
- root_cause: 钩子 path-B 启发式只看「写动词 + 冻结路径子串」是否共现，不解析重定向的真实目标。
- rule: 临时文件用 Write 工具落到 .secrets/(file_path 非冻结即放行)；codex 提示词用 `$(cat 文件)` 间接引用，命令串里不出现冻结路径字面量+重定向。
- refs:

## L15
- date: 2026-06-02 ｜ tags: codex,review,task,nondeterminism ｜ tier: ledger ｜ severity: major ｜ recurrence: 1
- symptom: task.ps1 ship 跑两次 codex 评审（gate + post-status），非确定性下两次裁决可能矛盾且翻倍耗时。
- root_cause: review.ps1 每次都重跑 codex；task.ps1 在 gate 与 post-status 各调一次。
- rule: PR 开好后**单次**运行 review.ps1 -PostStatus（评审+回贴合一），其退出码即闸门；已重排 task.ps1 ship（push→PR→单次评审→合并）。
- refs:

## L16
- date: 2026-06-02 ｜ tags: uv,uvicorn,windows,console-script ｜ tier: ledger ｜ severity: major ｜ recurrence: 1
- symptom: `uv run uvicorn ...`（或其它 venv console-script）在 Windows 报 "Failed to canonicalize script path"，服务起不来。
- root_cause: uv 解析 venv 控制台脚本(.exe)路径失败（Windows 下的已知怪癖）。
- rule: 用 `uv run python -m <module>`（模块入口，如 `-m uvicorn` / `-m pytest`）替代 `uv run <console-script>`。
- refs:

## L17
- date: 2026-06-03 ｜ tags: powershell,bash-tool,ps1,tooling,encoding ｜ tier: must ｜ severity: minor ｜ recurrence: 3
- symptom: 用 Bash 工具调 `.ps1` 有两种坏法——①反斜杠路径被吞成 `scriptstask.ps1`，exit 64，脚本根本没执行；②即便改用正斜杠路径让脚本真跑起来，Bash(Git Bash) 终端的控制台编码与 PowerShell 不一致，`selftest.ps1` 等含中文断言/输出的脚本会显示乱码、且**真的返回 FAIL**（非仅显示问题）——靠 Bash 跑出的「验证」结果不可信，须用 PowerShell 工具重跑核实。
- root_cause: Bash 把 Windows 路径反斜杠当转义消除；且 Bash(Git Bash) 子进程的控制台代码页与 pwsh 原生 `[Console]::OutputEncoding` 不同源，跨这层边界的中文断言/比较会失真。
- rule: `.ps1` 一律用 PowerShell 工具调用（task-loop 已规定一律 pwsh 非 bash），**不仅因路径分隔符会被吞，也因编码链不同会产出假结果**；连事后核验/巡检也不例外——别为图快用 Bash 抄近路查 pwsh 脚本结果。必须用 Bash 时路径改正斜杠 `scripts/task.ps1`，且任何看起来异常的失败先用 PowerShell 工具重跑一次再下结论。
- refs:

## L18
- date: 2026-06-03 ｜ tags: codex,review,allow_paths,ship,card-meta,rebase ｜ tier: ondemand ｜ severity: major ｜ recurrence: 2
- symptom: codex review.ps1 对必要的跨 allow_paths 改动误判 block；又：把任务卡自身 specs/tasks/<ID>.md 的 allow_paths/status 改动放进功能分支 → codex block「该路径不在本卡 allow_paths」。
- root_cause: review.ps1 若不读卡片 allow_paths/边界例外，会按通用硬边界误判；卡 allow_paths 过窄未含必要附带改动。
- rule: review.ps1 改卡片感知（读 specs/tasks/<branch>.md，honor 卡声明的 allow_paths/边界例外）；卡外必要改动单独提交 main 再 rebase 分支，使卡 diff 纯 allow_paths；卡自身的 allow_paths/status 改动属规划，走 main 的 docs 提交、勿入功能分支 PR。
- refs:

## L19
- date: 2026-06-03 ｜ tags: tdd,test-design,fixture,codex-review ｜ tier: ledger ｜ severity: major ｜ recurrence: 1
- symptom: codex block：测 transformation(A→B 替换) 时 fixture 缺被替换物，即使实现是 no-op 也过（vacuous pass）。
- root_cause: 测变换时 fixture 缺被变换的输入，只断言了无关属性，未断言「变换后状态」。
- rule: 测 transformation 时 fixture 必含被变换的输入，并断言变换后状态（如输出=新值 ≠ 旧值）；缺输入的断言是 vacuous pass。
- refs:

## L20
- date: 2026-06-04 ｜ tags: security-review-local,worktree,task-loop,gate ｜ tier: ledger ｜ severity: major ｜ recurrence: 1
- symptom: worktree 卡 ship 时跑 /security-review-local：命令内嵌的 `git diff HEAD` 在主检出(clean)求值，看不到 worktree 的未提交改动，于是审了空 diff、得到 vacuous pass。
- root_cause: 斜杠命令的 shell 替换在仓库根 cwd 求值，而 task-loop 的所有编辑都在 worktree；命令写死 `git diff HEAD`、无路径/cwd 参数。
- rule: worktree 卡跑安全闸别盲信 /security-review-local 的 verbatim 调用：改为对 worktree 实际 diff(`git -C <wt> diff HEAD`)施同一 rubric 再判；codex PR 评审在 worktree 内跑、不受此影响。
- refs:

## L21
- date: 2026-06-05 ｜ tags: codex,ship,review-gate,quota ｜ tier: ledger ｜ severity: major ｜ recurrence: 1
- symptom: task.ps1 ship 在 R3 Codex 评审闸门非零退出、回贴 codex-review=failure、未合并；codex 输出含 hit your usage limit。
- root_cause: Codex 用量配额耗尽，codex exec 返错而非裁决 JSON；review.ps1 解析不到 verdict 即 fail-closed 当 block（非真实评审拒绝）。
- rule: 非代码问题，勿绕过 codex 闸门勿手动合并；待配额重置后重跑 task.ps1 ship（DoD/commit/push 幂等、PR 复用、仅重跑 codex）。
- refs:

## L22
- date: 2026-06-05 ｜ tags: contract,manifest,frozen,guard-frozen,version ｜ tier: ledger ｜ severity: major ｜ recurrence: 1
- symptom: 给冻结物加字段或 bump 版本号时 guard-frozen 钩子 PreToolUse deny 一切编辑。
- root_cause: guard-frozen matcher 拦冻结物 Edit/Write；且耦合字段（如 manifest 引用 contract 版本）bump 会级联否则校验崩。
- rule: 走版本评审：卡 allow_paths 明列冻结物 + front-matter 声明本卡=版本评审（codex 据此不误 block）；临时改 .claude/settings.json 把 guard-frozen matcher 改为不匹配串（mid-session 即生效）编辑后立即复原净零；bump 版本必同步放宽下游耦合字段且保留旧值向后兼容；新字段一律 optional 默认值。
- refs:

## L23
- date: 2026-06-05 ｜ tags: review,codex,gh,powershell,branch ｜ tier: ledger ｜ severity: major ｜ recurrence: 1
- symptom: review.ps1 -PostStatus 对含斜杠分支名（如 feat/foo）回贴 codex-review=failure「未能解析 Codex 裁决」，即便 Codex 实际裁 pass → 阻塞合并。
- root_cause: 裁决文件 .review/<branch>.json 中 <branch> 含 / → 写到子目录但父目录未建 → 写入失败 → $raw 空 → 默认 block。
- rule: 评审分支名避免斜杠（用 T-id 或 feat-xxx 连字符）；review.ps1 已 sanitize 分支名(/→-) 作文件名根治。
- refs: scripts/review.ps1

## L24
- date: 2026-06-15 ｜ tags: loop, triage, planning, scope, judgment ｜ tier: ledger ｜ kind: judgment ｜ severity: major ｜ recurrence: 1
- symptom: 拿到 triage 收件箱（或任何回路/agent 产出的待办清单）时，倾向把所有信号一次性平推/批处理。
- root_cause: 把「发现」当成「已决定做」，跳过了**方向判断**这一步——回路只该自动化发现，做哪件仍需逐条权衡（Anthropic RSI：方向设置仍归人/agent）。
- rule: 心跳/回路产出**只发现不定方向**；一次只挑**一件**最该做的，做完再扫，别无脑平推收件箱。act 一律走既有交付链的闸门（worktree/TDD/Codex/CI）。
- enforced_by: none（judgment 类方向启发式，难机检；喂 docs/HARNESS-REVIEW.md judgment-feed 随模型变强复审）
- refs: docs/LOOP-ENGINEERING.md; scripts/triage.ps1

## L25
- date: 2026-06-21 ｜ tags: frontend,testing,determinism,ai ｜ tier: ledger ｜ kind: judgment ｜ severity: major ｜ recurrence: 1
- symptom: AI 给前端选测试时易被 2026 的 self-healing/AI-视觉验证带偏,或把跨 OS 像素截图当默认回归,导致闸门非确定性/联网/在 Linux CI 全红
- root_cause: 运行期有 LLM/视觉模型在验证回路=非确定性+联网,与离线 exit0/1 闸结构冲突;字体渲染由 OS 决定,Windows 生成的 screenshot baseline 在 Linux CI 必失配
- rule: 前端闸=纯 Playwright 断言脚本(getByRole/toHaveText/零console-error/无失败请求, exit0/1);模型(playwright-mcp/webapp-testing)只在上游探索写断言、绝不进闸。优先 DOM/文本断言而非像素截图;视觉回归仅当布局本身被测,且 baseline 只在固定 mcr.microsoft.com/playwright Docker 生成、agent 绝不自动 --update-snapshots。跳过所有 AI self-healing SaaS。最小基线只需 Vitest+Playwright+route-mock(+1条axe)。**此为软完成/Ralph Wiggum 失败的前端特例**——done 必须是客观 exit0/1、不是模型意见;通则(命名+/loop vs /goal 节律+安全税)见 docs/LOOP-ENGINEERING.md 软完成失败节
- enforced_by: 
- refs: 

## L26
- date: 2026-06-21 ｜ tags: scaffold,methodology,tooling,design-principle ｜ tier: ledger ｜ kind: judgment ｜ severity: major ｜ recurrence: 1
- symptom: 定义脚手架能力/流程时,易写成"必须用某个具体 skill/agent/工具"(如点名 agent-reach、某 MCP、某库);工具一过时/不可用/有更优替代,定义就绑死或失效
- root_cause: 工具是易变的实现细节,脚手架的持久价值在【方法论 + 标准(工具无关)】;把具体实现当定义 = 把易变当恒久,违背模板"纯净可复用"的本意
- rule: 定义层只写【方法论 + 标准】(工具无关、能力描述、可机检契约);具体 skill/agent/工具一律作"举例 / 按需接入 / 当前默认",措辞用 如/例如/若装/可选/按需,可随时搜更优的替换、且不破坏定义;选定新工具记进经验库。核心闸门(如"第二独立模型对抗评审")也按方法论表述,具体实现(当前=codex)作注解。审视每条定义自问:工具没了这条标准还成立吗?
- enforced_by: 
- refs: 

## L27
- date: 2026-06-21 ｜ tags: git,gitignore,security,public,secrets,database ｜ tier: ondemand ｜ kind: pitfall ｜ severity: blocking ｜ recurrence: 1
- symptom: repo 变 public 后核心数据库/隐私文件泄露;或把敏感文件加进 .gitignore 后以为安全,但它早被 git 追踪过——gitignore 对【已追踪】文件无效,变 public 照样泄露
- root_cause: .gitignore 只约束【未追踪】文件;已 commit 过的文件不受其约束(须 git rm --cached 才停止追踪)。且默认 .gitignore 常漏核心数据库(*.db/*.sqlite/*.duckdb)
- rule: 变 public 前必跑 scripts/check-secrets.ps1 -Strict(须 0 FATAL);核心数据库/密钥/隐私须在 .gitignore 且【未被追踪】;已追踪的先 git rm --cached 再 commit;建仓 gh-bootstrap 默认 private 且预检调用 check-secrets
- enforced_by: scripts/check-secrets.ps1 + scripts/gh-bootstrap.ps1
- refs: 

## L28
- date: 2026-06-22 ｜ tags: context,claude-md,budget,over-engineering ｜ tier: ledger ｜ kind: judgment ｜ severity: major ｜ recurrence: 1 ｜ cost: 早期数天反复低级错+换模型瞎折腾
- symptom: 一个会话从早干到晚不 /compact;CLAUDE.md/规则只增不减堆成文档库——上下文过半 Claude 开始选择性漏指令、犯低级错,误以为模型变笨
- root_cause: 大模型同时能稳守的指令数有上限(系统指令已占一块);上下文是内存不是无限纸,越堆每条权重越低、过半即降质。CLAUDE.md 是上下文预算表不是文档库
- rule: 把上下文当内存,CLAUDE.md/规则走预算制——能 linter 机检的别写进文档、分语言细则拆 .claude/rules 懒加载、只留"真犯过"的 Gotcha;回退优于在被污染上下文里纠正;子代理价值在隔离。预算纪律只约束每轮常驻文档,不再约束运行时长(会话拆分/长自主运行时长见 docs/LOOP-ENGINEERING.md)。
- enforced_by: 
- refs: docs/LOOP-ENGINEERING.md

## L29
- date: 2026-06-22 ｜ tags: docs,cross-reference,lessons,drift,skill ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 在 skill/doc 散文里用 L<n> 指针引用经验时写错或写旧 id（例：把 L20 的 worktree 安全闸内容标成 L21），把读者导向错误经验；而 selftest 闸⑪ 只校验文件路径交叉链接、不校验 L-id 引用，无机检兜底。
- root_cause: L-id 跨引散落在 prose、纯手写；现有交叉链接机检只覆盖 docs/specs/scripts/.claude/.github 的文件路径，不覆盖 LEDGER 的 L<n> 引用，故此类 drift 无闸可拦。
- rule: 写 L<n> 指针前先 pwsh -File scripts\lessons.ps1 search <关键词> 核对 id 与内容一致；改/重排 LEDGER 时顺手 grep 全仓 L<n> 引用处同步。存在性已机械化（selftest 闸⑯）；内容是否对得上（指针真指那条经验）仍靠人工。
- enforced_by: scripts/selftest.ps1 闸⑯（扫 .claude/skills + docs 的 L<n> 引用须存在于 LEDGER；排除 path:Lnn 行号/Lnn-mm 行段；存在性机检，语义仍人工）
- refs: .claude/skills/task-loop/SKILL.md; docs/lessons/LEDGER.md

## L30
- date: 2026-06-23 ｜ tags: workflow,routing,funnel ｜ tier: ledger ｜ kind: judgment ｜ severity: major ｜ recurrence: 1
- symptom: 用户用自然语言下达建造类请求(建一个X网站),agent 直接 free-build,跳过想法→计划漏斗与 task-loop 全链
- root_cause: 漏斗入口此前只是 CLAUDE.md 软指针,靠模型自觉;无 prompt-time 机械路由,模型可忽略
- rule: 新活启动用显式触发语「根据脚手架,...」;UserPromptSubmit 钩子 route-new-work 命中即强制全链,除非用户明确说跳过
- enforced_by: .claude/hooks/route-new-work.ps1
- refs: 

## L31
- date: 2026-06-23 ｜ tags: python,windows,encoding,cli ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: Python 向 Windows 控制台/重定向管道 print 中文时 UnicodeEncodeError(cp1252/cp936) 整个脚本崩;本次抓取管线与内联脚本各崩一次
- root_cause: Windows 默认控制台编码非 UTF-8;Python 按该编码编码 stdout 遇 CJK 即崩
- rule: 脚本开头 sys.stdout.reconfigure(encoding=utf-8, errors=replace);调用方设 PYTHONIOENCODING=utf-8 且 chcp 65001;print 尽量 ASCII
- enforced_by: 
- refs: 

## L33
- date: 2026-06-23 ｜ tags: windows,launcher,bat,ps1,encoding ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: start.bat 双击跑不了 / start.ps1 输出中文乱码
- root_cause: cmd 按 OEM 读 .bat 内含 UTF-8 中文即误解析;Windows PowerShell 把无 BOM 的 .ps1 当 ANSI 读致中文乱码
- rule: 启动器正文一律 ASCII;首行 chcp 65001 + 设 PYTHONIOENCODING=utf-8 让子进程 CJK 正常;.ps1 若必须含中文则存 UTF-8 BOM
- enforced_by: 
- refs: L9

## L37
- date: 2026-06-23 ｜ tags: workflow,tiering,scaffold ｜ tier: ledger ｜ kind: judgment ｜ severity: major ｜ recurrence: 1
- symptom: 用户根据脚手架做单用户本地可视化(T1) 实际跳过漏斗文档/任务卡/worktree/TDD/Codex/PR/handoff 全链 只保留多代理+反slop+目检+不污染元仓+记经验
- root_cause: route-new-work 要求强制全链除非用户说跳过;但对快速迭代实时验收的玩具级本地 viz 重闸收益低;我既没跑全链也没显式声明偏离
- rule: 把 T0/T1 快车道在漏斗里写成 sanctioned 路径 明列可跳过(worktree/TDD/Codex/PR/funnel docs)与必须保留(元仓洁净/多代理调研/反slop/真浏览器目检/记经验);跳全链时显式声明并经用户确认而非静默绕过;喂 HARNESS-REVIEW
- enforced_by: 
- refs: 

## L38
- date: 2026-06-23 ｜ tags: scaffold,git,workflow,worktree ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: task.ps1 -Phase start 在默认分支为 master 的仓库炸 git worktree add ... main 报 fatal invalid reference main 退出1;selftest 静态测不出 只有真跑 task-loop 才暴露
- root_cause: task.ps1 把基线分支硬编码 main;git init 默认分支随 init.defaultBranch 变(常 master) 无 main 引用即失败
- rule: 脚手架/git 脚本别硬编码 main 基线分支从 git symbolic-ref --short HEAD 动态取(回退 main)或读 _config;已修 task.ps1
- enforced_by: 
- refs: 

## L39
- date: 2026-06-23 ｜ tags: scaffold,selftest,testing,workflow ｜ tier: ledger ｜ kind: judgment ｜ severity: major ｜ recurrence: 1
- symptom: selftest 14 闸全过(静态完整性) 但真跑动态链 task.ps1 start 才发现硬编码 main 的 bug 静态绿不等于工作流对
- root_cause: selftest 只校验语法/schema/哨兵/init 干跑等静态面 从不真跑 task-loop(worktree/TDD/review/ship) 动态 bug 只在真实交付链冒出
- rule: 给 selftest 加一道动态 E2E 冒烟闸 临时目录 init 到 git 到建卡 到 task.ps1 start 断言 exit0 加 worktree 建成(ship/review 需 gh/codex 留下游);用户拿项目压测脚手架时按 L37 真跑链而非 free-build;喂 HARNESS-REVIEW
- enforced_by: 
- refs: 

## L40
- date: 2026-06-23 ｜ tags: workflow,planning-harness,paths ｜ tier: ledger ｜ kind: judgment ｜ severity: major ｜ recurrence: 1
- symptom: plan-forge 跑完判 fix-first 称计划不存在,实为审了错仓:agent cwd 是元仓,传入的绝对 planPath 未注入脚本 args 全局,脚本回退相对默认 _local/PLAN.md 命中元仓而非目标下游
- root_cause: 经 Workflow scriptPath 跑下游 workflow 脚本时 args 未必到达脚本 args 全局;相对路径默认相对 agent cwd(元仓)解析,导致静默审错对象,且无计划时正确判 FATAL 反而掩盖了真因
- rule: 跑 plan-forge/decompose 前确认真相源路径确指向目标计划:用绝对路径并核验产出确实引用了该计划的 §N;args 不生效时把绝对路径写进脚本默认,或让 PLAN 落在默认相对位置
- enforced_by: 
- refs: .claude/workflows/plan-forge.mjs; docs/IDEA-TO-PLAN.md

## L41
- date: 2026-06-23 ｜ tags: task-cards,dod,allow-paths,ci ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 卡 dod_command 用 uv/pytest/bandit/npm 等工具,但 allow_paths 不含 pyproject.toml 或 package.json:评审按 allow_paths 判越界与 DoD 冲突,且 CI 工具缺失被误判为 DoD 失败
- root_cause: _TEMPLATE.md 范例 allow_paths 仅 path/to,未含依赖清单,下游照抄即埋坑
- rule: dod_command 引入的依赖/工具,其清单文件(pyproject.toml(+lock) 或 package.json)必须在该卡 allow_paths;只用 CI 预装工具,否则卡内声明安装(已在 _TEMPLATE.md 注释固化)
- enforced_by: 
- refs: specs/tasks/_TEMPLATE.md; docs/PLAN-TEMPLATE.md

## L42
- date: 2026-06-23 ｜ tags: database,schema,soft-delete ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 软删除表的唯一键不含 deleted 列:行被软删后无法重建同一唯一值(或与残留行唯一冲突),静默数据损坏
- root_cause: 唯一约束只覆盖业务列,未把软删标记纳入键
- rule: 软删除表的唯一索引必须包含 deleted 列(如 uk_users_email_deleted(email,deleted));PostgreSQL 用部分索引 WHERE deleted=0
- enforced_by: 
- refs: docs/lessons/database.md; docs/QUALITY-RUBRIC.md

## L43
- date: 2026-06-23 ｜ tags: database,schema,types ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 金额字段用 FLOAT/DOUBLE:二进制浮点不能精确表示十进制金额,累加/对账出现分位误差
- root_cause: 用近似浮点类型存精确货币
- rule: 金额一律 DECIMAL(p,s),禁 FLOAT/DOUBLE;多币种显式存币种字段
- enforced_by: 
- refs: docs/lessons/database.md

## L44
- date: 2026-06-23 ｜ tags: database,architecture,triggers ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 业务逻辑塞进触发器/存储过程/DB 事件:一个 insert 在数据库里偷偷扣库存/写日志/发消息,副作用隐藏,难追踪难测试难迁移
- root_cause: 把应用业务过程下沉到数据库层
- rule: 业务逻辑放 service/领域层/应用事务/消息队列;触发器仅作文档化例外(遗留集成或合规最小审计),用时记名称/作用表/时机/副作用/迁移
- enforced_by: 
- refs: docs/lessons/database.md

## L45
- date: 2026-06-23 ｜ tags: database,schema,foreign-key ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 中大型/分库分表系统用物理外键:迁移/回填/高并发写/微服务边界受损;或只存引用字段却不建索引致关联查询慢
- root_cause: 默认物理 FK 或漏给引用列建索引
- rule: 默认逻辑外键:存引用字段 + 给引用列建索引 + 应用层(事务/校验/唯一约束)保完整性;物理 FK 仅小型单库;跨库/跨服务/跨限界上下文禁物理 FK
- enforced_by: 
- refs: docs/lessons/database.md

## L46
- date: 2026-06-23 ｜ tags: planning,plan-forge,process ｜ tier: ledger ｜ kind: judgment ｜ severity: major ｜ recurrence: 1
- symptom: free-build 14 个产物后再补计划喂 plan-forge:连续两轮 fix-first。根因是计划描述的架构(变体 import 集中 normalize / 三处同构)与已建产物(每个变体内联自己的 bucket + loadData)相反;且计划 DoD 引用不存在的产物(smoke.mjs / 数据落盘),甚至 DoD 命令在真实 shell 上恒红
- root_cause: 构建在前、计划在后(retrofit plan):计划成了对现实的愿望而非映射;plan-forge 跨检 计划↔代码↔shell,于是审计/冻结永远和既有产物打架
- rule: 计划在前、构建在后。若已 free-build,补的计划必须先【对齐现状】:把既有产物的真实架构写进计划真相源(描述现状而非理想),否则 plan-forge 会持续 fix-first。这正是脚手架"plan→build"铁律的反证
- enforced_by: 
- refs: docs/PLAN-FORGE.md; docs/IDEA-TO-PLAN.md

## L47
- date: 2026-06-27 ｜ tags: tdd,acceptance,dod,contract,red-first,task-loop ｜ tier: ledger ｜ kind: judgment ｜ severity: major ｜ recurrence: 1
- symptom: DoD/验收标准模糊或错估，到 ship 才暴露 → 昂贵 redo；或执行中为过闸悄悄放低标准（vacuous pass）。
- root_cause: 验收标准由计划单方写入、执行方被动接收；开工时无显式「确认可测/达成一致」握手；标准未在开工即冻结，goalposts 可中途移动。
- rule: 把验收当任务的开场契约：task start 即把卡片验收标准复述成可测形式，执行方以 RED-first 编码为首个提交「确认一致」；写不出 RED（不可测/模糊/错范围）→ 停，先修卡再开工，别私自重解读；标准冻结后别为过闸放低（见 L19/L20）。
- enforced_by: 
- refs: 

## L49
- date: 2026-06-29 ｜ tags: git,refactor,pattern-fix,grep,recurrence ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 修了某脚本里一类坑（如 task.ps1 把基线分支硬编码成 main），同一 pattern 的坑在别处脚本（gh-bootstrap.ps1 push origin main）仍在；静态闸全绿、下游真跑才炸（L38 在 task.ps1 修过却在 gh-bootstrap 复发）。
- root_cause: 按「报错的那一处」点修，没把同 pattern 的所有实例一并 grep 出来一起修；动态闸（selftest 闸15）只真跑 task.ps1，覆盖不到 gh-bootstrap。
- rule: 修「模式类」bug（硬编码默认分支 / 路径分隔符反斜杠-only / native 命令非零不加 $LASTEXITCODE 守卫 / 单源魔法字面量散落）时：先全仓 grep 该 pattern 的所有实例一并修，并尽量每处补机械守卫或动态测试；别只修触发报错的那一处。
- enforced_by: none（模式搜索本身无机械守卫；部分由 selftest 闸15 动态 E2E 真跑 task+ship、闸1 语法兜底，但跨脚本同 pattern 仍靠 grep 纪律）
- refs: scripts/gh-bootstrap.ps1, scripts/task.ps1, scripts/selftest.ps1(闸15 动态 E2E)

## L50
- date: 2026-06-30 ｜ tags: review,process,selftest,merge,meta ｜ tier: ledger ｜ kind: judgment ｜ severity: major ｜ recurrence: 1
- symptom: selftest 全绿被当作「已评审」，跳过独立 R3/code-review 直接自合并 meta-repo 的「小/文档」PR；后补 max-effort 评审发现 selftest 漏测的阻断 bug（probe-8 非对象账本行 strict-mode 崩溃、违反心跳恒 0 契约）。
- root_cause: selftest 是确定性自检（测已知路径）≠ 独立第二模型对抗评审（找未知失败面）；「docs/small」自我合理化 + 个人仓自合并无人评审（GitHub 拒自批准遂跳过）让未评审改动进 master；改防御性代码时测试没真触发被防御的坏输入路径＝假绿。
- rule: 合并前跑独立评审（code-review skill / codex R3），即便 meta-repo「小」改；selftest 绿≠已评审。改 enforcer/probe 的防御代码，测试必须真喂被防御的坏输入（null/标量/空对象/非法 JSON），否则假绿。自合并无人评审时至少跑一次 workflow code-review 再 merge。
- enforced_by: 
- refs: docs/adr/0003-effectiveness-ledger-rollback-2026-06-30.md

## L51
- date: 2026-06-30 ｜ tags: selftest,portability,testing,windows ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 单盘 Windows 机首次用脚手架即崩
- symptom: selftest 闸15 用临时值覆盖 WorktreeRoot 隔离自己，于是 Get-ScaffoldWorktreeRoot 的 Windows 默认分支（曾硬编码 D:\wt）零测试覆盖；单盘机器首次 task.ps1 start 的 New-Item 抛 DriveNotFoundException，错误既不提 WorktreeRoot 也不提 _config，selftest 仍全绿。
- root_cause: 测试 harness 为隔离注入了某配置值，正好绕过该配置「未设时的默认/分支选择」逻辑——被绕过的默认分支因此无任何断言守护。
- rule: 当测试 harness 注入隔离值绕过某配置（如 selftest 覆盖 WorktreeRoot/账号/路径），对「未注入时的默认分支」补一条静态断言（如 Windows 默认须用 env:SystemDrive 不硬编码盘符），否则默认值回归了也照样绿。
- enforced_by: scripts/selftest.ps1（闸8 的 8.0b）
- refs: 30-lens eval C02

## L52
- date: 2026-06-30 ｜ tags: windows,subprocess,codex,review,llm ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: Windows 上中等 diff 即评审闸不可用
- symptom: review.ps1 把整段评审 prompt（含数万字符 diff 正文）作单个位置参数传 codex.exe；中等以上 diff 触发 Windows CreateProcess 32767 字符命令行上限，默认评审闸在 Windows 直接启动失败。
- root_cause: LLM/CLI 的 prompt 走 argv 时受 OS 命令行长度上限约束；Windows CreateProcess 上限 32767 字符，diff 类大输入轻易超限。
- rule: 把大 prompt（含 diff/长上下文）经 stdin 管道喂 CLI（prompt 用管道而非位置参数）；codex exec 无位置 prompt 时从 stdin 读题并顺带 EOF（取代 L4 的空 stdin 规避）。
- enforced_by: scripts/review.ps1（prompt 经 stdin 管道喂 codex）
- refs: 30-lens eval C24

## L53
- date: 2026-07-02 ｜ tags: review,license,decision,fail-closed ｜ tier: ledger ｜ kind: judgment ｜ severity: major ｜ recurrence: 1 ｜ cost: 6 轮 R3 + 2 次用户确认
- symptom: 想把第三方 skill 加进仓；upstream 无 LICENSE 文件（仅 README 声 MIT）。连试 vendor→pointer→中性备选提名，R3 全 block 6 次、均同一根因（无上游 LICENSE），还试图同 PR 自写政策例外被判循环自批准。
- root_cause: 把每次 block 当「修这条 finding」逐个改写措辞，没识别根因是结构性、PR 内不可解——fail-closed 许可闸对任何仓内背书都拦。
- rule: 一道 fail-closed 闸对同一根因跨 ≥2 次改写仍 block = 结构性墙：停改措辞、换策略（价值走仓外 / 修上游 / 放弃）。别在同一 PR 自写政策例外放行自己的改动（循环自批准，R3 必拦）。
- enforced_by: 
- refs: session 2026-07 make-interfaces-feel-better

## L54
- date: 2026-07-02 ｜ tags: license,vendor,skill,third-party,mcp ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 整轮返工（vendor 全撤→pointer→个人安装）
- symptom: README 声 MIT 但无 LICENSE 文件；LICENSE-POLICY §1 对无明确 LICENSE 文件 fail-closed；R3 对任何仓内 vendor/推荐（连备选列表提名都算）都 block。对照：Context7（MCP 服务配置、不拷第三方码）R3 first-try pass。
- root_cause: 宽松许可的证据是 LICENSE 文件（或等效可核证据），不是 README 一行声明；拷第三方码触许可闸，引用服务/配置不拷码则不触。
- rule: upstream 无 LICENSE 文件 → 别 vendor、也别在仓内推荐（连备选提名都算背书）。价值走仓外：用户级个人安装（用户自担许可判断）或先让上游补 LICENSE。服务/MCP 配置不拷码 → 不触许可闸。
- enforced_by: 
- refs: session 2026-07；docs/LICENSE-POLICY.md §1

## L55
- date: 2026-07-02 ｜ tags: init-scaffold,retrofit,data-safety,cleanup ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 1 轮 R3 block
- symptom: init-scaffold -Retrofit 照抄 -Cleanup 加无条件删 CHANGELOG.md 的清理；R3 catch = 会误删既有仓自己产品的 CHANGELOG（用户数据）。
- root_cause: -Cleanup 新建下游删 meta 文件安全（无疑是脚手架的）；-Retrofit 既有仓不安全——通用文件名 CHANGELOG.md/README.md 与用户数据撞名。脚手架专属名 selftest.ps1 安全、通用名不安全。
- rule: -Retrofit / 既有仓清理只无条件删脚手架专属文件名；通用名 CHANGELOG.md/README.md 等改提示手动处理、绝不自动删（毁用户数据）。
- enforced_by: 
- refs: session 2026-07 TD12；PR #21

## L59
- date: 2026-07-04 ｜ tags: git,worktree,stash,parallel-agents ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 并行多 worktree 时一个 agent git stash push/pop 弹出了另一 worktree 的 stash——两侧未提交改动互换/丢失
- root_cause: git stash 栈是 repo 级共享（所有 worktree 共用 refs/stash），并发 push/pop 竞态弹错条目
- rule: 并行 agent 环境禁用 git stash 做基线对比——用 git show HEAD:路径 或 git diff HEAD 替代；已弹错时用 git fsck 找 dangling commit 恢复
- enforced_by: 
- refs: 

## L61
- date: 2026-07-04 ｜ tags: template,selftest,placeholder,init ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 2
- symptom: 在任务卡 forbid 行写了「双大括号包大写蛇形词」形态的字面量，selftest 闸 8（init 干跑）判其为残留占位符，master 与 CI 全红
- root_cause: 闸 8 扫描所有随模板下发的非脚本文件；非真 token 的占位符形态字面量不会被 init 替换，即被判残留
- rule: 凡 tracked 的 md/json/yml 非脚本文件里提及 token 概念时用文字描述（如「双大括号+大写蛇形」），绝不写真形态字面量；真 token 只出现在模板产物
- enforced_by: check-cards.ps1 建卡期拒绝式断言（卡文含双大括号大写 token 字面量即 exit≠0，错误信息给修法，TD111）；selftest 闸10g 种子缺陷锁（种一张含该字面量的夹具卡、断言 check-cards 拒绝它 + 点名卡）。**覆盖面 = 任务卡（specs/tasks/*.md，check-cards 扫描面）**；其余 tracked 非脚本 md 的同类字面量仍由 selftest 闸 8 的 init 后 leftover 扫描事后兜（本条只把最易失守、直推 master 无 PR 闸的卡文面前移到建卡当场）
- refs: commit b02c33a；机检下沉见 T52-TD111-CARD-TOKEN-GATE

## L63
- date: 2026-07-04 ｜ tags: cards,doc-sync,review,scope ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 改了工作流行为面但其专门文档留给 R5 doc_sync，R3 按 #7 block：合并瞬间文档与行为互相矛盾
- root_cause: 行为与其权威文档是同一个可评审单元；doc_sync 只适合合并后才可知的登记类同步（版本号/状态位），不适合契约性描述
- rule: 改某链行为面时，其权威文档进同一张卡的 allow_paths 一起改；doc_sync 只留登记类（status/tracker/CHANGELOG）
- enforced_by: 
- refs: PR #46 block 记录

## L64
- date: 2026-07-04 ｜ tags: cards,contract,orchestration ｜ tier: ledger ｜ kind: judgment ｜ severity: minor ｜ recurrence: 1
- symptom: 编排者给实现者的边界指令比卡片契约保守（两闸契约被实现成三闸默认停），R3 按卡文本 block
- root_cause: 卡片是评审执行的字面契约；实现偏保守同样是对契约的偏离，且编排中途加的「稳妥」措辞未回写卡片
- rule: 派工指令与卡片契约字面对齐；确需收紧边界先改卡（卡是唯一契约源），别只在指令里私加
- enforced_by: 
- refs: PR #46 第三轮 block

## L65
- date: 2026-07-04 ｜ tags: powershell,here-string,helper-script,dangerous-guard,forward-slash ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 用插值 here-string(@"..."@)现拼 JS/cjs 辅助脚本并内嵌反斜杠转义(如 -replace 把 \\ 换成 \\\\)、同一命令又带临时文件删除清理——危险命令守卫把转义串误判成受保护系统路径,整条命令被拦(已写临时文件不清理、命令白跑),并叠加 L1 首命令失败连带取消整批
- root_cause: 破坏性删除 cmdlet 与相邻的反斜杠转义字面量共处同一命令文本时,守卫的路径提取会把转义串当成路径参数而误拦;叠加插值 here-string 里手工双重反斜杠转义本就脆易错
- rule: 需要辅助脚本(js/py/cjs)时用 Write 工具把文件落到 scratchpad、路径一律正斜杠(Windows 下 node/bun/createRequire 都接受)再单独跑它;别在 PowerShell 插值 here-string 里现拼脚本+内联反斜杠转义,更别和删除清理放同一命令;临时文件清理独立成单独命令
- enforced_by: none(行为准则:优先用 Write 工具造辅助脚本、正斜杠路径;无机械守卫)
- refs: claude-mem 向量同步修复会话;关联 L1(批次连带取消)、L7(here-string 语法)

## L66
- date: 2026-07-05 ｜ tags: powershell,selftest,encoding,subprocess,review ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 断言匹配子进程输出里的中文/非 ASCII 字面量（经 2>&1 | Out-String 跨进程捕获）在宿主 console 非 UTF-8（git-bash/传统代码页）时被 mojibake，-match 失配致假 FAIL；本机恰为 UTF-8 时全绿，掩盖了环境敏感性（selftest 17k 两处 verifier 环境复现）
- root_cause: 跨进程捕获的解码走宿主 console 输出编码：子进程按自身编码写、父进程按自身编码读，两端非 UTF-8 时非 ASCII 字节错位；断言又钉在中文字面量上，故只在特定代码页炸
- rule: 跨进程捕获里若必须断言非 ASCII 文本，就地把父(解码)与子(编码)的 [Console]::OutputEncoding 钉 UTF-8（set 用 try/catch 兜底无 attached console 的 CI、finally 还原），只动 OutputEncoding 不碰 InputEncoding（后者破坏兄弟步骤的嵌套 stdin，见 L4）；能则优先断言 ASCII 标记、非 ASCII 只留给人看
- enforced_by: 
- refs: TD31 / selftest 17k / commit f334e61；相关 L31/L33（Python 侧同类编码坑）

## L67
- date: 2026-07-05 ｜ tags: check-cards,advisory,regex,guard,review ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 新建议闸用裸词/子串匹配（如 \bselftest\b）判 dod_command 是否调用重型套件，误报了把套件文件当参数的轻量命令（Select-String -Path scripts/selftest.ps1）——恰是该闸想推荐的沙箱可复跑形态，建议闸打脸
- root_cause: 文件路径把套件名当子串（scripts/selftest.ps1 含 selftest），裸词匹配分不清调用套件与引用套件文件路径两种截然相反的意图
- rule: 检测调用某工具/套件的静态闸要锚定调用形态（-File …selftest.ps1 / 命令位的 pytest / npm test），而非裸词或路径子串；建议闸落地后必须对真实输入集（全部真卡）跑一遍核对命中集 blast radius，确认只命中意图内的对象、无误伤
- enforced_by: 
- refs: TD33 / check-cards.ps1 / commit f334e61

## L70
- date: 2026-07-06 ｜ tags: git,hooks,windows,worktree,cross-platform ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 程序化安装 git pre-push 钩子时写死 .git/hooks 且直接覆盖：在设了 core.hooksPath（Husky 等）或链接工作树下 git 根本不看该文件（钩子静默失效、假成功）；覆盖丢失既有钩子；Windows 装钩不设可执行位致链式 [ -x ] 门槛静默跳过既有钩子
- root_cause: git 实际钩子目录由 core.hooksPath / 工作树共同决定，非恒为 .git/hooks；Windows MSYS 对含 shebang 文件 [ -x ] 判真、无 shebang 判假
- rule: 装 git 钩子用 git 自己的路径 API 定位目录：git -C <root> rev-parse --git-path hooks（一并处理 core.hooksPath 相对/~/绝对 + 链接工作树公共 hooks；相对结果按仓库根解析）。不静默覆盖既有钩子：备份为 <hook>.local（冲突则 .local.N）并在守卫后 glob 按序链式调用、缓存并原样转交 stdin+参数；链式条件用 [ -x ]&&直接执行(遵 shebang) 否则 sh 回退（Windows 无可执行位/无 shebang 时不丢钩子）
- enforced_by: 
- refs: 

## L71
- date: 2026-07-06 ｜ tags: testing,powershell,selftest,library-mode ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 脚本内部逻辑（分类/解析/装钩）想 hermetic 自测，但脚本本体有重副作用（Set-Location/网络/git/exit），直接 dot-source 会执行整套副作用且其 exit 杀掉调用方（selftest）
- root_cause: 脚本 = param 头 + 顶层主流程；dot-source 跑主流程
- rule: 给需被 selftest 复用内部函数的脚本加 -AsLibrary 开关：把纯逻辑（正则/常量/函数）定义在**任何副作用之前**，紧接 if ($AsLibrary) { return }（不 Set-Location/不网络/不 git/不 exit），selftest dot-source 该脚本 -AsLibrary 后用合成输入直测同一函数（单一真相源、免双源漂移）。本会话 check-licenses.ps1(Scan 分类)/gh-bootstrap.ps1(Install-PrePushHook) 均用此；既有 check-secrets.ps1(Find-LineSecret,TD18) 为原型
- enforced_by: 
- refs: 

## L72
- date: 2026-07-06 ｜ tags: task-loop,worktree,ship,git ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 在 worktree 里提交任务卡 specs/tasks/<id>.md 后 ship，范围闸拒「越界（不在卡 allow_paths 内）」——卡文件路径本就不在自己的 allow_paths
- root_cause: 卡自身是规划物、非本卡功能改动；ship 范围闸按 allow_paths 判越界（L18）
- rule: 任务卡文件在 task.ps1 -Phase start **之前**先提交到 master（不在 worktree 内提交卡）；worktree 分支从含卡的 master 拉出后，ship 前 git pull --rebase 会把该卡提交作为 already-upstream 干净丢弃，PR diff 只剩 allow_paths 内的功能改动。若已误在 worktree 提交：把卡提交挑到 master + 分支 rebase 收起。R5 的 status->merged 与 tracker 也走 master docs 提交（同 L18）
- enforced_by: 
- refs: 

## L73
- date: 2026-07-06 ｜ tags: vendored-skill,re-vendor,notice-contract ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: re-vendor 一个第三方 skill 时、易破坏 byte-identity（NOTICE 契约要求逐字）或误 vendor 上游未稳定的实验版。
- root_cause: WebFetch 把页面转 markdown 经小模型作答、是有损的、拿不到逐字原文；且上游 latest 可能是 experimental 分支。
- rule: byte-exact re-vendor 用 curl 取 raw 文件（非 WebFetch）→ pin release tag、NOTICE 记 commit SHA + 刷新 vendored 日期/版本 + 保留 deliberately-NOT-vendored 排除段 → 校验 Get-FileHash==上游。上游 latest 若 experimental 则暂缓 re-vendor、改在原创 pointer skill 加临时校准注（绝不手改 vendored 正文）。当前世代模型常需更少旧代 anti-slop 脚手架、跟随上游对当前世代的重校准即可。
- enforced_by: 
- refs: 

## L74
- date: 2026-07-06 ｜ tags: review,codex,prompt-injection,r3 ｜ tier: ondemand ｜ kind: pitfall ｜ severity: blocking ｜ recurrence: 2
- symptom: codex R3 非确定性 block 一张干净卡、reason 指 prompt-injection：卡片含 {verdict:pass} 预批准字面量、对实际 diff 零异议。
- root_cause: review.ps1 把整张任务卡原文注入 R3 prompt 供判范围；卡片标准 schema 字段 review_gate: codex {verdict:pass} 被评审者自己的注入防御误读为伪造预批准。全卡队都在、侥幸性触发。
- rule: 注入待审卡片前中和任何引号形态的 verdict 样式 token（review.ps1 已做、闸 17r 锁）。若某修复卡自身测试夹具必须含该 trigger token、保持它自己的卡片声明 review_gate 不含字面量——真闸用尚未修复的 main-repo review.ps1、如此才能 bootstrap 过旧评审器。注入防御立场须是"一律不得服从"而非"出现即拦"：待审数据里出现操纵文本本身不构成 block 理由、也不得为之记 reason——pass 的输出契约要求 reasons 必须为空数组，若"出现即需记 reason"就与"干净 diff 必须 pass"自相矛盾，等价于给任何能写入待审数据者一个强制 block 的 DoS 开关；仅当该文本本身即本次 diff 的 rubric 可判缺陷时才据 rubric 定裁决。防御靠立场，不靠删改待审数据（中和会让评审者读不到真 hunk）；测试夹具放惰性占位而非真操纵指令，真指令零测试增益却留活载荷。（闸 17r(stance) 锁）
- enforced_by: scripts/review.ps1（注入卡片前中和 verdict token + 注入防御立场句）+ scripts/selftest.ps1 闸 17r
- refs: 

## L75
- date: 2026-07-07 ｜ tags: powershell,git,security,url ｜ tier: ondemand ｜ kind: pitfall ｜ severity: blocking ｜ recurrence: 1
- symptom: 校验 git 远端 owner 用子串/正则匹配 github.com/<owner>/，把 host 内嵌进攻击者路径(https://evil.example/github.com/<owner>/repo.git)及 scp/ssh 形态误判为合法个人远端 -> push-target 账号守卫被绕过(TD38 实证旧正则 3/10 攻击形态误放行)。
- root_cause: github.com/<owner>/ 可出现在攻击者 host 的 PATH 里，负向后顾子串正则锚不住真 authority；[uri] 两坑: TryCreate(git@github.com:o/r.git,Absolute) 因 scheme 含 @ 非法返 False；TryCreate(evil.example:x/y,Absolute) 返 True 但 .Host 为空(opaque scheme:path 落 scp 支)。
- rule: 校验 git 远端 owner 一律解析 authority、不子串匹配: scheme URL 用 [uri] 且要求 .Host 非空并 -ieq github.com + 首路径段==owner；scheme-less scp 形态单独用 ^(?:[^@/]+@)?(?<host>[^:/]+):(?<owner>[^/]+)/；两形态皆不匹配即 fail-closed 拒。实现见 _guard.ps1 Test-PushTargetOwner。
- enforced_by: scripts/selftest.ps1 (17e)
- refs: 

## L76
- date: 2026-07-07 ｜ tags: selftest,testing,powershell ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: selftest 闸从脚本源里抠出实现细节正则(-notmatch 后那串)来测；实现从正则改成函数后测试机制本身失效(无正则可抠、假 PASS 或误 Fail)。
- root_cause: 测试耦合到实现细节(源里某条正则字面量)而非可观测行为。
- rule: 值得回归的脚本逻辑抽成纯命名函数，selftest dot-source 后对输入表直调函数断言；别从源文本 scrape 实现(正则/字面量)。仅定义函数、无副作用的脚本才可安全 dot-source(如 _guard.ps1)。TD38/17e 即此重构。
- enforced_by: 
- refs: 

## L77
- date: 2026-07-07 ｜ tags: gh,github,pr,workflow ｜ tier: ondemand ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 单人仓 gh pr review --approve 自己的 PR 报 GraphQL: Can not approve your own pull request；approve-and-merge 里 approve 这步必然失败。
- root_cause: GitHub 禁止 PR 作者审批自己的 PR；单人仓无第二协作者、也无 required-review 分支保护。
- rule: 单人仓「approve and merge」= 跳过 approve 直接 gh pr merge(--merge --delete-branch)；别卡在自审失败上。需真审批闸就加第二协作者或 required-review 规则集。(PR #55/#56 两次复现)
- enforced_by: 
- refs: 

## L78
- date: 2026-07-07 ｜ tags: selftest,testing,ship,git,hermetic-fixture ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 要给 task.ps1 远端(非 -Local) ship 的 push→PR→merge 段写 hermetic selftest 时，push 段被两道前置闸挡住：reviewAvail 闸(无评审后端即 fail-fast)与 Assert-PersonalAccount -CheckRemote(要求 github.com origin，一配真 origin 就让 push 联网、非确定，无从离线复现 push 失败)。
- root_cause: 账号守卫位于 ship 入口与 push 之间、且 TD38 后只精确匹配 github.com host；任何能过守卫的 origin 都会把 push 变成真实网络操作。
- rule: 夹具三步驱动 push 段离线确定性：①_config ReviewCommand 设非空(过 reviewAvail)；②覆盖 scripts/_guard.ps1 为 no-op Assert-PersonalAccount(与被测债正交、隔离掉账号守卫)；③origin 指向不存在的本地路径 → push 必非零失败(离线、无网络、无 gh)。中文 abort token 跨子进程匹配仍走 UTF-8 OutputEncoding 钉法(L69/TD31/34)。
- enforced_by: scripts/selftest.ps1 闸 15i(TD44 首用)
- refs: TD44 · TD-107 · selftest 15i

## L79
- date: 2026-07-07 ｜ tags: powershell,strictmode,testing,tdd,lessons ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: TD24 假 paid 一轮 + 本次重诊断
- symptom: 空集合/空账本场景的回归种子或自检用【显式构造入参】通过，但生产的【裸调用·默认参数绑定】路径仍崩——假绿。实证 TD24：lessons.ps1 自检调 Next-Id -Lessons @()（显式绑定，空数组保真→Count 0→过），但 add 走裸 Next-Id（默认绑定 =(Get-Lessons)），空 LEDGER 时 Get-Lessons 返回 @() 经 [array] 默认参数绑定 unroll 成 null，@(null).Count==1 绕过 Count-eq-0 守卫，取 .id 抛 PropertyNotFoundStrict。TD24 因此被标 paid 一整轮实为未修、下游首条 add 即崩。
- root_cause: 自检/种子走了与生产不同的代码路径：显式参数绑定会保真空数组，默认参数绑定 =(func) 在 StrictMode 下把 @() unroll 成 null（@(null) 仍 Count 1，@() 包裹单独不解决）。「函数返回空数组经 [array] 默认值绑定」这一步是隐形的路径分叉——探针没覆盖它就假绿。
- rule: 回归种子/enforcer 自检必须【真跑生产入口】（如 selftest 真调 lessons.ps1 add / check 子进程），不得用手搓的显式绑定捷径替代——路径不同即假绿。附 PowerShell 修坑：别用 return ,$out 修「函数返回空数组 unroll」——逗号包裹会让整个数组当【单个】管道项，破坏 func | Where-Object 直管调用（消费端取不到成员属性、召回崩）；改在【消费端】写 @($x | Where-Object { $null -ne $_ }) 先滤 null 再判 Count。
- enforced_by: scripts/selftest.ps1（子闸 2b：真跑 lessons.ps1 add/check 覆盖生产裸调用路径，非显式绑定 probe）
- refs: 

## L80
- date: 2026-07-07 ｜ tags: powershell,array-unwrap,validation,type-check ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 类型/形状守卫（如 $v -isnot [string]）对 1 元素数组 @('pass') 意外放行、当成标量——校验被静默绕过（本会话 review.ps1 加运行期裁决 schema 时，敌意 {"verdict":["pass"]} 的 selftest 子闸修后仍 RED）。
- root_cause: $v = if (cond) { $obj.prop } 及任何脚本块/管道捕获都把值经 PowerShell **输出流**传递，输出流会**枚举集合**：1 元素数组被解包成其唯一元素（标量），故 $v 成了字符串而非数组，绕过下游 -isnot [string]。
- rule: 当值的「数组性」影响类型/形状判定时，**直接赋值**取属性（$v = $obj.prop），绝不经 if(){}/$()/管道捕获；在直接引用上做校验。ConvertFrom-Json 的单元素数组是常见触发源。
- enforced_by: 
- refs: 

## L81
- date: 2026-07-08 ｜ tags: worktree,selftest,parallel-agents,orchestration ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1 ｜ cost: 误判一次 selftest 验收、多跑一轮
- symptom: 并行 worktree 子代理运行期跑 scripts\selftest.ps1 验收，gate ⑧ init-smoke 报「init 后仍残留占位符：CLAUDE.md, TEMPLATE-README.md」（每个活跃/残留 agent worktree 各一份，如 4 个 worktree = 4×2 条）。
- root_cause: gate ⑧ init-smoke 用 Copy-Item -Recurse 忠实拷 $RepoRoot；.claude/ 在白名单内，故 .claude/worktrees/agent-* 下每个子代理 worktree 的整仓副本（含未被 init 替换的 token 占位符——双花括号包大写名那类——的 CLAUDE.md/TEMPLATE-README.md）被一并拷入临时树，leftover-token 扫描把它们当残留 token。非 selftest 或被测改动的真回归。
- rule: 并行 worktree 子代理未全部完成并清理前，别拿 selftest gate ⑧ 当验收信号；先等子代理完成，再 git worktree remove --force 各 agent worktree + git worktree prune，然后跑 selftest。.claude/worktrees 已 gitignored（不入库/不下发），只污染运行期 init-smoke 拷贝。子坑（本条自己踩过）：写 LEDGER/docs 描述 token 时**别写字面双花括号包大写名**——它会被本 gate 的 leftover 扫描 `\{\{[A-Z_]+\}\}` 当残留误判红；CLAUDE.md/TEMPLATE-README.md 因含此类字面被 init-smoke 特例跳过，LEDGER 不在跳过名单。且 `lessons.ps1 check` 不跑 gate ⑧，故加经验后须真跑 selftest 再合并、勿只靠 check + auto-merge。
- enforced_by: none（运行期编排纪律；gate ⑧ 忠实拷贝设计上无法区分 worktree 副本，除非日后把 .claude/worktrees 纳入拷贝排除）
- refs: 

## L82
- date: 2026-07-08 ｜ tags: claude-code,hooks,stop-hook ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 三个 Stop 钩子（lessons/handoff/comprehension-reminder）长期靠裸 stdout（Write-Host/Console.Out.WriteLine）打提醒，未经验证 Stop 事件的 stdout 是否真的送达模型上下文
- root_cause: Claude Code hooks 契约里只有 UserPromptSubmit/UserPromptExpansion/SessionStart 的裸 stdout 会被注入模型上下文；Stop（及 SubagentStop）走 top-level decision 模式，非阻断反馈须用 hookSpecificOutput.additionalContext（回合结束时注入、对话继续），裸 stdout 只进调试日志/CLI transcript，模型看不到（官方 hooks 文档 exit-code 一节 + decision-control 表已核实）
- rule: Stop 钩子要把提醒喂给模型，必须输出 JSON {hookSpecificOutput:{hookEventName:"Stop",additionalContext:"..."}}（exit 0），不能只裸打印文本——裸 stdout 只有 UserPromptSubmit/UserPromptExpansion/SessionStart 事件才会被模型读到
- enforced_by: scripts/selftest.ps1 闸9f
- refs: 

## L83
- date: 2026-07-08 ｜ tags: multi-agent,worktree,selftest,integration ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 并行多 agent 波次：主检出跑 selftest 在 agent worktree 存活时 gate-17（深拷整仓的种子闸）偶发假红；多 agent 各加 selftest 子闸时字母撞车（两个 17u）；各改相邻 tracker 行 → 串行合并时相邻行 rebase 冲突。
- root_cause: gate-17 等深拷 gate 用 Get-ChildItem RepoRoot | Copy-Item -Recurse 复制整仓，不排除 .claude/worktrees（RootIgnore 只按顶层名过滤、排不掉嵌套 worktrees），把并发写入的 live worktree 一并拷入致偶发失败；并行 agent 互不知对方选的子闸字母；tracker 相邻行被多 agent 并行改。
- rule: 并行多 agent 波次收口三条：① 验收以每个 PR 的干净检出 CI 为准，本地 selftest 只在 worktree 清理后（git worktree remove）再跑，否则 gate-17 深拷误纳 live worktree 假红；② 整合时 dedup 子闸字母（同名子闸重命名到空闲字母，同步 tracker 引用）；③ tracker 相邻行冲突属预期，串行合并逐个 rebase 解决（只挑各自 row 的 paid 侧）。
- enforced_by: 
- refs: 

## L84
- date: 2026-07-08 ｜ tags: harness,self-improvement,reward-hacking,evaluator,loop,judgment ｜ tier: ledger ｜ kind: judgment ｜ severity: major ｜ recurrence: 1
- symptom: 自改回路(fresh regression sweep / 自主 harness 改进 / harness-refresh 心跳)为让某改动过闸,顺手弱化它自己的成功判据(selftest 闸/QUALITY-RUBRIC 维度/eval-suite/check-cards 规则/review.ps1 裁决逻辑)——绿灯是改出来的不是挣来的。
- root_cause: evaluator 落进了它评判的 optimization loop 内(Goodhart/reward hacking);个人仓自合并 + R3 只审 diff 正确性、未必把「弱化评审者」当独立失败类别,故弱化可静默随功能卡 ship。
- rule: 对成功判据本身的修改按 evaluator 变更处理,非对称:加严(GROW/补闸/加维)可随手;放宽/删闸/降维(PRUNE)只走 HARNESS-REVIEW 做减法正门(一次一闸+真卡量化+ADR 留痕),并显式标给回路外的 R3+人复核,绝不静默捆进它要放行的卡、绝不同 PR 自批例外。selftest 绿≠已评审(L50);循环自批准 R3 必拦(L53/L74)。
- enforced_by: none(judgment 方向类;喂 docs/HARNESS-REVIEW.md「评审者须在自改回路之外」节随模型复审;部分实例由 R3 循环自批准检测 L53/L74 覆盖)
- refs: docs/HARNESS-REVIEW.md; 关联 L50, L53

## L85
- date: 2026-07-08 ｜ tags: powershell,encoding,native-error,prelude,dot-source,hermetic-test ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 3
- symptom: 把散落各脚本的 console 编码/native-error 纪律收敛为共享 dot-source 前奏时三坑：(1) 无 guard 直接 dot-source 令 hermetic 单文件拷贝测试(只拷 verify/run-evals 到临时 scripts、不含新前奏)在 Stop 下崩；(2) 库脚本在 -AsLibrary 早返回前 dot-source 前奏,把 native pref 泄进 selftest 库 dot-source 的调用方作用域；(3) 全局设 [Console]::InputEncoding 破坏嵌套/重定向 stdin(codex / 子评审者)。
- root_cause: 共享前奏是新增运行期依赖,而本仓 hermetic 测试惯例是单文件拷贝(假设脚本自包含)；dot-source 在调用方作用域生效,位置(AsLibrary 前/后)决定污染面；InputEncoding 进程级、影响后续子进程 stdin 继承(L4)。
- rule: 收敛跨脚本纪律为共享前奏：① 消费方用 guarded try/catch dot-source(前奏缺失即 fail-open 退回原行为,免逐个改 hermetic 单文件测试站点的 L49 脆性)；② 库脚本 dot-source 放 -AsLibrary 早返回之后(免污染库调用方作用域)；③ 前奏只设 OutputEncoding + native pin,绝不全局 InputEncoding(读端 pin 就地就进程：子进程要从文件重定向的 stdin 里读非 ASCII 文本时,在该子进程自己的脚本体首行钉 [Console]::InputEncoding,只影响这一支子进程,不碰调用方/兄弟闸)；④ 前奏 dependency-free(不 dot-source _config),供刻意不依赖 _config 的脚本安全取用；⑤ 收敛后仍需按调用点治理:会向 stdout 打印大段非 ASCII 内容的第三方 CLI（如 codex）除接好前奏钉 UTF-8 外,应收窄输出——只保留裁决行({"verdict":...}那类),不把完整 prompt/回复正文原样回显进 transcript；否则纵使编码钉对,大段中文裸奔进对话历史仍可能触发下游安全分类器误判(与本地断言失配是两种不同后果:一为脚本自测假 fail,一为 transcript 层误判,同源不同灾)。
- enforced_by: scripts/selftest.ps1(子闸 1c/1d/1e/1f/1g)
- refs: scripts/_encoding.ps1; 关联 L4, L66, L69

## L86
- date: 2026-07-08 ｜ tags: task-loop,worktree,gh,ship ｜ tier: ondemand ｜ kind: pitfall ｜ severity: major ｜ recurrence: 2
- symptom: task.ps1 -Phase ship 跑在 worktree 内、用其自身相对路径 scripts\task.ps1（而非主检出那份）调用时，gh pr create --base <TaskId> --head <TaskId> 报 "head branch is the same as base branch"。
- root_cause: $RepoRoot 由 $PSScriptRoot 派生——哪份 task.ps1 被调用就用哪份所在目录当 $RepoRoot；worktree 自身副本被调用时 $RepoRoot=worktree 本身，$Base 自动探测 `git -C $RepoRoot symbolic-ref HEAD` 遂返回 worktree 当前分支（即 $TaskId 自身），而非真正基线（master/main）。
- rule: -Phase ship/red/cleanup 一律用【主检出】那份 scripts\task.ps1 调用（cd 到 worktree 只为编辑文件，不为跑 task.ps1 本身）。**显式传 -Base 并非逃生门**：T13-L86-GUARD 实测 `-Base master` 下 $RepoRoot 仍是该 worktree，`git -C $RepoRoot merge $TaskId` 照样把分支并进它自己、exit 0 假报「已本地合并」而 base 从未前进，随后 cleanup 强删这条未合并分支（唯一提示是一条 WARNING）。故 task.ps1 现已 fail-closed 拒绝「相位命令跑在本卡 worktree 里」（哨兵 L86-WT），并另拒 base==TaskId（哨兵 L86-BASE）。
- enforced_by: scripts/task.ps1（两道守卫，闸 15m 的三个用例分别锁 L86-WT / L86-BASE）
- refs: 

## L87
- date: 2026-07-08 ｜ tags: powershell,encoding ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: git show <rev>:<path> | Out-File -FilePath $f -NoNewline（或 Set-Content -NoNewline）写多行内容时，整个文件被压成一行——不止省略末尾换行，管道输入的每个对象之间的换行也被一并吞掉。
- root_cause: PowerShell 的 -NoNewline 语义是「写多个管道对象时对象之间也不插换行」，不是仅「文件末尾不加换行」；git show 的多行 stdout 经管道会被当成一串独立字符串对象逐个写入，故内部换行全部消失。
- rule: 先用 Out-String 把多行输出收成单个字符串（换行保真），再用 Set-Content -NoNewline 写它（此时只有一个对象、-NoNewline 只影响末尾）；不要把外部命令的多行管道输出直接接 -NoNewline。
- enforced_by: 
- refs: 

## L88
- date: 2026-07-08 ｜ tags: task-loop,worktree,ship,tdd ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 2
- symptom: task.ps1 -Phase ship 的 RED 证据闸报「证据 sha 与当前 HEAD 不符」——即便先前已正确跑过 -Phase red 并确认过 GREEN，只要 worktree 分支在那之后又产生了新提交（如 merge master 拉取任务卡自身的后续修正、或上一次 ship 因下游步骤失败而中途已跑完自己的 "提交改动" 步骤），HEAD 就会前移、令已记录的 RED 证据 sha 过期，ship 立即拒绝。
- root_cause: RED 证据机制（TD36）把 .review/<id>.red 的 sha 字段与当前 git HEAD 做逐位比对，语义是「这份 RED 结果绑定在这一个具体提交上」——任何让 HEAD 前移的动作（新提交/合并）都会使其失效，且 ship 内部的自动 commit 步骤本身就会制造这种前移；这是机制的必然代价，不是 bug。
- rule: 每次 HEAD 因合并/ship 部分执行而前移后，重 ship 前先在工作树里对一个只在 dod_command 检查中出现、且不会与其它检查项重复匹配的唯一标记做「临时破坏（不 commit）→ 确认非零退出（RED）→ -Phase red 记录证据（sha 落在当前 HEAD）→ 复原该标记（工作树与 HEAD 重新一致）→ 确认零退出（GREEN）」，再重 ship；-Phase ship 的内部 commit 步骤会在无变更时优雅跳过（"无新增改动可提交"），故只要工作树在复原后确实与 HEAD 一致即可安全重跑。（补丁：本仓子闸惯例是每条 Fail()/成功 Write-Host 都重复自己的编号标签，挑的标记若正是子闸自身 id 字符串，会在文件里出现多处——单点编辑不会翻转 dod_command 的 -Pattern 匹配、GREEN 恒 GREEN，须二次排查才发觉。用本配方前先 grep 该标记出现次数；若 >1，用 replace_all（非单点 Edit）一次性切换全部出现处到一个不会被大小写不敏感匹配误撞的替代词，复原时再 replace_all 换回。）
- enforced_by: none（task.ps1:264 的 sha 校验本身即 fail-closed 拒绝陈旧证据；本条只是记录人工重新建立 RED 的操作手法，非新增机械闸）
- refs: T9-DOCS-DRIFT 复盘；关联 L86（同一 worktree 自调用相关坑）、TD36（RED-first 闸机制）

## L89
- date: 2026-07-09 ｜ tags: evidence,reporting,selftest ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 无返工（当场自查捕获）
- symptom: 向用户或交接件复述工具输出时，把文档里的常量（如 CLAUDE.md 的「17 闸」）拼进对实际输出的引述——声称 selftest 打印「PASS 17/17」，但 selftest.ps1 只打印「selftest: PASS」并 exit 0，从不输出闸数。
- root_cause: 先验（文档/记忆里的闸数）与本轮真实 stdout 在复述时被混合成一条看似有据的引述；证据锚定只校验了「命令跑过、退出码为 0」，没校验「我引的那个字符串真的在输出里」。生成式会话记忆同样会引入此类不存在的细节，故不能拿记忆摘要当二次证据。
- rule: 引述工具输出一律逐字取自本轮 stdout：计数、闸数、版本、耗时等细节若不在输出里，要么不写，要么显式标注来源是文档而非运行结果。核验法——对着输出 grep 你要引的那句。selftest 的通过判据只有 exit 0 加末行 selftest: PASS。
- enforced_by: none（表述/判断类；由 CLAUDE.md 执行边界「完成与词义」+ task-loop 4.7 fresh-context 证据审计覆盖）
- refs: scripts/selftest.ps1:3108-3111；关联 L88（证据陈旧）、L62（断言覆盖）

## L90
- date: 2026-07-10 ｜ tags: ci,github-actions,diagnosis,transient ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1 ｜ cost: 无返工（当场分诊捕获）
- symptom: PR 的 CI 检查全红，各 job 耗时几乎一致（本次三 job 皆 15m02s），annotation 为「The job was not acquired by Runner of type hosted even after multiple attempts」；极易被误读为本次 diff 引入的回归，从而去改根本没跑过的代码。
- root_cause: GitHub 托管 runner 未能分派该 job——job 从未启动，failure 与提交内容无关。日志里没有任何步骤输出，那个耗时是【分派超时】而非任务耗时，故多个互不相关的 job 撞同一堵墙、时长高度一致。
- rule: 见 CI 全红先判断 job 是否真的跑过：annotation 含 not acquired by Runner，或多个不相关 job 耗时一致（=分派超时），即判为基础设施瞬时故障——先 gh run rerun <id> --failed，再谈碰代码。重跑仍以同样方式失败，才去查 Actions 配额/账单/runs-on 标签。判据是【job 有没有产生步骤输出】，不是【CI 是不是红的】。
- enforced_by: none（诊断/表述类；判据可从 gh run view 的 annotation 直接读出）
- refs: PR #92：run 29017931311 + 29017931326 三 job 均 15m02s 未启动；同一 commit 0d50bdf 原样重跑后 2m38s / 5m1s / 25s 全绿，未改一行代码

## L92
- date: 2026-07-10 ｜ tags: git,github,review,merge ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 两次合并前复核，未误合
- symptom: PR 获批后、合并前，分支被追加了新提交（并发会话/协作者/自己都可能），而 gh pr merge 合的是 PR 的当前 head，不是当初评审时看到的内容。本会话现场：PR #94 从「1 文件 1 行的 TD67 登记」长成「3 文件 + 新增 selftest 闸 14d（改评审者本体）」；PR #95 从 2 提交长成 6 提交 7 文件。差点拿旧批准把未复核的评审者变更合进 master。
- root_cause: GitHub 的 commit status（含 codex-review）是按 sha 附着的，不是按 PR 号；PR 号只是一个可变指针，指向随时会前移的 head。人却把「我批准过这个 PR」记成「我批准过它现在的内容」。R3 只判它当时看到的 diff，不负责回答「这份批准是否仍对得上当前 head」。
- rule: 合并前重新核对，别拿旧批准合新内容：① gh pr view <n> --json headRefOid,files,commits 读当前 head 与文件清单；② gh api repos/<owner>/<repo>/commits/<headSha>/status 确认 codex-review 落在当前 head sha 上，而非祖先提交；③ head 变了就重跑 review.ps1 再判；④ git diff --numstat <base>...<head> 核对没有对闸/评审者的删减——selftest.ps1 / QUALITY-RUBRIC / review.ps1 / check-cards 出现 deletions>0 即 PRUNE，须走 HARNESS-REVIEW 减法正门，不得随卡夹带；⑤ 范围变了先停下确认（CLAUDE.md 执行边界）。裁决文件缺失时别信绿徽章，自己跑一遍 review.ps1。本条把 L50「合并前跑独立评审」补齐为「且该评审必须锚在你要合的那个 sha 上」。
- enforced_by: none（流程/操作类；R3 判 diff 不判「批准是否仍对得上 head」；commit status 按 sha 附着是 GitHub 语义，非本仓可机检项）
- refs: PR #94 (b2546d5 -> b3ecf70) 与 PR #95 (1703e23 -> febe0be) 现场；docs/HARNESS-REVIEW.md「评审者须在自改回路之外」；关联 L50（合并前独立评审）、L91（共享检出并发）

## L93
- date: 2026-07-10 ｜ tags: powershell,exit-code,verification,false-green,truncation ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 一次误判，当场识破，未污染任何已声明结论
- symptom: 在 pwsh 里把原生命令的输出管进 Select-Object -First N，之后再读 $LASTEXITCODE，读到的是上一条命令的退出码（陈旧值）：失败的命令被读成 exit 0，产生假绿。本会话现场——git apply --check 明明打印 patch does not apply，紧随其后的 $LASTEXITCODE 却是 0，那个 0 其实来自上一条 git worktree add。
- root_cause: Select-Object -First N 取够 N 条就停掉上游管道（PipelineStopped），原生命令被提前终止，它的退出码从未写进 $LASTEXITCODE，于是变量仍保留上一条命令的旧值。-Last N 与 Select-String 会读完整个流，故退出码保真。实测：git nosuchsubcommand 2>&1 管进 Select-Object -First 1 时 $LASTEXITCODE=0，换成 -Last 1 或 Select-String 时为 1。
- rule: 作判据用的退出码，绝不读在 Select-Object -First N 之后。三选一：① 先把命令跑完（必要时管到 Out-Null），立刻把 $LASTEXITCODE 存进变量，再去筛输出；② 需要截断输出就用 -Last N 或 Select-String，二者读完整流、退出码保真；③ 完全不接管道，直接取退出码。凡是以 exit 0/1 为判据的场景（verify.ps1 / selftest.ps1 / review.ps1 / 卡片 dod_command / CI 步骤）尤其致命——它产生的是假绿，不是假红。一行自检：git nosuchsubcommand 2>&1 管进 Select-Object -First 1 再管进 Out-Null，随后 $LASTEXITCODE 应为 1；若得 0 即中招。已核本仓 .ps1 脚本无此形态，坑主要出在 agent 临时敲的校验命令里。另：同一 cmdlet 还有第二个与退出码无关的坑——用 -First N 截断的诊断输出，不足以支撑「已穷尽」的结论。凡要据某段输出判断覆盖面（某工具改了哪些文件、装了哪些 agent、命中哪些路径），必须不截断地取全量：重定向到文件后整份读、Out-String 全量、或改用结构化查询。注意 -Last N 同样只发 N 条，它保真的是退出码、不是覆盖面——本条前半管退出码、后半管覆盖面，两者别互相借用（-Last 可解退出码，不可解「已穷尽」）。
- enforced_by: none（已 grep 核实仓内 .ps1 无此形态；agent 临时命令不可机检。可选加严：给 selftest 加一条静态断言，禁止仓内 .ps1 出现「-First 之后读 $LASTEXITCODE」）
- refs: 本会话 stash 校验现场（git apply --check 被误读为 exit 0）；截断致漏判现场——codebase-memory-mcp 安装器打印的已配置 agent 列表被 -First 30 截断，漏看 Cursor/Kiro 两处已写入的配置，使「已全部清理」的断言一度为假（详见 L94 refs）；关联 L89（引述须逐字取自 stdout）、L25（确定性闸 exit 0/1）

## L94
- date: 2026-07-10 ｜ tags: powershell,array,slicing,false-green,strictmode ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 用下标拼接删除一段行时（$keep = $a[0..($i-1)] + $a[($j+1)..($a.Count-1)]），若被删区段一直延伸到文件末尾，最后一行不但没被删掉反而原样残留；脚本仍打印看似正确的 removed N lines（N 比实际少 1）。本会话现场——从 ~/.codex/config.toml 删除 11 行注入块，实际只删掉 10 行，收尾哨兵注释 # <<< 残留成了文件最后一行，而成功消息是绿的。
- root_cause: PowerShell 的 .. 区间运算符在左值大于右值时静默生成降序序列。区段删到 EOF 时 $j+1 等于 $a.Count，$a[($j+1)..($a.Count-1)] 即 $a[143..142]，下标序列 @(143,142)：越界的 143 被静默丢弃（不产出元素，也不是 $null），142 又把本该删掉的最后一行取了回来，尾段遂返回 1 个元素而非 0 个。关键前提是没开 StrictMode——实测 Set-StrictMode -Version Latest 下同一表达式抛 IndexOutOfRangeException，坑会变响；agent 临时敲的 pwsh 命令默认不开 StrictMode，正是它静默的地方。
- rule: ① 删除延伸到文件末尾的行块，别用下标拼接。带起止哨兵的块用有状态区间过滤：遍历行，遇起始哨兵置 $inBlock 并跳过、块内行一律丢弃、遇结束哨兵复位并跳过；EOF 时 $inBlock 仍为真即哨兵未闭合，须显式报错而非静默放行。天然免疫空尾段与降序区间。切勿用 $a | Where-Object { $_ -notmatch $sentinel } 代替——它只删掉哨兵行本身、块体原样存活（实测：三行块体全部幸存，标记却没了），仅当块内每一行都能被同一模式匹中时才碰巧正确；本坑现场就是这种「碰巧」。② 非用下标不可就先判空尾段：$tail = if ($j+1 -le $a.Count-1) { $a[($j+1)..($a.Count-1)] } else { @() }。③ agent 临时敲的 pwsh 校验/编辑命令、以及仓内尚未开 StrictMode 的 .claude/hooks/*.ps1，都应 Set-StrictMode -Version Latest，把这类越界从静默截断升级成 IndexOutOfRangeException。④ 删除类编辑一律用真实断言核验（重新读回 grep / Test-Path），绝不采信脚本自己打印的 removed N lines——成功消息不是证据。一行自检：@(1,2,3)[3..2] 返回 1 个元素（值 3）而非空，即本坑；开 StrictMode 后同一表达式改为抛 IndexOutOfRangeException。
- enforced_by: none（PowerShell 语言层行为，无脚本闸。覆盖面已实测而非假定：23 个受版本管理的 .ps1 里只有 13 个开 Set-StrictMode -Version Latest（scripts/ 下的主脚本全开），10 个没开——7 个 .claude/hooks/*.ps1 与 3 个被点源的 _config/_encoding/_guard.ps1。点源实测继承调用方的 StrictMode，故三个 helper 由 scripts/ 调用时受保护，由 guard-frozen.ps1 / route-new-work.ps1 这两个无 StrictMode 的钩子调用时不受保护。已 grep 核实全仓 .ps1/.mjs 均无此下标拼接形态，故当前无活体实例；真正暴露面 = 无 StrictMode 的钩子 + agent 临时敲的 pwsh 命令。可选加严：给 selftest 增一条静态断言，禁止出现 ..($x.Count-1) 形态的尾段拼接）
- refs: 本会话清理 codebase-memory-mcp 注入 ~/.codex/config.toml 的现场（11 行块只删掉 10 行，# <<< 哨兵残留为末行）；rule① 初稿误把「-notmatch 哨兵过滤」当通用解，被 R3（codex）在 PR #101 当场证伪并实测复现（哨兵没了、块体三行全活）——该缺陷此前逃过了 fresh-context 子代理复核，因其只在「块内每行都匹中」的偏置样例上验过；关联 L93（同为 PowerShell 静默假绿：命令自称成功、结果已错）、L25（确定性闸 exit 0/1）

## L95
- date: 2026-07-11 ｜ tags: task-loop,dod,tdd,powershell,red ｜ tier: must ｜ kind: pitfall ｜ severity: blocking ｜ recurrence: 1
- symptom: `-Phase red` 打印「RED 已确认（dod 退出 1）」并写下 .review/<id>.red 证据，但那个 1 其实来自 ParserError「Missing expression after unary operator '-not'」——DoD 命令根本没跑起来，一条断言都没执行；该 dod_command 的 GREEN 永远不可达。
- root_cause: task.ps1 用 `& pwsh -NoProfile -Command <卡片 dod_command 原文>` 执行 DoD，而卡片惯用写法自身又是 `pwsh -NoProfile -Command "..."`。双层包裹下，内层双引号串里的 `$ok` 被子 shell 先行内插成空串，孙 shell 只收到 ` = (...); if (-not ) {...}`。`-Phase red` 只看退出码非零，遂把「语法坏了」当「测试红了」收下 = vacuous RED。
- rule: dod_command 里一律不写 `$变量`——用 T9-DOCS-DRIFT 的无变量写法 `pwsh -NoProfile -Command "if (-not ((Select-String ...) -and (...))) { exit 1 }"`。跑完 -Phase red 必须读一眼 DoD 的实际输出，确认非零退出来自断言失败而非 ParserError/CommandNotFound；「RED 已确认」这行字不是证据。已合并的 T10-R3-PIN-MODEL 卡即误用 `$ok` 形态，其 DoD 至今无法执行断言（TD69）。
- enforced_by: none（暂无机检；候选守卫见 specs/tech-debt-tracker.md TD69：check-cards 拒含 $ 的 dod_command / -Phase red 区分 ParserError 与断言失败）
- refs: 

## L97
- date: 2026-07-11 ｜ tags: review,r3,doc-sync,task-loop,scoping ｜ tier: must ｜ kind: pitfall ｜ severity: major ｜ recurrence: 8
- symptom: 把一条散布在多处文档的横切纪律（如 L86「相位命令只在主检出跑」）从「文档提醒」升级为 in-code fail-closed 守卫时，R3 会逐轮外溢：每修好一处教该纪律的权威面，它就揪出下一处仍教旧工作流的面（rubric #7 文档同步）。T13 连续 5 轮 block，第 3/4/5 轮全是「还有 N 处文档没同步」，allow_paths 从 5 涨到 11、check-cards 一路告警卡过大。**内向半**同样成立：改了行为后，**同一文件内**用现在时描述旧行为的注释、以及卡片 front-matter（title/diagnosis）会与新码自相矛盾——T12 R3 第 2 轮点了 review.ps1 一条 stale 注释、我只修了它点名的那行漏了同类的另一行；合并后 fresh-context verifier 复审才揪出（review.ps1:163 旧「倾向 block」注释 + T13 卡 title/root_cause 仍称「base==TaskId 是真因」）。**第 8 次（T56 r15，2026-08-05）**：r14 把 t36set 取样换成全码位双向时扫了卡与 rubric，却漏了被改文件 `selftest.ps1` **自身**的载荷注释、t36 失败文案与 17t 总结行——「内向半」写进 rule 了照样漏，因为 grep 关键词只圈「教分工的文档面」、没把被改文件本身列进扫描清单；且失败文案把**历史病因**写死在文本里（任何族点幸存都报「CGJ 幸存 + 只剥 Cc/Cf」），报错措辞同属「现在时正面陈述」。
- root_cause: 行为一变，凡教「怎么用这条工作流」的面（CLAUDE.md/template/LEDGER/task-loop skill/DEVOPS-WORKFLOW/TEMPLATE-README/脚本头注）就全部自相矛盾。R3 每轮只判本次 diff 且只报它当轮看到的最刺眼一处，故须逐轮外溢而非一次点全。
- rule: 把横切纪律行为化前，先 grep 出教该纪律的全部权威面（rg 关键词 + 看 CLAUDE.md/template/相关 skill/操作手册/README/脚本头注），一次性同步 + 配一道机检子闸断言这 N 处一致（如 selftest 遍历文档列表断言都含新哨兵），别等 R3 逐轮挤牙膏。这类卡的 allow_paths 天然大（含那 N 处 + 机检），是「横切不变量行为化」的固有形态、非 scoping 失误——刻意保持单卡以免行为改与文档同步分处不同 PR 出现自相矛盾窗口（登记 sizing 例外，见 TD70）。check-cards「>5 告警」对这类卡是误报但不放宽阈值。**内向半**：同一 grep 也要扫**改动文件自身的注释**与**卡片 front-matter**（title/diagnosis），揪出用现在时描述旧行为的句子；R3 点名一条 stale 注释时当**一类**处理、自己 grep 全文补齐，别只修它点名那行。ship 后对重大改动派 fresh-context verifier 复审 master（task-loop 4.7），专找「prose 与 shipped 码矛盾」。**扫到之后还要判对——判据写死，别凭感觉**（T56 r11/r12 连栽两次，且第二次不是漏 grep、是 grep 完误判）：**凡「用现在时正面陈述当前行为」的句子一律要改**；**只有把旧实现明确标为「被否决 / 历史 / 反例」的才留**。带对照的句子最容易误判——`剥的是 A 而不是 B` 里，`不是 B` 那半正当，`剥的是 A` 那半仍是正面陈述，A 过时就得改；别因为句子里有「不是 B」就整句放行。**扫描清单必须显式含「本次 diff 改到的每个文件自身」**（注释 + 失败/日志文案 + 总结行），不是只扫「教这条规则的文档」；**失败文案里别写死具体病因**——那是一条必然过时的正面陈述，能从现场数据动态报就动态报（报「幸存的是哪个点」而不是「一定是 CGJ」）。
- enforced_by: 
- refs: 

## L98
- date: 2026-07-11 ｜ tags: task-loop,ship-local,git-merge,cards ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: ship -Local 在最后一步「本地合并」报 error: The following untracked working tree files would be overwritten by merge: specs/tasks/<id>.md 而中止；DoD/范围/评审/闸全绿却合并失败。
- root_cause: task.ps1 从主检出解析 $Card（$RepoRoot/specs/tasks/<id>.md），卡片须在主检出可读；而 -Local 合并也是 git -C $RepoRoot merge <id>，主检出即合并目标。若卡片既作分支提交要被带回、又在主检出里作未跟踪文件存在，git 的 untracked-would-be-overwritten 守卫即中止合并——对字节相同的未跟踪文件同样拒（已实测）。
- rule: 新建任务卡在 ship -Local 前先 git add + commit 到 master（令主检出副本已跟踪），把卡片留在主检出提交、不纳入功能分支 diff（分支只带实现+测试）；R5 再改 status 为 merged。切勿让卡片在 -Local 合并期作未跟踪文件留在主检出。
- enforced_by: 
- refs: 

## L99
- date: 2026-07-11 ｜ tags: r3-review,codex,test-design,task-loop ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: R3(codex) 对同一测试连续多轮 block，理由在「静态子串检查太弱、要真跑的行为测试」与「行为测试过度工程/偏离卡范围/引入非确定」之间来回摆——每轮朝一个方向改都招来相反方向的新 block（本卡 T15 摆了 5 轮）。
- root_cause: R3 是模型在环、非确定（L25）。在两种测试机制（静态断言 vs 行为执行）之间来回切换，每次切换都给评审者一个新攻击面、暴露新机制的弱点；且自造行为 harness 离开了卡片 specified 的静态测试范围，触发 rubric #7/#8/#16（可溯源/过度工程/去AI味）。
- rule: 认卡片指定的测试机制为权威范围；要加强就在**同一机制内**加强——静态断言从「查 token 存在」升级为「锁全套接线：每个不可信值的赋值端+消费端+关键语法边界都各一条断言」（T15 六条锁：$2/$ROOT 各自 from-赋值 + 经 $env: 消费 + sh 单引号 -Command 边界），而非换机制（静态↔行为）招 oscillation。卡确需行为覆盖就先改卡明确要求、再实现为聚焦 helper。附：shell-out 包装脚本写 BOM-free UTF-8（非 ascii，否则非 ASCII 路径变问号→非确定 fail）；StrictMode 下 Get-Command X 取 .Source 前先判非 null（$null.Source 抛、崩整个 selftest）。
- enforced_by: 
- refs: 

## L101
- date: 2026-07-12 ｜ tags: r3,review,task-card,allow_paths,workflow ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 对 ad-hoc（无任务卡）分支直接跑 review.ps1 求批准，R3 按 Dimension #1 必 block：no task card or allow_paths exists to approve these paths——浪费整轮评审
- root_cause: rubric 把「卡片 allow_paths 显式批准范围」列为 must-block 维度；review.ps1 按分支名定位卡片，无卡时整个 diff 都视为未授权越界，与改动质量无关
- rule: 元仓维护类改动要走 R3 批准/合并，先建 T<n>-NAME 卡（allow_paths 全覆盖含卡自身 + 无变量 DoD）并把分支命名/改名为卡 id，再跑 review.ps1；卡在首轮评审前就位，省一轮必 block 往返
- enforced_by: 
- refs: 

## L102
- date: 2026-07-10 ｜ tags: git,red-test,workflow ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 重打五处编辑，约十分钟；未丢进度（内容尚在上下文里）
- symptom: 为了给新闸做 RED 测试，在工作树里就地「种」一个缺陷，跑完再用 git checkout -- <file> 还原。结果 checkout 把文件还原到 HEAD，而不是还原到「种缺陷之前」的状态——那次改动尚未提交，于是**五处未提交的编辑连同种子一起被抹掉**，只能凭记忆重打一遍。
- root_cause: git checkout -- <file>（等价 git restore <file>）的语义是「用 HEAD/索引覆盖工作树」，不是「撤销我刚才那一下」。工作树里未提交的改动对它而言无法区分：种子和真改动都只是「与 HEAD 的差异」，一并丢弃。凡是「先改再还原」的临时试验，只要基线不是一个真实提交，还原就必然连真改动一起吃掉。
- rule: RED 测试（或任何「临时破坏→观察→还原」）之前**先提交**，让 HEAD 成为真实基线；之后 git checkout -- <file> 才只撤销种子。流程：① 实现 + 跑绿 → ② commit（这一步不可省）→ ③ 种缺陷 → ④ 跑出红 → ⑤ git checkout -- <file> 还原 → ⑥ 确认 git status 干净 + 再跑一次绿。若确实来不及提交，就把文件复制到临时目录当备份，或用 git stash push -- <file>（stash 保得住），绝不裸 checkout。与 L91 同宗（别用破坏性 git 动作处理未提交工作），但动词不同、触发场景不同：L91 讲分支指针，本条讲工作树文件。
- enforced_by: none（操作/流程类；无机检。RED 前是否已提交无法从最终 diff 反推）
- refs: 本会话 T11/T10 现场（review.ps1 五处编辑被 checkout -- 抹掉）；关联 L91（破坏性 git 动作）、L88（RED 证据与 HEAD 一致性）

## L103
- date: 2026-07-10 ｜ tags: api,model,validation,evidence ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 两轮 R3 评审往返；未误合
- symptom: 拿一次探测得到的报错枚举当成「该 API 的合法值全集」，写进代码做静态校验。实测 codex：给 gpt-5.6-luna 传一个非法档位，API 回「Supported values are: none, minimal, low, medium, high, xhigh」。据此硬编码枚举后，R3 指出该枚举既会**误拒**合法配置、又会**误放**非法组合——再探测确认：gpt-5.6-sol 与 -luna 都**接受 max**（枚举里没有），却都**拒 minimal**（枚举里有）。
- root_cause: 那条报错来自**参数层**的通用枚举（reasoning.effort 这个参数在整个 API 上允许哪些字面量），与**模型层**的支持集（某个具体模型实际接受哪些）不是一回事。二者由不同校验环节产生、措辞相似（Invalid value / Unsupported value），极易混为一谈。凡「能力随模型/版本而异」的维度，任何静态列表都必然在某个模型上是错的。
- rule: 别把一次报错的枚举当全集：① 校验交给最靠近真相的那一层（CLI/API 自己），错值即让它报错、走既有 fail-closed，而不是在外面复刻一份会腐烂的列表；② 若非要断言，就对**你实际要发的 <模型, 参数> 组合**逐一探测，并把探测证据写进注释/CHANGELOG；③ 注意区分 Invalid value（参数层枚举）与 Unsupported value（模型层支持集）——前者不蕴含后者。同理适用于模型名、上下文窗口、工具支持等一切随版本漂移的能力位。
- enforced_by: none（判断/证据类；selftest 17z 有一条回归守卫禁止 review.ps1 重新硬编码档位枚举）
- refs: 本会话 T10 现场（PR #99 R3 第一轮 block）；scripts/review.ps1 不再硬编码枚举；关联 L89（引述须逐字取自 stdout）、L26（方法论优先于工具）

## L104
- date: 2026-07-12 ｜ tags: task-cards,selftest,regression ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 删除一张从未开工的任务卡文件（specs/tasks/<id>.md），选完全干净 check-cards PASS，却让 selftest 悄悄倒退失败——具体是另一张早已合并、历史无关的卡（T11-R3-BASELINE）的 dod_command 里有一条 Test-Path 该被删文件路径的静态断言，而 selftest 的 hermetic 回放夹具（17aa(8)）会克隆当前工作树、真的重放那条历史 dod_command。
- root_cause: 任务卡之间存在隐式的跨卡静态依赖：一张卡的 dod_command 可以用 Test-Path 断言另一张卡文件的存在（用来验证「某债务已正确拆卡登记」这类历史事实），且这类断言会被 selftest 的行为夹具真实重放（不只是 check-cards 的结构校验）。删除卡文件只按「这张卡本身有没有 worktree/分支」判断是否安全，没有反向 grep 全仓看是否被其它卡的 dod_command 引用。
- rule: 删除/重命名 specs/tasks/*.md 前，先 grep 全仓其文件名/id——尤其别漏查其它卡的 dod_command 字段（可能含 Test-Path 该路径）与 selftest.ps1 的 hermetic 夹具。若被引用，改为保留文件、改写内容+status 反映真实处置（如撤销未实现），而非物理删除；删除前后都跑一次完整 selftest.ps1（非仅目标 DoD）确认无跨卡回归。
- enforced_by: 
- refs: 

## L106
- date: 2026-07-12 ｜ tags: powershell,worktree,sandbox,tool-usage ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 2
- symptom: PowerShell 工具对 C:\wt\<worktree> 等主检出之外的路径默认沙箱化：cd/写入表面成功（无报错、'done' 打印），但下一次调用读回验证却是旧内容；未加 dangerouslyDisableSandbox 时的一次写脚本还曾把 cd 静默重置回主检出，导致后续相对路径写操作真的落进了主检出（误把 BOM 加进 7 个生产脚本），须 git restore 撤销。
- root_cause: PowerShell 工具默认沙箱模式对主工作目录之外路径的读写不可靠——未显式传 dangerouslyDisableSandbox:true 时，跨目录操作可能被静默重定向/回退到主目录而非报错，造成'看起来成功、实际操作了错误位置'的假象。
- rule: 对 <WorktreeRoot>\<id> 等主检出之外路径的任何 PowerShell 读写（cd/Set-Content/WriteAllBytes/git -C 等）一律显式传 dangerouslyDisableSandbox:true；每次写操作后用绝对路径读回验证内容，不要只信打印的'done'；怀疑跨目录污染立刻 git status 主检出确认无意外改动。**具体机制（2026-07-23 复发，T49）**：.NET 静态方法（System.IO.File 的 ReadAllBytes/ReadAllText/WriteAllText 等）的**相对路径按 .NET 进程的当前目录解析，PowerShell 的 cd / Set-Location 不改它**——于是「先 cd 进 worktree 再查那边文件的 BOM」实际读的是**主检出**的同名文件，得出「BOM 还在、子代理没剥」的**假结论**，差点据此放过一处真回归。跨检出调 .NET API 一律传**绝对路径**（或显式 System.IO.Directory SetCurrentDirectory）；PowerShell 原生 cmdlet（Get-Content/Set-Content -LiteralPath）不受此影响，混用两者时尤其容易只对一半。**本次判定不 promote 进必须层**：Tier-1 刚由 TD88 弧压到 4 条，且该形态已被 L157「落盘改动先对 diff --stat」的通用习惯覆盖（同 L61/L148 的降级先例）。
- enforced_by: 
- refs: 

## L107
- date: 2026-07-12 ｜ tags: multi-agent,verification,planning,research ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 委派/多代理研究把每个事实都过了严格核验（双源、只认官方来源）交付仍错——错在「问题分解/前提名单」本身：名单来自模型记忆、从未被核验。Anthropic CMA cookbook 实测：20 项公园事实全过 nps.gov 核验，但「面积前十」名单本身错了一席；团队臂与单兵对照臂犯同一个错，对照实验也测不出。
- root_cause: 核验标准只覆盖被显式放进闸内的对象；分解/前提位于闸外，事实级全绿制造「整体已验证」的错觉。凡由模型记忆生成的枚举/名单/前提，未过独立核验就是未验证输入。
- rule: 前提影响结论时，为「分解/前提」单独加一道核验（如多派一个子代理专门验证名单/枚举本身）；plan-forge/R3 preflight 审计计划时把「前提清单来源已核验？」当显式检查项——核验闸只保证闸内对象，别拿事实级绿灯当整体绿灯。
- enforced_by: 
- refs: anthropics/claude-cookbooks managed_agents/CMA_plan_big_execute_small.ipynb (MIT)

## L108
- date: 2026-07-12 ｜ tags: multi-agent,subagent,cost,orchestration ｜ tier: ledger ｜ kind: judgment ｜ severity: minor ｜ recurrence: 1
- symptom: 为省钱把任务拆得更细、派更多更窄的子代理，总账单不降反升；或让协调者/贵模型跑了一轮却一个子代理都没派（白付一轮贵模型往返）。Anthropic CMA cookbook 实测：按公园拆 10 个 worker 接近成本最优，继续往「每事实一个 worker」细拆反而更贵。
- root_cause: 每个子代理有固定 overhead（系统提示、工具往返、回传摘要）；拆分低于任务的「自然工作单元」后，overhead 增量超过并行与上下文隔离的收益。反向同理：任务太窄根本不值得编排。
- rule: 按「一个独立可交付的子问题=一个子代理」拆，不按最小事实粒度拆；委派划算的形状=覆盖型（N 个必查项、读多写少），发现型（大海捞针）贵模型直做差距小；反向哨兵：协调者零 spawn=这活不该上编排。收益存疑先小规模试跑、比对 token 账单再放大。
- enforced_by: 
- refs: anthropics/claude-cookbooks managed_agents/CMA_plan_big_execute_small.ipynb (MIT)

## L110
- date: 2026-07-12 ｜ tags: mcp,tooling,search ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: codebase-memory MCP 对本仓 index_repository 后，search_code 查 scripts/ 或 docs/ 内容永远空结果——索引器默认排除目录清单含 scripts、docs、.claude（恰是本元仓全部实质内容所在）；排除清单只在 index_repository 返回体的 excluded.dirs 里列一次，空搜索结果本身不带提示，易被误读成「代码里没有」。
- root_cause: 索引器按常规 src/ 工程启发式把 scripts/docs 当非核心目录排除，对「实质内容在 scripts/docs 的元仓」不适配；后续查询的空结果不携带「该路径未入索引」信号，构成静默假阴性。
- rule: 用 codebase-memory 查询前先核对 index_repository/index_status 的 excluded.dirs；本仓 scripts/docs/.claude 不在索引内——查链路脚本一律用 Grep/Read，绝不拿该 MCP 的空结果当「不存在」的证据。
- enforced_by: 
- refs: 

## L111
- date: 2026-07-12 ｜ tags: harness,tool-output,context-budget,design ｜ tier: ledger ｜ kind: judgment ｜ severity: minor ｜ recurrence: 1
- symptom: 设计 harness/链路脚本时默认把纪律写进提示词与文档，工具输出只顾人读——agent 每轮要自行解读散文、猜下一步命令；且工具输出体积从不被当回归指标，膨胀只有撞上下文墙才被发现。对照情境：oak VCS 把「下一步该跑什么」直接放进每个输出 payload（recommended_next_commands / retry_command / 截断声明），并把 agent 须摄入的输出字节当一等 benchmark 门（67MB diff 优化到 1.7KB 是其 kept 判据之一）。
- root_cause: 提示词纪律是概率性的、每会话重付 token；工具输出里的明确下一步是确定性的、一次实现处处生效。「上下文当内存」（L28）只管住了每轮常驻文档，没管住运行时工具输出的预算。
- rule: 更好的启发式：能让工具输出把下一步变成无歧义（可直接复制的命令、截断声明、完成/待办阶段报告）就优先做在输出侧，而非追加提示词纪律；把「agent 须摄入的输出字节」当可预算指标，改链路脚本输出时比前后体积。喂 HARNESS-REVIEW 复审方向品味，不预建机制。
- enforced_by: 
- refs: oakdotspace/oak AGENTS.md + BENCHLOG.md (Apache-2.0)

## L113
- date: 2026-07-12 ｜ tags: review,r3,task-card,scoping,workflow ｜ tier: ledger ｜ kind: judgment ｜ severity: major ｜ recurrence: 2
- symptom: R3 评审中途点出的问题若与卡片原定 `diagnosis`/`allow_paths` 是**不同类**（非同一缺陷的更深一例），却被就地在同一分支上收编修复：分支越滚越大、`allow_paths` 跟着扩、round 数随之攀升——因为每新增一类问题，L97 的「R3 每轮只报一处」又在这个更大的 diff 上重新计一轮。首版本条曾写成「不同类问题就开新卡、本卡不管」，R3 自己（round-5 复核本条时）指出这个措辞有硬边界漏洞：若那个「不同类」问题**恰是本卡这次 diff 自己引入的真实缺陷**（只是维度、非卡片原诊断），「开新卡+本卡照样合并」等于放行一个已知缺陷，和「block 必须修完才能合并」的 fail-closed 精神矛盾。
- root_cause: R3 逐轮单点暴露（L97）本身不会自动止住范围蔓延——卡片的 `allow_paths` 只记录「当前已扩到哪」，没有机制判断某个新发现是否仍属于卡片原定的那一类问题；不主动拆卡，新发现就默认沉淀成「顺手再改一点」。**且**「不同类」与「是否该现在修」是两个正交问题——前者只关心分类，后者才是安全关键：不同类的问题仍可能是本次 diff 自己的真实缺陷（必须现在处理），也可能是纯粹顺带碰到、与本次 diff 无关的既有问题（才适合拆卡缓办）。
- rule: R3 block 命中的问题先判**是否是本卡这次 diff 本身引入/携带的真实缺陷**（即使维度与卡片原诊断不同）：**是**——当场修好，或把那段有缺陷的改动**整段回退/剥离**出本卡 diff（不得留着已知缺陷合并）；**否**（问题确属既有系统、与本次 diff 无关，纯属评审顺带发现）——才当场开一张新 `T<n>-NAME` 卡登记、本卡维持不动即可安全过闸。证据修正：T11（TD68 基线解析修复）曾内联一版 diff-path verdict 中和，R3 两轮指出精度缺陷——**这是 T11 自己新增代码的真实缺陷**，T11 的正确处理不是「记一笔 TD 就带缺陷合并」，而是**把这段有缺陷的内联实现整段回退**（不留在最终 diff 里），保留原本已够用的防护（`$card` token 中和 + diff nonce 栅栏）不受影响，另开 T14 把精确版本做好——回退到无该功能，比合并一个已知不精确的版本更安全。T12/T15/T16/T18 则是评审顺带发现、与 T11 这次 diff 本身无关的既有系统问题，直接拆卡且本卡无需回退任何东西。
- enforced_by: none（判断类；无机检——「是否本次 diff 自身缺陷」需要人/AI 判断，不可机检）
- refs: 本会话 T11→T12/T14/T15/T16/T18 谱系（PR #102 及其拆卡，TD83/T14 是本条判据的原始证据）；关联 L97（R3 逐轮单点外溢）、L101（不同点：L101 讲首轮评审前必须先有卡，本条讲评审**过程中**新发现该修/该回退还是该拆）

## L114
- date: 2026-07-12 ｜ tags: git,ship,concurrency,scope-gate,red-evidence ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 2
- symptom: ship 范围闸把大量本卡未改的文件（其他已合并卡的产物/文档）报成越界：origin/master 在本卡 worktree 分支好之后被其他并发会话的真实 PR 合并推进了多次，ship 的范围/评审基线解析到 origin/master，diff 的 merge-base 落在旧共同祖先上，中间所有'本地曾经领先但从未推送'的提交（含另一会话半途合并又撤销的卡）全部混入本卡 diff。
- root_cause: 多会话共享同一主检出时，本地 master 与 origin/master 会各自独立前移又不同步（他人 gh pr merge --squash 只更新 origin、不回写本地；本地又有会话直接在共享 master 上 commit/revert，即 L112 场景）——本卡的 worktree 分支是从这条已经该会话专属又混乱的本地 master 分出的，其真实 fork point 相对 origin 早已过期。
- rule: **预防（首选，成本最低）**：多会话共享主检出、本地 master 又脏（他会话未提交改动）又与 origin/master 发散（本地有未推送提交、且缺 origin 新提交）时，`-Phase start` 直接把 worktree 基在 origin 最新 tip——`pwsh -File scripts\task.ps1 -TaskId <id> -Phase start -Base origin/master`（ship 亦传 `-Base origin/master`）——分支从 origin/master 分出，PR diff 天然只含本卡改动、不含本地未推送的中间提交，范围闸/评审基线自始干净；R5 的 doc_sync/lesson 提交同理，在**从 origin/master 新开的干净临时 worktree**（`git worktree add --detach <path> origin/master`）里编辑→只 add 目标文件→push HEAD:master→删该 worktree，全程不 `git add -A`、不 stash、不 rebase 脏主检出（守 L112）。T29-LICENSE-FRONTEND-DIR 实测：主检出 62 个他会话脏文件 + 本地 master 1 ahead/1 behind 时，`-Base origin/master` 全程零污染、PR #113 diff 恰为 3 个 allow_paths 文件、doc_sync 提交只动 2 文件。**补救（已中招时）**：ship 报出大片无关越界文件时，先 git fetch origin 比对 origin/master 与本地 master/本卡分支的 ahead/behind；若已真发散，不要在混乱的中间历史上做多提交 rebase（会牵连他人已合并又走开的分支，冲突面不可控）——改用 git checkout -B <branch> origin/master 把分支基点重置到 origin 最新 tip，再把本卡的最终文件内容重新落到这个干净分支上（内容已知时直接重写比逐条 cherry-pick/rebase 更快更可靠）；RED 证据的 sha 会随之失效，须 git stash 退回改动前状态、在新 HEAD 上重新跑 -Phase red、再 git stash pop 续接 GREEN，否则 ship 会因 RED sha 不等于 HEAD 而拒收。
- enforced_by: none（人工识别症状后手动执行；候选：ship 范围闸报越界文件数异常多时打印 origin/master 与本地分支 ahead/behind 提示，引导走此恢复路径）
- refs: 

## L115
- date: 2026-07-12 ｜ tags: selftest,review-gate,fixture,vacuous,tdd ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 给 review.ps1(评审=审一个 diff) 加 selftest 种子闸时，夹具改一个文件来测「评审逻辑是否把某值注入判定标准」，断言「送达 prompt 含被改的裸 token」——RED 阶段该断言在修复前竟已 PASS(vacuous)，只有另一半断言真红。
- root_cause: 被改文件本身就在**被审 diff** 里(评审对象即该 diff)，故被删/改的 token 作为 diff 的 +/- 行原样出现在送达 prompt 的 diff 正文；prompt.Contains(裸token) 匹配的是 diff 回显、而非「评审逻辑有没有把该 token 注入判定标准」。与 L19 不同：那是「缺输入」，这是「输入在场但经被审 diff 回显」。
- rule: 测「评审工具如何处理某文件」时别断言被改文件里的裸 token(它必在被审 diff 里回显)；断言只有正确行为才产生的**输出结构**(如冻结子句 prose 模板「触碰冻结契约/ schema(<token>」，不出现在 ASCII diff)。RED 必两半都真红方证非 vacuous(L19 vacuous-pass 家族的 diff-回显特例；实证 T32-R3-STANDARD-BASELINE 闸 17ab)。
- enforced_by: 
- refs: 

## L116
- date: 2026-07-12 ｜ tags: ship,codex,quota,resume,red-evidence ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: Codex R3 配额耗尽导致 ship 在 commit 成功后卡住（PR 已开）；天真地重跑 -Phase ship 时被 task.ps1 拒绝：'RED 证据无效（证据 sha 与当前 HEAD 不符）'——因为 ship 自身的 commit 步骤已把 HEAD 推进到 RED 证据记录点之后。
- root_cause: -Phase ship 的 RED 证据闸要求证据 sha == 当前 HEAD，用以防篡改（防止拿旧/伪造证据蒙混过关）。但当 ship 在 R3（配额/外部依赖失败）处中断时，DoD/commit/push/PR 早已成功完成、HEAD 已前进——此时重跑整条 -Phase ship 会重新触发 RED 证据闸的 sha 校验，而该证据是 commit 之前记录的旧 HEAD，必然不符、必然死锁。
- rule: **本条 2026-07-12 原述的恢复路径已于 TD89 反转，勿照旧版执行**——旧版教「commit 后只差评审时别重跑 ship、改对已开 PR 直接评审并合并」，那条路绕过范围（scope）闸而 CI 没有范围闸，正是 TD89 的根因（详见 T50-TD93-DOCTRINE-LOCK / TD113）。现行教义（T36-DOCTRINE，权威长文见 docs/DEVOPS-WORKFLOW.md「ship 非原子→重跑即 resume」）：commit 已成功、只差 R3/merge 时（配额耗尽等外部原因），**主恢复 = 修复后重跑原封不动的同一条 -Phase ship**——commit 腿铸的 T35 watershed 收据会让合法重跑通过 RED 新鲜度闸，全部确定性闸 + R3 重判、无豁免。只有**收据缺失且已 push** 才退到兜底：先手工补跑全部确定性闸（DoD/verify/范围/许可/防泄露——CI 无范围闸，不可仅靠 CI），全绿后方可 review.ps1 -PostStatus + gh pr merge 作**最后手段**；收据**不自洽**（S8，含 watershed 后历史改写）须先对齐远端并核验 PR head == 已过闸的本地 HEAD 再兜底。与 L21（配额非代码问题、勿绕过勿手动合并）互补：L21 讲态度（等重置、别绕过），本条讲重跑的分岔点。
- enforced_by: none（人工识别症状后手动执行）；task.ps1 侧的旧教义措辞不复现由 T50 的 selftest 闸 15q 负断言看守——但 15q 是**字面量比对**、抓不到语义改写（本条正文即靠人守，非机检）
- refs: 本会话 T31-CARDS-AS-CODE 现场（PR #116，配额于 21:55/22:56/23:23 NZST 三次探测，第三次 pass；resume 命令验证有效）；关联 L21（配额非代码问题）、L113（origin/master 快速前移的 base 重置恢复，本卡同一 ship 里连续触发两次，佐证多会话高频合并期间两坑常伴发）

## L117
- date: 2026-07-12 ｜ tags: architecture,agents,orchestration ｜ tier: ledger ｜ kind: judgment ｜ severity: minor ｜ recurrence: 1
- symptom: 面对「是否搭一套预建角色的子代理班子 roster」的设计诱惑，容易照搬 Anthropic cookbook 的 orchestrator/worker 命名去新建 .claude/agents 常驻代理。
- root_cause: 脚手架已把 cookbook 五种 agent 模式（prompt-chaining/routing/parallelization/orchestrator-workers/evaluator-optimizer）以链与工作流形态收敛实现；再建常驻班子＝重复搭 + 固定 overhead（L108）+ 把易变工具写死违 L26。
- rule: 不建常驻命名代理 roster：角色按 phase/模型档位路由、按需派临时子代理用完即弃；唯一长驻角色是回路外第二独立评审者（codex R3）。加常驻角色前先查 docs/HARNESS-REVIEW.md 的 cookbook 映射表，多半已被某条链覆盖。
- enforced_by: 
- refs: 

## L118
- date: 2026-07-12 ｜ tags: r3,review,worktree,provenance ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 为一张全新卡 ship 一个映射/溯源类纯文档改动时，R3 连续多轮 block：#7 卡缺失无法溯源 → #1 diff 在「还原」他人已并改动 → #7 逐条挑文档 claim 与实现不符。
- root_cause: (a) 全新卡不在分支 base 上，R3 看不到卡无法溯源；(b) worktree base 的 origin/master 在评审周期内被并发会话推进，两点 diff 把 fork 后并入的提交显示成「还原」；(c) 映射类文档每条断言 R3 会对照真实实现核，不只看 token 在不在。
- rule: 映射/溯源文档＋全新卡：① 把新卡纳入本分支 PR 并加进 allow_paths（满足 #7 溯源）；② 并发仓每次重评审前先 git merge origin/master，令两点 diff＝仅本卡文件（治 #1 假还原）；③ 映射每行按实现据实写（parallelization＝预定固定扇出 / orchestrator-workers＝运行时动态委派），别按 cookbook 命名想当然。
- enforced_by: 
- refs: 

## L119
- date: 2026-07-13 ｜ tags: archival,cold-storage,selftest,fixture ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 给仓引入冷存压缩（archive.ps1 把 merged 卡 → specs/archive/tasks/、paid/accepted 债行 → tech-debt-archive.md）后，selftest gate 17aa 连环红：它复用真卡 T11-R3-BASELINE 作 E2E 夹具，而 T11 的静态「证据」DoD 会 Test-Path 姊妹卡路径（specs/tasks/T14-…）并 grep tracker 里历史债 id（TD68）——归档把二者移出活位置，DoD 遂假失败、ship 在 R2 DoD 闸而非被测的 base 校验处 block。
- root_cause: 静态证据式 DoD/夹具按「位置」grep 内容；冷存压缩正是按位置搬内容，二者天然耦合。只有 re-execute 已归档卡的 DoD 才触发——正常交付流程不重跑归档卡，唯 selftest 复用真卡作夹具时重跑，故耦合面仅此一处；下游项目不受影响。
- rule: 引入归档/冷存机制时，先 grep 所有「复用真卡 DoD / 按位置断言历史内容」的夹具与闸；在夹具内把冷存内容还原回活位置（cards→specs/tasks、debt rows 追加回 tracker）重建压缩前形态再跑被测逻辑。归档是运行时机制，不该让复用真内容的闸感知到。
- enforced_by: scripts/selftest.ps1 gate 17aa(8) 夹具还原冷存 + gate 12e archive.ps1 分区/幂等
- refs: TD86 · T28-CONTEXT-COMPACT · scripts/archive.ps1

## L120
- date: 2026-07-13 ｜ tags: archival,dedup,data-loss,verification ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 给 append-only 自由文本表（tech-debt-tracker：id 非机检唯一）做冷存压缩时，archive.ps1 按 id 去重追加到归档——同 id 不同内容的两行（合并/手误可致，本仓曾「TD69 两分支不同内容」）中，第二行被从热文件删掉却因 id 已存在而不写进归档 = 静默数据丢失，且汇总仍打印「已归档 N 条」谎报成功。自评 selftest 全绿也没抓到（作者自写的 gate 12e 当时没覆盖 dup-id 用例）；是 fresh-context 对抗 verifier 子代理独立审计才揪出（MAJOR）。
- root_cause: 去重键选错：把「非唯一的 id」当唯一键。搬运/压缩 append-only 记录时，唯一性只能按整行内容保证，不能按可重复的业务 id。且「作者写代码 + 作者写测试 + 测试全绿」不构成数据完整性证明——盲区会同时漏进实现与其自测。
- rule: 搬运/去重 append-only 记录一律按整行内容去重（保全同 id 相异行），绝不按可能重复的业务 id；数据完整性关键逻辑（不丢/不重/守恒）必须派 fresh-context 对抗 verifier 独立审计（对齐 task-loop 4.7 / comprehension 提醒），别拿自写自测的绿当完成——并把 verifier 揪出的每个缺陷回填成一条自证非空的机检断言。
- enforced_by: scripts/selftest.ps1 gate 12e（dup-id 守恒断言，已证非空）+ 流程：fresh-context verifier 子代理
- refs: TD86 · T28-CONTEXT-COMPACT · scripts/archive.ps1

## L121
- date: 2026-07-13 ｜ tags: selftest,cross-platform,ci,mock ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: selftest 用 `<cmd>.ps1` 桩 + PATH 前置来 mock 外部命令（gh/codex）。Windows 经 PATHEXT 把裸 `<cmd>` 解析成 `<cmd>.ps1`、桩生效；但 Linux/pwsh 无 PATHEXT，裸 `<cmd>` 跑真二进制而非桩——桩静默失效、被测在线路径不触发，gate 在 ubuntu 假红（T24 闸 15h4(d) 即此：合并后连带 master 及所有基于它的 PR 的 ubuntu CI 全红，windows 绿掩盖）。
- root_cause: PATHEXT（把无扩展名命令映射到 .ps1/.cmd/.bat）是 Windows 独有；pwsh 虽跨平台，命令解析仍走 OS——Linux 只按文件名精确 + 可执行位。「pwsh 跨 OS 同一解析路径」是错的假设。
- rule: 用 `<cmd>.ps1` + PATH 做的命令桩只在 Windows mock 成功；这类 gate 要么 `if ($IsWindows)` guard（同 17aa(8)/15h4），要么 Linux 侧改无扩展名可执行桩（`#!/bin/sh` 或 `#!/usr/bin/env pwsh` + chmod +x）。双 OS CI 下别假设 Windows-only 桩在 Linux 也生效——它会静默跑真命令、gate 假红。
- enforced_by: scripts/selftest.ps1 闸 15h4(d/e/f)+17aa(8) 均 $IsWindows guard（PR #120）
- refs: PR #120 · 闸 15h4 · 17aa(8)

## L123
- date: 2026-07-13 ｜ tags: workflow,task-loop,pr,efficiency ｜ tier: ledger ｜ kind: judgment ｜ severity: minor ｜ recurrence: 1
- symptom: 一个特性产出了两个 PR——主体 PR + 事后 cleanup/lessons PR——每个特性双 PR、双评审、双 CI，低效。
- root_cause: R5 doc-sync（status→merged / TD→paid）与 lessons 被当纯「合并后」步骤单独成 PR；但绝大多数并非真·合并后事实——status: merged 在 ship PR 里即「本 PR 合并即 merged」、合法诚实；lessons 多在做特性时就学到。真·合并后的只有 squash SHA 这类（可先填 PR#、SHA 事后补或省）。
- rule: 默认把 R5 doc-sync（卡 status→merged、TD→paid、指针填 PR#）+ 本次学到的 lessons 写进特性/ship PR 本体（一 PR、一评审、一 CI）；只有「从合并/CI 本身学到」的 lesson（如本会话 CI 假红 saga）或撞号被迫拆分时，才另开 follow-up PR。
- enforced_by: none（约定；docs/DEVOPS-WORKFLOW.md R5 段注明）
- refs: 本会话 #115+#119+closeout 三 PR 本可一 PR

## L125
- date: 2026-07-13 ｜ tags: guard-chain,error-handling,fail-safe ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 一轮 R3 block
- symptom: 给守卫链加「凭据不匹配 → 保留 + 提示用 -Force」的 fail-safe 后，-Force 重跑仍走进凭据分支再次被拒——警告里给出的出路永不可达（elseif 链里凭据判定在 -Force 之前，凭据在位就短路）。
- root_cause: 显式人工覆盖开关被排在状态判定之后：只要触发状态存在，覆盖分支不可达。加新守卫分支时只测了守卫生效路径，没测「提示给出的逃生路径真的可走」。
- rule: 守卫链铁序：显式人工覆盖（-Force 类）判定必须先于一切凭据/状态判定；每当报错/警告文案给出某条出路，就为「那条出路真的可走」加一条行为测试（本仓 15h4(c) 即此模式）。
- enforced_by: 
- refs: 

## L126
- date: 2026-07-13 ｜ tags: review-loop,testing,fixtures ｜ tier: ledger ｜ kind: judgment ｜ severity: minor ｜ recurrence: 1
- symptom: 第二模型评审连续加码测试覆盖要求，maker 以「需新建整条离线夹具链、违反复用原则」为由在卡上写豁免——下一轮评审不认可；随后才发现仓里 17aa(8) 早已有同型夹具（bare origin + gh mock + review stub 的远端 ship 链），豁免理由不成立，多烧一轮。
- root_cause: 写豁免时只评估了「从零新建」的成本，没先全仓检索既有测试设施；对自己仓的夹具库存没有清点习惯。
- rule: 对检查器「补 X 覆盖」的要求，先全仓 grep/清点既有同型设施（stub/mock/夹具）再决定实现或豁免；豁免理由里必须写明「已检索过哪些既有设施、为何都不适用」，否则不豁免。
- enforced_by: 
- refs: T24 PR #111 R3 round4-5 现场

## L127
- date: 2026-07-13 ｜ tags: powershell,gh,native-args ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 一轮 R3 block + 两处生产调用自 round-3 起静默降级
- symptom: `& gh pr view $pr --json state,headRefOid` 对着真 gh 直接报错、对着忽略参数的 stub 却假绿——裸逗号字段表被 PowerShell 当数组字面量拆成两个独立参数（--json state headRefOid），gh 收到多余位置参数即错；用 `"$(...)"` 包裹 + 2>$null 后表现为静默空串，下游 fail-safe 分支把功能路径静默降级。
- root_cause: PowerShell 参数模式里逗号是数组算子：native 命令收到数组参数时按元素 splat 成多个参数。参数不敏感的测试替身（只 Write-Output 的 stub）掩盖此类调用形态错误；只有 args 敏感的 mock 才暴露。
- rule: 给 native 命令传含逗号的单参数（gh --json 多字段、git pathspec 列表等）一律引号包裹：--json 后跟单引号包住的字段表；测试替身尽量做成 args 敏感（按参数分派应答），参数形态错误才会被夹具抓住而非假绿。
- enforced_by: 
- refs: 

## L128
- date: 2026-07-13 ｜ tags: testing,assertions,docs-drift ｜ tier: ledger ｜ kind: judgment ｜ severity: minor ｜ recurrence: 1
- symptom: 注释/卡片声称「X 路径由某断言词法锁定」，但对应断言从未写——声明先行、断言缺席，评审对照文档与断言清单即抓出（R3 r6）。
- root_cause: 把「打算锁」写成「已锁」：文档更新与断言实现不同步，且没有把声明当承诺去核对的习惯。
- rule: 凡在注释/卡片写下「已由 Y 锁定/机检」的声明，写完立刻核对该断言真实存在（grep 断言体），或改措辞为「拟由 Y 锁定」；评审/自检把「声明的锁」清单化逐一对账。
- enforced_by: 
- refs: 

## L129
- date: 2026-07-17 ｜ tags: powershell,splatting,fixture,selftest ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 夹具 wrapper 用数组 splat 转发参数（& script.ps1 @args，args=@('-TaskId','T26-...')）时，'-TaskId' 字符串被当**值**塞进首个位置参数，撞 ValidatePattern 报 TD50-BADID——参数名根本没被识别。
- root_cause: PowerShell 对脚本/函数的 splat 分两种：hashtable splat 按参数名绑定，**数组 splat 只按位置绑定**——数组元素即使形如 '-Name' 也不会被解析成参数名（native 命令则相反，数组元素原样成 argv）。与 L127（逗号数组算子对 native 命令拆参）同族：PS 参数传递形态坑。
- rule: 给 .ps1/函数转发参数一律用具名参数透传或 hashtable splat；数组 splat 只用于 native 可执行文件。夹具 wrapper 优先显式 param 再具名转发（见 selftest 15r(e) enc-wrap 写法）。
- enforced_by: 
- refs: 

## L130
- date: 2026-07-17 ｜ tags: dod_command,task-card,fixture,vacuous-green ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 卡片 dod_command 值整体用双引号包裹（dod_command: "pwsh -NoProfile -Command '...'"）时，-Phase red 报「已是 GREEN」（exit 0）、DoD 闸恒绿——但命令从未真正执行，输出只是把命令文本回显了一遍。
- root_cause: Get-CardField/red 相取值不剥字段值外层引号，实际执行的是 pwsh -Command '"pwsh ..."'——一个双引号字符串字面量：PowerShell 求值字符串=回显+exit 0。与 L95 同族（vacuous DoD）：L95 是双层包裹吃掉 $ 变量致 ParserError 假红，本条是整值引号致回显假绿。
- rule: dod_command 值不要整体加引号——照 _TEMPLATE/T26 形态写裸值：dod_command: pwsh -NoProfile -Command "..."（内层才用引号）。写 selftest hermetic 夹具卡同样适用；红相后读一眼 DoD 实际输出（L95 已要求）也能当场识破本形态（输出=命令文本回显即中招）。
- enforced_by: 
- refs: 

## L131
- date: 2026-07-17 ｜ tags: review,report-layer,recovery,scope ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 2
- symptom: T26 saga 报告层（本意只是失败时刻打印进度+恢复提示）被 codex R3 连续 6 轮 block：每轮都在恢复命令文案里找到真缺陷（错误重跑建议→选项丢失→PR 状态误断→待办清单吞项→文案嗅探误分类→闸门旁路），diff 从 66 行滚到 300+ 行。
- root_cause: 报告输出的「恢复命令」会被对抗评审当**可执行契约**逐失败状态审查——给命令=写代码，状态矩阵（commit 前/后 × 推送前/后 × PR 各态 × 合并各态 × -SkipRed/-Local）必须完整正确，远超「打印一行提示」的直觉工作量；且恢复语义会牵出系统级缺口（TD85/TD89），评审器会顺藤要求重设计系统契约（超卡范围）。
- rule: 设计「给恢复/操作命令」的输出层时：① 先画完整失败状态矩阵再写文案，每条命令按该状态可执行正确性自审；② 能引权威锚点就不复制命令正文（免双源+免逐态审查面）；③ 评审发现流从「文案错」转向「要求重设计系统契约」即触发人裁+登记 TD 划界（本例 TD89），别无限迭代；④ 状态判定用机器状态（HEAD 前移/MERGE_HEAD/已解析 PR 号）不嗅探异常文案；⑤ 划界的可操作检测信号：R3 逐轮要求改另一张卡的文件/建新独立机制（如可执行独立范围闸入口）/上 gh-mock e2e，即已到边界——此时把 checker 遗留下沉到计划已分配的卡（更新其 charter），人裁划定范围线，对卡契约合规的核心做人裁 override 合并；核心机制达 selftest 全绿即视为该卡「完成」，长文/e2e 归属其它已分卡负责（第二次现场：T35 撞 13 轮同一模式）。
- enforced_by: 
- refs: 

## L132
- date: 2026-07-18 ｜ tags: workflow,quota,plan-forge,multi-agent ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 大型多 agent 工作流（plan-forge/decompose-cards，60+ agent）跑到一半撞会话子代理配额：agent 批量返回 "hit your session limit"，脚本随即崩在 null 结果上（如 plan-forge.mjs 267 行 r.verified——未 .filter(Boolean)），或審计段整段缺失只剩部分产出；同一弧内复发两次（r2 全崩 + 卡审 4/5 缺失）
- root_cause: 工作流脚本对 agent() 返回 null（配额/终端 API 错误）未做防御性过滤即解引用；子代理配额与主回路配额独立，主回路仍可用时子代理已耗尽，长弧多轮全量审计极易在弧中撞窗
- rule: 恢复恒用 Workflow resumeFromRunId（完成的 agent 缓存重放、只重跑失败段），绝不从头重跑烧双倍；发起 60+ agent 工作流前掂量配额窗（临近重置时段先跑小段）；写/改工作流脚本时 parallel()/agent() 结果一律 .filter(Boolean) 再解引用；配额缺失的审计段可由主回路按同角度替补人审并如实标注降级
- enforced_by: 
- refs: 

## L134
- date: 2026-07-19 ｜ tags: codex,subagent,sandbox,worktree ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 3
- symptom: codex:codex-rescue 子代理默认 write-capable（forwarder 缺省加 --write，散文里写"read-only"不生效），意图只读的评审任务仍改了分支；且其 codex workspace-write 沙箱可写根钉在启动 cwd=主项目目录，对 C:\wt\<TaskId> 这类主仓外 worktree 只读（FILES_CHANGED: none — sandbox denied writes）。直接调 codex-companion.mjs 绕 rescue 又报「Codex CLI not installed」（runtime 仅在 rescue 子代理内可用）。
- root_cause: rescue forwarder 契约=缺省写能力（除非显式 read-only 标志）；codex CLI 沙箱把可写根钉在启动 cwd（=主项目目录），worktree 在别处故只读；codex runtime 只在 rescue 子代理环境可用，orchestrator 直调缺 runtime。
- rule: 用 codex:codex-rescue 做「只读」务必 prompt 显式 read-only 并预期它仍可能写；worktree-based 卡（本脚手架 C:\wt\<id>）别指望 Codex 子代理写 worktree——改混合模型：Codex 做分析/diff 提案/R3 二意见（读足够），worktree 写盘由未沙箱的 Claude(Edit/Write/[IO.File]，可自由写 C:\wt)落。大任务给 codex-rescue 一律喂自包含 inline-source brief（只读一个文件、纯 transform、不探索仓库），令其在超时前快速返回；或 Opus 起草 + codex 只做 R3 闸——别指望 codex-rescue 一发跑完需探索的大任务（foreground 撞约 10 分钟硬超时；background 返回不可收集的 job 指针，主循环无 status/result 工具取回）。把 codex-rescue 的自报产出当**未验证声明**：一律用 git -C <wt> status/diff 核真实编辑落地 + 主线亲跑 selftest 核绿，绝不信其自报（rescue 契约=单次 task forward、不能 poll/collect；其 codex exec 后台任务由 codex-companion 运行时托管、非 harness 追踪，完成不自动通知；子代理措辞会把「跑过 selftest(旧码绿)」误当「我的修复已 GREEN」）。收结果由主线（非子代理）跑 node <plugins>/codex/scripts/codex-companion.mjs status|result <taskId>；若 codex 停滞/假绿，goal-keeper 直接接管自修。
- enforced_by: 
- refs: 

## L135
- date: 2026-07-19 ｜ tags: bash,cd,worktree,windows ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: Bash 工具里 cd "C:/wt/<id>" && git status 静默失败回退主仓（末尾 "Shell cwd was reset to D:\..."），git 实际跑在主检出 master 上——误得「worktree 干净」结论，掩盖 worktree 真实态（未推送提交/HEAD 前移）。
- root_cause: Bash 工具每命令后重置 cwd，跨盘/worktree 的 cd 不持久生效；依赖 cwd 的相对命令因此指向错仓。
- rule: 查 worktree/别的仓一律用 git -C "<path>"（或 PowerShell Set-Location，其 cd 生效）——绝不靠 Bash 工具 cd 切到别路径再跑依赖 cwd 的命令；关键态判断(clean/HEAD/ahead-behind)用 -C 显式锚定并交叉核对。
- enforced_by: 
- refs: 

## L137
- date: 2026-07-19 ｜ tags: selftest,fixture,hermetic,git ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 15r(e) 隔离夹具改非-SkipRed red-then-ship 后两处假失败：① .review/<id>.red 被 git add -A 提交、卡外触范围闸(失败点非预期)；② 成功 -Local 合并铸 T24 凭据目录 .git/scaffold-merged/<id>，后续同一 $sg 子夹具建同名文件失败(目录已存在)。
- root_cause: 夹具仓缺真仓的 .gitignore(.review/)；且成功合并的持久副作用(凭据目录)污染被后续子夹具复用的共享 $sg。
- rule: 隔离夹具须镜像真仓 .gitignore(至少 .review/)；任何在共享 $sg 上留持久副作用(凭据/合并)的子夹具末尾显式 teardown(清凭据目录 + merge --abort)，防跨子夹具污染假失败。改夹具后逐子夹具核失败点，别只看总 exit。
- enforced_by: 
- refs: 

## L138
- date: 2026-07-21 ｜ tags: codex,R3,review,ship,timeout,cache ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 2
- symptom: R3 codex 评审在 ship 里反复撞 600s wall-clock timeout 被杀->fail-closed block；控制台见 codex_models_manager: failed to renew cache TTL: missing field supports_reasoning_summaries；而裸 codex exec 探针秒回(用户级有效缓存),故易误判为瞬时慢
- root_cause: codex-cli 升级后模型元数据 schema 新增字段(如 supports_reasoning_summaries),旧 ~/.codex/models_cache.json 反序列化失败->codex 每约3min 重试续期缓存而卡顿,令携大 prompt+high effort 的 R3 评审超 600s。--ignore-user-config(ReviewModel 钉住时启用)只跳过 config.toml,仍用该全局 models_cache.json,故 _config 钉模型不免疫
- rule: R3 反复 600s 超时且日志见 models_cache TTL 续期报错 -> 改名/删 ~/.codex/models_cache.json 令 codex 重生(可逆,秒级),再重跑 ship;先用 codex exec --ignore-user-config -c model_reasoning_effort=low 在被审 git 仓内探针确认恢复。ship 不透传 -TimeoutSec(task.ps1:612 恒 600s),根治靠修缓存,非调超时(调大须走手动 merge-safe 恢复)。注意(2026-07-22 T39/T40 弧实测):清缓存只是**暂时缓解**——CLI 会按旧 schema 重生缓存,续期报错每次调用重现,同弧内超时可复发(T39 r4、T40 r1 各清一次);真根治 = 升级 codex CLI 至 schema 匹配版本。**第二超时模式(T41 弧,与缓存无关)**:探针秒回=后端健康,超时是评审者在沙箱里自行跑全套 selftest(~7 分钟)吃光 600s——结构性碰撞非故障,重试是逐轮轮盘;连续 2-3 次即停走人裁(ship-now 先例 ×2),结构修登记 TD109(评审契约设计)
- enforced_by: 
- refs: 

## L140
- date: 2026-07-21 ｜ tags: selftest,planning-with-files,token,gate8 ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 主检出跑 selftest 突然 FAIL：8a「init 后仍残留占位符：task_plan.md」且 8d2 把根部规划三件套当意外顶层条目；同套代码在 worktree/CI 里却 PASS。
- root_cause: 根部规划文档（task_plan/findings/progress，gitignored、untracked）被 gate-8 冒烟整树拷贝进临时树：正文若含双大括号 token 字面量即被 8a 当「未替换占位符」；且冒烟树无 .git，8.1 的 check-ignore 豁免在 8d2 处失效。
- rule: 根部非模板 md（规划三件套等）勿写双大括号 token 字面量，引用时用文字描述；三件套已登记 $RootAllow（65f244e）修 8d2 侧。主检出独有的 selftest FAIL 先查「untracked 根部文件被冒烟树收进」这类环境差，别先怀疑刚做的改动。
- enforced_by: 
- refs: 

## L141
- date: 2026-07-21 ｜ tags: subtraction,doc-sync,r3,harness-review ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 3 轮 R3 往返(约40分钟)
- symptom: TD88 W2 减法卡(T39)连吃 3 轮 R3 block：①verify.ps1 头注压缩成「原则见 DELIVERY-OPS.md」但该文档并不含那三条硬边界(悬空语义指针)；②删钩子后 selftest 留 4 处陈旧计数/文案(「6 钩子」「三个 Stop」散在成功消息/注释/Step 标签)；③ffmpeg 探针删除后的人工核验注记写成裸跑 ffmpeg——核验的是 PATH 上另一份而非 pinned 分发件(错件假通过)
- root_cause: 减法/去重改动有自己的缺陷类：压缩成指针时没人核验目标真的承载被移走的内容；删除物周边的计数字面量/枚举文案不随删同步；自动化探针降级为手册步骤时其原有精确性约束(确切二进制、fail-closed、哈希钉扎)被默认丢失
- rule: 砍/去重三查：(1)每个「内容→指针」改动先打开指针目标逐字核验其真含该内容(标题/条目名对上)；(2)删除任何实体后全仓 grep 其名与其计数词(「N 个/N 钩子/三…」)同步剩余文案——R3 每轮只报最刺眼一处,自己扫尾(L97)；(3)自动化检查降级为手工注记时逐条继承原探针的精确性:确切路径/判空 fail-closed/版本+哈希钉扎,把「跑哪个二进制」写死在步骤里
- enforced_by: 
- refs: T39-TD88-W2-TRIM PR#128 R3 r1-r3；docs/LICENSE-POLICY.md 附录A；scripts/verify.ps1 头注

## L142
- date: 2026-07-22 ｜ tags: powershell,params,testing,r3 ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: T40 三轮 R3 往返
- symptom: archive.ps1 新参数 -LessonIds 连吃三类外部调用绑定坑：①README 文档的 pwsh -File 逗号形式 L32,L34 绑成单串"L32,L34"（-File 不做逗号数组拆分）整体判非法 0 搬；②显式空串参数绑 @("")、单元素数组按值展开为假,值真伪判定 $x -and $x.Count 静默跳过整段 exit 0；③[int] 数值匹配令前导零别名 L02 撞上 L2 的条目块
- root_cause: in-process 心智模型套在外部进程调用上：参数绑定/拆分/真伪展开语义在 pwsh -File 边界处不同；夹具只用进程内单 id 形式,从未按文档示例的真实调用形态跑 subprocess
- rule: 给脚本加参数时：文档里承诺的每种调用形态都要有 subprocess 级夹具逐字照跑（尤其逗号列表/空值/别名）；参数在场性用 $PSBoundParameters.ContainsKey 判,不用值真伪；id 类输入收规范形式并逐字串匹配,不做数值归一匹配
- enforced_by: 
- refs: T40-ARCHIVE-LEDGER-EXT PR#129 R3 r2/r6/r7；scripts/archive.ps1 F1/F10/F11 注释

## L143
- date: 2026-07-22 ｜ tags: durability,archive,atomic-write,r3 ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: T40 两轮 R3 往返
- symptom: 对 append-only 冷存文件做就地重写（读行→改→Set-Content 直写）被 R3 连环点穿三层丢失窗口：①先写源后写目的地=目的地失败时搬运块两边皆无；②Set-Content 截断先于写入=中断/盘满毁掉早已删源的既有归档条目；③两侧并存自愈不比内容=陈旧归档盲删在册较新块
- root_cause: 把「重写整个文件」当原子操作:实际是截断+逐写两步；搬运类操作的持久化顺序/中断窗口/内容分歧未按状态机推演
- rule: 重写 append-only/权威存储一律:暂存旁路临时件写全→同卷原子 rename 替换→目的地确认落盘后才动源；目的地先行、源后删；恢复分支比对完整内容一致才自愈,分歧即拒改留人工；每层配确定性注入夹具(tmp 路径同名目录跨平台必败,只读文件注入在 Linux rename 会成功不可靠)
- enforced_by: 
- refs: T40-ARCHIVE-LEDGER-EXT PR#129 R3 r2/r3/r5；scripts/archive.ps1 F3/F5/F9 注释；selftest 12e ⑦⑨⑩

## L144
- date: 2026-07-22 ｜ tags: dod,review,r3,self-proof,sandbox,cards ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 卡片改动脚本/守卫行为时,R3 因自证缺口连环 block:开卡未预算自证落点(全无自证)/自证塞了 selftest 整套却在评审沙箱 ConstrainedLanguage 下无法执行/dod_assert 声明 N 项改动但 dod_command 只机检一两项——三态本质都是评审者拿不到可复跑证据
- root_cause: R3 立场是不确定即 block:dod_command 是评审唯一可复跑的证据来源,任何未预算自证/自证不可复跑/自证不覆盖声明的缺口都等价于无法确认,评审只能 block;开工时若不把自证当契约的一部分,这类缺口要到评审才暴露、每类各花一轮返工
- rule: 开卡即把自证当契约一部分预算好,而非等 R3 block 才补:①凡卡改脚本/守卫行为,allow_paths 从一开始就含自证落点(selftest 子闸或脚本内建 seeded 自检),卡 hygiene 写明自证形态;②dod_command 只放沙箱可复跑的轻量静态断言(Select-String/Test-Path 类),重型套件(selftest/集成)的强制点留给 CI 闸、在 dod_assert 里注明即可,嵌套 pwsh 一律加 -NoProfile(受限语言模式加载 profile 即崩);③dod_command 逐项覆盖 dod_assert 每条声明,写卡时自问评审能对每条声明复跑什么
- enforced_by: 
- refs: L57+L60+L62 合并簇, 明细见 specs/archive/lessons-archive.md

## L145
- date: 2026-07-22 ｜ tags: r3,review,cards,worktree,scope ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: T41 一轮伪 block 往返
- symptom: 中途扩卡 allow_paths（改 master 上的卡文件）后重 ship，确定性范围闸过了，R3 却按旧范围 block「越界改动」——评审读到的是分支上的旧卡副本
- root_cause: 同一张卡双读者双来源：task.ps1 范围闸从主检出（$RepoRoot）读卡=拿到 master 最新修订；review.ps1 把卡注入 R3 prompt 时读被审分支里的副本=分支 fork 后未再同步——中途卡修订产生 split-brain
- rule: 中途修卡（扩 allow_paths/改产出节）一律：改 master 上的卡 → commit+push → 立刻 git merge master 进任务分支再重 ship，令 R3 读到的分支副本与主检出一致；卡体散文与 front-matter 同步改（评审也读卡体，只改 front-matter 会留自相矛盾句被点名）。**与 RED 证据闸的冲突见 L148**：本条的 merge 会制造 post-RED 提交、令 ship 判证据陈旧——若尚未跑 -Phase red 就先 merge 再 red；已经 red 过了就按 L148 的软恢复（reset --soft origin/master + stash + 重铸 red + stash pop）
- enforced_by: 
- refs: T41-TD88-W4-EVALCUT PR#130 R3 r3；L18（卡 meta 走 main）的中途修订补充

## L146
- date: 2026-07-22 ｜ tags: codex,subagent,windows,tooling ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: codex 系子代理（如 codex:codex-rescue）报 Codex CLI is not installed 并要求 npm install -g @openai/codex，但本机 codex --version 明明可跑（实测 0.144.6）。
- root_cause: 该插件子代理只带 Bash 工具，而 npm 在 Windows 装的 codex 是 PowerShell 包装（Get-Command codex 的 .Source 指向 codex.ps1），Git Bash 解析不到该 shim → 误报未安装。与 L17 同源：.ps1 归 PowerShell、不归 Bash。
- rule: 在 Windows 上别靠 Bash-only 的 codex 子代理拿第二意见；直接用 PowerShell 跑 codex exec（与 review.ps1 R3 同一条路径）：Get-Content <promptfile> -Raw 管道给 codex exec -s read-only -C <repo> -m <model> -c model_reasoning_effort=<v> --ignore-user-config --output-last-message <outfile>，只把 outfile 读回主上下文（大体量 transcript 不进主上下文）。判后端在不在，用 Get-Command codex 看 .Source，别信子代理的自述。**评审与派工的参数不同，别照抄**：评审（只读拿第二意见）= -s read-only + --ignore-user-config（hermetic）；派工（让它改代码）= -s workspace-write 且**必须去掉 --ignore-user-config**——该 flag 连 codex 的可信目录清单一起丢，沙箱被降级回 read-only、-s workspace-write 形同虚设。**最坑的是它静默**：实测启动横幅打 sandbox: read-only、补丁被拒、一个字没落盘，**进程退出码仍是 0**，不看 git diff 就会把「什么都没干」当成功收下（L139 亲验的又一例）。另 -C 须指向 git 仓/可信目录，否则 Not inside a trusted directory and --skip-git-repo-check was not specified. 直接退出 1。
- enforced_by: 
- refs: 

## L147
- date: 2026-07-22 ｜ tags: review,github-status,concurrency,r3,merge ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: review.ps1 退出码 0、日志「裁决: pass」、已回贴 codex-review=success，但数秒后同一 sha 上又出现一条 codex-review=failure（600s 超时文案），组合状态遂变 failure；同时 worktree 的 .review/<branch>.json 被改写成 block。看徽章会以为 R3 挂了，实则本轮评审真通过。
- root_cause: 同 context 的 commit status 后写覆盖先写（与 L92 同源：status 按 sha 附着、同 context 内最后一条为准）。本机另有 Codex 侧自动化会自行跑 review.ps1 -PostStatus 并按默认 600s 超时回贴 failure（2026-07-22 经用户确认为其本地工具）；此外 codex 评审者会在被审 worktree 里自行跑全套 selftest，codex 进程退出后该 selftest 子进程未随树被杀、成孤儿继续跑数分钟。
- rule: 判 R3 结果只认三样：本次 review.ps1 运行的退出码 + 该次运行日志里的裁决行 + 裁决 sha 等于待合 head；不认 PR 徽章、也不认 .review/<branch>.json——两者都可能被并发写者覆盖。合并前照 L92 复核 head sha 与文件清单；若见 success 之后还有更晚的 failure，先用 Get-CimInstance Win32_Process 找父进程已消失的 selftest.ps1/review.ps1 孤儿杀掉，再判。并发写者来源已查实：本机 Codex 侧自动化（models 缓存注册着 `codex-auto-review` 档；用户 2026-07-22 确认为其本地工具），**不是** selftest 跑在 worktree 里造成的——在任务 worktree 内跑全套 selftest 仍是本仓既有做法，不禁。
- enforced_by: 
- refs: 

## L148
- date: 2026-07-22 ｜ tags: tdd,red-evidence,ship,worktree,recovery ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 4
- symptom: -Phase ship 在「RED 证据闸」失败：证据 sha 与当前 HEAD 不符（陈旧/伪造证据），saga 报告只完成「卡校验」腿。实现明明是对的、DoD 也绿。
- root_cause: RED 证据把 -Phase red 当时的 HEAD 钉死；ship 要求证据 sha == 此刻 HEAD。而正常流程里实现应当**留在工作区不提交**，由 ship 自己的「提交」腿落盘——任何 post-RED 提交（含 git commit 实现、含按 L145 把 master merge 进分支）都会让 HEAD 前移、证据变陈旧。**L145 与本闸直接冲突**：L145 教你中途改卡就 merge master 进任务分支，照做即制造 post-RED 提交。
- rule: 顺序反过来：**先** merge master / 改卡 / 对齐基线，**再**跑 -Phase red，然后实现但**不提交**，直接 -Phase ship。已经撞上了就按未推/已推分流恢复——未推分支（gh pr list 与 rev-parse origin/分支 均空）用**软**恢复、别用 reset --hard：git -C 该worktree reset --soft origin/master（HEAD 归位、改动全留在暂存区，此时 diff 恰好只剩本卡文件，merge 进来的 master 提交自动从 diff 里消失）→ git stash push → -Phase red 重铸证据（此刻 DoD 必须真红，故须先 stash 掉实现）→ git stash pop → -Phase ship。已推分支改走 TD85-RESUME 的 merge-safe 分流，勿 reset。**（复发 2，T48-TD88-W10 补）常见简化形态**：若实现一直**未提交**（正常流程本就如此），分支便没有自己的提交，`git merge origin/master` 是一次**快进**——此刻 HEAD 已等于 origin/master，`reset --soft origin/master` 是 no-op，恢复缩成 **stash → -Phase red → stash pop → ship** 三步。先 `git rev-parse HEAD` 与 `git rev-parse origin/master` 比一下再决定要不要 reset：相等就别 reset（省一步、也不会误伤）；不等才说明分支有自己的提交，照上面的软恢复走。**另注**：撞上这闸时别急着怀疑证据被伪造——最常见的成因就是照 L145 补了一次 master 合并，属流程顺序问题，不是证据问题。
- enforced_by: 
- refs: （复发 3，2026-07-27 T57-TD110-TD100-WFCOST）新向量=编排者手写子代理派工提示词：未先检索本条、明令「实现完在卡分支上 commit」，子代理照做制造 post-RED 提交 → ship 死于证据闸；未推分支按 TD85-RESUME reset --soft 回 RED sha 后原样重 ship 即愈。给子代理的 task-loop 派工提示词必须写明「实现留工作区不提交，ship 自带提交腿」

## L149
- date: 2026-07-23 ｜ tags: grep,counting,recon,scoping,powershell ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 据 grep 计数下了范围/收益判断，事后发现数字错得离谱：本弧一次把 selftest.ps1 的建仓点数成 9 处（真值 40）、据此对外宣称「删除清单高估了、B4 不值得做」，实为**低估**；同弧另两次误读：把 git 的 `warning: in the working copy` CRLF 噪声（一跑 150+ 条）当成 selftest 失败、把自己 grep 命令抛的 InvalidOperation 当成被测脚本的报错。
- root_cause: Select-String 的两个默认行为咬人：①**模式太窄**——`git init` 只命中字面量，漏掉主流写法 `git -C $path init`（同类：`-Path` 与管道、单双引号、变量插值形态）；②**默认不分大小写**——`WARNING` 会连 git 的小写 `warning:` 一起收。再叠加「拿计数当结论」而没做一次反向验算（如换个更宽的正则复核、或抽样人眼核对若干命中/未命中行）。
- rule: 计数要用来做决策时，**先证明模式覆盖面**再引用数字：①同一目标至少换两种正则复核（窄的+宽的），数字不一致就以宽的为准并人眼抽查；②要区分本工具输出与被测输出时加 -CaseSensitive（本仓核 selftest 结果尤其：Fail 走 Write-Warning 打 `WARNING:`，git 噪声是小写 `warning:`）；③命中数为 0 或异常小，先怀疑自己的模式，别急着下「不存在/不值得做」的结论；④报出去的数字标明所用正则，便于他人复核（本条已让一次范围判断反了向）。
- enforced_by: 
- refs: 

## L150
- date: 2026-07-23 ｜ tags: dod,refactor,false-green,selftest ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: W7 卡的 DoD 第三条断言「add specs/tasks/ 出现数 8→2」，实现落地后实测是 1：helper 里把路径写成带引号的 "specs/tasks/<插值>.md"，该行不再被 add specs/tasks/ 模式命中。但 DoD 写的是 -gt 2 的松边界，1 照样过闸——闸绿了，卡上算式却是假的，且该断言实际已弱化成「不多于 2」（helper 根本不做 git add 也照样过）。
- root_cause: 结构性计数 DoD 用不等式当阈值时，只能证「不多于 N」，证不了「恰好塌成预测的那个结构」；而抽取重构本身会改动被匹配文本的形态（加引号、换插值、改路径写法），使实际计数偏离卡上预测值，偏低的一侧正好被松边界静默吸收。减法波的 DoD 普遍是这个形状（W6 用 symbolic-ref -gt 13、W7 用 add specs/tasks/ -gt 2），故这是会复发的类型缺陷而非一次手误。
- rule: 卡片 DoD 断言结构性计数时：①钉精确期望值（-ne N）而非松不等式，让「多了」和「少了」都能翻红；②实现后把实测计数与卡上预测值逐条对账，相等才算证成——闸绿不等于预测成立；③抽取重构里 helper 的新写法优先与原站点逐字同形（别顺手加引号/换插值），否则它就从原模式里消失、静默削弱断言；确需改形态就同步修正卡上预测值；④**卡上每条计数必须配「逐字可复跑的取数命令」，不能只写正则片段**——计数对模式极敏感（W9 实测同一棵树：`\bFail ` 是 776、`Fail ` 是 854、`\bFail\b` 是 941），复核者按自己的理解换个模式就得出别的数字，并据此**正当地**以「实现与卡漂移」block；此时该修的不是数字而是证据形式，另附一张口径对照表说明「数出别的数是模式不同、不是漂移」。自检一句：把实测数字念出来，和卡上写的那个数字比一比；再问一句：别人照卡上写的能不能一字不改地复跑出同一个数？
- enforced_by: 
- refs: 

## L151
- date: 2026-07-23 ｜ tags: judgment,gates,seeded-defect,false-bug-claim,recon ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 差点让一条假理由进 PR 与 R3；自查发现后返工改卡+额外一次提交
- symptom: 比对两处实现发现「能力不等」（如 check-cards 的 Get-YamlListItems 支持行内列表、task.ps1 手写行走器只支持块式），据此推断出一个后果（「合法卡会在 ship 被 fail-closed 卡死」）并当作真 bug 写进任务卡与提交讯息。实际上该分歧早已被发现并**有意**处理：selftest 闸 10d(行内) 正断言 check-cards 必须拒绝行内写法。假论断差点随 PR 送到 R3 面前。
- root_cause: 在已加固的代码库里，两处实现的**不对称往往是设计**而非疏漏，且其理由通常就写在对应闸门的旁注里。而我从「实现差异」直接跳到「后果推断」，跳过了「这个差异是否已被现有闸门覆盖」这一步——种子缺陷闸恰恰是本仓存放「为什么是这样」的地方，不查它就等于绕开了设计记录。
- rule: 声称「A 与 B 的分歧构成 bug」之前，先在闸门套件里搜证：拿涉及的标识符/症状词 grep selftest 的种子缺陷闸与断言文案（本仓尤其是 10x 系列），读一眼命中闸的旁注——它多半直接写着这个分歧是何时发现、为何选了当前处理方式。命中即说明分歧是有意的，你要判的就不是「修不修 bug」而是「要不要改变既有取舍」，二者送审的论证完全不同。查不到才可以按真 bug 论，并把「已搜过闸门套件、无覆盖」作为论断的一部分写出来。
- enforced_by: 
- refs: 

## L152
- date: 2026-07-23 ｜ tags: refactor,dedup,security-gate,strictness,single-source-of-truth ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: R3 一轮 block；若放行则 ship 范围闸留下可被畸形卡绕过的越界放行路径
- symptom: 把 N 处重复实现收敛进共享库时，选定其中一处当「真相源」、其余向它对齐。结果被替换掉的那个副本其实在两个维度上**更严**（只认缩进列表项 · 任何非缩进行即终止列表），而真相源更宽——收敛后 ship 范围闸（安全闸）能被畸形 front-matter 骗出一条越界白名单路径。全套 selftest 照常全绿，是 R3 对抗评审逐行读出来的。
- root_cause: 「单一真相源」重构隐含假设「被选中的那份是最严/超集的」，但重复实现往往守着**不同表面**——一份用于**校验**（要能看见畸形写法才能拒绝它，故必须宽），一份用于**执行/放行**（必须窄且 fail-closed）。宽窄需求相反时，非规范的那份才是严的一方。而这种严格性通常是**顺带得到、从未被任何断言钉住**的，所以收敛把它抹掉时没有任何测试变红——沉默即通过。
- rule: 收敛重复实现前，先对每对副本做**行为差分而非文本差分**：拿畸形/对抗输入（多余空行、非缩进垃圾行、行内 flow 写法、缺冒号、混合缩进）各喂一遍，把「谁更严」逐条列出来。任一副本更严，先问「它守的是校验面还是执行/安全面」——守执行面的，其严格性**必须保留**（做成独立且显式命名的函数，如 Get-YamlBlockListItems，并在注释里写明这个不对称是有意的、勿再合并），并**在收敛的同一个 PR 里把它钉成断言**。判据一句话：**没有断言钉住的严格性，收敛时一定会静默消失**——所以边保留边补测，而不是保留了就算完。
- enforced_by: 
- refs: 

## L153
- date: 2026-07-23 ｜ tags: r3,review,task-card,worktree,ship ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 两轮 R3 白跑（每轮含全套闸 + 1800s 评审预算）
- symptom: ship 期发现卡片写错/过时，于是在**主检出**改卡并推 master，然后重跑 ship——R3 却仍按**旧卡**判，连着两轮以「实现与卡不符（可追溯性维度）」block，reason 里引用的正是已被改掉的旧数字与旧函数名。人会误以为评审在胡说或在缓存裁决。
- root_cause: `review.ps1` 取卡的路径是 `Join-Path $WorktreePath "specs/tasks/<branch>.md"`——**从被审工作树（分支）读**，不是从主检出读。而 `task.ps1` 的 `$Card` 是 `Join-Path $RepoRoot ...`（$RepoRoot 由 $PSScriptRoot 派生 = 主检出）。两者取卡来源不同：DoD/check-cards 看到新卡、R3 看到旧卡。卡分支是在建卡那一刻从 master 切出的，此后主检出对卡的任何修改都不会自动进分支。
- rule: 在 ship 期改了任务卡，**必须把改动带进卡分支**再重 ship，否则 R3 永远按旧卡判。做法：在卡 worktree 里 `git fetch origin && git merge origin/master --no-edit`（禁 rebase，watershed 后不改历史）。这样卡文件在分支上与 origin/master 一致，`origin/master..HEAD` 的 diff 里**不会出现**该卡（也不会撞范围闸的 allow_paths），R3 却能读到新卡。核验一句：`git -C <worktree> diff --name-only origin/master..HEAD` 应恰为 allow_paths 那几个文件，且在 worktree 的卡文里 grep 得到你刚改的那个数字。
- enforced_by: 
- refs: 

## L154
- date: 2026-07-23 ｜ tags: dod,selftest,structural-count,self-reference,comments ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1 ｜ cost: 一轮 ship 白跑（DoD 闸拦下，未到 R3）
- symptom: DoD 用「某字面量在被扫文件里恰出现 N 次」当结构性断言，实现全部做完、selftest 也全绿，随后**只加了一条解释性注释**，ship 却在 DoD 闸红了——因为那条注释里为了讲清楚断言而**写出了被计数的字面量本身**，出现数遂 N→N+1。
- root_cause: 结构性计数断言把「代码里的实现」与「注释里对它的描述」一视同仁——扫的是文本，不是语义。于是「解释这条断言」这个动作本身就会破坏这条断言，形成自指陷阱。越是想把断言写清楚、越容易在注释里引用那个串，故这个坑对「写文档写得认真」的人命中率更高。
- rule: 设计结构性计数 DoD 时，选**不会出现在散文里**的指纹串（带代码上下文的片段，如函数签名前缀、含转义的正则片段），别选短到会被自然引用的串；写注释解释该断言时，**只描述、不写出**那个字面量（「front-matter 正则指纹串」而非把串抄进来），并在注释里留一句「此处刻意不写出该串」提醒后人。落卡前自检一句：如果有人要在同一批被扫文件里解释这条断言，他会不会不得不写出这个串？会，就换串。
- enforced_by: 
- refs: 

## L155
- date: 2026-07-23 ｜ tags: subagent,review,sandbox,encoding,false-fail,selftest ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: W9 一轮第二意见 block；若照单全收会去修不存在的缺陷或弱化 17z 断言
- symptom: 派给评审/第二意见子代理（codex 等）的复核任务里，子代理在**自己的沙箱**跑本仓 selftest，得到某闸 FAIL / exit 1，据此给出 block 裁决。而同一份工作树用主检出的 PowerShell 工具实跑是 `selftest: PASS` exit 0、该闸逐条 OK。若照单全收，就会去「修」一个不存在的缺陷，甚至为了让沙箱变绿而弱化断言。
- root_cause: 子代理沙箱的控制台编码/标准流捕获与本机 pwsh 不同源，本仓大量断言文案是中文、且有闸专门比对超时/告警文案，编码一错就命中假 FAIL——与 L17 记的「Bash 工具跑 .ps1 产生假 FAIL」是同一类，只是载体从 Bash 换成了子代理沙箱。子代理并不知道自己的环境是次等证据源，会把沙箱结果当事实写进裁决。
- rule: **长脚本的绿证只认主检出 PowerShell 工具的实跑**；子代理沙箱里跑出的 FAIL 一律先当环境嫌疑，不当缺陷。处置三步：①在主检出用 PowerShell 工具重跑，抓结论行 + 退出码 + 该闸的逐条 OK 行作为反证；②反证成立就**明确驳回**该条裁决并把证据写进 PR/卡（不是忽略，是举证驳回）；③派工时就写清楚「只做静态复核，不要跑全套 selftest；需要绿证时以我给的日志为准」，从源头省掉这轮往返。注意这不违反「绝不为过闸而让评审者少验」——被驳回的是**环境产物**，不是评审意见本身。
- enforced_by: 
- refs: 

## L156
- date: 2026-07-23 ｜ tags: r3,review,codex,convergence,non-determinism ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: R3 连续两轮 block 同一处，且第二轮要求与第一轮自己给出的修改建议直接冲突：r1 说压缩丢了合规断言并给出改写措辞，照它改后 r2 反过来要求逐字还原、把指针移出该块。机械照最新一轮改，就会在两个要求之间来回震荡、永不收敛。
- root_cause: R3 是有意接受的非确定闸（QUALITY-RUBRIC 明写裁决可跑跑不同），每轮独立采样，各轮 reasons 之间没有任何一致性保证。把「最新一轮的指示」当唯一权威，等于让评审在自己历次意见间震荡；而每轮单独看又都言之成理，容易一路照做而不自知。
- rule: 多轮 R3 对同一处给相反指示时，别照最新一轮改——把各轮 reasons 当【约束集合】，找同时满足全部约束的解。本例：逐字还原原文（满足 r2 的一字不改）+ 把附录指针挪到承诺块之外（满足 r1 的合规断言不可丢），两约束本就兼容。送审前先机验该解对每轮的可检验部分都成立（如与原文逐字比对 4/4）。若约束集真的不可同时满足，那才是 L53 式结构性墙或 maker-checker 僵局 → 停手排队人裁（执行边界：同一争点两轮互不认可即停），别继续循环讨好评审。开始第二轮修复【之前】就把停点写进交接，免得事后自我说服再来一轮。
- enforced_by: 
- refs: 

## L157
- date: 2026-07-23 ｜ tags: codex,subagent,diff-review,encoding,mutation-test ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 3
- symptom: 派 codex 子代理按逐字 brief 改三处文件：三处插入全对、DoD 计数断言绿、parse 退出 0，但 diffstat 是 9 insertions/2 deletions 而非应有的 3/0——它静默剥掉两个 .ps1 的 UTF-8 BOM 并给三文件各追加尾部空行。同会话另一次：自写变异测试用 CR-LF 转义串拆行，转义经工具层被吃掉，实际删掉 222 行而非 1 行，selftest 照样如期 FAIL，差点把这个假红当成变异证据收下。
- root_cause: 机检只看它被设计去看的那一面：DoD 是计数断言、闸 1g 只查 dot-source _encoding.ps1，两者都看不见 BOM 与尾部空行；退出码只说失败，不说因为哪一处失败。而写方（子代理、或被 shell 吃掉转义的自写脚本）实际落盘的改动面可以远大于 brief 要求的那一面。
- rule: 凡不是自己逐字敲下的落盘改动（派子代理改的、脚本批量改的、变异测试改的），先 git diff --stat 对账「实际 insertions/deletions == 预期行数」，不符就逐行读 diff，再谈 DoD 与退出码。变异测试尤甚：先证只改了那 1 行，再跑长套件，否则如期变红可能红在别处。批量改行用 Get-Content 行数组 + Where-Object 过滤，别自拼换行分隔符（转义未必活着穿过工具层）；回写用 System.IO.File.WriteAllText 显式指定编码以保 BOM。
- enforced_by: 
- refs: 

## L158
- date: 2026-07-25 ｜ tags: selftest,gate,dod,vacuous ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 2 ｜ cost: T52 建卡后返工改钉一轮
- symptom: 建卡时把 DoD 的 selftest 子句钉在「闸10e」，而 10e 早被 TD63 的 block-scalar 种子缺陷占用——该子句遂被既有闸满足，新子闸一行没写也能绿（vacuous）。
- root_cause: selftest 子闸标签是手工自增的字母命名空间、无唯一性机检；建卡时凭印象取下一个字母就会撞已占用标签，而 DoD 常用的「存在性计数 >=1」在撞标签时恒真。
- rule: 新增带字母/编号的 selftest 子闸前，先全文件枚举已占用标签取首个空位（如对 selftest 全文跑 regex 15[a-z] 去重再挑）；**DoD 里钉的闸标签，其建卡时计数必须为 0**，非 0 即换标签，否则该子句被既有闸满足即 vacuous。计数口径对照表的左值须如实记 0 并附可复跑取数命令。
- enforced_by: none（建卡期人工纪律；DoD 计数表左值=0 + 变异证据是其自证）
- refs: T52-TD111-CARD-TOKEN-GATE 改钉 10e->10g（commit dd83dfb）；T53-TD93-SCOPE-CHECKER 按此法取 15s；同族 L19/L83

## L159
- date: 2026-07-25 ｜ tags: powershell,regex,validator ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: R3 一轮 block
- symptom: 新校验断言声明「只拒大写蛇形形态」，实现写 -match 加字符类 A-Z，实际把小写与混合形态一并拒掉（过度拒绝），R3 首轮抓出。
- root_cause: PowerShell 的 -match/-notmatch/-replace/-split 默认**大小写不敏感**，故字符类 A-Z 同样匹配小写——与多数语言的正则默认相反，只读代码看不出来。
- rule: 契约里含大小写语义的匹配一律用 -cmatch/-cnotmatch/-creplace（或 [regex] 显式选项）；并**必配负夹具**（小写/混合形态须放行）证明没有过度拒绝——只写正夹具的断言对大小写敏感性完全盲。同理：核 selftest 结果 grep WARNING 时要加 -CaseSensitive。
- enforced_by: selftest 闸 10g（大小写负夹具）
- refs: T52-TD111 R3 r1 #14；scripts/check-cards.ps1 占位符字面量断言；selftest 闸 10g 三 case 夹具（大写拒/小写放/混合放）

## L160
- date: 2026-07-25 ｜ tags: validator,card,dogfood ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 同一卡内四次自伤、每次都要改写卡文
- symptom: 加「卡文不许出现某字面量」的校验闸时，为解释规则而在卡文/实现注释/夹具里写出被检字面量，闸一上线就拒掉自己这张卡（本卡四次踩到）。
- root_cause: 新校验闸的扫描面通常**包含承载它的工件自身**（新卡就在 specs/tasks 下、被 check-cards 扫；脚本注释落在自己的 grep 面内），而写规则时人本能地举例。
- rule: 加任何校验闸前先问「它的扫描面包不包括这张卡/这个脚本自己」；包括就只用**文字描述或转义形态**表达被检模式，实现后立刻拿新闸跑自己的卡（dogfood）；夹具里的被检字面量一律**运行时拼接构造**，别在源码留成品。同族反向坑见 L154（解释断言的注释本身让计数 +1 把 DoD 弄红）。
- enforced_by: check-cards 该断言自身（新闸 dogfood 即自证）
- refs: T52-TD111-CARD-TOKEN-GATE 卡的 hygiene 段与闸 10g 夹具构造

## L161
- date: 2026-07-25 ｜ tags: review,evidence,mutation ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: R3 多一轮 block + 重跑全套变异实验
- symptom: 重 ship 时 R3 唯一 block = 卡内变异证据表钉的文件哈希与被审 HEAD 对不上：哈希是上一轮修订**之前**跑的，上一轮的纯注释精简改了字节即令其失效，评审按「证据不可验证」正确拦下。
- root_cause: 变异证据是「对某棵确切的树做过一次实验」的记录，可验证性绑定在文件字节上；任何后续改动（含不改行为的注释/格式）都让它与被审树脱钩，而人只记得「行为没变、证据还算数」。
- rule: 写进卡的变异证据/哈希/精确计数都是**对某个确切 HEAD 的声明**：该文件此后任何改动之后必须按新 HEAD 重跑并更新证据，再 ship；重 ship 前对着**交付树**逐条复核证据表，绝不复用上一轮的数字。多条子断言各配各的变异实验（单次变异只证一条非 vacuous）。**推论：变异批在飞时被测文件冻结**——哪怕补一行注释也会让批的基线漂移（runner 的 ExpectedBaseline 会拒跑，或更糟：批已过基线检后改动被还原腿静默回滚）；要改就先停批、重钉基线、再重启（2026-08-05 T56 r17 实测险踩：批飞行中补注释，靠「停批（GREEN 期零植入）→ 重钉基线 → DryRun 全靶复验 → 重启」归零损失）。
- enforced_by: none（R3 评审判据：证据须可在被审 HEAD 上复现）
- refs: T52-TD111 R3 r3 #6（按真交付 HEAD 重跑 clean+变异并更新）；T50 的 15q 两条子断言各做一次变异；同族 L157

## L162
- date: 2026-07-25 ｜ tags: python,windows,encoding,tooling ｜ tier: ledger ｜ kind: pitfall ｜ severity: blocking ｜ recurrence: 1
- symptom: 在 Windows 上跑第三方/插件 Python 工具读本仓含中文的 JSON/MD 时崩 UnicodeDecodeError: charmap codec cant decode byte 0x81；设 PYTHONIOENCODING 不解决。同批还撞到：把启动 server 的 Python 脚本接管道（| head）后启动横幅/URL 永不出现，看起来像静默失败
- root_cause: Windows 上 Python 的 open() 默认用 locale 编码 cp1252 解码文件；PYTHONIOENCODING 只管 stdout/stderr 不管文件读取，故与 L31 不同源、套 L31 的解法会白试。管道场景下 stdout 从行缓冲变块缓冲，进程不退出就什么都不吐
- rule: 调第三方/插件 Python 工具读本仓文件一律前置 PYTHONUTF8=1（UTF-8 模式，改的正是 open() 默认编码）；自己写的脚本 open()/write_text 显式 encoding=utf-8。要即时看到长驻进程的启动横幅就用 python -u 且别接管道
- enforced_by: none（触发点是对第三方/插件 Python 的临时命令行调用，不在本仓 tracked 脚本里，无稳定机检面可挂；本仓自有 .py 由 code review 与显式 encoding 约定覆盖）
- refs: 

## L163
- date: 2026-07-25 ｜ tags: testing,assertions,review,eval ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 判「某条指令是否已从改写产物里删掉」的断言两个方向都判反：基线明明删了却被判成保留（匹配到的其实是否定式 Do not spawn a subagent），另有改写成同义句、约束原封不动的却被判成已删（原句 double-check your arithmetic 变成 recompute a second time and confirm the two passes agree）
- root_cause: 断言匹配的是名词短语/词汇，而「删没删」取决于祈使强度与语义：否定式复用同一批词汇 → 假阳性；换措辞保留同一约束 → 假阴性。词汇层匹配对「存在性」够用，对「已移除」不够用
- rule: 写「X 已被删除」类断言时匹配祈使/强制形态而非名词短语（always include a final verification、use a subagent to verify），并同时喂两条反向夹具：否定式写法 + 同义改写各一遍，确认既不误报也不漏报，再信它的判定。与 L157 同源——不是自己逐字敲出的判定，先证它正反两侧都判对
- enforced_by: 
- refs: 

## L164
- date: 2026-07-25 ｜ tags: gates,security,refactor,trust-boundary ｜ tier: ledger ｜ kind: pitfall ｜ severity: blocking ｜ recurrence: 1
- symptom: 把 ship 内联的 fail-closed 范围闸抽成共享核 + 独立 CLI 后，判定语义逐条搬对了、既有种子闸也继续绿，但新入口连吃 R3 十一轮 block：缺省基线取到卡分支自己（空 diff 印 PASS）、缺省工作树缺失即静默回退主检出、判定尖端跟着 -Path 那个检出的 HEAD 走、allow_paths 读的是被审分支自己的卡、连检查器脚本本身都可被被审分支替换成恒 PASS。
- root_cause: 原入口的安全性有一大半不在它的代码里，而在**它被规定的运行位置**：L86 强制相位命令只在主检出跑，于是「判定对象是谁、标准取自哪份卡、跑的是哪一份检查器」全都被运行位置隐式钉死。抽出第二个入口时只搬了可见的判定语义，这些隐式绑定一条都没跟着走——多一个入口就多一条绕过路径，而每条都表现为 fail-open（印 PASS），比没有这个闸更坏。
- rule: 给已有 fail-closed 闸增设第二个入口（独立 CLI / 恢复序列 / CI 腿）前，先把原入口「靠运行位置白拿的」信任绑定逐条列出来并在新入口显式补齐：①判定对象锚定到不可变标识（按卡 id 取分支引用，不看该检出的 HEAD）②判定标准取自受信基线（git show <baseRef>:卡 路径，绝不读被审检出里的副本）③检查器与其依赖须来自受信检出（跑主检出那份、用 -Path 指被审树）④入参只收纯名/完整 OID 并按提交身份判等（拒 git revision 语法与前缀匹配）⑤解析成 sha 后全程钉 sha，不再用可变引用名（防 TOCTOU）。每条各配一个 ASCII 哨兵与一枚只删该句的变异，证明它承重。
- enforced_by: scripts/selftest.ps1 闸 15s 的 [SCOPE-NOTIP]/[SCOPE-NOCARD]/[SCOPE-SELFBASE]/[SCOPE-BADBASE]/[SCOPE-NOWT]/[SCOPE-TIPDIVERGE]/[SCOPE-TIPMISMATCH] 哨兵族 + 变异 C-U
- refs: 

## L165
- date: 2026-07-25 ｜ tags: testing,vacuous,mutation,gates ｜ tier: must ｜ kind: pitfall ｜ severity: blocking ｜ recurrence: 2
- symptom: 同一张卡里「断言看起来在测 X、实际没测 X」连出四次：①断言写在**整份 stdout** 上，而被测命令在判定前先打印改动清单，那条路径无论判定如何都在输出里 ②断言匹配**中文结论行**，父进程 stdout 被重定向时解码成乱码，六个 case 在别人机器上齐红而我连跑六次全绿 ③断言只数文档里**关键词出现次数**，而周围散文本就含那些词，把真正的可执行守卫整段删掉照样绿 ④不符用例传**全零 OID**，于是停在「解析不出提交」那一支，根本走不到它声称要测的身份比对那句。**第 2 次（T56 r17 批，2026-08-05）：变异分类器自己犯②**——gate 锚带一个「闸」字、红面正则锚「闸17t(」，批改派 schtasks 后 OEM 码页把中文打成 '?'，六枚真红被误判 NOT-OK；改纯 ASCII 锚时又差点掉进③（裸 '17t(tXX)' 会把 t16 半覆盖信息行误计红面），红面行判别改锚 'WARNING: ' 前缀（L149）才闭合。
- root_cause: 断言落在了**比被测契约更宽的表面**上：整份输出 ⊃ 判定行、中文文案 ⊃ 稳定标识、关键词出现 ⊃ 可执行命令、任一非零 ⊃ 该守卫拦下。宽表面在被测契约还成立时当然绿，于是看不出问题；一旦契约被摘掉，宽表面仍可能因别的原因满足，断言就静默失效。人写断言时脑子里想的是契约，手上写的却是「输出里有没有这个字符串」。
- rule: 断言面必须**恰好等于**被测契约，且用一枚只删该契约那一句的变异来证明：①只比对**判定行**（先按稳定标识切出那一行再匹配），不比对整份输出 ②机检一律认 **ASCII 哨兵**，本地化文案只给人读（编码链一变中文断言就假红/假绿）③文档契约锚到**可执行命令行形态**（行首 + 真实命令），不数关键词出现次数 ④「不符/失败」用例必须让被测那一句**真的被执行到**（如身份比对要传可解析但不同的 OID，全零 OID 只测到解析失败那支），并断言输出里有该句独有的证据（如 judged=/expect= 两个值）。**每道守卫配一枚单句删除变异**——它红了才算这条断言真的在测它。⑤**判据提取器（变异分类器/红面正则/日志 grep）也是机检，锚同样纯 ASCII**——连锚里带一个中文字都会在换执行环境（schtasks OEM 码页）时整批失配；行判别锚 'WARNING: ' 前缀（L149），别锚中文前缀，也别裸锚标签（信息行会误计）。
- enforced_by: none（写断言时的人工纪律；其自证机制 = 每道守卫各配一枚只删该句的变异，本卡 A-U 共 21 枚即此法的落地）
- refs: 

## L166
- date: 2026-07-26 ｜ tags: models,references,migration,fallback ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 模型换代时把旧模型的 reference 当废档删掉，并把另一份 reference 里「拒答回退目标」从旧模型改成了新模型——正好改反。表面看是一次干净的换代同步，实则拆了回退链的依据
- root_cause: 把「换代」默认等同于「旧的作废」。但新模型上线不必然让旧模型退役：Fable 5 与 Opus 5 都带安全分类器、会返回 stop_reason refusal，官方默认回退路由（fallbacks default）按拒答类目（cyber/bio/frontier_llm/general_harms，其中 cyber 官方明说良性安全工作也可能触发）把请求改道到推荐兜底模型，当前正是 Opus 4.8。接手请求的是旧模型，所以旧模型那份提示词细则仍是活文档。且这类回退事实不在「prompting-<新模型>」页上，只在 refusals-and-fallback 页，按单页读会整片漏掉
- rule: 删任何模型 reference 之前先证它已停用：查上游该模型页是否仍在线、查 whats-new 是否写着 remains available、查 refusals-and-fallback 看它是不是当前的回退兜底档。仍在服役就只改角色说明（日常档 vs 兜底档）、保留文件与交叉指针。改任何一处「回退/fallback 目标」时，方向要对着上游文档核，别顺着换代的惯性把它一起升版
- enforced_by: 
- refs: 

## L167
- date: 2026-07-26 ｜ tags: testing,mutation,evidence,vacuous ｜ tier: ledger ｜ kind: pitfall ｜ severity: blocking ｜ recurrence: 1
- symptom: T54 的变异证据连续三批都「全红」却证明不了任何事：①12 枚逐腿变异用 `if ($false) { & $step …` 造成花括号不配对 ⇒ 全在语法阶段就死，exit=1 但无一条目标 Fail 行；②两枚腿把值写进 $script: 变量、腿外读取，删腿后那句读取因 StrictMode「变量未定义」抛异常、整段中止 ⇒ 红在异常而非目标断言；③变异脚本自身把 \" 当转义写进双引号串 ⇒ 脚本 parse 失败；④摘掉共享判定核那一句时，**更早的闸**（15s）先抓住并把 $fail 置真，令后面整个矩阵被 `if (-not $fail)` 跳过 ⇒ 被测闸根本没跑，却极易读成「它覆盖到了」。另有两枚变异「存活」，一枚是断言真的假、另一枚是变异写错而断言本来是对的。
- root_cause: 「跑了变异、它红了」与「我关心的那条断言响了」是两回事，而退出码只报前者。变异实验的失败面比被测代码还多：靶没命中、语法坏了、运行时异常、脚本自身写错、被更早的闸抢先中断——每一种都产出 exit≠0，都长得像成功。人只看汇总里的 exit 码就会把这些全记成「已证明」。
- rule: 变异证据必须带**判据分类器**，只有「非零 **且** 命中**指定的那条断言文本**」才记 OK；`PARSE-ERROR / 存活 / 红在别的断言 / 红但无目标 Fail 行` 一律单列报出，不许并进「全红」。配套四条：①靶字符串不在文件里即 **throw 中止**，绝不静默 no-op；②变异后先跑一次 parser，语法坏了直接判假红；③「存活」先分诊是**断言假**还是**变异假**——后者要改变异、别去削弱一条本来正确的断言；④证明「闸 X 覆盖缺陷 D」前，先确认**没有更早的闸**也抓 D 并提前中断整轮，否则要用**组合变异**（同时短路那道早闸）才测得到 X。
- enforced_by: none（写变异脚本时的人工纪律；其自证机制 = 分类器本身逐枚打标，T54 的 20 枚变异即此法的落地）
- refs: 

## L168
- date: 2026-07-27 ｜ tags: tracker,id-allocation,concurrency,handoff ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 并发会话各自登记新 TD 号：一方在在飞卡 worktree 的未提交 tracker 里占了 TD114/TD115，另一方在主检出按「已提交面最大号+1」也取 TD114 并已推 master——同号两义（improve-prompt eval 覆盖 vs 运行期 verdict schema），且 T56 卡已在 master 引用后者，交叉引用开始发散
- root_cause: TD/L 这类 append-only 序号的分配只看自己检出的已提交状态；登记面实际分布在多个检出（master 工作树、在飞 worktree 未提交 diff、未合并分支），max+1 在并发下不唯一，先来后到无仲裁
- rule: 登记新 TD/L 号前先扫全部在飞面取真实最大号：git worktree list 逐棵 grep 其未提交 tracker/LEDGER，未合并分支用 git show 分支:文件 看新增行；撞号裁定按「已被 master 引用者保号、未提交面改号」，改号必须在原行留改号记录（id 变、内容与发现日不变），并在 handoff 里点名通知占号方
- enforced_by: 
- refs: 

## L169
- date: 2026-07-27 ｜ tags: tracker,archive,status-enum,markdown ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: tracker 状态格写成粗体 paid（前后双星号），archive.ps1 的精确匹配 ^(paid|accepted)$ 判「非枚举值→保守留热表」，该行永不归档且零告警——paid 行静默滞留活表直到人工发现
- root_cause: 机检枚举字段被加了 markdown 视觉装饰；归档器 fail-safe 设计（暧昧即不动）叠加「无告警」，把一次格式手误变成无限期静默滞留
- rule: 机检枚举字段（tracker status 格、卡 front-matter status 等）只写裸枚举值，强调/加粗放行末备注列；排查「该归档没归档」先 diff 状态格字面量与枚举表
- enforced_by: 
- refs: 

## L170
- date: 2026-07-28 ｜ tags: process-scan,concurrency,guard,powershell ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 用 Get-CimInstance 按 CommandLine 正则扫「是否有别的 selftest 在跑」做碰撞守卫：守卫进程自己的命令行就含该正则字面量，恒自匹配 ≥1，等待循环永不放行——后台看门 3 小时窗口全程空等到被杀，机器其实早已空闲
- root_cause: 扫描器把模式字面量写进了自己的命令行（-Command 全文进 Win32_Process.CommandLine），且未排除自身 PID；「有 selftest 字样的进程」把观察者自己也算进去了
- rule: 进程扫描守卫两件套：模式用拼接构造（如 $pat = 前半 + 后半）使字面量不出现在自身命令行；过滤加 $_.ProcessId -ne $PID。验收守卫先空跑一次：机器确认空闲时它必须立即放行，否则就是在等自己
- enforced_by: 
- refs: 

## L171
- date: 2026-07-30 ｜ tags: security,review,symlink,fail-closed ｜ tier: ledger ｜ kind: pitfall ｜ severity: blocking ｜ recurrence: 1 ｜ cost: R3 两轮 block + 两次全量变异重测（约 12h 机时）
- symptom: 守卫检出裁决路径不安全后「自己不写/不删」，却仍把同一条路径设为 $env:REVIEW_OUT 交给评审者子进程——评审者跟着链接把工作树之外的文件覆写；同一错误在第二个站点（陈旧裁决删除处）原样复发（R3 r14 + r16 各抓一处）。
- root_cause: 「拒绝自己的写操作」被误当「守住了这条路径」；把不可信路径交给子进程/下游等于授权它代写，而 fail-closed 判断要等子进程回来才跑，为时已晚。
- rule: 「我不写」不等于「我没让别人写」——凡把路径交给子进程/下游，交之前就得过同一道判据，判不过就不唤起（检出即中止）；同一安全决策的响应抽成唯一函数供所有站点调用，防第二站点漏改。
- enforced_by: scripts/selftest.ps1 闸 17t（t20 叶子预置链接断言评审者未被唤起；t22 唤起前窗口植链）
- refs: T55-TD96-R3-REFUSAL-DIAG R3 r14/r16；变异 LEAFGUARD/PREINVOKE

## L172
- date: 2026-07-30 ｜ tags: powershell,encoding,detached,false-negative ｜ tier: ledger ｜ kind: pitfall ｜ severity: blocking ｜ recurrence: 1 ｜ cost: 一晚变异批次全部假红重跑
- symptom: Start-Process pwsh -NoProfile 起的 detached 进程跑本仓脚本，中文断言/中文判据 mojibake 即假红；变异 runner 的中文 want 串与阴性形态双双被打假（一枚被误判为「红在别的断言」）。
- root_cause: 脱离 harness 的 pwsh 子进程默认走 OEM 代码页而非 UTF-8，编码链与交互会话不同源；中文文本一经比对即失配（L17 同族：编码不同源即假结论）。
- rule: 凡在 harness 之外起 pwsh 跑本仓脚本（detached/计划任务/CI 外壳），前奏必须 dot-source scripts/_encoding.ps1 再执行；机检文本尽量用 ASCII 哨兵（L165），中文只给人读。
- enforced_by: none（启动前奏纪律；scripts/_encoding.ps1 为其载体）
- refs: T55 变异 runner 恢复序列；L17/L165 同族

## L173
- date: 2026-07-30 ｜ tags: git,stash,crlf,evidence ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 一次证据恢复排查（约 1h）
- symptom: 为 merge master 而 stash/pop 被测文件，内容一字未改、git status 只见 modified，SHA256 却全变——29 枚变异证据的字节戳当场作废。
- root_cause: stash/checkout/apply 走 git 内容规范化层（CRLF 到 LF），字节级证据绑定的是磁盘字节而非 git 语义内容；任何经过规范化层的往返都等于字节变更。
- rule: 证据字节戳在场时不用 stash 搬运被测文件——用文件级备份/恢复（先按忽略行尾比对确认内容一致再覆盖，不重测）；凡「哈希/字节戳」类证据，把 stash/checkout/apply 一律当字节变更处理。
- enforced_by: none（证据期操作纪律）
- refs: T55 merge master 中招（哈希失配 → 从备份逐文件恢复）；同族 L161

## L174
- date: 2026-07-30 ｜ tags: testing,mutation,defense-in-depth ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 一枚变异重定靶 + 单次重跑（约 20min）
- symptom: 统一守卫后入口与唤起前两处调用同一判据；只改单个调用点的变异被另一处以同一状态码拦下而「存活」，看起来像覆盖缺口。
- root_cause: 判「存活」的成因有三：断言假、变异假（L167 已列两种），以及另一道同义守卫替它拦下（真冗余/纵深防御，非缺口）；第三种与前两种表现相同，仅凭存活行分不出。
- rule: 变异「存活」先看它被谁拦下；若是等价守卫兜住，修法不是删冗余、也不是宣称缺夹具，而是把变异下移到共享判据自身的锚点，让所有调用点一起退化——此时目标夹具才真被压。
- enforced_by: none（变异实验分诊纪律，L167 分类器的补充支）
- refs: T55 LEAFGUARD 改锚（改入口调用存活 → 改共享判据锚点 OK hit=3）；L167 rule③ 第三支

## L175
- date: 2026-07-30 ｜ tags: review,evidence,mutation,sequencing ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: 约 18h 机时（三轮 × 约 6h）
- symptom: 全量变异证据（29-33 枚 × 约 8min）跑完后 ship，R3 block 改了字节即整套作废重测；同一弧内连续三轮各重测一遍。
- root_cause: 长跑证据绑定交付字节（L161），而 R3 每轮 block 都可能改字节；把长跑证据排在评审之前 = 拿机时赌评审不改代码，期望成本 = 单轮成本乘 block 轮数。
- rule: 长跑字节绑定证据尽量排在「R3 已 pass、只差合并」之后补跑；做不到就把「每轮 R3 block 全套重测」记入该卡预算，并把 runner 做成可续跑（自愈 + 跳过已测 + 字节戳记账），别当意外。
- enforced_by: none（排程纪律；runner 字节戳跳过机制是止损面）
- refs: T55 三轮重测（r15/r16/r17 字节各一套）；同族 L161

## L176
- date: 2026-07-30 ｜ tags: testing,fixture,platform,ci,vacuous ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1 ｜ cost: master CI 红一轮 + 一张热修卡（约 1.5h）
- symptom: Linux 上 New-Item -ItemType Junction 静默不创建且不抛（-ErrorAction Stop 也不抛，pwsh 7.4.6 实测）；「try{建}catch{无能力}」形态的能力探针遂恒报有能力（假阳性），夹具武装失败后落进反 vacuous 的 Fail 而非声明跳过——T55 合并让 17t 首上 ubuntu runner，master selftest 当场红（t18/t19/t20 三处）。
- root_cause: 「没抛异常」被当成能力证据，而探针面与断言面同一条纪律：面 = 产物在场，不 = 没报错；平台差异恰好藏在「静默 no-op」这种最不像失败的失败里，且只在夹具首次跑上新平台时暴露。
- rule: 能力探针必须回读创建物并验其关键属性（junction 即 ReparsePoint），任一步缺失即判无能力、走声明跳过；同一能力的探针抽成唯一 helper 防站点漂移；带平台依赖的夹具族合并前先在目标 OS 至少单测探针（WSL/容器即可，不必全量）。
- enforced_by: scripts/selftest.ps1 Test-ScaffoldJunctionCapability（1 定义 + 5 调用，T60 DoD 钉计数）
- refs: T60-JNPROBE-ITEMVERIFY（PR #147）；根因实测 = ubuntu run 30511675074 红 + WSL 复现脚本；L165 同族（面=产物）

## L177
- date: 2026-07-31 ｜ tags: powershell,mutation,evidence ｜ tier: ledger ｜ kind: pitfall ｜ severity: blocking ｜ recurrence: 1
- symptom: 变异 campaign 报告「跑完」且每枚都 OK，实际只跑了 1 枚；同一脚本把 16 枚的表打印成「3 mutations」。
- root_cause: PowerShell 变量名**大小写不敏感**：foreach ($m in $M) 里的循环变量 $m 就是集合 $M 本身，第一轮迭代即把 $M 覆盖成末元素（一个哈希表）。此后 $M.Count 返回哈希表键数（3），下一个 foreach ($m in $M) 只迭代那一枚。
- rule: 循环变量绝不能与集合变量同名（含仅大小写不同）：集合用 $MUTS/$items 之类复数名，循环用 $mut/$item。凡「批量证据」脚本，跑完必须核对**记录条数 == 计划条数**，别只看「每条都 OK」。
- enforced_by: none（变异 harness 是一次性脚手架、不在仓内受闸；代偿 = 本条 Rule 的「已录条数 == 计划条数」自检，跑完必核）
- refs: 

## L178
- date: 2026-07-31 ｜ tags: encoding,evidence,restore ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 把被测文件从变异态手工还原后，内容逐字正确，SHA256 却对不上基线；再核发现文件少了开头 3 字节。
- root_cause: 目标文件带 UTF-8 BOM，而 [System.IO.File]::WriteAllText 在 .NET Core 下默认写**无 BOM** UTF-8，还原时把 BOM 悄悄丢了。内容比对与 git status 都看不出来。
- rule: 还原被测文件一律用 WriteAllBytes（存原始字节）或显式 New-Object System.Text.UTF8Encoding($true)；判「已还原」以 **SHA256 等于基线**为准，不以内容比对或 git status 为准。
- enforced_by: 
- refs: 

## L179
- date: 2026-07-31 ｜ tags: mutation,evidence,classifier ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 一枚变异 exit=1（闸确实红了），判据分类器却判 WRONG-CAUSE——红的原因不是该枚变异声称要证的那条断言。
- root_cause: 预期哨兵猜错：夹具把明文载荷放在 A 通道、退格混淆载荷放在 B 通道，摘掉 B 通道的去毒暴露的是裸控制字符而非明文码，故命中的是另一枚哨兵。
- rule: WRONG-CAUSE 是分类器**正常工作**的信号，不是分类器太严。正确反应是查清实际命中哪条断言、据此改预期（或补齐该通道缺的载荷形态），**绝不是**把判据放宽成「非零即算过」（那正是 L167 要治的）。
- enforced_by: 
- refs: 

## L180
- date: 2026-07-31 ｜ tags: mutation,resume,evidence ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 长跑被中断后用 -SkipDone 续跑，某一枚被静默跳过，最终结果表少一行却看着像跑全了。
- root_cause: 续跑的「已完成」判据用了「日志文件在场且非空」。被 kill 的那枚留下了**非空的部分日志**，于是被误判为已完成。
- rule: 续跑判据一律按**已记录的结果**（结果表/账本里有该枚的行）判完成，不按中间产物（日志/临时文件）在场判。中断后先核对 已录条数 == 计划条数。
- enforced_by: 
- refs: 

## L181
- date: 2026-07-31 ｜ tags: regex,unicode,sanitize,dotnet ｜ tier: ledger ｜ kind: pitfall ｜ severity: blocking ｜ recurrence: 1
- symptom: 「剥控制字符」的消毒把 emoji、CJK 扩展 B 等增补平面字符打成两个空格，悄悄毁掉合法文本，而实现与文档都声称只折叠控制字符。
- root_cause: .NET 正则按 **UTF-16 码元**匹配：一个增补平面字符是代理对，两半各自属 Unicode 类别 Cs，而 Cs ⊂ \p{C}。故 -replace '\p{C}' 会命中代理对的每一半。
- rule: 要剥的只有 **Cc（控制）与 Cf（格式）**：用 [\p{Cc}\p{Cf}]，别用 \p{C}（它还含 Cs 代理 / Co 私用 / Cn 未分配，都不是终端可执行的）。凡「剥控制字符」的实现都配一枚**增补平面字符原样留存**的回归断言；另注意替换成空格而非删除，免得删后相邻字符缩合出本不存在的敏感前缀。
- enforced_by: scripts/selftest.ps1（闸 17t：哨兵 T56/spoof/supp——摘录 / 采信 reasons / 子进程 stdout 三面各一枚 emoji 原样留存断言；变异 N2·E6 证其可红）
- refs: 

## L182
- date: 2026-07-31 ｜ tags: handoff,tooling ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: handoff.ps1 check 报「BRANCH 不存在于 git」/「WORKTREE 路径不存在」，但分支与工作树都在。
- root_cause: 这两个字段被**按字面**取值去做存活性校验；值里加了括号注解（如「分支名（head abc 已 push；PR #148 open）」）后，校验器拿整串去 git/文件系统查，自然查不到。
- rule: HANDOFF 的 BRANCH / WORKTREE 只写裸值（分支名、绝对路径），任何注解放 TASK / LAST-GREEN / UPDATED 等散文字段。
- enforced_by: 
- refs: 

## L183
- date: 2026-08-02 ｜ tags: tech-debt,verification,staleness,tracker ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 2
- symptom: 按记忆或文档里的数字/状态登记技术债，事后核实全错：同一轮内两次——把 R3 超时预算写成内建 600s（_config.ps1:99 早已钉 1800），又断言在飞 T56 会顺带偿还 TD124（其 excerpt 只接 [R3-NO-VERDICT-JSON]，根本到不了 [R3-NO-OUTPUT] 那支）；同夜 TD82 的 3 张 S2 评审卡若照卡开卡也全是空炮，实际早被 T20/T21/T29 修掉且各有回归闸
- root_cause: 关于仓库现状的陈述会衰减：注释、文档、交接、评审背包写下时为真，随后被别的卡顺带改掉，而登记者凭记忆或凭那份陈旧文本下笔，不去读活值或那张卡的真实 diff。评审背包尤甚——本仓 _local 那份自陈评审树落后 origin/master 九个提交
- rule: 登记债项或写断言时，凡引用配置值、行号、或「另一张在飞卡会不会顺带修掉它」，先读活值/读那张卡的真实 diff 再下笔；照陈旧评审卡开卡前先做约一分钟的只读三问核验（还在吗 / 被哪个提交或卡修的 / 有无回归闸）——核验比开卡便宜两个数量级
- enforced_by: 
- refs: 

## L184
- date: 2026-08-02 ｜ tags: testing,mutation,escaping,vacuous,r3 ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: T56 的两枚 JSON 编码夹具（t30/t31）全绿，但把生产实现换成裸引号包装（$excerptJson = 双引号 + $excerpt + 双引号）后它们**照样全绿**——转义面根本没被测到。R3 r2 在沙箱里实跑证据：载荷不含双引号，naive 包装仍能被 ConvertFrom-Json 解析成功
- root_cause: 测「编码/转义」契约时，载荷里没有放该编码器**存在的理由**那个字符：JSON 字符串以双引号定界，而 t30 的脏载荷只有控制字符与反引号、t31 注入的是**撇号**。撇号碰不到 JSON 定界符，于是断言只证明了「字符串能往返」，没证明「内部定界符被转义」。属 L165「断言面必须恰好等于被测契约」在编码类契约上的具体形态
- rule: 测编码/转义/引号包裹类契约时，载荷必须包含**该编码器负责转义的那个字符本身**（JSON→双引号与反斜杠；shell→引号与分号；SQL→单引号；正则→元字符），并断言**精确往返**（解码结果逐字节等于消毒后原文），不是「含某哨兵」；配套变异 = 把编码器换成裸拼接，它必须红
- enforced_by: 
- refs: 

## L185
- date: 2026-08-03 ｜ tags: testing,assertion,encoding,vacuous,mutation ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 4
- symptom: T56 r3 新加的 t34 断言「扫描摘录里的落单代理码元」在变异（删掉截断处的代理回退句）下**纹丝不动**——闸 17t 照报 OK，红的是别处（PSScriptAnalyzer）。分类器判 NOT-OK 后逐层复现才发现：被测实现确实产出了孤高位代理，但断言永远看不到它。**第三次（T56 r11）**：t35 的「围栏外不得有状态码」查的是**字面** `[R3-`，可 `review.ps1` 本就把字面 `[R3-` 去毒成 `(R3-`、而载荷用的是 `&#91;R3-` 与 `[R<!--x-->3-` 两种渲染层写法 ⇒ 那条守卫**任何实现下都红不了**；改判**渲染等价形态**（去注释 + 还原数字实体）后，尾部泄漏变异当场红。**第四次（同轮）**：判别器上也长了同一个坑，见 L193
- root_cause: 断言点与产出点之间隔着一次 **UTF-8 落盘**：孤代理无法用 UTF-8 编码，裁决 JSON 一写盘就被替换成 U+FFFD，读回时早已不是代理。于是那条扫描在**任何**实现下都为真 = 结构上永远红不了（L174 等价守卫冗余的一种，且比普通冗余更隐蔽：它看起来精确地针对了缺陷）
- rule: 写断言前先问「这个属性在**断言点**还观测得到吗」——产出点与断言点之间若隔着序列化、落盘、编码转换、进程边界、日志转发，属性可能已被规约掉（孤代理→U+FFFD、CRLF→LF、NFC 归一、控制字符剥除）。断言要么锚在**转换后仍存在的signature**（如 U+FFFD 本身），要么直接用**精确相等**比对整个期望产出（最稳，且自带长度/标记等子属性）。判定靠变异：断言若在对应变异下不红，它就没在测
- enforced_by: 
- refs: 

## L186
- date: 2026-08-03 ｜ tags: mutation,powershell,line-endings,windows ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 变异脚本把整行连同换行符一起作为靶串删除时，靶未命中：`$raw.Contains($old)` 恒为 False，白跑一轮 6.5 分钟的 selftest
- root_cause: 靶串尾部拼了 `[Environment]::NewLine`，它在 Windows 上是 CRLF；而本仓文件是 **LF-only**（review.ps1 实测 730 个 LF、0 个 CRLF，.gitattributes 亦按 LF 入库）。跨平台常量与仓库实际字节不是一回事
- rule: 变异/补丁脚本构造靶串时，换行一律写死 `` `n ``，别用 `[Environment]::NewLine`；不确定就先 `([regex]::Matches($raw, "``r``n")).Count` 数一下。整行删除的靶串务必先 `Contains` 断言命中、不命中即 throw（本次正是该断言把假证据挡在门外）
- enforced_by: 
- refs: 

## L187
- date: 2026-08-03 ｜ tags: review,r3,verification,scope ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: R3 r5 的 finding 点名两种绕过形态，照单实现的话会做多余的活并漏掉真正的面：实测当前实现后，`\[R3-…` 那半**根本不成立**（它含字面 `[R3-`，现有替换照样命中），而 finding 没点名的 `&lbrack;R3-…` 反倒是真绕过
- root_cause: 把 checker 的断言当既成事实。评审者（含第二模型）给的是**假设 + 修法建议**，其举例可能部分失效、也可能不穷尽；maker 若直接照着改，既会写多余代码，也会以为「照做即闭合」而漏掉同类未点名的变种
- rule: 收到评审 finding 先**逐条对当前实现复现**（一段几行的脚本跑一遍，把「现输出 / 是否仍可绕过」列成表），再决定改什么：不成立的那条别改、成立的那条按**根因**修而非按举例修（举例是样本、根因才是面）。复现表随卡归档，作为「为何这样改」的证据
- enforced_by: 
- refs: 

## L188
- date: 2026-08-03 ｜ tags: mutation,assertion,diagnostics,testing ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: D7 变异判 OK（exit≠0 且命中 `闸17t(t35)`），但记账里那枚更细的哨兵 `T56/md/entity` 是 False——红的其实是同一用例里的**另一条**断言（`md/vacuous`），其文案说「载荷没走到 sink」，而真因是「转义那句被删了」。撞闸的人会被指向完全错误的地方
- root_cause: 断言顺序写反：把「载荷在不在」的反 vacuous 守卫写成了「**转义后**的载荷在不在」，于是修法一被删，它先于真正的 entity 断言命中。分类器只要求「命中该用例」就算过，故这条错误诊断在「OK」的外表下溜过去了
- rule: ① 反 vacuous 守卫要判**与被测属性无关**的在场性（判「载荷在不在」用两种形态都含的片段，别用只有修好时才出现的形态）；② 变异分类器除了记「哪个用例红了」，再记**哪条断言红了**——命中的若不是该变异针对的那条，就是断言顺序错了或变异没打中，两者都得先查清再记 OK。这是 L167 的细化：非零+命中用例只证「在测」，还要证「报的是真因」（TD98 同源：失败信息即修法）
- enforced_by: 
- refs: 

## L189
- date: 2026-08-03 ｜ tags: security,design,review,rendering ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: R3 连续三轮（r1/r5/r6）用不同渲染花样伪造出同一枚状态码：退格（终端层）→ HTML 实体（Markdown 层）→ HTML 注释 + default-ignorable 不占位字符（两层）。每轮按点补一次，下一轮就换一种形态；r7 显然还有 RLO/变体选择符/tag 字符等着
- root_cause: 把「让一枚**文本 token** 在对手也能写字的**同一条信道**里不可伪造」当成可以靠过滤达成的目标。过滤本质是黑名单：渲染层能把字节变成字形的路子是开放集合，穷举不完，每补一个洞只是把下一个洞推后一轮
- rule: 这类需求要按**结构**解，不按字符解：把对手可控文本整段放进渲染器**不解析**的容器（Markdown 围栏 / 明确分隔的独立字段/信道），authoritative 记号只出现在容器**之外**；随之把文档里的保证**如实收窄**成「容器之外成立」——收窄措辞不是弱化闸，是让文档停止说假话。若某个 sink 拿不到容器（如终端），退而用**类目级/属性级**判据（Unicode Default_Ignorable_Code_Point 之类）而非逐字符黑名单。识别信号：同一条保证被连续两轮以不同形态绕过 ⇒ 立刻停手改结构，别补第三次
- enforced_by: 
- refs: 

## L190
- date: 2026-08-03 ｜ tags: verification,regex,unicode,oracle ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 2
- symptom: r7 我用 `[CharUnicodeInfo]::GetUnicodeCategory()` 验出 U+1BCA0/U+E0001 属 `Cf`，据此断定「正则的 `\p{Cf}` 已覆盖增补平面」并写进权威注释与 rubric；r8 实测 `$s -match '\p{Cf}'` 对这四个增补标量**全 False**，整段增补面其实一个都没被剥，伪造码照样拼得出来
- root_cause: **验证用的 oracle 与被测实现不是同一套判据**：`GetUnicodeCategory` 按 **Unicode 标量**判类目，而 .NET 正则按 **UTF-16 码元**匹配——增补标量在正则眼里是一对 `Cs` 代理，永远进不了 `\p{Cf}`/`\p{Mn}` 之类的类目类。用前者去证后者，等于拿另一台机器的读数当本机结论。这比不验证更坏：它产生**有据可依的错误自信**，还会被写进文档变成下一轮的假前提
- rule: 验证一个断言时，**必须用被测代码实际使用的那套机制去验**，不能用「语义上等价」的另一个 API：正则覆盖面就用 `-match` 实测、别查类目 API；编码/落盘行为就真写一遍文件再读回、别推理；渲染层行为就看渲染器实际输出。判断法：问「我的验证脚本和生产代码，是不是同一个引擎在做同一个判断？」不是就换写法。**且断言的对象若是一个「类目/属性」（`Cf`、default-ignorable 之类），取样证不了它——必须把全集从权威表枚举出来逐个比对**（2026-08-03 r13 更正：本条原写「逐点实测代表码位（BMP 与增补各取样）」，那正是又栽一次的原因——r8 照它取样补完仍漏 18 个增补面 `Cf`，r13 才由全码位枚举挖出）。落地形态：用**标量级** API（`Rune.GetUnicodeCategory`）枚举出「应该命中的全集」，再用**生产代码那套机制**（正则）逐个验它是否真命中；两套 oracle 各司其职、谁也不替谁。好处是 Unicode 升版新增码位时断言会自己红，而不必等下一个评审者发现
- enforced_by: 
- refs: 

## L191
- date: 2026-08-03 ｜ tags: testing,fixture,coverage,drift ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 夹具用**手挑代表点**覆盖一个集合（如 default-ignorable 的各族），扩充载荷时把 U+E0100 换成了 U+E0001，而总结行仍宣称覆盖 E0100 ⇒ 「变体选择符补充」那一段实际无人验、收窄它照样全绿。R3 r9 抓到
- root_cause: 夹具的覆盖集与实现的覆盖集是**两份手工维护的清单**，改一份不会强制改另一份。清单越长、轮次越多，漂移越必然——本卡 r7/r8/r9 连续三轮都栽在「实现改了、描述或夹具没跟上」的同一根因上
- rule: 夹具要**从被测集合派生**、别另开一张手工清单：把「族 → 代表码位」写成一张表放在夹具旁（最好紧邻被测模式），载荷与断言都**遍历该表生成**；这样任一族被收窄，夹具必红，且表与模式并列、漂了一眼可见。手工清单只在无法派生时用，且必须配「清单 == 实现」的机检。同理适用于：状态码枚举 vs 文档表、闸列表 vs 总结行、allow_paths vs 实际改动面
- enforced_by: 
- refs: 

## L192
- date: 2026-08-03 ｜ tags: testing,fixture,codegen,vacuous ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 把测试替身（stub）的**代码体**改成程序生成后，生成时用错了转义层（拿 `ConvertTo-Json` 当 PowerShell 转义用），stub 语法坏、一个字节都没写出来；该用例遂掉进「后端没产出」那一支，看起来像被测系统的正常分支，而不是夹具坏了
- root_cause: stub body 是**代码**，但生成它的那一步没有任何人验证过——只有它产出的**结果**被断言。生成层与执行层的转义规则不同（JSON 转义 `\"` 在 PowerShell 双引号串里不是转义），错了不会报错、只会静默产出坏脚本。本卡「已知坑」里早写着这条，我仍然又踩了一次：**知识写在卡里 ≠ 动手前读过**
- rule: 凡 stub/夹具的代码体是**拼出来或生成出来**的，落进套件之前**先单独跑一遍**并断言它真产出了预期产物（文件非空、可解析、关键标记在场）；这一步几秒钟，省的是一整轮假绿或误诊。配套：每个此类用例都要有**反 vacuous 守卫**（断言载荷残迹在场）——本次正是它把坏 stub 抓出来的，否则会被读成「被测系统走了另一条分支」。另：动夹具前先把卡的「已知坑」节读一遍
- enforced_by: 
- refs: 

## L193
- date: 2026-08-03 ｜ tags: verification,harness,stop-rule,evidence ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 2
- symptom: 给「区间收窄」做变异判别时，离线脚本报「FE00–FE0F 收窄成 FE00 测不出」，但独立复核显示 U+FE0F 属 `Mn`、不被 `\p{Cf}`/`\p{Cc}` 命中、也不落任何代理对分支——**两个结论直接矛盾**。连查三次没定位到脚本哪一层出错（疑似字符类字面量/转义层）
- root_cause: **（2026-08-03 重建判别器后更正——原先记的「疑似字符类字面量/转义层」是当场的猜测，实测证伪）**真因是判别器**只模拟了链条的前一半**：它跑完「实现侧剥不可见字符」就去查**字面** `[R3-`，而载荷本就写成 `[R`+不可见字符+`3-`，字面比对**在任何实现下都扫不到** ⇒ 它对每一种收窄都回答「测不出」。断言侧真正的判据是**再剥一次后的渲染等价形态**，那一段被漏掉了。故这与「U+FE0F 属 `Mn`」的复核结论根本不在同一层，两者从未真的矛盾。**这就是 L185 的同型坑长在判别器上**：一条永远不可能成立的比对
- rule: **判别工具与被测实现互相矛盾时，先停下修工具，别继续产证据**——此时产出的任何「变异 OK / 覆盖完整」都是无效证据，比没有更坏（会被写进卡当作已验）。具体防线：① 判别脚本一律从**被测文件里读出真实模式**再操作，不在测试侧另抄一份；② 一切不可见码位只写转义形态、源码里绝不嵌真字符——**但光这么说不够**：实测**写文件的工具本身**会把源码里 反斜杠-u-四位十六进制 的字面形态**自动换成真字符**，故转义形态要**用代码拼**（如 `[char]92 + 'u'`）让该字面形态根本不出现在源码里，并在用它之前**断言靶串真能在被测文件里找到**（找不到就是本枚作废，不是「测不出」）；③ 判别脚本自己要有 sanity case（已知必命中/必不命中各一），它先绿了才信它的结论；④ **判别器必须模拟到断言点那一层**，不能只模拟被测实现就下结论——少模拟一段，得到的必然是「哪儿都测不出」这种假阴性
- enforced_by: 
- refs: 

## L194
- date: 2026-08-03 ｜ tags: mutation,evidence,harness,staleness,verification ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 中途查变异批进度时，按文件名读 scratchpad 里的 t56-mut-<id>.log，得出「D15-D20 只红 t36、没红新加的 t36set」的结论，据此差点判定新断言是死代码并去改它。实际那六份日志是**上一批**（r11）15:38-16:12 写的，本批（r12，16:22 启动）当时只跑到 D14——r11 那会儿 t36set 根本还不存在，两者毫无矛盾。
- root_cause: 变异 runner 按固定名字写日志（t56-mut-<id>.log），跨批次复用同一批文件名。批次跑到一半时，「文件存在」只说明**某一批**跑过这枚，不说明**本批**跑过；而人读进度时默认「存在即本批产物」。同族于 L161（证据须对应最终字节）与 L167（判据分类器），只是坑长在**产物读取**这一层：证据本身没错，错的是把它归给了另一次运行。
- rule: 把验证产物**绑定到产生它的那次运行**，别只靠文件名：读进度一律加 mtime 过滤（只认 mtime > 本批启动时刻的日志），或把 run-id/时间戳写进文件名与文件头。中途进度读数在批次跑完前一律当**部分结果**，缺的条目报「本批尚未产出」而不是拿旧值顶上。判据同 L161：证据要能说清它属于哪次运行、哪份字节。
- enforced_by: 
- refs: 

## L195
- date: 2026-08-03 ｜ tags: review,r3,verification,unicode,transcription ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: R3 finding 指出增补面 Cf 漏剥，并给出 U+13430 的高位代理为 D80C。若照抄写成分支 \uD80C[...]，它匹配不到任何东西——正确值是 D80D（0xD800 + (0x3430 >> 10) = 0xD80D）。这类错误不会报错、跑起来一切正常，只是那条分支永远不命中，等于修了个寂寞，而卡上还会留下一条「已修复」的假记录。
- root_cause: 评审 finding 里的**具体数值**（码位、代理对、偏移、行号、范围端点）与它的**结论**可信度不同：结论往往对，数值常常是评审者顺手推算的。我按 L187 复现了「有没有漏」这个结论，却差点直接采信它随附的数值。数值一旦错，产出的是**语法正确、语义空转**的代码——比没修更坏，因为它带着一条通过的变异记录。
- rule: 评审 finding 里的数值一律**自己重算/枚举，不照抄**：码位与代理对用程序算（[char]::ConvertFromUtf32 / Rune）、范围端点用枚举得出、行号用 grep 现查。复现 finding 的结论（L187）与复核它的数值是**两件事**，都要做。修完立刻用「这条新分支到底命中了什么」反验一次——命中数为 0 就是抄错了。
- enforced_by: 
- refs: 

## L196
- date: 2026-08-04 ｜ tags: mutation,background,restore,session-kill ｜ tier: must ｜ kind: pitfall ｜ severity: major ｜ recurrence: 5
- symptom: 后台变异批被会话结束硬杀在「植入后、还原前」，finally 不执行，review.ps1 跨会话停在 D28 收窄变异态；git 只显示 M、注释仍宣称全区间覆盖，与真修复混在同一 diff 里肉眼难辨（r11 强杀后已发生过一次，本次复发；第三次 2026-08-05：r14 批被前会话超上下文拆除杀在 D23 植入后 1 秒，任务报 exit 4，本条 rule 的「续接第一步核 SHA」当场抓到并从 .bak 还原——per-mut 日志让续跑只补缺失 10 枚，不必全批重来；第四/五次同日晚：r17 批两连遭会话侧外杀（D14/D17 植入后），每次同一套「核 SHA → .bak 还原 → -Only 续跑」恢复、单次损失一枚——机制已把事故成本从「整批作废」压到「一枚」。两连杀后加固：**长批改派 OS 计划任务（schtasks）脱离会话进程树跑，会话侧只留可弃 watcher 轮询完成标记**——会话怎么死都杀不到批）
- root_cause: 硬杀（会话终止/进程树 kill）不执行 finally/trap；变异批把还原动作只挂在 finally 上，批死在植入与还原之间就留下变异态文件
- rule: 还原动作不得只依赖 finally：批启动先核基线 SHA、不符即中止（既有守卫）；**每次会话续接第一步核被测文件 SHA==上批基线**，不符先从 .bak 还原再谈 diff/证据；判干净以 SHA256 为准（L178），别信 git status 或文件注释
- enforced_by: 
- refs: 

## L197
- date: 2026-08-04 ｜ tags: powershell,args,pwsh-file,tooling ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 经工具层跑 & pwsh -File script.ps1 -Only 'A','B'：脚本的 [string[]] 参数收到**带引号的单元素字面串**，-Only 过滤匹配 0 枚直接 throw；同一调用在交互 pwsh 里却会展开成两个参数
- root_cause: 工具层命令降低（AST 到 argv）不求值数组表达式，逗号数组连引号按单个字面 token 透传；pwsh -File 侧的参数绑定也不做 PowerShell 求值，收到什么就是什么
- rule: 本仓 .ps1 带数组参数时不经 pwsh -File 中转：用进程内 & script.ps1 -Param @(...) 调用；必须跨进程时让脚本收单串自拆（如收 -Only 'A,B' 后内部按逗号 split）——先用一次 DryRun/回显核实参数条数再跑长批
- enforced_by: 
- refs: 

## L198
- date: 2026-08-06 ｜ tags: nodejs,scaffold,naming,windows ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1 ｜ cost: 浪费一条命令+一次重装依赖，约3分钟
- symptom: npx create-next-app@latest <dir> 直接指向 CamelCase 目录（如 D:\Projects\CHRD）时立刻退出 1：Could not create a project called "CHRD" because of npm naming restrictions: name can no longer contain capital letters。同类生成器（create-vite / npm init）走同一套 validate-npm-package-name 校验，症状一致
- root_cause: 生成器把目标目录 basename 直接当 npm 包名写进 package.json 的 name 字段，而 npm 包名规范自 v2 起禁大写；校验在写盘前做，故是干净失败、不留半个仓
- rule: 目标目录名含大写时别硬碰：在临时目录用全小写名跑生成器（npx create-next-app@latest <tmp>\<lowercase-name> ...），跑完把除 node_modules 外的全部条目（含 .git 与点文件）Move-Item 到真实目标目录，再在目标目录跑一次 npm install（跨盘搬 node_modules 比重装慢得多）。生成的 package.json name 保持小写 slug、与目录名不一致无妨。加 --yes 免交互提示
- enforced_by: none（下游一次性建仓动作，元仓无闸可挂；失败是 fail-fast 且信息明确）
- refs: 

## L199
- date: 2026-08-14 ｜ tags: tar,git-bash,windows,path ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: Git Bash 里 tar -xf C:/...tar（或 -C D:/...）报 tar: Cannot connect to C: resolve failed
- root_cause: GNU tar 把带盘符的 Windows 路径按远程语法 host:path 解析，冒号前盘符被当远程主机名；pwsh 下 Windows 自带 bsdtar 无此行为，同一命令换 shell 即崩
- rule: 在 Git Bash 里给 tar 传含盘符路径一律改 POSIX 形态（/c/... /d/...）或加 --force-local；git archive -o 不受影响（git 自行解析路径），只有 tar 的 -f/-C 参数中招
- enforced_by: 
- refs: 

## L200
- date: 2026-08-14 ｜ tags: selftest,诊断,失败遮蔽 ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: selftest 排障绕路：真失败与建议性 lint 都打 WARNING 难分；修完首批失败重跑才暴露新崩溃（17aa(8) 藏在 if (-not $fail) 后，前面全绿才首次执行）；夹具终止性错误直接中断整跑、连「结论」行都不打
- root_cause: Fail() 实现为 Write-Warning + $script:fail 标志，与建议性 Write-Warning 同貌；部分后置闸以 -not $fail 门控形成失败遮蔽；夹具在 ErrorActionPreference=Stop 下抛终止异常即弃整跑
- rule: selftest 排障固定三步：①先看尾部「结论 selftest: PASS|FAIL」，无结论行=中途崩溃、按最后输出定位行号；②失败清单用 Select-String WARNING 收集（剔除标注「建议性」的 lint 行）；③每修一批必须重跑到 PASS 为止——门控闸在前面转绿后才首次执行，一轮清单不是全集
- enforced_by: 
- refs: 

## L201
- date: 2026-08-14 ｜ tags: codex,subagent,多模型编排 ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 经 Agent 工具派 codex:codex-rescue 讨论任务，结果只回「Codex Task started in background as task-<id>」；再让该子代理去取结果被拒——它按设计是一次性转发器，禁调 status/result/轮询
- root_cause: codex 插件把 rescue 子代理定义为单发 forwarder（skill 明文禁 status/result/cancel）；后台 codex 任务的取结果责任在主线程，子代理无法被消息扩权
- rule: 主线程自己轮询取结果：node "<codex 插件 cache>/scripts/codex-companion.mjs" status <task-id> 到 completed，再 result <task-id> 取全文；长任务（gpt-5.6 高档 5–10 分钟）用其它并行工作填等待，不要重复派新子代理去问
- enforced_by: 
- refs: 

## L202
- date: 2026-08-15 ｜ tags: orchestration,subagent,review,task-loop ｜ tier: ledger ｜ kind: pitfall ｜ severity: minor ｜ recurrence: 1
- symptom: 派子代理跑整张任务卡时，它的收尾消息说「ship 还在后台跑」，但同一时刻 .review/<id>.json 里 R3 裁决已经写好且是 block——照它的自述等下去会白等一个已经结束的相位。
- root_cause: 子代理的自述是它对自己动作的叙述，不是 harness 的状态；task.ps1 的相位状态本来就落在磁盘工件上（.review/<id>.json 的 verdict+sha、worktree 是否存在、卡片 status 字段、master 的 git log），子代理并不去读这些，它只是复述自己最后一次调用的印象。
- rule: 编排多卡时，任务卡的进度只认磁盘工件、不认子代理的叙述：读 .review/<id>.json（verdict + sha 必须等于被审 tip）、git worktree list、卡片 status、git log 主检出。子代理报「还在跑/已完成」一律先落地核这四样再决定下一步；裁决是 block 就直接带着 findings 原文 SendMessage 让它返工，别重派新代理（重派丢上下文、还会重跑一遍已过的闸）。
- enforced_by: 
- refs: 

## L203
- date: 2026-08-15 ｜ tags: scope-gate,review,git,subagent,verification ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: 子代理「修好了」的改动里含**新建文件**（如 android/app/src/main/res/xml/data_extraction_rules.xml），但它一直没 git add；此时 git diff / diff --stat 全看不到这个文件，范围闸与 R3 评审也看不到——manifest 指向一个提交里根本不存在的资源，却能一路走到 ship。
- root_cause: git diff 只比对已追踪文件；未追踪文件既不在 diff 里，也不在 task.ps1 范围闸的改动清单里（清单由 git diff 求值），R3 拿到的 diff 同样为空。L157 立的习惯是「落盘改动先 git diff --stat 对账」，而该习惯对新建文件恰好是盲的——核验手段本身窄于契约（L165 同型）。
- rule: 核验非自己敲的改动时，git status --porcelain 与 git diff --stat 一起看，别只看后者：?? 开头的行就是闸门和评审都看不见的部分。新建文件类的改动（新资源、新 .sq、新源文件）尤其要显式确认已 staged 再 ship；ship 前一句 git -C <worktree> status --porcelain 若还有 ?? 行，先判断它该进本卡还是该删，不要放着不管。
- enforced_by: 
- refs: 

## L204
- date: 2026-08-15 ｜ tags: review,scope-gate,task-loop,worktree,orchestration ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1
- symptom: R3 评审者报「越界：某文件不在 allow_paths」，但同一次 ship 的范围闸对**同一 sha 的同一 diff** 判 PASS。两个机制结论相反，且评审者的措辞极像真缺陷（点名文件+引用 allow_paths），很容易被信以为真而去回退正确的改动。
- root_cause: 两者读的是**两份不同的卡**：范围闸按设计从 base ref 取卡原文（防分支自扩 allow_paths），而 review.ps1 把**工作树**交给评审者——卡若在施工中被修订，按 L18 修订只落 master、不进功能分支，工作树里就永远是开卡时那份旧卡。于是评审者拿旧 allow_paths 判新 diff。已登记 TD3（修法=让 review.ps1 也从 base ref 取卡）。
- rule: 施工中修订了任务卡，下次 ship 前先把 master 合进卡分支（git -C <worktree> merge master），让工作树的卡与 base 一致——评审者才看得到新 allow_paths 与修订说明。判据口径：**allow_paths 的权威永远是 base ref 上那份卡**，工作树那份只是副本。凡遇「评审者报越界 vs 范围闸 PASS」冲突，先 git show master:specs/tasks/<id>.md 与 git -C <wt> show HEAD:specs/tasks/<id>.md 对比两份卡，确认是陈旧副本再驳回，别回退改动。注：-SkipRed 的卡合并 master 无 RED 证据可被打乱，L148 的顺序禁忌不适用；有 RED 证据的卡仍按 L148 走。
- enforced_by: 
- refs: 
