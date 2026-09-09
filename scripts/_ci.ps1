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

function Test-ScaffoldCiFanIn {
  <#
  .SYNOPSIS
    Return a workflow's fan-in contract findings. An empty result means the contract holds.
  .OUTPUTS
    Zero or more finding strings, each carrying an ASCII state code:
      [CI-FANIN-NO-REQUIRED] the workflow declares no fan-in job at all
      [CI-FANIN-UNWIRED]     a job exists that the fan-in job does not depend on
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

  foreach ($ln in ($WorkflowText -split "\r?\n")) {
    if (-not $inJobs) { if ($ln -match '^jobs:\s*(#.*)?$') { $inJobs = $true }; continue }
    if ($ln -match '^\S') { $inJobs = $false; continue }                                  # a new top-level key ends the jobs block
    if ($ln -match '^  ([A-Za-z0-9_.-]+):\s*(#.*)?$') { $cur = $Matches[1]; $jobs += $cur; continue }
    if ($cur -eq $ScaffoldCiFanInJob -and $ln -match '^\s+needs:\s*\[([^\]]*)\]') { $needs += @(($Matches[1] -split ',') | ForEach-Object { ($_ -replace '["'']', '').Trim() } | Where-Object { $_ }) }
  }

  if ($jobs -notcontains $ScaffoldCiFanInJob) {
    $findings += "[CI-FANIN-NO-REQUIRED] no '$ScaffoldCiFanInJob' job under jobs: - nothing fans the workflow in, so no single check context can prove it ran. Add a job named '$ScaffoldCiFanInJob' with if: always(), needs: [<every other job>], and one step that fails unless every needs.*.result is 'success'."
  }
  else {
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
  $noFanIn = @('  verify:', '    runs-on: ubuntu-latest', '  lint:', '    runs-on: ubuntu-latest')
  $unwired = @('  verify:', '    runs-on: ubuntu-latest', '  lint:', '    runs-on: ubuntu-latest',
               '  required:', '    if: always()', '    needs: [verify]', '    runs-on: ubuntu-latest')
  $cases = @(
    @{ what = 'well-formed fan-in - every job in needs, fan-in not its own dependency'; text = (& $wf $goodJobs $null); expect = 0; sentinel = $null }
    @{ what = 'no fan-in job at all'; text = (& $wf $noFanIn $null); expect = 1; sentinel = 'CI-FANIN-NO-REQUIRED' }
    @{ what = 'a job absent from the fan-in needs list'; text = (& $wf $unwired $null); expect = 1; sentinel = 'CI-FANIN-UNWIRED' }
    @{ what = 'empty workflow text'; text = ''; expect = 1; sentinel = 'CI-FANIN-NO-REQUIRED' }
    @{ what = 'a top-level key after the jobs block is not a job'; text = (& $wf $goodJobs @('concurrency:', '  group: ci')); expect = 0; sentinel = $null }
  )
  $useVariant = $PSBoundParameters.ContainsKey('Variant')
  $v = if ($useVariant) { $Variant } else { $null }
  $findings = @()
  foreach ($c in $cases) {
    $got = @(Test-ScaffoldCiFanInVia $c.text $v)
    if ($got.Count -ne $c.expect) { $findings += "[CI-FANIN-EXAMPLE] case '$($c.what)' produced $($got.Count) finding(s), expected $($c.expect) - the fan-in contract is what one required check context is allowed to mean, so a parser that misreads it lets a failing job merge (T86). [FIX] fix the parser, never the example." }
    elseif ($c.sentinel -and ($got[0] -notmatch [regex]::Escape($c.sentinel))) { $findings += "[CI-FANIN-EXAMPLE] case '$($c.what)' was reported, but not with the $($c.sentinel) state code - a finding from the wrong branch is not evidence the right one fired (L165). [FIX] fix the parser, never the example." }
  }
  return $findings
}
