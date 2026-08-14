---
name: frontend-design
description: >-
  Use for ANY frontend / UI work in this project — building or reshaping web components, pages,
  portals, landing pages, dashboards, or when the user says the UI "looks generic / templated / not
  great", asks to "make it look good", "redesign", or "improve the design". A router: it points you
  at the vendored anti-slop design skill and (when present) Claude Code's built-in frontend-design
  skill, and carries a distilled design discipline so it's useful even if neither is loaded. Do NOT
  use for backend/logic/tests (that's task-loop) or for cutting scope (that's ponytail).
---

# frontend-design — UI design router（设计层路由 · 配 frontend/ 骨架）

> **这是一个路由 + 纪律卡，不是 vendored 第三方文本。** 本仓「许可洁净」铁律下不复制 Anthropic 专有文本
> （anthropics/claude-code 为 All-rights-reserved / Commercial ToS，非宽松开源）。所以这里**只放原创路由 + 通用纪律**，
> 把「重活」指向两处真正的设计技能。做任何 UI 之前先按下面顺序取用。

## 先用哪个（按可用性顺序）
1. **`taste-skill`（本仓已 vendored，MIT）** —— 反 slop 前端技能：brief inference、三档配置（the three dials）、
   设计系统映射、"AI tells" 禁用模式黑名单、redesign 协议、起飞前检查。**这是首选**，离线就在 `.claude/skills/taste-skill/`。
   - **当前世代模型校准（Opus 5 / Sonnet 5 / Fable 5）**：taste-skill 是为**旧代模型**写的反-slop 长提示
     （~1200 行、21 处 MUST/NEVER/ALWAYS、大量反模式枚举与 anti-drift 复读）。当前世代模型**按它开篇的自述用**——
     「每条都是情境化的、没有一条自动触发；先读 brief，只取契合的」：把 MUST/NEVER 墙当**情境启发式**、不当逐字铁律，
     别让 1200 行规则挤掉对 brief 本身的判断（长提示挤占上下文、过度约束反伤当前世代模型的品味）。**vendored 正文不手改**
     （NOTICE 契约）；上游正迭代 v2.0.0 但 2026-07-06 核仍标 experimental，稳定后再评估 re-vendor。
2. **`ui-ux-pro-max`（可选安装的 MIT 插件，非 vendored）** —— 当你需要**具体的**调色板（161 个）/字体配对（57）/
   具体风格（glassmorphism / brutalism / neumorphism / bento / claymorphism…）/按技术栈（React/Next/Vue/Svelte/SwiftUI/
   Flutter/Tailwind/shadcn…）的组件模式 / 图表选型 / UX 准则评审时，用这个**可搜索设计 DB**。
   - **它是约 10MB 的可搜索库插件，本仓刻意不 vendor（太重、违反精简铁律），改为按需安装**：
     `/plugin marketplace add nextlevelbuilder/ui-ux-pro-max-skill` → `/plugin install ui-ux-pro-max`（或用 `/plugin` 菜单）。
   - 装了就用它取具体调色板/风格/栈内组件；没装就退回 taste-skill + 本卡通用纪律。来源（MIT）：<https://github.com/nextlevelbuilder/ui-ux-pro-max-skill>。
3. **Claude Code 内置 `frontend-design`**（若本环境有）—— 在 `/mnt/skills/public/frontend-design/SKILL.md`
   或以插件形式（`frontend-design` / `document-skills:frontend-design`）提供。它是 Anthropic 第一方技能：
   你作为 Claude Code 用户**就地使用**它没问题（受你自己的 Anthropic ToS 约束），但**不要把它的正文拷进本仓**
   （那会让模板不再纯宽松）。有就读它取构图/排版/signature 的更细指引。
4. **本卡的「通用纪律」**（下节）—— 即使上面都没加载，也按它做，保证不出 AI 默认脸。

> **设计层分工**：结构/构图/signature → 本路由卡 + 内置 frontend-design；反 slop 品味 → `taste-skill`；
> 具体调色板/风格/栈内组件/图表 DB → `ui-ux-pro-max`（按需装）；真浏览器验收 → `webapp-testing`。

