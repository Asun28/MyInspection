export const meta = {
  name: 'decompose-cards',
  description: '把(修正后的)计划任务章节投影为带依赖关系的可执行任务卡 + 4 角度对抗式卡片审计',
  phases: [
    { title: 'Decompose', detail: '读计划(+可选审计报告) → 产任务卡结构化定义' },
    { title: 'Card-Audit', detail: '4 并行 lens 对抗审卡: 拓扑/DoD可机检/硬边界许可/allow_paths覆盖' },
  ],
}

// ── 路径全部经 args 参数化；换项目只改 args（或编辑下方相对默认值）──
const A = args || {}
const PLAN = A.planPath || '_local/PLAN.md'
const REPORT = A.reportPath || ''                            // plan-forge 的审计报告(可选)
const TEMPLATE = A.templatePath || 'specs/tasks/_TEMPLATE.md'
const SPECS = A.specsReadmePath || 'specs/README.md'
const CLAUDEMD = A.claudeMdPath || 'CLAUDE.md'
// 额外的项目特定「已确认决定」(plan-forge 修正项的浓缩)，逐条吸收进卡，避免把旧坑写进卡。
// 形如 ['ffmpeg 仅出现在 X/Y 卡且标 LGPL', '契约冻结面只含 schemas/manifest* 而非整 schemas/'] —— 按你项目填。
const DECISIONS = A.decisions || []

const hasReport = !!REPORT

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
          non_goals: { type: 'array', items: { type: 'string' } },
          dod_command: { type: 'string' },
          dod_exit: { type: 'number' },
          dod_assert: { type: 'string' },
          plan_ref: { type: 'string' },
          hygiene: { type: 'string' },
          doc_sync: { type: 'string' },
          notes: { type: 'string' },
        },
        required: ['id', 'title', 'depends_on', 'allow_paths', 'dod_command', 'dod_assert'],
      },
    },
    freeze_point: { type: 'string' },
    topo_valid: { type: 'boolean' },
    parallel_window: { type: 'string' },
  },
  required: ['cards', 'freeze_point', 'topo_valid'],
}

const CARD_AUDIT_SCHEMA = {
  type: 'object',
  properties: {
    lens: { type: 'string' },
    graph_ok: { type: 'boolean' },
    issues: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          card: { type: 'string' },
          problem: { type: 'string' },
          severity: { type: 'string', enum: ['FATAL', 'HIGH', 'MEDIUM'] },
          fix: { type: 'string' },
        },
        required: ['card', 'problem', 'severity', 'fix'],
      },
    },
  },
  required: ['lens', 'issues'],
}

const DECISIONS_BLOCK = DECISIONS.length
  ? '计划已修正，卡必须吸收以下【已确认决定】，否则就是把旧坑写进卡:\n' +
    DECISIONS.map((d, i) => (i + 1) + '. ' + d).join('\n') + '\n'
  : ''

