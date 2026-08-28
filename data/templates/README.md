# data/templates — 巡检模板内容真相源

> 写模板内容的人看这一份就够。引擎（解析 / 校验 / 入库 / 版本对齐）在
> `android/core/src/main/kotlin/nz/myinspection/core/template/`，由 `T1-TEMPLATE-ENGINE` 落地。
> 本目录当前**只有这份指南**，真实模板内容由 `T2-ROUTINE-CONTENT`（Routine）与
> `T6-TEMPLATES-REST`（Ingoing / Exit / Annual）填。

## 文件形态

一类型一文件，命名 `<type 小写>-v<版本号>.json`，如 `routine-v1.json`、`exit-v2.json`。

```json
{
  "type": "ROUTINE",
  "version": 1,
  "items": [
    {
      "stableId": "KIT-BENCH-01",
      "area": "INTERIOR",
      "room": "KITCHEN",
      "textEn": "Bench tops and splashback",
      "textZh": "厨房台面与挡水板",
      "allowedStatuses": ["GOOD", "FAIR", "POOR", "NOT_APPLICABLE"],
      "photoRule": "ADVERSE_ONLY"
    }
  ]
}
```

**未知字段一律报错**，不是被忽略——`textZH` 这种拼错的键会当场让加载失败，而不是静默变成空文案。

## 房间定义

`rooms` 声明模板允许使用的房间键；`repeatable: true` 表示后续采集流程可为该键建立多个实例。
只要 `rooms` 非空，每条 item 的 `room` 就必须与其中一个 `key` **逐字精确相等**。数组顺序即房间模板序。

```json
{
  "type": "ROUTINE",
  "version": 1,
  "rooms": [
    { "key": "BEDROOM", "repeatable": true },
    { "key": "KITCHEN", "repeatable": false }
  ],
  "items": [
    {
      "stableId": "BED-WALL-01",
      "area": "INTERIOR",
      "room": "BEDROOM",
      "textEn": "Walls and ceiling",
      "textZh": "墙面与天花",
      "allowedStatuses": ["GOOD"],
      "photoRule": null
    },
    {
      "stableId": "KIT-BENCH-01",
      "area": "INTERIOR",
      "room": "KITCHEN",
      "textEn": "Bench tops",
      "textZh": "厨房台面",
      "allowedStatuses": ["GOOD"],
      "photoRule": null
    }
  ]
}
```

## 字段规则

| 字段 | 规则 |
|---|---|
| `type` | `ROUTINE` / `INGOING` / `EXIT` / `ANNUAL` 四选一，大写。拼错不会被当成第五类收下 |
| `version` | ≥ 1 的整数。同一 `type` 下**同一 version 只能有一份活跃内容**（数据库唯一索引拦） |
| `rooms` | 可省略；省略或空数组保持旧模板兼容。非空时 `key` 不得空白或重复，`repeatable` 省略即 `false` |
| `items` | 非空数组。**数组顺序即模板序**：报告按它排版、内容哈希按它定序，改顺序 = 改内容 |
| `stableId` | 模板内唯一、跨版本恒定。建议 `房间缩写-对象-序号`（`KIT-BENCH-01`），只为可读，不承载语义 |
| `area` | 报告分区，如 `INTERIOR` / `EXTERIOR` / `GROUNDS`。非空 |
| `room` | 模板层房间键，如 `KITCHEN` / `BEDROOM`。非空。建巡检时按物业实例化成 Bedroom 1..N |
| `textEn` / `textZh` | 双语条目文案，两者都必填、都不得为空白。报告双语并排，缺一边就是缺一半 |
| `allowedStatuses` | 该项允许的评级，非空，且必须落在本 `type` 的域内（见下） |
| `photoRule` | `ROOM_PANORAMA` / `ADVERSE_ONLY` / `null`（无强制拍照要求） |

### 评级域按类型定

- `ROUTINE` / `INGOING` / `EXIT`：`GOOD` `FAIR` `POOR` `NOT_APPLICABLE`
- `ANNUAL`：`NO_ISSUE` `MONITOR` `MAINTENANCE_ITEM` `SIGNIFICANT_DEFECT` `NOT_APPLICABLE`

同一条项目不必列全，但**列出来的每一个都得在域内**。年检表里写 `GOOD`、出租表里写 `MONITOR`，都会被拒。

### photoRule 的含义

- `ROOM_PANORAMA`：房间级全景，1–2 张强制。
- `ADVERSE_ONLY`：只有当该项评为**不利发现**时才强制拍照。`NOT_APPLICABLE` 不逼拍照。
- `null`：无强制要求，拍不拍随现场。

## 改模板的三条铁律

1. **改措辞不改 `stableId`。** 历史对齐只认 `stableId`，不认名字——改了 id，这一项在历史对比里就断成
   「旧的消失了 + 新的冒出来」，房客搬走时的对照直接失真。
2. **加项给新 `stableId`，别复用退役的 id。** id 全局意义上一次性使用。
3. **内容任何改动都走新 `version`。** 已被巡检引用的版本在数据库层不可再改（连加项都被拒）。
   同一 `version` 下换内容 = 那一版报告的重渲会失真，`content_hash`（模板文件字节的 SHA-256）
   就是用来把这种漂移暴露出来的。

版本升级后，新旧两版按 `stableId` 对齐成三份清单（沿用 / 新增 / 移除），历史对比据此渲染。

## 怎么验

内容卡的 DoD 直接拿引擎的校验器当闸：一次跑出**全部**问题，每条点名是哪个 `stableId` 缺什么，
例如 `KIT-BENCH-01: textZh is blank`、`KIT-BENCH-01: status NO_ISSUE is not allowed for template type ROUTINE`。
从上往下改完再跑一遍即可，不必一条一条试。

> 本目录的内容默认被 `.gitignore` 排除（`data/*`），真实模板文件入库时须 `git add -f` 显式纳入。
