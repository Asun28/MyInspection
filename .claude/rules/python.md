---
paths: backend/**/*.py
---

# 改 Python（backend/）时

> 懒加载指针：只在 Read 到 `backend/**/*.py` 时注入。命名契约**正文**的真相源在
> `CLAUDE.md`「代码与接口命名」Python 节（避免双源漂移，此处不复制）。

- 命名一律按 `CLAUDE.md`「代码与接口命名」的 **Python** 行：模块/函数/变量 `snake_case`、类/Pydantic 模型/异常 `PascalCase`、常量 `UPPER_SNAKE`、私有成员前导单下划线。
- **机检兜底**：命名由 ruff `N`（pep8-naming，见 `pyproject.toml`）确定性拦截。**创建新文件时本 rule 不触发**（paths 只在 Read 触发，见 `.claude/rules/README.md` 坑 a），所以别指望它在新建文件时提醒——靠 ruff 兜底。
- 约定：路径用 `pathlib` 绝对路径；subprocess 用参数列表 + 显式 UTF-8、禁拼 shell；错误分 retryable / non-retryable（见 `CLAUDE.md`「约定」节）。
- JSON 字段与 HTTP/MCP 接口字段口径见同节 HTTP API / MCP 行（全栈 `snake_case`，勿混 camelCase）。
