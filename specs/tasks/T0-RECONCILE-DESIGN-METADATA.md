---
id: T0-RECONCILE-DESIGN-METADATA
title: 建立 Field Ledger 可机读设计令牌与组件注册表
depends_on: []
parallelizable_with: [T0-RECONCILE-DATA-AUTHORITY, T0-RECONCILE-LESSONS]
status: todo
branch: T0-RECONCILE-DESIGN-METADATA
worktree: C:\wt\T0-RECONCILE-DESIGN-METADATA
allow_paths:
  - context/DESIGN.md
forbid:
  - 修改产品代码、生成资源或把 skeleton 当成生产视觉先例
  - 引入需要联网才能解析的字体、图标或设计依赖
non_goals:
  - 页面旅程、导航/恢复语义和完整性矩阵
  - 实现 Compose 组件
acceptance:
  - "A1 顶层 schema：front matter 恰有 version/name/description 与 colors/dark-colors/typography/rounded/spacing/iconography/interaction/motion/components 共 12 个顶层键"
  - "A2 颜色和文字：light/dark 各 28 个同名语义颜色角色（共 56 行），并完整登记 12 个 typography roles；状态语义不得只靠颜色"
  - "A3 几何和触控：rounded 6 项、spacing 11 项（共 17 个 px token），明确 48px touch、56px action 与 16px screen gutter"
  - "A4 图标/交互/动效：iconography 5 键、interaction 7 键、motion 8 键共 20 键，含 focus ring、camera scrim、reduced-motion translation 和确定时长"
  - "A5 组件注册表：81 个批准 component id 各出现且仅出现一次；删除、重命名、重复任一 id 均使 exact-set 指纹断言 RED"
dod_command: $r=Get-Content 'context/DESIGN.md' -Raw;$f=[regex]::Match($r,'(?s)^---\r?\n(.*?)\r?\n---').Groups[1].Value;function K($n,$x){$p=if($x){'(?ms)^'+[regex]::Escape($n)+':\r?\n(.*?)(?=^'+[regex]::Escape($x)+':$)'}else{'(?ms)^'+[regex]::Escape($n)+':\r?\n(.*)\z'};$v=[regex]::Match($f,$p).Groups[1].Value;@([regex]::Matches($v,'(?m)^  ([^ :\r\n][^:\r\n]*):')|%{$_.Groups[1].Value})};$n='top','colors','dark-colors','typography','rounded','spacing','iconography','interaction','motion','components';$x=$null,'dark-colors','typography','rounded','spacing','iconography','interaction','motion','components',$null;$s=[Collections.Generic.List[object]]::new();[void]$s.Add(@([regex]::Matches($f,'(?m)^([^ \r\n][^:\r\n]*):')|%{$_.Groups[1].Value}));for($i=1;$i-lt$n.Count;$i++){[void]$s.Add(@(K $n[$i] $x[$i]))};$counts=12,28,28,12,6,11,5,7,8,81;$lines=for($i=0;$i-lt$n.Count;$i++){$a=@($s[$i]);if($a.Count-ne$counts[$i]-or$a.Count-ne@($a|sort -Unique).Count){throw 'count/unique'};$n[$i]+':'+(($a|sort)-join',')};$all=$lines-join[char]10;$hash=([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($all)))).ToLower();if($hash-ne'a30f3a48f259dc83257ee1d8d555c3c8846b20cdc058be3704c9d558a79494a9'){throw 'exact-set'};foreach($p in @('(?m)^  touch: 48px$','(?m)^  action: 56px$','(?m)^  screen-gutter: 16px$','(?m)^  focusRingWidth: 3px$','(?m)^  cameraScrim: "#000000"$','(?m)^  cameraScrimOpacity: 0\.64$','(?m)^  reducedMotionTranslation: 0px$','(?m)^  pressFeedbackMs: 100$','(?m)^  stateChangeMs: 180$','(?m)^  expandMs: 200$','(?m)^  sheetEnterMs: 250$','(?m)^  exitMs: 150$')){if([regex]::Matches($f,$p).Count-ne1){throw 'token value'}}
dod_exit: 0
dod_assert: A1–A5 的完整 schema 指纹、分组计数、唯一 token 值与删除变异全绿；任一增删改名即 RED
review_gate: codex {verdict:pass}
hygiene: 元数据键稳定、无同义重复；视觉状态同时具备非颜色语义
doc_sync: 本卡只建立机读层，后续两卡补充行为合同（R5）
---

# T0-RECONCILE-DESIGN-METADATA

## 产出

在既有 `context/DESIGN.md` 前加入可机读的 Field Ledger 设计系统：颜色、排版、间距、形状、动效、触控/无障碍和具名组件状态。保持单文件真相源，不在本卡扩写页面旅程。

## 验收

执行 front matter 的 `dod_command`，并确认本卡净 diff 低于 R3 预算。
