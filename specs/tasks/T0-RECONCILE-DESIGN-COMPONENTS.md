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
  - "A1 视觉可验证：light/dark 对比阈值表具名 4.50:1、7.00:1、3.00:1，CI contrast metadata、visual physics、typography/layout/elevation/shapes 不产生第二套 token"
  - "A2 组件闭包：九个 component family matrix 共恰好 81 个 backtick id rows，与 machine-readable registry 一一对应；每行有 anatomy、states、deterministic behaviour、semantics/focus、Compose base"
  - "A3 完整状态：导航/结构、证据/输入、反馈/决策、相机、列表/发现、表单/选择、进度/恢复、历史/媒体、备份/健康/合规九族均有 empty/loading/error/disabled/read-only/commit 等适用状态"
  - "A4 动效与触觉：100/150/180/200/250ms 与 reduced-motion 零位移一致；无限 pulse、颜色-only、焦点丢失和 layout-shift 动效被禁止"
  - "A5 无障碍与危险操作：48dp、200% 字号、TalkBack 顺序、焦点回退、状态文字+图标、预览影响/输入确认/执行中阻止重复和 Back 均为硬合同"
dod_command: $r=Get-Content 'context/DESIGN.md' -Raw;function Must($s,$ps){foreach($p in $ps){if($s-notmatch$p){throw $p}}};function Exact($a,$e){$a=@($a);$e=@($e);if($a.Count -ne $e.Count -or $a.Count -ne @($a|Sort-Object -Unique).Count -or (Compare-Object ($a|Sort-Object) ($e|Sort-Object))){throw 'exact-set mismatch'}}; $h=@([regex]::Matches($r,'(?m)^### (Navigation and structure component matrix|Evidence and input component matrix|Feedback and decision component matrix|Camera component matrix|Structure, list, and discovery component matrix|Form and selection component matrix|State, progress, and recovery component matrix|History, evidence, and media component matrix|Backup, report, health, and compliance component matrix)$')|%{$_.Groups[1].Value}); Exact $h @('Navigation and structure component matrix','Evidence and input component matrix','Feedback and decision component matrix','Camera component matrix','Structure, list, and discovery component matrix','Form and selection component matrix','State, progress, and recovery component matrix','History, evidence, and media component matrix','Backup, report, health, and compliance component matrix'); $meta=[regex]::Match($r,'(?ms)^components:\r?\n(.*?)(?=^---$)').Groups[1].Value;$registry=@([regex]::Matches($meta,'(?m)^  ([a-z0-9-]+):')|%{$_.Groups[1].Value});$rows=@([regex]::Matches($r,'(?m)^\| `([a-z0-9-]+)` \|[^\r\n]+$'));$ids=@($rows|%{$_.Groups[1].Value});Exact $ids $registry;if($ids.Count -ne 81 -or ($rows|?{($_.Value.Trim('|').Split('|')|?{-not $_.Trim()}).Count})){throw 'component row fields'};$must='(?m)^### Contrast threshold contract$','`4\.50:1`','`7\.00:1`','`3\.00:1`','(?m)^### Element completeness gate$','(?m)^## Motion and haptics$','reduced motion','(?m)^## Accessibility contract$','`48dp`','`200%`','TalkBack','100ms','150ms','180ms','200ms','250ms','infinite pulse','layout shift';foreach($p in $must){$m=[regex]::Match($r,$p);if(-not $m.Success -or [regex]::IsMatch($r.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}};if($rows|?{($_.Value.Trim('|').Split('|')).Count-ne6}){throw 'component columns'};Must $r @('(?m)^3\. Empty, loading, populated, busy, success, recoverable failure, blocking failure, offline/provider, permission, and low-storage states','(?m)^4\. Every control.s enabled, pressed, focused, selected, busy, error, and disabled rendering','(?m)^5\. TalkBack role/name/state/value, focus entry/return, 48dp touch target, 200% text reflow, dark theme, and reduced-motion outcome','(?m)^6\. Destructive scope and confirmation, sensitive-surface protection, and external-sharing boundary','先预览影响，再明确动词/输入确认，执行中禁止重复/Back','Status, save, camera, and compliance changes are announced as state changes')
dod_exit: 0
dod_assert: A1–A5 的 exact sets、逐块语义和删除变异全绿；删改任一要求即 RED
review_gate: codex {verdict:pass}
hygiene: prose 引用机读 token/组件 id，避免第二套数值或命名真相源
doc_sync: 与前两张设计卡形成一个完整 DESIGN.md（R5）
---

# T0-RECONCILE-DESIGN-COMPONENTS

## 产出

为已登记的组件和页面旅程补齐视觉、交互、错误、空态、无障碍与动效合同，明确对比度和触控硬下限，并保持设计系统可在无网络环境中实现和验证。

## 验收

执行 front matter 的 `dod_command`，再运行 diff 预算与文档链接检查。
