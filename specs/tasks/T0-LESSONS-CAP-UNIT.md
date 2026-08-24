---
id: T0-LESSONS-CAP-UNIT
title: 必须层封顶改按驻留经验 id 计量，并把 enforced_by 做成双向判据（横切卡，尺寸见 allow_paths）
depends_on: [T0-LESSONS-TIER1-CUT]
parallelizable_with: []
status: in-progress
branch: T0-LESSONS-CAP-UNIT
worktree: C:\wt\T0-LESSONS-CAP-UNIT
allow_paths:
  # 横切卡（L97）：改的是「必须层驻留规则怎么计量」这条规则本身，教它的面一次扫齐——
  # 判定核 / 两个消费者 / 接线闸 / 教学面。>5 是横切的固有形态，非 scoping 失误。
  - scripts/_lessons.ps1
  - scripts/lessons.ps1
  - scripts/_cards.ps1
  - scripts/selftest.ps1
  - scripts/triage.ps1
  - CLAUDE.md
  - docs/LOOP-ENGINEERING.md
  - docs/DELIVERY-CHAINS.md
  - .claude/skills/triage/SKILL.md
  - specs/tasks/T0-LESSONS-CAP-UNIT.md
  # 2026-08-23 扩围：pre-R3 独立复核（Opus 5）扫出 L97 清单**漏了五处权威面**，它们仍在教被本卡
  # 废止的「条数」单位、或仍按旧探针数枚举心跳。L97 明写这类面须**一次性纳入 allow_paths**，
  # 分两张卡扫必然多打一轮 R3，故就地扩围而非另开卡（扩围落在 pinned base = master，符合范围闸语义）。
  - docs/LESSONS.md                     # :10 三层表 Tier-1 容量格仍写「封顶 N 条」——权威文档 #4
  - .claude/skills/lessons/SKILL.md     # :18 同上；:33 PURIFY 步骤写「必须层≤上限」
  - scripts/_config.ps1                 # :140 LessonsMustCap 定义处注释仍写「封顶条数」；本卡注释三次指向它
  - docs/HARNESS-REVIEW.md              # :137 自动触发点只列三枚探针，新探针的 next 串却指回本文
  - docs/scaffold-architecture.html     # :468 心跳节点枚举 8 枚探针，缺新增两枚
forbid:
  - 抬高 `LessonsMustCap`
  - 让心跳探针打网络或调 gh（`delivery-blocked` 只读 `.review/*.json`；心跳恒离线、退出码恒 0）
  - 新增第二处「驻留 id 怎么数」的实现——判定核只有 `scripts/_lessons.ps1` 一处
non_goals:
  - fleet 回路与探针 12 `scaffold-stale`（那是 `T0-SCAFFOLD-FLEET-LOOP`，本卡的下一张）
  - 必须层减法本身（上一张卡 `T0-LESSONS-TIER1-CUT` 已做完）