const CONSTRAINTS =
  '动手前先 Read: 计划 ' + PLAN + '(尤其任务拆分与验收两节)' +
  (hasReport ? '、审计报告 ' + REPORT + '(吸收其修正项)' : '') +
  '、卡片模板 ' + TEMPLATE + '、投影约定 ' + SPECS + '、硬边界 ' + CLAUDEMD + '。\n' +
  DECISIONS_BLOCK +
  '通用硬要求(项目无关):\n' +
  '- 【右尺寸·一个可评审/可验证单元】卡的大小标准【工具/模型无关】(本仓尺寸标准的**定义处**,他处引用此定义、勿另立):每张卡必须是**一个可评审、可验证的单元**——单一连贯产出、一条 dod_command 二值验收、能被一个评审者(人或模型)一次评审判完对错、独立开工。判据:\n' +
  '    · 执行者预算档位(判"够不够小"的量化缺省,按项目/执行者调,不绑定任何具体模型名):\n' +
  '        - 默认档(保守):单卡净改动约 ≤ 200-400 行有效代码、touched 文件 ≈ 1-3(最多 5)、引入概念≤1 个;一个执行者一次不靠"续写记忆"就能装下"读相关文件+写实现+写测试"且仍有余量给评审/重构。这是缺省预算,预估会超 → 默认必拆;卡越小,红绿越稳、评审越准、返工越少——按"稳"而非"塞得下极限"来定。\n' +
  '        - 长自主档(受支持模式):跨多文件/多子系统的弧,由长自主执行者一次推进,须**显式声明**并配**间隔 fresh-context 校验**(每推进一段用独立、新上下文的 verifier 子代理对规格核对——见 docs/references)。声明了长自主执行 + 有间隔校验的卡,超默认档不判过大。\n' +
  '    · 单一产出:一张卡只交付一个连贯能力(一个模块/接口/端点/页面),一句话讲得清"产出物是什么"。讲不清或要用"且/和"连接多件事 → 拆。\n' +
  '    · 验收单一:dod_command 是【一条】可机检命令、判一件事。要好几条命令各判一块 → 拆成对应几张卡。\n' +
  '    · 评审可判:一个评审者(人或模型)能在一次评审里判完对错——diff 大到看不动,对人对模型都是失败。经验阈值:allow_paths 通常 ≤ 3-5 个文件/目录;明显超出 → 拆。\n' +
  '    · 自足性(卡可独立开工):卡内 plan_ref/notes 要携带足够上下文,使卡能被**独立拉起**——执行者无需"记得上一张卡"就能从这张卡开工;跨卡共享的契约/类型走冻结点卡显式依赖,不靠隐式上下文。\n' +
  '    · 不可再拆的下限:拆到"再拆就没有可独立验收的产出"为止。**别过度拆**(见反面);setup/配置/脚手架步骤折叠进它服务的那张卡,不单列成卡。\n' +
  '    · 反面(过碎):一个函数/一行配置一张卡、或测试与实现分两张卡,都是过度拆分;同一单元的红绿应在同一张卡内完成。3000 张碎卡和 1 张巨卡一样坏。\n' +
  '    · 冻结点例外:契约/schema 卡可略大(它是一等资产),但也只冻一等资产文件、不塞实现。\n' +
  '  拆分后必须仍构成无环 DAG、各卡 allow_paths 不重叠(并行窗口)。\n' +
  '- 卡 id 必须匹配正则 ^T\\d+-[A-Z0-9]+(-[A-Z0-9]+)*$ (T<阶段号>-<大写短横名>, 如 T0-SCAFFOLD/T2-API/T3-REVIEW-GATE); 禁小写/下划线/空白; id 即文件名即 branch 即 worktree 末段。check-cards.ps1 机检此正则。\n' +
  '- depends_on 无环; 冻结点(契约/schema 那张卡)在所有依赖它的卡之前且其它卡依赖它; 标出真实可并行窗口。\n' +
  '- 每卡 dod_command 在目标 shell(本模板默认 Windows/PowerShell)下【真能跑且二值可判】; import 路径/包根与目录结构一致。\n' +
  '- 每卡 allow_paths 必须覆盖其 DoD 真正要改/要建的文件(例: DoD 跑测试 → allow_paths 含测试目录; DoD import 某依赖 → 该依赖在依赖清单且 allow_paths 含清单文件)。\n' +
  '- 路径只经项目约定的 storage/派生层(若有该不变量); DoD 禁硬编码运行时临时路径。\n' +
  '- 构建期 vs 运行期网络要分清: 卡内显式声明的构建期联网装依赖(如 npm install / 下载权重)属显式批准, 不与"运行期禁网"边界冲突; 把仅用于人工演示、不入确定性门禁的卡(如前端)显式标注、其 DoD 不放进禁网门禁。\n' +
  '- 并行窗口里的卡 allow_paths 互不重叠(各自 worktree 合并不撞)。\n' +
  '所有 dod_command 必须是目标 shell 下真能跑且二值可判的真实命令。'

log('decompose-cards: 投影计划任务章节为任务卡 + 4 角度对抗卡审')

