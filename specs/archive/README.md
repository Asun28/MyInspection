# specs/archive/ — 冷存（cold storage）

已**闭合**的工件从热路径搬到这里：活文件（`specs/tech-debt-tracker.md`、`specs/tasks/*.md`）只留**在飞项**，
新任务卡 / triage 心跳 / 「这坑还没还过」检索不再吞整段已还历史——止住新任务 token 随历史无界增长（TD86 / 卡 `T28-CONTEXT-COMPACT`）。

## 内容
| 文件 | 是什么 | 怎么用 |
|---|---|---|
| `tech-debt-index.md` | 已闭合债项（`paid`/`accepted`）**精简索引**（一行一条、可 grep） | 查「这坑还没还过？」先 grep 这里 |
| `tech-debt-archive.md` | 同上债项的**整行**（含完整还债指针 = 机构记忆，append-only） | 命中索引后按 id 取整行细节 |
| `cards-index.md` | 已 `merged` 卡的**精简索引**（id · 状态 · 标题） | 找某张老卡先看这里 |
| `tasks/<id>.md` | 已 `merged` 卡**原文** | 需要完整卡时打开 |
| `lessons-archive.md` | 已归档/合并的 `docs/lessons/LEDGER.md` **lesson 条目整块**（`-LessonIds` 搬运器仍是唯一写入口） | `lessons.ps1 search` 统一召回并标 `[archived]`；也可裸 grep |

## 维护（勿手工编辑索引/归档正文——由脚本投影生成）
- 生成/更新：`pwsh -File scripts\archive.ps1`（**幂等**；`-DryRun` 先预览会搬什么、写零文件——含 lessons：
  无效/拒绝的 id 在 `-DryRun` 下同样非零退出，预览模式也 fail-closed，不会把问题伪装成绿）；
  lessons 搬运另加 `-LessonIds L<n>[,L<n>...]`（如 `-LessonIds L32,L34`，逗号形式外部调用也可用；id 须规范形式
  无前导零，`L02` 这类别名会被拒），两路径正交、互不影响。
- 搬运规则（保守，只搬定局闭合项）：债 status=`paid`/`accepted` → 搬；卡 status=`merged` → 搬；
  `open`/`carded`/`todo`/`in-progress`/`in-review` 一律留活文件。lesson 条目**无 status 字段可依**，故本脚本
  自己从不判定该搬谁：id 恒由 `-LessonIds` 显式传入（手工或 agent 指定），并拒搬 LEDGER 当前最高 id
  （防 Next-Id 重铸撞号）、拒搬两侧皆查无的未知 id（fail-closed 防手滑打错）。
- 那份 id 清单可由 `lessons.ps1 archive -DryRun` 机械预筛得出（**选择规则的权威表述见 `docs/LESSONS.md` §3 PURIFY**，
  此处不复述）。去掉 `-DryRun` 后仍只转调本文件既有的 `archive.ps1 -LessonIds`，不会出现第二套搬运器；
  预览本身也是透传给它跑的，故上面四类拒绝在预览里同样出现、退出码一致。候选只是预筛，不取代人工复核。
- **只搬不删**——append-only 语义在归档侧延续，还债/交付轨迹一条不丢。
- 何时跑：每张卡合并后的 R5 doc-sync 顺手跑一次（幂等、-DryRun 可先预览）；或发现活文件里闭合项开始堆积时手动跑。
- 锁：`scripts/selftest.ps1` 子闸 **12e** 以 hermetic 夹具验证热/冷分区、索引条数一致、幂等（含 lessons 子夹具：
  逗号形式精确文本搬运、幂等、拒最高 id、拒未知 id、空 token 与 DryRun 亦 fail-closed、归档与 LEDGER
  双侧暂存原子替换——任一侧写失败双文件零丢失、两侧并存态重跑自愈（仅内容逐字一致时；不一致拒改留人工）。
  选择器一侧另有 **2d**（候选集逐字相等 + 四类排除面各一条夹具，含常驻引用的方括号/裸写/范围端点三形态）、
  **2e**（元数据只认规范 meta 行，十四条敌意夹具读写两侧均留热、零写入、非零退出）、
  **2f**（预览透传搬运器拒绝 / 写失败非零透传 / 两侧并存自愈 / 标题口径一致）与 **16a**（热∪冷并集接线）。

> 这是「智能索引 + 冷存」的落地：冷数据仍在仓内、版本化、可 grep，只是移出了每轮必读的热路径。
