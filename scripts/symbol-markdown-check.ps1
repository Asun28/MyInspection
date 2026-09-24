#requires -Version 7
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$helperPath = Join-Path $PSScriptRoot '_symbol-markdown.ps1'
$cases = [Collections.Generic.List[object]]::new()
function Add-Case($Name, $Operation, $Text, $Want, $ErrorPrefix = '', $ExactBody = $null) {
    $cases.Add([pscustomobject]@{ Name=$Name; Operation=$Operation; Text=$Text; Want=$Want; ErrorPrefix=$ErrorPrefix; ExactBody=$ExactBody })
}
$f = ([string][char]96)*3
$label = 'powershell symbol-contract'
$block = $f+$label+"`nreturn 42`n"+$f
foreach ($character in @([string][char]96, '~')) {
    foreach ($length in @(3,4,6)) {
        $delimiter = $character*$length
        Add-Case "closed-$character-$length" block ($delimiter+$label+"`nreturn 42`n"+$delimiter) 'return 42'
    }
}
Add-Case 'crlf-content-offsets' block ($block.Replace("`n","`r`n")) 'return 42'
Add-Case 'cr-content-offsets' block ($block.Replace("`n","`r")) 'return 42' '' "return 42`r"
Add-Case 'mixed-content-offsets' block ("前言`r`n`r"+$f+$label+"`rfirst`nsecond`r`n"+$f) '' '' "first`nsecond`r`n"
Add-Case 'mixed-opening-crlf-closing-cr' block ($f+$label+"`r`nreturn 42`r"+$f) '' '' "return 42`r"
Add-Case 'cr-empty-block' block ($f+$label+"`r"+$f) '' '' ''
Add-Case 'longer-closing-fence' block ($block+([string][char]96)) 'return 42'
Add-Case 'indented-fence-retains-original-body' block ("  "+$f+$label+"`n  return 42`n  "+$f) '  return 42'
Add-Case 'code-comment-literal-not-markup' block ($f+$label+"`n# <!-- not markup`n"+$f) '# <!-- not markup'
Add-Case 'missing-block' block '# Heading' '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'duplicate-block' block ($block+"`n`n"+$block) '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'label-is-case-sensitive' block ($block.Replace('symbol-contract','SYMBOL-CONTRACT')) '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'label-not-substring' block ($block.Replace('symbol-contract','symbol-contract-extra')) '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'commented-block' block ("<!--`n"+$block+"`n-->") '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'outer-backtick-fence' block (([string][char]96)*5+"text`n"+$block+"`n"+([string][char]96)*5) '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'outer-tilde-fence' block ("~~~~text`n"+$block+"`n~~~~") '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'quoted-block' block (($block -split "`n" | ForEach-Object { '> '+$_ }) -join "`n") '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'indented-code-block' block (($block -split "`n" | ForEach-Object { '    '+$_ }) -join "`n") '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'list-nested-block' block ("- item`n`n"+(($block -split "`n" | ForEach-Object { '    '+$_ }) -join "`n")) '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'closed-hidden-plus-real' block ("<!--`n"+$block+"`n-->`n`n"+$block) 'return 42'
foreach ($badFence in @($f,'~~~~')) {
    Add-Case "unclosed-$badFence" block ($badFence+$label+"`nreturn 42") '' 'SYMBOL-MARKDOWN-CLOSURE:'
    Add-Case "mismatched-$badFence" block ($badFence+$label+"`nreturn 42`n"+ $(if($badFence -eq $f){'~~~'}else{$f})) '' 'SYMBOL-MARKDOWN-CLOSURE:'
}
Add-Case 'short-closing-fence' block (([string][char]96)*4+$label+"`nreturn 42`n"+$f) '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'closing-fence-with-text' block ($block+' trailing') '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'unclosed-comment-before-block' block ("<!--`n"+$block) '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'unclosed-comment-after-block' block ($block+"`n`n<!--") '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'unclosed-inline-comment' visible 'before <!-- unfinished' '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'nested-unclosed-fence' visible ("> "+$f+"ps`n> unfinished") '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'plain-headings-and-table' visible "## Real`n`n| A | B |`n|---|---|`n| one | two |" '## Real|| A | B |||---|---||| one | two |'
Add-Case 'closed-comment-masked' visible "## Real`n`n<!--`n## Fake`n-->" '## Real'
Add-Case 'tilde-fenced-heading-masked' visible "## Real`n`n~~~~`n## Fake`n~~~~" '## Real'
Add-Case 'backtick-fenced-heading-masked' visible ("## Real`n`n"+$f+"`n## Fake`n"+$f) '## Real'
Add-Case 'quoted-heading-masked' visible "## Real`n`n> ## Fake" '## Real'
Add-Case 'list-heading-masked' visible "## Real`n`n- ## Fake" '## Real'
Add-Case 'indented-heading-masked' visible "## Real`n`n    ## Fake" '## Real'
Add-Case 'html-block-masked' visible "## Real`n`n<div>`n## Fake`n</div>" '## Real'
Add-Case 'inline-comment-masked' visible 'before <!-- hidden --> after' 'before                 after'
Add-Case 'inline-code-comment-literal-retained' visible ('before '+[char]96+'<!-- literal'+[char]96+' after') ('before '+[char]96+'<!-- literal'+[char]96+' after')
Add-Case 'inline-html-excludes-containing-block' visible "## Real`n`nbefore <span hidden>Fake</span> after" '## Real'
Add-Case 'closed-inline-comment-then-unclosed' visible 'before <!-- closed --> after <!-- open' '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'escaped-comment-opener-is-literal' visible 'before \<!-- literal' 'before \<!-- literal'
Add-Case 'empty-input-projection' visible '' ''
Add-Case 'empty-input-no-block' block '' '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'empty-block-content' block ($f+$label+[char]10+$f) ''
Add-Case 'unicode-prefix-content-span' block ('前言'+[char]10+[char]10+$block) 'return 42'
Add-Case 'inline-comment-in-heading' visible '## Visible <!-- hidden -->' '## Visible'
Add-Case 'inline-html-in-heading' visible '## Visible <span hidden>hidden</span>' ''
Add-Case 'inline-comment-in-table' visible ('| A | B |'+[char]10+'|---|---|'+[char]10+'| x <!-- y --> | z |') ('| A | B |||---|---||| x '+(' '*10)+' | z |')
Add-Case 'inline-html-excludes-table' visible ('| A | B |'+[char]10+'|---|---|'+[char]10+'| x <span>y</span> | z |') ''
Add-Case 'even-backslashes-do-not-escape-comment' visible 'before \\<!-- open' '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'odd-backslashes-escape-comment' visible 'before \\\<!-- literal' 'before \\\<!-- literal'
Add-Case 'closed-then-open-comment-before-block' block ("<!-- closed --> <!-- open`n"+$block) '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'closed-then-open-comment-before-heading' visible "<!-- closed --> <!-- open`n## Fake" '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'multiline-inline-comment' visible "before <!-- hidden`n## Fake`n--> after" 'before|after'
Add-Case 'closed-then-multiline-comment-hides-block' block ("<!-- closed --> <!-- open`n"+$block+"`n-->") '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'closed-then-multiline-comment-hides-heading' visible "<!-- closed --> <!-- open`n## Fake`n-->" ''
Add-Case 'comment-literal-code-closer' visible ('before '+[char]96+'<!--'+[char]96+' after') ('before '+[char]96+'<!--'+[char]96+' after')
Add-Case 'bullet-clauses-opt-in' visible-list "- Counts use complete language`n- Do give every status a cue" '- Counts use complete language|- Do give every status a cue'
Add-Case 'numbered-clauses-opt-in' visible-list "1. First`n2. The control carries a name" '1. First|2. The control carries a name'
Add-Case 'list-heading-not-admitted' visible-list "- item`n`n  ## Fake`n`n  kept clause" '- item|kept clause'
Add-Case 'list-code-not-admitted' visible-list ("- item`n`n  "+$f+"`n  ## Fake`n  "+$f+"`n`n  kept clause") '- item|kept clause'
Add-Case 'list-quote-not-admitted' visible-list "- item`n`n  > hidden`n`n  kept clause" '- item|kept clause'
Add-Case 'list-inline-comment-masked' visible-list '- Counts <!-- hidden --> stay' '- Counts                 stay'
Add-Case 'list-inline-html-not-admitted' visible-list "- okay`n- before <span hidden>Fake</span> after" '- okay'
Add-Case 'default-numbered-list-still-hidden' visible "1. First`n2. Second" ''
Add-Case 'cr-list-html-keeps-prior-content' visible-list "## Real`r`r- okay`r- before <span hidden>Fake</span> after" '## Real|- okay'
Add-Case 'mixed-list-html-keeps-prior-content' visible-list "## Real`n`n- okay`r- before <span hidden>Fake</span> after`r`n- last" '## Real|- okay|- last'
Add-Case 'inline-html-attribute-comment-literal' visible 'before <span title="<!--">hidden</span> after' ''
Add-Case 'block-html-attribute-comment-literal' visible "<div title='<!--'>hidden</div>`n`n## Real" '## Real'
Add-Case 'html-attribute-before-contract' block ("<div title='<!--'>hidden</div>`n`n"+$block) 'return 42'
Add-Case 'inline-html-attribute-before-contract' block ('before <span title="<!--">hidden</span> after'+"`n`n"+$block) 'return 42'
Add-Case 'html-real-comment-still-closes' block ("<div title='<!--'>`n<!-- open`n</div>`n`n"+$block) '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'html-real-comment-hides-contract' block ("<div title='<!--'>`n<!-- open`n</div>`n`n"+$block+"`n-->") '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'quoted-angle-then-comment-literal' visible 'before <span title="> <!--">hidden</span> after' ''
Add-Case 'multiline-html-attribute-before-contract' block ("<div title='line`r<!--'>hidden</div>`r`r"+$block) 'return 42'
Add-Case 'real-comment-after-inline-html' visible 'before <span title="<!--">hidden</span> <!-- open' '' 'SYMBOL-MARKDOWN-CLOSURE:'
foreach ($quotePair in @(@('"','"'),@("'","'"),@('(',')'))) {
    $link='[label](url '+$quotePair[0]+'<!-- literal'+$quotePair[1]+')'
    Add-Case ("link-title-"+$quotePair[0]) block ($link+"`n`n"+$block) 'return 42'
    Add-Case ("link-title-projection-"+$quotePair[0]) visible $link ('[label]('+(' '*18)+')')
}
Add-Case 'image-title-before-contract' block ('![alt](image.png "<!-- literal")'+"`n`n"+$block) 'return 42'
Add-Case 'image-title-projection' visible '![alt](image.png "<!-- literal")' ('![alt]('+(' '*24)+')')
Add-Case 'reference-title-before-contract' block ('[ref]: /url "<!-- literal"'+"`n`n"+$block) 'return 42'
Add-Case 'reference-title-projection' visible ('[ref]: /url "<!-- literal"'+"`n`n## Real") '## Real'
Add-Case 'multiline-reference-title' block ("[ref]: /url`r`n  "+'"<!-- literal"'+"`r`n`r`n"+$block) 'return 42' '' "return 42`n"
Add-Case 'link-destination-comment-literal' block ('[label](path<!--literal)'+"`n`n"+$block) 'return 42'
Add-Case 'link-destination-projection' visible '[label](path<!--literal)' ('[label]('+(' '*15)+')')
Add-Case 'real-comment-after-title' block ('[label](url "<!-- literal") <!-- open'+"`n`n"+$block) '' 'SYMBOL-MARKDOWN-CLOSURE:'
Add-Case 'real-comment-in-link-label' visible '[a <!-- hidden --> b](url "title")' ('[a '+(' '*15)+' b]('+(' '*11)+')')
Add-Case 'comment-hides-title-and-contract' block ("<!--`n"+'[label](url "<!-- literal")'+"`n`n"+$block+"`n-->") '' 'SYMBOL-MARKDOWN-BLOCK:'
Add-Case 'used-reference-retains-visible-prefix' visible ("[x][ref]`n`n"+'[ref]: /url "title"') '[x][   ]'
Add-Case 'collapsed-reference-keeps-label' visible ("[ref][]`n`n"+'[ref]: /url "title"') '[ref][]'
Add-Case 'shortcut-reference-keeps-label' visible ("[ref]`n`n"+'[ref]: /url "title"') '[ref]'
Add-Case 'literal-reference-key-before-contract' block ("[x][<!-- literal]`n`n[<!-- literal]: /url`n`n"+$block) 'return 42'
Add-Case 'literal-reference-key-projection' visible "[x][<!-- literal]`n`n[<!-- literal]: /url" ('[x]['+(' '*12)+']')
Add-Case 'literal-shortcut-label-is-not-comment' visible "[<!-- literal]`n`n[<!-- literal]: /url" '[<!-- literal]'
Add-Case 'literal-collapsed-label-is-not-comment' visible "[<!-- literal][]`n`n[<!-- literal]: /url" '[<!-- literal][]'
Add-Case 'literal-inline-link-label-is-not-comment' visible '[<!-- literal](url)' ('[<!-- literal]('+(' '*3)+')')
Add-Case 'reference-definition-does-not-mask-prior-heading' visible "## Real`n`n[ref]: /url" '## Real'

