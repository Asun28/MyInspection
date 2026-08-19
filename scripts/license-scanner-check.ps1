#requires -Version 7
[CmdletBinding()]
param(
  [Parameter(Mandatory)]
  [ValidateSet('graph')]
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
