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
- **项目内复用资产沉淀归位（验证过就沉淀）**：验证过的资产按类型落到固定位置——**页面结构/组件规范/业务判断/路由约定** → `context/`（项目约定，入库共享）；**踩过的坑** → `lessons`（`scripts/lessons.ps1 add`）；**架构/选型决策** → `docs/adr/`；**元层（脚手架自身）的缺陷/改进** → **上游 issue**（`scripts/scaffold-sync.ps1 report`，账在 `docs/SCAFFOLD-SYNC.md`）。判据不靠感觉：修复该落在 `scripts/`/`.claude/`/`.github/` 就是上游的事，落在 `android/`/`configs/` 就是自己开卡。**红线**：别把业务具体实现（表单/列表/结果页模板等）塞进可复用元层（CLAUDE.md / 脚手架）——元层只装**工具无关的约定/标准/清单**，项目资产沉淀到 `context`/`lessons`/`adr`。

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
<!-- 随 R5 文档同步更新。 -->
需求已收口 + **设计已定稿**（ADR-0001–0004、ADR-0006）+ **用户已签认**（2026-08-15：ADR-0002 / 2 套以上物业部分在租 / 租客联系方式留 12 个月 / 不做双刻度与费用字段，见 `docs/TASK-BOARD.md`「用户已定」）。ADR-0006 的 accepted 依据是需求 §11 的 `[定]` 合同及其在本 ADR 中的收紧，不另宣称一次未入账的签认。技术路线 = **原生 Kotlin + Compose**（ADR-0001）；任务卡 `specs/tasks/` 存未合并活卡、`specs/archive/tasks/` 存已合并历史，模型路由总表 `docs/TASK-BOARD.md`。

**W0 已完成**：`T0-TOOLCHAIN` **merged**（2026-08-15，R3 pass 于 `5fec73c`，9 轮评审）——JDK 17 + Android SDK（用户级 `JAVA_HOME=C:\Android\jdk-17` / `ANDROID_HOME=C:\Android`）+ `android/` 双模块骨架（`:core` 纯 JVM / `:app` Compose 壳）+ 全项目依赖目录 pin（compileSdk 35、Compose BOM 2026.06.01、TestNG 而非 JUnit——JUnit=EPL 禁列）+ CI 收紧至 windows-latest。verify 的 Android 闸已收紧（哨兵「Android :core check 全绿」）。
> 评审途中拆出新卡 **`T0-GATE-HARDENING`**（许可闸递归发现 + verify 确定性 + 两枚闸门自测 + 许可政策），承接被撤销的三次破例，见该卡「拆分依据」与仲裁段。

**W1 首个产品卡已合并**：`T1-SKELETON-E2E` **merged**（2026-08-16，master `19fd908`，R3 pass 于第 **2** 轮）——
一次性 walking skeleton（建巡检→加一项→拍一张→SAF 导出一页 PDF），4 文件 258 行、零新依赖、只在 `:app/skeleton/`。
**它是可抛弃代码**，`T2-CAPTURE-UI` 落地时整包删。真机走查产出三条产品反馈，已按归属记进该卡（画质→`T2-PHOTO-PIPELINE`
· UI→`T2-CAPTURE-UI` · ghost overlay→`T3-HISTORY-COMPARE`），**不回流本卡**。

**同日两处流程收口**（起因：19 小时 3 张卡 30 次 R3 block、零产品代码）：
① **R3 轮次封顶** `ReviewRoundCap = 2`（`scripts/_config.ps1`）——到顶不唤起评审者，1 秒出 `[R3-ROUND-CAP]` 转人裁；
**不是放行阀**（仍 block、仍非零退出），只止损（`ReviewTimeoutSec=3600`，每轮最坏 1 小时）。计数器随 worktree 生灭，
`review.ps1 -ResetRounds` 清零。恢复路由见 `docs/QUALITY-RUBRIC.md` §5。
② **rubric 加两条立场**：block 理由必须在本卡内可修（要动 `allow_paths` 之外或 `non_goals` 之内 → 记 `[FOLLOW-UP]`、不 block）；
维度按**卡片自己声明的 DoD** 判（spike/骨架卡声明的验收即满足 #6）。

**仓库已 public**：`https://github.com/Asun28/MyInspection`（`origin` 为唯一 remote，MIT）。`-Local` 不再是唯一选项，PR 流程可用。
⚠️ `T1-SCHEMA-CORE` **推送/合并前**须先摘掉 `android/core/src/main/sqldelight/databases/1.db`——它在该分支两个提交里、
但**不在 tip tree**，故 **squash 合并即可完全绕开**，无须改写历史（squash 亦是 `gh-bootstrap` 给远端配的策略）。

**★冻结点已合并**：`T1-SCHEMA-CORE` **merged**（2026-08-16，master `fcdc88d`，R3 pass 于第 **17** 轮）——13 表全量
schema + UUIDv7 + `core/model/` 快照类型 + 70 个 JVM 测试。合并后 `android/core/src/main/sqldelight/` 已登记进
`_config.ps1` FrozenPaths（实测 `guard-frozen` 拒绝就地编辑）：**此后加表/改列/加查询 = 新 `.sqm` + 版本评审，
并通过已恢复的 `verifyMigrations` 与逐路径 schema 快照防泄露闸**（TD4 已 paid）。`core/model/` **刻意不冻**——T1-CANON-HASH 落地时哈希域
形状若需微调属正常演进，形状已由 `InspectionSnapshotTest` 的逐字段断言钉住，不会静默改变。
> 17 轮里修掉 **9 个真缺陷**（6 个配单句删除变异证明），含两个数据丢失级（孤儿清理会删掉仍被引用的物理文件；
> 已被引用的模板版本仍可加项）、一个确定性级（`items[]` 无全序 ⇒ 同数据两个 `data_hash`，而该哈希是 PDF 页脚的
> 防篡改自证）、一个隐私级（`privacy_flag` 无 CHECK ⇒ 含租客物品的照片绕过排除查询进报告）。
> **结论两条并存**：这些洞赶在冻结前抓到了，值；但 17 轮也证明「全量 schema + 13 表 + 冻结点」当初就该按表族拆成
> 2–3 张——**后续冻结点卡按表族拆**（详见该卡「修订之十一」的编排者自评）。

