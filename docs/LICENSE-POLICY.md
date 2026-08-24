# 依赖许可证政策（License Policy）

> 若你的项目是**商用产品**，本文件是依赖准入的硬规则。每新增/升级一个依赖（代码、模型权重、字体、素材、训练数据条款）都必须照此核验。
> ⚖️ 本文件是工程约束，非法律意见；商用发布前由法务终审。
> （若你的项目是开源/内部工具，可放宽 §1，但保留「无 non-commercial、来源洁净、许可明确」三条。）

## 1. 绝对禁止（任一即拒）
- **Copyleft 传染性许可**：GPL-2.0 / GPL-3.0 / AGPL-3.0 / LGPL（静态链接情形）/ SSPL / EUPL 等——
  会要求本项目源码以同等条款开源，与商用专有冲突。
- **Android/Gradle 的 EPL-1.0 / EPL-2.0**：由 §3.2 的 Gradle 分类器按禁列许可阻断。现有 PyPI/npm
  共享扫描器保留原行为，本卡未把 EPL 自动阻断扩展到这两个明确排除的生态；在后续专卡补齐前须人工复核。
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

> 下表只保留构建工具、受控例外及其它需要人工证据的登记；它不是 Gradle 传递依赖的人工覆盖清单。
> `scripts/check-licenses.ps1` 自动扫描 PyPI/npm，并从 Gradle **真实解析的 classpath**逐坐标读取缓存 POM 许可；
> Gradle 的已解析传递闭包按 §3.2 机器核验，模型权重/数据/字体/素材仍须**逐项**人工登记。Gradle 的缺失、未知或
> 无法解析的元数据不是告警，而是普通模式也失败的合规问题。

| 依赖 | 许可 | 结论 |
|---|---|---|
| _(示例)_ FastAPI | MIT | ✅ |
| _(示例)_ uvicorn | BSD-3 | ✅ |
| … | … | … |

### 3.1 Android/Gradle 构建工具与受控例外的人工证据（非传递闭包清单）

> 本表保存构建工具与受控例外的证据来源，不承担传递闭包覆盖；自动扫描规则见 §3.2。人工证据须对准每条坐标的**发布方主库
> LICENSE 文件 / Maven POM `<licenses>` 块**（非 README 措辞、非 `aar-metadata.properties`——后者记
> `minCompileSdk` 等构建要求，与许可无关，见 T0-TOOLCHAIN 第 4 轮评审纠正）。同一发布方（同一 monorepo/组织）
> 下的多个坐标共用同一份 LICENSE 依据，逐条列出坐标、依据只列一次代表性来源。

