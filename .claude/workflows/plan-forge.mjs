export const meta = {
  name: 'plan-forge',
  description: '按 tier 路由深度审计一份计划(若有前次评审则只打其遗漏 + 冻结点风险) -> T2 多裁判对抗核验 -> 裁决即止；投影任务卡归 decompose-cards.mjs 单一所有',
  phases: [
    { title: 'Lens-Audit', detail: 'lens 并行审计：T2 走全 8 个 lens、T1 走 3 个(tier 路由)；若有前次评审先读它、禁止重报已知项' },
    { title: 'Adversarial-Verify', detail: '仅 T2：每条 FATAL/HIGH 发现派 3 个裁判从不同角度尝试反驳，>=2 反驳即枪毙' },
    { title: 'Synthesize', detail: '汇总发现 + 裁决 plan 是否可拆解。裁决就是终点——不投影任务卡' },
  ],
}

// ── 路径全部经 args 参数化；换项目只改 args（或编辑下方相对默认值）──
const A = args || {}
// T152：漏斗产物目录的真相源是 scripts/_config.ps1 的 PlanDir（accessor: Get-ScaffoldPlanDir，留空 => '_local'）。
// 工作流脚本**没有文件系统访问**（Workflow 运行时契约），读不了 _config，故耦合走 args：调用方传
//   planPath = "$(Get-ScaffoldPlanDir)/PLAN.md"。下面的字面量只是 PlanDir 留空时的**同值默认**，
//   不是第二真相源——改了 PlanDir 而不传 args，拿到的就还是旧位置。
const PLAN = A.planPath || '_local/PLAN.md'                  // 计划真相源（PlanDir 留空时的同值默认）
const PRIOR = A.priorReviewPath || ''                        // 前次评审（可选；有则避免重报）
const CLAUDEMD = A.claudeMdPath || 'CLAUDE.md'              // 硬边界/不变量/许可硬规则
const SPECS = A.specsReadmePath || 'specs/README.md'        // 任务卡投影约定（薄投影，非第二真相源）

// TD180：审计**深度**由调用方声明的 tier 路由——此前每份计划都按最深档收费，把一份改文案的计划
// 收得和一次 schema 迁移一样贵（最坏约 83 次模型调用）。三个刻意选择，都是量出来的：
//   * tier 走 `args`，和 planPath 一样：工作流脚本没有文件系统访问权，读不了 _config.ps1。
//   * 调用方传**字面量**，绝不写 `"$(Get-ScaffoldProjectTier)"`。该 accessor 在键缺失**和值为空**时
//     都回退 'T1'，而本仓 _config 就是 `ProjectTier = 'T1'`——内插它等于给这里每份计划**静默**选了
//     最浅档。且 `ProjectTier` 回答的是另一个问题（「跳过哪些交付链」，它的 T1 行恰恰**要跑**本漏斗），
//     深度是**逐计划**的判断，故它只是本参数的**起点建议**，不做自动输入。
//   * **缺省 = 未声明，不等于 T1**：回退到最深档，这样升级脚手架永远不会让没改调用点的人
//     悄悄拿到一份更浅的审计。
const TIER = (A.tier === 'T0' || A.tier === 'T1' || A.tier === 'T2') ? A.tier : 'T2'
const VERIFY = TIER === 'T2'
// 返回里带 verify_mode，是因为 `confirmed` 这个键在两档下含义不同：T2 = 熬过了 >=2/3 反驳，
// T1 = 根本没人投过票。同一个键两种意思正是本仓的闸门存在的理由，所以把它说出来而不是让读者猜。
const VERIFY_MODE = VERIFY ? 'adversarial' : 'synthesis-only'
// T1 保留三个专打「现在错、后面全白干」那一类的 lens：冻结点爆炸半径 / 切法对不对 / 验收可否机检。
// 另外五个是 T2 专属；其中 `boundary` 的失效**另有确定性兜底**（check-licenses / check-secrets /
// guard-frozen / 范围闸，每张卡都跑、与 tier 无关），故概率性 lens 不是它唯一防线。
const T1_LENS_KEYS = ['future-self', 'decomposition', 'dod']

