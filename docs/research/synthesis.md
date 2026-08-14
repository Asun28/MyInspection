# 竞品与 UX 综合分析（Opus 5 主报告 · 2026-08-14）

> 10 个产品深挖（证据主要来自 vendor 帮助中心与公开样例 PDF，营销声明已标注）。姊妹篇：`chapps.md`、`property-inspect.md`、`opensource-indie.md`。本文件 = 结论与可执行建议；逐产品细节精华收录于文末速览表。

## 谁做得最好

**采集：zInspector > RentCheck Fast-Track > Inventory Hive Smart Fill**
- zInspector 的架构级决策：**相机永不关闭**——取景器内滑动切项、点按评级、长按细分级、`!` 提行动项、`Ø` 把无关项从本次与未来巡检中移除（模板在现场自愈）。HappyCo 正在重建同样的形态（"through the camera, not the form"）= 方向正确的最强佐证。
- RentCheck Fast-Track 问对了问题：例行走查为什么要逐项评 200 条？每房间一步、只记异常、10 分钟内完成（标准流程 30–60 分钟）。
- Inventory Hive Smart Fill：评级前置于文本，按评级对自动生成描述（约 32,000 条库），消灭「第二班」誊写。

**报告：Inventory Hive > InventoryBase > myInspections**
- Inventory Hive 退租 PDF 三绝：**封面即答案**（维护汇总 + 按责任方拆分的费用卷积 Tenant £150/Landlord £100/Total）；**照片按匹配引用号真并排**（2.2.1 当时 vs 2.2.1 现在）；**每照带出处页脚**（提供者/拍摄方式/时间/引用号——证据设计而非排版设计）。
- InventoryBase 双列行（`Condition at Inventory | at Check Out`）+ 无变化自动标注（"As Check In"/"As Check In +"）+ Actions 页（Ref/Action/Responsibility/Comments）。
- myInspections（NZ，$8/月）：封面 + 可点击目录 + 逐房间照片就位 + 维护附录——$8 产品做出了完整骨架。

**全行业空白（我们的差异化确认）**
1. **无人有 ghost overlay 重拍**（HappyCo 全帮助中心 / Inventory Hive 发布记录回溯 2020 / IB / PI / Chapps / RentCheck / zInspector 逐一确认缺席）。唯一近似 = 场外小产品 PropCheckAI 的 AI Align。RapidEye 佐证价值：机位一致性显著提升比对准确度、光影是最大假阳性源（"a shadow can look like a stain"）。
2. **无人做损耗折旧算术**（IB 编辑立场明拒）；费用都是手填数字。
3. **无人让工件比 app 长寿**：全行业经由订阅绑定的过期链接交付。最狠差评 = Inventory Hive 用户订阅失效后报告照片全打不开、官方确认「订阅结束我们无权存储数据」——**local-first + 用户自有工件是本项目最强立论**。

## 18 条建议（采纳态与落卡去向）

| # | 建议 | 态度 | 落卡 |
|---|---|---|---|
| 1 | 相机即巡检屏（取景器内滑动/评级/行动项） | [adapt·设计方向] | T2-CAPTURE-UI（v1 允许卡片流起步，相机流为演进向） |
| 2 | **二值主评级（OK / 需注意）+ 长按细分级**——NZ 官方表格与 myInspections 双双二值 | [adopt·UI 层] | T2-CAPTURE-UI（存储枚举不变：OK→GOOD；需注意→FAIR/POOR 选择） |
| 3 | Condition 与 Cleanliness 双独立刻度（法律依据：wear-tear 适用状况不适用清洁，NZ s40(1)(e) vs s49A） | [adapt·收窄] | 清洁作为 Ingoing/Exit 模板的房间级条目建模（T6），不上全项双刻度；全量双刻度列入待用户定 |
| 4 | **绝不逼每项填值**（PI 2.2★ 首因「每格填 N/A」）；zInspector `Ø` 模板自愈 | [adopt] | T1-SCHEMA（property_item_override 抑制表）+ T2-CAPTURE-UI（房间级「余项全标」批量 + 单项「本物业不存在」） |
| 5 | 例行 = 异常驱动模式（Fast-Track） | [adopt·内建] | 两级拍照规则 + 二值主评级已构成此形态 |
| 6 | 评级前置 + 备注自动写 + 缩写展开 | [adopt] | T2-PHRASELIB shortcut 已加；suggestFor 即按评级预填 |
| 7 | 语音在拍照当下绑定到当前项（免去 45–60 分钟事后配对） | [adopt] | T2-CAPTURE-UI（听写入口在项目卡片内，音频随项存） |
| 8 | 缺陷强制拍照=醒目计数不硬拦（HappyCo 橙条「还差 N 项，点击跳转」） | [adopt] | T2-CAPTURE-UI（missingPhotos/missingNotes 驱动橙条）；finalize 仍要求补齐（我们的哈希证据立场） |
| 9 | 标注在设备端、保留原图（IB 桌面端毁原图 = 反面教材） | [v1.1 候补] | 登记 tracker；v1 不做标注 |
| 10 | 基线感知项目屏（前照缩略条 + 有基线徽标） | [adopt] | T3-HISTORY-COMPARE 已含 |
| 11 | **Ghost overlay = 品类空白**，30% 透明度 + 滑杆即可、无需 AI | [adopt·核心差异化] | T1-SPIKE-PLATFORM / T3-HISTORY-COMPARE 已含 |
| 12 | 稳定 ID 而非名字匹配 + 模板快照进报告 | [adopt] | T1 契约已是（RentCheck 按名字匹配改名即断 = 反面教材） |
| 13 | 报告骨架：封面→异常摘要→逐房间照片就位→行动附录；**封面即答案** | [adopt] | T3-REPORT-COMPOSER（封面加不利发现/行动卷积） |
| 14 | 照片内联不进默认附录（HappyCo 默认附录 = 反面教材）；我们内联缩略+附录大图双轨保留（打印证据需大图） | [adapt] | T3-REPORT-COMPOSER 现设计已兼顾 |
| 15/16 | 比对报告布局（双列/折叠无变化项） | [v2] | 需求 §6 明确不做差异对比 PDF；登记 v2 候选 |
| 17 | 责任方一等字段（wear-tear/租客/房东/待查）+ 费用可选 | [部分已有] | Exit wear_or_damage 已有；费用与责任方扩展列入待用户定（v1 不做费用） |
| 18 | 工件必须比 app 长寿（无账号、无过期链接、自选存储） | [adopt·已是架构] | ADR-0002 即此 |

