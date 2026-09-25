---
id: T0-REMOTE-ROUND3-REQUESTS-EVIDENCE
title: Publish complete retained Requests proof with explicit historical retention gap
status: merged
depends_on: [T3-PDF-MEASUREMENT-REQUESTS]
allow_paths:
  - specs/tasks/T0-REMOTE-ROUND3-REQUESTS-EVIDENCE.md
acceptance:
  - "A1 Replay all fixed retained778 leaves and exact nested437/136/19/23/78 manifests, full26 compiling mutants and29 named AssertionErrors with reconstructed source/restoration, native timings and exact filters. Preserve complete original Requests acceptance and successor ownership."
  - "A2 Bind restored DoD and verify to exact sources/commands/logs/XML and distinguish cached from fresh. Bind actual reviewed/merged13 source blobs, one actual Sol/high PASS and all raw lifecycle/CI/merge/cleanup records; no replayed runtime or hidden failed event."
  - "A3 Preserve contemporaneous independent first-GREEN audit, recovered raw copies and root coverage decision. The original51 manifest/support package remains missing; no original per-file hash claim, no restored227 raw claim, no retrodated receipt and no weakened product acceptance."
  - "A4 Independently approved whole own-card source, exact schema/task/root decision, original-D latest sole-path authority commit/blob/rawSHA, committed scope and four whitespace states; missing or malformed authority blocks. Reject linked/reparse portable leaves before reads."
forbid:
  - Product/runtime/test/schema/script/config changes, product R5 closure, fabricated historical evidence or approvals
  - Treating cached ship XML as fresh acceptance, audit references as original leaves, wrapper failure as actual review, or ignored hashes as authority
non_goals:
  - Restore missing historical51, rerun tests, deliver Binding snapshots or19 Composer integration cases, device claims
dod_command: $raw=Get-Content 'specs/tasks/T0-REMOTE-ROUND3-REQUESTS-EVIDENCE.md' -Raw -Encoding utf8; $m=[regex]::Matches($raw,'(?ms)^```python\r?\n(.*?)^```[ \t]*$'); if($m.Count -ne 1){throw 'one complete proof core required'}; $dir=Join-Path (Get-Location) '_local/requests-evidence-runtime'; New-Item -ItemType Directory -Force $dir | Out-Null; $script=Join-Path $dir 'check.py'; [IO.File]::WriteAllText($script,$m[0].Groups[1].Value,[Text.UTF8Encoding]::new($false)); & python $script --evidence '_local/round3-requests-proof' --repo (Get-Location).Path --guard; if($LASTEXITCODE -ne 0){exit 1}; & pwsh -NoProfile -File scripts/check-cards.ps1; if($LASTEXITCODE -ne 0){exit 1}; & pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex; if($LASTEXITCODE -ne 0){exit 1}
dod_exit: 0
dod_assert: Full retained proof replay and explicit missing-history disclosure, exact source/lifecycle/CI/cleanup, independent whole-card authority and one-path scope pass.
review_gate: codex {verdict:pass}
hygiene: True metadata SkipRed only after root approval; first full diff<=750lines/45000UTF16chars, hard1000/60000 with repair reserve; retain all semantic negative controls.
doc_sync: Merged state becomes effective only after actual publication merge. Approval is established only by the external root record and actual full DoD; publication adds no product completion.
---

# Retained Requests evidence publication

Projection base `c0afac77175786b55c61c6ebb0292a64a33431a8`; historical product merge `47b78af825c699608821eb75bfa77104ac4bb86b`, reviewed head `f79458ac44b387f1d1cc91cffd29e1c238f7d6d5`. The complete original contract stays unchanged at SHA `3B3D4506D11779E975548E5374CD140E936A638D8823119BCA8762394912C2E7`. This own-card-only metadata proposal publishes the complete replay below. The retained778-leaf bundle is separately copied outside canonical and pinned by its immutable portable index; the published code is the later closure ABI `replay(evidence, repo)`.

**Historical retention gap remains explicit.** Original first-GREEN's51-entry manifest `117C852D...` and full supporting package were not recovered after cleanup. Its contemporaneous independent audit verified51/51 then; that is an audit record, not today's original leaf ledger. Later recovered24 XML copies have original-run suite timestamps/272 passing cases, nine source copies match the pre-GREEN checkpoint, and recovered raw-log bytes match the earlier audit hash. Their later source paths remain provenance. No missing manifest is reconstructed, and history227 denotes audit references rather than227 surviving raw leaves. Root's fixed preserved coverage decision concludes final raw evidence supports original A1–A5 without new gratuitous execution; it does not waive or conceal archival loss.

The complete final437 set contains26 compiling mutations/29 named assertions and restored acceptance. GREEN-02 is24 cached suites; GREEN-03 has21 fresh report suites/266 cases plus3 cached e2e suites/6 cases. Verify has89 fresh core suites/987 cases and3 cached e2e suites/6, with4 explicitly recorded existing symlink-permission skips. Ship136's92 XML suites are all cached. The actual one formal PASS comes from the raw reviewer output with exact Sol/high/session/head; six peripheral read rejections are retained. Saved native evidence, not filesystem copy mtimes, establishes historical chronology. Actual cleanup native0 and immediate raw postabsence are retained separately from the earlier ship receipt's then-pending wording.

The retained19 R1,23 pre-GREEN,437 final,136 ship,78 Binding handoff, three earlier budget files,14 cleanup leaves, recovered copies, contemporaneous audit and independent coverage/root decision remain exact original bytes. Outer index also includes original manifests and provenance documents. A future cleanup must enumerate nested references and independently preserve every claimed required raw leaf before deleting its source. Original acceptance still assigns19 Composer numerical integration cases and emitted TextRun snapshot provenance to Binding.

Independent authority is fixed at original D/master latest own-card-only commit and root `round3-requests-evidence-own-card-approval/` plus `round3-requests-evidence-root-decision.json`. Both approval and root decision require strict integer schemaVersion1; require exact task, full raw card equality, blob/SHA, actual base/scope and explicit root decision/authorizedBy. Approval is established only by those independent records and actual full DoD. No remote call, test, device, formal review, main/canonical mutation or phase is performed by replay. Complete candidate budget and actual new base must be checked again before root grants registration; later product closure independently consumes this complete published card and its actual publication lifecycle.

