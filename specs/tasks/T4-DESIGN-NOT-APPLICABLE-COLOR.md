---
id: T4-DESIGN-NOT-APPLICABLE-COLOR
title: NOT_APPLICABLE 的破折号没有声明前景色：evidence-rail 五个段态只有四个色
depends_on: [T4-DESIGN-STATUS-CARRIERS]
parallelizable_with: []
plan_ref: context/DESIGN.md#colors
status: todo
branch: T4-DESIGN-NOT-APPLICABLE-COLOR
worktree: C:\wt\T4-DESIGN-NOT-APPLICABLE-COLOR
allow_paths:
  - context/DESIGN.md
forbid:
  - 改动 design token 的色值，或新增一个调色板 role
  - 改动 `Symbol-only chrome` 节的准入条件与载体规则（前前卡已验收）
  - 改动 `State glyph contrast map` 已登记的 20 个 状态色 × 内容底 配对或其实测比值（前卡已验收）
  - 弱化「颜色不得是状态的唯一通道」这一 WCAG 1.4.1 下限
non_goals:
  - 落地任何界面代码（各 UI 卡拥有）
  - 给 `status-choice` / `compliance-check-row` 的各变体补色（它们**所有**变体都不声明颜色，NOT_APPLICABLE 在那里并不特殊，属另一类问题）
  - 重开 `OPTIONAL` 与 `NOT_APPLICABLE` 是否应当同色这一设计判断以外的任何 rail 语义
acceptance:
  - "A1 every state that `evidence-rail` declares in `segmentStates` resolves to exactly one declared color, so the component no longer names five segment states and four segment colors"
  - "A2 the not-applicable dash the status clause makes mandatory has one declared foreground stated once, and that foreground is one the contrast gate already registers on every content surface, so the amendment introduces no new pair"
  - "A3 the rail prose and the component row agree with the frontmatter on which states share a color, with no state left described only by a word that is not one of its declared state names"