const decomp = await agent(
  '把(修正后的)计划任务章节投影为【完整的带依赖关系任务卡集】(specs/tasks 的薄投影，非第二份计划)。\n' + CONSTRAINTS + '\n\n' +
    '为计划任务章节列出的每张卡产出每卡全字段(id/title/depends_on/parallelizable_with/allow_paths/forbid/non_goals/dod_command/dod_exit/dod_assert/plan_ref/hygiene/doc_sync/notes)。\n' +
    '把计划「本版砍掉/推迟」「右尺寸·刻意不做」里的能力级非目标按影响域分发进对应卡的 non_goals(只放与该卡功能相关的;无则 []);non_goals 是 forbid(横切硬边界)的能力级对偶,供 R3 评审 #14 判「顺手多做」的越界。\n' +
    '标 freeze_point(契约/schema 卡)与 topo_valid 与真实 parallel_window。',
  { phase: 'Decompose', schema: CARDS_SCHEMA }
)
log('拆出 ' + ((decomp.cards && decomp.cards.length) || 0) + ' 张卡, 冻结点 ' + decomp.freeze_point + ', topo_valid=' + decomp.topo_valid)

const LENSES = [
  '右尺寸/一个可评审可验证单元(尺寸定义见上 CONSTRAINTS): 每卡是否为单一连贯产出、一条 dod_command 判一件事、可一次评审判完(默认档量化缺省:净改动约≤200-400行、touched 文件≈1-3最多5)。报【过大】卡(产出含"且/和"多件事、dod 要多条命令、allow_paths 远超5 → 给出建议拆法)与【过碎】卡(一函数/一行配置一张、测试与实现分卡 → 建议合并)。也查卡是否自足(plan_ref/notes 让卡可独立开工)。severity: 【超默认档且未声明长自主执行】才判 HIGH(开工就会跑偏/返工);已声明长自主档(多文件弧+间隔 fresh-context 校验)的卡超默认档不算过大;过碎判 MEDIUM',
  '拓扑/依赖正确性: depends_on 无环、冻结点在所有依赖它的卡前、显式依赖(编排卡←脚手架/存储卡, 实跑卡←样例资产卡)是否齐, 并行窗口是否真实',
  'DoD 可机检性: 每卡 dod_command 在目标 shell 下是否真能跑且二值可判; import 路径与目录结构一致; 禁硬编码运行时路径; 依赖是否在 allow_paths/依赖清单内 —— 可 Read 仓库实际文件核',
  '硬边界/许可: 是否违反 CLAUDE.md 声明的硬边界(确定性/离线/无GPU/许可 等, 以本项目为准); 构建期联网是否被正确隔离出运行期禁网门禁',
  'allow_paths 覆盖与冲突: 每卡 allow_paths 是否覆盖其 DoD 真正要改的文件; 冻结卡是否只冻一等资产文件而非整目录; 并行卡 allow_paths 是否互不重叠; non_goals 是否承接了计划的「本版砍掉/推迟」(漏接=卡缺能力级围栏, 评审 #14 将无的放矢)',
]

const audits = await parallel(
  // TD63 item7：4 个审计 agent 此前共用同一个 label 'cardaudit'，令并行运行时的日志/追踪无法区分是哪个
  // lens 产出的 —— 按序号区分（cardaudit1..cardaudit4）。
  LENSES.map((l, i) => () =>
    agent(
      '你是卡片审计裁判，只看一个角度: ' + l + '\n\n' + CONSTRAINTS + '\n\n' +
        '审计下面这套任务卡(允许 Read 仓库真实文件如依赖清单/specs/ 来核实, 不要臆测):\n' + JSON.stringify(decomp, null, 1) + '\n\n' +
        '返回 lens + graph_ok + issues[{card,problem,severity(FATAL/HIGH/MEDIUM),fix}]。只报真问题, 能 Read 核实就核实。',
      { label: 'cardaudit' + (i + 1), phase: 'Card-Audit', schema: CARD_AUDIT_SCHEMA }
    )
  )
)

const allIssues = audits.filter(Boolean).flatMap((a) => (a.issues || []).map((i) => Object.assign({ lens: a.lens }, i)))
const fatal = allIssues.filter((i) => i.severity === 'FATAL')
const high = allIssues.filter((i) => i.severity === 'HIGH')
log('卡审完成: FATAL ' + fatal.length + ' / HIGH ' + high.length + ' / MEDIUM ' + (allIssues.length - fatal.length - high.length))

return {
  cards: decomp.cards,
  freeze_point: decomp.freeze_point,
  topo_valid: decomp.topo_valid,
  parallel_window: decomp.parallel_window,
  audit_issues: allIssues,
  fatal_count: fatal.length,
  high_count: high.length,
}
