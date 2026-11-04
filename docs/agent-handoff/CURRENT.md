PROJECT: Powerful-Calendar
PLAN: PLAN-2026-11-03-R2
BRANCH: codex/skin-single-default-closure
WAVE2_START_HEAD: 3b54097d6383c0349b5d3d638c228320e3c8e22c
WAVE3_START_HEAD: 82b456fd2bfc15df379fd15d967fac3da57b899d
LATEST_CODE_HEAD: 0cfd15564189f9c0e9e3c0436441cd084183022f
LATEST_VERIFIED_HEAD: 96fc4bde3836724d1de88c36aa7de5e3699a8dd1
CURRENT_TASK: R-06
TASK_STATUS: READY
LAST_COMPLETED_TASK: FUNCTIONAL-PREVIEW-CHECKPOINT-A (DELIVERED)
LAST_COMPLETED_COMMIT: 96fc4bde3836724d1de88c36aa7de5e3699a8dd1 (APK build baseline)
LAST_ATTEMPTED_TASK: F-09 TalkBack follow-up (BLOCKED; TalkBack traversal NOT TESTED)
LAST_ATTEMPTED_COMMIT: 3932427ac7c8a06d1dabb53b732fef693954efdd
NEXT_TASK: R-06
NEXT_ACTION: R-06 runtime convergence. TalkBack traversal remains NOT TESTED and is a release-quality gap; per Owner's 2026-11-05 delivery decision it does not block the Functional Preview or independent runtime convergence. Keep Product Cutover HOLD.
PRODUCT_DELIVERY_CHECKPOINT_A:
  status: DELIVERED (PowerCal Functional Preview; Debug only)
  apk: dist/powercal/PowerCal-Functional-Preview-2026-11-05.apk
  apk_sha256: 2ff316e1e350fdd4573ceac7ab154b616fb2e425ef209bde0d2b3e79d89b33a6
  default_skin_sha256: b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3
  build_head: 96fc4bde3836724d1de88c36aa7de5e3699a8dd1
  smoke: API 35 emulator, 11 focused Android instrumentation assertions passed; 970 JVM tests passed
  evidence: .local/android-test/reports/functional-preview-20261105/
  limitations: TalkBack traversal NOT TESTED; physical/OEM and live AI provider NOT TESTED
WAVE2_STATUS: COMPLETE (8 authorized work packages attempted; all PASS)
WAVE3_STATUS: IN_PROGRESS (F-08 PASS; F-09 code/device flows PASS but TalkBack traversal NOT TESTED; S-06/S-07 PASS; Checkpoint A delivered; R-06 next; Product Cutover HOLD)

WAVE3_QUEUE:
  - F-08 permissions/reminders/ICS/backup/system settings: PASS (commit c3a1e59f44e2b304314673f0030ae19c2f3f9d28)
  - F-09 Back/IME/recovery/accessibility: BLOCKED (code/test fixes committed; TalkBack traversal order not verified)
  - S-06 from-blank complete Skin: PASS (commit 03c8ba38db785b16052a2f9b440b8bf3bc5ee79b; Studio GUI/API, 88/88 Python, Android 4/4, MainActivity force-stop/restore verified)
  - S-07 default Skin Studio roundtrip: PASS (commit 0cfd15564189f9c0e9e3c0436441cd084183022f; Studio save/reopen, Settings source edit, export/import/restore, 88/88 Python, final APK byte check, API 35 WebView Settings 1/1)
  - R-06 runtime retirement: NEXT / READY under Owner 2026-11-05 delivery priority; F-09 TalkBack traversal remains unverified and release-relevant but does not block Functional Preview or independent work
  - R-07 runtime/recovery gate: CONDITIONAL; requires R-06 prerequisites and safe recovery evidence
  - Q-01 install/migration acceptance: NOT STARTED
  - Q-02 full parity replay: NOT STARTED

WAVE_2_QUEUE:
  - F-03 Closure: PASS (commit 2a8c323890c0f121b80c72a2a4d1c5c2a35d010d)
  - S-02 Android Gate: PASS (commit 62334dce69ac25c316b5bc39497f08adbc20bd16)
  - S-03 Host state/action binding catalog: PASS (commit 5eeb87d091e84bfd0906135497bc4ebca94cffba)
  - F-05 editor/draft/trusted-write parity: PASS (commit bb2007d69d90204acf0e27deb325a492f9c37746)
  - F-06 recurrence scopes/date/DST parity: PASS (commit 177ec0b733952ba56d5c13542e97ead08489d1d9)
  - S-04 Studio preview/runtime parity: PASS (commit f051ca04e743b3cdbb315874150e2df36b490ae0)
  - S-05 resource/source round-trip: PASS (commit a008f20771062e2d037a9f810435e5328b70bd46)
  - F-07 AI chat/proposal/review parity: PASS (commit 25711d3569edfd59c23c774c3bb371fbbc192044)

