#requires -Version 7
<#
.SYNOPSIS
  单任务闭环编排器（R1 worktree + R2 TDD + R3 Codex-PR闸门 + R4 测试卫生 + R5 文档同步）。

.DESCRIPTION
  把一张任务卡，跑成可机检的 worktree → 红 → 绿 → 重构/剪枝 → Codex 评审 →
  PR + 合并 → 收尾 → 文档同步 闭环。分阶段执行（编码本身由 Claude/人在 worktree 内做）：

    start   : 建 worktree(<WorktreeRoot>\<TaskId>) + 引导环境(uv sync / npm i)，打印 TDD 提醒。
    ship    : DoD(必绿) → verify 总闸 → 提交 → 范围闸(allow_paths) → 预算闸(base 卡的 budget:，T233) → 许可闸 → 防泄露闸
              → push → 开 PR → 基线版本的 Codex 评审 → required CI 检查 → 基线/HEAD 复验 → squash 合并。
              MyInspection 配置 ReviewGate=required：Codex 必须 pass，且 CI 必须明确 success；无服务端规则集也不省略这些检查。
    cleanup : 合并后 Windows 安全拆除 worktree + 剪枝 + 删分支。脏树守卫：worktree 有未提交改动时默认拒绝拆除（防不可逆丢失），加 -Force 显式覆盖。

  设计取舍见 docs\DEVOPS-WORKFLOW.md。全部合并闸通过后自动合并；-NoAutoMerge 同样验证 CI 后暂停，并打印重新进入完整 ship 的接续命令。
  项目级常量（账号 / worktree 根 / Python 版本）来自 scripts\_config.ps1。

.PARAMETER TaskId  形如 T1-FOO。start / red / ship 须存在 specs\tasks\<TaskId>.md（它们读卡字段）；
                   cleanup 不读卡，卡已被冷存清扫搬走或根本不存在时照样拆除（T293 / issue #361）。
.PARAMETER Phase   start | ship | cleanup（默认 start）
.PARAMETER Base    基线分支（默认=仓库当前分支,自动探测 main/master,可显式覆盖）
.PARAMETER NoAutoMerge  ship 时在合并前暂停；晚些时候按输出的 [SHIP-MANUAL-RESUME] 命令重新进入完整 ship
.PARAMETER Force  cleanup 阶段：worktree 有未提交改动时仍强制拆除（确认丢弃；缺省有脏改动即拒，防不可逆数据丢失）
.EXAMPLE
  # 所有相位命令都从**主检出**根目录跑（L86）。cd 进 worktree 只为编辑文件，别在里面跑这份脚本——
  # worktree 自带的副本会把 $RepoRoot 派生成 worktree 本身，被 fail-closed 守卫拒（哨兵 L86-WT）。
  pwsh -File scripts\task.ps1 -TaskId T0-SCAFFOLD -Phase start
  # ... 在 <WorktreeRoot>\T0-SCAFFOLD 里写失败测试→实现→绿（编辑文件；相位命令仍回主检出跑）...
  pwsh -File scripts\task.ps1 -TaskId T0-SCAFFOLD -Phase ship
  pwsh -File scripts\task.ps1 -TaskId T0-SCAFFOLD -Phase cleanup
#>
[CmdletBinding()]
param(
  # TaskId 绑定期即校验字符集（同 check-cards 卡 id 契约）——start 外的相（red/ship/cleanup）此前拿未净化
  # TaskId 拼 $Wt/$Card 路径（cleanup 更 Remove-Item -Recurse -Force $Wt），路径穿越面。绑定期校验
  # 令 4 相统一在最外层即拒畸形/穿越 id，不依赖 :109 卡存在守卫的偶发耦合（TD50/TD-113）。
  # 「单一真相源」在 T229 之前只是一句注释里的主张：三处各持一份**字面相同**的正则，而行为已经分叉。
  # 现在它是机检事实——判据只有 _cards.ps1 的 card-id 规则行一份，本处不再有副本（TD231）。
  # ErrorMessage 内嵌 ASCII 哨兵 [TD50-BADID]，供 selftest 15l 跨子进程/locale 稳定判定绑定期拒（非 :109 throw）。
  # T229/TD231: the grammar is DERIVED from the card-id rule row in _cards.ps1, never copied here. A
  # ValidatePattern attribute cannot call a function, so the check is a ValidateScript that dot-sources the
  # shared library off this script's own directory (the L86 rule: the trusted checkout's copy, not the
  # caller's) and asks the one judgement. Two things this buys that the old copy did not: narrowing the rule
  # row now narrows THIS gate in the same commit, and the row's CaseSensitive flag is honoured - the old
  # ValidatePattern defaulted to IgnoreCase and accepted ids check-cards rejects. Binding time is still the
  # point of rejection, which is TD50's whole requirement: red/ship/cleanup must refuse a malformed id
  # BEFORE it is concatenated into $Wt/$Card, and cleanup runs Remove-Item -Recurse -Force on that path.
  # _cards.ps1 is a pure function library with no _config dependency, so dot-sourcing it here is side-effect
  # free and idempotent - the script body dot-sources it again at :58.
  [Parameter(Mandatory)]
  [ValidateScript({ . (Join-Path $PSScriptRoot '_cards.ps1'); Test-ScaffoldCardId $_ }, ErrorMessage = 'TaskId 非法格式 [TD50-BADID]：值 "{0}" 不符卡 id 契约（判据取自 _cards.ps1 的 card-id 规则行，与 check-cards 同源、大小写敏感）；red/ship/cleanup 各相均在绑定期校验，防未净化 TaskId 拼进 worktree/卡片路径致路径穿越。')]
  [string]$TaskId,
  [ValidateSet('start', 'red', 'ship', 'cleanup')][string]$Phase = 'start',
  [string]$Base = '',
  [switch]$NoAutoMerge,
  [switch]$Local,      # 本地完成：按 ReviewGate/card policy 完成 R3 后本地合并，不 push/PR/gh；本仓 required 配置仍强制评审
  [switch]$SkipRed,    # compat no-op since T68 (the RED-evidence gate is gone); kept so documented commands and fixtures still bind
  [switch]$Force       # cleanup：确认丢弃 worktree 内未提交改动后再拆除（TD47 脏树守卫的显式覆盖；缺省=有脏改动即拒）
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# UTF-8 控制台输出 + 顶层原生命令按退出码判（git diff --cached --quiet / gh pr view 等正常返回非零，不当终止错抛）：
# 单源自 _encoding.ps1（TD54/TD-117；DoD 包装器内仍按需局部覆盖 $true，见下方 ship 阶段）（30-lens C13）。
try { . (Join-Path $PSScriptRoot '_encoding.ps1') } catch { }   # 前奏缺失（如 hermetic 单文件测试未随拷）即 fail-open 退回原行为
# 忽略会话里无效的 token（空串仍被 gh 视为“存在”→会遮蔽 keyring），用 Remove-Item 彻底清除
Remove-Item Env:GH_TOKEN, Env:GITHUB_TOKEN -ErrorAction SilentlyContinue

. (Join-Path $PSScriptRoot '_config.ps1')
. (Join-Path $PSScriptRoot '_cards.ps1')
. (Join-Path $PSScriptRoot '_gitbase.ps1')   # 共享基线名→引用解析（TD68 单一实现，与 review.ps1 共用防漂移）
. (Join-Path $PSScriptRoot '_scope.ps1')     # 共享范围闸判定核（TD93 单一实现，与 check-scope.ps1 共用防漂移）
. (Join-Path $PSScriptRoot '_ci.ps1')        # shared CI fan-in contract judgement (T86 single implementation, shared with selftest gate 8)
# T189 (TD184): loaded HERE rather than inside the remote-ship branch, because Get-ReviewBlockDetail below
# needs the quality-round core on the -Local path too, and that path never reaches the account guard.
. (Join-Path $PSScriptRoot '_guard.ps1')     # personal-account guard + shared quality-round count (T189, gated by selftest 17z)
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $Base) {
  # default base = repo's current branch (handles main vs master vs any); detached HEAD falls through to
  # probing the repo's actual default branch instead of hardcoding 'main' (TD63 item4：master-default 仓在
  # detached HEAD 下会得到错误的 $Base——镜像 review.ps1 的同款探测：origin/HEAD → main/master 存在性 → 告警兜底)。
  $Base = (& git -C $RepoRoot symbolic-ref --quiet --short HEAD 2>$null)
  if ($Base) { $Base = $Base.Trim() }
  if (-not $Base) {
    $originHead = (& git -C $RepoRoot symbolic-ref --quiet --short refs/remotes/origin/HEAD 2>$null)
    if ($originHead) { $Base = ($originHead.Trim() -replace '^origin/', '') }
  }
  if (-not $Base) {
    foreach ($b in @('main', 'master')) {
      & git -C $RepoRoot rev-parse --verify --quiet $b 1>$null 2>$null
      if ($LASTEXITCODE -eq 0) { $Base = $b; break }
    }
  }
  if (-not $Base) {
    Write-Warning "无法探测默认分支（detached HEAD 且无 origin/HEAD、本地也无 main/master）——回退硬编码 'main'；如非预期请显式传 -Base <分支>。"
    $Base = 'main'
  }
}
$WtRoot = Get-ScaffoldWorktreeRoot   # 留空配置 => 按 OS 取默认（Windows <系统盘>\wt 如 C:\wt / *nix ~/.wt），可移植
$Wt = Join-Path $WtRoot $TaskId
$Card = Join-Path $RepoRoot "specs/tasks/$TaskId.md"

# ── 两道 fail-closed 守卫（TD-203 / 铁律 L86）。置于任何相位逻辑之前，覆盖 red/ship/cleanup 与远端路径。
#    各带**互不相同**的 ASCII 哨兵，令闸 15m 能分别证明是哪一道拦下的（共用哨兵则删掉一道也照样绿）。──
# (1) $RepoRoot 就是本卡自己的 worktree：即「用 worktree 内那份 task.ps1 跑相位命令」。此时 -Local 的
#     `git -C $RepoRoot merge $TaskId` 是把分支并进它自己（"Already up to date." + exit 0 的假成功），
#     **传 -Base 也救不了**（实测：-Base master 绕过下面的 (2)，仍假成功且 base 从未前进）。故与 $Base 无关地拒。
$wtResolved = if (Test-Path $Wt) { (Resolve-Path $Wt).Path } else { $null }
if ($wtResolved -and ($RepoRoot -ieq $wtResolved)) {
  throw "L86-WT: 相位命令跑在本卡自己的 worktree 里（`$RepoRoot == $Wt）——本地合并会把分支并进它自己而假报成功、base 从未前进，随后 cleanup 会强删这条从未合并的分支。回**主检出**跑相位命令（cd 进 worktree 只为编辑文件）。传 -Base 并不能修复此情形。"
}
# (2) 基线解析成本卡分支自己（主检出里显式误传 -Base <卡 id>）：范围闸 `$Base...HEAD` 得空 diff → 越界改动会被空过。
if ($Base -eq $TaskId) {
  throw "L86-BASE: base==TaskId（'$TaskId'）——基线退化成分支自己，范围闸会对空 diff 空过。传 -Base 指定真实基线分支（如 master）。"
}
$Py = $ScaffoldConfig.PythonVersion

function Step($m) { Write-Host "`n=== [$TaskId] $m ===" -ForegroundColor Cyan }

function Initialize-CiContainment {
  param([string]$Fault = '')
  if (($Fault -ceq 'platform') -or (-not $IsWindows)) {
    throw '[CI-GATE-CONTAINMENT] stage=platform：候选 CI 进程树容纳当前只支持 Windows；请在 Windows host 重跑 ship。'
  }
  try {
    if ($Fault -ceq 'add-type') { Add-Type -TypeDefinition 'public class {' -ErrorAction Stop; return }
    if ('ScaffoldContainedProcess' -as [type]) { return }
    Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading.Tasks;
using Microsoft.Win32.SafeHandles;

public sealed class ScaffoldProcessResult {
  public bool TimedOut { get; set; }
  public int ExitCode { get; set; }
  public string Stdout { get; set; }
  public string Stderr { get; set; }
}

public static class ScaffoldContainedProcess {
  const uint CREATE_SUSPENDED = 0x00000004, CREATE_NO_WINDOW = 0x08000000, EXTENDED_STARTUPINFO_PRESENT = 0x00080000;
  const uint STARTF_USESTDHANDLES = 0x00000100, HANDLE_FLAG_INHERIT = 0x00000001;
  const uint JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x00002000;
  const uint WAIT_OBJECT_0 = 0;
  const int PROC_THREAD_ATTRIBUTE_HANDLE_LIST = 0x00020002;
  const uint GENERIC_READ = 0x80000000, FILE_SHARE_READ = 1, FILE_SHARE_WRITE = 2;
  const uint OPEN_EXISTING = 3, FILE_ATTRIBUTE_NORMAL = 0x80;

  [StructLayout(LayoutKind.Sequential)] struct SECURITY_ATTRIBUTES {
    public int nLength; public IntPtr lpSecurityDescriptor;
    [MarshalAs(UnmanagedType.Bool)] public bool bInheritHandle;
  }
  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)] struct STARTUPINFO {
    public int cb; public string lpReserved, lpDesktop, lpTitle;
    public uint dwX, dwY, dwXSize, dwYSize, dwXCountChars, dwYCountChars, dwFillAttribute, dwFlags;
    public short wShowWindow, cbReserved2; public IntPtr lpReserved2, hStdInput, hStdOutput, hStdError;
  }
  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)] struct STARTUPINFOEX {
    public STARTUPINFO StartupInfo; public IntPtr lpAttributeList;
  }
  [StructLayout(LayoutKind.Sequential)] struct PROCESS_INFORMATION {
    public IntPtr hProcess, hThread; public uint dwProcessId, dwThreadId;
  }
  [StructLayout(LayoutKind.Sequential)] struct IO_COUNTERS {
    public ulong ReadOperationCount, WriteOperationCount, OtherOperationCount, ReadTransferCount, WriteTransferCount, OtherTransferCount;
  }
  [StructLayout(LayoutKind.Sequential)] struct JOBOBJECT_BASIC_LIMIT_INFORMATION {
    public long PerProcessUserTimeLimit, PerJobUserTimeLimit; public uint LimitFlags;
    public UIntPtr MinimumWorkingSetSize, MaximumWorkingSetSize; public uint ActiveProcessLimit;
    public UIntPtr Affinity; public uint PriorityClass, SchedulingClass;
  }
  [StructLayout(LayoutKind.Sequential)] struct JOBOBJECT_EXTENDED_LIMIT_INFORMATION {
    public JOBOBJECT_BASIC_LIMIT_INFORMATION BasicLimitInformation; public IO_COUNTERS IoInfo;
    public UIntPtr ProcessMemoryLimit, JobMemoryLimit, PeakProcessMemoryUsed, PeakJobMemoryUsed;
  }

  [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] static extern IntPtr CreateJobObjectW(IntPtr a, string name);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool SetInformationJobObject(IntPtr job, int cls, IntPtr info, uint len);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool TerminateJobObject(IntPtr job, uint code);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool CreatePipe(out IntPtr read, out IntPtr write, ref SECURITY_ATTRIBUTES sa, uint size);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool SetHandleInformation(IntPtr handle, uint mask, uint flags);
  [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] static extern IntPtr CreateFileW(string name, uint access, uint share, ref SECURITY_ATTRIBUTES sa, uint creation, uint flags, IntPtr template);
  [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] static extern bool CreateProcessW(string app, StringBuilder commandLine, IntPtr pa, IntPtr ta, bool inherit, uint flags, IntPtr env, string cwd, ref STARTUPINFOEX si, out PROCESS_INFORMATION pi);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool InitializeProcThreadAttributeList(IntPtr list, int count, uint flags, ref IntPtr size);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool UpdateProcThreadAttribute(IntPtr list, uint flags, IntPtr attribute, IntPtr value, IntPtr size, IntPtr previous, IntPtr returned);
  [DllImport("kernel32.dll")] static extern void DeleteProcThreadAttributeList(IntPtr list);
  [DllImport("kernel32.dll", SetLastError=true)] static extern uint ResumeThread(IntPtr thread);
  [DllImport("kernel32.dll", SetLastError=true)] static extern uint WaitForSingleObject(IntPtr handle, uint ms);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool GetExitCodeProcess(IntPtr process, out uint code);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool TerminateProcess(IntPtr process, uint code);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool CloseHandle(IntPtr handle);

  static Exception Stage(string stage, string detail="fault injection") {
    return new InvalidOperationException("[CI-GATE-CONTAINMENT] stage=" + stage + ": " + detail);
  }
  static bool IsFault(string fault, string stage) { return String.Equals(fault, stage, StringComparison.Ordinal); }
  static int Left(DateTime deadline) {
    double ms = (deadline - DateTime.UtcNow).TotalMilliseconds;
    return ms <= 0 ? 0 : (ms >= Int32.MaxValue ? Int32.MaxValue : (int)Math.Floor(ms));
  }
  static bool WaitHandle(IntPtr handle, DateTime deadline) {
    int ms = Left(deadline); return ms > 0 && WaitForSingleObject(handle, (uint)ms) == WAIT_OBJECT_0;
  }
  static bool WaitStreams(Task<string> stdout, Task<string> stderr, DateTime deadline) {
    if (stdout.IsCompleted && stderr.IsCompleted) return true;
    int ms = Left(deadline); if (ms <= 0) return false;
    try { return Task.WaitAll(new Task[] { stdout, stderr }, ms); } catch { return false; }
  }
  static void Close(ref IntPtr h) { if (h != IntPtr.Zero && h != new IntPtr(-1)) CloseHandle(h); h = IntPtr.Zero; }

  public static ScaffoldProcessResult Run(string application, string commandLine, string cwd, long deadlineTicks, int cleanupAllowanceMs, string fault) {
    DateTime deadline = new DateTime(deadlineTicks, DateTimeKind.Utc);
    DateTime cleanupDeadline = deadline.AddMilliseconds(cleanupAllowanceMs);
    IntPtr job=IntPtr.Zero, outR=IntPtr.Zero, outW=IntPtr.Zero, errR=IntPtr.Zero, errW=IntPtr.Zero, nul=IntPtr.Zero;
    IntPtr infoPtr=IntPtr.Zero, attrs=IntPtr.Zero, attrSize=IntPtr.Zero, handleList=IntPtr.Zero; bool attrsReady=false;
    PROCESS_INFORMATION pi = new PROCESS_INFORMATION();
    StreamReader outReader=null, errReader=null; Task<string> outTask=null, errTask=null;
    bool created=false, resumed=false;
    try {
      job = CreateJobObjectW(IntPtr.Zero, null);
      bool jobOk=job != IntPtr.Zero; if (IsFault(fault,"create-job")) jobOk=false;
      if (!jobOk) throw Stage("create-job",IsFault(fault,"create-job")?"api-result":new Win32Exception(Marshal.GetLastWin32Error()).Message);
      JOBOBJECT_EXTENDED_LIMIT_INFORMATION info = new JOBOBJECT_EXTENDED_LIMIT_INFORMATION();
      info.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
      int infoSize=Marshal.SizeOf(info); infoPtr=Marshal.AllocHGlobal(infoSize); Marshal.StructureToPtr(info,infoPtr,false);
      bool configured=SetInformationJobObject(job,9,infoPtr,(uint)infoSize); if (IsFault(fault,"configure-job")) configured=false;
      if (!configured) throw Stage("configure-job",IsFault(fault,"configure-job")?"api-result":new Win32Exception(Marshal.GetLastWin32Error()).Message);

      SECURITY_ATTRIBUTES sa=new SECURITY_ATTRIBUTES { nLength=Marshal.SizeOf(typeof(SECURITY_ATTRIBUTES)), bInheritHandle=true };
      if (!CreatePipe(out outR,out outW,ref sa,0) || !SetHandleInformation(outR,HANDLE_FLAG_INHERIT,0)) throw Stage("create-pipe",new Win32Exception(Marshal.GetLastWin32Error()).Message);
      if (!CreatePipe(out errR,out errW,ref sa,0) || !SetHandleInformation(errR,HANDLE_FLAG_INHERIT,0)) throw Stage("create-pipe",new Win32Exception(Marshal.GetLastWin32Error()).Message);
      nul=CreateFileW("NUL",GENERIC_READ,FILE_SHARE_READ|FILE_SHARE_WRITE,ref sa,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,IntPtr.Zero);
      if (nul == new IntPtr(-1)) throw Stage("create-pipe",new Win32Exception(Marshal.GetLastWin32Error()).Message);
      InitializeProcThreadAttributeList(IntPtr.Zero,1,0,ref attrSize);
      if (attrSize == IntPtr.Zero) throw Stage("create-process",new Win32Exception(Marshal.GetLastWin32Error()).Message);
      attrs=Marshal.AllocHGlobal(attrSize); if (!InitializeProcThreadAttributeList(attrs,1,0,ref attrSize)) throw Stage("create-process",new Win32Exception(Marshal.GetLastWin32Error()).Message); attrsReady=true;
      handleList=Marshal.AllocHGlobal(IntPtr.Size*3); Marshal.WriteIntPtr(handleList,0,nul); Marshal.WriteIntPtr(handleList,IntPtr.Size,outW); Marshal.WriteIntPtr(handleList,IntPtr.Size*2,errW);
      if (!UpdateProcThreadAttribute(attrs,0,new IntPtr(PROC_THREAD_ATTRIBUTE_HANDLE_LIST),handleList,new IntPtr(IntPtr.Size*3),IntPtr.Zero,IntPtr.Zero)) throw Stage("create-process",new Win32Exception(Marshal.GetLastWin32Error()).Message);
      STARTUPINFOEX si=new STARTUPINFOEX(); si.StartupInfo=new STARTUPINFO { cb=Marshal.SizeOf(typeof(STARTUPINFOEX)), dwFlags=STARTF_USESTDHANDLES, hStdInput=nul, hStdOutput=outW, hStdError=errW }; si.lpAttributeList=attrs;
      if (Left(deadline) <= 0) return new ScaffoldProcessResult { TimedOut=true, ExitCode=124, Stdout="", Stderr="" };
      bool processOk=CreateProcessW(application,new StringBuilder(commandLine),IntPtr.Zero,IntPtr.Zero,true,CREATE_SUSPENDED|CREATE_NO_WINDOW|EXTENDED_STARTUPINFO_PRESENT,IntPtr.Zero,cwd,ref si,out pi);
      created=processOk; if (IsFault(fault,"create-process")) processOk=false;
      if (!processOk) throw Stage("create-process",IsFault(fault,"create-process")?"api-result pid="+pi.dwProcessId:new Win32Exception(Marshal.GetLastWin32Error()).Message);
      Close(ref outW); Close(ref errW); Close(ref nul);
      bool assigned=!IsFault(fault,"assign") && AssignProcessToJobObject(job,pi.hProcess);
      if (!assigned) throw Stage("assign",IsFault(fault,"assign")?"api-result pid="+pi.dwProcessId:new Win32Exception(Marshal.GetLastWin32Error()).Message);
      outReader=new StreamReader(new FileStream(new SafeFileHandle(outR,true),FileAccess.Read,4096,false),Encoding.UTF8,true); outR=IntPtr.Zero;
      errReader=new StreamReader(new FileStream(new SafeFileHandle(errR,true),FileAccess.Read,4096,false),Encoding.UTF8,true); errR=IntPtr.Zero;
      outTask=outReader.ReadToEndAsync(); errTask=errReader.ReadToEndAsync();
      if (IsFault(fault,"expire-before-resume")) System.Threading.Thread.Sleep(Math.Max(1,Left(deadline)+20));
      if (Left(deadline) <= 0) {
        string e=""; if (!TerminateJobObject(job,124)) e=new Win32Exception(Marshal.GetLastWin32Error()).Message;
        bool root=WaitHandle(pi.hProcess,cleanupDeadline), streams=WaitStreams(outTask,errTask,cleanupDeadline);
        if (!String.IsNullOrEmpty(e)) throw Stage("terminate-job",e+" pid="+pi.dwProcessId);
        if (!root || !streams) throw Stage("cleanup","pre-resume timeout cleanup pid="+pi.dwProcessId);
        created=false;
        return new ScaffoldProcessResult { TimedOut=true, ExitCode=124, Stdout="", Stderr="" };
      }
      uint resumedCount=IsFault(fault,"resume")?UInt32.MaxValue:ResumeThread(pi.hThread);
      if (resumedCount == UInt32.MaxValue) throw Stage("resume",IsFault(fault,"resume")?"api-result pid="+pi.dwProcessId:new Win32Exception(Marshal.GetLastWin32Error()).Message);
      resumed=true; Close(ref pi.hThread);

      bool rootExited=WaitHandle(pi.hProcess,deadline);
      bool streamsDone=rootExited && WaitStreams(outTask,errTask,deadline);
      bool timedOut=!rootExited || !streamsDone;
      string terminateError="";
      if (timedOut) {
        if (IsFault(fault,"terminate-job")) terminateError="api-result pid="+pi.dwProcessId;
        else if (!TerminateJobObject(job,124)) terminateError=new Win32Exception(Marshal.GetLastWin32Error()).Message;
        WaitHandle(pi.hProcess,cleanupDeadline);
        streamsDone=WaitStreams(outTask,errTask,cleanupDeadline);
        if (!String.IsNullOrEmpty(terminateError)) throw Stage("terminate-job",terminateError);
        if (!streamsDone) throw Stage("cleanup","stdout/stderr did not close before the shared cleanup deadline");
      }
      uint ec=124; if (!timedOut && !GetExitCodeProcess(pi.hProcess,out ec)) throw Stage("exit-code",new Win32Exception(Marshal.GetLastWin32Error()).Message);
      return new ScaffoldProcessResult { TimedOut=timedOut, ExitCode=timedOut?124:(int)ec,
        Stdout=outTask != null && outTask.Status==TaskStatus.RanToCompletion ? outTask.Result : "",
        Stderr=errTask != null && errTask.Status==TaskStatus.RanToCompletion ? errTask.Result : "" };
    } finally {
      string cleanupError="";
      if (created && !resumed) {
        if (!TerminateProcess(pi.hProcess,125)) cleanupError=new Win32Exception(Marshal.GetLastWin32Error()).Message;
        else if (!WaitHandle(pi.hProcess,cleanupDeadline)) cleanupError="suspended root did not exit before cleanup deadline";
      }
      if (outReader != null) outReader.Dispose(); if (errReader != null) errReader.Dispose();
      Close(ref outR); Close(ref outW); Close(ref errR); Close(ref errW); Close(ref nul);
      Close(ref pi.hThread); Close(ref pi.hProcess); Close(ref job);
      if (infoPtr != IntPtr.Zero) Marshal.FreeHGlobal(infoPtr);
      if (attrsReady) DeleteProcThreadAttributeList(attrs); if (attrs != IntPtr.Zero) Marshal.FreeHGlobal(attrs); if (handleList != IntPtr.Zero) Marshal.FreeHGlobal(handleList);
      if (!String.IsNullOrEmpty(cleanupError)) throw Stage("cleanup",cleanupError+" pid="+pi.dwProcessId);
    }
  }
}
'@ -ErrorAction Stop
  } catch {
    $detail = $_.Exception.Message
    throw "[CI-GATE-CONTAINMENT] stage=add-type：$detail"
  }
}

