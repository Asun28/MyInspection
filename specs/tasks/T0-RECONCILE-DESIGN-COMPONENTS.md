---
id: T0-RECONCILE-DESIGN-COMPONENTS
title: 补齐 Field Ledger 组件合同、对比度、动效与无障碍规则
depends_on: [T0-RECONCILE-DESIGN-JOURNEYS]
status: todo
branch: T0-RECONCILE-DESIGN-COMPONENTS
worktree: C:\wt\T0-RECONCILE-DESIGN-COMPONENTS
allow_paths:
  - context/DESIGN.md
forbid:
  - 修改产品代码、组件 id 或已签认产品范围
  - 仅靠颜色表达状态、无限动画、阻断式离线提示或危险操作无确认
non_goals:
  - 页面覆盖索引与下游实现卡同步
  - 生成 Figma、位图或 Compose 代码
acceptance:
  - "A1 light/dark 对比阈值具名 4.50:1、7.00:1、3.00:1；visual/typography/layout/elevation/shapes 不产生第二套 token"
  - "A2 九个 matrix 均非空，其 rows 合计恰好覆盖 81 registry ids；每行有 anatomy/states/behaviour/semantics/Compose base"
  - "A3 九族完整覆盖适用的 empty/loading/error/disabled/read-only/commit 状态"
  - "A4 100/150/180/200/250ms 与 reduced-motion 零位移一致；禁无限 pulse、颜色-only、焦点丢失和 layout shift"
  - "A5 48dp、200%、TalkBack、焦点回退、状态文字+图标、危险操作预览/确认/阻止重复与 Back 为硬合同"
dod_command: function Run($id){$p=@("specs/tasks/$id.md","specs/archive/tasks/$id.md")|?{Test-Path $_}|select -First 1;$l=Get-Content $p|?{$_-like'dod_command:*'};&([scriptblock]::Create($l.Substring(13)))};Run 'T0-RECONCILE-DESIGN-JOURNEYS';$r=Get-Content 'context/DESIGN.md' -Raw;function Must($s,$ps){foreach($p in $ps){if($s-notmatch$p){throw $p}}};function Exact($a,$e){$a=@($a);$e=@($e);if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'exact-set'}};$h=@([regex]::Matches($r,'(?m)^### (.+ component matrix)$')|%{$_.Groups[1].Value});Exact $h @('Navigation and structure component matrix','Evidence and input component matrix','Feedback and decision component matrix','Camera component matrix','Structure, list, and discovery component matrix','Form and selection component matrix','State, progress, and recovery component matrix','History, evidence, and media component matrix','Backup, report, health, and compliance component matrix');$rows=@();foreach($name in $h){$b=[regex]::Match($r,('(?ms)^### '+[regex]::Escape($name)+'\r?\n(.*?)(?=^### |^## |\z)')).Groups[1].Value;$m=@([regex]::Matches($b,'(?m)^\| `([a-z0-9-]+)` \|[^\r\n]+$'));if(-not$m.Count){throw 'empty matrix'};$rows+=$m};$meta=[regex]::Match($r,'(?ms)^components:\r?\n(.*?)(?=^---$)').Groups[1].Value;$registry=@([regex]::Matches($meta,'(?m)^  ([a-z0-9-]+):')|%{$_.Groups[1].Value});$ids=@($rows|%{$_.Groups[1].Value});Exact $ids $registry;$bad=@($rows|?{($_.Value.Trim('|').Split('|')).Count-ne6-or@($_.Value.Trim('|').Split('|')|?{-not$_.Trim()}).Count});if($ids.Count-ne81-or$bad.Count){throw 'component rows'};$must='(?m)^### Contrast threshold contract$','`4\.50:1`','`7\.00:1`','`3\.00:1`','(?m)^### Element completeness gate$','(?m)^## Motion and haptics$','reduced motion','(?m)^## Accessibility contract$','`48dp`','`200%`','TalkBack','100ms','150ms','180ms','200ms','250ms','infinite pulse','layout shift';foreach($p in $must){$m=[regex]::Match($r,$p);if(-not$m.Success-or[regex]::IsMatch($r.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}};Must $r @('(?m)^3\. Empty, loading, populated, busy, success, recoverable failure, blocking failure, offline/provider, permission, and low-storage states','(?m)^4\. Every control.s enabled, pressed, focused, selected, busy, error, and disabled rendering','(?m)^5\. TalkBack role/name/state/value, focus entry/return, 48dp touch target, 200% text reflow, dark theme, and reduced-motion outcome','(?m)^6\. Destructive scope and confirmation, sensitive-surface protection, and external-sharing boundary','先预览影响，再明确动词/输入确认，执行中禁止重复/Back','Status, save, camera, and compliance changes are announced as state changes')
dod_exit: 0
dod_assert: live/archive 前置卡、九矩阵、81 registry 与 A1–A5 全绿
review_gate: codex {verdict:pass}
hygiene: prose 引用机读 token/组件 id，避免第二套真相源
doc_sync: 与前两张设计卡形成完整 DESIGN.md（R5）
---

# T0-RECONCILE-DESIGN-COMPONENTS

## 产出

补齐视觉、交互、错误、空态、无障碍与动效合同，保持离线可实现/验证。

## 验收

执行 `dod_command` 与 diff 预算检查。
