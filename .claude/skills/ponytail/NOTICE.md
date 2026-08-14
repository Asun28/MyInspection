# Vendored: ponytail (skills only)

Source: https://github.com/DietrichGebert/ponytail (MIT, see LICENSE).
Vendored on-demand into this scaffold as a **design-altitude** YAGNI lens
(paired with the harness `/simplify` command at the code-mechanics altitude).

Deliberately NOT vendored:
- the always-on Node hooks (`hooks/*.js`, SessionStart/UserPromptSubmit) — kept
  on-demand instead of persistent, no new runtime surface in downstream projects;
- `ponytail-debt` skill — redundant with `specs/tech-debt-tracker.md`;
- non-Claude agent rule dirs (`.cursor`, `.windsurf`, `.clinerules`, etc.).

Vendored: 2026-07-06 (re-vendored from upstream tag `v4.8.4`, commit
`bc9ee949d5f439e8b9f3bb92c6d6d3d1e6ebd324`; `SKILL.md` verified byte-identical to
that commit). First tracked in this repo at commit e454a93 (upstream 4.7.0,
2026-06-22); 4.8.4 is the upstream author's recalibration tested against current-gen
models (Haiku 4.5 / Sonnet / Opus).
Upstream plugin version at vendoring time: 4.8.4.