dod_command: $d=Get-Content -Raw -LiteralPath 'context/DESIGN.md'; $bad=@(); foreach($c in @(@('    notApplicableColor: "{colors.outline}"',1),@('    optionalColor: "{colors.outline}"',1),@('complete, missing-required, blocked, optional and not-applicable segments, the last two sharing one role',1),@('complete, missing-required, blocked and optional segments',0),@('optional and not-applicable share one declared segment color',1),@('optional and not-applicable segments share one neutral, outline, and each carries a dash',1),@('irrelevant',0),@('essential card boundaries, optional and not-applicable evidence segments, and focus use `outline`',1),@('essential card boundaries and focus use `outline`',0),@('essential card boundaries, evidence segments, and focus use `outline`',0),@('"usage":"state-icon"',26))){ $n=([regex]::Matches($d,[regex]::Escape($c[0]))).Count; if($n -ne $c[1]){ $bad+=('anchor [{0}] expected {1} found {2}' -f $c[0],$c[1],$n) } }; $tok=@{}; $pfx=''; foreach($line in ($d -split '\r?\n')){ if($line -eq 'colors:'){$pfx='light'} elseif($line -eq 'dark-colors:'){$pfx='dark'} elseif($line -match '^[a-z]'){$pfx=''} elseif($pfx -and $line -match '^  ([a-z-]+): "(#[0-9A-Fa-f]{6})"\s*$'){ $tok["$pfx.$($Matches[1])"]=$Matches[2] } }; if($tok.Count -ne 56){ $bad+="token map has $($tok.Count) entries, expected 56" }; $lum={ param($hex) $acc=0.0; $wt=@(0.2126,0.7152,0.0722); for($k=0;$k -lt 3;$k++){ $ch=[Convert]::ToInt32($hex.Substring(1+2*$k,2),16)/255.0; $acc+=$wt[$k]*$(if($ch -le 0.04045){$ch/12.92}else{[Math]::Pow((($ch+0.055)/1.055),2.4)}) }; $acc }; $ratio={ param($fg,$bg) $x=(& $lum $fg); $y=(& $lum $bg); ([Math]::Max($x,$y)+0.05)/([Math]::Min($x,$y)+0.05) }; $seen=@{}; foreach($mm in [regex]::Matches($d,'\{"foreground":"([a-z.-]+)","value":"(#[0-9A-Fa-f]{6})","background":"([a-z.-]+)","backgroundValue":"(#[0-9A-Fa-f]{6})","usage":"[a-z-]+","minRatio":([0-9.]+),"essential":(?:true|false)\}')){ $fk=$mm.Groups[1].Value; $fv=$mm.Groups[2].Value; $bk=$mm.Groups[3].Value; $bv=$mm.Groups[4].Value; $mr=[double]$mm.Groups[5].Value; if($seen.ContainsKey("$fk|$bk")){ $bad+="duplicate binding $fk on $bk" }; $seen["$fk|$bk"]=$true; if($tok.ContainsKey($fk) -and $tok[$fk] -ne $fv){ $bad+="binding value drifts from token $fk" }; if($tok.ContainsKey($bk) -and $tok[$bk] -ne $bv){ $bad+="binding value drifts from token $bk" }; if(((& $ratio $fv $bv)+0.0001) -lt $mr){ $bad+="ratio below minRatio: $fk on $bk" } }; if($seen.Count -ne 75){ $bad+="binding count is $($seen.Count), expected 75" }; foreach($th in @('light','dark')){ foreach($sc in @('primary','tertiary','error','outline','privacy')){ foreach($gr in @('surface','surface-container-low','surface-container','surface-container-high')){ if(-not $seen.ContainsKey("$th.$sc|$th.$gr")){ $bad+="state glyph pair unregistered: $th.$sc on $th.$gr" } } } }; $rows=[regex]::Matches($d,'(?m)^\| `([a-z-]+)` \| `([a-z-]+)` \| `([0-9.]+):1` \| `([0-9.]+):1` \|\r?$'); if($rows.Count -ne 13){ $bad+="state glyph map has $($rows.Count) rows, expected 13" }; foreach($mm in $rows){ $sc=$mm.Groups[1].Value; $gr=$mm.Groups[2].Value; foreach($pair in @(@('light',[double]$mm.Groups[3].Value),@('dark',[double]$mm.Groups[4].Value))){ $k1="$($pair[0]).$sc"; $k2="$($pair[0]).$gr"; if(-not ($tok.ContainsKey($k1) -and $tok.ContainsKey($k2))){ $bad+="map row names unknown token: $k1 or $k2"; continue }; $act=[Math]::Round((& $ratio $tok[$k1] $tok[$k2]),2); if($act -ne $pair[1]){ $bad+=('map row {0} on {1} {2}: printed {3} actual {4}' -f $sc,$gr,$pair[0],$pair[1],$act) } } }; $fm=[regex]::Match($d,'(?s)\A---\r?\n(.*?)\r?\n---\r?\n').Groups[1].Value; $blk=[regex]::Match($fm,'(?ms)^  evidence-rail:\r?\n(.*?)(?=^  [a-z0-9-]+:\r?$)').Groups[1].Value; if(-not $blk){ $bad+='evidence-rail block not found'; $bad | ForEach-Object { Write-Host $_ }; exit 1 }; $keys=@{}; foreach($k in [regex]::Matches($blk,'(?m)^    ([A-Za-z0-9]+Color): "\{colors\.([a-z-]+)\}"\s*$')){ $keys[$k.Groups[1].Value]=$k.Groups[2].Value }; $states=@(([regex]::Match($blk,'(?m)^    segmentStates: \[([A-Z_, ]+)\]\s*$').Groups[1].Value -split ',\s*') | Where-Object { $_ }); if($states.Count -ne 5){ $bad+="segmentStates has $($states.Count) entries, expected 5" }; $want=@('backgroundColor'); $byState=@{}; foreach($s in $states){ $p=(($s -split '_') | ForEach-Object { $_.Substring(0,1)+$_.Substring(1).ToLower() }) -join ''; $key=($p.Substring(0,1).ToLower()+$p.Substring(1))+'Color'; $want+=$key; if(-not $keys.ContainsKey($key)){ $bad+="segment state $s resolves to no declared color: $key is absent" } else { $byState[$s]=$keys[$key] } }; $have=(@($keys.Keys | Sort-Object) -join '/'); $wantJoined=(@($want | Sort-Object) -join '/'); if($have -cne $wantJoined){ $bad+=("declared segment colors [{0}] are not exactly the ones the states require [{1}]" -f $have,$wantJoined) }; foreach($s in $states){ if($byState.ContainsKey($s) -and -not ($tok.ContainsKey("light.$($byState[$s])") -and $tok.ContainsKey("dark.$($byState[$s])"))){ $bad+="segment state $s names a color absent from the palette: $($byState[$s])" } }; $bg=$keys['backgroundColor']; foreach($s in $states){ if($byState.ContainsKey($s)){ foreach($th in @('light','dark')){ if(-not $seen.ContainsKey("$th.$($byState[$s])|$th.$bg")){ $bad+="segment state $s is unregistered on the rail ground in $th" } } } }; if($byState['NOT_APPLICABLE'] -cne 'outline'){ $bad+="the not-applicable segment declares '$($byState['NOT_APPLICABLE'])', not outline" }; if($byState['NOT_APPLICABLE'] -cne $byState['OPTIONAL']){ $bad+='the not-applicable and optional segments do not share one color' }; foreach($th in @('light','dark')){ foreach($gr in @('surface','surface-container-low','surface-container','surface-container-high')){ if(-not $seen.ContainsKey("$th.$($byState['NOT_APPLICABLE'])|$th.$gr")){ $bad+="the not-applicable foreground is unregistered on $th.$gr" } } }; $groups=@{}; foreach($s in $states){ if($byState.ContainsKey($s)){ $t=$byState[$s]; if(-not $groups.ContainsKey($t)){ $groups[$t]=@() }; $groups[$t]+=$s } }; $shared=@($groups.Keys | Where-Object { $groups[$_].Count -gt 1 }); if($shared.Count -ne 1){ $bad+="expected exactly one shared segment color, found $($shared.Count)" } elseif(((@($groups[$shared[0]]) | Sort-Object) -join ',') -cne 'NOT_APPLICABLE,OPTIONAL'){ $bad+='the one shared segment color is not the optional/not-applicable pair' }; $owners=@(); foreach($cb2 in [regex]::Matches([regex]::Match($fm,'(?ms)^components:\r?\n(.*)\z').Groups[1].Value,'(?ms)^  ([a-z0-9-]+):\r?\n(.*?)(?=^  [a-z0-9-]+:\r?$|\z)')){ $cid=$cb2.Groups[1].Value; $cbody=$cb2.Groups[2].Value; $ck=@([regex]::Matches($cbody,'(?m)^    ([A-Za-z0-9]+Color):') | ForEach-Object { $_.Groups[1].Value }); $sn=@(); foreach($ll in [regex]::Matches($cbody,'(?m)^    (?:variants|states|segmentStates): \[([^\]]+)\]')){ $sn += ((($ll.Groups[1].Value) -replace "'",'') -split ',\s*') }; foreach($s2 in $sn){ if($s2 -notmatch '^[A-Z][A-Z0-9_]*$'){ continue }; $p2=(($s2 -split '_') | ForEach-Object { $_.Substring(0,1)+$_.Substring(1).ToLower() }) -join ''; $k2=($p2.Substring(0,1).ToLower()+$p2.Substring(1))+'Color'; if($ck -ccontains $k2){ $owners+=$cid; break } } }; $owners=@($owners | Sort-Object -Unique); if(($owners -join '/') -cne 'evidence-rail'){ $bad+=("components naming a color after one of their own states are [{0}], expected evidence-rail alone" -f ($owners -join '/')) }; $need="optional and not-applicable segments share one neutral, $($byState['NOT_APPLICABLE']), and each carries a dash"; if(([regex]::Matches($d,[regex]::Escape($need))).Count -ne 1){ $bad+="the rail prose does not name the one neutral role the frontmatter declares for those two states" }; if($bad){ $bad | ForEach-Object { Write-Host $_ }; exit 1 }; exit 0
dod_exit: 0
dod_assert: A1 `evidence-rail` declares one color per segment state: for each of the five names in `segmentStates` the frontmatter carries exactly the color key that name derives, `notApplicableColor` included, and the set of declared segment colors is exactly that derived set, so the component can name neither a state without a color nor a color without a state, and every color it names is a palette role present in both themes. A2 the not-applicable segment declares `outline`, bound once, and that role is registered with the contrast gate on the rail ground and on all four content surfaces in both themes, so the amendment introduces no pair the gate did not already carry: the binding set still holds seventy-five entries with no duplicate pair, no value drifting from its token and no ratio under its declared minimum, the twenty state-color-on-content-surface pairs per theme are all still registered, the twenty-six state-icon bindings are unchanged, and the thirteen rows of the state glyph contrast map still recompute from the frontmatter tokens to their printed light and dark ratios. A3 the optional and not-applicable segments are the one and only pair of segment states sharing a role; the contrast map sentence now names five segments for four roles and says which two share one, so its arithmetic still holds; the component row says those two share one declared segment color and the rail prose names the very role the frontmatter declares for them, recomputed from the frontmatter rather than matched against a literal; the light palette bullet now credits `outline` with the optional and not-applicable evidence segments specifically, so it neither withholds the duty the rail row names nor claims the whole class of evidence segments, and both the narrower baseline wording and the over-broad blanket wording are asserted absent; the word irrelevant, which named no declared state, is gone from the document; and `evidence-rail` remains the only component that names a color after one of its own states, which is why the amendment lands there and nowhere else.
review_gate: codex {verdict:pass}
hygiene: 每条断言由「删掉被改写的那一句即变红」的单点变异证明；变异批钉生产文件 SHA-256，且每枚植入后须断言 mutant != baseline、条目元数自检（L196/L270/L319）。
doc_sync: TASK-BOARD 记录合并 OID。
---

