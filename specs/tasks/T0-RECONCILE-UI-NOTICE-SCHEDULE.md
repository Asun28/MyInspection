---
id: T0-RECONCILE-UI-NOTICE-SCHEDULE
title: 对齐通知与日程实现卡的设计系统指针
depends_on: [T0-RECONCILE-UI-COVERAGE]
status: todo
branch: T0-RECONCILE-UI-NOTICE-SCHEDULE
worktree: C:\wt\T0-RECONCILE-UI-NOTICE-SCHEDULE
allow_paths:
  - specs/tasks/T4-NOTICES.md
  - specs/tasks/T4-SCHEDULE.md
forbid:
  - 修改产品代码、法定规则或通知送达语义
  - 复制 DESIGN.md 的完整组件规格
non_goals:
  - 采集、报告、备份、健康或清除卡同步
acceptance:
  - "A1 Notices 指针：plan_ref 合规/report matrix，恰有 A1–A4，覆盖 center/compose、draft/valid/blocked/copied/recorded、compliance correction、share boundary 与焦点返回"
  - "A2 Copy/record 真实性：acceptance 明确 Copy 只等于复制，Record delivery 要 method/time 且重新校验；无 sent 假状态、无后台发送或地址锁屏泄露"
  - "A3 Schedule 指针：plan_ref list/discovery matrix，恰有 A1–A4，覆盖 due/empty/filter/state badge、进入 property/inspection 路由、13周本地提醒、离线与权限失败动作"
  - "A4 去重与范围：两卡只增 plan_ref/acceptance，不复制 component matrix、改法律规则/送达语义或产品代码"
dod_command: function ExactIds($r){$q=[regex]::Match($r,'(?ms)^acceptance:\r?\n((?:  - [^\r\n]+\r?\n)+)').Groups[1].Value;$a=@([regex]::Matches($q,'(?m)^  - \"?([^ \"\r\n]+) ')|%{$_.Groups[1].Value});if($a.Count-ne4-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) @('A1','A2','A3','A4'))){throw 'acceptance ids'}};function Must($s,$ps){foreach($p in $ps){$m=[regex]::Match($s,$p);if(-not$m.Success-or[regex]::IsMatch($s.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}}};$n=Get-Content 'specs/tasks/T4-NOTICES.md' -Raw;$s=Get-Content 'specs/tasks/T4-SCHEDULE.md' -Raw;ExactIds $n;ExactIds $s;Must $n @('(?m)^plan_ref: context/DESIGN\.md#backup-report-health-and-compliance-component-matrix$','(?m)^  - \"A1 (?=[^\"\r\n]*center)(?=[^\"\r\n]*compose)(?=[^\"\r\n]*draft)(?=[^\"\r\n]*valid)(?=[^\"\r\n]*blocked)(?=[^\"\r\n]*copied)(?=[^\"\r\n]*recorded)[^\"\r\n]+\"$','(?m)^  - \"A2 (?=[^\"\r\n]*Copy)(?=[^\"\r\n]*Record delivery)(?=[^\"\r\n]*method)(?=[^\"\r\n]*time)(?=[^\"\r\n]*not sent)[^\"\r\n]+\"$','(?m)^  - \"A3 (?=[^\"\r\n]*compliance correction)(?=[^\"\r\n]*share boundary)[^\"\r\n]+\"$','(?m)^  - \"A4 (?=[^\"\r\n]*focus return)(?=[^\"\r\n]*lock-screen address)(?=[^\"\r\n]*no background send)[^\"\r\n]+\"$');Must $s @('(?m)^plan_ref: context/DESIGN\.md#structure-list-and-discovery-component-matrix$','(?m)^  - \"A1 (?=[^\"\r\n]*due)(?=[^\"\r\n]*empty)(?=[^\"\r\n]*filter)(?=[^\"\r\n]*state badge)[^\"\r\n]+\"$','(?m)^  - \"A2 (?=[^\"\r\n]*property)(?=[^\"\r\n]*inspection route)[^\"\r\n]+\"$','(?m)^  - \"A3 [^\"\r\n]*13-week local reminder[^\"\r\n]+\"$','(?m)^  - \"A4 (?=[^\"\r\n]*offline)(?=[^\"\r\n]*permission failure)[^\"\r\n]+\"$');if(($n+$s)-match'(?m)^### .*component matrix$'){throw 'copied matrix'}
dod_exit: 0
dod_assert: A1–A4 的 exact sets、逐块语义和删除变异全绿；删改任一要求即 RED
review_gate: codex {verdict:pass}
hygiene: 卡内只留 owning acceptance，不复制组件矩阵
doc_sync: 两张卡与 UI 覆盖索引一致（R5）
---

# T0-RECONCILE-UI-NOTICE-SCHEDULE

## 产出

给通知和日程两张卡补充最小设计系统指针，覆盖列表、状态、空态、复制确认与本地提醒，不改变既有合规语义。

## 验收

执行 front matter 的 `dod_command`。
