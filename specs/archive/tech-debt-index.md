# 技术债精简索引（cold-storage index · 可 grep）

> 一行一条已归档（paid/accepted）债项，共 36 条；完整还债指针在 `tech-debt-archive.md` 按 id 查。
> 由 `scripts/archive.ps1` 从归档文件投影生成，勿手工编辑。新卡/续接查「这坑还没还过？」先 grep 本表。

| id | 严重度 | 状态 | 位置 | 一句话（债，截断） |
|---|---|---|---|---|
| TD5 | major | paid | `core/canon`(canonicalJson 数组序前置) ↔ `inspection_item.select… | **canonical 数组序契约在 canon 层不可验证也不可重建**：ADR-0003/卡文规定 items 按模板全序、photos/audios 按 UUID 序，但排序键与 UUID 都不进快照（round-16 用户已决=选… |
| TD16 | minor | paid | `docs/SECURITY.md` §4、`docs/IDEA-TO-PLAN.md` L82、`scripts/_… | **权威 TD 交叉引用已漂移或失效**：前三处把单人账号／组织治理指向 TD14，但 TD14 现为媒体落盘↔入库原子性；LOOP-ENGINEERING 把 R3 非确定性 carve-out 指向 TD1，但 TD1 是上游 sel… |
| TD23 | major | paid | `scripts/selftest.ps1` 17cc 的 `$probe = { Invoke-MarkerAsse… | **17cc 变异探针依赖闭包外脚本作用域中的函数名解析，R3 只读评审宿主不保证该绑定可见**：同一 `pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded` 在常规 ship… |
| TD21 | minor | paid | `CLAUDE.md`「当前阶段」任务卡总数 ↔ `specs/tasks/T*.md` | **任务卡库存数已失真**：CLAUDE.md 写「29 张」，发现时 `579c45e` 的 `specs/tasks/` 已有 31 张真实 `T*.md` 卡（`_TEMPLATE.md` 不计），且偿还债务的新卡会继续增长。后果：… |
| TD25 | major | paid | `scripts/selftest.ps1` 17cc(case-mut) 的 `$probe = { Invoke-… | **case mutation 闭包仍依赖宿主的动态函数名解析**：同一 seeded 分片以 `pwsh -NoProfile -File` 运行通过，但以真实评审可用的 `pwsh -NoProfile -Command '& .\s… |
| TD3 | major | paid | `scripts/review.ps1`(交给评审者的工作树) ↔ `scripts/_scope.ps1`(读 ba… | **评审者与范围闸读的是两份不同的卡，冲突时评审者恒输出假「越界」block**：范围闸按设计从 **base ref** 取卡原文（`git show <base>:specs/tasks/<id>.md`，防分支自扩 allow_pa… |
| TD22 | minor | paid | `scripts/archive.ps1`(L328–338) ↔ `CLAUDE.md` L247、`docs/ad… | **归档移动 merged 卡却未维护入站具体卡路径**：`52d95f5` 将 `T0-TOOLCHAIN`、`T5-BACKUP-FORMAT`、`T2-CAPTURE-CORE` 分别以 R100 从 `specs/tasks/<i… |
| TD13 | minor | paid | `core/template/TemplateStore.kt`(`read()` 返回值) | **`Collections.unmodifiableList` 包一层但无自证测试**：`TemplateStore.read()` 把读回的 `items` 包进 `Collections.unmodifiableList` 防调用方… |
| TD11 | minor | paid | `scripts/selftest.ps1` 闸 17ee（`$rcCanonHash`）+ `docs/RELEAS… | **发布清单那一项被整行 SHA-256 钉死**（T0-GATE-FIXFORWARD 的人裁产物）：「不含第二条解锁路径」这个契约无法用模式穷举散文替代（评审实证：不带序号的「或完成人工核验后即可勾选」绕过任何字形断言），故改钉规范文… |
| TD27 | major | paid | `scripts/selftest.ps1`（17ac(moving-ref) Unix git shim） | **17ac(moving-ref) 的 Unix `git` shim 在 Ubuntu 将 `$PSScriptRoot` 展开为空**：PR #20 Ubuntu seeded job `95566561516` 报 `/git-s… |
| TD135 | major | paid | `scripts/check-licenses.ps1` 的 `Get-GradleGavParts` ↔ 图解析 /… | **已接纳 Gradle/Maven GAV 的每段无长度上限，和诊断的“exact GAV + 总行有界”合同互相冲突**：当前共享验证器只校验字符集、三段数和路径遍历；任意长的 group/artifact/version 仍可进入解… |
| TD15 | minor | paid | `core/media/`(JPEG 编码内存预算) | **编码字节上界是「有依据的余量」而非可证明上界**：预算余量已从 2 B/px 提到 4 B/px 以覆盖 `ByteArrayOutputStream` 底层数组 + `toByteArray()` 复制，注释也已如实改口（不再自称… |
| TD131 | major | paid | `app/media/PhotoJpegEncoder.kt` 固定 q92 且无尺寸档 | **所有新照片固定以 q92 编码且没有长边上限/用户设置**。后果：多物业、多年巡检的本机照片增长不可控，Extra High 输入还会放大 TD15 内存峰值；用户也无法区分日常记录与小字证据需求 / 修法：先以流式编码偿还 TD15… |
| TD14 | major | paid | `core/media/`(落盘↔入库) + `app/media/`(清理调度) | `.jpg.pending` 文件侧 lease、无行/软删照片清理 worker 与 24h KEEP 调度已落地；路径/DB 运行时组成统一为 `filesDir/media` + `myinspection.db`。目录项掉电顺序不… |
| TD138 | major | paid | `scripts/check-licenses.ps1` ↔ `scripts/license-scanner-che… | **Gradle diagnostics 权威实现与 selftest 旧回归漂移**：PR #33 为验证无 git skip 取消尾段切片后，17cc 暴露 CLI/plain password/token 泄漏、旧逐规则 marke… |
| TD4 | major | paid | `scripts/check-secrets.ps1`(L57 模式 `\.db$` · L175 glob `*.d… | **防泄露闸与迁移校验闸结构性互斥，导致 T1-SCHEMA-CORE 关掉了 `verifyMigrations`**：SQLDelight 的 `verifyMigrations` 需要把 `<version>.db` schema… |
| TD1 | minor | paid | `scripts/selftest.ps1`(闸15n · 闸17aa(8)) | **对上游脚手架 bug 的本地补丁 ×2，已回搬**：①15n 在 post-init 缺少元仓专属 `TEMPLATE-README.md` 时稳定跳过；②17aa(8) 在 live/archive 均缺少元仓 T11 卡时稳定跳过… |
| TD137 | major | paid | `.jpg.pending` / JPEG sibling directory durability | PR #32 已保证 marker 文件 force + 最深父目录同步，但首次创建的祖先目录项未逐级 fsync；补偿/worker 删除 JPEG 后也未先同步同目录再清 marker。掉电可能丢失 marker 层级，或恢复出“JP… |
| TD140 | major | paid | `T2-FIELD-LEDGER-THEME` PR #41 R3 round cap | 原卡两轮 R3 后仍剩一类主题契约完整性缺口：Material 3 未显式传入的 inverse/fixed accent、surface 层级与四个 Typography 角色会继承库默认值，标准组件可能重新出现未批准的默认紫色/中性色… |
| TD145 | minor | paid | `T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST` PR #47 R3 round cap | 原卡第二轮 R3 只剩 Windows migration fixture 清理不确定：真实 ADDED/REMOVED 断言通过后，立即执行的 `git worktree remove --force` 偶发非零且 stderr 被丢弃… |
| TD146 | minor | paid | `specs/archive/cards-index.md` ↔ `specs/archive/tasks/` | **归档卡索引静默漂移**：归档目录已有 39 张卡，自动生成索引仍声明并列出 36 张。`archive.ps1` 每次真运行会重算正确投影，但 R5 可手工移动卡；现有 selftest 只在隔离夹具里证明生成器，没有普通 PR 闸验… |
| TD147 | major | paid | `docs/lessons/LEDGER.md` L214 ↔ `CLAUDE.md` 必须层 | **blocking 经验达到晋升门槛后仍停留总账**：L214 已记录“未提交代码做单句删除变异后用 `git checkout --` 还原，连同新工作一起被抹掉”的真实损失，并以 severity=blocking 达到必须层门槛；… |
| TD148 | major | paid | `docs/lessons/LEDGER.md` L177 ↔ `CLAUDE.md` L17 PowerShell… | **blocking mutation 批次假全绿经验未进入默认上下文**：PowerShell 变量名大小写不敏感，`foreach ($m in $M)` 会在第一轮覆盖集合，令 16 枚计划只跑 1 枚；若汇总只看每条 OK 而不核… |
| TD149 | major | paid | `docs/lessons/LEDGER.md` L167 ↔ `CLAUDE.md` L165 mutation 铁律 | **blocking mutation 假红分类纪律未进入默认上下文**：L167 已记录多批变异因靶未命中、parser 失败、StrictMode 异常或更早闸抢先而非目标断言失败，却仍被汇总成“全红”。L165 已要求单句删除变异与… |
| TD150 | major | paid | `docs/lessons/LEDGER.md` L172 ↔ `CLAUDE.md` L17/L177 PowerS… | **blocking detached pwsh 编码假红纪律未进入默认上下文**：harness 外启动的 pwsh 默认 OEM 编码曾让中文断言 mojibake，整晚 mutation 批次全部假红；L172 已记录事故但仍是 l… |
| TD152 | major | paid | `docs/lessons/LEDGER.md` L162 ↔ `CLAUDE.md` L17/L172/L177 脚… | **blocking Windows Python 编码/缓冲误诊纪律未进入默认上下文**：Python 的 locale 默认编码会让第三方工具读取仓库中文文件时报 UnicodeDecodeError，管道块缓冲又会让长驻进程看似静默… |
| TD153 | major | paid | `docs/lessons/LEDGER.md` L171 ↔ `CLAUDE.md` 必须层安全铁律 | **blocking 不安全路径委托纪律未进入默认上下文**：守卫曾拒绝自己写/删不安全裁决路径，却仍经 REVIEW_OUT 把同一路径交给评审者子进程，令链接可覆写工作树外文件；同型错误在第二站点复发。L171 已有 17t 行为闸但… |
| TD154 | major | paid | `docs/lessons/LEDGER.md` L164 ↔ `CLAUDE.md` L171 信任边界铁律 | **blocking fail-closed 新入口信任绑定纪律未进入默认上下文**：范围闸抽出第二入口时遗漏原入口靠运行位置白拿的判定对象、基线卡、检查器来源、提交身份与 TOCTOU 绑定，产生多条空 diff/自证式 fail-op… |
| TD155 | major | paid | `docs/lessons/LEDGER.md` L21 ↔ `CLAUDE.md` L205 R3 轮次证据铁律 | **R3 配额故障被误读为真实评审分歧**：Codex 配额耗尽时评审者不产出裁决，重复 ship 会继续消耗 rounds，最终以 round cap 表象掩盖“评审者从未真正运行”。L21 已复发两次并有独立 probe/ResetR… |
| TD156 | major | paid | `scripts/selftest.ps1` `New-SelftestSnapshot` | **detached/linked worktree 的 all 快照采用错误 master，令 core 14f 假红**：从最新 `origin/master` 建出的 detached worktree 跑 `-Shard all`… |
| TD158 | major | paid | `scripts/selftest.ps1` gate 15b / 17a3 + post-merge runs 32… | **post-merge canary 把 harness 环境/编排问题误报成产品 CI 红且无法定位**：seeded 在无 wrapper/plugin cache 的 hosted runner 直接跑 `:core:check`… |
| TD151 | major | paid | `docs/lessons/LEDGER.md` L181 ↔ `CLAUDE.md` L193 Unicode 铁律 | **blocking Unicode sanitizer 数据损坏纪律未进入默认上下文**：.NET 正则按 UTF-16 码元匹配，宽泛 `\p{C}` 会把合法增补平面字符的代理对两半当 `Cs` 清除；L181 已记录真实数据损坏但… |
| TD159 | major | paid | `.github/workflows/ci.yml` pull_request `verify` | **纯文档 PR 仍无差别启动完整 Windows Android 工具链**：PR #111 只做 R5 元数据时，run 32524624342 依次 provision Java/Android/Gradle、在线 build 与许… |
| TD24 | major | paid | `Photo.sq` `selectActiveAssetsByContentHash` → `PhotoIngest… | **照片按内容哈希全局复用，却要求备份源为单一物业 owner**：查询不按物业过滤，`PhotoIngest` 也只校验路径形状；现有 recorder 测试已允许 B 物业照片复用 A 物业的同一 `rel_path`。但 `Back… |
| TD157 | major | paid | `scripts/check-licenses.ps1` + `scripts/check-secrets.ps1`… | **增补平面格式标量可穿过许可与 secrets 信任边界**：.NET regex 按 UTF-16 码元分类，U+1BCA0/U+E0001 等 Cf 在正则眼里是两个 Cs，现有 guard/sanitizer 因此漏判；L190… |
| TD146 | minor | paid | `scripts/selftest.ps1` 闸号命名 | 三方同时各自挑「下一个空号」，`T0-LESSONS-BUMP-PLANE`(#129)、`T0-LESSONS-COLD-RECALL`(#51)、`T0-LESSONS-CAP-UNIT`(#127) 都取了 `2d`——同号不同闸，… |
