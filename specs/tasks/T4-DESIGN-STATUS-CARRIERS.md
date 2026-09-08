---
id: T4-DESIGN-STATUS-CARRIERS
title: 状态载体收口的三处遗留：对比度绑定、两处边界行、capture Back 双名
depends_on: [T4-DESIGN-SYMBOL-CHROME-V2]
parallelizable_with: []
plan_ref: context/DESIGN.md#colors
status: merged
branch: T4-DESIGN-STATUS-CARRIERS
worktree: C:\wt\T4-DESIGN-STATUS-CARRIERS
allow_paths:
  - context/DESIGN.md
forbid:
  - 弱化「颜色不得是状态的唯一通道」这一 WCAG 1.4.1 下限
  - 把无障碍播报当作可见视觉线索的替代项（前卡 R3 第 5 轮已裁死）
  - 改动 design token 的色值（本卡只补登记缺失的对比度绑定，不调色）
  - 改动 `Symbol-only chrome` 节的准入条件与载体规则（前卡已验收）
non_goals:
  - 落地任何界面代码（各 UI 卡拥有）
  - 重做前卡已完成的十一处组件行补齐（已随前卡合并）
acceptance:
  - "A1 the additional foreground/background pairs are registered with the contrast gate so the predecessor's constraint confining mandatory state glyphs to already-registered pairs can be relaxed, with each new pair carrying its audited light and dark ratio"
  - "A2 verification-receipt and photo-evidence-tile are each judged explicitly against both duties, and every declared state is recorded as carried, or fixed, or exempted with its reason"
  - "A3 the capture Back control has exactly one declared accessible name across the whole document, so the tooltip condition has a single referent"
