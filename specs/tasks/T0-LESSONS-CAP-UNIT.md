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
  - docs/LESSONS.md                     # 三层表 Tier-1 容量格原写「封顶 N 条」——权威文档 #4，已改按驻留 id
  - .claude/skills/lessons/SKILL.md     # 同上；PURIFY 步骤原写「必须层≤上限」，已补单位与占位符拒收
  - scripts/_config.ps1                 # LessonsMustCap 定义处原写「封顶条数」；本卡注释三次指向它
  - docs/HARNESS-REVIEW.md              # 自动触发点原只列三枚探针，而新探针的 next 串指回本文，已改列全 10 枚
  - docs/scaffold-architecture.html     # 心跳节点原枚举 8 枚探针，缺新增两枚，已补齐并改用探针本名
  # 以上五处漏改一处即由 selftest 闸 14g 的两条枚举断言当场变红——L97 的「一次扫齐」从此是常设闸、不再是一次性动作。
forbid:
  - 抬高 `LessonsMustCap`
  - 让心跳探针打网络或调 gh（`delivery-blocked` 只读 `.review/*.json`；心跳恒离线、退出码恒 0）
  - 新增第二处「驻留 id 怎么数」的实现——判定核只有 `scripts/_lessons.ps1` 一处
non_goals:
  - fleet 回路与探针 12 `scaffold-stale`（那是 `T0-SCAFFOLD-FLEET-LOOP`，本卡的下一张）
  - 必须层减法本身（上一张卡 `T0-LESSONS-TIER1-CUT` 已做完）
acceptance:
  # 封闭验收集合：以下即本卡「完成」的全部内容。清单内每条须有可证伪测试。
  - "A1 生产路径的计量单位：lessons.ps1 check 对真实 CLAUDE.md 同时断言驻留 id 数 == 9 与承载条目数 == 7，两个数各断言精确值——只断言 id=9 证不出「单位换了」"
  - "A2 封顶两侧边界：驻留 id 恰好 == LessonsMustCap 出 minor 且文案含「达封顶」，== cap+1 出 major 且文案含「超封顶」，两侧各一枚 hermetic 夹具，阈值取自脚本常量而非字面量"
  - "A3 旧口径必须在同一夹具下仍绿：夹具的 markdown 条目数 <= cap 而驻留 id 数 > cap，且该前提本身有断言（否则夹具证不了新口径）"
  - "A4 分节解析 fail-closed：CLAUDE.md 里找不到「经验铁律」小节时 Get-ScaffoldMustLayerBullet 不得静默返回 0 条——须以 ASCII 哨兵报错且 lessons.ps1 check 非零退出，一枚改标题的夹具断言之"
  - "A5 enforced_by 双向四方向：有守卫的 ledger 条不被提名晋升 / 有守卫的 must 条被提名降层 / 显式 none（理由）判为无守卫 / 空字段判为无守卫（不得跨行捕到下一行的值），四条各一断言"
  - "A6 enforced_by 未知取值 fail-closed：TODO / N/A / 待补 这类既非空又非 none 的取值不得被读成「已有守卫」——须与空字段同判，且 lessons.ps1 check 直接拒绝该形态"
  - "A7 批量窗口两侧：候选数恰好 == PromoteBatchSize 时逐条报（断言条数 == N），== N+1 时合成 1 条且文案含 N+1；阈值读脚本常量，-gt 退化成 -ge 即有一侧变红"
  - "A8 delivery-blocked 四态：in-progress+block 报恰 1 条且 severity=blocking、文案含卡 id 与理由条数；in-progress+pass 不报；todo 卡+block 不报；坏 JSON 不抛异常且不报"
  - "A9 主检出取证路径：worktree 侧刻意缺席、只留 <RepoRoot>/.review/<id>.json 时仍报恰 1 条，且文案含该卡 id"
  - "A10 取证路径重合去重：<RepoRoot> == <wtRoot>/<id> 时报恰 1 条，且断言报的就是该卡（what 含 id、path 指向那唯一文件）——只断言条数会把「两条都被归属挡掉」的 0 条与真去重混为一谈"
  - "A11 去重键的规范化本身：两条路径字符串不等、指向同一文件（一条含 .\\ 段或大小写不同的目录段）时仍只报 1 条；配两枚单句变异——去掉 GetFullPath、把比较器换成 Ordinal——各自让本条变红"
  - "A12 去重键的 OS 语义：路径比较的大小写敏感性按运行 OS 定——Linux 上 a.json 与 A.json 是两份不同裁决须各报一条，Windows 上同名不同壳只报一条；两个断言按 OS 判据分支各跑一次"
  - "A13 归属校验三道各一条：文件名非本卡且 branch 属别人不报 / 文件名恰是 <id>.json 但 branch 属别人不报 / 无 branch 字段且文件名非本卡不报。删 branch 判定使第二条红、删文件名兜底使第三条红"
  - "A14 归属契约有机检：selftest 静态断言 review.ps1 写进裁决的 branch 字段取自 git rev-parse --abbrev-ref HEAD、且字段名为 branch（review.ps1 不在本卡 allow_paths，故只钉断言不改它）"
  - "A15 L97 权威面一致（两条枚举断言）：① 教「封顶单位」的面（docs/LESSONS.md · lessons skill · _config.ps1 · lessons.ps1 头注 · triage.ps1 头注 · CLAUDE.md 小节）皆不得再含「封顶 N 条 / 条数上限」形态；② 列探针清单的面（docs/LOOP-ENGINEERING.md · triage SKILL.md · docs/DELIVERY-CHAINS.md · docs/scaffold-architecture.html · docs/HARNESS-REVIEW.md）条数须等于 triage.ps1 里 Invoke-Probe* 的实际个数。漏改任一处即红"
  - "A16 常设接线不断：_cards.ps1 的 BOM 分支有常设用例（带前导 U+FEFF 的 front-matter 仍解得出 status，码位只写转义形态）；triage.ps1 经共享 Get-FrontMatter 的调用点数 == 4；_lessons.ps1 进每一份选择性夹具拷贝清单"
