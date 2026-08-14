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
| `lessons-archive.md` | 已归档/合并的 `docs/lessons/LEDGER.md` **lesson 条目整块**（`-LessonIds` 显式策展输入驱动，无自动判定） | 检索=裸 grep（无 index，条目本身即是完整块） |

## 维护（勿手工编辑索引/归档正文——由脚本投影生成）
- 生成/更新：`pwsh -File scripts\archive.ps1`（**幂等**；`-DryRun` 先预览会搬什么、写零文件——含 lessons：
  无效/拒绝的 id 在 `-DryRun` 下同样非零退出，预览模式也 fail-closed，不会把问题伪装成绿）；
  lessons 搬运另加 `-LessonIds L<n>[,L<n>...]`（如 `-LessonIds L32,L34`，逗号形式外部调用也可用；id 须规范形式
  无前导零，`L02` 这类别名会被拒），两路径正交、互不影响。
- 搬运规则（保守，只搬定局闭合项）：债 status=`paid`/`accepted` → 搬；卡 status=`merged` → 搬；
  `open`/`carded`/`todo`/`in-progress`/`in-review` 一律留活文件。lesson 条目**无 status 字段可依**，恒为
  显式策展——由 `-LessonIds` 手工/agent 指定 id 才搬，拒搬 LEDGER 当前最高 id（防 Next-Id 重铸撞号）、
  拒搬两侧皆查无的未知 id（fail-closed 防手滑打错）。
- **只搬不删**——append-only 语义在归档侧延续，还债/交付轨迹一条不丢。
- 何时跑：每张卡合并后的 R5 doc-sync 顺手跑一次（幂等、-DryRun 可先预览）；或发现活文件里闭合项开始堆积时手动跑。
- 锁：`scripts/selftest.ps1` 子闸 **12e** 以 hermetic 夹具验证热/冷分区、索引条数一致、幂等（含 lessons 子夹具：
  逗号形式精确文本搬运、幂等、拒最高 id、拒未知 id、空 token 与 DryRun 亦 fail-closed、归档与 LEDGER
  双侧暂存原子替换——任一侧写失败双文件零丢失、两侧并存态重跑自愈（仅内容逐字一致时；不一致拒改留人工）。

> 这是「智能索引 + 冷存」的落地：冷数据仍在仓内、版本化、可 grep，只是移出了每轮必读的热路径。