| 坐标 / 构建组件 | 许可 | 结论 | 依据 |
|---|---|---|---|
| **androidx 七个不同 group**（同 monorepo，共享同一 LICENSE，逐条列出真实坐标防误读成同一 group 通配）：`androidx.compose:compose-bom`；`androidx.compose.ui:ui` / `androidx.compose.ui:ui-tooling` / `androidx.compose.ui:ui-tooling-preview`；`androidx.compose.material3:material3`；`androidx.activity:activity-compose`；`androidx.camera:camera-core` / `androidx.camera:camera-camera2` / `androidx.camera:camera-lifecycle` / `androidx.camera:camera-view`；`androidx.exifinterface:exifinterface`；`androidx.work:work-runtime-ktx` | Apache-2.0 | ✅ | androidx monorepo `LICENSE.txt`（`android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt`） |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | Apache-2.0 | ✅ | `github.com/Kotlin/kotlinx.serialization` `LICENSE.txt` |
| `app.cash.sqldelight:runtime` / `sqlite-driver` / `android-driver`，及同名 Gradle 插件 `app.cash.sqldelight` | Apache-2.0 | ✅ | `github.com/cashapp/sqldelight` `LICENSE.txt` |
| `org.jetbrains.kotlin:kotlin-test` / `kotlin-test-testng`，及 Kotlin Gradle **插件 ID**（`libs.versions.toml` 里 `kotlin-jvm`/`kotlin-android`/`kotlin-compose`/`kotlin-serialization` 只是目录别名，真实 id 分别是）`org.jetbrains.kotlin.jvm` / `org.jetbrains.kotlin.android` / `org.jetbrains.kotlin.plugin.compose` / `org.jetbrains.kotlin.plugin.serialization` | Apache-2.0 | ✅ | `github.com/JetBrains/kotlin` `license/LICENSE.txt` |
| `androidx.camera:camera-core:1.5.3` | Apache-2.0（CameraX/AOSP）+ BSD-3-Clause（内置 libyuv） | ✅（**仅此精确 GAV + POM 名称映射；人裁确认**） | CameraX 发布源码沿用 AndroidX Apache-2.0；Google Maven 的精确 GAV POM 额外声明宽泛的 `BSD License` 并指向 libyuv：`https://dl.google.com/dl/android/maven2/androidx/camera/camera-core/1.5.3/camera-core-1.5.3.pom`。本次人裁确认该制品按拆分许可记录，并以 AOSP libyuv 不可变快照 `9ac065f6219f6a1c7d000be0673133947e8e5fe7` 的 BSD-3-Clause `LICENSE` 作为名称映射证据：`https://android.googlesource.com/platform/external/libyuv/+/9ac065f6219f6a1c7d000be0673133947e8e5fe7/LICENSE`。公开发布元数据未把该 AAR 钉到这个 libyuv SHA，因此这是**人裁接受的精确 GAV 映射**，不是发布方构建溯源证明，也不是 `BSD License` 的全局白名单。登记见 `configs/licenses/gradle-exceptions.json`。 |
| `com.google.auto.value:auto-value-annotations:1.6.3` | Apache-2.0 | ✅（POM 无 `<licenses>` 的精确回退） | 已缓存的该精确坐标 POM 只有 Apache-2.0 头注、无 `<licenses>`；发布方 `auto-value-1.6.3` 快照 `cee66e1081668d30d7e1c9892efa759e851f22f0` 的 `LICENSE.txt`：`https://raw.githubusercontent.com/google/auto/cee66e1081668d30d7e1c9892efa759e851f22f0/LICENSE.txt`；登记见 `configs/licenses/gradle-exceptions.json`。 |
| `com.google.guava:listenablefuture:1.0` | Apache-2.0 | ✅（POM 无 `<licenses>` 的精确回退） | 已缓存的该精确坐标 POM 无 `<licenses>`，其父为 `guava-parent:26.0-android`；Guava v26.0 快照 `bbf9295088d668c06e573cea03f01375985c1814` 的 `COPYING`：`https://raw.githubusercontent.com/google/guava/bbf9295088d668c06e573cea03f01375985c1814/COPYING`；登记见 `configs/licenses/gradle-exceptions.json`。 |
| `org.testng:testng`（经 `kotlin-test-testng` 引入，测试运行器；替代 EPL-1.0 的 JUnit，见 toml 内注释） | Apache-2.0 | ✅ | **实测解析版本** `testng:7.0.0`（`:core:dependencies --configuration testRuntimeClasspath` 输出所示，非 Maven Central 上的最新版——早前文档误引了 `7.5.1.pom`，R3 评审纠正）；`testng-7.0.0.pom` `<licenses>` 块 = `Apache Version 2.0, January 2004`；其 `junit:junit:4.12` 依赖标 `<optional>true</optional>`，Gradle 不解析进最终 classpath（实测同一条 `:core:dependencies` 输出零 junit 节点）——**版本号以实测解析结果为准，不是 POM 里写的最新版**，核验证据须对准"实际会被打进产物的那个版本"，同 `docs/LICENSE-POLICY.md` 附录 A 对 ffmpeg 的"对准确切二进制"要求同一纪律 |
| `com.android.tools.build:gradle`（AGP，`com.android.application`/`com.android.library` 插件） | Apache-2.0 | ✅ | `dl.google.com` 该坐标 POM `<licenses>` 块（`The Apache Software License, Version 2.0`） |
| Gradle 构建工具本体（非 Maven 坐标依赖，`gradle/wrapper/gradle-wrapper.properties` 钉版） | Apache-2.0 | ✅（构建期工具，未随产品分发） | gradle.org 许可声明；wrapper `distributionSha256Sum` 已由编排者独立核验匹配官方发布 |

### 3.2 Android/Gradle 传递依赖：逐坐标机器核验

- **解析范围**：脚本离线运行 `:core` 的 `runtimeClasspath` 与 `testRuntimeClasspath`，以及 `:app` 的
  `debugRuntimeClasspath` 与 `releaseRuntimeClasspath`；后者确保交付 app 的两条 runtime 图被覆盖，前者保留
  TestNG 测试图。它从 Gradle 实际解析输出取 GAV，绝不把 `libs.versions.toml` 的字面声明当作传递闭包。
