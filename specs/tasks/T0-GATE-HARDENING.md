---
id: T0-GATE-HARDENING
title: 许可闸看得见 Gradle + verify 确定性 + 两枚闸门自测（从 T0-TOOLCHAIN 拆出）
depends_on: [T0-TOOLCHAIN]
status: merged
branch: T0-GATE-HARDENING
worktree: C:\wt\T0-GATE-HARDENING
allow_paths:
  - scripts/check-licenses.ps1
  - scripts/verify.ps1
  - scripts/selftest.ps1
  - docs/LICENSE-POLICY.md
  - docs/RELEASE-CHECKLIST.md
forbid:
  - android/ 与 .github/（T0-TOOLCHAIN 领地，已合并，勿回头改）
  - 为过闸弱化任何既有断言
  - 在本卡内造完整 Gradle 许可扫描器（那是 TD2 的选型活，见下「编排者仲裁」）
non_goals:
  - 逐坐标自动许可扫描 / CI 强制 allowlist（TD2 独立卡）
  - 手工审计全部约 220 个传递坐标（见仲裁：义务绑定在**分发**，不在骨架卡）
dod_command: pwsh -NoProfile -Command "if (-not ((pwsh -NoProfile -File scripts/check-licenses.ps1 | Select-String -SimpleMatch 'libs.versions.toml') -and (Select-String -Path scripts/verify.ps1 -Pattern '--no-daemon' -SimpleMatch))) { exit 1 }"
dod_exit: 0
dod_assert: check-licenses 实跑输出里点名 libs.versions.toml（行为断言，非源码文本）；verify.ps1 的 Android 闸调用含 --no-daemon。**另须（评审沙箱外、本机 + CI 强制）**：`pwsh -NoProfile -File scripts\selftest.ps1` 全绿，且新增两枚断言各自的单句删除变异**确实变红、非零确实来自该断言**（分类器判据，L165）——变异红/绿证据贴进卡片记录。重型套件按 L60/L62 不进 dod_command（评审沙箱不保证可复跑）。
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: docs/DELIVERY-CHAINS.md 与 docs/scaffold-architecture.html 的 CI 形态描述（T0 改了 runner）+ TASK-BOARD 备注（R5）
---

# T0-GATE-HARDENING

## 合并状态与事后 R3（2026-08-16 登记）

**已合并于 `5ba3319`，但走的是非常规路径**：绕开 `task.ps1 ship` 直接合并，`-SkipRed` ×2（RED-first 闸显式跳过），
**合并前 R3 从未跑过**。事后补跑的 R3（基线 `premerge-t0gh` = `f9f06bf`，被审 tip `a97de47`，裁决存于
`C:\wt\T0-GATE-HARDENING\.review\T0-GATE-HARDENING.json`）判 **block**，两条理由**经独立复核均属实**：

1. 路径比较大小写语义不一致（`-contains` 不敏感 vs `StartsWith('android/')` 敏感）⇒ Linux 上 `Build/`、`Data/`
   等被追踪目录被静默剪掉——恰好是本卡产出 #2 要根除的「闸在看不见时反而变安静」形态；
2. `docs/RELEASE-CHECKLIST.md:27-28` 两项相邻却互相矛盾，人工核验替代②走完 `-Strict` 仍恒退出 1，无绿色发布路径。

**用户已裁：fix-forward，不 revert**（revert 会连带撤掉已被 17cc/17dd 变异判据证明有效的递归发现 / fail-closed /
`--no-daemon` / ReparsePoint 守卫）。承接卡 = **`specs/tasks/T0-GATE-FIXFORWARD.md`**，人工核验替代②按人裁删除。

> **`-SkipRed` 的债有多大，说准确**：它跳过的是 `task.ps1 -Phase red` 这道工作流闸，**不是证据**——
> 本卡两枚断言的单句删除变异做在 selftest 本体里（17cc 变异 A/B + reparse-mut、17dd），每次跑 selftest 都在
> 重新证明它们承重（master 实测 `-Shard seeded` exit 0 / 330 秒）。故承接卡**不需要**回头补测这两枚。

## 为什么单独成卡（拆分依据）
T0-TOOLCHAIN 的宪章是「本机工具链 + `android/` 骨架空编译绿」。R3 连续六轮把它推着长出了
**第二个子系统**：许可闸递归发现、verify 确定性、闸门自测、许可政策正文。三次 allow_paths 破例后
`check-cards` 已告警「6 条 > 5，卡可能过大」——**告警是对的**。骨架卡与闸门加固是两个可独立评审的单元，
硬塞一卡导致每轮评审面都在变大、轮次不收敛（L205 现场）。故按 `specs/README.md` 右尺寸标准拆出本卡。

## 产出
1. `check-licenses.ps1` 的其它生态探针**递归发现** Gradle 清单，**必须含 `android/gradle/libs.versions.toml`**
   （T0 六轮 finding #2：只 glob `build.gradle{,.kts}` 会漏掉卡片点名的目标，且当时"能报出来"只是因为恰好还有别的构建脚本存在）。
   排除 `.gradle/`、`build/` 等缓存/产物目录。
2. **失败必须 fail-closed**（T0 六轮 finding #4）：递归枚举出错（子树不可读等）**不得**被 `-ErrorAction SilentlyContinue` 吞掉——
   吞掉后「没扫到」会被当成「没有清单」，`-Strict` 照过，闸在看不见时反而变安静。捕获枚举错误 → 记一条 coverage gap。
