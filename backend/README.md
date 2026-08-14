# backend/

后端 / API / 代理服务代码根。框架自选（FastAPI / Litestar / Flask…，下例用 FastAPI）。

**可以放**：HTTP 或 RPC 服务、AI 代理编排（agent / workflow runner）、工具(tool)实现、LLM 客户端封装、任务调度。
Python 惯用 `backend/app/` 作可导入包（按需分 `api/ agents/ tools/ services/ core/`），测试放 `backend/tests/`。

---

## 后端契约 + 验证（5 道闸 · 与 `frontend/README.md` 对称）

> 痛点：后端是「接口单一真相源」的**发出端**——前端闸 2 由后端 schema 生成类型。后端字段/契约一漂移，全栈跟着漂。
> 下面 5 道闸把漂移在 DoD/CI/规划期机检掉。命名规范权威在 `CLAUDE.md`「代码与接口命名」节（Python 由 ruff `N` 机检）。

### 闸 1 · 命名 + 类型机检（ruff + mypy，进 DoD）
`pyproject.toml`（由根 `pyproject.toml.example` 渲染）已配 ruff（含 `N`=pep8-naming）+ mypy。任务卡 `dod_command` 真的跑：
```
uv run ruff check . && uv run mypy app && uv run pytest -q
```
- ruff `N` 机检：类 `PascalCase`、函数/变量 `snake_case`、常量 `UPPER_CASE`（与 `CLAUDE.md` 命名表同源）。
- pydantic 模型字段一律 `snake_case`；对外 JSON 若需 `camelCase` 用 `alias`/`model_config`，**别在 Python 侧用 camelCase**。

### 闸 2 · 接口单一真相源（pydantic → OpenAPI，喂前端闸 2）
**schema 先于实现**：先定 pydantic 模型 → FastAPI 自动产 OpenAPI → 前端由它生成类型（见 `frontend/README.md` 闸 2）。
- 导出契约：`python scripts/export-openapi.py > openapi.json`（见 `backend/scripts/export-openapi.py.example`）。
- 契约一旦冻结，演进走版本评审——把 schema 文件登记进 `scripts/_config.ps1` 的 `FrozenPaths`，`guard-frozen` 钩子 + `review.ps1` 据此拦就地改。
- 铁律：**对外字段名以 OpenAPI 为准**；改字段=改契约=版本评审，不是顺手重命名。

### 闸 3 · 规划期一致性（plan-forge 的 `consistency` + `data-model` lens）
`plan-forge.mjs` 的「内部矛盾猎手」审「前端调用 ↔ 后端契约」字段同构；`data-model` lens 审实体/关系建模。规划期就堵，不等代码期。

### 闸 4 · 确定性离线测试（pytest · 模型只在上游）
> 一句话原则：每个测试 = agent 能当 `dod_command` 跑的机检 exit 0/1；LLM 只在闸的上游（帮写/审测试），绝不进闸内。
- **离线 fail-closed**：测试里**零真实网络**——LLM/HTTP 客户端一律 mock/fake（见 `backend/conftest.py.example` 的网络阻断 fixture）。
- **固定时钟 + 确定性种子**：冻结 `datetime.now`、固定随机种子；不手写 `sleep`。
- **DB 用临时实例**：SQLite/容器化 Postgres 起后即销，测试间不共享状态。
- **`-p no:randomly` 或固定顺序**：顺序敏感即隐藏耦合。
- DoD：任务卡跑上面闸 1 的三连（ruff+mypy+pytest），退出码即闸。

### 闸 5 · 业务逻辑不进数据库（与 database-design 同源）
状态机/校验/计算等**业务逻辑留在应用层**，**不进** DB 触发器/存储过程/事件——见 `database-design` skill 与 `docs/lessons/database.md`。逻辑外键、审计/软删/状态字段按 rubric #13 设计。

### 分层 / 配置 / 注入（创建期契约 · 防「改一处牵全身」）

> **约定节，不编号**——上面 5 道闸管机检漂移，本节管文件一落笔就要守的结构方向。
> 常驻速记 = `CLAUDE.md`「关键不变量」的配置单点 bullet（创建期契约不能只靠懒加载文档，两处互指）。

- **依赖方向单向**：`api → services → core`；`core/` 不得 import 上层。可选机检：import-linter layers 契约（工具无关，例）。
- **配置单点**：env 只在 `backend/app/core/config.py`（如 pydantic-settings，例）读入一次；业务代码**禁散读 `os.environ`/`os.getenv`**——测试才能整体覆写。
- **注入不散取**：clock / LLM 客户端 / storage 经构造参数或框架 DI（如 FastAPI `Depends`，例）注入；`conftest.py` 的 `frozen_now`、mock/real 切换（registry）都以此为前提。

---

## 可观测（R3 评审第 12 维）
结构化日志（JSON）带请求 ID / 用户 ID / 关键参数，**敏感字段脱敏**（见 `docs/DELIVERY-OPS.md`）。别用裸 `print`。

## 配套样例文件（本目录）
`app/main.py.example`（最小 FastAPI + pydantic 契约）· `scripts/export-openapi.py.example`（导出 OpenAPI 喂前端闸 2）· `conftest.py.example`（pytest 离线阻断 + 确定性 fixture）· `tests/example_test.py.example`（确定性离线单测示例）
—— 拷掉 `.example` 后缀即用；下游按真实领域替换 schema 与路由。**红线**：这些是**骨架示例**，非业务实现——元层只装工具无关的约定/标准/清单（见 `CLAUDE.md`「改动时的硬规则」）。
