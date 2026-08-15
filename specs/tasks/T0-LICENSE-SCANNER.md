---
id: T0-LICENSE-SCANNER
title: Gradle 依赖许可自动扫描（偿还 TD2 · 把「覆盖缺口告警」变成「逐坐标机检」）
depends_on: [T0-GATE-HARDENING]
status: todo
branch: T0-LICENSE-SCANNER
worktree: C:\wt\T0-LICENSE-SCANNER
allow_paths:
  - scripts/check-licenses.ps1
  - scripts/selftest.ps1
  - docs/LICENSE-POLICY.md
  - configs/licenses/
forbid:
  - android/ 与 .github/（非本卡领地）
  - 为过闸弱化任何既有断言
  - 把「未知许可」默认放行（必须 fail-closed：查不到即视为不合规，人工登记豁免才放行）
non_goals:
  - 换掉现有的「其它生态清单探针」结构（本卡只把 Gradle 这一支从告警升级为真扫描）
  - 前端 / PyPI 生态的扫描器（各自生态另说）
dod_command: pwsh -NoProfile -Command "if (-not ((pwsh -NoProfile -File scripts/check-licenses.ps1 | Select-String -SimpleMatch 'org.testng:testng') -and (pwsh -NoProfile -File scripts/check-licenses.ps1) )) { exit 1 }"
dod_exit: 0
dod_assert: check-licenses 实跑输出中**逐坐标**列出已解析的 Gradle 依赖及其许可（至少含 org.testng:testng 一行，证明传递坐标真被解析而不只是直接依赖）；植入一枚禁列许可（如 EPL/GPL）的假坐标夹具后 `-Strict` **必非零退出且指名该坐标**；查不到许可的坐标计为不合规而非放行。三条各配单句删除变异，变异须「非零 **且** 命中指定断言文本」（L165 分类器）
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: docs/LICENSE-POLICY.md §3 改为「机检覆盖」并删除人工表的临时说明；specs/tech-debt-tracker.md 把 TD2 置 paid（R5）
---

# T0-LICENSE-SCANNER

## 为什么有这张卡（TD2 的偿还卡）
`T0-GATE-HARDENING` 让许可闸**看见**了 Gradle 清单（递归发现 + 覆盖缺口 + `-Strict` fail-closed），
但它只到「知道自己没扫」为止：约 **220 个传递坐标**至今未逐一核验，靠的是一次性人工表 + 显式披露。
编排者当时的裁决（见 `T0-GATE-HARDENING` 的仲裁段）是：手工审 220 个坐标在下次依赖变动即过期，
**正确解法是自动扫描器**，而许可义务的触发点是**分发**，故允许把它推迟到发布前——但必须**有卡承接**。
这就是那张卡。**在还清它之前，`docs/RELEASE-CHECKLIST.md` 的「Gradle 传递依赖许可全量核验通过」是发布阻断项。**

## 产出
1. 从 Gradle 真实解析结果取坐标（如 `.\gradlew.bat -p android :core:dependencies --configuration runtimeClasspath`
   等各 configuration，或等价的依赖报告任务），解析成 `group:artifact:version` 列表——**不是**读 `libs.versions.toml`
   的字面量（那只有直接依赖，正是 TD2 的盲区）。
2. 逐坐标判定许可：优先 POM 的 `<licenses>` 块；取不到再回落到已登记的**人工豁免表** `configs/licenses/`
   （每条须写明坐标、许可、证据 URL、登记人/日期）。**查不到 ≠ 通过**——未知即不合规。
3. 判定口径沿用 `docs/LICENSE-POLICY.md`：宽松（MIT/BSD/Apache 等）放行；GPL/AGPL/SSPL/EPL/non-commercial 致命。
4. 输出可读报告 + 非零退出语义与现有闸一致（正常运行告警、`-Strict` 失败）。

## 上下文包
- 现成起点：`findings.md` 里已有编排者复核过的直接依赖许可表（DeepSeek 出表 + testng POM 经编排者独立复核），
  可作为**回归夹具的期望值**——但不要把它当运行时数据源，它会过期。
- `org.testng:testng` 的 POM 把 `junit:junit`(EPL) 标为 `<optional>true</optional>`，实测不进 classpath。
  这正是「读清单字面量」与「读真实解析结果」的差别，适合做一枚断言。
- 离线：CI/verify 恒 `--offline`，故扫描器需能在**已填充的依赖缓存**上工作，或把取 POM 这一步限定在显式联网的维护命令里，
  **不得**让 verify 闸依赖出站网络（硬边界）。

## 验收
见 dod_command / dod_assert。

## 执行建议
Sonnet 5 · max（PowerShell + 依赖图解析，模式成熟）；备选 DeepSeek V4 Pro。难度 M。
先读 `T0-GATE-HARDENING` 的仲裁段与 `specs/tech-debt-tracker.md` 的 TD2 整行，别重走已被裁掉的路。
