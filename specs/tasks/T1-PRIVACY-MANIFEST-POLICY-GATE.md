---
id: T1-PRIVACY-MANIFEST-POLICY-GATE
title: Android merged-manifest privacy gate and narrow policy primitives
depends_on: [T1-LOCAL-DATA-SECURITY]
status: todo
branch: T1-PRIVACY-MANIFEST-POLICY-GATE
worktree: C:\wt\T1-PRIVACY-MANIFEST-POLICY-GATE
allow_paths:
  - android/app/build.gradle.kts
  - android/app/src/main/AndroidManifest.xml
  - android/app/src/main/res/xml/
  - android/app/src/main/kotlin/nz/myinspection/app/privacy/policy/
  - android/app/src/test/kotlin/nz/myinspection/app/privacy/policy/
forbid:
  - 运行期出站网络；修改冻结 schema/backup format；申请 READ_MEDIA_IMAGES/READ_EXTERNAL_STORAGE 只为导入照片
  - 全局 FLAG_SECURE；后台/启动时读取剪贴板；绕过系统 Photo Picker/SAF 扫描相册
  - FileProvider、content/file URI、分享 staging、temporary grant、报告渲染或 chooser；这些归 T1-SHARE-SCREEN-PRIVACY
  - 禁止未经授权的运行期出站网络、账号/RBAC、遥测；未经本卡 version review 不得改冻结 schema/backup format
non_goals:
  - PDF/HTML 渲染、已验证报告产物、文件分享、备份/恢复、网络、UI 导航或权限请求 UI
  - 任何读取 Clipboard 的能力，或把系统 Picker/SAF 原始 URI 变成可任意复用的路径
requirements:
  - "R1 合并 manifest 必须保持 allowBackup=false、usesCleartextTraffic=false，且旧版/Android12+ 的每个 backup/D2D domain 均完整排除；不得声明全相册读取权限。"
  - "R2 SelectedMediaPolicy 只能接受系统 Photo Picker 或 SAF 的单项、用户已选 scoped capability；ClipboardPolicy 只允许显式通知正文写入且无读取 API；SensitiveSurfacePolicy 只标记已列敏感表面。"
acceptance:
  - "A1 :app:check 的 merged-manifest task 解析 application attrs、backup/D2D XML 以及 uses-permission 元素，删除或错误替换 cleartext/backup/permission 任一生产配置后失败。"
  - "A2 pure JVM tests reject picker/SAF 之外、批量/未选择 capability 和任意 clipboard read；删除任一 policy allowlist/敏感表面分类后对应行为测试失败。"
  - "A3 SensitiveSurfacePolicy 仅标记 backup password、restore preflight、tenant contact、full sensitive photo；capture/list/report action 明确不带全局 secure。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:check :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: :app:check 的真实 merged-manifest 解析证明 backup/D2D、cleartext 和读取权限闸；JVM policy 测试证明三份窄 policy；无 FileProvider、ShareStaging 或 Clipboard read API。
review_gate: codex {verdict:pass}
hygiene: R4 对 cleartext attr、任一 backup/D2D domain、任一禁读权限、每个 policy allowlist 与每个 sensitive/ordinary 分类保留生产语义变异
version_review: this card = the manifest policy review
doc_sync: ADR-0006 + SECURITY + TASK-BOARD（R5）
---

# T1-PRIVACY-MANIFEST-POLICY-GATE

## 产出与边界

本卡取得现有 merged-manifest Gradle 检查的真实 owner：扩展 `requiredApplicationAttrs` 为 `usesCleartextTraffic=false`，并解析 merged manifest 的 `uses-permission`，拒绝 `READ_MEDIA_IMAGES` 与 `READ_EXTERNAL_STORAGE`。既有 backup/D2D XML 的所有 domain/path 排除继续由同一 `:app:check` 任务解析，不以源文本匹配代替。

它新增纯 policy primitives：`SelectedMediaPolicy` 只表达单项、已选的系统 Picker/SAF capability；`ClipboardPolicy` 没有 read 方法，只有用户显式 `Copy notice` 的通知正文写命令；`SensitiveSurfacePolicy` 只返回已列的敏感表面。三者都不触碰 Android Stub、原始 URI、文件或导航。

## 预算与执行

完整 diff 目标 450–620 changed lines / 30k–44k characters：Gradle/manifest/XML 45–75、policy 120–170、直接 JVM 与 merged-manifest 回归 190–260、R4 与修复余量 95–115。首选 GPT-5.6 Terra · high；merged-manifest 解析和闭集 policy 需要高 effort，预算不因 effort 上调。超过 650 行或 48k 字符先把 policy matrix 与 manifest gate 分开，不削弱 merged-artifact 验收。
