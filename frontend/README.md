# frontend/

前端 UI 根。框架自选（React / Vue / Svelte…）。

**可以放**：聊天 / 对话界面、控制台、设置页。静态资源就近放此目录；`dist/`、`node_modules/` 已 gitignored。

---

## 前端验证 + 命名/接口一致性（5 道闸 · 适配 AI 生成的前端）

> 痛点：AI（含 Claude Design / 设计技能）生成的组件，其**命名**与**接口字段**可能与脚手架标准、与后端契约漂移。
> 下面 5 道闸把"漂移"在 DoD/CI/规划期机检掉。命名规范权威在 `CLAUDE.md`「代码与接口命名」节，本文件是其前端落地。

### 闸 1 · 命名机检（eslint + tsc，进 DoD）
拷 `eslint.config.mjs.example` → `eslint.config.mjs`、`tsconfig.json.example` → `tsconfig.json`，装依赖后让**任务卡 `dod_command` 真的跑**：
```
npx eslint . --max-warnings=0 && npx tsc --noEmit
```
- eslint 的 `@typescript-eslint/naming-convention` 机检：组件/类型 `PascalCase`、变量/函数/hook `camelCase`（hook 以 `use` 开头）、常量 `UPPER_CASE`。
- 组件文件名 `PascalCase.tsx`、其余 `kebab-case.ts`；CSS 类 `kebab-case`（与 `CLAUDE.md` 命名表同源）。
- `tsc --noEmit` 抓类型不齐——配合闸 2，前后端字段对不上会编译期就红。

### 闸 2 · 接口单一真相源（后端 schema → 生成前端类型）
**前端不准手写后端返回的类型**，只准 import 从后端契约生成的类型 —— 字段名物理上无法漂移：
- 后端有 OpenAPI/JSON-Schema → 用 `openapi-typescript` 生成 `src/types/api.ts`（见 `scripts/gen-api-types.sh.example`），前端只 `import type { ... } from './types/api'`。
- 无 OpenAPI → 后端契约与前端共享一个 `types/` 目录（contract 卡冻结它，登记进 `_config.ps1` 的 `FrozenPaths`）。
- 铁律：**禁止在前端重新声明后端实体的字段**（`userId` vs `user_id` 这类漂移由此根除）。生成物可 gitignore、构建期重生成。

### 闸 3 · 规划期一致性（plan-forge 的 `consistency` lens）
`plan-forge.mjs` 的「内部矛盾猎手」lens 已含**前端**：审「前端组件 props/调用 ↔ 后端契约/对外 schema」字段是否同构。规划期就堵接口不一致，不等到代码期。

### 闸 4 · 真跑契约断言（webapp-testing）
`webapp-testing` skill 真浏览器验收时，不只看"能跑/好看"，还**断言关键字段确实来自后端 schema**（渲染出的数据键名 == 契约字段名），截图留证。产物落 `_local/`。

> **视觉忠于设计稿是另一回事**：本闸断言**数据**保真（键名 == 契约），不管**视觉**是否忠于批准的设计稿。若本页有源视觉目标（pencil 稿 / mockup），交付前另做一次**设计保真 QA**（spec vs render · 5 个保真面 · P0–P3 · 上游辅助非闸 · L25）——见 `docs/FRONTEND-FLOW.md` §3「设计保真 QA」。

### 闸 5 · 视觉风格单一真相源（design tokens）
和闸 2「接口真相源」同构——**视觉风格不准散落在各组件/各页 HTML，集中在 design tokens**，一改全栈生效、且不被组件库锁死。这是「设计 skill 改不动 React 技术栈风格」的治本招：
- **风格真相源 = CSS 变量 / theme 文件 / Tailwind config**（颜色/字号/间距/圆角/阴影都在此）。组件只引 token，不写魔法值。
- **设计 skill（Claude Design / Impeccable / taste-skill …）改 tokens，不改一次性散 HTML** —— 只吐散 HTML 的 skill 改不动现有 React 栈（有主见组件库的 CSS-in-JS 会盖过外部样式）。
- **选开放可 override 的组件**（shadcn/ui 的 owned-code、Tailwind 原子类），避免 MUI/Ant Design 这类把视觉锁死的有主见库——否则「改 token 全站变」不成立。
- **铁律**：禁止在组件里硬编码颜色/间距魔法值（`#3b82f6` / `16px`），一律走 token（`var(--color-primary)` / Tailwind 类）。这样「改 token = 改全站风格」才成立，设计 skill 才真能改动 React 栈。

---

## 前端测试（确定性闸 · 模型只在上游）

> **一句话原则：每个测试 = agent 能当 `dod_command` 跑的机检 exit 0/1；模型（LLM/视觉）只在闸的上游，绝不进闸内。**

**最小栈（MVP，别加 Storybook/Chromatic/Cypress/BackstopJS）**：
- **Vitest 4** —— 单元/组件测试（纯函数、渲染断言）。
- **Playwright** —— E2E（真浏览器、真交互）。
- **route-mock** —— `page.route()` 喂固定夹具保确定。
- **@axe-core/playwright** —— 一条 a11y 断言进闸。

**确定性铁律**（这才是重点）：
- **DOM/文本断言优先于像素截图**：`getByRole` / `getByTestId` / `toHaveText` 跨平台稳、免 Docker。`data-testid` 当公共测试 API（kebab-case、行为导向）。
- **离线 fail-closed**：Playwright `page.route()` 放行预期、`route.abort()` 掉一切意外请求 —— 测试里**零真实网络**。
- **固定时钟 + 关动画 + 不手写 sleep**：`page.clock` / `vi.setSystemTime` 冻结时间；config 里 `reducedMotion:'reduce'`；只靠 auto-wait / `waitForLoadState`。
- **`retries:0`**：retry 会掩盖 flaky = 容忍不确定 = 闸失效。

**dod_command 闸**：任务卡跑 `npm run verify`（= `check && test && test:e2e`，即 命名/类型 + 单元 + E2E 一把跑，退出码即闸）。

**跨 OS 截图警告（可视回归默认关闭）**：`toHaveScreenshot` 是**可选/次要**手段，**仅当布局本身是被测对象**时才开。像素基线只能在**固定 tag 的 `mcr.microsoft.com/playwright:<tag>` Docker** 里生成——Windows 本机基线会因 OS 字体渲染差异在 Linux CI 上炸。**agent 绝不自动跑 `--update-snapshots`（重做基线是人工闸）。** 默认保持关闭。

**非闸**：AI 自愈 / 视觉-LLM 校验 **不是闸**（非确定、要联网）；它们只在上游帮写/审测试，永不替代 exit 0/1 的机检。

与上文「5 道闸」（命名 eslint+tsc / 生成类型 / plan-forge consistency / webapp-testing 契约断言 / design tokens 风格真相源）同守「接口 + 视觉一致 + 可验收」红线；真浏览器探索性验收走 `webapp-testing` skill（产物落 `_local/`、dev-time 不进运行期）。

---

## 配套样例文件（本目录）
`eslint.config.mjs.example` · `tsconfig.json.example` · `package.json.example` · `scripts/gen-api-types.sh.example`
测试：`vitest.config.ts.example` · `vitest.setup.ts.example` · `playwright.config.ts.example` · `src/example.test.ts.example` · `e2e/example.spec.ts.example`
—— 拷掉 `.example` 后缀即用；下游按实际框架（React/Vue/Svelte）微调。设计层用 `frontend-design` / `taste-skill` skill。
