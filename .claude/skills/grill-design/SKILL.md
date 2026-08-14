---
name: grill-design
description: >-
  写 PLAN 之前/之中,对【技术设计决策】做交互式无情拷问——沿设计决策树一次问一个、每问给推荐答案、
  逐一消解决策间依赖,达成共识后再落 PLAN。漏斗第三步(3-plan)写计划前的设计 stress-test,介于
  shape-idea(需求拷问)与 plan-forge(自动审计)之间,提升一次过审率、减少 fix-first 往返。
  Triggers on: "grill", "拷问我", "拷问设计", "stress-test 这个设计/计划", "写计划前敲定设计",
  "敲定设计决策", "grill the plan/design". 不用于需求层(那是 shape-idea)或自动审计已写好的计划(那是 plan-forge)。
---

# grill-design — 写 PLAN 前的设计决策拷问(漏斗第三步 · 交互式)

> 「发散→收敛→**乘法(设计)**」的乘法那一拍。`shape-idea` 把**需求**(what/why)谈清后,本卡把 **PLAN 的技术设计决策**
> (how)一个个 grill 清楚,再落 `docs/PLAN-TEMPLATE.md` → 交 `plan-forge.mjs` 审计。**设计决策模糊就进 plan-forge,会换来一堆 `fix-first` 往返**——本卡前移消解。
> 分工:`shape-idea`=需求层拷问 · **grill-design=设计层交互式拷问** · `plan-forge`=写完后自动对抗审计。三者正交。

## 何时用
拿到 `_local/1-brief.md`(需求)+ `docs/adr/*`(选定的 base,来自 `scout-options`)后、动手填 `_local/PLAN.md` 之前/之中。

## 机制(适配 AI:AI 主导拷问,人只定方向)
1. **沿设计决策树走,一次只问一个**。多问令人困惑;问完一个、等回答、再下一个。
2. **每问都给推荐答案 + 理由**(不是开放式抛给人)。**承重/难逆决策**(契约/schema/数据模型/状态机/模块边界)逐个 grill、等人确认/改/否再定;**叶子/非承重决策走自主模式**——直接采纳推荐答案、记进 PLAN 对应节(标注「自采推荐」),不打断人,只在承重/难逆处要人拍板。
3. **能从代码/brief/ADR 找到答案的,不问**——先去查,查不到才问。
4. **逐一消解决策间依赖**:先问 load-bearing 的(契约/schema/数据模型/状态机/模块边界),它定了再问依赖它的;别在叶子决策上纠结而漏了根。
5. **聚焦技术设计决策**,典型几类:数据模型与字段、契约/接口形态与冻结面、状态机与合法转移、错误/边界/失败路径、模块边界与依赖方向(高内聚低耦合)、mock→real 演进、并发/幂等。需求层问题(做不做、给谁)**回 `shape-idea`**,别在这里重谈。
6. **达成共识即落地**:把敲定的决策写进 `docs/PLAN-TEMPLATE.md` 对应节(尤其 §4.5 模块设计 / §5 数据模型 / §6 契约 / §7 任务拆分),然后交 `plan-forge.mjs` 审计。

## 红线
- 一次一问(批量提问 = 反模式)。能查就查,不拿可查的事问人。
- 只 grill 设计决策,不重谈需求(shape-idea)、不替代自动审计(plan-forge)。
- 产出落 PLAN(唯一真相源),不另起第二真相源;人拥有/批准方向(RSI:perspiration 自动化、direction 归人)。
