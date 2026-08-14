export const meta = {
  name: 'scout-options',
  description:
    '第二步「2-options」：拿一页 brief → 多角度并行搜现成开源/库方案 → 逐候选按本仓硬边界(许可/离线/确定性/目标shell)打分 + 对抗式可行性核验 → 决策矩阵 + 推荐 + 可落 docs/adr 的 ADR 草案。填补 idea→plan 漏斗里"搜选项+评可行性"这一段。',
  phases: [
    { title: 'Scout', detail: '多角度并行搜候选(品类/关键词/生态/前人工程/近年顶会论文 SOTA)，各自产出带许可与活跃度信号的候选清单' },
    { title: 'Vet', detail: '去重后每个候选并行核验: 许可是否撞硬边界 + 可行性对抗打分 + build-vs-buy' },
    { title: 'Synthesize', detail: '汇总成决策矩阵 + 推荐 + 决策日志 + ADR 草案' },
  ],
}

// ── 路径全部经 args 参数化；换项目只改 args（或编辑下方相对默认值）──
const A = args || {}
const BRIEF = A.briefPath || (A.idea ? '' : '_local/1-brief.md')  // TD53：给了 idea 无 briefPath 时留空、交给下方 SOURCE 走 idea 分支；两者都没给才退常规默认路径
const IDEA = A.idea || ''                                     // 无 brief 时的一句话兜底
const CLAUDEMD = A.claudeMdPath || 'CLAUDE.md'               // 硬边界/不变量/许可硬规则
const LICENSE = A.licensePolicyPath || 'docs/LICENSE-POLICY.md'
const COUNT = A.count || 5                                    // 搜索角度数(并行) ；越大越广越贵(含学术 SOTA 角度)
const TODAY = A.today || ''                                   // 可选: 当前日期/年份(如 '2026-07' 或 '2026')；不传则用相对措辞、不猜绝对年份
                                                               // (沙箱内 new Date()/Date.now() 会抛，禁用；只能靠调用方传入)

if (!BRIEF && !IDEA) {
  return { error: '需要 args.briefPath(指向 _local/1-brief.md) 或 args.idea(一句话)。先跑 shape-idea skill 产出 brief。' }
}

const SOURCE = BRIEF
  ? '需求来源(先 Read 它): ' + BRIEF
  : '需求来源(无 brief，按这句想法): ' + IDEA

const CANDIDATE_SCHEMA = {
  type: 'object',
  properties: {
    angle: { type: 'string' },
    candidates: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          name: { type: 'string' },
          url: { type: 'string' },
          what: { type: 'string' },                          // 一句话: 它是什么/解决什么
          license: { type: 'string' },                       // 已知许可(MIT/Apache/GPL/未知)
          language: { type: 'string' },
          maturity_signals: { type: 'string' },              // star/最近活跃/release/维护者数等可观察信号
          why_relevant: { type: 'string' },
          source_kind: { type: 'string', enum: ['oss', 'paper', 'reference'] }, // 来源类型: 开源库 / 论文(含其代码) / 仅参考
          paper_ref: { type: 'string' },                      // 若 source_kind=paper: 会议+年份+标题(如 "CVPR 2025: Foo")
        },
        required: ['name', 'what', 'why_relevant'],
      },
    },
  },
  required: ['angle', 'candidates'],
}

const VET_SCHEMA = {
  type: 'object',
  properties: {
    name: { type: 'string' },
    license: { type: 'string' },
    license_ok: { type: 'boolean' },                         // 是否过本仓许可硬规则(GPL/AGPL/SSPL/非商用=禁)
    constraint_violations: { type: 'array', items: { type: 'string' } }, // 撞了哪些硬边界(离线/确定性/目标shell/无GPU 等)
    feasibility_score: { type: 'number' },                   // 0-10: 10=直接可用且贴合, 3=要大改, 0=不可行
    feasibility_reasons: { type: 'string' },
    build_vs_buy: { type: 'string', enum: ['adopt', 'fork-and-adapt', 'reference-only', 'build-from-scratch'] },
    risks: { type: 'array', items: { type: 'string' } },
    verdict: { type: 'string', enum: ['keep', 'drop'] },
  },
  required: ['name', 'license_ok', 'feasibility_score', 'build_vs_buy', 'verdict'],
}

