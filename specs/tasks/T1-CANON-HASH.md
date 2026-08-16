---
id: T1-CANON-HASH
title: canonical JSON 序列化 + SHA-256 + 黄金向量（★冻结点）
depends_on: [T1-SCHEMA-CORE]
parallelizable_with: [T1-TEMPLATE-ENGINE]
status: todo
branch: T1-CANON-HASH
worktree: C:\wt\T1-CANON-HASH
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/canon/
  - android/core/src/test/kotlin/nz/myinspection/core/canon/
forbid:
  - 碰 db/template 包（并行卡领地）与构建文件
  - 哈希 PDF 字节 / 把 updated_at、文件路径、UI 态、LLM 建议纳入哈希域（ADR-0003 明确排除）
non_goals:
  - finalize 事务本身（T3-FINALIZE 消费本卡产出）
  - 备份 manifest 组装（T5-BACKUP-FORMAT 复用本序列化器）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.canon.*"
dod_exit: 0
dod_assert: 黄金向量测试全绿（固定输入→固定 canonical 串→固定 SHA-256 十六进制）；键乱序输入产同一哈希；NFC 归一生效（组合字符两种编码产同一哈希）；排除域字段变化不改哈希
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: CLAUDE.md 当前阶段；合并后把 core/canon/ 登记进 FrozenPaths（R5）
---

# T1-CANON-HASH

## 产出
`core/canon` 包：canonical JSON 序列化器（RFC 8785 风格）+ SHA-256 哈希 + 巡检快照投影函数 + 黄金向量测试。**这是 finalize 哈希与备份 manifest 的共用地基，合并即冻结。**

## 上下文包（执行模型必读）
- **canonical 规则（ADR-0003 冻结版）**：kotlinx.serialization JsonObject 手工遍历序列化——对象键按 UTF-16 码位排序；字符串先 NFC 归一（java.text.Normalizer）；数值只允许整数（时间一律 epoch 毫秒 Long）；数组顺序 = 显式序数（模板内顺序）再按 UUID 字典序；无空白、无转义歧义（照 RFC 8785 转义最小集）。
- **哈希域（inspection 快照投影，字段全单）**：inspection（id/type/tenancy_id/scheduled_at/finalized_at/previous_inspection_id/baseline_inspection_id）、property 快照（id/address/kind/is_boarding_house）、tenancy 快照（id/start/end；**不含租客联系方式**——保留期清理不得破坏哈希可复验性）、template（id/type/version/content_hash）、items[]（stable_id/status/note/wear_or_damage）按模板序、photos[]（content_hash/source/exif_time_ms/room 级标记）按 UUID 序、audios[]（content_hash）按 UUID 序。**排除域**：updated_at/deleted_at、rel_path、UI 态、PDF 元数据、LLM 建议、supplement（后置——补充说明链另行锚定，见下）。
- Supplement 哈希链：`chain_hash(n) = SHA-256(canonical(supplement_n) + prev_hash)`，`prev_hash(1) = inspection.data_hash`。本卡实现链函数 + 测试；写库时机归 T3-FINALIZE。
- API 形态：纯函数 `fun canonicalJson(snapshot: InspectionSnapshot): String` + `fun sha256Hex(s: String): String` + `fun supplementChainHash(prev: String, s: SupplementSnapshot): String`。输入是 model 层不可变数据类（T1-SCHEMA-CORE 已定义），不直接依赖 DB。
- 黄金向量：至少 3 组手工固定样例（含中文备注、组合字符 é 两种编码、乱序键构造），期望哈希写死在测试里——**评审会拿「删除单句变异」验测试真咬合**。

## 验收 / 执行建议
dod 见 front-matter。首选 DeepSeek V4 Pro · high；备选 Opus 5；Terra 对黄金向量独立复算一遍（交叉复核）。难度 H（契约卡）。

## 执行记录（R2–R4，供评审）
- 2026-08-16 R2：RED 先行——4 文件 14 测试对 TODO 桩全红（证据 `.review/T1-CANON-HASH.red`）→ 实现后 GREEN（DoD 退出 0，14/14）。
- **黄金向量独立性**：期望 canonical 串与 SHA-256 由独立 Python 实现（json.dumps sort_keys + NFC 归一 + hashlib sha256）预先计算后写死进测试；Kotlin 实现首跑即与之全量吻合。被断言字符串中组合字符/控制符一律 \uXXXX ASCII 转义（L165）。
- **R4 变异证明 7/7 全中**（判据分类器：非零退出 且 命中指定失败集 且 判别测试保持绿；逐枚还原后文件 SHA256 与基线一致）：
  M1 去 NFC(值)→3 项 NFC 测试红 · M2 去 NFC(键)→键冲突测试红 · M3 去键排序→排序+3 黄金向量红 ·
  M4 链序对调→链黄金向量红 · M5 投影键改名→向量 1/2 红（向量 3 空 items 保持绿=判别项）·
  M6 投影泄漏 updated_at→排除域+3 向量红 · M7 去整数闸→拒非整数红（Long 极值保持绿=判别项）。
- **hygiene 结论**：14 测试各有独立击杀变异，无 mutation-survivor 冗余可剪。
- 设计取值备注：tenancy 投影键名按卡文哈希域清单取 `start`/`end`（非 start_ms/end_ms——photos 的 `exif_time_ms` 卡文即带单位，两处均照卡文原文）；可空字段显式序列化为 null（缺席与为空必须可区分，形状唯一）。