function Invoke-ExternalBeforeDeadline {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments,
    [Parameter(Mandatory)][DateTimeOffset]$Deadline,
    [Parameter(Mandatory)][string]$WorkingDirectory
  )
  $fault = "$env:SCAFFOLD_CI_CONTAINMENT_FAULT"
  Initialize-CiContainment -Fault $fault
  $remainingMs = [int][Math]::Floor(($Deadline - [DateTimeOffset]::UtcNow).TotalMilliseconds)
  if ($remainingMs -le 0) { return [pscustomobject]@{ TimedOut = $true; ExitCode = 124; Stdout = ''; Stderr = '' } }
  $payload = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(([ordered]@{ Command=$Command; Arguments=@($Arguments) } | ConvertTo-Json -Compress)))
  $child = @'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$p=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('__PAYLOAD__'))
$o=$p|ConvertFrom-Json; $a=@($o.Arguments); & "$($o.Command)" @a; exit $LASTEXITCODE
'@.Replace('__PAYLOAD__',$payload)
  $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($child))
  $pwsh = (Get-Command pwsh -ErrorAction Stop).Source
  $line = '"' + $pwsh + '" -NoProfile -NonInteractive -EncodedCommand ' + $encoded
  try {
    return [ScaffoldContainedProcess]::Run($pwsh,$line,$WorkingDirectory,$Deadline.UtcDateTime.Ticks,2000,$fault)
  } catch {
    $e = $_.Exception; while ($e.InnerException) { $e = $e.InnerException }
    if ($e.Message -match '^\[CI-GATE-CONTAINMENT\]') { throw $e.Message }
    throw "[CI-GATE-CONTAINMENT] stage=runner：$($e.Message)"
  }
}
function Invoke-GhBeforeDeadline {
  param(
    [Parameter(Mandatory)][string[]]$Arguments,
    [Parameter(Mandatory)][DateTimeOffset]$Deadline,
    [Parameter(Mandatory)][string]$WorkingDirectory
  )
  return (Invoke-ExternalBeforeDeadline -Command 'gh' -Arguments $Arguments -Deadline $Deadline -WorkingDirectory $WorkingDirectory)
}
function Get-GitOidBeforeDeadline {
  param(
    [Parameter(Mandatory)][string]$Ref,
    [Parameter(Mandatory)][DateTimeOffset]$Deadline,
    [Parameter(Mandatory)][string]$WorkingDirectory
  )
  $r = Invoke-ExternalBeforeDeadline -Command 'git' -Arguments @('-C',$WorkingDirectory,'rev-parse',$Ref) -Deadline $Deadline -WorkingDirectory $WorkingDirectory
  if ($r.TimedOut) { throw "[CI-GATE-TIMEOUT] git rev-parse $Ref。" }
  if ($r.ExitCode -ne 0) { return '' }
  return "$($r.Stdout)".Trim()
}
function Wait-CiRetryBeforeDeadline([DateTimeOffset]$Deadline) {
  $sleepMs = [int][Math]::Min(1000, [Math]::Max(0, [Math]::Floor(($Deadline - [DateTimeOffset]::UtcNow).TotalMilliseconds)))
  if ($sleepMs -gt 0) { Start-Sleep -Milliseconds $sleepMs }
}
# 「这是不是一个 JSON 整数」只能按 **CLR 类型** 判，不能按「能不能转成 long」判：
# ConvertFrom-Json 把 JSON 整数字面量映射成 Int64、小数映射成 Double、超出 Int64 的整数映射成 BigInteger、
# 带引号的映射成 String（本仓 PS 7.6 实测）。而 [long]'11' 与 [long]11.0 都会成功——那两种形态正是要拒的，
# 用 TryParse/强转做判据等于放行它们。列表只收能**无损**落进 Int64 的整数类型；UInt64 刻意不在列
# （它超出 Int64 时无法无损承载，而 total_count 与 id 去重集都以 Int64 承载，放它进来会把一次拒绝
# 变成一次强转溢出异常）。
function Test-JsonInteger($Value) {
  return ($Value -is [byte]) -or ($Value -is [sbyte]) -or ($Value -is [int16]) -or ($Value -is [uint16]) -or
    ($Value -is [int32]) -or ($Value -is [uint32]) -or ($Value -is [int64])
}
# 分页读取的身份契约（T0-CI-PAGED-CONTRACT）：check-runs / workflow-runs / jobs 三个 endpoint 共用本函数，
# 故契约在此一次收口、三处同时生效。
# 为什么 id 是必需的而非可选的：条目没有稳定身份时，一页被重放（或与下一页重叠）会把同一个绿 run 计成两条、
# 凑满 total_count 提前满足终止条件，从而**掩盖一个从未被读到的红 run 并走到 merge**。
# `$items.Count -eq $total` 只证明数量对得上，不证明读到的是 N 个**不同**的 run。真实 GitHub API 的这三个
# endpoint 都返回正整数 id，故「要求 id」是向真实形态收紧、不是新增假设。
function Get-GhPagedCollectionBeforeDeadline {
  param(
    [Parameter(Mandatory)][string]$EndpointTemplate,
    [Parameter(Mandatory)][string]$CollectionProperty,
    [Parameter(Mandatory)][DateTimeOffset]$Deadline,
    [Parameter(Mandatory)][string]$WorkingDirectory
  )
  # $seen 跨页存活（声明在页循环之外）——跨页重放正是它要拦的形态，每页新建一个集合等于没有去重。
  $items = @(); $total = -1L; $seen = [Collections.Generic.HashSet[long]]::new()
  for ($page = 1; $page -le 100; $page++) {
    $endpoint = $EndpointTemplate.Replace('{page}', "$page")
    $api = Invoke-GhBeforeDeadline -Arguments @('api', $endpoint) -Deadline $Deadline -WorkingDirectory $WorkingDirectory
    if ($api.TimedOut) { return [pscustomobject]@{ Readable = $false; TimedOut = $true; Items = @(); Reason = "$CollectionProperty timeout/$page" } }
    try {
      if (($api.ExitCode -ne 0) -or [string]::IsNullOrWhiteSpace($api.Stdout)) { throw "gh api exit $($api.ExitCode) 或空输出" }
      $response = $api.Stdout | ConvertFrom-Json -ErrorAction Stop; $propertyNames = @($response.PSObject.Properties.Name)
      if (($propertyNames -cnotcontains 'total_count') -or ($null -eq $response.total_count)) { throw 'total_count 缺失/null' }
      if (($propertyNames -cnotcontains $CollectionProperty) -or ($null -eq $response.$CollectionProperty)) { throw "$CollectionProperty 缺失/null" }
      $totalRaw = $response.total_count
      if (-not (Test-JsonInteger $totalRaw)) { throw 'total_count 非 JSON integer' }
      $pageTotal = [long]$totalRaw
      if ($pageTotal -lt 0) { throw 'total_count 非负整数契约失败' }
      $collectionRaw = $response.$CollectionProperty
      if ($collectionRaw -isnot [System.Array]) { throw "$CollectionProperty 必须是 JSON array，不能是 scalar/object" }
      $pageItems = @($collectionRaw)
    } catch { return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty p$page/$($api.ExitCode):$($_.Exception.Message)" } }
    if ($total -lt 0) { $total = $pageTotal }
    elseif ($total -ne $pageTotal) { return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty total $total->$pageTotal" } }
    # 身份校验必须在 `$items +=` **之前**：不合格/重复的条目一律不得进入累积，否则它已经把 total 凑近一格，
    # 后面无论怎么判都晚了。三个出口的 Reason 各自可辨（非对象 / id 非正整数 / id 重复），夹具才能证明
    # 命中的是去重出口，而不是更早的 total 漂移或 count>total（hygiene 要求）。
    foreach ($item in $pageItems) {
      if ($item -isnot [pscustomobject]) {
        return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty p$page item-not-object" }
      }
      if ((@($item.PSObject.Properties.Name) -cnotcontains 'id') -or (-not (Test-JsonInteger $item.id)) -or ([long]$item.id -le 0)) {
        return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty p$page id-not-positive-integer" }
      }
      if (-not $seen.Add([long]$item.id)) {
        return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty p$page id-duplicate:$($item.id)" }
      }
    }
    $items += $pageItems
    if ($items.Count -gt $total) { return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty count $($items.Count)>$total" } }
    if ($items.Count -eq $total) { return [pscustomobject]@{ Readable = $true; TimedOut = $false; Items = @($items); Reason = '' } }
    if ($pageItems.Count -eq 0) { return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty empty:$($items.Count)/$total" } }
  }
  return [pscustomobject]@{ Readable = $false; TimedOut = $false; Items = @(); Reason = "$CollectionProperty >100" }
}
function Get-ExactHeadChecksBeforeDeadline {
  param(
    [Parameter(Mandatory)][string]$Head,
    [Parameter(Mandatory)][DateTimeOffset]$Deadline,
    [Parameter(Mandatory)][string]$WorkingDirectory
  )
  $pages = Get-GhPagedCollectionBeforeDeadline `
    -EndpointTemplate "repos/{owner}/{repo}/commits/$Head/check-runs?per_page=100&page={page}" `
    -CollectionProperty 'check_runs' -Deadline $Deadline -WorkingDirectory $WorkingDirectory
  if (-not $pages.Readable) { return [pscustomobject]@{ Readable = $false; TimedOut = $pages.TimedOut; Runs = @(); Blocking = @(); Reason = $pages.Reason } }
  $runs = @($pages.Items)
  $blocking = @($runs | Where-Object {
    ("$($_.status)" -ieq 'completed') -and
    ("$($_.conclusion)" -in @('failure', 'cancelled', 'timed_out', 'action_required', 'startup_failure', 'stale'))
  })
  return [pscustomobject]@{ Readable = $true; TimedOut = $false; Runs = $runs; Blocking = $blocking; Reason = '' }
}

# 候选 run 与本 PR 的关联判定（T0-CI-IDENTITY-DEADLINE）。返回恰好匹配的条目数，调用方要求 == 1。
# 不能写成 `$prs | ? { "$($_.number)" -ceq "$pr" }`：PS 的**属性访问本身**大小写不敏感，只带 `Number` 的条目
# 照样被读成 `number` 而过闸——载荷形状与校验过的那份并不相同，「该 run 属于该 PR」这条证据遂失效（原卡实测
# 踩中）。故属性名走 `-ccontains`，值走 `Test-JsonInteger`（"218" 与 218.0 都不是 JSON 整数，不得靠强转洗白）。
function Get-CandidateRunPrMatchCount {
  param([AllowNull()]$PullRequests, [Parameter(Mandatory)][int]$Pr)
  if ($PullRequests -isnot [System.Array]) { return 0 }
  return @($PullRequests | Where-Object {
    ($_ -is [pscustomobject]) -and (@($_.PSObject.Properties.Name) -ccontains 'number') -and
    (Test-JsonInteger $_.number) -and ([long]$_.number -ceq [long]$Pr)
  }).Count
}

function Get-ExactCandidateJobState {
  param([AllowNull()][object[]]$Jobs, [AllowNull()][object[]]$Wanted)
  $drift = { param([string]$Reason) [pscustomobject]@{ Drift = $true; Reason = $Reason; Blocking = @(); Pending = @() } }
  $expected = @($Wanted)
  if ($expected.Count -eq 0 -or @($expected | Where-Object { $_ -isnot [string] -or [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) {
    return (& $drift 'invalid declared job set')
  }
  $items = @($Jobs); $names = @()
  foreach ($job in $items) {
    if ($job -isnot [pscustomobject]) { return (& $drift 'job item must be object') }
    $props = @($job.PSObject.Properties.Name)
    if (@(@('name', 'status', 'conclusion') | Where-Object { $props -cnotcontains $_ }).Count -gt 0 -or
        $job.name -isnot [string] -or $job.status -isnot [string] -or
        (($null -ne $job.conclusion) -and ($job.conclusion -isnot [string])) -or
        [string]::IsNullOrWhiteSpace($job.name)) {
      return (& $drift 'job item shape/name')
    }
    $names += $job.name
  }
  $actual = @($names | Sort-Object -CaseSensitive)
  $expected = @($expected | Sort-Object -CaseSensitive)
  if (($actual.Count -ne $expected.Count) -or (Compare-Object $actual $expected -CaseSensitive)) {
    return (& $drift "expected=$($expected -join ','); actual=$($actual -join ',')")
  }
  $blocking = @(); $pending = @()
  foreach ($job in $items) {
    $status = $job.status; $conclusion = $job.conclusion
    if (@('queued', 'in_progress', 'pending', 'requested', 'waiting', 'completed') -cnotcontains $status) {
      return (& $drift "job '$($job.name)' status '$status'")
    }
    if ($status -ceq 'completed') {
      if ($conclusion -ceq 'success') { continue }
      if ([string]::IsNullOrWhiteSpace($conclusion)) { return (& $drift "job '$($job.name)' completed without conclusion") }
      $blocking += $job
    } elseif (-not [string]::IsNullOrWhiteSpace($conclusion) -and $conclusion -cne 'success') {
      $blocking += $job
    } else {
      $pending += $job
    }
  }
  return [pscustomobject]@{ Drift = $false; Reason = ''; Blocking = $blocking; Pending = $pending }
}

# TD45：卡片解析共享自 _cards.ps1（front-matter-only 提取 + 大小写敏感取值 + 注释剥离）。
# 旧 Get-CardField 曾整文件 `Select-String`（大小写不敏感、正文/前置元数据不分），与 check-cards 的契约脱节：
# (a) 卡片正文里一行形似 `dod_command: ...` 的文档示例会被当真；(b) `DOD_COMMAND:`（大小写错）仍被找到；
# (c) 不剥注释，`title: 真标题 # 备注` 会把注释泄进 PR 标题。三者 check-cards 从 start 起就已正确拒绝/剥离，
# ship 阶段的取值必须与之同判——否则 ship 执行的是 check-cards 从未核准过的内容。
function Get-CardField($name) {
  if (-not (Test-Path $Card)) { return $null }
  $fm = Get-FrontMatter (Get-Content $Card -Raw)
  if (-not $fm) { return $null }
  $v = Get-Scalar $fm $name
  if ($null -eq $v) { return $null }
  # dod_command 故意保留注释（同 check-cards.ps1:127 的取值——注释可能是命令片段的一部分，如 URL 里的 #），
  # 其余字段（如 title）剥尾随 ` # 注释`，防注释泄入下游用途（PR 标题等）。
  if ($name -eq 'dod_command') { $v = $v.Trim() } else { $v = Get-UncommentedValue $v }
  if ([string]::IsNullOrWhiteSpace($v)) { return $null }
  return $v
}

# 效果账本（TD2）：ship 闸门**真拦截**时追加一行 JSONL 到 _local/effectiveness-ledger.jsonl（gitignored）。
# best-effort：写失败一律吞掉——仪表盘绝不能影响闸门本身。triage 探针 8 读它做各闸拦截计数复审。
# T145: what a review block ACTUALLY cost, replacing the constant detail string the ledger used to carry.
# Measured over 216 ledger rows with gate=review across 65 cards: mean 3.32 blocked rounds, median 2,
# max 18, and 13 cards consumed 55% of all blocks - the ledger proved the cost was concentrated while
# recording nothing about WHERE it came from. The dimensions are the rubric numbers the reasons cite.
# T189 (TD184): the round number reports QUALITY rounds, with the attempt count and the non-success classes
# named beside it. It used to be a bare count of the round-indexed siblings, so a reviewer that timed out
# twice was indistinguishable IN THE COUNT from two genuine quality rounds - and this is the number
# CLAUDE.md's "maker and checker stop after two rounds and queue a human" rule reads. The classification
# lives in scripts/_guard.ps1 so a gate can reach it (17z): nothing can dot-source this file, which takes a
# mandatory TaskId, which is why this counter had no machine check at all. The round FILES are untouched -
# review.ps1's $roundIndex still names every sibling and the corpus still records every attempt.
# NO RATE, RATIO OR DENOMINATOR is derived here or anywhere downstream: ADR 0003 settled that this ledger
# carries honest counts only, and a denominator would re-open a decision the user closed. Best-effort: an
# unreadable verdict yields a plain string rather than taking ship down over an accounting line.
function Test-ShipReviewPathUnsafe([string]$Path, [string]$WorktreePath) {
  $probe = $Path
  while ($probe) {
    $item = Get-Item -LiteralPath $probe -Force -ErrorAction SilentlyContinue
    if ($item -and (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) { return $true }
    if ($probe -eq $WorktreePath) { break }
    $parent = Split-Path $probe -Parent
    if (-not $parent -or $parent -eq $probe) { break }
    $probe = $parent
  }
  return $false
}

function Get-ReviewBlockDetail($WorktreePath, $BranchName) {
  try {
    $rbDir = Join-Path $WorktreePath '.review'
    if (Test-ShipReviewPathUnsafe $rbDir $WorktreePath) { throw 'Unsafe review directory.' }
    $rbSafe = ($BranchName -replace '[\/]', '-')
    $rbText = @(@(Get-ChildItem -LiteralPath $rbDir -Filter "$rbSafe.r*.json" -ErrorAction SilentlyContinue) |
      ForEach-Object { try { if (Test-ShipReviewPathUnsafe $_.FullName $WorktreePath) { throw 'Unsafe round artifact.' }; [string](Get-Content -Raw -LiteralPath $_.FullName) } catch { '' } })
    $rbCost = Get-ScaffoldQualityRoundCount -RoundText $rbText
    $rbDims = @()
    $rbPointer = Join-Path $rbDir "$rbSafe.json"
    if (Test-ShipReviewPathUnsafe $rbPointer $WorktreePath) { throw 'Unsafe review pointer.' }
    if (Test-Path -LiteralPath $rbPointer) {
      $rbObj = Get-Content -Raw -LiteralPath $rbPointer | ConvertFrom-Json
      if ($rbObj -and ($rbObj.PSObject.Properties.Name -contains 'reasons')) {
        $rbDims = @(@($rbObj.reasons) | ForEach-Object { [regex]::Matches([string]$_, '#(\d{1,2})\b') } | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
      }
    }
    $rbDimText = if ($rbDims.Count) { 'dimensions #' + ($rbDims -join ' #') } else { 'dimensions not cited' }
    return "R3 block $($rbCost.Summary) - $rbDimText"
  } catch { return 'R3 block - round and dimensions unreadable' }
}

# T288: WHERE THE R3 REVIEWER SCRIPT COMES FROM - the BASE commit, never the tree under review. review.ps1
# already reads the RUBRIC from the baseline so a diff cannot weaken the standard it is judged by; the
# script that APPLIES that standard now carries the same rule, which is what docs/HARNESS-REVIEW.md's
# "evaluator outside the self-improvement loop" asks for. Until this card the two ship legs disagreed - the
# -Local leg ran the WORKTREE's copy and the remote leg the main checkout's - so T283, a card whose whole
# subject was the verdict extractor, had its own review read by the extractor it was replacing. A card that
# repairs the reviewer is reviewed by the base copy, and its repair serves the NEXT card.
#
# THE SHA IS THE ONE THE SCOPE GATE PINNED, not a ref: the reviewer, the card's allow_paths, the tier and
# the budget then all come from one commit, which is the "one tree" invariant T241 established.
#
# THE REVIEWER IS A BUNDLE, not one file. review.ps1 dot-sources _config / _gitbase / _encoding and, when it
# publishes status, _guard; _guard in turn dot-sources _lessons. Every member comes from the SAME $BaseSha.
# Pulling siblings from $RepoRoot would mix a pinned evaluator with mutable policy and path guards, so a dirty
# main checkout could decide a review whose script claims to come wholly from the baseline.
#
# The temp directory is this run's own and the ship removes it on every exit path including a throw, so a
# failed ship leaves no reviewer copy behind for the next one to pick up.
$script:ShipReviewerDir = ''
function Resolve-ShipReviewerScript {
  [CmdletBinding()]
  param([Parameter(Mandatory)][string]$BaseSha)
  if ($BaseSha -notmatch '^[0-9a-fA-F]{40}$') {
    Write-Warning "[SHIP-NO-REVIEWER] '$BaseSha' is not a pinned commit OID, so no reviewer bundle can be trusted. Rerun after the base gate has resolved one immutable commit."
    return ''
  }
  # Enumerate from the fixed tree, preserving the old bundle surface (review.ps1 + every sibling _*.ps1)
  # without freezing today's dependency graph into this newer harness. review.ps1 already conditionally loads
  # _cards/_scope and future baseline reviewers may grow another underscore sibling; the BASE decides its own
  # coherent bundle. Replacement refs are disabled so a local refs/replace entry cannot silently substitute a
  # different evaluator for the pinned OID. Requiring a regular-file mode also refuses symlink script blobs.
  $expectedBlobs = @{}
  $treeRows = @(& git --no-replace-objects -C $Wt ls-tree -r --full-tree $BaseSha -- scripts 2>$null)
  if ($LASTEXITCODE -ne 0) {
    Write-Warning "[SHIP-NO-REVIEWER] could not enumerate the reviewer dependency bundle at base commit $BaseSha; refusing to read mutable checkout siblings."
    return ''
  }
  foreach ($treeRow in $treeRows) {
    if ([string]$treeRow -notmatch '^100(?:644|755) blob ([0-9a-fA-F]{40})\t(.+)$') { continue }
    $blobOid = $Matches[1].ToLowerInvariant()
    $rel = $Matches[2]
    if ($rel -cne 'scripts/review.ps1' -and $rel -notmatch '^scripts/_[^/]+\.ps1$') { continue }
    if ($expectedBlobs.ContainsKey($rel)) {
      Write-Warning "[SHIP-NO-REVIEWER] base commit $BaseSha enumerated reviewer dependency $rel more than once; refusing an ambiguous bundle."
      return ''
    }
    $expectedBlobs[$rel] = $blobOid
  }
  if (-not $expectedBlobs.ContainsKey('scripts/review.ps1')) {
    # A base with no reviewer is the same state as no backend, and it routes through the paths that state
    # already has - blocking refuses, advisory skips - rather than inventing a third.
    Write-Warning "[SHIP-NO-REVIEWER] the base commit $BaseSha carries no regular scripts/review.ps1, so this ship has no reviewer to run. Restore it on the base branch and rerun the same ship."
    return ''
  }
  $reviewerFiles = @($expectedBlobs.Keys | Sort-Object)
  if (-not $script:ShipReviewerDir) {
    $script:ShipReviewerDir = Join-Path ([System.IO.Path]::GetTempPath()) "scaffold-r3-base-$PID-$((New-Guid).ToString('N').Substring(0, 8))"
    New-Item -ItemType Directory -Force $script:ShipReviewerDir -ErrorAction Stop | Out-Null
  }
  # Stream every blob from git's native stdout straight to a file. Capturing stdout in PowerShell or using a
  # working-tree/archive representation may decode text or apply EOL attributes, changing BOM/newlines. The
  # direct byte stream followed by hash-object proves the file to be executed is the exact baseline blob.
  $gitExe = @(Get-Command git -CommandType Application -ErrorAction Stop)[0].Source
  foreach ($rel in $reviewerFiles) {
    $materialized = Join-Path $script:ShipReviewerDir $rel
    New-Item -ItemType Directory -Force (Split-Path -Parent $materialized) -ErrorAction Stop | Out-Null
    $exportExit = -1
    $exportStderr = ''
    $exportFault = ''
    $blobProcess = $null
    $blobStream = $null
    try {
      $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
      $startInfo.FileName = $gitExe
      $startInfo.UseShellExecute = $false
      $startInfo.CreateNoWindow = $true
      $startInfo.RedirectStandardOutput = $true
      $startInfo.RedirectStandardError = $true
      [void]$startInfo.ArgumentList.Add('--no-replace-objects')
      [void]$startInfo.ArgumentList.Add('-C')
      [void]$startInfo.ArgumentList.Add($Wt)
      [void]$startInfo.ArgumentList.Add('cat-file')
      [void]$startInfo.ArgumentList.Add('blob')
      [void]$startInfo.ArgumentList.Add($expectedBlobs[$rel])
      $blobProcess = [System.Diagnostics.Process]::Start($startInfo)
      $blobStream = [System.IO.File]::Open($materialized, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
      $copyTask = $blobProcess.StandardOutput.BaseStream.CopyToAsync($blobStream)
      $stderrTask = $blobProcess.StandardError.ReadToEndAsync()
      $blobProcess.WaitForExit()
      $null = $copyTask.GetAwaiter().GetResult()
      $exportStderr = $stderrTask.GetAwaiter().GetResult()
      $exportExit = $blobProcess.ExitCode
    } catch { $exportFault = $_.Exception.Message }
    finally {
      if ($blobStream) { $blobStream.Dispose() }
      if ($blobProcess) { $blobProcess.Dispose() }
    }
    if ($exportFault -or $exportExit -ne 0) {
      $why = if ($exportFault) { $exportFault } else { "git exit $exportExit $($exportStderr.Trim())" }
      Write-Warning "[SHIP-NO-REVIEWER] failed to export reviewer dependency $rel from base commit $BaseSha; refusing a partial evaluator: $why"
      return ''
    }
    $actualBlob = [string](& git --no-replace-objects -C $Wt hash-object --no-filters -- $materialized 2>$null | Out-String)
    if ($LASTEXITCODE -ne 0 -or $actualBlob.Trim().ToLowerInvariant() -cne $expectedBlobs[$rel]) {
      Write-Warning "[SHIP-NO-REVIEWER] materialized reviewer dependency $rel does not match its blob at base commit $BaseSha; refusing to run it."
      return ''
    }
  }
  $rvPath = Join-Path $script:ShipReviewerDir 'scripts/review.ps1'
  Write-Host "[R3-REVIEWER-FROM-BASE] sha=$BaseSha" -ForegroundColor DarkGray
  Write-Host "[R3-REVIEWER-BUNDLE-VERIFIED] sha=$BaseSha files=$($reviewerFiles.Count)" -ForegroundColor DarkGray
  Write-Host '  The reviewer and every required sibling are verified blobs from that commit, not copies from either mutable checkout (HARNESS-REVIEW: the evaluator sits outside the loop it judges). A repair to this bundle in THIS branch serves the next card, never this ship''s own review.' -ForegroundColor DarkGray
  return $rvPath
}

# T277 (ADR 0016 item 4): the ONE review finding that stops a ship. R3 answers on two axes that are never
# merged - `spec` (does the diff do what the card closed) and `standards` (is the code sound) - and only a
# `spec` block, only on a Tier-S card, blocks. Every other tier and every `standards` finding stay advisory
# exactly as T68 left them: the merge bar is still the deterministic gates, and this is not a re-arming of
# `ReviewGate = 'required'`, which keeps its own meaning untouched.
#
# THE TIER IS READ FROM THE BASE CARD, never from the branch copy. `allow_paths` is the tier's only input
# and the scope gate binds that same field to the base ref (T241/TD247), so narrowing allow_paths inside a
# branch cannot buy a cheaper bar - the identical binding T280 gave the acceptance run. The config accessors
# are reached through the existence guard check-cards uses: an older _config.ps1 carrying no tier lists
# yields empty lists, which the decision reads as tiering OFF, and off means every card is S (T273).
#
# AN UNDECIDABLE TIER LEAVES THE RUN ADVISORY, and that direction is chosen, not conceded. The tier decision
# itself can be absent or can throw - a checkout predating T273 has no Get-ScaffoldCardTier at all, and the
# function throws when its declared rule row is missing - and this whole mechanism ADDS a refusal to a path
# that was advisory before it (acceptance: "every other tier stays exactly as today"). Manufacturing that
# refusal out of an unrelated fault in the tier machinery would break the promise for cards the rule was
# never about, on evidence that says nothing about the diff. Nothing is silently softened either: the state
# is announced, and the merge bar it falls back to is the deterministic gate set, which is untouched (T68).
#
# NO AXES MEANS NO SPEC BLOCK, and that is why this reads the verdict body rather than the exit code. A
# reviewer timeout, an unreadable verdict and a malformed one exit non-zero exactly like a real block, but
# review.ps1 attaches axes only to a verdict it read cleanly, so those states arrive here with none and stay
# on the advisory path with their `run_status` and their existing timeout guidance. Blocking a ship on an
# outage would be reading an infrastructure failure as a statement about the diff (L21).
function Get-ShipSpecAxisDecision($WorktreePath, $BranchName, $BaseCardText) {
  $tierSPaths = @(); $tier0Paths = @()
  if (Get-Command Get-ScaffoldTierSPaths -ErrorAction SilentlyContinue) { $tierSPaths = @(Get-ScaffoldTierSPaths) }
  if (Get-Command Get-ScaffoldTier0Paths -ErrorAction SilentlyContinue) { $tier0Paths = @(Get-ScaffoldTier0Paths) }
  $specTier = ''
  try {
    if (Get-Command Get-ScaffoldCardTier -ErrorAction SilentlyContinue) {
      # allow_paths comes through the SHARED core, never a second parse in this file (sub-gate 10d): the
      # scope gate reads the same field from the same text with the same extractor a few lines above, and a
      # tier computed from a differently-parsed list would be a tier bound to something other than the paths
      # that gate authorised. It also means the empty case is already refused upstream - the scope gate
      # throws [SHIP-SCOPE-ALLOW-EMPTY] before this runs - so no card reaches here with a list that would
      # compute a cheaper tier by being unreadable.
      $specTier = [string](Get-ScaffoldCardTier -AllowPaths @(Get-ScaffoldCardAllowPathFromText -CardText $BaseCardText) -TierSPaths $tierSPaths -Tier0Paths $tier0Paths -Declared (Get-UncommentedValue (Get-Scalar (Get-FrontMatter $BaseCardText) 'tier'))).Tier
    }
  } catch { $specTier = '' }
  if (-not $specTier) {
    Write-Host "[R3-SPEC-TIER-UNKNOWN] this ship could not compute the card's tier (Get-ScaffoldCardTier is absent or failed on the base card), so the Tier-S spec-axis refusal cannot apply and the R3 review stays advisory - which is what it was before ADR 0016 item 4. The deterministic merge gates are unaffected. Fix the tier machinery (scripts/_cards.ps1 / _config.ps1) if you expected a tiered decision here." -ForegroundColor DarkYellow
  }
  $specBlocked = $false
  $specReasons = @()
  $specSha = ''
  try {
    # The same sanitization review.ps1 names the file with (`[\\/]` - both separators), read from the
    # PRODUCER rather than re-derived, so a branch name carrying a slash cannot make this look at a path
    # nothing writes and report "no spec block" for a verdict that is sitting right there (L23/TD63 item2).
    $specPath = Join-Path (Join-Path $WorktreePath '.review') (($BranchName -replace '[\\/]', '-') + '.json')
    if (Test-ShipReviewPathUnsafe $specPath $WorktreePath) { throw 'Unsafe review pointer.' }
    if (Test-Path -LiteralPath $specPath -PathType Leaf) {
      $specVerdict = Get-Content -Raw -LiteralPath $specPath | ConvertFrom-Json
      # T285: the sha the reviewer actually judged, read from the artifact the PRODUCER wrote rather than
      # re-derived from HEAD. An arbitration binds to THAT sha, so a ruling covers exactly the verdict it
      # names; reading HEAD here would let a ruling written for one review cover whatever is checked out now.
      if ($specVerdict -and ($specVerdict.PSObject.Properties.Name -contains 'sha')) { $specSha = [string]$specVerdict.sha }
      if ($specVerdict -and ($specVerdict.PSObject.Properties.Name -contains 'axes') -and $specVerdict.axes -and
          ($specVerdict.axes.PSObject.Properties.Name -contains 'spec') -and $specVerdict.axes.spec) {
        $specNode = $specVerdict.axes.spec
        # Case-sensitive string equality, the same shape the consumer enforces on the top-level field: a
        # 'BLOCK', or a ["block"] array, is a malformed axis and must not be read as a finding either way.
        if (($specNode.PSObject.Properties.Name -contains 'verdict') -and ($specNode.verdict -is [string]) -and ($specNode.verdict -ceq 'block')) {
          $specBlocked = $true
          if ($specNode.PSObject.Properties.Name -contains 'reasons') { $specReasons = @(@($specNode.reasons) | ForEach-Object { [string]$_ }) }
        }
      }
    }
  } catch { $specBlocked = $false; $specReasons = @(); $specSha = '' }   # an unreadable verdict is the advisory no-verdict path, not a spec finding
  $specText = if ($specReasons.Count) { $specReasons -join ' | ' } else { '(the axis blocked and named no reason)' }

  # -- T285: THE RECORDED RULING THAT ENDS AN IMPASSE, and the only thing that makes a Tier-S spec block
  #    advisory. CLAUDE.md's boundary already ends one - two rounds unresolved, a person rules - and until
  #    T285 the ship could not read that ruling (T279 paid eight rounds with every gate green). T288 split
  #    the two questions it asks into the named helpers above - how many consecutive spec-block rounds the
  #    records show, and which recorded ruling (if any) covers THIS verdict - and 17b'(G) lifts all three
  #    functions out of this file by name, so what that fixture measures is still the production decision.
  $arbRounds = 0
  $arbUsed = $null
  if (($specTier -eq 'S') -and $specBlocked) {
    $arbRounds = Get-ShipReviewRoundHistory $WorktreePath $BranchName
    $arbUsed = Get-ShipArbitrationDecision $BaseCardText $specSha $arbRounds $BranchName
  }
  if ($arbUsed) {
    Write-Host "[R3-SPEC-ARBITRATED] sha=$($arbUsed.Sha) rounds=$arbRounds by=$($arbUsed.By) reason=$($arbUsed.Reason)" -ForegroundColor Yellow
    Write-Host "  The base card records a ruling for the MAKER on this exact reviewed verdict, after $arbRounds consecutive spec-block rounds - the stop rule CLAUDE.md states, in machine form. The spec axis stays a finding and is not deleted: it is advisory FOR THIS SHA ONLY, so the next round starts clean, and this use is in _local/effectiveness-ledger.jsonl. Everything else about the ship is unchanged - the deterministic merge gates decide as always." -ForegroundColor DarkYellow
    Add-CatchRecord 'review' "spec-axis arbitrated on a tier-S card (ruling=maker, by=$($arbUsed.By), sha=$($arbUsed.Sha), consecutive spec-block rounds=$arbRounds): $specText"
  }

  return [pscustomobject]@{
    Tier = $specTier
    # T285: an arbitrated verdict is advisory for this sha only. Every other input to this expression is
    # unchanged, so a card with no ruling, a ruling for the checker, and a ruling naming another sha all
    # block exactly as they did under T277.
    Block = ($specTier -eq 'S' -and $specBlocked -and (-not $arbUsed))
    Reasons = @($specReasons)
    # Built once and thrown at both ship paths (-Local and remote): two copies of one message are two
    # things to keep in step, and the sentinel is what every machine check anchors on.
    Message = "[R3-SPEC-BLOCK] this card computes tier S (ADR 0016) and the R3 review's 'spec' axis returned block: the reviewer says the diff does not do what the card closed, which is the one finding that stops a ship on the riskiest paths. Nothing was merged. Spec findings: $specText. Fix the diff, or fix the card on the base branch as its own commit, then rerun the same ship. What does NOT block, at any tier: a 'standards' finding (advisory everywhere, by decision), a reviewer timeout, and an unreadable or malformed verdict - those carry no axes and keep their existing advisory path. Never bypass with --no-verify."
  }
}

# T288 (T285 recorded this split as its [FOLLOW-UP] and could not take it: 17b'(G) loaded ONE function by
# name through the syntax tree and would not have seen a sibling, so the round count had to be counted
# inside the decision. That fixture now loads the helpers too, which is what makes this extraction legal).
#
# HOW MANY CONSECUTIVE SPEC-BLOCK ROUNDS the producer's own record shows for this branch, counted back from
# the newest. This is the number CLAUDE.md's "two rounds unresolved, stop, a person rules" is about, and it
# is read from the round-indexed siblings review.ps1 writes ('.review/<branch>.r<n>.json') - NEVER from the
# card, which is why a card declaring two rounds against a record showing one arbitrates nothing.
# CONSECUTIVE, AND FROM THE NEWEST BACKWARDS, because the rule is about an impasse that is still live: a
# card whose reviewer blocked twice, was fixed, passed, and later blocked once more is on its FIRST block
# again, and counting every spec block ever recorded would let a settled disagreement pay for a new one.
# THE INDEX IS PARSED AS A NUMBER, never sorted as text ('r10' sorts before 'r2' lexically, and the newest
# round is the one that decides). FOUR THINGS END THE RUN rather than being skipped, and every one of them
# SHORTENS the count, which is the fail-closed direction - fewer rounds means no arbitration, and no
# arbitration means the block stands: a round that is not a spec block; a round that will not parse; a GAP
# in the indices, because a missing record is a round nobody can read and 'consecutive' is exactly what the
# rule asks for; and a round that does not carry this branch's own name, or an index that two files claim
# (`r2.json` and `r02.json`), because a count assembled from records whose ownership is unknown would be
# forgeable by copying a file in. The branch field must be PRESENT and case-exact: absent is unknown, and
# unknown counts against the arbitration, never for it.
function Get-ShipReviewRoundHistory($WorktreePath, $BranchName) {
  $arbRounds = 0
  try {
    if (Test-ShipReviewPathUnsafe (Join-Path $WorktreePath '.review') $WorktreePath) { return 0 }
    $sbcSafe = ($BranchName -replace '[\\/]', '-')
    $sbcRounds = @(@(Get-ChildItem -LiteralPath (Join-Path $WorktreePath '.review') -Filter "$sbcSafe.r*.json" -ErrorAction SilentlyContinue) |
      ForEach-Object { if ($_.Name -match '\.r(\d+)\.json$') { [pscustomobject]@{ Index = [int]$Matches[1]; Path = $_.FullName } } } |
      Sort-Object -Property Index -Descending)
    # GROUPED BY INDEX FIRST, and the duplicate is rejected BEFORE any claimant of that index is counted.
    # Checking it while walking was not enough and the difference is a real hole: with r3, r2 and r02 on
    # disk the walk accepted r3, accepted the first index-2 file, and only then met the second - leaving
    # rounds=2 from three files describing two rounds. Grouping also removes a determinism problem, since
    # Sort-Object may visit equal keys in any order when the two records differ.
    $sbcGroups = @($sbcRounds | Group-Object -Property Index | Sort-Object -Property { [int]$_.Name } -Descending)
    $sbcExpect = -1
    foreach ($sbcGroup in $sbcGroups) {
      $sbcIndex = [int]$sbcGroup.Name
      if ($sbcExpect -ge 0 -and $sbcIndex -ne $sbcExpect) { break }   # a gap: a round nobody can read
      if (@($sbcGroup.Group).Count -ne 1) { break }                   # one index, two files: which round is this?
      $sbcExpect = $sbcIndex - 1
      $sbcRound = @($sbcGroup.Group)[0]
      if (Test-ShipReviewPathUnsafe $sbcRound.Path $WorktreePath) { break }
      $sbcSpec = ''
      try {
        $sbcObj = Get-Content -Raw -LiteralPath $sbcRound.Path | ConvertFrom-Json
        if (-not ($sbcObj -and ($sbcObj.PSObject.Properties.Name -contains 'branch') -and ([string]$sbcObj.branch -ceq $BranchName))) { break }
        if ($sbcObj -and ($sbcObj.PSObject.Properties.Name -contains 'axes') -and $sbcObj.axes -and
            ($sbcObj.axes.PSObject.Properties.Name -contains 'spec') -and $sbcObj.axes.spec -and
            ($sbcObj.axes.spec.PSObject.Properties.Name -contains 'verdict')) { $sbcSpec = [string]$sbcObj.axes.spec.verdict }
      } catch { $sbcSpec = '' }
      if ($sbcSpec -ceq 'block') { $arbRounds++ } else { break }
    }
  } catch { $arbRounds = 0 }
  return $arbRounds
}

# T288 (the other half of T285's [FOLLOW-UP]): WHICH RECORDED RULING, IF ANY, APPLIES TO THIS VERDICT.
# FOUR CONDITIONS, ALL OF THEM, AND FAIL-CLOSED ON EACH: the entry is well formed, its sha equals the sha
# THIS verdict was written at, the ruling is `maker`, and the records show two consecutive spec blocks. Any
# one unmet or unreadable leaves the block where T277 put it. The card text handed in is READ FROM THE BASE
# CARD by the caller, the same text the tier comes from (T280), so a branch cannot arbitrate itself.
# NEVER SILENT: every entry that does NOT apply prints [R3-SPEC-ARBITRATION-INERT] naming the condition it
# missed - otherwise "the ruling I wrote did nothing" reads exactly like "the ship never looked". The
# announcement and the ledger row for an entry that DOES apply stay with the caller, which is where the
# spec reasons and the ledger writer live.
function Get-ShipArbitrationDecision($BaseCardText, $VerdictSha, $Rounds, $BranchName) {
  $arbEntries = @()
  # Reached through the existence guard the tier decision uses: a checkout predating T285 has no
  # Get-ScaffoldCardArbitration, and that reads as "no ruling", which is the blocking direction.
  try { if (Get-Command Get-ScaffoldCardArbitration -ErrorAction SilentlyContinue) { $arbEntries = @(Get-ScaffoldCardArbitration -CardText $BaseCardText) } } catch { $arbEntries = @() }
  foreach ($arbEntry in $arbEntries) {
    $arbWhy = ''
    if ($arbEntry.Finding) { $arbWhy = "the entry is malformed, so check-cards would refuse the card carrying it - $($arbEntry.Finding)" }
    elseif ($arbEntry.Sha -cne $VerdictSha) { $arbWhy = "it is bound to sha '$($arbEntry.Sha)' and this review judged '$VerdictSha'. That is the design, not a fault: a ruling covers exactly the verdict it names, and the next round starts clean" }
    elseif ($arbEntry.Ruling -cne 'maker') { $arbWhy = "the ruling is '$($arbEntry.Ruling)' - the person who looked sided with the reviewer, so the block stands by that ruling and not by default" }
    elseif ($Rounds -lt 2) { $arbWhy = "the review records show $Rounds consecutive spec-block round(s) for this branch, below the two a maker-checker impasse takes (the entry declares $($arbEntry.Rounds); the count is read from '.review/$($BranchName -replace '[\\/]', '-').r<n>.json', never from the card). The first block is the reviewer's turn and the second is the maker's rebuttal" }
    if ($arbWhy) {
      Write-Host "[R3-SPEC-ARBITRATION-INERT] an arbitration entry on the base card did NOT apply to this verdict: $arbWhy. The spec block is unchanged." -ForegroundColor DarkYellow
      continue
    }
    return $arbEntry
  }
  return $null
}

# T288: the two helpers' DECLARED EXAMPLES. They live in this file because the helpers do - nothing can
# dot-source task.ps1 (it takes a mandatory TaskId), so 17b'(G) lifts them out by name through the syntax
# tree and runs them there, the same device that measures the decision itself. A healthy table returns
# NOTHING; each finding names the shape, what was expected, and why that shape must answer the way it does.
# What keeps the tables honest is the mutation batch: deleting a single line of either helper reddens them.
function Test-ShipReviewRoundHistoryExamples {
  [CmdletBinding()]
  param()
  $findings = @()
  $exBlock = '{"branch":"T0-EX","axes":{"spec":{"verdict":"block"}}}'
  $exPass = '{"branch":"T0-EX","axes":{"spec":{"verdict":"pass"}}}'
  $exForeign = '{"branch":"T9-OTHER","axes":{"spec":{"verdict":"block"}}}'
  $exAnon = '{"axes":{"spec":{"verdict":"block"}}}'
  $exRoot = Join-Path ([System.IO.Path]::GetTempPath()) "scaffold-hist-ex-$PID-$((New-Guid).ToString('N').Substring(0, 8))"
  try {
    foreach ($ex in @(
      @{ Name = 'two-contiguous-blocks'; Want = 2; Files = @{ '1' = $exBlock; '2' = $exBlock }
         Why = 'this is the shape the producer writes when a reviewer blocks twice, and the only history a ruling may rest on' },
      @{ Name = 'newest-round-passed'; Want = 0; Files = @{ '1' = $exBlock; '2' = $exPass }
         Why = 'the newest round passed, so the impasse is settled - an older pair of blocks must not pay for a disagreement that is over' },
      @{ Name = 'anonymous-newest'; Want = 0; Files = @{ '1' = $exBlock; '2' = $exAnon }
         Why = 'the newest record carries no branch field, so its ownership is unknown, and unknown counts against a waiver rather than for it' },
      @{ Name = 'foreign-newest'; Want = 0; Files = @{ '1' = $exBlock; '2' = $exForeign }
         Why = "the newest record names another card's branch, which is what a history assembled by copying a file in looks like" },
      @{ Name = 'index-gap'; Want = 1; Files = @{ '1' = $exBlock; '3' = $exBlock }
         Why = 'r2 is missing, so the run ends at the newest round instead of jumping the hole - consecutive is what the stop rule asks for' },
      @{ Name = 'duplicate-index'; Want = 0; Files = @{ '1' = $exBlock; '2' = $exBlock; '02' = $exBlock }
         Why = 'one index is claimed by two files, so three records describe two rounds and neither may be counted' },
      @{ Name = 'no-records'; Want = 0; Files = @{}
         Why = 'a branch nobody has reviewed has no history, and absence is zero rather than an error' }
    )) {
      $exWt = Join-Path $exRoot $ex.Name
      New-Item -ItemType Directory -Force (Join-Path $exWt '.review') | Out-Null
      foreach ($exIdx in @($ex.Files.Keys)) { Set-Content -LiteralPath (Join-Path $exWt ".review/T0-EX.r$exIdx.json") -Value $ex.Files[$exIdx] -Encoding utf8 }
      $exGot = Get-ShipReviewRoundHistory $exWt 'T0-EX'
      if ($exGot -ne $ex.Want) { $findings += "[SHIP-HISTORY-EXAMPLE] '$($ex.Name)' counted $exGot consecutive spec-block round(s), expected $($ex.Want) - $($ex.Why). This count is what decides whether a recorded ruling applies, so a shape counted high hands out a waiver nobody wrote. [FIX] fix the reader, never the example." }
    }
    $exOutside = Join-Path $exRoot 'external-history'
    $exLinkedWt = Join-Path $exRoot 'linked-worktree'
    New-Item -ItemType Directory -Path $exOutside, $exLinkedWt | Out-Null
    foreach ($exIndex in 1, 2) { Set-Content -LiteralPath (Join-Path $exOutside "T0-EX.r$exIndex.json") -Value $exBlock -Encoding utf8 }
    $exLink = Join-Path $exLinkedWt '.review'
    try {
      $exLinkType = if ($IsWindows) { 'Junction' } else { 'SymbolicLink' }
      New-Item -ItemType $exLinkType -Path $exLink -Target $exOutside -ErrorAction Stop | Out-Null
      if ((Get-ShipReviewRoundHistory $exLinkedWt 'T0-EX') -ne 0) {
        $findings += '[SHIP-HISTORY-EXAMPLE] linked .review directory was accepted as local arbitration history.'
      }
    } catch { $findings += "[SHIP-HISTORY-EXAMPLE] unsafe-ancestor example could not execute: $($_.Exception.Message)" }
    finally {
      # Remove only the link before cleaning the owned fixture directory, never traverse its target.
      if (Get-Item -LiteralPath $exLink -Force -ErrorAction SilentlyContinue) { Remove-Item -LiteralPath $exLink -Force }
    }
  } finally {
    $exFull = [IO.Path]::GetFullPath($exRoot)
    $exTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $exFull.StartsWith($exTemp, [StringComparison]::OrdinalIgnoreCase) -or (Split-Path -Leaf $exFull) -notlike 'scaffold-hist-ex-*') { throw 'Unsafe history example cleanup path.' }
    if (Test-Path -LiteralPath $exFull) { Remove-Item -LiteralPath $exFull -Recurse -Force }
  }
  return @($findings)
}

function Test-ShipArbitrationDecisionExamples {
  [CmdletBinding()]
  param()
  $findings = @()
  $nl = [string][char]10
  $exSha = '0123456789abcdef0123456789abcdef01234567'
  $exOther = 'fedcba9876543210fedcba9876543210fedcba98'
  foreach ($ex in @(
    @{ Name = 'maker-this-sha-two-rounds'; Sha = $exSha; Rounds = 2; Ruling = 'maker'; Have = 2; Applies = $true
       Why = 'all four conditions hold, so the ruling a person recorded is the one thing that makes a Tier-S spec block advisory' },
    @{ Name = 'below-the-floor'; Sha = $exSha; Rounds = 2; Ruling = 'maker'; Have = 1; Applies = $false
       Why = 'the records show one block: the first is the reviewer turn and the second is the maker rebuttal, so only a third would be the loop' },
    @{ Name = 'checker-ruling'; Sha = $exSha; Rounds = 2; Ruling = 'checker'; Have = 2; Applies = $false
       Why = 'the person who looked sided with the reviewer, so the block stands by that ruling rather than by default' },
    @{ Name = 'another-sha'; Sha = $exOther; Rounds = 2; Ruling = 'maker'; Have = 2; Applies = $false
       Why = 'a ruling covers exactly the verdict it names - a waiver by card or by branch is the bypass this field exists to refuse' },
    @{ Name = 'malformed-entry'; Sha = '43c6d18'; Rounds = 2; Ruling = 'maker'; Have = 2; Applies = $false
       Why = 'an entry bound to an abbreviated sha is malformed, and check-cards would refuse the card carrying it' }
  )) {
    $exCard = ("---$nl" + "arbitration:$nl  - sha: $($ex.Sha)$nl    rounds: $($ex.Rounds)$nl    ruling: $($ex.Ruling)$nl    by: arc-owner$nl    reason: the asks are outside the closed list$nl" + "---$nl")
    $exGot = Get-ShipArbitrationDecision $exCard $exSha $ex.Have 'T0-EX'
    $exApplied = ($null -ne $exGot)
    if ($exApplied -ne $ex.Applies) { $findings += "[SHIP-ARB-EXAMPLE] '$($ex.Name)' $(if ($exApplied) { 'applied' } else { 'did not apply' }), expected the opposite - $($ex.Why). [FIX] fix the rule, never the example." }
  }
  # ABSENCE IS SILENT, and it is the shape almost every ship has: a card with no ruling must produce no
  # entry and no announcement, or the console would carry an [R3-SPEC-ARBITRATION-INERT] line on every block.
  $exNone = Get-ShipArbitrationDecision ("---$nl" + "id: T0-EX$nl" + "status: todo$nl" + "---$nl") $exSha 2 'T0-EX'
  if ($null -ne $exNone) { $findings += '[SHIP-ARB-EXAMPLE] a card declaring no arbitration produced a ruling, so a waiver would apply to cards nobody ruled on. [FIX] fix the rule, never the example.' }
  return @($findings)
}

function Add-CatchRecord($gate, $detail) {
  try {
    $dir = Join-Path $RepoRoot '_local'
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force $dir -ErrorAction SilentlyContinue | Out-Null }
    (@{ ts = (Get-Date).ToString('o'); gate = $gate; task = $TaskId; detail = "$detail" } | ConvertTo-Json -Compress) |
      Add-Content -Path (Join-Path $dir 'effectiveness-ledger.jsonl') -Encoding utf8
  } catch { }
}

# PR 建好后与自动合并前各调用一次：查询失败、空输出、或与已评审基线不一致均 fail-closed。
function Assert-RemotePrBase {
  param([Parameter(Mandatory)][int]$Pr, [Parameter(Mandatory)][string]$ExpectedBase)
  $actual = (& gh pr view $Pr --json baseRefName -q .baseRefName 2>$null)
  $queryExit = $LASTEXITCODE
  if ($actual) { $actual = "$actual".Trim() }
  if ($queryExit -ne 0 -or [string]::IsNullOrWhiteSpace("$actual")) {
    Add-CatchRecord 'scope' "PR #$Pr baseRefName 查询失败或为空（exit $queryExit）"
    throw "[SHIP-PR-BASE-UNKNOWN] scope gate fail-closed: could not confirm PR #$Pr's actual base branch (gh pr view exit $queryExit, empty output). Check gh auth/API and re-ship; no review or merge until the merge target is confirmed (Codex#1/TD84)."
  }
  if ($actual -cne $ExpectedBase) {
    Add-CatchRecord 'scope' "PR #$Pr baseRefName '$actual' != 评审基线 '$ExpectedBase'"
    throw "[SHIP-PR-RETARGET] scope gate fail-closed (PR merge target != reviewed baseline): PR #$Pr's actual base branch is '$actual' but this run reviewed/scoped against '$ExpectedBase' - `gh pr merge` would merge into '$actual', a diff the review never covered (Codex#1/TD84). Fix with `gh pr edit $Pr --base $ExpectedBase`, or re-ship with the correct -Base."
  }
}

# ── -Local 合并目标不变量（单一实现，ship 入口 + 合并前两处调用；R3 PR#102 九轮 + 双审计 F1/F3）──
# -Local 的合并目标 = 主检出**当前分支** $Cur（`git -C $RepoRoot merge $TaskId` 并入它，本地 ref）。scope/R3 都对照 $Base 算 diff。
# 以下任一即 fail-closed（否则「对照 A 评审、却并入 B」或自并入空操作，随后 cleanup branch -D 丢弃未合并的 work）：
function Assert-LocalMergeTarget {
  param([string]$Cur, [string]$Base, [string]$TaskId)
  if (-not $Cur) {
    throw "[SHIP-DETACHED] scope gate fail-closed (-Local cannot determine the merge target): the main checkout is on a detached HEAD - `git merge $TaskId` would merge into the floating HEAD and land on no branch, while review/scope compare against '$Base'. Run git switch <target-branch> in the main checkout, then re-ship."
  }
  # 注：合并目标 == 任务分支（worktree 内自调用 / -Base <卡 id> 退化）由 ship 顶部已合入的 L86-WT/L86-BASE 守卫更早拦下
  # （$Cur==$TaskId 时 $Base 缺省即 = $TaskId → L86-BASE 命中）；此处不再重复该 clause，避免与 L86 双守卫冗余。
  if ($Base -match '^origin/') {
    throw "[SHIP-REMOTE-BASE] scope gate fail-closed (-Local rejects a remote-qualified baseline): -Local merges $TaskId into the main checkout's current branch '$Cur' (a local ref), but -Base '$Base' is a remote-tracking ref - with local ahead of origin it would review against a stale origin yet merge into local (TD68 wrong-baseline class). Drop the origin/ prefix (pass -Base '$Cur') or use the remote ship path."
  }
  if ($Base -and ($Base -cne $Cur)) {   # F4：大小写敏感（refs 大小写敏感；-Base MAIN vs 分支 main 须判为不一致）
    throw "[SHIP-BASE-MISMATCH] scope gate fail-closed (-Local baseline mismatch): -Local merges $TaskId into the main checkout's current branch '$Cur', but -Base is '$Base' - it would review/scope against '$Base' yet merge into '$Cur' (TD68 wrong-baseline class). git switch '$Base', or pass -Base '$Cur' and re-ship."
  }
}

# The card is a precondition of the phases that READ it: start/red/ship resolve dod_command, allow_paths,
# review_gate and budget out of it, so a missing card is a real error there. `cleanup` reads no card field -
# the worktree path and the branch name are both derived from $TaskId, and the branch delete is authorised
# by the merge credential (T24-CLEANUP-TOKEN), never by the card - so requiring one made the teardown
# unrunnable in a state that is genuinely reachable. NOT every order: for a card that declares `worktree:`,
# T107's [ARCHIVE-HELD] holds the sweep back while that directory is on disk, so the sweep cannot get ahead
# of a full teardown, and that hold is unchanged. The dead end is the BRANCH-ONLY RESIDUAL L238's
# enforced_by names - worktree already gone, local branch still alive because deleting it is gated on the
# credential rather than on a path test. The sweep then legitimately moves the card, and the one command
# that can spend that credential could no longer run: cleanup exited 1 and the branch stayed stranded while
# the message told the operator to create the card, backwards for one that just merged. Upstream issue
# #361, three times in one downstream; four times here as L238. Sub-gate 15h5(a) reproduces that residual.
if ((-not (Test-Path $Card)) -and ($Phase -ne 'cleanup')) { throw "任务卡不存在: $Card（先在 specs\tasks\ 建卡）。" }
# Which of the two cardless states this is, named rather than passed over in silence - and named by two
# DISTINCT sentinels, for the reason the L86-WT/L86-BASE pair is distinct: one shared sentinel would let
# either branch be deleted with sub-gate 15h5 still green.
if (($Phase -eq 'cleanup') -and (-not (Test-Path $Card))) {
  if (Test-Path (Join-Path $RepoRoot "specs/archive/tasks/$TaskId.md")) {
    Write-Host "[CLEANUP-CARD-COLD] the card for $TaskId has already been swept to specs/archive/tasks/. Teardown needs no card - the worktree path and the branch name both derive from the TaskId - so cleanup proceeds and finishes whatever the sweep left behind, typically a local branch still held by its merge credential." -ForegroundColor DarkGray
  } else {
    Write-Host "[CLEANUP-CARD-ABSENT] no card for $TaskId in specs/tasks/ or specs/archive/tasks/. Cleanup proceeds anyway, because teardown derives from the TaskId; if the id is mistyped there will be no worktree and no branch to remove and this run is a no-op." -ForegroundColor Yellow
  }
}

switch ($Phase) {

  'start' {
    Step '校验任务卡（check-cards：id=文件名 / status 枚举 / branch·worktree 不漂移 / dod_command·allow_paths 完整 / 拒卡文占位符 token 字面量 / 拒 front-matter 非缩进无冒号垃圾行，注释行豁免）'
    & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'check-cards.ps1') -TaskId $TaskId
    if ($LASTEXITCODE -ne 0) { throw "任务卡校验未过：先修正 specs\tasks\$TaskId.md 再 start。" }

    Step "R1 建 worktree $Wt（分支 $TaskId ← $Base）"
    # try/catch 给出可操作错误：默认 worktree 根落在 <系统盘>\wt；若该盘/路径不可写，
    # 指向 _config.ps1 WorktreeRoot 而非裸抛 DriveNotFoundException（30-lens C02）。
    try { New-Item -ItemType Directory -Force $WtRoot -ErrorAction Stop | Out-Null }
    catch { throw "无法创建 worktree 根目录 '$WtRoot'：$($_.Exception.Message)。请在 scripts\_config.ps1 设 WorktreeRoot 为一个可写的浅路径（如 C:\wt 或 ~/.wt）后重试。" }
    if (Test-Path $Wt) { throw "worktree 已存在: $Wt。恢复指引——若上次 ship 中断（已 commit、未合并/未推）→ 直接重跑 `-Phase ship` 续（ship 各闸幂等、可安全重入）；若要从头重来 → 先 `-Phase cleanup` 拆除再 start。" }
    & git -C $RepoRoot worktree add -b $TaskId $Wt $Base
    if ($LASTEXITCODE -ne 0) { throw 'git worktree add 失败' }

    Step '引导隔离环境（.venv / node_modules 每 worktree 独立，gitignored）'
    Push-Location $Wt
    try {
      if ((Test-Path (Join-Path $Wt 'pyproject.toml')) -and (Get-Command uv -ErrorAction SilentlyContinue)) {
        & uv venv --python $Py .venv 2>&1 | Write-Host
        & uv sync 2>&1 | Write-Host
      } elseif (Test-Path (Join-Path $Wt 'pyproject.toml')) { Write-Warning 'uv 不在 PATH，跳过后端环境引导。' }
      if (Test-Path (Join-Path $Wt 'frontend/package.json')) {
        Push-Location (Join-Path $Wt 'frontend'); & npm install 2>&1 | Write-Host; Pop-Location
      }
      if (Test-Path (Join-Path $RepoRoot '.env')) { Copy-Item (Join-Path $RepoRoot '.env') $Wt -Force }
    } finally { Pop-Location }

    Write-Host "`nTDD 提醒（R2）：" -ForegroundColor Yellow
    Write-Host "  1) 先写失败测试，确认 RED（跑卡片 dod_command，期望非 0）。" -ForegroundColor Yellow
    Write-Host "  2) 实现到 GREEN，不改冻结契约/manifest（见 _config.ps1 FrozenPaths）。" -ForegroundColor Yellow
    Write-Host "  3) 重构 + R4 测试剪枝（mutation-survivor 法，见 DEVOPS-WORKFLOW.md）。" -ForegroundColor Yellow
    Write-Host "  完成后：pwsh -File scripts\task.ps1 -TaskId $TaskId -Phase ship" -ForegroundColor Yellow
    $dod = Get-CardField 'dod_command'
    if ($dod) { Write-Host "  本卡 DoD: $dod" -ForegroundColor DarkGray }
  }

  'red' {
    # R2 RED-first 检查点（治「RED 仅是 start 阶段的 Write-Host 提醒、无任何强制」）：
    # 跑卡片 DoD，断言**非零**（测试确实先失败），把证据落 .review\<id>.red 供 ship 校验。
    if (-not (Test-Path $Wt)) { throw "worktree 不存在: $Wt（先 -Phase start）" }
    Push-Location $Wt
    try {
      Step 'R2 RED 检查点（TDD：先写会失败的测试 → 跑 DoD，期望 NON-zero）'
      # TD69/L95：红相前置一次 check-cards（同 start:154 / ship:218 的确定性契约），令「嵌套 pwsh -Command + 内插
      #   $ → 双层包裹铸成 vacuous RED」这类坏 dod_command 在**假 RED 被铸出之前**即被拒（红相此前从不校验卡片、
      #   会把 dod_command 的 ParserError 当合法 RED 收下）。单一真相源 = check-cards.ps1，不在此复制其判定逻辑。
      & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'check-cards.ps1') -TaskId $TaskId
      if ($LASTEXITCODE -ne 0) { throw "任务卡校验未过（见上）：先修正 specs\tasks\$TaskId.md 再 -Phase red（TD69：dod_command 勿用嵌套 pwsh -Command + 内插变量写法，改无变量内联）。" }
      $dod = Get-CardField 'dod_command'
      if (-not $dod) { throw "卡片缺少 dod_command 字段: $Card" }
      Write-Host "运行（期望 RED / 非 0）: $dod" -ForegroundColor DarkGray
      # T270/TD250 fix (b): red executes the SAME text ship does. The two settings below answer DIFFERENT
      # questions and the old code conflated them - which is why this phase ran the dod raw for so long,
      # and why every vacuous GREEN in the T243..T268 chain was reachable:
      #   * Get-ScaffoldDodPayload decides what the DOD runs under, INSIDE the child.
      #   * $PSNativeCommandUseErrorActionPreference below decides whether THIS script throws when that
      #     child exits non-zero. Red must read the code rather than throw, so it stays $false - it is not
      #     in tension with wrapping the payload, and the old comment claiming red "has to" run raw was
      #     simply reading one of these two as if it implied the other.
      $PSNativeCommandUseErrorActionPreference = $false   # 非零不抛：要捕获退出码判 RED（与载荷内的 regime 无关）
      & pwsh -NoProfile -Command (Get-ScaffoldDodPayload -Dod $dod) 2>&1 | Write-Host
      $dodExit = $LASTEXITCODE
      if ($dodExit -eq 0) {
        throw 'RED 检查失败：dod_command 退出 0（已是 GREEN）。TDD 要求先有失败测试——先写断言尚未实现行为的测试（应失败）再跑 -Phase red。'
      }
      $proofDir = Join-Path $Wt '.review'
      New-Item -ItemType Directory -Force $proofDir | Out-Null
      $redSha = (& git -C $Wt rev-parse HEAD 2>$null)
      $redSha = if ($redSha) { $redSha.Trim() } else { '(no-commit-yet)' }
      @{ taskId = $TaskId; sha = $redSha; dodExit = $dodExit; phase = 'red' } |
        ConvertTo-Json | Set-Content (Join-Path $proofDir "$TaskId.red") -Encoding utf8
      Write-Host "RED 已确认（dod 退出 $dodExit）。证据落 .review\$TaskId.red。实现到 GREEN 后 -Phase ship（ship 会校验本证据）。" -ForegroundColor Green
    } finally { Pop-Location }
  }

  'ship' {
    # ── -Local 合并目标守卫（fail-closed，置于 ship 最前，任何工作之前；合并前会**再断言一次**见 F3）──
    # 单一实现在 Assert-LocalMergeTarget：detached / 目标==任务分支(F1) / 远端限定 / 与当前分支不一致，任一即拒。
    if ($Local) {
      $shipCur = (& git -C $RepoRoot symbolic-ref --quiet --short HEAD 2>$null)
      if ($shipCur) { $shipCur = $shipCur.Trim() }
      Assert-LocalMergeTarget -Cur $shipCur -Base $Base -TaskId $TaskId
    }
    # ── T147 [SHIP-CONCURRENT-SESSION]：另一个会话是否也在写这个检出？（L114 x6 的「分叉」那一半）──
    # 判定在共享核 Get-ScaffoldConcurrentSessionSignal（与 [SCOPE-UNPUSHED-BASE] 同处 _scope.ps1，防两处诊断打架）。
    # **纯提示、绝不阻断**：L114 的 enforced_by 明写没有闸能兜住这件事，误报挡掉一次合法的单人 ship 比沉默更糟，
    # 所以这里只打印、不碰退出码。三个观测都尽量宽容失败（git 不可用/无远端即视作无信号），best-effort 不可让诊断
    # 影响 ship 本身。放在 ship 最前，是为了让操作者在**范围闸抛出一个解释不了的 block 之前**就先看到真正的原因。
    try {
      $csWtNames = @()
      $csWtRoot = Get-ScaffoldWorktreeRoot
      if ($csWtRoot -and (Test-Path $csWtRoot)) {
        $csWtNames = @(Get-ChildItem -LiteralPath $csWtRoot -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.Name })
      }
      $csBaseOnly = @()
      $csDirty = @()
      $csRef = if ($Local) { $Base } else { ($Base -replace '^origin/', '') }
      if ($csRef) {
        $csBaseOnly = @(& git -C $RepoRoot log --oneline "origin/$csRef..$csRef" 2>$null | Where-Object { $_ })
      }
      $csDirty = @(& git -C $RepoRoot status --porcelain 2>$null |
          ForEach-Object { ($_ -replace '^..\s+', '').Trim() } |
          Where-Object { $_ -and ($_ -match '^specs/') })
      # The MAIN CHECKOUT's copy on purpose, unlike the scope and budget gates below, which read the base
      # ref (T241/TD247). This probe reports what is happening in THIS checkout right now - a committed
      # base copy would answer a question nobody asked here - and it judges nothing: it prints a NOTICE.
      $csAllow = if (Test-Path $Card) { @(Get-ScaffoldCardAllowPath -CardPath $Card) } else { @() }
      $csSignals = @(Get-ScaffoldConcurrentSessionSignal -WorktreeName $csWtNames -SelfCardId $TaskId `
          -BaseOnlyCommit $csBaseOnly -DirtyRegistryPath $csDirty -AllowPath $csAllow)
      foreach ($csSig in $csSignals) {
        Write-Host "[SHIP-CONCURRENT-SESSION] $($csSig.Signal): $(@($csSig.Detail) -join ', ')" -ForegroundColor Yellow
      }
      if (@($csSignals).Count -gt 0) {
        Write-Host "[SHIP-CONCURRENT-SESSION] another session appears to be writing to this checkout. This is a NOTICE, not a gate - ship continues. It matters because the scope gate judges against the REMOTE base, so a second session's commits or files can surface below as out-of-scope paths you never touched. Before trusting a scope block, check whether the paths it names are theirs. [L114]" -ForegroundColor Yellow
      }
    } catch { }   # 诊断绝不影响 ship：任何观测失败即无信号

    # GitHub 的 baseRefName 不含 origin/ 前缀。显式 -Base origin/master 与 master 同义；从这里起远端
    # fetch / PR create / base 校验 / review 统一使用此单源，避免部分归一化造成假错配。
    $shipBase = if ($Local) { $Base } else { $Base -replace '^origin/', '' }
    if (-not $Local -and [string]::IsNullOrWhiteSpace($shipBase)) {
      throw "远端 ship 的 -Base '$Base' 归一化后为空；请传分支名（如 master 或 origin/master）。"
    }

    if (-not (Test-Path $Wt)) { throw "worktree 不存在: $Wt（先 -Phase start）" }

    # T26-SHIPSAGA 腿完成跟踪：ship 是多腿可重试 saga，任一腿失败须在失败时刻自述进度（TD85 事件实证：四个并发
    # 会话各自从散文重推断状态、其一误判）。有序腿名与下方既有 Step 标签一一对应；只记相内内存、不写盘
    # （ship 幂等可重入，跨进程状态无必要）。catch 只报告后原样 rethrow——异常语义/退出码/失败面均不变。
    $sagaLegs = @('card-check', 'DoD', 'verify', 'commit', 'scope-gate', 'budget-gate', 'license-gate', 'leak-gate') + $(if ($Local) { @('R3-review', 'local-merge') } else { @('push+PR', 'R3-review', 'CI-gate', 'merge') })
    $sagaDone = @()
    # T143 [SHIP-TIME]: per-leg wall clock. The saga already NAMES its legs; it never recorded how long
    # one took, so 'where did that ship's sixteen minutes go' could only be answered with a stopwatch and
    # every latency proposal was argued rather than measured. Measured by hand over PRs #214-#228 before
    # this existed: the remote leg is 49-52s, of which CI-complete to merged is 6s.
    # IT PRINTS, IT NEVER DECIDES. No ship decision reads these values and none is written to the
    # effectiveness ledger (ADR 0003 settled that ledger as honest counts; durations are a different
    # artifact). ADR 0007 rejected a -Only router with 'it adds harness complexity to solve a problem
    # caused by harness complexity' - an instrument that decided would be that same mistake.
    $sagaClock = [System.Diagnostics.Stopwatch]::StartNew()
    $sagaMark = [double]0
    function Complete-ShipLeg([string]$LegName) {
      $legNow = $script:sagaClock.Elapsed.TotalSeconds
      Write-Host "[SHIP-TIME] $LegName $([math]::Round($legNow - $script:sagaMark, 1))s" -ForegroundColor DarkGray
      $script:sagaMark = $legNow
      return $LegName
    }
    # 腿间非腿操作的显式追踪（R3 r3 #9）：卡校验→DoD 之间还有预检（评审后端可用性/账号守卫/环境引导）与 RED 证据闸
    # 两段非 Step 操作——只按「首个未完成腿」推断会把这些失败误报成 DoD（TD85 事件正是这样被误判的）；$sagaAt 非空
    # 即失败点真相源，进入某腿的 Step 前清空。
    $sagaAt = ''
    $sagaLocalMerged = $false  # -Local 合并是否已成功——合并腿失败按阶段状态分流（post-merge 凭据 / 合并中 / 守卫拦下）
    try {   # saga 报告层（体内缩进保持原样：外科式最小 diff，不重排任何既有闸门）

    Step '卡片校验（check-cards：ship 与 start 用同一份确定性契约重跑，TD45——防 start 后卡片漂移 / -Phase ship 未 fresh start 时跳过校验）'
    & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'check-cards.ps1') -TaskId $TaskId
    if ($LASTEXITCODE -ne 0) { throw "任务卡校验未过（见上）：ship 与 start 共用同一份 check-cards 契约——先修正 specs\tasks\$TaskId.md 再 ship（卡片可能在 start 后被编辑，或本次 ship 未经 fresh start）。" }
    $sagaDone += (Complete-ShipLeg 'card-check')
    $sagaAt = 'preflight (review-backend availability / account guard / env bootstrap - between card-check and DoD)'

    # T68：合并闸 = 确定性闸；R3 默认降为「随叫随到的第二意见」（_config `ReviewGate` 留空）。
    # 仅 ReviewGate='required'（opt-in 旧强制行为）时，远端 ship 才要求评审后端在场并 fail-fast。
    $reviewGate = if ($ScaffoldConfig.ContainsKey('ReviewGate')) { "$($ScaffoldConfig.ReviewGate)".Trim() } else { '' }
    # T242/TD248: the card's OWN `review_gate:` is the SECOND input to this decision. It used to be read by
    # nobody at ship time - load-bearing for T161's `acceptance:` trigger at card-VALIDATION time, inert for
    # the gate it names - so a card could declare a review and ship without one (measured on T238 and T239,
    # whose reviews both returned block once someone ran them by hand, after the merge). What the card buys
    # is a RUN, never a veto: T68's merge bar stays the deterministic gates, so the card-driven state is
    # advisory. The decision keeps Run and Blocking as two fields precisely so routing the card into the
    # `required` path would be a visible failure rather than a quiet re-arming of a gate the project removed.
    # WHICH COPY: the MAIN CHECKOUT's, like the PR-title leg below and unlike the scope and budget gates.
    # Those two BIND to the base card because they are judgements a branch must not be able to widen; this is
    # not a judgement, it is a request for a read, and the unsafe direction does not exist here - deleting the
    # field in-branch only returns the T68 default of no review. Which copy ship should read in general is
    # TD247/T241's open question and is deliberately not settled here.
    $cardReviewGate = if (Test-Path $Card) { "$(Get-CardField 'review_gate')" } else { '' }
    $reviewRun = Get-ScaffoldReviewRunDecision -ConfigReviewGate $reviewGate -CardReviewGate $cardReviewGate
    $reviewAvail = (Get-Command codex -ErrorAction SilentlyContinue) -or ($ScaffoldConfig.ContainsKey('ReviewCommand') -and $ScaffoldConfig.ReviewCommand)
    # Asks the DECISION, not the config string a second time: leaving `$reviewGate -eq 'required'` here
    # would keep two ways of asking "is the reviewer a gate?" in one file, which is the drift this card
    # exists to remove. Only the blocking state fail-fasts - an advisory run with no backend is a no-op.
    if ($reviewRun.Blocking -and -not $reviewAvail) {
      Add-CatchRecord 'review' '无评审后端（ReviewGate=required，ship 前 fail-fast）'
      throw "[SHIP-NO-REVIEWER] no review backend: ReviewGate='required' must pass the second-model review (R3), but codex was not detected and _config.ps1 ReviewCommand is empty - stopped BEFORE ship can merge. Install codex, set ReviewCommand to another backend, or leave ReviewGate empty for advisory mode."
    }
    # 个人账号守卫：push/PR/合并前确认仅配置的个人账号（禁组织）。-Local 无远端 → 跳过。
    if (-not $Local) {
      Assert-PersonalAccount -RepoRoot $RepoRoot -CheckRemote   # _guard.ps1 is loaded at the top (T189)
    }
    Push-Location $Wt
    try {
      # T68 subtraction: the RED-evidence gate was removed from ship (merge gates = deterministic set;
      # "thinner with use"). -Phase red remains an optional TDD aid; ship neither reads nor mints
      # any evidence around it (T69 removed the T35 waterline-receipt residue as well).

      $sagaAt = ''   # 自此失败点=首个未完成腿（各腿 Step 之间再无非腿操作）
      Step 'R2 DoD 闸门（必须全绿）'
      $dod = Get-CardField 'dod_command'
      if (-not $dod) { throw "卡片缺少 dod_command 字段: $Card" }
      Write-Host "运行: $dod" -ForegroundColor DarkGray
      # 让 dod 里**任一** native 命令非零即失败（否则只取最后一句退出码，
      # 多语句 dod（如 python -c ...; pytest ...）会被末句成功掩盖前句失败）。
      # T270/TD250 fix (b): built by Get-ScaffoldDodPayload, the SAME call `-Phase red` makes. The regime
      # used to be spelled here as a literal and nowhere else, which is how the two phases came to disagree
      # about what this field MEANS - one field, two executors, each spelling the contract itself (the
      # shape T229 removed for the card-id grammar). Do not inline it back.
      & pwsh -NoProfile -Command (Get-ScaffoldDodPayload -Dod $dod)
      if ($LASTEXITCODE -ne 0) { Add-CatchRecord 'dod' "DoD exit $LASTEXITCODE"; throw "DoD 未通过（退出码 $LASTEXITCODE）。修绿再 ship。" }
      $sagaDone += (Complete-ShipLeg 'DoD')

      Step 'R2 verify 总闸（项目级确定性回归；free+private 下本地即权威）'
      # 跑工作树自带的 verify.ps1（卡 DoD 只验本卡窄面，verify 兜项目级跨子系统回归）。空配置/脚手架期 verify 自身优雅降级。
      & pwsh -NoProfile -File (Join-Path $Wt 'scripts/verify.ps1')
      if ($LASTEXITCODE -ne 0) { Add-CatchRecord 'verify' "verify exit $LASTEXITCODE"; throw 'verify.ps1 未过（项目级回归红）。修绿再 ship。' }
      $sagaDone += (Complete-ShipLeg 'verify')

      Write-Host "`n安全闸提醒（建议性 · 非自动闸）：涉敏感面时，commit 前宜先跑 /security-review-local。" -ForegroundColor Yellow
      Write-Host "  注：security-review 是模型在环的【建议】层（非确定性 DoD 闸）；硬编码密钥/被追踪机密由下面 check-secrets 强制拦。" -ForegroundColor DarkGray

      Step 'commit changes'
      # TD44：本脚本关了 $PSNativeCommandUseErrorActionPreference（:46）→ 原生命令非零不抛，须逐个显式校验退出码，
      # 否则失败静默续跑（同 gh-bootstrap:180-184 的已知坑）。git add 失败会漏 stage → 后续 push 陈旧内容。
      & git add -A
      if ($LASTEXITCODE -ne 0) { throw "git add -A 失败（exit $LASTEXITCODE）——已中止以防提交不完整。检查工作树后重 ship。" }
      & git diff --cached --quiet
      if ($LASTEXITCODE -ne 0) {
        & git commit -m "feat($TaskId): 实现至 DoD 全绿`n`n参见 specs/tasks/$TaskId.md"
        if ($LASTEXITCODE -ne 0) { throw "git commit 失败（exit $LASTEXITCODE）——已中止。检查 git 状态/身份配置后重 ship。" }
      } else { Write-Host '无新增改动可提交。' }
      $sagaDone += (Complete-ShipLeg 'commit')

      Step '范围闸（allow_paths 确定性越界拦截；评审只判质量，不兜底范围）'
      # 判定核（allow_paths 取值 / 改动清单求值 / 段级匹配器）见 scripts/_scope.ps1——独立检查器
      # scripts/check-scope.ps1 打的是同一枚核（TD93 item①，防第二实现漂移）。本处只留 ship 侧策略：
      # 远端定向 fetch + F5 fail-closed + 效果账本 + saga 文案。
      # Undecidable input - diff evaluation failed, card absent from the baseline, allow_paths parsed
      # empty - blocks fail-closed. Uncertain is not permission.
      # T241/TD247 — BASE-CARD BINDING. allow_paths is read BELOW, from the card as it exists on the base
      # ref, once that ref is resolved; never from `$Card`, which is the MAIN CHECKOUT's working file. The
      # standard and the diff have to come from one tree. Read one from a working file and the other from a
      # committed ref and an UNCOMMITTED edit in the main checkout sets the merge standard - leaving no
      # trace in history, and, because sessions share a checkout, possibly not even written by the shipper.
      # This is the binding eight faces in this tree already described (this gate's own throw text below
      # among them, and `check-scope.ps1`, which implements it) while the code here did otherwise.
      # TD68：范围闸对照的基线须是**本次 ship 的合并目标**——远端 ship=origin/<base>，-Local=本地 <base>
      # （line ~382 并入本地当前分支；前次 -Local 合并会让本地合法领先 origin，此时强用 origin 会把前次文件当越界误拦，
      # R3 PR#102 三轮指出）。-Local 走本地解析；远端则先刷新、再只接受远端跟踪引用。与 review.ps1 共用解析器防漂移。
      # F5（TD84）：远端 ship 必须在范围/评审闸前刷新并使用 GitHub 合并目标的远端跟踪引用。
      # 仅「分支名相同」不能证明提交相同；缺失或陈旧的 origin/<base> 若回退本地，会重入 TD68 的错基线 fail-open。
      if (-not $Local) {
        $remoteBaseName = $shipBase
        & git -C $Wt fetch --quiet --no-tags origin "+refs/heads/${remoteBaseName}:refs/remotes/origin/${remoteBaseName}" 2>$null
        if ($LASTEXITCODE -ne 0) {
          Add-CatchRecord 'scope' "无法刷新远端基线 refs/remotes/origin/$remoteBaseName"
          throw "范围闸 fail-closed：无法在闸门前刷新远端基线 refs/remotes/origin/$remoteBaseName。检查网络、origin 与分支名后重 ship；禁止回退到可能陈旧的本地基线（TD68/TD84）。"
        }
        $scopeBaseRef = Resolve-ScaffoldBaseRef -GitDir $Wt -BaseName "origin/$remoteBaseName"
        if ($scopeBaseRef -cne "refs/remotes/origin/$remoteBaseName") {
          Add-CatchRecord 'scope' "刷新后远端基线仍无法解析：refs/remotes/origin/$remoteBaseName"
          throw "范围闸 fail-closed：刷新后仍无法解析远端基线 refs/remotes/origin/$remoteBaseName；禁止回退本地引用。"
        }
      } else {
        $scopeBaseRef = Resolve-ScaffoldBaseRef -GitDir $Wt -BaseName $Base -PreferLocal:$Local
        if (-not $scopeBaseRef) {
          Add-CatchRecord 'scope' "本地基线引用无法解析（refs/heads/$Base 与 refs/remotes/origin/$Base 均不存在）"
          throw "范围闸无法解析本地基线引用：refs/heads/$Base 与 refs/remotes/origin/$Base 均不存在，无法求改动清单。显式传 -Base <分支> 或修复基线后重 ship。"
        }
      }
      # ONE read of the base card, serving BOTH this gate and the budget gate below. That gate asks the same
      # question of the same file at the same ref, so a second `git show` there is a second chance to bind
      # differently - which is exactly the drift TD247 records against the pair. Only the READ is shared;
      # each gate keeps its own policy about what an answer means.
      # EXIT CODES ARE CHECKED EXPLICITLY, and that is not defensive habit - it is TD44: line 46 turns
      # $PSNativeCommandUseErrorActionPreference OFF, so a failing native command does NOT throw. Unchecked,
      # a failed read hands back an empty string, an empty string is indistinguishable from a card that
      # declares no allow_paths, and this gate would name the wrong repair exactly when something is broken.
      # PIN THE BASE COMMIT ONCE, and use the sha - never the ref name - for all three reads below (the
      # card, the change list, the budget's numstat). A ref is mutable: another process fetching between
      # two of those calls moves it, and the standard then comes from one commit while the diff comes from
      # another, which is precisely the "one tree" claim this card exists to make true. check-scope.ps1
      # pins for this reason (its lines 236-254, codex R3 r5 #2 on that card); ship carried the hole until
      # R3 round 1 named it here. Resolving also REPLACES the old fallback discrimination: a ref that does
      # not resolve is caught here, so a later git show failure can only mean the card is absent.
      $scopeBaseSha = "$(& git -C $Wt rev-parse --verify --quiet "${scopeBaseRef}^{commit}" 2>$null)".Trim()
      if (-not $scopeBaseSha) {
        Add-CatchRecord 'scope' "base ref '$scopeBaseRef' does not resolve to a commit - the card's allow_paths cannot be read"
        throw "[SHIP-SCOPE-BASEREF-UNREADABLE] the base ref '$scopeBaseRef' does not resolve to a commit, so this card's allow_paths cannot be read from it. An unreadable baseline must never be treated as 'no restriction' - that turns a blocking gate into a silent pass precisely when the baseline is broken. Fix the baseline (git fetch origin $shipBase) and rerun the same ship."
      }
      $baseCardText = (& git -C $Wt show "${scopeBaseSha}:specs/tasks/$TaskId.md" 2>$null | Out-String)
      if ($LASTEXITCODE -ne 0) {
        Add-CatchRecord 'scope' "card absent from base '$scopeBaseRef' ($scopeBaseSha)"
        throw "[SHIP-SCOPE-CARD-ABSENT] specs/tasks/$TaskId.md is not on '$scopeBaseRef' ($scopeBaseSha), so this ship has no scope standard to judge against. The copy in your checkout is not it: allow_paths is read from the base commit, so a card that exists only in a working tree authorises nothing. Fix: commit specs/tasks/$TaskId.md to '$shipBase'$(if (-not $Local) { ' and push it' }), then rerun the same ship."
      }
      $fmAllow = @(Get-ScaffoldCardAllowPathFromText -CardText $baseCardText)
      # The PINNED sha, not the ref: same tree as the card read above, by construction rather than by luck.
      try { $changed = @(Get-ScaffoldChangedPath -GitDir $Wt -BaseRef $scopeBaseSha) }
      catch {
        Add-CatchRecord 'scope' "git diff $scopeBaseRef...HEAD 求值失败"
        throw "范围闸无法求值改动清单（git diff --name-only $scopeBaseRef...HEAD 非零退出）。确认基线 '$scopeBaseRef' 在本仓可解析后重 ship。"
      }
      if ($fmAllow.Count -eq 0) {
        Add-CatchRecord 'scope' 'allow_paths parsed empty from the base card'
        throw "[SHIP-SCOPE-ALLOW-EMPTY] no allow_paths list items parsed from the front-matter of specs/tasks/$TaskId.md as it exists on '$scopeBaseRef' ($scopeBaseSha). The BASE copy is the standard; the one in your working tree does not count (T241/TD247). Fill in allow_paths, commit it to '$shipBase', then rerun the same ship. check-cards enforces the field too, and note that the inline flow form parses to 0 items here - see sub-gate 10d."
      }
      $oos = @(Get-ScaffoldOutOfScopePath -ChangedPath $changed -AllowPath $fmAllow)
      if ($oos.Count -gt 0) {
        Add-CatchRecord 'scope' ($oos -join ', ')
        # L114 (six recurrences, the latest 2026-08-25): the commonest cause of this block is NOT an out-of-scope
        # edit - it is a base branch committed locally and never pushed. This gate diffs against the REMOTE base
        # (TD68/TD84 fail-closed), so commits the operator made to <base> themselves surface here as out-of-scope,
        # and the throw below names none of the three repairs that would actually work. Report it from LIVE data
        # and stay silent unless it truly holds (L97: never hardcode a cause into failure text).
        # Remote mode only - under -Local the base IS the local branch, so the state cannot arise by construction.
        # Diagnosis only: the throw fires either way, and any failure in here must leave that untouched.
        if (-not $Local) {
          $unpushedCount = 0
          $baseOnly = @()
          try {
            # The pinned sha here too (T241, R3 round 2): this diagnosis explains the block the gate above
            # just produced, so it has to be measured against the same commit that produced it. Re-reading
            # the mutable ref would let the explanation describe a different baseline than the verdict.
            $unpushed = @(& git -C $Wt log --format=%H "$scopeBaseSha..refs/heads/$shipBase" 2>$null | Where-Object { $_ })
            if ($LASTEXITCODE -eq 0 -and $unpushed.Count -gt 0) {
              $unpushedCount = $unpushed.Count
              $baseOnly = @(Get-ScaffoldChangedPath -GitDir $Wt -BaseRef $scopeBaseSha -TipRef "refs/heads/$shipBase")
            }
          } catch { $unpushedCount = 0; $baseOnly = @() }   # a broken probe must never mask or replace the real block
          if ($unpushedCount -gt 0 -and @(Get-ScaffoldUnexplainedOutOfScopePath -OutOfScopePath $oos -BaseOnlyPath $baseOnly).Count -eq 0) {
            Write-Host "[SCOPE-UNPUSHED-BASE] every path listed below is carried by $unpushedCount commit(s) that exist on local '$shipBase' but not on 'origin/$shipBase'. This gate judges against the REMOTE base, so commits you made to '$shipBase' yourself (a card registration, a lessons entry) show up here as out-of-scope. Rerunning this ship cannot help - it re-judges against the same remote base. Fix: git push origin $shipBase   then rerun the same ship (saga resume). [L114]" -ForegroundColor Yellow
          }
        }
        # T36-DOCTRINE: watershed 后禁历史改写——卡外改动走反向提交/base 前移吸收，不 rebase（权威长文见 docs/DEVOPS-WORKFLOW.md TD85-RESUME 段）
        throw "[SHIP-SCOPE-BLOCK] out-of-scope changes (not under the card's allow_paths): $($oos -join ', ')`nFix (L18): revert card-external changes out of this branch with a reverse commit, or let base advance to absorb them and rerun ship (no rebase/history rewrite after the watershed); if they truly belong to this card, widen allow_paths on main first."
      }
      Write-Host "范围闸 PASS（$($changed.Count) 个改动文件均在卡 allow_paths 内）" -ForegroundColor DarkGray
      $sagaDone += (Complete-ShipLeg 'scope-gate')

      Step 'card budget gate（[CARD-BUDGET-OVER]；预算取自 BASE 卡，故分支内抬高无效）'
      # T233/TD235. The scope gate above judges WHERE a card may change things; this one judges HOW MUCH.
      # It is the same shape as its neighbour on purpose - it reuses that gate's already fail-closed,
      # already refreshed $scopeBaseRef, so the budget and the diff are read from the SAME tree. Reading
      # the budget from one ref and the diff from another is how two halves of one judgement come to
      # describe different trees (measured live on this mechanism's own card, T231).
      #
      # BASE-CARD BINDING. The budget is read from the card as it exists on the base ref, never from the
      # branch copy - exactly the reason allow_paths is read there. If a card could raise its own budget
      # inside the diff the budget is judging, the budget judges nothing. Raising it is ALLOWED and is
      # meant to be VISIBLE: land the raise on the base branch as its own commit with the reason in that
      # commit, which is the rule DocBudgets already states for its own ceilings (_guard.ps1). There is
      # deliberately no cap on what a budget may be - a cap is the fixed ceiling this mechanism exists to
      # remove, since a ceiling of N makes N-1 a pass.
      #
      # NO DEFAULT, AND THAT IS THE CONTRACT. DefaultBudget is 0 here even though _config.ps1 carries one:
      # the configured default is the METER's reporting affordance (check-budget.ps1 prints a number for a
      # card that declares none) and it must never become a merge block. `budget:` is not a required field,
      # so a card that predates it - or one deliberately opting out - has to ship exactly as it does today.
      # A card blocks on the number IT declared, or on nothing at all.
      #
      # The judgement itself is Get-ScaffoldCardBudgetDecision (_guard.ps1), the same core check-budget.ps1
      # and check-cards call. Nothing here decides anything: no threshold, no default and no fraction is
      # named on this side, which is why the card's DoD asserts the absence as an inverse arm. A private
      # copy of a threshold is precisely how the meter and this gate would come to disagree without either
      # being obviously wrong.
      # ONE READ, SHARED WITH THE SCOPE GATE (T241/TD247). This gate used to run its OWN `git show` against
      # the same ref for the same file. The read - and with it the TD44 exit-code check and the
      # absent-card-versus-unreadable-ref discrimination R3 required on T233 - now lives at the scope gate
      # above, the first gate that needs it. Two reads were two chances to bind differently, and binding
      # differently is the defect TD247 records against this pair; one read makes agreement structural
      # rather than a convention two comments have to keep restating.
      # `$baseCardText` is guaranteed to be the base ref's copy or nothing at all: the scope gate throws on
      # an unreadable ref AND on a card the ref does not carry, so "no base card to read a budget from" is
      # no longer a state ship can reach.
      $budgetBaseCard = $baseCardText
      # The BRANCH card is the WORKTREE's copy, not the main checkout's. $Card points at the main checkout
      # (line 101), which on this path holds the same content as the base ref - so passing it would hand
      # the core two copies of the base card and the base-card binding would be untestable: swapping the
      # two arguments would change nothing observable. Found by R3; the branch-binding sub-gate was passing
      # because the branch card was never read at all, not because the binding held.
      $budgetBranchCard = ''
      $budgetBranchPath = Join-Path $Wt "specs/tasks/$TaskId.md"
      try { if (Test-Path $budgetBranchPath) { $budgetBranchCard = (Get-Content $budgetBranchPath -Raw) } } catch { $budgetBranchCard = '' }
      # BranchCardText is handed over and deliberately ignored by the core. It is passed so the binding is
      # visible at the call site and testable, rather than being an unstated convention this entry point
      # could quietly get wrong.
      $budgetLines = 0
      $budgetMeasured = $false
      # The PINNED sha again (T241): the budget text and the budget measurement must describe one tree, and
      # so must this gate and the scope gate above - re-dereferencing the ref here would reopen exactly the
      # split the pin closes, one gate down.
      $budgetNumstat = (& git -C $Wt diff "$scopeBaseSha...HEAD" --numstat 2>$null | Out-String)
      if ($LASTEXITCODE -ne 0) {
        Add-CatchRecord 'card-budget' "git diff $scopeBaseRef...HEAD --numstat failed - budget undecidable"
        throw "[CARD-BUDGET-UNDECIDABLE] could not measure the diff against '$scopeBaseRef' (git diff --numstat exited non-zero). A failed measurement is not a small one: letting it through would report every unmeasurable tree as within budget. Confirm the baseline resolves and rerun the same ship."
      }
      foreach ($bLn in ($budgetNumstat -split "`r?`n")) {
        if ($bLn -match '^(\d+)\s+(\d+)\s+') { $budgetLines += [int]$Matches[1] + [int]$Matches[2]; $budgetMeasured = $true }
      }
      if (-not $budgetMeasured) {
        # The command SUCCEEDED and produced no countable lines: an empty diff, or a binary-only one whose
        # numstat columns are '-'. That is genuinely nothing to judge. It is now distinct from a git
        # failure, which threw above - the two used to be indistinguishable here, and conflating them is
        # what made "I could not measure" read as "you are within budget".
        Write-Host "[CARD-BUDGET] no line-countable diff against '$scopeBaseRef' (empty, or binary-only) - nothing to judge. The measurement itself succeeded." -ForegroundColor DarkGray
      }
      else {
        $budgetDecision = Get-ScaffoldCardBudgetDecision `
          -BaseCardText $budgetBaseCard `
          -BranchCardText $budgetBranchCard `
          -ChangedLines $budgetLines `
          -DefaultBudget 0 `
          -TripFraction (Get-ScaffoldCardBudgetTripFraction)
        if ($budgetDecision.State -eq 'over') {
          Add-CatchRecord 'card-budget' "declared $($budgetDecision.Budget), diff $($budgetDecision.Used) lines against $scopeBaseRef"
          throw "[CARD-BUDGET-OVER] this card's diff is $($budgetDecision.Used) lines against a budget of $($budgetDecision.Budget) declared on the BASE card (read from $scopeBaseRef, so raising it in this branch is inert by design). Fix, either way round: SPLIT the remainder into a follow-up card, or RAISE the budget on '$shipBase' as its own commit with the reason in that commit, then rerun the same ship. What is not a fix is trimming the diff to fit - the number is a prompt to decide, not a ceiling to optimise against."
        }
        elseif ($budgetDecision.State -eq 'trip') {
          Write-Host "[CARD-BUDGET] $($budgetDecision.Message)" -ForegroundColor Yellow
        }
        elseif ($budgetDecision.State -eq 'no-budget') {
          # ONE state reaches here now: the base card exists and declares no `budget:`. The second arm this
          # used to carry - the card not being on the base ref at all - is unreachable since T241, because
          # the scope gate fail-closes on exactly that state. The guidance it carried was not dropped: it
          # MOVED to [SHIP-SCOPE-CARD-ABSENT], which can actually fire and whose repair is the same one.
          Write-Host "[CARD-BUDGET] the base card declares no 'budget:' - $budgetLines changed lines, reported only. Absence never blocks (T233/TD235)." -ForegroundColor DarkGray
        }
        else {
          Write-Host "card budget gate PASS（$($budgetDecision.Used)/$($budgetDecision.Budget) 行，取自 BASE 卡）" -ForegroundColor DarkGray
        }
      }
      $sagaDone += (Complete-ShipLeg 'budget-gate')

      Step '商用许可闸门（check-licenses；命中禁列即 block）'
      # 跑**工作树自带**的 check-licenses（其 $RepoRoot=本工作树 → 扫到本卡 uv add/npm install 新增的依赖），
      # 而非主仓副本（TD80/TD-262：卡的依赖清单/venv 只存在于工作树，扫主仓会漏判违禁许可依赖）。该闸逻辑
      # 由本卡分支自带是 verify/check-secrets 已接受的既有属性，由 allow_paths 范围闸 + R3 评审 + 基线检出的
      # CI 副本共同兜底。
      & pwsh -NoProfile -File (Join-Path $Wt 'scripts/check-licenses.ps1')
      if ($LASTEXITCODE -ne 0) { Add-CatchRecord 'license' 'check-licenses block'; throw '依赖许可不合规（见 docs/LICENSE-POLICY.md）。修复后重 ship。' }
      $sagaDone += (Complete-ShipLeg 'license-gate')

      Step '防泄露闸（check-secrets；提交后、推送/合并前拦截硬编码密钥与被追踪机密）'
      # 跑**工作树自带**的 check-secrets（其 $RepoRoot=本工作树 → 扫到本卡刚提交的改动），而非主仓副本。
      & pwsh -NoProfile -File (Join-Path $Wt 'scripts/check-secrets.ps1')
      if ($LASTEXITCODE -ne 0) { Add-CatchRecord 'secrets' 'check-secrets fatal'; throw '检出疑似机密（见上 check-secrets）。立即轮换密钥、改用环境变量/密钥管理并移除后重 ship（见 docs/SECURITY.md）。' }
      $sagaDone += (Complete-ShipLeg 'leak-gate')

      if ($Local) {
        # ── -Local：无 push/PR/gh 的本地完成路径；是否运行/阻断 R3 服从配置与卡策略，本仓 required 不降级。──
        Step 'R3 第二模型评审（-Local：ReviewGate=required 才是强制闸；卡自带 review_gate 则意见模式下也跑，不拦合并）'
        # Bind the exact candidate before reviewer code runs. The task branch name remains mutable and must
        # never be dereferenced again as the merge payload after this point.
        $r3Head = "$(& git -C $Wt rev-parse HEAD 2>$null)".Trim()
        if ($r3Head -cnotmatch '^[0-9a-f]{40}$') {
          Add-CatchRecord 'review' "R3 前本地 HEAD 不可判：'$r3Head' (-Local)"
          throw '[CI-GATE-LOCAL-HEAD] local candidate HEAD is invalid.'
        }
        # T288: ONE reviewer script for both sub-branches below, taken from the base commit the scope gate
        # pinned rather than from this worktree - which is the tree under review, and until this card was
        # exactly what the -Local leg ran. Resolved only when a review is actually going to run, so a ship
        # that skips R3 announces nothing; an empty result means the base carries no reviewer at all, which
        # routes through the paths a missing backend already has (skip here, refusal on the blocking leg).
        $rvLocal = if ($reviewAvail -and ($reviewRun.Blocking -or $reviewRun.Run)) { Resolve-ShipReviewerScript -BaseSha $scopeBaseSha } else { '' }
        # A BACKEND WITH NO SCRIPT TO RUN IS STILL A MANDATORY REVIEW UNMET, and the branch below would
        # otherwise fall into its no-backend warning and merge - a required gate satisfying itself in
        # silence. Refused here, in the same words the remote leg uses, so the state has one meaning.
        if ($reviewRun.Blocking -and $reviewAvail -and (-not $rvLocal)) { Add-CatchRecord 'review' "no scripts/review.ps1 on the base commit $scopeBaseSha (ReviewGate=required, -Local)"; throw "[SHIP-NO-REVIEWER] the base commit $scopeBaseSha carries no scripts/review.ps1 and this ship requires the second-model review (see the warning above) - stopped before the local merge. Restore the reviewer on '$Base', or leave ReviewGate empty for advisory mode, then rerun the same ship." }
        if ($reviewRun.Blocking) {
          if ($reviewAvail -and $rvLocal) {
            # -LocalBase：-Local 的合并目标是本地 <base>，评审基线也须对照本地（否则前次本地合并的文件被误判，TD68）。
            & pwsh -NoProfile -File $rvLocal -WorktreePath $Wt -Base $Base -LocalBase
            if ($LASTEXITCODE -ne 0) { Add-CatchRecord 'review' ((Get-ReviewBlockDetail $Wt $TaskId) + ' (-Local)'); throw '第二模型评审 block（-Local），已停止。修复后重 ship -Local。' }
          } else { throw '[SHIP-NO-REVIEWER] required review backend disappeared before the local merge.' }
        } elseif ($reviewRun.Run) {
          # T242/TD248：卡自身声明了 review_gate → 跑，但**不拦合并**（T68 的合并闸不变）。block 只报告 + 入账。
          if ($reviewAvail -and $rvLocal) {
            & pwsh -NoProfile -File $rvLocal -WorktreePath $Wt -Base $Base -LocalBase
            if ($LASTEXITCODE -ne 0) {
              # L21/L97: non-zero is not necessarily a block - quota exhaustion and the four [R3-*]
              # no-verdict states exit non-zero too. Report the state, never a guessed cause.
              $advLocal = (Get-ReviewBlockDetail $Wt $TaskId)
              # T277: the one exception to "advisory", taken BEFORE the local merge below.
              $specLocal = Get-ShipSpecAxisDecision $Wt $TaskId $baseCardText
              if ($specLocal.Block) {
                Add-CatchRecord 'review' "spec-axis block on a tier-S card, merge REFUSED (-Local): $advLocal"
                throw $specLocal.Message
              }
              Add-CatchRecord 'review' "advisory R3 exited non-zero, merge NOT blocked (card declares review_gate; -Local): $advLocal"
              Write-Warning "[R3-ADVISORY-NONZERO] the card declares review_gate and the advisory R3 exited non-zero: $advLocal - the merge is NOT blocked (T68: the merge bar is the deterministic gates). This is a verdict of block, an exhausted backend quota (L21), or one of the four [R3-*] no-verdict states - read .review/$TaskId.json to tell which. Recorded in _local/effectiveness-ledger.jsonl."
            }
          } else {
            Write-Warning 'The card declares review_gate but this ship has no reviewer to run - either no codex / ReviewCommand backend, or the base commit carries no scripts/review.ps1 (the [SHIP-NO-REVIEWER] warning above says which). Advisory mode does not block on it. Install codex, set ReviewCommand in _config, or restore the reviewer on the base branch.'
          }
        } else {
          Write-Host 'R3 意见模式（ReviewGate 留空，且本卡未声明 review_gate）：合并闸=确定性闸。需要第二意见随时：pwsh -File scripts/review.ps1 -WorktreePath <wt> -Base <base>' -ForegroundColor DarkGray
        }
        $r3HeadAfter = "$(& git -C $Wt rev-parse HEAD 2>$null)".Trim()
        if (($r3HeadAfter -cnotmatch '^[0-9a-f]{40}$') -or ($r3HeadAfter -cne $r3Head)) {
          Add-CatchRecord 'review' "R3 期间本地 HEAD 变化（$r3Head -> '$r3HeadAfter'; -Local）"
          throw "[CI-GATE-LOCAL-HEAD-MOVED] $r3Head -> '$r3HeadAfter'"
        }
        $sagaDone += (Complete-ShipLeg 'R3-review')   # 按 ReviewGate/card policy 完成：required 必须 pass，意见模式可显式跳过
        Step '本地合并（-Local：并入当前基线分支，无 push/PR/gh）'
        # F3（R3 PR#102 九轮 + 审计）：入口守卫读的是 ship 开始时的 HEAD；DoD/verify/R3 可跑 10+ 分钟，其间主检出可能被
        # 切分支 / detach（L88 记有 mid-flight HEAD 移动）。合并前**重新断言**同一不变量（throw 不 warn）——否则会对照 $Base
        # 评审、却并入变化后的分支或游离 HEAD（同 F1 的数据丢失尾：cleanup 会 branch -D 丢弃未真正合并的 work）。
        $curBranch = (& git -C $RepoRoot symbolic-ref --quiet --short HEAD 2>$null)
        if ($curBranch) { $curBranch = $curBranch.Trim() }
        Assert-LocalMergeTarget -Cur $curBranch -Base $Base -TaskId $TaskId
        $localBaseNow = "$(& git -C $RepoRoot rev-parse HEAD 2>$null)".Trim()
        if (($localBaseNow -cnotmatch '^[0-9a-f]{40}$') -or ($localBaseNow -cne $scopeBaseSha)) {
          Add-CatchRecord 'base' "local base:$scopeBaseSha->$localBaseNow"
          throw "[SHIP-LOCAL-BASE-MOVED] reviewed scope base $scopeBaseSha -> '$localBaseNow'; rerun ship against the current local base."
        }
        & git -C $RepoRoot merge --no-ff --no-edit $r3Head
        if ($LASTEXITCODE -ne 0) { throw "[SHIP-LOCAL-MERGE-FAIL] local merge of $TaskId failed (conflict?). Resolve in the main worktree, then retry." }
        $sagaLocalMerged = $true   # 合并已成功——此后失败（凭据铸造）不得误报为合并前守卫态（R3 r5 #9）
        # T24-MERGETOKEN 铸造（-Local 合并成功事件）：cleanup 的 branch -D 只认这枚单次凭据（或 -Force / gh 在线补验）。
        # 铸造 fail-closed：失败即 throw（合并本身已成功，报错只指示凭据未铸——cleanup 会 fail-safe 保留分支）。
        $tokDir = "$(& git -C $RepoRoot rev-parse --git-common-dir 2>$null)".Trim()
        if (-not $tokDir) { throw "T24-MERGETOKEN：本地合并已成功但无法解析 git-common-dir，合并凭据未铸造——cleanup 将保留分支（或用 -Force）。排查 git 环境。" }
        if (-not [System.IO.Path]::IsPathRooted($tokDir)) { $tokDir = Join-Path $RepoRoot $tokDir }
        $tokDir = Join-Path $tokDir 'scaffold-merged'
        New-Item -ItemType Directory -Force $tokDir -ErrorAction Stop | Out-Null
        # R3 r3 #17：tip 取合并提交第二亲（HEAD^2 = 恰被并入的分支 tip）——原子锚定「已合并的那个状态」，免「合并后
        # 并发 ref 前移再 rev-parse 分支名」把未合并的新状态铸进凭据。--no-ff 保证合并提交恒有第二亲。
        $mintTip = "$(& git -C $RepoRoot rev-parse HEAD^2 2>$null)".Trim()
        if (-not $mintTip) { throw "T24-MERGETOKEN：本地合并已成功但无法解析合并提交第二亲（HEAD^2），合并凭据未铸造——cleanup 将保留分支（或用 -Force）。" }
        "tip=$mintTip`nmerged=$("$(& git -C $RepoRoot rev-parse HEAD 2>$null)".Trim())`nutc=$((Get-Date).ToUniversalTime().ToString('o'))" | Set-Content (Join-Path $tokDir $TaskId) -Encoding utf8
        $sagaDone += (Complete-ShipLeg 'local-merge')
        Write-Host "已本地合并 $TaskId（无远端 / 无 PR；已铸 T24-MERGETOKEN 合并凭据）。下一步：scripts\task.ps1 -TaskId $TaskId -Phase cleanup 拆 worktree。" -ForegroundColor Green
        return
      }

      Step 'push + open PR (the R3 review, when required, runs once after the PR exists and posts status back)'
      & git push -u origin $TaskId
      # TD44（载重护栏）：push 静默失败（网络/凭证/非 fast-forward 拒绝）时若续跑，下游 `gh pr merge` 会合并 origin/$TaskId
      # 当前指向的【陈旧】head——与本地刚过闸的产物解耦、把未评审内容并入基线，且 R3 把绿状态回贴到 head 已陈旧的 PR（状态误导）。
      # 故 push 后立即校验退出码：非零即在开 PR / 合并之前 throw，恢复「过闸的产物 === 被合并的产物」不变量。
      if ($LASTEXITCODE -ne 0) { throw "[SHIP-PUSH-FAIL] git push failed (exit $LASTEXITCODE) - remote not updated; aborted to avoid merging a stale remote head (TD44). Check network/credentials; on a non-fast-forward rejection, git fetch origin first, then inside the worktree git merge origin/$TaskId (merge, never rebase; git pull --no-rebase also works), then re-ship; after the watershed, rebase/history rewrite is forbidden." }
      # PR 标题 = Conventional Commits + 卡 id，由共享纯函数 Get-PrTitle 组合（scripts/_cards.ps1；
      # 形态与推导规则见 docs/DEVOPS-WORKFLOW.md §3.1）。allow_paths 经共享判定核取值，绝不在此自行解析（闸 10d）。
      # Also the MAIN CHECKOUT's copy, and unlike the scope and budget gates this is not a judgement -
      # allow_paths only shapes the title's scope prefix (T241/TD247). Reading the base copy would be
      # equally defensible here; what would not be is leaving the difference unstated, which is how the
      # gate above came to be described as base-bound for three cards while it was not.
      $cardText = ''; $cardAllow = @()
      if (Test-Path $Card) { $cardText = Get-Content $Card -Raw; $cardAllow = @(Get-ScaffoldCardAllowPath -CardPath $Card) }
      $title = Get-PrTitle -TaskId $TaskId -CardText $cardText -AllowPaths $cardAllow
      $exists = (& gh pr view $TaskId --json number -q .number 2>$null)
      if (-not $exists) {
        & gh pr create --base $shipBase --head $TaskId --title $title `
            --body "闭环：worktree+TDD+Codex 评审。DoD 见 specs/tasks/$TaskId.md。Codex 裁决已回贴。" 2>&1 | Write-Host
      }
      $prRaw = (& gh pr view $TaskId --json number -q .number 2>$null)
      $pr = 0
      if (-not [int]::TryParse("$prRaw".Trim(), [ref]$pr) -or $pr -le 0) {
        throw "[SHIP-PR-NUMBER-FAIL] could not get the PR number (gh pr view returned '$prRaw'). Check push/gh auth, then re-ship."
      }
      # Codex#1（TD84）：复用/新建的 PR 其**实际 base 分支**须 == 本次 $shipBase——否则 review/范围对照它、而 `gh pr merge`
      # 却并入 PR 真实 base（已存在或被 retarget 的 PR 即此情形），评审从未审对那个基线的 diff（错合并目标 fail-open）。
      Assert-RemotePrBase -Pr $pr -ExpectedBase $shipBase
      $sagaDone += (Complete-ShipLeg 'push+PR')

      Step 'R3 第二模型评审（ReviewGate=required 才是强制闸；卡自带 review_gate 则意见模式下也跑，不拦合并——合并闸=确定性闸）'
      # This OID is the candidate the reviewer is about to judge. Every later PR/CI/merge binding is
      # compared with this value; a branch name is never accepted as proof of reviewed identity.
      $r3Head = "$(& git -C $Wt rev-parse HEAD 2>$null)".Trim()
      if ($r3Head -cnotmatch '^[0-9a-f]{40}$') {
        Add-CatchRecord 'review' "R3 前本地 HEAD 不可判：'$r3Head'"
        throw '[CI-GATE-LOCAL-HEAD] candidate HEAD is invalid.'
      }
      # T288: the same base-commit reviewer the -Local leg above resolves. This leg already ran a copy the
      # branch could not edit (the main checkout's), so what changes here is WHICH untouched copy: the one
      # the scope gate pinned, so the reviewer, the allow_paths, the tier and the budget all come from one
      # commit instead of from two trees that happen to agree.
      $rvRemote = if ($reviewAvail -and ($reviewRun.Blocking -or $reviewRun.Run)) { Resolve-ShipReviewerScript -BaseSha $scopeBaseSha } else { '' }
      if ($reviewRun.Blocking) {
        # The one branch that does not consult $reviewAvail (the pre-flight already fail-fasted on a missing
        # backend before the push), so the missing-reviewer state is refused here in its own words.
        if (-not $rvRemote) { Add-CatchRecord 'review' "no scripts/review.ps1 on the base commit $scopeBaseSha (ReviewGate=required)"; throw "[SHIP-NO-REVIEWER] the base commit $scopeBaseSha carries no scripts/review.ps1 and this ship requires the second-model review (see the warning above) - stopped before the merge. Restore the reviewer on '$shipBase', or leave ReviewGate empty for advisory mode, then rerun the same ship." }
        & pwsh -NoProfile -File $rvRemote -WorktreePath $Wt -Base $shipBase -PostStatus -PrNumber $pr
        if ($LASTEXITCODE -ne 0) { Add-CatchRecord 'review' (Get-ReviewBlockDetail $Wt $TaskId); throw 'Codex 裁决 block，已停止。修复后重 ship（PR 已开，重 ship 会更新）。' }
      } elseif ($reviewRun.Run) {
        # T242/TD248：卡自身声明了 review_gate → 跑在 merge 之前，但**不拦合并**（T68 的合并闸不变）。
        # **刻意不传 -PostStatus**：block 时它会回贴一枚 failing commit status，而下面的 CI 检查闸会
        # 「任一其它检查失败即不合并」——那等于从后门把意见变回合并闸，正是本卡 forbid 的那条。裁决仍
        # .review 与效果账本是本地诊断；-PostStatus 的 exact-sha 状态及 PR 评论承载持久 PR 证据。
        if ($reviewAvail -and $rvRemote) {
          & pwsh -NoProfile -File $rvRemote -WorktreePath $Wt -Base $shipBase
          if ($LASTEXITCODE -ne 0) {
            # L21/L97: a non-zero exit is NOT necessarily a block - an exhausted quota and the four
            # [R3-*] no-verdict states land here too. Never name a cause the exit code does not carry;
            # report the state and point at the verdict file, which is where the difference is legible.
            $advRemote = (Get-ReviewBlockDetail $Wt $TaskId)
            # T277: the one exception to "advisory", taken BEFORE the CI gate and the merge below. The
            # branch and its PR are already pushed and stay open, unmerged, which is what makes the fix
            # cheap: repair the diff in place and rerun the same ship.
            $specRemote = Get-ShipSpecAxisDecision $Wt $TaskId $baseCardText
            if ($specRemote.Block) {
              Add-CatchRecord 'review' "spec-axis block on a tier-S card, merge REFUSED: $advRemote"
              throw $specRemote.Message
            }
            Add-CatchRecord 'review' "advisory R3 exited non-zero, merge NOT blocked (card declares review_gate): $advRemote"
            Write-Warning "[R3-ADVISORY-NONZERO] the card declares review_gate and the advisory R3 exited non-zero: $advRemote - the merge is NOT blocked (T68: the merge bar is the deterministic gates). This is a verdict of block, an exhausted backend quota (L21), or one of the four [R3-*] no-verdict states - read .review/$TaskId.json to tell which. Recorded in _local/effectiveness-ledger.jsonl. If it is a real finding, fix it in THIS branch while the PR is still open."
          }
        } else {
          Write-Host 'The card declares review_gate but this ship has no reviewer to run - either no codex / ReviewCommand backend, or the base commit carries no scripts/review.ps1 (the [SHIP-NO-REVIEWER] warning above says which). Advisory mode does not block on it. Install codex, set ReviewCommand in _config, or restore the reviewer on the base branch.' -ForegroundColor DarkGray
        }
      } else {
        Write-Host "R3 意见模式（ReviewGate 留空，且本卡未声明 review_gate）：跳过评审。需要第二意见随时：pwsh -File scripts/review.ps1 -WorktreePath `"$Wt`" -Base $shipBase -PostStatus -PrNumber $pr（只输出意见与回贴，不拦合并）" -ForegroundColor DarkGray
      }
      $r3HeadAfter = "$(& git -C $Wt rev-parse HEAD 2>$null)".Trim()
      if (($r3HeadAfter -cnotmatch '^[0-9a-f]{40}$') -or ($r3HeadAfter -cne $r3Head)) {
        Add-CatchRecord 'review' "R3 期间本地 HEAD 变化（$r3Head -> '$r3HeadAfter'）"
        throw "[CI-GATE-LOCAL-HEAD-MOVED] $r3Head -> '$r3HeadAfter'"
      }
      $sagaDone += (Complete-ShipLeg 'R3-review')

      # One immutable CI chain serves both auto-merge and manual-ready: reviewed local OID -> PR OID ->
      # exact ci.yml run/attempt -> its jobs -> final server snapshots -> the still-identical scoped base.
      Step '[CI-GATE] exact reviewed head + ci.yml workflow/run-attempt + required fan-in + final base/head snapshots'
      $ciWf = Join-Path $Wt '.github/workflows/ci.yml'
      if (-not (Test-Path -LiteralPath $ciWf -PathType Leaf)) {
        throw "[CI-GATE-WF-MISSING] no .github/workflows/ci.yml in the merge candidate tree ($Wt); fail-closed."
      }
      $ciWfText = Get-Content -LiteralPath $ciWf -Raw
      $ciFanIn = @(Test-ScaffoldCiFanIn -WorkflowText $ciWfText)
      if ($ciFanIn.Count -gt 0) {
        throw "[CI-GATE-JOBS-DRIFT] candidate ci.yml does not satisfy the required fan-in contract: $($ciFanIn -join ' | ')"
      }
      $ciExpected = @($ScaffoldCiFanInJob)

      # Check-runs trusts only the single `required` fan-in context. The exact selected workflow attempt's
      # jobs additionally prove that the candidate ci.yml job set ran, without reviving the removed
      # scaffold-selftest matrix derivation.
      $ciDeclared = @(); $ciDeclErrors = @(); $ciInJobs = $false
      foreach ($ln in @($ciWfText -split '\r?\n')) {
        if (-not $ciInJobs) {
          if ($ln -cmatch '^jobs:\s*(?:#.*)?$') { $ciInJobs = $true }
          continue
        }
        if ($ln -cmatch '^\S') { break }
        if ($ln -cmatch '^  \S') {
          if ($ln -cmatch '^  (?<job>[A-Za-z0-9_-]+):\s*(?:#.*)?$') { $ciDeclared += $Matches['job'] }
          elseif ($ln -cnotmatch '^  #') { $ciDeclErrors += $ln.Trim() }
        }
        elseif ($ln -cmatch '^    name:\s*(?<job>[\w ./()_-]+?)\s*(?:#.*)?$') {
          if ($ciDeclared.Count -eq 0) { $ciDeclErrors += $ln.Trim() } else { $ciDeclared[-1] = $Matches['job'] }
        }
        elseif ($ln -cmatch '^    (?:name|strategy|uses):|^      matrix:') { $ciDeclErrors += $ln.Trim() }
      }
      $ciJobExpected = @($ciDeclared | Sort-Object -Unique -CaseSensitive)
      if ((-not $ciInJobs) -or $ciDeclared.Count -eq 0 -or $ciJobExpected.Count -ne $ciDeclared.Count -or $ciDeclErrors.Count -gt 0) {
        Add-CatchRecord 'ci' "jobs declaration drift:$($ciDeclared.Count)/$($ciJobExpected.Count):$($ciDeclErrors -join ',')"
        throw '[CI-GATE-JOBS-DRIFT] candidate ci.yml jobs cannot be bound to an exact run-attempt collection.'
      }

      $ciTimeoutSec = 1800
      if ($env:SCAFFOLD_CI_TIMEOUT_SEC) {
        $ciTimeoutParsed = 0
        if ((-not [int]::TryParse($env:SCAFFOLD_CI_TIMEOUT_SEC, [ref]$ciTimeoutParsed)) -or $ciTimeoutParsed -le 0) {
          throw "[CI-GATE-TIMEOUT-CONFIG] '$($env:SCAFFOLD_CI_TIMEOUT_SEC)' is not a positive integer."
        }
        $ciTimeoutSec = $ciTimeoutParsed
      }
      Initialize-CiContainment -Fault "$env:SCAFFOLD_CI_CONTAINMENT_FAULT"
      $ciDeadline = [DateTimeOffset]::UtcNow.AddSeconds($ciTimeoutSec)

      # This is the first PR head read after R3. It must already equal the reviewed local OID; checking only
      # later would let an unreviewed replacement tip establish the entire CI chain.
      $firstPr = Invoke-GhBeforeDeadline -Arguments @('pr','view',"$pr",'--json','baseRefName,headRefOid') -Deadline $ciDeadline -WorkingDirectory $Wt
      if ($firstPr.TimedOut) { throw "[CI-GATE-TIMEOUT] PR #$pr initial base/head snapshot exceeded ${ciTimeoutSec}s." }
      $firstBase = ''; $ciHead = ''
      if ($firstPr.ExitCode -eq 0 -and $firstPr.Stdout) {
        try {
          $firstPrObject = $firstPr.Stdout | ConvertFrom-Json -ErrorAction Stop
          $firstBase = "$($firstPrObject.baseRefName)".Trim()
          $ciHead = "$($firstPrObject.headRefOid)".Trim()
        } catch { }
      }
      if ($firstBase -cne $shipBase) {
        Add-CatchRecord 'base' "$shipBase!=$firstBase/$($firstPr.ExitCode)"
        throw "[CI-GATE-BASE-MISMATCH] '$firstBase' != '$shipBase' (exit $($firstPr.ExitCode))."
      }
      if ($ciHead -cnotmatch '^[0-9a-f]{40}$') { throw "[CI-GATE-NOHEAD] invalid PR headRefOid '$ciHead'." }
      if ($ciHead -cne $r3Head) {
        Add-CatchRecord 'ci' "$r3Head!=$ciHead"
        throw "[CI-GATE-HEAD-MISMATCH] PR head $ciHead != reviewed head $r3Head."
      }

      $readCheckState = {
        param($checks)
        $runs = @($checks.Runs); $badShape = @(); $pending = @(); $blocking = @($checks.Blocking)
        foreach ($cr in $runs) {
          if ($cr -isnot [pscustomobject]) { $badShape += 'item-not-object'; continue }
          $props = @($cr.PSObject.Properties.Name)
          if (@(@('name','status','conclusion') | Where-Object { $props -cnotcontains $_ }).Count -gt 0 -or
              $cr.name -isnot [string] -or $cr.status -isnot [string] -or
              (($null -ne $cr.conclusion) -and $cr.conclusion -isnot [string]) -or [string]::IsNullOrWhiteSpace($cr.name)) {
            $badShape += 'item-shape'; continue
          }
          if ($cr.status -cne 'completed') { $pending += $cr }
        }
        $required = @($runs | Where-Object { $_ -is [pscustomobject] -and $_.name -is [string] -and $_.name -ceq $ScaffoldCiFanInJob })
        if ($required.Count -eq 0) {
          # A check run may not exist yet while GitHub is creating it. Treat absence as pending until the
          # shared deadline; duplicate required contexts remain an ambiguous acceptance surface.
          $pending += [pscustomobject]@{ name=$ScaffoldCiFanInJob; status='missing'; conclusion=$null }
        }
        elseif ($required.Count -gt 1) { $badShape += "required-count=$($required.Count)" }
        elseif ($required[0].status -ceq 'completed' -and $required[0].conclusion -cne 'success') { $blocking += $required[0] }
        [pscustomobject]@{ Drift=($badShape.Count -gt 0); Reason=($badShape -join ','); Pending=@($pending); Blocking=@($blocking) }
      }
      $last = 'CI has not returned a stable candidate snapshot'
      :ciStable while ($true) {
        if ([DateTimeOffset]::UtcNow -ge $ciDeadline) { throw "[CI-GATE-TIMEOUT] ${ciTimeoutSec}s: $last" }
        $checks = Get-ExactHeadChecksBeforeDeadline -Head $ciHead -Deadline $ciDeadline -WorkingDirectory $Wt
        if ($checks.TimedOut) { throw "[CI-GATE-TIMEOUT] checks: $($checks.Reason)" }
        if (-not $checks.Readable) { throw "[CI-GATE-API] checks: $($checks.Reason)" }
        $checkState = & $readCheckState $checks
        if ($checkState.Drift) { throw "[CI-GATE-API] check-runs shape: $($checkState.Reason)" }
        if ($checkState.Blocking.Count -gt 0) {
          $bad = @($checkState.Blocking | ForEach-Object { "$($_.name)=$($_.status)/$($_.conclusion)" }) -join ', '
          throw "[CI-GATE-RED] checks: $bad"
        }
        if ($checkState.Pending.Count -gt 0) { $last = 'check-runs pending'; Wait-CiRetryBeforeDeadline $ciDeadline; continue }

        $wfPg = Get-GhPagedCollectionBeforeDeadline -EndpointTemplate "repos/{owner}/{repo}/actions/workflows/ci.yml/runs?event=pull_request&head_sha=$ciHead&per_page=100&page={page}" -CollectionProperty 'workflow_runs' -Deadline $ciDeadline -WorkingDirectory $Wt
        if ($wfPg.TimedOut) { throw "[CI-GATE-TIMEOUT] workflow: $($wfPg.Reason)" }
        if (-not $wfPg.Readable) { throw "[CI-GATE-API] workflow: $($wfPg.Reason)" }
        $wfRuns = @($wfPg.Items)
        if ($wfRuns.Count -eq 0) { $last = 'no pull_request ci.yml run for the reviewed head'; Wait-CiRetryBeforeDeadline $ciDeadline; continue }
        if ($wfRuns.Count -ne 1) { throw "[CI-GATE-WORKFLOW-AMBIGUOUS] runs=$($wfRuns.Count)." }
        $run = $wfRuns[0]; $runProps = @($run.PSObject.Properties.Name)
        $missing = @(@('id','head_sha','event','status','conclusion','run_attempt','path','pull_requests') | Where-Object { $runProps -cnotcontains $_ })
        if ($missing.Count -gt 0) { throw "[CI-GATE-WORKFLOW-IDENTITY] missing: $($missing -join ',')." }
        $runId = 0L; $attempt = 0; $runPath = "$($run.path)"; $prMatch = Get-CandidateRunPrMatchCount -PullRequests $run.pull_requests -Pr $pr
        if ((-not [long]::TryParse("$($run.id)",[ref]$runId)) -or $runId -le 0 -or
            (-not [int]::TryParse("$($run.run_attempt)",[ref]$attempt)) -or $attempt -le 0 -or
            "$($run.head_sha)" -cne $ciHead -or "$($run.event)" -cne 'pull_request' -or
            $runPath -cnotmatch '^\.github/workflows/ci\.yml(?:@.*)?$' -or $prMatch -ne 1) {
          Add-CatchRecord 'ci' "wf=$($run.id)/$($run.run_attempt)/$($run.head_sha)/$($run.event)/$runPath/prs=$prMatch"
          throw "[CI-GATE-WORKFLOW-IDENTITY] PR #$pr workflow identity mismatch."
        }
        if ("$($run.status)" -ieq 'completed' -and "$($run.conclusion)" -ine 'success') { throw "[CI-GATE-RED] workflow $runId=$($run.status)/$($run.conclusion)." }
        if ("$($run.status)" -ine 'completed' -or "$($run.conclusion)" -ine 'success') { $last = "workflow $runId pending"; Wait-CiRetryBeforeDeadline $ciDeadline; continue }

        $jobPg = Get-GhPagedCollectionBeforeDeadline -EndpointTemplate "repos/{owner}/{repo}/actions/runs/$runId/attempts/$attempt/jobs?per_page=100&page={page}" -CollectionProperty 'jobs' -Deadline $ciDeadline -WorkingDirectory $Wt
        if ($jobPg.TimedOut) { throw "[CI-GATE-TIMEOUT] jobs: $($jobPg.Reason)" }
        if (-not $jobPg.Readable) { throw "[CI-GATE-API] jobs: $($jobPg.Reason)" }
        $jobState = Get-ExactCandidateJobState -Jobs @($jobPg.Items) -Wanted $ciJobExpected
        if ($jobState.Drift) { throw "[CI-GATE-JOBS-DRIFT] $($jobState.Reason)" }
        if ($jobState.Blocking.Count -gt 0) { throw "[CI-GATE-RED] jobs: $(@($jobState.Blocking | ForEach-Object { "$($_.name)=$($_.status)/$($_.conclusion)" }) -join ', ')" }
        if ($jobState.Pending.Count -gt 0) { $last = 'workflow jobs pending'; Wait-CiRetryBeforeDeadline $ciDeadline; continue }

        # Re-enumerate every decision-bearing collection so a rerun/replacement attempt cannot inherit an
        # earlier run's result merely because the check context name is unchanged.
        $finalChecks = Get-ExactHeadChecksBeforeDeadline -Head $ciHead -Deadline $ciDeadline -WorkingDirectory $Wt
        if ($finalChecks.TimedOut) { throw "[CI-GATE-TIMEOUT] final checks: $($finalChecks.Reason)" }
        if (-not $finalChecks.Readable) { throw "[CI-GATE-API] final checks: $($finalChecks.Reason)" }
        $finalCheckState = & $readCheckState $finalChecks
        if ($finalCheckState.Drift) { throw "[CI-GATE-API] final check-runs shape: $($finalCheckState.Reason)" }
        if ($finalCheckState.Blocking.Count -gt 0) { throw '[CI-GATE-RED] final checks changed to a blocking state.' }
        if ($finalCheckState.Pending.Count -gt 0) { $last = 'final checks pending'; Wait-CiRetryBeforeDeadline $ciDeadline; continue ciStable }

        $fwPg = Get-GhPagedCollectionBeforeDeadline -EndpointTemplate "repos/{owner}/{repo}/actions/workflows/ci.yml/runs?event=pull_request&head_sha=$ciHead&per_page=100&page={page}" -CollectionProperty 'workflow_runs' -Deadline $ciDeadline -WorkingDirectory $Wt
        if ($fwPg.TimedOut) { throw "[CI-GATE-TIMEOUT] final workflow: $($fwPg.Reason)" }
        if (-not $fwPg.Readable) { throw "[CI-GATE-API] final workflow: $($fwPg.Reason)" }
        $fwRuns = @($fwPg.Items)
        if ($fwRuns.Count -ne 1) { throw "[CI-GATE-WORKFLOW-AMBIGUOUS] final runs=$($fwRuns.Count)." }
        $fwRun = $fwRuns[0]; $fwProps = @($fwRun.PSObject.Properties.Name); $fwId = 0L; $fwTry = 0
        $fwMissing = @(@('id','head_sha','event','status','conclusion','run_attempt','path','pull_requests') | Where-Object { $fwProps -cnotcontains $_ })
        if ($fwMissing.Count -gt 0) {
          throw "[CI-GATE-WORKFLOW-IDENTITY] final workflow missing: $($fwMissing -join ',')."
        }
        $fwPrMatch = Get-CandidateRunPrMatchCount -PullRequests $fwRun.pull_requests -Pr $pr
        if (
            (-not [long]::TryParse("$($fwRun.id)",[ref]$fwId)) -or $fwId -ne $runId -or
            (-not [int]::TryParse("$($fwRun.run_attempt)",[ref]$fwTry)) -or $fwTry -ne $attempt -or
            "$($fwRun.head_sha)" -cne $ciHead -or "$($fwRun.event)" -cne 'pull_request' -or
            "$($fwRun.path)" -cnotmatch '^\.github/workflows/ci\.yml(?:@.*)?$' -or $fwPrMatch -ne 1) {
          throw "[CI-GATE-WORKFLOW-IDENTITY] final workflow identity drifted (id=$($fwRun.id), expected=$runId)."
        }
        if ("$($fwRun.status)" -ieq 'completed' -and "$($fwRun.conclusion)" -ine 'success') { throw "[CI-GATE-RED] final workflow $runId=$($fwRun.status)/$($fwRun.conclusion)." }
        if ("$($fwRun.status)" -ine 'completed' -or "$($fwRun.conclusion)" -ine 'success') { $last = 'final workflow pending'; Wait-CiRetryBeforeDeadline $ciDeadline; continue ciStable }

        $fjPg = Get-GhPagedCollectionBeforeDeadline -EndpointTemplate "repos/{owner}/{repo}/actions/runs/$runId/attempts/$attempt/jobs?per_page=100&page={page}" -CollectionProperty 'jobs' -Deadline $ciDeadline -WorkingDirectory $Wt
        if ($fjPg.TimedOut) { throw "[CI-GATE-TIMEOUT] final jobs: $($fjPg.Reason)" }
        if (-not $fjPg.Readable) { throw "[CI-GATE-API] final jobs: $($fjPg.Reason)" }
        $fjState = Get-ExactCandidateJobState -Jobs @($fjPg.Items) -Wanted $ciJobExpected
        if ($fjState.Drift) { throw "[CI-GATE-JOBS-DRIFT] final $($fjState.Reason)" }
        if ($fjState.Blocking.Count -gt 0) { throw '[CI-GATE-RED] final jobs changed to a blocking state.' }
        if ($fjState.Pending.Count -gt 0) { $last = 'final jobs pending'; Wait-CiRetryBeforeDeadline $ciDeadline; continue ciStable }

        $finalPr = Invoke-GhBeforeDeadline -Arguments @('pr','view',"$pr",'--json','baseRefName,headRefOid') -Deadline $ciDeadline -WorkingDirectory $Wt
        if ($finalPr.TimedOut) { throw "[CI-GATE-TIMEOUT] PR #$pr final base/head snapshot exceeded ${ciTimeoutSec}s." }
        $finalBase = ''; $finalHead = ''
        if ($finalPr.ExitCode -eq 0 -and $finalPr.Stdout) {
          try {
            $finalPrObject = $finalPr.Stdout | ConvertFrom-Json -ErrorAction Stop
            $finalBase = "$($finalPrObject.baseRefName)".Trim()
            $finalHead = "$($finalPrObject.headRefOid)".Trim()
          } catch { }
        }
        if ($finalBase -cne $shipBase) {
          Add-CatchRecord 'base' "$shipBase!=$finalBase/$($finalPr.ExitCode)"
          throw "[CI-GATE-BASE-MISMATCH] '$finalBase' != '$shipBase' (exit $($finalPr.ExitCode))."
        }
        if ($finalHead -cnotmatch '^[0-9a-f]{40}$' -or $finalHead -cne $ciHead) {
          Add-CatchRecord 'ci' "head:$ciHead->$finalHead/$($finalPr.ExitCode)"
          throw "[CI-GATE-HEAD-MOVED] $ciHead -> '$finalHead'."
        }

        # Refresh the remote base immediately before readiness/merge, under the same absolute deadline, and
        # compare its commit identity with the scope/reviewer base pinned before all gates.
        $baseFetch = Invoke-ExternalBeforeDeadline -Command 'git' -Arguments @('-C',$Wt,'fetch','--quiet','--no-tags','origin',"+refs/heads/${remoteBaseName}:refs/remotes/origin/${remoteBaseName}") -Deadline $ciDeadline -WorkingDirectory $Wt
        if ($baseFetch.TimedOut) { throw "[CI-GATE-TIMEOUT] git fetch origin/$remoteBaseName." }
        if ($baseFetch.ExitCode -ne 0) { throw "[CI-GATE-BASE-REFRESH] origin/$remoteBaseName." }
        $baseNow = Get-GitOidBeforeDeadline -Ref "refs/remotes/origin/$remoteBaseName" -Deadline $ciDeadline -WorkingDirectory $Wt
        if ($baseNow -cnotmatch '^[0-9a-f]{40}$' -or $baseNow -cne $scopeBaseSha) {
          Add-CatchRecord 'ci' "base:$scopeBaseSha->$baseNow"
          throw "[CI-GATE-BASE-MOVED] $scopeBaseSha -> '$baseNow'."
        }
        break ciStable
      }
      Write-Host "  [CI-GATE-PASS] PR #$pr head=$ciHead workflow=$runId/$attempt required='$ScaffoldCiFanInJob' base=$scopeBaseSha" -ForegroundColor Green
      $sagaDone += (Complete-ShipLeg 'CI-gate')

      if (-not $NoAutoMerge) {
        # 本地闸门（DoD + verify + 范围闸 + 许可闸 + 防泄露闸 + Codex 评审）+ 上方 CI 检查闸均已过（任一失败即 throw、不到此处），故直接 squash 合并。
        Step '本地闸门 + CI 检查闸已过（DoD+verify+范围+许可+密钥+Codex+CI）→ 直接 squash 合并'
        # 不加 --delete-branch：在 worktree 内它会尝试 checkout base(main) 以删本地分支，
        # 而 main 被主工作树占用 → fatal "'main' is already used by worktree"（合并其实已成功）。
        # 远端分支由仓库 delete_branch_on_merge=true 自动删；本地分支由 cleanup 阶段删。
        $mergeRun = Invoke-GhBeforeDeadline -Arguments @('pr','merge',"$pr",'--squash','--match-head-commit',$ciHead) -Deadline $ciDeadline -WorkingDirectory $Wt
        if ($mergeRun.TimedOut) { throw "[CI-GATE-TIMEOUT] PR #$pr merge exceeded the shared ${ciTimeoutSec}s CI deadline." }
        if ($mergeRun.ExitCode -ne 0) { throw "[SHIP-MERGE-FAIL] PR #$pr squash merge failed (exit $($mergeRun.ExitCode)). Check gh permissions/merge conflicts and retry (--match-head-commit mismatch = head moved again; rerun ship)." }
        # T24-MERGETOKEN 铸造（PR squash 合并成功事件）：内容记 PR 号 + 分支 tip（仅溯源），在位即凭据。
        $tokDir = "$(& git -C $RepoRoot rev-parse --git-common-dir 2>$null)".Trim()
        if (-not $tokDir) { throw "T24-MERGETOKEN：PR #$pr 已合并但无法解析 git-common-dir，合并凭据未铸造——cleanup 将走 gh 在线补验（或 -Force）。排查 git 环境。" }
        if (-not [System.IO.Path]::IsPathRooted($tokDir)) { $tokDir = Join-Path $RepoRoot $tokDir }
        $tokDir = Join-Path $tokDir 'scaffold-merged'
        New-Item -ItemType Directory -Force $tokDir -ErrorAction Stop | Out-Null
        # R3 r3 #17：tip 取已合并 PR 的 headRefOid（权威=恰被 squash 进 base 的 head）——免并发本地 ref 前移铸错凭据。
        # R3 r5 #17：`gh pr merge` exit 0 ≠ 已合并（auto-merge 生效/merge queue 场景只是入队）——铸造前查 state，
        # 仅 MERGED 才铸；否则不留凭据、fail-closed（PR 真合并后 cleanup 走 gh 在线补验，或确认后 -Force）。
        # 注意 --json 字段表必须引号包裹：裸 state,headRefOid 会被 PowerShell 当数组拆成两个参数，真 gh 直接报错
        $mintJson = "$(& gh pr view $pr --json 'state,headRefOid' 2>$null)"
        $mintState = ''; $mintTip = ''
        if ($mintJson) { try { $mintO = $mintJson | ConvertFrom-Json; $mintState = "$($mintO.state)"; $mintTip = "$($mintO.headRefOid)".Trim() } catch { $mintState = ''; $mintTip = '' } }
        if (($mintState -ine 'MERGED') -or (-not $mintTip)) { throw "T24-MERGETOKEN：gh pr merge 已返回但 PR #$pr 状态非 MERGED（state='$mintState'，可能 auto-merge/merge queue 仅入队）或无 headRefOid——不铸造合并凭据（fail-closed）。PR 真正合并后 cleanup 可走 gh 在线补验；或人工确认后 -Phase cleanup -Force。" }
        "tip=$mintTip`nmerged_pr=#$pr`nutc=$((Get-Date).ToUniversalTime().ToString('o'))" | Set-Content (Join-Path $tokDir $TaskId) -Encoding utf8
        $sagaDone += (Complete-ShipLeg 'merge')
        Write-Host "PR #$pr 已 squash 合并（远端分支由仓库设置自动删；已铸 T24-MERGETOKEN 合并凭据）。合并后跑：scripts\task.ps1 -TaskId $TaskId -Phase cleanup，并执行 R5 文档同步。" -ForegroundColor Green
      } else {
        # -NoAutoMerge is a pause, not a weaker manual verifier. A later delivery must enter this same ship
        # path again so R3, exact workflow/run-attempt/PR/jobs identity, final base/head/OID snapshots and the
        # merge all belong to one fresh invocation. Preserve every caller-bound ship option except the pause.
        $manualTaskArg = "'" + $TaskId.Replace("'", "''") + "'"
        $manualBaseArg = "'" + $shipBase.Replace("'", "''") + "'"
        $manualResumeCmd = "pwsh -NoProfile -File scripts\task.ps1 -TaskId $manualTaskArg -Phase ship -Base $manualBaseArg"
        if ($SkipRed) { $manualResumeCmd += ' -SkipRed' }
        Write-Host "PR #$pr paused before merge after CI verification of head $($ciHead.Substring(0,8)). Later delivery must rerun the protected ship path; its fresh checks replace this snapshot." -ForegroundColor Green
        Write-Host "[SHIP-MANUAL-RESUME] $manualResumeCmd" -ForegroundColor Yellow
      }
    } finally {
      # T288: the base reviewer copy is this RUN's, and it goes on every exit path - a clean ship, a thrown
      # gate, the -Local return above. Removed BEFORE Pop-Location and inside its own try, so neither a
      # failing removal nor a failing Pop-Location can leave the other undone; a stale copy left behind
      # would be a reviewer nobody pinned.
      try { if ($script:ShipReviewerDir -and (Test-Path -LiteralPath $script:ShipReviewerDir)) { Remove-Item -Recurse -Force $script:ShipReviewerDir -ErrorAction SilentlyContinue } } catch { }
      $script:ShipReviewerDir = ''
      Pop-Location
    }
    } catch {
      # T26-SHIPSAGA saga report: on any leg failure, state progress, then rethrow the original exception
      # untouched — exit code / failure surface / upstream catch behavior all unchanged. The report states
      # status and commands only, no auto-recovery/retry; the raw exception prints right after it
      # (report = shape, exception = root cause).
      $sagaTodo = @($sagaLegs | Where-Object { $sagaDone -notcontains $_ })
      $sagaFirst = if ($sagaTodo.Count) { $sagaTodo[0] } else { '(undecidable: every leg already marked done)' }
      # Fail-point truth source (R3 r3 #9): when a between-legs operation (preflight) fails, $sagaAt is
      # non-empty — never infer "first pending leg" for those (the TD85 misjudgment reported them as DoD).
      $sagaFailPoint = if ($sagaAt) { "$sagaAt; first pending leg after it = $sagaFirst" } else { "$sagaFirst (= first pending leg)" }
      Write-Host "`n-- ship saga report [T26-SHIPSAGA] (retryable saga: every merge gate is deterministic and idempotent - fix, then rerun the same ship to resume; see the recovery line below) --" -ForegroundColor Yellow
      Write-Host ('  [SAGA-DONE] ' + $(if ($sagaDone.Count) { $sagaDone -join ' -> ' } else { '(none)' })) -ForegroundColor Yellow
      # T143: the leg that did not finish gets its own line, so a ship that dies mid-saga still reports
      # where the time went rather than only which legs completed.
      Write-Host "  [SHIP-TIME] (incomplete leg) $([math]::Round($sagaClock.Elapsed.TotalSeconds - $sagaMark, 1))s" -ForegroundColor Yellow
      Write-Host "  [SAGA-FAIL] $sagaFailPoint" -ForegroundColor Yellow
      # Pending legs (R3 r4 #9): drop the first pending item only when the failure point IS a leg
      # ($sagaAt empty); when a between-legs preflight/gate failed, the first pending leg did not fail
      # and must not be swallowed from the pending list.
      $sagaPending = @(if ($sagaAt) { $sagaTodo } else { $sagaTodo | Select-Object -Skip 1 })
      Write-Host ('  [SAGA-TODO] ' + $(if ($sagaPending.Count) { $sagaPending -join ' -> ' } else { '(none)' })) -ForegroundColor Yellow
      # Recovery routing (post-T68/T69): with the RED-evidence gate and its watershed receipts removed,
      # re-running the same ship command is ALWAYS a safe resume — every merge gate is deterministic and
      # idempotent (legs already passed re-pass; the commit leg is a no-op when nothing new is staged).
      # The only state-sensitive case left is the -Local merge leg, routed by on-disk state
      # ($sagaLocalMerged / MERGE_HEAD present), never by sniffing exception text.
      if ($Local -and (-not $sagaAt) -and $sagaTodo.Count -and ($sagaTodo[0] -eq 'local-merge')) {
        # -Local 合并腿失败：按**阶段状态**分流（R3 r5 #9——不嗅探异常文案：每个合并失败消息都含「冲突？」，文案匹配
        # 必把非冲突失败误导去 merge --continue）。三态：凭据未铸（$sagaLocalMerged=合并其实已成功）/ 合并中
        # （MERGE_HEAD 在盘，续跑而非重发）/ 守卫拦下（合并未开始，回基线分支后重发）。置于重跑分支之前：停在合并中时重跑必失败。
        if ($sagaLocalMerged) {
          Write-Host '    [SAGA-MERGE-TOKEN] local merge already succeeded; only the T24 merge token failed to mint (token write/parse failure) - cleanup will fail-safe keep the branch; after manually confirming the merge, run -Phase cleanup -Force.' -ForegroundColor Yellow
        } else {
          $sagaMergeHead = "$(& git -C $RepoRoot rev-parse --git-path MERGE_HEAD 2>$null)".Trim()
          if ($sagaMergeHead -and -not [System.IO.Path]::IsPathRooted($sagaMergeHead)) { $sagaMergeHead = Join-Path $RepoRoot $sagaMergeHead }
          if ($sagaMergeHead -and (Test-Path $sagaMergeHead)) {
            Write-Host "    [SAGA-MERGE-CONFLICT] local merge conflict (MERGE_HEAD on disk) - main path, three steps (the resolved tree re-passes every gate, not a bypass; authority: docs/DEVOPS-WORKFLOW.md TD85-RESUME S6): (1) git merge --abort in the main checkout (2) inside worktree $Wt run git merge $Base, resolve conflicts and commit (no rebase) (3) rerun the same ship (the resolved tree re-passes the scope gate and every other gate; the in-pipeline merge is then clean). Last resort (tree cannot pass the scope gate): git merge --continue in the main checkout, then IMMEDIATELY rerun pwsh -File scripts\selftest.ps1 (or the project verify) to check the merged tree." -ForegroundColor Yellow
          } else {
            Write-Host "    [SAGA-MERGE-GUARDED] local merge never started - stopped by a guard (merge-target drift/detached, L86/F3): in the main checkout switch back to the baseline branch (git switch $Base), then rerun git -C `"$RepoRoot`" merge --no-ff --no-edit $TaskId (without a T24 token cleanup will fail-safe keep the branch; -Force after confirming the merge)." -ForegroundColor Yellow
          }
        }
      } else {
        # Full re-run is always safe: every merge gate is deterministic and idempotent. Rebuild the exact
        # command with every behavior-affecting bound option (dropping -Base would change the baseline,
        # dropping -NoAutoMerge would merge against the caller's intent). -Base only when explicitly bound.
        $sagaCmd = "pwsh -File scripts\task.ps1 -TaskId $TaskId -Phase ship"
        if ($PSBoundParameters.ContainsKey('Base')) { $sagaCmd += " -Base $Base" }
        if ($Local) { $sagaCmd += ' -Local' }
        if ($SkipRed) { $sagaCmd += ' -SkipRed' }   # compat no-op flag: still echoed so the rebuilt command matches what the caller ran
        if ($NoAutoMerge) { $sagaCmd += ' -NoAutoMerge' }
        Write-Host "  [SAGA-RESUME] $sagaCmd  (rerun = resume: every merge gate is deterministic and idempotent; already-passed legs pass again - no deadlock, no bypass)" -ForegroundColor Yellow
      }
      throw
    }
  }

  'cleanup' {
    Step 'R1 Windows 安全拆除 worktree'
    if (Test-Path $Wt) {
      # TD47 脏树守卫：cleanup 会 force-destroy worktree（worktree remove --force + Remove-Item -Recurse -Force），
      # 未提交/未跟踪改动一旦删除即不可逆（未入 git 对象库、无 reflog）。删除前先查脏树：有改动且无 -Force → 拒绝、
      # 打印将丢失什么、零删除；干净树（或 -Force 显式覆盖，或非 git 目录=残留态）照旧拆除。
      # 用 `status --porcelain`（非 `branch --merged`）：squash-merge 使卡分支非 base 祖先，--merged 会误拒正路径 cleanup、破坏收尾链。
      $dirty = & git -C $Wt status --porcelain 2>$null
      if (($LASTEXITCODE -eq 0) -and $dirty -and (-not $Force)) {
        Write-Host '以下未提交/未跟踪改动将随 worktree 一并永久丢失（未入 git 对象库、不可恢复）：' -ForegroundColor Red
        $dirty | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
        throw "cleanup 已拒绝拆除 '$Wt'：worktree 有未提交改动（见上）。确认要丢弃 → 加 -Force 重跑；要保留 → 先在该 worktree 提交/推送后再 cleanup。"
      }
      & git -C $RepoRoot worktree remove --force $Wt 2>&1 | Write-Host
      if (Test-Path $Wt) {
        Write-Warning '常规移除失败（多半 .venv/node_modules 被占用）。请关掉占用该目录的进程后重试。'
        & git -C $RepoRoot worktree remove --force --force $Wt 2>&1 | Write-Host
        # 即便 git 取消了 worktree 跟踪，被锁文件仍可能残留 → 显式清目录，避免下次 start 撞 "已存在"
        if (Test-Path $Wt) { Remove-Item -Recurse -Force $Wt -ErrorAction SilentlyContinue }
      }
    }
    & git -C $RepoRoot worktree prune
    # T24-MERGETOKEN 凭据闸：branch -D 是本相位唯一无脏树守卫的破坏性删除点——未合并提交被删仅 reflog 期限内可救。
    # 删除本地分支只认三种机检/显式信号之一：① ship 合并成功时铸造的单次凭据（用后即废）；② -Force（语义同 TD47 脏树
    # 守卫：确认丢弃未合并工作；abandon/重来路径走此口）；③ gh 在线补验 PR 状态==MERGED（-NoAutoMerge 人工合并/他机
    # 合并路径）。三者皆无 → 保留分支 fail-safe。不用 ancestry（--merged/merge-base）判合并：squash-merge 使卡分支
    # 永非 base 祖先（见上方脏树守卫注）。
    $tokPath = "$(& git -C $RepoRoot rev-parse --git-common-dir 2>$null)".Trim()
    if ($tokPath) {
      if (-not [System.IO.Path]::IsPathRooted($tokPath)) { $tokPath = Join-Path $RepoRoot $tokPath }
      $tokPath = Join-Path (Join-Path $tokPath 'scaffold-merged') $TaskId
    }
    $branchTip = "$(& git -C $RepoRoot rev-parse --verify --quiet $TaskId 2>$null)".Trim()
    if (-not $branchTip) {
      # 本地分支已不存在：无事可删（保持既有幂等语义，静默略过）
    } elseif ($Force) {
      # R3 r3 #9：显式人工覆盖优先于一切凭据判定——若排在凭据分支之后，残留/tip 不匹配的凭据会遮蔽 -Force，
      # 警告里给出的「-Force 重跑」出路将永不可达。-Force 语义（确认丢弃）覆盖任意状态，故用 branch -D。
      & git -C $RepoRoot branch -D $TaskId 2>$null
      if ($LASTEXITCODE -eq 0) {
        if ($tokPath -and (Test-Path $tokPath)) { Remove-Item $tokPath -Force -ErrorAction SilentlyContinue }   # 连带注销残留凭据（防陈旧凭据日后误配同名新分支）
        if ($tokPath -and (Test-Path $tokPath)) {
          # R3 r4 #9：与凭据路径同款——注销失败不得虚报成功，残留凭据须显式报修
          Write-Warning "T24-MERGETOKEN：-Force 已删除本地分支 $TaskId，但残留凭据注销失败（$tokPath 仍在）——请手工删除该文件（防日后误配同名新分支）。"
        } else {
          Write-Host "T24-MERGETOKEN：-Force 在位（确认丢弃未合并工作）——已删除本地分支 $TaskId 并注销残留凭据。" -ForegroundColor Yellow
        }
      } else {
        Write-Warning "T24-MERGETOKEN：-Force 下 branch -D 仍失败（分支被别的 worktree 占用？）——处理后重跑 cleanup。"
      }
    } elseif ($tokPath -and (Test-Path $tokPath)) {
      # R3 #17：凭据内容升级为载荷（tip 绑定）——凭据证明的是「这个分支状态已合并」，不是「这个名字可删」。
      # 删除用 CAS（`update-ref -d <ref> <expected-sha>`，现值不符即拒）：比对与删除之间的并发 ref 前移也拦住（R3 r3 #17）。
      $tokTip = "$(@(Get-Content $tokPath -ErrorAction SilentlyContinue) -match '^tip=' -replace '^tip=', '' | Select-Object -First 1)".Trim()
      if ($tokTip -and ($tokTip -ieq $branchTip)) {
        & git -C $RepoRoot update-ref -d "refs/heads/$TaskId" $tokTip 2>$null
        if ($LASTEXITCODE -eq 0) {
          Remove-Item $tokPath -Force -ErrorAction SilentlyContinue   # 单次性：仅在删除成功后注销（失败保留凭据供重试）
          if (Test-Path $tokPath) {
            # R3 #9：注销失败不得虚报成功——残留凭据可再授权一次同名分支删除（单次性受损），显式报修
            Write-Warning "T24-MERGETOKEN：本地分支 $TaskId 已删除，但凭据注销失败（$tokPath 仍在）——单次性受损，请手工删除该文件。"
          } else {
            Write-Host "T24-MERGETOKEN：合并凭据在位且 tip 匹配（CAS 删除）——已删除本地分支 $TaskId 并注销凭据。" -ForegroundColor DarkGray
          }
        } else {
          Write-Warning "T24-MERGETOKEN：CAS 删除失败（比对后 ref 又前移 / 引用被占用）——凭据保留，处理后重跑 cleanup。"
        }
      } else {
        Write-Warning "T24-MERGETOKEN：凭据在位但 tip 不匹配（凭据 tip=$tokTip / 分支 tip=$branchTip）——铸造后分支另有新提交或同名重建，旧凭据不授权删除，保留分支 fail-safe。重新合并新状态 → 重跑 ship；确认丢弃 → -Phase cleanup -Force。"
      }
    } else {
      # 在线补验前先判 origin 在位：无远端仓（含 hermetic 夹具、纯本地 -Local 项目）直接跳过 gh，保持离线确定性（L78 同族约束）。
      # 另判 gh 在位（Get-Command）：命令缺席时 `& gh` 抛 CommandNotFoundException，`2>$null` 接不住、EAP=Stop 下直接
      # 崩相位——按契约「补验不可得」应走保留分支，而非中途非零退出（fresh-context 审计 F1）。
      $hasOrigin = "$(& git -C $RepoRoot remote get-url origin 2>$null)".Trim()
      $ghOk = if ($hasOrigin) { [bool](Get-Command gh -ErrorAction SilentlyContinue) } else { $false }
      # R3 #17：在线补验同样 tip 绑定——PR 须 MERGED **且** headRefOid == 本地分支 tip，否则（PR 合并后分支又添新提交）保留。
      # --json 字段表须引号包裹（裸逗号被 PowerShell 拆成数组 → 真 gh 收到两个参数即报错）
      $prJson = if ($ghOk) { "$(& gh pr view $TaskId --json 'state,headRefOid' 2>$null)" } else { '' }
      $prState = ''; $prHead = ''
      if ($prJson) { try { $prO = $prJson | ConvertFrom-Json; $prState = "$($prO.state)"; $prHead = "$($prO.headRefOid)" } catch { $prState = ''; $prHead = '' } }
      if (($prState -ieq 'MERGED') -and $prHead -and ($prHead -ieq $branchTip)) {
        & git -C $RepoRoot update-ref -d "refs/heads/$TaskId" $prHead 2>$null
        if ($LASTEXITCODE -eq 0) {
          Write-Host "T24-MERGETOKEN：无本地凭据，gh 在线补验 PR=MERGED 且 headRefOid==本地分支 tip（CAS 删除）——已删除本地分支 $TaskId。" -ForegroundColor DarkGray
        } else {
          Write-Warning "T24-MERGETOKEN：在线补验通过但 CAS 删除失败（比对后 ref 又前移 / 引用被占用）——保留分支，处理后重跑 cleanup。"
        }
      } else {
        Write-Warning "T24-MERGETOKEN：无合并凭据且在线补验不可得/非 MERGED——保留本地分支 $TaskId（fail-safe，防丢未合并提交）。确认丢弃 → -Phase cleanup -Force 重跑；确已合并 → 人工 git branch -D $TaskId。"
      }
    }
    Write-Host "`nR5 文档同步提醒：合并后请更新" -ForegroundColor Yellow
    Write-Host "  - specs/tasks/$TaskId.md  status: -> merged" -ForegroundColor Yellow
    Write-Host "  - CLAUDE.md '当前阶段' / README（若面向用户）" -ForegroundColor Yellow
    Write-Host "  - 冷存清扫：pwsh -File scripts\archive.ps1（卡一旦 merged 就该搬出热路径；下面的自检会告诉你是否有待搬项）" -ForegroundColor Yellow
    Write-Host "`n复盘（自净化经验，见 docs/LESSONS.md）：本卡若踩过非平凡坑，入账" -ForegroundColor Yellow
    Write-Host "  pwsh -File scripts\lessons.ps1 add -Tags '..' -Severity blocking|major|minor -Symptom '..' -RootCause '..' -Rule '..'" -ForegroundColor DarkGray
    Write-Host "  blocking 的当场 promote <id>" -ForegroundColor DarkGray

    Step '经验系统自检（lessons check）'
    & pwsh -NoProfile -File (Join-Path $RepoRoot 'scripts/lessons.ps1') check
    if ($LASTEXITCODE -ne 0) { Write-Warning '经验系统 check 未过（必须层超限/id 重复/字段缺失）——见 docs/LESSONS.md 提纯。' }

    # R5 doc-sync also owns the cold store, and cleanup is the moment it is actually due: the card just
    # merged, so it is now a `merged` card sitting in the hot specs/tasks/ - precisely the state the cold
    # store exists to drain. -Check is read-only; it re-projects both archive indices and names any drift,
    # and prints [ARCHIVE-CHECK-PENDING] listing what is waiting to be swept, so the sweep command is one
    # line away exactly when it is needed rather than waiting for a triage heartbeat to notice.
    # ADVISORY, like the lessons check above - deliberately NOT a gate: this step runs AFTER the merge, and
    # the merge bar stays the deterministic ship gates (T68 - the merge path gets thinner, never thicker).
    # 脚本缺席即跳过（下游可能裁掉冷存链）——EAP=Stop 下直接 & 一个不存在的路径会崩相位，而 cleanup 走到这里
    # 时合并早已完成，绝不该因一条建议性自检而失败（同 ci.yml 对 check-cards 的 Test-Path 处理）。
    $archiveScript = Join-Path $RepoRoot 'scripts/archive.ps1'
    if (Test-Path $archiveScript) {
      Step '冷存索引自检（archive -Check：两张索引仍等于生成器投影？含待搬项提示）'
      & pwsh -NoProfile -File $archiveScript -Check
      if ($LASTEXITCODE -ne 0) { Write-Warning '[ARCHIVE-CHECK] 冷存索引已不等于生成器投影——重跑 pwsh -File scripts\archive.ps1 重投影，切勿手工编辑索引（见 specs/archive/README.md）。' }
    }
  }
}
