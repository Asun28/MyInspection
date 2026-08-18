# 技术债精简索引（cold-storage index · 可 grep）

> 一行一条已归档（paid/accepted）债项，共 10 条；完整还债指针在 `tech-debt-archive.md` 按 id 查。
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