```python
"""Read-only retained Requests proof. Missing historical51 is disclosed, never reconstructed."""
from pathlib import Path
from datetime import datetime
import argparse,hashlib,json,re,stat,subprocess,xml.etree.ElementTree as ET
BASE='c0afac77175786b55c61c6ebb0292a64a33431a8'
PRODUCT_MERGE='47b78af825c699608821eb75bfa77104ac4bb86b'
HEAD='f79458ac44b387f1d1cc91cffd29e1c238f7d6d5'
CARD_SHA='3B3D4506D11779E975548E5374CD140E936A638D8823119BCA8762394912C2E7'
INDEX='05174669658698C8EF00382D77F903F1DAFB3C11B5F750E8ABEE84C60FA36D3B'
OWN='specs/tasks/T0-REMOTE-ROUND3-REQUESTS-EVIDENCE.md'
AUTH=Path('D:/Projects/MyInspection/_local/rotating-card-orchestrator/round3-requests-evidence-own-card-approval')
ROOT_DECISION=AUTH.parent/'round3-requests-evidence-root-decision.json'
def need(condition, label):
    if not condition: raise ValueError(label)

def sha(data): return hashlib.sha256(data).hexdigest().upper()

def text(path): return path.read_text(encoding='utf-8-sig')

def pairs(items):
    result = {}
    for key, value in items:
        need(key not in result, 'duplicate JSON key: ' + key)
        result[key] = value
    return result

def doc(path): return json.loads(text(path), object_pairs_hook=pairs)

def utc(s): return datetime.fromisoformat(s.replace('Z', '+00:00'))

def ordinary(path):
    info=path.lstat()
    need(not stat.S_ISLNK(info.st_mode) and not getattr(info,'st_file_attributes',0)&1024,'no symlink or reparse evidence: '+str(path))
    need(not stat.S_ISREG(info.st_mode) or info.st_nlink==1,'no hardlinked evidence file: '+str(path))
    return info

def safe_leaf(root, relative):
    rel=Path(relative)
    need(not rel.is_absolute() and not rel.drive and '..' not in rel.parts and '\\' not in relative,'relative evidence path')
    ordinary(root);current=root
    for part in rel.parts:current=current/part;ordinary(current)
    need(stat.S_ISREG(current.lstat().st_mode),'ordinary evidence file')
    return current

def ordinary_tree(root):
    ordinary(root);pending=[root];files=set()
    while pending:
        for path in pending.pop().iterdir():
            info=ordinary(path)
            if stat.S_ISDIR(info.st_mode):pending.append(path)
            else:
                need(stat.S_ISREG(info.st_mode),'ordinary evidence leaf')
                files.add(path.relative_to(root).as_posix())
    return files

def git(repo, *args):
    result = subprocess.run(['git', '-C', str(repo), *args], capture_output=True)
    need(result.returncode == 0, 'git read failed: ' + ' '.join(args))
    return result.stdout

def raw_review(log, saved, head, verdict):
    matches = re.findall(r'(?m)^codex\s*\r?\n(\{[^\r\n]*\})\s*\r?\ntokens used', log)
    need(len(matches) == 1, 'one actual raw review, excluding prompt examples')
    actual = json.loads(matches[0], object_pairs_hook=pairs)
    need(saved['sha'] == head and saved['verdict'].lower() == verdict, 'saved verdict/head')
    need(actual['verdict'].lower() == verdict and actual['reasons'] == saved['reasons'], 'raw reasons/verdict')
    need(bool(saved['reasons']) == (verdict == 'block'), 'BLOCK reasons / empty PASS reasons')
    parts=re.split(r'(?m)^user\r?$',log,maxsplit=1);need(len(parts)==2,'actual prompt boundary')
    prefix=parts[0];starts=list(re.finditer(r'(?m)^OpenAI Codex v[^\r\n]+\r?$',prefix))
    need(len(starts)==1,'one actual pre-prompt reviewer header');header=prefix[starts[0].start():]
    need(re.findall(r'(?m)^model: ([^\r\n]+)\r?$',header)==['gpt-5.6-sol'] and re.findall(r'(?m)^reasoning effort: ([^\r\n]+)\r?$',header)==['high'], 'actual model/effort')
    need(re.findall(r'(?m)^Codex [^\r\n]* @ ([0-9a-f]{8}) \.\.\.\r?$',prefix)==[head[:8]], 'actual review wrapper head')
    need(len(re.findall(r'(?m)^session id: [0-9a-f-]+\s*$',header))==1, 'one actual reviewer session')
    return actual

def source(repo, path, head, merge, raw, pin, blob):
    need(sha(raw) == pin, 'source raw SHA: ' + path)
    for commit in [head, merge]:
        need(git(repo, 'rev-parse', commit + ':' + path).decode().strip() == blob, 'reviewed/merged blob: ' + path)
        need(git(repo, 'show', commit + ':' + path) == raw, 'reviewed/merged/saved bytes: ' + path)

def approval_identity(approval, decision, card_raw):
    need(type(decision.get('schemaVersion')) is int and decision['schemaVersion']==1,'root decision schemaVersion exactly 1')
    need(type(approval.get('schemaVersion')) is int and approval['schemaVersion']==1,'approval schemaVersion exactly 1')
    need(approval.get('task')==Path(OWN).stem,'approval exact task')
    need(approval.get('rootAuthorization')==ROOT_DECISION.as_posix(),'approval fixed rootAuthorization')
    need(decision.get('status')=='ROOT_APPROVED_FOR_SCOPED_BOOTSTRAP_R1_LIGHT_ONLY','explicit root decision status')
    for key,value in {'task':Path(OWN).stem,'repository':'D:/Projects/MyInspection','ref':'refs/heads/master','path':OWN,'base':BASE,'sha256':sha(card_raw),'allow_paths':[OWN]}.items():
        need(decision.get(key)==value,'root decision exact '+key)
    need(isinstance(decision.get('decision'),str) and bool(decision['decision'].strip()),'explicit root decision text')
    need(isinstance(decision.get('authorizedBy'),str) and bool(decision['authorizedBy'].strip()),'explicit root authorizedBy')
    need(utc(decision['utc'])<=utc(approval['approvedUtc']),'root decision precedes approval')

def publication_guard(repo, card_raw):
    # Authority identifiers are fixed by reviewed code, never selected by ignored candidate input.
    origin=Path('D:/Projects/MyInspection')
    commit=git(origin,'log','-1','--format=%H','refs/heads/master','--',OWN).decode().strip()
    need(bool(commit), 'own card has no independent authority commit yet')
    changed=git(origin,'diff-tree','--no-commit-id','--name-only','-r',commit).decode().splitlines()
    need(changed==[OWN], 'authority commit changes only complete own card')
    approval=doc(AUTH/'approval.json'); approved=(AUTH/'approved-card.md').read_bytes()
    approval_identity(approval,doc(ROOT_DECISION),card_raw)
    need(approval['repository']=='D:/Projects/MyInspection' and approval['ref']=='refs/heads/master' and approval['path']==OWN, 'fixed approval authority')
    need(approval['commit']==commit and approval['base']==BASE and approval['allow_paths']==[OWN], 'approval commit/base/exact scope')
    blob=git(origin,'rev-parse',commit+':'+OWN).decode().strip()
    need(approval['blob']==blob and approval['sha256']==sha(card_raw), 'whole approved-card blob/SHA')
    need(card_raw==approved==git(origin,'show',commit+':'+OWN), 'candidate/complete approved/authority bytes')
    actual=set(git(repo,'diff','--name-only',BASE,'HEAD').decode().splitlines())
    need(actual=={OWN}, 'whole publication exact one-path scope')
    need(not git(repo,'status','--porcelain','--untracked-files=all').strip(), 'formal candidate clean and committed')
    for args in [('diff','--check',BASE,'HEAD'),('diff','--check',BASE),('diff','--check'),('diff','--cached','--check')]:git(repo,*args)

check=need
load=doc
read_json=doc
stamp=utc
time=utc
digest=sha

def file_pin(path,expected_bytes=None,expected_sha=None,label=None):
    raw=path.read_bytes()
    if expected_bytes is not None:need(len(raw)==expected_bytes,str(label or path)+' bytes')
    if expected_sha is not None:need(sha(raw)==expected_sha.upper(),str(label or path)+' SHA')
    return raw

def bytes_at(path,length=None,sha=None,name=None):return file_pin(path,length,sha,name)
def read_saved(path,relative):return path.read_bytes()

def manifest(root,name,pin,count,exact=True):
    raw=safe_leaf(root,name).read_bytes();need(sha(raw)==pin,'fixed child manifest '+name)
    entries=doc(root/name)['artifacts'];names=[e['path'].replace('\\','/') for e in entries]
    need(len(names)==len(set(names))==count,'child exact count/set')
    if exact:need(ordinary_tree(root)==set(names)|{name},'child entire leaf set')
    for entry,relative in zip(entries,names):file_pin(safe_leaf(root,relative),entry['bytes'],entry['sha256'])
    return entries

def mutations(evidence):
    SOURCE=evidence/'final/_local/measurement-requests'
    PREP=SOURCE/'r4-preparation';EXEC=SOURCE/'r4-execution'
    BASELINE=SOURCE/'r4-closeout/green-02/sources'
    EXPECTED_IDS={f'E{i:02}' for i in range(1,10)}|{f'B{i:02}' for i in range(1,8)}|{f'L{i:02}' for i in range(1,11)}
    ROWS=[]
    plan_bytes = read_saved(PREP / 'mutation-plan.json', 'mutation-plan.json')
    check(sha(plan_bytes) == '910D2FAAAA145FC48BD8F8A22177471902A603F9830DA7623273B00838346766', 'plan SHA-256')
    plan = json.loads(plan_bytes)
    variants = plan['variants']
    ids = [v['id'] for v in variants]
    check(len(ids) == 26 and len(set(ids)) == 26 and set(ids) == EXPECTED_IDS, '26 unique E/B/L IDs')
    check(sum(len(v['targets']) for v in variants) == 29, '29 named targets in plan')
    source_bytes = file_pin(BASELINE / plan['source'], expected_sha=plan['sourceSha256'], label='frozen baseline source')
    read_saved(BASELINE / plan['source'], 'baseline/ReportComposer.kt')
    actual_dirs = {p.name for p in EXEC.iterdir() if p.is_dir()}
    check(actual_dirs == EXPECTED_IDS, 'execution variant directory set')

    for batch in sorted(EXEC.glob('batch-*.json')):
        read_saved(batch, f'batches/{batch.name}')
    batch_results = [r for batch in EXEC.glob('batch-*.json') for r in read_json(batch).get('results', [])]
    check(len(batch_results) == 26 and {r['id'] for r in batch_results} == EXPECTED_IDS, 'batch receipt 26 exact IDs')
    check(all(r['status'] == 'KILLED_BY_EXACT_NAMED_ASSERTION' for r in batch_results), 'batch statuses')

    for variant in variants:
        vid = variant['id']
        directory = EXEC / vid / 'attempt-01'
        check(directory.is_dir(), f'{vid}: attempt directory')
        names = {'result.json', 'restoration.json', 'original-source.bin', 'mutant-source.bin', 'compile.raw.log', 'compile.json', 'test.raw.log', 'test.json', 'TEST-nz.myinspection.core.report.ReportComposerLayoutContractTest.xml'}
        found = {p.name for p in directory.iterdir() if p.is_file()}
        check(found == names, f'{vid}: exact attempt leaf set')
        for name in names:
            read_saved(directory / name, f'{vid}/{name}')
        result = read_json(directory / 'result.json')
        compile_data = read_json(directory / 'compile.json')
        test_data = read_json(directory / 'test.json')
        restoration = read_json(directory / 'restoration.json')
        check(result['id'] == vid and result['status'] == 'KILLED_BY_EXACT_NAMED_ASSERTION', f'{vid}: result identity/status')
        check(result['targets'] == variant['targets'], f'{vid}: exact target plan')
        check(result['compile'] == compile_data and result['test'] == test_data and result['restoration'] == restoration, f'{vid}: result/detail equality')
        active = result['active']
        check(active['id'] == vid and active['base'] == plan['base'] and active['source'] == plan['source'], f'{vid}: active identity')
        check(active['originalSha256'] == variant['sourceBeforeSha256'] == plan['sourceSha256'], f'{vid}: source before pin')
        check(active['mutantSha256'] == variant['sourceAfterSha256'], f'{vid}: mutant pin')
        check(active['attempt'].replace('\\', '/').endswith(f'/r4-execution/{vid}/attempt-01'), f'{vid}: attempt path')
        check(len(source_bytes) == len((directory / 'original-source.bin').read_bytes()) and (directory / 'original-source.bin').read_bytes() == source_bytes, f'{vid}: original source bytes')
        before = variant['before'].encode('utf-8')
        after = variant['after'].encode('utf-8')
        check(source_bytes.count(before) == 1, f'{vid}: exact selector unique')
        mutant = source_bytes.replace(before, after, 1)
        check(sha(mutant) == variant['sourceAfterSha256'], f'{vid}: reconstructed mutant hash')
        check((directory / 'mutant-source.bin').read_bytes() == mutant, f'{vid}: mutant file exact bytes')
        check(restoration['originalSha256'] == plan['sourceSha256'] and restoration['actualRestoredSha256'] == plan['sourceSha256'] and restoration['allSourceTestAndReadonlyPinsMatch'] is True, f'{vid}: restoration')
        frozen = result['frozenTestSourcePins']
        check(len(frozen) == 9 and {p['path'] for p in frozen} == {p['path'] for p in read_json(SOURCE / 'r4-closeout/green-02/dod-evidence.json')['sourceTestPins']}, f'{vid}: nine source pins set')
        for pin in frozen:
            file_pin(BASELINE / pin['path'], pin['bytes'], pin['sha256'], f'{vid}: frozen {pin["path"]}')
        for kind, record in [('compile', compile_data), ('test', test_data)]:
            raw = file_pin(directory / f'{kind}.raw.log', record['rawLogBytes'], record['rawLogSha256'], f'{vid}: {kind} raw')
            check(record['cwd'].replace('\\', '/') == 'C:/wt/T3-PDF-MEASUREMENT-REQUESTS', f'{vid}: {kind} cwd')
            check(record['rawLog'].replace('\\', '/') == f'{vid}/attempt-01/{kind}.raw.log', f'{vid}: {kind} log path')
            check(record['command'][:7] == ['cmd', '/d', '/c', 'android\\gradlew.bat', '-p', 'android', '--offline'], f'{vid}: {kind} command prefix')
            check('--no-build-cache' in record['command'], f'{vid}: {kind} no build cache')
            check(record['exitCode'] == (0 if kind == 'compile' else 1), f'{vid}: {kind} exit')
            if kind == 'compile':
                check(':core:compileTestKotlin' in record['command'], f'{vid}: compile task')
            else:
                check(':core:test' in record['command'], f'{vid}: test task')
                check([record['command'][i + 1] for i, x in enumerate(record['command'][:-1]) if x == '--tests'] == [t['gradleFilter'] for t in variant['targets']], f'{vid}: exact test filters')
        start = stamp(active['startedUtc']); cs = stamp(compile_data['startedUtc']); ce = stamp(compile_data['endedUtc']); ts = stamp(test_data['startedUtc']); te = stamp(test_data['endedUtc']); restored = stamp(restoration['restoredUtc']); end = stamp(result['endedUtc'])
        check(start <= cs < ce <= ts < te <= restored <= end, f'{vid}: execution order')
        xml_path = directory / 'TEST-nz.myinspection.core.report.ReportComposerLayoutContractTest.xml'
        xml_data = file_pin(xml_path, result['xml']['bytes'], result['xml']['sha256'], f'{vid}: XML')
        root = ET.fromstring(xml_data)
        xml = result['xml']
        modified = stamp(xml['modifiedUtc']); suite = stamp(xml['suiteTimestamp'])
        check(ts <= modified <= te and ts <= suite <= te, f'{vid}: fresh XML times')
        check(root.attrib.get('name') == plan['className'], f'{vid}: XML suite')
        check(root.attrib.get('timestamp') == xml['suiteTimestamp'], f'{vid}: XML suite timestamp')
        for key in ['tests', 'failures', 'errors', 'skipped']:
            check(int(root.attrib.get(key, '-1')) == xml[key], f'{vid}: XML {key}')
        check(xml['tests'] == len(variant['targets']) and xml['failures'] == len(variant['targets']) and xml['errors'] == 0 and xml['skipped'] == 0, f'{vid}: XML exact failure totals')
        cases = root.findall('testcase')
        check(len(cases) == len(variant['targets']), f'{vid}: XML exact testcase count')
        observed = {}
        for case in cases:
            failures = case.findall('failure')
            check(len(failures) == 1 and not case.findall('error') and not case.findall('skipped'), f'{vid}: {case.attrib.get("name")}: primary failure only')
            if failures:
                observed[case.attrib['name']] = failures[0]
        for target in variant['targets']:
            name = target['method']
            check(name in observed, f'{vid}: named testcase {name}')
            if name in observed:
                failure = observed[name]
                check(failure.attrib.get('type') == target['xmlFailureType'] == 'java.lang.AssertionError', f'{vid}: named AssertionError {name}')
                message = failure.attrib.get('message', '') + (failure.text or '')
                for fragment in target['requiredMessageFragments']:
                    check(fragment in message, f'{vid}: message fragment {name}: {fragment}')
        outcomes = xml['outcomes']
        check(len(outcomes) == len(variant['targets']) and {o['method'] for o in outcomes} == {t['method'] for t in variant['targets']}, f'{vid}: result named outcomes')
        ROWS.append({'id': vid, 'mutantSha256': sha(mutant), 'targets': [t['method'] for t in variant['targets']], 'compileExit': compile_data['exitCode'], 'testExit': test_data['exitCode'], 'xmlSha256': sha(xml_data), 'restoredSha256': restoration['actualRestoredSha256'], 'endedUtc': result['endedUtc']})

    check(len({r['mutantSha256'] for r in ROWS}) == 26, '26 unique mutant hashes')
    return {'variants':len(ROWS),'assertions':sum(len(r['targets']) for r in ROWS),'copiedMtimeNotExecutionTime':True}

def closeout(saved,repo):
    CLOSE=saved/'final/_local/measurement-requests/r4-closeout'
    FIRST=CLOSE/'green-02' # Later frozen source copies, not a restored original51 package.
    evidence=doc(CLOSE/'closeout-evidence.json')
    need(sha((CLOSE/'closeout-evidence.json').read_bytes())=='E9735F1EEA1BE17429AB6E76EBD1CE36F04448E3CE3FE855311D178C8B318D46','fixed closeout evidence')
    manifest(saved/'final','_local/measurement-requests/r4-closeout/mutation-evidence-manifest.json','2EF068A821BF48D0932124ECABEC710D32F61BD02DB05A27588EDB0BA666C8F1',285,False)
    final_pins = evidence['finalSourceTestPins']
    first_pins = load(FIRST / 'dod-evidence.json')['sourceTestPins']
    check(len(final_pins) == len(first_pins) == 9 and {p['path'] for p in final_pins} == {p['path'] for p in first_pins}, 'nine final source paths')
    changes = []
    for pin in final_pins:
        rel = pin['path']
        final_bytes = bytes_at(CLOSE / 'green-03/sources' / rel, pin['bytes'], pin['sha256'], f'final source {rel}')
        prior = bytes_at(FIRST / 'sources' / rel)
        if final_bytes != prior:
            changes.append(rel)
            check(rel.endswith('/ReportComposerLayoutContractTest.kt'), f'only Layout changed {rel}')
            if rel.endswith('/ReportComposerLayoutContractTest.kt'):
                receipt = (CLOSE / 'actual-readable-receipt.txt').read_text(encoding='utf-8')
                inserted = ('\n' + '\n'.join('    ' + line for line in receipt.splitlines()) + '\n').encode('utf-8')
                check(final_bytes.count(inserted) == 1 and final_bytes.replace(inserted, b'', 1) == prior, 'Layout insertion equals readable receipt only')
    check(changes == ['android/core/src/test/kotlin/nz/myinspection/core/report/ReportComposerLayoutContractTest.kt'], 'only Layout changed from first GREEN')
    for stage, pins in [('green-02', evidence['restoredDoD']['sourceTestPins']), ('green-03', evidence['finalDoD']['sourceTestPins']), ('verify-before', evidence['verify']['native']['beforeSourceTestPins']), ('verify-after', evidence['verify']['native']['afterSourceTestPins'])]:
        expected = first_pins if stage == 'green-02' else final_pins
        check(pins == expected, f'{stage}: source pins')

    diff = bytes_at(CLOSE / 'complete-with-r4.diff', sha='B70113840F9486A61107C77F450E256D13DE5EFF2200A3F8C408AF68A374B9C5')
    check(bytes_at(CLOSE / 'green-03/candidate.diff') == diff, 'final complete diff equals green-03 diff')
    check(len([line for line in diff.decode('utf-8').splitlines() if line.startswith('diff --git ')]) == 9, 'nine final diff paths')
    check(sum(line.startswith('+') and not line.startswith('+++') or line.startswith('-') and not line.startswith('---') for line in diff.decode('utf-8').splitlines()) == 393, 'independent 393 changed lines')
    check(len(diff.decode('utf-8').encode('utf-16-le')) // 2 == 39923, 'independent 39923 UTF-16 units')
    budget = load(CLOSE / 'actual-complete-budget.json')
    check(budget['changedLines'] == 393 and budget['utf16Units'] == 39923 and budget['with25PercentRepair'] == 49904 and budget['before40000Headroom'] == 77, 'final size budget')
    check(budget['completeDiffSha256'] == digest(diff) and budget['onlyReceiptAdded'], 'budget pins and receipt-only declaration')
    gate_budget = load(CLOSE / 'gate-budget-check.json')
    check(gate_budget['gateUtf16Units'] == 39923 and gate_budget['changedFiles'] == 9, 'actual gate budget receipt')

    STAGES = {}
    for stage in ['green-02', 'green-03']:
        data = load(CLOSE / stage / f'{stage}.json')
        detail = load(CLOSE / stage / 'dod-evidence.json')
        raw = bytes_at(CLOSE / stage / f'{stage}.raw.log', data['rawLogBytes'], data['rawLogSha256'], f'{stage} raw')
        check(data['exitCode'] == detail['nativeExit'] == 0, f'{stage}: actual native exit 0')
        check(data['cardSha256'] == '3B3D4506D11779E975548E5374CD140E936A638D8823119BCA8762394912C2E7', f'{stage}: card SHA')
        check(data['base'] == evidence['base'] and data['startedUtc'] == detail['startedUtc'] and data['endedUtc'] == detail['endedUtc'], f'{stage}: identity/time')
        card = git(repo,'show',PRODUCT_MERGE+':specs/tasks/T3-PDF-MEASUREMENT-REQUESTS.md').decode('utf-8')
        dod = next(line.removeprefix('dod_command: ') for line in card.splitlines() if line.startswith('dod_command: '))
        check(data['command'] == ['pwsh', '-NoProfile', '-Command', dod], f'{stage}: exact DoD command')
        check(detail['rawLogSha256'] == digest(raw), f'{stage}: detail raw pin')
        xml_manifest = load(CLOSE / stage / 'xml-manifest.json')
        check(len(xml_manifest) == 24 and {x['kind'] for x in xml_manifest} == {'report', 'e2e'}, f'{stage}: XML set count/kind')
        totals = {'report': {'suites': 0, 'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}, 'e2e': {'suites': 0, 'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}}
        fresh = 0
        cases = set()
        for x in xml_manifest:
            path = CLOSE / stage / x['snapshot'].replace('\\', '/')
            xml = bytes_at(path, x['bytes'], x['sha256'], f'{stage}: {x["name"]}')
            root = ET.fromstring(xml)
            check(root.attrib.get('name') == x['name'] and root.attrib.get('timestamp') == x['suiteTimestamp'], f'{stage}: {x["name"]} identity/time')
            # The closeout copier did not preserve the original Gradle XML mtime.
            # The frozen XML timestamp remains independently available in its bytes.
            for key in ['tests', 'failures', 'errors', 'skipped']:
                check(int(root.attrib.get(key, -1)) == x[key], f'{stage}: {x["name"]} {key}')
                totals[x['kind']][key] += x[key]
            totals[x['kind']]['suites'] += 1
            is_fresh = time(data['startedUtc']) <= time(x['suiteTimestamp']) <= time(data['endedUtc'])
            check(is_fresh == x['executedWithinNativeBounds'], f'{stage}: {x["name"]} freshness')
            if is_fresh:
                check(time(data['startedUtc']) <= time(x['modifiedUtc']) <= time(data['endedUtc']), f'{stage}: {x["name"]} recorded source mtime')
            if is_fresh: fresh += 1
            cases |= {t.attrib.get('name') for t in root.findall('testcase')}
        check(totals == detail['counts'] and detail['xmlFiles'] == 24 and detail['freshXmlSuites'] == fresh, f'{stage}: XML totals/freshness')
        check(set(detail['namedMethodsObserved']).issubset(cases), f'{stage}: named testcases')
        STAGES[stage] = {'counts': totals, 'freshSuites': fresh, 'rawSha256': digest(raw)}
    check(STAGES['green-02']['freshSuites'] == 0 and STAGES['green-03']['freshSuites'] == 21, 'green02 cache and green03 fresh report suites')

    v = load(CLOSE / 'verify-01/verify.json')
    vd = load(CLOSE / 'verify-01/verification-evidence.json')
    verify_raw = bytes_at(CLOSE / 'verify-01/verify.raw.log', sha=v['rawLogSha256'])
    check(v == vd['native'] and v['nativeExit'] == 0, 'verify native receipt/exit')
    check(v['command']==['pwsh','-NoProfile','-File',r'C:\wt\T3-PDF-MEASUREMENT-REQUESTS\scripts\verify.ps1'] and v['cwd']==r'C:\wt\T3-PDF-MEASUREMENT-REQUESTS', 'verify worktree script')
    check(v['base'] == evidence['base'] and time(v['startedUtc']) < time(v['endedUtc']), 'verify identity/time')
    verify_xml = load(CLOSE / 'verify-01/xml-manifest.json')
    check(len(verify_xml) == 92, 'verify XML 92 suites')
    vtotal = {'core': {'suites': 0, 'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0, 'freshXmlSuites': 0}, 'e2e': {'suites': 0, 'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0, 'freshXmlSuites': 0}}
    observed_skips = []
    for x in verify_xml:
        path = CLOSE / 'verify-01/xml' / x['kind'] / f'TEST-{x["name"]}.xml'
        root = ET.fromstring(path.read_bytes())
        check(root.attrib.get('name') == x['name'] and root.attrib.get('timestamp') == x['suiteTimestamp'], f'verify {x["name"]}: identity/time')
        for key in ['tests', 'failures', 'errors', 'skipped']:
            check(int(root.attrib.get(key, -1)) == x[key], f'verify {x["name"]}: {key}')
            vtotal[x['kind']][key] += x[key]
        vtotal[x['kind']]['suites'] += 1
        fresh = time(v['startedUtc']) <= time(x['suiteTimestamp']) <= time(v['endedUtc'])
        check(fresh == x['executedWithinNativeBounds'], f'verify {x["name"]}: freshness')
        if fresh: vtotal[x['kind']]['freshXmlSuites'] += 1
        for case in root.findall('testcase'):
            if case.find('skipped') is not None:
                skipped = case.find('skipped')
                observed_skips.append((x['name'], case.attrib['name'], skipped.attrib.get('message', ''), ET.tostring(skipped, encoding='unicode')))
    check(vtotal==vd['counts']=={'core':{'suites':89,'tests':987,'failures':0,'errors':0,'skipped':4,'freshXmlSuites':89},'e2e':{'suites':3,'tests':6,'failures':0,'errors':0,'skipped':0,'freshXmlSuites':0}}, 'verify 89/987 and e2e 3/6 totals')
    expected_skips = load(CLOSE / 'verify-skipped-tests.json')
    check(len(expected_skips) == len(observed_skips) == 4, 'four skip records')
    for x in expected_skips:
        actual_skip = next((row for row in observed_skips if row[0] == x['suite'] and row[1] == x['name']), None)
        check(actual_skip is not None and actual_skip[3] == x['skipped'], f'skip XML element {x["name"]}')
        if actual_skip:
            reason = next(s['reason'] for s in evidence['skippedTests'] if s['suite'] == x['suite'] and s['name'] == x['name'])
            check(actual_skip[2] == reason, f'skip XML message {x["name"]}')
    check({x['suite'].split('.')[-1] for x in expected_skips} == {'OrphanedAssetCleanupTest', 'PendingPhotoAssetCleanupTest', 'PendingPhotoLeaseTest'}, 'skip suite set')
    check(all('symlink' in x['skipped'].lower() or 'symbolic links' in x['skipped'].lower() for x in expected_skips), 'skip platform permission reason')

    release = load(CLOSE / 'heavy-release.json')
    check(release['status'] == 'HEAVY_RELEASE' and release['javaCount'] == 0 and release['activeMutation'] is False and release['nativeExit'] == 0 and release['head'] == evidence['base'], 'heavy release')
    check(time(release['utc']) > time(v['endedUtc']), 'release after verify')

    return {'green':STAGES,'verify':vtotal,'finalPins':final_pins}


def xml_nodes(path):
    root=ET.fromstring(path.read_bytes());nodes=root.findall('testcase')
    need(int(root.attrib['tests'])==len(nodes),'XML actual testcase count')
    need(len({(n.attrib.get('classname'),n.attrib['name']) for n in nodes})==len(nodes),'XML distinct named cases')
    for field,tag in [('failures','failure'),('errors','error'),('skipped','skipped')]:
        need(int(root.attrib[field])==sum(len(n.findall(tag)) for n in nodes),'XML actual '+field)
    return root

def retained_history(saved,repo):
    before=saved/'pre-green';r=doc(before/'red.json');receipt=doc(before/'official-red.receipt.json')
    file_pin(before/'red.raw.log',r['rawLogBytes'],r['rawLogSha256'])
    need(r['exitCode']==0 and receipt['dodExit']==1 and receipt['taskId']=='T3-PDF-MEASUREMENT-REQUESTS','official RED wrapper versus DoD exits')
    need(receipt['sha']==r['base'] and r['cardSha256']==CARD_SHA,'RED source/card identity')
    red=doc(before/'pre-green-evidence.json')['RED']
    need(red['rawLogSha256']==r['rawLogSha256'] and red['diagnosticCount']==len(red['diagnostics'])==70,'RED complete diagnostics')
    raw=text(before/'red.raw.log');need(all(line in raw for line in red['diagnostics']),'RED diagnostics in original log')
    audit=doc(saved/'contemporaneous-first-green/audit.json');gap=doc(saved/'retention/audit.json')
    need(audit['manifestSha256']==gap['firstGreenManifestSha256Known']=='117C852D4579320AC192BE8662D45F6F06BA017B048317315569B5A4515C681F','original missing manifest known identity')
    need(gap['firstGreenManifestPhysicalPresent'] is False and gap['firstGreen51ExactLeafLedgerReconstructible'] is False,'do not invent original51 restoration')
    need(audit['manifestEntries']==51 and audit['leafSetHashOrSizeMismatches']==0 and audit['nativeDoD']['exit']==0
         and type(audit.get('physicalLeavesIncludingManifest')) is int and audit['physicalLeavesIncludingManifest']==52
         and audit.get('previousCheckpointManifestSha256')==sha((before/'checkpoint-before-first-green-manifest.json').read_bytes())=='AA9D127BBC91320DE632D0DEBE0132E98A03384622A449724B472A61C3046BBA'
         and audit.get('previousCheckpointLinkMatches') is True,'contemporaneous audit observation retained')
    decision=doc(saved/'retention/root-decision.json')
    need(decision['historical51ManifestRestored'] is False and decision['historical227RawCopied'] is False and decision['newRuntimeGranted'] is False,'root retention limits')
    need(decision['productA1toA5RawSupport'] is True and decision['basisSha256']==sha((saved/'retention/coverage-decision.json').read_bytes()),'root coverage decision exact basis')
    recovered=saved/'recovered'
    need(sha((recovered/'first-green-rawlog-by-hash.log').read_bytes())==audit['nativeDoD']['rawLogSha256'],'recovered identical raw bytes, not original path provenance')
    ledger=gap['recoveredXmlLedger'];need(len(ledger)==24,'24 recovered later XML copies')
    total=0
    for entry in ledger:
        path=recovered/'first-green-xml-by-run-timestamp'/entry['path']
        file_pin(path,entry['bytes'],entry['sha256']);suite=xml_nodes(path)
        need(utc(audit['nativeDoD']['startedUtc'])<=utc(suite.attrib['timestamp'])<=utc(audit['nativeDoD']['endedUtc']),'recovered XML suite timestamp association')
        need(all(int(suite.attrib[k])==0 for k in ['failures','errors','skipped']),'recovered XML passing')
        total+=int(suite.attrib['tests'])
    need(total==272,'recovered 272 case association, original per-file manifest hashes unavailable')
    baseline=saved/'final/_local/measurement-requests/r4-closeout/green-02'
    pins=doc(baseline/'dod-evidence.json')['sourceTestPins'];need(len(pins)==9,'nine baseline copies')
    for pin in pins:need((before/'candidate-before-green'/pin['path']).read_bytes()==(baseline/'sources'/pin['path']).read_bytes(),'nine pre-GREEN and later sources identical')
    card=git(repo,'show',PRODUCT_MERGE+':specs/tasks/T3-PDF-MEASUREMENT-REQUESTS.md')
    need(sha(card)==CARD_SHA,'original registered full contract unchanged')
    need('19 Composer numerical integration cases' in card.decode() and 'T3-PDF-MEASUREMENT-BINDING' in card.decode(),'successor handoff remains in original contract')
    return {'original51':'manifest/full support package missing; contemporaneous independent audit retained','recoveredXML':24,'recoveredCases':272,'original227':'audit references only, never raw227 restoration','newRuntime':False}

def ship_and_cleanup(saved,repo,final_pins):
    ship=saved/'ship';s=doc(ship/'normal-ship-evidence.json');native=doc(ship/'native-exit.json');log=(ship/'normal-ship.raw.log').read_bytes()
    need(s['head']==HEAD and s['native']==native and native['nativeExit']==0,'actual ship exact head/native receipt')
    need(sha(log)==s['postguard']['rawLogSha256']=='50F5FF1CAF1862A45EA396B5E928AC2C393E6DD7B6CD0CC20848FE9BC76D6FD7','ship raw log exact pin')
    raw_review(log.decode('utf-8-sig'),s['formalReview'],HEAD,'pass')
    need(s['reviewToolRejectionLogOccurrences']==len(re.findall(r'(?m)^\d{4}-[^\r\n]+ERROR codex_core::tools::router: error=exec_command failed: [^\r\n]+ rejected: blocked by policy[^\r\n]*\r?$',log.decode('utf-8-sig')))==6,'retain peripheral reviewer read rejection limitation')
    pr=doc(ship/'pr-final.json');ci=doc(ship/'ci-run-final.json');jobs=doc(ship/'ci-jobs-final.json')['jobs']
    need(pr['number']==317 and pr['state']=='MERGED' and pr['headRefOid']==HEAD and pr['mergeCommit']['oid']==PRODUCT_MERGE,'actual Requests PR317 merge')
    need(ci['id']==35327016532 and ci['head_sha']==HEAD and ci['status']=='completed' and ci['conclusion']=='success','Requests exact CI run')
    need({(j['name'],j['id']) for j in jobs}=={('verify',105542315114),('required',105544463945)},'Requests exact CI jobs')
    need(len(jobs)==2 and all(j['run_id']==ci['id'] and j['head_sha']==ci['head_sha']==HEAD and j['status']=='completed' and j['conclusion']=='success' and utc(j['completed_at'])<=utc(pr['mergedAt']) for j in jobs),'all required CI complete before merge')
    need('tip='+HEAD in text(ship/'merge-token.txt') and 'merged_pr=#317' in text(ship/'merge-token.txt'),'actual merge token')
    sources=doc(ship/'merged-source-proof.json');need(len(sources)==13,'nine writable plus four readonly actual blobs')
    for entry in sources:
        raw=(ship/'sources'/entry['path']).read_bytes()
        need(entry['reviewedBlob']==entry['mergedBlob'],'same reviewed/merged blob')
        source(repo,entry['path'],HEAD,PRODUCT_MERGE,raw,entry['sha256'],entry['reviewedBlob'])
    for pin in final_pins:file_pin(ship/'sources'/pin['path'],pin['bytes'],pin['sha256'])
    entries=doc(ship/'xml-manifest.json');need(len(entries)==92,'92 ship XML cached inventory')
    totals={'core':[0,0,0],'e2e':[0,0,0]}
    for entry in entries:
        path=ship/'xml'/entry['kind']/('TEST-'+entry['name']+'.xml');file_pin(path,expected_sha=entry['sha256']);suite=xml_nodes(path)
        need(suite.attrib['name']==entry['name'] and suite.attrib['timestamp']==entry['suiteTimestamp'],'ship XML identity')
        need(not (utc(native['nativeStartedUtc'])<=utc(suite.attrib['timestamp'])<=utc(native['nativeEndedUtc'])) and entry['freshWithinWholeShipBounds'] is False,'ship XML cached, not new acceptance execution')
        need(int(suite.attrib['failures'])==int(suite.attrib['errors'])==0,'ship XML no failures/errors')
        totals[entry['kind']][0]+=1;totals[entry['kind']][1]+=int(suite.attrib['tests']);totals[entry['kind']][2]+=int(suite.attrib['skipped'])
    need(totals=={'core':[89,987,4],'e2e':[3,6,0]},'ship full counts/skips')
    clean=saved/'cleanup';m=doc(clean/'manifest.json')
    need(sha((clean/'manifest.json').read_bytes())=='591957464A4663D8259C7FBA21074E9B2BE9C9AC7D4AFFCF28D627969A46DD6F','fixed cleanup manifest')
    need(len(m['entries'])==14 and ordinary_tree(clean)=={e['path'] for e in m['entries']}|{'manifest.json'},'exact cleanup14 leaves')
    for entry in m['entries']:file_pin(safe_leaf(clean,entry['path']),entry['bytes'],entry['sha256'])
    native=doc(clean/'native-exit.json');post=doc(clean/'postcheck.json')
    need(native['exit']==0 and native['argv'][native['argv'].index('-TaskId')+1]=='T3-PDF-MEASUREMENT-REQUESTS','actual cleanup target/native exit')
    need(utc(pr['mergedAt'])<utc(native['startedUtc'])<=utc(native['endedUtc'])<=utc(post['utc']),'cleanup after merge then immediate postcheck')
    need(post['status']=='PASS' and not post['errors'] and set(post['checks'])=={'filesystemAbsent','T24Absent','T35Absent','worktreeReadExitZero','branchReadExitZero','registrationAbsent','branchAbsent','mainProtected'} and all(v is True for v in post['checks'].values()),'cleanup postabsence including filesystem/T24/T35')
    for kind in ['post-branch','post-worktrees']:
        need(doc(clean/(kind+'.exit.json'))['exit']==0,'native post-Git exit')
        need(not text(clean/(kind+'.stderr.raw')).strip(),'native post-Git stderr')
    need(not text(clean/'post-branch.stdout.raw').strip(),'actual branch absence')
    need('T3-PDF-MEASUREMENT-REQUESTS' not in text(clean/'post-worktrees.stdout.raw'),'actual registration absence')
    need(sha((clean/'cleanup.stdout.raw').read_bytes())=='1006733BF4BEF4DFF586626302747E05C60D6DAE2377578BB9EA8F7B2A267C09','cleanup raw output')
    return {'formalReviews':1,'actualVerdict':'pass','resetCount':0,'sourceBindings':13,'shipXML':'all92cached','cleanupNative':0,'cleanupPostAbsence':True}

def replay(evidence,repo):
    evidence,repo=Path(evidence),Path(repo)
    file_pin(safe_leaf(evidence,'portable-index.json'),expected_sha=INDEX)
    entries=doc(evidence/'portable-index.json')['files'];names=[e['path'] for e in entries]
    need(len(names)==len(set(names))==778 and ordinary_tree(evidence)==set(names)|{'portable-index.json'},'exact retained778 portable leaves')
    for entry in entries:file_pin(safe_leaf(evidence,entry['path']),entry['bytes'],entry['sha256'])
    for folder,name,pin,count in [('final','artifact-manifest.json','A20D20502D7D4FB8FEEEBBDF52612BDECC3D7257BA65114FD34ECD91931EA5F5',437),('ship','artifact-manifest.json','76504CAAF7E64A225F62AAA9BA3F88A5BFAB15AFC3065F8A47FED9ECA53EA5CC',136),('r1','artifact-manifest.json','AE67966D4C9CB5EE620B15CB46B3F03950CEB67621CA640AC11E98F019A5696A',19),('pre-green','checkpoint-before-first-green-manifest.json','AA9D127BBC91320DE632D0DEBE0132E98A03384622A449724B472A61C3046BBA',23),('binding-handoff','artifact-manifest.json','9F13A09285F5FF27A5B77B77BF4DEE5E4A633B307E8FC15D07AA8C840037BE6A',78)]:
        manifest(evidence/folder,name,pin,count)
    for path in (evidence/'final/_local/measurement-requests/r4-closeout').rglob('*.xml'):xml_nodes(path)
    history=retained_history(evidence,repo);r4=mutations(evidence);final=closeout(evidence,repo)
    ship=ship_and_cleanup(evidence,repo,final.pop('finalPins'))
    return {'retainedLeaves':778,'history':history,'mutations':r4,'closeout':final,'ship':ship,'scope':'saved proof replay only; missing original51 remains a retention gap; no new execution or current remote attestation'}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--evidence',required=True);parser.add_argument('--repo',required=True);parser.add_argument('--guard',action='store_true');args=parser.parse_args()
    result=replay(args.evidence,args.repo)
    if args.guard:publication_guard(Path(args.repo),(Path(args.repo)/OWN).read_bytes())
    print(json.dumps(result,ensure_ascii=True,indent=2))

if __name__=='__main__':main()
```

