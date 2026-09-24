---
id: T1-APP-STORAGE-POLICY-REMOTE
title: App-private storage routing over a verified path boundary
depends_on: [T1-SPIKE-PLATFORM, T1-SAFE-MEDIA-LOGGING-REMOTE, T1-STORAGE-PATH-BOUNDARY-REMOTE]
status: merged
parallelizable_with: [T3-PDF-MEASUREMENT-REQUESTS]
branch: T1-APP-STORAGE-POLICY-REMOTE
worktree: C:\wt\T1-APP-STORAGE-POLICY-REMOTE
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/platform/AppStoragePolicy.kt
  - android/app/src/test/kotlin/nz/myinspection/app/platform/AppStoragePolicyTest.kt
forbid:
  - 运行期出站网络；修改冻结 SQLDelight schema/backup format；明文 secret/tenant data 写日志或系统备份
  - device-protected storage 存租客数据；hard-coded 绝对路径；卷不可用时静默写共享相册
  - 任何 Keystore、加密 envelope、alias/purpose/version 策略或生产装配（分别归 T1-LOCAL-DATA-SECURITY 与 T1-APP-BOUNDARY-ASSEMBLY）
  - AndroidAppStorageEnvironment、Android 原始卷状态映射和目录可写探针（整体归 T1-APP-STORAGE-ANDROID）
  - 内嵌或重复真实路径解析、根快照与子目录边界实现（完整消费 T1-STORAGE-PATH-BOUNDARY）
non_goals:
  - SAF 备份写入/恢复状态机/口令 UX（T5-BACKUP-IO）；FileProvider/secure-window/network manifest（T1-SHARE-SCREEN-PRIVACY）
  - SQLCipher、账号、同步、遥测、业务 UI；迁移既有 PhotoRuntimeStorage 的媒体或数据库位置
  - SafeLog 或媒体日志接线的重复实现；前置卡保有该能力
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: app JVM 测试与 assemble 绿：AppStoragePolicy 消费具名环境端口及已交付 StoragePathBoundary，将六类受保护数据路由至保存的 CE/no-backup 根下、由前置逐次检查并返回的实际目录；媒体只消费 app-specific external 端口。六类映射、CE 转换、create/resolveChild 两处接线、固定错误脱敏与致命 Error 身份均被行为断言覆盖；卷空白/缺失/不可写/未挂载、低空间及等值边界保留。SafeLog 和前置路径测试继续绿；实际 Android 平台适配、业务装配和 Keystore 仍由后继验收。
requirements:
  - "R1 DB、settings、receipts、secret envelope、restore journal 与 staging metadata 必须路由至 credential-encrypted internal/no-backup；仅大媒体可路由至 app-specific external。"
  - "R2 端口提供的 external 卷必须为 MOUNTED 且可写；空目录或端口判定不可写（包括非空不存在路径）、未挂载、只读返回 Unavailable，usableBytes < requestedBytes 返回 InsufficientSpace，等于边界可用；不得暴露绝对路径或选择共享回退。Android MEDIA_MOUNTED 和实际文件状态的映射由 T1-APP-STORAGE-ANDROID 提供。"
acceptance:
  - "A1 各数据类别以显式类别到不同内部子目录的映射夹具证明 protected 类别绝不落 device-protected/external；标记为 DP 的环境先请求 CE 环境，转换后仍标记 DP 则拒绝；媒体仅消费具名 app-specific 端口根，不自行选择 shared/public。删除、交换或错误替换任一策略路由后，对应行为测试必须失败。实际 Android getter 来源和转换由后继直接验收。"
  - "A2 空目录、非空不存在路径且端口报告不可写、未挂载、只读、低空间与正常可用夹具返回闭合状态，明确验证 usableBytes 等于 requestedBytes；敏感路径与原始异常不得出现在结果文本、失败信息或 cause。"
  - "A3 实际根/子目录解析和全部直接边界测试归前置；本卡以真实错误根及越界 child 黑盒用例证明 create 与 resolveChild 没被绕过。保存 boundary.directory，返回 resolveChild 的已检查结果，不重新读取 raw root；两处 null 均转为固定无 cause 异常，致命 Error 按身份传播。"
review_gate: codex {verdict:pass}
hygiene: 路由、卷状态、环境转换、create/resolveChild 接线及脱敏各有具名变异；路径直接义务整体迁前置，旧证据不可冒充新 pin
doc_sync: ADR-0006 + SECURITY + TASK-BOARD（R5）
---

