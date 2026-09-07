---
id: T4-DESIGN-SYMBOL-CHROME-V2
title: 「符号化 chrome」准入条件收口（承接 T4-DESIGN-SYMBOL-CHROME，含相机面决策与逐行对齐）
depends_on: []
parallelizable_with: []
plan_ref: context/DESIGN.md#components
status: in-progress
branch: T4-DESIGN-SYMBOL-CHROME-V2
worktree: C:\wt\T4-DESIGN-SYMBOL-CHROME-V2
allow_paths:
  - context/DESIGN.md
  - docs/UI-UX-ELEMENTS.md
forbid:
  - 弱化「颜色不得是状态的唯一通道」这一 WCAG 1.4.1 下限
  - 删除计数的完整数值或其完整播报，或允许把数值折成纯符号
  - 让任何符号化控件失去无障碍名
  - 改动 design token 取值（colors / spacing / typography / motion / iconography 的值）
  - 新增图标依赖或图标资源
  - 改动任何 Kotlin、Gradle、schema 或任务卡以外的文件
  - 在实例代入表（见「必做前置」）完成之前动手写规则正文
non_goals:
  - 落地任何 Schedule / Capture / Report / Camera 界面代码（各自的 UI 卡拥有）
  - 底部导航「每个目的地同时显示 icon 与 label」这条规则本身（DESIGN.md:922，只作为实例被代入验证，不改写）
acceptance:
  - "A1 DESIGN.md carries one named condition set under which a chrome control may omit visible text, stated once, and every clause in DESIGN.md and UI-UX-ELEMENTS.md that governs a symbol-only control resolves to it by reference rather than restating its conditions"
  - "A2 the card body carries a completed instance-substitution table: every existing instance the rule governs is listed, each is evaluated against the final wording, and each row records compliant / requires-restatement / explicitly-exempt with its reason"
  - "A3 no instance in that table is left indeterminate: no instance satisfies two mutually exclusive branches of the rule, and no instance the document already permits is judged non-compliant by the rule"
  - "A4 the amended count clause still requires every count to carry its numeral and to announce its full value, and no longer forbids a symbol-only badge in terms that contradict the state-badge DOT variant"
  - "A5 the amended status clause still forbids colour from being the sole carrier of state, and states its satisfying carriers rather than requiring a visible text label unconditionally"
  - "A6 the camera surface is decided one way and stated once: either the camera rows carry a tooltip and their anatomy says so, or the tooltip requirement is scoped so those rows satisfy it as written; no camera row is left contradicting the rule"
dod_command: $d=Get-Content -Raw -LiteralPath 'context/DESIGN.md'; $u=Get-Content -Raw -LiteralPath 'docs/UI-UX-ELEMENTS.md'; $checks=@(@('D','### Symbol-only chrome',1),@('D','A chrome control may omit visible text only when all of the following hold',1),@('D','names the action it performs',1),@('D','Domain values are never carried by a glyph alone',1),@('D','Color is never the sole state channel',1),@('D','never the sole state channel',1),@('D','Where the component anatomy declares a tooltip',1),@('D','and its accessible name states that a local-health issue needs attention',1),@('D','carries its numeral and announces its full value',1),@('D','plural-aware',1),@('D','announce the full count',1),@('D','with one named exception, the camera shutter',1),@('D','the owner also expresses the state the badge marks',1),@('D','Do give every status a carrier besides color',1),@('D','or icon-only badges',0),@('D','Every icon-only toolbar/camera action exposes the same verb-object label as accessibility text',0),@('D','Every icon has a visible tooltip and an accessibility label using verb + object',0),@('D','Icon and tooltip use the same declared action; target never shrinks to visible glyph bounds',0),@('D','Pair every status with a label and stable symbol',0),@('D','Do pair every status color with a label and icon',0),@('U','Symbol-only chrome',1),@('U','symbol-only chrome',1),@('U','tooltip',1)); $bad=@(); foreach($c in $checks){ $h=$(if($c[0] -eq 'D'){$d}else{$u}); $n=([regex]::Matches($h,[regex]::Escape($c[1]))).Count; if($n -ne $c[2]){ $bad += ('{0} anchor [{1}] expected {2} found {3}' -f $c[0],$c[1],$c[2],$n) } }; if($bad){ $bad | ForEach-Object { Write-Host $_ }; exit 1 }; exit 0
dod_exit: 0
dod_assert: DESIGN.md declares one named Symbol-only chrome section holding the domain-value carrier rule and the admission conditions, whose accessible-name condition imposes no phrase grammar; the count clause keeps plural-aware phrasing, keeps the numeral and full-value announcement, and drops the unconditional icon-only-badge ban; the status clause carries the color rule as the single occurrence of never the sole state channel and no longer demands a visible label unconditionally; the tooltip requirement is scoped to components whose anatomy declares one and the camera shutter is the one named exception to the icon-never-replaces-label list; the Settings destination announces its actionable local-health state; UI-UX-ELEMENTS.md resolves both its icon-only rule and its theme checklist to that same section and no longer restates the tooltip condition.
review_gate: codex {verdict:pass}
hygiene: 每条断言由「删掉被改写的那一句即变红」的单点变异证明；每个 DoD 锚点在被查文件里只出现一次，否则单点删除杀不掉它；变异批钉生产文件 SHA-256，批中不并行跑第二批或独立复核（L196）。
doc_sync: CLAUDE.md「权威文档」21 行与 TASK-BOARD 记录本卡合并 OID；解锁 T4-SCHEDULE-UI-PRESENTATION。
---

