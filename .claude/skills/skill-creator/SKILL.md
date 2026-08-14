---
name: skill-creator
description: >-
  Use when creating, editing, or optimizing a skill in THIS repo's .claude/skills/, writing a
  SKILL.md, improving a skill's triggering/description, or vendoring a third-party skill. Triggers on:
  "create a skill", "add a skill", "write a SKILL.md", "improve this skill / its triggering",
  "vendor this skill", "做个 skill", "加个技能", "优化触发". Encodes THIS scaffold's skill conventions
  (vendor vs pointer, license cleanliness, index sync, selftest). Do NOT use for normal feature coding
  (task-loop) or for capturing a lesson (lessons skill).
---

# skill-creator — 建/改/优化 skill 路由（编码本仓 skill 约定）

> **路由卡 + 项目约定,非 vendored 专有正文。** Claude Code 内置 `skill-creator`(Anthropic 第一方、专有);本仓许可洁净
> 铁律下**不拷其正文**,这里只放原创路由 + **本仓特有的加 skill 约定**(这是本卡真正的价值——别人写 skill 容易漏掉这些闸)。

## 先用哪个
1. **Claude Code 内置 `skill-creator`**(若本环境有,常以 `skill-creator` / `document-skills:skill-creator` 提供):
   就地用它取 SKILL.md 结构、description 优化、eval 等通用能力。**别把它的正文拷进本仓。**
2. **本仓约定**(下节)——任何在本仓加/改 skill **必须**遵守。

## SKILL.md 基本盘
- 放 `.claude/skills/<kebab-name>/SKILL.md`;frontmatter 至少 `name` + `description`。
- **description 就是触发契约**:写清「何时用 / 何时不用 + 触发词(中英双语)」,agent 据它决定调不调,不靠猜。
  skill 会被**周期复审**(`docs/HARNESS-REVIEW.md` 的 skill 减法/纠偏):**从不触发**的 skill 要么 description 太弱(补触发词)、要么退役。写强 description 是避免被误判退役的关键。
- 一个 skill 单一职责;保持聚焦,别让一张卡什么都管。

## 本仓加 skill 的硬约定（容易漏、必须做）
1. **第三方 skill 看许可,分两条路**:
   - **宽松开源(MIT/BSD/Apache)** → **vendor**:`SKILL.md`(逐字)+ `LICENSE` + `NOTICE.md`(来源 URL / 许可 / vendored 日期 / 「勿手改、从上游重新 vendor」)。范例:`.claude/skills/ponytail`、`.claude/skills/taste-skill`。
   - **专有 / 非宽松(如 Anthropic 内置、GPL/AGPL)** → **不拷正文**,写一张**原创 pointer 卡**就地引用内置/上游。范例:`.claude/skills/frontend-design`、`webapp-testing`、`mcp-builder`、本卡。
   - 判许可拿不准 → 默认走 pointer;别让专有/ copyleft 正文进仓(违反 `docs/LICENSE-POLICY.md` 与模板「纯宽松」承诺)。
2. **命名复用,不新造**:skill 文件夹用 `<kebab>`;别为放它新建顶层目录(根白名单由 `selftest.ps1` `$RootAllow` 机检)。
3. **同步三处索引**(doc-sync,漏一处 selftest 不一定抓但会漂移):`CLAUDE.md` 触发层 skill 行、`CLAUDE.template.md` 交付层 skill 段、`TEMPLATE-README.md` 能力表/目录树。
4. **token 安全**:SKILL.md 里**别出现 init 的替换占位符字面量**(双花括号包大写下划线名,如 PROJECT_NAME / SCAFFOLD_VERSION 那种——它们只该出现在模板产物里;残留进运行时 skill 会让 selftest 闸 ⑧ init 干跑失败)。
5. **跑 selftest 收口**:`pwsh -File scripts\selftest.ps1` 必须 PASS(尤其闸 ⑧ 根洁净/init 干跑、闸 ⑨ 设置完整、闸 ⑪ 交叉链接)。
6. **settings.json 钩子**:若 skill 配套钩子,钩子文件须存在且在 `settings.json` 注册(闸 ⑨ 机检)。

## 红线
- 不 vendor 专有/copyleft 正文;判不准走 pointer。
- 不新建顶层目录或新命名体系;不漏索引同步;改完不跑 selftest 不算完成。
