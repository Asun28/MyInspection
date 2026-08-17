# 技术债精简索引（cold-storage index · 可 grep）

> 一行一条已归档（paid/accepted）债项，共 4 条；完整还债指针在 `tech-debt-archive.md` 按 id 查。
> 由 `scripts/archive.ps1` 从归档文件投影生成，勿手工编辑。新卡/续接查「这坑还没还过？」先 grep 本表。

| id | 严重度 | 状态 | 位置 | 一句话（债，截断） |
|---|---|---|---|---|
| TD5 | major | paid | `core/canon`(canonicalJson 数组序前置) ↔ `inspection_item.select… | **canonical 数组序契约在 canon 层不可验证也不可重建**：ADR-0003/卡文规定 items 按模板全序、photos/audios 按 UUID 序，但排序键与 UUID 都不进快照（round-16 用户已决=选… |
| TD16 | minor | paid | `docs/SECURITY.md` §4、`docs/IDEA-TO-PLAN.md` L82、`scripts/_… | **权威 TD 交叉引用已漂移或失效**：前三处把单人账号／组织治理指向 TD14，但 TD14 现为媒体落盘↔入库原子性；LOOP-ENGINEERING 把 R3 非确定性 carve-out 指向 TD1，但 TD1 是上游 sel… |
| TD23 | major | paid | `scripts/selftest.ps1` 17cc 的 `$probe = { Invoke-MarkerAsse… | **17cc 变异探针依赖闭包外脚本作用域中的函数名解析，R3 只读评审宿主不保证该绑定可见**：同一 `pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded` 在常规 ship… |
| TD21 | minor | paid | `CLAUDE.md`「当前阶段」任务卡总数 ↔ `specs/tasks/T*.md` | **任务卡库存数已失真**：CLAUDE.md 写「29 张」，发现时 `579c45e` 的 `specs/tasks/` 已有 31 张真实 `T*.md` 卡（`_TEMPLATE.md` 不计），且偿还债务的新卡会继续增长。后果：… |
