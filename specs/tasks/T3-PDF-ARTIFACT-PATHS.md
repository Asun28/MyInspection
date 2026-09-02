---
id: T3-PDF-ARTIFACT-PATHS
title: Report artifact path derivation and anchored shape predicate
depends_on: [T3-PDF-RENDERER]
parallelizable_with: [T3-REPORT-HTML-RENDERER]
status: todo
branch: T3-PDF-ARTIFACT-PATHS
worktree: C:\wt\T3-PDF-ARTIFACT-PATHS
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/pdf/
  - android/core/src/test/kotlin/nz/myinspection/core/report/pdf/
forbid:
  - 手拼路径字符串（全仓禁，见 CLAUDE.md 关键不变量；派生点唯一）
  - 绝对路径或注入存储根（只产相对路径，根由 :app 注入，同 core/media/MediaPaths）
non_goals:
  - 落盘、原子发布、重开逐字节核验与 receipt（T3-REPORT-EXPORT-CORE）
  - HTML 产物命名（该格式质量恒为 NONE，归 T3-REPORT-HTML-RENDERER / EXPORT-CORE）
  - 渲染程序、几何、采样与逐页内存上界（T3-PDF-RENDERER 已合并）
plan_ref: context/DESIGN.md#backup-report-health-and-compliance-component-matrix
acceptance:
  - "A1 a derived path names property, inspection, audience and quality, and the eight audience-quality combinations of one inspection are eight distinct paths"
  - "A2 derivation and recognition share one anchored pattern whose audience and quality tokens are built from the same enums the derivation writes"
  - "A3 a segment that could escape or reshape the path is refused, and a foreign, malformed, mis-extensioned or trailing-newline path is not recognised"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.*"
dod_exit: 0
dod_assert: 派生路径形如 reports/{propertyId}/{inspectionId}-{audience}-{quality}.pdf；2 受众 × 4 档 = 8 条互不相同；shape 判定接受全部派生路径、拒绝 photos/ 命名空间、错扩展名、缺档位、未知档位/受众、多层子目录、空段、`..` 段与尾随换行；空白/含分隔符/`.`/`..` 的入参抛 IllegalArgumentException；report 包既有测试保持绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4），每条 acceptance 至少一枚具名单点变异被击杀
doc_sync: TASK-BOARD 备注（R5）
---

# T3-PDF-ARTIFACT-PATHS

## 产出
`core/report/pdf/PdfArtifactPaths`：导出报告在 app 私有存储根下的**相对路径**派生点，加上与派生共用同一真相源的锚定形状判定。

## 拆分依据（2026-09-02 用户裁定）
从 `T3-PDF-RENDERER` 拆出。该卡 R3 首轮三条 finding 修完后 diff 达 1139 行 / 60679 字符，同时越 1000 行与
60000 字符两道硬闸；可砍注释约 85 行，补不上 146 行的缺口，且变异收据必须留在 diff 内（L227）。路径命名与
绘制操作本就是两件事，故整段拆出。

**实现草稿在 `_local/T3-PDF-ARTIFACT-PATHS-draft/`（gitignored，不入库）**：那份代码在原卡里已随 R3 首轮
finding #7 落地并跑绿，可作参考，但本卡仍按 R2 走 TDD——先写红、再实现，别直接拷进去当成已验证。

## 上下文包（执行模型必读）
- 形态：`reports/{propertyId}/{inspectionId}-{audience}-{quality}.pdf`。**只产相对路径**，根由 :app 注入。
  形态照抄 `core/media/MediaPaths`（同一模块已有的派生点，含 `requireSafeSegment` 防 `/`、`\`、`.`、`..`）。
- **文件名同时含 audience 与 quality**，于是一档已核验的产物不会被另一档覆盖，房东版也不会被房客版覆盖：
  一次巡检的 2 受众 × 4 档 = 8 份可并存的产物（需求 §8 与 `T3-REPORT-EXPORT-CORE` A3 都依赖这一点）。
- **派生与判定共用同一真相源**：正则里的受众/档位 token 由 `Audience.entries` 与 `PdfExportQuality.entries`
  拼出，新增一个档位不会只更新一半。判定用 `matchEntire`——注意 Java 正则的 `$` 会在**结尾换行之前**匹配，
  单靠 `$` 会放过 `"....pdf\n"`；`matchEntire` 因为末尾换行未被消费才拒掉它。该负例要留在测试里。
- 判定的用途在下游：`T3-REPORT-EXPORT-CORE` 要对来自数据库列的存量路径做发布/清理/计数，而该列没有任何
  约束保证它出自本派生点——所以先过形状闸再动文件，与 `MediaPaths.isPhotoRelPathShape` 同理。
- `PdfExportQuality` 已由 `T3-PDF-RENDERER` 合并（`storedValue` = low/medium/high/extra_high）；
  `Audience` 在 `core/report/ReportModel.kt`（LANDLORD/TENANT），token 取 `name.lowercase()`。

## 验收 / 执行建议
dod 见 front-matter。首选 Sonnet 5；备选 DeepSeek V4 Pro。难度 S。预算目标 ≤ 200 行。
