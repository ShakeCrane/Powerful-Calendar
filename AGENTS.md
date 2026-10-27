# PowerCal — Repository Agent Instructions

This file applies repository-wide to autonomous coding agents, code reviewers, and assistants.
It defines **durable engineering rules**, not a frozen snapshot of the current task or test counts.
Read the latest user instructions and relevant specs first. When this file and a current user
decision conflict, follow the user decision and update documentation explicitly.

## 1. Project and authoritative sources

- PowerCal is an **Android-first, local-first** calendar with four independent kinds:
  `ALARM`, `ACTIVITY`, `DEADLINE`, and `MEMO`. Persistent application data, not a model reply,
  is authoritative; AI proposes, deterministic core validates and commits.
- Read `docs/README.md` for the document map.
- Core behavior: `docs/CORE_CONTRACT_FREEZE_2026-10-21.md`, **including later dated
  amendments**. Current owner amendments supersede older drafts, archived reports,
  README descriptions, and earlier handoff prompts where they conflict.
- Storage, AI authorization, and skin contracts:
  `docs/STABLE_DATA_CONTRACT_V1.md`, `docs/AI_AGENT_PROTOCOL_V2.md`,
  `docs/UI_SKIN_EXTENSION_CONTRACT_V4.md`, and relevant implemented interfaces.
  Distinguish proposals/specification, implemented code, and **verified behavior**.
- Current core freeze work: `docs/CORE_FREEZE_ACCEPTANCE_LEDGER.csv`,
  `docs/CORE_FREEZE_VERDICT.md`, `docs/agent-handoff/PROGRESS.md`,
  `docs/issues/` and `docs/agent-handoff/CORE_FREEZE_TASK_UNDERSTANDING_2026-10-23.md`.
  These are live progress references; **do not hard-code their counts, SHA, or issue status here**.
- If product documents disagree, identify the precise conflicting clause and prioritize
  the latest explicit owner-approved decision. Do not silently invent or drift the spec.

## 2. Working tree, coordination, and data safety

- **Audit before editing:** `git status --short`, current diff, recent commits, related
  contracts/tests, and relevant running processes. This repository may be edited by
  another local agent at the same time. Respect file ownership and avoid conflicting writes.
- Never blindly `git reset --hard`, `git clean`, force-push, discard a worktree diff,
  overwrite untracked assets, or stage everything with `git add .`.
- Preserve `design/`, unfinished Issue work, existing test evidence, local SDK/AVD,
  user databases and credentials. Do not clear app data, uninstall, wipe AVDs, or delete
  caches without verifying ownership and authorization. Use isolated fixtures/devices
  for destructive import/restore tests.
- Stage **only files owned by your change**; inspect staged paths before commit.
  Prefer small logical commits. If another agent is staging/committing concurrently,
  coordinate rather than absorbing their work into your commit.
- Do not put API keys, bearer tokens, production backups, private chat/calendar contents,
  signing keys, local `*.db`, AVD data, or raw sensitive logs in Git or reports.
  Key presence may be verified without printing its value.

## 3. Stable product invariants

- Keep the event types, date/date-time precision, zone/DST semantics, recurrence
  original-slot identity, optional completion tracking and independent reminder/enable
  states. Transactions, authorization, optimistic versions, and idempotence matter more
  than making an isolated UI interaction succeed once.
- AI cannot publish/modify/delete formal events merely by returning text/JSON.
  Explicit scoped user approval, validation, receipts, retries and recovery are required.
  Imported text/images and model-produced marker fields are untrusted.
- Timeline geometry represents **true time**. Preserve the central vertical time ruler,
  labels/ticks, horizontal blue current-time marker, 24h reset, vertical zoom,
  deterministic unlimited horizontal lanes, and every independent event.
  Never restore the obsolete three-lane cap or `×N` hidden-event aggregation.
- Greedy title folding, when relevant, hides only visually colliding **titles**, not
  event geometry or accessibility/tap targets; the decision must depend on visible
  candidates and actual screen rectangles, not temporal overlap alone.
- **Latest approved visual amendment matters:** a faint horizontal gray edge is
  accepted, and the blue current-time indicator may cross/cover the vertical axis.
  Do not reopen accepted visuals or unrelated UI while executing core-only QA.
- Do not remove the native recovery/fallback interface or perform `.pcskin` cutover
  under the core-freeze task. The separate skin parity/cutover gate requires its own
  explicit acceptance.

## 4. Evidence-driven engineering loop