## Backup 设计 skill（按需自取 · 不 vendor · 按机制分类挑）
> 这些是可选备选,**就地引用/按需安装,不拷进本仓**(许可/质量参差,保模板纯宽松,见 L26)。
> **关键:挑能落「风格真相源(design tokens/theme)」的,而非只吐一次性散 HTML 的——后者改不动现有 React 栈**(见 `frontend/README.md` 闸 5)。
> 三类机制(挑前先分清):
> - **生成型**(文字→页面/原型,greenfield 强):Claude Design(官方原生高保真)、ui-ux-pro-max。
> - **矫正型**(审美/留白/层级矫正,对已锁死组件库的现有 React **效果受限**):Impeccable、taste-skill(已 vendored)。
> - **规范/tokens 型**(产出可复用设计规范/tokens,**最能改 React 栈**):Awesome DESIGN.md、设计系统 tokens 库。
> - 其它备选:Open Design(开源平替,可本地私有化)、Huashu(商务后台风)。
> 选用前过本仓硬规则(许可宽松、不绑死有主见组件库);用了哪个、为何,记一条 lesson 或 ADR(L26:工具可换、记经验库)。

## 通用纪律（原创蒸馏，可独立使用）
- **扎根主题（ground in the subject）**：先一句话定死「这是什么产品 / 给谁 / 这页唯一任务」。独特性来自主题自身的材料与术语，不是套模板。
- **Hero 即论点**：开篇放主题世界里最有代表性的那一个东西（标题/图/动效/实时 demo 皆可），别默认「大数字+小标签+渐变」。
- **紧凑 token 系统**：4–6 个命名 hex；2+ 字体角色（克制使用的特征 display 体 + 易读正文体 + 可选数据/caption 体）；一句话布局概念；**一个 signature 元素**——这页被记住的唯一记忆点。
- **避开三种 AI 默认脸**：① 奶油底 + 高对比衬线 + 赭石点缀；② 近黑底 + 单一荧光色；③ 通用细线报纸体。除非 brief 明确要，否则别把自由度花在这三种上。
- **把张扬只花在一处**：signature 元素大胆，其余一律安静克制；删掉任何不服务于 brief 的装饰（Chanel：出门前对镜摘掉一件）。
- **质量底线（不喧哗地达标）**：响应式到移动端、`:focus-visible` 键盘焦点可见、`@media (prefers-reduced-motion: reduce)`、对比度过 WCAG AA。
- **文案也是设计材料**：从用户视角命名（"管理通知" 不是 "webhook 配置"）；主动语态、句子大小写；空态/错误是给方向，不是发情绪。
- **CSS 选择器特异性**：当心 `.section` 与 `.cta` 这类基于类型/元素的选择器互相抵消（padding/margin 常踩），结构化好特异性。

## AI-slop 视觉清单（渲染后对着页面自查 · 命中即回上面纪律改）
> 上面「避开三种 AI 默认脸」是**配色/字体**层的三大坑；本清单是更细的**渲染后自查表**——常见的 AI 生成视觉「破绽」。
> 按 taste-skill 的用法当**情境启发式、不当逐字铁律**：命中一条不必然错，但**命中多条 = AI 默认脸**，说清哪条、为什么，回纪律改。
1. 紫 / 靛 / 蓝→紫**渐变**配色（最常见的 AI tell）。
2. **三列特性网格**：彩色圆圈图标 + 标题 + 两行描述，对称重复三份。
3. 彩色圆圈里塞图标当章节装饰。
4. **全局居中**：所有标题 / 描述 / 卡片一律 `text-align:center`。
5. 所有元素统一的「Q 弹」大圆角。
6. 装饰性色块 / 浮动圆 / 波浪 SVG 分隔条。
7. emoji 当设计元素（火箭、emoji 项目符号）。
8. 卡片左侧彩色竖边（`border-left:3px solid`）。
9. 通用 hero 文案（「欢迎来到…」「释放…的力量」「一体化解决方案」）。
10. 千篇一律的章节节奏（hero → 3 特性 → 评价 → 定价 → CTA）。
11. 拿 `system-ui` / `-apple-system` 当**主**字体（= 放弃排版的信号，除非 brief 明确要系统字体）。

## 流程（两遍 + 自我批评）
1. 先在脑内 brainstorm 一份紧凑设计计划（color/type/layout/signature）。
2. 拿计划对照 brief：哪一块读起来像「给任何同类页都会产出的默认值」就改掉，并说清改了什么、为什么。
3. 确认相对独特后再写码，严格照修订后的计划，每个颜色/字体决定都从 token 系统派生。
4. 边写边自我批评；环境允许就截图核对（一图胜千 token）。

## 红线
- 不把内置/第三方专有技能的正文拷进本仓（许可洁净）。要用就**就地引用**它们。
- 不破坏既有功能契约：复制按钮等交互用原生 API、不引外部 JS 依赖、不把不可信源文本标 `|safe`（防 XSS）。
