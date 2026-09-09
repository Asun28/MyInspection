# V1 Android 平台探针

状态：2026-09-08 的历史受测 APK **实体手机验收完成，三项均判「成立」**；2026-09-06 模拟器预检保留在左栏。
当前远端恢复候选已通过构建 DoD、相机拍存读回与用户回位验证、SAF 重启读取及 PDF 真机复验；R3 和 PR 尚待完成。历史结论与本轮结果分开记录。
只包含 V1 相机叠图、SAF 和 PDF；V2 听写交专属任务卡。

## 设备与复现

**实体手机（2026-09-08 验收，各表右栏全部出自这台机器与这个 APK）**

- 设备：三星 Galaxy A34 5G，`SM-A346E`，Android 13 / API 33，USB 序列号 `RFCW50M8MXA`。
- Build fingerprint：`samsung/a34xdxx/a34x:13/TP1A.220624.014/A346EXXU4AWG8:user/release-keys`
  ——**零售 `user/release-keys` 机器**，而非模拟器的 `userdebug/dev-keys`；API 33 亦低于模拟器的 35，故非重复覆盖。
- 受测 debug APK：17,401,647 bytes，SHA-256 `593ff461c2ee56ac6b95bb6cfc245a8ef046db52ebf08251e936072122118cca`
  ——与下方模拟器栏的 APK **不是同一个构建**。

**历史受测 APK 与当时候选树的等价性**（仅限本地 feature `7851be96` 的交付记录；三处历史构建结果一致）：

| 构建基线 | SHA-256 | 字节数 |
|---|---|---|
| master `6814df77` + 本卡 8 文件 | `593ff461…118cca` | 17,401,647 |
| master `99a27657` + 本卡 8 文件 | `593ff461…118cca` | 17,401,647 |
| 历史候选树（feature `7851be96`，吸收当时 base 后） | `593ff461…118cca` | 17,401,647 |

当时记录的基线移动只含文档变化；此事实只解释上表，不适用于当前远端恢复。当前远端与历史候选有 Android 业务源码差异，不能推定 APK 字节相同。

**须如实记下的一处不等价**：若只在历史分支的 RED 取证基线 `89ae7657` 上构建（不吸收 base），得到的是
`f366b5a1…651539` / 17,213,270 bytes，与受测 APK **不同**——因为那个基线落后 master 168 个提交，其区间**确实**动过 `android/`。
该树不是当时的待合并物，真机结论也不依据它；此处点明，是为免后来者误以为「分支上任意一次构建」都等于受测 APK。
- 历史取证经 MTP：APK 复制进 `Download`，仅核对机上 `System.Size` 与 PC 文件**大小相同**后手动安装；这不是传输后逐字节或 SHA 校验。结果由机上截图承载并经 MTP 取回。
- 后续本地记录更正了“该机 adb 不可用”：验收期间未暴露 ADB 接口，但同晚拔插后接口以 MI_03 出现，ADB 立即看到已授权设备；随后读出的 fingerprint 与收据一致，SDK 为 33。原因是否为 Windows 接口缓存未经证实；本次报告修订未操作设备，也不把后续记录冒充当前恢复验收。

**模拟器（2026-09-06 预检，各表左栏）**

- Pixel_6 AVD，`emulator-5554`，Android 15 / API 35，`sdk_gphone64_x86_64`。
- Build：`google/sdk_gphone64_x86_64/emu64xa:15/AE3A.240806.043/12960925:userdebug/dev-keys`。
- 该轮 debug APK：17,081,418 bytes，SHA-256 `1b9bf01918f5c52da4cab7034122d99ebd79b5468c5c70f6691ca1590e87aba6`。

- 所有探针、CAMERA 权限、入口与字体只在 `android/app/src/debug/`；正式 app 导航不变。
- 构建：`cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug`。
- 安装生成的 debug APK，打开“平台探针（调试）”，或 `adb shell am start -n nz.myinspection.app/.spike.PlatformSpikeActivity`。
- 两栏数据分属两个 APK、两个 API 级别，**不得互相搬运**。

## ① 相机叠图