acceptance:
  # 作者声明的验收清单：以下是本卡认为「完成」所需的事实，每条应有可证伪测试。
  # **这是一份声明，不改变任何评审语义**——裁决仍完全按 docs/QUALITY-RUBRIC.md 现行 rubric 判，
  # 清单未列到的问题照常按现行 rubric 处理（含其现行的 [FOLLOW-UP] 适用条件）。
  # 「把清单当排他性判据、清单外一律 FOLLOW-UP」是上游提案 Asun28/claude-devops-scaffold#203
  # 的内容，**上游落地前本仓不采用**。
  - "A1 生产路径的计量单位：lessons.ps1 check 对真实 CLAUDE.md 同时断言驻留 id 数 == 9 与承载条目数 == 7，两个数各断言精确值——只断言 id=9 证不出「单位换了」"
  - "A2 封顶两侧边界：驻留 id 恰好 == cap 出 minor 且文案含「达封顶」，== cap+1 出 major 且文案含「超封顶」，两侧各一枚 hermetic 夹具。夹具内把 cap **注入**成一个小值（免于造 11 个 id），但两侧的 id 数必须由该注入变量算出（cap 与 cap+1），**不得出现 3/4 这类字面量**；生产常量 LessonsMustCap 的取值不参与本夹具，改它不应让本用例变色"
  - "A3 旧口径必须在同一夹具下仍绿：A2 的**两枚**夹具都须满足「markdown 条目数 ≤ cap」——超封顶那枚驻留 id 数 > cap，达封顶那枚 == cap（不是两枚都 > cap，那与 A2 的 minor 侧自相矛盾）——且该前提本身有断言，否则夹具证不了新口径"
  - "A4 分节解析 fail-closed：CLAUDE.md 里找不到「经验铁律」小节时，scripts/_lessons.ps1 的分节解析器（本卡内由 Get-ScaffoldMustLayerBullet 更名为 **Get-ScaffoldMustLayerSection**，返回 Found/Reason 以区分「标题没找到」与「小节在、零驻留」）不得静默返回 0 条——须以 ASCII 哨兵 [LESSONS-SECTION-NOT-FOUND] 报错且 lessons.ps1 check 非零退出。一枚改标题的负例夹具与一枚「小节在、零驻留」的正例夹具（须仍 PASS）各断言之"
  - "A5 enforced_by 双向四方向：有守卫的 ledger 条不被提名晋升 / 有守卫的 must 条被提名降层 / 显式 none（理由）判为无守卫 / 空字段判为无守卫（不得跨行捕到下一行的值），四条各一断言"
  - "A6 enforced_by 未知取值 fail-closed：TODO / N/A / 待补 这类既非空又非 none 的取值不得被读成「已有守卫」——须与空字段同判，且 lessons.ps1 check 直接拒绝该形态"
  - "A7 批量窗口两侧：候选数恰好 == PromoteBatchSize 时逐条报（断言条数 == N），== N+1 时合成 1 条且文案含 N+1；阈值读脚本常量，-gt 退化成 -ge 即有一侧变红"
  - "A8 delivery-blocked 四态：in-progress+block 报恰 1 条且 severity=blocking、文案含卡 id 与理由条数；in-progress+pass 不报；todo 卡+block 不报；坏 JSON 不抛异常且不报"
  - "A9 主检出取证路径：worktree 侧刻意缺席、只留 仓库根的 .review/ 下与卡 id 同名的 json 时仍报恰 1 条，且文案含该卡 id"
  - "A10 取证路径重合去重：仓库根恰好等于 worktree 根加卡 id 时报恰 1 条，且断言报的就是该卡（what 含 id、path 指向那唯一文件）——只断言条数会把「两条都被归属挡掉」的 0 条与真去重混为一谈"
  - "A11 去重键按**完全限定路径**比较：FileInfo.FullName 本就完全限定且已折叠 . / .. 段，故不再对它二次调用 [IO.Path]::GetFullPath——该调用经十一种路径形态实测为恒等变换（含 8.3 短名、目录联接、UNC、尾分隔符、不同 PSDrive 位置下的相对路径），没有任何变异能杀死它，**本条不为它要求变异**。要证的只有比较器：一枚夹具让两条取证路径的**字符串真不等**（一侧整段大写），并配一枚单句变异——把比较器换成 Ordinal——让本条在大小写不敏感的文件系统上变红。**期望条数按运行 OS 判据分支，见 A12**；本条不写死「只报 1 条」，那在 Linux 上与 A12 要求的 2 条矛盾"
  - "A12 去重键的 OS 语义：路径比较的大小写敏感性按运行 OS 定——Linux 上 a.json 与 A.json 是两份不同裁决须各报一条，Windows 上同名不同壳只报一条；两个断言按 OS 判据分支各跑一次"
  - "A13 归属校验三道各一条：文件名非本卡且 branch 属别人不报 / 文件名恰是卡 id 加 .json 但 branch 属别人不报 / 无 branch 字段且文件名非本卡不报。删 branch 判定使第二条红、删文件名兜底使第三条红"
  - "A14 归属契约有机检：selftest 静态断言 review.ps1 写进裁决的 branch 字段取自 git rev-parse --abbrev-ref HEAD、且字段名为 branch（review.ps1 不在本卡 allow_paths，故只钉断言不改它）"
  - "A15 L97 权威面一致（两条枚举断言）：① 教「封顶单位」的面（docs/LESSONS.md · lessons skill · _config.ps1 · lessons.ps1 头注 · triage.ps1 头注 · CLAUDE.md 小节）皆不得再含「封顶 N 条 / 条数上限」形态；② 列探针清单的面（docs/LOOP-ENGINEERING.md · triage SKILL.md · docs/DELIVERY-CHAINS.md · docs/scaffold-architecture.html · docs/HARNESS-REVIEW.md）条数须等于 triage.ps1 里 Invoke-Probe* 的实际个数。漏改任一处即红"
  - "A16 常设接线不断：_cards.ps1 的 BOM 分支有常设用例（带前导 U+FEFF 的 front-matter 仍解得出 status，码位只写转义形态）；triage.ps1 经共享 Get-FrontMatter 的调用点数 == 4；_lessons.ps1 进每一份选择性夹具拷贝清单"
