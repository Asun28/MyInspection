---
name: pr-recap
description: >-
  Turn a diff / branch / commit / PR into a high-altitude VISUAL recap —— 变更鸟瞰 + mermaid 架构delta图
  + 文件触达图 + 评审注意点 —— 贴进 GitHub PR 描述/评论(GitHub 原生渲染 mermaid)或落 `_local/`。纯 markdown
  + git/gh,零运行时依赖、自包含。给评审者一个「先看形状再读逐行 diff」的入口,补 codex R3(文字裁决)之外
  给人看的高空视图。Triggers on: "recap", "visual recap", "PR recap", "diff recap", "变更鸟瞰",
  "高空俯瞰", "把 diff/PR 变成可读摘要", "改动落地后总结一下", "PR 摘要". 模型在回路 = 上游评审【辅助】,
  绝非确定性 DoD 闸(L25)。Do NOT use for: 写计划(PLAN-TEMPLATE + plan-forge)、单元测试/DoD(task-loop /
  verify.ps1)、纯视觉设计(frontend-design / taste-skill)、捕获经验(lessons)。
---

# pr-recap —— diff→可视化高空摘要（原创卡 · 辅助非闸 · L26 工具无关）

> 填的空白：合并前评审只有 codex R3 的**文字**契约/工程裁决，没有给**人**看的「这坨改动长什么形状」的高空视图。
> 本卡把 `git diff` 投影成可扫的可视 recap，**渲染走 GitHub PR 原生 mermaid**（代码 PR 本就上 GitHub），零外部查看器、零运行时依赖。
> **定位 = 上游评审辅助，不是闸**（模型在回路 = 非确定，见 L25）：它帮人快速进入评审，确定性把关仍是 codex R3 + `verify.ps1`/CI。

## 能力（L26：标准在此，后端可换）
- **标准**：一份 diff 应能投影成「高空可视摘要」——让评审者在读逐行 diff 前先抓住改动的*形状*。
- **默认后端**（自包含、零依赖）：本卡的 mermaid + GitHub PR 原生渲染。
- **可选更高保真后端**（opt-in，不绑死、不设默认）：Builder `visual-recap`（annotated-diff UI / 可分享链接）——但它需 `@agent-native/core` 运行时 + node ≥ 22.22 + Plan 查看器，非本地纯净，按需自行接。

## 怎么做（Windows / pwsh）
1. **定范围**：默认 `git diff --stat master...HEAD` + `git diff master...HEAD`；给了 commit/branch/range 就用给的。按需先 `git fetch` 对齐 base。
2. **取事实、别臆测**：从 `git diff --stat` 拿净增删 + 触达文件；所有结论须**落在真实 diff 行**上，不编没改的东西。
3. **产出结构**（精简但有料；没内容的块写「无」）：
   - **变更鸟瞰**：一段话 what + why、净 +/−、触达文件数。
   - **架构 delta**：mermaid 图，标新增/改动的模块与边（新增用虚线或标注）。
   - **文件触达**：mermaid 图或表，PR → 各文件 + 每文件 +/−。
   - **契约/数据变更**：若动了 `scripts/_config.ps1` 的 `FrozenPaths` 契约/schema 或数据模型——**显式高亮**（冻结物改动 = 版本评审 + 下游返工，评审第一优先级）。
   - **评审注意点**：最该先看的几处（风险/反直觉处）。
4. **输出去向**（二选一或都做）：
   - 落 `_local/recap-<slug>.md`（gitignored，**绝不落仓库根**——根洁净闸会拦）。
   - 贴 PR：`gh pr edit <n> --body-file _local/recap-<slug>.md` 或 `gh pr comment <n> --body-file _local/recap-<slug>.md`（mermaid 在 GitHub 原生渲染）。
     用 gh 前确认账号守卫（`scripts/_config.ps1` 的 `GhAccount` 非空，见 `_guard.ps1`）；**清 token 用 `Remove-Item Env:GH_TOKEN, Env:GITHUB_TOKEN -EA SilentlyContinue`**（赋空串仍遮蔽 keyring 致 401，见 L3）。

## mermaid 约定
- 节点标签含特殊字符（`-` / `+` / 空格 / ★）一律加引号：`f1["registry.py +90"]`。
- 架构图用 `graph LR`/`graph TD`；新增边标 `-. 新 .->`，删除/弃用在标签里注明。
- **一张图聚焦一件事**（架构 delta 与文件触达分开画），别堆成意大利面。

## 红线
- **辅助非闸**：产出不进 `dod_command`/CI（模型在回路 = 非确定，L25）；确定性把关是 codex R3 + `verify.ps1`。
- **不落根、不入库**：recap 落 `_local/`；要留痕就贴进 PR（在 GitHub 上）。
- **不重造**：要 annotated-diff UI / 可分享链接时按需接 Builder `visual-recap`（opt-in），别在本卡里堆查看器/运行时依赖。
- 所有结论须能由 diff 复核，不编造改动。
