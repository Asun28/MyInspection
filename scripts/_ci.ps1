#requires -Version 7
# Shared: judge whether a GitHub Actions workflow states and satisfies its own acceptance contract.
#
# The contract in one sentence: ci.yml carries ONE fan-in job named `required` whose `needs:` list is
# the version-controlled list of jobs that must pass, and that list must name every other job in the
# file. A branch ruleset can then require exactly one context (`required`) instead of a client
# reconstructing the set on every merge - a job added to the workflow and wired into `needs:` is
# covered with no client change at all.
#
# Why this is its own file rather than a block inside task.ps1 (same reasoning as _gitbase.ps1 /
# _cards.ps1 / _scope.ps1): two consumers need the SAME judgement - the ship gate in task.ps1, which
# fail-closes when the merge candidate tree carries no fan-in job, and selftest gate 8, which refuses
# to let a workflow ship with a job the list forgot. Two implementations would drift, and the drift
# would be silent in exactly the direction that matters: the gate the merge trusts goes fail-open
# while the gate that guards the file stays green.
#
# Deliberate scope note, not an oversight: only the INLINE `needs: [a, b]` form is parsed - the form
# ci.yml actually uses. A block-sequence `needs:` would read as an empty list, which makes every job
# look unwired, so gate 8 goes RED and names the jobs: loud and fail-closed, never a silent pass. If a
# workflow ever needs the block form, teach it to this function - never by adding a second parser.

# The fan-in job's name IS the contract (it is the context name a ruleset requires). Both consumers
# read it from here, so renaming it stays one edit rather than a three-file hunt.
$ScaffoldCiFanInJob = 'required'