dod_command: pwsh -NoProfile -Command "if (-not ((((& pwsh -NoProfile -File scripts/triage.ps1 selfcheck) -match 'triage selfcheck: PASS').Count -eq 1) -and (((& pwsh -NoProfile -File scripts/lessons.ps1 check) -match 'id=9').Count -eq 1))) { exit 1 }"
dod_exit: 0
dod_assert: `triage selfcheck` 打印 ASCII 哨兵 PASS（覆盖新探针 10/11 与改口径后的探针 1/5，全部走 hermetic 夹具），且 `lessons.ps1 check` 在**生产路径**上按驻留 id 报出 9——不是按条目报 7，证明新口径不只在夹具里生效
review_gate: codex {verdict:pass}
hygiene: 6 枚单句变异逐一击杀（封顶退回按条目计数 / 撤 enforced_by 闸 / enforced_by 正则退回跨行 / delivery-blocked 不再按 verdict 过滤 / 降层不再要求守卫 / selfcheck 不再注入夹具总账），每枚还原后核 SHA256 逐字节一致；另一枚证 `_cards.ps1` 的 BOM 锚点（去掉 `\uFEFF?` 后带 BOM 的卡 front-matter 解析成 null）
doc_sync: CLAUDE.md 计量单位说明 · LOOP-ENGINEERING 与 triage skill 的探针枚举与计数 · DELIVERY-CHAINS 心跳行（R5）
---

# T0-LESSONS-CAP-UNIT

## 产出

把「必须层封顶」的**计量单位**从 markdown 条目改成**驻留的经验 id**，并让 `enforced_by`
在晋升与降层两个方向上都作数。

- `scripts/_lessons.ps1`（新）—— 判定核**只此一处**，`lessons.ps1 check` 与心跳探针 5 共用，不会漂移。
- 探针 1 `lessons-promote` 加 `enforced_by` 闸与批量窗口；新增探针 10 `lessons-demote`（逆向）。
- 新增探针 11 `delivery-blocked` —— 唯一读**交付**状态的探针，离线读 `.review/*.json`。
- `_cards.ps1` front-matter 容忍前导 U+FEFF（上游 v0.41.0 TD130）。

## 这三条修复的来历

2026-08-21 从本仓向上游提了三个 issue：#184（封顶数的是条目、不是它要管的上下文成本）、
#183（晋升探针无视自己声称支持的 `enforced_by`）、#185（十个探针全都对交付状态失明）。
上游 v0.43.0 的 #188/#189/#190 就是这三条的修复。**回填它们不是跟版本号，是把自己报上去的洞补上。**

## 为什么依赖上一张卡

改对单位后，`CLAUDE.md` 的真实驻留数会第一次被如实报出。上一张卡已把它从 19 降到 9，
所以本卡落地即绿；**反过来先改单位，selftest 闸 2 会当场红在 19>10 上**。

## 禁止 / 非目标

见 front-matter。心跳的「只读、离线、确定性」是刻意不变量（`docs/LOOP-ENGINEERING.md`）：
`delivery-blocked` 不调 gh，因为 `review.ps1` 每次跑都已把归一化裁决写在本地。

## 验收（DoD = 命令 + 退出码 + 断言）

```powershell
pwsh -NoProfile -File scripts\triage.ps1 selfcheck
pwsh -NoProfile -File scripts\lessons.ps1 check
```
- 期望退出码：0
- 断言：`triage selfcheck: PASS` + `lessons.ps1 check` 报驻留 `id=9`
