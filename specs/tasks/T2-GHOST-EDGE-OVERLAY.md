---
id: T2-GHOST-EDGE-OVERLAY
title: Ghost 叠图的边缘描边层（纯 JVM 抽取 + 真机三style目检）
depends_on: [T1-SPIKE-PLATFORM]
parallelizable_with: [T2-ROUTINE-CONTEXT-V2]
status: todo
branch: T2-GHOST-EDGE-OVERLAY
worktree: C:\wt\T2-GHOST-EDGE-OVERLAY
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/ghost/
  - android/core/src/test/kotlin/nz/myinspection/core/ghost/
  - android/app/src/debug/
forbid:
  - 在 :core 里碰 android.graphics.Bitmap 或任何 Android 类型（:core 是纯 JVM；像素以 IntArray 进出）
  - 把描边层接进 main/release source set 或生产采集流程（呈现归 T3-HISTORY-COMPARE）
  - 硬编码颜色（颜色由调用方注入；取值归 context/DESIGN.md 与其承接卡）
non_goals:
  - 真正的矢量化虚线笔画（需把边缘连成路径再描边，是另一量级的活；本卡的 dash 是屏幕空间条纹掩码）
  - 相机对位、坐标变换与 EXIF 方向（T1-SPIKE-PLATFORM 已判成立，不重做）
  - 历史条 / 双轨基线 / 生产叠图 UI（T3-HISTORY-COMPARE）
  - 性能优化与 GPU/RenderScript 路线（先证明形状与可读性，快慢按实测再议）
requirements:
  - "R1 当把历史照作为对位参考叠在取景画面上时，应能改用只画轮廓的描边层，使实景不被整片半透明照片遮挡。"
acceptance:
  - "A1 抽取是确定性的纯函数：输出长度等于输入长度，同一输入两次调用逐元素相等，且一枚固定黄金夹具产出逐元素等于写死的期望向量"
  - "A2 覆盖率受控：纯色图产出零个描边像素；单一高对比阶跃图的描边像素全部落在阶跃两侧的邻域内；请求覆盖率越低，产出的描边像素数不增"
  - "A3 边框像素永不成为描边像素（1 px 边框无完整 3x3 邻域），且尺寸不足 3x3、像素数与宽高不符、覆盖率越界的入参一律拒绝而非静默产出"
  - "A4 dash 变体是 solid 变体的**真子集**，且条纹周期可由入参改变并被断言看见"
  - "A5 [人工设计评审] 真机上比较 photo / solid edges / dashed edges 三种 style，记录哪一种最便于把机位移回原处；本条明确不是自动验收"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.ghost.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }
dod_exit: 0
dod_assert: ghost 包测试全绿且真实执行（--rerun-tasks --no-build-cache，非 UP-TO-DATE 假绿）；debug APK 编译通过，证明探针确实消费了 :core 的抽取器；A1–A4 各至少一枚具名单点变异被击杀；A5 的真机三style对比结论写进卡内「真机目检」节并标注为人工评审
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）；每条自动 acceptance 至少一枚具名单点变异被击杀，变异收据钉生产文件 SHA-256（L270）
doc_sync: 卡片 status -> merged；TASK-BOARD 备注；T3-HISTORY-COMPARE 行注明描边层可复用及其真机结论（R5）
---

# T2-GHOST-EDGE-OVERLAY

## 产出
`core/ghost/`：把一张历史照的像素抽成**描边层**的纯 JVM 确定性函数，加上一个消费它的 debug 探针，
用于在真机上判断「绿色轮廓」是否比现有的 alpha 0.30 整片半透明照片更便于对位。

## 开卡依据（2026-09-09 用户提出并裁定卡形）
`T1-SPIKE-PLATFORM` 已在真机判定 ghost overlay **成立**（master `e8c2359a`），采用的是 alpha 0.30 的整片照片。
用户当场提出：能否改画成绿色（虚）线轮廓，只勾物体边缘。这是**呈现方式**的改进，不是可行性问题——
既有方案已经够用，本卡属主动改良，不是补洞。

卡形经用户裁定取「**核心算法 + debug 探针**」而非纯 debug spike：抽取本身是纯计算，天然可单测可变异，
放进 `:core` 就能过本仓正常闸；而 `T1-SPIKE-PLATFORM` 因为一行测试源都没有，R4 零候选、DoD 只能靠
「构建绿 + 报告存在」，验收实质压在人眼上——那个缺口不该再复制一次。

## 上下文包（执行模型必读）
- **边界**：`:core` 是纯 JVM（`T0-TOOLCHAIN` 定），**不得** import `android.graphics.*`。像素以 `IntArray`
  （ARGB_8888 打包）进出，宽高另传；Bitmap 与 IntArray 的互转留在 `:app` 探针里。
- **算法**：亮度（整数权重）→ Sobel → `|gx| + |gy|` 幅值 → 按**目标覆盖率**取阈值（直方图累加，不是拍脑袋的固定阈值，
  否则暗场景全黑、亮场景糊成一片）→ 超阈值处写入注入的颜色，其余写 0（全透明）。
- **dash**：屏幕空间条纹掩码（形如按 `(x + y) / period` 的奇偶取舍），故**必然**是 solid 的子集——A4 就是钉这条。
  真矢量虚线在 non_goals 里，别顺手做。
- **确定性**：同输入同输出，逐元素可断言；黄金夹具的期望向量**写字面量**，不许由被测函数回拼（L165）。
- **拒绝面**：尺寸 < 3x3、`pixels.size != width * height`、覆盖率不在 (0,1] —— 一律抛，不静默返回空层。
- **探针**：在既有 `CameraGhostProbe` 上加一个 style 循环（photo / solid / dashed），三者共用同一坐标变换，
  只换绘制的那一层；真机目检结论回填本卡「真机目检」节。设备已在手（Galaxy A34 5G，adb 可用）。

## 真机目检
（A5 待填：三种 style 的对位可用性对比结论 + 截图，标注为人工设计评审。）

## 验收
见 dod_command / dod_assert。

## 执行建议（TASK-BOARD）
首选 Sonnet 5 · max（规格清晰的纯函数 + 测试）；备选 DeepSeek V4 Pro。难度 M。A5 需用户真机约 5 分钟。
