---
id: T1-SKELETON-E2E
title: 一次性走通骨架：建巡检 → 加一项 → 拍一张 → 导出一份 PDF（真机可见，用完即弃）
depends_on: [T0-TOOLCHAIN]
parallelizable_with: [T1-SCHEMA-CORE, T0-LICENSE-SCANNER]   # 与 T1-SPIKE-PLATFORM 不可并行：它认领整个 android/app/src/main/，两卡都要往 AndroidManifest.xml 注册 Activity
status: todo
branch: T1-SKELETON-E2E
worktree: C:\wt\T1-SKELETON-E2E
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/skeleton/
  - android/app/src/main/kotlin/nz/myinspection/app/MainActivity.kt
  - android/app/src/main/AndroidManifest.xml
forbid:
  - 任何出站网络（LLM / 遥测 / 云同步一律不碰）
  - 新增任何运行时依赖（只用 SDK 自带：CameraX 已 pin、PdfDocument、SAF）
  - 改动 :core 的任何文件（T1-SCHEMA-CORE 正在该模块内作业，零重叠是本卡前提）
  - 把骨架代码搬进 :core、或让任何后续卡 import 它
non_goals:
  - 正确的数据模型（不建表、不碰 SQLDelight、不用 UUIDv7；进程内内存对象即可）
  - 合规校验、48h 通知、排程
  - 双语 / 短语库 / 听写 / ghost overlay / 历史对比
  - canonical 哈希、finalize 只读、补充说明链
  - 加密备份、租客数据保留期
  - 房东版/房客版双版本（本卡只出**一份** PDF）
  - 任何 UI 打磨（能点就行，丑无所谓）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: assembleDebug 退出 0，产出 app-debug.apk；随后**真机走查一遍**并把结果贴进 PR / 卡片记录：装机 → 建一次巡检 → 加一个检查项并置状态 → 拍一张照 → 点导出 → 在设备上打开那份 PDF，PDF 里能看见该检查项的状态文字与那张照片。六步任一步走不通即本卡未完成。
review_gate: codex {verdict:pass}
hygiene: 本卡是一次性骨架，不做 mutation 剪枝（无长期测试资产）
doc_sync: CLAUDE.md 当前阶段 + docs/TASK-BOARD.md 备注（R5）
---

# T1-SKELETON-E2E

## 为什么要这张卡

板子上 27 张卡里，第一份 PDF 埋在 **9 层依赖**底下：

```
T1-SCHEMA-CORE → T1-TEMPLATE-ENGINE → T2-CAPTURE-CORE ─┐
T1-SCHEMA-CORE → T2-PHOTO-PIPELINE ────────────────────┤→ T2-CAPTURE-UI
T1-SCHEMA-CORE → T1-CANON-HASH → T3-FINALIZE → T3-REPORT-COMPOSER → T3-PDF-RENDERER
```

在穿过这 9 张卡之前，**没有任何一个点能在手机上看见东西**。这是「一天零交付」的结构性原因：
路径太长、中途无反馈，所有精力都流向了路径本身（脚手架），而不是路径尽头的东西。

本卡是 walking skeleton：用最笨的实现把整条路打通一次，**今天就能在手机上看见一份 PDF**，
同时把三个最大的未知在 9 张卡之前验掉 —— 相机取图能不能落盘、PdfDocument 能不能把图排进去、
导出的文件用户能不能真的打开。

## 产出

一个 `:app` 内自包含的 `skeleton` 包，跑起来是一条能点完的路：

1. 建一次巡检（内存对象，一个地址字符串 + 时间戳，**不落库**）
2. 加一个检查项：一行文字 + 三选一状态（好 / 一般 / 差）
3. 拍一张照（CameraX 或系统相机 intent，二选一，怎么简单怎么来），存进 app 私有目录
4. 点导出：用 `android.graphics.pdf.PdfDocument` 画一页 —— 标题、地址、时间、那一行检查项及其状态、那张照片
5. 把 PDF 落到 app 私有目录并用系统 viewer 打开（`ACTION_VIEW` + FileProvider）

`MainActivity` 只加一个入口按钮跳进去，原有内容不动。

## 禁止

见 front-matter `forbid`。补充两条口径：

- **这是可抛弃代码。** 骨架包不是任何后续卡的依赖，后续卡不得 import 它；真实实现落地后本包整体删除
  （删除动作挂在 `T2-CAPTURE-UI` 的 doc_sync 里）。**不要**为它写抽象层、接口、DI 或配置项。
- **不碰冻结点。** 本卡不产生任何需要版本评审的契约：不建表、不定 JSON schema、不算哈希。

## 非目标（本卡刻意不做的能力）

见 front-matter `non_goals`。一句话概括：**除了「能走到头」，什么都不做。**
评审若就数据模型正确性、合规、双语、哈希、双版本报告开条件 —— 那些是下游卡的产出，本卡按 non_goals 拒绝，
按 R3 收窄口径**开新卡、不 block**。

## 验收（DoD = 命令 + 退出码 + 断言）

```powershell
cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
```

- 期望退出码：**0**
- 机检断言：`assembleDebug` 绿，产出 `android/app/build/outputs/apk/debug/app-debug.apk`
- 人工断言（真机，约 5 分钟，结果贴 PR / 卡片记录）：装机 → 建巡检 → 加一项并置状态 → 拍一张 →
  导出 → 打开 PDF，**PDF 里同时看得见那行检查项状态和那张照片**。

### 关于 RED-first

本卡是 spike 形态（同 `T1-SPIKE-PLATFORM`），产出是可抛弃代码 + 一次真机走查，没有值得长期保留的测试资产。
ship 时用 `-SkipRed` 显式跳过并记账；**不要**为了凑 RED 给一次性代码补写单元测试。

## 执行建议

Sonnet 5（标准 Compose + Android 框架调用，模式成熟）。难度 S–M，目标是**一个会话内做完**。
如果做到一半发现需要建表、需要抽象、需要新依赖 —— 那是走偏了，停下来砍需求，不要扩卡。
