# 发布前检查清单（Release Checklist）

> **工具无关的发布前收口**：把已有闸（防泄露 / verify）+ 授权安全自查整合成一张可勾选清单。
> 这是 10 类沉淀里唯一真缺的收口环节——**上线/变 public/交付前**逐条过。
>
> **小项目（T0）按需取子集**（见 `docs/IDEA-TO-PLAN.md` 按规模档位表），别全套照搬；
> 每条都**工具无关、可勾**——括号里的脚本只是本脚手架当前的落地举例。

## 安全
- [ ] `pwsh scripts\check-secrets.ps1 -Strict` 全绿 —— 核心数据库 / 密钥 / 凭据**既被 gitignore、又未被 git 追踪**（已追踪 → `git rm --cached`，gitignore 救不了已追踪文件）。
- [ ] 代码 / 配置 / 日志里**无明文密钥**（API key / token / 口令 / 连接串）。
- [ ] `.env.example` 只含占位，**不含任何真值**；真实 `.env` 永不入库。

## 授权 / 认证
> 方法论 + 检查项，不绑具体实现（session / JWT / OAuth 任选）。
- [ ] 认证方式已定且一致（session / JWT / OAuth 之一），未在同项目里混用。
- [ ] **越权（IDOR）**：每个按 id 取数据 / 改数据的接口都校验「当前用户是否有权访问该资源」，不只靠「登录即放行」。
- [ ] **会话固定**：登录后轮换 session id / token；登出使旧凭据失效。
- [ ] **token 安全存储**：不放可被 JS 读取的 localStorage（XSS 可窃）；敏感 cookie 设 `HttpOnly` / `Secure` / `SameSite`。
- [ ] **CSRF**：状态变更请求有 CSRF 防护（token / SameSite / 双提交 cookie）。
- [ ] **密码哈希**：用 bcrypt / argon2 / scrypt 等慢哈希 + 每用户 salt，**绝不**明文 / MD5 / SHA-1。
- [ ] 所有**敏感操作**（删除 / 转账 / 改权限 / 导出）服务端有鉴权，不靠前端隐藏按钮。

## 质量
- [ ] 关键能力完成度达标 —— **✅DONE**（NOT-DONE 项已确认可接受或已补；按 `docs/EVAL.md` 方法论自评，opt-in，若已接自有 eval runner/CI 则据其确定性报告核对）。
- [ ] `pwsh scripts\verify.ps1` 通过（确定性 / 离线最小闭环）。
- [ ] `pwsh scripts\check-licenses.ps1`（变 public / 正式发布前加 `-Strict`）—— 依赖许可合规：无 GPL/AGPL/SSPL/非商用等**禁列**；黄牌（LGPL/OpenRAIL/MPL）已人工确认用途/链接方式；模型权重 / 数据 / 字体 / 素材另按 `docs/LICENSE-POLICY.md` 政策表登记。
<!-- 编辑本项的任何字符都须同步更新 scripts/selftest.ps1 的 17ee $rcCanonHash，并重跑 pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded。 -->
- [ ] **Gradle 传递依赖许可全量核验通过** `[GRADLE-LIC-SCANNER-ONLY]`（阻断项）——运行 `pwsh -NoProfile -File scripts/license-scanner-check.ps1 -Suite integration`；该套件聚合 graph/policy/diagnostics/gav-bounds，并对真实仓执行 `scripts/check-licenses.ps1 -Strict`、确认 `org.testng:testng` 被逐坐标报告。缺失、未知、禁列或无法解析的许可元数据均 fail-closed；模型权重、数据、字体、素材仍按 `docs/LICENSE-POLICY.md` 人工登记。**未清零前不得分发**（打包 APK 对外分发 / 变 public 均算分发）。**触发点澄清**：GPL 系纯 copyleft 的义务触发点是**分发**；但 AGPL/SSPL/EUPL/非商用/研究限等触发点与分发无关，即便当前未分发也不豁免（见 `docs/LICENSE-POLICY.md` §1.1「不是自动豁免」）——这些坐标一旦在传递依赖中现身须**立即**处理，不得拖到发布前才查。承接 `T0-LICENSE-SCANNER` 的 `T0-LICENSE-CI-INTEGRATION` 合并且总验收通过后方可勾选，**刻意不设人工逐坐标核验替代路径**（人裁 2026-08-16；该单一解锁契约由 `selftest.ps1` 闸 17ee 对本项逐字节机检，CI 接线由上述套件的 `[INTEGRATION-CI-*]` 断言机检）。CI 里 License gate 排在 JDK/Android/Gradle setup、Gradle 在线 cache warm-up 与精确版本 `pip-licenses==5.5.5` / `license-checker@25.0.1` 在线工具预热**之后**；Android 四步另有 `hashFiles('android/gradlew.bat') != ''` 条件，该文件缺席时它们不执行而 License gate 仍会跑并独立发现 Gradle 清单：确无清单才只余 PyPI/npm；有清单但 wrapper/缓存不可用则以 `GRADLE-WRAPPER-OFFLINE` fail-closed。License gate 和 integration 真实扫描对 uv/npm/Gradle 三种生态均强制离线；缓存或工具缺失即失败，冷缓存探针验证不会尝试 outbound network。
- [ ] 集成 / e2e 覆盖**主流程**（不只单元测试；接法见 `docs/DELIVERY-OPS.md`，verify 闸门2 已接非占位）。
- [ ] 根 `LICENSE` 的 copyright holder 已替换为你的法律实体（脚手架默认是**专有占位**，含上游账号名），或明确确认沿用占位；若分发，确认 bundle 的第三方 MIT 件（`.claude/skills/ponytail`、`taste-skill`）各自的 `LICENSE`/`NOTICE` 仍随附保留（根 LICENSE 的「All Rights Reserved」**不覆盖**这些第三方件）。

## 可观测
- [ ] 关键路径有**结构化日志**就绪（含请求 ID / 用户 ID 便于追踪），且**敏感字段已脱敏**（密码 / token / PII 不落日志）。

## 发布
- [ ] **灰度 / feature-flag** 就绪（新功能可分批放量 / 一键关）。
- [ ] **回滚路径已验证**（不是「理论上能回滚」，是真演练过）。
- [ ] （变 public 则）防泄露闸 `check-secrets.ps1 -Strict` 全绿（与「安全」首条同，变 public 前**再跑一次**确认）。

---
> 收口完成 = 上面与本次发布相关的项**全勾**。流程入口与档位裁剪见 `docs/IDEA-TO-PLAN.md`；交付/运维方法论见 `docs/DELIVERY-OPS.md`；安全约定见 `docs/SECURITY.md`。