# T1-APP-STORAGE-POLICY-REMOTE

## 产出与边界

本卡提供 `AppStorageEnvironment`、`AppStoragePolicy` 和结构化结果，并消费已交付 `StoragePathBoundary`。构造时使用前置验证并保存的 CE/no-backup 根；每次 location 由前置检查类别子目录并返回该次实际检查的路径。外部媒体根来源仍由端口契约及后继 Android 适配器保证。六类受保护数据映射与闭合媒体状态留在本卡；类型名称不证明平台来源，也不保证检查之后的文件系统操作。

不做加密、KeyStore、信封 codec 或 alias/version/purpose 隔离。后继 `T1-LOCAL-DATA-SECURITY` 只消费这一 primitive 并交付 `LocalSecretBox`；`T1-APP-BOUNDARY-ASSEMBLY` 才把两者接入生产入口。现有媒体日志脱敏仅作为已交付前置的回归，不在本卡重写。

## RED、DoD 与预算

先用纯 JVM 测试固定六种映射、封闭媒体状态与前置两处接线；直接路径测试由前置整体交付，后继 DoD 仍运行这些真实测试。不得新增依赖；不得保留已知缺陷实现而只推迟测试。

2026-09-17：第三次 R3 指出实际 Android 映射缺直接测试；原差异 613 行 / 29,691 字符，补齐平台执行预计达 813–923 行，因此将整个 Android 适配实现、原始状态映射、可写 helper 及专属测试移至 `T1-APP-STORAGE-ANDROID`。保留三次失败裁决和原提交；不得仅推迟测试而保留未验证适配器。当前卡拆后预计 528–558 行 / 25k–27k，另有约 90 行修复空间；650 行或 45k 提前闸不变。纯策略重新跑完整 DoD、所有剩余最终源 pin 的 R4 和正式 R3。作者修复提升 GPT-5.6 Terra · high；独立 GPT-5.6 Sol · high 正式评审。

2026-09-17 后续裁定覆盖上述当前预算：第五次 R3 在 fd1dd18c 发现返回类别目录未检查真实边界；JDK17 实测 canonicalFile 不解析 Windows Junction。完整路径能力与直接测试拆至 T1-STORAGE-PATH-BOUNDARY，原卡在此前停驻，不新增修复 RED。15 行 helper 与 173 行直接测试整体迁出，必要接线测试保留；后继完整预计468–482行/23.5k–25.5k，650/45k提前闸不变，首候选不超过487行以保留25%容量。作者升级 GPT-6 Astra/high。保留全部五次 BLOCK、原提交和证据；前置实际交付后非重写 merge 吸收主线，再替换实现并重跑全部闸门，不 restart/rebase/reset 或将旧通过结果用于新源码。

## Remote execution order (2026-09-17)

This is product round 3, paired with T3-PDF-MEASUREMENT-REQUESTS, retaining the complete T1-APP-STORAGE-POLICY behavioral contract above. Dependency names in the front matter bind the remote deliveries. Start only after those functional PRs actually merge; registered metadata alone does not satisfy a dependency. Use the original D:/Projects/MyInspection/scripts/task.ps1 from the main checkout: start with -Base origin/master, then fresh RED and ship with -Base master, without -Local. Run the same candidate's full DoD, R4, verify, scope, licenses, secrets, complete-diff budget, independent R3 and exact-head CI before remote PR merge. Record PR/head/checks/merge and complete R5. This execution paragraph supersedes earlier local-only routing text, without reducing any acceptance or adding another card to the five-round count.
Preserve the original fd1dd18c branch/worktree and all five BLOCK verdicts. This isolated alias must consume the remotely delivered StoragePathBoundary create/resolveChild API and repair the named integration defects; it does not restart or erase the original review history. The old inline canonicalFile boundary is not an implementation source for this alias.

## Delivery (R5, 2026-09-24)

Merged by [PR #316](https://github.com/Asun28/MyInspection/pull/316): reviewed head `afffd3836a2d47521bbec338d2a1a435983e9a47`, CI run `35318686041` (verify and required succeeded), merge `15f3931b77924f5d1ae3e55cb0c866bc36e85946`, Codex R3 pass. R5 was not recorded at the time; this note closes it. Test gaps later found on a twin implementation of this card are handled by `T1-APP-STORAGE-POLICY-TESTS`; production behaviour is not in question.
