export const meta = {
  name: 'plan-forge',
  description: '差异化审计一份计划(若有前次评审则只打其遗漏 + 冻结点风险) -> 多裁判对抗核验 -> 裁决 -> 拆解为带依赖关系的可执行任务卡 -> 卡片审计',
  phases: [
    { title: 'Lens-Audit', detail: '8 个 lens 并行审计；若有前次评审先读它、禁止重报已知项' },
    { title: 'Adversarial-Verify', detail: '每条 FATAL/HIGH 发现派 3 个裁判从不同角度尝试反驳，>=2 反驳即枪毙' },
    { title: 'Synthesize', detail: '汇总存活发现 + 裁决 plan 是否可拆解' },
    { title: 'Decompose', detail: '把计划的任务章节投影为带 depends_on 的完整任务卡集' },
    { title: 'Card-Audit', detail: '审计卡片图: 无环拓扑 / DoD可机检 / 硬边界 / 并行撞文件' },
  ],
}

// ── 路径全部经 args 参数化；换项目只改 args（或编辑下方相对默认值）──
const A = args || {}
const PLAN = A.planPath || '_local/PLAN.md'                  // 计划真相源（落 _local/，gitignored）
const PRIOR = A.priorReviewPath || ''                        // 前次评审（可选；有则避免重报）
const CLAUDEMD = A.claudeMdPath || 'CLAUDE.md'              // 硬边界/不变量/许可硬规则
const SPECS = A.specsReadmePath || 'specs/README.md'        // 任务卡投影约定（薄投影，非第二真相源）
const TEMPLATE = A.templatePath || 'specs/tasks/_TEMPLATE.md'

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

const CARDS_SCHEMA = {
  type: 'object',
  properties: {
    cards: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: { type: 'string' },
          title: { type: 'string' },
          depends_on: { type: 'array', items: { type: 'string' } },
          parallelizable_with: { type: 'array', items: { type: 'string' } },
          allow_paths: { type: 'array', items: { type: 'string' } },
          forbid: { type: 'array', items: { type: 'string' } },
          dod_command: { type: 'string' },
          dod_exit: { type: 'number' },
          dod_assert: { type: 'string' },
          plan_ref: { type: 'string' },
        },
        required: ['id', 'title', 'depends_on', 'dod_command', 'dod_assert'],
      },
    },
    freeze_point: { type: 'string' },
    topo_valid: { type: 'boolean' },
    parallel_window: { type: 'string' },
  },
  required: ['cards', 'freeze_point'],
}

const CARD_AUDIT_SCHEMA = {
  type: 'object',
  properties: {
    graph_ok: { type: 'boolean' },
    cycles: { type: 'array', items: { type: 'string' } },
    issues: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          card: { type: 'string' },
          problem: { type: 'string' },
          severity: { type: 'string' },
        },
        required: ['card', 'problem'],
      },
    },
    dod_not_machine_checkable: { type: 'array', items: { type: 'string' } },
    boundary_violations: { type: 'array', items: { type: 'string' } },
  },
  required: ['graph_ok', 'issues'],
}

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
      '- 右尺寸(一个可评审/可验证单元,尺寸标准工具/模型无关): 每卡是否为单一连贯产出、一条 dod_command 判一件事、可一次评审判完(默认档量化缺省:净改动约≤200-400行、touched≈1-3最多5)? 【过大】卡(含"且/和"多产出、要多条 dod、allow_paths 远超5)——**仅当超默认档且未声明长自主执行(多文件弧+间隔 fresh-context 校验)时才判 HIGH 并建议拆法**;已声明长自主执行的卡超默认档不判过大。【过碎】卡(一函数/一行一张、测试与实现分卡 → 建议合并)也要点出。',
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

log('plan-forge 启动: 8 个 lens 审计计划' + (hasPrior ? '(有前次评审，只打遗漏与冻结点风险)' : ''))