**★第二冻结点已合并**：`T1-CANON-HASH` **merged**（2026-08-16，master `4681e69`，R3 pass 于第 **2** 轮）——
`core/canon/` canonical JSON 序列化器（RFC 8785 风格：UTF-16 码元键序 + NFC 归一 + 整数规范拼写 + 良构 Unicode
强制——lone surrogate 会被 UTF-8 静默换 '?' 致两串同哈希，故直接拒绝）+ SHA-256 + 快照投影 + supplement 哈希链 +
4 组黄金向量（20 个 JVM 测试）。**黄金向量三方独立复算一致**（执行侧独立 Python 预计算 / DeepSeek 交叉复核 /
Sol 评审时自行复算），**14 枚单点变异逐一击杀**（判据分类器 + SHA 还原核验，记录见卡）。合并后 canon 主/测试
目录已登记 FrozenPaths（**黄金向量测试即契约本体，一并冻结**）；演进走版本评审。方法论沉淀：步骤 4.6 本地自检
先吃掉 10 条发现（省一轮 R3 往返），R3 首轮仍抓到 2 条真问题（`HexFormat` 需 Android API 34 而 minSdk 26；
键序测试纯 ASCII 区分不了 UTF-16 码元序与码点序）——**`:core` 纯 JVM 只是测试形态，运行载体是 ART，
JDK API 可用性按 Android API level 判**（L217）。TD5（数组序跨层机检归 T3-FINALIZE）与 TD6（Supplement.sq
注释哈希域指向）均已由后续卡偿还（TD6 见 PR #190）。

**★第三冻结点已合并**：`T1-TEMPLATE-ENGINE` **merged**（2026-08-16，master `72ec5e6`，**5 轮 R3 后经人裁合并**）——
`core/template/`：模板 JSON schema（`Template`/`TemplateItem`/`TemplateDomains`）+ 加载校验器 + 入库读回 +
`alignHistory`（按 stable_id 出 沿用/新增/移除 三份清单）+ `data/templates/README.md`（内容作者指南）。
**29 个 JVM 测试、18 枚单点变异逐一击杀**（判据分类器 + 每枚还原后核 SHA）。合并后 `template/Template.kt`
已登记 FrozenPaths（模板 JSON 形态即契约，改=版本评审）；加载器/入库器**不冻**（实现可演进，形态由测试钉住）。
> 5 轮里修掉 **7 个真缺陷**：`LoadedTemplate` 可伪造（我自己的测试就是证据）· 校验器承诺"一次报全"却提前返回 ·
> 只读集合非不可变（哈希算完仍可强转改写）· `toString(UTF_8)` 静默替换坏字节（库里内容与文件对不上而无人知）·
> INGOING/EXIT 分支从未被加载过（删掉照样绿）· `affected == 1` 守卫无任何测试能让它红 · `content_hash` 由调用方
> 随手递入。**修法两次都是把不变量做进类型**：`LoadedTemplate` 构造器私有、唯一出生点 `parse(bytes)` 只收字节，
> 于是"合法模板配假哈希入库"这条路不是被运行期拦下，而是写不出来——`persist` 的重复校验遂成死代码，一并删掉。
> **争点经人裁**：房间 `repeatable` 标记被评审者连提 4 轮（其自己第 1 轮判为 `[FOLLOW-UP]`），因持久化它须改
> 已冻结的 `sqldelight/`、只加 JSON 字段又会造出"入库静默丢字段"路径，故拆出 `T2-ROOM-REPEATABLE`
> （已进 board + 本卡 `non_goals`），用户裁定按此办并授权合并；该承接卡现已由 PR #190 合并。
> **同工作树并发事故**：另一会话（刚合完 T1-CANON-HASH）依陈旧 handoff 也开了本卡、在同一 worktree 改文件，
> 撞在变异批中途——靠变异脚本的 SHA 基线守卫当场停住（L196 生效），未污染任何提交。

**模板房间契约与 schema v2 已合并**：`T2-ROOM-REPEATABLE` **merged**（2026-08-29，master `deb01fe1`，
PR #190，R3 第 **2** 轮 pass）——`Template.rooms[]` 以向后兼容默认接入，房间 key / repeatable / 模板序经
`template_room_def` 持久化并完整读回；`1.sqm` + `databases/2.db` 通过真实迁移闸。历史模板读取现在包含软删的
item/room 定义，并用 `(template_version_id, sort, id)` 索引保持有序读；TD6/TD7/TD8 同窗口 paid。运行时
`room_instance` 多实例状态机由下述运行时卡承接并已闭合。

**重复房间运行时已合并**：`T2-REPEATABLE-ROOM-RUNTIME` **merged**（2026-08-29，master `6a92aa58`，PR #193，
R3 第 **2** 轮 pass）——schema v4 的物业级数量、模板序 + `instance_no` 实例化、缺实例 finalize 闸与
`(room_key, instance_no, stable_id)` baseline 对齐已统一落地；TD26 paid，capture UI/history 前置已同步。

**★第四冻结点已合并**：`T5-BACKUP-FORMAT` **merged**（2026-08-16，master `efedcfb`，R3 pass 于第 **4** 轮，
其中两次经人裁）——`core/backup/format/`：47 字节明文头（magic `MYINSPBK`/format_version/kdf_id/迭代数/盐/
nonce 前缀/口令校验值）+ **分块 AES-256-GCM** 密文体（内含 zip：`manifest.json` 首条目 + `db.sqlite` +
`photos/**`·`audio/**`·`configs/**`）+ manifest（canonical JSON 复用已冻结的 core/canon）+ 写读两侧逐文件
复核 SHA-256 与双向完备性。**84 个 JVM 测试、30 枚单点变异逐一击杀**（判据分类器 + 每枚还原后核 SHA）。
合并后 `core/backup/format/` 主/测试目录已登记 FrozenPaths（格式锚点测试即契约本体，一并冻结）。
> **两处偏离卡片草图，均经人裁写进卡片「格式评审记录」**：① 密文体是**分块 AEAD**而非一路 CipherOutputStream
> ——JCE/Conscrypt 的 AEAD 会把整份密文缓冲到 `doFinal`（实测 JDK 17 SunJCE：解密 `update(1 MiB)` 交出 **0 字节**），
> 与卡片自己的硬不变量「GB 级照片、恒定内存」冲突；且任何「边解密边交明文」的单发变体都会在验 tag 前
> 交出未认证字节。分块后 nonce = 前缀‖块序号‖final 标志、AAD = 整个头，于是**块序/块数/末块身份**全被 tag 认证。
> ② zip 容器 = 认证加密层内的**运输信封**，其 CD/EOCD **非规范性**、**manifest 是唯一权威**，
> **禁止未来实现信任 CD**（永久封死 local/CD 歧义面）。二者仍只组合 javax.crypto 标准件、零新依赖。
> **口令做 NFC 归一**（不同输入法的 NFD 口令否则永远打不开自己的备份，而本格式无口令找回）。
> R4 变异首轮 21/25，4 枚存活各暴露一处真问题：**2 个冗余守卫**（目录条目预检、未知键预检——「canonical
> 字节相等」那道闸已覆盖）当场删除、**1 个测试太弱**、**1 枚变异选错靶**；改完后续三批 23+3+4 全杀。
> 卡片写的「Terra 复读格式头」由 **DeepSeek V4 Pro 独立复读**代替（Terra 未接线；L26 标准=独立非 Claude 复读）。