# T4-DESIGN-SYMBOL-CHROME-V2

## 起因：承接被退役的 `T4-DESIGN-SYMBOL-CHROME`（2026-09-06 用户裁定）

前卡跑了 **6 轮 R3、11 条 finding，全部属实**，两次经用户裁定 `-ResetRounds`，仍未收敛。
**11 条里没有一条是实现缺陷**——全是同一类：**新写的中心规则与文档里既有实例不符**，
每轮修完措辞、下一轮就在另一处冒出新缝。用户裁定关闭 PR #236、把整件事折进本卡重做。
经验已入账 **L309**（成熟文档加中心规则须先做实例代入表）。

**前卡的产出不作废**：分支 `T4-DESIGN-SYMBOL-CHROME`（PR #236，已关闭但分支保留）里有一份
经 6 轮打磨的草案，其**条件集骨架与各条款的解析式改写可直接复用**，只有「chrome / domain content
边界」那一段是坏的（见下）。前卡卡片记录了逐轮 finding 与修法，是本卡最有价值的输入。

## 必做前置：实例代入表（写规则正文之前，`forbid` 第 7 条）

**这是本卡与前卡唯一的方法论差别，也是它存在的理由。** 前卡的病根是：规则的每一句声称都在对整份
文档做全称断言，而我只对着「开卡时盘点出的那几处冲突」验证过它。故本卡**先建表、后写规则**：

1. 用**不变量**而非症状词 grep（前卡第 1 轮的教训：搜 `icon-only` / `纯图标` 只找到 4 处，
   真正该找的是「status + label + icon 的合取」「计数 + 播报」「字形 + 无障碍名」这些**不变量**）。
2. 列出**每一个**受该规则管辖的既有实例，逐个代入拟定措辞算一遍：它合规吗？按规则它该长什么样？
   与它自己那一行冲突吗？
3. 冲突的当场消解或**显式豁免并写明理由**；不留「以后再说」。
4. 规则里每出现一次全称词（every / never / all / 一律），回头核一遍该全称在文档里是否真成立。

**表至少须覆盖下列实例**（开卡时重跑 grep 补全，勿照抄本清单当全集）：

| 实例 | 现状要点 | 前卡在这里栽过 |
|---|---|---|
| `:922` `Settings` 未标注错误点 | 记录派生的二元状态，文档明写 `unlabelled` | 是（第 6 轮） |
| `:922` 底部导航 icon+label | 更严的合同，压在准入之上 | — |
| `:922` `Schedule` 计数徽标 `1`–`99+` | 计数 + 视觉截断 | — |
| `state-badge` `COUNT / DOT / STATUS / SOURCE` | `announce the full count`；`dots require an owning row label`；`Merged into owner` | 是（第 2/3/6 轮） |
| 计数条款（产品语言合同） | 复数短语 + 数值 + 全额播报 | 是（第 5 轮：播报保证被缩到 `state-badge`） |
| `camera-control` | 24dp 图标，anatomy **无 tooltip**；语义含 current state | 是（第 3/4 轮） |
| `camera-shutter` | 72dp 圆，无障碍名 `Take photo`，**无 tooltip** | 是（第 3/4 轮） |
| `camera-overlay-control` | `Switch` + slider | 是（第 4 轮） |
| `icon-button` | anatomy **含** tooltip；变体含 `CAMERA` | 是（第 4 轮：`CAMERA` 变体被读成替相机组件背书） |
| `tooltip` 行 | 曾三度复述条件正文 | 是（第 2/3/4 轮） |
| `:1414` Colors 章状态句 | 原文「Pair every status with a label and stable symbol」+ 符号词汇表 | 是（第 1 轮漏掉） |
| `:1826` Do's and Don'ts 状态句 | 颜色不得唯一承载状态 | — |
| `:1756` 「图标不得替代这些动作的标签」 | capture/compliance/privacy/delete/finalize/backup/restore | 是（第 3 轮：原样搬进规则 ⇒ 与 `camera-shutter` 抵触） |
| `:1707` 房间进度段「不得缩成无标签的点」 | 更严的合同 | — |
| `notice-delivery-row` | `Status is text + icon` | — |
| `status-choice` / `privacy-chip` / `privacy-action` | 有可见标签；无障碍名含当前状态 | 是（第 4 轮：条件 3 与「名字里带状态」抵触） |
| `metadata-row` / `summary-stat` | 不得单以颜色编码状态 | — |
| `docs/UI-UX-ELEMENTS.md:35` 纯图标操作 | 已给准入条件（tooltip + 无障碍名） | — |
| `docs/UI-UX-ELEMENTS.md:122` 主题清单第 5 条 | 原文「仍有文本和图标，不靠颜色」 | 是（第 1 轮漏掉） |

