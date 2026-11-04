PROJECT: Powerful-Calendar
PLAN: PLAN-2026-11-03-R2
BRANCH: codex/skin-single-default-closure
WAVE2_START_HEAD: 3b54097d6383c0349b5d3d638c228320e3c8e22c
LATEST_CODE_HEAD: 177ec0b733952ba56d5c13542e97ead08489d1d9
CURRENT_TASK: S-04
TASK_STATUS: IN_PROGRESS
LAST_COMPLETED_TASK: F-06-RECURRENCE-SCOPES-DATE-DST-PARITY (PASS)
LAST_COMPLETED_COMMIT: 177ec0b733952ba56d5c13542e97ead08489d1d9
NEXT_TASK: S-05
NEXT_ACTION: Compare Studio preview bridge outputs and failure/capability behavior with Android WebView Runtime using the S-03 binding catalog and fixed page fixtures; prove preview performs no formal Store writes.

WAVE_2_QUEUE:
  - F-03 Closure: PASS (commit 2a8c323890c0f121b80c72a2a4d1c5c2a35d010d)
  - S-02 Android Gate: PASS (commit 62334dce69ac25c316b5bc39497f08adbc20bd16)
  - S-03 Host state/action binding catalog: PASS (commit 5eeb87d091e84bfd0906135497bc4ebca94cffba)
  - F-05 editor/draft/trusted-write parity: PASS (commit bb2007d69d90204acf0e27deb325a492f9c37746)
  - F-06 recurrence scopes/date/DST parity: PASS (commit 177ec0b733952ba56d5c13542e97ead08489d1d9)
  - S-04 Studio preview/runtime parity: IN_PROGRESS
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
  F-06:
    status: PASS (task scope); SSA-06 remains PARTIAL.
    commit: 177ec0b733952ba56d5c13542e97ead08489d1d9
    jvm: RecurrenceFeatureApiV1Test 9/9; WebSkinRecurrenceBridgeTest 16/16; full suite 970/970 across 140 suites, 0 failures/errors/skips.
    android: API 35 isolated AVD PowerCal_Wave2_QA35_20261104 / emulator-5554; Native/Clear trusted-touch parity 1/1 method and six scopes (ALARM and date-only DEADLINE × Single/This-and-future/Whole); cancellation had zero Store writes and approved outcomes matched.
    dst: API and Bridge JVM tests select the earlier New York fall-fold occurrence (2026-11-01 01:30 => 05:30Z, -04:00); date-only Deadline retains local date and null instant deadline.
    boundaries: Skin and Native source checks PASS; APK assemble PASS; lint 0 errors / 56 warnings; AndroidTest Kotlin compile PASS.
    apk_sha256: 08739af8ec4967c764e738e20885a2fc8c1c96033f4e33a3414643b48422d16e; exactly one factory .pcskin, source/APK byte-identical, sha256 299e4bdd78c8936f6c15db0200172e659aaa5b0e0643f0d70c115966ac35a9f4.
    evidence: .local/android-test/reports/F-06-20261104-wave2/manifest.md
    limits: device DST fold/gap and date boundaries, Studio recurrence authoring, physical/OEM NOT TESTED; Product Cutover remains HOLD.
  F-05:
    status: PASS
    commit: bb2007d69d90204acf0e27deb325a492f9c37746
    android: API 35 isolated AVD; WebSkinAllKindsTrustedWrite 17/17, WebSkinFormalDeleteTouch 3/3, NewEventEditorLifecycle 2/2, process prepare/force-stop/restore 1+1; 0 final failures/skips.
    process_recovery: PID 14276 -> force-stop dev.powerfulcalendar.test -> PID 14559; process token changed; synthetic UUID database and marker removed by successful restore test.
    jvm: WebSkinEditBridgeTest 49/49; full gate 967/967 across 140 suites, 0 failures/errors/skips.
    boundaries: skin and native source checks PASS; APK assemble PASS; lint 0 errors / 56 warnings; AndroidTest Kotlin compile PASS.
    apk_sha256: 08739af8ec4967c764e738e20885a2fc8c1c96033f4e33a3414643b48422d16e.
    factory_package: exactly one .pcskin in APK; source and APK bytes match; sha256 299e4bdd78c8936f6c15db0200172e659aaa5b0e0643f0d70c115966ac35a9f4.
    store: cancellation/conflict caused no unauthorized formal write; approved paths wrote only expected isolated fixture rows; process-recovery fixture removed.
    root_cause: same-revision calendar-window cache remained stale after a concurrent Store writer; conflict-close now queries an Event API snapshot through the existing Calendar API snapshot overload.
    evidence: .local/android-test/reports/F-05-20261104-wave2/manifest.md
    limits: physical/OEM NOT TESTED; SSA-05 and SSA-07 remain PARTIAL because Studio/remaining acceptance coverage is incomplete; Product Cutover remains HOLD.
  S-03:
    catalog: 36 actual Host actions, 26 edit command names, 45 state paths; source-linked JSON catalog and strict binding validation PASS.
    studio: Python 78/78 PASS; blank-project browser E2E and Clear bridge authoring E2E PASS; Preview updates bound camera for panTimeline then today/local-noon/24h.
    android: API 35 AVD PowerCal_Wave2_QA35_20261104 / emulator-5554; 1/1 PASS, 0 skipped; MainActivity Host updated camera, read-only createDraft was rejected, Store snapshot unchanged.
    jvm: 967/967, 140 XML suites, 0 failures/errors/skips.
    boundaries: skin and native source checks PASS; lint 0 errors / 56 warnings; AndroidTest Kotlin compile PASS.
    apk_sha256: f5e5fa6fe4194ad213ef60d5933018422a818d0afb97e7b68fd223b3f54e3522 (unchanged); production APK contains only powercal-clear-default.pcskin, sha256 299e4bdd78c8936f6c15db0200172e659aaa5b0e0643f0d70c115966ac35a9f4.
    evidence: .local/android-test/reports/S-03-20261104-wave2/manifest.md
    limits: physical/OEM NOT TESTED; Product Cutover remains HOLD.
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

BLOCKERS: None for F-06 task scope. S-04 is now in progress. Physical/OEM and full SEP signature remain NOT TESTED/INCOMPLETE; R-06/R-07 remain DEFERRED and Product Cutover remains HOLD.

AUTHORITATIVE_PLAN: docs/POWERCAL_SINGLE_DEFAULT_SKIN_EXECUTION_PLAN_2026-11-03_R2.md
ACCEPTANCE_MATRIX: docs/agent-handoff/SINGLE_SKIN_ACCEPTANCE.md
PROTECTED_ASSETS:
  - docs/agent-handoff/WORKTREE_DISPOSITION.md
  - Existing protected Worktrees, user files, Studio projects, design sources, and test fixtures.
