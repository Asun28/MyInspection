---
id: T0-DEBT-UNICODE-SCALAR-TEXT
title: 建立 fail-closed 的 Unicode scalar 控制/格式文本单一真相源
depends_on: []
status: merged
branch: T0-DEBT-UNICODE-SCALAR-TEXT
worktree: C:\wt\T0-DEBT-UNICODE-SCALAR-TEXT
allow_paths:
  - scripts/_unicode.ps1
  - scripts/selftest.ps1
forbid:
  - 用 UTF-16 码元正则代替 Unicode scalar 分类
  - 静默替换 malformed UTF-16 后继续信任输入
  - 新增依赖、出站网络、登录态写入或自动发布
non_goals:
  - 接入 license 或 secrets consumer
  - Unicode 归一化、大小写折叠、grapheme 或 bidi policy
  - 修改产品代码、schema 或冻结物
diagnosis:
  root_cause: .NET regex 按 UTF-16 码元分类，增补平面 Cf 标量在正则眼里是两个 Cs，现有 Cc/Cf 字符类因此漏判。
  same_class: 全仓 Cc/Cf consumer 已扫描；本卡只建立共享 scalar primitive，license 与 secrets 分别由后续卡接入。
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard core
dod_exit: 0
dod_assert: helper 逐 Unicode scalar 保留普通 BMP/增补字符、把所有 Cc/Cf 各替换为一个 ASCII space、对 lone high/low surrogate fail-closed；exhaustive oracle 枚举完整 scalar 空间而非手挑样本，删除 scalar advance、类别判定、malformed guard 任一句均命中专属失败码
review_gate: codex {verdict:pass}
hygiene: exhaustive fixture 只做内存内确定性枚举；无临时文件、无网络；mutation 各自精确分类并核还原 SHA
doc_sync: 合并后卡状态/归档；TD157 保持 carded，TASK-BOARD 记录 helper 已就绪
---

# T0-DEBT-UNICODE-SCALAR-TEXT

## 单一产出

新增 dependency-free PowerShell helper，按 Unicode scalar 而非 UTF-16 code unit 处理文本：Cc/Cf 映射为单个 ASCII space，其他 scalar 原样保留，malformed UTF-16 抛出明确错误。

## 验收

```powershell
pwsh -NoProfile -File scripts/selftest.ps1
```

- 期望退出码：0
- 断言：完整 scalar oracle、普通增补字符、增补平面 Cf 与 malformed surrogate 四类行为均由真实 helper 执行证明。