// TD180：`templatePath` 随 Decompose 一起删了——卡片模板只有投影时才用得上，而这里不再投影。
// 它现在是 `decompose-cards.mjs` 的参数（那边一直就有），不再是本工作流的参数。

const hasPrior = !!PRIOR

const FINDINGS_SCHEMA = {
  type: 'object',
  properties: {
    lens: { type: 'string' },
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: { type: 'string' },
          title: { type: 'string' },
          severity: { type: 'string', enum: ['FATAL', 'HIGH', 'MEDIUM'] },
          where: { type: 'string' },
          claim: { type: 'string' },
          why_compounds: { type: 'string' },
          fix: { type: 'string' },
          novel_vs_prior_review: { type: 'boolean' },
        },
        required: ['id', 'title', 'severity', 'where', 'claim', 'fix'],
      },
    },
  },
  required: ['lens', 'findings'],
}

const VERDICT_SCHEMA = {
  type: 'object',
  properties: {
    finding_id: { type: 'string' },
    refuted: { type: 'boolean' },
    confidence: { type: 'string', enum: ['high', 'med', 'low'] },
    reasoning: { type: 'string' },
  },
  required: ['refuted', 'reasoning'],
}

const SYNTH_SCHEMA = {
  type: 'object',
  properties: {
    verdict: { type: 'string', enum: ['ready-to-decompose', 'fix-first'] },
    fatal_count: { type: 'number' },
    high_count: { type: 'number' },
    corrections: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          where: { type: 'string' },
          problem: { type: 'string' },
          fix: { type: 'string' },
          severity: { type: 'string', enum: ['FATAL', 'HIGH', 'MEDIUM'] },
        },
        required: ['where', 'problem', 'fix', 'severity'],
      },
    },
    rationale: { type: 'string' },
  },
  required: ['verdict', 'corrections', 'rationale'],
}

// TD180：这里**没有** CARDS_SCHEMA / CARD_AUDIT_SCHEMA，也没有 Decompose / Card-Audit 两个 agent——
// 这是刻意的删除，不是遗漏。投影任务卡归 `decompose-cards.mjs` 单一所有：漏斗在人**批准修正后的计划**
// 之后才跑它，而此处一旦也投影，那套卡在任何一条 correction 落地的瞬间就**按构造过期**了——两个生成器、
// 两套卡、其中一套生来就是错的，且返回结构里没有任何字段说得出这件事。别把它们加回来。
// （`decompose-cards.mjs` 本就是更强的那个投影器：它还带 non_goals / acceptance / hygiene / doc_sync /
//   freeze_point / topo_valid / parallel_window 与 4 角度对抗卡审，这里的 Decompose 从来没有。）
// selftest 子闸 1i **驱动**本文件在每个 tier 上跑，断言没有任何一条路径请求这两个 phase。

const COMMON =
  '你在审计一份项目【计划/计划书】(真相源)。动手前先 Read 这些文件:\n' +
  '- 计划全文: ' + PLAN + '\n' +
  (hasPrior ? '- 前次评审结论(你必须主动避免重复它已发现的问题): ' + PRIOR + '\n' : '') +
  '- 冻结不变量/硬边界/许可硬规则: ' + CLAUDEMD + '\n' +
  '- 任务卡投影约定(specs 是计划任务章节的薄投影，不是第二真相源): ' + SPECS + '\n\n' +
  '铁律:\n' +
  (hasPrior
    ? '1. 不要重报前次评审已发现的问题 —— 那是浪费。只找它【遗漏的】或【随契约/schema 即将冻结才变得 load-bearing 的】问题。每条都自评 novel_vs_prior_review。\n'
    : '1. 优先找【会随契约/schema 冻结而变得 load-bearing】、以及【前期错则后面白干】的问题。每条自评 novel_vs_prior_review(无前次评审时填 true)。\n') +
  '2. 按严重度分级: FATAL = 前期错则后面白干(冻结契约/schema 设计缺陷、拓扑/依赖错误、会逼迫返工的根本假设错误); HIGH = 开工早期必须修否则放大; MEDIUM = 应改但不阻塞。\n' +
  '3. 宁缺毋滥: 只报你能具体定位(§N 或字段名/文件行)且能给出可执行修法的问题。不报文风/措辞类琐碎项。\n' +
  '4. 最多返回 3 条最重要的 FATAL/HIGH 发现; MEDIUM 可另外列(不参与对抗核验)。\n' +
  '5. 计划的真相源地位不可动摇; 你的产出是【对它的审计意见】，不是改写它。'

