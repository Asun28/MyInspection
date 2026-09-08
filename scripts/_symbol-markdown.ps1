#requires -Version 7
# Narrow Markdown contract extraction. Uses PowerShell's bundled parser; no I/O or code execution.
function Get-SymbolMarkdownDocument {
    [CmdletBinding()]
    param([AllowEmptyString()][string]$Text)
    try {
        $inputText = if ($Text.Length) { $Text } else { "`n" }
        $document = (ConvertFrom-Markdown -InputObject $inputText -ErrorAction Stop).Tokens
        if ($document -isnot [Markdig.Syntax.MarkdownDocument]) { throw 'MarkdownDocument unavailable' }
        $nodes = @([Markdig.Syntax.MarkdownObjectExtensions]::Descendants($document))
    } catch {
        throw "SYMBOL-MARKDOWN-API: bundled Markdown parser unavailable: $($_.Exception.Message)"
    }
    $codeEnds = @{}
    foreach ($node in $nodes) {
        if ($node -is [Markdig.Syntax.FencedCodeBlock] -and
            $null -eq $node.PSObject.Properties['ClosingFencedCharCount']) {
            throw 'SYMBOL-MARKDOWN-API: fence closure metadata unavailable'
        }
        if ($node -is [Markdig.Syntax.CodeBlock] -or $node -is [Markdig.Syntax.Inlines.CodeInline]) {
            $end = $node.Span.End
            if ($node -is [Markdig.Syntax.FencedCodeBlock] -and $node.ClosingFencedCharCount -lt $node.OpeningFencedCharCount) {
                $end = $Text.Length-1
            }
            $codeEnds[$node.Span.Start] = $end
        }
    }
    # Markdig may keep "closed comment + new opener" in one HtmlBlock. Scan actual
    # comment boundaries, skipping AST code only while outside a comment; a fence
    # encountered inside a comment cannot turn its contents back into visible code.
    $comments = [Collections.Generic.List[object]]::new()
    for ($i=0; $i -lt $Text.Length;) {
        if ($codeEnds.ContainsKey($i)) { $i=$codeEnds[$i]+1; continue }
        if ($Text[$i] -eq '\') { $i+=2; continue }
        # Use the bundled HTML lexer for tags/attributes, not a second HTML grammar.
        # Skip only that token: real comments inside an unsupported HTML block still count.
        if ($Text[$i] -eq '<' -and ($i+4 -gt $Text.Length -or $Text.Substring($i,4) -cne '<!--')) {
            $tagSlice = [Markdig.Helpers.StringSlice]::new($Text,$i,$Text.Length-1)
            [string]$tag = ''
            if ([Markdig.Helpers.HtmlHelper]::TryParseHtmlTag([ref]$tagSlice,[ref]$tag)) {
                $i=$tagSlice.Start; continue
            }
        }
        if ($i+4 -le $Text.Length -and $Text.Substring($i,4) -ceq '<!--') {
            $end = $Text.IndexOf('-->',$i+4,[StringComparison]::Ordinal)
            if ($end -lt 0) { throw 'SYMBOL-MARKDOWN-CLOSURE: unclosed comment' }
            $comments.Add([pscustomobject]@{Start=$i;End=$end+2})
            $i=$end+3
        } else { $i++ }
    }
    foreach ($node in $nodes) {
        $hidden = @($comments | Where-Object { $node.Span.Start -ge $_.Start -and $node.Span.Start -le $_.End }).Count
        if (-not $hidden -and $node -is [Markdig.Syntax.FencedCodeBlock] -and
            $node.ClosingFencedCharCount -lt $node.OpeningFencedCharCount) {
            throw 'SYMBOL-MARKDOWN-CLOSURE: unclosed fence'
        }
    }
    return [pscustomobject]@{Document=$document;Comments=$comments.ToArray()}
}

function Get-SymbolMarkdownBlock {
    [CmdletBinding()]
    param([AllowEmptyString()][string]$Text, [Parameter(Mandatory)][string]$Label)
    $context = Get-SymbolMarkdownDocument -Text $Text
    $document = $context.Document
    # Enumerate only direct children: a fenced block in a quote/list is not a top-level contract.
    $matches = @(foreach ($node in $document) {
        if ($node -is [Markdig.Syntax.FencedCodeBlock] -and
            ($node.Info+' '+$node.Arguments).TrimEnd() -ceq $Label) {
            if (-not @($context.Comments | Where-Object { $node.Span.Start -ge $_.Start -and $node.Span.Start -le $_.End }).Count) { $node }
        }
    })
    if ($matches.Count -ne 1) {
        throw "SYMBOL-MARKDOWN-BLOCK: expected one visible top-level $Label block, found $($matches.Count)"
    }
    $block = $matches[0]
    $start = $Text.IndexOfAny([char[]]"`r`n",$block.Span.Start)+1
    if ($start -gt 0 -and $start -lt $Text.Length -and $Text[$start-1] -eq [char]13 -and $Text[$start] -eq [char]10) { $start++ }
    $end = $Text.LastIndexOfAny([char[]]"`r`n",$block.Span.End)+1
    if ($start -le 0 -or $end -lt $start) { throw 'SYMBOL-MARKDOWN-API: invalid content span' }
    return [pscustomobject]@{ Value=$Text.Substring($start,$end-$start); Index=$start; Length=$end-$start }
}

function Set-SymbolMarkdownMask {
    param([char[]]$Buffer, [int]$Start, [int]$End)
    if ($End -lt $Start) { return } # Parser-generated link references have no source span.
    if ($Start -lt 0 -or $End -ge $Buffer.Length) { throw 'SYMBOL-MARKDOWN-API: invalid source span' }
    for ($i=$Start; $i -le $End; $i++) {
        if ($Buffer[$i] -notin @([char]10,[char]13)) { $Buffer[$i] = ' ' }
    }
}

function Get-SymbolMarkdownVisible {
    [CmdletBinding()]
    param([AllowEmptyString()][string]$Text, [switch]$IncludeListText)
    $context = Get-SymbolMarkdownDocument -Text $Text
    $document = $context.Document
    $visible = $Text.ToCharArray()
    $entries = [Collections.Generic.List[object]]::new()
    foreach ($root in $document) {
        if ($IncludeListText -and $root -is [Markdig.Syntax.ListBlock]) {
            foreach ($child in [Markdig.Syntax.MarkdownObjectExtensions]::Descendants($root)) {
                if ($child -is [Markdig.Syntax.Block]) { $entries.Add([pscustomobject]@{Node=$child;Nested=$true}) }
            }
        } else { $entries.Add([pscustomobject]@{Node=$root;Nested=$false}) }
    }
    foreach ($entry in $entries) {
        $block = $entry.Node
        if ($entry.Nested -and ($block -is [Markdig.Syntax.ListBlock] -or $block -is [Markdig.Syntax.ListItemBlock])) { continue }
        $supported = (-not $entry.Nested -and $block -is [Markdig.Syntax.HeadingBlock]) -or
                     $block -is [Markdig.Syntax.ParagraphBlock] -or
                     $block.GetType().FullName -ceq 'Markdig.Extensions.Tables.Table'
        if (-not $supported) {
            Set-SymbolMarkdownMask $visible $block.Span.Start $block.Span.End
            continue
        }
        $scan = $block
        if ($block -is [Markdig.Syntax.LeafBlock]) { $scan = $block.Inline }
        if ($null -eq $scan) { continue }
        foreach ($inline in [Markdig.Syntax.MarkdownObjectExtensions]::Descendants($scan)) {
            if ($inline -isnot [Markdig.Syntax.Inlines.HtmlInline]) { continue }
            if ($inline.Tag.StartsWith('<!--',[StringComparison]::Ordinal)) { continue }
            # Unsupported HTML excludes its containing block, including a list marker.
            $start=$block.Span.Start
            if ($entry.Nested) { $start=$Text.LastIndexOfAny([char[]]"`r`n",[Math]::Max(0,$start-1))+1 }
            Set-SymbolMarkdownMask $visible $start $block.Span.End
            break
        }
    }
    foreach ($comment in $context.Comments) {
        Set-SymbolMarkdownMask $visible $comment.Start $comment.End
    }
    return -join $visible
}
