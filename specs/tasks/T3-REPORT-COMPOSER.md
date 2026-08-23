---
id: T3-REPORT-COMPOSER
title: 纯 Kotlin 报告布局引擎：分页/缩略图排版/双语行配对/哈希页脚 + 黄金布局树（★冻结点级质量）
depends_on: [T1-CANON-HASH, T2-CAPTURE-CORE]
parallelizable_with: [T2-CAPTURE-UI, T3-FINALIZE]
status: todo
branch: T3-REPORT-COMPOSER
worktree: C:\wt\T3-REPORT-COMPOSER
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/
  - android/core/src/test/kotlin/nz/myinspection/core/report/
forbid:
  - 任何 android import / 位图解码（渲染归 T3-PDF-RENDERER）
  - 房客版包含 remediation/内部判断字段（版本分离在 composer 层强制）
non_goals:
  - PDF 字节输出（T3-PDF-RENDERER）；LLM 建议内容（T7-REMEDIATION 注入房东版插槽）
acceptance:
  # 封闭验收集合：以下即本卡「完成」的全部内容。清单内每条须有可证伪测试。
  # 页面常量：A4 210x297mm，页边距 15mm，正文顶 y=15mm，正文底 y=272mm，正文高 257mm，正文宽 180mm。
  # 几何断言一律用**字面量数字**，不得拿 ReportComposer 的伴生常量作期望值——那是拿生产值和自己比。
  - "A1 黄金布局树逐块钉死：固定 fixture（ReportTestFixtures.report()）产出精确页数，逐页断言 (类型:标识@y:height) 全序列，且 plan.dataHash == ea9cd02e76bf79ac320df5795e51433b3200eb28900ab8837479a0c15eaf452d"
  - "A2 项目内联缩略图几何用字面量断言：thumbnail.widthMm == 40、xMm == 140、首张 yMm == 0、第 n 张 yMm == (n-1)*56、图片框高 == 40、含 3 行 caption 的槽高 == 54；同一行的 textRuns 宽 == 135 且 xMm+widthMm <= 140"
  - "A3 附录大图：每张图片框 180mm 宽、含 1 行 caption 的槽高 == 122；**每个附录页恰好 2 张**且每页都带 photo-appendix 段标题——含一枚 3 行 caption 的对抗夹具证明它仍是 2 张、第二页仍有标题（当前会退化成每页 1 张且第二页丢标题）"
  - "A4 房间全景图槽：图片框 44mm 高、180mm 宽，含 1 行 caption 的槽高 == 50，xMm == 0（相对所在 PlacedBlock）"
  - "A5 图槽不可分割且不重复：对每个 purpose，全文档内同一 photoId 恰好出现一次（**含 ItemRowBlock.thumbnails 内的图槽**）；对抗夹具须同时给「单项 >= 5 张照片」与「单项 >= 5 张照片 + 2 万字符备注」两种输入——后者当前会把同一张照片发出 3 次"
  - "A6 图片框尺寸是计划的字段而非渲染器的推导：ImageSlotBlock 显式携带图片框高度（与含 caption 的 heightMm 分开），断言 INLINE 缩略图 == 40、房间全景 == 44、附录 == 116；渲染器不得靠 textRuns[0].yMm 反推"
  - "A7 caption 有界：超长 reference 下 textRuns.size == 3、末行以 U+2026 结尾、且**末行字符数 <= 该列的量宽预算**（当前末行比列宽多一个字形，内联缩略图那一列会画进页边距）；reference/source/capturedAt 三个结构字段仍保留完整原值"
  - "A8 页脚渲染文本：每页 footer.textRuns 拼接后含 dataHash 的**前 12 字符**（字面量 12，不引用生产常量），且任一 run 都不含 64 字符全摘要；FooterBlock.dataHash 仍是全摘要"
  - "A9 孤行控制两条，各带**能证伪的**边界夹具：(a) 剩余空间恰好容得下 12mm 房间标题、容不下其首行时，标题与首行同页迁移——夹具须把上一页填到 y<=260 才成立（当前夹具填到 263，标题自己就放不下，闸门根本没被执行到）；(b) 空房间的标题与其首张全景图同页"
  - "A10 80 照 fixture：断言**精确页数**（不用 <= 上界），且每个可绘制单元——含 ItemRowBlock.thumbnails 内的每个图槽——的**绝对** y+height <= 272；当前 80 个缩略图全部塞进一个 18mm 行、超出页底 4233mm 而测试仍绿"
  - "A11 双语成对不拆页：构造一枚使分页边界恰好落在 en 行与 zh 行之间的夹具，断言两行落在同一块且后续 chunk 内不再出现该对；把 splitBlock 的 paired 特判换成空列表后该夹具必红"
  - "A12 版本分离：TENANT plan 内无 RemediationBlock、ItemRowBlock.wearOrDamage 全为 null，**且全 plan 的 textRuns 文本不含 remediation/待处理/建议/紧急度 任一形态**（当前 TENANT 封面绘出「Pending remediation / 待处理：1」，命中本卡 forbid）；LANDLORD 侧含 RemediationBlock 与 Urgency"
  - "A13 privacy 照片：默认两版报告的图槽数为 0；显式 includePrivacyPhotos=true 时恰好出现 INLINE+APPENDIX 两次；**且「无检查项、照片全为 privacy」的房间不得使 compose 抛异常**——当前抛 IllegalArgumentException，等于只有把租客私照包含进来才生成得出报告，与隐私默认相反"
  - "A14 投影校验拒绝集（每条一枚负例，各断言该守卫**自己的**消息文本）：空白 room id、空白 item id、重复 photo id、重复 photo reference、roomPhotos 内 isRoomLevel=false、item photos 内 isRoomLevel=true、item/photo 多重集与 canonical 不符、statusDefinitions 与模板域不符、item status 越域、remediation 指向未知 item、**非正的 exifTimeMs**（渲染取的是它而非 capturedAt，当前只校验了不被渲染的那个字段）"
  - "A15 封面即答案：封面 textRuns 精确含带标签的不利/待处理数量与 ROUTINE·ISO-8601 时刻，照片 caption 精确含 引用号·来源·ISO-8601，全 plan 无裸 epoch 毫秒串；**且封面必须恰好占 1 页**——60 个房间时当前会裂成两个 CoverBlock，第二个仍带完整 address 与两个总数"
  - "A16 纯 :core 与字面量纪律：report 包源码不含任何 android./androidx. import 与位图解码调用（对 allow_paths 下的 .kt 逐文件扫描断言）；且几何/长度/哈希断言全部用字面量，ReportComposer 的伴生常量维持 private"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.report.*"