dod_command: $d=Get-Content -Raw -LiteralPath 'context/DESIGN.md'; $bad=@(); foreach($c in @(@('### State glyph contrast map',1),@('each with its audited light and dark ratio',1),@('as distinct from every other ground this document names, a semantic container, a base or',1),@('every state color is registered on every content surface, as the state glyph contrast map records',1),@('essential-icon threshold in both themes',1),@('"usage":"state-icon"',26),@('so requiring such a glyph never turns on which neutral ground it lands on',1),@('renders only on a container whose foreground and background pair is already registered',0),@('absent from this table and from the state glyph contrast map below',1),@('at 3.51:1 light and 4.37:1 dark',1),@('receipt state named in visible text as verified, stale, failed or unavailable',1),@('verified/stale state, absolute time',0),@('an empty optional tile names that the photo is optional',1),@('a temporary capture names that it is not saved yet and never appears as persisted evidence',1),@('except where the top app bar contract declares another name for that page type, which it does once, for',1),@('Back to {parent}',1),@('Back, accessible label',1))){ $n=([regex]::Matches($d,[regex]::Escape($c[0]))).Count; if($n -ne $c[1]){ $bad+=('anchor [{0}] expected {1} found {2}' -f $c[0],$c[1],$n) } }; $tok=@{}; $pfx=''; foreach($line in ($d -split '\r?\n')){ if($line -eq 'colors:'){$pfx='light'} elseif($line -eq 'dark-colors:'){$pfx='dark'} elseif($line -match '^[a-z]'){$pfx=''} elseif($pfx -and $line -match '^  ([a-z-]+): "(#[0-9A-Fa-f]{6})"\s*$'){ $tok["$pfx.$($Matches[1])"]=$Matches[2] } }; if($tok.Count -ne 56){ $bad+="token map has $($tok.Count) entries, expected 56" }; $lum={ param($hex) $acc=0.0; $wt=@(0.2126,0.7152,0.0722); for($k=0;$k -lt 3;$k++){ $ch=[Convert]::ToInt32($hex.Substring(1+2*$k,2),16)/255.0; $acc+=$wt[$k]*$(if($ch -le 0.04045){$ch/12.92}else{[Math]::Pow((($ch+0.055)/1.055),2.4)}) }; $acc }; $ratio={ param($fg,$bg) $x=(& $lum $fg); $y=(& $lum $bg); ([Math]::Max($x,$y)+0.05)/([Math]::Min($x,$y)+0.05) }; $seen=@{}; foreach($mm in [regex]::Matches($d,'\{"foreground":"([a-z.-]+)","value":"(#[0-9A-Fa-f]{6})","background":"([a-z.-]+)","backgroundValue":"(#[0-9A-Fa-f]{6})","usage":"[a-z-]+","minRatio":([0-9.]+),"essential":(?:true|false)\}')){ $fk=$mm.Groups[1].Value; $fv=$mm.Groups[2].Value; $bk=$mm.Groups[3].Value; $bv=$mm.Groups[4].Value; $mr=[double]$mm.Groups[5].Value; if($seen.ContainsKey("$fk|$bk")){ $bad+="duplicate binding $fk on $bk" }; $seen["$fk|$bk"]=$true; if($tok.ContainsKey($fk) -and $tok[$fk] -ne $fv){ $bad+="binding value drifts from token $fk" }; if($tok.ContainsKey($bk) -and $tok[$bk] -ne $bv){ $bad+="binding value drifts from token $bk" }; if(((& $ratio $fv $bv)+0.0001) -lt $mr){ $bad+="ratio below minRatio: $fk on $bk" } }; foreach($th in @('light','dark')){ foreach($sc in @('primary','tertiary','error','outline','privacy')){ foreach($gr in @('surface','surface-container-low','surface-container','surface-container-high')){ if(-not $seen.ContainsKey("$th.$sc|$th.$gr")){ $bad+="state glyph pair unregistered: $th.$sc on $th.$gr" } } } }; $rows=[regex]::Matches($d,'(?m)^\| `([a-z-]+)` \| `([a-z-]+)` \| `([0-9.]+):1` \| `([0-9.]+):1` \|\r?$'); if($rows.Count -ne 13){ $bad+="state glyph map has $($rows.Count) rows, expected 13" }; foreach($mm in $rows){ $sc=$mm.Groups[1].Value; $gr=$mm.Groups[2].Value; foreach($pair in @(@('light',[double]$mm.Groups[3].Value),@('dark',[double]$mm.Groups[4].Value))){ $k1="$($pair[0]).$sc"; $k2="$($pair[0]).$gr"; if(-not ($tok.ContainsKey($k1) -and $tok.ContainsKey($k2))){ $bad+="map row names unknown token: $k1 or $k2"; continue }; $act=[Math]::Round((& $ratio $tok[$k1] $tok[$k2]),2); if($act -ne $pair[1]){ $bad+=('map row {0} on {1} {2}: printed {3} actual {4}' -f $sc,$gr,$pair[0],$pair[1],$act) } } }; if($bad){ $bad | ForEach-Object { Write-Host $_ }; exit 1 }; exit 0
dod_exit: 0
dod_assert: DESIGN.md carries one named State glyph contrast map that names the five state colors and the four content surfaces, states that every pair those two sets form is registered with the contrast gate at the essential-icon threshold in both themes, and tabulates the thirteen pairs it adds with their audited light and dark ratios; the CI metadata carries exactly those twenty-six new state-icon bindings; the dark map admits a foreground on a background listed there instead of forbidding it; the map separates a neutral ground from every other ground the document names, a semantic container, a base or on- role, or the camera scrim, each carrying its own paired role, so the coverage claim is not asserted over grounds it does not cover; the status clause keeps the closed requirement that a mandatory state glyph render on a registered pair and credits the registration to the registrations themselves rather than to the map, which holds thirteen of the twenty pairs per theme, while recording that requiring such a glyph no longer turns on which neutral ground it lands on; the summary paragraph records the lowest state glyph ratio as outline on surface-container-high at 3.51:1 light and 4.37:1 dark. The verification-receipt anatomy names its state in visible text as one of verified, stale, failed or unavailable, so no state of that row rests on the binary reading. The photo-evidence-tile behaviour gives the two states that carried nothing their own visible text: an empty optional tile names that the photo is optional, and a temporary capture names that it is not saved yet while still never appearing as persisted evidence. The capture Back control has exactly one declared accessible name: the generic top-app-bar label defers for the one page type whose contract declares another name, STREAM_CAPTURE, and Back to parent occurs once in the whole document.
review_gate: codex {verdict:pass}
hygiene: 每条断言由「删掉被改写的那一句即变红」的单点变异证明；变异批钉生产文件 SHA-256（L196/L270）。
doc_sync: TASK-BOARD 记录合并 OID。
---

