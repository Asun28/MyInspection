---
id: T0-GATE-FIXFORWARD
title: 许可闸路径比较改 OS 感知 + 发布清单收敛为单一解锁路径（T0-GATE-HARDENING 事后 R3 两条 block 的 fix-forward）
depends_on: [T0-GATE-HARDENING]
status: todo
branch: T0-GATE-FIXFORWARD
worktree: C:\wt\T0-GATE-FIXFORWARD
allow_paths:
  - scripts/check-licenses.ps1
  - scripts/selftest.ps1
  - docs/RELEASE-CHECKLIST.md
forbid:
  - 改 docs/LICENSE-POLICY.md（已核 §3.2/§5 与本卡改动一致，无需同步——见「权威面已扫齐」；若真发现残留矛盾 → 记 [FOLLOW-UP] 报告，不在本卡改）
  - 为过闸弱化任何既有断言（尤其 17cc/17dd 已有的变异判据与 SHA 守卫）
  - 在本卡内做逐坐标 Gradle 许可扫描（那是 T0-LICENSE-SCANNER）
  - 动卡片元数据 / tech-debt 表 / TASK-BOARD / CLAUDE.md（L18/L212：只在 master 做，见「落点声明」）
  - 与 T0-HARNESS-PERF / T0-LICENSE-SCANNER 并行开卡（三卡共用 scripts/selftest.ps1，见「排队约束」）
non_goals:
  - 偿还 TD2（逐坐标机检 = T0-LICENSE-SCANNER）
  - 让 `-Strict` 在扫描器落地前能退出 0（人裁已定：删掉人工替代，不造人工放行机制）
  - 重造 17cc/17dd 的变异 harness（复用它，不另起一套）
  - 补测 T0-GATE-HARDENING 已合并的两枚断言（它们已自带 in-selftest 变异证明，见「-SkipRed 到底跳过了什么」）
dod_command: pwsh -NoProfile -Command ". ./scripts/check-licenses.ps1 -AsLibrary; if ((Test-GradleNameEquals -Left 'Build' -Right 'build' -Comparison ([System.StringComparison]::Ordinal)) -or (-not (Test-GradleNameEquals -Left 'Build' -Right 'build' -Comparison ([System.StringComparison]::OrdinalIgnoreCase)))) { exit 1 }"
dod_exit: 0
dod_assert: 单一比较器 `Test-GradleNameEquals`（名字即契约，内部实现自由）在 Ordinal 下判 'Build' ≠ 'build'、在 OrdinalIgnoreCase 下判相等——本条 dod_command 已机检，轻量且评审沙箱可复跑（L60/L62）。**另须（沙箱外、本机 + CI 强制）**：`pwsh -NoProfile -File scripts\selftest.ps1` 全绿，且 17cc 新增断言证明**五个调用点全部**改用该比较器（Names / SkipDirs / SkipRelativePaths / AndroidSkipDirs / `android/` 前缀），夹具按 OS 分支（见「平台陷阱」）；RELEASE-CHECKLIST 的 Gradle 阻断项只剩**一条**解锁路径且由一枚 selftest 断言钉住。每枚新断言配单句删除变异 + 判据分类器（非零**且**命中指定断言文本才算数，L165），红/绿输出贴进最终报告。
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）；新夹具复用 17cc 既有 harness，不新起一套
doc_sync: 本卡「变异证据」节由编排者在 R5 于 master 追加；T0-GATE-HARDENING 卡 status 与事后 R3 结论；TASK-BOARD 补登 W0 追加卡（R5）
---

# T0-GATE-FIXFORWARD

## 为什么有这张卡

`T0-GATE-HARDENING` 在 `5ba3319` **绕开 `task.ps1 ship` 直接合并**（`-SkipRed` ×2，合并前 R3 从未跑过）。
事后补跑的 R3（第 1 轮，基线 `premerge-t0gh` = `f9f06bf`，被审 tip `a97de47`）判 **block**，两条理由，
裁决记录在 `C:\wt\T0-GATE-HARDENING\.review\T0-GATE-HARDENING.json`。**两条都经独立复核属实，不是误报。**

