---
id: T0-CARD-ACCEPTANCE-FIELD
title: 把 acceptance 作者声明的验收清单登记为正式卡片字段，并给它一道形态机检
depends_on: [T0-R3-DIFF-BUDGET]
parallelizable_with: []
status: merged
branch: T0-CARD-ACCEPTANCE-FIELD
worktree: C:\wt\T0-CARD-ACCEPTANCE-FIELD
allow_paths:
  - specs/README.md
  - specs/tasks/_TEMPLATE.md
  - scripts/check-cards.ps1
  - scripts/selftest.ps1
forbid:
  - 改 docs/QUALITY-RUBRIC.md 的判定维度（上游 PR #208/v0.46 已落地，但本仓当前 ScaffoldVersion 仍为 v0.45.0；本卡不提前回填）
  - 把 acceptance 变成必填字段（存量活卡会当场全红，且未实现的卡此刻写不出夹具级条目）
  - 用机检判断条目「够不够精确」——那是人/评审的判断，机检只判形态
non_goals:
  - 给存量卡补写 acceptance 内容（那是各卡自己的活）
  - 改 review.ps1 的卡片注入方式（现已整卡 raw 注入，acceptance 天然可见）
  - 退役 *-R3-CLOSURE 卡（R5 的活，随各自母卡走）
acceptance:
  # 作者声明的验收清单：以下是本卡认为「完成」所需的事实，每条应有可证伪测试。
  # **这是一份声明，不改变任何评审语义**——裁决仍完全按 docs/QUALITY-RUBRIC.md 现行 rubric 判，
  # 清单未列到的问题照常按现行 rubric 处理（含其现行的 [FOLLOW-UP] 适用条件）。
  # 上游 Asun28/claude-devops-scaffold PR #208（v0.46）已落地 closed acceptance-set / 清单外 [FOLLOW-UP] 语义；
  # 本仓当前 ScaffoldVersion 仍为 v0.45.0，尚未吸收该评审语义；本卡不改 docs/QUALITY-RUBRIC.md。
  - "A1 字段登记：specs/README.md 的 front-matter 字段表新增 acceptance 一行，说明它是**可选**的作者声明性验收清单；本仓当前 rubric 仍按现行规则判定，不因该字段自动将清单外问题归为 [FOLLOW-UP]（上游 closed-set 语义见 PR #208/v0.46，暂不回填）"
  - "A2 模板落地：specs/tasks/_TEMPLATE.md 含注释态的 acceptance 示例块，示例条目形如 A1/A2 且演示夹具级精度（含具体数值与 ASCII 哨兵各一例）"
  - "A3 形态机检：check-cards.ps1 在卡片含 acceptance 时校验它是至少 3 条的块式双引号字符串序列（本仓无 YAML 解析依赖，故只登记这一种规范形态），任一条不是该形态即以 [CARD-ACCEPTANCE-INVALID] 非零退出"
  - "A4 编号连续：条目须严格 A1..An 顺序编号，缺号（A1,A3）、重号（A1,A1）、乱序（A2,A1）、越位起始（A0 或 A2 开头）四类各有一枚夹具，均以 [CARD-ACCEPTANCE-INVALID] 拒绝并点名首个违例的序号"
  - "A5 诊断可定位：拒绝信息含卡片 id 与首个违例条目的 1-based 序号，两者各有一枚断言（不只断言退出码非零）"
  - "A6 缺失只告警不阻断：不含 acceptance 的卡片仍 PASS（退出 0），但输出一行含卡片 id 的 advisory；一枚夹具断言 exit 0 且 advisory 在，另一枚断言含 acceptance 的卡片**不**产生该 advisory"
  - "A7 存量不回归：对 origin/master 的 specs/tasks/*.md 全部活卡（_TEMPLATE.md 按 A8 豁免）跑 check-cards，退出 0；所有含 acceptance 的活卡均不触发 A3–A5；已合并归档的卡不计入活卡集合"
  - "A8 _TEMPLATE 豁免不被削弱：_TEMPLATE.md 继续被跳过（其占位符故意违规），一枚夹具证明把 A4 的乱序编号放进 _TEMPLATE.md 仍 PASS"
  - "A9 单句删除变异：A3/A4/A6 三道守卫各配一枚只删一句的变异，删后专属断言变红（判据分类器：非零**且**命中该断言文本才算击杀）"
  - "A10 selftest 接线：闸 10 系列新增本卡断言，且 -Shard core 与 -Shard workflow 均 exit 0"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/check-cards.ps1 -SimpleMatch '[CARD-ACCEPTANCE-INVALID]' -Quiet) -and (Select-String -Path specs/README.md -Pattern '^\|\s*.?acceptance.?\s*\|' -Quiet) -and (Select-String -Path specs/tasks/_TEMPLATE.md -Pattern '^#?\s*acceptance:' -Quiet))) { exit 1 }"