3. `verify.ps1` 的 Android 闸调用补 `--no-daemon`（与 CLAUDE.md「命令」节、T0 卡 prose 的既有口径一致；
   现场实证：残留 Gradle daemon 曾累计 800+ 秒 CPU）。**只改这一处**。
4. `selftest.ps1` 加**两枚**断言：① 嵌套 Gradle 清单发现（含 `libs.versions.toml`）的行为断言；
   ② verify.ps1 的 Android 闸调用含 `--no-daemon`。**每枚配单句删除变异**，且变异脚本须带**判据分类器**——
   只有「非零 **且** 命中指定断言文本」才算数（L165：变异本身也会撒谎，非零可能来自语法坏/更早的闸抢先中断）。
   ③ 发现逻辑的**每个分支**各自被变异覆盖（T0 六轮 finding #6c：只变异了 `libs.versions.toml` 一支，
   `build.gradle{,.kts}` 那一支从未被证明在测）。
5. **`verify.ps1` 的 gradlew 调用改显式路径**（T0 合并后实测）：现为 `cmd /c 'gradlew.bat …'`，**裸文件名**依赖
   「当前目录参与 exe 搜索」这一可被关闭的行为——Claude Code 的 shell 会话带进程级 `NoDefaultCurrentDirectoryInExePath=1`
   （已核：HKLM/HKCU 注册表均无此项，非本机真实设置），该形态下直接 `'gradlew.bat' is not recognized`。改
   `cmd /c '.\gradlew.bat …'`（或等价显式路径）即免疫（注：verify.ps1 已 `Push-Location` 进 `android/`，故**不需要** `-p android`——那是从仓库根跑时才要的），真实终端/CI 行为不变。**同属本卡「verify 确定性」主题，不是新范围。**
6. `docs/LICENSE-POLICY.md` §3 落已核验的 Gradle 直接依赖许可表（DeepSeek 出表 + 编排者复核 testng POM，
   见 `findings.md`）。**`aar-metadata.properties` 不是许可证据**——它记 `minCompileSdk` 等构建要求，与许可无关，勿再引用。

## 编排者仲裁：约 220 个传递坐标未审计（T0 第 4/5/6 轮同一争点）
R3 三轮要求「审完全部传递坐标，否则移除/推迟依赖」。按 CLAUDE.md「maker 与 checker 同一争点两轮互不认可即停、
排队人裁」，此处**由编排者裁决，不再迭代**：
- **认**：约 220 个传递坐标当前**确实未逐一核验**，这是真实合规缺口，不粉饰。
- **不认**：在本卡手工审 220 个坐标。手工表在下次依赖变动即过期，正确解法是自动扫描器 = **TD2 的选型活**。
- **裁决**：许可义务的触发点是**分发**（`_config.ps1` `Distributes = $true`），而本项目当前**无任何发布路径**
  （发布相关卡在 T7 之后）。故：① 缺口在 `docs/LICENSE-POLICY.md` 显式披露（数量 + 未审计含义 + 责任卡 TD2）；
  ② 在 `docs/RELEASE-CHECKLIST.md` 加一条**发布前阻断项**「Gradle 传递依赖许可全量核验通过」；
  ③ TD2 置 `carded` 指向偿还卡。**风险被记账并绑到真正的触发点，而不是靠在骨架卡上手工堆表来假装消除。**

### 编排者更正 2026-08-15（R3 第 7 轮 · 上面 ③ 与「证据贴进卡片记录」本就不该由本卡做）
R3 第 7 轮（同一 TD2 争点第三次）要求本卡「把 TD2 置 carded」并「把变异证据贴进卡片记录」。执行者两次
指出这两件事的落点（`specs/tech-debt-tracker.md`、新卡、**本卡自身**）**都不在本卡 allow_paths**，停下报告——**执行者对**。
根因是**编排者写卡时的失误**：把两个只能在 master 上做的动作写进了一张 feature 卡的义务里。按 **L18**，
卡片元数据（卡文、tech-debt 表、新建卡）**一律只落 master、不进功能分支**，否则它们会混进本卡 diff、
被范围闸拦下，或逼着再破例扩 allow_paths（正是 L206）。故：
- **③ 已由编排者在 master 完成**：新建偿还卡 `specs/tasks/T0-LICENSE-SCANNER.md`，TD2 置 `carded` 并指向它。
  **不是本卡的活，本卡 diff 里不该出现这两个文件。**
- **变异证据的落点 = 本卡文件的「变异证据」节，由编排者在 R5 doc-sync（合并后、在 master）追加**；
  本卡执行者只需把红/绿输出**贴进最终报告**交给编排者。`-Local` ship 无 PR 可贴、无 CI 可引，这是唯一自洽的落点。
- 若 R3 第 8 轮仍就 TD2 或证据落点开条件：**报告，不要动**。这两件事已在 master 完成，本卡 diff 不该包含它们。

## 验收
```powershell
pwsh -NoProfile -File scripts\selftest.ps1
```
- 退出码 0；断言见 dod_assert。两枚变异的红/绿证据贴进 PR 或卡片记录。

## 执行建议
Sonnet 5 · max（PowerShell 闸门代码 + 变异证明，模式成熟但细节多）；备选 DeepSeek V4 Pro。难度 M。
**先读 `findings.md` 的「T0-TOOLCHAIN review history」与本卡仲裁段**，别重走已被裁掉的路。