- **范围边界**：四张图之外的 `kotlinCompilerClasspath*`、`kotlinCompilerPluginClasspath*`、
  `androidLintTool`/`*LintChecksClasspath`、SQLDelight `*DialectClasspath`/`*IntellijEnv`/`*MigrationEnv`、
  `unified-test-platform-*` 与 Android/unit-test 专用图是构建期工具或未随 app 分发的测试图，不纳入本卡的
  交付闭包。它们的直接构建组件仍按 §3.1 人工登记；若未来随交付物分发，须另卡扩展自动扫描范围。
- **证据与输出**：每个唯一 GAV 只报告一次（按坐标稳定排序），并列出命中的 configuration；许可证来自本机
  Gradle 缓存的对应 POM。POM 的自声明 GAV 必须与缓存路径/已解析坐标一致，POM 解析失败、无许可证、未识别
  的许可证名或无 POM 都会立即失败，**即使未传 `-Strict`**。
- **受控例外 A（缺失元数据回退）**：仅当缓存中根本没有 POM、或**所有**已校验的缓存 POM 都没有
  `<licenses>`/`<license><name>` 时，才可在 `configs/licenses/gradle-exceptions.json` 记录精确的
  `group:artifact:version` 回退；它不适用于任何有效 POM，也不会覆盖同 GAV 的缺失/已声明混合副本。
- **受控例外 B（有效 POM 的名称映射）**：有效 POM 中某个**未识别**的 `license/name` 可附
  `declared_license`，但它必须与该 POM 已观察到的名称逐字匹配，并与 `coordinate` 共同精确定位；记录的
  `license` 必须是 §2 的完整宽松 canonical 名称。该路径只处理 `unknown`，不会覆盖 GPL/AGPL/SSPL/EUPL/EPL/
  非商用等禁列，也不会把同一 POM 的其它禁列名称掩盖掉。例如 camera-core 1.5.3 的 `BSD License` 仅映射到
  BSD-3-Clause：人裁确认该精确制品采用 Apache-2.0 + BSD-3-Clause 的拆分许可，并以不可变 AOSP libyuv
  `9ac065f6219f6a1c7d000be0673133947e8e5fe7` 的 `LICENSE` 为证据；这不扩展为宽泛名称的全局映射。风险分类会先统一
  分隔符、`License`/`Licence` 与常见全称别名；`declared_license` 键本身仍须与原始 POM 文本逐字匹配，不做宽松归一化。
- **共同校验**：每条记录都必须有 `coordinate`、`license`、`evidence_url`、`registered_by`、`registered_on`
  （`yyyy-MM-dd`）；名称映射另须 `declared_license`。URL 必须是绝对 http(s) 地址。通配、重复、缺字段、
  非宽松 canonical、日期/URL/JSON 格式错误全部 fail-closed。POM 解析失败、GAV 不匹配或同 GAV 缓存副本冲突
  一律不可由任何例外覆盖；未映射的未知名称仍为普通模式即失败的 `[GRADLE-UNKNOWN]`。
- **变更纪律**：新增/升级 Gradle 依赖后运行本脚本并审阅逐坐标输出；例外不是自动批准，仍须保留可复查的许可
  证据和登记人。GPL/AGPL/SSPL/EUPL/EPL/非商用等命中 §1 时照常阻断。

## 4. 模型权重 / 数据 / 素材核验（项目特定 · 待填）

> 代码与权重**双双**宽松（MIT/BSD/Apache-2.0）才算洁净；研究专用 / 非商用 / OpenRAIL 用途限制 / 自定义"社区许可" / NOASSERTION 一律按 §1 阻断。
> 常见陷阱：① 代码 MIT 但**权重 OpenRAIL-M/非商用**（以权重分发点的 model card 为准，README 措辞不算数）；
> ② 传递依赖藏 GPL（如某 G2P/VAD/JP 文本前端组件）——**静态扫描洁净 ≠ 运行洁净**，须真机 provision + smoke 实测（见 docs/lessons L17/L28）；
> ③ 无可行 CPU 路径的大模型——许可可能洁净但卡在架构边界（按你的 GPU 政策决定）。