# This deliberately supports one auditable pwsh step, not arbitrary YAML or shell programs. The body is
# compared without indentation; its behavior is exercised below with success and non-success results.
function Get-ScaffoldCiSuccessBody {
  @(
    '$results = @'''
    '${{ toJSON(needs) }}'
    '''@ | ConvertFrom-Json -AsHashtable'
    'if ($results.Count -eq 0) { throw ''No required jobs were reported'' }'
    'foreach ($job in $results.Keys) {'
    '  if ($results[$job].result -cne ''success'') {'
    '    throw "Required job $job completed as $($results[$job].result)"'
    '  }'
    '}'
  ) -join "`n"
}

function Test-ScaffoldCiSuccessStep([string[]]$JobLines) {
  $jobKeys = @{}
  $stepKeys = @{}
  $body = [Collections.Generic.List[string]]::new()
  $inSteps = $false
  $inBody = $false
  $stepCount = 0
  foreach ($line in $JobLines) {
    if ($line -match '^\s*(?:#.*)?$') { continue }
    if ($inBody) {
      if ($line -notmatch '^          (.*)$') { return $false }
      $body.Add($Matches[1].Trim())
      continue
    }
    if (-not $inSteps) {
      if ($line -notmatch '^    ([a-z-]+):\s*(.*?)\s*$') { return $false }
      $key = $Matches[1]; $value = $Matches[2]
      if ($jobKeys.ContainsKey($key) -or $key -cnotin @('if','needs','runs-on','timeout-minutes','steps')) { return $false }
      $jobKeys[$key] = $value
      if ($key -ceq 'steps') {
        if ($value) { return $false }
        $inSteps = $true
      }
      elseif ($key -ceq 'runs-on' -and $value -cnotmatch '^[A-Za-z0-9_-]+$') { return $false }
      elseif ($key -ceq 'timeout-minutes' -and $value -cnotmatch '^[1-9][0-9]*$') { return $false }
      continue
    }
    if ($line -match '^      - name:\s+[^\r\n]+$') {
      $stepCount++
      if ($stepCount -ne 1) { return $false }
    }
    elseif ($line -match '^        (shell|run):\s*(.*?)\s*$') {
      $key = $Matches[1]; $value = $Matches[2]
      if ($stepCount -ne 1 -or $stepKeys.ContainsKey($key)) { return $false }
      $stepKeys[$key] = $value
      if ($key -ceq 'run') {
        if ($value -cne '|' -or $stepKeys['shell'] -cne 'pwsh') { return $false }
        $inBody = $true
      }
    }
    else { return $false }
  }
  if (-not $jobKeys.ContainsKey('runs-on') -or $stepCount -ne 1 -or -not $inBody) { return $false }
  $expected = @((Get-ScaffoldCiSuccessBody) -split '\r?\n' | ForEach-Object { $_.Trim() }) -join "`n"
  return ($body -join "`n") -ceq $expected
}

function Test-ScaffoldCiFanIn {
  <#
  .SYNOPSIS
    Return a workflow's fan-in contract findings. An empty result means the contract holds.
  .OUTPUTS
    Zero or more finding strings, each carrying an ASCII state code:
      [CI-FANIN-NO-REQUIRED] the workflow declares no fan-in job at all
      [CI-FANIN-UNWIRED]     a job exists that the fan-in job does not depend on
      [CI-FANIN-MISSING-ALWAYS] the fan-in is not explicitly unconditional
      [CI-FANIN-NO-SUCCESS-GUARD] the fan-in is not the supported strict success step
  #>
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][AllowEmptyString()][string]$WorkflowText
  )

  $findings = @()
  $jobs = @()      # every 2-space key inside the jobs: block, in file order
  $needs = @()     # the fan-in job's declared dependencies
  $cur = ''        # the job whose body we are currently inside
  $inJobs = $false
  $jobsBlocks = 0
  $requiredLines = [Collections.Generic.List[string]]::new()

  foreach ($ln in ($WorkflowText -split "\r?\n")) {
    if ($ln -match '^jobs:\s*(#.*)?$') { $jobsBlocks++; $inJobs = $true; $cur = ''; continue }
    if (-not $inJobs) { continue }
    if ($ln -match '^\S') { $inJobs = $false; continue }                                  # a new top-level key ends the jobs block
    if ($ln -match '^  ([A-Za-z0-9_.-]+):\s*(#.*)?$') { $cur = $Matches[1]; $jobs += $cur; continue }
    if ($cur -eq $ScaffoldCiFanInJob) {
      $requiredLines.Add($ln)
      if ($ln -match '^    needs:\s*\[([^\]]*)\]\s*(?:#.*)?$') { $needs += @(($Matches[1] -split ',') | ForEach-Object { ($_ -replace '["'']', '').Trim() } | Where-Object { $_ }) }
    }
  }

  if ($jobs -notcontains $ScaffoldCiFanInJob) {
    $findings += "[CI-FANIN-NO-REQUIRED] no '$ScaffoldCiFanInJob' job under jobs: - nothing fans the workflow in, so no single check context can prove it ran. Add a job named '$ScaffoldCiFanInJob' with if: always(), needs: [<every other job>], and one step that fails unless every needs.*.result is 'success'."
  }
  else {
    if (@($requiredLines | Where-Object { $_ -cmatch '^    if:\s*(?:always\(\)|\$\{\{\s*always\(\)\s*\}\})\s*$' }).Count -ne 1) {
      $findings += '[CI-FANIN-MISSING-ALWAYS] required must declare job-level if: always() so failed or skipped dependencies still reach its result guard.'
    }
    if ($jobsBlocks -ne 1 -or @($jobs | Select-Object -Unique).Count -ne $jobs.Count -or
        @($jobs | Where-Object { $_ -ceq $ScaffoldCiFanInJob }).Count -ne 1 -or
        -not (Test-ScaffoldCiSuccessStep -JobLines $requiredLines.ToArray())) {
      $findings += '[CI-FANIN-NO-SUCCESS-GUARD] required must contain one unconditional pwsh step with the canonical nonempty toJSON(needs) success guard; bypass properties, extra steps and alternate bodies are unsupported.'
    }
    if (-not $needs.Count -or @($needs | Where-Object { $_ -ceq $ScaffoldCiFanInJob -or $_ -cnotin $jobs }).Count) {
      $findings += '[CI-FANIN-UNWIRED] required must depend on existing non-fan-in jobs and cannot have an empty dependency list.'
    }
    foreach ($j in $jobs) {
      if ($j -eq $ScaffoldCiFanInJob) { continue }                                        # the fan-in job is never its own dependency
      if ($needs -notcontains $j) {
        $findings += "[CI-FANIN-UNWIRED] job '$j' is absent from the '$ScaffoldCiFanInJob' job's needs: list - it can fail while '$ScaffoldCiFanInJob' still reports success. Add '$j' to that needs: list."
      }
    }
  }
  return $findings
}

# -- T111-CORE-SELFCHECK-GATE (TD140 / ADR 0011): the declared self-check for the fan-in judgement --
# Test-ScaffoldCiFanIn had exactly one assertion before this: selftest 8.2g feeds it the REAL ci.yml. That
# file is correct by construction, so 8.2g proves the parser accepts a good workflow and can never prove it
# REJECTS a bad one - a function returning an empty finding list unconditionally passes it. That is the
# concrete shape of "indirect coverage is not coverage", and it is why this core was ADR 0011's first.
# The cases below are the shapes the real ci.yml cannot be: no fan-in job at all, a job missing from the
# fan-in's needs list, an empty workflow, and a jobs block followed by another top-level key (which must
# not be read as a job). Rejected shape, declared by name:
#   accept-any-workflow - a parser that finds nothing, ever. It passes 8.2g and fails every case here.
function Test-ScaffoldCiFanInVia($WorkflowText, $Variant) {
  if ($Variant -eq 'accept-any-workflow') { return @() }
  return @(Test-ScaffoldCiFanIn -WorkflowText $WorkflowText)
}

function Test-ScaffoldCiFanInExamples {
  [CmdletBinding()]
  param([ValidateSet('accept-any-workflow')][string]$Variant)
  $wf = {
    param($jobsBlock, $tail)
    (@('name: ci', 'on:', '  push:', 'jobs:') + $jobsBlock + @($tail | Where-Object { $null -ne $_ })) -join "`n"
  }
  $goodJobs = @('  verify:', '    runs-on: ubuntu-latest', '  lint:', '    runs-on: ubuntu-latest',
                '  required:', '    if: always()', '    needs: [verify, lint]', '    runs-on: ubuntu-latest')
  $successStep = @('    steps:', '      - name: Assert project verification succeeded', '        shell: pwsh', '        run: |') +
    @((Get-ScaffoldCiSuccessBody) -split '\r?\n' | ForEach-Object { '          ' + $_ })
  $goodJobs += $successStep
  $noFanIn = @('  verify:', '    runs-on: ubuntu-latest', '  lint:', '    runs-on: ubuntu-latest')
  $unwired = @('  verify:', '    runs-on: ubuntu-latest', '  lint:', '    runs-on: ubuntu-latest',
               '  required:', '    if: always()', '    needs: [verify]', '    runs-on: ubuntu-latest') + $successStep
  $goodText = & $wf $goodJobs $null
  $cases = @(
    @{ what = 'well-formed fan-in - every job in needs, fan-in not its own dependency'; text = (& $wf $goodJobs $null); expect = 0; sentinel = $null }
    @{ what = 'no fan-in job at all'; text = (& $wf $noFanIn $null); expect = 1; sentinel = 'CI-FANIN-NO-REQUIRED' }
    @{ what = 'a job absent from the fan-in needs list'; text = (& $wf $unwired $null); expect = 1; sentinel = 'CI-FANIN-UNWIRED' }
    @{ what = 'empty workflow text'; text = ''; expect = 1; sentinel = 'CI-FANIN-NO-REQUIRED' }
    @{ what = 'a top-level key after the jobs block is not a job'; text = (& $wf $goodJobs @('concurrency:', '  group: ci')); expect = 0; sentinel = $null }
    @{ what = 'job skips on failed dependency'; text = $goodText.Replace('    if: always()', '    if: success()'); expect = 1; sentinel = 'CI-FANIN-MISSING-ALWAYS' }
    @{ what = 'job has no unconditional guard'; text = $goodText.Replace("    if: always()`n", ''); expect = 1; sentinel = 'CI-FANIN-MISSING-ALWAYS' }
    @{ what = 'success predicate reversed'; text = $goodText.Replace("-cne 'success'", "-ceq 'success'"); expect = 1; sentinel = 'CI-FANIN-NO-SUCCESS-GUARD' }
    @{ what = 'early successful exit'; text = $goodText.Replace('        run: |', "        run: |`n          exit 0"); expect = 1; sentinel = 'CI-FANIN-NO-SUCCESS-GUARD' }
    @{ what = 'job tolerates failure'; text = $goodText.Replace('  required:', "  required:`n    continue-on-error: true"); expect = 1; sentinel = 'CI-FANIN-NO-SUCCESS-GUARD' }
    @{ what = 'step tolerates failure'; text = $goodText.Replace('        shell: pwsh', "        continue-on-error: true`n        shell: pwsh"); expect = 1; sentinel = 'CI-FANIN-NO-SUCCESS-GUARD' }
    @{ what = 'step conditional skip'; text = $goodText.Replace('        shell: pwsh', "        if: false`n        shell: pwsh"); expect = 1; sentinel = 'CI-FANIN-NO-SUCCESS-GUARD' }
    @{ what = 'extra success step'; text = $goodText + "`n      - run: exit 0"; expect = 1; sentinel = 'CI-FANIN-NO-SUCCESS-GUARD' }
    @{ what = 'empty results accepted'; text = $goodText.Replace("          if (`$results.Count -eq 0) { throw 'No required jobs were reported' }`n", ''); expect = 1; sentinel = 'CI-FANIN-NO-SUCCESS-GUARD' }
    @{ what = 'duplicate jobs block can replace the validated block'; text = $goodText + "`njobs:`n  verify:`n    runs-on: ubuntu-latest"; expect = 1; sentinel = 'CI-FANIN-NO-SUCCESS-GUARD' }
  )
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()
  foreach ($c in $cases) {
    $got = @(Test-ScaffoldCiFanInVia $c.text $v)
    if ($got.Count -ne $c.expect) { $findings += "[CI-FANIN-EXAMPLE] case '$($c.what)' produced $($got.Count) finding(s), expected $($c.expect) - the fan-in contract is what one required check context is allowed to mean, so a parser that misreads it lets a failing job merge (T86). [FIX] fix the parser, never the example." }
    elseif ($c.sentinel -and ($got[0] -notmatch [regex]::Escape($c.sentinel))) { $findings += "[CI-FANIN-EXAMPLE] case '$($c.what)' was reported, but not with the $($c.sentinel) state code - a finding from the wrong branch is not evidence the right one fired (L165). [FIX] fix the parser, never the example." }
  }
  # Execute only the trusted canonical body, never workflow-supplied code. The byte-shape check above
  # binds a workflow to this behavior, including rejection of absent, skipped and cancelled results.
  $resultCases = @(
    @{ name='all successful'; json='{"verify":{"result":"success"},"lint":{"result":"success"}}'; reject=$false }
    @{ name='failure'; json='{"verify":{"result":"failure"}}'; reject=$true }
    @{ name='cancelled'; json='{"verify":{"result":"cancelled"}}'; reject=$true }
    @{ name='skipped'; json='{"verify":{"result":"skipped"}}'; reject=$true }
    @{ name='empty'; json='{}'; reject=$true }
    @{ name='missing result'; json='{"verify":{}}'; reject=$true }
    @{ name='one failure among successes'; json='{"verify":{"result":"success"},"lint":{"result":"failure"}}'; reject=$true }
  )
  foreach ($case in $resultCases) {
    $rejected = $false
    try { & ([scriptblock]::Create((Get-ScaffoldCiSuccessBody).Replace('${{ toJSON(needs) }}', $case.json))) }
    catch { $rejected = $true }
    if ($rejected -ne $case.reject) { $findings += "[CI-FANIN-EXAMPLE] canonical result guard misclassified '$($case.name)'." }
  }
  return $findings
}