## 已知陷阱（前卡 11 条 finding 的浓缩，勿重犯）

1. **边界判据别自造**。前卡第 4 轮我发明了「取值读数是否超过两种」的基数判据，第 6 轮就被证伪：
   一个**记录派生的二元状态**同时满足「领域内容保留文字」与「二元标记可为字形」两边 ⇒ 规则不可判定。
2. **别把某处的清单原样搬进中心规则**。第 3 轮把 `:1756` 的动作清单搬进准入段，于是规则自称
   「capture 动作保留可见标签」，而 `camera-shutter` 正是纯符号的 capture 控件。
3. **延期必须显式豁免**。第 4 轮：相机面已进 `non_goals`，但条件 3 仍然绑着它们——
   一边说「不管」一边判它们不合规。延期只有在规则点名豁免时才自洽。
4. **矩阵行只许引用、不许复述**。`tooltip` / `state-badge` 两行连着三轮因复述条件正文被拦。
   判据：一行只有在**复述了准入条件的正文**时才须改；组件**特有**事实（角色、焦点键、live region、
   具体标签值）一律保留。
5. **收窄一句声称，就回头查所有引用该声称的句子**。ship 前自审抓到过一次：加了相机豁免段之后，
   「所有条款都解析到本集」那句立刻自相矛盾。
6. **全称词是风险点**。every / never / all 每出现一次，就是一次对整份文档的断言。

## 诊断与建议正解（前卡第 6 轮留下，**本卡须先验证再采用**）

文档里真正成立的规则**不是基数，而是载体**：`:922` 的错误点之所以可以无标签，是因为它
`Merged into owner`、拥有者本身有可见标签；`state-badge` 的「dots require an owning row label」
是同一件事。故拟定正解：

> **领域值永不由字形单独承载；字形可以额外标记它，只要拥有者以文字或播报给出该值。**

一条规则同时覆盖计数、巡检项状态、`Settings` 错误点与 `DOT`，且删掉「基数」这个自造判据。

> **未验证的一步（务必先查）**：该正解要求「拥有者以文字或播报给出该值」。文档目前**没有明说**
> `Settings` 目的地的无障碍名会陈述本机健康状态——它的可见标签只是 `Settings`。若确实没有，
> 本卡要么补上这一句（属本卡范围），要么换措辞。**这正是实例代入表该抓出来的东西，别跳过。**

> **评审者在前卡给的备选（「所有记录派生值一律保留可见文字」）在本文档里不成立**：
> 它会判 `:922` 的 unlabelled error dot 违规。已实测该行未被前卡 diff 触碰、原文确写 `unlabelled`。

## DoD 锚点设计规矩（前卡验证有效，沿用）

- 锚点一律 **ASCII**（L165）；**每个锚点在被查文件里只许出现一次**，否则「删掉被改写的那一句」
  杀不掉它、变异必然存活。前卡靠这条把 9 枚变异全部做成可击杀。
- 大小写敏感可用来给两个形态各留一个坑位（前卡：标题形态 `Symbol-only chrome` 在 DESIGN.md 只出现在
  标题上，正文与跨文档引用各用不同形态）。**但这属于权宜**：本卡若能用天然唯一的整句作锚点更好。
- 正锚点断言「解析式措辞在」，负锚点断言「被替换的复述措辞不在」，两侧都要有。
- 收紧 `dod_command` 只许加、不许减；每次加完实测「master 基线仍 RED、本卡工作树 GREEN」。

## 未决决策（2026-09-07 用户裁定，均已收口）

| ID | 裁定 | 落地位置 |
|---|---|---|
| OD-1 | 选项 (b)：tooltip 要求按组件 anatomy 是否声明而绑定。相机行 anatomy 一字未改，按新措辞天然满足条件 3 | 条件 3 第二句 + `tooltip` 行改为引用 |
| OD-2 | 选项 (a)：`Settings` 目的地在无障碍名中陈述可行动的本机健康状态 | `:926`（本地行号）Settings 徽标句 |

OD-2 的事实由下面的实例代入表查实后才提交裁决：基线文档里 `navigation-destination` 只暴露
label 与 selected，`state-badge` 说 dot「Merged into owner」且「never the sole state channel」，
而 owner 的可见标签只有 `Settings`——**该状态今天没有任何通道播报**，`state-badge` 行的自我承诺
当场就不成立。故 (a) 不是为迁就新规则而加的一句，而是补上文档自己已经承诺、却没有兑现的那一环。

## 实例代入表（`forbid` 第 7 条的必做前置 · 按最终措辞逐个代入）

