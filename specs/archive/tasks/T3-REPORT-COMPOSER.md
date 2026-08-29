---
id: T3-REPORT-COMPOSER
title: 纯 Kotlin 报告布局引擎：分页/缩略图排版/双语行配对/哈希页脚 + 黄金布局树（★冻结点级质量）
depends_on: [T1-CANON-HASH, T2-CAPTURE-CORE]
parallelizable_with: [T2-CAPTURE-UI, T3-FINALIZE]
status: merged
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
  # 作者声明的验收清单：以下是本卡认为「完成」所需的事实，每条应有可证伪测试。
  # **这是一份声明，不改变任何评审语义**——裁决仍完全按 docs/QUALITY-RUBRIC.md 现行 rubric 判，
  # 清单未列到的问题照常按现行 rubric 处理（含其现行的 [FOLLOW-UP] 适用条件）。
  # 「把清单当排他性判据、清单外一律 FOLLOW-UP」是上游提案 Asun28/claude-devops-scaffold#203
  # 的内容，**上游落地前本仓不采用**。
  # 页面常量：A4 210x297mm，页边距 15mm，正文顶 y=15mm，正文底 y=272mm，正文高 257mm，正文宽 180mm。
  # 几何断言一律用**字面量数字**，不得拿 ReportComposer 的伴生常量作期望值——那是拿生产值和自己比。
  # 每条都是**规范性**要求：无论当前实现是否已满足，都须有可证伪测试钉住。这些条目的由来（复核当日
  # 看到的具体缺陷形态）见正文「为什么这些条目存在（2026-08-23 复核）」——那是带日期的历史观察，
  # **不是对当前代码的断言**，条目本身不依赖它。
  - "A1 黄金布局树逐块钉死：固定 fixture（ReportTestFixtures.report()）产出**恰好 6 页**，逐页断言 (类型:标识@y:height) 全序列，且 plan.dataHash == ea9cd02e76bf79ac320df5795e51433b3200eb28900ab8837479a0c15eaf452d。页数写字面量 6，并在测试旁以注释写下它由哪些几何常量与夹具内容推算而来——分页逻辑改动后页数若变，须**重新推算**并核对，不得把新输出抄回断言"
  - "A2 项目内联缩略图几何用字面量断言：thumbnail.widthMm == 40、xMm == 140、首张 yMm == 0、图片框高 == 40、含 3 行 caption 的槽高 == 54；同列相邻两槽满足 yMm[n] == yMm[n-1] + heightMm[n-1] + 2（槽高随 caption 行数变，故不写 (n-1)*56 这种只在「每条 caption 都是 3 行」时才成立的闭式；另给一枚全 3 行 caption 的夹具，在那里断言第 n 张 yMm 恰好 == (n-1)*56）；**带缩略图的那一行**其 textRuns 宽全部 == 135 且 xMm+widthMm **< 140**（严格小于：等于 140 就贴上了图片列左缘）；**另断言一枚无照片的 ItemRowBlock 其 textRuns 宽全部 == 180**——文本列只在有图片列时收窄，把 135 写成所有 item row 的性质会让无照片那一行永远红。"
  - "A3 附录大图：每张图片框 180mm 宽、图片框高 == 108、含 1 行 caption 的槽高 == 114；**照片数 >= 1 时**，每个非末页的附录页恰好 2 张，末页在照片数为奇数时恰好 1 张、为偶数时 2 张，且每个附录页都带 photo-appendix 段标题——须同时有偶数与奇数两枚夹具（奇数那枚证明末页 1 张不是缺陷），并含一枚 3 行 caption、**至少 3 张照片**的对抗夹具证明密度不退化、第二页仍有标题；在最满的那页断言最后一个可绘制单元的**绝对** y+height <= 272。图片框高的取值依据是『段标题 + 2 张最大 caption 槽仍装得下 257mm 正文』这条**不变量**，它由 compose 入口的 measurer 预检守（把图片框高调到 110 即让该预检抛出并点名两种 style 与两个测得值）；**不要**声称「调大图片框高必须让 y+height 那条红」——108→109 时最后一格仍落在 271 <= 272，绝对边界断言照样绿。"
  - "A4 房间全景图槽：图片框 44mm 高、180mm 宽，含 1 行 caption 的槽高 == 50，xMm == 0（相对所在 PlacedBlock）"
  - "A5 图槽不可分割且不重复：对每个 purpose，**被 ReportOptions 纳入本次报告的**每个 photoId 在全文档内恰好出现一次（含 ItemRowBlock.thumbnails 内的图槽）；被排除的照片（如默认下的 privacy=true）出现次数为 0，由 A13 单独钉——本条只管「纳入的不重不漏」，不得写成「所有 photoId 一律出现一次」，那与 A13 直接矛盾。对抗夹具须同时给「单项 >= 5 张照片」与「单项 >= 5 张照片 + 2 万字符备注」两种输入，后者是分块与缩略图列交叉的那条路径"
  - "A6 图片框尺寸是计划的字段而非渲染器的推导：ImageSlotBlock 显式携带 imageHeightMm（与含 caption 的 heightMm 分开），断言 INLINE 缩略图 == 40、房间全景 == 44、附录 == 108；渲染器不得靠 textRuns[0].yMm 反推"
  - "A7 caption 有界：超长 reference 下 textRuns.size == 3、末行以 U+2026 结尾、且**末行字符数 <= 该列的量宽预算**——省略号必须是「替换末尾字符」而非「追加」，因为量宽器已把末行填满该列预算，追加会把一个字形推出列宽，而内联缩略图那一列的右缘就是正文右缘，溢出直接落进页边距；reference/source/capturedAt 三个结构字段仍保留完整原值"
  - "A8 页脚渲染文本：每页 footer.textRuns 拼接后含 dataHash 的**前 12 字符**（字面量 12，不引用生产常量），且任一 run 都不含 64 字符全摘要；FooterBlock.dataHash 仍是全摘要"
  - "A9 孤行控制两条，各带**能证伪的**边界夹具：(a) 剩余空间恰好容得下 12mm 房间标题、容不下「标题 + 首行」整组时，两者同页迁移——夹具须把上一页正文末端 y 落在 **242 < y <= 260** 这个窗口内（下界：y + 12 + 18 > 272 才会触发迁移，否则整组本来就装得下、闸门根本没被执行到；上界：y > 260 时标题自己就放不下，走的是另一条路径），并在测试里断言该夹具**实测的** y 落在该窗口内，夹具漂移即红；(b) 空房间的标题与其首张全景图同页。"
  - "A10 80 照 fixture：断言**恰好 64 页**（写字面量，不用 <= 上界——上界断言在缩略图全部堆进同一行时照样绿），测试旁以注释写下 64 由哪些几何常量推算而来；且每个可绘制单元——含 ItemRowBlock.thumbnails 内的每个图槽——的**绝对** y+height <= 272。页数若因分页改动而变，须重新推算，不得抄回当前输出"
  - "A11 双语成对不拆页（可证伪的形态是**拒绝**，不是『恰好落在 en/zh 之间的分页边界』）：composer 切块前要求 `paired.endY() + 2 <= maxHeightMm`，因此只要一个块 compose 成功，它的整对 en/zh 一定装得进第一块——不存在『分页边界恰好落在 en 行与 zh 行之间』的合法夹具（把 paired 特判换成空列表后贪心装填仍把整对放进第一块，断言不会变红）。故改为两条：(a) 构造一枚 en/zh 对本身高过正文的块（如超长 RemediationBlock），断言 compose 抛出、消息含 `an en/zh pair is never split across pages` 并点名该块——把 splitBlock 的 paired 特判换成空列表后该拒绝消失、本条必红；(b) 构造一枚 en/zh 对装得下、自由文本溢出的块，断言 en 与 zh 落在同一 chunk 且后续 chunk 内不再出现该对。该性质只对 splitBlock 的通用路径成立：ItemRowBlock 走 splitItemRow，其双语标签**刻意**在每个 chunk 重复（续行仍须说明这些图片属于哪一项），不得据此判它有缺陷。"
  - "A12 版本分离：TENANT plan 内无 RemediationBlock、无 Urgency 字段、ItemRowBlock.wearOrDamage 全为 null，且 CoverBlock.pendingItemCount 为 null、**该 CoverBlock 自己的 textRuns 拼接后不含 `Pending remediation` 与「待处理」**；判定限定在**封面块与块类型**上，不对全 plan 的 textRuns 做词表扫描——ItemRowBlock 备注是用户自由文本（短语库 T2-PHRASELIB 里就有「建议…」这类措辞），全局词表会在无缺陷时变红，而修法必然是弱化断言；LANDLORD 侧封面含该行，正文含 RemediationBlock 与 Urgency"
  - "A13 privacy 照片与空房，两向各一例且分属不同测试：(a) 一张 privacy=true 的照片在默认两版报告中的图槽数为 0，显式 includePrivacyPhotos=true 时该照片恰好出现 INLINE+APPENDIX 两次；(b)「无检查项、照片全为 privacy」的房间被**静默跳过**——既不抛异常也不产生孤标题（抛异常等于只有把租客私照包含进来才生成得出报告，与隐私默认相反）；(c)「无检查项且无任何照片」的房间必须 **fail-closed 抛出**、消息点名该 room id（T3-REPORT-COMPOSER-R3-CLOSURE「单一范围」第 4 条：空房无图明确拒绝、不产生孤标题）"
  - "A14 投影校验拒绝集（每条一枚负例，各断言该守卫**自己的**消息文本，且这些消息文本**两两不同**——共用一串时删掉其中一条会由兄弟分支补位而无人变红）：空白 room id、空白 item id、重复 photo id、重复 photo reference、roomPhotos 内 isRoomLevel=false、item photos 内 isRoomLevel=true、item/photo 多重集与 canonical 不符、statusDefinitions 与模板域不符、item status 越域、remediation 指向未知 item、**非正的 exifTimeMs**（caption 渲染取的是 exifTimeMs ?: capturedAt，只校验 capturedAt 等于校验了一个不被渲染的字段）"
  - "A15 封面即答案且恰好 1 页：封面 textRuns 精确含带标签的不利/待处理数量与 ROUTINE·ISO-8601 时刻，照片 caption 精确含 引用号·来源·ISO-8601；**「无裸 epoch 毫秒串」的判定限定在 CoverBlock 与 ImageSlotBlock 的 textRuns 上**（不对全 plan 扫 13 位数字串——ItemRowBlock 备注与 SupplementBlock 是用户自由文本，仪表读数/序列号会在无缺陷时命中，而修法必然是弱化断言；同 A12 的立场）：断言这两类块的 textRuns 拼接后不含 `report.canonical.scheduledAt` 与各 photo `capturedAt`/`exifTimeMs` 的十进制字面量。且给一枚 60 个房间的夹具，断言全文档 CoverBlock 恰好 1 个——房间×状态明细超出页面预算时以一行显式省略标记收尾（明细全量仍在 CoverBlock.roomStatusCounts、不利项全量在摘要页），绝不允许裂成第二个带完整 address 与总数的封面。"
  - "A16 字面量纪律：`nz.myinspection.core.report` 测试包下的**每一个** `.kt` 源文件（含夹具文件与本扫描自己的文件）内 `ReportComposer.` 出现次数为 0（拿生产常量当期望值 = 让常量和自己比，改成什么都仍绿）——扫描须先断言它枚举到的文件名集合恰好等于该目录当前的全部 `.kt`，否则一个静默找不到文件的扫描会永远绿，而排除清单正是它要堵的漏洞；几何/长度/哈希断言全部写字面量；配套把 `ReportComposer` 的伴生对象声明为 `private`——收不成即说明仍有测试在引用它。**纯 :core 由构建保证**（`:core` 用 kotlin.jvm 插件、classpath 上没有 Android），**不**再写 `android.` / `androidx.` 源码文本扫描：名字黑名单挡不住全限定名与反射，且严格弱于已有的编译期保证。"
  - "A17 尾页与页脚的固定文案槽（`dod_assert` 点名「页脚含 data_hash 与免责声明槽」，且 CLAUDE.md 硬边界写死「报告必带免责声明」）：每页 footer 的唯一 TextRun 文本精确等于「dataHash 前 12 字符」+ ` · ` + 页码 + `/` + 总页数——对 A1 的固定 fixture，首页那串以字面量 `ea9cd02e76bf · 1/` 开头，首末两页各断言一个**写死的完整字符串**（总页数取 A1 钉死的字面量 **6**，不从 plan 现算——现算会让分页缺陷同时改掉断言与被断言的值）；FooterBlock.pageNumber / totalPages 与之一致；尾页恰好含 1 个 DisclaimerBlock，其 textRuns 为 en/zh 成对且文本精确等于 REPORT_DISCLAIMER 的两支；带 supplement 的夹具尾页含与 report.supplements 等长的 SupplementBlock 序列、reference 序列精确相等；TENANT plan 尾页恰好含 1 个 TenantAgreementBlock（官方表的租客同意/签名空白栏），LANDLORD 侧恰好 0 个。删掉 disclaimerBlock() 调用一句后本条必红——A1 的黄金序列只钉「当前实现输出了什么」，若免责声明从未实现它照样全绿，证明不了尾页**必须**有"
  - "A18 摘要页回链：adverseItems 的每一项在 SummaryItemBlock 里恰好出现一次，(roomId · itemId · status) 序列精确等于按房间序/项序展开的期望列表，且每个 SummaryItemBlock.itemId 都能在同一 plan 内找到同 id 的 ItemRowBlock"
  - "A19 变异证明：A1–A18 中**每条有可执行守卫的条目**各配一枚最小变异（生产代码的单句删除/反转；A16 的守卫只能由『在某个测试源里插入一处 `ReportComposer.` 引用』杀死，允许以插入型变异计），跑一遍并把「变异 → 哪一条断言变红 → 失败文本」的对照表落进 **diff 本身**——写进被改测试类的 KDoc 或同包一份新的证据注释块，**不要只写 PR body**：`review.ps1` 只把 git diff 喂给评审者，PR 描述从不进入其上下文（L227），只写 PR body 等同没写。任一条守卫找不到能杀死它的变异，即视为该条未完成。判据按 L165 的分类器：**非零退出且命中指定断言文本**才算击杀，仅凭非零不算。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.*"
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