**W2 并行窗口已收（2026-08-17 凌晨，编排会话：7 卡并行、6 合 1 悬）**：`T2-ROUTINE-CONTENT`（83 项双语模板，9 轮 R3，
含一次 must-block 许可命中：官方表逐字转写违反 Tenancy Services 再利用条款，改独立措辞）· `T2-CAPTURE-CORE`（76 测试，
7 轮）· `T2-PHRASELIB`（66 短语，L227：**R3 评审者只读 diff、看不见 PR body**——证据须落 diff 内）· `T5-RETENTION`
（Pacific/Auckland 民历月算术依 ADR-0004，非 UTC）均 **merged**；`T5-BACKUP-FORMAT` 见上方第四冻结点。**一卡悬置待用户裁**：
`T2-PHOTO-PIPELINE`（PR #6，:core DoD 面自第 1 轮起零挑战，5 轮 block 全落 :app 薄壳硬化深度——选项见 progress.md 决策简报）。

**`T3-FINALIZE` merged**（2026-08-17，master `a5a71ed`，PR #7，15 轮 R3 后合并，48 测试）——finalize 用例
（完备性校验 → canonical 快照装配 → data_hash 落库 → 只读强制）+ Supplement 追加哈希链。**唯一悬点由用户裁定收口**：
`DbCompletenessChecker` 是否该在 finalize 闸重验逐项 `allowed_statuses`——评审同一诉求三度提出（round 5/12/13 各按
mint-point/L220 驳回），第三次触发本仓「两轮争议转人裁」规则，用户裁**选项 A（实现该检查）**：per-item 合法性铸造点
仍在 `core/capture`（`setItemStatus`），finalize 闸再核一次是防御纵深而非重复劳动，新增
`CompletenessResult.itemsWithDisallowedStatus`。裁后重新评审又拦两条真发现：① `DbCompletenessChecker` 自己的
`classifyAdverseness`/`Adverseness` 三态分类器是仅论战胜利者留下的重复权威（ADVERSE/NOT_ADVERSE 从未被消费，只有
UNCLASSIFIABLE 影响结果）——评审用本仓自己的单一真相源原则反打回来，删成 `isInDomain` 纯布尔判定；② finalize 只读
强制此前只证明了冻结 SQL 谓词本身，没证明真实公开写入口（`InspectionRepository.setItemStatus`/`setWearOrDamage`）
真的会显式拒写——补一条经真实 INGOING→EXIT 巡检链路的集成测试。过程沉淀：L205 晋 Tier-1（修复轮也须对抗自检）·
L221/L227/L228（完备性门须全函数 fail-closed）/L229–L232 · TD9（selftest 可诊断性+load-flake）· **TD5 → paid**
（本 PR 为偿还指针）· TD10（多连接契约仲裁：**评审不得再以多连接证明 block 单连接卡**）· TD12/TD13。

**数据库生命周期权限已合并**：`T1-DATABASE-LIFECYCLE-AUTHORITY`（2026-08-29，master `3d50f690`，PR #191）完成 schema v3 active/any 读取分流、具名 baseline 守卫、purge 终态与 deleted override 防写；TD160 paid。

**报告语义边界已收口**：`T3-REPORT-CONTENT-ADAPTER` **merged**（2026-09-02，master `4ec952df`，PR #225，
R3 pass 于第 **2** 轮）——`ReportComposer` 的排版入口收为 `compose(content: ReportContent)`，签名里**没有**
`Audience` 也没有 `ReportOptions`，于是「受众/隐私在排版层被再判一次」不是被规则禁止，而是**写不出来**；
快照入口保留，经 `ReportContentAdapter` 投影后转调同一方法。页脚哈希改读 `content.nativeIntegrity.dataHash`
（不再由过滤后的快照重算），composer 与 projector 重复的 `validateProjection` 整段删除、单一权威归 projector。
新增 `ProvenanceBlock` 与独立标注的 provenance 分节，仅导入报告发出，原生报告页数/块序/几何逐字不变。
**17 枚单点变异全杀**；其中 M4 首轮存活，暴露「套件里每个 photo-only room 夹具都只有一张照片，无人能区分
房间开场照被取出与没取」这一真实覆盖缺口，补双照片夹具后连同 M15 一并封住。
> **两处值得复用的判断**：① A3 要的 provenance block 是 `DocumentBlock` 密封层级成员，须与 12 个同级块同文件，
> 而 `DocumentPlan.kt` 不在原 allow_paths ⇒ **动手前**请用户裁定扩 allow_paths（master `e237545a`），
> 而非把类型塞进别的文件绕开范围闸。② R3 第 1 轮指「直接入口对空房间无守卫」——该状态因 `ReportContent`
> 构造器私有 + projector 是唯一出生点而**不可表达**，遂按评审者给出的备选补「证明其不可表达」的测试，
> 并以 M17 证明该测试确实会红；**没有**补一道任何变异都杀不掉的死守卫。