1. Reproduce the exact problem or document why reproduction is blocked.
2. Trace root cause across persistence, deterministic core, UI, OS and AI provider.
3. Make the smallest maintainable fix; add a regression that would fail before it.
4. Run focused tests, then all relevant integration/device checks, then full gates.
5. Record expected vs actual, commit SHA, APK SHA-256, environment, screenshots/logs,
   test IDs, and residual uncertainty. No test is PASS based only on code inspection.
6. Write/update **one independently auditable Issue report per resolved problem** at
   `docs/issues/`, record the state in `docs/agent-handoff/PROGRESS.md`, commit/push
   only verified work, and immediately report the closure before continuing autonomously.
7. If a case cannot be demonstrated, use `BLOCKED` or `NOT TESTED`, never a
   fabricated PASS. Keep GO/HOLD consequences explicit.

Use statuses `PASS / FAIL / BLOCKED / NOT TESTED`; label old or reused evidence
`PREVIOUS PASS` with its source baseline. The historical “209 cases” is a planning
number, not independently recovered original case IDs: the reconstructed ledger must
retain provenance and avoid inventing historical sign-offs. JVM unit-test totals do
not substitute for formal acceptance-case coverage.

## 5. Commands and evidence locations

From repository root, on Windows:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --console=plain
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --console=plain
python -m unittest discover -s tools/skin-studio/tests -v
```

For JavaScript edits use `node --check <changed-file>`. On-device tests use the
**verified** repository-local Android SDK/ADB and explicit device serial (the
historical test emulator was `emulator-5554`; recheck at runtime). Do not conflate
Robolectric, emulator, physical/OEM device, and third-party calendar compatibility.

Keep scripts, emulator captures, and detailed diagnostic evidence under
`.local/android-test/` (ignored by Git). Store non-secret summaries, contracts,
acceptance indexes, and Issue reports under `docs/`. Never write test artifacts
to random unrelated paths.

## 6. API cost, authorization, and stopping rules

- Prefer configured free CommandCode models for AI tests. The user's only
  pre-authorized paid model is `deepseek/deepseek-v4.1-flash`; use it **sparingly**
  for essential controlled comparisons, with a bounded call count. Do not assume any
  other paid model is approved or retry indefinitely through rate limits.
- Never weaken data integrity, scoped approvals, or security just to satisfy a test.
  Changes involving irreversible user-data loss, spending beyond agreed bounds,
  spec conflicts, release signing, or actual production deployment require user
  authorization; routine low-risk tests/fixes do not need per-step approval.
- GO requires reconciled contract coverage, all required high-priority test evidence,
  closed freeze blockers, platform/physical-device evidence where mandatory, and
  a reproducible Git/APK/evidence baseline in `docs/CORE_FREEZE_VERDICT.md`.
  Otherwise stay **HOLD** and report exactly what is missing.
- Work on the **core freeze only** until it is genuinely complete or the user changes
  scope; do not interpret this file as permission to launch unrelated feature projects.


## 7. Owner-approved post-core separation phase (2026-10-27)

The owner expressly authorized **core scope freeze** and beginning UI/skin
separation. Read docs/CORE_FREEZE_OWNER_SIGNOFF_2026-10-27.md and
docs/SEPARATION_PHASE_KICKOFF_2026-10-27.md before new work. The exact frozen
app-source commit is b1f7629f0c52812e1887443872f43aa6c49908c4.

The earlier HOLD/GO requirement remains a **Release QA/cutover** gate, not
permission to veto the owner's core-work-scope signoff. Do not mark individual
unverified acceptance rows PASS. Do not edit the concurrent CSV without its
owner. Freeze means normal core semantics do not change during separation;
core or security fixes still need a separate evidence/authorization process.

New work belongs on a separation branch. Use the existing v3 Web Host Bridge,
Skin Studio and canonical Clear source as the first migration target, not a
second store/client/scheduler. No direct skin access to credentials/database
or untrusted authorization. Existing Native must remain available until
all functional parity, WebView/device tests and an independent factory-cutover
authorization have passed. No default switch or native UI removal was approved.

For current source-level evidence and unresolved work, read
`docs/SKIN_SEPARATION_PARITY_MATRIX_2026-10-27.md` and the latest entries in
`docs/agent-handoff/PROGRESS.md`. Studio uses `python tools/skin-studio/skin_studio.py serve`;
run `python -m unittest discover -s tools/skin-studio/tests -v`,
`python tools/skin-studio/build_default_clear.py --verify`, and the relevant
browser / Android checks for each change. Mock previews are not device evidence.
For machine-operated v3 skin work, read `docs/SKIN_STUDIO_AI_AUTOMATION_V1.md` and use
`skin_studio.py inspect-v3`, `read-v3`, and `edit-v3`; these validate and export to a
new `.pcskin` path but do not prove visual or device parity.