# T4-DESIGN-STATUS-CARRIERS

## 起因：`T4-DESIGN-SYMBOL-CHROME-V2` 的三处显式遗留（2026-09-08）

前卡把「状态由什么承载」整个收口了：非颜色载体的两项义务（**视觉半**：每个状态都有非颜色的
视觉线索；**播报半**：无自身可见文字时 owner 另外播报，且**播报永不替代视觉线索**）已写进
`:1420`，十一处既有组件行的无障碍缺口已逐个补齐并随前卡合并。

> **本卡一度被裁定承接「整个载体分类学 + 组件行补齐」，随即证明不可分割**：把补齐撤回、
> 只留下限声明后，`:691`「rail never carries state by color alone」立刻与 `:1736`
> （complete/missing/blocked 仅靠颜色区分）自相矛盾，R3 当轮即拦。**下限声明与行补齐必须同卡**，
> 故补齐已还原进前卡，本卡只承接下面三处真正独立的尾巴。这条经验值得记住：
> **拆卡的切口必须落在「声称」与「证据」之间不产生断裂的地方**。

## A1 · 放宽前卡的「已登记配对」约束

前卡把若干状态图标从 optional 改为**必带**（`metadata-row` 的 warning/error、`summary-stat` 的
complete/attention/blocked、`task-stepper` 的 complete/failed 状态标记）。必带状态图标是
**essential icon**，落对比度表的 3.00:1 档，而对比度闸要求每个渲染前景/背景对都有精确 metadata 条目。

**前卡 R3 第 8 轮的裁定**：把线索定为必带、却没有证据它可被感知，并不能建立下限。
前卡取了评审给的第二条出路——**约束到已验证配对**，在状态条款写下
「组件行定为必带的状态字形，只渲染在对比度闸已登记的前景/背景配对上」，不新造绑定、不改色值。

**本卡的活是放宽它**：补登记缺失的配对（已查实 `light.tertiary` / `light.error` 只对
`surface-container` 有绑定、缺对 `surface`；dark 侧同理须复核），每个新配对带其实测 light/dark 比值，
之后该约束才可放宽。开卡时先重跑一遍实际缺口，勿照抄本段。

## A2 · 两处边界行（前卡划过线、明确未判失败）

前卡的双半扫描对这两行**给出了理由但未下最终判定**，留给本卡按同一方法收口：

| 行 | 状态 | 前卡的读法（须复核） |
|---|---|---|
| `verification-receipt` | `failed` / `unavailable` | 仅当解剖里的 `verified/stale state` 读作**通用状态字段**（而非二值字段）时才通过。若读作二值，两态无载体 |
| `photo-evidence-tile` | `TEMPORARY` | 该行自身无载体，但它总渲染在相机复核流内，`Retake` / `Use photo` 可作 owner 可见文字。须确认该流程是否为其**唯一**渲染场合 |

## A3 · capture Back 的双名（基线既有矛盾）

`STREAM_CAPTURE` 的 Back 同时被两处声明：一处给 `Save and exit`，另一处的 `top-app-bar` 行给
`Back to {parent}`。前卡的准入条件 3 要求「anatomy 声明了 tooltip 时，tooltip 携**同一短语**」,
而「同一短语」在此没有唯一指代。

**该矛盾在基线即存在**，且前卡替换掉的旧 `icon-button` 行本就带同一依赖，故前卡按
`[FOLLOW-UP]` 记账而未就地收口（收口它属于 Back 标签合同，超出前卡范围）。本卡定一个名。

