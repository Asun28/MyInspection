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
  - 让心跳探针打网络或调 gh（`delivery-blocked` 只读 `.review/*.json`，并以离线 `git rev-parse HEAD` 绑定当前检出；心跳恒离线、退出码恒 0）
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
acceptance_notes: |
  逐条落点（验收即在这些位置可证伪）：
    A1  scripts/selftest.ps1 闸 2a（生产路径，两个期望值各自精确断言、且断言二者不相等）
    A2  scripts/triage.ps1 用例 5（foreach 两侧边界，阈值全部由 $MustCap 算出）
    A3  scripts/triage.ps1 用例 5 的 $bulletCount 前置断言（两侧夹具各跑一次）
    A4  scripts/selftest.ps1 闸 2h（正例仍 PASS + 负例非零且带 [LESSONS-SECTION-NOT-FOUND]）；探针侧 = 用例 5b
    A5  scripts/triage.ps1 用例 6（L901 降层 / L903 不晋升 / L902 none 不降层 / L904 空字段须晋升，四条各一断言）
    A6  同上 L905（TODO，tier must，不得降层）与 L906（N/A，tier ledger，须晋升）+ lessons.ps1 check 的
        Test-ScaffoldLessonEnforcedByWellFormed 闸与其自检探针；**中文取值方向 = 用例 6b**（见下）
    A7  scripts/triage.ps1 用例 7      A8 用例 4      A9 用例 8      A10 用例 9      A12 用例 9b
    A13 scripts/triage.ps1 用例 10(a)(b)(c)          A14 scripts/selftest.ps1 闸 10d(接线/review→triage)
    A15 scripts/selftest.ps1 闸 14g①②                A16 闸 10d(BOM/纯函数)、闸 10d(接线/triage)、闸 12d 拷贝清单
  R3 后续加固（不改上述 pinned acceptance 的语义）：
    - A6 的拒收前缀覆盖完整占位符声明，而不是只拒 TODO/N/A/待补；`TBD scripts/future.ps1` 在 must 侧不得降层，
      `FIXME scripts/future.ps1` 在 ledger 侧必须晋升，且 lessons.ps1 check 的形态自检同步拒绝二者。
    - delivery-blocked 只接纳 `sha` 与**产物所属仓库当前 HEAD**逐字一致的裁决；worktree 与主检出两份都当前时，
      固定按来源优先级选择 worktree，不再用可漂移/可伪造的 LastWriteTimeUtc。用例 9c 覆盖 stale block/current pass、
      stale pass/current block，以及两份都当前时两种相反 verdict（并故意把较新的 mtime 给低优先来源）。
    - 驻留 id 在同一 bullet 或跨 bullet 重复时，以 `[LESSONS-DUPLICATE-RESIDENT-ID]` fail-closed；不得先 Unique
      再计数而把任意多个重复驻留压成一个。用例 5c 的两种 hermetic 夹具均穿过 triage 与 lessons.ps1 check 两个消费者。
  A11 的实现说明：`[IO.Path]::GetFullPath($vf.FullName)` 实测是恒等变换——
    FileInfo.FullName 本就完全限定且已折叠 `.`/`..` 段（`Get-Item` 传入一条含 `..` 段的路径时，它交出的 FullName
    已无 `..`），故「去掉 GetFullPath」这枚变异**不可能**让任何用例变红，A11 那半条无法成立。已按「不留写不出
    变异的守卫」处理：删掉该恒等调用、键改为 $vf.FullName，并把注释里「Windows 路径大小写不敏感」这句普适宣称
    换成按 OS 取比较器的实际规则。A11 的另一半（大小写不同的目录段仍只报 1 条 + 比较器换 Ordinal 即红）由
    用例 9b 满足，并同时用纯候选列表钉住大小写敏感/不敏感两侧的分组结果。
  A6 的**反方向补洞**（R3 再评审 rr127 的唯一 block，已修）：把 A6 从否定清单改成允许清单时，只覆盖了
    ASCII 占位符，允许清单本身却朝反方向过宽——判定核用 .NET 正则，而 .NET 的 `\w` **认 CJK**，于是
    `[\w.-]{2,}[\\/][\w.-]{2,}` 把任何含斜杠的中文短语读成「仓库路径」（人工/评审、手动/人工核验、
    见 PR #183 的讨论/结论）；`闸\s*\S` 又把「闸」后的**任意**字符当闸编号，于是 `无闸门（只能靠人）`
    ——字面就是「没有闸门」——被判成**已有守卫**。总账是中文散文，这两种形态是常态而非边角。后果直达
    探针 10：「某条经验坐在必须层…但机器已在守它：无闸门（只能靠人）」，一边引用「没有闸门」四个字、一边据此
    主张删掉一条本就无守卫的铁律——正是 `_lessons.ps1` 自己注释里点名要防的那种灾难。
    修法：路径分支收成 ASCII `[A-Za-z0-9._-]`，闸分支要求其后是**真编号**（ASCII 字母数字，或本仓在用的
    圈码 `闸⑯`/`gate ⑧`，即 U+2460-U+24FF——整块无表意文字，故收它不会重开中文那个口子）；`gate` 后
    保留 `\s+` 以免 `gateway` 变成闸引用。**实测：真实 LEDGER 全部 195 条 enforced_by 取值改前改后判定
    逐条一致**（133 空 / 36 none / 26 有守卫 / 0 拒绝，改前改后同数、零条裁决翻转），六种中文 fail-open
    形态全部关闭。新增夹具落在 scripts/triage.ps1 用例 6b：L907（无闸门（只能靠人），tier must，不得降层）
    · L908（人工/评审，须晋升）· L909（闸，靠人，须晋升）· L910（gate 讨论，须晋升）· L911（selftest 闸⑯，
    tier ledger 但**有**守卫，不得晋升——钉住「收紧不得连带拒真引用」这一侧）；lessons.ps1 的守卫自检探针
    同步加这五种取值。
  措辞说明：A9/A10/A13 的路径形态已改用中文描述（原先的尖括号记号会触发 check-cards 的占位符启发式），
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
  用例 6b（中文取值方向）另五枚，靶均为 `_lessons.ps1` 守卫判定那一行、每枚只动一处字符类，且**两两独立**
  （任一枚只让它对应的那条用例变红）；分类器要求 **DoD 非零退出 且 命中该用例自己那句失败文案**——只认
  id 会被用例 6b 的条数汇总行冒充（那行同时含 L908/L909/L910 三个 id），首轮就是这么误判的，收紧后重跑：
    ⑲ 路径分支退回 `[\w.-]`（.NET 的 \w 认 CJK）  → L908 未被提名晋升
    ⑳ 闸分支字符类放进「门」                      → L907 被提名降层
    ㉑ 闸分支字符类放进全角逗号                    → L909 未被提名晋升
    ㉒ `\bgate\s+` 之后退回 `\S`                   → L910 未被提名晋升
    ㉓ 闸分支去掉 U+2460-U+24FF 圈码范围           → L911 被提名晋升（收紧过头，真守卫报成没守卫）
    ㉔ 整行退回改前拼法                            → lessons.ps1 check 打出「enforced_by 守卫判定回归」并非零退出
  ⑲-㉔ 6/6 击杀；批次全程只写 `%TEMP%` 副本，跑完核工作树四个被测脚本 SHA-256 与基线逐字节一致（L196）。
  R3 后续加固由常设 selfcheck 直接覆盖：用例 6 的 L914/L915 分别钉住 TBD/FIXME 复合占位符在降层/晋升两侧；
  用例 9c 的 stale/current SHA 交叉夹具在去掉 SHA 匹配守卫时变红，两份 current 的反向 verdict + 反向 mtime
  夹具在选择器退回 LastWriteTimeUtc 时变红；另用临时 git 仓真实执行 `git rev-parse HEAD`，钉住离线读 HEAD 的边界。
  用例 5c 另以同 bullet 重复与跨 bullet 重复两种夹具真跑两个消费者；任一层恢复提前 Unique 都会让 triage 无 finding、
  lessons.ps1 check 缺失稳定哨兵，从而同时变红。
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
- 新增探针 11 `delivery-blocked` —— 唯一读**交付**状态的探针，离线读 `.review/*.json`，并读取本地仓库 HEAD 拒绝陈旧裁决。
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
`delivery-blocked` 不调 gh，因为 `review.ps1` 每次跑都已把归一化裁决写在本地；它只额外执行本地 `git rev-parse HEAD` 来核对裁决 SHA，不访问网络。

## 验收（DoD = 命令 + 退出码 + 断言）

```powershell
pwsh -NoProfile -File scripts\triage.ps1 selfcheck
pwsh -NoProfile -File scripts\lessons.ps1 check
```
- 期望退出码：0
- 断言：`triage selfcheck: PASS` + `lessons.ps1 check` 报驻留 `id=9`
