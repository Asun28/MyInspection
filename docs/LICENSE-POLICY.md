# 依赖许可证政策（License Policy）

> 若你的项目是**商用产品**，本文件是依赖准入的硬规则。每新增/升级一个依赖（代码、模型权重、字体、素材、训练数据条款）都必须照此核验。
> ⚖️ 本文件是工程约束，非法律意见；商用发布前由法务终审。
> （若你的项目是开源/内部工具，可放宽 §1，但保留「无 non-commercial、来源洁净、许可明确」三条。）

## 1. 绝对禁止（任一即拒）
- **Copyleft 传染性许可**：GPL-2.0 / GPL-3.0 / AGPL-3.0 / LGPL（静态链接情形）/ SSPL / EUPL 等——
  会要求本项目源码以同等条款开源，与商用专有冲突。
- **非商用条款**：任何 "non-commercial" / "research only" / CC-BY-**NC** / "仅学术研究" 的代码、模型权重、数据集或素材。
- **缺失许可**：无明确 LICENSE 文件、许可不明、或"保留所有权利"但未授权使用的第三方代码。
- **来源不洁**：疑似抄袭、镜像他人专有源码、或训练数据明显侵权的模型。

### 1.1 `Distributes` 旗：不分发的项目可把**纯 GPL** 从致命降为黄牌（⚖️ 非法律意见）
不同 copyleft/限制的**义务触发点**不同——这决定「不分发」能否豁免：

| 许可 | 触发点 | 项目**从不分发**软件时 |
|---|---|---|
| **纯 GPL**（GPL-2.0/3.0、GNU General Public License） | **分发**二进制/源码 | 不触发 → 可降为**黄牌**（人工确认） |
| **AGPL / Affero** | **网络提供**（SaaS 后端即触发） | 仍触发 → **致命** |
| **SSPL** | **作为服务提供** | 仍触发 → **致命** |
| **EUPL** | 分发 **+ 向公众通信**（含网络） | 保守视为触发 → **致命** |
| **non-commercial / CC-BY-NC / research-only** | **用途**（商用即违反，与分发无关） | 仍违反 → **致命** |
| **LGPL** | （动态链接/进程外可接受） | 恒**黄牌**（不受本旗影响） |

- `scripts/_config.ps1` 的 `Distributes`（默认 `$true`=保守/fail-closed）设为 `$false` 后，`check-licenses.ps1` 只把**纯 GPL** 降为黄牌；上表其余各类**一律仍致命**。
- 仅当项目**确实从不分发软件**（纯内部工具 / 纯 SaaS 后端且不随产品交付二进制）才设 `$false`；随产品分发任何二进制/库、或**变 public（开源=分发源码）** 即属分发——变 public 前用 `check-licenses.ps1 -Strict` 复核（`-Strict` 把降级后的 GPL 黄牌重新升级为致命）。
- 本旗是**工程降噪**、非法律豁免；商用发布前仍由法务终审。

## 2. 允许（商用安全，优先选用）
- **宽松许可**：MIT / BSD-2 / BSD-3 / Apache-2.0 / ISC / Unlicense / 0BSD / Python-2.0 / MPL-2.0（文件级 copyleft，隔离使用可接受）。
- **LGPL**：仅当**动态链接**且不修改其源码、或经**进程外 CLI 子进程**调用时可接受（如 ffmpeg **LGPL 构建**）。
  ⚠️ **进程外/子进程边界只豁免 LGPL、不豁免 GPL**——GPL 触发点是**分发**，随商用产品分发 GPL 二进制即触发义务、与跨不跨进程无关。
- **OpenRAIL-M 等"负责任 AI"许可**：允许商用但带**基于用途的行为限制**且须向下游传递——**需逐案评估**并把限制写入用户协议；默认标黄，非自动通过。

## 3. 当前依赖核验（项目特定）

> 随开发把每个依赖登记到下表（脚本只覆盖 PyPI/npm，覆盖不到模型权重/数据/字体/素材/Gradle——这些**逐项**人工登记）。
> 本项目无 PyPI/npm 依赖（原生 Kotlin + Compose，ADR-0001）；下表登记 Gradle 生态。

| 依赖 | 许可 | 结论 |
|---|---|---|
| _(示例)_ FastAPI | MIT | ✅ |
| _(示例)_ uvicorn | BSD-3 | ✅ |
| … | … | … |

### 3.1 Android/Gradle 直接依赖（`android/gradle/libs.versions.toml`，已核验）

