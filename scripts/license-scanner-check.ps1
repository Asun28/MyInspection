#requires -Version 7
[CmdletBinding()]
param(
  [Parameter(Mandatory)]
  [ValidateSet('graph', 'policy', 'diagnostics', 'gav-bounds')]
  [string]$Suite,
  [string]$ScannerPath = (Join-Path $PSScriptRoot 'check-licenses.ps1'),
  [switch]$SkipMutations
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$failures = [System.Collections.Generic.List[string]]::new()
function Assert-Graph {
  param(
    [Parameter(Mandatory)][bool]$Condition,
    [Parameter(Mandatory)][string]$Message
  )
  if (-not $Condition) { $failures.Add($Message) }
}

. $ScannerPath -AsLibrary

if ($Suite -eq 'gav-bounds') {
  function Assert-GavBounds {
    param(
      [Parameter(Mandatory)][bool]$Condition,
      [Parameter(Mandatory)][string]$Message
    )
    if (-not $Condition) { $failures.Add($Message) }
  }

  $fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) "license-gav-bounds-$PID-$([guid]::NewGuid().ToString('N'))"
  try {
    $group255 = (('Gg' * 127) + 'G')
    $artifact255 = (('Aa' * 127) + 'A')
    $version255 = (('Vv' * 127) + 'V')
    $acceptedCoordinate = "$group255`:$artifact255`:$version255"
    $acceptedParts = Get-GradleGavParts -Coordinate $acceptedCoordinate
    Assert-GavBounds (
      $null -ne $acceptedParts -and
      [string]::Equals([string]$acceptedParts.Group, $group255, [System.StringComparison]::Ordinal) -and
      [string]::Equals([string]$acceptedParts.Artifact, $artifact255, [System.StringComparison]::Ordinal) -and
      [string]::Equals([string]$acceptedParts.Version, $version255, [System.StringComparison]::Ordinal)
    ) '[GAV-BOUND-255] 255-character GAV segments did not preserve exact ordinal identity'

    $auditText = Get-GradleAuditText -Value "$acceptedCoordinate => prefix-$('x' * 600)-tail"
    Assert-GavBounds (
      $auditText.Length -eq 1000 -and
      $auditText.StartsWith("$acceptedCoordinate => [TRUNCATED] ", [System.StringComparison]::Ordinal) -and
      $auditText.EndsWith('-tail', [System.StringComparison]::Ordinal)
    ) '[GAV-AUDIT-ENVELOPE] accepted maximum GAV did not preserve the coordinate inside the 1000-character audit envelope'

    $script:bad = @()
    Add-GradleNonCompliance "$acceptedCoordinate => prefix-$('x' * 600)-tail [GRADLE-PARSE]"
    $auditEntry = if ($script:bad.Count -eq 1) { [string]$script:bad[0] } else { '' }
    Assert-GavBounds (
      $auditEntry.Length -eq 1024 -and
      $auditEntry.StartsWith("[GRADLE] $acceptedCoordinate => [TRUNCATED] ", [System.StringComparison]::Ordinal) -and
      $auditEntry.EndsWith('-tail [GRADLE-PARSE]', [System.StringComparison]::Ordinal)
    ) '[GAV-AUDIT-CATEGORY] accepted maximum GAV lost its exact coordinate or caller-owned category'

    $overlongCases = @(
      @{ Name = 'group'; Coordinate = "$(('g' * 256)):artifact:1.0" },
      @{ Name = 'artifact'; Coordinate = "group:$(('a' * 256)):1.0" },
      @{ Name = 'version'; Coordinate = "group:artifact:$(('v' * 256))" }
    )
    foreach ($overlongCase in $overlongCases) {
      Assert-GavBounds (
        $null -eq (Get-GradleGavParts -Coordinate $overlongCase.Coordinate)
      ) "[GAV-BOUND-$($overlongCase.Name.ToUpperInvariant())] 256-character $($overlongCase.Name) segment was accepted by the shared GAV boundary"
    }

    $cacheRejected = $false
    try {
      [void](Get-GradleCacheCoordinateRoot -GradleUserHome $fixtureRoot -Coordinate $overlongCases[0].Coordinate)
    } catch {
      $cacheRejected = $true
    }
    Assert-GavBounds $cacheRejected '[GAV-CACHE-BOUND] overlong GAV became a cache-coordinate path'

    $cachedPomResult = Get-GradleCachedPomInfo -Coordinate $overlongCases[1].Coordinate -GradleUserHome $fixtureRoot
    Assert-GavBounds (
      $cachedPomResult.State -ceq 'Error' -and
      $cachedPomResult.Detail -ceq '坐标不是具体且安全的 GAV。' -and
      $cachedPomResult.Paths.Count -eq 0
    ) '[GAV-POM-BOUND] overlong GAV reached cache lookup instead of failing at the cached-POM identity boundary'

    $exceptionPath = Join-Path $fixtureRoot 'exceptions.json'
    $exceptionRecord = @{
      coordinate = $overlongCases[2].Coordinate
      license = 'Apache-2.0'
      evidence_url = 'https://example.invalid/gav-bound'
      registered_by = 'gav-bound-test'
      registered_on = '2026-08-20'
    }
    New-Item -ItemType Directory -Force -Path $fixtureRoot | Out-Null
    [System.IO.File]::WriteAllText($exceptionPath, (ConvertTo-Json -InputObject @($exceptionRecord) -Compress), [System.Text.UTF8Encoding]::new($false))
    $exceptionResult = Get-GradleExceptionMap -Path $exceptionPath
    Assert-GavBounds (
      $exceptionResult.Entries.Count -eq 0 -and
      $exceptionResult.Error.StartsWith('[GRADLE-OVERRIDE] 坐标不是具体且安全的 GAV：', [System.StringComparison]::Ordinal)
    ) '[GAV-EXCEPTION-BOUND] overlong GAV became an exception identity'

    $policyExceptionPath = Join-Path $fixtureRoot 'policy-exceptions.json'
    $policyExceptionRecord = @{
      coordinate = $overlongCases[1].Coordinate
      license = 'Apache-2.0'
      evidence_url = 'https://example.invalid/gav-policy-bound'
      registered_by = 'gav-bound-test'
      registered_on = '2026-08-20'
    }
    [System.IO.File]::WriteAllText($policyExceptionPath, (ConvertTo-Json -InputObject @($policyExceptionRecord) -Compress), [System.Text.UTF8Encoding]::new($false))
    $policyResult = Get-GradleLicensePolicyResult -Resolved @([PSCustomObject]@{
      Coordinate = $overlongCases[1].Coordinate
      Configurations = @(':core:testRuntimeClasspath')
    }) -GradleUserHome $fixtureRoot -ExceptionPath $policyExceptionPath
    Assert-GavBounds (
      $policyResult.Findings.Count -eq 0 -and
      @($policyResult.Violations | Where-Object Code -CEQ 'GRADLE-POM').Count -eq 1 -and
      @($policyResult.Violations | Where-Object Code -CEQ 'GRADLE-OVERRIDE').Count -eq 1
    ) '[GAV-POLICY-BOUND] overlong GAV became a cached-POM or exception-backed finding'

    $graphResult = Get-GradleCoordinatesFromDependencyOutput -Output @("+--- $($overlongCases[0].Coordinate)")
    Assert-GavBounds (
      $graphResult.Coordinates.Count -eq 0 -and
      $graphResult.Errors.Count -eq 1 -and
      $graphResult.Errors[0] -match '\[GRADLE-PARSE\]$'
    ) '[GAV-GRAPH-BOUND] overlong GAV entered the parsed dependency finding set'
  } catch {
    Assert-GavBounds $false "[GAV-BOUND-SETUP] gav-bounds fixture failed: $($_.Exception.Message)"
  } finally {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
  }

  if ($failures.Count -gt 0) {
    foreach ($failure in $failures) { Write-Error $failure -ErrorAction Continue }
    exit 1
  }

  if (-not $SkipMutations) {
    $source = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $ScannerPath))
    $gavBoundMutationCases = @(
      @{
        Name = 'segment-length'
        From = '    if ($segment.Length -gt 255) { return $null } # GAV segment length guard'
        To = '    if ($false) { return $null } # GAV segment length guard'
        Expected = '[GAV-BOUND-GROUP]'
      },
      @{
        Name = 'cache-shared-guard'
        From = '  $gav = Get-GradleGavParts -Coordinate $Coordinate # cache coordinate shared GAV guard'
        To = "  `$gav = [PSCustomObject]@{ Group = 'accepted'; Artifact = 'accepted'; Version = 'accepted' } # cache coordinate shared GAV guard"
        Expected = '[GAV-CACHE-BOUND]'
      },
      @{
        Name = 'pom-shared-guard'
        From = '  $gav = Get-GradleGavParts -Coordinate $Coordinate # cached POM shared GAV guard'
        To = "  `$gav = [PSCustomObject]@{ Group = 'accepted'; Artifact = 'accepted'; Version = 'accepted' } # cached POM shared GAV guard"
        Expected = '[GAV-POM-BOUND]'
      },
      @{
        Name = 'exception-shared-guard'
        From = '      if ($null -eq (Get-GradleGavParts -Coordinate $coordinate)) {'
        To = '      if ($false) {'
        Expected = '[GAV-EXCEPTION-BOUND]'
      },
      @{
        Name = 'graph-shared-guard'
        From = '    if ($null -eq (Get-GradleGavParts -Coordinate $resolvedCoordinate)) {'
        To = '    if ($false) {'
        Expected = '[GAV-GRAPH-BOUND]'
      }
    )

    foreach ($mutationCase in $gavBoundMutationCases) {
      $matches = [regex]::Matches($source, [regex]::Escape($mutationCase.From)).Count
      if ($matches -ne 1) {
        Write-Error "[GAV-BOUND-MUTATION] $($mutationCase.Name) target count=$matches"
        exit 1
      }
      $mutantPath = Join-Path $PSScriptRoot ".license-gav-bounds-$PID-$($mutationCase.Name).ps1"
      try {
        [System.IO.File]::WriteAllText($mutantPath, $source.Replace($mutationCase.From, $mutationCase.To), [System.Text.UTF8Encoding]::new($false))
        $mutationOutput = (& pwsh -NoProfile -File $PSCommandPath -Suite gav-bounds -ScannerPath $mutantPath -SkipMutations 2>&1 | Out-String)
        $mutationExit = $LASTEXITCODE
        if ($mutationExit -eq 0 -or $mutationOutput -notmatch [regex]::Escape($mutationCase.Expected)) {
          Write-Error "[GAV-BOUND-MUTATION] $($mutationCase.Name) did not fail on its semantic inverse (exit=$mutationExit; output=$mutationOutput)"
          exit 1
        }
      } finally {
        if (Test-Path -LiteralPath $mutantPath) { Remove-Item -LiteralPath $mutantPath -Force }
      }
    }
    Write-Host "license-scanner-check(gav-bounds mutations): PASS ($($gavBoundMutationCases.Count))"
  }

  Write-Host 'license-scanner-check(gav-bounds): PASS'
  exit 0
}

