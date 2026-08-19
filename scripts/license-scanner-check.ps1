#requires -Version 7
[CmdletBinding()]
param(
  [Parameter(Mandatory)]
  [ValidateSet('graph', 'policy')]
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

    Set-PolicyExceptions -Json @'
[
  {"coordinate":"fixture.policy:fallback:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/fallback","registered_by":"policy-test","registered_on":"2026-08-19"},
  {"coordinate":"fixture.policy:fallback-no-license:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/fallback-no-license","registered_by":"policy-test","registered_on":"2026-08-19"},
  {"coordinate":"fixture.policy:valid-unknown:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/valid-unknown","registered_by":"policy-test","registered_on":"2026-08-19"},
  {"coordinate":"fixture.policy:declared:1.0","declared_license":"BSD License","license":"BSD-3-Clause","evidence_url":"https://example.invalid/declared","registered_by":"policy-test","registered_on":"2026-08-19"},
  {"coordinate":"fixture.policy:risk:1.0","declared_license":"EPL-1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/risk","registered_by":"policy-test","registered_on":"2026-08-19"}
]
'@
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
      @{ Id = 'wildcard'; Json = '[{"coordinate":"fixture.policy:*:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'empty-registrant'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"","registered_on":"2026-08-19"}]' },
      @{ Id = 'canonical-alias'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"apache 2","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'bad-url'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"file:///tmp/evidence","registered_by":"policy-test","registered_on":"2026-08-19"}]' },
      @{ Id = 'bad-date'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"19-08-2026"}]' },
      @{ Id = 'non-string'; Json = '[{"coordinate":"fixture.policy:a:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":7,"registered_on":"2026-08-19"}]' }
    )
    foreach ($invalid in $invalidExceptions) {
      Set-PolicyExceptions -Json $invalid.Json
      $invalidResult = Invoke-PolicyFixture -Coordinates @('fixture.policy:a:1.0')
      Assert-Policy (
        $invalidResult.Findings.Count -eq 0 -and @($invalidResult.Violations | Where-Object Code -CEQ 'GRADLE-OVERRIDE').Count -eq 1
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

    $emptyGraphRoot = Join-Path $policyRoot 'empty-graph-root'
    New-Item -ItemType Directory -Force -Path (Join-Path $emptyGraphRoot 'configs/licenses') | Out-Null
    [System.IO.File]::WriteAllText(
      (Join-Path $emptyGraphRoot 'configs/licenses/gradle-exceptions.json'),
      '[{"coordinate":"fixture.policy:*:1.0","license":"Apache-2.0","evidence_url":"https://example.invalid/a","registered_by":"policy-test","registered_on":"2026-08-19"}]',
      [System.Text.UTF8Encoding]::new($false)
    )
    function Get-GradleResolvedGraphs {
      return [PSCustomObject]@{ Resolved = @(); Errors = @() }
    }
    $script:bad = @(); $script:warn = @()
    Invoke-GradleLicenseScan -Root $emptyGraphRoot
    Assert-Policy (
      @($script:bad | Where-Object { $_ -match '\[GRADLE-OVERRIDE\]' }).Count -eq 1
    ) '[POLICY-EMPTY-GRAPH-OVERRIDE] empty resolved graph skipped exception-table validation'
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
        Name = 'override-required-field'
        From = "      foreach (`$field in @('coordinate', 'license', 'evidence_url', 'registered_by', 'registered_on')) {"
        To = "      foreach (`$field in @('coordinate', 'license', 'evidence_url', 'registered_on')) {"
        Expected = '[POLICY-OVERRIDE-EMPTY-REGISTRANT]'
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
        Name = 'forbidden-precedence'
        From = '      } elseif ($classification -eq ''forbidden'') { # policy forbidden precedence'
        To = '      } elseif ($false) { # policy forbidden precedence'
        Expected = '[POLICY-FORBIDDEN-FIRST]'
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