**grep 用的是不变量不是症状词**：`tooltip` / `accessible name` / `label`（全量）/ `count`+`announce` /
`icon-only`+`symbol`+`glyph`+`dot` / `color alone`+`sole state` / `unlabelled`+`decorative`+`merged`。
**前卡引用的行号已随本地 `context/DESIGN.md` 演进失效（该文件现 1841 行），本表行号全部本地重取。**

最终措辞把受管辖对象分成互斥的两支：**chrome 控件**（意义即它执行的动作）走准入条件；
**领域值**（记录自身持有的：计数、状态、日期、地址、房间/物业名）走载体规则。
一个实例只会落进一支——控件自身的开关态（如 flash on）不是记录持有的值，故相机控件只在准入支；
`evidence-rail` 不是控件（无目标、无动作），故只在载体支；`status-choice` 带可见标签，
落在载体支且由文字满足，不触发准入条件。**这消掉了前卡第 6 轮那个「二元记录态两栖」的不可判定点。**

### 甲 · 省略可见文字的 chrome 控件（准入条件支）

| 实例 | 基线事实 | 代入最终措辞 | 判定 |
|---|---|---|---|
| `icon-button` `:1589` | 24dp symbol、opaque 48dp target、anatomy **含** tooltip、accessible name 强制 | 1 字形声明于组件合同 ✓ 2 名强制 ✓ 3 anatomy 声明 tooltip ✓ 4 目标不缩到字形 ✓ | compliant；行内复述改为引用，并写明 `CAMERA` 变体不外扩准入 |
| 顶栏尾部图标 `:894` 第 5 条 | 至多两枚；每枚有可见 tooltip 与 verb+object 无障碍名 | 同上；bar 图标 anatomy 声明 tooltip ✓ | compliant；该条复述了条件正文，改为引用 |
| `camera-control` `:1614` | 24dp icon、opaque 48dp target、anatomy **无** tooltip；语义为「Label describes action and current state」 | 3 由 OD-1 明写「anatomy 未声明者按原文即满足」✓ 2 明许名字兼带当前状态 ✓ 5 OFF/ON 播报 ✓ | compliant，**anatomy 不改** |
| `camera-shutter` `:1615` | 72dp 圆 + inner state mark、无障碍名 `Take photo`、anatomy **无** tooltip | 1 说「字形由组件合同声明」而非「取自图标集」，故自定义圆形合格 ✓ 2 verb-object ✓ 3 同上 ✓ 4 72dp ✓ 5 状态播报一次 ✓ | compliant；并在 `:1748` 具名豁免（见丙） |
| `overflow-menu` 触发器 `:1643` | 纯图标；文档已声明其播报 `More options` | **首版规则判它不合规**——条件 2 曾要求 verb-object 短语，而 `More options` 不是 | 由 R3 第 1 轮修法消解：条件 2 改为「名字须命名该动作」，不设短语语法 |
| `STREAM_CAPTURE` 的 Back `:880` | 纯图标；无障碍标签 `Save and exit` | 同上，`Save and exit` 无宾语 | 同上 |
| `search-field` 的 Clear / `input-field` 尾部动作 / sheet 与 dialog 的 Close / `room-progress-strip` 的 previous-next | 均为 `icon-button` 实例 | 由 `icon-button` 一行统一承载 | compliant（不单独立规则） |

### 乙 · 由字形承载领域值（载体规则支）

| 实例 | 基线事实 | 代入最终措辞 | 判定 |
|---|---|---|---|
| `state-badge` `COUNT`（`Schedule` `1`–`99+`，`:926`） | 数值可见、视觉截断到 `99+`、播报全额 | 数值即文字，owner 亦播报全额 ✓ | compliant |
| `state-badge` `DOT`（`Settings` 未标注错误点，`:926`） | 文档明写 `unlabelled`；owner 可见标签只有 `Settings` | **基线下不合规**：无任何通道给出该状态 | **由 OD-2 消解**：Settings 无障碍名陈述该状态后 compliant。未弱化规则去迁就它 |
| `state-badge` `STATUS` / `SOURCE` | Merged into owner | owner 表达其所标记的状态 ✓ | compliant；该行语义格改为「the owner also expresses the state the badge marks」 |
| `evidence-rail` `:1591`、`:1720` 段 | 三段字形承载 status/photo/note；子段对 TalkBack 隐藏；整条并作一句 `Status complete, photo missing, note complete` | owner 以播报给出该值 ✓ | compliant。**它正是载体规则成立的证据**——前卡的「基数」判据判不了它 |
| `room-progress-segment` `:1576` | Room label + completion count + state mark | 文字在 ✓ | compliant；另受 `:1714` 更严合同约束（见丙） |
| `missing-evidence-strip` `:1577` | icon + exact count copy | 计数在文案里 ✓ | compliant |
| `summary-stat` `:1670` / `save-status` `:1603` / `notice-delivery-row` `:1688` / `compliance-check-row` / `health-issue-row` / `review-gap-row` / `task-stepper` 当前标记 | 均带可见标签或文字 | ✓ | compliant |
| `metadata-row` `:1626` | 明写「never ... encodes state by color alone」 | 与载体规则同向 ✓ | compliant，不改 |
| `status-choice` `:1592` / `privacy-chip` `:1596` / `privacy-action` `:1597` / `camera-overlay-control` `:1616` | 均有可见标签或状态文字 | 由文字满足载体规则；不省略可见文字故不触发准入条件 | compliant。**前卡第 4 轮「条件 3 与名字带状态抵触」在此不复存在** |
| `inspection-item-card` `NOT_APPLICABLE` `:1734` | `Dash, explicit label, Change` | 字形 + 显式标签 ✓ | compliant |