if ($Suite -eq 'diagnostics') {
  function Assert-Diagnostics {
    param(
      [Parameter(Mandatory)][bool]$Condition,
      [Parameter(Mandatory)][string]$Message
    )
    if (-not $Condition) { $failures.Add($Message) }
  }

  # Each case names the production guard whose deletion must expose the hostile payload.
  $uriCanary = 'DIAG_URI_CANARY'
  $uriText = Get-GradleDiagnosticTail -Output @("ssh://credential-user:$uriCanary@example.invalid/repository") -MaxLines 2 -MaxChars 400
  Assert-Diagnostics (
    $uriText -ceq 'ssh://[REDACTED]@example.invalid/repository' -and $uriText -notmatch [regex]::Escape($uriCanary)
  ) '[DIAG-URI] URI userinfo was not redacted'

  $authorizationCanary = 'DIAG_AUTH_CANARY'
  $authorizationText = Get-GradleDiagnosticTail -Output @("X-Authorization: Bearer $authorizationCanary") -MaxLines 2 -MaxChars 400
  Assert-Diagnostics (
    $authorizationText -ceq 'X-Authorization: [REDACTED]' -and $authorizationText -notmatch [regex]::Escape($authorizationCanary)
  ) '[DIAG-AUTH] Authorization value was not redacted'

  $credentialCanary = 'DIAG_CREDENTIAL_CANARY'
  $credentialName = '--pass' + 'word'
  $keyText = Get-GradleDiagnosticTail -Output @("$credentialName=$credentialCanary") -MaxLines 2 -MaxChars 400
  Assert-Diagnostics (
    $keyText -ceq "$credentialName=[REDACTED]" -and $keyText -notmatch [regex]::Escape($credentialCanary)
  ) '[DIAG-KEY] secret-like key value was not redacted'

  $credentialKeyName = 'credential' + 's'
  $credentialKeyText = Get-GradleDiagnosticTail -Output @("$credentialKeyName=$credentialCanary") -MaxLines 2 -MaxChars 400
  Assert-Diagnostics (
    $credentialKeyText -ceq "$credentialKeyName=[REDACTED]" -and $credentialKeyText -notmatch [regex]::Escape($credentialCanary)
  ) '[DIAG-CREDENTIAL-KEY] credential-like key value was not redacted'

  $recordCanary = 'DIAG_RECORD_CANARY'
  $recordKeyName = 'pass' + 'word'
  $recordCredentialCases = @(
    @{ Input = "Authorization:`nBearer $recordCanary"; Expected = 'Authorization: [REDACTED]' },
    @{ Input = "Authorization: Bearer`n$recordCanary"; Expected = 'Authorization: [REDACTED]' },
    @{ Input = "Authorization: Bearer prefix`n$recordCanary"; Expected = 'Authorization: [REDACTED]' },
    @{ Input = "Authorization: Bearer prefix`nmiddle`n$recordCanary"; Expected = 'Authorization: [REDACTED]' },
    @{ Input = "$recordKeyName=`r`n$recordCanary"; Expected = "$recordKeyName=[REDACTED]" },
    @{ Input = "$recordKeyName=prefix`n$recordCanary"; Expected = "$recordKeyName=[REDACTED]" },
    @{ Input = "$recordKeyName=prefix`nmiddle`n$recordCanary"; Expected = "$recordKeyName=[REDACTED]" },
    @{ Input = "ssh://user:`n$recordCanary@example.invalid/repository"; Expected = 'ssh://[REDACTED]@example.invalid/repository' },
    @{ Input = "ssh://user:prefix`n$recordCanary@example.invalid/repository"; Expected = 'ssh://[REDACTED]@example.invalid/repository' },
    @{ Input = "ssh://user:prefix`nmiddle`n$recordCanary@example.invalid/repository"; Expected = 'ssh://[REDACTED]@example.invalid/repository' },
    @{ Input = "ssh://first@second:$recordCanary@example.invalid/repository"; Expected = 'ssh://[REDACTED]@example.invalid/repository' }
  )
  foreach ($recordCase in $recordCredentialCases) {
    $recordText = Get-GradleDiagnosticTail -Output @($recordCase.Input) -MaxLines 3 -MaxChars 400
    Assert-Diagnostics (
      $recordText -ceq $recordCase.Expected -and $recordText -notmatch [regex]::Escape($recordCanary)
    ) "[DIAG-RECORD-CREDENTIAL] composed credential record leaked or split before redaction: $recordText"
  }
  $ordinaryRecordText = Get-GradleDiagnosticTail -Output @("https://user`nordinary@example.invalid/repository") -MaxLines 3 -MaxChars 400
  Assert-Diagnostics (
    $ordinaryRecordText -ceq 'https://user | ordinary@example.invalid/repository'
  ) "[DIAG-RECORD-BOUNDARY] ordinary next line was consumed as URI userinfo: $ordinaryRecordText"

  $windowsPathCases = @(
    @{ Input = 'failed at C:\Users\alice\private\pom.xml'; Expected = 'failed at [USER_HOME]\private\pom.xml' },
    @{ Input = 'failed at C:\Users\Alice Smith\private\pom.xml'; Expected = 'failed at [USER_HOME]\private\pom.xml' },
    @{ Input = 'failed at file:///C:/Users/alice/private/pom.xml'; Expected = 'failed at file:///[USER_HOME]/private/pom.xml' },
    @{ Input = 'failed at C:\Users\Alice Smith'; Expected = 'failed at [USER_HOME]' },
    @{ Input = 'failed at C:\Users\Alice Smith: denied'; Expected = 'failed at [USER_HOME]: denied' },
    @{ Input = 'failed at C:\Users\Alice Smith|denied'; Expected = 'failed at [USER_HOME]|denied' },
    @{ Input = 'failed at file:///C:/Users/Alice Smith'; Expected = 'failed at file:///[USER_HOME]' }
  )
  foreach ($pathCase in $windowsPathCases) {
    $windowsPathText = Get-GradleDiagnosticTail -Output @($pathCase.Input) -MaxLines 2 -MaxChars 400
    Assert-Diagnostics (
      $windowsPathText -ceq $pathCase.Expected -and $windowsPathText -notmatch '(?i)C:[\\/]Users[\\/]alice'
    ) "[DIAG-WINDOWS-HOME] Windows user directory was not redacted: $windowsPathText"
  }

  $unixPathCases = @(
    @{ Input = 'failed at /home/alice/.gradle/caches/pom.xml'; Expected = 'failed at [USER_HOME]/.gradle/caches/pom.xml' },
    @{ Input = 'failed at /home/Alice Smith/.gradle/caches/pom.xml'; Expected = 'failed at [USER_HOME]/.gradle/caches/pom.xml' },
    @{ Input = 'failed at /home/alice: permission denied'; Expected = 'failed at [USER_HOME]: permission denied' },
    @{ Input = 'failed at /Users/alice/Library/cache/pom.xml'; Expected = 'failed at [USER_HOME]/Library/cache/pom.xml' },
    @{ Input = 'failed at /root/.gradle/caches/pom.xml'; Expected = 'failed at [USER_HOME]/.gradle/caches/pom.xml' },
    @{ Input = 'failed at file:///home/alice/.gradle/caches/pom.xml'; Expected = 'failed at file://[USER_HOME]/.gradle/caches/pom.xml' },
    @{ Input = 'failed at /home/Alice Smith'; Expected = 'failed at [USER_HOME]' },
    @{ Input = 'failed at /Users/Alice Smith'; Expected = 'failed at [USER_HOME]' },
    @{ Input = 'failed at /Users/Alice Smith|denied'; Expected = 'failed at [USER_HOME]|denied' },
    @{ Input = 'failed at file:///home/Alice Smith'; Expected = 'failed at file://[USER_HOME]' },
    @{ Input = 'failed at file:///Users/Alice Smith'; Expected = 'failed at file://[USER_HOME]' }
  )
  foreach ($pathCase in $unixPathCases) {
    $pathText = Get-GradleDiagnosticTail -Output @($pathCase.Input) -MaxLines 2 -MaxChars 400
    Assert-Diagnostics ($pathText -ceq $pathCase.Expected) "[DIAG-UNIX-HOME] Unix user directory was not redacted: $pathText"
  }

  $configuredHomeCases = @(
    @{ EnvName = 'USERPROFILE'; Home = 'D:\Profiles\Alice Smith'; Input = 'failed at D:\Profiles\Alice Smith\private\pom.xml'; Expected = 'failed at [USER_HOME]\private\pom.xml' },
    @{ EnvName = 'USERPROFILE'; Home = '\\server\profiles\Alice Smith'; Input = 'failed at \\server\profiles\Alice Smith\private\pom.xml'; Expected = 'failed at [USER_HOME]\private\pom.xml' },
    @{ EnvName = 'HOME'; Home = '/var/home/Alice Smith'; Input = 'failed at /var/home/Alice Smith/private/pom.xml'; Expected = 'failed at [USER_HOME]/private/pom.xml' },
    @{ EnvName = 'HOME'; Home = '/srv/users/Alice Smith'; Input = 'failed at file:///srv/users/Alice Smith/private/pom.xml'; Expected = 'failed at file://[USER_HOME]/private/pom.xml' }
  )
  foreach ($configuredHomeCase in $configuredHomeCases) {
    $oldConfiguredHome = [Environment]::GetEnvironmentVariable($configuredHomeCase.EnvName)
    try {
      [Environment]::SetEnvironmentVariable($configuredHomeCase.EnvName, $configuredHomeCase.Home)
      $configuredHomeText = Get-GradleDiagnosticTail -Output @($configuredHomeCase.Input) -MaxLines 2 -MaxChars 400
      Assert-Diagnostics (
        $configuredHomeText -ceq $configuredHomeCase.Expected
      ) "[DIAG-CONFIGURED-HOME] configured user directory was not redacted: $configuredHomeText"
    } finally {
      [Environment]::SetEnvironmentVariable($configuredHomeCase.EnvName, $oldConfiguredHome)
    }
  }

  $formatControl = [char]0x202E
  $controlText = Get-GradleDiagnosticTail -Output @("prefix-$formatControl-suffix") -MaxLines 2 -MaxChars 400
  Assert-Diagnostics (
    $controlText -ceq 'prefix- -suffix' -and -not [regex]::IsMatch($controlText, '[\p{Cc}\p{Cf}]')
  ) '[DIAG-CONTROL] control/format character survived diagnostics'

  $ansiText = Get-GradleDiagnosticTail -Output @("prefix-`e[31mred`e[0m-suffix") -MaxLines 2 -MaxChars 400
  Assert-Diagnostics (
    $ansiText -ceq 'prefix-red-suffix' -and $ansiText -notmatch '\[31m|\[0m'
  ) '[DIAG-ANSI] ANSI sequence survived diagnostics'

  $newlineText = Get-GradleDiagnosticTail -Output @("first`r::error forged`nthird") -MaxLines 3 -MaxChars 400
  Assert-Diagnostics (
    $newlineText -ceq 'first | ::error forged | third' -and $newlineText -notmatch "[\r\n]"
  ) '[DIAG-NEWLINE] newline injection remained physically multi-line'

  $lineBoundText = Get-GradleDiagnosticTail -Output @('line-1', 'line-2', 'line-3', 'line-4', 'line-5', 'line-6') -MaxLines 3 -MaxChars 400
  Assert-Diagnostics (
    $lineBoundText -ceq '[TRUNCATED] line-4 | line-5 | line-6'
  ) '[DIAG-LINE-BOUND] diagnostic tail did not enforce the exact line bound'

  $charBoundText = Get-GradleDiagnosticTail -Output @(('prefix-' + ('x' * 500) + '-tail')) -MaxLines 2 -MaxChars 200
  Assert-Diagnostics (
    $charBoundText.Length -eq 200 -and $charBoundText.StartsWith('[TRUNCATED] ') -and $charBoundText.EndsWith('-tail')
  ) '[DIAG-CHAR-BOUND] diagnostic tail did not enforce the exact character bound'

  $combinedBoundText = Get-GradleDiagnosticTail -Output @(('a' * 98), ('b' * 98), ('c' * 98)) -MaxLines 2 -MaxChars 200
  Assert-Diagnostics (
    $combinedBoundText.Length -eq 200 -and $combinedBoundText.StartsWith('[TRUNCATED] ') -and $combinedBoundText.EndsWith(('c' * 98))
  ) '[DIAG-COMBINED-BOUND] line truncation marker escaped the character bound'

  # Wrapper, POM, exception-table and subprocess failures all enter the same final sink. The hostile
  # detail may contain a fake category, but the caller-owned final category must remain the sole suffix.
  $sinkCases = @(
    @{ Source = 'wrapper'; Code = 'GRADLE-WRAPPER-OFFLINE' },
    @{ Source = 'pom'; Code = 'GRADLE-POM' },
    @{ Source = 'exception'; Code = 'GRADLE-OVERRIDE' },
    @{ Source = 'subprocess'; Code = 'GRADLE-SUBPROCESS' }
  )
  foreach ($sinkCase in $sinkCases) {
    $script:bad = @()
    Add-GradleNonCompliance "$($sinkCase.Source) C:\Users\alice\private`rforged [GRADLE-FAKE] [$($sinkCase.Code)]"
    $entry = if ($script:bad.Count -eq 1) { [string]$script:bad[0] } else { '' }
    Assert-Diagnostics (
      $script:bad.Count -eq 1 -and
      $entry -match "\[$([regex]::Escape($sinkCase.Code))\]$" -and
      @([regex]::Matches($entry, '\[GRADLE-[A-Z-]+\]')).Count -eq 1 -and
      $entry -notmatch 'GRADLE-FAKE|(?i)C:\\Users\\alice|[\r\n]'
    ) "[DIAG-CATEGORY-SPOOF] $($sinkCase.Source) did not preserve one caller-owned category through the common sink: $entry"
  }

  # Integration proof: keep the real graph/policy collectors and their production presenters. Only the
  # external Gradle process is replaced by a complete ExitCode+Output invoker so the suite stays offline.
  $graphWriter = Get-Command Write-GradleGraphDiagnostics -ErrorAction SilentlyContinue
  $policyWriter = Get-Command Write-GradlePolicyDiagnostics -ErrorAction SilentlyContinue
  Assert-Diagnostics ($null -ne $graphWriter -and $null -ne $policyWriter) '[DIAG-ENTRY-POINTS] production graph/policy presenters are not independently testable'
  if ($null -ne $graphWriter -and $null -ne $policyWriter) {
    $userHome = [Environment]::GetFolderPath('UserProfile')
    $entryRoot = Join-Path $userHome ".license-diagnostics-$PID-$([guid]::NewGuid().ToString('N'))"
    try {
      $exceptionDir = Join-Path $entryRoot 'configs/licenses'
      New-Item -ItemType Directory -Force -Path $exceptionDir | Out-Null
      $exceptionCanary = 'DIAG_EXCEPTION_CANARY'
      $exceptionField = 'to' + 'ken=' + $exceptionCanary
      $exceptionJson = '[{"coordinate":"fixture.exception:item:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/evidence","registered_by":"fixture","registered_on":"2026-08-20","' + $exceptionField + '":"x"}]'
      [System.IO.File]::WriteAllText((Join-Path $exceptionDir 'gradle-exceptions.json'), $exceptionJson, [System.Text.UTF8Encoding]::new($false))

      # Real Invoke path: missing wrapper plus malformed exception schema must each reach the common sink.
      $script:bad = @(); $script:warn = @()
      Invoke-GradleLicenseScan -Root $entryRoot
      $wrapperEntries = @($script:bad | Where-Object { $_ -match '\[GRADLE-SUBPROCESS\]$' })
      $exceptionEntries = @($script:bad | Where-Object { $_ -match '\[GRADLE-OVERRIDE\]$' })
      $entryText = $script:bad -join "`n"
      Assert-Diagnostics (
        $wrapperEntries.Count -eq 1 -and $exceptionEntries.Count -eq 1 -and
        $entryText -notmatch [regex]::Escape($userHome) -and
        $entryText -notmatch [regex]::Escape($exceptionCanary)
      ) "[DIAG-ENTRY-WRAPPER-EXCEPTION] real Invoke path bypassed redaction/category preservation: $entryText"

      # Real POM policy path: an external license name containing a secret-like value remains unknown,
      # but its coordinate/code survive while the value is redacted.
      $pomCoordinate = 'fixture.diagnostics:pom-entry:1.0'
      $gradleHome = Join-Path $entryRoot 'gradle-home'
      $pomDir = Join-Path (Get-GradleCacheCoordinateRoot -GradleUserHome $gradleHome -Coordinate $pomCoordinate) 'fixture-hash'
      New-Item -ItemType Directory -Force -Path $pomDir | Out-Null
      $pomCanary = 'DIAG_POM_CANARY'
      $pomLicense = ('pass' + 'word=' + $pomCanary)
      $pomXml = '<project><modelVersion>4.0.0</modelVersion><groupId>fixture.diagnostics</groupId><artifactId>pom-entry</artifactId><version>1.0</version><licenses><license><name>' + $pomLicense + '</name></license></licenses></project>'
      [System.IO.File]::WriteAllText((Join-Path $pomDir 'pom-entry-1.0.pom'), $pomXml, [System.Text.UTF8Encoding]::new($false))
      $pomResolved = @([PSCustomObject]@{ Coordinate = $pomCoordinate; Configurations = @(':core:testRuntimeClasspath') })
      $pomPolicy = Get-GradleLicensePolicyResult -Resolved $pomResolved -GradleUserHome $gradleHome -ExceptionPath (Join-Path $entryRoot 'missing-exceptions.json')
      $script:bad = @(); $script:warn = @()
      Write-GradlePolicyDiagnostics -Policy $pomPolicy -Resolved $pomResolved
      $pomEntry = if ($script:bad.Count -eq 1) { [string]$script:bad[0] } else { '' }
      Assert-Diagnostics (
        $script:bad.Count -eq 1 -and $pomEntry.StartsWith("[GRADLE] $pomCoordinate => ") -and
        $pomEntry -match '\[GRADLE-UNKNOWN\]$' -and $pomEntry -notmatch [regex]::Escape($pomCanary)
      ) "[DIAG-ENTRY-POM] real POM policy path bypassed the common sink or changed category: $pomEntry"

      # Real graph collector path with only the slow child process replaced.
      $androidRoot = Join-Path $entryRoot 'android'
      $wrapperDir = Join-Path $androidRoot 'gradle/wrapper'
      $distributionDir = Join-Path $gradleHome 'wrapper/dists/gradle-9.7.0-bin/d4tj7w02tcgubx9zk9hbippn6'
      $distributionRoot = Join-Path $distributionDir 'gradle-9.7.0'
      $nativeArtifactRoot = Join-Path $gradleHome 'caches/modules-2/files-2.1/fixture.group/fixture-artifact/1.0/fixture-hash'
      $nativeMetadataRoot = Join-Path $gradleHome 'caches/modules-2/metadata-2.107'
      foreach ($directory in @($wrapperDir, (Join-Path $distributionRoot 'lib'), (Join-Path $distributionRoot 'bin'), $nativeArtifactRoot, $nativeMetadataRoot)) {
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
      }
      Set-Content -LiteralPath (Join-Path $wrapperDir 'gradle-wrapper.properties') -Encoding utf8 -Value 'distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.0-bin.zip'
      foreach ($file in @(
        (Join-Path $androidRoot 'gradlew.bat'),
        (Join-Path $distributionDir 'gradle-9.7.0-bin.zip.ok'),
        (Join-Path $distributionRoot 'lib/gradle-launcher-9.7.0.jar'),
        (Join-Path $distributionRoot 'bin/gradle'),
        (Join-Path $distributionRoot 'bin/gradle.bat'),
        (Join-Path $nativeArtifactRoot 'fixture-artifact-1.0.pom'),
        (Join-Path $nativeMetadataRoot 'module-metadata.bin')
      )) { Set-Content -LiteralPath $file -Encoding utf8 -Value 'fixture' }
      $subprocessCanary = 'DIAG_SUBPROCESS_CANARY'
      $failureInvoker = {
        param([string]$Command, [string[]]$Arguments)
        [PSCustomObject]@{
          ExitCode = 42
          Output = @("ssh://user:$subprocessCanary@example.invalid/repo", "C:\Users\alice\private`r::error forged")
        }
      }.GetNewClosure()
      $graphResult = Get-GradleResolvedGraphs -Root $entryRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $failureInvoker
      $script:bad = @(); $script:warn = @()
      Write-GradleGraphDiagnostics -Errors $graphResult.Errors -DecodeEscapedNewlines $false
      $subprocessText = $script:bad -join "`n"
      Assert-Diagnostics (
        $graphResult.Errors.Count -eq 4 -and $script:bad.Count -eq 4 -and
        @($script:bad | Where-Object { $_ -match '退出 42；' -and $_ -match '\[GRADLE-SUBPROCESS\]$' }).Count -eq 4 -and
        $subprocessText -notmatch [regex]::Escape($subprocessCanary) -and
        $subprocessText -notmatch '(?i)C:\\Users\\alice|[\r]'
      ) "[DIAG-ENTRY-SUBPROCESS] real graph subprocess path bypassed redaction or changed error semantics: $subprocessText"
    } finally {
      if (Test-Path -LiteralPath $entryRoot) { Remove-Item -LiteralPath $entryRoot -Recurse -Force }
    }
  }

  $script:bad = @()
  $missingCategoryRejected = $false
  try { Add-GradleNonCompliance 'detail without a caller-owned category' } catch { $missingCategoryRejected = $true }
  Assert-Diagnostics ($missingCategoryRejected -and $script:bad.Count -eq 0) '[DIAG-CATEGORY-REQUIRED] uncategorized diagnostic entered the final sink'

  $script:bad = @()
  $longCoordinate = 'fixture.group:fixture-artifact:1.2.3'
  Add-GradleNonCompliance "$longCoordinate => prefix-$('x' * 1400)-tail [GRADLE-POM]"
  $longEntry = if ($script:bad.Count -eq 1) { [string]$script:bad[0] } else { '' }
  Assert-Diagnostics (
    $script:bad.Count -eq 1 -and
    $longEntry.StartsWith("[GRADLE] $longCoordinate => [TRUNCATED] ") -and
    $longEntry.EndsWith('-tail [GRADLE-POM]') -and
    $longEntry.Length -le 1070
  ) "[DIAG-GAV-PRESERVATION] bounded diagnostic lost exact GAV/category or exceeded the sink bound: $longEntry"

  $script:bad = @()
  $boundedCoordinate = "$(('g' * 500)):artifact:1.0"
  Add-GradleNonCompliance "$boundedCoordinate => prefix-$('x' * 1400)-tail [GRADLE-POM]"
  $boundedCoordinateEntry = if ($script:bad.Count -eq 1) { [string]$script:bad[0] } else { '' }
  Assert-Diagnostics (
    $boundedCoordinateEntry.StartsWith("[GRADLE] $boundedCoordinate => [TRUNCATED] ") -and
    $boundedCoordinateEntry.EndsWith('-tail [GRADLE-POM]') -and
    $boundedCoordinateEntry.Length -le 1040
  ) "[DIAG-GAV-TOTAL-BOUND] coordinate escaped the diagnostic character budget: $($boundedCoordinateEntry.Length)"

  $script:bad = @()
  $oversizedCoordinate = "$(('g' * 3000)):artifact:1.0"
  Add-GradleNonCompliance "$oversizedCoordinate => tail [GRADLE-PARSE]"
  $oversizedCoordinateEntry = if ($script:bad.Count -eq 1) { [string]$script:bad[0] } else { '' }
  Assert-Diagnostics (
    $oversizedCoordinateEntry.Length -le 1040 -and $oversizedCoordinateEntry.EndsWith('tail [GRADLE-PARSE]')
  ) "[DIAG-GAV-TOTAL-BOUND] oversized coordinate produced an unbounded diagnostic: $($oversizedCoordinateEntry.Length)"

  $script:bad = @()
  $multilineCoordinate = 'fixture.multiline:artifact:1.0'
  Add-GradleNonCompliance "$multilineCoordinate => first`nsecond [GRADLE-FAKE] tail [GRADLE-POM]"
  $multilineCoordinateEntry = if ($script:bad.Count -eq 1) { [string]$script:bad[0] } else { '' }
  Assert-Diagnostics (
    $multilineCoordinateEntry -ceq "[GRADLE] $multilineCoordinate => [TRUNCATED] second [REDACTED-CATEGORY] tail [GRADLE-POM]" -and
    $multilineCoordinateEntry -notmatch '[\r\n]'
  ) "[DIAG-GAV-NEWLINE] multiline detail lost exact GAV/category preservation: $multilineCoordinateEntry"

  if ($failures.Count -gt 0) {
    foreach ($failure in $failures) { Write-Error $failure -ErrorAction Continue }
    exit 1
  }

  if (-not $SkipMutations) {
    $source = Get-Content -LiteralPath $ScannerPath -Raw
    $diagnosticMutationCases = @(
      @{
        Name = 'record-credential-redaction'
        From = '    $raw = Protect-GradleDiagnosticRecord -Value $raw # diagnostic record credential boundary'
        To = '    $raw = $raw # diagnostic record credential boundary'
        Expected = '[DIAG-RECORD-CREDENTIAL]'
      },
      @{
        Name = 'record-uri-boundary'
        From = '    $Value = [regex]::Replace($Value, ''(?is)(?<scheme>[A-Za-z][A-Za-z0-9+.-]*://)[^/\r\n]*:[^/]*@'', ''${scheme}[REDACTED]@'') # diagnostic multiline URI boundary'
        To = '    $Value = [regex]::Replace($Value, ''(?is)(?<scheme>[A-Za-z][A-Za-z0-9+.-]*://)[^/]*@'', ''${scheme}[REDACTED]@'') # diagnostic multiline URI boundary'
        Expected = '[DIAG-RECORD-BOUNDARY]'
      },
      @{
        Name = 'windows-user-home'
        From = '    $line = [regex]::Replace($line, ''(?i)(?<![A-Za-z0-9])(?:[A-Za-z]:)[\\/]+Users[\\/]+(?:[^\\/|]+(?=[\\/])|[^\\/|:;,)\]\r\n]+)(?=[\\/]|[|:;,)\]]|$)'', ''[USER_HOME]'') # diagnostic Windows user-home redaction'
        To = '    $line = $line # diagnostic Windows user-home redaction'
        Expected = '[DIAG-WINDOWS-HOME]'
      },
      @{
        Name = 'unix-user-home'
        From = '    $line = [regex]::Replace($line, ''(?i)(?<![A-Za-z0-9:])/(?:home/(?:[^/|]+(?=/)|[^/|:;,)\]\r\n]+)|Users/(?:[^/|]+(?=/)|[^/|:;,)\]\r\n]+)|root)(?=/|[|:;,)\]]|$)'', ''[USER_HOME]'') # diagnostic Unix user-home redaction'
        To = '    $line = $line # diagnostic Unix user-home redaction'
        Expected = '[DIAG-UNIX-HOME]'
      },
      @{
        Name = 'configured-user-home'
        From = '      $line = [regex]::Replace($line, [regex]::Escape($homeVariant) + ''(?=[\\/]|[|:;,)\]\s]|$)'', ''[USER_HOME]'', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase) # diagnostic configured user-home redaction'
        To = '      $line = $line # diagnostic configured user-home redaction'
        Expected = '[DIAG-CONFIGURED-HOME]'
      },
      @{
        Name = 'ansi-redaction'
        From = '    $line = [regex]::Replace($_, "`e\[[0-?]*[ -/]*[@-~]", '''') # diagnostic ANSI redaction'
        To = '    $line = "$_" # diagnostic ANSI redaction'
        Expected = '[DIAG-ANSI]'
      },
      @{
        Name = 'control-normalization'
        From = '    $line = [regex]::Replace($line, ''[\p{Cc}\p{Cf}]'', '' '') # diagnostic control/format normalization'
        To = '    $line = $line # diagnostic control/format normalization'
        Expected = '[DIAG-CONTROL]'
      },
      @{
        Name = 'line-bound'
        From = '  $tail = (@($sanitized | Select-Object -Last $MaxLines) -join '' | '') # diagnostic line bound'
        To = '  $tail = (@($sanitized) -join '' | '') # diagnostic line bound'
        Expected = '[DIAG-LINE-BOUND]'
      },
      @{
        Name = 'character-bound'
        From = '  if ($tail.Length -gt $payloadMax) { # diagnostic character bound'
        To = '  if ($false) { # diagnostic character bound'
        Expected = '[DIAG-CHAR-BOUND]'
      },
      @{
        Name = 'marker-inclusive-bound'
        From = '  $payloadMax = if ($truncated) { $MaxChars - $marker.Length } else { $MaxChars } # diagnostic marker-inclusive bound'
        To = '  $payloadMax = $MaxChars # diagnostic marker-inclusive bound'
        Expected = '[DIAG-COMBINED-BOUND]'
      },
      @{
        Name = 'category-spoof'
        From = '  $safeDetail = [regex]::Replace($safeDetail, ''(?i)\[GRADLE-[A-Z-]+\]'', ''[REDACTED-CATEGORY]'') # diagnostic category spoof guard'
        To = '  $safeDetail = $safeDetail # diagnostic category spoof guard'
        Expected = '[DIAG-CATEGORY-SPOOF]'
      },
      @{
        Name = 'category-required'
        From = '  if (-not $categoryMatch.Success) { throw ''Gradle diagnostic missing caller-owned [GRADLE-*] category.'' } # diagnostic category required guard'
        To = '  if ($false) { throw ''Gradle diagnostic missing caller-owned [GRADLE-*] category.'' } # diagnostic category required guard'
        Expected = '[DIAG-CATEGORY-REQUIRED]'
      },
      @{
        Name = 'exact-gav-preservation'
        From = '    $coordinatePrefix = "$($coordinateMatch.Groups[''coordinate''].Value) => " # diagnostic exact-GAV preservation'
        To = '    $coordinatePrefix = '''' # diagnostic exact-GAV preservation'
        Expected = '[DIAG-GAV-PRESERVATION]'
      },
      @{
        Name = 'gav-total-bound'
        From = '    $detailBudget = [Math]::Max($minimumDiagnosticChars, $auditMaxChars - $coordinatePrefix.Length) # diagnostic coordinate-inclusive bound'
        To = '    $detailBudget = $auditMaxChars # diagnostic coordinate-inclusive bound'
        Expected = '[DIAG-GAV-TOTAL-BOUND]'
      },
      @{
        Name = 'gav-oversized-coordinate-bound'
        From = '    if ($coordinatePrefix.Length -le ($auditMaxChars - $minimumDiagnosticChars)) { # diagnostic oversized-coordinate bound'
        To = '    if ($true) { # diagnostic oversized-coordinate bound'
        Expected = '[DIAG-GAV-TOTAL-BOUND]'
      },
      @{
        Name = 'gav-multiline-preservation'
        From = '  $coordinateMatch = [regex]::Match($Value, ''^(?<coordinate>[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+)\s*=>\s*(?<rest>.*)$'', [System.Text.RegularExpressions.RegexOptions]::Singleline) # diagnostic multiline-GAV preservation'
        To = '  $coordinateMatch = [regex]::Match($Value, ''^(?<coordinate>[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+)\s*=>\s*(?<rest>.*)$'') # diagnostic multiline-GAV preservation'
        Expected = '[DIAG-GAV-NEWLINE]'
      },
      @{
        Name = 'graph-presenter-route'
        From = '  Write-GradleGraphDiagnostics -Errors $graph.Errors -DecodeEscapedNewlines:(-not $IsWindows) # unified graph diagnostic route'
        To = '  $null = $graph.Errors # unified graph diagnostic route'
        Expected = '[DIAG-ENTRY-WRAPPER-EXCEPTION]'
      },
      @{
        Name = 'policy-presenter-route'
        From = '  Write-GradlePolicyDiagnostics -Policy $policy -Resolved $graph.Resolved # unified policy diagnostic route'
        To = '  $null = $policy # unified policy diagnostic route'
        Expected = '[DIAG-ENTRY-WRAPPER-EXCEPTION]'
      }
    )

    foreach ($mutationCase in $diagnosticMutationCases) {
      $matches = [regex]::Matches($source, [regex]::Escape($mutationCase.From)).Count
      if ($matches -ne 1) {
        Write-Error "[DIAGNOSTICS-MUTATION] $($mutationCase.Name) target count=$matches"
        exit 1
      }
      $mutantPath = Join-Path $PSScriptRoot ".license-diagnostics-$PID-$($mutationCase.Name).ps1"
      try {
        [System.IO.File]::WriteAllText($mutantPath, $source.Replace($mutationCase.From, $mutationCase.To), [System.Text.UTF8Encoding]::new($false))
        $mutationOutput = (& pwsh -NoProfile -File $PSCommandPath -Suite diagnostics -ScannerPath $mutantPath -SkipMutations 2>&1 | Out-String)
        $mutationExit = $LASTEXITCODE
        if ($mutationExit -eq 0 -or $mutationOutput -notmatch [regex]::Escape($mutationCase.Expected)) {
          Write-Error "[DIAGNOSTICS-MUTATION] $($mutationCase.Name) did not fail on its semantic inverse (exit=$mutationExit; output=$mutationOutput)"
          exit 1
        }
      } finally {
        if (Test-Path -LiteralPath $mutantPath) { Remove-Item -LiteralPath $mutantPath -Force }
      }
    }
    Write-Host "license-scanner-check(diagnostics mutations): PASS ($($diagnosticMutationCases.Count))"
  }

  Write-Host 'license-scanner-check(diagnostics): PASS'
  exit 0
}

