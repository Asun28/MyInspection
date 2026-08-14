# scout-options — 第二步「2-options」：搜现成方案 + 评可行性 + 选 base

> 把一页 brief 变成一个**有据的选型决策**：先广搜现成开源/库方案，再逐个按本仓硬边界打分、对抗式核验，
> 最后产出**决策矩阵 + 推荐 + ADR 草案**。这是 idea→plan 漏斗里原本缺失的一段（"搜选项 + 评技术可行性"），
> 也是 obra/Superpowers 与 garrytan/gstack 都没有的一段——本仓用既有的 `deep-research`（fan-out 搜索 → 抓取 →
> 对抗核验 → 带引用综合）能力把它补上。
>
> 漏斗位置：**1-brief（`shape-idea` skill）→ 2-options（本工作流）→ 3-plan（`PLAN-TEMPLATE` + `plan-forge`）**。
> 总览见 `docs/IDEA-TO-PLAN.md`。

## 怎么跑
```
Workflow({ scriptPath: ".claude/workflows/scout-options.mjs",
           args: { briefPath: "_local/1-brief.md", count: 4 } })
```
- 没有 brief 时可临时用 `args: { idea: "一句话需求" }` 兜底（但建议先跑 `shape-idea` 产出 brief）。
- `count`：搜索角度数（并行），默认 5（含 `by-research` 学术 SOTA 角度）；越大越广也越贵。
- 触发：需用户显式开启多 Agent 编排（prompt 含 "ultracode"，或直接说"跑 scout-options 工作流"）。

## 三段（段间有人工闸；自主链式边界见下「产物落位」）
```
brief ──Scout(多角度并行搜候选)──▶ 去重候选清单
                                      │
              Vet(逐候选并行核验) ◀────┘  许可硬规则 + 硬边界 + 可行性对抗打分 + build-vs-buy
                                      │
              Synthesize ◀────────────┘  决策矩阵 + 推荐 + 决策日志 + ADR 草案
                                      │
        推荐 + ADR 草案 ──[人审/批准]──▶ 落 _local/2-options.md + docs/adr/NNNN-*.md
```

1. **Scout（多角度并行搜）**：5 个角度各自搜——`by-category`（品类/awesome 清单）·`by-keyword`（核心能力关键词）·
   `by-ecosystem`（PyPI/npm 生态）·`by-prior-art`（前人工程实现/复现仓/大厂开源）·`by-research`（**近年顶会 SOTA 论文**：
   经 Google Scholar / arXiv / Papers with Code 检索 **CVPR / ECCV / ICCV / ICLR / ICML / NeurIPS / AAAI** 等，
   每篇映射到其官方代码仓 + 许可）。多模态扫描：单一角度搜不全；论文角度防「只盯 GitHub、漏掉最新 SOTA」。
   - **研究代码许可警示**：顶会代码常**无 license 或非商用/研究-only/CC-BY-NC/自定义权重许可**——`by-research` 据实标注，
     vet 阶段按许可硬规则照拦（别因「是 SOTA」放宽）；这类多半只能 `reference-only`（借思路、原创复现），不能直接 adopt。
2. **Vet（逐候选对抗核验）**：去重后每个候选并行打分——**许可**是否撞硬规则（GPL/AGPL/SSPL/非商用 = 禁）、撞了哪些**硬边界**
   （离线/确定性/目标 shell/无 GPU）、**可行性** 0–10、**build-vs-buy**（adopt / fork-and-adapt / reference-only / build-from-scratch）。
   默认怀疑：许可不明、维护停滞、贴合度低 → 倾向 drop。
3. **Synthesize（汇总选型）**：决策矩阵（每候选 fit/effort/risk/decision）+ 推荐 + **决策日志**（为什么选 X 不选 Y，逐条）+
   **ADR 草案**（背景/决策/备选方案/后果，可直接落 `docs/adr/`）。所有候选都不过或都不贴合 → 明确推荐 build-from-scratch。

## 产物落位（人工闸·默认 / 自主链式·长自主运行可选）
- 决策矩阵 / 推荐 → `_local/2-options.md`（非技术同事也读得懂的一页选型说明，gitignored）。
- ADR 草案 → `docs/adr/NNNN-<kebab>.md`（**永久**决策记录，入库；命名/结构见 `docs/adr/README.md`）。这条 ADR 就是第三步计划
  「技术栈 / 目录结构 / Provider 契约」三节的依据来源。
- 然后进第三步：按推荐的 base 写 `_local/PLAN.md`（`docs/PLAN-TEMPLATE.md`）→ `plan-forge.mjs`。
- **自主链式边界**（与 `scout-options.mjs` 的 next_step 一致；启用长自主运行本身即用户对本段的委托）：落盘决策矩阵、归档 ADR
  自主完成并报证据（recommendation/matrix/decision_log）后，若 recommendation 与已批准 brief 的范围/约束一致，即按推荐自主进入第三步并报证据；
  **仅当推荐会改变已批范围**（如引入新许可类别、成本量级、超出 brief 的架构含义）**或出现只有用户能答的问题时才停**。计划签核仍是下一个硬人工闸。漏斗级边界总述见 `docs/IDEA-TO-PLAN.md` 总图下的自主链式注记（不在此重复）。

## 设计要点
- **许可闸前移**：选型阶段就按 `docs/LICENSE-POLICY.md` 卡掉 copyleft/非商用，而不是等到 `check-licenses.ps1` 在施工期才爆。
- **build-vs-buy 显式化**：每个候选必须落一个四选一结论，避免"似乎能用"的含糊；reference-only 也是合法结论（只借思路不进依赖）。
- **决策可追溯**：决策日志 + ADR 让"为什么是它"半年后还查得到；需求变了重跑本工作流、新开一条 ADR 标 supersedes。
- **真相源不变**：本工作流只产**草案与建议**；有人驱动时 ADR 由人批准后入库；自主链式下归档与采纳推荐可自主并报证据（仅当推荐改变已批范围或出现只有用户能答的问题才停），计划仍是唯一真相源、计划签核仍是下一个硬人工闸。

## 成本（按 effort/预算缩放角度与候选数）
| 段 | 量级 | 备注 |
|---|---|---|
| Scout | ≈ count 个 agent（默认 5） | 每角度一个（含 by-research 学术 SOTA） |
| Vet | ≈ 候选数个 agent | 去重后每候选一个，并行 |
| Synthesize | 1 agent | 汇总 + ADR 草案 |