## 方法（沿用前卡验证有效的三条）

1. 代入表要覆盖**被改写的周边条款**，不只中心规则。
2. 代入表管不了**新句与新句**自相矛盾，新写的句子之间要交叉核对。
3. 规则若有两半，**两半必须同时判**——只判一半时，为满足另一半而新增的载体会系统性制造未覆盖项。

## 未决决策

无。A3 的选名与 A2 的两处读法均属实现自由度，由 A1–A3 的性质断言与 R3 评审约束。

## 交付记录（2026-09-08）

### A1 · 开卡时重跑的实际缺口（不照抄建卡段）

按 WCAG 公式（DESIGN.md「Contrast threshold contract」原文所述那套，非等价替代 API，L190）逐对
实测后确认：缺的不止建卡段猜的两对。**状态色**取 `primary` / `tertiary` / `error` / `outline` /
`privacy`——前四个正是 `evidence-rail` 为 complete / missing-required / blocked / **optional** 四段
命名的 role（**注意不是 `NOT_APPLICABLE`**，见下方 FOLLOW-UP），第五个是调色板分配给租客物品隐私标记
与报告排除控件的 role；**内容底**取 `surface` / `surface-container-low` /
`surface-container` / `surface-container-high`。5 × 4 × 2 主题 = 40 对，其中**已登记 7 对/主题**
（focus / boundary / evidence-segment / card-boundary 用途），**缺 13 对/主题、共 26 条**：

| 状态色 | 已有绑定的底 | 缺的底 |
|---|---|---|
| `primary` | `surface`（focus）· `surface-container`（evidence-segment） | `surface-container-low` · `surface-container-high` |
| `tertiary` | `surface-container` | `surface` · `surface-container-low` · `surface-container-high` |
| `error` | `surface-container` | `surface` · `surface-container-low` · `surface-container-high` |
| `outline` | `surface` · `surface-container-low` · `surface-container` | `surface-container-high` |
| `privacy` | 无（只有 `on-privacy`/`privacy` 与 `on-privacy-container`/`privacy-container` 两对文字绑定） | 四个底全缺 |

light 与 dark 缺的**是同一组 13 个组合**，故新表一行同时载两侧比值。40 对全部 ≥ `3.00:1`
（essential icon 档），最低 `outline` on `surface-container-high` = 3.51:1 light / 4.37:1 dark。
沿途复算了文档既有的 8 处比值声明（`on-tertiary`/`tertiary` 5.79、dark 表七行等），**逐个吻合**，
未发现基线错值；**未改任何 token 色值**（forbid#3）。

**放宽的写法**：状态条款保留「只渲染在闸已登记的配对上」这条**封闭**要求（它与 CI 闸自身的
`MISSING_METADATA` 同向，不是负担），改为记录「登记面已覆盖每个状态色 × 每个内容底，故**要不要**
必带一个字形不再取决于它落在哪个中性底上」。**未把它写成全称句**——`privacy-chip` 的盾牌落在
`privacy-container`、相机控件落在 scrim，都不是内容底；新节因此显式把「内容底」与「语义容器 /
相机 scrim（各自带 `on-` role）」分开，免得又造一条被既有实例证伪的全称断言（L309 的病根）。

### A2 · 两处边界行的逐状态判定（两半同时判）

**视觉半** = 每个状态有非颜色的视觉线索；**播报半** = 无自身可见文字时 owner 另外播报
（播报永不替代线索，forbid#2）。