if ($Suite -eq 'policy') {
  function Assert-Policy {
    param(
      [Parameter(Mandatory)][bool]$Condition,
      [Parameter(Mandatory)][string]$Message
    )
    if (-not $Condition) { $failures.Add($Message) }
  }

  $policyRoot = Join-Path ([System.IO.Path]::GetTempPath()) "license-policy-$PID-$([guid]::NewGuid().ToString('N'))"
  $policyGradleHome = Join-Path $policyRoot 'gradle-home'
  $policyExceptionPath = Join-Path $policyRoot 'exceptions.json'

  function Write-PolicyPom {
    param(
      [Parameter(Mandatory)][string]$Coordinate,
      [Parameter(Mandatory)][string]$Xml,
      [string]$Hash = 'fixture-hash'
    )
    $gav = Get-GradleGavParts -Coordinate $Coordinate
    $coordinateRoot = Get-GradleCacheCoordinateRoot -GradleUserHome $policyGradleHome -Coordinate $Coordinate
    $hashRoot = Join-Path $coordinateRoot $Hash
    New-Item -ItemType Directory -Force -Path $hashRoot | Out-Null
    $pomPath = Join-Path $hashRoot "$($gav.Artifact)-$($gav.Version).pom"
    [System.IO.File]::WriteAllText($pomPath, $Xml, [System.Text.UTF8Encoding]::new($false))
    return $pomPath
  }

  function Set-PolicyExceptions([Parameter(Mandatory)][string]$Json) {
    [System.IO.File]::WriteAllText($policyExceptionPath, $Json, [System.Text.UTF8Encoding]::new($false))
  }

  function New-PolicyResolved([Parameter(Mandatory)][string]$Coordinate) {
    return [PSCustomObject]@{ Coordinate = $Coordinate; Configurations = @(':core:testRuntimeClasspath') }
  }

  function Invoke-PolicyFixture([Parameter(Mandatory)][string[]]$Coordinates) {
    $resolved = @($Coordinates | ForEach-Object { New-PolicyResolved -Coordinate $_ })
    return Get-GradleLicensePolicyResult -Resolved $resolved -GradleUserHome $policyGradleHome -ExceptionPath $policyExceptionPath
  }

  try {
    New-Item -ItemType Directory -Force -Path $policyRoot | Out-Null
    Set-PolicyExceptions -Json '[]'

    $multiCoordinate = 'fixture.policy:multi:1.0'
    [void](Write-PolicyPom -Coordinate $multiCoordinate -Xml @'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <groupId>fixture.policy</groupId><artifactId>multi</artifactId><version>1.0</version>
  <licenses>
    <license><name>Apache-2.0</name></license>
    <license><name>LGPL-2.1</name></license>
  </licenses>
</project>
'@)
    $multiResult = Invoke-PolicyFixture -Coordinates @($multiCoordinate)
    $multiLicenses = @($multiResult.Findings | ForEach-Object DeclaredLicense)
    Assert-Policy (
      $multiResult.Violations.Count -eq 0 -and
      ($multiLicenses -join ',') -ceq 'Apache-2.0,LGPL-2.1' -and
      $multiResult.Warnings.Count -eq 1 -and
      $multiResult.Warnings[0].DeclaredLicense -ceq 'LGPL-2.1' -and
      ($multiResult.Findings[0].Configurations -join ',') -ceq ':core:testRuntimeClasspath'
    ) '[POLICY-POM-MULTI] valid multi-license POM did not preserve both decisions/configuration'

    $classificationCases = @(
      @{ License = 'Apache-2.0'; Expected = 'permissive' },
      @{ License = 'LGPL-2.1'; Expected = 'yellow' },
      @{ License = 'EPL-1.0'; Expected = 'forbidden' },
      @{ License = 'GPL-3.0'; Expected = 'plain-gpl' },
      @{ License = 'Mystery Apache License'; Expected = 'unknown' }
    )
    foreach ($case in $classificationCases) {
      Assert-Policy (
        (Get-GradleLicenseClassification -License $case.License) -ceq $case.Expected
      ) "[POLICY-CLASSIFICATION] $($case.License) was not $($case.Expected)"
    }

    $dtdCoordinate = 'fixture.policy:dtd:1.0'
    [void](Write-PolicyPom -Coordinate $dtdCoordinate -Xml @'
<!DOCTYPE project [<!ENTITY policyLicense "Apache-2.0">]>
<project><groupId>fixture.policy</groupId><artifactId>dtd</artifactId><version>1.0</version><licenses><license><name>&policyLicense;</name></license></licenses></project>
'@)
    $dtdResult = Invoke-PolicyFixture -Coordinates @($dtdCoordinate)
    Assert-Policy (
      $dtdResult.Findings.Count -eq 0 -and @($dtdResult.Violations | Where-Object Code -CEQ 'GRADLE-POM').Count -eq 1
    ) '[POLICY-POM-DTD] DTD-bearing POM was not rejected as GRADLE-POM'

    $mismatchCoordinate = 'fixture.policy:mismatch:1.0'
    [void](Write-PolicyPom -Coordinate $mismatchCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>mismatch</artifactId><version>2.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>')
    $mismatchResult = Invoke-PolicyFixture -Coordinates @($mismatchCoordinate)
    Assert-Policy (
      @($mismatchResult.Violations | Where-Object Code -CEQ 'GRADLE-POM').Count -eq 1
    ) '[POLICY-POM-GAV] POM self-declared GAV mismatch was not rejected'

    $blankNameCoordinate = 'fixture.policy:blank-name:1.0'
    [void](Write-PolicyPom -Coordinate $blankNameCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>blank-name</artifactId><version>1.0</version><licenses><license><name> </name></license></licenses></project>')
    $blankNameResult = Invoke-PolicyFixture -Coordinates @($blankNameCoordinate)
    Assert-Policy (
      @($blankNameResult.Violations | Where-Object Code -CEQ 'GRADLE-POM').Count -eq 1
    ) '[POLICY-POM-LICENSE-NAME] blank declared license name was not rejected'

    $duplicatePomCases = @(
      @{ Id = 'group'; Coordinate = 'fixture.policy:duplicate-group:1.0'; Xml = '<project><groupId>fixture.policy</groupId><groupId>other</groupId><artifactId>duplicate-group</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'artifact'; Coordinate = 'fixture.policy:duplicate-artifact:1.0'; Xml = '<project><groupId>fixture.policy</groupId><artifactId>duplicate-artifact</artifactId><artifactId>other</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'version'; Coordinate = 'fixture.policy:duplicate-version:1.0'; Xml = '<project><groupId>fixture.policy</groupId><artifactId>duplicate-version</artifactId><version>1.0</version><version>2.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'parent'; Coordinate = 'fixture.policy:duplicate-parent:1.0'; Xml = '<project><parent><groupId>fixture.policy</groupId><artifactId>parent-a</artifactId><version>1.0</version></parent><parent><groupId>fixture.policy</groupId><artifactId>parent-b</artifactId><version>1.0</version></parent><groupId>fixture.policy</groupId><artifactId>duplicate-parent</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'parent-group'; Coordinate = 'fixture.policy:duplicate-parent-group:1.0'; Xml = '<project><parent><groupId>fixture.policy</groupId><groupId>other</groupId><artifactId>parent</artifactId><version>1.0</version></parent><artifactId>duplicate-parent-group</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'parent-artifact'; Coordinate = 'fixture.policy:duplicate-parent-artifact:1.0'; Xml = '<project><parent><groupId>fixture.policy</groupId><artifactId>parent</artifactId><artifactId>other</artifactId><version>1.0</version></parent><groupId>fixture.policy</groupId><artifactId>duplicate-parent-artifact</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'parent-version'; Coordinate = 'fixture.policy:duplicate-parent-version:1.0'; Xml = '<project><parent><groupId>fixture.policy</groupId><artifactId>parent</artifactId><version>1.0</version><version>2.0</version></parent><groupId>fixture.policy</groupId><artifactId>duplicate-parent-version</artifactId><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'licenses-container'; Coordinate = 'fixture.policy:duplicate-licenses-container:1.0'; Xml = '<project><groupId>fixture.policy</groupId><artifactId>duplicate-licenses-container</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses><licenses><license><name>MIT</name></license></licenses></project>' },
      @{ Id = 'license-name'; Coordinate = 'fixture.policy:duplicate-license-name:1.0'; Xml = '<project><groupId>fixture.policy</groupId><artifactId>duplicate-license-name</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name><name>EPL-1.0</name></license></licenses></project>' }
    )
    foreach ($duplicatePom in $duplicatePomCases) {
      [void](Write-PolicyPom -Coordinate $duplicatePom.Coordinate -Xml $duplicatePom.Xml)
      $duplicatePomResult = Invoke-PolicyFixture -Coordinates @($duplicatePom.Coordinate)
      Assert-Policy (
        @($duplicatePomResult.Violations | Where-Object Code -CEQ 'GRADLE-POM').Count -eq 1
      ) "[POLICY-POM-SINGLETON-$($duplicatePom.Id.ToUpperInvariant())] repeated singleton element was accepted"
    }

    $parentMetadataCases = @(
      @{ Id = 'missing-group'; Coordinate = 'fixture.policy:parent-missing-group:1.0'; Xml = '<project><parent><artifactId>parent</artifactId><version>1.0</version></parent><groupId>fixture.policy</groupId><artifactId>parent-missing-group</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'missing-version'; Coordinate = 'fixture.policy:parent-missing-version:1.0'; Xml = '<project><parent><groupId>fixture.policy</groupId><artifactId>parent</artifactId></parent><groupId>fixture.policy</groupId><artifactId>parent-missing-version</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' },
      @{ Id = 'control-artifact'; Coordinate = 'fixture.policy:parent-control-artifact:1.0'; Xml = '<project><parent><groupId>fixture.policy</groupId><artifactId>parent&#x202E;</artifactId><version>1.0</version></parent><groupId>fixture.policy</groupId><artifactId>parent-control-artifact</artifactId><version>1.0</version><licenses><license><name>Apache-2.0</name></license></licenses></project>' }
    )
    foreach ($parentMetadata in $parentMetadataCases) {
      [void](Write-PolicyPom -Coordinate $parentMetadata.Coordinate -Xml $parentMetadata.Xml)
      $parentMetadataResult = Invoke-PolicyFixture -Coordinates @($parentMetadata.Coordinate)
      Assert-Policy (
        @($parentMetadataResult.Violations | Where-Object Code -CEQ 'GRADLE-POM').Count -eq 1
      ) "[POLICY-POM-PARENT-$($parentMetadata.Id.ToUpperInvariant())] malformed parent GAV was accepted"
    }

    $baselineExceptions = @'
[
  {"coordinate":"fixture.policy:fallback:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/fallback","registered_by":"policy-test","registered_on":"2026-08-19"},
  {"coordinate":"fixture.policy:fallback-no-license:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/fallback-no-license","registered_by":"policy-test","registered_on":"2026-08-19"},
  {"coordinate":"fixture.policy:valid-unknown:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/valid-unknown","registered_by":"policy-test","registered_on":"2026-08-19"},
  {"coordinate":"fixture.policy:declared:1.0","declared_license":"BSD License","license":"BSD-3-Clause","evidence_url":"https://example.invalid/declared","registered_by":"policy-test","registered_on":"2026-08-19"},
  {"coordinate":"fixture.policy:risk:1.0","declared_license":"EPL-1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/risk","registered_by":"policy-test","registered_on":"2026-08-19"}
]
'@
    Set-PolicyExceptions -Json $baselineExceptions
    $fallbackResult = Invoke-PolicyFixture -Coordinates @('fixture.policy:fallback:1.0')
    Assert-Policy (
      $fallbackResult.Violations.Count -eq 0 -and
      $fallbackResult.Findings.Count -eq 1 -and
      $fallbackResult.Findings[0].Source -ceq 'fallback-override' -and
      $fallbackResult.Findings[0].EffectiveLicense -ceq 'Apache-2.0'
    ) '[POLICY-OVERRIDE-FALLBACK] exact missing-POM fallback was not accepted'

    $missingLicenseCoordinate = 'fixture.policy:fallback-no-license:1.0'
    [void](Write-PolicyPom -Coordinate $missingLicenseCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>fallback-no-license</artifactId><version>1.0</version></project>')
    $missingLicenseResult = Invoke-PolicyFixture -Coordinates @($missingLicenseCoordinate)
    Assert-Policy (
      $missingLicenseResult.Violations.Count -eq 0 -and
      $missingLicenseResult.Findings.Count -eq 1 -and
      $missingLicenseResult.Findings[0].Source -ceq 'fallback-override'
    ) '[POLICY-OVERRIDE-MISSING-LICENSE] exact fallback did not cover a valid POM with no license/name'

    Set-PolicyExceptions -Json '[]'
    $missingWithoutFallback = Invoke-PolicyFixture -Coordinates @('fixture.policy:missing-without-fallback:1.0')
    $missingLicenseWithoutFallback = Invoke-PolicyFixture -Coordinates @($missingLicenseCoordinate)
    Assert-Policy (
      @($missingWithoutFallback.Violations | Where-Object Code -CEQ 'GRADLE-METADATA').Count -eq 1 -and
      @($missingLicenseWithoutFallback.Violations | Where-Object Code -CEQ 'GRADLE-METADATA').Count -eq 1
    ) '[POLICY-METADATA-NO-FALLBACK] missing POM or missing license/name passed without an exact fallback'
    Set-PolicyExceptions -Json $baselineExceptions

    $validUnknownCoordinate = 'fixture.policy:valid-unknown:1.0'
    [void](Write-PolicyPom -Coordinate $validUnknownCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>valid-unknown</artifactId><version>1.0</version><licenses><license><name>Mystery License</name></license></licenses></project>')
    $validUnknownResult = Invoke-PolicyFixture -Coordinates @($validUnknownCoordinate)
    Assert-Policy (
      @($validUnknownResult.Violations | Where-Object Code -CEQ 'GRADLE-UNKNOWN').Count -eq 1 -and
      @($validUnknownResult.Findings | Where-Object Source -CEQ 'fallback-override').Count -eq 0
    ) '[POLICY-FALLBACK-STATE] valid unknown POM was incorrectly replaced by a missing-metadata fallback'

    $declaredCoordinate = 'fixture.policy:declared:1.0'
    [void](Write-PolicyPom -Coordinate $declaredCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>declared</artifactId><version>1.0</version><licenses><license><name>BSD License</name></license></licenses></project>')
    $declaredResult = Invoke-PolicyFixture -Coordinates @($declaredCoordinate)
    Assert-Policy (
      $declaredResult.Violations.Count -eq 0 -and
      $declaredResult.Findings.Count -eq 1 -and
      $declaredResult.Findings[0].Source -ceq 'declared-override' -and
      $declaredResult.Findings[0].EffectiveLicense -ceq 'BSD-3-Clause'
    ) '[POLICY-DECLARED-EXACT] exact declared_license mapping was not applied'

    $nearCoordinate = 'fixture.policy:declared-near:1.0'
    [void](Write-PolicyPom -Coordinate $nearCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>declared-near</artifactId><version>1.0</version><licenses><license><name>bsd license</name></license></licenses></project>')
    $nearExceptions = @'
[{"coordinate":"fixture.policy:declared-near:1.0","declared_license":"BSD License","license":"BSD-3-Clause","evidence_url":"https://example.invalid/near","registered_by":"policy-test","registered_on":"2026-08-19"}]
'@
    Set-PolicyExceptions -Json $nearExceptions
    $nearResult = Invoke-PolicyFixture -Coordinates @($nearCoordinate)
    Assert-Policy (
      @($nearResult.Violations | Where-Object Code -CEQ 'GRADLE-UNKNOWN').Count -eq 1
    ) '[POLICY-DECLARED-ORDINAL] case-near declared_license mapping was accepted'

    Set-PolicyExceptions -Json @'
[{"coordinate":"fixture.policy:risk:1.0","declared_license":"EPL-1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/risk","registered_by":"policy-test","registered_on":"2026-08-19"}]
'@
    $riskCoordinate = 'fixture.policy:risk:1.0'
    [void](Write-PolicyPom -Coordinate $riskCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>risk</artifactId><version>1.0</version><licenses><license><name>EPL-1.0</name></license></licenses></project>')
    $riskResult = Invoke-PolicyFixture -Coordinates @($riskCoordinate)
    Assert-Policy (
      @($riskResult.Violations | Where-Object Code -CEQ 'GRADLE-FORBIDDEN').Count -eq 1
    ) '[POLICY-FORBIDDEN-FIRST] declared mapping overrode a forbidden POM license'

    $invalidExceptions = @(
      @{ Id = 'top-level'; Error = $null; Json = '{}' },
      @{ Id = 'non-object'; Error = $null; Json = '[7]' },
      @{ Id = 'duplicate-field'; Error = '字段重复'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","license":"BSD-3-Clause","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'unsupported-field'; Error = '不支持字段'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19","note":"no"}]' },
      @{ Id = 'control'; Error = '控制/格式'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy\u202etest","registered_on":"2026-08-19"}]' },
      @{ Id = 'wildcard'; Error = '具体且安全'; Json = '[{"coordinate":"fixture.policy:*:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'missing-coordinate'; Error = '缺少必填字段 coordinate'; Json = '[{"license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'missing-license'; Error = '缺少必填字段 license'; Json = '[{"coordinate":"fixture.policy:a:1.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'missing-evidence'; Error = '缺少必填字段 evidence_url'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'missing-registrant'; Error = '缺少必填字段 registered_by'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_on":"2026-08-19"}]' },
      @{ Id = 'missing-date'; Error = '缺少必填字段 registered_on'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test"}]' },
      @{ Id = 'empty-registrant'; Error = '缺少必填字段'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"","registered_on":"2026-08-19"}]' },
      @{ Id = 'blank-declared'; Error = 'declared_license 不能为空'; Json = '[{"coordinate":"fixture.policy:a:1.0","declared_license":" ","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'canonical-alias'; Error = '精确 canonical'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"apache 2","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'bad-url'; Error = '绝对 http'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"file:///tmp/evidence","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'bad-date'; Error = 'yyyy-MM-dd'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"19-08-2026"}]' },
      @{ Id = 'non-string'; Error = 'JSON 字符串'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":7,"registered_on":"2026-08-19"}]' },
      @{ Id = 'duplicate-fallback'; Error = '坐标重复'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"},{"coordinate":"fixture.policy:a:1.0","license":"BSD-3-Clause","evidence_url":"https://example.invalid/b","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'duplicate-declared'; Error = 'declared_license 重复'; Json = '[{"coordinate":"fixture.policy:a:1.0","declared_license":"Mystery","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"},{"coordinate":"fixture.policy:a:1.0","declared_license":"Mystery","license":"BSD-3-Clause","evidence_url":"https://example.invalid/b","registered_by":"policy-test","registered_on":"2026-08-19"}]' }
    )
    foreach ($invalid in $invalidExceptions) {
      Set-PolicyExceptions -Json $invalid.Json
      $invalidResult = Invoke-PolicyFixture -Coordinates @('fixture.policy:a:1.0')
      $overrideErrors = @($invalidResult.Violations | Where-Object Code -CEQ 'GRADLE-OVERRIDE')
      Assert-Policy (
        $invalidResult.Findings.Count -eq 0 -and
        $overrideErrors.Count -eq 1 -and
        ([string]::IsNullOrEmpty([string]$invalid.Error) -or $overrideErrors[0].Detail -match [regex]::Escape($invalid.Error))
      ) "[POLICY-OVERRIDE-$($invalid.Id.ToUpperInvariant())] malformed exception did not fail closed"
    }

    $invalidContinueCoordinate = 'fixture.policy:invalid-continue:1.0'
    [void](Write-PolicyPom -Coordinate $invalidContinueCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>invalid-continue</artifactId><version>1.0</version><licenses><license><name>Mystery License</name></license></licenses></project>')
    Set-PolicyExceptions -Json '[{"coordinate":"fixture.policy:invalid-continue:1.0","declared_license":"Mystery License","license":"Apache-2.0","evidence_url":"https://example.invalid/partial","registered_by":"policy-test","registered_on":"2026-08-19"},{"coordinate":"fixture.policy:*:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]'
    $invalidContinueResult = Invoke-PolicyFixture -Coordinates @($invalidContinueCoordinate)
    Assert-Policy (
      @($invalidContinueResult.Violations | Where-Object Code -CEQ 'GRADLE-OVERRIDE').Count -eq 1 -and
      @($invalidContinueResult.Violations | Where-Object Code -CEQ 'GRADLE-UNKNOWN').Count -eq 1 -and
      @($invalidContinueResult.Findings | Where-Object Source -CEQ 'declared-override').Count -eq 0
    ) '[POLICY-OVERRIDE-CONTINUE] invalid exception table retained a partial override or suppressed concrete-GAV evaluation'

    $caseGavCoordinate = 'fixture.policy:case-gav:1.0'
    [void](Write-PolicyPom -Coordinate $caseGavCoordinate -Xml '<project><groupId>fixture.policy</groupId><artifactId>case-gav</artifactId><version>1.0</version><licenses><license><name>Mystery License</name></license></licenses></project>')
    Set-PolicyExceptions -Json '[{"coordinate":"Fixture.Policy:case-gav:1.0","declared_license":"Mystery License","license":"Apache-2.0","evidence_url":"https://example.invalid/case-gav","registered_by":"policy-test","registered_on":"2026-08-19"}]'
    $caseGavResult = Invoke-PolicyFixture -Coordinates @($caseGavCoordinate)
    Assert-Policy (
      @($caseGavResult.Violations | Where-Object Code -CEQ 'GRADLE-UNKNOWN').Count -eq 1 -and
      @($caseGavResult.Findings | Where-Object Source -CEQ 'declared-override').Count -eq 0
    ) '[POLICY-OVERRIDE-GAV-ORDINAL] case-near GAV matched an exception record'

    $emptyGraphRoot = Join-Path $policyRoot 'empty-graph-root'
    New-Item -ItemType Directory -Force -Path (Join-Path $emptyGraphRoot 'configs/licenses') | Out-Null
    [System.IO.File]::WriteAllText(
      (Join-Path $emptyGraphRoot 'configs/licenses/gradle-exceptions.json'),
      '[{"coordinate":"fixture.policy:*:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]',
      [System.Text.UTF8Encoding]::new($false)
    )
    $script:policyMainResolved = @()
    function Get-GradleResolvedGraphs {
      return [PSCustomObject]@{ Resolved = @($script:policyMainResolved); Errors = @() }
    }
    $script:bad = @(); $script:warn = @()
    Invoke-GradleLicenseScan -Root $emptyGraphRoot
    Assert-Policy (
      @($script:bad | Where-Object { $_ -match '\[GRADLE-OVERRIDE\]' }).Count -eq 1
    ) '[POLICY-EMPTY-GRAPH-OVERRIDE] empty resolved graph skipped exception-table validation'

    $policyMainRoot = Join-Path $policyRoot 'main-path-root'
    New-Item -ItemType Directory -Force -Path (Join-Path $policyMainRoot 'configs/licenses') | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $policyMainRoot 'configs/licenses/gradle-exceptions.json'), '[]', [System.Text.UTF8Encoding]::new($false))
    $savedPolicyGradleHome = $env:GRADLE_USER_HOME
    try {
      $env:GRADLE_USER_HOME = $policyGradleHome
      $mainCases = @(
        @{ Id = 'permissive'; License = 'Apache-2.0'; Distributes = $false; Bad = 0; Warn = 0 },
        @{ Id = 'yellow'; License = 'LGPL-2.1'; Distributes = $false; Bad = 0; Warn = 1 },
        @{ Id = 'gpl-private'; License = 'GPL-3.0'; Distributes = $false; Bad = 0; Warn = 1 },
        @{ Id = 'gpl-distributed'; License = 'GPL-3.0'; Distributes = $true; Bad = 1; Warn = 0 },
        @{ Id = 'forbidden'; License = 'EPL-1.0'; Distributes = $false; Bad = 1; Warn = 0 },
        @{ Id = 'unknown'; License = 'Mystery License'; Distributes = $false; Bad = 1; Warn = 0 }
      )
      foreach ($mainCase in $mainCases) {
        $coordinate = "fixture.policy:main-$($mainCase.Id):1.0"
        [void](Write-PolicyPom -Coordinate $coordinate -Xml "<project><groupId>fixture.policy</groupId><artifactId>main-$($mainCase.Id)</artifactId><version>1.0</version><licenses><license><name>$($mainCase.License)</name></license></licenses></project>")
        $script:policyMainResolved = @([PSCustomObject]@{ Coordinate = $coordinate; Configurations = @(':core:testRuntimeClasspath') })
        $script:bad = @(); $script:warn = @(); $script:Distributes = [bool]$mainCase.Distributes
        Invoke-GradleLicenseScan -Root $policyMainRoot
        Assert-Policy (
          $script:bad.Count -eq $mainCase.Bad -and $script:warn.Count -eq $mainCase.Warn
        ) "[POLICY-MAIN-$($mainCase.Id.ToUpperInvariant())] production caller outcome was bad=$($script:bad.Count), warn=$($script:warn.Count)"
      }
    } finally {
      $env:GRADLE_USER_HOME = $savedPolicyGradleHome
      $script:Distributes = $false
    }
  } catch {
    Assert-Policy $false "[POLICY-SETUP] policy suite failed: $($_.Exception.Message)"
  } finally {
    if (Test-Path -LiteralPath $policyRoot) { Remove-Item -LiteralPath $policyRoot -Recurse -Force }
  }

  if ($failures.Count -gt 0) {
    Write-Error "[POLICY-CONTRACT] $($failures -join "`n[POLICY-CONTRACT] ")"
    exit 1
  }

  if (-not $SkipMutations) {
    $source = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $ScannerPath))
    $policyMutationCases = @(
      @{
        Name = 'pom-dtd'
        From = '      $readerSettings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit'
        To = '      $readerSettings.DtdProcessing = [System.Xml.DtdProcessing]::Parse'
        Expected = '[POLICY-POM-DTD]'
      },
      @{
        Name = 'pom-gav'
        From = '      if ($declaredGroup -cne $group -or $declaredArtifact -cne $artifact -or $declaredVersion -cne $version) {'
        To = '      if ($false) {'
        Expected = '[POLICY-POM-GAV]'
      },
      @{
        Name = 'pom-license-name'
        From = '        if ([string]::IsNullOrWhiteSpace($licenseName)) { throw ''POM 中每个已声明 license 都必须有非空 name。'' } # require every declared license name'
        To = '        if ([string]::IsNullOrWhiteSpace($licenseName)) { continue } # require every declared license name'
        Expected = '[POLICY-POM-LICENSE-NAME]'
      },
      @{
        Name = 'pom-singleton'
        From = '  if ($nodes.Count -gt 1) { throw "POM 元素 $LocalName 必须至多出现一次。" } # POM singleton ambiguity guard'
        To = '  if ($false) { throw "POM 元素 $LocalName 必须至多出现一次。" } # POM singleton ambiguity guard'
        Expected = '[POLICY-POM-SINGLETON-PARENT]'
      },
      @{
        Name = 'pom-parent-required'
        From = '  if ($null -eq $Node -or [string]::IsNullOrWhiteSpace([string]$Node.InnerText)) { throw "POM $Field 缺失或为空。" } # POM required scalar guard'
        To = '  if ($null -eq $Node -or [string]::IsNullOrWhiteSpace([string]$Node.InnerText)) { return '''' } # POM required scalar guard'
        Expected = '[POLICY-POM-PARENT-MISSING-GROUP]'
      },
      @{
        Name = 'pom-parent-scalar'
        From = '  Assert-GradleMetadataScalar -Field "POM $Field" -Value $value # POM required scalar safety guard'
        To = '  $null = $value # POM required scalar safety guard'
        Expected = '[POLICY-POM-PARENT-CONTROL-ARTIFACT]'
      },
      @{
        Name = 'classification-unknown'
        From = "  return 'unknown'"
        To = "  return 'permissive'"
        Expected = '[POLICY-CLASSIFICATION]'
      },
      @{
        Name = 'override-exact-gav'
        From = '      if ($null -eq (Get-GradleGavParts -Coordinate $coordinate)) {'
        To = '      if ($false) {'
        Expected = '[POLICY-OVERRIDE-WILDCARD]'
      },
      @{
        Name = 'override-gav-ordinal'
        From = '  $empty = [System.Collections.Generic.Dictionary[string,object]]::new([System.StringComparer]::Ordinal) # exception coordinate ordinal map'
        To = '  $empty = [System.Collections.Generic.Dictionary[string,object]]::new([System.StringComparer]::OrdinalIgnoreCase) # exception coordinate ordinal map'
        Expected = '[POLICY-OVERRIDE-GAV-ORDINAL]'
      },
      @{
        Name = 'override-required-field'
        From = "      foreach (`$field in @('coordinate', 'license', 'evidence_url', 'registered_by', 'registered_on')) {"
        To = "      foreach (`$field in @('coordinate', 'license', 'evidence_url', 'registered_on')) {"
        Expected = '[POLICY-OVERRIDE-EMPTY-REGISTRANT]'
      },
      @{
        Name = 'override-duplicate-field'
        From = '          if (-not $seenFields.Add($field)) { throw "记录字段重复（大小写完全相同）：$field。" } # exception duplicate property guard'
        To = '          [void]$seenFields.Add($field) # exception duplicate property guard'
        Expected = '[POLICY-OVERRIDE-DUPLICATE-FIELD]'
      },
      @{
        Name = 'override-supported-field'
        From = "      ForEach-Object { [void]`$allowedFields.Add(`$_) }"
        To = "      ForEach-Object { [void]`$allowedFields.Add(`$_) }; [void]`$allowedFields.Add('note')"
        Expected = '[POLICY-OVERRIDE-UNSUPPORTED-FIELD]'
      },
      @{
        Name = 'override-json-string'
        From = '          if ($property.Value.ValueKind -ne [System.Text.Json.JsonValueKind]::String) {'
        To = '          if ($false) {'
        Expected = '[POLICY-OVERRIDE-NON-STRING]'
      },
      @{
        Name = 'override-metadata-control'
        From = '        Assert-GradleMetadataScalar -Field ([string]$field) -Value ([string]$record[$field])'
        To = '        $null = [string]$record[$field]'
        Expected = '[POLICY-OVERRIDE-CONTROL]'
      },
      @{
        Name = 'override-declared-nonblank'
        From = '        if ([string]::IsNullOrWhiteSpace($declaredLicense)) { throw "declared_license 不能为空：$coordinate" } # exception declared license nonblank guard'
        To = '        if ($false) { throw "declared_license 不能为空：$coordinate" } # exception declared license nonblank guard'
        Expected = '[POLICY-OVERRIDE-BLANK-DECLARED]'
      },
      @{
        Name = 'override-url'
        From = "      if (-not [uri]::TryCreate(`$evidenceUrl, [System.UriKind]::Absolute, [ref]`$uri) -or `$uri.Scheme -notin @('http', 'https')) { # exception evidence URL guard"
        To = '      if ($false) { # exception evidence URL guard'
        Expected = '[POLICY-OVERRIDE-BAD-URL]'
      },
      @{
        Name = 'override-date'
        From = "      if (-not [datetime]::TryParseExact([string]`$record.registered_on, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None, [ref]`$registeredOn)) { # exception registration date guard"
        To = '      if ($false) { # exception registration date guard'
        Expected = '[POLICY-OVERRIDE-BAD-DATE]'
      },
      @{
        Name = 'override-duplicate-fallback'
        From = '        if ($null -ne $bucket.Fallback) { throw "坐标重复、缺失元数据回退有歧义：$coordinate" } # exception duplicate fallback guard'
        To = '        if ($false) { throw "坐标重复、缺失元数据回退有歧义：$coordinate" } # exception duplicate fallback guard'
        Expected = '[POLICY-OVERRIDE-DUPLICATE-FALLBACK]'
      },
      @{
        Name = 'override-duplicate-declared'
        From = '        if ($bucket.DeclaredLicenses.ContainsKey($declaredLicense)) { # exception duplicate declared mapping guard'
        To = '        if ($false) { # exception duplicate declared mapping guard'
        Expected = '[POLICY-OVERRIDE-DUPLICATE-DECLARED]'
      },
      @{
        Name = 'override-canonical'
        From = '      if (-not (Test-GradleExceptionCanonicalLicense -License $entry.License)) {'
        To = '      if ($false) {'
        Expected = '[POLICY-OVERRIDE-CANONICAL-ALIAS]'
      },
      @{
        Name = 'override-partial-discard'
        From = '    $failedEntries = [System.Collections.Generic.Dictionary[string,object]]::new([System.StringComparer]::Ordinal) # discard partial exception records'
        To = '    $failedEntries = $empty # discard partial exception records'
        Expected = '[POLICY-OVERRIDE-CONTINUE]'
      },
      @{
        Name = 'declared-ordinal'
        From = '          DeclaredLicenses = [System.Collections.Generic.Dictionary[string,object]]::new([System.StringComparer]::Ordinal)'
        To = '          DeclaredLicenses = [System.Collections.Generic.Dictionary[string,object]]::new([System.StringComparer]::OrdinalIgnoreCase)'
        Expected = '[POLICY-DECLARED-ORDINAL]'
      },
      @{
        Name = 'fallback-state'
        From = '    if ($pom.State -in @(''Missing'', ''MissingLicense'')) { # policy fallback state gate'
        To = '    if ($pom.State -in @(''Missing'', ''MissingLicense'', ''Valid'')) { # policy fallback state gate'
        Expected = '[POLICY-FALLBACK-STATE]'
      },
      @{
        Name = 'fallback-missing-license'
        From = '    if ($pom.State -in @(''Missing'', ''MissingLicense'')) { # policy fallback state gate'
        To = '    if ($pom.State -eq ''Missing'') { # policy fallback state gate'
        Expected = '[POLICY-OVERRIDE-MISSING-LICENSE]'
      },
      @{
        Name = 'metadata-no-fallback'
        From = '        $violations.Add($missingMetadataViolation) # policy missing metadata fail-closed record'
        To = '        $null = $missingMetadataViolation # policy missing metadata fail-closed record'
        Expected = '[POLICY-METADATA-NO-FALLBACK]'
      },
      @{
        Name = 'forbidden-precedence'
        From = '      } elseif ($classification -eq ''forbidden'') { # policy forbidden precedence'
        To = '      } elseif ($false) { # policy forbidden precedence'
        Expected = '[POLICY-FORBIDDEN-FIRST]'
      },
      @{
        Name = 'main-yellow'
        From = '        $script:warn += Get-GradleAuditText -Value "$($finding.Coordinate) => $($finding.DeclaredLicense)（Gradle 黄牌：需人工确认用途/链接方式）" # structured yellow warning'
        To = '        $null = $finding # structured yellow warning'
        Expected = '[POLICY-MAIN-YELLOW]'
      },
      @{
        Name = 'main-gpl-private'
        From = '        $script:warn += Get-GradleAuditText -Value "$($finding.Coordinate) => $($finding.DeclaredLicense)（Gradle 黄牌：纯 GPL 且本项目声明不分发[Distributes=`$false]；变 public 前用 -Strict 复核）" # structured plain-GPL warning'
        To = '        $null = $finding # structured plain-GPL warning'
        Expected = '[POLICY-MAIN-GPL-PRIVATE]'
      },
      @{
        Name = 'main-forbidden'
        From = '      Add-GradleMetadataNonCompliance "$Coordinate => $license [GRADLE-FORBIDDEN]" # direct forbidden classification'
        To = '      $null = $finding # direct forbidden classification'
        Expected = '[POLICY-MAIN-GPL-DISTRIBUTED]'
      },
      @{
        Name = 'main-unknown'
        From = '      Add-GradleMetadataNonCompliance "$($finding.Coordinate) => $($finding.Detail) [GRADLE-UNKNOWN]" # structured unknown classification'
        To = '      $null = $finding # structured unknown classification'
        Expected = '[POLICY-MAIN-UNKNOWN]'
      }
    )

    foreach ($mutationCase in $policyMutationCases) {
      $matches = [regex]::Matches($source, [regex]::Escape($mutationCase.From)).Count
      if ($matches -ne 1) {
        Write-Error "[POLICY-MUTATION] $($mutationCase.Name) target count=$matches"
        exit 1
      }
      $mutantPath = Join-Path $PSScriptRoot ".license-policy-$PID-$($mutationCase.Name).ps1"
      try {
        [System.IO.File]::WriteAllText($mutantPath, $source.Replace($mutationCase.From, $mutationCase.To), [System.Text.UTF8Encoding]::new($false))
        $mutationOutput = (& pwsh -NoProfile -File $PSCommandPath -Suite policy -ScannerPath $mutantPath -SkipMutations 2>&1 | Out-String)
        $mutationExit = $LASTEXITCODE
        if ($mutationExit -eq 0 -or $mutationOutput -notmatch [regex]::Escape($mutationCase.Expected)) {
          Write-Error "[POLICY-MUTATION] $($mutationCase.Name) did not fail on its semantic inverse (exit=$mutationExit; output=$mutationOutput)"
          exit 1
        }
      } finally {
        if (Test-Path -LiteralPath $mutantPath) { Remove-Item -LiteralPath $mutantPath -Force }
      }
    }
    Write-Host "license-scanner-check(policy mutations): PASS ($($policyMutationCases.Count))"
  }

  Write-Host 'license-scanner-check(policy): PASS'
  exit 0
}

# Break caught: accepting Gradle constraint-only `(c)` rows would scan a declaration that is not a
# resolved runtime component. A repeated resolved component `(*)` remains a real graph member.
$constraintResult = Get-GradleCoordinatesFromDependencyOutput -Output @(
  '+--- fixture.constraint:only:1.0 (c)',
  '+--- fixture.actual:node:2.0',
  '\--- fixture.actual:node:2.0 (*)'
)
$constraintCoordinates = @($constraintResult.Coordinates)
Assert-Graph ($constraintResult.Errors.Count -eq 0) "constraint fixture produced parser errors: $($constraintResult.Errors -join ' | ')"
Assert-Graph (
  @($constraintCoordinates | Where-Object { $_ -ceq 'fixture.constraint:only:1.0' }).Count -eq 0
) "constraint-only row entered resolved GAV set: $($constraintCoordinates -join ', ')"
Assert-Graph (
  $constraintCoordinates.Count -eq 1 -and
  $constraintCoordinates[0] -ceq 'fixture.actual:node:2.0'
) "resolved duplicate GAV was not deduplicated: $($constraintCoordinates -join ', ')"

$parserCases = @(
  @{
    Name = 'resolved-version'
    Lines = @('+--- fixture.redirect:artifact:0.9 -> 1.0', '\--- fixture.redirect:artifact:1.0 (*)')
    Coordinates = @('fixture.redirect:artifact:1.0')
    ErrorCodes = @()
  },
  @{
    Name = 'project-boundary'
    Lines = @('+--- project :core', '+--- project :source -> project :core', '\--- project :source -> fixture.external:artifact:1.2.3')
    Coordinates = @('fixture.external:artifact:1.2.3')
    ErrorCodes = @()
  },
  @{
    Name = 'selected-targets'
    Lines = @('+--- old.group:old-artifact:1.0 -> project :core', '\--- old.group:old-artifact:1.0 -> new.group:new-artifact:2.0')
    Coordinates = @('new.group:new-artifact:2.0')
    ErrorCodes = @()
  },
  @{
    Name = 'unresolved-and-malformed'
    Lines = @('+--- fixture.unresolved:artifact:1.0 (n)', '+--- project :source ->', '\--- malformed external edge')
    Coordinates = @()
    ErrorCodes = @('GRADLE-UNRESOLVED', 'GRADLE-PARSE', 'GRADLE-PARSE')
  },
  @{
    Name = 'non-concrete'
    Lines = @('+--- fixture.invalid:artifact:..', '\--- fixture.invalid:artifact:latest.release')
    Coordinates = @()
    ErrorCodes = @('GRADLE-PARSE', 'GRADLE-PARSE')
  }
)
foreach ($case in $parserCases) {
  $result = Get-GradleCoordinatesFromDependencyOutput -Output $case.Lines
  $actualCoordinates = @($result.Coordinates)
  $actualCodes = @($result.Errors | ForEach-Object {
    if ($_ -match '\[(GRADLE-[A-Z-]+)\]\s*$') { $Matches[1] } else { 'UNCLASSIFIED' }
  })
  Assert-Graph (($actualCoordinates -join ',') -ceq ($case.Coordinates -join ',')) "parser/$($case.Name) returned wrong GAVs: $($actualCoordinates -join ', ')"
  Assert-Graph (($actualCodes -join ',') -ceq ($case.ErrorCodes -join ',')) "parser/$($case.Name) returned wrong error codes: $($actualCodes -join ', ')"
}

# Break caught: graph collection must be independently consumable by policy code. It must execute
# exactly the four approved configurations offline and return resolved GAVs with their provenance.
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) "license-graph-$PID-$([guid]::NewGuid().ToString('N'))"
$hadGradleUserHome = Test-Path Env:GRADLE_USER_HOME
$savedGradleUserHome = $env:GRADLE_USER_HOME
try {
  $androidRoot = Join-Path $fixtureRoot 'android'
  $gradleHome = Join-Path $fixtureRoot 'gradle-home'
  $wrapperDir = Join-Path $androidRoot 'gradle/wrapper'
  $distributionDir = Join-Path $gradleHome 'wrapper/dists/gradle-9.7.0-bin/d4tj7w02tcgubx9zk9hbippn6'
  $distributionRoot = Join-Path $distributionDir 'gradle-9.7.0'
  $nativeCacheRoot = Join-Path $gradleHome 'caches/modules-2/files-2.1'
  $nativeArtifactRoot = Join-Path $nativeCacheRoot 'fixture.group/fixture-artifact/1.0/fixture-hash'
  $nativeMetadataRoot = Join-Path $gradleHome 'caches/modules-2/metadata-2.107'
  foreach ($directory in @(
    $wrapperDir,
    (Join-Path $distributionRoot 'lib'),
    (Join-Path $distributionRoot 'bin'),
    $nativeArtifactRoot,
    $nativeMetadataRoot
  )) { New-Item -ItemType Directory -Force -Path $directory | Out-Null }
  Set-Content -LiteralPath (Join-Path $wrapperDir 'gradle-wrapper.properties') -Encoding utf8 -Value 'distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.0-bin.zip'
  foreach ($file in @(
    (Join-Path $androidRoot 'gradlew'),
    (Join-Path $androidRoot 'gradlew.bat'),
    (Join-Path $distributionDir 'gradle-9.7.0-bin.zip.ok'),
    (Join-Path $distributionRoot 'lib/gradle-launcher-9.7.0.jar'),
    (Join-Path $distributionRoot 'bin/gradle'),
    (Join-Path $distributionRoot 'bin/gradle.bat'),
    (Join-Path $nativeArtifactRoot 'fixture-artifact-1.0.pom'),
    (Join-Path $nativeMetadataRoot 'module-metadata.bin')
  )) { Set-Content -LiteralPath $file -Encoding utf8 -Value 'fixture' }

  $invocations = [System.Collections.Generic.List[object]]::new()
  $reports = @{
    runtimeClasspath = @('+--- fixture.core:runtime:1.0')
    testRuntimeClasspath = @('+--- org.testng:testng:7.0.0', '\--- fixture.constraint:only:1.0 (c)')
    debugRuntimeClasspath = @('+--- fixture.app:debug:1.0')
    releaseRuntimeClasspath = @('+--- fixture.app:release:1.0')
  }
  $invoker = {
    param([string]$Command, [string[]]$Arguments)
    $invocations.Add([PSCustomObject]@{ Command = $Command; Arguments = @($Arguments); GradleUserHome = $env:GRADLE_USER_HOME })
    $configurationIndex = [Array]::IndexOf($Arguments, '--configuration')
    $configuration = if ($configurationIndex -ge 0) { $Arguments[$configurationIndex + 1] } else { '' }
    [PSCustomObject]@{ ExitCode = 0; Output = @($reports[$configuration]) }
  }.GetNewClosure()

  try {
    $ambientColdHome = Join-Path $fixtureRoot 'ambient-cold-gradle-home'
    $env:GRADLE_USER_HOME = $ambientColdHome
    $graphResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    $resolvedCoordinates = @($graphResult.Resolved | ForEach-Object Coordinate)
    Assert-Graph ($graphResult.Errors.Count -eq 0) "graph collector returned errors: $($graphResult.Errors | ConvertTo-Json -Compress)"
    Assert-Graph ($invocations.Count -eq 4) "graph collector invoked $($invocations.Count) configurations instead of 4"
    Assert-Graph (
      ($resolvedCoordinates -join ',') -ceq 'fixture.app:debug:1.0,fixture.app:release:1.0,fixture.core:runtime:1.0,org.testng:testng:7.0.0'
    ) "graph collector returned wrong concrete GAV set: $($resolvedCoordinates -join ', ')"
    $testNg = @($graphResult.Resolved | Where-Object Coordinate -CEQ 'org.testng:testng:7.0.0')
    Assert-Graph (
      $testNg.Count -eq 1 -and ($testNg[0].Configurations -join ',') -ceq ':core:testRuntimeClasspath'
    ) "graph collector lost configuration provenance for TestNG"
    $expectedWindowsWrapper = Join-Path $androidRoot 'gradlew.bat'
    $expectedConfigurations = [ordered]@{
      runtimeClasspath = ':core:dependencies'
      testRuntimeClasspath = ':core:dependencies'
      debugRuntimeClasspath = ':app:dependencies'
      releaseRuntimeClasspath = ':app:dependencies'
    }
    foreach ($configuration in $expectedConfigurations.Keys) {
      $matchingCall = @($invocations | Where-Object {
        $_.Command -ceq $expectedWindowsWrapper -and
        ($_.Arguments -join "`u{001F}") -match "(?:^|`u{001F})--configuration`u{001F}$([regex]::Escape($configuration))(?:$|`u{001F})"
      })
      Assert-Graph ($matchingCall.Count -eq 1) "Windows graph call for $configuration was not exact"
      if ($matchingCall.Count -eq 1) {
        Assert-Graph ($matchingCall[0].GradleUserHome -ceq $gradleHome) "Windows $configuration did not bind preflighted GradleUserHome"
        Assert-Graph (@($matchingCall[0].Arguments | Where-Object { $_ -ceq '--offline' }).Count -eq 1) "Windows $configuration call omitted --offline"
        Assert-Graph (@($matchingCall[0].Arguments | Where-Object { $_ -ceq '--no-daemon' }).Count -eq 1) "Windows $configuration call omitted --no-daemon"
        Assert-Graph (@($matchingCall[0].Arguments | Where-Object { $_ -ceq $expectedConfigurations[$configuration] }).Count -eq 1) "Windows $configuration used wrong Gradle project task"
      }
    }
    Assert-Graph ($env:GRADLE_USER_HOME -ceq $ambientColdHome) "graph collector did not restore ambient GradleUserHome"

    # Break caught: the repository's POSIX wrapper is mode 100644, so Unix must invoke it through sh.
    $invocations.Clear()
    $unixResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $false -Invoker $invoker
    $expectedUnixWrapper = Join-Path $androidRoot 'gradlew'
    Assert-Graph ($unixResult.Errors.Count -eq 0) "Unix graph collector returned errors"
    Assert-Graph ($invocations.Count -eq 4) "Unix graph collector invoked $($invocations.Count) configurations instead of 4"
    foreach ($call in $invocations) {
      Assert-Graph ($call.Command -ceq 'sh') "Unix graph collector did not invoke sh: $($call.Command)"
      Assert-Graph ($call.Arguments.Count -gt 0 -and $call.Arguments[0] -ceq $expectedUnixWrapper) "Unix graph collector did not pass gradlew as sh argv[0]"
    }

    # Break caught: each nonzero Gradle subprocess must remain a graph error with target and exit code.
    $failureInvoker = {
      param([string]$Command, [string[]]$Arguments)
      [PSCustomObject]@{
        ExitCode = 42
        Output = @('simulated Gradle failure detail')
      }
    }
    $failureResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $failureInvoker
    Assert-Graph ($failureResult.Errors.Count -eq 4) "nonzero Gradle fixture did not return one error per approved graph"
    $failureLabels = @($failureResult.Errors.Configuration | Sort-Object)
    Assert-Graph (
      ($failureLabels -join ',') -ceq ':app:debugRuntimeClasspath,:app:releaseRuntimeClasspath,:core:runtimeClasspath,:core:testRuntimeClasspath'
    ) "nonzero Gradle error lost target provenance"
    foreach ($errorRecord in $failureResult.Errors) {
      Assert-Graph ($errorRecord.Code -ceq 'GRADLE-SUBPROCESS' -and $errorRecord.ExitCode -eq 42) "nonzero Gradle error lost code/exit"
    }

    # A warm ambient cache must not authorize a different, cold caller-supplied cache.
    $coldGradleHome = Join-Path $fixtureRoot 'caller-cold-gradle-home'
    New-Item -ItemType Directory -Force -Path $coldGradleHome | Out-Null
    Copy-Item -LiteralPath (Join-Path $gradleHome 'wrapper') -Destination (Join-Path $coldGradleHome 'wrapper') -Recurse
    $env:GRADLE_USER_HOME = $gradleHome
    $invocations.Clear()
    $mismatchedCacheResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $coldGradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $mismatchedCacheResult.Errors.Count -eq 1 -and $mismatchedCacheResult.Errors[0].Code -ceq 'GRADLE-CACHE-OFFLINE'
    ) "cold caller cache with warm ambient cache did not return GRADLE-CACHE-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "cold caller cache with warm ambient cache still started $($invocations.Count) wrapper calls"
    Assert-Graph ($env:GRADLE_USER_HOME -ceq $gradleHome) "cold-cache preflight changed ambient GradleUserHome"

    # Break caught: an absent native dependency cache must fail before any wrapper process starts.
    Remove-Item -LiteralPath $nativeCacheRoot -Recurse -Force
    $invocations.Clear()
    $missingCacheResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $missingCacheResult.Errors.Count -eq 1 -and $missingCacheResult.Errors[0].Code -ceq 'GRADLE-CACHE-OFFLINE'
    ) "missing native cache did not return GRADLE-CACHE-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "missing native cache still started $($invocations.Count) wrapper calls"

    # Break caught: an empty files-2.1 directory is not a warmed native dependency cache.
    New-Item -ItemType Directory -Force -Path $nativeCacheRoot | Out-Null
    $invocations.Clear()
    $emptyCacheResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $emptyCacheResult.Errors.Count -eq 1 -and $emptyCacheResult.Errors[0].Code -ceq 'GRADLE-CACHE-OFFLINE'
    ) "empty native cache did not return GRADLE-CACHE-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "empty native cache still started $($invocations.Count) wrapper calls"

    # A directory-only Maven cache shape is still cold: no resolved artifact/POM was cached.
    New-Item -ItemType Directory -Force -Path $nativeArtifactRoot | Out-Null
    $invocations.Clear()
    $emptyArtifactResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $emptyArtifactResult.Errors.Count -eq 1 -and $emptyArtifactResult.Errors[0].Code -ceq 'GRADLE-CACHE-OFFLINE'
    ) "empty artifact subtree did not return GRADLE-CACHE-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "empty artifact subtree still started $($invocations.Count) wrapper calls"

    # Cached files without Gradle's module-resolution metadata cannot prove an offline graph is ready.
    Set-Content -LiteralPath (Join-Path $nativeArtifactRoot 'fixture-artifact-1.0.pom') -Encoding utf8 -Value 'fixture'
    Remove-Item -LiteralPath $nativeMetadataRoot -Recurse -Force
    $invocations.Clear()
    $missingMetadataResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $missingMetadataResult.Errors.Count -eq 1 -and $missingMetadataResult.Errors[0].Code -ceq 'GRADLE-CACHE-OFFLINE'
    ) "missing native metadata did not return GRADLE-CACHE-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "missing native metadata still started $($invocations.Count) wrapper calls"

    New-Item -ItemType Directory -Force -Path $nativeMetadataRoot | Out-Null
    $invocations.Clear()
    $emptyMetadataResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $emptyMetadataResult.Errors.Count -eq 1 -and $emptyMetadataResult.Errors[0].Code -ceq 'GRADLE-CACHE-OFFLINE'
    ) "empty native metadata did not return GRADLE-CACHE-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "empty native metadata still started $($invocations.Count) wrapper calls"

    # Gradle 9.7 consumes metadata-2.107; a populated older format is not an offline-ready native cache.
    Remove-Item -LiteralPath $nativeMetadataRoot -Recurse -Force
    $staleMetadataRoot = Join-Path $gradleHome 'caches/modules-2/metadata-2.106'
    New-Item -ItemType Directory -Force -Path $staleMetadataRoot | Out-Null
    Set-Content -LiteralPath (Join-Path $staleMetadataRoot 'module-metadata.bin') -Encoding utf8 -Value 'stale fixture'
    $invocations.Clear()
    $staleMetadataResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $staleMetadataResult.Errors.Count -eq 1 -and $staleMetadataResult.Errors[0].Code -ceq 'GRADLE-CACHE-OFFLINE'
    ) "stale native metadata format did not return GRADLE-CACHE-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "[GRAPH-STALE-METADATA-STARTED] stale native metadata format still started $($invocations.Count) wrapper calls"
    Remove-Item -LiteralPath $staleMetadataRoot -Recurse -Force
    New-Item -ItemType Directory -Force -Path $nativeMetadataRoot | Out-Null

    # Existing wrapper readiness remains a graph boundary: missing completion marker is zero-start.
    Set-Content -LiteralPath (Join-Path $nativeMetadataRoot 'module-metadata.bin') -Encoding utf8 -Value 'fixture'
    Remove-Item -LiteralPath (Join-Path $distributionDir 'gradle-9.7.0-bin.zip.ok') -Force
    $invocations.Clear()
    $missingDistributionResult = Get-GradleResolvedGraphs -Root $fixtureRoot -GradleUserHome $gradleHome -UseWindows $true -Invoker $invoker
    Assert-Graph (
      $missingDistributionResult.Errors.Count -eq 1 -and $missingDistributionResult.Errors[0].Code -ceq 'GRADLE-WRAPPER-OFFLINE'
    ) "missing wrapper completion marker did not return GRADLE-WRAPPER-OFFLINE"
    Assert-Graph ($invocations.Count -eq 0) "incomplete wrapper distribution still started $($invocations.Count) wrapper calls"
  } catch {
    Assert-Graph $false "resolved graph API failed: $($_.Exception.Message)"
  }
} finally {
  if ($hadGradleUserHome) { $env:GRADLE_USER_HOME = $savedGradleUserHome }
  else { Remove-Item Env:GRADLE_USER_HOME -ErrorAction SilentlyContinue }
  if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
}

if ($failures.Count -gt 0) {
  Write-Error "[GRAPH-CONTRACT] $($failures -join "`n[GRAPH-CONTRACT] ")"
  exit 1
}

if (-not $SkipMutations) {
  $source = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $ScannerPath))
  $mutationCases = @(
    @{
      Name = 'constraint'
      From = '    if ($body -match ''\s+\(c\)\s*$'') { continue } # exclude Gradle constraint-only edge'
      To = '    if ($false) { continue } # exclude Gradle constraint-only edge'
      Expected = 'constraint-only row entered resolved GAV set'
    },
    @{
      Name = 'concrete-gav'
      From = '    if ($null -eq (Get-GradleGavParts -Coordinate $resolvedCoordinate)) {'
      To = '    if ($false) {'
      Expected = 'parser/non-concrete returned wrong GAVs'
    },
    @{
      Name = 'direct-project'
      From = '        continue # direct internal Gradle project edge'
      To = '        $body = $body # direct internal Gradle project edge'
      Expected = 'parser/project-boundary returned wrong error codes'
    },
    @{
      Name = 'redirected-internal-project'
      From = '        if ($body -match $internalProjectPattern) { continue }'
      To = '        if ($false) { continue }'
      Expected = 'parser/project-boundary returned wrong error codes'
    },
    @{
      Name = 'selected-project-target'
      From = '      if ($selectedTarget -match $internalProjectPattern) { continue } # selected internal project target'
      To = '      if ($false) { continue } # selected internal project target'
      Expected = 'parser/selected-targets returned wrong error codes'
    },
    @{
      Name = 'selected-module-target'
      From = '        $body = $selectedTarget # selected external module target'
      To = '        continue # selected external module target'
      Expected = 'parser/selected-targets returned wrong GAVs'
    },
    @{
      Name = 'deduplication'
      From = '  $coordinates = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)'
      To = '  $coordinates = [System.Collections.Generic.List[string]]::new()'
      Expected = 'resolved duplicate GAV was not deduplicated'
    },
    @{
      Name = 'selected-version'
      From = '      $resolvedVersion = $Matches.resolved # selected version after replacement'
      To = '      $resolvedVersion = ($tail -replace ''^:'', '''' -replace ''\s+->.*$'', '''') # selected version after replacement'
      Expected = 'parser/resolved-version returned wrong GAVs'
    },
    @{
      Name = 'project-external'
      From = '        $body = $Matches.resolved.Trim() # project external substitution target'
      To = '        continue # project external substitution target'
      Expected = 'parser/project-boundary returned wrong GAVs'
    },
    @{
      Name = 'unresolved'
      From = '    if ($body -match ''(?:^|\s)(?:FAILED|\(n\))(?:\s|$)'') { # graph unresolved edge guard'
      To = '    if ($false) { # graph unresolved edge guard'
      Expected = 'parser/unresolved-and-malformed returned wrong error codes'
    },
    @{
      Name = 'configuration-set'
      From = '  [PSCustomObject]@{ Project = '':core''; Configuration = ''runtimeClasspath''; Label = '':core:runtimeClasspath'' }, # graph target core runtime'
      To = ''
      Expected = 'invoked 3 configurations instead of 4'
    },
    @{
      Name = 'project-configuration-pair'
      From = '  [PSCustomObject]@{ Project = '':core''; Configuration = ''runtimeClasspath''; Label = '':core:runtimeClasspath'' }, # graph target core runtime'
      To = '  [PSCustomObject]@{ Project = '':app''; Configuration = ''runtimeClasspath''; Label = '':core:runtimeClasspath'' }, # graph target core runtime'
      Expected = 'runtimeClasspath used wrong Gradle project task'
    },
    @{
      Name = 'offline'
      From = '    $gradleArguments = @(''-p'', $androidRoot, ''--offline'', ''--no-daemon'', "$($target.Project):dependencies", ''--configuration'', $target.Configuration) # graph offline invocation'
      To = '    $gradleArguments = @(''-p'', $androidRoot, ''--online'', ''--no-daemon'', "$($target.Project):dependencies", ''--configuration'', $target.Configuration) # graph offline invocation'
      Expected = 'omitted --offline'
    },
    @{
      Name = 'posix-sh'
      From = '    $commandArguments = if ($UseWindows) { $gradleArguments } else { @($wrapper) + $gradleArguments } # POSIX wrapper via sh'
      To = '    $commandArguments = $gradleArguments # POSIX wrapper via sh'
      Expected = 'did not pass gradlew as sh argv[0]'
    },
    @{
      Name = 'windows-wrapper'
      From = '  return (Join-Path $AndroidRoot $(if ($UseWindows) { ''gradlew.bat'' } else { ''gradlew'' }))'
      To = '  return (Join-Path $AndroidRoot ''gradlew'')'
      Expected = 'Windows graph call for runtimeClasspath was not exact'
    },
    @{
      Name = 'wrapper-preflight'
      From = '  if (-not $distribution.Ready) { # graph wrapper zero-start guard'
      To = '  if ($false) { # graph wrapper zero-start guard'
      Expected = 'missing wrapper completion marker'
    },
    @{
      Name = 'cache-preflight'
      From = '  if (-not $nativeCacheReady) { # graph native cache zero-start guard'
      To = '  if ($false) { # graph native cache zero-start guard'
      Expected = 'missing native cache'
    },
    @{
      Name = 'cache-artifact-readiness'
      From = '      $nativeCacheReady = $cachedArtifact.Count -eq 1 -and $metadataReady # native cache readiness'
      To = '      $nativeCacheReady = $metadataReady # native cache readiness'
      Expected = 'empty artifact subtree did not return GRADLE-CACHE-OFFLINE'
    },
    @{
      Name = 'cache-metadata-readiness'
      From = '      $nativeCacheReady = $cachedArtifact.Count -eq 1 -and $metadataReady # native cache readiness'
      To = '      $nativeCacheReady = $cachedArtifact.Count -eq 1 # native cache readiness'
      Expected = 'missing native metadata did not return GRADLE-CACHE-OFFLINE'
    },
    @{
      Name = 'cache-metadata-version'
      From = "      `$metadataRoot = Join-Path `$modulesCacheRoot 'metadata-2.107' # Gradle 9.7 native metadata format"
      To = "      `$metadataRoot = @(Get-ChildItem -LiteralPath `$modulesCacheRoot -Directory -Force -ErrorAction Stop | Where-Object { `$_.Name -match '^metadata-2\.`\d+`$' } | Select-Object -First 1).FullName # Gradle 9.7 native metadata format"
      Expected = '[GRAPH-STALE-METADATA-STARTED]'
    },
    @{
      Name = 'gradle-user-home-binding'
      From = '    $env:GRADLE_USER_HOME = $GradleUserHome # bind preflighted cache to child'
      To = '    $env:GRADLE_USER_HOME = $savedGradleUserHome # bind preflighted cache to child'
      Expected = 'did not bind preflighted GradleUserHome'
    },
    @{
      Name = 'subprocess-exit'
      From = '    if ($gradleExit -ne 0) { # graph nonzero subprocess guard'
      To = '    if ($false) { # graph nonzero subprocess guard'
      Expected = 'nonzero Gradle error lost code/exit'
    },
    @{
      Name = 'subprocess-target'
      From = '      $errors.Add([PSCustomObject]@{ Code = ''GRADLE-SUBPROCESS''; Configuration = $target.Label; ExitCode = $gradleExit; Detail = $null; Output = @($output) }) # graph subprocess target provenance'
      To = '      $errors.Add([PSCustomObject]@{ Code = ''GRADLE-SUBPROCESS''; Configuration = $null; ExitCode = $gradleExit; Detail = $null; Output = @($output) }) # graph subprocess target provenance'
      Expected = 'nonzero Gradle error lost target provenance'
    }
  )

  foreach ($mutationCase in $mutationCases) {
    $matches = [regex]::Matches($source, [regex]::Escape($mutationCase.From)).Count
    if ($matches -ne 1) {
      Write-Error "[GRAPH-MUTATION] $($mutationCase.Name) target count=$matches"
      exit 1
    }
    $mutantPath = Join-Path $PSScriptRoot ".license-scanner-$PID-$($mutationCase.Name).ps1"
    try {
      [System.IO.File]::WriteAllText($mutantPath, $source.Replace($mutationCase.From, $mutationCase.To), [System.Text.UTF8Encoding]::new($false))
      $mutationOutput = (& pwsh -NoProfile -File $PSCommandPath -Suite graph -ScannerPath $mutantPath -SkipMutations 2>&1 | Out-String)
      $mutationExit = $LASTEXITCODE
      if ($mutationExit -eq 0 -or $mutationOutput -notmatch [regex]::Escape($mutationCase.Expected)) {
        Write-Error "[GRAPH-MUTATION] $($mutationCase.Name) did not fail on its semantic inverse (exit=$mutationExit; output=$mutationOutput)"
        exit 1
      }
    } finally {
      if (Test-Path -LiteralPath $mutantPath) { Remove-Item -LiteralPath $mutantPath -Force }
    }
  }
  Write-Host "license-scanner-check(graph mutations): PASS ($($mutationCases.Count))"
}

Write-Host 'license-scanner-check(graph): PASS'
exit 0
