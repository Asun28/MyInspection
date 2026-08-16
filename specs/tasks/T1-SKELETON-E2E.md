---
id: T1-SKELETON-E2E
title: 一次性走通骨架：建巡检 → 加一项 → 拍一张 → 导出一份 PDF（真机可见，用完即弃）
depends_on: [T0-TOOLCHAIN]
parallelizable_with: [T1-SCHEMA-CORE, T0-LICENSE-SCANNER]   # 与 T1-SPIKE-PLATFORM 不可并行：它认领整个 android/app/src/main/，两卡都要往 AndroidManifest.xml 注册 Activity
status: merged
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
3. 拍一张照：`ActivityResultContracts.TakePicturePreview()`，拿系统相机返回的**缩略图 Bitmap**，只在内存里
4. 点导出：用 `android.graphics.pdf.PdfDocument` 画一页 —— 标题、地址、时间、那一行检查项及其状态、那张照片
5. 经 SAF `ActivityResultContracts.CreateDocument("application/pdf")` 让用户自选落点写出；成功/失败用 Toast 报

`MainActivity` 的根内容直接换成骨架界面（原内容是个空 `Box`，没有值得保留的东西）。

> **本节 2026-08-16 按 L212 更正**（R3 第 1 轮三条 #7 命中的都是这里）。原文要求「照片存进 app 私有目录」
> 「PDF 落私有目录 + `ACTION_VIEW` 打开」「MainActivity 只加入口按钮、原有内容不动」——**前两条在本卡
> `allow_paths` 内做不到**：让相机写入私有文件、或把私有 PDF 交给外部 viewer，都要 FileProvider，而
> FileProvider 要 `android/app/src/main/res/xml/file_paths.xml`，那在 `T2-CAPTURE-UI` 的 allow_paths 里。
> 卡的义务必须在它自己的 allow_paths 内可达（L212），故改成缩略图 + SAF：**更少代码、零权限、零
> FileProvider，且 SAF 本就是 ADR-0002 定的导出方式**。第三条（入口按钮）是给一个空屏加一层无意义的跳转，
> 一并去掉。全分辨率落盘是 `T2-PHOTO-PIPELINE` 的活，不是本卡的。

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

### 真机走查证据（2026-08-16，用户在自己的 Android 机上跑完）

装机 → 建巡检 → 加一项并置状态 → 拍一张 → SAF 导出 → 打开 PDF，**六步全通**；PDF 里检查项文字、状态、
照片三者都看得见（用户原话：「basic is working」）。走查同时提出三条产品反馈，**均不属本卡、已各有归属卡**，
按 rubric §0「不得给卡加范围」记为 follow-up、不在本卡处理：

| 反馈 | 归属卡 | 为何不在本卡修 |
|---|---|---|
| 照片画质差（`TakePicturePreview` 只给缩略图） | `T2-PHOTO-PIPELINE` | 全分辨率要 FileProvider → `res/xml/file_paths.xml`，在 T2-CAPTURE-UI 的 allow_paths 里 |
| UI/UX 粗糙 | `T2-CAPTURE-UI` | 本卡 `non_goals` 明列「任何 UI 打磨（能点就行，丑无所谓）」 |
| 拍照时背景没有历史照片（ghost overlay） | `T3-HISTORY-COMPARE`（可行性在 `T1-SPIKE-PLATFORM` 探测①） | 需历史数据 + overlay，两者都在下游 |

这三条正是本卡存在的理由：**10 分钟真机点完拿到具体产品反馈**，而按原依赖链要穿过 9 张卡才看得见第一份 PDF。

### 关于 RED-first

本卡是 spike 形态（同 `T1-SPIKE-PLATFORM`），产出是可抛弃代码 + 一次真机走查，没有值得长期保留的测试资产。
ship 时用 `-SkipRed` 显式跳过并记账；**不要**为了凑 RED 给一次性代码补写单元测试。

## 执行建议

Sonnet 5（标准 Compose + Android 框架调用，模式成熟）。难度 S–M，目标是**一个会话内做完**。
如果做到一半发现需要建表、需要抽象、需要新依赖 —— 那是走偏了，停下来砍需求，不要扩卡。