### block ①：路径比较大小写语义不一致（`check-licenses.ps1:88-93` / `selftest.ps1:7609-7654`）
PowerShell 的 `-contains` / `-notcontains` **大小写不敏感**，而同一行里的 `$relativePath.StartsWith('android/')`
**大小写敏感**——一行之内两套语义。后果在 Linux（大小写敏感文件系统，CI 的 `ubuntu-latest` 正是）上是**静默漏扫**：
仓里真实存在的 `Build/build.gradle` 或 `Data/libs.versions.toml` 会被当成被 ignore 的 `build/` / `data` 剪掉，
可 `.gitignore` 里排的是小写那个，这两个目录是**被追踪的业务目录**。

这恰好戳中本闸的立身之本：`T0-GATE-HARDENING` 的全部意义就是把许可闸从「看不见」修成「看得见且 fail-closed」，
而漏扫是**静默**的——没扫到会被当成没有清单，闸在看不见时反而变安静，正是该卡产出 #2 要根除的形态。

复核实证：`'Build' -in @('build')` → **True**（不敏感）；`'Build' -cin @('build')` → **False**（敏感）。

### block ②：发布清单没有可通过的绿色路径（`RELEASE-CHECKLIST.md:27-28`）
第 27 项要求 `check-licenses.ps1 -Strict` 通过；紧邻的第 28 项却写着「完成人工核验②之后 `-Strict` 依旧会退出 1，
这是设计使然」。两项相邻且互相矛盾：发布者要么**在闸红着的时候勾选**（正是 fail-closed 设计要根除的习惯），
要么永远被挡住。复核实证（本卡开卡时在 master 实跑）：

```
pwsh -NoProfile -File scripts\check-licenses.ps1 -Strict   → exit 1
  - Gradle：检测到 4 个清单（android/app/build.gradle.kts, android/build.gradle.kts,
    android/core/build.gradle.kts, android/gradle/libs.versions.toml）但本闸无对应许可扫描器……
```

**用户已裁（2026-08-16）：删掉人工核验替代②，不造人工放行机制。** 理由与本卡上游仲裁同源——手工表在下次
依赖变动即过期，正确解法是扫描器；而 `docs/LICENSE-POLICY.md` §5 第 117-118 行早已写明「脚本**不会**自动清除
该告警……这是刻意的 fail-safe」。删掉②之后，第 27 与第 28 项**共用同一个解锁点**（`T0-LICENSE-SCANNER` 落地），
矛盾消失：清单诚实地说「扫描器落地前不得分发」。当前项目 T7 之前本就没有发布路径，这条约束今天零成本。

> **给评审者的预先说明**：删②之后第 27 项在扫描器落地前**仍然过不了**——这是本卡的**预期终态**，不是残留缺陷。
> 「无矛盾」的判据不是「两项都能勾」，而是**两项由同一个动作解锁**。评审者原文给的第一条修法就是
> "Remove the manual alternative"。

## 为什么 fix-forward 而不是 revert

1. `5ba3319` 是一条 12 提交、中途两次并回 master 的合并线；revert 会连带撤掉**已被证明有效**的产出——
   递归发现（实测已扫到 4 个清单，含卡片点名的 `libs.versions.toml`）、枚举出错 fail-closed、
   `--no-daemon`、`.\gradlew.bat` 显式路径、ReparsePoint 不下钻。
2. 这些产出**已被 17cc/17dd 的 in-selftest 变异判据钉住**，撤掉是净损失。
3. 两条 block 的修面都很窄：一个比较器 + 一段清单文字。

## `-SkipRed` 到底跳过了什么（别高估这笔债）

`-SkipRed` 跳过的是 **`task.ps1 -Phase red` 这道工作流闸**（本卡未经红→绿验证就进 ship）。它**没有**跳过证据：
17cc/17dd 把单句删除变异**做进了 selftest 本体**（17cc 变异 A = `libs.versions.toml` 分支、B = `build.gradle{,.kts}`
分支、17cc(reparse-mut)、17dd 各自基线 GREEN + 变异 RED，并带 ABSENT/PRESENT 判据与生产文件 SHA256 全程守卫），
**每次跑 selftest 都在重新证明这些断言是承重的**。本卡开卡时在 master 实测 `-Shard seeded` → **exit 0 / 330 秒**。

