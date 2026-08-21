---
id: T0-DEBT-LICENSE-SCALAR-FORMAT
title: 让许可元数据与诊断拒绝或清洗增补平面格式标量
depends_on: [T0-DEBT-UNICODE-SCALAR-TEXT]
parallelizable_with: [T0-DEBT-SECRETS-SCALAR-FORMAT]
status: merged
branch: T0-DEBT-LICENSE-SCALAR-FORMAT
worktree: C:\wt\T0-DEBT-LICENSE-SCALAR-FORMAT
allow_paths:
  - scripts/check-licenses.ps1
  - scripts/license-scanner-check.ps1
forbid:
  - 复制或重写 scripts/_unicode.ps1 的 scalar iteration
  - 把未知/非法许可元数据降级为 warning 或 permissive
  - 新增依赖、出站网络、登录态写入或自动发布
non_goals:
  - 修改许可政策、GAV/POM 解析或分发触发语义
  - 修改 secrets allowlist
  - Unicode 归一化或 bidi policy
diagnosis:
  root_cause: check-licenses 的 metadata guard 与 diagnostic sanitizer 使用 UTF-16 regex，增补平面 Cf 可穿过拒绝和输出清洗边界。
  same_class: 许可脚本内两个 live consumer 与 dedicated scanner check 同卡覆盖；secrets consumer 独立拆卡。
dod_command: pwsh -NoProfile -File scripts/license-scanner-check.ps1 -Suite policy; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -File scripts/license-scanner-check.ps1 -Suite diagnostics
dod_exit: 0
dod_assert: POM/exception metadata 中任一增补平面 Cf 或 malformed UTF-16 均 fail-closed；diagnostic 输出把每个 BMP/增补 Cc/Cf 映射为单个 space 且普通 emoji 原样保留；真实 scanner 行为与删除 consumer 接线变异均命中专属 ASCII 失败码
review_gate: codex {verdict:pass}
hygiene: 扩展既有 license-scanner-check，不建平行测试；期望值用手写 hostile fixtures，不由生产 helper 反算
doc_sync: 合并后卡状态/归档；TD157 保持 carded，TASK-BOARD 记录 license consumer 已关闭
---

# T0-DEBT-LICENSE-SCALAR-FORMAT

## 单一产出

许可入口复用已合并的 scalar helper，令 metadata validation 与输出 sanitizer 对增补平面 Cc/Cf、malformed UTF-16 与普通增补字符给出一致且可证伪的边界行为。

## 验收

```powershell
pwsh -NoProfile -File scripts/license-scanner-check.ps1
```

- 期望退出码：0
- 断言：真实许可扫描器与诊断路径均执行 hostile scalar fixtures；不以 source token 计数替代行为。
