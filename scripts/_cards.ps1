# T46-W8-CARDSLIB

function Get-FrontMatter($raw) {
  # TD60/TD-123：闭合 `---` 须锚定到整行（其后只允许行尾空白，再接换行或文件末尾）——不锚定时，
  # front-matter 内某行只要「以 --- 开头」（哪怕后面还有文字，非真正的闭合符）就会被非贪婪 .*? 提前
  # 当作闭合命中，导致真正的闭合符之后（其实还在 front-matter 内）的键被切进「正文」而「消失」。
  # TD130（上游 v0.41.0）：开头锚点也接受一个可选的 U+FEFF。经管道取来的卡片文本
  # （git show BASEREF:specs/tasks/CARDID.md）保留文件的 UTF-8 BOM——只有 Get-Content 的文件读取器会剥它——
  # 于是裸 \A--- 锚失配、front-matter 解析成 null、allow_paths 取空，完整式 check-scope 必判
  # [SCOPE-UNDECIDABLE]，并把原因误报成「卡没写 allow_paths」。恰在中断恢复场景失效。
  # 剥在**唯一共享解析器**里，全部调用面一次同愈（绝不逐调用点补，那必漂移）。
  # 码位只写转义形态，源码里没有字面 BOM 字节（L193）。
  $m = [regex]::Match($raw, '(?s)\A\uFEFF?---\r?\n(.*?)\r?\n---[ \t]*(?:\r?\n|\z)')
  if ($m.Success) { return $m.Groups[1].Value }
  return $null
}
function Get-Scalar($fm, $n) {
  $m = [regex]::Match($fm, "(?m)^$([regex]::Escape($n))\s*:\s*(.*?)\s*$")
  if (-not $m.Success) { return $null }
  return $m.Groups[1].Value
}
function Get-UncommentedValue($v) {
  if ($null -eq $v) { return $null }
  return ($v -replace '\s+#.*$', '').Trim()
}
# 数某个 YAML 列表键（如 allow_paths）下的 `-` 列表项数量：从该键行起，到下一个顶层键（行首非空白且含 ':'）止。
function Get-YamlListCount($fm, $key) {
  $lines = $fm -split '\r?\n'
  $in = $false; $n = 0
  foreach ($ln in $lines) {
    if (-not $in) {
      if ($ln -match "^$([regex]::Escape($key))\s*:") { $in = $true }
      continue
    }
    if ($ln -match '^\S' -and $ln -match ':') { break }   # 下一个顶层键 → 列表结束
    if ($ln -match '^\s*-\s+\S') { $n++ }                  # 一个列表项
  }
  return $n
}
# ── 两个列表取值器：差异是**有意设计**，勿当重复代码合并 ────────────────────────────────────
# 用途分工：`Get-YamlListItems` 服务 check-cards 的**校验**（要能「看见」畸形写法才能拒绝它）；
#           `Get-YamlBlockListItems` 服务 ship 范围闸的**执行**（必须窄且 fail-closed）。
#
# 二者**恰有两处**行为差异，逐条列明（勿再写「边界规则一致」——那是错的，且这类假等价声明正是本卡要消灭的东西）：
#   ① 行内 flow `[a, b]`：ListItems 认（故 check-cards 能看见并拒绝它，闸 10d 行内子例）；BlockListItems **一律 0 项**。
#   ② 列表终止条件：ListItems 只在「非缩进**且含冒号**」处 break；BlockListItems 在**任何非缩进行**处 break。
#      差异后果：畸形 front-matter（`allow_paths` → 合法项 → 非缩进**无冒号**垃圾行 → 又一个 `- 项`）在 ListItems 下
#      会把后一项**也吸进列表**；范围闸若用它就会放行一条越界路径（R3 #9 的原始场景）。
#   （列表项形态也随之略有不同：BlockListItems 只认**缩进**项 `^\s+-\s+`，ListItems 认 `^\s*-\s+`。）
#
# 两处差异均为 fail-closed 方向的**收紧**，且 BlockListItems 逐字复刻它替换掉的 task.ps1 手写行走器。
# **不得**合并回 Get-YamlListItems——那会连带放宽 ship 范围闸，或反过来改变 check-cards 的判定面。
# 机检在 selftest 闸 10d(范围闸)：行内→0 项 · 块式→正常取到 · 畸形终止不吸后项 · **且断言 task.ps1 确实接的是本函数**。
function Get-YamlBlockListItems($fm, $key) {
  $items = @(); $in = $false
  foreach ($ln in ($fm -split '\r?\n')) {
    if (-not $in) { if ($ln -match "^$([regex]::Escape($key))\s*:") { $in = $true }; continue }
    if ($ln -match '^\s+-\s+(.+)$') {
      $v = Get-UncommentedValue $Matches[1]
      if ($v) { $items += $v.Trim('"').Trim("'") }
    }
    elseif ($ln -match '^\S') { break }   # 任何非缩进行即列表结束（比 Get-YamlListItems 严，刻意为之）
  }
  return $items
}
# 块式 + 行内 `[a, b]` 皆认。**与 master 逐字等价**（check-cards 的校验行为零变化）——
# 行内形态必须能「看见」才能被 check-cards 明确拒绝（闸 10d 行内子例）。
function Get-YamlListItems($fm, $key) {
  $m = [regex]::Match($fm, "(?m)^$([regex]::Escape($key))\s*:\s*(.*)$")
  if (-not $m.Success) { return @() }
  $inline = Get-UncommentedValue $m.Groups[1].Value
  if ($inline -match '^\[(.*)\]$') {
    return @($Matches[1] -split ',' | ForEach-Object { $_.Trim().Trim('"').Trim("'") } | Where-Object { $_ })
  }
  $items = @(); $in = $false
  foreach ($ln in ($fm -split '\r?\n')) {
    if (-not $in) { if ($ln -match "^$([regex]::Escape($key))\s*:") { $in = $true }; continue }
    if ($ln -match '^\S' -and $ln -match ':') { break }
    if ($ln -match '^\s*-\s+(.+)$') {
      $v = Get-UncommentedValue $Matches[1]
      if ($v) { $items += $v.Trim('"').Trim("'") }
    }
  }
  return $items
}