做法：竖屏，CameraX 1.5.3 Preview/ImageCapture 共享 PreviewView 的 ViewPort 与 UseCaseGroup；历史照限定为本探针之前一拍。通过官方坐标变换对位，保存后读回显示。图片仅存 app 专属 `spike/`。

操作：打开“相机叠图”并授权；拍一张含水平/垂直边缘的场景，观察历史照透明叠加。轻移手机后按边缘重新对位，比较保存后读回图的比例、方向和边缘。

| 项目 | 模拟器辅助结果 | 实体手机结果 |
|---|---|---|
| 相机权限、取景、保存、读回 | 通过；拒绝权限不报成功，授权后可重试；拍照、隐藏/显示 ghost、退出相机通过 | 通过：授权后取景、拍存、读回与 ghost 显隐均正常 |
| 同 ViewPort 对位与 EXIF 方向 | 同一虚拟场景静态边缘可重合；JPEG 1280×960、EXIF=6，读回为960×1280竖图；Preview 996×1328 | 真实场景（门框/门把手/开关面板）叠加后**轮廓成对可见**，据此可把机位移回原处；JPEG **1440×1080 横向像素 + EXIF 方向标记**，读回经 CameraX `oriented` 变换后正立且无形变；Preview 990×1320 |
| 二值结论 | 不代替实体结论 | **ghost overlay 成立**（采纳，不降级） |

给正式卡的参数：alpha=0.3、竖屏、后置镜头；任意外部历史照片不在本探针验证范围。

真机与模拟器在**方向处理上不同**：模拟器读回时已是 960×1280 竖图，本机则存下 1440×1080 **横向**像素、靠 EXIF 标记转正
（`decoded` 报的是未套 EXIF 的原始像素，屏幕上那张缩略图走的是 `GhostPainter(photo, photo.oriented, photo.orientedSize)`）。
这正好在真机上走通了 CLAUDE.md「EXIF 旋转必须处理」这条不变量，是 `T2-PHOTO-PIPELINE` 可直接引用的参数。

对位是否**可用**由操作者当场判定（人工判断，非机检）：结论为可用。用户同时提出把 ghost 改画成绿色描边而非平铺半透明照片，
该诉求归 `T3-HISTORY-COMPARE`（叠图呈现的归属卡），不在本 spike 内实现。

## ② SAF 持久授权

做法：系统 ACTION_OPEN_DOCUMENT_TREE 选择专用测试子目录，保留返回的读写授权；创建唯一名称的合成文本，写入后逐字节重开验证。app 只保存该测试文档、目录 URI 与单行合成 ID，重启后重建预期字节。

操作：点“选择测试目录并写入”，选择一个新建的测试子目录并同意使用；记录通过。强制停止 app 后重新启动，点“重启后只读验证”；该步骤不得重写文件或重新弹授权。取消选择应保留旧记录。测试文件只含随机探针标识，可在验证后人工删除。

| 项目 | 模拟器辅助结果 | 实体手机结果 |
|---|---|---|
| 新文件写入/读回 | 通过，60 bytes；provider=`com.android.externalstorage.documents`，目录为专用 Documents 子目录 | 通过，`bytes=60`：新建目录被选中后写入并逐字节读回（22:13 截图） |
| 进程重启后授权仍在、只读重开 | 修复后通过；PID 6297→6531，persisted=0x3，文件SHA及修改时间不变；三项UI预检APK再次通过 | 通过（22:14 截图）：重启后 `persistedUriPermissions` 仍含该 tree 的读写权，文件按 60 字节精确重开，**未重写文件、未重新弹授权** |
| 二值结论 | 不代替实体结论 | **成立**（采纳，不降级） |

给备份卡的参数：只验证实际选中的 DocumentsProvider；不承诺所有 OEM/云 provider 具有相同语义，不写入任何巡检或备份数据。

**证据边界**：真机这一轮的「杀进程」是操作者按流程手动执行的（Settings → Apps → Force stop），**没有**像模拟器那轮
那样留下 PID 变化的机检记录，故此处只声称「重启后授权仍在、文件按精确字节重开」，不声称已机检证明进程确实被杀。
截图能证明的是 `persistedUriPermissions` 复查通过且 `verifyBytes` 的 60 字节精确比较通过。

