---
id: T0-LICENSE-CI-INTEGRATION
title: Gradle 许可扫描套件接线与 TD2 总验收（TD2 收口卡 5/5）
depends_on: [T0-LICENSE-GAV-BOUNDS]
status: todo
branch: T0-LICENSE-CI-INTEGRATION
worktree: C:\wt\T0-LICENSE-CI-INTEGRATION
allow_paths:
  - .github/workflows/ci.yml
  - scripts/selftest.ps1
  - scripts/license-scanner-check.ps1
  - docs/LICENSE-POLICY.md
  - docs/RELEASE-CHECKLIST.md
forbid:
  - 重写前四卡已通过 R3 的 scanner 核心
  - 让 verify 或 scanner 依赖出站网络
  - 在前四卡未 merged 或 TD 总验收未通过时把 TD2 标 paid
non_goals:
  - 新增生态扫描器、许可类别或 exception 格式
  - 修改 Android 产品代码或 Gradle 依赖
  - 发布应用、商店提交或外部交付
acceptance:
  # 作者声明的验收清单：以下是本卡认为「完成」所需的事实，每条应有可证伪测试。
  # **这是一份声明，不改变任何评审语义**——裁决仍完全按 docs/QUALITY-RUBRIC.md 现行 rubric 判，
  # 清单未列到的问题照常按现行 rubric 处理（含其现行的 [FOLLOW-UP] 适用条件）。
  # 「把清单当排他性判据、清单外一律 FOLLOW-UP」是上游提案 Asun28/claude-devops-scaffold#203
  # 的内容，**上游落地前本仓不采用**。
  # 本清单由 2026-08-23 的 pre-R3 独立复核（Opus 5 读全量 diff）产出——本卡 diff 实测 213,186 字符、
  # 远超 review.ps1:297 的 6 万字符首屏 cap，故 R3 只能读到约 28%；封闭清单是那道缺口的补偿控制。
  - "A1 selftest 接线判定的是可执行调用而非字符串：断言须认 CommandAst 的被调命令为 pwsh 且其 -File 实参 extent 含 license-scanner-check.ps1；把 selftest 里那行整行换成引用同一命令文本的 Write-Host，必须以 [INTEGRATION-SELFTEST-WIRING] 变红（只比 CommandAst.Extent.Text 会把字符串字面量判为调用）"
  - "A2 CI 接线判定的是被执行的路径而非同行字符串：须把 License gate 步骤内活跃行的执行目标解析到 scripts/check-licenses.ps1（变量赋值与 -File $f 成对，或直接字面量）；两种形态各一枚变异必须以 [INTEGRATION-CI-SCANNER] 变红——① 把 $f 改指 scripts/check-cards.ps1，② 把执行行换成 Write-Host 加行尾注释形态的原调用"
  - "A3 [INTEGRATION-SELFTEST-COLD] 有专属单点变异：只删 selftest 那行的 -SkipRealScan（保留 -Suite integration）必须恰以该码变红；且变异判据分类器须要求「命中期望码且不命中其它接线码」，杜绝顺带触发被当成证明"
  - "A4 CI 步骤的存在与顺序分码分变异：把 - name: License gate 移到 Setup Java 之前必须以 [INTEGRATION-CI-ORDER-SEQUENCE] 变红；删除或注释该步骤名必须以 [INTEGRATION-CI-ORDER-COUNT] 变红（两者共用一个码时顺序分支拿不到任何变异）"
  - "A5 PASS 文案带机检可读的 ASCII 模式哨兵，两段各自独立：-SkipRealScan 运行输出 PASS [real-scan=skipped]、默认运行输出 PASS [real-scan=executed]；-SkipMutations 运行输出 [mutations=skipped]、默认输出 [mutations=executed]，两段拼成 PASS [real-scan=…] [mutations=…]，四种组合由一张真值表逐字钉住。把任一个三元折叠成恒定分支后，selftest 的 17cc(scanner-integration)（它以 -SkipRealScan 运行、正则要求 stdout 含 `license-scanner-check(integration): PASS [real-scan=skipped]` 这一行）必须变红（中文证据句零机检 ⇒ R3 #12 的修复无回归保护）"
  - "A6 -SkipMutations 转发可证：以 -Suite integration -SkipRealScan -SkipMutations 运行时，四个子套件输出必须不含 mutations): PASS 行；删掉转发那行后该断言必须变红"
  - "A7 -SkipMutations 对 integration 自身接线变异的语义显式定死并机检：要么一并跳过，要么 PASS 文案只声明「子套件 mutation 已跳过」——不得出现「已按 -SkipMutations 跳过」而接线变异表照跑。**断言写成「整张接线变异表一枚都没跑」**（例如 -SkipMutations 运行的 stdout 里不得出现 `integration wiring mutations): PASS`），不写死枚数——加减一枚变异不该改这条"
  - "A8 -SkipRealScan 的作用域守卫有 RED 证据：-Suite graph -SkipRealScan 必须非零退出并打 [INTEGRATION-SKIPREALSCAN-SCOPE]，删掉该守卫后断言变红；且守卫须前移到 dot-source scanner 之前（现在非法调用会先把整个 scanner 载入一遍再抛错）"
  - "A9 wrapper distribution readiness 的七个合取项逐条保留 RED 证据：.ok 标记 / root 基数 / root 精确名 / launcher 基数 / launcher 精确版本名 / bin/gradle / bin/gradle.bat，每删一条都必须让 graph 套件以 GRADLE-WRAPPER-OFFLINE 加零 wrapper 启动变红，各带专属失败码（selftest 的 -1434 行删掉了其中六条的唯一夹具）"
  - "A10 依赖图解析器补回三个只存在于旧夹具的形态：FAILED 行必须产出 GRADLE-UNRESOLVED（现夹具只有 (n)，从生产正则删掉 FAILED| 后全套仍绿）；空 selector 与空重定向尾两种边必须各产出 GRADLE-PARSE 并保留完整边文本"
  - "A11 1434 行删除的无损性在 diff 内可核：selftest 删除处的注释须列出「被删断言类 → 承接套件 + 失败码」的逐条映射，至少覆盖 wrapper-* / parser-* / pom-* / override-* / redaction 五族。理由是 R3 只读 diff（L227），映射只有落进 diff 才是证据"
  - "A12 真实仓扫描证据与坐标真相源同源且失败可诊断：org.testng:testng **不在** android/gradle/libs.versions.toml 里被 pin（它是 org.jetbrains.kotlin:kotlin-test-testng 的传递依赖），故期望**版本**无从推导、也不得写死 7.0.0（升版即假红，失败文案会变成一句关于病因的假陈述）。改为两条断言：① [INTEGRATION-TESTNG-MANIFEST] 锚到清单里真正声明的那一行（org.jetbrains.kotlin:kotlin-test-testng 在场；它不在了就说明本条断言的前提没了）；② [INTEGRATION-TESTNG] 要求真实扫描输出里出现以 `org.testng:testng:` 开头、后跟具体版本号的逐坐标行。两条失败文案都须带上扫描输出"
  - "A13 本脚本的中文 stdout 落进闸 1g 的编码契约：或在入口 dot-source _encoding.ps1、或加进 selftest 的 $encScripts 并补前奏（本卡引入了该脚本第一条非 ASCII stdout，而 -AsLibrary 早退拿不到 scanner 的编码前奏）"
  - "A14 发布清单改写不得静默丢掉两条分发红线：docs/RELEASE-CHECKLIST.md 须保留「未清零前不得分发（打包 APK 对外分发与变 public 均算分发）」的明文禁令，以及指向 LICENSE-POLICY §1.1 的「AGPL/SSPL/EUPL/非商用触发点与分发无关」指针；改后同步 selftest 的 $rcCanonHash（哈希同步会让这类丢失对闸 17ee 不可见）"
  - "A15 接线断言不得是变量名黑名单：[INTEGRATION-SELFTEST-INLINE] 须以正向契约表达（如 selftest 中调用本脚本的 CommandAst 恰一个、且不存在构造 Gradle wrapper 夹具的写文件命令），改个变量名即可让整份夹具复活而断言仍绿的形态不算数"
  - "A16 完备性陈述须机检、不得只靠注释：凡声称「每条断言都有对应变异」这类完备性陈述，须有一条断言把它变成可证伪的事实——[INTEGRATION-WIRING-CODE-COVERAGE] 从 Get-IntegrationWiringFailures 的源码 AST 抽出它能抬起的全部失败码，断言该集合**精确等于**变异表声明的码集合（多一个 = 有 raise site 无人盯，少一个 = 变异指向了产不出的码），并断言 Get-IntegrationWiringFailures 在本脚本里恰好定义一次，否则抽取目标不唯一。其余无法机检的完备性措辞（如「赋值不算执行」）一律改写成指向具体断言码的陈述，不得留下自证式注释"