| 行 | 状态 | 视觉半载体 | 播报半 | 判定 |
|---|---|---|---|---|
| `verification-receipt` | `VERIFIED` | 解剖的状态字段可见文字 | 有自身可见文字，义务不触发 | carried |
| `verification-receipt` | `STALE` | 同上 | 同上 | carried |
| `verification-receipt` | `FAILED` | 同上（改写后） | 同上 | **fixed** |
| `verification-receipt` | `UNAVAILABLE` | 同上（改写后） | 同上 | **fixed** |
| `photo-evidence-tile` | `EMPTY_OPTIONAL` | 「这张照片是可选的」可见文字（改写后） | 语义列已整节点播报 state | **fixed**（改写前**歧义**：解剖列了 `requirement` 元素，但没有一句说它在可选态是否渲染） |
| `photo-evidence-tile` | `EMPTY_REQUIRED` | `Required empty names reason` 的可见理由 | 同上 | carried |
| `photo-evidence-tile` | `TEMPORARY` | 「尚未保存」可见文字（改写后） | 同上 | **fixed** |
| `photo-evidence-tile` | `PRESENT` | 图像本身取代占位形状（形状差异，非颜色） | 同上 | carried |
| `photo-evidence-tile` | `PRIVACY` | 解剖声明的 privacy 元素 + 状态条款钉死的盾牌字形 | 同上 | carried |
| `photo-evidence-tile` | `ARCHIVED` | `archived exposes Restore` 的 owner 可见文字 | 同上 | carried |
| `photo-evidence-tile` | `FAILED` | `failed names its error` 的可见错误 | 同上 | carried |

无一状态判 exempt——两行都拿到了自己的载体，故不需要豁免条款。

**两处读法的复核结论，都没有采纳建卡段的假设**：
- `verification-receipt`：没有在「通用字段 / 二值字段」里选一个读法，而是**把歧义消掉**——解剖直接
  写明状态以可见文字命名四态中的哪一个。选读法只会把同一场争论留给下一位读者。
- `photo-evidence-tile`：**「相机复核流是 `TEMPORARY` 的唯一渲染场合」在文档里立不住**。`:1776`
  只描述复核步骤展示什么，从未声明该态不在别处渲染，而 `camera-review-bar` 与
  `photo-evidence-tile` 是两个独立组件行；靠一个证不出来的唯一性去借 owner 的可见文字，正是
  「写下的保证超出证据」。故改为给该行自己的载体，`EMPTY_OPTIONAL` 同批一并补上（它与
  `TEMPORARY` 同属「解剖列了元素、但没有一条确定性行为说该态由谁承载」）。

### A3 · capture Back 定名

定 **`Save and exit`**，即 `STREAM_CAPTURE` 那份页型合同已有的声明；通用 `top-app-bar` 行的
`Back to {parent}` 改为**为声明了别名的页型让位**，并点名唯一那个页型。理由：该页 Back 的确定性
行为是「保存屏障 → Pop 到物业 hub」（页型表与 `:820` 一致），转场表两处也以 `Save and exit` 指代
这个动作；把它改叫 `Back to {parent}` 会让名字不再描述它做的事。让位后全文 `Back to {parent}`
只出现一次、`Back, accessible label` 只出现一次，准入条件 3 的「同一短语」遂有唯一指代。
DoD 用**计数=1**（而非存在性）钉住这一点，M13/M14 各造一处第二声明证明它真会红。

### R4 变异收据（26/26，全部击杀）

基线 `context/DESIGN.md` SHA-256 = `3F3A8784A6A04A610BFEA130EAF227E39D60187B215CDA0D339CBD094164DFCF`
（批前钉、每枚还原后复核，批后仍等此值；L196/L270）。每枚只改一处、选择器要求在文件里**恰好命中一次**，
逐枚跑卡片 `dod_command`，全部退出 1，并各带一条点名失败：

