# 技术债追踪器（持续重构，append-only）

> **动机**（OpenAI《Harness Engineering》核心实践之一）：把技术债当**持续的小额还款**，而非**周期性大修**。
> 每发现一处「能跑但偏离既定模式/契约」的地方，**立刻登记**——不要等它攒成大重构。
>
> **形态**：本表 append-only（只追加、改状态，不删行）。每条债项最终应转成一张任务卡（`specs/tasks/<id>.md`）偿还，
> 或在 `docs/adr/` 记一条「有意接受此债」的决定。
> **热/冷分离（省上下文 · TD86）**：本活表只留 `open`/`carded` 的**在飞**债项；`paid`/`accepted` 的**已闭合**整行由
> `scripts/archive.ps1` 搬到 `specs/archive/tech-debt-archive.md`（append-only 语义在归档侧延续、轨迹不丢）+ 精简索引
> `specs/archive/tech-debt-index.md`（一行一条、可 grep）。查已还债项来龙去脉：先 grep 索引、再按 id 取归档整行；
> 闭合项堆积时在 R5 doc-sync 后手动跑一次压缩（幂等，`-DryRun` 可先预览）。
> **与经验系统的区别**：`docs/lessons/LEDGER.md` 记「**工具链/方法**的坑」（怎么干活）；本表记「**本代码库当前的具体偏离**」（哪里欠债）。
> **一行怎么写**：7 列固定不加列（三个消费者按列数解析），「偏离了什么」那格按**后果 / 修法 / 可测 / 前置**四段写，
> WHO 与根因归解决层（卡的 `diagnosis`）——细则与样板见 `specs/README.md`「技术债的一行怎么写」。

## 状态枚举
`open`（已登记待还） · `carded`（已开卡偿还，注明卡 id） · `paid`（已还清，注明 PR/commit） · `accepted`（有意接受，注明 ADR）

## 债项
| id | 发现日 | 位置 | 偏离了什么（债） | 严重度 | 状态 | 偿还指针 |
|---|---|---|---|---|---|---|
| _示例_ | 2026-06-15 | `backend/app/...` | 直接拼路径，未经 `core/storage.py` 派生（违反关键不变量） | major | open | — |

| TD1 | 2026-08-14 | `scripts/selftest.ps1`(闸15n · 闸17aa(8)) | **对上游脚手架 bug 的本地补丁 ×2，待回搬**：①15n 把 `TEMPLATE-README.md` 硬列为 L86-WT 权威面，但该文件属元仓专属物（`-Cleanup`/`-Retrofit` 不下发）——已初始化下游必红；②17aa(8) 夹具硬依赖元仓真卡 `specs/tasks/T11-R3-BASELINE.md`（活位或 archive），下游缺卡时 Get-Content 直接终止整跑（且它藏在 `if (-not $fail)` 后，前面全绿才暴露）。二者同为上游 TD74「无 CI 支路测生成下游」的实例。后果：本仓 selftest.ps1 与上游漂移，backfill 对照须带上这两块 diff / 修法：把 `$isPostInit` 跳过（15n）与缺卡跳过（17aa(8)）两补丁回搬上游元仓 / 可测：下游（无 TEMPLATE-README、无 T11 卡）selftest 全绿、元仓两面仍必查 / 前置：无 | minor | open | — |
















<!-- 新债项追加到上表。偿还时改 status + 填指针；勿删行（保留还债轨迹）。 -->

## 可选：背景重构 agent（OpenAI 持续重构循环）
> OpenAI 用后台 agent 定期扫描偏离、自动提重构 PR、小修快速合并。本仓不内置该自动化（避免无人值守写操作），
> 但可手动等价：每若干张卡后跑一次「对照 `CLAUDE.md` 关键不变量 + `docs/QUALITY-RUBRIC.md` §2 扫描偏离」，命中即在此登记 → 开卡。