dod_command: pwsh -NoProfile -File scripts/license-scanner-check.ps1 -Suite integration
dod_exit: 0
dod_assert: CI 在 JDK/Android/Gradle setup 与在线 cache warm-up 后运行 scanner，scanner 本身强制 offline；fresh-runner 顺序夹具可证不会因未预热必红。仓库真实扫描逐坐标输出至少含 org.testng:testng，禁列和未知夹具均非零；五个子套件与 selftest 接线全绿。政策/发布文档准确写明覆盖与剩余人工边界。只有本卡 merge 后执行 TD2 总验收并补齐五个 PR/commit 证据，随后才允许 paid/归档。
review_gate: codex {verdict:pass}
hygiene: integration 只证明接线、真实仓扫描和五个套件聚合；不复制 graph/policy/gav-bounds/diagnostics 的细粒度夹具
doc_sync: 本卡 merge 后把 TD2 置 paid，并记录 PR #20 基线及五张收口卡的 PR/commit；归档五张已 merged 卡；同步 LICENSE-POLICY 与 RELEASE-CHECKLIST
---

# T0-LICENSE-CI-INTEGRATION

## 目标

把前四张已独立通过 R3 的能力接进 CI 和权威文档，并执行 TD2 的总验收。本卡是 fan-in/closure，不拥有 scanner 核心。

