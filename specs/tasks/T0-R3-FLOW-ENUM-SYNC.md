---
id: T0-R3-FLOW-ENUM-SYNC
title: 把真实 diff 预算闸补进每一处确定性闸枚举，并各配锚定断言（承接 T0-R3-DIFF-BUDGET 的 A13）
depends_on: [T0-R3-DIFF-BUDGET]
parallelizable_with: []
status: todo
branch: T0-R3-FLOW-ENUM-SYNC
worktree: C:\wt\T0-R3-FLOW-ENUM-SYNC
allow_paths:
  - scripts/task.ps1
  - scripts/selftest.ps1
  - docs/DEVOPS-WORKFLOW.md
forbid:
  - 改预算闸的度量口径、边界或 fail-closed 语义（那是母卡 T0-R3-DIFF-BUDGET 的契约）
  - 提高 MaxChangedLines / MaxDiffChars，或给枚举断言加「跳过」开关
  - 用全文 IndexOf 或整份 stdout 匹配代替锚到枚举文本块自身的有序子序列断言
non_goals:
  - 实现或修改预算闸本身（母卡已做）
  - 同步 CLAUDE.md 的 ship 流程描述与 specs 计划 §5 的状态表（两者都不在 allow_paths，另开卡）
  - 硬化 diff 输入可信度 / 提交身份（T0-R3-DIFF-INPUT-TRUST / T0-R3-MEASURED-OID-BINDING）
acceptance:
  # 作者声明的验收清单：以下是本卡认为「完成」所需的事实，每条应有可证伪测试。
  # **这是一份声明，不改变任何评审语义**——裁决仍完全按 docs/QUALITY-RUBRIC.md 现行 rubric 判，
  # 清单未列到的问题照常按现行 rubric 处理（含其现行的 [FOLLOW-UP] 适用条件）。
  # 「把清单当排他性判据、清单外一律 FOLLOW-UP」是上游提案 Asun28/claude-devops-scaffold#203
  # 的内容，**上游落地前本仓不采用**。
  - "A1 发现面用命令固定，不靠人眼：以 `grep -n '确定性闸\\|防泄露' scripts/task.ps1 docs/DEVOPS-WORKFLOW.md` 的全量命中为权威清单，卡内逐行列出命中位置与「是否属于枚举面」的判定；新增枚举面须同时登记断言（L97：横切纪律先 grep 出全部权威面，一次性纳入）"
  - "A2 母卡遗漏的五处补齐：scripts/task.ps1:381（TD85-RESUME 「仅 merge-safe」分支打印给操作者的手动补跑清单）· :710 · :712 · :716（三处 saga 恢复分支消息）· docs/DEVOPS-WORKFLOW.md:144（最后手段段「以下命令本身不会重跑 …」的否定式枚举），五处均须含预算闸"
  - "A3 每处枚举各有专属锚定断言：断言锚到该枚举**自身的文本块**（连续非空行或该注释块），在块内做有序子序列判定；删任一处枚举里的预算闸即让**该处专属**断言变红，且不得连带别处一起红（判据分类器：非零且命中该条断言文本才算击杀）"
  - "A4 段落级提取而非单行：提取器须返回锚点所在的**连续文本块**——task.ps1 的 ship 注释横跨三行（:11-13），单行提取会让 push / 开 PR / Codex 评审 三步落在锚点行之外而恒不可达；一枚夹具用三行注释证明单行提取器会漏判"
  - "A5 相位标签也在枚举面内：`Step '真实 diff 预算闸'` 这个相位标签本身有一条锚定断言（母卡 A13 未覆盖它）"
  - "A6 顺序而非仅存在：每条断言判的是**有序**子序列（防泄露闸 → 真实 diff 预算 → push → 开 PR → Codex 评审），把预算闸挪到 push 之后即变红；一枚乱序夹具证明之"
  - "A7 静态断言的锚点不得被文档提及满足：ship 前置的静态顺序检查须锚到 `Step '真实 diff 预算闸'` 而非 task.ps1 里**首个** `-SizeOnly` 字面量——否则某处新增的说明性注释提到 -SizeOnly 就能真空满足它；一枚在真实调用点之上插入注释的夹具证明之"
  - "A8 DEVOPS-WORKFLOW:141 的恢复命令无孤立 CR：该行须逐字为 `scripts\\review.ps1`；全文件 lone-CR（0x0D 不跟 0x0A）计数断言为 0，并配一枚植入孤立 CR 的变异使之变红（L193：写文件的工具层会把反斜杠-r 字面静默解码成回车，落盘后肉眼与显示层都看不见）"
  - "A9 selftest 两分片绿：`-Shard workflow` 与 `-Shard seeded` 均 exit 0；本卡自身 `review.ps1 -SizeOnly` 亦 exit 0"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/task.ps1 -SimpleMatch '真实 diff 预算' -Quiet) -and ((Select-String -Path scripts/task.ps1 -SimpleMatch '防泄露闸）' -AllMatches).Count -eq 0))) { exit 1 }"