### 丙 · 更严合同：准入不覆盖，显式点名

| 实例 | 基线事实 | 处置 |
|---|---|---|
| 底部导航目的地 `:926` | 「Every destination always displays icon and label」 | 不改；准入段点名其保留可见标签（`non_goals` 已排除改写这条本身） |
| 房间进度段 `:1714` | 「do not reduce rooms to unlabeled dots」 | 不改；准入段点名 |
| `docs/UI-UX-ELEMENTS.md:35` 选型句 | 原文「纯图标操作必须使用 `icon-button`」 | **与相机面抵触**：`camera-control` / `camera-shutter` 是各自独立的组件合同（后者 base 是 Custom `Button`），且本卡刚写明 `icon-button` 的 `CAMERA` 变体不外扩准入。按 R3 第 1 轮修法把选型收窄到普通界面，并把三者的准入统一路由到本节 |
| `:1748` 图标不得替代标签的动作清单 | capture/compliance/privacy/delete/finalize/backup/restore | **基线自相矛盾**：`camera-shutter` 就是纯符号的 capture 控件。按陷阱 2 **不把该清单搬进中心规则**，只在原句加一处具名例外（camera shutter），准入段以「the actions named under Buttons and selection controls」引用之，例外随引用一起旅行 |

### 丁 · 不受本规则管辖（判定确定，非「以后再说」）

| 实例 | 理由 |
|---|---|
| `divider` `:1579` | 装饰性、对无障碍隐藏；`:1274` 明写「Carries no state, grouping, or focus meaning」 |
| `focus-indicator` `:1607` | 纯视觉，平台焦点为权威；不承载动作也不承载记录值 |
| `loading-indicator` `:1657` | 既非动作也非记录值；其播报义务由该行自己规定 |
| 无障碍合同 `:1809` | 规定播报义务，未复述准入条件；与条件 5 同向，不改 |
| 无障碍合同 `:1808` | 「Decorative rails and dividers have no separate content description」——rail 无**单独**描述是因为并入卡片，而卡片确实描述了它；不矛盾，不改 |

### 全称词复核（`必做前置` 第 4 步）

最终措辞里的全称共四处，逐处回核文档是否真成立：
`Domain values are never carried by a glyph alone`（乙表全部实例已代入）·
`A chrome control may omit visible text only when all of the following hold`（甲表全部实例已代入）·
`Color is never the sole state channel`（乙表 + 丙表；`:1420` 与 `:1818` 两处状态条款已按 A5 改写）·
`Admission never overrides a stricter component contract`（丙表三条即其全集，逐条点名）。

## 落地形态（2026-09-07）

`context/DESIGN.md` 新增 `### Symbol-only chrome`，置于 `Component contract schema` 之后、
各组件矩阵之前。该节先划 chrome / 领域值的界并给出**载体规则**（领域值永不由字形单独承载；
字形可额外标记，只要 owner 以文字给出或播报该值），再列 5 条准入条件，最后写明准入不覆盖更严合同
并点名三处。四处治「符号化控件」的条款改为解析到该节：`:894` 第 5 条 · `icon-button` 行 ·
`tooltip` 行 · `docs/UI-UX-ELEMENTS.md:35`；`UI-UX-ELEMENTS.md:123` 的主题清单第 5 条同并入。
计数条款与两处状态条款按 A4/A5 重写，`:1748` 加一处具名例外，`:926` 按 OD-2 补播报。

**四处判断记账**

1. **`never the sole state channel` 归位到状态条款**。该串原在 `state-badge` 行、主语是徽标；
   A5 要的是**状态条款**承载「颜色不得是状态唯一通道」。同一串给两个主语会让 A5 无可击杀变异，
   故移至 `:1420` 并以 `Color is never the sole state channel` 作唯一锚点；`state-badge` 行改写为
   `the owner also expresses the state the badge marks`（同义且更具体，非弱化），
   其 `announce the full count` 与 `dots require an owning row label` 一字未动。
   M4/M5/M12 分别证明这三件事各自在测。
2. **条件 1 写「字形由组件合同声明」而非「取自既有 iconography」**。后者会判 `camera-shutter`
   不合规——它的字形是自定义 72dp 圆而非 Material 符号。这正是「全称词先回核」抓到的一处。