// 8 个 lens（项目无关·概念普适；focus 文本不绑定任何具体项目领域）
const LENSES = [
  {
    key: 'future-self',
    title: '未来自我(冻结点压测)',
    focus:
      '角色扮演: 你是后续阶段负责把【真实实现/真实模型/真实集成】适配到本计划即将冻结的契约/schema 的工程师。\n' +
      '逐字段追问: 真实接入时哪个字段缺失/语义错/不够用? 计划承诺的异步/接口/状态机能否覆盖真实实现的全部形态(本地/远端/排队/失败重试)?\n' +
      '契约是否在不经意间被某个具体实现的形状绑死，以致将来替换实现时要重写编排? "只改注册表/适配层就能切换实现"的承诺在真实接入下是否真的成立? 时间轴/顺序/对齐假设在真实条件下是否站得住?\n' +
      '这是最高价值 lens: 冻结后改契约 = 所有下游卡返工。',
  },
  {
    key: 'consistency',
    title: '内部矛盾猎手(含前后端接口对齐)',
    focus:
      '逐条交叉核对全文所有【具体决策/数字/字段名/版本号/路径】的一致性: 版本漂移(语言/依赖版本前后不一)、字段名跨章节对不齐、谁引用谁、版本号是否处处一致。逐处定位行号。\n' +
      '- 四处模型同构: 契约 <-> 持久层 <-> 对外 schema <-> **前端**(组件 props / API 调用字段 / 类型)是否同构? 典型漂移: 前端 userId vs 后端 user_id、端点路径/方法对不上、枚举值不一致。\n' +
      '- 前端接口是否声明【从后端契约生成类型】(openapi-typescript / 共享 types)而非前端手写后端字段? 手写后端字段=漂移源,应判 HIGH。\n' +
      '- 前端命名是否合 CLAUDE.md「代码与接口命名」(组件 PascalCase / hook useXxx / 文件名 / CSS kebab) 且计划承诺 eslint+tsc 进 DoD 机检?',
  },
  {
    key: 'decomposition',
    title: '拆解正确性',
    focus:
      '审任务拆分图是否是【正确的切法】(这是"想法->带依赖关系的卡"的核心质检)。\n' +
      '- 隐藏依赖: 各卡 depends_on 是否齐全(常漏: 编排卡漏依赖 core/storage/db; 实跑卡漏依赖样例资产)?\n' +
      '- 冻结点位置对不对? 声称可并行的卡是否共享同一批文件、会不会并行写冲突(各自 worktree 也要合并)?\n' +
      '- 缺卡/多卡: 卡集是否覆盖验收闸门跑通所需的一切? 关键资产(样例/schema 校验器/合规占位/护栏)由哪张卡产出?\n' +
      '- 右尺寸(一个可评审/可验证单元,尺寸标准工具/模型无关): 每卡是否为单一连贯产出、一条 dod_command 判一件事、可一次评审判完(默认档量化缺省:净改动约≤200-400行、touched≈1-3最多5)? 【过大】卡(含"且/和"多产出、要多条 dod、allow_paths 远超5)——**仅当超默认档且未声明长自主执行(多文件弧+间隔 fresh-context 校验)时才判 HIGH 并建议拆法**;已声明长自主执行的卡超默认档不判过大。【过碎】卡(一函数/一行一张、测试与实现分卡 → 建议合并)也要点出。\n' +
      '- 【预算已声明】(T233/TD235) 每张卡是否写了一行 `budget:` <净改动行数>? 这是上面那两档的**机检形式**:写作期 check-budget.ps1 拿它量 diff、0.6 跳表提示决策,合并期 ship 超出即 [CARD-BUDGET-OVER] 阻断,预算取自 base 卡故分支内抬高无效。缺了这一行,机制就永远是"靠遗漏来选择退出"——计划里每张卡都该带上它,声明长自主档 = 写一个更大的整数并在提交里写明理由。**缺失判 MEDIUM 并给出建议值**(按该卡的产出规模,默认档写 200-400);已声明但与卡的实际范围明显不符的,点出该值与拆法。',
  },
  {
    key: 'boundary',
    title: '硬边界/不变量',
    focus:
      '系统核对计划是否任何地方暗中违反 CLAUDE.md 声明的硬边界与不变量(如确定性/离线/无 GPU/依赖许可 GPL-AGPL-SSPL-非商用禁用/原创实现/机密不入库 等——以本项目实际声明为准)。\n' +
      '- 是否有步骤隐式引入运行期出站网络、或依赖某个可被关闭的环境标志当唯一防线?\n' +
      '- 禁网/脱敏/白名单等护栏的实现路径是否真的可执行、有没有洞? .gitignore 对机密的覆盖面是否完整?\n' +
      '- 后续阶段的许可雷(copyleft/非商用权重数据)是否已被本阶段架构提前规避，还是埋在契约假设里、到后期才爆?',
  },
  {
    key: 'dod',
    title: 'DoD 可机检性',
    focus:
      '审各卡 DoD 命令在目标 shell(本模板默认 Windows/PowerShell)下是否【真能跑且二值可判】。\n' +
      '- import 路径/包根/PYTHONPATH 是否与目录结构一致(这类错会让 DoD 永远跑不绿，是隐蔽 FATAL)?\n' +
      '- 探测/断言命令是否完整可执行、可机器判定(避免依赖人眼读输出)? 验收闭环是否自洽、每步都有机器可判断言?\n' +
      '- DoD 用到的依赖/工具是否已在该卡 allow_paths/依赖清单内(否则 DoD 与 allow_paths 自相矛盾)?',
  },
  {
    key: 'scope',
    title: 'MVP 高度(过度/不足设计 · KANO)',
    focus:
      '以"资深工程师会不会嫌过度设计"为尺,并带 KANO 视角。\n' +
      '- 过度: 本版里哪些是镀金(可砍/可推迟)? 砍掉清单是否砍够或砍错(两个等价机制是否二选一即可)? 有没有 delighter/should 偷偷漏进了 must?\n' +
      '- KANO/MoSCoW: 列入本版的功能哪些是真 must-have(没它产品就废)? 能否回答"砍掉 50% 留什么"?\n' +
      '- 不足: 最小闭环真的能产出【可验收的最终产物 + 合法元数据】吗? 有没有为了"显得完整"而漏掉闭环真正必需的一环?',
  },
  {
    key: 'module-design',
    title: '模块化/扁平化/去中心化(做乘法) + 右尺寸',
    focus:
      '审计计划的目录结构/契约/数据模型是否满足三原则——但先按 MVP 体量【右尺寸】,别让 AI 过度工程(这是 AI 最易犯的设计缺陷)。\n' +
      '- 模块化(高内聚低耦合): 每模块单一职责吗? 模块间靠显式接口/事件而非直读对方数据或共享可变状态? 依赖有无环(ADP)? 有无"上帝类/大泥球"? 总在一起改的两块其实是一块。\n' +
      '- 扁平化: 有没有不必要的中间层/编排层/adapter 噪音(删了它下游要重复逻辑吗——不要就删)? 同步调用链是否过深?\n' +
      '- 去中心化: 有无单点故障(SPOF)、关键编排是否集中一处? (DDD)各 bounded context 是否各自拥有数据与语言?\n' +
      '- 【右尺寸/反过度·最重要】: 小 MVP 默认【模块化单体】优先;微服务/分布式队列/cell/事件编排 仅当真有不同伸缩或可靠性需求才上。过早拆分/抽象本身就是 FATAL 级缺陷,按 ponytail 砍——团队/域未稳时,边界划错比不划更贵。',
  },
  {
    key: 'data-model',
    title: '数据模型/Schema 设计(关系型 · 仅当本版含数据库)',
    focus:
      '审计计划 §5 数据模型 / §6 契约 的【关系型 schema 设计质量】。本版无数据库则填「本版无」跳过。每条须能定位到表/字段/§N:\n' +
      '- 主键策略: 业务表是否用稳定代理主键(规模化/分布式默认 Snowflake BIGINT / UUIDv7 / ULID;小型单库才 AUTO_INCREMENT)? 是否误用 email/手机/SKU/单号等可变业务标识做主键?\n' +
      '- 审计/生命周期: 业务表是否有 created_at/by、updated_at/by、软删除 deleted? 【软删除表的唯一索引是否包含 deleted】——漏了删后无法重建同值=静默数据损坏(判 FATAL/HIGH)。\n' +
      '- 状态字段: 每个 status/state 是否定义全部取值 + 合法流转 + 终态? 模糊状态字段判缺陷。\n' +
      '- 关系/外键: 基数(1:1/1:N/N:M)与所有权 vs 引用是否明确? 默认逻辑外键(存引用字段+建索引+应用层保完整性),跨库/跨服务/跨限界上下文禁物理外键? N:M 是否用关联表?\n' +
      '- 业务逻辑位置: 是否把业务逻辑塞进触发器/存储过程/DB 事件(隐藏副作用,反模式)? 业务逻辑应在 service/领域层。\n' +
      '- 索引来自查询场景: 索引是否由读路径推导而非按字段名乱建? 组合索引顺序 等值>范围>排序? 多租户表 tenant_id 是否最左?\n' +
      '- 数据类型: 金额是否 DECIMAL(禁 FLOAT/DOUBLE)? 时间是否 UTC? MySQL 是否 utf8mb4(非 utf8)? 核心可查询字段是否被藏进 JSON?\n' +
      '- 反模式: EAV 三元组 / 逗号分隔值列 / 100+ 字段宽表 / ENUM 滥用 / 自引用递归层级 是否出现?\n' +
      '- 契约冻结: §6 是否指定 schema/迁移文件冻结点并登记 _config.ps1 FrozenPaths(否则契约漂移无机械防线)?\n' +
      'FATAL(前期错后面白干): 主键策略错、缺审计/软删除致返工、状态机缺失、业务逻辑塞进 DB、schema 契约未冻结。详见 docs/lessons/database.md。具体默认(MySQL8/Snowflake/utf8mb4)按项目栈调,工具无关地审「标准是否被满足」。',
  },
]

