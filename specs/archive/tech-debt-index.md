# 技术债精简索引（cold-storage index · 可 grep）

> 一行一条已归档（paid/accepted）债项，共 15 条；完整还债指针在 `tech-debt-archive.md` 按 id 查。
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