## 为什么这些条目存在（2026-08-23 复核）

下面是 2026-08-23 复核本卡分支（PR #39）全量 diff 时看到的**具体缺陷形态**，也是 acceptance 各条的由来。
**这是带日期的历史观察，不是对当前代码的断言**——分支此后仍在演进，其中若干已被后续提交修掉。卡片的寿命
长于分支：把「当前如何」写进条目本身，合并后就会变成对着能用的代码断言它坏掉，读者也无从判断该条是否已满足。
故条目一律**规范性**书写，是否已满足与它成不成立无关；下面这份记录只回答「当初为什么要写这一条」。

- **A3**：附录页会退化成每页 1 张，且第二页丢掉 photo-appendix 段标题。
- **A5**：「单项 >= 5 张照片 + 2 万字符备注」的输入会把同一张照片发出 3 次。
- **A7**：caption 末行是**追加**省略号而非替换，末行比列宽多一个字形，内联缩略图那一列会画进页边距。
- **A9**：孤行夹具把上一页填到 y=263，标题自己就放不下，(a) 那道闸门根本没被执行到。
- **A10**：80 个缩略图全部塞进同一个 18mm 行、超出页底 4233mm，而测试仍绿（它只断言了页数上界）。
- **A12**：TENANT 封面绘出「Pending remediation / 待处理：1」，命中本卡 `forbid` 的「房客版包含 remediation」。
- **A13**：「无检查项、照片全为 privacy」的房间使 compose 抛 IllegalArgumentException——等于只有把租客私照
  包含进来才生成得出报告，与隐私默认相反；而真正该 fail-closed 的「无项且无照片」空房当时无人钉。