if (A.tier && A.tier !== TIER) {
  log('tier "' + A.tier + '" 不是 T0/T1/T2，已回退到最深档 ' + TIER + '（未声明 != T1：宁可多审，不可静默变浅）。')
}

// T0：档位表（docs/IDEA-TO-PLAN.md）自己的规则就是「跳过整个想法→计划漏斗，直接写卡开干」。
// 把那条规则做成机器可读的一条分支，代价为零；真要审就显式传 tier: "T1" / "T2"。
if (TIER === 'T0') {
  log('tier T0: 按档位表跳过整个漏斗，本次不做任何审计（0 个 agent）。要审就显式传 tier: "T1" 或 "T2"。')
  return {
    verdict: 'tier-skipped',
    tier: TIER,
    verify_mode: 'none',
    confirmed_count: 0,
    refuted_count: 0,
    medium_count: 0,
    synth: null,
    confirmed: [],
    refuted: [],
    medium: [],
    decomp: null,
    cardAudit: null,
  }
}

const ACTIVE_LENSES = TIER === 'T1' ? LENSES.filter((d) => T1_LENS_KEYS.indexOf(d.key) >= 0) : LENSES
log('plan-forge 启动 (tier ' + TIER + '): ' + ACTIVE_LENSES.length + ' 个 lens 审计计划、' +
  (VERIFY ? '多裁判对抗核验' : '无对抗轮，汇总裁判即唯一裁判') +
  (hasPrior ? '(有前次评审，只打遗漏与冻结点风险)' : ''))

