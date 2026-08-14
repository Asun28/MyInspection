# context/frontend-assets/ — 可复用前端资产沉淀位

> `context/` 的**前端子集**:验证过的**区块/页面模式**沉淀于此(入库共享,供下次做相似页时复用)。
> 由前端生成闭环第 4 段「资产回流」写入(见 `.claude/skills/frontend-flow` 与 `docs/FRONTEND-FLOW.md`)。

## 约定:每个资产记元信息
每沉淀一个资产,在 `assets.md`(或单独 `<asset-name>.md`)登记一条,字段如下：

| 字段 | 说明 |
|---|---|
| **名** | 资产名(kebab-case,如 `data-table-with-filters`) |
| **用途** | 一句话:解决什么前端场景 |
| **依赖 tokens** | 用到哪些 design tokens(颜色/间距/圆角…),便于换主题时核对 |
| **出处** | 哪个项目/页面验证过的(可追溯) |
| **截图或代码位置** | 截图路径 或 `frontend/` 内的实现文件路径 |
| **注意事项** | 坑/前提/不适用场景 |

示例条目：
```
### data-table-with-filters
- 用途：带列筛选 + 分页的数据表，数据密集型列表页通用
- 依赖 tokens：--color-surface / --color-border / --space-2 / --radius-sm
- 出处：<项目X> 的 /items 列表页（已过 5 闸 + webapp-testing 验收）
- 截图/代码：frontend/src/components/DataTable.tsx（截图 _local/...）
- 注意事项：依赖后端契约的 page_size 字段；空态走 token，不硬编码
```

## 红线（沉淀什么 / 不沉淀什么）
- **沉淀**:模式 / 约定 / 元信息(`context/` 性质 = 给 agent 的领域知识,入库共享)。
- **不沉淀**:具体业务组件实现——那仍在 `frontend/`。别把整坨组件源码塞进 `context/`(呼应 CLAUDE.md「项目内复用资产沉淀归位」红线:元层只装约定/标准/清单)。
- 私有 / 机密放 `_local/`(永不入库);原始 / 大体积语料放 `data/`。