3. **准入段引用 `:1748` 而不复制其清单**（陷阱 2）。复制会让中心规则自称「capture 动作保留可见标签」，
   与 `camera-shutter` 直接抵触；改为引用后，加在 `:1748` 上的具名例外随引用一起生效。

4. **ship 前自审抓到三处「解析到错误的支」，全部当场改**（陷阱 5：收窄一句声称就回头查所有引用它的句子）。
   状态条款尾句与 Do 条原本写作「其**控件**由 symbol-only chrome 准入」——但 `Settings` 的健康点
   **不是控件**（它是徽标，其 owner 目的地带可见标签、并不省略文字），于是我自己的规则会判这个
   文档明确允许的实例不合规（A3 第二半）。二者改为解析到**本节**（内含载体规则与准入条件两支），
   两支各自接住 dot 与相机控件。第三处：准入段原称三类更严合同「keep their visible labels」，
   而 `:1748` 自己带着相机例外——补「apart from the one exception that clause itself names」。
   **这三处改动作废了首批 22 枚变异收据（收据钉生产文件 SHA-256，L270），已按新字节整批重跑，仍 22/22。**

## R4 变异收据（2026-09-07 · R3 第 2 轮 + 本地对抗复核修复后重跑 · 23/23 KILLED）

被测基线 SHA-256（收据钉这两个文件的确切字节，此后任何改动即作废本批）：
`context/DESIGN.md` = `FA59ACA33B7BAD6E9CE005DF3E1E8FFB31DC6CF08C5A9FCBA1FD41EEC4F1B836` ·
`docs/UI-UX-ELEMENTS.md` = `F1364E4823FA7E3ED41CCC8E7F5C4D51FE27E4822EC1EEF6E60AF4CDBF497BA8`。
基线 `dod_command` 退出 **0**（GREEN）；23 枚跑完后两文件 SHA 逐一回到上列基线、退出仍 0。
每枚植入前断言靶串在文件内**恰好出现 1 次**（不符即作废该枚，L190）、断言渲染后文本与 SHA 均已改变
（防 no-op 冒充击杀，L297）、断言植入前文件仍等于基线（L196）。**无一枚 VOID。**

RED 证据取自真实基线：`-Phase red` 首次运行读到的是主检出卡片里尚未定稿的 `TBD` 字面量，
非零退出由 PowerShell 解析错误产生、**不构成 RED 证据**（L282）；卡片 `dod_command` 定稿后重跑，
19 条锚点逐条点名失败、3 条「保留项」锚点在基线即为绿，才是真 RED。

| # | 断言 | 文件 | 单点变异 | 判定 |
|---|---|---|---|---|
| M1 | A1 | DESIGN | 改掉声明具名条件集的标题行形态 | KILLED |
| M2 | A1 | DESIGN | 准入开场句去掉 only-when 限定 | KILLED |
| M3 | A2 | DESIGN | 载体规则 never → not | KILLED |
| M4 | A5 | DESIGN | 状态条款 sole → only | KILLED |
| M5 | A5 | DESIGN | 把该串再加回 `state-badge` 行（令其不再单点） | KILLED |
| M6 | A6 | DESIGN | 条件 3 去掉 component-anatomy 限定 | KILLED |
| M7 | OD-2 | DESIGN | 删掉 Settings 的健康状态播报 | KILLED |
| M8 | A4 | DESIGN | 计数保证 its → the | KILLED |
| M9 | A4 | DESIGN | 计数条款去掉 `plural-aware` | KILLED |
| M10 | A4 | DESIGN | `state-badge` 去掉 `full` | KILLED |
| M11 | A6 | DESIGN | 删掉 `:1748` 的具名相机例外 | KILLED |
| M12 | A1 | DESIGN | `state-badge` 去掉 owner 表达义务 | KILLED |
| M13 | A5 | DESIGN | Do 条 besides → beyond | KILLED |
| M14 | A4 | DESIGN | 还原无条件禁令 `or icon-only badges` | KILLED |
| M15 | A6 | DESIGN | 还原断言相机动作带 tooltip 的旧 `tooltip` 行 | KILLED |
| M16 | A1 | DESIGN | 还原 `:894` 第 5 条的条件复述 | KILLED |
| M17 | A1 | DESIGN | 还原 `icon-button` 行的条件复述 | KILLED |
| M18 | A5 | DESIGN | 还原「Pair every status with a label」无条件要求 | KILLED |
| M19 | A5 | DESIGN | 还原旧 Do 条 | KILLED |
| M20 | A1 | ELEMENTS | `:35` 去掉跨文档具名锚点 | KILLED |
| M21 | A1 | ELEMENTS | `:123` 去掉解析引用 | KILLED |
| M22 | A1 | ELEMENTS | 还原 `:35` 未按相机合同收窄的旧选型句 | KILLED |
| M23 | A3 | DESIGN | 还原「无障碍名须为 verb-object 短语」这条全称（`More options` / `Save and exit` 正违反它） | KILLED |