const lensResults = await pipeline(
  LENSES,
  (d) =>
    agent(
      COMMON +
        '\n\n## 你的 lens: ' + d.title + '\n' + d.focus +
        '\n\n返回结构化发现(每条含 id/title/severity/where/claim/why_compounds/fix/novel_vs_prior_review)。id 用 ' +
        d.key + '-1, ' + d.key + '-2 ...',
      { label: 'lens:' + d.key, phase: 'Lens-Audit', schema: FINDINGS_SCHEMA }
    ),
  (review, d) =>
    parallel(
      // TD63 item8：prompt 第 147 行只在文案里声明"最多返回 3 条最重要的 FATAL/HIGH 发现"，但 judge 是否
      // 遵守全凭自觉——扇出到下方 3-裁判对抗核验的量从未被机械限住。.slice(0, 3) 把这条上限落成硬约束。
      (((review && review.findings) || []).filter((f) => f.severity === 'FATAL' || f.severity === 'HIGH')).slice(0, 3).map(
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
)

const confirmed = lensResults.flatMap((r) => r.verified.filter((f) => !f.refuted))
const refutedList = lensResults.flatMap((r) => r.verified.filter((f) => f.refuted))
const medium = lensResults.flatMap((r) => (r.allFindings || []).filter((f) => f.severity === 'MEDIUM'))
log('对抗核验完成: 确认 ' + confirmed.length + ' 条(FATAL/HIGH 存活), 枪毙 ' + refutedList.length + ' 条, 另有 ' + medium.length + ' 条 MEDIUM 待人评')

const synth = await agent(
  '你是汇总裁判。下面是经过多裁判对抗核验后【存活】的 FATAL/HIGH 发现，以及未核验的 MEDIUM 项。\n' +
    '存活发现:\n' + JSON.stringify(confirmed, null, 1) + '\n\nMEDIUM:\n' + JSON.stringify(medium, null, 1) + '\n\n' +
    '动手前先 Read 计划 ' + PLAN + ' 核对每条。然后: 去重合并, 按"前期错后面白干"的杀伤力排序, 给出每条 correction(where/problem/fix/severity)。\n' +
    '裁决 verdict: 仅当【无 FATAL 且所有 HIGH 都能在开拆前修掉】才给 ready-to-decompose; 否则 fix-first。给出 fatal_count/high_count 与 rationale。',
  { phase: 'Synthesize', schema: SYNTH_SCHEMA }
)
if (!synth) {
  log('裁决被跳过(汇总 agent 返回 null)——保留已核验发现，不拆解，不虚构裁决')
  return {
    verdict: 'synthesis-skipped',
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

const decomp = await agent(
  '把计划的任务章节投影为【完整的带依赖关系任务卡集】(这是 specs/tasks 的薄投影，不是第二份计划)。\n' +
    '动手前先 Read: 计划 ' + PLAN + '(尤其任务拆分与验收两节)、卡片模板 ' + TEMPLATE + '、投影约定 ' + SPECS + '。\n' +
    '已确认的 plan 问题(拆解时要规避或吸收其修法):\n' + JSON.stringify((synth.corrections || []), null, 1) + '\n\n' +
    '为计划任务章节列出的每张卡产出字段: id/title/depends_on/parallelizable_with/allow_paths/forbid/dod_command/dod_exit/dod_assert/plan_ref。\n' +
    '硬要求: depends_on 必须构成无环拓扑; 冻结点(契约/schema 那张卡)必须在所有依赖它的卡之前; 标出真正可并行的窗口(parallel_window)。dod_command 必须是目标 shell 下可跑且二值可判的真实命令, import 路径要和目录结构一致。topo_valid 自评。',
  { phase: 'Decompose', schema: CARDS_SCHEMA }
)
if (!decomp) {
  log('拆解被跳过(拆解 agent 返回 null)——裁决结论已保留，不再审计卡片图')
  return {
    verdict: synth.verdict,
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
}
log('拆出 ' + ((decomp.cards && decomp.cards.length) || 0) + ' 张卡, 冻结点 ' + decomp.freeze_point)

const cardAudit = await agent(
  '审计下面这套任务卡依赖图(它应是计划任务章节的薄投影)。\n' + JSON.stringify(decomp, null, 1) + '\n\n' +
    '动手前先 Read ' + CLAUDEMD + ' 的硬边界/关键不变量 与 ' + SPECS + ' 的投影约定。\n' +
    '逐项检查: (1) depends_on 是否无环、拓扑成立(列出任何 cycle); (2) 每张卡 dod_command 是否真的机器可判而非"应该能"(列出不达标的 card id 到 dod_not_machine_checkable); ' +
    '(3) 是否有卡违反硬边界(列到 boundary_violations); (4) 并行窗口里的卡是否会写同一文件而冲突(列到 issues)。给出 graph_ok 总评。',
  { phase: 'Card-Audit', schema: CARD_AUDIT_SCHEMA }
)

return {
  verdict: synth.verdict,
  confirmed_count: confirmed.length,
  refuted_count: refutedList.length,
  medium_count: medium.length,
  synth: synth,
  confirmed: confirmed,
  refuted: refutedList.map((f) => ({ id: f.id, lens: f.lens, title: f.title, severity: f.severity })),
  medium: medium,
  decomp: decomp,
  cardAudit: cardAudit,
}
