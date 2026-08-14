---
name: webapp-testing
description: >-
  Use to verify a RUNNING web app / UI by driving a real browser — launch it, click through it,
  screenshot it, read console/network logs, assert behavior. Uses Playwright MCP (@playwright/mcp, the
  2026 agent-browser standard) when connected, else built-in webapp-testing — both UPSTREAM exploration,
  never the deterministic DoD gate. Triggers on: "test the app", "verify the
  UI / the page / the portal works", "screenshot it", "does the frontend work", "show me it running",
  "确认页面跑起来", "截个图看看", "验证前端". Complements R2 TDD (unit) and verify.ps1 with a real-browser
  acceptance path. Do NOT use for unit tests (that's task-loop/TDD) or for visual design (that's
  frontend-design / taste-skill).
---

# webapp-testing — UI 真跑验收路由（Playwright · 配前端设计层）

> **路由卡 + 项目约定,非 vendored 专有正文。** Claude Code 内置了 `webapp-testing`(Playwright 工具集,Anthropic
> 第一方、专有);本仓许可洁净铁律下**不拷其正文**,这里只放原创路由 + 本仓用法。它补上「加了设计层却看不到渲染效果」
> 的缺口:给 UI 一条**真浏览器验收**路径,与 R2 TDD(单元)、`scripts/verify.ps1`(确定性闸)互补。

## 先用哪个
1. **Playwright MCP(`@playwright/mcp`,微软官方)——上游探索首选,按需接入、不 vendor。** 2026 的 agent 浏览器事实标准:
   accessibility-tree 模式(不靠视觉模型、更稳更省),提供 navigate/snapshot/click/console/network 等工具,让 AI **真驱动浏览器**探索它自己建的 UI。
   接入(MCP server,外部工具,类比 ui-ux-pro-max 按需 `/plugin`——按需接、不进模板依赖):
   `claude mcp add playwright npx @playwright/mcp@latest`(或在 MCP 配置里加 `npx -y @playwright/mcp`)。装了就用它做交互验证;没装就退回下面第 2/3 项。
   **铁律(关键):它运行期有模型在回路=非确定+联网,只能当【上游探索】,绝不当 DoD 闸**(见下「两层分清」)。
2. **Claude Code 内置 `webapp-testing`**(若本环境有,常以 `document-skills:webapp-testing` 提供):就地用它启动本地 app、
   用 Playwright 交互、截图、看 console/network 日志。**别把它的正文拷进本仓。** 与 Playwright MCP 二选一即可(都为上游探索)。
3. **本卡通用纪律**(下节):即使上面都没加载,也按它做基本浏览器验收。

## 通用纪律
- **先把 app 跑起来**再测:本地起服务(或打开静态 HTML),拿到 URL/路径。
  (Windows 上拉起 npm/vite 等 `.cmd` 经 `cmd.exe` 间接——`Start-Process -FilePath 'cmd.exe' -ArgumentList '/c','npm','run','dev'`;**别**用 `Start-Process npm -NoNewWindow`,会报 "%1 is not a valid Win32 application"。见经验 L9。)
- **每步程序化断言(不只终态)**:每次 navigate/click/输入后**立即**断言期望的状态变化(URL / 可见文本 / DOM / network 响应),别只在末尾断一次——逐步断言能精确定位坏在哪一步,也直接是把上游探索固化成闸门脚本的草稿。
- **驱动而非臆测**:用 Playwright 打开页面、定位元素、点击/输入、断言可见文本/DOM/状态——别靠读源码猜行为。
- **截图是证据**:关键状态各截一张(一图胜千 token);对照设计意图(配 `frontend-design`/`taste-skill` 的 token 与 signature)。
- **查 console/network**:捕获报错、404、未捕获异常——这些是「看着对、其实坏」的隐蔽 bug。
- **契约断言(前后端对齐)**:不只验「能跑/好看」,还断言**渲染出的数据键名 == 后端契约字段名**(抓 network 响应或 DOM data-* 比对 schema 字段),证明前端确实消费的是后端 schema、没字段漂移。这是 `frontend/README.md` 闸 4,与命名机检(eslint+tsc)、生成类型(闸 2)、plan-forge consistency lens(闸 3)同守一条「接口一致」红线。
- **可达性/响应式抽查**:键盘焦点可见、移动端不塌、`prefers-reduced-motion` 生效(设计层质量底线)。

## 两层分清:探索(模型在内) vs 闸门(模型在外) —— 最重要
> 2026 调研结论(经验 L25):任何**运行期有 LLM/视觉模型在验证回路**的东西(self-healing、VLM 看截图、browser-use、本 skill 的交互式驱动)都是**非确定 + 联网**,**绝不能当 DoD 闸**。本 skill 的浏览器驱动属**上游探索**:用它发现问题、**写出**断言,然后**提交一个纯 Playwright 断言脚本**当真正的闸。
- **上游(本 skill,模型在内)**:交互驱动、截图、看 console/network、契约比对——产出是「该断言什么」。非确定,只为发现与起草。
- **闸门(模型在外,exit 0/1)**:把上游发现固化成**确定性脚本**——`getByRole`/`getByTestId`/`toHaveText` + 零 console error + 无失败请求 + 一条 axe 断言;`page.route()` fail-closed 离线;`retries:0`;固定时钟、关动画。这才是任务卡 `dod_command` 跑的东西(`frontend/` 的 `npm run verify`)。
- **DOM/文本断言优先于像素截图**(跨平台稳、免 Docker)。视觉回归(`toHaveScreenshot`)仅当布局本身被测才开,baseline **只在固定 tag 的 `mcr.microsoft.com/playwright` Docker** 生成,**agent 绝不自动 `--update-snapshots`**(重做基线=人工闸)。
- **跳过所有 AI self-healing SaaS**:非离线、非确定,与 exit 0/1 闸结构冲突。

## 本仓约定（重要）
- **产物落 `_local/`,不入库**:截图、trace、临时 HTML 一律写 `_local/`(已 gitignore),**绝不落仓库根**(根洁净闸会拦)。
  交互式探索可开 **Playwright trace / video** 作可复审证据(同样落 `_local/`):trace 比孤立截图更能还原「点了什么 → 看到什么」,便于把发现固化成断言。它是**探索期证据**,不进 `dod_command` 闸(闸只跑确定性断言脚本)。
- **是 dev-time 验收,不是运行期依赖**:浏览器自动化只在验证时用,不进产品运行期、不破坏「运行期离线/确定性」边界;
  `dod_command` 若含浏览器验收,标清它是人工/演示卡的验收,别塞进确定性禁网门禁(参考 specs 投影约定)。
- **装 Chromium 要联网**:`playwright install chromium` 从其 CDN 下载,属**构建期联网**(显式批准),离线环境会失败——
  无网时退回静态断言(解析生成的 HTML)而非浏览器驱动。
- 与 `verify`/`run` 等命令互补:本卡负责「真浏览器看一眼」,确定性回归仍走 `verify.ps1`/pytest。

## 红线
- 不 vendor 内置/专有技能正文,要用就就地引用。
- 测试产物不入库、不落根;不把浏览器自动化做成产品运行期依赖。