**PDF 渲染程序层已合并**：`T3-PDF-RENDERER` **merged**（2026-09-02，master `a3d1e702`，PR #227，
R3 pass 于第 **2** 轮、零 finding）——`core/report/pdf/`：四档质量契约（需求 §8 的内联/附录 dpi，默认 MEDIUM）
+ mm→pt 几何 + 逐槽采样参数 + 逐页解码字节上界 + `DocumentPlan` → 有序绘制操作的 builder。27 个 JVM 测试、
**26 枚单点变异逐一击杀**（零编译型假击杀，收据钉四个生产文件的 SHA-256）。**纯数据层，零 Android 依赖**；
真正拿起 `PdfDocument`/`Canvas` 的壳与真机实测归 `T3-PDF-RENDER-DEVICE`，产物路径归 `T3-PDF-ARTIFACT-PATHS`。
> **本卡两次拆分，皆用户裁定**：① 开工前发现原卡把「本仓闸门可证明的纯数据契约」与「只有真机能跑的
> Android 渲染」混在一张卡——`:app` 单测是纯 JVM（无 `returnDefaultValues`、T0-TOOLCHAIN 禁 Robolectric），
> 且原 `dod_command` **根本不跑任何 `:app` 测试**，于是写进该卡 `app/src/test/**` allow_path 的测试永不执行
> （结构性 vacuous pass，已记 **L280**）；A2 的重开核验本就归 EXPORT-CORE，A4 的实测内存依赖未跑的 spike ④。
> ② R3 首轮三条 finding 全部成立并修完后 diff 达 1139 行 / 60679 字符、同时越两道硬闸，可砍注释补不上缺口，
> 遂把 A3 产物路径拆给 `T3-PDF-ARTIFACT-PATHS`。
> **R3 首轮三条都是真缺陷**：整数回绕（`decodedBytes` 可返回**负**内存上界；`xMm + widthMm` 回绕后
> 「越页几何」反而**通过**了 containment 闸——守卫 fail-open）· 缺少卡片自己要求的锚定 shape 判定 ·
> 测试落点未镜像源码单元。修法分别是照抄 `ImportBounds` 的饱和、给采样入参加上界、远边用 Long 求和。
> **方法论沉淀**：变异**编排阶段**（不是执行阶段）就吃掉两个覆盖缺口——附录 dpi 与内联 dpi 在多数夹具上
> 舍入到同一个 `inSampleSize`，故「用错密度」原本能全绿通过；而「块自身越页」与「块内 run 越页」原本无从区分。
> 另有一处自证反例：`assertEquals(DEFAULT, fromStoredValue(null))` 拿生产值和自己比，`DEFAULT` 被变异成 HIGH
> 时照样绿——已改写成字面量（同 `ReportSourcePurityTest` 对 composer 的纪律）。
> **语义指纹不写进 PDF 字节**：`PdfDocument` 公开面只有 `startPage`/`finishPage`/`writeTo`/`close`/`getPages`，
> **无任何文档元数据 API**（经 Context7 核官方 reference）；画到页面上属渲染器自造内容、且会让 PDF 与 HTML
> 两版不一致。故 fingerprint 作**程序身份**随程序旅行，落账归 `T3-REPORT-EXPORT-CORE` 的 receipt。

**当前已解锁待做**：`T3-PDF-ARTIFACT-PATHS`（依 T3-PDF-RENDERER，S 档、草稿在 `_local/`）· `T3-PDF-RENDER-DEVICE`（另依 `T1-SPIKE-PLATFORM` 真机 spike）· `T3-REPORT-HTML-RENDERER`
· `T3-DOCX-PACKAGE-READER` · `T3-REPORT-INTERCHANGE-SCHEMA` · `T2-ROUTINE-CONTEXT-V2` ·
`T5-BACKUP-IO`（依 backup-format）· `T4-COMPLIANCE-ENGINE`（依 schema；**设计前置=L228 fail-closed 门纪律**）。

**T0-GATE-HARDENING 的事后 R3 已结清**：其合并 `5ba3319` 未经 `task.ps1 ship`（`-SkipRed` ×2），post-hoc R3
block ×2 且经复核属实；用户裁定 **fix-forward 不 revert**，承接卡 `T0-GATE-FIXFORWARD` 已 **merged**
（2026-08-16，master `6f255d3`，PR #4，R3 pass 于第 **4** 轮）——许可闸五个路径比较调用点统一走 OS 感知比较器
（`Test-GradleNameEquals`/`Test-GradleNameInList`/`Test-GradlePathPrefix`），发布清单 Gradle 阻断项收敛为
**单一解锁路径**（人裁：删掉人工核验替代，唯一解锁 = `T0-LICENSE-SCANNER` 落地），12 枚变异各自按专属失败码击杀。
> 病根值得记住：`-contains` 恒**不敏感**、`String.StartsWith(string)` 恒**敏感**，一行之内两套语义 ⇒ Linux/CI 上
> 被追踪的 `Build/`、`Data/` 被当成 ignore 的小写目录**静默剪掉**——漏扫是静默的，闸在看不见时反而变安静。
> 三轮 block 的同一根因是「重构完 narration 还停在上一版形状」（L224）与「新断言没有能打到它的变异——
> 短路顺序下靠前的断言会掩护靠后的」（L225）；另有 L226（拼装文件时 PS7 的 `-Encoding utf8` 静默抹掉 BOM）。

下一步：① **用户裁 `T2-PHOTO-PIPELINE`**（三选一，简报见 progress.md）；
② 进 **W3**（`T3-REPORT-COMPOSER`★ 已解锁、为关键路径头牌）或继续 W2 余卡（`T5-BACKUP-IO` 等，一卡一会话）；
③ `T0-LICENSE-SCANNER`（偿还 TD2；落地即解锁 `docs/RELEASE-CHECKLIST.md` 的 Gradle 发布阻断项与 `-Strict` 退出 0）；
④ `T1-SPIKE-PLATFORM`（需用户真机约 15 分钟）。

**复发计数不再随卡片丢失**：`T0-LESSONS-BUMP-PLANE` **merged**（2026-08-23，master `edc2770`，PR #129）——
`lessons.ps1 bump` 的账本平面由 `$PSScriptRoot/..` 改为经 `git rev-parse --git-common-dir` 解析的**主检出**，
于是在卡片 worktree 里跑 bump 也立刻落账、且永不进卡片 diff（此前它会被范围闸与 R3 #7 正确拦下，计数遂
结构性丢失——卡片工作全在 worktree 里做）。只有 `bump` 走这条平面：`add` 随卡入库是有意的，`promote` 只打印。
Stop 钩子 `lessons-reminder` 补上了从头到尾缺失的 `bump` 入口。**R3 四轮 + 一次 R3 前 codex 预审共出 5 条真缺陷**
（漏判 git 退出码 · 闸只在 Windows 真跑过 · 继承的 `GIT_*` 可把计数写进**另一个仓库** · 闸未证明「那次禁止的写入
没发生」），8 枚变异全杀；末轮 pass 零发现。**方法论**：ship 前先把 `origin/master` 合上来——第 1 轮 3 条 finding
全是「分支落后一个提交」被评审读成「本分支在撤销它」的假象，白烧一轮。

**W5 Golden Evidence JVM Core E2E 已闭环、隔离并修复跨平台 Gate**（2026-08-27，PR #180–#185，终态 master `56f2bc51`）：真实
`routine-v1`（83 项）+ inspection/9 张 photo evidence + expected `data_hash` + landlord/tenant sentinel 已冻结；纯 JVM
链路经真实 SQLite/finalize/report 验证 **DB hash = 报告页脚 hash = 独立 DB 重读重算 hash**，tenant report 保留 public
内容且不泄露 landlord/private/remediation sentinel。Golden Evidence 已迁入独立 `e2eTest` source set；普通
`:core:test` / `:core:check` 不再编译或执行它，`scripts/verify.ps1` Gate 2 精确执行
`:core:e2eTest --offline --no-daemon`。Gate 1/2 在 Windows 经 `cmd` 执行 `.bat` wrapper，在 Unix 经 `sh` 执行
`gradlew`（不依赖 executable bit）；wrapper 缺失、命令未执行、任务错误或测试非零一律 fail-closed；Android UI、
权限、TalkBack、模拟器/真机与进程死亡仍只属于 `T7-SMOKE-POLISH`，不混入 JVM E2E。

