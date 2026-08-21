<#
.SYNOPSIS
  Shared Unicode-scalar text helpers.

.DESCRIPTION
  .NET regular expressions classify UTF-16 code units. Supplementary-plane
  format characters therefore appear as two surrogate code units and evade a
  Cc/Cf character class. This dependency-free helper walks System.Text.Rune
  values instead, preserves every non-target scalar, and rejects malformed
  UTF-16 rather than silently repairing trusted input.
#>

function ConvertTo-ScaffoldControlFormatSpaces {
  [OutputType([string])]
  param(
    [Parameter(Mandatory, Position = 0)]
    [AllowEmptyString()]
    [string]$Text
  )

  $result = [System.Text.StringBuilder]::new($Text.Length)
  $offset = 0
  while ($offset -lt $Text.Length) {
    try {
      $rune = [System.Text.Rune]::GetRuneAt($Text, $offset)
    }
    catch {
      throw [System.ArgumentException]::new(
        "[UNICODE-SCALAR-MALFORMED] malformed UTF-16 at code-unit offset $offset.",
        'Text',
        $_.Exception
      )
    }

    $category = [System.Text.Rune]::GetUnicodeCategory($rune)
    if ($category -eq [System.Globalization.UnicodeCategory]::Control -or
        $category -eq [System.Globalization.UnicodeCategory]::Format) {
      [void]$result.Append(' ')
    }
    else {
      [void]$result.Append($rune.ToString())
    }
    $offset += $rune.Utf16SequenceLength
  }

  return $result.ToString()
}
