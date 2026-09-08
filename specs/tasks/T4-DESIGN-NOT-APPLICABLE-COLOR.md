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
dod_command: $d=Get-Content -Raw -LiteralPath 'context/DESIGN.md'; $bad=@(); foreach($c in @(@('    notApplicableColor: "{colors.outline}"',1),@('    optionalColor: "{colors.outline}"',1),@('complete, missing-required, blocked, optional and not-applicable segments, the last two sharing one role',1),@('complete, missing-required, blocked and optional segments',0),@('optional and not-applicable share one declared segment color',1),@('optional and not-applicable segments each carry a dash and both take the neutral the component declares for them, since neither marks outstanding evidence',1),@('irrelevant',0),@('"usage":"state-icon"',26))){ $n=([regex]::Matches($d,[regex]::Escape($c[0]))).Count; if($n -ne $c[1]){ $bad+=('anchor [{0}] expected {1} found {2}' -f $c[0],$c[1],$n) } }; $tok=@{}; $pfx=''; foreach($line in ($d -split '\r?\n')){ if($line -eq 'colors:'){$pfx='light'} elseif($line -eq 'dark-colors:'){$pfx='dark'} elseif($line -match '^[a-z]'){$pfx=''} elseif($pfx -and $line -match '^  ([a-z-]+): "(#[0-9A-Fa-f]{6})"\s*$'){ $tok["$pfx.$($Matches[1])"]=$Matches[2] } }; if($tok.Count -ne 56){ $bad+="token map has $($tok.Count) entries, expected 56" }; $lum={ param($hex) $acc=0.0; $wt=@(0.2126,0.7152,0.0722); for($k=0;$k -lt 3;$k++){ $ch=[Convert]::ToInt32($hex.Substring(1+2*$k,2),16)/255.0; $acc+=$wt[$k]*$(if($ch -le 0.04045){$ch/12.92}else{[Math]::Pow((($ch+0.055)/1.055),2.4)}) }; $acc }; $ratio={ param($fg,$bg) $x=(& $lum $fg); $y=(& $lum $bg); ([Math]::Max($x,$y)+0.05)/([Math]::Min($x,$y)+0.05) }; $seen=@{}; foreach($mm in [regex]::Matches($d,'\{"foreground":"([a-z.-]+)","value":"(#[0-9A-Fa-f]{6})","background":"([a-z.-]+)","backgroundValue":"(#[0-9A-Fa-f]{6})","usage":"[a-z-]+","minRatio":([0-9.]+),"essential":(?:true|false)\}')){ $fk=$mm.Groups[1].Value; $fv=$mm.Groups[2].Value; $bk=$mm.Groups[3].Value; $bv=$mm.Groups[4].Value; $mr=[double]$mm.Groups[5].Value; if($seen.ContainsKey("$fk|$bk")){ $bad+="duplicate binding $fk on $bk" }; $seen["$fk|$bk"]=$true; if($tok.ContainsKey($fk) -and $tok[$fk] -ne $fv){ $bad+="binding value drifts from token $fk" }; if($tok.ContainsKey($bk) -and $tok[$bk] -ne $bv){ $bad+="binding value drifts from token $bk" }; if(((& $ratio $fv $bv)+0.0001) -lt $mr){ $bad+="ratio below minRatio: $fk on $bk" } }; if($seen.Count -ne 75){ $bad+="binding count is $($seen.Count), expected 75" }; foreach($th in @('light','dark')){ foreach($sc in @('primary','tertiary','error','outline','privacy')){ foreach($gr in @('surface','surface-container-low','surface-container','surface-container-high')){ if(-not $seen.ContainsKey("$th.$sc|$th.$gr")){ $bad+="state glyph pair unregistered: $th.$sc on $th.$gr" } } } }; $rows=[regex]::Matches($d,'(?m)^\| `([a-z-]+)` \| `([a-z-]+)` \| `([0-9.]+):1` \| `([0-9.]+):1` \|\r?$'); if($rows.Count -ne 13){ $bad+="state glyph map has $($rows.Count) rows, expected 13" }; foreach($mm in $rows){ $sc=$mm.Groups[1].Value; $gr=$mm.Groups[2].Value; foreach($pair in @(@('light',[double]$mm.Groups[3].Value),@('dark',[double]$mm.Groups[4].Value))){ $k1="$($pair[0]).$sc"; $k2="$($pair[0]).$gr"; if(-not ($tok.ContainsKey($k1) -and $tok.ContainsKey($k2))){ $bad+="map row names unknown token: $k1 or $k2"; continue }; $act=[Math]::Round((& $ratio $tok[$k1] $tok[$k2]),2); if($act -ne $pair[1]){ $bad+=('map row {0} on {1} {2}: printed {3} actual {4}' -f $sc,$gr,$pair[0],$pair[1],$act) } } }; $fm=[regex]::Match($d,'(?s)\A---\r?\n(.*?)\r?\n---\r?\n').Groups[1].Value; $blk=[regex]::Match($fm,'(?ms)^  evidence-rail:\r?\n(.*?)(?=^  [a-z0-9-]+:\r?$)').Groups[1].Value; if(-not $blk){ $bad+='evidence-rail block not found'; $bad | ForEach-Object { Write-Host $_ }; exit 1 }; $keys=@{}; foreach($k in [regex]::Matches($blk,'(?m)^    ([A-Za-z0-9]+Color): "\{colors\.([a-z-]+)\}"\s*$')){ $keys[$k.Groups[1].Value]=$k.Groups[2].Value }; $states=@(([regex]::Match($blk,'(?m)^    segmentStates: \[([A-Z_, ]+)\]\s*$').Groups[1].Value -split ',\s*') | Where-Object { $_ }); if($states.Count -ne 5){ $bad+="segmentStates has $($states.Count) entries, expected 5" }; $want=@('backgroundColor'); $byState=@{}; foreach($s in $states){ $p=(($s -split '_') | ForEach-Object { $_.Substring(0,1)+$_.Substring(1).ToLower() }) -join ''; $key=($p.Substring(0,1).ToLower()+$p.Substring(1))+'Color'; $want+=$key; if(-not $keys.ContainsKey($key)){ $bad+="segment state $s resolves to no declared color: $key is absent" } else { $byState[$s]=$keys[$key] } }; $have=(@($keys.Keys | Sort-Object) -join '/'); $wantJoined=(@($want | Sort-Object) -join '/'); if($have -cne $wantJoined){ $bad+=("declared segment colors [{0}] are not exactly the ones the states require [{1}]" -f $have,$wantJoined) }; foreach($s in $states){ if($byState.ContainsKey($s) -and -not ($tok.ContainsKey("light.$($byState[$s])") -and $tok.ContainsKey("dark.$($byState[$s])"))){ $bad+="segment state $s names a color absent from the palette: $($byState[$s])" } }; $bg=$keys['backgroundColor']; foreach($s in $states){ if($byState.ContainsKey($s)){ foreach($th in @('light','dark')){ if(-not $seen.ContainsKey("$th.$($byState[$s])|$th.$bg")){ $bad+="segment state $s is unregistered on the rail ground in $th" } } } }; if($byState['NOT_APPLICABLE'] -cne 'outline'){ $bad+="the not-applicable segment declares '$($byState['NOT_APPLICABLE'])', not outline" }; if($byState['NOT_APPLICABLE'] -cne $byState['OPTIONAL']){ $bad+='the not-applicable and optional segments do not share one color' }; foreach($th in @('light','dark')){ foreach($gr in @('surface','surface-container-low','surface-container','surface-container-high')){ if(-not $seen.ContainsKey("$th.$($byState['NOT_APPLICABLE'])|$th.$gr")){ $bad+="the not-applicable foreground is unregistered on $th.$gr" } } }; $groups=@{}; foreach($s in $states){ if($byState.ContainsKey($s)){ $t=$byState[$s]; if(-not $groups.ContainsKey($t)){ $groups[$t]=@() }; $groups[$t]+=$s } }; $shared=@($groups.Keys | Where-Object { $groups[$_].Count -gt 1 }); if($shared.Count -ne 1){ $bad+="expected exactly one shared segment color, found $($shared.Count)" } elseif(((@($groups[$shared[0]]) | Sort-Object) -join ',') -cne 'NOT_APPLICABLE,OPTIONAL'){ $bad+='the one shared segment color is not the optional/not-applicable pair' }; if($bad){ $bad | ForEach-Object { Write-Host $_ }; exit 1 }; exit 0
dod_exit: 0
dod_assert: A1 `evidence-rail` declares one color per segment state: for each of the five names in `segmentStates` the frontmatter carries exactly the color key that name derives, `notApplicableColor` included, and the set of declared segment colors is exactly that derived set, so the component can name neither a state without a color nor a color without a state, and every color it names is a palette role present in both themes. A2 the not-applicable segment declares `outline`, stated once, and that role is registered with the contrast gate on the rail ground and on all four content surfaces in both themes, so the amendment introduces no pair the gate did not already carry: the binding set still holds seventy-five entries with no duplicate pair, no value drifting from its token and no ratio under its declared minimum, the twenty state-color-on-content-surface pairs per theme are all still registered, the twenty-six state-icon bindings are unchanged, and the thirteen rows of the state glyph contrast map still recompute from the frontmatter tokens to their printed light and dark ratios. A3 the optional and not-applicable segments are the one and only pair of segment states sharing a role; the contrast map sentence now names five segments for four roles and says which two share one, so its arithmetic still holds; the component row and the rail prose each name optional and not-applicable as that pair; and the word irrelevant, which named no declared state, is gone from the document.
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

## 方法

1. 沿用前卡三条：代入表要覆盖被改写的**周边**条款；新句与新句之间交叉核对；规则有两半就两半同时判。
2. **不写全称句**（L309，前卡在这条上栽了三次、其中两次靠复核才抓到）：要说的是
   「`evidence-rail` 的每个 `segmentStates` 都解析到一个已声明颜色」这种**有界**断言，
   不是「文档里每个状态都有颜色」这种对整份文档的断言——后者对 `status-choice` 立刻为假。
3. DoD 除锚点外**复用前卡的机检骨架**：从 frontmatter 重算绑定/配对覆盖，证明本卡没有引入
   任何未登记的前景/背景配对（选 A 时该数应当逐字不变）。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-08 | 收口 OD-1：用户裁定取 A；落地形态定为加 `notApplicableColor` 键（理由见上节），钉定 dod_command / dod_assert。 |
| 2026-09-08 | 建卡：承接 `T4-DESIGN-STATUS-CARRIERS` 的 `[FOLLOW-UP]`（其 ship 前本地对抗复核发现）。缺口已逐处核实并写明「这不是 WCAG 缺口、是颜色绑定完整性缺口」，近邻的 `status-choice` / `compliance-check-row` 显式划出范围外。 |