**W5 T4-NOTICES 已合并**（2026-08-29，PR #199，master `b77fbc21`）：通知生成先过配置驱动合规闸，固定双语
全文、生成/巡检时间、提前量和校验结果均存审计快照；app 只在用户确认边界后写带 sensitive metadata 的剪贴板，
不发送短信/邮件。人工回记要求方式与真实时刻，生成前/未来时刻不落库，首条送达原子锁定；掉出通知窗口仍诚实存档并
以 error 文案、颜色和无障碍语义提示改期。7 个 SQLite JVM 测试与 7 枚定向变异覆盖关键门。

**W5 提醒串行链 3/5 已合并**（`T4-SCHEDULE-REMINDER-*`，2026-09-01）：`CONTRACTS`（master `a1fb2bbe`，PR #215）
定身份/路由/文案/隐私描述符与精确关联诊断 · `RECEIPTS`（master `3c08c2dd`，PR #217）定应用私有 v1 耐久回执、
损坏隔离与 generation CAS · `DELIVERY`（master `41793005`，PR #219）定 Worker、通知发布与不重投边界。
**DELIVERY 的形状值得复用**：preparation 与 notifier 是两个独立端口，于是「失败发生在发布之前」由**哪个端口抛的**
决定，而非一个任何调用方都可能忘记套的异常包装器——A3 重试阶梯与「一旦发布绝不重投」遂是结构性的、非约定性的。
余 `SCHEDULER`（WorkManager 唯一注册与 callback 边界，**必须用 `reminderGenerationId` 派生的 UUID 建 WorkRequest**，
否则 Worker 每次运行都在 INPUT 阶段被拒）与 `T4-SCHEDULE-UI`。


**W0 闸号协调债已结清**（2026-08-28，PR #186，master `b1e5f0b5`）：`selftest.ps1` 现在从闸头注释与真实
`Fail` 文案解析 gate id，重复声明会点名 id 与全部位置；同时强制唯一 raw 插入锚、源码可解析、双面集合与词法 owner
一致，并以 6 类删除变异防止守卫静默失效。TD146 已 paid。

**TD9 五张子卡已全部合并**（2026-08-29，PR #189，master `95f74222`）：8.2e rendezvous 现用具名的
30 秒默认预算与可注入的 1..120 秒测试预算；marker handshake、单调时钟及有界子进程清理共同覆盖旧 5 秒调度延迟、
真实 timeout 与 early-exit，并由删除/替换变异锁住确定性。DoD、focused gate8、verify 与 R3 均通过；TD9 仍保持
carded，仅余一次 post-merge core 重放，稳定后才可置 paid。

**候选 CI 的 run 身份绑定已合并**（2026-09-01，PR #220，master `424009ee`）：`T37-CIGATE/WORKFLOW-BINDING`
把「已过 R3 的本地 HEAD ≡ PR `headRefOid` ≡ run `head_sha` → run id/attempt → 终局 exact-head/base 快照 →
`merge --match-head-commit`」逐层钉住，任一层不匹配即 fail-closed。`path`/`event`/`pull_requests[].number`
三处一律**大小写敏感**比较——PS 的 `-eq`/`-in`/`-contains` 与**属性访问本身**默认不敏感，只带 `Number` 的关联
条目原本可借此过闸并合并（单点变异实测：改回 `-contains` 该负例即 exit 0 一路合并）。每条负例的判据是
「专属哨兵 + 精确读计数（证明停在链上哪一环）+ 未触达合并 + 效果账本点名被造坏的那一处」，5/5 变异全杀。
> **本卡被拆了两次，都是用户裁定**：① diff 达 61233 字符 > R3 的 60000 完整读取预算 ⇒ API 侧 job 集漂移拆给
> `T0-CI-JOBS-DRIFT`（DEVOPS-WORKFLOW §35：超限必须拆卡，不得放宽限额）；② R3 连续三轮的实质 finding **全部**
> 落在 deadline/进程树那段机器上（无界 `WaitForExit()` · 单点挂起分辨不出「共享预算」与「每调用各计时」·
> 子进程早退致孙进程漏杀 · 清理余量串行花两遍 · 容纳非 fail-closed 且漏关句柄）⇒ 整段拆给
> `T0-CI-DEADLINE-CONTAINMENT`。留在本卡的 A1–A4 三轮零实质 finding。**教训**：一处反复吃 finding 的子问题
> 通常是独立子问题（自带平台原语选型），越早拆越省轮次——而不是「再修一处」。

**Gradle 测试输入假绿已封口**（2026-08-29，PR #188，master `6fda9f88`）：`:core:test` / `:core:e2eTest` 现在精确声明
运行期读取的 compliance 配置、双语模板与源码文件，权威文件或 source-purity 目标变化不会再被 UP-TO-DATE / FROM-CACHE
静默吞掉；T3/T4 的 Gradle DoD 同时强制 `--rerun-tasks --no-build-cache`。配置、模板与字节码中性源码变异均已证明先红后绿。

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
   - 配套解读：`docs/HARNESS-LIFECYCLE-AND-R3-DIAGNOSIS.md` — 本项目需求→设计/技术债→任务卡→R1–R5→PR/merge 的逐步说明，以及 PR #20 的 R3 耗时证据与改进点；**非第二真相源**，冲突时仍以本索引中的权威合同/脚本为准
