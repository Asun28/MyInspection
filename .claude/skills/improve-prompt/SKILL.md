---
name: improve-prompt
description: >-
  按目标 Claude 模型改写/优化一段提示词（prompt）。最少只需粘贴原 prompt：本卡先推断或确认目标模型
  （规划/架构/评审 → Opus 5；规格清晰实现 → Sonnet 5；长自主/多卡运行 → Fable 5），再指名 Read
  docs/references/claude-<model>-prompting-llms.txt + 跨模型 claude-prompting-best-practices-llms.txt，
  **既加该加的、也删该删的**（为旧模型写的补偿性脚手架在新模型上常反过来伤你），
  输出可直接复制的改进版 prompt + 逐条「改了什么/为什么」（每条可溯源到 reference 条目）。
  Triggers on: "improve this prompt", "prompt improve", "optimize my prompt", "rewrite this prompt
  for <model>", "make this prompt better", "这个 prompt 在新模型上还合适吗", "迁移提示词",
  "优化提示词", "改进提示词", "帮我改 prompt", "提示词优化", "润色 prompt",
  "把这个 prompt 调成 Opus/Sonnet/Fable 用的". Do NOT use for: 在本仓加/改 skill 的
  SKILL.md 与触发优化（那是 skill-creator）、给本仓 hook/rubric 调措辞的维护活（直接走 CLAUDE.md
  「模型专属提示词细则」）、与提示词无关的一般文案润色。
---

# improve-prompt — 按模型改写提示词（原创卡 · reference 驱动 · 辅助非闸）

> 填的空白：`docs/references/` 已 vendor 六份提示词 reference（4 份模型专属：`opus-5` 日常 + `opus-4-8` 兜底
> + `sonnet-5` + `fable-5`；2 份跨模型：最佳实践 + Console 工具），但只有
> 「维护脚手架时按需 Read」的指针，没有「随手贴一段 prompt 就能改」的会话入口。本卡就是那个入口：
> **输入随意（最少只要原 prompt），输出可复制的改进版 + 溯源解释**。改写质量的真相源永远是 reference
> 文件本身——本卡只编排流程，不内联复制任何提示技巧正文（免双源漂移；reference 随模型出新版刷新）。

## 输入契约（最少一项，越多越准）
- **必须**：原 prompt（粘贴文本，任何语言）。
- **可选**：目标模型 · 用在哪（system prompt / skill / hook / 一次性对话 / Console 模板）· 现在哪里不满意
  · **这段 prompt 原本是为哪个模型写的**（有的话删减面会准很多）。

## 流程
1. **定模型**（用户指明了就用；没指明按 prompt 的活推断）：路由映射（哪种活 → 哪个模型）的
   **单一真相源是 CLAUDE.md「模型分工与交接」节**——去读它、照它分，本卡不复述映射、不另维护会漂移的表。
   定了模型后，其专属 reference 按命名约定取：`docs/references/claude-<model>-prompting-llms.txt`
   （`<model>` = 后缀值 `opus-5` / `sonnet-5` / `fable-5`，非完整模型 ID；另有 `opus-4-8` = **拒答回退的兜底档**，
   用户在配回退链、或问「被拒改道后那一档怎么调」时才路由过去）。
   真歧义 → **问一个带选项的问题**，别猜。三者之外（Haiku / 非 Claude 模型）→ 只走跨模型通用篇，
   并明说「无该模型专属 reference，以下为跨模型通用改进」。
2. **指名 Read（不许凭训练记忆答）**：跨模型 `docs/references/claude-prompting-best-practices-llms.txt`
   + 上一步定的模型专属篇。若 prompt 是 Console/API 侧模板（模板变量/改进器场景）再加
   `claude-prompting-tools-llms.txt`。
3. **改写 = 两面都过，别只做加法**。多数「优化提示词」的产出只会变长，但**提示词烂掉的另一半原因是攒了
   为旧模型缺陷写的补偿**，那些补偿在新模型上从「没必要」升级成「有害」——所以两面都要过：
   - **加法面**：对照 reference 条目逐项过（清晰度/结构/角色/示例/思考/工具/长上下文 + 模型专属怪癖），补该补的。
   - **删减面**：找**补偿性脚手架**——为模型某个旧弱点写的、而 reference 说该弱点已消失/已反转的句子。
     典型形态：替模型做它现在自带的事（自检/复核/汇报节奏）、为旧默认值写的开关、绕某个已修掉的怪癖的迂回写法。
     **判定门槛（fail-closed）**：每条删除都要能在**读到的 reference 里指出依据**（明说「这条现在没必要 / 反而有害 /
     行为已反转」）。指不出依据就**别删**，改为在「缺信息」里提一句请用户确认。
   - **两面共同的红线**：**只改表达不加范围**——不替用户发明新需求、不顺手扩能力（rubric #14 同款病）；
     删减面只删**为模型写的补偿**，**绝不删用户自己的需求、约束、领域知识、输出格式要求**。
     保留用户原语言（中文 prompt 改出来还是中文，除非用户要求换）。
   - prompt 里若带 effort/thinking 之类**运行配置**：reference 说新模型该重调就提示一句「这些默认值值得在你自己的
     eval 上重跑一遍」，**但别替用户拍板改数值**——那要他的 eval 说了算。
4. **输出**（固定结构）：
   - **改进版 prompt**：单个 fenced code block，可直接复制。
   - **改了什么/为什么**：逐条 bullet，每条指到 reference 的具体条目/章节（可溯源，不空口）。
     **删除项单独标出来**（如 `[删] …`）——加法在 diff 里看得见，删掉的东西读者不专门被告知就不会注意到，
     而删减恰恰是最需要他点头的部分。
   - **缺信息**（如有）：列 1–3 个补上会更准的事项（如运行环境/失败样例/原本为哪个模型写的），**不阻塞输出**。

## 红线
- **必 Read reference，不凭记忆**——reference 是为治「幻觉过时提示技巧」vendor 的（不在上下文里=不存在）。
- **改表达不改意图/范围**：产出的 prompt 做的事和原来一样，只是做得更好。
- **产物去向**：回对话；要留档落 `_local/`，**绝不落仓库根**（根洁净闸会拦）。
- **辅助非闸（L25）**：产出是建议，不进 `dod_command`/CI。
- 出了新模型没有专属 reference → 按 `docs/references/<name>-llms.txt` 命名新增 + 登记
  `docs/references/README.md` 索引，**别把提示技巧正文内联进本卡**（免双源漂移）。
  **换代 ≠ 旧篇作废**：动手删旧模型那份之前先查它是否仍在服役——尤其是不是**拒答回退的兜底档**
  （Fable 5 与 Opus 5 都带安全分类器，官方默认路由按拒答类目把请求改道到推荐兜底模型，当前是 Opus 4.8）。
  仍在服役就**改写它的角色说明、保留文件**；确已停用才退休，并把指向它的交叉指针一并改掉。
  （这条是踩出来的：一次换代里旧篇被当废档删掉，同时把另一篇的回退目标改成了新模型——正好改反。）
- 本仓维护场景（给某模型写 skill/hook/rubric）仍走 CLAUDE.md「模型专属提示词细则」直接 Read——
  本卡是**会话式改 prompt 入口**，不替代那条约定。
