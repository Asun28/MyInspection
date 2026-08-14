---
name: shape-idea
description: >-
  Step 1 of the idea→plan front funnel ("1-brief"), AI-driven end-to-end. Two explicit modes:
  DIVERGE (发散/做加法 — explore wide, find real pain, NO early feasibility filtering) then
  CONVERGE (收敛/做减法 — KANO/MoSCoW prioritize, cut pseudo-needs, MVP, user stories + acceptance
  criteria). The AI does the research/fan-out/drafting; the human only approves direction at gates.
  Output: _local/1-brief.md. Triggers on: "1-brief", "brief", "brainstorm", "新想法", "我有个想法",
  "想做一个…", "帮我理需求", "是否值得做", or any new product/feature idea. Do NOT use to write the
  plan (PLAN-TEMPLATE + plan-forge) or pick a tech base (scout-options).
---

# shape-idea — 第一步「1-brief」:发散 → 收敛(AI 自驱,人只把关)

> 前置漏斗第一段。**全程 AI 执行,适配 AI 不适配人**:AI 做调研/发散/起草/收敛,人**只在闸口定方向**
> (RSI:把 perspiration 自动化,direction 归人)。人类世界的做法做等价翻译——见下表。产物:`_local/1-brief.md`。
> 漏斗:**1-brief(本卡)→ 2-options(`scout-options.mjs`)→ 3-plan(`PLAN-TEMPLATE` + `plan-forge`)**;总览 `docs/IDEA-TO-PLAN.md`。

## 人 → AI 原生 翻译(为什么这么做)
- 用户深访/问卷 → AI 挖**公开民意**:web 搜 + 社交/论坛/评论(Reddit/X/HN 等;有相应 MCP/CLI 工具则用)里的真实抱怨与 workaround。
- 限时手绘/头脑风暴 → **并行 fan-out**:多 agent / 多角度,各被强制一个迥异框架,不自我审查(可派子 agent)。
- KANO 问卷 → AI **有证据的推理分类** + 缺真实信号者标 `needs-signal`,别假装有数据。
- 开会拍板 → 人只在两个闸口(发散后选方向、收敛后批 brief)拍板,其余 AI 自驱。

## 硬闸
**没有人批准的 brief 之前,不写码、不进 scout、不收敛到单一方案。** 简单想法 brief 可短,但发散→收敛两步都要走、都要人点头。

---

## 阶段 1 — 发散 DIVERGE(做加法 · 探索期)
**目的**:找**真痛点 / 创新点 / 商业价值**,避免"拿着锤子找钉子"。
**铁律**:此阶段**严禁**用"技术难 / 没资源 / 实现不了"来收敛——过早收敛只会做出平庸妥协物或解一个伪需求。鼓励 What-if、允许"错"。
**AI 动作(尽量并行 fan-out,越宽越好)**:
1. **痛点 / JTBD 挖掘**:web 搜 + 社交/论坛/评论(有相应 MCP/CLI 工具则用)搜真实用户怎么吐槽、现在拿什么凑合(workaround)及其代价。落 JTBD 句式:「当[情境],我想[动机],以便[结果]」;按 重要度 × 不满足度 找机会(高重要+低满足=金矿)。
2. **竞品 + 邻域扫描**:扫 3+ 竞品/相邻工具;对竞品套 SCAMPER(替代/合并/改造/反转/删除…)找空白。
3. **跨域类比**:别的行业(游戏/医疗/物流…)怎么解这类问题?借过来会怎样?
4. **反共识 / What-if**:如果用户**什么都不用做**就拿到价值?如果把流程**反过来**?如果只服务最极端的那个用户?
5. **产出「机会地图」**:≥5 个**迥异**角度(不是同一想法的变体),每个带证据/出处,**先不筛可行性**。
**闸**:把机会地图给人 →「哪些方向你有感觉?」人选方向(可"都不是,换个角度再发散")。

## 阶段 2 — 收敛 CONVERGE(做减法 · 定义期)
**目的**:划边界,明确**做什么 / 坚决不做什么**,把资源投到 ROI 最高处,防需求蔓延(scope creep)。收敛是"做减法的艺术"。
**AI 动作**:
1. **KANO 分类**每个候选功能:必备(must-be)/ 期望(performance)/ 兴奋(delighter)/ 无差异(indifferent)/ 反向(reverse)。**先砍 indifferent + reverse**;必备只投到阈值、别镀金;兴奋点是高 ROI 差异化。证据不足的分类标 `needs-signal`。
2. **MoSCoW 排序**:Must / Should / Could / Won't-this-time。Must 必须二值("没它产品就废吗?"否则降 Should);**Won't 是显式"暂不"、不是否决**(给 stakeholder 一个受保护的停车场)。
3. **砍伪需求/边缘需求 → 提炼 MVP**:最小可验收闭环。能回答「**砍掉 50% 留什么**」。
4. **转 User Story + 验收标准**:每条「作为<角色>,我想<动作>,以便<收益>」——**`so that` 收益句必填**(没有它就只是任务不是需求)。配验收标准 Given/When/Then,**可被非作者独立判定**(对齐第三步可机检 DoD)。
5. **六个逼问做证据自检**(AI 自答、标 assumption):真实需求证据? 现状 workaround 代价? 最需要它的**那个真人**? 这周就肯付钱的最小版本? 有没有观察到意外? 三年后更必需还是更没用?
**写 brief**:照 `docs/PROJECT-BRIEF-TEMPLATE.md` 写到 `_local/1-brief.md`,保持**做什么/为什么**高度(怎么做留给第三步)。自检:占位/矛盾/双解歧义/范围过宽/YAGNI。
**闸**:请人过目批准 → 然后唯一下一步是 **Step 2 `scout-options`**(`docs/SCOUT-OPTIONS.md`)。

---

## 规模判断(任何时候)
若想法其实是**多个独立子系统**("平台含聊天+存储+计费+分析"),立即指出、先拆子项目,每个各走一遍 brief→scout→plan,别在该先拆的项目里细抠。

## 高风险存疑协议
碰到高风险歧义(架构/数据模型/破坏性范围/缺关键上下文):停,一句话点名,给 2–3 个带权衡选项,问人。日常小事别用。

## AI 自驱铁律
- 发散期严禁可行性/资源借口;收敛期严禁"全都要"(答不出"砍 50% 留什么"就是没收敛)。
- 人只定方向 + 批 brief,其余 AI 干;别把六个逼问当人类问卷逐条丢给人——AI 先自答、只把**真需要人定的方向**抛给人。
- 每条 User Story 必须有 `so that` + 可机检验收标准。证据不足别编,标 `needs-signal`。

## 语气
先说结论;具体(点名文件/数字/出处);把技术选择落到用户得失;避免 AI 腔(delve/crucial/robust/comprehensive/furthermore/landscape/…)。最终由用户拍板方向。
