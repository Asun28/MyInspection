# context/

给 AI 代理的**持久领域上下文 / 知识**：术语表、领域笔记、API / 数据字典、外部参考摘要、长期约定。

**可以放**：可被代理检索（RAG）或直接喂入上下文的精炼资料。
> 入库、团队共享（项目知识的一部分）。私有 / 机密放 `_local/`（永不入库）；原始 / 大体积语料放 `data/`。

## 项目上下文索引

- [`DESIGN.md`](./DESIGN.md) — MyInspection Compose UI 的视觉 token、设计理由、组件规范与使用边界（Google DESIGN.md `alpha` 格式）。
- [`frontend-assets/`](./frontend-assets/) — 已验证、可复用的前端区块与页面模式。