10. `docs/references/` — 喂给 agent 的依赖文档层：**静态** `*-llms.txt` +（当前默认）**动态** Context7 MCP（按**实际 pinned 版本**取实时 API 文档，治「模型幻觉过时/错版本 API」；根 `.mcp.json` 已声明）；「不在上下文里=对 agent 不存在」。索引/用法见 `docs/references/README.md`；自带 `uv-llms.txt`
11. `specs/tech-debt-tracker.md` — 技术债追踪（持续小额还债，非周期大修）
12. `docs/PROJECT-BRIEF-TEMPLATE.md` — 产品简报模板（规划上游：what/why；由 `shape-idea` skill 即漏斗第一步产出，照 `docs/PLAN-TEMPLATE.md` 手工扩写成 PLAN）
13. `docs/LOOP-ENGINEERING.md` — **三层回路**（①AI 编码/分钟 ②人定方向/小时 ③真实用户/天–周；及③的两条回流正门）+ 心跳（cadence 发现+分诊，`scripts/triage.ps1`）+ 理解债闸 + judgment 经验（loop-engineering / RSI 落地）
14. `docs/EVAL.md` — 能力完成度自评方法论（opt-in stub：确定性 exit-0/1 eval 标准；四维 frontend-behavior/backend-mcp/security/functional；LLM-judge 留上游不当闸；不带 runner/套件，下游自建自接 CI）
15. `docs/DELIVERY-OPS.md` — **合并之后**交付/运维方法论（opt-in 姊妹篇：集成/e2e 测试层 · 结构化日志/可观测 · 灰度+feature-flag · CD 部署/回滚/staging；全为方法论+标准+占位、工具无关；**脚手架永不自动发布**，CD 下游接线）
16. `docs/RELEASE-CHECKLIST.md` — **发布前收口清单**（工具无关、可勾选）：整合已有闸（防泄露 `check-secrets -Strict` / `verify`）+ 授权/认证安全自查（越权 IDOR/会话固定/token 存储/CSRF/密码哈希）+ 可观测 + 灰度/回滚。小项目按需取子集
17. `docs/FRONTEND-FLOW.md` — **前端生成闭环**（T2 档 · 复杂多页前端）：四段串现有件（生成前/中/后/资产回流）+ **流程卡(页面地图)** 与 **意图卡(单页目标)** 两个模板；流程卡→喂 `plan-forge`、意图卡→`grill-design` 拷问敲定；驱动卡 `.claude/skills/frontend-flow`。**不重造引擎**，简单单页前端直接 `frontend-design`+pencil
18. `docs/SCAFFOLD-SYNC.md` — **fleet 回路 + 决策账**：`check` 展示上游 Downstream 耦合组；`report` 反哺 issue；每版记 applied/partial/skipped。`ScaffoldOriginVersion` 是不可变来源（v0.29.0），`ScaffoldVersion` 是已裁决高水位（v0.45.0）；缺/坏账只回退 origin。`scaffold-stale` 只读本地 ref、绝不 fetch
19. `docs/DATABASE-DESIGN.md` — 离线主证据库、诊断库、文件存储、写权限、生命周期、读模型与诊断导出的设计权威
20. `docs/adr/0006-offline-security-backup-hardening.md` — ADR-0002 的离线安全、密钥、provider 失败隔离与恢复验证加固；保留整包/按物业备份范围
21. `docs/UI-UX-ELEMENTS.md` — UI 页面、Overlay 与状态的 Elements 覆盖索引；规范细节唯一服从 `context/DESIGN.md`

## 开发工作流（每张任务卡，详见 docs/DEVOPS-WORKFLOW.md）
单卡闭环：`scripts\task.ps1 -TaskId <ID> -Phase start|ship|cleanup`
- **R1 worktree**：每卡建 `<WorktreeRoot>\<ID>` 隔离分支（.venv/node_modules 每树独立、gitignored）
- **R2 TDD**：先写失败测试→实现到绿→重构；契约测试 mock 必 100% 过
- **R3 pre-push + PR/Codex 评审代替人工**：ship 在 push 前依次强制 DoD、verify、范围、许可、防泄露与**真实 diff 预算闸**；随后 `review.ps1` 按 `docs/QUALITY-RUBRIC.md` 判（注入 rubric + 反自我开脱立场），出 `{verdict:pass|block}`→回贴 `codex-review` 状态；
  有 Pro 规则集则 `verify`(CI)+`codex-review` 双绿自动合并；free+private 由 review.ps1 退出码本地强制；**阻断态可诊断**——「跑完了但读不出可用裁决」分四态各带 ASCII 状态码 + 恢复路由（见 rubric §5），拒答原文另存 `.review/(分支名).raw.txt`
  - **评审者的模型/档位钉在 `scripts/_config.ps1`**（`ReviewModel`/`ReviewEffort`，留空=后端默认）：别让**用户级**
    `~/.codex/config.toml`（GUI 可改）决定本项目合并闸的生死——它一旦被改成当前 CLI 不支持的模型，R3 对所有 PR 都会 fail-closed block
- **CI 触发形态**：`ci.yml` 跑 `[main, master]` push+PR；`verify` 是必需检查。`scaffold-selftest.yml` 仅默认分支 push/手动 canary，每个 OS 跑 core/workflow/seeded-git/remote/scanner 五片，拆掉超 20 分钟单片而不减覆盖（8.2d/8.2e 锁死）。合并前仍由卡 DoD + verify + R3 守门。
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
  `lessons.ps1 bump` 的当前路由先规范 common-dir 与 `git worktree list --porcelain`：普通仓取首条真实 checkout；
  submodule 仅在首条等于 common-dir 时按 repository-local `core.worktree` 解析，并以候选 `show-toplevel` + common-dir
  反向验同仓。初始仓库探针失败才回落；仓已识别后的拓扑失败统一 `[LSN-PLANE-UNRESOLVED]` fail-closed。
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
- Android 工程（T0-TOOLCHAIN 落地后）：全部测试/静检 `cmd /c android\gradlew.bat -p android --offline --no-daemon :core:check`；装机包 `:app:assembleDebug`；装环境步骤见 `specs/archive/tasks/T0-TOOLCHAIN.md`
- **验收总闸门**：`scripts\verify.ps1`（确定性、无网络跑通最小闭环）
- **工作流自检**：`pwsh -File scripts\selftest.ps1`；完整 17 闸本地聚合 core/workflow/seeded，CI canary 用 2 OS × 5 片；任务卡可选单片作 DoD。
- **范围检查**（核「改动 ∈ 卡 allow_paths」；与 ship 范围闸共用判定核 `scripts/_scope.ps1`，越界/不可判即非零退出，**不自动 fetch**）：**诊断式**（不承担绑定）`pwsh -NoProfile -File scripts\check-scope.ps1 -TaskId T1-FOO -Base master`（`-Local` 判本地那棵）；**已推送状态的手工恢复必须用完整式**——跑**主检出**那份 checker（相对自身位置加载判定核，从被审工作树跑＝被审分支自己判自己，同 L86 之理）、`-Path` 指被审树，先 `git fetch origin master T1-FOO`（**fetch/gh 非零即中止**——陈旧 `origin/*` 会让 allow_paths 都取自旧卡，空 head 会把绑定静默关掉）、**核 PR 的 `baseRefName` == 本次判定的 base**（判定前 + 合并前各一次；PR 被 retarget 会「按 A 判往 B 合」）、**合并前再复核基线 OID 未前移**（名没变但 base 前移时，合并落到新基线而 allow_paths 取自基线那份卡 ⇒ 判定依据已变，须重跑），再把两侧 OID 都钉进闸 `pwsh -File <主检出>\scripts\check-scope.ps1 -TaskId T1-FOO -Base master -Path <被审树> -ExpectTip $head -ExpectBase $baseOid`，合并配 `gh pr merge --match-head-commit`（权威序列含退出码检查见 `docs/DEVOPS-WORKFLOW.md`）
- 依赖许可扫描（加/升级依赖后必跑）：`pwsh -File scripts\check-licenses.ps1`