# T4-DESIGN-NOT-APPLICABLE-COLOR

## 起因：`T4-DESIGN-STATUS-CARRIERS` ship 前本地对抗复核记下的 `[FOLLOW-UP]`（2026-09-08）

前卡把 状态色 × 内容底 的 40 个配对全部登记进对比度闸，于是「必带状态字形落在哪个中性底上」
不再是问题。ship 前的全新上下文复核指出了它的**对偶缺口**：那个字形**用哪个前景色**，对
`NOT_APPLICABLE` 而言全文没有答案。该发现被判为 `[FOLLOW-UP]` 而非阻断，理由是给一个态指派
role 颜色属**调色决定**，落在前卡「只补登记、不调色」的范围之外、且贴着其 `forbid` 第三条。

## 缺口的确切形态（开卡时已逐处核实，勿照抄、仍须复跑）

**这不是 WCAG 1.4.1 缺口。** 每一处渲染 not-applicable 的地方都已有非颜色载体：item card 的
`NOT_APPLICABLE` 行是「Dash, explicit label, `Change`」（破折号**加**显式文字标签），rail 段的
破折号是形状而非颜色。下限不受影响，本卡也不得借此松动它。

缺的是**颜色绑定的完整性**，而它现在有了机检后果：前卡把状态条款改成「必带状态字形渲染在
**对比度闸已登记的**前景/背景配对上」——要判一个配对是否已登记，就得先知道前景是哪个 token。
对 `NOT_APPLICABLE` 这一步答不出来。