const lensResults = await pipeline(
  ACTIVE_LENSES,
  (d) =>
    agent(
      COMMON +
        '\n\n## 你的 lens: ' + d.title + '\n' + d.focus +
        '\n\n返回结构化发现(每条含 id/title/severity/where/claim/why_compounds/fix/novel_vs_prior_review)。id 用 ' +
        d.key + '-1, ' + d.key + '-2 ...',
      { label: 'lens:' + d.key, phase: 'Lens-Audit', schema: FINDINGS_SCHEMA }
    ),
  (review, d) => {
    // TD63 item8：prompt 只在文案里声明"最多返回 3 条最重要的 FATAL/HIGH 发现"，但 judge 是否遵守全凭
    // 自觉——扇出到对抗核验的量从未被机械限住。.slice(0, 3) 把这条上限落成硬约束，两档共用。
    const top = (((review && review.findings) || []).filter((f) => f.severity === 'FATAL' || f.severity === 'HIGH')).slice(0, 3)
    // T1（TD180）没有对抗轮：3 lens + 1 汇总 = 4 个 agent。哪怕每条发现只派 1 个裁判也是 3 + 3x3 + 1 = 13，
    // 越过 TD180 定的 10 上限。发现照样带下去，只是 votes 为空——空 votes 意思是「没人投过票」而不是
    // 「没人反对」，靠返回里的 verify_mode 把这两件事分开。下游 confirmed/refuted/medium 与汇总调用两档同路。
    if (!VERIFY) {
      return Promise.resolve({
        lens: d.key,
        title: d.title,
        verified: top.map((f) => Object.assign({}, f, { lens: d.key, refuted: false, votes: [] })),
        allFindings: (review && review.findings) || [],
      })
    }
    return parallel(
      top.map(
        (f) => () =>
          parallel(
            ['契约/工程正确性', '可复现性: 这问题在本项目里真的会发生吗', (hasPrior ? '是否与前次评审重复(若重复则应废弃此发现)' : '证据是否充分、定位是否精确')].map(
              (angle) => () =>
                agent(
                  '针对计划(' + PLAN + ')的一条审计发现，从【' + angle + '】角度尝试【反驳】它。默认怀疑: 证据不足、定位不准' +
                    (hasPrior ? '、或前次评审已覆盖' : '') + '，就判 refuted=true。\n' +
                    '发现: ' + JSON.stringify(f) + '\n' +
                    '动手前先 Read 计划相关 §N' + (hasPrior ? ' 与前次评审 ' + PRIOR : '') + ' 再下判断。',
                  { label: 'verify:' + (f.id || 'x'), phase: 'Adversarial-Verify', schema: VERDICT_SCHEMA }
                )
            )
          ).then((votes) => {
            const v = votes.filter(Boolean)
            const refuted = v.filter((x) => x.refuted).length >= 2
            return Object.assign({}, f, { lens: d.key, refuted: refuted, votes: v })
          })
      )
    ).then((verified) => ({
      lens: d.key,
      title: d.title,
      verified: verified.filter(Boolean),
      allFindings: (review && review.findings) || [],
    }))
  }
)