dod_exit: 0
dod_assert: check-cards 含 [CARD-ACCEPTANCE-INVALID] 状态码；specs/README.md 字段表含 acceptance 行；_TEMPLATE.md 含 acceptance 示例块。A1–A10 每条都有可证伪测试。
review_gate: codex {verdict:pass}
hygiene: A3/A4/A6 三道守卫各留一枚最小行为夹具 + 一枚单句删除变异；不为编号规则再造第二套解析器
doc_sync: specs/README.md 字段表即文档本体；合并后在 CLAUDE.md「权威文档」无需新增条目（specs/README.md 已在列）
---

# T0-CARD-ACCEPTANCE-FIELD

## 问题

`acceptance:` 已经在用——本仓多张活卡带着它，`review.ps1:354` 把**整张卡原文**注入评审者提示词，所以声明确实抵达 R3——
但 `specs/README.md` 的 front-matter 字段表里没有它，`specs/tasks/_TEMPLATE.md` 里也没有。于是：

- 新卡的作者（人或 agent）照模板写卡，**根本不知道有这个字段**；
- 已有卡片的条目形态（`A1 ...` 编号、双引号块式列表）是口口相传的惯例，没有任何机检；
- 编号一旦漂移，指向它的提交信息、评审 reason、测试注释（「A9 ship 前置」）会静默失指。

## 决策

**登记 + 形态机检，不做内容判断。**

`check-cards.ps1` 只判**形态**——这是它既有的分工（`specs/README.md`「卡片即代码」一节写死：它校验形态、不校验内容意图）。
条目「够不够夹具级精度」由人和 R3 判，机检碰都不碰：那是判断题，写成正则只会催生绕过它的措辞。

字段保持**可选**。存量活卡此刻补不出夹具级条目——未实现的卡还不知道自己的夹具长什么样，硬填只会写出
PR #124 那种被 R3 连打两轮的名字黑名单式条目。缺失走 advisory（照抄 `allow_paths > 5` 的既有形态），
让补齐进度机器可见，而不是把存量活卡一次性判红。

## 编号为什么要连续

`A1..An` 严格连续不是洁癖。这些 id 是**跨文件引用的锚**：提交信息写「补 A9 远端取证」、评审 reason 写「A5 违反」、
测试注释写「A14：allow_paths 条目数不是规模证明」。缺号或重号让这些引用指向空气，而**没有任何东西会报错**——
正是本仓 L165 要根除的静默失效形态。

## 与上游的关系

`acceptance:` 是本仓先行的下游试验，曾作为提案提给上游 Asun28/claude-devops-scaffold **#203**；该方向已在上游
PR #208（v0.46）落地。当前本仓 ScaffoldVersion 仍为 v0.45.0，尚未吸收 #208 的评审语义；本卡只登记**本地字段与形态闸**，
**不改** `docs/QUALITY-RUBRIC.md`，也不把清单外问题自动标为 `[FOLLOW-UP]`。吸收 v0.46 需另走版本审查/回填卡。

## 串行约束

`scripts/selftest.ps1` 目前被 5 张在飞卡共用（#49 / #51 / #127 / #128 / T0-LESSONS-BUMP-PLANE）。
本卡须排在它们之后，`depends_on` 取其中最靠后落地的一张（T0-R3-DIFF-BUDGET），避免同文件并发。