const SYNTH_SCHEMA = {
  type: 'object',
  properties: {
    recommendation: { type: 'string' },                     // 选哪个做 base, 一句话
    rationale: { type: 'string' },
    runner_up: { type: 'string' },
    matrix: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          name: { type: 'string' },
          license: { type: 'string' },
          fit: { type: 'string' },                           // 贴合度(high/med/low + 一句)
          effort: { type: 'string' },                        // 接入工作量
          risk: { type: 'string' },
          decision: { type: 'string', enum: ['recommend', 'backup', 'reject'] },
        },
        required: ['name', 'decision'],
      },
    },
    decision_log: { type: 'array', items: { type: 'string' } }, // 为什么选 X 不选 Y, 逐条
    adr_markdown: { type: 'string' },                        // 可直接落 docs/adr/NNNN-*.md (背景/决策/备选方案/后果)
  },
  required: ['recommendation', 'rationale', 'matrix', 'adr_markdown'],
}

const HARD =
  '动手前先 Read 本仓硬边界与许可硬规则: ' + CLAUDEMD + ' 与 ' + LICENSE + '。\n' +
  '本仓硬规则(打分必须据此): 依赖许可 GPL/AGPL/SSPL/非商用 = 禁(只许 MIT/BSD/Apache 等宽松); ' +
  '运行期默认离线/确定性(出站网络/不确定性是硬边界, 除非卡内显式声明的构建期联网); 目标 shell 默认 Windows/PowerShell; ' +
  '原创实现优先、机密不入库。任何候选撞这些都要在 constraint_violations 里点名。'

// 多角度搜索: 每个角度只换"怎么找"，互相不知道对方搜到什么(多模态扫描, 单一角度搜不全)
const ANGLES = [
  { key: 'by-category', focus: '按问题**品类**搜: 该领域的成熟开源项目、"awesome-<品类>"清单、事实标准库。先广后窄。' },
  { key: 'by-keyword', focus: '按**核心能力关键词**直接搜最匹配的库/项目(1-3 词查询, 别堆长句)。' },
  { key: 'by-ecosystem', focus: '在**目标语言/运行时生态**内搜(如 PyPI/npm 的对应包), 优先维护活跃、许可宽松者。' },
  { key: 'by-prior-art', focus: '搜**前人工程实现**: 大厂/知名开源的参考实现、复现仓库、工程博客。source_kind 多为 oss 或 reference。' },
  {
    key: 'by-research',
    focus:
      '搜**近年顶会论文与 SOTA**(近 1-2 年优先' + (TODAY ? (', 当前是 ' + TODAY) : '') + '): 经 **Google Scholar / arXiv / Papers with Code** 检索, ' +
      '覆盖 **CVPR / ECCV / ICCV / ICLR / ICML / NeurIPS / AAAI**(及领域对口会, 如 NLP 的 ACL/EMNLP)。\n' +
      '对每篇有价值的论文: 找出其**官方代码仓 + 许可 + 是否可复现/在维护**; url 填代码仓(无代码则填论文页), source_kind=paper, ' +
      'paper_ref 填「会议+年份+标题」。\n' +
      '**警示**: 研究代码常**无 license 或为非商用/研究-only/CC-BY-NC/自定义权重许可**——据实标注, 这类多半只能 reference-only, 会被本仓许可闸拦(见 vet 阶段)。别因为"是 SOTA"就放宽许可判断。',
  },
].slice(0, COUNT)

log('scout-options 启动: ' + ANGLES.length + ' 个角度并行搜候选 → 逐候选核验 → 决策矩阵')

const SCOUT_COMMON =
  SOURCE + '\n\n你在为这个需求**搜可复用的现成方案**(开源项目/库/参考实现)。' +
  '尽量用 web 搜索与抓取找真实存在的候选(查询短、1-3 词最佳; 找到后抓主页/README/许可页核实, 别臆造)。' +
  '每个候选给 name/url/what/license(查到就填, 没查到填"未知")/language/maturity_signals(star/最近 release/维护活跃度等可观察信号)/why_relevant/source_kind(oss|paper|reference)/paper_ref(来自论文则填 会议+年份+标题)。' +
  '若候选来自论文: url 优先填其**官方代码仓**(无代码则填论文页), license 填该代码仓的许可(研究代码常为非商用/无许可, 据实填、别留空)。' +
  '只报**真实存在**的候选; 宁缺毋滥。'

const scoutResults = await parallel(
  ANGLES.map((a) => () =>
    agent(
      SCOUT_COMMON + '\n\n## 你的搜索角度: ' + a.key + '\n' + a.focus,
      { label: 'scout:' + a.key, phase: 'Scout', schema: CANDIDATE_SCHEMA }
    )
  )
)