| 枚 | 造坏的东西 | 首条失败（击杀判据） |
|---|---|---|
| M1 | 删 `### State glyph contrast map` 标题 | anchor 该标题 expected 1 found 0 |
| M2 | 删一条 state-icon 绑定 | `"usage":"state-icon"` expected 26 found 25 |
| M3 | 删「每对带实测 light/dark 比值」承诺 | anchor expected 1 found 0 |
| M4 | 把状态条款换回前卡的旧约束 | anchor 新句 expected 1 found 0 |
| M5 | 删「内容底 ≠ 语义容器/scrim」的区分 | anchor expected 1 found 0 |
| M6 | dark 表准入句改回旧版 | anchor expected 1 found 0 |
| M7 | 删最低比值的实测记录 | anchor `at 3.51:1 light and 4.37:1 dark` found 0 |
| M8 | 覆盖句去掉 `in both themes` | anchor expected 1 found 0 |
| M9 | receipt 解剖改回二值读法 | anchor expected 1 found 0 |
| M10 | 去掉 `TEMPORARY` 的可见文字载体 | anchor expected 1 found 0 |
| M11 | 去掉 `EMPTY_OPTIONAL` 的可见文字载体 | anchor expected 1 found 0 |
| M12 | 撤销 top-app-bar 的让位 | anchor expected 1 found 0 |
| M13 | 另处再声明一次 `Back to {parent}` | anchor expected 1 **found 2** |
| M14 | 另处再声明一次 `Back, accessible label` | anchor expected 1 **found 2** |
| M15 | 表里 3.51 写成 3.52 | map row light: printed 3.52 actual 3.51 |
| M16 | 绑定的 backgroundValue 偏离 token | binding value drifts from token `light.privacy` |
| M17 | 改 `outline` token 色值 | binding value drifts from token `light.outline` |
| M18 | 重复一条 state-icon 绑定 | `"usage":"state-icon"` expected 26 **found 27** |
| M19 | 删表里一行 | state glyph map has 12 rows, expected 13 |
| M20 | 对调两行的印刷比值 | map row light: printed 6.25 actual 7.77 |
| M21 | 删 `privacy` 色 token | token map has 55 entries, expected 56 |
| M22 | 把一条绑定的 minRatio 抬到真实比值之上 | ratio below minRatio: `light.outline` on `light.surface-container-high` |
| M23 | 让旧约束与新句**并存**（不替换） | anchor 旧句 expected 0 **found 1** |
| M24 | 在同一行另处复述旧的二值解剖 | anchor 旧解剖 expected 0 **found 1** |
| M25 | 重复一条**非** state-icon 绑定 | duplicate binding `camera.on-scrim` |
| M26 | 把 `privacy` token 改名 | map row names unknown token: `light.privacy` |
| M27 | 把登记功劳记在新表名下（新表只装 20 对里的 13 对） | anchor 归属句 expected 1 found 0 |

**M23–M26 是编排阶段补的，各自封住一处「只由它杀得死」的断言**（L318：剪枝的可靠性不超过它所对照
的变异集）——M23/M24 证明两条 `expected 0` 不是死重（只有「旧句并存」这种改法才单独触发它们）；
M25 单独打 duplicate 分支（M18 先被计数 anchor 杀掉，杀不到它）；M26 单独打 unknown-token 分支
（M21 先被 token 计数杀掉）。据此**未剪任何断言**：每条都有专属击杀者。

**变异脚本自身也踩了一枚并当场加闸**：PowerShell 在逗号列表里会把未加括号的字符串拼接（`'a'` 加上一个换行字面量）折成
额外元素，于是 `$m[3]` 拿到的不是我写的替换串——M18 因此「替换后与原文相同」抛错暴露。修法是给
每处拼接加括号，**并在 runner 里加一条 `$m.Count -ne 4` 的元数守卫**：生成证据的工具必须能对着
它所描述的东西自检，否则一批「全绿」收据可能描述的根本不是我以为的那些变异。

### R3 前本地对抗复核（步骤 4.6）：0 must-block，3 条措辞精度当场修

首次 ship 前派了一个**全新上下文**的复核者，只喂 rubric + 卡片 + 实际 diff，不喂本会话对话。
未发现必须阻断项；它独立复算了全部 13 行表值（双主题）、40 个配对的登记、26 条 state-icon 绑定、
「每主题已有 7 对」与最低比值，并逐条确认 forbid#1–#4 未被触碰、`allow_paths` 未越界、
旧措辞在两份文档里 0 残留。三条**属实**的措辞发现已当场修：