const confirmed = lensResults.flatMap((r) => r.verified.filter((f) => !f.refuted))
const refutedList = lensResults.flatMap((r) => r.verified.filter((f) => f.refuted))
const medium = lensResults.flatMap((r) => (r.allFindings || []).filter((f) => f.severity === 'MEDIUM'))
log((VERIFY ? '对抗核验完成: 确认 ' : 'lens 审计完成(T1 无对抗轮): 带出 ') + confirmed.length + ' 条 FATAL/HIGH, 枪毙 ' + refutedList.length + ' 条, 另有 ' + medium.length + ' 条 MEDIUM 待人评')

const synth = await agent(
  (VERIFY
    ? '你是汇总裁判。下面是经过多裁判对抗核验后【存活】的 FATAL/HIGH 发现（每条已被 3 个裁判从不同角度尝试反驳、未达 2 票即保留），以及未核验的 MEDIUM 项。\n'
    : '你是汇总裁判，**也是本轮唯一的裁判**（tier ' + TIER + ' 不跑对抗轮）。下面的 FATAL/HIGH 发现【没有经过任何反驳核验】，votes 为空表示没人投过票，不表示没人反对——你必须逐条自己对着计划核实，核不实的直接丢弃。另附未核验的 MEDIUM 项。\n') +
    (VERIFY ? '存活发现:\n' : '未核验发现:\n') + JSON.stringify(confirmed, null, 1) + '\n\nMEDIUM:\n' + JSON.stringify(medium, null, 1) + '\n\n' +
    '动手前先 Read 计划 ' + PLAN + ' 核对每条。然后: 去重合并, 按"前期错后面白干"的杀伤力排序, 给出每条 correction(where/problem/fix/severity)。\n' +
    '裁决 verdict: 仅当【无 FATAL 且所有 HIGH 都能在开拆前修掉】才给 ready-to-decompose; 否则 fix-first。给出 fatal_count/high_count 与 rationale。',
  { phase: 'Synthesize', schema: SYNTH_SCHEMA }
)
if (!synth) {
  log('裁决被跳过(汇总 agent 返回 null)——保留已核验发现，不虚构裁决')
  return {
    verdict: 'synthesis-skipped',
    tier: TIER,
    verify_mode: VERIFY_MODE,
    confirmed_count: confirmed.length,
    refuted_count: refutedList.length,
    medium_count: medium.length,
    synth: null,
    confirmed: confirmed,
    refuted: refutedList.map((f) => ({ id: f.id, lens: f.lens, title: f.title, severity: f.severity })),
    medium: medium,
    decomp: null,
    cardAudit: null,
  }
}
log('裁决: ' + synth.verdict + ' | FATAL ' + (synth.fatal_count || 0) + ' / HIGH ' + (synth.high_count || 0))