| 模型/权重/素材 | 代码许可 | 权重/数据许可 | 结论 |
|---|---|---|---|
| _(待填)_ | | | |
| `docs/references/claude-*-llms.txt`（7 份，vendored 第三方**文档**提炼件） | n/a（非代码） | © Anthropic，公开文档、**无再分发授权** | ⚖️ **按最小引用保留，非再分发**：正文自行提炼（中文改写 + 本仓注解），仅保留**要照字面喂给模型才生效**的功能性提示语短片段并逐条标注出处；超出功能必要长度的整块示例改写为要点 + 链接（已执行：4.8 篇 AEFRM 整份 brief）。逐条基准见 `docs/references/README.md`「来源与引用基准」，每份文件头注含源 URL + © + 校核日期。**本判断是工程约束非法律意见**——若本仓或下游要**对外分发**含本目录的产物，须法务复核 |

> **为什么单列这一行**：本目录不是依赖、不是权重，是**第三方文档的提炼件**，`check-licenses.ps1` 的自动许可扫描覆盖不到（§5.1 生态缺口的一个具体实例），故按 §4「逐项人工登记」在此登记。新增任何 vendored 文档类 reference 时**在此追加一行**。

**运行时机器闸（可选模式）**：若项目接入真实模型权重，建议落一个 `require_clean_weights(model_id)` 运行时闸——
未登记为商用洁净的 `model_id` 即 fail-closed 拒绝（默认拒绝），与授权闸并列为推理前置。

## 5. 核验流程（每次加依赖）
1. 跑 `pwsh -File scripts\check-licenses.ps1`（扫描后端 venv、前端 node_modules 及 Gradle 四张已解析 classpath；
   命中禁列、Gradle 元数据未知或解析失败均非零退出）。
   Scanner 自身或 CI 接线变更须再跑 `pwsh -NoProfile -File scripts/license-scanner-check.ps1 -Suite integration`；
   它串行聚合 graph/policy/diagnostics/gav-bounds 四个专用套件，并对真实仓执行 `-Strict` 扫描、确认
   `org.testng:testng` 被逐坐标报告。CI 里 License gate 步骤排在 JDK/Android/Gradle setup 与在线 cache
   warm-up **之后**（该顺序由上述套件的 `[INTEGRATION-CI-ORDER-SEQUENCE]` 断言机检）；但那四步另有
   `hashFiles('android/gradlew.bat') != ''` 条件，该文件缺席时它们不执行、License gate 仍会跑并独立发现
   Gradle 清单：确无清单才只余 PyPI/npm；有清单但 wrapper/缓存不可用则以 `GRADLE-WRAPPER-OFFLINE`
   fail-closed。CI 先在独立在线步骤以精确版本 `pip-licenses==5.5.5` / `license-checker@25.0.1` 预热工具；
   License gate 与 integration 真实扫描随后对 uv 设置 `UV_OFFLINE=1`、对 npm 设置
   `npm_config_offline=true`，Gradle 继续使用 `--offline --no-daemon`。三种生态在扫描阶段均禁止网络，
   缓存或工具缺失即 fail-closed；integration 的冷缓存探针会验证工具确实被调用且没有 outbound attempt。
2. 模型/权重/数据/字体/素材**逐项**记录到 §3/§4 表（自动许可扫描不覆盖这些资产）。
3. Codex 评审闸门会阻断疑似 copyleft/非商用片段。
4. 真实模型子环境跑 `pip-licenses` **全审**：GPL 硬禁（声明但未 import 的可卸）；LGPL 仅进程隔离/动态可留；UNKNOWN 元数据逐个核实际许可。

### 5.1 其它未接入扫描器的生态依赖清单覆盖缺口（advisory-only）
`scripts\check-licenses.ps1` 自动扫描 PyPI（`pyproject.toml`）、npm（`frontend/package.json`）和 Gradle
（见 §3.2）；若仓库还存在其它生态的依赖清单（`go.mod`/`Cargo.toml`/`Gemfile`/`composer.json`/
`pubspec.yaml`/`pom.xml` 等），探针会把它登记为「覆盖缺口」——**这不代表你
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

**审计闭环**：PR 走 codex 评审（`scripts/review.ps1`）对照改动是否含可疑外来源码片段 · `scripts/check-licenses.ps1` 自动扫描 PyPI/npm 与 Gradle 已解析坐标、并对其它生态清单报覆盖缺口 · 安全约定见 `docs/SECURITY.md`。上述第 3 条 LGPL 构建的逐步核验（含 `-buildconf` 比对与 SHA-256 校验）见**附录 A**。

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