acceptance_notes: |
  逐条落点（验收即在这些位置可证伪）：
    A1  scripts/selftest.ps1 闸 2a（生产路径，两个期望值各自精确断言、且断言二者不相等）
    A2  scripts/triage.ps1 用例 5（foreach 两侧边界，阈值全部由 $MustCap 算出）
    A3  scripts/triage.ps1 用例 5 的 $bulletCount 前置断言（两侧夹具各跑一次）
    A4  scripts/selftest.ps1 闸 2d（正例仍 PASS + 负例非零且带 [LESSONS-SECTION-NOT-FOUND]）；探针侧 = 用例 5b
    A5  scripts/triage.ps1 用例 6（L901 降层 / L903 不晋升 / L902 none 不降层 / L904 空字段须晋升，四条各一断言）
    A6  同上 L905（TODO，tier must，不得降层）与 L906（N/A，tier ledger，须晋升）+ lessons.ps1 check 的
        Test-ScaffoldLessonEnforcedByWellFormed 闸与其自检探针
    A7  scripts/triage.ps1 用例 7      A8 用例 4      A9 用例 8      A10 用例 9      A12 用例 9b
    A13 scripts/triage.ps1 用例 10(a)(b)(c)          A14 scripts/selftest.ps1 闸 10d(接线/review→triage)
    A15 scripts/selftest.ps1 闸 14g①②                A16 闸 10d(BOM/纯函数)、闸 10d(接线/triage)、闸 12d 拷贝清单
  A11 的**偏离**（人裁待办，不自行弱化清单）：`[IO.Path]::GetFullPath($vf.FullName)` 实测是恒等变换——
    FileInfo.FullName 本就完全限定且已折叠 `.`/`..` 段（`Get-Item <d>\a\..\a\.review\X.json` 交出的 FullName
    已无 `..`），故「去掉 GetFullPath」这枚变异**不可能**让任何用例变红，A11 那半条无法成立。已按「不留写不出
    变异的守卫」处理：删掉该恒等调用、键改为 $vf.FullName，并把注释里「Windows 路径大小写不敏感」这句普适宣称
    换成按 OS 取比较器的实际规则。A11 的另一半（大小写不同的目录段仍只报 1 条 + 比较器换 Ordinal 即红）由
    用例 9b 满足，实测该枚变异击杀。
  两处措辞说明（清单逐字保留，不就地改写）：① A4 里的 `Get-ScaffoldMustLayerBullet` 已随本轮更名为
    `Get-ScaffoldMustLayerSection`（同一枚判定核，多返回 Found/Reason 两个字段，好让「标题没找到」与
    「小节在、零驻留」可分辨）；② A9/A10/A13 里的 `<RepoRoot>` / `<id>.json` 是路径**形态**记号，
    check-cards 的占位符启发式会据此发一条 advisory 告警（非阻断，`check-cards: PASS`）。