> `check-licenses.ps1` 只扫 PyPI/npm，覆盖不到 Gradle（见 §5.1）；本表是**人工核验**，对准每条坐标的**发布方主库
> LICENSE 文件 / Maven POM `<licenses>` 块**（非 README 措辞、非 `aar-metadata.properties`——后者记
> `minCompileSdk` 等构建要求，与许可无关，见 T0-TOOLCHAIN 第 4 轮评审纠正）。同一发布方（同一 monorepo/组织）
> 下的多个坐标共用同一份 LICENSE 依据，逐条列出坐标、依据只列一次代表性来源。

| Gradle 坐标（前缀） | 许可 | 结论 | 依据 |
|---|---|---|---|
| **androidx 三个不同 group**（同 monorepo，共享同一 LICENSE，逐条列出真实坐标防误读成同一 group 通配）：`androidx.compose:compose-bom`；`androidx.compose.ui:ui` / `androidx.compose.ui:ui-tooling` / `androidx.compose.ui:ui-tooling-preview`；`androidx.compose.material3:material3`；`androidx.activity:activity-compose`；`androidx.camera:camera-core` / `androidx.camera:camera-camera2` / `androidx.camera:camera-lifecycle` / `androidx.camera:camera-view`；`androidx.exifinterface:exifinterface`；`androidx.work:work-runtime-ktx` | Apache-2.0 | ✅ | androidx monorepo `LICENSE.txt`（`android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt`） |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | Apache-2.0 | ✅ | `github.com/Kotlin/kotlinx.serialization` `LICENSE.txt` |
| `app.cash.sqldelight:runtime` / `sqlite-driver` / `android-driver`，及同名 Gradle 插件 `app.cash.sqldelight` | Apache-2.0 | ✅ | `github.com/cashapp/sqldelight` `LICENSE.txt` |
| `org.jetbrains.kotlin:kotlin-test` / `kotlin-test-testng`，及 Kotlin Gradle **插件 ID**（`libs.versions.toml` 里 `kotlin-jvm`/`kotlin-android`/`kotlin-compose`/`kotlin-serialization` 只是目录别名，真实 id 分别是）`org.jetbrains.kotlin.jvm` / `org.jetbrains.kotlin.android` / `org.jetbrains.kotlin.plugin.compose` / `org.jetbrains.kotlin.plugin.serialization` | Apache-2.0 | ✅ | `github.com/JetBrains/kotlin` `license/LICENSE.txt` |
| `org.testng:testng`（经 `kotlin-test-testng` 引入，测试运行器；替代 EPL-1.0 的 JUnit，见 toml 内注释） | Apache-2.0 | ✅ | **实测解析版本** `testng:7.0.0`（`:core:dependencies --configuration testRuntimeClasspath` 输出所示，非 Maven Central 上的最新版——早前文档误引了 `7.5.1.pom`，R3 评审纠正）；`testng-7.0.0.pom` `<licenses>` 块 = `Apache Version 2.0, January 2004`；其 `junit:junit:4.12` 依赖标 `<optional>true</optional>`，Gradle 不解析进最终 classpath（实测同一条 `:core:dependencies` 输出零 junit 节点）——**版本号以实测解析结果为准，不是 POM 里写的最新版**，核验证据须对准"实际会被打进产物的那个版本"，同 `docs/LICENSE-POLICY.md` 附录 A 对 ffmpeg 的"对准确切二进制"要求同一纪律 |
| `com.android.tools.build:gradle`（AGP，`com.android.application`/`com.android.library` 插件） | Apache-2.0 | ✅ | `dl.google.com` 该坐标 POM `<licenses>` 块（`The Apache Software License, Version 2.0`） |
| Gradle 构建工具本体（非 Maven 坐标依赖，`gradle/wrapper/gradle-wrapper.properties` 钉版） | Apache-2.0 | ✅（构建期工具，未随产品分发） | gradle.org 许可声明；wrapper `distributionSha256Sum` 已由编排者独立核验匹配官方发布 |

### 3.2 Android/Gradle 传递依赖：约 220 个坐标未逐一核验（已知缺口，非合规声明）

- **现状**：上表只覆盖 `libs.versions.toml` 声明的**直接**坐标；`:core:dependencies` 实际解析出的**传递闭包
  约 220 个坐标**，当前**未逐一核验**其许可。这是真实缺口，不粉饰。
- **为什么不在本卡补齐**：手工审 220 个坐标的表格在下次依赖变动即过期；正确解法是自动化扫描器——已登记为
  **TD2**（见 `specs/tech-debt-tracker.md`），而非在骨架/闸门加固卡里手工堆表格造成假的"已核验"观感。