实测发现并修复：本 build 将 prefs 中以换行结尾的 payload 写成 XML 时附入4个缩进空格（64 vs 文件60 bytes），重启比较失败，授权本身仍在。现只存单行 ID；原文件的逐字节比较不变。修复后**重新创建测试文档**再重启验收，未复用旧 prefs。将新文件追加1字节，验证拒绝；恢复60字节后通过。取消选择目录后旧记录仍可读回。

## ③ PDF 压力与中英字形

做法：80 张带独立编号的合成 JPEG，源 2048×1536，BitmapFactory `inSampleSize=4`，逐张检查实际解码 512×384 并 recycle。A4 595×842 pt，每图一页，第81页中英混排。每张源/解码/recycle及写入/关闭后采样进程 PSS。

操作：点“运行 80 图 PDF 压力测试”；读取 app 专属 `spike/platform-stress.pdf` 与 `spike/receipt.json`，复核81页、80图、回收80次、文件字节数、耗时与采样内存。查看第1、80、81页：编号正确、无拉伸、中文无缺字。

| 项目 | 模拟器辅助结果 | 实体手机结果 |
|---|---|---|
| 81页/80图/逐图回收、耗时与PSS | 顺序预检通过；初次9631ms / 最大215759 KiB；三项UI预检APK8000ms / 最大223839 KiB；修复重入后的交付APK10147ms / 基线118927 KiB / 最大219182 KiB，243个检查点 | 通过：**81页 / 80图 / 回收80次，7272ms**，基线 PSS 87051 KiB、采样最大 **225620 KiB**，243个检查点（收据 `receipt.json`） |
| 中英混排实际输出 | 输出550243 bytes；独立解析逐页编号与80枚512×384图像，中文文本完整；第1、80、81页渲染检查无拉伸/缺字；修后PDF所有页内容流及图像字节与该目检基线一致 | 输出 **2,366,495 bytes**（SHA-256 `6c9702cb…f84cb0`）；取回 PC 后用 PyMuPDF **独立复核**：81 页全为 A4 595×842 pt，内嵌图像恰 80 枚且**全部 512×384**，80 页页内编号 `Image N / 80` 全部正确，末页四行中英文本可原样抽出，内嵌字体为 `BAAAAA+DroidSansFallback` 与 `AAAAAA+RobotoStatic-Regular`（均 Type0/Identity-H）；渲染第 1/80/81 页目检**无缺字、无拉伸** |
| 二值结论 | 不代替实体结论 | **成立**（采纳，不降级） |

采样最大 PSS 是**整个进程在检查点的最大值，不是连续采样的瞬时绝对峰值**；耗时包括占位图编码和内存采样。合成图不是正式设备卡的80张真实复杂照片夹具，不可据此承诺四档大小或铭牌可读性。

给 `T3-PDF-RENDERER` 的参数：真机 7272ms **快于**模拟器三轮（8000–10147ms），内存量级相当（225620 vs 219182–223839 KiB），
故 `inSampleSize=4` + 逐图 recycle 的恒定内存路线在零售 API 33 机器上成立。**但 PDF 体积差 4.3 倍**
（真机 2,366,495 vs 模拟器 550,243 bytes），同样 80 张合成图、同样绘制代码——差异来自平台侧的图像编码与字体内嵌，
故**体积不可跨设备外推**，四档质量的字节预算必须在目标机型实测。另注意平台把一行混排文本拆给了两个字体
（拉丁走 Roboto、中日韩走 DroidSansFallback），两者都被内嵌，`T3-PDF-RENDERER` 估算内嵌字体开销时要按两个子集算。

重入修复：实测“生成中按返回→重开→再生成”会启动两轮任务，后一次开始时前一次尚未结束，并读到上一轮成功收据。现用进程级互斥覆盖全部输出操作；重复请求明确提示等待，不触碰文件。相同重叠操作复验通过，只产生原轮次收据。另用自建的非空临时目录模拟输出失败，确认不留成功收据；移除该测试阻挡文件后重试完成81页，证明异常也会释放互斥。原始 RED/GREEN 分别为 `lifecycle-fast-red.exit=1`、`lifecycle-fast-green.exit=0`；`failure-retry.exit=0`。