M1/M20/M21 证明具名锚点由定义处与两处跨文档引用各自独占承载；M14/M15/M16/M17/M18/M19/M22
证明七处被替换的复述措辞两侧都在测；M5 与 M12 成对证明「唯一承载」而非「存在即可」。
变异脚本不入库（跑在 scratchpad，不属 `allow_paths`）。


## R3 第 1 轮（2026-09-07 · block · 3 条 finding 全部属实、全部当场修）

三条同属一类，也正是退役卡的病根：**中心规则里的全称词，被文档既有实例证伪**。
我的实例代入表按组件行代入过，却漏了两层——**具体已声明的标签取值**，以及**选型类条款**。

| # | finding | 属实性 | 修法 |
|---|---|---|---|
| 1 | 条件 2 要求 verb-object 短语，而 `More options`（`:1643`）与 `Save and exit`（`:880`）都不是 | 属实。二者在**基线里就已违反** `:894` 原有的「verb + object」措辞，非本卡引入 | 条件 2 改为「名字须命名它执行的动作」并明写不设短语语法；`tooltip` 行同步去掉 verb-object 字样。新增 DoD 锚点 `names the action it performs` + M23 钉住 |
| 2 | `UI-UX-ELEMENTS.md:35` 要求纯图标操作一律用 `icon-button`，而相机两个组件是独立合同、`camera-shutter` 的 base 是 Custom `Button`，且本卡刚写明 `CAMERA` 变体不外扩准入 | 属实，且是本卡自己制造的新缝：写下「变体不外扩」的同时没回头看选型句 | `:35` 把选型收窄到普通界面，点名相机两个合同不套用，三者准入统一路由到本节 |
| 3 | 状态条款写「glyph、position 与 text 三者**together**」，与载体规则允许的「owner 以文字**或播报**给出」及 Do 条允许的「载体是字形而非文字」互相矛盾，令 `Settings` 点与 evidence rail 不可判定 | 属实。ship 前自审已改掉尾句与 Do 条两处，**却漏了主句里的合取** | 主句改为列举允许的载体：字形 + 位置，加上「自己的可见标签 **或** owner 给出的文字或播报」，并显式解析到载体规则 |

**教训（比三条 finding 本身更值钱）**：实例代入表若只代入到**组件行**，会漏掉两类实例——
① 文档在别处**已声明的具体取值**（一个无障碍名、一个标签字面量）；
② **选型/归属类条款**（「必须用哪个组件」），它同样在管辖符号化控件。
下次建表时这两层要单独扫一遍：grep 已声明的标签字面量，grep「必须使用 / 一律用 / 归 X 承载」这类选型措辞。

## R3 第 2 轮 + 本地对抗复核（2026-09-07）

### R3 第 2 轮（block · 1 条 finding · 属实）

状态条款仍要求**每个状态都有字形**，而九个合同声明状态时没有强制字形
（`metadata-row` 与 `summary-stat` 的图标原文就写着 optional，另有 `property-summary-card`、
`result-list-row`、`verification-receipt`、`history-evidence-strip`、`destination-row`、
`health-issue-row`、`compliance-check-row`）。**这条 finding 由第 1 轮的修法引入**。

**该条款至此已被两种不同形态各证伪一次**（第 1 轮：合取里要求文字；第 2 轮：全称要求字形）——
按 L309 自己的识别信号，**停手别补第三次措辞**，改为把 17 个承载状态的合同逐个列出、
从实例反推出析取式，而不是再猜一句。

### 本地对抗复核（fresh-context 子代理，L205；不烧 R3 轮次）

第 2 轮修法落地后**先在本地跑一次对抗复核**再 ship。两轮复核共出 9 条，7 条属实：