## 架构大图
单用户、单设备（Android）、**local-first** 的房产巡检 App：按模板逐项走查 → 拍照（ghost overlay 对位历史机位）/ 系统听写 / 短语库备注 → 生成双版本 PDF 报告（房东版含 LLM 整改建议，房客版纯客观）→ NZ 合规校验 + 48h 通知生成与送达存档。无服务端、无账号；数据全在本地（app 私有存储：SQLite + 文件系统照片/音频），备份 = 加密归档经 SAF 导出（ADR-0002）。技术路线 = **原生 Kotlin + Compose，2 模块**（ADR-0001）。

| 路径 | 职责 |
|---|---|
| `docs/inspection-app-requirements.md` | **需求真相源**（[定]=已决定 / [待]=需确认 / [验]=需 spike） |
| `docs/adr/0001–0004`、`docs/adr/0006-offline-security-backup-hardening.md` + `docs/TASK-BOARD.md` | 设计决策 + 任务/模型路由总表（状态以卡为准） |
| `android/core/` | **纯 JVM 领域**：model / db(SQLDelight ★) / template / compliance / report / backup / canon ★ |
| `android/app/` | Android 薄壳：Compose UI · CameraX · SAF · 听写 · PdfDocument 渲染 · WorkManager |
| `configs/compliance/` | 可更新的 NZ 合规规则配置（不硬编码；schema 含 entryPurpose，ADR-0004） |
| `prompts/remediation/` | LLM prompt + 「检查项 → 建议」种子对照表（需求 §9） |
| `data/templates/` | 巡检模板内容真相源（四类、双语、带版本号；构建期拷入 assets） |
| `docs/research/` | 竞品 UX 调研（Opus 5 产出，喂采集 UI 与模板内容卡） |

## 硬边界（不可违反）
- **永不做**（需求 §1 写死，防范围蔓延）：租金/账务 · 房客筛选/背调 · 工单派发 · 房源广告 · 押金托管 · **任何账号体系** · **任何服务端功能** · 多用户/权限 · 模板编辑器 UI。
- **local-first**：数据（SQLite + 照片/音频文件）永在本地；唯一联网点 = remediation 时调 LLM API（自己的 key，可完全跳过）；不做云账号、不做遥测。app **自己不发送**通知（只生成 + 一键复制，人工发送后回记存档）。
- **合规校验为阻断闸、不可关闭、不进设置页**（需求 §10）：4 周内不得重复 Routine（法律上限；Ingoing/Exit 不计入）· 通知提前量 ≥48h 且 ≤14 天 · 巡检落在 08:00–19:00（寄宿公寓 08:00–18:00）。
- **LLM 建议只进房东版**报告；定位 = 提示 + 分级（NZS 4306 思路）+ 建议找谁，**不做诊断/处方/成本估算**；报告必带免责声明。
- 隐私（Privacy Act 2020）：备份包**必加密**（含租客照片/联系方式）；租客数据设明确保留期限 + 可一键清理；`.env` 与密钥永不入库。
- 测试/verify/CI 走确定性/离线路径（LLM 调用全 mock，禁出站网络）。
- **依赖许可（见 `docs/LICENSE-POLICY.md`）**：禁 GPL/AGPL/SSPL 等 copyleft 与 non-commercial 素材；仅 MIT/BSD/Apache 等宽松；每加依赖跑 `check-licenses.ps1`。

## 关键不变量
- **检查项 ID 稳定**：历史对齐只靠 ID、不靠名字；模板带版本号——改措辞不改 ID，加项给新 ID（需求 §4）。
- 主键一律 **UUIDv7**（禁自增整数——同步时是死局）；每表带 `updated_at`（UTC）+ `deleted_at`（软删除）。
- 照片/音频存**文件系统**，DB 只存相对路径 + 内容哈希，**禁 BLOB**。导入照片必存：EXIF 拍摄时间（与巡检时间分开）+ 来源标记（`camera`/`imported`）+ 内容哈希防重；**复制不移动**原文件，EXIF 旋转必须处理。
- **finalize 后原始条目只读**，只允许追加带独立时间戳的「补充说明」；导出 PDF 页脚写入该次巡检数据哈希（自证未事后修改）。
- 基线引用双轨分开存：`previous_inspection`（时间上前一次）≠ `baseline_inspection`（该 tenancy 的 Ingoing）；**Exit 默认对照 baseline**、不是上次 Routine。
- **原始音频永远保留**（识别会失败；换模型后可重跑历史音频），存照片同目录、报告里不出现。
- schema/迁移与合规校验引擎落地后登记进 `scripts/_config.ps1` FrozenPaths（`guard-frozen` 钩子拒改），演进走版本评审。

