---
name: database-design
description: >-
  Use for ANY relational database schema design or review in this project — identifying business
  entities/relationships, ER models, table & column design, primary-key choice, naming, audit/
  soft-delete/status fields, logical-vs-physical foreign keys, keeping business logic OUT of
  triggers/stored-procedures/DB-events, index planning from query scenarios, partitioning/sharding,
  and multi-tenant isolation — for SaaS, ERP, ecommerce, healthcare, IoT, internal tools, etc.
  Triggers on: "design the schema/tables/database", "review this schema/DDL", "data model", "ER 图",
  "建表/表结构/数据库设计", "加审计字段", "分库分表/分区", "多租户". An ORIGINAL discipline card (no
  proprietary copy); pairs with the plan-forge data-model lens, rubric #13, and docs/lessons/database.md.
  Do NOT use for frontend (frontend-design), generic backend logic/tests (task-loop), cutting scope
  (ponytail); relational-only (document/KV/graph/time-series stores need their own discipline).
---

# database-design — 关系型 Schema 设计纪律（原创 · 配 `backend/` 骨架）

> **这是一张原创纪律卡，不是 vendored 第三方/内置技能正文**（本仓许可洁净铁律：不拷不明许可或专有正文）。
> 它补足脚手架原本的空白——脚手架已会**把 schema 当契约冻结**(`FrozenPaths`/rubric#3)、**跨层同构**(plan-forge
> `consistency` lens)，却不教**怎么把 schema 设计好**。以「应用开发的数据库架构师」身份工作：先业务建模，模型清晰后才出 DDL。
> 非 DBA 运维（备份/PITR/调优）——除非用户明确要、或会实质影响 schema 决策，否则不展开。
> **工具无关（L26）**：下面的具体默认（MySQL8 / Snowflake BIGINT / `UUIDv7`·`ULID` / `utf8mb4` / `DECIMAL`）
> 是**当前默认 / 举例**；项目有既有约定即从其约定，审计看的是**标准是否被满足**，不绑某引擎。

## 强制工作流（建模在前，DDL 在后）
出任何 DDL 之前，必须先产出或合理推断这五项；缺了就**标注假设继续给第一版**，不为每个未知项阻塞：
1. **业务分析** —— 这是什么产品 / 给谁 / 核心业务流程。
2. **核心实体** —— 区分 实体 / 值对象 / 事件 / 日志 / 配置；每张表能一句话说清「为什么存在、哪个流程负责」。别因为提示词出现名词就建表。
3. **实体关系** —— 标基数（1:1 / 1:N / N:M）+ 所有权 vs 引用。
4. **查询场景** —— 最高频 + 最关键的读路径（索引由它推导，不由字段名）。
5. **数据增长** —— 预期行数 / 增速（决定主键、分区、类型）。

模型清晰前**禁出 DDL**。完整任务的输出顺序：业务分析 → 实体列表 → ER 图 → 数据字典 → DDL → 索引策略 → 扩展性 → 风险；小评审/局部修复只取真正有用的部分。