DEFERRED_TASKS: R-06 and R-07 remain DEFERRED until dependent Skin parity is proven.
PHASE: F/S Wave 3
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
  S-07:
    status: PASS (Studio roundtrip task scope; Product Cutover and full SEP sign-off remain HOLD/INCOMPLETE)
    commit: 0cfd15564189f9c0e9e3c0436441cd084183022f
    studio: Canonical default Clear imported through Studio GUI/API; one harmless Settings label edit was saved, closed/reopened, built/exported, and re-imported. Restoring the original label restored every one of the 9 package source/resource files byte-for-byte; normalized manifest and project metadata matched. Studio Python 88/88; browser E2E and Node syntax PASS.
    android: API 35 isolated AVD PowerCal_Wave2_QA35_20261104 / emulator-5554; `ClearSettingsOptionsInstrumentedTest.clearSettingsRetireSkinCatalogueAndKeepProtectedOperations` PASS 1/1 on the final rebuilt APK. Default Clear Settings, protected operations, notification denial, backup chooser cancellation, and cancellation/retired-action Store invariants verified.
    package: official builder regenerated the canonical package unchanged; APK contains exactly one `.pcskin` (`powercal-clear-default.pcskin`), source/APK asset bytes identical, SHA-256 b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3.
    apk_sha256: 2ff316e1e350fdd4573ceac7ab154b616fb2e425ef209bde0d2b3e79d89b33a6.
    gradle: Full gate PASS; `testDebugUnitTest`, assemble, lint, and AndroidTest Kotlin compile were UP-TO-DATE, so JVM tests were not rerun for S-07. APK was then forced to rebuild (83/83 tasks), and the focused Android test was rerun on that APK.
    evidence: .local/android-test/reports/S-07-20261104/manifest.md
    limits: Studio's re-exported ZIP has a different raw SHA because manifest `sha256` keys serialize in a different order; semantic manifest and every source/resource file match. The canonical source builder and APK asset are byte-identical. No product, Core, Clear source, or APK asset was changed.
  S-06:
    status: PASS (task scope; Product Cutover and full SEP sign-off remain HOLD/INCOMPLETE)
    commit: 03c8ba38db785b16052a2f9b440b8bf3bc5ee79b
    studio: Blank v3 GUI/API created and rebound 23 components across five routes; save/close/reopen/re-edit/build/export/formal-validate/import/re-edit PASS; project contains no Clear source; Python 88/88; browser E2E PASS; JS syntax checks PASS.
    android: API 35 isolated AVD PowerCal_Wave2_QA35_20261104 / emulator-5554; final AndroidTest class 4/4, 0 failures/errors/skips. Five-page interactions, four event kinds, editor cancel/save, delete confirmation, recurrence scopes, Memo, AI Stub/review, permission denial, file-picker cancel, and Store invariants verified.
    cold_start: Normal MainActivity selected powercal-from-blank-functional and hosted one WebView; app PID 6399 was force-stopped and absent; restore instrumentation reloaded the package in PID 6552, rendered Month, and accepted touch. Test-owned app-private package and preference state were restored after the demo.
    packages: AndroidTest-only Skin SHA-256 e810bc0897cd3b35985067dd23c077394c0a131381795fd8136c616db71751b7. Production APK contains exactly powercal-clear-default.pcskin; source/APK hash b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3, unchanged.
    apk_sha256: 4baebe7a02073cfde91e87e117c1a3f1305cac69822a3836acbbcbbd288e37f5.
    evidence: .local/android-test/reports/S-06-20261104/manifest.md
    limits: physical/OEM behavior not tested; this proves an isolated authored Skin, not complete SEP sign-off. Product Cutover remains HOLD.
  F-09:
    status: BLOCKED; code/runtime checks are PASS, but the required TalkBack traversal gate remains open.
    commit: 3932427ac7c8a06d1dabb53b732fef693954efdd
    jvm: 970/970, 140 XML suites, 0 failures/errors/skips.
    android: API 35 isolated AVD PowerCal_Wave2_QA35_20261104 / emulator-5554; 11 unique instrumentation methods passed after applying required font/animation settings; extra 360dp and 288dp responsive runs passed.
    back_recovery: Page Back and clean editor close preserve Store snapshots; dirty editor leaves one pending draft without formal writes; JS/renderer failures retain draft and Native recovery remains available.
    accessibility: Follow-up on API 35 AVD with the actual Clear WebView (APK 2ff316e1e350fdd4573ceac7ab154b616fb2e425ef209bde0d2b3e79d89b33a6): TalkBack bound and touch exploration enabled. Touch moved accessibility highlight to the Timeline tab; horizontal right and left ADB swipe probes left the highlight on Timeline. The same ADB swipe did not advance focus on the TalkBack settings screen (after initial focus landed on Navigate up, a second swipe stayed there), so the injected gesture path is not a valid traversal oracle. Clear's TalkBack traversal order remains NOT TESTED and blocks F-09. TalkBack settings restored to null/0/0.
    followup_evidence: .local/android-test/reports/F-09-20261104/talkback-followup-20261104.txt; screenshots and hierarchy in the same directory; focused factory boot instrumentation PASS 1/1.
    boundaries: Skin/Native source checks, APK assemble, Lint, and AndroidTest Kotlin compile PASS.
    apk_sha256: 4baebe7a02073cfde91e87e117c1a3f1305cac69822a3836acbbcbbd288e37f5.
    factory_package: exactly one `.pcskin`; source/APK byte-identical; SHA-256 b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3.
    evidence: .local/android-test/reports/F-09-20261104/manifest.md; issue reports: docs/issues/PC-ISSUE-SEPARATION-F09-*.md.
  F-08:
    status: PASS (task scope); SSA-09 and SSA-10 remain PARTIAL; Product Cutover remains HOLD.
    commit: c3a1e59f44e2b304314673f0030ae19c2f3f9d28
    jvm: focused settings/import-export group 69/69; full suite 970/970 across 140 suites, 0 failures/errors/skips.
    android: API 35 isolated AVD PowerCal_Wave2_QA35_20261104 / emulator-5554; 13/13 final targeted instrumentation methods passed across permission, reminder, calendar, ICS, backup, rejection, and protected settings paths.
    reminder: exact-alarm state/settings/reconcile passed; actual AlarmManager delivery reached receiver, recorded FIRED and posted QA notification.
    import_export: ICS review drafts/export, synthetic system-calendar import parity, backup export/empty-Store restore and refusal/cancel zero-write paths passed.
    boundaries: Skin and Native source checks PASS; APK assemble PASS; lint 0 errors / 56 warnings; AndroidTest Kotlin compile PASS.
    apk_sha256: 08739af8ec4967c764e738e20885a2fc8c1c96033f4e33a3414643b48422d16e.
    factory_package: exactly one `.pcskin`; source/APK byte-identical, SHA-256 299e4bdd78c8936f6c15db0200172e659aaa5b0e0643f0d70c115966ac35a9f4.
    evidence: .local/android-test/reports/F-08-20261104/manifest.md
    limits: physical/OEM alarm delivery, notification grant via Skin request, ICS picker cancellation, reminder reboot/time-zone/DST/grace-boundary NOT TESTED; unrelated AI history-order assertion observed during initial broad run remains outside F-08.
  F-07:
    status: PASS (task scope); Product Cutover and full SEP sign-off remain HOLD/INCOMPLETE.
    commit: 25711d3569edfd59c23c774c3bb371fbbc192044
    jvm: 970/970 across 140 suites, 0 failures/errors/skips; focused AI set 106/106.
    android: API 35 isolated AVD PowerCal_Wave2_QA35_20261104 / emulator-5554; proposal/review WebView suite 9/9, multi-turn workflow 1/1, chat controls 1/1 in each of two process cycles.
    chat: real Clear WebView touchscreen actions edited/resubmitted and retracted turns; cycle 1 restored the two-message final transcript with 0 provider replays; Core Store snapshots unchanged.
    ai: deterministic loopback Stub only; CREATE/STAGE/REPLACE/DELETE review and attachment authorization covered by the 9 passing WebView cases; live provider behavior NOT TESTED.
    boundaries: Skin and Native source checks PASS; APK assemble PASS; lint 0 errors / 56 warnings; AndroidTest Kotlin compile PASS.
    apk_sha256: 08739af8ec4967c764e738e20885a2fc8c1c96033f4e33a3414643b48422d16e; AndroidTest APK SHA-256 4a1d87d8e7358503894016d41aaac7f1903348060aa6951c019bc63ccd82dfb0.
    factory_package: exactly one .pcskin in APK, source/APK byte-identical, SHA-256 299e4bdd78c8936f6c15db0200172e659aaa5b0e0643f0d70c115966ac35a9f4.
    evidence: .local/android-test/reports/F-07-20261104/manifest.md
    limits: physical/OEM and live AI provider NOT TESTED; MainActivity-specific cold launch not separately exercised (the fresh-process test re-created PowerCalApp and its real Clear WebView Host); Product Cutover remains HOLD.
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
  S-04:
    status: PASS (task scope); SSA-12 and SSA-13 remain PARTIAL.
    code_commit: f051ca04e743b3cdbb315874150e2df36b490ae0
    studio: Python 78/78; Edge 156.0.4314.58 browser E2E PASS across fixed page fixtures, formal browseMonth, capability matrix, CSP/MIME/fonts, resource errors, and source/package roundtrip.
    android: API 35 isolated AVD PowerCal_Wave2_QA35_20261104 / emulator-5554; resource parity 1/1, capability gate 1/1, Studio-exported package browseMonth trusted-touch 1/1 with Store count unchanged, shared Native/Memo fixture regression 1/1.
    package: AndroidTest-only Studio export SHA-256 267383470e215659144b1a302ee57b5daea8721bbc240cc762c87699c833d74f; not in production app assets.
    production: APK SHA-256 08739af8ec4967c764e738e20885a2fc8c1c96033f4e33a3414643b48422d16e; exactly one factory .pcskin, source/APK byte-identical, SHA-256 299e4bdd78c8936f6c15db0200172e659aaa5b0e0643f0d70c115966ac35a9f4.
    security: Preview remains a non-writing Mock; Android's formal capability bridge intentionally has no denial acknowledgement. Preview bridgeLog is diagnostic-only; unauthorized Android callbacks were not invoked and Store state stayed unchanged.
    evidence: .local/android-test/reports/S-04-20261104-wave2/manifest.md
    limits: physical/OEM NOT TESTED; complete blank-project authoring and full SEP signature remain incomplete; Product Cutover remains HOLD.
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
  S-05:
    status: PASS (task scope); global SEP signoff remains incomplete.
    code_commit: a008f20771062e2d037a9f810435e5328b70bd46
    studio_python: 85/85; browser blank-project/resource roundtrip PASS; JS syntax and Python compilation PASS.
    android: API 35 isolated AVD; generated read-only package WebView/DOM/resources PASS 1/1; prepare/force-stop/restore PASS 1/1 each; Core Store snapshots unchanged.
    studio_package_sha256: 7ffaf4a8ee526c56afb18fd2a2b38760193bbf3daf22879db736d5472865bd8e
    android_read_only_package_sha256: 1f7c1053022342d3ab78c4f549fc2719f211e616b256f761c907cdfabb79f492
    android_test_apk_sha256: 2ec5efb670b6505b1fc96723b55499c7dd866b82ae4ef02be57eefab08e20aad
    app_apk_sha256: 08739af8ec4967c764e738e20885a2fc8c1c96033f4e33a3414643b48422d16e
    factory_package: exactly one production .pcskin; powercal-clear-default SHA-256 299e4bdd78c8936f6c15db0200172e659aaa5b0e0643f0d70c115966ac35a9f4; unchanged.
    evidence: .local/android-test/reports/S-05-20261104/manifest.md
    limits: physical/OEM NOT TESTED; Product Cutover remains HOLD.
  F-03:
    status: PASS
    commit: 2a8c323890c0f121b80c72a2a4d1c5c2a35d010d
    evidence: .local/android-test/reports/F-03-20261104-wave2/

BLOCKERS: F-09 cannot PASS until actual TalkBack focus traversal order is directly verified. R-06/R-07 remain deferred until their required parity/recovery gates are satisfied. Physical/OEM and live-provider behavior remain NOT TESTED; full SEP signature remains INCOMPLETE; Product Cutover remains HOLD.

AUTHORITATIVE_PLAN: docs/POWERCAL_SINGLE_DEFAULT_SKIN_EXECUTION_PLAN_2026-11-03_R2.md
ACCEPTANCE_MATRIX: docs/agent-handoff/SINGLE_SKIN_ACCEPTANCE.md
PROTECTED_ASSETS:
  - docs/agent-handoff/WORKTREE_DISPOSITION.md
  - Existing protected Worktrees, user files, Studio projects, design sources, and test fixtures.
