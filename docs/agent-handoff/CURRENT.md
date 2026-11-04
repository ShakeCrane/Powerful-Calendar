PROJECT: Powerful-Calendar
PLAN: PLAN-2026-11-03-R2
BRANCH: codex/skin-single-default-closure
WAVE2_START_HEAD: 3b54097d6383c0349b5d3d638c228320e3c8e22c
LATEST_CODE_HEAD: 62334dce69ac25c316b5bc39497f08adbc20bd16
CURRENT_TASK: S-03
TASK_STATUS: IN_PROGRESS
LAST_COMPLETED_TASK: S-02-ANDROID-GATE (PASS)
LAST_COMPLETED_COMMIT: 62334dce69ac25c316b5bc39497f08adbc20bd16
NEXT_TASK: F-05
NEXT_ACTION: Build the machine-readable catalog from the existing real Web Host state projections and actions; keep Studio and Runtime names, schemas, capability requirements, and error/result semantics identical.

WAVE_2_QUEUE:
  - F-03 Closure: PASS (commit 2a8c323890c0f121b80c72a2a4d1c5c2a35d010d)
  - S-02 Android Gate: PASS (commit 62334dce69ac25c316b5bc39497f08adbc20bd16)
  - S-03 Host state/action binding catalog: IN_PROGRESS
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
  Release_gate: Product Cutover remains HOLD.

LATEST_VERIFIED_EVIDENCE:
  S-02:
    studio_browser: five routes, all 13 components, project tree/DOM parity, save/reopen, package export/import, relative PNG resource PASS.
    studio_python: 70/70 PASS.
    android: API 35 AVD PowerCal_Wave2_QA35_20261104 / emulator-5554; single, prepare, cold restore each 1/1 PASS; Store snapshot unchanged; host.read only.
    jvm: 967/967, 140 XML suites, 0 failures/errors/skips.
    boundaries: skin and native source checks PASS.
    lint: 0 errors, 56 warnings.
    factory_package_sha256: 299e4bdd78c8936f6c15db0200172e659aaa5b0e0643f0d70c115966ac35a9f4 (one package in production APK, byte-identical to source).
    test_package_sha256: 751e1f2cd59f795a66045564e500f42f25b58a2a18a9e822f396cca3b398cfda (AndroidTest only).
    apk_sha256: f5e5fa6fe4194ad213ef60d5933018422a818d0afb97e7b68fd223b3f54e3522.
    evidence: .local/android-test/reports/S-02-20261104-wave2/manifest.md
    limits: physical/OEM NOT TESTED; S-03 action bindings and full functional parity not covered.
  F-03:
    status: PASS
    commit: 2a8c323890c0f121b80c72a2a4d1c5c2a35d010d
    evidence: .local/android-test/reports/F-03-20261104-wave2/

BLOCKERS: None for S-03. Physical/OEM evidence remains NOT TESTED; Product Cutover remains HOLD.

AUTHORITATIVE_PLAN: docs/POWERCAL_SINGLE_DEFAULT_SKIN_EXECUTION_PLAN_2026-11-03_R2.md
ACCEPTANCE_MATRIX: docs/agent-handoff/SINGLE_SKIN_ACCEPTANCE.md
PROTECTED_ASSETS:
  - docs/agent-handoff/WORKTREE_DISPOSITION.md
  - Existing protected Worktrees, user files, Studio projects, design sources, and test fixtures.