PR #20 已在 master `b0a76d0` 合入 CI 顺序和 scanner 初版，但五个专用套件、selftest 聚合与权威文档事实同步尚未完成。本卡只验收新形成的串行合同，并如实把 PR #20 记为共同基线；不得倒填五个不存在的历史 PR。

## 单一产出

1. CI 顺序固定为 setup/cache warm-up → offline license scan。
2. `scripts/selftest.ps1` 只聚合调用五个专用 license scanner 套件，不再内嵌上千行 scanner fixture。
3. 政策和发布清单准确描述机检范围、fail-closed 语义与仍需人工证据的非分发图。
4. 用真实仓扫描和五个套件结果形成 TD2 总验收证据。

## 关闭条件

`T0-LICENSE-SCANNER`、`T0-LICENSE-POLICY`、`T0-LICENSE-DIAGNOSTICS`、`T0-LICENSE-GAV-BOUNDS`、本卡全部 merged；本卡 integration DoD、仓库 verify、R3 均通过；tracker 补齐 PR #20 基线与五张收口卡 PR/commit 后，TD2 才能从 `carded` 变 `paid`。

## 根因诊断

CI 和文档依赖稳定的 CLI/exit contract，却被旧卡与解析器、POM 和日志同时评审。把它放在末端可避免核心行为每改一次就重审 workflow 和政策措辞。

## PR #49 R3 round-cap 记录

PR #49 已完成两轮 R3，仍剩两类 harness 合同缺口：integration 的 raw-text 接线断言可被注释满足，且 cold seeded 的 PASS 文案与 `SkipMutations` 语义不准确。按两轮硬上限，不在 PR #49 继续第 3 轮；原 PR 只能经人裁决定是否合并。

剩余两类缺口**已改由本卡承接**（见验收 A1/A2/A3/A5/A7/A15，分支上已实施）——用户 2026-08-23 裁定走「在原卡上修 + `-ResetRounds`」这条路线（路线 2），故 `T0-LICENSE-CI-INTEGRATION-R3-CLOSURE` 已被架空，待本卡合并后于 R5 退役。**本段保留是为说明那张卡为何不再启动**，不表示本卡范围外扩：承接范围仍限于活跃接线断言、comment/delete mutation 与 cold 聚合文案，不恢复旧 1400 行 fixture，也不重开 scanner 核心。
