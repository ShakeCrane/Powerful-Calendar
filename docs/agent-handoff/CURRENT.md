PROJECT: Powerful-Calendar
PLAN: PLAN-2026-11-03-R2
BRANCH: codex/skin-single-default-closure
BASELINE: 3b54097d6383c0349b5d3d638c228320e3c8e22c
CURRENT_TASK: S-02-ANDROID-GATE
TASK_STATUS: IN_PROGRESS
LAST_COMPLETED_TASK: F-03-CLOSURE (PASS)
LAST_COMPLETED_COMMIT: 2a8c323890c0f121b80c72a2a4d1c5c2a35d010d
NEXT_TASK: S-03
NEXT_ACTION: Complete the Android runtime gate for the previously completed S-02 Blank-v3 Studio GUI; do not mark it PASS without Android WebView evidence.

WAVE_2_QUEUE:
  - F-03 Closure: PASS
  - S-02 Android Gate: IN_PROGRESS
  - S-03 Host state/action binding catalog: NOT STARTED
  - F-05 editor/draft/trusted-write parity: NOT STARTED
  - F-06 recurrence scopes/date/DST parity: NOT STARTED
  - S-04 Studio preview/runtime parity: NOT STARTED
  - S-05 resource/source round-trip: NOT STARTED
  - F-07 AI chat/proposal/review parity: NOT STARTED

DEFERRED_TASKS: R-06 and R-07 remain DEFERRED until dependent Skin parity is proven.
PHASE: F/S Wave 2
SEP_SIGNOFF: INCOMPLETE (historical parity remains unsigned)
PRODUCT_CUTOVER: HOLD

OWNER_GOAL:
  Core: Owns business behavior, data, transactions, reminders, AI, permissions, and stable feature APIs.
  Skin: Owns normal presentation and requests Core capabilities through approved APIs.
  Default_skin: One unbeautified factory .pcskin; Native remains the independent safety recovery path.
  Studio: Create a complete skin from a blank project, including components, bindings, assets, editing, preview, export, import, and Android runtime.
  Beautification: Only after separation acceptance, apply Claude design work through Studio without changing Core.

RUNTIME_POLICY:
  Factory_skin: powercal-clear-default.pcskin (id powercal-clear-default).
  Native_recovery: powercal-default; R-06/R-07 remain deferred.
  No second factory skin, multi-skin store, or production cutover is authorized.

OWNER_OVERRIDE_2026_11_03:
  Legacy_package_policy: R-03 cleanup was limited to isolated QA app-private installed packages; do not archive/export legacy packages or preserve a daily multi-skin list.
  Protected: Calendar Store, drafts, reminders, AI history, Studio source projects, design sources, fixtures, external SAF files, and Git Worktrees.
  Release_gate: Production Cutover remains HOLD.

PROTECTED_ASSETS:
  - docs/agent-handoff/WORKTREE_DISPOSITION.md
  - Existing protected Worktrees, user files, Studio source projects, design sources, fixtures, and external SAF documents.

LATEST_VERIFIED_EVIDENCE:
  F-03_CLOSURE:
    status: PASS
    commit: 2a8c323890c0f121b80c72a2a4d1c5c2a35d010d
    test_fix: SystemCalendarImportTest waits for the Host terminal notice after the async import's final reload/reconcile before opening a separate SQLite connection; all Store assertions remain.
    jvm: 967/967; 140 XML suites; 0 failures/errors/skips. SystemCalendarImportTest 4/4; WebSkinHostProjectionTest 24/24.
    gradle: Both source boundaries, Debug APK, lint (0 errors/56 warnings), and AndroidTest Kotlin compile PASS.
    android: API 35 AVD PowerCal_Wave2_QA35_20261104, emulator-5554; Native/Clear month parity 3/3, two independent prepare/force-stop/restore cycles, WebView recurrence open/single-scope 1/1 PASS.
    apk_sha256: F5E5FA6FE4194AD213EF60D5933018422A818D0AFB97E7B68FD223B3F54E3522
    default_pcskin_sha256: 299e4bdd78c8936f6c15db0200172e659aaa5b0e0643f0d70c115966ac35a9f4
    evidence: .local/android-test/reports/F-03-20261104-wave2/
    remaining: Physical/OEM NOT TESTED; SSA-03 remains PARTIAL.
  S-02_STUDIO_GUI:
    status: PASS (prior task; not the Android gate)
    commit: d0569f14d383d4cc7bd8b5bef2a922008969de4c
    evidence: .local/android-test/reports/S-02-20261104/
    remaining: Generated package's Android WebView execution is the current task.

BLOCKERS:
  - S-02 Android Gate must prove all five generated pages/components render after export/import and cold start on isolated API 35.
  - The Android SDK had no registered AVD at task start; a new isolated API 35 AVD is now available and verified.
  - Physical/OEM evidence remains NOT TESTED.

AUTHORITATIVE_PLAN: docs/POWERCAL_SINGLE_DEFAULT_SKIN_EXECUTION_PLAN_2026-11-03_R2.md
ACCEPTANCE_MATRIX: docs/agent-handoff/SINGLE_SKIN_ACCEPTANCE.md