- **A14**：非正的 exifTimeMs 无人校验——caption 渲染取的是 `exifTimeMs ?: capturedAt`，校验却只覆盖了
  不被渲染的 capturedAt。
- **A15**：60 个房间时封面裂成两个 CoverBlock，第二个仍带完整 address 与两个总数。
- **A16**：三个测试类共 5 处拿 `ReportComposer` 的伴生常量当期望值（LayoutContractTest:29,30,32,36,57），
  等于让生产值和自己比。
- **A17**：`dod_assert` 与 CLAUDE.md 硬边界都要求免责声明，但清单里只有短哈希一条覆盖页脚，
  免责声明、页码、supplement 列表、租客签名栏、摘要页五项在实现里都有、在验收面上却无人认领——
  按 `hygiene` 的 mutation-survivor 剪枝规则，没有验收条目认领的测试正是第一批被剪的。

**几何字面量的时效**：A2 / A3 / A4 / A6 里的数字取自复核当日的实现常量。**任何一次改动图片框高，都必须在
同一个 PR 里同改本清单与黄金树**——清单里的数字一旦落后于实现，实现者为了让红测试变绿会把正确的实现改回去，
那正是「验收条目反过来制造缺陷」的路径。

## 验收 / 执行建议
dod 见 front-matter。首选 Opus 5 · max（全项目最难纯逻辑卡）；备选 Sonnet 5 max。难度 H+。