所以：**本卡不需要回头补测那两枚断言**（已写进 `non_goals`）；本卡自己的新断言必须**真走 RED-first**。

## 产出

1. **`check-licenses.ps1`：一个 OS 感知的路径比较器，五个调用点全部改用它。**
   - 导出 `Test-GradleNameEquals`（`-Left` / `-Right` / `-Comparison`，`-Comparison` 缺省 = 按 OS 判定的默认值），
     **必须定义在 `if ($AsLibrary) { return }`（现 line 126）之前**，否则 dod_command 的 `-AsLibrary` 取不到它。
   - 默认值语义：Windows / macOS 文件系统默认大小写不敏感 → `OrdinalIgnoreCase`；其余（Linux/CI）→ `Ordinal`。
   - 五个调用点（现 `Find-GradleManifests`，line 90-92）：`$Names` / `$SkipDirs` / `$SkipRelativePaths` /
     `$AndroidSkipDirs` / `$relativePath.StartsWith('android/')`。**一行之内两套语义正是本 block 的病因，
     改完必须只剩一套。**
   - `$Names` 也走同一比较器是**刻意的**：Linux 上 `BUILD.GRADLE` 不是 Gradle 清单（Gradle 只认小写），
     Windows 上它就是同一个文件——OS 感知在这一支同样是正确语义，不是顺手统一。

2. **`selftest.ps1` 17cc：大小写变体回归 + 每枚配变异。** 复用现有 harness（`$mutantCL` 同目录临时副本 +
   `LineMarker`/`Target`/`Control` 判据分类器 + 收尾 SHA256 核验），**不另起一套**。

3. **`RELEASE-CHECKLIST.md`：Gradle 阻断项收敛为单一解锁路径。** 删除人工核验替代②，保留缺口披露、
   触发点澄清（AGPL/SSPL 等不因未发布而豁免）与「未清零不得分发」；并加一枚 selftest 断言钉住
   「该项不含第二条替代解锁路径」，防止日后有人把②写回来。

## 平台陷阱：大小写夹具不能两边一个写法（**开卡前必读，这一条最容易烧轮次**）

Windows 上**造不出**同一目录下并存的 `build/` 与 `Build/`——`New-Item -ItemType Directory 'Build'` 在已有 `build/`
时只会返回那个既有目录。所以夹具与断言**必须按 OS 分支**，且两支断的是**不同的正确行为**：

| 平台 | 夹具 | 期望 | 理由 |
|---|---|---|---|
| Linux（CI ubuntu-latest） | 同时建 `build/` 与 `Build/`，各放一个 `build.gradle` | `Build/build.gradle` **必须被发现**；`build/build.gradle` 必须被剪 | 两个是不同目录，`.gitignore` 只排小写那个 |
| Windows | 只建 `build/` | 命中集为空（同一目录被剪） | 大小写不敏感下 `Build/` 就是 `build/`，剪掉是正确语义 |

**不要为了"两边写法一致"去造一个在 Windows 上不可能的夹具**，也不要把 Windows 那支写成 `Skip` 就算完——
Windows 那支要断言的是「不敏感语义下确实剪掉了」，它同样是承重断言。同理，`android/` 前缀那一支在 Windows 上
要证明 `Android/feature/deep/.kotlin` 也被当作 android 子树处理，在 Linux 上要证明它**不**被当作（前缀不匹配）。

## 权威面已扫齐（L97）

本卡开卡前已 grep 全部教「Gradle 许可缺口如何解锁」的面，结论如下——**执行者不必再扫，也不要顺手改**：