- **TD2 当前状态**：`carded`，指向 `specs/tasks/T0-LICENSE-SCANNER.md`——该卡负责把这约 220 个坐标从
  「覆盖缺口告警」升级为「逐坐标机检」（从 Gradle 真实解析结果取坐标，不是读 `libs.versions.toml` 字面量），
  还清前 `docs/RELEASE-CHECKLIST.md`「Gradle 传递依赖许可全量核验通过」保持发布阻断项。
- **风险绑定到触发点**：GPL 系 copyleft 的义务触发点是**分发**（见 §1.1）；本项目当前**无任何发布路径**
  （发布相关卡排在 T7 之后），故该缺口暂不构成当前的合规违规。但**发布前必须清零**——见
  `docs/RELEASE-CHECKLIST.md`「质量」节的阻断项。
- **不是自动豁免**：AGPL/SSPL/EUPL/非商用等触发点与分发无关的许可类别（§1.1 表）即便在传递依赖中出现，
  也不因"未发布"而被豁免——TD2 已 carded（`T0-LICENSE-SCANNER`），但扫描器**落地前**（该卡尚未开工），
  `android/` 子树的这一风险敞口持续存在，接入 CI 强制前只能作为已知缺口管理。

## 4. 模型权重 / 数据 / 素材核验（项目特定 · 待填）

> 代码与权重**双双**宽松（MIT/BSD/Apache-2.0）才算洁净；研究专用 / 非商用 / OpenRAIL 用途限制 / 自定义"社区许可" / NOASSERTION 一律按 §1 阻断。
> 常见陷阱：① 代码 MIT 但**权重 OpenRAIL-M/非商用**（以权重分发点的 model card 为准，README 措辞不算数）；
> ② 传递依赖藏 GPL（如某 G2P/VAD/JP 文本前端组件）——**静态扫描洁净 ≠ 运行洁净**，须真机 provision + smoke 实测（见 docs/lessons L17/L28）；
> ③ 无可行 CPU 路径的大模型——许可可能洁净但卡在架构边界（按你的 GPU 政策决定）。

| 模型/权重/素材 | 代码许可 | 权重/数据许可 | 结论 |
|---|---|---|---|
| _(待填)_ | | | |
| `docs/references/claude-*-llms.txt`（7 份，vendored 第三方**文档**提炼件） | n/a（非代码） | © Anthropic，公开文档、**无再分发授权** | ⚖️ **按最小引用保留，非再分发**：正文自行提炼（中文改写 + 本仓注解），仅保留**要照字面喂给模型才生效**的功能性提示语短片段并逐条标注出处；超出功能必要长度的整块示例改写为要点 + 链接（已执行：4.8 篇 AEFRM 整份 brief）。逐条基准见 `docs/references/README.md`「来源与引用基准」，每份文件头注含源 URL + © + 校核日期。**本判断是工程约束非法律意见**——若本仓或下游要**对外分发**含本目录的产物，须法务复核 |

> **为什么单列这一行**：本目录不是依赖、不是权重，是**第三方文档的提炼件**，`check-licenses.ps1` 的 PyPI/npm 扫描覆盖不到（§5.1 生态缺口的一个具体实例），故按 §4「逐项人工登记」在此登记。新增任何 vendored 文档类 reference 时**在此追加一行**。

**运行时机器闸（可选模式）**：若项目接入真实模型权重，建议落一个 `require_clean_weights(model_id)` 运行时闸——
未登记为商用洁净的 `model_id` 即 fail-closed 拒绝（默认拒绝），与授权闸并列为推理前置。

## 5. 核验流程（每次加依赖）
1. 跑 `pwsh -File scripts\check-licenses.ps1`（扫描后端 venv + 前端 node_modules 的许可，命中禁列即非零退出）。
2. 模型/权重/数据/字体/素材**逐项**记录到 §3/§4 表（脚本只覆盖 PyPI/npm）。
3. Codex 评审闸门会阻断疑似 copyleft/非商用片段。
4. 真实模型子环境跑 `pip-licenses` **全审**：GPL 硬禁（声明但未 import 的可卸）；LGPL 仅进程隔离/动态可留；UNKNOWN 元数据逐个核实际许可。

### 5.1 非 Python/npm 生态依赖清单覆盖缺口（advisory-only）
`scripts\check-licenses.ps1` 只**扫描**（自动识别许可证文本并分类）PyPI（`pyproject.toml`）与 npm
（`frontend/package.json`）两个生态；若仓库还存在其它生态的依赖清单（`go.mod`/`Cargo.toml`/`Gemfile`/
`composer.json`/`pubspec.yaml`/`pom.xml`/`build.gradle` 等），探针会把它登记为「覆盖缺口」——**这不代表你
必须用 Python**，只是提醒该生态没有对应的自动扫描器，需要人工或其原生工具核验，不能让「零覆盖」被误读成
「已核验合规」。

