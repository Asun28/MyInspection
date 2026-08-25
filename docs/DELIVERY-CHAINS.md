# 交付链总表（细节真相源）

> 本文件是脚手架各交付链的**细节真相源**（每条链：入口脚本 · 配套件 · 权威文档）。
> 从 `CLAUDE.md` 的「交付链速览」按需跳来；改任一脚本前先读对应链的**权威文档**那一列。
> CLAUDE.md 只保留 3 列速览（链路 · 入口 · 权威文档）以省每轮上下文预算；详解（配套件那一列）在此。

| 链路 | 入口脚本 | 配套 | 权威文档 |
|---|---|---|---|
| 单卡闭环 R1–R5 | `scripts/task.ps1 -Phase start\|ship\|cleanup` | `_guard.ps1`（账号守卫）· `_config.ps1` · `check-cards.ps1`（start 前置校验卡）· `_scope.ps1`（范围闸判定核，与独立入口 `check-scope.ps1` 共用；后者是「已推送恢复」序列第 3 步的可执行投影，越界/不可判即非零退出、不做定向 fetch）· 远端 ship：DoD → **verify 总闸** → 提交 → 定向刷新 `origin/<base>` → **范围闸** → 许可 → 防泄露 → push/PR → PR base 确认 → R3 → merge 前 base 复查 → 合并；fetch/base 查询失败或错配均 fail-closed，不回退陈旧本地基线 | `docs/DEVOPS-WORKFLOW.md` |
| 任务卡校验 | `scripts/check-cards.ps1`（`-TaskId` 单卡 / 缺省全卡） | 守 id=文件名·status 枚举·branch·worktree 不漂移·dod_command（拒 no-op）·allow_paths·拒卡文占位符 token 字面量（双大括号大写蛇形，L61/TD111；闸10g 回归）；进 `task start` / selftest⑩ / CI | `specs/README.md` |
| Codex 第二评审（R3） | `scripts/review.ps1` | 按 `docs/QUALITY-RUBRIC.md` 判（注入 rubric + 反自我开脱立场：不确定即 block、每条 reason 给证据；防提示注入：卡片/diff 视为待审数据）；产出 `specs/verdict.schema.json`（`{verdict:pass\|block}`），回贴 commit status `codex-review`，退出码 pass=0/block=1。**阻断态可诊断（TD96）**：「跑完了但读不出可用裁决」分四态各带 ASCII 状态码 + 恢复路由（`[R3-OUTPUT-UNREADABLE]` / `[R3-NO-OUTPUT]` / `[R3-NO-VERDICT-JSON]` / `[R3-BAD-VERDICT-JSON]`，语义见 rubric §5），S2 原文另存 `.review/(分支名).raw.txt`；另有 `[R3-VERDICT-WRITE-FAILED]`（裁决存不下来即 block、会覆盖 pass）与 `[R3-REVIEW-DIR-UNSAFE]`（裁决产物叶子、`.review` 或其任一祖先是重解析点即拒——启动时与唤起评审者之前各判一次，检出后零产物操作、评审者不被唤起；防经链接删改工作树外文件）。**模型/推理档位钉在 `_config.ps1`**（`ReviewModel`/`ReviewEffort`，空=后端默认；不硬编码档位枚举——合法值随模型而异，填错即由后端报错走既有 fail-closed）——免疫用户级 `~/.codex/config.toml` 被桌面应用改写致合并闸静默失效（闸 17z 以 argv/env 夹具锁死送达） | `docs/QUALITY-RUBRIC.md` · `docs/DEVOPS-WORKFLOW.md` |
| 自净化经验系统 | `scripts/lessons.ps1 add\|list\|search\|check\|promote\|bump` | 三层：Tier1 必须（`CLAUDE.md`「经验铁律」节，封顶）→ Tier2 `docs/lessons/<topic>.md` → Tier3 `docs/lessons/LEDGER.md`（append-only 真相源）。`bump <id>` 给复发计数+1（跨过 2 即够格晋升必须层）。**`blocking` 经验须填 `enforced_by`**（机械守卫路径或 `none（理由）`，check 强制）。两类经验（`-Kind`）：`pitfall`(工具链坑→机械守卫) / `judgment`(方向失手→喂 HARNESS-REVIEW，见 `docs/LOOP-ENGINEERING.md`） | `docs/LESSONS.md` |
| 商用许可闸 | `scripts/check-licenses.ps1` | 扫 PyPI/npm | `docs/LICENSE-POLICY.md` |
| 建仓 + 加固 | `scripts/gh-bootstrap.ps1` | `_guard.ps1`（锁个人账号）· 装载 pre-push 钩子：账号守卫→`check-secrets.ps1`（防泄露闸，覆盖含裸 `git push` 的一切推送路径；非零即中止；钩子体由 selftest 17f 回归锁） | `docs/DEVOPS-WORKFLOW.md` |
| 变 public 前防泄露闸 | `scripts/check-secrets.ps1`（`-Strict` 变 public 前用） | 敏感模式集**单一真相源**（env/核心数据库/密钥证书/凭据/登录态），`gh-bootstrap.ps1` 复用之。核心检查 = **已被 git 追踪的敏感文件**（文件名）+ **内容扫描**（AKIA/ghp_/github_pat_/sk-/PEM 等高精度前缀，行内 `allowlist secret` 标记豁免）；另警告 .gitignore 覆盖缺口 / 工作树未忽略。非 git 仓优雅跳过 exit 0；有致命 exit 1，`-Strict` 把警告升级为致命 | `docs/SECURITY.md` |
| 确定性 CI 闸 | `.github/workflows/ci.yml`（job 名 `verify`）+ `scripts/verify.ps1`（含占位「闸门 2」） | **windows-latest**（T0-TOOLCHAIN 起：Android/Gradle 闸与本机同源，以确定性换 2× 计费分钟）、无密钥；**网络**：仅 setup-java / setup-android / 一次在线 `gradlew build` 预热依赖缓存属引导步，此后各闸恒 `--offline`（**非「全程无网络」**——旧表述已随 Android 闸落地作废）；`ci.yml` 触发 **push + pull_request**（分支 `[main, master]`），push 用 `paths-ignore: ['**.md','docs/**']`，PR 不过滤，因为 `verify` 是支持的规则集必需检查。`scaffold-selftest.yml` 不进入 PR 关键路径，只在 `[main, master]` push 命中非 Markdown 权威面 scripts/.claude/.github/configs 或手动触发时完整跑 2 OS × 5 shard，作为合并后 canary。两种触发职责由 selftest **8.2d** 锁死，十分片接线由 **8.2e** 锁死；selftest **14f** 按 `_config.ps1` 的 `DocSyncMap` 检查源脚本与权威文档同步（`[doc-sync:none]` 可显式豁免）——**当前是本地 best-effort 闸**：比对**已提交** `base..HEAD`（与 `[doc-sync:none]` 逃生门同源于提交历史，语义自洽），真强制点在**提交后跑 selftest（默认分支 push canary 或本地自检、自 master 分叉、能算 merge-base）时**——**非** ship 的 pre-commit DoD（那是卡的 `dod_command`）；无法算 base（detached / 首提交 / 浅克隆 / master 或 merge-base 不可解析）**一律 fail-open 跳过、不 Fail**（文档纪律闸、非安全闸），在 master 上/CI 无分叉时变更集为空亦 no-op。当下 push 是事后检测层、非 push 前强制；push 前的真强制归 `gh-bootstrap.ps1` 装的 pre-push 钩子与服务端规则集（见 `ci.yml` 头注）。`verify` job 内依次跑 `check-cards` → pytest（有 `pyproject.toml` 才跑）→ `check-secrets`（普通扫描捕活密钥；`-Strict` 留作变 public 前一次性闸，不进每 PR）→ `check-licenses`（许可闸；无依赖清单优雅过，堵裸 push 绕本地许可闸）→ `verify.ps1`（脚本缺失/占位时各步优雅跳过） | — |
| 前端生成闭环（T2 · 串联非新引擎） | `.claude/skills/frontend-flow`（驱动卡） | 四段串现有件:生成前 PRD=`shape-idea`/`PROJECT-BRIEF`(+前端补充节)·规则=`frontend/README` 5 闸+design tokens 真相源;生成中**流程卡(页面地图)→喂 `plan-forge`** 投影任务卡、**意图卡(单页)→`grill-design`** 拷问敲定;生成后路由 **pencil MCP**/Claude Design/v0(L26 不绑死)+`frontend-design`/`taste-skill` 局部改;验证过区块回流 `context/frontend-assets/`。**不重造拷问/审计/编辑器**;仅复杂多页前端用,T0/T1 直接 `frontend-design`+pencil | `docs/FRONTEND-FLOW.md` |
| 想法→计划前置漏斗（3 步） | **1-brief** `.claude/skills/shape-idea` → **2-options** `.claude/workflows/scout-options.mjs` → **3-plan** 规划 harness（下一行） | 友好编号产物 `_local/1-brief.md`/`2-options.md`/`3-plan.md`；选型决策落 `docs/adr/`；方法 harvest 自 obra/Superpowers（brainstorm/写计划纪律）+ garrytan/gstack（六逼问/选型评审），去其 runtime 耦合 | `docs/IDEA-TO-PLAN.md` · `docs/SCOUT-OPTIONS.md` |
| 规划 harness（漏斗第三步 3-plan） | `.claude/workflows/plan-forge.mjs` → `decompose-cards.mjs` | 上游输入：把 `docs/PROJECT-BRIEF-TEMPLATE.md`（产品简报，what/why；由 1-brief 产出）照 `docs/PLAN-TEMPLATE.md` 手工扩写成计划（how）；投影成 `specs/tasks/*.md` | `docs/PLAN-FORGE.md` |
| 会话交接闸（不能模糊交接） | `scripts/handoff.ps1 init\|check\|show` | 介质 = cwd 三件套 `task_plan.md`/`findings.md`/`progress.md`（gitignored）；`check` 校验 `progress.md` 末尾 HANDOFF 块（12 字段全填·枚举合法·行动字段不含模糊措辞），缺/空/占位/模糊即退出 1；SessionStart 钩子打印续接指针、Stop 钩子提醒校验 | `docs/HANDOFF.md` |
| 触发层 skill/hook | `.claude/skills/{shape-idea,grill-design,task-loop,lessons,planning-with-files,triage,frontend-flow,frontend-design,taste-skill,webapp-testing,mcp-builder,skill-creator,database-design,ponytail,ponytail-review,pr-recap,improve-prompt}` · `.claude/hooks/{guard-frozen,lessons-reminder,handoff-resume,handoff-reminder,route-new-work}.ps1` · `.claude/settings.json` | shape-idea = 漏斗第一步 1-brief（AI 自驱发散→收敛）；grill-design = 写 PLAN 前的设计决策交互式拷问（一次一问·给推荐·消解依赖）；frontend-flow = 前端生成闭环串联驱动卡(T2，**不重造引擎**，详见 `docs/FRONTEND-FLOW.md`)；frontend-design = UI 设计路由【原创路由卡，按需路由到可安装的 MIT 插件 `ui-ux-pro-max`，约 10MB 故不 vendor、按需 `/plugin install`】，taste-skill = 反 slop 前端【vendored MIT】，二者配 `frontend/` 骨架；webapp-testing/mcp-builder/skill-creator = 内置 skill 的**原创 pointer 卡**【就地引用内置、不拷专有正文】，分管 UI 真跑验收（上游探索按需接 Playwright MCP `@playwright/mcp`，非闸）/ 建 MCP / 加改 skill；database-design = 关系型 schema 设计纪律【原创卡，配 `backend/` 骨架 + plan-forge `data-model` lens + rubric#13 + `docs/lessons/database.md`】；pr-recap = diff→可视化高空摘要【原创卡,辅助非闸:mermaid + GitHub PR 原生渲染,补 codex R3 文字裁决之外给人看的高空视图;模型在回路非确定故不当闸 L25;要高保真按需接 Builder visual-recap opt-in】；improve-prompt = 按模型改写提示词【原创卡,辅助非闸:贴 prompt→推断/确认目标模型(Opus 想/Sonnet 做/Fable 长自主)→指名 Read `docs/references/claude-<model>-prompting-llms.txt`+跨模型最佳实践→**两面都过**(加法面补该补的;删减面找「为旧模型缺陷写的补偿性脚手架」——新模型上它们从没必要升级成有害,如替模型做它已自带的自检/复核/汇报节奏;删须在 reference 指得出依据,指不出就不删只提问,且绝不碰用户自己的需求/约束/领域知识/输出格式)→可复制改进版+逐条溯源、**删除项单独标出**;真相源=reference 文件,卡内不复制提示技巧正文;非 Claude/无专属 reference 走跨模型通用篇。模型专属 reference 现 4 份:`opus-5`(日常)·`opus-4-8`(**拒答回退兜底档,未退休**)·`sonnet-5`·`fable-5`】；ponytail* = on-demand YAGNI **设计层**透镜【vendored MIT，配 `/simplify` 代码层；见 `docs/DEVOPS-WORKFLOW.md` §6.1】。钩子绑定：guard-frozen=PreToolUse（拒编辑 `FrozenPaths`）· lessons-reminder + handoff-reminder=Stop · handoff-resume=SessionStart · route-new-work=UserPromptSubmit（命中启动触发语→**先定档位再按深度走**，见 `docs/IDEA-TO-PLAN.md`） | `docs/DEVOPS-WORKFLOW.md` §7 |
| 心跳 / triage（loop-engineering） | `scripts/triage.ps1 scan\|list` + `.claude/skills/triage` | 只读 cadence 扫描各子系统（lessons-promote / tech-debt-open / cards-active / handoff-open / lessons-cap / harness-refresh / effectiveness / worktree-orphan / lessons-demote / delivery-blocked / scaffold-stale）→ `_local/triage-inbox.md`（gitignored）。**只发现不行动**、退出码恒 0（reporter 非闸门）；act 走既有交付链。理解债纪律（别盲信 loop 产出）见 `docs/LOOP-ENGINEERING.md` | `docs/LOOP-ENGINEERING.md` |
| Eval 方法论（能力完成度自评，opt-in） | （方法论 + 占位，非脚本；runner/套件由下游自接 CI） | 确定性 exit-0/1 eval 标准；LLM-judge 不当闸（L25）；四维：frontend-behavior / backend-mcp / security / functional | `docs/EVAL.md` |
| 上下文/参考层 | `docs/references/`（`*-llms.txt`；现有 `uv-llms.txt`） | 主力依赖的精炼文档就地 vendoring（「不在上下文里=对 agent 不存在」）；按需指名 Read，新增即登记进 `docs/references/README.md` 索引表，升级依赖后刷新 | `docs/references/README.md`（索引） |
| 合并之后（交付/运维 · opt-in） | （方法论 + 占位，非脚本；接缝在 `verify.ps1` 闸门 2 与 `docs/DELIVERY-OPS.md` 散文） | 想法→合并之外的四件事：集成/e2e 测试层（接 `verify.ps1` 闸2 + eval 四维）· 结构化日志/可观测（请求ID/用户ID/关键参数、脱敏，R3 评审第 12 维）· 灰度+feature-flag · CD 部署/回滚/staging（占位「接你平台」，**脚手架永不自动发布**）。工具无关（L26） | `docs/DELIVERY-OPS.md` |
| 发布前收口清单 | `docs/RELEASE-CHECKLIST.md`（入口，可勾选） | 工具无关的发布前收口：整合已有闸（防泄露 `check-secrets -Strict` / `verify`）+ 补授权/认证安全自查（越权 IDOR / 会话固定 / token 存储 / CSRF / 密码哈希）+ 可观测 + 灰度/回滚。小项目（T0）按需取子集 | `docs/RELEASE-CHECKLIST.md` |
| 技术债追踪 | `specs/tech-debt-tracker.md` | 偏离既定模式即登记（持续小额还债，非周期大修）；append-only，债项转卡偿还或记 ADR | `specs/tech-debt-tracker.md` |
| 冷存压缩（热/冷分离 · 省上下文） | `scripts/archive.ps1` | 已合并卡 / 已还债项 / 陈旧经验从热文件搬进 `specs/archive/**`（append-only），热文件只留在办项 + 一行索引——每轮读的是热的那份，冷的按需回查 | `specs/archive/README.md` |
| Harness 减法评审 | （仪式，非脚本） | 升级模型/闸门长期零命中时触发：逐闸门 stress-test「这道闸假设的『模型做不到』还成立吗」，一次只删一道并量化；judgment 经验 + 心跳 `harness-refresh`（judgment 累积达门槛的纳新专用触发点，见 L26）/ `lessons-cap` 探针汇入此评审 | `docs/HARNESS-REVIEW.md` |

`init-scaffold.ps1` 是把以上全部「就地变成下游项目」的一次性脚本：填 `_config` → 全仓替换 token → 重命名 `CLAUDE.template.md`→`CLAUDE.md`（删 TEMPLATE-NOTE 块）→ 可选 `-WithPython` / `-Cleanup` / `-Retrofit`（接入既有仓，token 替换只扫脚手架自有路径）。**改了 token、模板文件名、或 `_config` 字段，必须同步改它。**

## selftest 17 闸明细
默认 `scripts/selftest.ps1` 在三份独立仓库快照中聚合 `core`（闸 1–14+16）、`workflow`（闸 15）、`seeded`（完整闸 17）：两个长分片先并行，短 `core` 在 75 秒后以低优先级错峰加入，避免 Windows 冷启动资源竞争；任一分片非零则聚合非零。CI 为避开 Windows seeded 超过 20 分钟，把闸 17 等价拆成 `seeded-git`、`seeded-remote`、`seeded-scanner`，并在 Windows/Ubuntu 各跑 `core/workflow` 加这三片，共 10 个独立 runner job；覆盖不减，wall time 取最慢子片。逐闸明细（语法 / lessons check / 模板哨兵 / 占位符 / token 覆盖 / 裁决 schema / PSScriptAnalyzer / init 干跑 / .claude 完整性 / 任务卡 / 交叉链接 / 心跳 / 防泄露 / 计数一致 / 动态 E2E / L-id 引用 / 种子缺陷）是 `scripts/selftest.ps1` 头注的真相源——读那里，避免双源漂移。

## Codex 子代理派工（模型路由 + 调用形态）
> R3 合并闸与「临时派 Codex 干活 / 拿第二意见」共用同一个 codex CLI，但**该用哪个模型随任务性质变**。
> 方法论层（L26）：闸门要的是「第二独立模型 + 足够核验深度」，下表只是**当前默认映射**，换后端/换模型不破坏该标准。

| 任务性质 | 模型 | 备注 |
|---|---|---|
| 商用级实现 · 架构决策 · 安全评审 · **终审/R3 合并闸** | `gpt-5.6-sol` | `_config.ps1` 的 `ReviewModel` 即钉此档；合并闸与安全面**不降档**（降档=拿核验深度换过闸率） |
| 仓库探索 · 文档通读 · 大体量只读消化 | `gpt-5.6-terra` | 配合「大体量只读不进主上下文」：隔离上下文里消化、只回传结论 |

> **不设小模型快档**：曾列 `gpt-5.3-codex-spark`（快速迭代档），2026-07-29 移除——小模型核验深度不适配本仓风险面（元层改动多触闸门/模板/安全面）；小而明确的活留 Claude 侧 Sonnet（低 effort），或仍走 `gpt-5.6-sol`。

调用形态（**Windows 坑见 L146**：npm 装的 codex 是 `codex.ps1` 包装，只带 Bash 的插件子代理解析不到、会误报「未安装」——一律用 PowerShell 直调）：

```powershell
Get-Content <promptfile> -Raw |
  & codex exec -s read-only -C <repo> -m <model> -c model_reasoning_effort=<v> `
      --ignore-user-config --output-last-message <outfile>
```

- 只读第二意见 → `-s read-only`；派它**改代码** → `-s workspace-write` 且 `-C` 限定到该卡 worktree。
- 只把 `--output-last-message` 的产物读回主上下文，别把整份 transcript 灌进来（同 L85 ⑤ 收窄输出）。
- `--ignore-user-config` **只给评审用**：它让评审者 hermetic（不吃仓外、GUI 可改的用户级配置），但**同时丢掉 codex 的可信目录清单 → 沙箱被降级回 `read-only`，`-s workspace-write` 形同虚设**（2026-07-22 实测：带该 flag 时启动横幅打 `sandbox: read-only`、补丁被拒且一个字没写；去掉即 `sandbox: workspace-write [workdir, /tmp, $TMPDIR]`）。**派它改代码时别加这个 flag。**
- `-C` 必须指向 git 仓/可信目录，否则直接 `Not inside a trusted directory and --skip-git-repo-check was not specified.` 退出 1（临时空目录会撞这条）。
- 同族技术债：TD100（fan-out 档位下沉）· TD105（R3 档位）· TD106（降档实测校准）。
