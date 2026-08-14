---
id: T3-PDF-RENDERER
title: PdfDocument 渲染器：DocumentPlan → 双版本 PDF（CJK 字体 + 逐页内存策略）
depends_on: [T3-REPORT-COMPOSER]
parallelizable_with: [T3-HISTORY-COMPARE, T5-BACKUP-IO]
status: todo
branch: T3-PDF-RENDERER
worktree: C:\wt\T3-PDF-RENDERER
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/export/pdf/
  - android/app/src/main/assets/fonts/
  - android/app/src/test/kotlin/nz/myinspection/app/export/pdf/
forbid:
  - 布局判断混进渲染器（plan 说画哪就画哪；发现布局缺陷回 T3-REPORT-COMPOSER 修）
  - 引入 PDF 三方库（iText=AGPL 禁；平台 PdfDocument 足够，ADR-0003）
non_goals:
  - 报告结构/分页（composer 已定）；分享/云盘上传（生成后落私有目录 + SAF 另存交 T5-BACKUP-IO 同类机制）
dod_command: cmd /c android\gradlew.bat --offline --no-daemon -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat --offline --no-daemon -q :core:test --tests "nz.myinspection.core.report.*"
dod_exit: 0
dod_assert: assembleDebug 绿、composer 黄金测试仍绿；真机出两版 PDF（房东/房客）人工核：中文字形完整（DroidSansFallback 生效）、80 照 fixture 内存不 OOM（spike 参数内）、附录图编号回链正确、页脚哈希与 DB data_hash 一致——核验记录附 PR
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T3-PDF-RENDERER

## 产出
`app/export/pdf`：DocumentPlan → PDF 文件的渲染器（真 Paint 量宽实现 TextMeasurer + 位图槽渲染）+ 字体资产。

## 上下文包（执行模型必读）
- 单位换算：plan 用 mm；PdfDocument PageInfo 用 1/72 inch 点——A4 = 595×842pt，mm→pt = ×72/25.4。
- 字体：assets/fonts/DroidSansFallback.ttf（Apache-2.0，随卡引入并在 PR 里附许可来源链接，check-licenses 须绿）；Typeface.createFromAsset；en 可用平台 sans，zh 一律 fallback 字体（composer 的双语对块标记语言）。
- **内存策略（spike ④ 实测参数为准）**：一次只 startPage 一页；该页 ImageSlot 逐个 BitmapFactory bounds→inSampleSize（内联缩略目标 ~150dpi、附录 ~200dpi，长边帽 2048px）→draw→recycle；页 finish 后再下一页；写 FileOutputStream 流式。
- TextMeasurer 实现：Paint.measureText 按块字号/字体；量宽结果供 composer 复排？——**不**：plan 已定稿（JVM 假 measurer 排的版），渲染器只按块坐标画、超宽做尾部省略号并记 warning 日志（布局缺陷回 composer 修，本卡不自行重排——单一职责红线）。
- 输出落位：reports/{propertyId}/{inspectionId}-{audience}.pdf（路径派生走 core/media 同一派生点扩展）；两版一次生成。
- 免责声明双语固定文案（需求 §8 [定]）由 composer 提供，渲染照画。

## 验收 / 执行建议
dod 见 front-matter。首选 Sonnet 5 · max；备选 Opus 5。难度 H。