## 字体资产与许可登记

本卡按许可政策对调试字体逐项登记；字体与完整上游 NOTICE 同目录打包。

- 资产：`android/app/src/debug/assets/fonts/DroidSansFallback.ttf`，3,451,900 bytes，Apache-2.0。
- 固定来源：[AOSP android-15.0.0_r3 字体](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r3/data/fonts/DroidSansFallback.ttf)。Git blob `1099b177c881c96bf298989dcdca5fda7841133d`。
- SHA-256：`21b96a0377f067833a93af3082eb28d4ffab7a8cd46bfd513286f1d64b7b0949`。
- 许可依据：同固定版本 [MODULE_LICENSE_APACHE2](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r3/data/fonts/MODULE_LICENSE_APACHE2) 和 [NOTICE](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r3/data/fonts/NOTICE)。NOTICE SHA-256 `38751245389e1e23f73e6f5384b5cbe7fa972cc4410c5adc9c04b082a0b9561a`。
- 下载字节的 Git blob 均与官方目录 JSON 中 blob id 匹配。为通过仓库 whitespace 检查，打包 NOTICE 只移除末尾一个空行，许可正文完整；打包 SHA-256 为 `92d336191c9ec51cc39f0b2fc44e5153f685c661c3d1e44e088fad4e68358ab2`。没有加入 Gradle 运行时依赖。

## 历史验证记录（不代表当前远端恢复结果）

历史正式 RED 已记录：原始构建成功、报告缺失令 DoD exit1；模拟器入口断言因 Activity 不存在而失败。额外修复了授权成功后仍显示“权限被拒绝”的提示，并在最终APK复验。

- 卡片 DoD 命令在快进到 master `6814df77` 的基线上重跑通过（`assembleDebug` exit 0、报告存在）；三节真机结论已补齐。
- 真机证据留存 `_local/android-spike-physical/`：`shots/` 七张机上截图、`artifacts/` 的 `receipt.json`、
  `platform-stress.pdf`、渲染出的 `page-01/80/81.png` 与相机读回原图。
- `verify.ps1` 通过 core check 与 Golden Evidence JVM Core E2E；未新增/运行仪器测试或 Robolectric。
- Debug/release 构建通过；直接检查 release APK 的 manifest、DEX、assets：无探针入口、CAMERA 权限、探针类或字体。两次编译出现 Kotlin daemon 连接告警，Gradle 自动回退后 exit0；最终构建无该告警。
- 三项UI预检 APK 为 `315c664507b9a2fccd29dbc732c87373bf3eef9816c2963ac317307fd99edca3`；随后仅规范 NOTICE 空行的 APK 为 `16050cb776c6c4ffccd2917b5c923e5e35f546b9fc846f34daa6881ab0fcf134`。该轮模拟器交付 APK 在该版本上只增加 PDF 互斥，已实际安装并通过上述重叠与异常重试；相机/SAF 源码未改。
- 已解析的未变更依赖图许可扫描通过；调试字体人工许可登记见上。独立子 Agent 复核已完成；R4 无候选冗余测试，删除0项、未执行源码变异（本卡未添加测试源）。当时“尚未 R3/ship”的记录已被后续本地交付取代：正式 R3 第二轮通过，本地合并 `e8c2359a`；这不等于远端 PR 已交付。
- 历史本地全量 `selftest.ps1 -TaskId T1-SPIKE-PLATFORM -Base master` **exit1**：core/workflow 分片通过；seeded 在 `17a3(migration-continue/mutant)` 与 `17` 失败。前者的缺迁移被 verifier 先检出，未满足夹具要求的 test 先失败；后者报告 `17ai` 的交付脚本断言枚举不一致。相关脚本/core/build 输入与基线相同；未把失败当作通过，也未在本探针卡修改脚手架。这些参数与结果仅属当时本地脚手架，不作为当前远端命令或验证证据。完整日志及诊断留存 `lifecycle-selftest-master.log` 与 `selftest-diagnosis-*`。
- 原始日志、UI树、截图、PDF、收据与安装包保存在主检出 `_local/android-spike-2026-09-06/`，`checkpoint.md` 和 `final-verification.json` 记录恢复状态。

