---
paths: frontend/**/*.{ts,tsx}
---

# 改前端（frontend/）时

> 懒加载指针：只在 Read 到 `frontend/**/*.{ts,tsx}` 时注入。细则**正文**真相源在
> `frontend/README.md`（避免双源漂移，此处不复制）。

- 先看 `frontend/README.md` 的 **5 道闸**：① 命名机检（eslint + tsc）② 接口单一真相源（后端 schema → 生成前端类型，禁手写后端实体字段）③ 规划期一致性（plan-forge consistency lens）④ 真跑契约断言（webapp-testing）⑤ 视觉风格单一真相源（design tokens）。
- **视觉风格真相源 = design tokens**（CSS 变量 / theme / Tailwind config）：组件只引 token，**禁硬编码颜色/间距魔法值**（`#3b82f6` / `16px`）。改 token = 改全站。
- 命名契约真相源在 `CLAUDE.md`「代码与接口命名」前端行；**机检兜底**走 eslint `@typescript-eslint/naming-convention`。**创建新文件时本 rule 不触发**（paths 只在 Read 触发，见 `.claude/rules/README.md` 坑 a）——靠 eslint 兜底。