## 经验铁律（必须加载 · Tier 1 · 封顶 10 个驻留 id）
> 自净化经验系统的**必须层**：踩过且会复发的硬坑，每轮都在上下文里，**同样问题不再重导**。
> 全量见 `docs/lessons/LEDGER.md`；按需细则 `docs/lessons/<topic>.md`；流程见 `docs/LESSONS.md`。
> `scripts/lessons.ps1` 完整命令集：`add|archive|bump|check|list|promote|search`；本节超上限须淘汰最不活跃项回按需层。
> **封顶的计量单位是驻留的经验 id、不是本节的条目数**：一条写着 `[L190][L193]` 的 Markdown bullet 包含 2 个驻留 id、
> 占 2 个封顶单位；封顶要管的是驻留 id。判定核 `scripts/_lessons.ps1`，`lessons.ps1 check` 与心跳探针 5 共用。
- **[L1] 并行工具批次**：只读诊断与写操作**分批**；首个命令非零退出会**连带取消整批**、丢失已写文件。
- **[L165] 断言面必须恰好等于被测契约，并用「只删那一句」的变异证明它在测**：宽于契约的断言（整份 stdout ⊃ 判定行 · 本地化文案 ⊃ ASCII 哨兵 · 关键词出现次数 ⊃ 可执行命令行 · 任一非零 ⊃ 该守卫拦下）在契约还在时照样绿，契约被摘掉后又常因别的原因满足 ⇒ 静默失效。故：只比判定行 · 机检认 ASCII 哨兵（本地化文案只给人读，编码链一变即假红/假绿）· 文档契约锚到可执行命令行形态 · 「不符」用例要让被测那句**真被执行到**。**每道守卫配一枚单句删除变异，它红了才算数**。
- **[L17] `.ps1` 一律用 PowerShell 工具，不用 Bash；经 Bash 传给任何解释器的字符串也别含字面反斜杠**：Bash 工具吞的是**任何**字符串里的反斜杠层级（引号 heredoc 也不例外——`scripts\review.ps1` 到达 Python 时 `\r` 已成回车，锚点静默失配），且控制台编码与 pwsh 不同源——后者会让 `selftest.ps1` 等中文断言脚本产出**假 FAIL**，连事后核验也会被误导；异常失败先用 PowerShell 工具重跑再下结论。
- **[L97] 横切纪律行为化前先 grep 出全部权威面、一次性纳入 allow_paths**：改的是「所有面都在教的那条规则」时，教它的面（CLAUDE.md · README/操作手册 · skill · 脚本头注 · 架构图 · 各校验清单枚举）**一次扫齐再开卡**，并连同**改动文件自身的注释与卡 front-matter** 一起对齐——评审每轮只报当轮最刺眼的一处，漏一处就多打一轮。**扫描清单显式含本次 diff 改到的每个文件自身**（注释 + 失败/日志文案 + 总结行）；**失败文案不写死具体病因**（那是必然过时的正面陈述），能从现场数据动态报就动态报。这类卡 allow_paths 天然大，是横切的固有形态、非 scoping 失误；check-cards「>5 告警」对它是误报但不放宽阈值，在卡标题声明式扩尺寸即可。
- **[L196] 后台长批硬杀不执行 finally，会话续接第一步先核被测文件 SHA**：变异批/长批被会话拆除或进程树 kill 杀在「植入后、还原前」时，还原挂在 finally 上不会跑，被测文件跨会话停在变异态，git 只显示 M、肉眼难辨。故：批启动核基线 SHA、不符即中止；**每次会话续接第一步**核被测文件 SHA==上批基线，不符先从 `.bak` 还原再谈 diff/证据（判干净只认 SHA256）；批须落 per-mutation 日志，续跑只补缺失枚、不整批重来。**变异批进行中勿并行跑独立交叉复核**——复核者读到瞬态变异文件会产出自信的假阳性。
- **[L190][L193] 验证必须用被测代码那套机制，且不可见码位只写转义形态**：验证一个断言时必须用**被测代码实际使用的那套机制**去验，不能用「语义上等价」的另一个 API（正则覆盖面就用 `-match` 实测、别查类目 API；编码/落盘行为就真写一遍文件再读回）；断言对象若是一个**类目/属性**，取样证不了它——须由标量级权威 oracle（`Rune.GetUnicodeCategory`）枚举全集，再逐个走**生产那套机制**验证。写文件的工具层会把「反斜杠u四位十六进制」字面静默解码成真字符——组合符/控制符落盘后肉眼与显示层都看不见，故转义形态要**用代码拼出**（`chr(92)+'u'`、`0xD800.toChar()`、`appendCodePoint`），写完立刻字节级验证，并在用它之前断言靶串真能在被测文件里找到。**判别工具与被测实现矛盾时先停手修工具**——此时产出的任何「变异 OK / 覆盖完整」都是无效证据，比没有更坏；靶串找不到=本枚作废、中止，而非「测不出」。
- **[L21][L205] R3 轮次先判证据真伪，修复轮也要本地对抗自检**：无裁决时先按 `review.ps1` 状态码与已保全原文分类：`[R3-NO-VERDICT-JSON]` 仍可能含真实负面 finding，须读原文并按反馈处理；`[R3-NO-OUTPUT]` 只证明没有可解析输出，不能直接推定配额故障。再在同一 worktree 独立 probe reviewer（不经 `review.ps1`、不碰 `.rounds`）并保全完整 stderr；**只有 probe/stderr 证实配额或其它外部失败，才判为评审者未真正运行、非真实评审分歧**。禁止靠反复 `ship` 烧轮次，更不得绕过 R3；若外部失败已误耗尽 cap，只能在确诊后 `ResetRounds`，随后仍须正常过 R3。一轮修 ≥3 条 finding 的修复 diff 常大于首轮实现，且「按条修不看整体」使新代码不再被任何人当新代码审——ship 前先派 fresh-context/换模型子代理只对**本轮修复 diff** 按 rubric 复核（新错误处理是否 fail-open？点名目标是否全覆盖？改动文件自身注释/文案还成立吗）。W2 五卡有四张的 R3 轮次通胀皆此模式（修复自己引入下一轮的缺陷）。
- **[L266] 体量在写 RED 之前就量，别等 ship 才发现顶破预算**：验收契约那一步就用闸门自己的尺估一次——产线 + 测试 + R4 收据合计对着 R3 的 **1000 changed-lines / 60000 字符**硬上限报预算（`review.ps1` fail-closed、只许收紧），**超过约 800 行就在动手前提出拆卡**。量晚了就只剩坏选项：ship 压力下先删注释、再打包字面量、最后开始动测试用例与变异收据。**且收据钉的是生产文件的确切 SHA-256，批后任何生产改动（哪怕纯注释）都作废整批证据**（L270）——所以「为压预算而修剪」必须发生在变异批之前。L246 管「用哪把尺量」，本条管「什么时候量」。

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
- **本项目执行舰队（性价比路由；每卡首选/备选/effort 的真相源 = `docs/TASK-BOARD.md`）**：DeepSeek V4 Pro = 默认工作马（规格清晰/机械卡，最高性价比）；Opus 5 = 设计重/新颖单点（相机、PDF composer、加密格式）；Sonnet 5（max effort）= 标准 Compose 界面/中档逻辑；GPT-5.6 Terra = 中档替补 + 大体量只读消化；GPT-5.6 Luna Max = 轻档内容/交叉复核；**GPT-5.6 Sol = R3 评审席（`_config.ps1` 已钉），原则上不作同卡作者，保评审独立**。执行时把卡内「上下文包」整段喂给执行模型。
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
<sub>脚手架溯源：**MyInspection** 由 devops-scaffold **v0.29.0** 生成（`ScaffoldOriginVersion`）；已裁决到的当前版本为 **v0.45.0**（`ScaffoldVersion`）。</sub>