dod_exit: 0
dod_assert: task.ps1 里再无「以『防泄露闸）』收尾的确定性闸枚举」（即五处遗漏已补齐），且预算闸出现在流程注释中。A1–A9 每条都有可证伪测试。
review_gate: codex {verdict:pass}
hygiene: 每处枚举一条锚定断言 + 一枚只删该处预算闸的单句变异；不为同一处枚举写两条重复断言
doc_sync: DEVOPS-WORKFLOW 的 ship / resume / 闸门保真 / 最后手段各段（本卡即改动本体）；CLAUDE.md 与 specs 计划 §5 的同步另开卡

---

# T0-R3-FLOW-ENUM-SYNC

## 来源

`T0-R3-DIFF-BUDGET`（PR #128）第 2 轮 R3 的 finding #4 点名 `scripts/task.ps1:13,381,592-594,704-716` 与
`docs/DEVOPS-WORKFLOW.md:87,97-154` 的确定性闸枚举须补上预算闸。母卡修了 13 / 592-594 / 704-705 与
DEVOPS 87/97，**漏了五处**（见 acceptance A2），且母卡为此写的提取器只取锚点**单行**，而 `task.ps1` 的
ship 注释横跨三行——于是那条断言本身在当前树上就是红的（`-Shard seeded` 因此无法 exit 0）。

母卡随后触到自己的 60,000 字符上限（committed 51,882、加上修复 63,023），按其自身教义
「超限就拆、不许提高上限」再拆一次；A13 这条**文本同步**契约与母卡其余的**行为度量**契约无共享代码面，
是天然的缝。母卡 A13 槽位永久留空、编号不重排（acceptance id 是跨文件引用的锚）。

## 为什么这五处值得单独一张卡

`task.ps1:381` 是**运行期打印给操作者**的恢复指引：分支已 push、只剩 merge-safe 时，它告诉人
「先手动补跑全部确定性闸（DoD、verify、范围闸、许可闸、防泄露闸）」。清单里没有预算闸，照着做的人
就会在从未跑过预算闸的情况下合并——而这正是预算闸存在的那条路径。`:710/:712/:716` 三处 saga 恢复
分支同理，`DEVOPS-WORKFLOW.md:144` 的否定式枚举则低估了「最后手段」路径的风险。

母卡已经证明「一处枚举漏一次就多打一轮 R3」：两轮 R3 各抓到不同的遗漏。所以本卡的重点不是把这五处
文字改对（那是十分钟的活），而是**让漏改必红**——每处一条锚定断言 + 一枚单句变异，且发现面由
grep 命令而非人眼固定。

## 被否决的替代

- **并进母卡**：母卡已超预算 3,023 字符，再加五处编辑与五条断言只会更远。
- **只改文字不加断言**：下一次有人新增一处枚举时同样静默漏掉，本卡等于没做。
- **一条全文断言覆盖所有枚举**：全文 IndexOf 无法定位是哪一处漏了，且任一处出现该词即满足——
  正是 L165 要根除的「断言宽于契约」形态。
