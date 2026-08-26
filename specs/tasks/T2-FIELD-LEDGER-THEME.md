---
id: T2-FIELD-LEDGER-THEME
title: Field Ledger Material 3 主题契约：light/dark token 与语义状态角色
depends_on: [T1-SPIKE-PLATFORM]
status: todo
branch: T2-FIELD-LEDGER-THEME
worktree: C:\wt\T2-FIELD-LEDGER-THEME
allow_paths:
  - android/app/build.gradle.kts
  - android/app/src/main/kotlin/nz/myinspection/app/ui/theme/
  - android/app/src/test/kotlin/nz/myinspection/app/ui/theme/
forbid:
  - 改 MainActivity、capture 组件、相机、导航或 :core
  - 动态壁纸取色改变 OK/attention/error/privacy 的稳定语义
  - 新增外部字体、图标或未在版本目录中钉住的依赖
non_goals:
  - 把主题接到一次性 skeleton 或真实 capture root（T2-CAPTURE-UI）
  - inspection item、evidence rail、按钮与 sheet 的组件实现
  - 间距 API、动画系统或平板布局
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: light/dark ColorScheme、Typography、Shapes、ThemeMode resolver、五个语义角色与状态层单测全绿；每个渲染 token 有 targetSurface/minRatio 元数据，低对比/漏元数据/未声明绑定变异均命中具名 Build Fail；动态色恒关闭；assembleDebug 绿；无 capture/root 行为变化
review_gate: codex {verdict:pass}
hygiene: 每个 token 只由一条最小断言钉住，删除重复映射测试（R4）
doc_sync: T2-CAPTURE-UI 标记主题前置已满足；若实现需偏离 context/DESIGN.md，先同步设计理由（R5）
---

# Theme Task Card — T2-FIELD-LEDGER-THEME

## Output boundary

This card converts `context/DESIGN.md` into compile-time Compose Material 3 theme contracts. It does not mount the theme in a production page, implement a theme settings UI, or modify capture behaviour.

## Theme mode state

```kotlin
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ThemeResolutionInput(
    val persistedMode: String?,
    val systemIsDark: Boolean?
)

enum class ResolvedTheme { LIGHT, DARK }
```

Resolution order is fixed:

```text
parse persistedMode
  invalid or null → ThemeMode.SYSTEM and persist "SYSTEM"

ThemeMode.LIGHT  → ResolvedTheme.LIGHT
ThemeMode.DARK   → ResolvedTheme.DARK
ThemeMode.SYSTEM + systemIsDark=true  → ResolvedTheme.DARK
ThemeMode.SYSTEM + systemIsDark=false → ResolvedTheme.LIGHT
ThemeMode.SYSTEM + systemIsDark=null  → ResolvedTheme.LIGHT
```

| Event | Result |
| --- | --- |
| First launch | Persist `SYSTEM`; resolve from system |
| User selects Light | Persist `LIGHT`; ignore later system theme changes |
| User selects Dark | Persist `DARK`; ignore later system theme changes |
| User selects System | Persist `SYSTEM`; apply the current system theme immediately |
| System theme changes while mode is `SYSTEM` | Recompose to the matching scheme without recreating domain state |
| System theme changes while mode is `LIGHT` or `DARK` | No app theme change |
| Persisted value is unknown | Replace with `SYSTEM`, emit one diagnostic, continue |

Dynamic wallpaper color is always `false`. No API level, device brand, wallpaper, or battery mode changes evidence semantics.

## Semantic role mapping

| Semantic role | Light container / content | Dark container / content | Evidence rail | Icon + text |
| --- | --- | --- | --- | --- |
| `OK` / `COMPLETE` | `primary-container / on-primary-container` | `dark.primary-container / dark.on-primary-container` | Primary | Check + `Complete` |
| `ATTENTION` / `MISSING_REQUIRED` | `tertiary-container / on-tertiary-container` | `dark.tertiary-container / dark.on-tertiary-container` | Tertiary | Exclamation + exact missing reason |
| `CRITICAL` / `BLOCKED` | `error-container / on-error-container` | `dark.error-container / dark.on-error-container` | Error | Octagon + blocking reason |
| `NOT_APPLICABLE` / `OPTIONAL` | `surface-container-high / on-surface-variant` | `dark.surface-container-high / dark.on-surface-variant` | Neutral | Dash + `Not applicable` or `Optional` |
| `PRIVACY` | `privacy-container / on-privacy-container` | `dark.privacy-container / dark.on-privacy-container` | Privacy violet | Shield + inclusion state |
| `FOCUS` | `primary` ring on current surface | `dark.primary` ring on current surface | Not applicable | 3dp ring + platform focus semantics |

The status, photo, and note evidence-rail segments consume this table. Components do not select raw hex values and do not infer semantic states from display labels.

## Interaction-state mapping

| State | Token operation | Behaviour |
| --- | --- | --- |
| `ENABLED` | Base semantic container/content | Accept input |
| `PRESSED` | Add current content color at `0.12` state-layer opacity | Feedback starts within `100ms`; bounds do not change |
| `FOCUSED` | Add `0.12` state layer + external 3dp primary ring | Keep semantic base color |
| `BUSY` | Keep base color; replace leading icon with 18dp progress | Reject duplicate activation |
| `DISABLED` | Content alpha `0.38`; container alpha `0.12` | Reject input; adjacent copy names prerequisite |

## Fallback and failure rules

| Failure | Required result |
| --- | --- |
| Invalid persisted theme mode | Resolve as `SYSTEM`, overwrite invalid value, log code `THEME_MODE_INVALID` |
| System theme unavailable | Resolve `LIGHT` |
| Missing light or dark token | Build fails with `THEME_TOKEN_MISSING`; no runtime color fallback |
| Contrast below token metadata `minRatio` | Build fails with `THEME_CONTRAST_FAIL` |
| Component binds a token to an unapproved surface | Build fails with `THEME_PAIR_UNDECLARED` |
| Unknown evidence semantic enum | Render `CRITICAL` tokens, octagon icon, text `Unknown evidence state`; emit `THEME_SEMANTIC_UNKNOWN` |
| Camera preview makes controls unreadable | Use fixed `#000000` 64% scrim + `#FFFFFF`; preview-derived color is forbidden |
| Dynamic-color API is available | Ignore it; use Field Ledger scheme |

## RED-first and verification

1. Add `FieldLedgerThemeContractTest` before theme implementation.
2. The first RED references the absent `fieldLedgerLightColorScheme`, `fieldLedgerDarkColorScheme`, and `resolveThemeMode`.
3. Tests assert every approved hex token, every foreground/background ratio from `context/DESIGN.md`, the resolution table above, state-layer constants, and dynamic color disabled.
4. Tests delete one metadata record and mutate one dark foreground token; each mutation must produce the named build failure.
5. `T2-CAPTURE-UI` mounts the finished theme and removes the skeleton. This card never edits `MainActivity` or capture components.