// 去重(按 name 小写归并)
const seen = new Map()
for (const r of scoutResults.filter(Boolean)) {
  for (const c of r.candidates || []) {
    const k = (c.name || '').trim().toLowerCase()
    if (k && !seen.has(k)) seen.set(k, c)
  }
}
const candidates = Array.from(seen.values())
log('搜到去重后 ' + candidates.length + ' 个候选, 开始逐个核验(许可 + 可行性对抗打分)')

if (!candidates.length) {
  return {
    candidates: [],
    note: '没搜到候选——可能需求太新/太窄(倾向 build-from-scratch), 或搜索角度不对。建议人工补一轮搜索或直接进第三步按 build-from-scratch 写计划。',
  }
}

const vetted = await parallel(
  candidates.map((c) => () =>
    agent(
      '你在核验一个候选方案能否做本项目的 base/依赖。' + HARD + '\n\n' +
        '候选: ' + JSON.stringify(c) + '\n' +
        '需求: ' + SOURCE + '\n\n' +
        '允许 web 抓取该候选的 README/许可/issue 核实, 别臆测。给出: license + license_ok(撞 GPL/AGPL/SSPL/非商用即 false) + ' +
        'constraint_violations(撞了哪些硬边界) + feasibility_score(0-10) + feasibility_reasons + ' +
        'build_vs_buy(adopt/fork-and-adapt/reference-only/build-from-scratch) + risks + verdict(keep/drop)。' +
        '默认怀疑: 许可不明、维护停滞、与硬边界冲突、贴合度低 → 倾向 drop。',
      { label: 'vet:' + (c.name || 'x'), phase: 'Vet', schema: VET_SCHEMA }
    )
  )
)

const vets = vetted.filter(Boolean)
const kept = vets.filter((v) => v.verdict === 'keep')
const dropped = vets.filter((v) => v.verdict === 'drop')
log('核验完成: 保留 ' + kept.length + ' / 淘汰 ' + dropped.length + ' (含许可/可行性不过者)')

const synth = await agent(
  '你是汇总裁判, 产出一份**选型决策**。先 Read 需求(' + (BRIEF || IDEA) + ')与硬规则(' + CLAUDEMD + ' / ' + LICENSE + ')。\n' +
    '通过核验的候选:\n' + JSON.stringify(kept, null, 1) + '\n\n被淘汰的(附理由, 供决策日志引用):\n' + JSON.stringify(dropped, null, 1) + '\n\n' +
    '产出: recommendation(选哪个做 base, 或判定 build-from-scratch) + rationale + runner_up + ' +
    'matrix(每候选 name/license/fit/effort/risk/decision) + decision_log(为什么选 X 不选 Y, 逐条) + ' +
    'adr_markdown(可直接落 docs/adr/NNNN-kebab.md 的 ADR: 标题 + 背景 / 决策 / 备选方案 / 后果 四节, 中文)。\n' +
    '红线: 推荐项的许可必须过本仓硬规则; 若所有候选都不过或都不贴合, 就明确推荐 build-from-scratch 并说清原因。',
  { phase: 'Synthesize', schema: SYNTH_SCHEMA }
)
log('选型完成: 推荐 = ' + synth.recommendation)

return {
  recommendation: synth.recommendation,
  rationale: synth.rationale,
  runner_up: synth.runner_up || '',
  matrix: synth.matrix,
  decision_log: synth.decision_log || [],
  adr_markdown: synth.adr_markdown,
  scouted_count: candidates.length,
  kept_count: kept.length,
  dropped: dropped.map((d) => ({ name: d.name, license: d.license, why: d.feasibility_reasons })),
  next_step:
    '人工(默认): 把决策矩阵/推荐落到 _local/2-options.md; 把 adr_markdown 落到 docs/adr/NNNN-<kebab>.md(永久决策记录); ' +
    '然后进第三步: 按推荐的 base 写 _local/PLAN.md(docs/PLAN-TEMPLATE.md)→ plan-forge.mjs。' +
    '自主链式(长自主运行可选; 启用长自主运行本身即用户对本段的委托): 落盘决策矩阵/归档 ADR 自主完成并报证据(recommendation/matrix/decision_log)后, ' +
    '若 recommendation 与已批准 brief 的范围/约束一致, 即按 recommendation 自主进入第三步并报证据; ' +
    '仅当推荐会改变已批范围(如引入新许可类别、成本量级、超出 brief 的架构含义)或出现只有用户能答的问题时才停。计划签核仍是下一个硬人工闸。',
}
