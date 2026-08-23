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
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.report.*"
dod_exit: 0
dod_assert: 黄金布局树测试绿（固定巡检 fixture → 固定 DocumentPlan：页数/块序列/图槽位）；80 照 fixture 分页无溢出/无孤行；房东版含建议插槽+紧急度、房客版仅客观节；双语行配对（en 行+zh 行成对不拆页）；页脚含 data_hash 与免责声明槽
acceptance:
  # 作者声明的验收清单：以下是本卡认为「完成」所需的事实，每条应有可证伪测试。
  # **这是一份声明，不改变任何评审语义**——裁决仍完全按 docs/QUALITY-RUBRIC.md 现行 rubric 判，
  # 清单未列到的问题照常按现行 rubric 处理（含其现行的 [FOLLOW-UP] 适用条件）。
  # 「把清单当排他性判据、清单外一律 FOLLOW-UP」是上游提案 Asun28/claude-devops-scaffold#203
  # 的内容，**上游落地前本仓不采用**。
  - "A1 黄金布局树：固定巡检 fixture → 固定 DocumentPlan，逐字段断言页数 + 块序列 + 每个图槽的 x/y/width/height（mm）"
  - "A2 项目内联缩略图几何：落在项目表行内、目标宽约 40mm，断言坐标而非仅断言块序"
  - "A3 附录大图：每页 2 张、目标宽约 120mm，带回链编号（房间.项.序）"
  - "A4 ImageSlotBlock 永不可分割：INLINE 与 APPENDIX 两种槽位各一条「超长 caption」对抗用例，证明分页不会把一个图槽拆成两块（同一 photoId 绝不出现两次）"
  - "A5 caption 有界：合成期按固定行数封顶或截断，使 A4 的不变量不依赖输入长度"
  - "A6 页脚渲染文本含 data_hash **短形**（取前 12 位），断言渲染文本原文而非仅断言元数据字段"
  - "A7 页脚每页含页码 + 免责声明槽（固定双语文案）"
  - "A8 孤行控制（两条）：非空房间的标题与首行不分离；空房间的标题与其首张全景图不分离——或显式拒绝空房间"
  - "A9 80 照 fixture 分页无溢出，且断言页数上界"
  - "A10 双语配对：固定文案 en 行 + zh 行成对，分页不拆对（含跨页边界用例）"
  - "A11 版本分离：TENANT plan 内不存在 remediation 类型块与紧急度字段（类型层断言）；LANDLORD 含该插槽"
  - "A12 privacy_flag 照片默认不进两版报告；显式传参时才包含（两条）"
  - "A13 投影校验拒绝集（各一条负例）：空白 room id、空白 item id、重复 photo 引用、roomPhotos 里 isRoomLevel=false、item photos 里 isRoomLevel=true"
  - "A14 封面「不利发现卷积」：房间×状态计数与待处理条目数以实测、带标签的文本 run 出现在封面（非仅元数据）"
  - "A15 时间一律渲染为确定性人类可读串（不得出现裸 epoch 毫秒）：scheduledAt 与照片 capturedAt 各一条断言渲染原文"
  - "A16 composer 不含任何 android import / 位图解码（静态断言或依赖检查）"
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