// TD179 起：裁决是一个【分支】，不只是一个报告字段——fix-first 的计划要先拿回去修，然后才谈投影。
// TD180 把它推到底：**任何 tier、任何裁决都在这里停**。投影归 `decompose-cards.mjs` 单一所有，漏斗在
// 人批准修正后的计划之后才跑它；此处若也投影，那套卡在任何一条 correction 落地的瞬间就按构造过期。
// 两条路径交回的是**同一个 shape**（上面两个 null 守卫返回也是它），所以调用方不会学到第二种形状。
// 由 selftest 子闸 1i 机检：它**驱动**本文件在 T0/T1/T2 与两种裁决下跑，断言无一路径请求 Decompose /
// Card-Audit——「顺序」和「计数」都是文本匹配看不见的东西，只有真跑才判得了。
log(
  synth.verdict === 'fix-first'
    ? 'fix-first: 停在裁决。按 corrections 修计划后重跑；修到 ready 再由 decompose-cards.mjs 投影任务卡。'
    : 'ready-to-decompose: 停在裁决。人批准计划后跑 decompose-cards.mjs 投影任务卡（它是投影的唯一所有者）。'
)

return {
  verdict: synth.verdict,
  tier: TIER,
  verify_mode: VERIFY_MODE,
  confirmed_count: confirmed.length,
  refuted_count: refutedList.length,
  medium_count: medium.length,
  synth: synth,
  confirmed: confirmed,
  refuted: refutedList.map((f) => ({ id: f.id, lens: f.lens, title: f.title, severity: f.severity })),
  medium: medium,
  decomp: null,
  cardAudit: null,
}