# These literals are non-comment HTML tokens; bypassing their lexer path must
# reject a valid later contract, while a real comment after the token still closes.
$rawHtmlCases = @(
    @('processing-instruction', '<?x <!-- ?>', '<?'),
    @('declaration', '<!A <!-- >', '<!A'),
    @('cdata', '<![CDATA[<!--]]>', '<![CDATA[')
)
foreach ($rawHtmlCase in $rawHtmlCases) {
    $name = $rawHtmlCase[0]; $rawHtml = $rawHtmlCase[1]
    Add-Case ("raw-html-$name-contract") block ($rawHtml+"`n`n"+$block) 'return 42'
    Add-Case ("raw-html-$name-projection") visible ($rawHtml+"`n`n## Real") '## Real'
    Add-Case ("raw-html-$name-real-comment") block ($rawHtml+"`n<!-- open`n`n"+$block) '' 'SYMBOL-MARKDOWN-CLOSURE:'
}

function Invoke-Suite([string]$Source) {
    $module = New-Module -ScriptBlock ([scriptblock]::Create($Source))
    $failures = [Collections.Generic.List[string]]::new()
    foreach ($case in $cases) {
        try {
            $result = & $module {
                param($c,$blockLabel)
                if ($c.Operation -eq 'block') { Get-SymbolMarkdownBlock -Text $c.Text -Label $blockLabel }
                elseif ($c.Operation -eq 'visible-list') { Get-SymbolMarkdownVisible -Text $c.Text -IncludeListText }
                else { Get-SymbolMarkdownVisible -Text $c.Text }
            } $case $label
            if ($case.ErrorPrefix) { $failures.Add($case.Name+': expected '+$case.ErrorPrefix); continue }
            if ($case.Operation -eq 'block') {
                $ending = if ($case.Text.Contains("`r`n")) { "`r`n" } else { "`n" }
                $body = if ($case.Want.Length) { $case.Want+$ending } else { '' }
                if ($null -ne $case.ExactBody) { $body=$case.ExactBody }
                if ($result.Value -cne $body) { $failures.Add($case.Name+': wrong content') }
                if ($result.Index -lt 0 -or $result.Length -lt 0 -or $case.Text.Substring($result.Index,$result.Length) -cne $result.Value) {
                    $failures.Add($case.Name+': incorrect original content span')
                }
            } else {
                if ($result.Length -ne $case.Text.Length) { $failures.Add($case.Name+': projection length changed') }
                for ($i=0; $i -lt $case.Text.Length; $i++) {
                    if ($case.Text[$i] -in @([char]10,[char]13) -and $result[$i] -cne $case.Text[$i]) {
                        $failures.Add($case.Name+': newline offset changed'); break
                    }
                }
                $actual = (@($result -split '\r\n|\r|\n' | ForEach-Object {$_.Trim()} | Where-Object {$_}) -join '|')
                if ($actual -cne $case.Want) { $failures.Add($case.Name+": expected '$($case.Want)', got '$actual'") }
            }
        } catch {
            if (-not $case.ErrorPrefix -or -not $_.Exception.Message.StartsWith($case.ErrorPrefix,[StringComparison]::Ordinal)) {
                $failures.Add($case.Name+': unexpected failure: '+$_.Exception.Message)
            }
        }
    }
    Remove-Module -ModuleInfo $module -ErrorAction SilentlyContinue
    return $failures.ToArray()
}
if (-not (Test-Path -LiteralPath $helperPath -PathType Leaf)) {
    Write-Host 'SYMBOL-MARKDOWN-TEST FAIL: production helper unavailable; visible-contract behavior is not implemented'
    exit 1
}
$source = (Get-Content -LiteralPath $helperPath -Raw).Replace("`r`n","`n")
$errors = @(Invoke-Suite $source)
if ($errors.Count) { $errors | ForEach-Object { Write-Host "SYMBOL-MARKDOWN-TEST FAIL: $_" }; exit 1 }
# Each mutation names a specific real fixture. Parse errors and absent/ambiguous targets
# invalidate the mutation setup; they are not counted as killed behavioral mutations.
$mutations = @(
    @('fence-closure', 'if (-not $hidden -and $node -is [Markdig.Syntax.FencedCodeBlock] -and', 'if ($false -and $node -is [Markdig.Syntax.FencedCodeBlock] -and', 'unclosed-'),
    @('comment-closure', "throw 'SYMBOL-MARKDOWN-CLOSURE: unclosed comment'", 'return [pscustomobject]@{Document=$document;Comments=@()}', 'unclosed-comment-before-block:'),
    @('comment-scan-resumes', '$i=$end+3', '$i=$Text.Length', 'closed-then-open-comment-before-block:'),
    @('code-literal-exclusion', '$literalEnds.ContainsKey($i)', '$false', 'code-comment-literal-not-markup:'),
    @('link-title-literal', '@($node.UrlSpan,$node.TitleSpan)', '@($node.UrlSpan)', 'link-title-'),
    @('link-url-literal', '@($node.UrlSpan,$node.TitleSpan)', '@($node.TitleSpan)', 'link-destination-comment-literal:'),
    @('reference-title-literal', ' -or $node -is [Markdig.Syntax.LinkReferenceDefinition]', '', 'reference-title-before-contract:'),
    @('synthetic-reference-span', 'if ($node.Span.End -lt $node.Span.Start) { continue }', '$null = $node', 'plain-headings-and-table:'),
    @('link-literal-projection', 'Set-SymbolMarkdownMask $visible $span.Start $span.End', '$null = $span', 'link-title-projection-'),
    @('reference-use-source-spans', 'if ($isReferenceUse) { $spans=@($node.LabelSpan) }', 'if ($false) { $spans=@($node.LabelSpan) }', 'used-reference-retains-visible-prefix:'),
    @('collapsed-reference-label', '-not $isReferenceUse -or $null -eq $node.LastChild -or $span.Start -gt $node.LastChild.Span.End', '$true', 'collapsed-reference-keeps-label:'),
    @('link-label-literal', 'if ($null -ne $owner) { $literalEnds[$node.Span.Start]=$node.Span.End }', '$null = $owner', 'literal-shortcut-label-is-not-comment:'),
    @('reference-definition-source-spans', 'if ($root -is [Markdig.Syntax.LinkReferenceDefinitionGroup])', 'if ($false)', 'reference-definition-does-not-mask-prior-heading:'),
    @('escaped-opener', "`$Text[`$i] -eq '\'", '$false', 'escaped-comment-opener-is-literal:'),
    @('top-level-only', 'foreach ($node in $document)', 'foreach ($node in [Markdig.Syntax.MarkdownObjectExtensions]::Descendants($document))', 'quoted-block:'),
    @('case-sensitive-label', ').TrimEnd() -ceq $Label', ').TrimEnd() -ieq $Label', 'label-is-case-sensitive:'),
    @('exact-label', ').TrimEnd() -ceq $Label', ").TrimEnd() -clike (`$Label+'*')", 'label-not-substring:'),
    @('unique-block', '$matches.Count -ne 1', '$matches.Count -lt 1', 'duplicate-block:'),
    @('content-start', 'if ($start -gt 0 -and', 'if ($false -and', 'crlf-content-offsets:'),
    @('cr-opening-boundary', '$Text.IndexOfAny([char[]]"`r`n",$block.Span.Start)', '$Text.IndexOf("`n",$block.Span.Start)', 'cr-content-offsets:'),
    @('cr-closing-boundary', '$Text.LastIndexOfAny([char[]]"`r`n",$block.Span.End)', '$Text.LastIndexOf("`n",$block.Span.End)', 'mixed-opening-crlf-closing-cr:'),
    @('cr-list-boundary', '$Text.LastIndexOfAny([char[]]"`r`n",[Math]::Max(0,$start-1))', '$Text.LastIndexOf([char]10,[Math]::Max(0,$start-1))', 'cr-list-html-keeps-prior-content:'),
    @('html-token-attributes', '[Markdig.Helpers.HtmlHelper]::TryParseHtmlTag([ref]$tagSlice,[ref]$tag)', '$false', 'html-attribute-before-contract:'),
    @('supported-blocks-only', 'if (-not $supported)', 'if ($false)', 'quoted-heading-masked:'),
    @('comment-range-mask', 'Set-SymbolMarkdownMask $visible $comment.Start $comment.End', '$null = $comment', 'inline-comment-masked:'),
    @('comment-range-block-filter', 'if (-not @($context.Comments | Where-Object { $node.Span.Start -ge $_.Start -and $node.Span.Start -le $_.End }).Count) { $node }', '$node', 'closed-then-multiline-comment-hides-block:'),
    @('inline-html-mask', 'Set-SymbolMarkdownMask $visible $start $block.Span.End', '$null = $inline', 'inline-html-excludes-containing-block:'),
    @('newline-fidelity', '$Buffer[$i] -notin @([char]10,[char]13)', '$true', 'closed-comment-masked:'),
    @('leaf-inline-traversal', '$scan = $block.Inline', '$scan = $block', 'inline-html-excludes-containing-block:'),
    @('visible-projection', 'return -join $visible', 'return $Text', 'closed-comment-masked:'),
    @('list-opt-in', '$IncludeListText -and $root -is [Markdig.Syntax.ListBlock]', '$root -is [Markdig.Syntax.ListBlock]', 'default-numbered-list-still-hidden:'),
    @('nested-heading-exclusion', '-not $entry.Nested -and $block -is [Markdig.Syntax.HeadingBlock]', '$block -is [Markdig.Syntax.HeadingBlock]', 'list-heading-not-admitted:')
)
foreach ($rawHtmlCase in $rawHtmlCases) {
    $lexerCall = '[Markdig.Helpers.HtmlHelper]::TryParseHtmlTag([ref]$tagSlice,[ref]$tag)'
    $onlyOtherTokens = '(-not $Text.Substring($i).StartsWith('''+$rawHtmlCase[2]+''',[StringComparison]::Ordinal) -and '+$lexerCall+')'
    $mutations += ,@("raw-html-$($rawHtmlCase[0])-lexer", $lexerCall, $onlyOtherTokens, "raw-html-$($rawHtmlCase[0])-contract:")
}
foreach ($mutation in $mutations) {
    $needle = $mutation[1]
    if ([regex]::Matches($source,[regex]::Escape($needle)).Count -ne 1) {
        throw "SYMBOL-MARKDOWN-MUTATION: target missing/ambiguous: $($mutation[0])"
    }
    $changed = $source.Replace($needle,$mutation[2])
    $null = [scriptblock]::Create($changed)
    $bad = @(Invoke-Suite $changed)
    $witness = @($bad | Where-Object { $_.StartsWith($mutation[3],[StringComparison]::Ordinal) })
    if (-not $witness.Count) { throw "SYMBOL-MARKDOWN-MUTATION: survived named fixture: $($mutation[0])" }
    Write-Host "SYMBOL-MARKDOWN-MUTATION KILLED: $($mutation[0]) -> $($witness[0])"
}
Write-Host "SYMBOL-MARKDOWN-TEST PASS: $($cases.Count) real behavior cases; $($mutations.Count) named guard mutations killed"