| 处 | 原文事实 |
|---|---|
| `evidence-rail` frontmatter | 声明 `completeColor` / `missingRequiredColor` / `blockedColor` / `optionalColor` **四个**颜色，而 `segmentStates` 是 `COMPLETE, MISSING_REQUIRED, BLOCKED, OPTIONAL, NOT_APPLICABLE` **五个**态 |
| rail 散文段 | 「A complete segment uses primary; missing-required evidence uses amber; a compliance-blocked segment uses red; **optional/irrelevant** evidence uses neutral with a dash.」——`irrelevant` **不是**任何一个声明过的状态名，故它是否指 `NOT_APPLICABLE` 靠读者推断 |
| 组件行 `evidence-rail` | 变体列逐字列出五个态，行内不提颜色 |
| 状态条款 | 「Where a glyph marks OK, attention, blocked, **not applicable** or privacy, it uses the declared symbol: … **dash for not applicable** …」——钉死了字形，没钉前景 |
| item card 状态表 | `NOT_APPLICABLE` 行「Dash, explicit label, `Change`」——有载体，无颜色 |

**范围外的近邻，别顺手改**：`status-choice` 的变体含 `NOT_APPLICABLE`、`compliance-check-row`
的状态含 `not applicable`，但这两个组件对**所有**变体都不声明颜色，`NOT_APPLICABLE` 在那里
并不比 `OK` 更缺——那是「组件不声明配色」的另一类问题，不在本卡（见 `non_goals`）。
另注：`:1770` 的「If a state cannot occur, the page contract marks it `NOT_APPLICABLE`」讲的是
**页面契约的元标注**，不是被渲染的状态，别把它一起卷进来。

## 未决决策：已收口（2026-09-08，用户裁定）