## NZ 专属发现（最高价值段）

### 官方模板即默认模板
政府官方 **Property Inspection Report**（表格号 MB_TEN0004_10/25，租赁协议一部分，[可填 PDF](https://www.tenancy.govt.nz/assets/forms-templates/property-inspection-report-fillable.pdf)）直接给出房间/条目分类学：
- 列：`ROOM AND ITEM | CONDITION ACCEPTABLE?（LANDLORD/TENANTS 双勾选列）| DAMAGE/DEFECTS`——**官方模型 = 二值状况 + 自由文本缺陷**（支撑建议 #2）。
- 房间：LOUNGE · KITCHEN/DINING · BATHROOM · LAUNDRY · BEDROOM 1–4 · GENERAL；每房间重复条目组（Wall/Doors · Lights/Power points · Floors/Coverings · Windows · Blinds/Curtains）+ 房间专属（Kitchen: Cupboards/Sinks/Oven/Refrigerator/Ventilation；Bathroom: Mirror/Bath/Shower/Basin/Toilet/Ventilation；Laundry: Washing machine/Wash tub；Lounge: Heater）。
- GENERAL：Rubbish bins · Locks · Garage/Car port · Grounds · No. keys supplied · **Insulation · Gutters and downpipes · Ground moisture barrier**。
- 另含：**7 点烟雾报警器声明** · 家具与动产清单 · **水表读数** · 双方签名 · 押金收据块（含 2025-12 新增 pet bond）。
→ **T2-ROUTINE-CONTENT 以此表为骨架**；PDF 保留租客同意列（即便单用户）。

### Healthy Homes：官方合规声明即数据模型（T6-HHC 规格）
全部私有租赁 2025-07-01 起须合规；违规惩罚性赔偿至 **$7,200**，协议缺合规声明 $500/tenancy。官方声明（MB_TEN8113_01/26）字段：加热（主起居室所需 **kW**、加热器类型与各自 kW、现代住宅判定、公差/补足、10A 专家信息）；绝缘（天花/地板每区 R 值或厚度、安装/检查日期、类型、「无霉/湿/损/缝」勾选；墙面合理努力声明）；通风（每居室开窗面积 ≥ 地板 **5%**；每风扇直径或排量与所在房间、2019-07-01 前后安装）；水汽与排水（檐槽/落水管/排水至适当出口、围闭地下空间 **50% 周长**遮挡测试、防潮布在且未损）；挡风（壁炉封堵或书面租客同意、无不合理缝隙）。
**硬编码阈值**：天花 R2.9（1/2 区）/R3.3（3 区）、地板 R1.3、2016-07 前天花绝缘 ≥120mm 即过；厨房风扇 150mm 或 50 l/s、浴室 120mm 或 25 l/s（2019-07 后装）；固定加热器最小 1.5kW，所需 >2.4kW 处禁非热泵电加热器；缝隙 >**3mm**（**$2 硬币**塞得进即需封）。
**评级标签抄官方 checklist**（MB_TEN8271_09/25）：二值「**Room to improve / You're on track**」。价值锚：商业评估 $219–249+GST。

### 租赁法功能点
- **s48 通知**：例行 48h–14d、8am–7pm、4 周一次——已在合规引擎；**s48(2)(cb)：Healthy Homes 合规工作进入只需 24h 通知**（+烟雾报警器同）→ 规则配置 entryPurpose 维度再添一档真实用例（ADR-0004 的留门被证实必要）。非法进入惩罚至 $1,500。
- **s123A 记录保留**：租期中 + 结束后 **12 个月**须保留巡检报告（reg 40 明确含**照片与视频**）、维护记录、合规记录，MBIE 索取 10 个工作日内出示；实务建议 6 年（Limitation Act）。→ T5-RETENTION 的「证据保留、联系方式清理」立场与法定完全对齐；12 个月是**法定下限**而非上限。
- **s49A–D 损耗 vs 损坏**：租客对 fair wear and tear 一律免责；**careless 损坏责任封顶 =保险超额与 4 周租金二者较小者**；故意/可监禁罪行/宠物（2025-12 起）损坏不封顶；索赔超限本身违法（$1,800）。**举证责任：房东先证非正常损耗**——同机位基线照+退租照正是卸除举证责任的工件（ghost overlay 的法律级立论）。
- **照片隐私 = 现实风险**：OPC 指引不得拍人/不聚焦租客物品；判例：66 张聚焦租客物品的照片被判侵犯隐私，业界标尺「例行 10–12 张合理」；2026-03 一房东被判赔 $2,643。→ **每照「含租客物品」标记 + 报告排除选项 + 例行拍照数软提示**（入 schema 与采集卡）。
- **烟雾报警器**：光电、10 年密封电池或硬连线、每卧门 3m 内至少一个、每层至少一个，罚至 $7,200；官方表 7 点声明入模板。
- **Tribunal 证据**：接受任何形式证据；实务要求「所有证据备**两份额外纸质副本**」→ PDF 必须打印可读（附录大图双轨的又一理由）。官方定性：「租约开始时已记录的损坏不能归咎租客」。

### NZ 竞争位
myInspections（$8/月，无语音/无对比/**无 Healthy Homes**）· myRent（外包 HH）· Keyhook（AI 巡检、无 HH）· InspectPro（免费 HH PDF 模板、非 app）· MRI Palace（企业档）。**精确空位**：离线 Android + 官方政府模板 + 官方问题集的 HH 自查 + Tribunal-ready、存于房东自有存储的报告。——即本项目。

## 逐产品速览（细节证据）
- **zInspector**：评级 N/S/D+!（长按 E/SC/F/P/DN/DR）；按条件值强制照片/备注（`picture`/`both` 配置）；`Ø` 永久移除；Quick Pictures 模式返程自动出 PDF。免费档 5 物业。
- **RentCheck**：Good/Fair/Poor 可关；按最少照片数强制（非按评级）；居民不可从相册上传（完整性）；比对按**名字**精确匹配（≠ 差异旗 + 过滤器）——改名即断；**离线不能建巡检**、删 app 丢未提交数据；AI Damage Assist 事后扫描。$1–1.25/unit/月。
- **HappyCo**：评级含 **Wear and Tear 一等值**（Good/Repair/Replace/Damaged/Wear and Tear）；「Multiply Section Based on Number of Rooms」自动重复卧室节；批量 Rate All；缺失项橙色 UX（**顶栏橙条计数 + 点击滚到下一缺失项**）；照片默认进附录（反面教材）；未同步巡检离线打不开；500 unit 起卖。
- **InventoryBase/Property Inspect**：六种节类型原语（Detailed/Overview/Simplified/Question/Rating/Meters+Keys+Alarms+Manuals）；**主力条件字段是自由文本 + Dictionary**（fwt→Fair wear and tear）；音频按房间随拍随录 + 人工转写 35–60p/分钟；比对须重拍才生效 + 蓝点标记；标注桌面端且毁原图；Android 2.2★（N/A 逼填 + 报错不指位）。
- **Inventory Hive**：**双 5 级刻度逐级带实例**（Condition 顶格是 New Item 非 Excellent；Cleanliness 顶格 Excellent 非 New——刻意不对称）；Beyond fair wear and tear 状态图标；Smart Fill 评级→自动写描述；退租默认**排除**无变化项（54 页→23 页，但忘勾=报告空白）；订阅失效数据丢失差评。£30/月。
- **myInspections（NZ）**：二值 No Issues/Requires Attention；区域级照片；拍摄时非破坏标注；封面+可点目录+维护附录；「上车前完成报告」。$8/月。
- **Chapps**：房间/元素/观察各 6 照；app 内标注含草图；缺陷跨巡检 carry-forward 汇总；$260/年+每巡检 $3.60–10 信用点。
- **Tap Inspect**：$7.50/单或 $90/月；「发布在车道上完成」定位（细节未验证）。
- **PropCheckAI**：唯一解决重拍对位的产品（AI Align 对齐度量表 + 手动比对滑杆），$2.99/月起——场外验证 ghost overlay 需求真实。

## 未能验证
Tap Inspect 采集细节；SnapInspect NZ 创立说法；Chapps 是否分离 condition/cleanliness 刻度；多数产品 Play 评论全文；Tribunal 照片证据实践规程（不存在官方规程，上文为运营指引+判例合成）。
