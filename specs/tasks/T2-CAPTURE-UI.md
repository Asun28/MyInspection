---
id: T2-CAPTURE-UI
title: Field Ledger Compose 走查：房间导航 + 状态/证据 + 备注/拍照
depends_on: [T2-CAPTURE-CORE, T2-PHOTO-PIPELINE, T1-SPIKE-PLATFORM, T2-FIELD-LEDGER-THEME-R3-CLOSURE, T2-REPEATABLE-ROOM-RUNTIME]
parallelizable_with: [T3-REPORT-COMPOSER, T3-FINALIZE]
status: todo
branch: T2-CAPTURE-UI
worktree: C:\wt\T2-CAPTURE-UI
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/MainActivity.kt
  - android/app/src/main/kotlin/nz/myinspection/app/skeleton/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/
  - android/app/src/main/kotlin/nz/myinspection/app/media/camera/
  - android/app/src/main/res/
forbid:
  - 业务判断写进 Composable/ViewModel（判定一律调 :core；UI 只呈现与转发）
  - 下拉框选状态（需求 §5：大按钮）
non_goals:
  - ghost overlay 与历史条（T3-HISTORY-COMPARE 在本卡骨架上加）
  - 报告/导出入口（T3/T5 各卡）；平板/横屏适配（单手竖屏优先）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.capture.*"
dod_exit: 0
dod_assert: app 主题/语义单测 + assembleDebug + capture 核测试全绿（UI 未旁路核心规则）；真机走完一个两房间 fixture：Field Ledger 固定主题、状态大按钮、短语/听写、全景与不利发现拍照提示、杀进程恢复；记录附 PR
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T2-CAPTURE-UI

## 产出
可在真机走完整次巡检采集的 Compose 界面（物业列表→建巡检→房间走查→项目卡片→拍照/备注→完成度指示）。

## 上下文包（执行模型必读）
- **触摸优先铁律**（需求 §5）：大点击区（≥ 48dp、单手拇指可达）、不用下拉；键盘输入最小化——备注入口顺序：短语库（底部弹层按分类+按项推荐 `suggestFor(stableId,status)`，shortcut 展开）> 系统听写（离线可用性按 spike 报告；不可用则按钮隐藏；**音频随当前项绑定存储**——事后配对成本 45–60 分钟的反面教材见 synthesis #7）> 键盘兜底。
- **二值主评级 + 长按/二段细分**（synthesis #2：NZ 官方表与 myInspections 双双二值）：主控件两枚大按钮 [OK ✓]（落库 GOOD/NO_ISSUE）与 [需注意]（弹出 FAIR/POOR（或年检三档）+ 备注/拍照面板）；N_A 与「本物业不存在」（写 property_item_override，永久抑制）收进溢出菜单。存储枚举不变，纯 UI 层。
- **缺失计数橙条**（HappyCo 模式，synthesis #8）：顶部常驻「还差 N 项/N 照/N 备注」，点击滚动到下一缺失处；数据全来自 :core 完备性查询。**警示不硬拦**——硬闸只在 finalize。
- **房间级批量**：「余项全标 OK」带确认（HappyCo Rate All 先例；防 PI 式逐格 N/A 差评根因）。
- **照片隐私**（synthesis NZ 节）：拍照/导入后可点「含租客物品」标记（privacy_flag，报告可排除）；例行巡检照片数超 ~12 时一次性软提示（OPC 判例标尺）。
- 结构：Scaffold + 房间横向进度条（房间完成度来自 `:core` capture 的完备性查询）；项目卡片列表纵向滚动；房间级顶部「拍全景」卡片（1–2 张，ROOM_PANORAMA 规则）；项目级状态为不利发现时卡片内出现「必须拍照」标记（missingPhotos 驱动，UI 不自算规则）。
- 相机：CameraX 拍摄页（本卡纯拍摄，无 overlay；预留 overlay 插槽参数给 T3-HISTORY-COMPARE）；出片走 T2-PHOTO-PIPELINE 的保存管线（转正烘焙+哈希）。
- 草稿：每房间退出/切换即触发 `:core` 房间粒度保存；进程死亡恢复由核保证，UI 只需重进时读进度。
- 视觉基调：工具型、素色高对比、日光下可读；参考 `docs/research/` 调研的 [adopt] 项（若报告尚未就位，按上述铁律即可，不阻塞）。
- ViewModel 只做状态流转发（StateFlow 包 :core 查询），无业务分支——评审会按「UI 未旁路核心规则」查。
- **Field Ledger 生产化**：主题契约已由 `T2-FIELD-LEDGER-THEME-R3-CLOSURE` 完整闭合（PR #55 / master `cc4c67c`），Material 3 的核心与扩展颜色角色、Typography 和 Shapes 均有 Field Ledger 显式映射；本卡只负责在 app root 应用该固定主题（禁 capture 动态取壁纸色），不重新设计 token。删除一次性 `skeleton/`，`MainActivity` 只挂真实根界面。自定义 evidence rail 合并 TalkBack 语义，状态/照片/历史均有文本标签；“需注意”细分用可见 sheet，长按只可作快捷方式。

## 验收 / 执行建议
dod 见 front-matter；人工冒烟清单条目写进 PR 描述。
首选 Sonnet 5 · max；备选 Terra。难度 M。