**OD-1 · `NOT_APPLICABLE` 用哪个前景？→ 用户裁定取 A**（本卡开工指令即 `with option A`）。

- **推荐 A：明说它与 `OPTIONAL` 共用 `optionalColor`（即 `outline`）。** 与 rail 散文
  「optional/**irrelevant** evidence uses neutral with a dash」的既有意图一致；`outline` 已由前卡
  在两个主题的四个内容底上全部登记，故**零新绑定、零新配对、零调色**，A2 自动满足。落地形态可以是
  把 `optionalColor` 的语义写清（它服务哪些段态），而不是新增一个 `notApplicableColor` 键。
- 备选 B：给它自己的 role 与色值。需要新 token + 新绑定 + 新实测比值，直接撞 `forbid` 第一条，
  且没有任何证据说明「可选」与「不适用」在 6dp 宽的 rail 段上需要被颜色区分（二者都已带破折号，
  且 rail 整条合并成一个 TalkBack 节点播报）。**除非用户明确要求区分，否则不取。**

选 A 时仍须**显式写下来**：本卡的价值正是把「靠读者把 `irrelevant` 推断成 `NOT_APPLICABLE`」
换成一句可被机检的声明。

### 落地形态：加 `notApplicableColor: "{colors.outline}"` 键（偏离本节括注的"不新增键"，理由如下）

A 的实质是**取哪个前景**——`outline`，零新 token、零新 role、零新绑定、零新配对。落地形态是另一回事，
本卡取「加一个键」而非「把 `optionalColor` 的服务范围写成散文」，三条理由：

1. **A1 的字面要求是让计数相等**（"the component no longer names five segment states and four
   segment colors"）。只写散文时组件仍是五态四色，A1 只能靠解释满足。
2. **解析规则是既有的，不是新造的**：四个既有键已经是 `<状态名转小驼峰>Color`
   （`COMPLETE`→`completeColor`、`MISSING_REQUIRED`→`missingRequiredColor`），
   故"每个 `segmentStates` 解析到恰好一个已声明颜色"可由**该命名规则直接机检**，
   而无须为本卡发明第二套映射语法（frontmatter 里组件层从无嵌套映射的先例）。
3. **共用仍然可见且可机检**：两个键取同一个 `{colors.outline}`，
   于是"哪两个态共用一个 role"是从 frontmatter **算出来**的，不是散文声称的——
   DoD 据此断言「有且仅有一组共用，且它就是 OPTIONAL/NOT_APPLICABLE」。

**这不是 B**：B 是给它自己的 role 与色值（新 token + 新绑定 + 新实测比值），撞 `forbid` 第一条；
本形态一个色值都没动，绑定集仍是 75 条、状态色×内容底 仍是每主题 20 对全登记。

### 为什么 `inspection-item-card` 的破折号不在本卡（与 `status-choice` 的排除理由不同）

`non_goals` 排除 `status-choice` / `compliance-check-row` 的理由是「它们对所有变体都不声明颜色」。
item card 的 `NOT_APPLICABLE` 行（`Dash, explicit label, Change`）需要另一条理由，因为它**确实**声明了
四个颜色（`backgroundColor` / `textColor` / `boundaryColor` / `boundaryAdjacentColor`）。理由是那四个都是
**组件级**颜色，无一以自己的状态名命名；`evidence-rail` 是全 frontmatter 里**唯一**把颜色按自己的状态
命名的组件，因此也是唯一可能出现「某个状态没有对应颜色」这一缺口的组件。这一条不是散文声称：
DoD 遍历每个组件、按同一条 `<状态名转小驼峰>Color` 规则求解，断言这样的组件有且仅有 `evidence-rail`。

## 方法

1. 沿用前卡三条：代入表要覆盖被改写的**周边**条款；新句与新句之间交叉核对；规则有两半就两半同时判。
2. **不写全称句**（L309，前卡在这条上栽了三次、其中两次靠复核才抓到）：要说的是
   「`evidence-rail` 的每个 `segmentStates` 都解析到一个已声明颜色」这种**有界**断言，
   不是「文档里每个状态都有颜色」这种对整份文档的断言——后者对 `status-choice` 立刻为假。
3. DoD 除锚点外**复用前卡的机检骨架**：从 frontmatter 重算绑定/配对覆盖，证明本卡没有引入
   任何未登记的前景/背景配对（选 A 时该数应当逐字不变）。

## 交付记录（2026-09-08）

改动是 `context/DESIGN.md` 的 **5 行**（新增 1 行 + 改写 4 行），一个色值都没动。

| # | 位置 | 改动 | 兑现 |
|---|---|---|---|
| 1 | `evidence-rail` frontmatter | 新增 `notApplicableColor: "{colors.outline}"` | A1 / A2 |
| 2 | `State glyph contrast map` 引言 | 四个 role 服务 complete / missing-required / blocked / **optional 与 not-applicable** 五个段，**后两者共用一个 role** | 邻接条款（见下） |
| 3 | 组件表 `evidence-rail` 行 | 约束列追加「optional and not-applicable share one declared segment color」 | A3 |
| 4 | rail 散文 | 「optional/**irrelevant** evidence uses neutral with a dash」→「optional and not-applicable segments share one neutral, outline, and each carries a dash」 | A3 |
| 5 | light 调色板「Surfaces」项 | `outline` 的职责补上 evidence segments（dark 对应项早已列出） | 邻接条款（见下） |

**A1** 由第 1 处兑现，且是**算出来的**而非声称的：四个既有键本就是 `<状态名转小驼峰>Color`，
DoD 据此为 `segmentStates` 的每个名字派生键名、断言它存在，并断言**已声明的段色集合恰好等于派生集合**
——于是「有状态无色」与「有色无状态」两个方向都被封死（M1 杀前者，M3 杀后者）。

**A2** 由第 1 处兑现：not-applicable 的前景是 `outline`，**全文只绑定这一次**；`outline` 在两个主题的
四个内容底上早已全部登记（`boundary` / `evidence-boundary` / `card-boundary` / 前卡补的 `state-icon`），
故本卡**零新配对**。DoD 直接复算这件事：绑定集仍是 **75** 条、无重复配对、无值偏离 token、无比值低于
其 `minRatio`；状态色 × 内容底 每主题 **20** 对仍全登记；`state-icon` 仍是 **26** 条；
`State glyph contrast map` 的 **13** 行印刷比值与从 frontmatter token 重算的值仍逐位相等。

**A3** 由第 3、4 处兑现，另加两条机检：① DoD 从 frontmatter **算出**哪些段态共用同一 role，断言
「共用组有且仅有一个，且它就是 `OPTIONAL` / `NOT_APPLICABLE`」（M13 把 `optionalColor` 指向 `error`
即造出错误的共用组、被杀）；② 散文里点名的那个 role **由 frontmatter 算出后再比对**、不是字面量，
故散文与 frontmatter 不可能各说各的（M19 只改散文里的 role 名即被杀）。
`irrelevant` 这个不属于任何已声明状态名的词已从全文消失（`expected 0`）。

### 两处邻接条款为什么必须一起改（不是顺手改）

**`State glyph contrast map` 引言**：原文把五个 state color 解释成「`evidence-rail` 为其
complete / missing-required / blocked / optional **四个段**命名的四个 role」。本卡给 `NOT_APPLICABLE`
补上取色后，`evidence-rail` 命名的段变成**五个**而 role 仍是**四个**——原句遂成过时的算术。
改写只补足枚举并写明「后两者共用一个 role」，**没有**触碰该节已登记的配对或任何实测比值
（前卡验收面、本卡 `forbid` 第三条），这一点由 DoD 的 13 行重算 + 20 对覆盖 + 75 条绑定复核证明。

**light 调色板项**：它此前只把 `outline` 记给「essential card boundaries and focus」，而 **dark 对应项
早已写明** 「Inputs, cards, **evidence segments**, selected states, and focus indicators use `dark.outline`」。
本卡把「optional / not-applicable 用 `outline` 做**段色**」写进组件行与散文之后，读者回 light 项对照会
落空。此缺口在基线即存在（`optionalColor` 本就是 `outline`），本卡只是把它放大到必须收口。

> **R3 第 1 轮就在这一处拦下一次过度声明，属实、当场修**：我的初稿照抄 dark 侧的名词写成「essential card
> boundaries, **evidence segments**, and focus use `outline`」——那是对**整类** evidence segment 的断言，而
> complete / missing-required / blocked 三个段分别用 `primary` / `tertiary` / `error`，同一份 diff 自己就推翻它。
> （dark 侧原句带「or a semantic container, or the focus token」这个出口，故不是全称句；我抄名词时把出口丢了。）
> 改成「essential card boundaries, **optional and not-applicable** evidence segments, and focus use `outline`」，
> 并把**基线措辞**与**这句被驳回的全称措辞**双双钉成 `expected 0` 反向断言（M20 / M21 各杀一个方向）。
> 这正是 L309 又一次复发：全称句的成本在于它对整类做断言，而我只对手边那两个段态验证过。

### R4 变异收据

基线（GREEN 态）`context/DESIGN.md` SHA-256 =
`33d7e0fb8ebc2f4fb346d94b32be6a233187364e5a1edce383fdcb1e199bb60b`；批后同文件 SHA-256 逐位相同，
批后 DoD 退出 0。每枚植入后先断言 **mutant sha != baseline sha**（证明这一枚确实改动了文件，L319），
还原后断言 **sha == baseline**（L196）；条目表在跑之前做元数自检（每条恰好 4 个字段、id 唯一、
`old != new`）。**20/20 全杀**：

| 变异 | 造的假 | 被杀于 |
|---|---|---|
| M1 | 删掉 `notApplicableColor` 行 | 锚点缺失 + 该态解析不到颜色 |
| M2 | `notApplicableColor` 指向 `tertiary`（同样在四个底上已登记） | 锚点 + 「声明的不是 outline」+ 散文与 frontmatter 不符 |
| M3 | 加一个不对应任何状态的 `pendingColor` | 已声明段色集合 != 派生集合 |
| M4 | 把对比度图引言还原成旧的四段措辞 | 新锚点缺失 + 旧措辞复现（`expected 0`） |
| M5 | 删掉组件行的共用子句 | 锚点缺失 |
| M6 | 把 rail 散文还原成 `optional/irrelevant` | 锚点缺失 + `irrelevant` 复现 |
| M7 | 在别处（`:691`）重新引入 `irrelevant` 一词 | `irrelevant` 的 `expected 0` 反向断言 |
| M8 | 把一条 `state-icon` 绑定改标 `boundary`（配对仍在） | `state-icon` 计数 26 → 25 |
| M9 | 把一行印刷比值挪 0.01 | 13 行重算：印刷 3.52 实算 3.51 |
| M10 | 删掉 `light.privacy` on `light.surface` 绑定 | 计数 75 → 74 + 该配对未登记 |
| M11 | 把 rail 的底换成没有任何段色绑定其上的语义容器 | 五个段态在两个主题上均「未登记于 rail 底」 |
| M12 | 从 `segmentStates` 删掉 `NOT_APPLICABLE` | 段态计数 5 → 4 + 该色成孤儿 |
| M13 | `optionalColor` 指向 `error`（共用组变成 blocked/optional） | 锚点 + 「唯一共用组不是 optional/not-applicable 那对」 |
| M14 | 给 `feedback-banner` 加一个以自身变体命名的 `errorColor` | 「按自身状态命名颜色的组件」不再只有 evidence-rail |
| M15 | light `outline` token 漂一位十六进制 | 绑定值偏离 token |
| M16 | 把一条 `state-icon` 绑定挪到它已覆盖的底上 | 重复配对 + 计数 74 + 原配对未登记 |
| M17 | 删掉一行调色板 token | token 表 56 → 55 |
| M18 | `completeColor` 指向调色板未定义的 role | 该段色不在调色板 + 未登记于 rail 底 |
| M19 | 只把散文里的 role 名改成 `primary`（frontmatter 不动） | 锚点 + 派生断言「散文没点名 frontmatter 声明的那个 role」 |
| M20 | 把 light 调色板项还原成只记 boundaries 与 focus | 新锚点缺失 + 旧措辞复现（`expected 0`） |
| M21 | 把 light 调色板项放大回被 R3 驳回的全称措辞 | 新锚点缺失 + 全称措辞复现（`expected 0`） |

### ship 前全新上下文对抗复核（判 PASS，5 条 finding 的处置）

复核者独立复算（不跑本卡的闸）：56 个 token、75 条绑定、0 重复、0 漂移、0 低于 `minRatio`、
40 个状态色×内容底配对全登记、26 条 `state-icon`、13 行比值全部重算吻合、83 个组件里只有
`evidence-rail` 以自身状态命名颜色。处置：

- **已改文（2 条）**：① 散文原稿「the one neutral the component **declares for them**」把唯一性挂在
  *声明*上，而 frontmatter 有两条声明；同句另三个分句都点名颜色（primary / amber / red），只有被改写
  这句不点名。改为点名 `outline` 并把唯一性挂回颜色，与同句其余三处一致。② light 调色板项补 evidence
  segments（见上）。
- **已改卡（1 条）**：本卡起因段原写「前卡的『必带状态字形须落在已登记配对上』这一步对
  `NOT_APPLICABLE` 答不出来」。复核指出该条款的原文是「**a component row** makes mandatory」，
  而没有任何组件行强制 not-applicable 的破折号（它在散文 `:1782` 与卡态表 `:1796`，两者都不是组件行）。
  故本卡的缺口就是它标题所写的那一个——**颜色绑定的完整性**，不必也不该借那条条款立论。已在下方
  「缺口的确切形态」保留原始事实表、并在此更正因果表述。
- **记 `[FOLLOW-UP]`（1 条）**：`light.outline` / `dark.outline` on `surface-container` 的 CI 条目标
  `"usage":"evidence-boundary"`，而同底的另三个段色标 `"usage":"evidence-segment"`。该配对在本卡后
  同时服务边界与段色两种职责，但 `:1341` 要求**每个配对恰好一条**条目，一个 usage 标签只能命名一种
  ——改标签会让另一种职责失准，真正的收口需要 metadata schema 决定（多 usage），超出本卡。
- **判无须改（1 条）**：`OPTIONAL` 与 `NOT_APPLICABLE` 现在在文档指定的每个通道上都不可区分
  （同 role、同破折号、rail 整条合并成一个 TalkBack 节点）。这**不是**本卡造成的：基线散文本就是
  「optional/irrelevant evidence uses neutral with a dash」、`:1466` 本就规定「dash for not applicable」，
  二比一的字形映射基线即存在，本卡只是把它写清楚。WCAG 1.4.1 要求每个状态有非颜色线索（两者都有
  破折号），不要求两个状态彼此可区分。是否要区分是 OD-1 的设计判断，已由用户裁定取 A。

### 明确未做的三处（及各自的理由不同）

- `status-choice` / `compliance-check-row`：卡片 `non_goals` 已排除——它们对**所有**变体都不声明颜色。
- `inspection-item-card`：它**确实**声明四个颜色，故需要另一条理由——那四个都是组件级、无一以自身状态
  命名。这条理由是机检的：DoD 遍历全部组件按同一条命名规则求解，断言「按自身状态命名颜色」的组件
  有且仅有 `evidence-rail`（M14 证明该断言活着）。
- `:1770` 的「If a state cannot occur, the page contract marks it `NOT_APPLICABLE`」是**页面契约的元标注**，
  不是被渲染的状态，未卷入。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-08 | R3 第 1 轮 block 一条、属实、当场修：light 调色板项的「evidence segments use `outline`」是对整类段的全称断言，与同一 diff 里 complete/missing-required/blocked 各用 primary/tertiary/error 自相矛盾。收窄为「optional and not-applicable evidence segments」，并把基线措辞与该全称措辞双双钉成 `expected 0`。变异批加至 21/21。 |
| 2026-09-08 | ship 前全新上下文对抗复核（独立复算 token/绑定/配对/比值）判 PASS，5 条 finding：2 条改文（rail 散文改名出 role、light 调色板项补 evidence segments），1 条改卡（A2 的因果表述），1 条记 FOLLOW-UP（`evidence-boundary` 标签），1 条判无须改（两态不可区分为基线既有）。变异批加至 20/20。 |
| 2026-09-08 | 措辞复核：rail 散文原稿写的「since neither marks outstanding evidence」对分组欠定（`COMPLETE` 同样不欠证据却不共用），改为只陈述事实；另把「唯一按自身状态命名颜色的组件」做成机检，使 item card 的排除理由不再是散文。 |
| 2026-09-08 | 收口 OD-1：用户裁定取 A；落地形态定为加 `notApplicableColor` 键（理由见上节），钉定 dod_command / dod_assert。 |
| 2026-09-08 | 建卡：承接 `T4-DESIGN-STATUS-CARRIERS` 的 `[FOLLOW-UP]`（其 ship 前本地对抗复核发现）。缺口已逐处核实并写明「这不是 WCAG 缺口、是颜色绑定完整性缺口」，近邻的 `status-choice` / `compliance-check-row` 显式划出范围外。 |