dod_command: pwsh -NoProfile -Command "if (-not ((((& pwsh -NoProfile -File scripts/triage.ps1 selfcheck) -match 'triage selfcheck: PASS').Count -eq 1) -and (((& pwsh -NoProfile -File scripts/lessons.ps1 check) -match 'id=9').Count -eq 1))) { exit 1 }"
dod_exit: 0
dod_assert: `triage selfcheck` 打印 ASCII 哨兵 PASS（覆盖新探针 10/11 与改口径后的探针 1/5，含批量窗口两侧边界与主检出 `.review` 取证路径，全部走 hermetic 夹具；`_cards.ps1` 的 BOM 分支由 selftest 闸 10d(BOM/纯函数) 常设守住），且 `lessons.ps1 check` 在**生产路径**上按驻留 id 报出 9——不是按条目报 7，证明新口径不只在夹具里生效
review_gate: codex {verdict:pass}
hygiene: |
  单句变异逐一击杀，判据分类器只认「selfcheck 打出 FAIL **且**命中指定用例编号」（光是红不算，可能红错原因）；
  变异一律植入 scripts/ 的**临时副本**、跑完从源文件重拷还原并核 SHA256 逐字节一致（工作树全程零变异态，L196 的
  硬杀窗口从根上消掉）。枚数与靶：
    ① 封顶退回按 markdown 条目计数              ② 封顶 minor 侧 `-ge` 退化（两侧边界合一）
    ③ 封顶 fail-closed 分支删掉（标题漂移静默判「未超」）  ④ 分节解析吞掉「标题没找到」态（_lessons.ps1）
    ⑤ 守卫判定退回旧式「非 none 即有守卫」（_lessons.ps1）  ⑥ enforced_by 正则退回跨行捕获
    ⑦ 降层不再要求守卫                          ⑧ 晋升不再看 enforced_by
    ⑨ selfcheck 不再注入夹具总账                ⑩ 批量阈值 `-gt` 退化成 `-ge`（off-by-one）
    ⑪ 摘掉主检出 `.review` 取证路径             ⑫ delivery-blocked 不再按 verdict 过滤
    ⑬ 去重的 HashSet.Add 恒真（一份裁决被数两次）⑭ branch 归属判定摘掉（隔壁分支的 block 算到本卡头上）
    ⑮ 文件名归属兜底摘掉（无 branch 字段的旧产物失守）
    ⑯ 去重比较器 `OrdinalIgnoreCase` → `Ordinal`（Windows 侧：同一份裁决被数两次）
    ⑰ 去重比较器写死 `OrdinalIgnoreCase`（**只在大小写敏感 FS 上可判**：Linux 上两份不同裁决被并成一条；
       由 CI 的 ubuntu-latest `core` 分片经闸 12c 覆盖，Windows 本地跑判 SKIP 而非 KILL）
    ⑱ `_cards.ps1` 去掉 `\uFEFF?` 锚点后带 BOM 的卡 front-matter 解析成 null（常设闸 10d(BOM/纯函数)）
doc_sync: CLAUDE.md 计量单位说明与铁律小节 · docs/LESSONS.md 三层表 Tier-1 容量格 + PURIFY 步骤 ·
  .claude/skills/lessons/SKILL.md 三层描述 + PURIFY 步骤 · scripts/_config.ps1 的 LessonsMustCap 定义处注释 ·
  scripts/lessons.ps1 与 scripts/triage.ps1 的头注（子命令说明 / 探针清单）· docs/LOOP-ENGINEERING.md 与
  .claude/skills/triage/SKILL.md 的探针枚举与计数 · docs/DELIVERY-CHAINS.md 心跳行 · docs/HARNESS-REVIEW.md
  「心跳的发现信号」（改列全 10 枚并点明哪 5 枚指回本文）· docs/scaffold-architecture.html 心跳节点与经验系统节点（R5）
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

DoD 是**最小闸**，不是完成定义：完成定义 = front-matter 的 `acceptance` 封闭集合 A1–A16
（清单内每条都有可证伪测试，落点见 `acceptance_notes`；**清单外的发现记 `[FOLLOW-UP]`**）。
其中 A1/A4/A14/A15 由 `pwsh -NoProfile -File scripts\selftest.ps1 -Shard core` 常设看守
（闸 2a / 2d / 10d(接线/review→triage) / 14g①②），其余由 `triage.ps1 selfcheck` 的 hermetic 用例看守。