# The same narrow quoted-list grammar serves acceptance and optional requirements.
# Only front matter is accepted; callers keep their own numbering/reference policy.
function Get-CardQuotedList([string]$fm, [string]$Key, [string]$Prefix) {
  $lines = @($fm -split '\r?\n'); $keyLine = -1; $keyCount = 0; $bad = 0
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match "^$([regex]::Escape($Key))\s*:(?<sep>[ \t]*)(?<inline>.*)$") {
      $keyCount++
      if ($keyLine -lt 0) {
        $keyLine = $i; $inline = $Matches['inline']
        if (($Matches['sep'].Length -eq 0 -and $inline.StartsWith('#')) -or
            ($inline -replace '^\s*#.*$', '' -replace '\s+#.*$', '').Trim()) { $bad = 1 }
      }
    }
  }
  if ($keyLine -lt 0) { return [pscustomobject]@{ Present=$false; InvalidEntry=0; Items=@() } }
  if ($keyCount -gt 1) { $bad = 1 }
  $items = @(); $entry = 0; $indent = $null
  # Decode the YAML escapes already accepted by the grammar below. Decoding makes
  # whitespace-only descriptions and escaped [R1] citations behave as their text.
  $escapes = [System.Collections.Generic.Dictionary[string,int]]::new([StringComparer]::Ordinal)
  $keys = @(' ', '0', 'a', 'b', 't', 'n', 'v', 'f', 'r', 'e', '"', '/', 'N', '_', 'L', 'P', '\')
  $codes = @(32, 0, 7, 8, 9, 10, 11, 12, 13, 27, 34, 47, 133, 160, 8232, 8233, 92)
  for ($i = 0; $i -lt $keys.Count; $i++) { $escapes.Add($keys[$i], $codes[$i]) }
  for ($i = $keyLine + 1; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    if ($line -match '^[^\s#].*?:') { break }
    if ([string]::IsNullOrWhiteSpace($line) -or $line -match '^\s*#') { continue }
    $entry++
    $shape = [regex]::Match('', '(?!)')
    if ($line -match '^(?<indent> +)- +(?<value>.+?)\s*$') {
      $thisIndent = $Matches['indent']; $value = $Matches['value']
      if ($null -eq $indent) { $indent = $thisIndent }
      if ($thisIndent -ceq $indent) {
        $shape = [regex]::Match($value, '^"(?<label>' + [regex]::Escape($Prefix) + '[0-9]+)\s+(?<text>(?:[^"\\]|\\(?:[ 0abtnvfre"/N_LP\\]|x[0-9A-Fa-f]{2}|u(?![dD][89A-Fa-f])[0-9A-Fa-f]{4}|U(?:0000(?![dD][89A-Fa-f])[0-9A-Fa-f]{4}|00(?:0[1-9A-Fa-f]|10)[0-9A-Fa-f]{4})))+)"(?:[ \t]+#.*|[ \t]*)$')
      }
    }
    if (-not $shape.Success) { if ($bad -eq 0) { $bad = $entry }; continue }
    $text = [regex]::Replace($shape.Groups['text'].Value, '\\(x[0-9A-Fa-f]{2}|u[0-9A-Fa-f]{4}|U[0-9A-Fa-f]{8}|.)', {
      param($escape)
      $value = $escape.Groups[1].Value
      $code = if ($value.Length -gt 1) { [Convert]::ToInt32($value.Substring(1), 16) } else { $escapes[$value] }
      [char]::ConvertFromUtf32($code)
    })
    if ([string]::IsNullOrWhiteSpace($text)) { if ($bad -eq 0) { $bad = $entry }; continue }
    $items += [pscustomobject]@{ Entry=$entry; Label=$shape.Groups['label'].Value; Text=$text }
  }
  if ($entry -eq 0 -and $bad -eq 0) { $bad = 1 }
  return [pscustomobject]@{ Present=$true; InvalidEntry=$bad; Items=$items }
}


function Split-TdRow([string]$line) {
  ($line.Trim().Trim('|') -split '(?<!\\)\|') | ForEach-Object { $_.Trim() }
}