## R3 first-round repair preparation

The actual first publication review blocked five proof guards at `c6a22b07284fdceffec979330a6d8605be126f8a`; no publication merge or new review is claimed here. Repairs restrict model/effort/session and wrapper head to the pre-prompt record, pin full verify argv/cwd and XML totals, bind the independent audit's52-leaf observation to the retained prior checkpoint, count six raw rejected reads and bind both CI jobs to their run/head, and require all eight exact cleanup checks with boolean true values. The original51/227 retention gap is unchanged.

Local guard controls:78/78 passed (10 positive,68 negative); the original code falsely accepted60 negatives and rejected one valid prompt-decoy case. Groups: header9, verify21, prior-audit10, raw-rejection/CI11, cleanup27. Controls execute the actual `raw_review` function and uniquely labelled guard expressions; the complete saved778 replay separately passed. These are metadata preparation checks, not new product tests, committed authority approval, or formal R3. The local harness and native evidence remain in `_local/requests-publication-repair-r3-01`; the earlier harness-scope and raw-CRLF failures are preserved, excluded from passing evidence.

Replay code SHA `FA40F78C50CACC0588F5E2859BB4275B94149CA664881271028BD68D9C7FF412`; local control harness SHA `B51871DCDEFBF86BB3A4358EA81A927D30B710EE32CC5CB400DCC29103E3D7CF`; final green-control report SHA `FDBF1FEB66E6D6D767F7043A4105805E4D4A930F68860C25CBCEA237273CBAF9`; saved replay stdout SHA `31EDFBD00C2236B6CD294508E9DD6505AC3D5336D374D57E54F247D0F608D37E`. This paragraph is included in the complete candidate diff budget; the ignored harness is not a proposed tracked file.

## Reconcile note (2026-09-24)

The 2026-09 local/origin reconcile merge is not this card's publication merge. As the section above records, the publication review blocked at `c6a22b07` and no publication merge happened; `status: merged` here reflects local master's card state and does not record a completed publication, which remains open.

## Closed without publication (2026-09-25)

PR #323 was closed unmerged on 2026-09-25 by user decision. Round-2 R3 on its head `e0ec8ca5` blocked with findings that
are still open, and the round cap was reached. `publication_guard` also no longer passes: reconcile commit `13735e06`
touched this card after the approved authority commit `eb12b4af`, so finishing would need those fixes plus a new root
decision and approval. Nothing was published; the retained proof stays under `_local/`. The card keeps the
`status: merged` it received in the reconcile, so it counts as closed, but that status records no publication.
`T3-PDF-MEASUREMENT-REQUESTS` was marked merged on the strength of its own DoD, rerun on origin/master `8146d794`.