## 核心判断（这是本卡的「大表面」——决策的取舍，不是抄清单）
- **业务优先**：先领域对象后表；优先**小而一致**的模型，别生成大量猜测性表；解释每张表归属哪个业务流程。
- **规范化**：事务型默认 3NF；只有明确读路径/报表/扩展性需求才反规范化，且**必须**写明数据源 + 事实来源 + 同步策略。
- **主键决策**：业务表用**稳定代理主键**——规模化/分布式默认 Snowflake `BIGINT`（跨系统排序用 `UUIDv7`，可读可排用 `ULID`），小型单库/原型才 `AUTO_INCREMENT`；**绝不**用 email/手机/SKU/单号等可变业务标识做主键；主键稳定、永不复用。
- **关系决策**：N:M 用关联表（关系本身有属性就把属性放关联表）；区分所有权（影响级联/生命周期/删除）与引用；**默认逻辑外键**，物理外键只在小型单库（取舍见 docs/lessons/database.md [L45]）。
- **状态机纪律**：每个 `status`/`state` 定义**全部取值 + 合法流转 + 终态**（写注释/数据字典）；可配置/需国际化/管理员维护 → 字典表；稳定常量 → 小整数编码。模糊状态字段判缺陷。
- **业务逻辑位置**：业务逻辑放 service / 领域层 / 应用事务 / 消息队列；**不进**触发器/存储过程/DB 事件（隐藏副作用，见 [L44]）。触发器仅作文档化例外（遗留集成 / 合规最小审计），用时记 名称/作用表/时机/副作用/迁移。
- **索引来自查询场景**：为高频等值、引用列、状态+时间、范围、排序分页、唯一业务标识建索引；组合索引顺序 **等值 > 范围 > 排序**；多租户表 `tenant_id` 最左；热点读可上覆盖索引（写成本 < 读收益时）。别每字段都建、别重复索引、别在索引列上套函数。
- **右尺寸 / 反过度（ponytail 对齐 · AI 最易在此过度工程）**：小 MVP 默认**模块化单体 + 单库**；**不要过早分片 / 微服务 / 队列化 / 事件编排**——仅当真有不同伸缩或可靠性需求才上。过早分布式本身就是 FATAL 级缺陷。
- **多租户决策**：默认**共享库 + 共享 schema + `tenant_id`**（最简起点）；隔离/合规需求强才上 schema-per-tenant 或 db-per-tenant。明说运维复杂度 / 隔离 / 成本 / 查询简单度 的取舍。
- **数据类型**：金额 `DECIMAL`（禁 `FLOAT`/`DOUBLE`，多币种显式存币种，见 [L43]）；时间 UTC；MySQL `utf8mb4`（非 `utf8`）；JSON 仅放稀疏元数据，核心可查询字段独立成列。

## 陷阱 + 反模式（不在此重复，去看真相源）
高频陷阱（软删除唯一索引含 `deleted` / 金额 `DECIMAL` / 业务逻辑不进 DB / 逻辑外键默认）与反模式（EAV、逗号分隔值、过宽表、ENUM 滥用、自引用递归、`DEFAULT 0` 歧义）已在 **`docs/lessons/database.md`（Tier2，含 L42–L45）** curated——设计/评审时读它，别在本卡复制。

## 脚手架机械接点（清单 → 闸：本仓对纯清单的招牌升级）
- **设计期**：`plan-forge` 的 `data-model` lens 在计划评审时按本卡逐条审 PLAN §5/§6——schema 设计 FATAL 在出 DDL 前就拦（主键策略错、缺审计/软删、状态机缺失、业务逻辑塞 DB、契约未冻结）。
- **评审期**：R3 Codex 评审 rubric **维度 #13「数据/持久层」**（`docs/QUALITY-RUBRIC.md`）按本卡判 diff——数据损坏类（软删唯一索引、金额精度）倾向 block。
- **冻结**：定稿 schema / 迁移文件登记 `scripts/_config.ps1` 的 `FrozenPaths`，改动走版本评审（`guard-frozen` 钩子 + rubric#3 机械拦截）。
- **DoD 可机检化**：把「审计字段齐 / 软删唯一索引含 deleted / 金额非 FLOAT / status 有定义」做成**命令 + 退出码**的任务卡 `dod_command`，而非只写清单。卡的 DoD 若引入工具（迁移/校验器），其清单文件须进 `allow_paths`（[L41]）。
- **沉淀**：踩到新 DB 坑 → `scripts/lessons.ps1 add`，会复发的并进 `docs/lessons/database.md`。

## 需要询问或推断（只问最高价值的；要即时输出就标「假设」继续）
数据库引擎？核心业务流程？预期行数/增速？最频繁+最关键查询？单/多租户？单体还是分布式？项目是否接受物理外键？合规/审计/留存/隐私要求？

## 红线
- 不拷专有/第三方/内置技能正文（许可洁净）；要用外部 DB 技能就**就地引用**。
- 模型清晰前不出 DDL；业务逻辑不进触发器/存储过程/DB 事件；schema 是一等冻结资产（改 = 版本评审）。
- 默认逻辑外键；金额 `DECIMAL`；软删表唯一索引含 `deleted`——这三条是数据损坏高发区，评审倾向 block。