1. **把登记功劳记错了对象**——原句说「state glyph contrast map registers every state color on every
   content surface」，而该表 21 行前自己写着它只装 20 对里的 13 对（另 7 对在 dark 表与 CI metadata）。
   事实（40 对全登记）为真，**归属**为假。改为「every state color **is registered** on every content
   surface, **as the state glyph contrast map records**」，并加锚点 + M27 钉住。
2. **「其它底」只列了两类，文档实有四类**——`missing-evidence-strip` 落 `tertiary`、
   `bottom-action-dock` 落 `secondary`、`button-destructive` 落 `error`、`privacy-action` 落 `privacy`
   都是**基础 role 当底**，而 `camera-shutter`（解剖含 inner state mark，是必带状态标）的底是
   `on-primary`——即**一个 `on-` role 当底**。原句「semantic container 或 camera scrim」漏掉这两类。
   原句未被证伪（它没说「只有这两类」），但卡的 `dod_assert` 靠它撑「不对未覆盖的底做声称」，
   故改成穷尽式列举。复核者另已确认这五对**全部**早已登记，保留的封闭要求对它们仍成立。
3. **privacy role 的说明漏了一半**——调色板给它的是「租客物品隐私标记**与报告排除控件**」，
   原句只写前者且措辞像排他。已补全。

另两条不改代码：`EMPTY_OPTIONAL` 的判定由「无载体」改记为「歧义」（见上表脚注）；
`NOT_APPLICABLE` 的取色缺口记为下方 FOLLOW-UP。

### [FOLLOW-UP] `NOT_APPLICABLE` 在全文没有取色绑定

状态条款要求「dash for not applicable」，但文档**没有任何一处**为该态绑定颜色：`evidence-rail`
只声明 `completeColor` / `missingRequiredColor` / `blockedColor` / `optionalColor` 四个，而它的
`segmentStates` 有五项、含 `NOT_APPLICABLE`；`:1782` 说的是「optional/irrelevant evidence uses
neutral with a dash」，同样没替 `NOT_APPLICABLE` 发言。故本卡收口后，not-applicable 的破折号
「落在哪个底上」有答案了，「用哪个前景色」仍没有。

**不在本卡修**：给一个态指派 role 颜色是**调色决定**，落在 A1「只补登记、不调色」之外，且贴着
forbid#3。按 L113 记为 FOLLOW-UP，**已开卡 `T4-DESIGN-NOT-APPLICABLE-COLOR` 承接**（其 OD-1 推荐
最小修法：让 `evidence-rail` 明说 `NOT_APPLICABLE` 与 `OPTIONAL` 共用 `optionalColor`——`outline`
已由本卡在两主题四个内容底全部登记，那样连新绑定都不需要）。

### 本卡 DoD 不只查锚点

`dod_command` 除 16 条 ASCII 锚点（含 3 条 `expected 0` 的反向断言）外，还**从文档自身重算**：
按 DESIGN.md 写明的 WCAG 公式，用 frontmatter 的 token 值复算每条绑定的比值并核 `minRatio`、
核绑定值未偏离 token、核无重复配对、核 40 个 状态色 × 内容底 配对全部登记、核新表 13 行的
印刷比值与重算值逐位相等。故 A1 的「每对带其实测比值」是**机检**的，不是散文声称的。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-08 | 钉定 DoD（16 条锚点 + 从文档自身重算比值/绑定/配对覆盖）与 `dod_assert`，落交付记录：A1 实测缺口 26 条绑定、A2 十一个状态逐条判定、A3 定名 `Save and exit`、R4 27/27 变异收据；R3 前本地对抗复核 0 must-block，3 条措辞精度当场修，`NOT_APPLICABLE` 取色缺口记 FOLLOW-UP。 |
| 2026-09-08 | 建卡：自 `T4-DESIGN-SYMBOL-CHROME-V2` 拆出。**初版曾承接整个载体分类学与十一处组件行补齐，但 R3 当轮证明该切口不可分割**（撤回补齐后下限声明立即与 rail 规格矛盾），补齐已还原进前卡；本卡收窄为三处真正独立的遗留：对比度绑定、两处边界行、capture Back 双名。 |