- 正常运行（无 `-Strict`）下，覆盖缺口只告警、不失败；`-Strict`（发布/变 public 前）才把它升级为致命，
  逼你在发布前处理掉。
- 各生态可用的**原生**许可扫描工具举例（按需选用，非强制）：Rust → `cargo-license`；Go → `go-licenses`；
  Ruby → `license_finder`；多语言通用 → `license_finder`（支持多生态）、`FOSSA`、`ScanCode Toolkit`。
- 核验完成后把结果按 §3/§4 表格式逐项登记；脚本本身**不会**自动清除该告警（它不感知人工核验结果，
  这是刻意的 fail-safe，避免「装了扫描工具但没人看结果」的假绿）。

## 6. 例外
任何 §1 例外必须：书面记录理由 + 法务签字 + 写入本表。无记录即视为违规。

## 7. 净室来源声明（clean-room provenance · 模板，按需填）
参考产品的**流程与功能不受版权约束**，但复制其**源代码、独特目录结构、独特实现**可能构成衍生作品并触发 §1 的 copyleft 传染。故本项目采用净室策略：**只参考公开产品的能力与流程描述，绝不阅读或复制其源代码**。

**硬承诺（四条，对外可引用）**
1. 本仓**全部代码为原创实现**；未从任何参考产品复制源代码、目录结构或独特实现。
2. **未引入任何 GPL / AGPL / SSPL 等 copyleft 代码**进入本项目可分发产物；第三方依赖仅用 MIT/BSD/Apache 等宽松许可。
3. LGPL 组件（若有，如 ffmpeg 官方 LGPL 构建）**仅经进程外 CLI 调用**，不静态链接，符合 `docs/LICENSE-POLICY.md`。
4. 非商用 / research-only 的代码·权重·数据·素材**一律不接入**。

**参考来源清单（仅流程/能力，未碰源码）— 待填**：公开的「〈某类产品〉」功能与交互流程（公开页面/演示级别）· 公开的技术**概念**（教科书/公开文档级别）· 开源**许可证原文**（用于合规判定，非功能借用）。以上均为公开、非源码层面的参照；任何具体实现均为本项目独立编写。新增来源时在此追加登记，保持可审计。

**审计闭环**：PR 走 codex 评审（`scripts/review.ps1`）对照改动是否含可疑外来源码片段 · `scripts/check-licenses.ps1` 扫 PyPI/npm 命中禁列即 fail-closed · 安全约定见 `docs/SECURITY.md`。上述第 3 条 LGPL 构建的逐步核验（含 `-buildconf` 比对与 SHA-256 校验）见**附录 A**。

---

## 附录 A：ffmpeg 商用合规（若项目分发 ffmpeg，常见坑 · 可选 · 人工核验）

> 仅当你项目随包/分发 ffmpeg（如经 `imageio-ffmpeg`）时相关；否则忽略本附录。无自动化探针，靠下方步骤人工核验。

- **随包 imageio-ffmpeg 二进制常是 GPL 构建**（`--enable-gpl --enable-libx264 --enable-libx265`），商用分发触发 copyleft。
- 解法：换**官方 LGPL 构建**（如 BtbN `*-lgpl-shared`），钉版本 + SHA-256 provision 到本地，设 `IMAGEIO_FFMPEG_EXE` 指向之；各入口脚本统一加载、子进程调用。
- **人工核验（对准确切二进制 · fail-closed）**：核验对象必须是**将随包分发的那一个**二进制——用完整路径调用
  （如 `& $env:IMAGEIO_FFMPEG_EXE -buildconf`，即 provision 时钉下的那份），**别**裸跑 `ffmpeg`（那核验的是 PATH 上
  碰巧存在的另一份，会误放行真正分发的 GPL 构建）；命中 `enable-gpl`/`enable-nonfree` 即不合规——换官方 LGPL 构建重来；
  `-buildconf` 无输出或二进制路径缺失**同判不合规**（fail-closed，不许当通过）；provision 时记录二进制 SHA-256，
  每次核验前先比对哈希确认没被换件，每次更换版本重跑全套。
- H.264/AAC 等编解码属 MPEG-LA/Via-LA **专利池**——与许可正交的另一类风险，商用分发前法务终审。
- 实现细节（provision 脚本、`_ffmpeg-env.ps1` 加载器）属**项目特定**，未随模板分发——参考实现见原始项目。