| 面 | 现状 | 本卡是否要改 |
|---|---|---|
| `docs/RELEASE-CHECKLIST.md:28` | 写了①扫描器 / ②人工核验 两条解锁路径 | **改**（删②） |
| `docs/LICENSE-POLICY.md` §3.2（line 73-75） | 已只指向 TD2/`T0-LICENSE-SCANNER`，并写明「还清前 RELEASE-CHECKLIST 保持发布阻断项」 | 否，已一致 |
| `docs/LICENSE-POLICY.md` §5（line 117-118） | 已写明脚本不会自动清除告警、是刻意 fail-safe | 否，已一致（删②反而是向它对齐） |
| `specs/tech-debt-tracker.md` TD2 | 已 `carded` → `T0-LICENSE-SCANNER` | 否（且元数据只在 master 改） |
| `specs/tasks/T0-LICENSE-SCANNER.md` | `doc_sync` 已含「§3 改为机检覆盖」 | 否 |

另按 L97：**本次 diff 改到的每个文件自身的注释与失败文案也要一起对齐**——`Find-GradleManifests` 头上那段
讲排除规则的注释现在没提大小写语义，改完必须补上；17cc 的失败文案**不要写死具体病因**，从现场数据动态报。

## 落点声明（L212 · 上游卡就是在这里烧掉三轮）

以下四件事**不在本卡 allow_paths**，由编排者在 master 完成，**执行者只需把证据贴进最终报告**：

1. `T0-GATE-HARDENING.md` 的 `status`（现仍是 `todo`，实际已合并）与事后 R3 结论的登记；
2. 本卡「变异证据」节（红/绿输出），R5 合并后由编排者在 master 追加；
3. `docs/TASK-BOARD.md` 补登 W0 追加卡；
4. 任何新经验入 `docs/lessons/`。

**若 R3 就以上任一条开 block：报告，不要动。** 它们的落点按 L18 只在 master。

## 排队约束（三张 T0 卡共用 `scripts/selftest.ps1`）

`T0-HARNESS-PERF`（`in-review`，R3 已 **pass** 于 `c29b74b`，重构 selftest 聚合器约 300 行）与
`T0-LICENSE-SCANNER`（`todo`，同时占 `check-licenses.ps1` + `selftest.ps1`）与本卡三方重叠，**必须串行**：

```
T0-HARNESS-PERF（先合，它已过 R3 且改动最大）
  → T0-GATE-FIXFORWARD（本卡，改面最窄）
  → T0-LICENSE-SCANNER（最后，它落地即解锁 RELEASE-CHECKLIST 的 Gradle 阻断项）
```

本卡**从 `T0-HARNESS-PERF` 合并后的 master 开卡**；若先于它开卡，17cc 周边会撞进那次重构。

## 验收（DoD = 命令 + 退出码 + 断言）

```powershell
# 沙箱内（轻量，R3 可复跑）——见 front-matter dod_command
pwsh -NoProfile -Command ". ./scripts/check-licenses.ps1 -AsLibrary; ..."

# 沙箱外（本机 + CI 强制）
pwsh -NoProfile -File scripts\selftest.ps1
```

- 期望退出码：均为 0。断言见 front-matter `dod_assert`。
- **dod_command 的已知覆盖边界（诚实声明，勿当遗漏）**：它只证明比较器**存在且两种模式都对**，
  **不**证明 `Find-GradleManifests` 真的调用了它。原因是这个 bug 在 Windows 上**行为不可观测**
  （Windows 下大小写不敏感本来就是正确语义）。「五个调用点真的改用了它」由 17cc 的 OS 分支夹具 +
  单句删除变异承担——这是本卡测试面的重心，别只让 dod_command 绿了就交卷。

## 执行建议

Sonnet 5 · max（PowerShell 闸门代码 + 跨平台夹具 + 变异证明，模式成熟但细节多）；备选 DeepSeek V4 Pro。难度 M。

**上下文包（开卡先读这四样，别重走已被裁掉的路）**：
① 本卡「平台陷阱」与「落点声明」两节；
② `C:\wt\T0-GATE-HARDENING\.review\T0-GATE-HARDENING.json`（两条 block 原文）；
③ `specs/tasks/T0-GATE-HARDENING.md` 的「编排者仲裁」与「编排者更正 2026-08-15」两段（TD2 争点已三次裁定，别重开）；
④ `scripts/selftest.ps1` 的 17cc/17dd 整块（line ~7797 起）——你要复用的 harness 就在那里。