## 三项历史真机结论（绑定 APK `593ff461…118cca`）

| 风险 | 结论 | 归属承接卡 |
|---|---|---|
| ① Ghost overlay | **成立**，采纳实时叠图；**不**降级为「拍完并排比对」 | `T3-HISTORY-COMPARE` 按叠图方案推进；绿色描边呈现另议 |
| ② SAF 持久授权 | **成立**，采纳 | `T5-BACKUP-IO` |
| ③ PDF 压力与中英字形 | **成立**，采纳 | `T3-PDF-RENDERER`（体积须按机型实测，勿外推） |

## 当前远端恢复状态

- 恢复基线：`b0e07ce5`。本轮真实 RED 已记录：`assembleDebug` exit 0，报告不存在，完整 DoD exit 1；未复用历史 RED 凭据。
- 新候选 GREEN：`assembleDebug` exit 0，报告存在，完整 DoD exit 0。APK 为 **17,062,166 bytes**，SHA-256 `f85be189660fa190d68dff451d98f227eae421aa023ec486fba81369df1b5d5f`；与历史 APK 不同。
- 构建源码为远端 `b0e07ce5` 加本卡七个 debug 文件（逐文件 SHA-256 与 `7851be96` 一致）；本报告只修正证据归属与过时表述，不参与 APK 构建。
- 2026-09-09 已保持应用数据安装新候选；安装前后签名证书 SHA-256 均为 `9359bc3ace455fd6ce9bd511c2714fcb9ebc0cef24773e60a871772633be7ff7`。回读设备实际 `base.apk` 的完整 SHA-256 与候选 `f85be189…1b5d5f` 相同，设备为上述 SM-A346E / API 33。旧 APK 与 26 个探针产物先保存至 ignored 证据目录；未卸载、未清除数据、未读取巡检记录。
- **本轮 SAF**：现有探针授权只读读取 60 bytes 通过；force-stop 后旧 PID 19183 消失，冷启动 PID 19350 后再次读取 60 bytes 通过。未新建或重写 SAF 文档；写入步骤仍只由历史 APK 的记录证明，本轮验证授权延续与重启读取。
- **本轮 PDF**：新回执记录 81 页 / 80 图 / 回收 80 图，7,346 ms，243 个检查点的进程 PSS 最大值 237,093 KiB（非绝对峰值），文件 2,366,495 bytes。独立解析确认全部 A4、80 枚 512×384 图像、编号 1–80 与四行中英文完整；第 1/80/81 页渲染目检无缺字或拉伸。PDF SHA-256 为 `6c9702cb3feaf3de10159166419df27310f0d7056f4a58be2841d36a60f84cb0`，其字节与旧输出相同，但本轮耗时/内存来自新回执，未复用旧值。
- **本轮相机**：用户把镜头朝向门框后拍摄，屏幕出现 `History ghost`；`ghost-1788915133829.jpg` 的存储像素为 1440×1080、EXIF orientation=6，读回画面转正且门框/把手无可见拉伸。用户按提示轻微移开并依据门框、把手和开关重影移回，明确回复“可以对齐”；这是用户物理操作的确认，不伪称代理移动过手机。保留拍摄、叠图与读回 UI XML/截图，结论仍为 ghost overlay 成立。界面固定提示 `Visual alignment pending` 不自动裁决；本条结论来自该人工验证。正式 R3、PR 尚未执行。
- 本轮证据：主检出 `_local/routine-remote-recovery/device-current/` 的 UI XML、截图、PDF、回执及 `pdf-inspection.json`；安装回读为同目录上一级 `installed-candidate.apk`。
- 历史 `17a3`／`17ai` selftest 失败仍保留为失败记录，不冒充当前远端结果；本轮按实际远端卡与标准交付流程验收。
