---
id: T4-COMPLIANCE-ENGINE-R3-CLOSURE
title: 合规引擎配置驱动与改期身份契约（PR #43 R3 收口）
depends_on: [T4-COMPLIANCE-ENGINE]
status: todo
branch: T4-COMPLIANCE-ENGINE-R3-CLOSURE
worktree: C:\wt\T4-COMPLIANCE-ENGINE-R3-CLOSURE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/compliance/
  - android/core/src/test/kotlin/nz/myinspection/core/compliance/
forbid:
  - 修改 48h/14d/08:00–19:00/寄宿 18:00/28d 的权威业务值
  - 启用 work-check、通知文本、UI、DB 或 Android 接线
  - 重开 PR #43 已闭合的 DST、checksum、exact timezone 与 fail-closed reason 合同
non_goals:
  - 改 configs/compliance/nz-rules-v1.json 或扩展 schemaVersion
  - 生成通知、导入 override UI、持久化排程记录
  - 为未被 schema 要求的输入再增加新验证分支
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.compliance.*"
dod_exit: 0
dod_assert: 非默认配置逐项越过默认边界并驱动 notice min/max、普通/寄宿时窗与 frequency days；权威 JSON 每条规则有正反例；改期排除自身但仍拦真实竞争记录；R3 点名的配置拒绝分支与所有公开集合不可变性均有可证伪测试
review_gate: codex {verdict:pass}
hygiene: 只接住 PR #43 第 2 轮四项 finding；表驱动合并同类拒绝夹具，避免一分支一大段重复测试
doc_sync: 合并后把 TD141 置 paid；随后按原卡 R5 单独登记 configs/compliance FrozenPaths、归档两卡并同步 CLAUDE.md 当前阶段
---

# T4-COMPLIANCE-ENGINE-R3-CLOSURE

## 根因

PR #43 已实现纯 JVM 合规配置加载、checksum override、DST/真实时长边界与阻断 reason，但两轮 R3 后仍缺四类可证伪证据：非默认配置没有跨过默认常量边界；改期输入没有当前记录身份；若干 fail-closed 分支没有负例；公开集合的不可变包装没有 mutation-sensitive 证明。

## 单一产出

1. 用非默认且跨默认边界的夹具分别证明 notice 最小/最大、普通/寄宿时窗、frequency days 真由配置驱动，并对权威 JSON 的每条规则各跑正反例。
2. 给排程记录与待校验请求加入稳定身份；改期排除自身，同物业/同用途的另一条真实记录仍会触发频率阻断。
3. 用表驱动负例覆盖第 2 轮点名的 built-in/schema/日期/时区/sourceRefs/rules/purpose/数值/窗口/UTF-8/unknown-field 拒绝路径；不为测试继续扩验证面。
4. 对 sourceRefs、rules、exemptTypes、validation errors、blocked reasons 做 `MutableList`/`MutableMap` cast 后的替换或写入，要求精确拒绝且原值不变。

## RED-first

先在 PR #43 当前实现上加入四组测试：至少一组非默认边界仍被默认常量放过、包含自身的改期记录自冲突、一个点名拒绝分支缺少预期结果、一个集合可被修改或测试编译暴露缺少身份 API。记录正式 RED 后才修改生产实现。

## 边界

本卡只收口第 2 轮 R3 的四项 finding。法律/产品数值、配置文件、work-check 留门、通知文案和消费者接线均不在本卡；原卡已通过的规则语义不重新设计。