dod_exit: 0
dod_assert: 黄金布局树测试绿（固定巡检 fixture → 固定 DocumentPlan：页数/块序列/图槽位）；80 照 fixture 分页无溢出/无孤行；房东版含建议插槽+紧急度、房客版仅客观节；双语行配对（en 行+zh 行成对不拆页）；页脚含 data_hash 与免责声明槽
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T3-REPORT-COMPOSER

## 产出
`core/report`：巡检快照 → `DocumentPlan`（页列表；每页 = 定位块：文本行/图槽/表格行/页眉页脚）的纯函数布局引擎 + 黄金测试。**渲染器只照 plan 画，不再做任何布局判断。**

## 上下文包（执行模型必读）
- **DocumentPlan 模型**（Codex 方案采纳）：Page{ blocks: [TextRun(样式/双语对)、ImageSlot(photoId/目标 mm 尺寸/inline|appendix)、TableRow、HeaderFooter] }；单位用 mm（A4 210×297，边距 15mm），渲染器换算像素。
- **文本量宽注入**：composer 不依赖平台字体——`TextMeasurer` 接口注入（JVM 测试给等宽假 measurer；渲染器给真 Paint 量宽）。CJK 字形由渲染器捆 DroidSansFallback 保证（ADR-0003），composer 只管行高/换行预算。
- **报告结构**（需求 §8 + synthesis #13「封面即答案」）：封面（物业/类型/日期/tenancy + **不利发现卷积**：房间×状态计数 + 待处理条目数——Inventory Hive 把答案放封面是全场最佳报告设计）→ **评级词表页**（当前模板类型状态枚举逐条双语释义——Chapps 信任装置，[adopt]）→ 摘要页（不利发现清单，回链房间）→ 逐房间（按 room_instance，display_label 双语）：房间标题 + 全景缩略图 + 项目表（状态/备注/缩略图内联 ~40mm）→ 附录大图（每页 2 张 ~120mm，编号回链条目——Tribunal 要求纸质两副本，附录大图保打印证据可读）→ 尾页：Supplement 列表（若有）+ 免责声明（固定双语文案槽）+ 页脚每页含 data_hash 短形 + 页码。
- **每照出处页脚**（Inventory Hive 证据设计，[adopt]）：图槽下一行小字 = source（CAMERA/IMPORTED）+ 拍摄时间（EXIF 或采集时刻）+ 引用号（房间.项.序）。
- **privacy_flag 照片默认排除于两版报告**（可在生成参数显式包含）；房客版另按 Audience 硬排 remediation 类型块（既有约束）。
- 官方表兼容：房客版末尾保留「租客同意/签名」空白栏（官方 MB_TEN0004 双列勾选形态的纸面延续；app 内不做签名流——硬边界无账号）。
- **双版本分离在类型层**：`compose(snapshot, Audience.LANDLORD|TENANT)`——LANDLORD 含 remediation 插槽（T7 注入前渲染为空节不渲染）与紧急度分级；TENANT 硬性排除这些字段（测试断言 TENANT plan 里无该类型块）。
- 双语配对：固定文案（模板项/标题/免责声明）en+zh 成对块、分页时不拆对；自由文本原语言 + 标注（需求 §8）。
- 分页算法：贪心装页 + 「房间标题与首行不分离」「图槽不切割」两条约束；80 照 fixture 断言页数上界与无溢出。
- 输入 = `:core` 快照（T1-CANON 的 InspectionSnapshot 扩展只读视图），photoId 引用不触文件。

## 验收 / 执行建议
dod 见 front-matter。首选 Opus 5 · max（全项目最难纯逻辑卡）；备选 Sonnet 5 max。难度 H+。
