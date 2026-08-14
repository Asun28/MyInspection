---
paths: .claude/rules/**
---

# .claude/rules/ — 按路径懒加载的分语言细则

治上下文预算：把「改某类文件才需要的细则」拆出主 `CLAUDE.md`，只在 Claude **Read 到**匹配 `paths:` glob 的文件时才加载（无 `paths:` 则每会话无条件加载，等价写进 CLAUDE.md）。

**坑**：
- **(a) `paths:` 只在 Read 触发、Write/创建新文件不触发**——创建期必守契约（命名/落位/接口口径）不能只靠懒加载，须留主 `CLAUDE.md` 或靠 linter 机检兜底（ruff `N` / eslint naming-convention）。
- **(b) 只用项目级 `.claude/rules/`**，别用用户级 `~/.claude/rules/`（其 `paths:` 匹配有已知 bug）。

**分工**：命名/接口真相源是 `CLAUDE.md`「命名约定」节 + 对应 linter；本目录 rule 只做**指针**，不复制正文（免双源漂移）。

## 现有 rule
| 文件 | paths | 指向 |
|---|---|---|
| `kotlin.md` | `android/**/*.{kt,kts}` | ADR-0001/0003 模块红线 + 冻结物 + Kotlin 风格 |

（`python.md`、`frontend.md` 已删——技术路线定为原生 Kotlin+Compose（ADR-0001），无 Python 后端、无 Web 前端。）