| 条 | 内容 | 处置 |
|---|---|---|
| 1 | `room-progress-segment` 的 `BLOCKED` 只由 state mark 字形承载，而 owner 的播报串只给 label/计数/current | 属实（与 Settings 点同类的基线缺口）。`room-progress-strip` 播报补 blocked |
| 2 | 条件 2 指向「文档已声明的那些名字」，而 capture 的 Back 有 `Save and exit`(`:880`) 与 `Back to {parent}`(`:1590`) 两个声明 | 属实。删掉该指代句；**两名之争属基线既有矛盾、非本卡引入**，且被替换的旧 `icon-button` 行本就带同一依赖 → 记 `[FOLLOW-UP]`，不在本卡收口 |
| 3 | 状态条款把「a declared glyph」列为独立充分通道，而同一 diff 的载体规则写着「领域值永不由字形单独承载」——**两条新句自相矛盾** | 属实且最重。改为「颜色不是唯一通道，且状态不由字形单独承载」，通道列表去掉裸字形 |
| 4 | `camera-control` 等行仍在复述条件 2/5，而新句声称「一律解析、不复述」 | 属实。声称收窄为「就这些条件解析到本节，组件行仍陈述自己特有的事实」 |
| 5 | 称 `camera-control` 的 `IMPORT` 变体使「唯一具名例外」少算 | **驳回**：`:1748` 的七个词是 capture/compliance/privacy/delete/finalize/backup/restore，import 不在其中，且文档通篇把 Camera 与 Imported 当两种来源（`:1133` `:1585` `:1688` `:1774`）。复核者查证后撤回该条 |
| 6 | 更严合同的三例读起来像封闭全集，而另有四个合同独立强制可见文字 | 属实。改成「规则 + 举例」，并在第二轮复核指出**规则只护「组件行」而 `:1764` 是散文**后再放宽为「组件行或其他条款」 |
| 7 | `:691` 原句只许 icon 或 label 作第二通道，而 rail 的实际规格是颜色 + owner 播报 | 属实。首轮改成指向本节，**第二轮复核指出这个指向落空**（rail 不是控件，三个段态也无字形，真正管它的是 `:1420`）→ 最终只保留「不由颜色单独承载」，删掉空指针 |
| 8 | `history-evidence-strip` 的 previous/baseline 标记是记录持有的值，而「announces relation」只作用于**被选中**的那条 | 属实（两轮复核都未先发现，第二轮才抓到）。改为每条都播报其 relation |
| 9 | `summary-stat` 图标 optional 且合并短语未声明含状态，图标缺席时只剩颜色 | 属实。合并短语改为含「任何已表达的状态」，与 Settings 点、blocked 房间同法收口 |

**复核者最终覆盖**：五条准入条件 × 6 个控件族；载体规则 × 14 个合同；解析声称 × 9 处条款；
更严合同 × 8 行；计数保证 × 5 处；唯一例外 × 9 个候选。末轮除两条已记 `[FOLLOW-UP]` 的
基线残留（capture Back 双名、`summary-stat` 已修）外无剩余证伪者。

### 方法论补记（本卡premise 的两处缺口，比 finding 本身更值钱）

L309 说「给成熟文档加中心规则前先做实例代入表」。本卡照做了，仍连吃 10 条。病因有二，
**都不是实例代入表能覆盖的**：

1. **只把中心规则代入了实例，没把被改写的周边条款也代入**。状态条款、`:1748`、`:691`、
   更严合同那句，每一句都自带全称词，它们同样在对整份文档做断言。
2. **实例代入表比对的是「新句 vs 旧实例」，管不了「新句 vs 新句」**。第 3 条（裸字形通道
   与载体规则互相矛盾）就是两条新句打架，表里任何一行都发现不了它。

故本卡把方法补成三步：**建表 → 逐句代入（含被改写的周边条款）→ 新句之间交叉核对**，
并在 ship 前跑一次 fresh-context 对抗复核（L205）——本卡两轮复核抓到 7 条真缺陷，
**全部在本地免费循环里吃掉，没有烧掉任何一轮 R3**。

### 遗留 `[FOLLOW-UP]`（不在本卡收口，理由见上表第 2 条）

`STREAM_CAPTURE` 的 Back 同时被 `:880` 的 `Save and exit` 与 `:1590` 的 `Back to {parent}`
声明，条件 3「tooltip 携同一短语」因此没有唯一指代。该矛盾在基线即存在，且被本卡替换的
旧 `icon-button` 行本就带同一依赖；收口它属于 Back 标签合同，超出本卡范围。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-06 | 建卡：承接被退役的 `T4-DESIGN-SYMBOL-CHROME`（6 轮 R3 / 11 条 finding 全属实但未收敛，用户裁定关 PR #236 重做）。合并原 `T4-DESIGN-SYMBOL-COMPONENT-ROWS` 的相机面与逐行对齐范围（该卡同日撤销，未曾开工）。新增「必做前置：实例代入表」为本卡与前卡的唯一方法论差别（L309）。 |
| 2026-09-07 | 实现：新增 `Symbol-only chrome` 具名节（载体规则 + 5 条准入 + 三处更严合同点名）；四处条款改为解析到它，计数/两处状态条款按 A4/A5 重写，`:1748` 加具名相机例外，`:926` 按 OD-2 补播报。OD-1 取 (b)、OD-2 取 (a)（用户裁定）。先建实例代入表后写规则正文（`forbid` 第 7 条）；R4 22/22 击杀。 |
| 2026-09-07 | R3 第 2 轮 1 条 finding 属实（状态条款仍全称要求字形，九个合同证伪）；按 L309 识别信号停手，从 17 个承载状态的合同反推析取式。随后本地跑两轮 fresh-context 对抗复核，9 条中 7 条属实全部当场修、1 条驳回、1 条记 FOLLOW-UP，未烧 R3 轮次。补记方法论两处缺口：周边被改写条款同样要代入，且实例代入表管不了新句与新句相互矛盾。R4 按最终字节重跑 23/23。 |
