PROJECT: Powerful-Calendar
PLAN: PLAN-2026-11-03-R2
BRANCH: codex/skin-single-default-closure
# Canonical current fields are the top-level values here; nested handoff entries are historical evidence snapshots, not runnable NEXT_ACTION instructions.
OWNER_VISUAL_BASELINE: frozen native PowerCal 0.9.5-rc.1 (2026-10-27); supersedes current Clear appearance and unapproved Claude concepts
OWNER_VISUAL_SOURCE_SHA: b1f7629f0c52812e1887443872f43aa6c49908c4
OWNER_VISUAL_REFERENCE_APK: .local/android-test/artifacts/physical-qa/PowerCal-0.9.5-rc.1-b1f7629-debug-core-HOLD.apk
OWNER_VISUAL_REFERENCE_APK_SHA256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
WAVE2_START_HEAD: 3b54097d6383c0349b5d3d638c228320e3c8e22c
WAVE3_START_HEAD: 82b456fd2bfc15df379fd15d967fac3da57b899d
LATEST_CODE_HEAD: e9ec15e113025cb1f819e5d37b3eba7cd49c9e4d (Native 0.9.5 dual-wing initial-edge instrumentation and Browser E2E; product UI source unchanged)
LATEST_VERIFIED_HEAD: e9ec15e113025cb1f819e5d37b3eba7cd49c9e4d (serial Gradle PASS; 988/988 JVM; source boundaries, assemble, lint and AndroidTest compile PASS; frozen Native and current Web API 35 dual-wing tests 1/1 each; official package verification and full Browser E2E PASS)
CURRENT_TASK: NATIVE_095_VISUAL_PARITY
TASK_STATUS: IN_PROGRESS
PRODUCT_CUTOVER: HOLD
TASK_BASE_HEAD: 05514a12c3cf6c0b62bfe2daad04fbbdf433097f
WORKING_APK_SHA256: 3E95E2992AD89EAE7BC8C0B59084F39504A27432A3533532BB78B1D6AD58EA56
WORKING_DEFAULT_SKIN_SHA256: dd49636784dfab7ee356a114d3d4db0963087f37a4e1f0bc3ce505b766f9d14e
WORKING_DEFAULT_SKIN_ID: powercal-clear-default
NATIVE_VISUAL_PARITY_AUDIT: docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md (inventory complete; pinch and dual-wing initial-edge slices narrow PASS; full page parity remains IN PROGRESS)
PREVIOUS_TASK_STATE: Q-05 single-default startup migration and protected-branch cutover (PASS; public release remains HOLD)
LAST_COMPLETED_TASK: Native 0.9.5 dual-wing initial-edge parity verification (narrow PASS; full Timeline and Draft Deck remain PARTIAL)
LAST_COMPLETED_COMMIT: e9ec15e113025cb1f819e5d37b3eba7cd49c9e4d
LAST_ATTEMPTED_TASK: PowerCal Functional Preview artifact and main-flow smoke (DELIVERED for isolated QA; AI chat send NOT TESTED after two identical trusted-touch automation failures)
LAST_ATTEMPTED_COMMIT: 05514a12c3cf6c0b62bfe2daad04fbbdf433097f (APK build baseline)
NEXT_TASK: NATIVE_095_TIMELINE_DATE_DEADLINE_PARITY
NEXT_ACTION: Compare the date-only Deadline date-band chip and its 23:59 timeline anchor on the frozen Native APK and current Web v3 with the same API 35 fixture. Android A/B is NOT TESTED in the just-completed dual-wing slice; Studio Mock evidence is not device evidence. Then continue the PARTIAL Draft Deck empty/transient, AI-locked, restore/conflict, delete-confirmation, completion/undo and IME/accessibility states.
FUNCTIONAL_PREVIEW_DELIVERY:
  status: DELIVERED (isolated QA Functional Preview; not a public release and not full Native 0.9.5 parity)
  apk: dist/powercal/PowerCal-Functional-Preview-2026-11-07-05514a12.apk
  apk_sha256: CF310827CA4C3A7989DB79A5D35F6F2755F2AD01E7601D28B40CA271C74492CB
  apk_bytes: 67983743
  build_source_commit: 05514a12c3cf6c0b62bfe2daad04fbbdf433097f
  package: dev.powerfulcalendar / 0.9.5-rc.1 / 905 / Debug
  default_skin: powercal-clear-default; one .pcskin; source/APK SHA-256 78C13F92BB2878A559CCD57FE64FEA7F22D2137460620139C69155C7CBF5BB7E
  device: PowerCal_Wave2_QA35_20261104 / emulator-5556 / API 35 / WebView 124.0.6367.219
  verified: Gradle 987/987; Studio Python 90/90; binding catalog 9/9; all-kind create/edit, recurrence scopes, Memo paging/completion and two-phase process recovery instrumentation passed; Timeline/Month/Settings navigation observed
  limitation: AI page entry only; chat-send instrumentation failed twice before Host request at trusted #chatSend pointerdown; no live provider used. Import/export, permission change, OEM reminder delivery, TalkBack and physical-device tests not rerun.
  evidence: dist/powercal/PowerCal-Functional-Preview-2026-11-07.md; .local/android-test/reports/functional-preview-20261107/manifest.md
LATEST_SLICE_EVIDENCE:
  NATIVE_095_TIMELINE_INITIAL_LEFT:
    status: NARROW PASS (frozen runtime and Web both start at outer lane edge; product UI source unchanged; Timeline remains PARTIAL)
    code_commit: e9ec15e113025cb1f819e5d37b3eba7cd49c9e4d
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    original_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    current_apk_sha256: 3E95E2992AD89EAE7BC8C0B59084F39504A27432A3533532BB78B1D6AD58EA56
    default_skin_sha256: dd49636784dfab7ee356a114d3d4db0963087f37a4e1f0bc3ce505b766f9d14e (10 files / 420396 bytes; one package; source/APK bytes match)
    android: Native reference PowerCal_Native095_MenuReference_20261107 / emulator-5560; Web QA PowerCal_Wave2_QA35_20261104 / emulator-5556; API 35, 1080x2400, 420 dpi, GMT; WebView 124.0.6367.219
    geometry: Native left lane-3 x=32..253 px, right lane-1 x=824..1045 px; Web converted x=31.46..251.65 and 823.36..1043.55 px; initial scroll=0 on both wings in both runtimes; vertical edge delta about 5.36 px is outside this narrow horizontal signoff
    store: Native baseline 3 drafts / 8 commits / 11 identities and isolated Web fixture 0 drafts / 8 commits / 8 identities remained unchanged during event open/close and independent wing scrolling
    tests: serial Gradle 988/988 JVM; boundaries, assemble, lint, AndroidTest compile PASS; raw APK and current Android WebView target tests 1/1 each; full Browser E2E PASS; official default package verification PASS
    screenshots: Native 6D99B750900D4207618A16B9C9DEE2BEF4FC11386BE5AD421B1FF5C63A0AAF33; Web B38F9E797A0AF6C279411819ED8658327E7C921B24F813179F51C6270F947525
    evidence: .local/android-test/reports/native095-timeline-initial-left-20261107/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-TIMELINE-INITIAL-LEFT-20261107.md; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    residuals: frozen source comment initializes LEFT with Int.MAX_VALUE but actual APK startup is 0; internal Compose cause not asserted. Date-only Deadline Android A/B, other Timeline states and all remaining page parity stay open; Product Cutover HOLD
  NATIVE_095_DRAFT_DECK_STACK:
    status: NARROW PASS (persisted stack, search/browse, proposal route, scrim and system bars; complete Deck remains PARTIAL)
    code_commit: 26cea3e974c5f695b940d8b26c9fa19a8641cc9c
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    original_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    current_apk_sha256: 3E95E2992AD89EAE7BC8C0B59084F39504A27432A3533532BB78B1D6AD58EA56
    default_skin_sha256: dd49636784dfab7ee356a114d3d4db0963087f37a4e1f0bc3ce505b766f9d14e (one package; source/APK bytes match)
    android: emulator-5556 / PowerCal_Wave2_QA35_20261104, API 35; paired reference screenshot from emulator-5560 / PowerCal_Native095_MenuReference_20261107
    tests: full serial Gradle gate 988/988 JVM PASS; official package verifier PASS; exact rebuilt asset Android Deck test 1/1 PASS; Studio Python 90/90; focused Deck lifecycle and search Browser E2Es PASS
    evidence: .local/android-test/reports/native095-draftdeck-20261107/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-DRAFT-DECK-STACK-20261107.md; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    residuals: full Browser E2E finds a separate initial-left-wing Timeline fold mismatch; complete Draft Deck state coverage remains partial; Product Cutover HOLD
  NATIVE_095_TIMELINE_PINCH_ZOOM:
    status: NARROW PASS (two-finger vertical pinch, ruler double-tap reset, repeat pinch, display rounding; entire Timeline remains PARTIAL)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    code_commit: 05514a12c3cf6c0b62bfe2daad04fbbdf433097f
    original_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    current_apk_sha256: CF310827CA4C3A7989DB79A5D35F6F2755F2AD01E7601D28B40CA271C74492CB
    default_skin_sha256: 78C13F92BB2878A559CCD57FE64FEA7F22D2137460620139C69155C7CBF5BB7E (one package; source/APK asset bytes match)
    android: frozen Native AVD emulator-5560 and Web QA AVD emulator-5556, API 35, 1080x2400, 420dpi; WebView 124.0.6367.219
    tests: full serial Gradle gate PASS; 987 JVM tests, 0 failures/errors/skips; both source boundaries, APK, lint and AndroidTest compile PASS; final Native and Web pinch test 1/1 each; Studio Python 90/90, binding catalog 9/9, Node syntax and Preview Mock browser E2E PASS
    evidence: .local/android-test/reports/native095-timeline-pinch-20261107/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-TIMELINE-PINCH-20261107.md; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    residuals: no dense-event geometry, whole-page screenshot parity, three-finger/pointer-cancel, local-year Android boundary, or DB snapshot; full Timeline and overall Native-to-Web parity remain PARTIAL; Product Cutover HOLD
  NATIVE_095_SETTINGS_PROFILE_CARD:
    status: NARROW PASS (profile card only; Settings remains PARTIAL)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    code_commit: b6a9b91679c0f77d4e3415b6bdcee77d5663312e
    raw_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    current_apk_sha256: B0AEEF6E19F8AA14AB5FBB2B4ED80C2DE0DED405C1519809A4827053EA09F0DF
    default_skin_sha256: 4A057F27EA31343362B1EC7BD8F2E7B928E8C97A8520DE653B2A32196B3CD400 (one package; source/APK bytes identical)
    android: PowerCal_Native095_MenuReference_20261107/emulator-5560 and PowerCal_Wave2_QA35_20261104/emulator-5556; API 35, 1080x2400, 420dpi; WebView 124.0.6367.219
    tests: full serial Gradle PASS; 987 JVM, 0 failures/errors/skips; source boundaries, assemble, lint, AndroidTest compile/package PASS; two profile/Settings Android methods 2/2; Studio package tests 7/7 and Browser mock E2E PASS
    geometry: Native card [42,299]-[1038,963]; Web input physical bounds derived from DOM `dpr=2.625`; screenshot input edge deltas about 1px horizontal/top and 3px bottom; raw/Web screenshot hashes are in evidence manifest
    evidence: .local/android-test/reports/native095-settings-profile-20261107/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-SETTINGS-PROFILE-20261107.md; parity audit docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    residuals: AI settings, reminder/permission/data cards and complete Settings states remain PARTIAL; Native full-screen parity remains IN_PROGRESS; Product Cutover HOLD
  NATIVE_095_AI_MESSAGE_MENU:
    status: NARROW PASS (user menu screenshot geometry/style; user and assistant DOM/menu assertions; full AI page remains PARTIAL)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    code_commit: 141d18ca8f50cadeaef3b7ddb920ec51e827b720
    raw_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    current_apk_sha256: F3EB40CA90C732137F820C4F8843217C990AC9672F6843EC68F3D1EDDE5105D7
    default_skin_sha256: 1B05DA65418D5C0C24273F369C54B95EE59D1DCC0CFBA32A3FFD6E80D5478F11 (one package; source/APK bytes identical)
    android: PowerCal_Native095_MenuReference_20261107/emulator-5560 and PowerCal_Wave2_QA35_20261104/emulator-5556; both API 35, 1080x2400, 420dpi; WebView 124.0.6367.219
    tests: Gradle serial gate PASS; 987 JVM tests/zero failures-errors-skips; target WebView instrumentation 1/1; Node syntax PASS; distribution Python 2/2; package verifier PASS
    evidence: .local/android-test/reports/native095-ai-menu-20261107/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-AI-MENU-20261107.md
    residuals: menu near viewport edge, separate Native screenshot of assistant menu, AI proposal/attachment/error states, dark screenshot and focus traversal NOT TESTED; Product Cutover HOLD
  NATIVE_SETTINGS_ROUTE_AUDIT:
    status: CLOSED (ordinary route removed; Native recovery remains separate); Settings visual comparison NOT TESTED
    finding: Clear no longer renders the nativeSettings action; WebSkinHost decoder rejects it and PowerCalApp has no ordinary Page.TOOLS branch for it
    recovery_route: MainActivity nativeRecoverySignal remains; Android dirty-editor checkpoint/recovery instrumentation passed 1/1
    replacement_paths: Web reminder/permission, ICS/system-calendar, backup, profile, and protected AI Host actions remain present
    tests: Android route/settings and recovery/profile tests 3/3; full serial Gradle gate 987/987 JVM; official package verification PASS
    apk_sha256: B41DCD08421B4BE91D40FC0B205945BCEB0D3884B0DD4F38C8D80B8C505F9A6C
    default_skin_sha256: 88ea98745669139bd4c454b4e23c1555cd29450de3469e906d11df99124f8a50 (one powercal-clear-default package; source/APK bytes identical)
    evidence: .local/android-test/reports/native-settings-route-20261107/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-TOOLS-ROUTE-20261107.md
    next: perform paired frozen-Native/current-Web Settings screenshots and real operation comparison; visual parity remains PARTIAL
LAST_TASK_EVIDENCE:
  NATIVE_095_AI_INPUT_LIMIT:
    status: PASS (6000-code-unit composer boundary only; AI page and overall Native parity remain PARTIAL)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    code_commit: a8da55828a3ba6d2f0b0c9d12f99ef8213ed08be
    android: PowerCal_Wave2_QA35_20261104 / emulator-5556, API 35, WebView 124.0.6367.219; actual WebView textarea/form → MessagePort → Host → Core → local stub; 6000 characters received and persisted; Calendar Store snapshot unchanged
    tests: serial full Gradle gate 983/983 JVM, zero failures/errors/skips; both source boundaries, assembleDebug, lintDebug, AndroidTest compile/package PASS; targeted Android test 1/1 PASS; Node syntax, Studio bridge E2E, package verification PASS
    current_apk_sha256: EF29297DF78D253E2A9C3ED03B24C81664CC23CD2A429874B8C7E348D3E3789B
    current_skin_sha256: 2C503559A2CD7FAE57C2F40E529A05913D41B3192931458D17771AF2714F9E4E (10 files, one package, source/APK identical)
    evidence: .local/android-test/reports/native095-ai-input-20261106/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-AI-INPUT-LIMIT-20261106.md; audit docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    limits: test populated the WebView input via script (physical keyboard not tested); raw Native APK 6000-character input not directly compared; AI history, attachment, proposal, error/retry and menu visual parity remain open; Product Cutover HOLD
  NATIVE_095_AI_HISTORY_PROJECTION_AUDIT:
    status: CONFIRMED as the pre-fix Android A/B finding; the narrow defect is verified fixed by NATIVE_095_AI_HISTORY_PAGING below; AI page remains PARTIAL
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    raw_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    web_apk_sha256: EF29297DF78D253E2A9C3ED03B24C81664CC23CD2A429874B8C7E348D3E3789B
    devices: PowerCal_Native095_RawFixture_20261106/emulator-5590 and PowerCal_Wave2_QA35_20261104/emulator-5556; API 35, 1080x2400, 420dpi; WebView 124.0.6367.219
    fixture: 42 alternating messages; final content 1945 chars; SHA-256 272CB979F92023335C55BFF29BD453EC425BF4EA11832CC827147541842F4CE1
    observed: Native top includes HISTORY_00; Web top begins HISTORY_02 (39 numbered items 02-40 plus long item); Native end includes LONG_HISTORY_END_SENTINEL; Web truncates at 1600 and has no end sentinel
    screenshots: raw top B82A5AC4C43E8AE74F3C356E73F808F20381131E8B36685189833F2F7765F157; raw bottom DE65B04118CB08E327C2B0646311854D512899DB86C586F83BA37193A6CE1711; Web top 643FC2CE814D70BD54551AFF163FBF380E3CB6694704659312360E175076FE88; Web bottom CE9BB6A84403509C061EAAF373FCB9739224F142B2A1340A77294E69D84F2ABE
    evidence: .local/android-test/reports/native095-ai-history-audit-20261106/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-AI-HISTORY-PROJECTION-20261106.md; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    safety: no chat message or Calendar action invoked; test fixture removed; Raw 2-byte chat file restored; Web history file removed; onboarding prompt preference restored false; no AVD/app wipe; no Core calendar mutation requested
    adjacent_risk: unconditional Web bottom-scroll was resolved by the paging implementation; browser E2E verifies anchor preservation and conditional follow behavior
  NATIVE_095_AI_HISTORY_PAGING:
    status: PASS (full message availability/content, bounded frames, stale revision/cursor rejection, reader anchoring; AI page remains PARTIAL)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    code_commit: 3ee4e79dd956a5e419adea8400be6ba76601a651
    apk_sha256: acabdf1fbb3df1c998d6b743851b01f3289042aa1159c8871364ca21063614ae
    default_skin_sha256: cd09d73f021fd34bc3ce56534df5b72e5ba26a8bba87e6e34dc3ffda2bc4e0c8 (one powercal-clear-default.pcskin; source/APK bytes identical)
    android: PowerCal_Wave2_QA35_20261104 / emulator-5556; API 35, 1080x2400, 420dpi, WebView 124.0.6367.219; actual touch paging displayed all 42 fixture messages and full 1945-character tail
    browser: E2E loaded 84 ordered messages across stale revision plus append; rejected the old page request, preserved cached messages and 0px prepend anchor; conditional follow/reset checks PASS
    tests: serial full Gradle 987/987 JVM, 0 failures/errors/skips; Skin/Native source boundaries, assembleDebug, lintDebug, AndroidTest compile PASS; Studio catalog 9/9 and Clear bridge E2E PASS
    studio_python: 87/90 PASS; 3 unrelated stale source-shape assertions FAIL (names in Issue); suite is not reported as full PASS
    store: Calendar DB SHA-256 unchanged at 0c009f99188c8584d805a31f17812c507d5f54412d17d9ec68ef38e9cace765a; synthetic chat file unchanged during read-only paging, then exact fixture removed; onboarding preference restored false
    evidence: .local/android-test/reports/native095-ai-history-paging-20261106/manifest.md; Issue docs/issues/PC-ISSUE-SEPARATION-NATIVE-AI-HISTORY-PROJECTION-20261106.md; parity audit docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    limits: bubble/role-label visuals, menu/attachment/proposal/error parity, and real provider NOT TESTED; AI page and global Native parity remain PARTIAL; Product Cutover HOLD
  NATIVE_095_EDGE_POINT_HIT_SLICES:
    status: PASS (bounded edge slivers and actual click reachability only; Timeline remains PARTIAL)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    code_commit: 7858ee357714a29fba955d9acf7b36715ba22c47
    raw_android: PowerCal_Native095_RawFixture_20261106 / emulator-5590, API 35, 1080x2400, 420dpi, GMT; installed base.apk matches reference file; existing Deadline #0012 clicked at upper-edge sliver, no save
    web_android: PowerCal_Wave2_QA35_20261104 / emulator-5556, API 35, WebView 124.0.6367.219; dual-edge actual touch test 1/1 PASS; 8h distant point omitted
    tests: serial full Gradle gate 982/982 JVM, 0 failures/errors/skips; both source boundaries, assembleDebug, lintDebug, AndroidTest Kotlin compile PASS; package verifier PASS; targeted instrumentation 1/1 PASS; changed-file Node syntax PASS
    current_apk_sha256: 39A89C98A0789963EFABCD8DC13106D55859ADCBB24378B10D11920ACA91D634
    current_skin_sha256: 7AA3700A0D1E1CE1F19943CE012595E55A933B8D75FBFDBE306987537CDFE3EC (10 files, one package, source/APK identical)
    evidence: .local/android-test/reports/native095-edge-points-20261106/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-TIMED-POINT-HITBOX-20261106.md; audit docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    limits: different isolated fixture/camera positions, so this proves frozen edge behavior and Web touch reachability, not whole-page pixel equality; no Core or Store code changed; Product Cutover HOLD
  NATIVE_095_VISIBLE_POINT_BOUNDS:
    status: PASS (paired current Native Compose/Web geometry within 1.5px and raw/Web integer edge samples agree; Timeline remains PARTIAL)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    code_commit: 206a2bb9
    android: PowerCal_Native095_RawFixture_20261106/emulator-5590 and PowerCal_Wave2_QA35_20261104/emulator-5556; API 35, 1080x2400, 420dpi, GMT; WebView 124.0.6367.219
    geometry: Native Compose [267,1112]-[488,1259]; Web physical [267.6283,1111.8183]-[487.8225,1258.7352]; CSS 84x56; maximum edge delta <1px
    touch: raw and Web x=487 opens #0012/fixture Deadline; x=489 does not; Native/Web isolated Store counts unchanged
    tests: connectedDebugAndroidTest 1/1 PASS, 0 failures/errors/skips; targeted test compiled the AndroidTest APK; no full JVM gate rerun in this test-only slice
    screenshots: raw reference native095-edge-initial.png 2F41FF202CED96D037CFD7784D6854F6B45D2CCC0927551D9422B1ED03CE2649; Native A14609598EACB02A554915607E7071375BB74961B0BE2FEBF1FCAAAEFD448255; Web 916AF5075E3A55E4010D56978F601440438F90C9AC9DED39BF656732407F812A
    evidence: .local/android-test/reports/native095-point-bounds-20261106/manifest.md; updated audit and issue PC-ISSUE-SEPARATION-NATIVE-TIMED-POINT-HITBOX-20261106
    limits: raw uiautomator node bound is 210px wide while observed taps reach x=487; do not equate accessibility bounds with hit reach; no subpixel/raw-vs-Web full screenshot parity claimed
  NATIVE_095_ALARM_POINT_TITLE:
    status: PASS (ALARM title typography, type color and title-box geometry only; Timeline remains PARTIAL)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    code_commit: 75a3510c5affa18c08dbb0d23482496d0240453d
    current_apk_sha256: AA7D061C7EE412BBF368E33B237869539E076992FEE7A8D3A7E79E3CAB79CD5D
    current_skin_sha256: 95526F9DF0C1524790E48EFF3ECDCA6F6848C078BBD34825D2FC2463D0E9D58E (10 files, 410943 bytes, one package, source/APK identical)
    tests: serial full Gradle gate PASS; 977/977 JVM, 0 failures/errors/skips; source boundaries, assemble, lint, AndroidTest compile/package PASS; raw Native and Web v3 instrumentation each 1/1 PASS; distribution 2/2 and Node syntax PASS
    android: raw frozen APK AVD PowerCal_Native095_RawFixture_20261106 / emulator-5590; Web v3 AVD PowerCal_Wave2_QA35_20261104 / emulator-5556; both API 35, 1080x2400, 420dpi, GMT; same eight-event fixture; Store counts 0 drafts / 8 commits / 8 identities
    geometry: Native title box [40,1047]-[226,1110]; Web converted [39.32,1046.89]-[226.64,1109.85], max edge/size delta 1.32px; ink bbox Native (42,1066)-(220,1089), Web (42,1065)-(222,1088)
    evidence: .local/android-test/reports/native095-alarm-point-title-20261106/manifest.md; issue docs/issues/PC-ISSUE-SEPARATION-NATIVE-TIMELINE-POINT-TITLE-20261106.md; audit docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    limitations: only ALARM title directly tested; DEADLINE style/position, ACTIVITY cards, dark/large font, recurrence, full Timeline and all other pages remain open; Native recovery intact; Product Cutover HOLD
  NATIVE_095_TIMELINE_WING_BOUNDS_AND_SCROLL:
    status: PASS (dual-wing physical bounds, independent scroll extents and touch movement; Timeline remains PARTIAL)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    commit: f55140a2697befe483188c88052d1c5b4c8c22b4
    current_apk_sha256: 960EE33EB990A94290646CDBF8D6C1F714031A237F5B107BCA93CE0316B67DDD
    current_skin_sha256: 7373A2E0B7BE209AD5FCAAB304D90C5F96BD7C77DAC51B453504826FB315C3AF (410658 bytes, one package, source/APK identical)
    tests: serial Gradle gate PASS; 976/976 JVM, 0 failures/errors/skips; source boundaries, assemble, lint, AndroidTest compile/package PASS; targeted API 35 instrumentation 1/1 PASS; distribution tests 2/2 PASS; package verifier PASS
    android: isolated API 35 AVD PowerCal_Wave2_QA35_20261104 / emulator-5556, 1080x2400, 420dpi, WebView 124.0.6367.219; eight fixture events; Camera unchanged; Store writes 0
    geometry: Native left/right [32,488]/[593,1049] physical px; Web [31.46,488]/[593,1049], max edge delta <2.5px; Native scroll 458px, Web 456.19px; physical drag distances 152px vs 151.83/152.80px
    evidence: .local/android-test/reports/native095-timeline-wings-20261106/manifest.md; issue docs/issues/PC-ISSUE-SEPARATION-NATIVE-TIMELINE-WINGS-20261106.md; audit docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    limitations: Native image is from test Compose reference, not direct original APK; do not infer full-screen or event-card parity; raw APK same-fixture comparison is next
  NATIVE_095_TIMELINE_OUTSIDE_TITLE_GREEDY_FOLDING:
    status: PASS (ACTIVITY outside-title placement, measured rectangle folding, title bounds, folded-event target; Timeline overall remains PARTIAL)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    commit: 23d6d648b06155caa412a4a5d47b9cc4d9722667
    current_apk_sha256: E18730AB26D010EDA290989FB0C9FC8B79DF55A55102AA0EA2FB81D4F5A328C1
    current_skin_sha256: 3B1BBCD210D24458DF40F3AEBC5A4090F7B059F9406E964EAAB81661078BBA0E (410655 bytes, one package, source/APK identical)
    tests: serial Gradle gate PASS; 976/976 JVM, 0 failures/errors/skips; source boundaries, assemble, lint and AndroidTest compile/package PASS; greedy-fold unit tests 10/10; distribution tests 2/2; package verifier PASS; targeted Android instrumentation 2/2 PASS
    android: verified isolated API 35 AVD PowerCal_Wave2_QA35_20261104 / emulator-5556, 1080x2400, 420dpi, WebView 124.0.6367.219; Compose and actual WebView used the same five-ACTIVITY fixture; folded Fold_B opened its own editor without Store/commit change
    geometry: three visible title leads match; title rectangle edges within 2.5 physical px; noon timeline point differs by 0.67px; today, zoom and reset actions passed
    evidence: .local/android-test/reports/native095-timeline-title-20261106/manifest.md; issue docs/issues/PC-ISSUE-SEPARATION-NATIVE-TIMELINE-OUTSIDE-TITLE-GREEDY-FOLDING-20261106.md; audit docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    limitations: left ALARM lane title/visual states, other kinds/states, dark/large font, recurrence and full Timeline parity remain unverified; Product Cutover HOLD
  NATIVE_095_TIMELINE_DATE_DEADLINE:
    status: PASS (date-only Deadline geometry, 23:59 anchor, flag chip, accessible date precision, Host drag and read-only point tap; Timeline overall remains PARTIAL)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    original_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    commit: 1f1d8c2d2e92ec665d81dd215248532efca0e533
    current_apk_sha256: F2F4CBC14737437155E7CB373391CC94D44DAD27892FC788BABFB262ABBA4361
    current_skin_sha256: 37B3C2FBECE928F547191B759BCC8DAEFB792799233506F8EAE861036BB49BF6
    tests: full serial Gradle gate PASS; 976/976 JVM, 0 failures/errors/skips; boundaries, assemble, lint, AndroidTest compile/package PASS; Studio bridge E2E PASS; targeted Android TimelineDragWebViewInstrumentedTest 3/3 PASS; package verifier PASS
    android: isolated API 35 AVD PowerCal_Native095_Original_20261105 / emulator-5554, 1080x2400, 420dpi, GMT, WebView 124.0.6367.219; same-fixture Native/Web interaction, Web DOM geometry against Core/Host time anchors, date point tap, date-chip drag, and Store invariants verified
    geometry: Activity true-time top/height and Deadline `visualAxisMillis` point within 1.5 CSS px; chip starts at 16 CSS px and matches Native tertiary colors; date-only accessibility text has no clock time
    screenshots: original frozen APK `.local/android-test/reports/native095-timeline-20261106/native-timeline-nov15.png` SHA `1DA4516BF4CFF4D1A297F53D220613A902BA03D0C4243A0CAFADEE0223D002A9`; Web `.local/android-test/reports/native095-timeline-fix-20261106/clear-date-deadline.png` SHA `BCDD33726E881EB30316E961F3BA3DEB52744E9636DE8AC0FD74B6A92E50795C`
    evidence: `.local/android-test/reports/native095-timeline-fix-20261106/manifest.md`; issue `docs/issues/PC-ISSUE-SEPARATION-NATIVE-TIMELINE-DATE-DEADLINE-20261106.md`
    limitations: screenshot fixtures have different event/date sets; dual wings, outside titles, greedy folding, dark/font/accessibility states and full Timeline parity remain open; Product Cutover HOLD
  NATIVE_095_DENSE_MONTH_AND_EVENT_EDITOR_SHELL:
    status: PARTIAL (dense Month ordering/title wrapping/tap and editor outer shell geometry/surface are narrow PASS; no page is fully signed off)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    original_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    commits: b578c52e88e65ba5eee7c4e9946c6907e3c303e8, 668bf33f21eaef757bc362f88282a8ed65404685, f05a7e7a1eab8ebdf2cfd30db3a0390b574d8907
    current_apk_sha256: B9D7105231CE8ECEBAE2A83E6701E4F46D0425180518B0470BAFD465719CA0EF
    current_skin_sha256: 1A5C89E0A0CFA8AD30A7CEDFF28C2B382FF3962A78DF47AA00BA746E9D552918
    current_skin_id: powercal-clear-default
    checks: source boundaries, assembleDebug, lintDebug, build_default_clear --verify --apk, Node syntax, Studio bridge E2E PASS; API 35 Compose/Web dense-month instrumented comparison 1/1 PASS; direct original APK/Web screenshot and card tap comparison PASS; read-only Store snapshot unchanged
    studio_python: 86/89 passed, 3 failed source-shape tests (test_full_route_registry_has_one_restore, test_privacy_editors_remain_host_owned_but_placeable, test_native_settings_and_hidden_activity_rescue_are_both_available); not represented as PASS
    full_jvm: NOT RUN for this CSS-only slice
    geometry: editor Web panel x=58.095,y=49.524,w=296,h=792 CSS px at viewport 412x867 DPR 2.625; frozen Compose bounds [152,193][928,2272] physical px; panel color #F6F8FC, radius 22, scrim alpha 0.60
    evidence: .local/android-test/reports/native095-month-dense-20261106/manifest.md
    issue_docs: docs/issues/PC-ISSUE-SEPARATION-NATIVE-MONTH-DENSE-AGENDA-20261106.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-EVENT-EDITOR-SHELL-20261106.md
    limitations: editor fields/actions still differ; Timeline true-time/dual-wing/outside-title/greedy-folding comparison remains next; other Native pages remain PARTIAL; Product Cutover HOLD
  NATIVE_095_MONTH_AGENDA_TYPOGRAPHY:
    status: PASS (light-theme foreground and Material text tokens only; Month remains PARTIAL)
    code_commit: 1ffd87499c8d6d6089eb1c158178d6c39f1e9bf5
    test_only_commit: 6d8118f4a34cc50647bdf3b5a35c7bac008d91e8 (Studio export toast assertion)
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    android: PowerCal_Native095_Parity_20261105 / emulator-5580, API 35, 1080x2400, 420dpi, WebView 124.0.6367.219
    text_pixels: summary and sequence median RGB Native/Web both (17,21,28); Native/Web summary bbox (77,1554)-(298,1580)/(77,1555)-(304,1581); sequence bbox (75,1604)-(155,1625)/(75,1605)-(156,1626)
    tests: full serial app gate 976/976 JVM PASS; after correcting AndroidTest expected `#11151C`, AndroidTest compile/package and target instrumentation 1/1 PASS; Month CDP E2E PASS; corrected Studio bridge E2E PASS; distribution 2/2; package verification PASS
    apk_sha256: 1AB2F1FA90223CDD07D5B181F86562BE646260A384398E53360EA93F3FCAF094 (installed base.apk matches)
    default_skin_sha256: 57A5498366AD40E7BCF671F4EEB76E23B264546CA9C567C8F4D25BCE2A99FC7C (10 files, 410062 bytes, one package, source/APK identical)
    screenshot_sha256: Native 9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A; Web 0115C306758C3A00974E351EA3C006E296C5BD433679405D51B2492423619499
    evidence: .local/android-test/reports/native095-month-card-type-20261106/; Issue docs/issues/PC-ISSUE-SEPARATION-NATIVE-MONTH-AGENDA-TYPOGRAPHY-20261106.md
    residual: Web glyph bbox is 1px lower; summary ink is 6px wider; tokens/color match but glyph geometry is not pixel-identical
  STUDIO_CLEAR_BRIDGE_EXPORT_TOAST_ASSERTION:
    status: PASS (test-only stale substring fixed; product behavior unchanged)
    commit: 6d8118f4a34cc50647bdf3b5a35c7bac008d91e8
    evidence: .local/android-test/reports/native095-month-card-type-20261106/studio-export-toast-e2e.log
    issue: docs/issues/PC-ISSUE-STUDIO-E2E-EXPORT-TOAST-ASSERTION-20261106.md
  NATIVE_095_MONTH_EMPTY_AGENDA:
    status: PASS (empty-state typography and action spacing only; Month remains PARTIAL)
    code_commit: 24fbb376eb8b0ccf1825d70e0b4f7f68fceaa552
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    android: isolated API 35 AVD PowerCal_Native095_Parity_20261105 / emulator-5580, 1080x2400, 420dpi, WebView 124.0.6367.219; frozen Native and Clear both selected 2026-11-05 with empty agenda
    text_geometry: Native bbox (34,1492)-(349,1531), Web (33,1492)-(348,1531); median RGB Native (18,22,29), Web (17,21,28); button outline top Native y=1615, Web y=1617
    tests: final serial Gradle gate PASS; 976/976 JVM XML, 0 failures/errors/skips; source boundaries, APK, lint, AndroidTest compile/package PASS; month CDP E2E PASS; distribution 2/2; targeted Android instrumentation 1/1 PASS; package verifier PASS
    apk_sha256: 24F92FC77FE64BDA5CE15747234A1C3E221172713D6C67BEC7D21D0FE4FB6934 (installed base.apk SHA matches)
    default_skin_sha256: 0C60CB77F99A7074698025630B49FA8CA00BB7ED583EC16B153FA3A09B1FC53A (10 files, 410057 bytes, single APK entry, source/APK byte-identical)
    screenshots: Frozen Native 99F96C44646D2CFC3FAEE8427E376B0A4C4E0FC065D479DB89C027ED6E725249; final MainActivity Web 88C5A28FFE5912AA5CF649249E11C7B5BC22B9C8E39E89D1810CB2ED0662B498; instrumented screenshots and pixel script under .local/android-test/reports/native095-month-empty-20261106/
    store: instrumented parity operation asserted Native and Clear store counts and Timeline Camera remain unchanged
    retries: first CDP E2E lost a transient iframe execution context; fresh run PASS. First Gradle compile rejected a package-level fetchSemanticsNode import; using the existing Compose test package wildcard pattern compiled and the final full gate passed.
    limits: dense agenda/title folding, dark mode, complete Month page and other Native pages remain open; Product Cutover HOLD
  NATIVE_095_MONTH_YEAR_PICKERS:
    status: PASS (year/month panel, content, focus, validation, real touch and system-bar dim/restore narrow slice; Month overall remains PARTIAL)
    code_commit: c8d8724720f395d425fe8fb1116e2b781d780aaa
    picker_commit: 800cb630dd9037688908ffbfb2785b0c0a063830
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    tests: final serial Gradle gate PASS; 976/976 JUnit XML, 0 failures/errors/skips; source boundaries, APK, lint, AndroidTest Kotlin compile/package PASS; month browser E2E PASS; distribution 2/2; Node syntax PASS; targeted Android instrumentation 1/1 PASS
    android: isolated API 35 AVD PowerCal_Native095_Parity_20261105 / emulator-5580, 1080x2400, 420dpi, WebView 124.0.6367.219; final actual device test verifies dim and restore at four status/navigation samples, touch dismissal/selection and Store/Camera invariants
    screenshots: Native year E5F9E21CD4BE9DAF28D78BFBB484830BB7213EEDD4171D836D540165DB691151; Web year 5EE40BD8D7F631DD74A14343C208BB4189FF2E97AAC550891BF0A5A3919B0CA6; Native month 958C2AA7148E7DC508742A3BF8CF323CF6B565F656847178085BA1492BE44ED7; Web month 340BF684FFD5959A24A400E82A17AE314E53D825C812E21A0F257B1ADA51799A
    verified_geometry: Native/Web surface bounds year (120,924)-(959,1540)/(121,925)-(960,1539); month (120,739)-(959,1724)/(121,740)-(960,1725); remaining about 1 physical pixel raster offset
    apk_sha256: 590E3897512122EA216C92751B8CE6E63FD939A5EF03A4355E555149C3197753
    default_skin_sha256: 5D39562345B00DD719B89E2CCAB51A59964DFB8530AFC8AF046FF06B790EAEA7 (10 files, 410037 bytes, one APK entry, source/APK byte-identical)
    system_bar_parity: Native sample pixels (97,98,100); Web (97,97,97), maximum channel delta 3; final instrumentation verifies restoration after dismiss
    evidence: .local/android-test/reports/native095-month-picker-audit-20261106/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-MONTH-YEAR-PICKERS-20261106.md; system-bar-decor-instrumentation-final.log; gradle-systembar-final-gate.log; final screenshot SHA values in manifest.md
    limitations: Month full-page, dark theme, empty/dense states, TalkBack and OEM behavior remain open; Product Cutover HOLD
  NATIVE_095_MONTH_ACTIONS:
    status: PASS (bottom create/jump actions only; Month page remains PARTIAL)
    code_commit: b8376da7bb46c2ae41d8a86618998a282b16d816
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    tests: serial full Gradle gate PASS; 975/975 JVM, 0 failures/errors/skips; source boundaries, assembleDebug, lintDebug, AndroidTest compile/package PASS; Chromium month E2E PASS; distribution 2/2; Node checks PASS; two API 35 instrumentation methods PASS 1/1 each; factory package verification PASS
    android: isolated AVD PowerCal_Native095_Parity_20261105 / emulator-5580, API 35, 1080x2400, 420dpi, WebView 124.0.6367.219; date selection and jump/create behavior exercised; Calendar Store snapshots unchanged
    screenshots: Native SHA 9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A; Web SHA 51CCEC518B5D31A4C249784048A641448901A9D34B337D3C5B6CC87E2742B254
    verified_geometry: Native button border y=1710..1835, Web y=1711..1836; 126px raster height and 1px residual offset; 12 CSS px agenda-to-button gap; border mask IoU 0.863 after aligning Web by 1px (0.457 unaligned)
    apk_sha256: 19D52E6ED16AD42855E8DD5798A3B8D1B425C60A3B9951FD637ABA3BD4A9C818
    default_skin_sha256: 04AC4E3358448C18C2D0FBCF03853C9F2B16BCE3DDBDB0CD01C6DB9F01022D77 (10 files, 409520 bytes, one APK entry, source/APK byte-identical)
    evidence: .local/android-test/reports/native095-month-actions-20261106/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-MONTH-ACTIONS-20261106.md
    limitations: Month page remains PARTIAL; year/month dialogs, empty/dense states, dark mode, other pages, TalkBack and OEM behavior remain unverified; Product Cutover HOLD
  NATIVE_095_MONTH_AGENDA_CARD:
    status: PASS (agenda card/selected-date heading style slice only; Month page remains PARTIAL)
    code_commit: caa1d18e7160df5ee51ed33783b4bf4fe36df1ee
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    tests: serial full Gradle gate PASS; 975/975 JVM, 0 failures/errors/skips; source boundaries, assembleDebug, lintDebug, AndroidTest Kotlin compile/package PASS; Chromium month-selection E2E PASS; distribution 2/2; Node syntax checks PASS; two targeted Android methods PASS 1/1 each
    android: isolated API 35 AVD PowerCal_Native095_Parity_20261105 / emulator-5580, 1080x2400, 420dpi, WebView 124.0.6367.219; trusted date touch and recurring-instance editor path verified; Store snapshot unchanged for selection
    screenshots: Native SHA 9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A; Web SHA 6021DC49EED9315B105C3A84D8701B0250F9E32E220206B52FFD12DE7443E16F
    verified_geometry: card bbox Native (32,1436)-(1047,1677), Web (32,1438)-(1050,1679); Web card begins 2px lower and right edge is 3px farther right; heading ink bbox Native (37,1357)-(379,1398), Web (35,1358)-(376,1398); ROI x0..1079/y1420..1689 MAE 4.583, P95 channel delta 2, >2 delta pixels 5.3443%, surface-mask IoU 0.948156
    apk_sha256: 1A6C36F310295A0594C72C43B73F478A4F0A7C5A5C84A688BAC44FA670AA7ACD
    default_skin_sha256: F7ECF2A168A8D65968362A8A464053DA10248148E4837DC7ACF0DCB73F0B1826 (10 files; one APK entry; source/APK bytes match; ID powercal-clear-default)
    evidence: .local/android-test/reports/native095-month-agenda-20261106/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-MONTH-AGENDA-CARD-20261106.md
    limitations: dark-mode agenda card, Month bottom actions, full Month state matrix, other Native pages, TalkBack and OEM behavior remain unverified; Product Cutover HOLD
  NATIVE_095_MONTH_GRID_GLASS_SURFACE:
    status: PASS (inner fill color and pixel extent within about 2 px; outer Month and agenda remain PARTIAL)
    code_commit: 4a0a3f5303e13f682abf9ccbea2875d988f15a68
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    tests: Browser E2E PASS; distribution 2/2; Node checks PASS; source boundaries, assemble, lint, AndroidTest compile/package PASS; targeted connectedDebugAndroidTest 1/1; full JVM not rerun (previous 975/1 SQLite SQLITE_BUSY failure remains recorded)
    android: API 35 AVD PowerCal_Native095_Parity_20261105 / emulator-5580, 1080x2400, 420 dpi; trusted date touch, MonthFixture visible, Store snapshot unchanged; screenshot captured after WebView visual-state callback
    screenshots: Native SHA 9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A; Web SHA 163A5BE271320D25CEE3E2161866B507DF3DF9E6365B9817B81D8C798ED29A65
    apk_sha256: 896D5BAB6A01A145FDA97D53706DCB7DF30E31CE77B3E0998C89730C7B2278D6
    default_skin_sha256: 0B41CAC7FA5BF65C5A5D64D97AA740D6656B93560F1269EC59359B1B9C6B5331 (single APK entry; byte-identical to source)
    evidence: .local/android-test/reports/native095-month-grid-surface-20261105/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-MONTH-GRID-GLASS-SURFACE-20261105.md
    verified_geometry: ROI x80..1000/y300..1300 exact #FDFDFE bbox Native (105,338)-(973,1235); Web (107,338)-(974,1234), edges within 2 px; earlier screenshot had stale selected-date composition and is superseded by synchronized capture
  NATIVE_095_MONTH_OUTER_GLASS_PANEL:
    status: PASS (outer panel fill, outline, 28dp radius and side shadow profile; Month page remains PARTIAL)
    code_commit: c268902482d14b7d4462c440253e0bf9f2493454
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    tests: browser CDP month-selection E2E PASS; distribution 2/2; JS syntax PASS; source boundaries, assembleDebug, lintDebug, AndroidTest compile/package PASS; targeted API 35 instrumentation 1/1; no JVM suite run for CSS/test-only changes
    android: isolated API 35 AVD PowerCal_Native095_Parity_20261105 / emulator-5580, 1080x2400, 420dpi, WebView 124.0.6367.219; trusted date touch, MonthFixture visible, Store snapshot unchanged
    screenshots: Native SHA 9915F9DFF8D1F421F2233F6BEBB38CCBA5D7A44F33C5CEA092BD19C6F5B8B59A; synchronized Web SHA D0AD6368B24434BBA64A84C9593ECABC22E114FCEC54FF6B496CF9CAC9932D86
    pixel_compare: side ROI x22..35/y600..1200 MAE 0.786/255, P95 per-channel delta 2; border x32..34 is 3 physical pixels on both captures
    apk_sha256: B7659FC55A3188D2915940FF879702E0FC6658E3750F0ADA0D5BBCE7C4DA7F37
    default_skin_sha256: 0AE888C5BC9CB7335AC2499A6A1D85DE11EB989BF37EEE9855A92411CF2B203C (one APK entry; source/APK bytes match)
    evidence: .local/android-test/reports/native095-month-outer-panel-20261105/; docs/issues/PC-ISSUE-SEPARATION-NATIVE-MONTH-OUTER-GLASSPANEL-20261105.md
    limitations: agenda event-card surface/spacing, dark-theme panel comparison and complete Month parity remain open; previous full JVM gate has one SQLite SQLITE_BUSY failure; Product Cutover HOLD
  NATIVE_095_MONTH_SELECTION_COLOR:
    status: PASS (selected date number/count foreground only; Month page remains PARTIAL)
    code_commit: 35c294d445a4280166cdd7d3de0030efaba04d7f
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    tests: browser computed color and navigation E2E PASS; distribution 2/2; Android instrumentation 1/1; source boundaries, assemble, lint, AndroidTest compile/package PASS; full JVM 975 tests/1 SQLite lock failure
    android: isolated API 35 AVD PowerCal_Native095_Parity_20261105 / emulator-5580, 1080x2400, 420 dpi; trusted touch and Store unchanged
    screenshots: .local/android-test/reports/native095-month-count-20261105/month-web-selected-20261105.png; Native/Web fill bounds and hashes in audit appendix
    apk_sha256: 6CEA8B862C55C23F76262FE7D1B59F30A1B795EBFEE5AB78F7CCB7A321E7C64C
    default_skin_sha256: 85E2DD18688CF622823BFBF23233DD6957617CDF772CC551E1323CAF54EBE120
    issue: docs/issues/PC-ISSUE-SEPARATION-NATIVE-MONTH-SELECTION-COLOR-20261105.md
  NATIVE_095_MONTH_SELECTED_RADIUS:
    status: PASS (selected-day/today radius and tap only; Month page remains PARTIAL)
    code_commit: 84229c8a707761e295ff4bc366619804a653f82d
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    apk_sha256: A064F2F66AEEB0BC726CC15F79C6ACDA3D4179160991030C2ACD083D103DF40D
    default_skin_sha256: DDA39D12548368A33CB0F90041B61EAFE08AA7D53C710822491B29819F5345D3
    tests: Studio month-selection E2E and distribution 2/2 PASS; source boundaries, assembleDebug, AndroidTest compile/package PASS; no full JVM/Lint rerun for CSS/test-only changes
    android: API 35 AVD PowerCal_Native095_Parity_20261105 / emulator-5580, 1080x2400, 420 dpi; selected-day trusted touch, 14px computed radius, 52px cell height, MonthFixture visible; Store unchanged
    screenshots: .local/android-test/reports/native095-month-radius-20261105/ (Native/Web same-device pair, SHA-256 and descriptive ROI metrics in audit)
    limitations: selected count color, calendar surface, agenda card geometry remain visibly different; Product Cutover HOLD
  NATIVE_095_AI_MESSAGE_CONTEXT_MENU:
    status: PASS (WebView message-role/menu/copy slice only; Native original APK menu screenshot NOT TESTED; AI page remains PARTIAL)
    code_commit: dcd283a9d6cc6cfa1b7b08609fd8c2f4f6162bdb
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    apk_sha256: 40BF867049F303EA32DA6B3C06D3321C30F0A56BA5AA20B2F07A38D1D07B770D
    default_skin_sha256: 81A99440F5F9E67608460611FECE5825C61229852A8372B2455216086EE88456
    tests: 975/975 JVM; source boundaries, assemble, lint (0 errors), AndroidTest Kotlin compile PASS; Studio catalog Python 8/8, Node syntax and Chromium E2E PASS; Studio full Python 86/89, 3 unrelated source-shape assertions failed and are documented
    android: API 35 AVD PowerCal_Native095_AI_20261105 / emulator-5556, 1080x2400, 420 dpi, WebView 124.0.6367.219; actual touch long-press/menu order/Host clipboard test 1/1 PASS; Store counts and sequence unchanged
    store: message content and proposal source resolved from Host history; proposalSource omitted from Web state; no event/draft/pending/commit/identity/sequence changes
    evidence: .local/android-test/reports/native095-ai-message-menu-20261105/; docs/issues/PC-ISSUE-SEPARATION-AI-MESSAGE-CONTEXT-MENU-20261105.md
    limitations: original Native APK did not resume in foreground, so Native menu screenshot/paired screen NOT TESTED; proposal-create action touch, attachment, review, and error/retry remain open; Product Cutover HOLD
  NATIVE_095_AI_SETTINGS_HOST_SURFACE:
    status: PASS (route and Host-surface slice only; full AI parity remains PARTIAL)
    code_commit: 07c62fbf16c0aa72181f92783e84a5826905fc56
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    apk_sha256: a27588ea0a5e2251e14bd4bdaa269703a336a38c9fbe2bbccb2e3af2b91a7bf6
    default_skin_sha256: eaa55919a071107938db08e788b59a89625adf4bd7a3c6547d6ac235bf1ceeb6
    tests: 974/974 JVM; source boundaries, assemble, lint (0 errors), AndroidTest compile PASS; Studio Python 14/14, JS syntax, Studio Mock E2E PASS
    android: API 35 AVD PowerCal_Native095_AI_20261105 / emulator-5556, 1080x2400, 420 dpi; protected settings route and close/navigation 2/2 PASS; no app data clear and no credential values inspected or changed
    store: route/close test snapshot unchanged; Web state contains no apiKey field; one factory .pcskin, APK bytes match source
    evidence: .local/android-test/reports/host-ai-settings-20261105/; docs/issues/PC-ISSUE-SEPARATION-AI-SETTINGS-HOST-SURFACE-20261105.md
    limitations: dirty-form discard prompt not separately exercised; original frozen APK AI-settings screenshot not paired; chat/history/attachments/proposals/error parity and live provider remain unverified; Product Cutover HOLD
  NATIVE_095_AI_HOME_AND_OVERFLOW:
    status: PARTIAL (AI empty home and Web overflow order/outside dismissal verified; frozen original APK menu screenshot NOT TESTED; AI configuration/chat/attachment/proposal states remain open)
    commits: 96fa026594e8f085c6221c6bbda83a311829e779, 1e11116be0b22922988e8a0c777826ad0c7e8629, aceae50b9241509d1914f54493161e28af037f40
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    apk_sha256: 37E66E35ED14751E0FD3E71554CF42F5200762ED5F87B81A2BB886BDAD642B13
    default_skin_sha256: C8F8D3AE09B29A365A2B87BE2A9FEA4BB356E71F6E2DE93C0F9AA2667DA50338
    tests: 974/974 JVM and full source/build gate on parent 96fa0265; final HTML/CSS default distribution 2/2, Node check, assemble and APK asset verification PASS
    android: API 35 AVD PowerCal_Native095_AI_20261105 / emulator-5556, 1080x2400, 420 dpi, WebView 124.0.6367.219; WebView bounds [0,63][1080,2337]; empty-home and overflow open/dismiss captured; Native menu screenshot NOT TESTED
    evidence: .local/android-test/reports/native095-parity-20261105/native095-ai-host-inset-full-gate.log; native095-ai-menu-assemble.log; ai-native-home.png; ai-web-final-paired-20261105.png; ai-web-overflow-final-20261105.png; ai-web-overflow-dismissed-final-20261105.png
    limitations: one accidental cache-clear tap returned 0 files; no Store snapshot; AI configuration route still leads to full Native Tools via nativeSettings; Product Cutover HOLD
  NATIVE_095_ORIGINAL_APK_TIMELINE:
    status: PARTIAL (real WebView ACTIVITY fixture interaction/geometry passed; 1px raster deltas remain and other event kinds/timeline states are not signed off)
    commit: 10eae7e560e6b793e256e03482ac81cddd136117
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    apk_sha256: AB97D9AD1B6E57EFB43AC519D7A649F4D55C1004054DBA023F286297923B4F62
    default_skin_id: powercal-clear-default
    default_skin_sha256: 505535824f4780a4a6a50edd2e22130cd36131382baaae6bed18e86565a56177
    tests: 973/973 JVM; source boundaries, APK, lint, AndroidTest compile/package PASS; WebView fold/touch/geometry 1/1 PASS; Studio default package 5/5 Python; JS syntax and source Chromium E2E PASS
    android: API 35 AVD PowerCal_Native095_Original_20261105 / emulator-5582, 1080x2400, 420 dpi; fixed UTC time and four ACTIVITY fixture
    store: preseeded QA Calendar Store records, revisions and commitCount unchanged; preferences/files isolated
    evidence: .local/android-test/reports/native095-parity-20261105/timeline-native-pixel-raster-final.log; final-gate-native095-timeline.log; original-apk-native-fixture-today.png; webv3-mainactivity-native095-final.png; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    limitations: Web bar x-boundary and title-ink core differ by about 1px; ALARM/DEADLINE/recurrence/overlap/lane and full Native surface parity remain untested; complete owner visual sign-off is not claimed
  NATIVE_095_TIMELINE_FIXTURE:
    status: PARTIAL (current Native Compose route vs Web fixed event fixture PASS; original APK same-fixture comparison and pixel geometry remain incomplete)
    source_head: e75abbd1
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    apk_sha256: 5D92A32FD112BEA78422C9702FF337BFDC7B9B0E9237FC036B4C4758D76C5FF6
    default_skin_sha256: 0E0F51897CDC7AA784925EEBACCDAC395E45CD10D7B33F22D348DD6ECA26F598
    tests: 973/973 JVM; source boundaries, APK, lint PASS; AndroidTest assembly PASS; title-fold/geometry/touch 1/1, zoom+axis-doubletap 1/1, Native/Clear Today-camera-doubletap 1/1; Studio catalog 7/7; source Chromium E2E PASS
    android: isolated API 35 AVD PowerCal_Native095_Parity_20261105 / emulator-5580, 1080x2400, 420 dpi
    evidence: .local/android-test/reports/native095-parity-20261105/timeline-greedy-webview-api35-paired-screenshots.log; screenshots `native-fixed-event-geometry.png` and `web-fixed-event-geometry.png`
    audit: docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    limitations: event date/time fixed but system clock and camera were not frozen; Native capture is current Native Compose route, not the original APK; Native/Web physical-screen origin conversion and typography deltas not yet measured
  NATIVE_095_PARITY_SLICE:
    status: PARTIAL (visual and interaction parity remains IN_PROGRESS)
    source_head: e41810fa
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    apk_sha256: 1304586DC5E3189B4DB215D731618812D58DEC751C0C99FEEF497889F9F60E2A
    default_skin_sha256: ba4d9831fb774e4bc02e440b00a58b7608c7d390b9b97083e9e9596cfe930d81
    tests: 973/973 JVM; both source boundaries, APK, lint, AndroidTest compile PASS; focused Android touch 1/1 PASS; Studio targeted Python 12/12 PASS; Studio full Python 86/88 (2 existing source-literal failures); Studio source Chromium E2E PASS; Puppeteer browser suite BLOCKED
    android: isolated API 35 AVD PowerCal_Native095_Parity_20261105 / emulator-5580; screenshots are structural only because capture times crossed midnight
    evidence: .local/android-test/reports/native095-parity-20261105/manifest.md
    audit: docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    limitations: fixed-clock event geometry/title folding, editor/deck/review overlays, and full permissions/SAF touch parity remain unverified
  Q-01:
    status: PASS (isolated migration/state matrix; package uninstall/reinstall NOT TESTED)
    source_head: 49b0841a6ea98ceec4872b07d3b6cec260fe55a9
    android: 24 instrumentation methods PASS; one initial missing-phase invocation was assumption-skipped then rerun; API 35 AVD PowerCal_Wave2_QA35_20261104 / emulator-5554
    apk_sha256: 66fa0941beda7ff5fce3dbbfa176d3152a002205faafaba5666e8ac0068617a3
    store_sha256_before_after: 881b1b234520d24636d9816fe48681d0bcfb7a8b1f4d39a2c1513b33fefb9cf7
    evidence: .local/android-test/reports/Q-01-20261105/manifest.md
    limits: QA suffix APK build unavailable because the SDK platform/build-tools are absent; current installed app and test APK hashes matched their local artifacts; no app data clear/uninstall or production cutover.
  Q-02:
    status: PASS as matrix/replay task; SEP/SSA overall sign-off remains INCOMPLETE
    source_head: b71cf0eedbd6dba9dafe0d9215ab33457258d045
    android: Current RC SEP-09 native/Clear chat comparator 1/1 PASS on isolated API 35 AVD; test-helper fixed-tabbar scrolling was corrected; 48/51 Q-02 invocations PASS, with three historical failures preserved
    apk_sha256: EC27EBE7A85FD117CB83F846F26CD4D6A374D4908F09001FC051231A8592C340
    android_test_apk_sha256: A7ED51676045AEC57BB67DBBE868CF25BFF95ABDCF55571657ABB871B3ABA175
    store_sha256_before_after: 881b1b234520d24636d9816fe48681d0bcfb7a8b1f4d39a2c1513b33fefb9cf7
    evidence: .local/android-test/reports/Q-02-20261105/current-rc-real-touch/q02-sep09-retest.md
    limits: Full JVM was not rerun for the focused comparator; SSA states were not upgraded; TalkBack, physical/OEM, live provider and full SEP sign-off remain incomplete.
PRODUCT_DELIVERY_CHECKPOINT_A:
  status: DELIVERED (PowerCal Functional Preview; Debug only)
  apk: dist/powercal/PowerCal-Functional-Preview-2026-11-05.apk
  apk_sha256: 2ff316e1e350fdd4573ceac7ab154b616fb2e425ef209bde0d2b3e79d89b33a6
  default_skin_sha256: b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3
  build_head: 96fc4bde3836724d1de88c36aa7de5e3699a8dd1
  smoke: API 35 emulator, 11 focused Android instrumentation assertions passed; 970 JVM tests passed
  evidence: .local/android-test/reports/functional-preview-20261105/
  limitations: TalkBack traversal NOT TESTED; physical/OEM and live AI provider NOT TESTED
PRODUCT_DELIVERY_CHECKPOINT_B:
  status: DELIVERED (PowerCal Release Candidate; isolated internal Debug build)
  apk: dist/powercal/PowerCal-Release-Candidate-2026-11-05.apk
  apk_sha256: EC27EBE7A85FD117CB83F846F26CD4D6A374D4908F09001FC051231A8592C340
  apk_bytes: 67060154
  build_head: b71cf0eedbd6dba9dafe0d9215ab33457258d045
  package: dev.powerfulcalendar / 0.9.5-rc.1 / versionCode 905 / Debug
  default_skin_sha256: b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3 (one APK .pcskin; source/APK bytes match)
  gate: 971 JVM PASS; source boundaries, assemble, lint, AndroidTest compile/package PASS
  android: API 35 isolated AVD; final APK emergency-checkpoint and persisted-Clear cold-boot checks 2/2 PASS; R-07 failure/restart checks PASS
  evidence: .local/android-test/reports/R-07-20261105/
  limitations: TalkBack traversal, physical/OEM reminder delivery, live AI provider and full SEP sign-off remain NOT TESTED/INCOMPLETE; Product Cutover HOLD
PRODUCT_DELIVERY_CHECKPOINT_C:
  status: DELIVERED (Clear fallback Visual RC; custom visual integration PARTIAL)
  apk: dist/powercal/PowerCal-Visual-Release-Candidate-2026-11-05.apk
  apk_sha256: 66fa0941beda7ff5fce3dbbfa176d3152a002205faafaba5666e8ac0068617a3
  build_head: 447f3b6f7c8e2586cb787faa97ba9d27b9730cb5
  default_skin: dist/powercal/powercal-clear-default-Visual-Release-Candidate-2026-11-05.pcskin
  default_skin_sha256: b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3
  studio_project: tools/skin-studio/default-clear/
  android: API 35 isolated AVD; five WebView pages captured; see .local/android-test/reports/V-02-20261105/manifest.md
  limitations: No approved Claude artwork source found; TalkBack, physical/OEM and full SEP gaps remain; Product Cutover HOLD
WAVE2_STATUS: COMPLETE (8 authorized work packages attempted; all PASS)
WAVE3_STATUS: INTERNAL RELEASE CANDIDATE DELIVERED (Q-05 PASS on protected branch; F-09 TalkBack traversal NOT TESTED; Q-03 accessibility and Q-04 physical/OEM remain incomplete; V-02 custom artwork PARTIAL; historical SEP/SSA sign-off incomplete; public Product Cutover HOLD)

WAVE3_QUEUE:
  - F-08 permissions/reminders/ICS/backup/system settings: PASS (commit c3a1e59f44e2b304314673f0030ae19c2f3f9d28)
  - F-09 Back/IME/recovery/accessibility: BLOCKED (code/test fixes committed; TalkBack traversal order not verified)
  - S-06 from-blank complete Skin: PASS (commit 03c8ba38db785b16052a2f9b440b8bf3bc5ee79b; Studio GUI/API, 88/88 Python, Android 4/4, MainActivity force-stop/restore verified)
  - S-07 default Skin Studio roundtrip: PASS (commit 0cfd15564189f9c0e9e3c0436441cd084183022f; Studio save/reopen, Settings source edit, export/import/restore, 88/88 Python, final APK byte check, API 35 WebView Settings 1/1)
  - R-06 runtime retirement: PASS (commit ffde207343454bdd998ced223f3cfbcc0527d341; compiled examples moved to test source, v2 dispatch default-off, Native recovery preserved)
  - R-07 runtime/recovery gate: PASS (commit b71cf0eedbd6dba9dafe0d9215ab33457258d045; API 35 boot/failure/restart and dirty-editor checkpoint verified)
  - V-01 design/asset takeover: PASS (available Clear resources and Studio anchors mapped; no separate approved Claude artwork found in this worktree; see `VISUAL_ASSET_MAP.md`)
  - V-02 Clear-fallback Visual RC: PARTIAL (commit 0d6ceb1fb6a7a7a6fbc074a508d77480aa561ef6; APK and canonical .pcskin available in dist/powercal; five API 35 WebView screenshots and hashes recorded; custom art absent)
  - Q-01 install/migration acceptance: PASS (24 API 35 instrumentation methods; third-party package cleanup, native/clear/unknown selection states, process recovery and Core Store snapshots; package uninstall/reinstall NOT TESTED; evidence `.local/android-test/reports/Q-01-20261105/manifest.md`)
  - Q-02 full parity replay matrix: PASS as a task (16/16 SEP rows mapped; current RC Native-to-Clear AI chat trusted-touch comparator 1/1 PASS after fixing fixed-tabbar test scrolling; 48/51 invocation passes, with three historical failure logs preserved); SSA states were not raised and historical SEP sign-off remains INCOMPLETE. Evidence `.local/android-test/reports/Q-02-20261105/manifest.md` and `.local/android-test/reports/Q-02-20261105/current-rc-real-touch/`.
  - Q-03 recovery/performance/accessibility: BLOCKED for complete accessibility sign-off; 9/9 current API 35 renderer/corrupt-package/process-restart/2x-font/small-screen methods PASS; TalkBack focus traversal and low-end hardware NOT TESTED. Evidence `.local/android-test/reports/Q-03-20261105/manifest.md`.
  - Q-04 physical/OEM reminders: BLOCKED - EXTERNAL DEVICE; ADB listed only the isolated API 35 emulator. No physical/OEM test was run; see `Q04_PHYSICAL_DEVICE_RUNBOOK.md` and `.local/android-test/reports/Q-04-20261105/manifest.md`. This does not block the internal RC.
  - Q-05 single-default startup migration: PASS (commit 7197a907cacc875e0e8ecf4c662d2d550342a2f4; 973/973 JVM; source boundaries, APK assemble, lint and AndroidTest compile PASS; API 35 isolated AVD fresh install, Native/third-party choice migration, cold launch and corrupt-factory Native recovery PASS; legacy package files preserved when factory validation fails). QA APK: `dist/powercal/PowerCal-Release-Candidate-Q05-QA-2026-11-05.apk`, package `dev.powerfulcalendar.d05qaq05final`, SHA-256 `08E096B994C9078666DB43732F78D11B52B3603E836763B51A789E39348BA455`; evidence `.local/android-test/reports/Q-05-20261105/manifest.md`.


  NATIVE_095_TIMELINE_EMPTY_DATE_BAND_AND_API35_STATUS_INSET:
    status: PASS (two narrow slices; full Timeline and overall Native-to-Web parity remain PARTIAL)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    commit: 2f30b70013fc3fa2f464cb75c79852a9837f2d02
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    app_apk_sha256: 254d2ff1a8e4da4ebb3af4cbbffd68f9a2f3c13ff92fdf57cbd2ac3f509683de
    default_skin_sha256: a60e791b35d8793082fd944974b6a05cc45a6d1b8040af8ff891a5985458f964 (10 files, 410938 bytes; source/APK byte-identical; exactly one official package)
    tests: 977/977 JVM; source boundaries, assembleDebug, lintDebug, AndroidTest compile/package PASS; targeted API 35 empty-date-band instrumentation 1/1 PASS; JS syntax, distribution tests 2/2 and package verification PASS
    android: isolated API 35 AVD PowerCal_Wave2_QA35_20261104 / emulator-5556; Native/Web same-fixture empty state; no unintended Store writes. Live status inset 128px; Web title screen layout y=162.39px vs Native y=153px (3.58dp), direct title/subtitle ink baselines within 1px.
    studio_python: prior run 86/89; three named source-literal/route-registry failures; not rerun after final production changes, so no full-suite PASS claim.
    evidence: .local/android-test/reports/native095-empty-date-band-20261106/; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-TIMELINE-EMPTY-DATE-BAND-20261106.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-SHELL-STATUS-INSET-20261106.md
    limits: raw-APK and Web screenshots use different fixture state; only header geometry is compared. Remaining event/component and other-page differences are not signed off. Native recovery, Core behavior and Product Cutover remain unchanged/HOLD.

WAVE_2_QUEUE:
  - F-03 Closure: PASS (commit 2a8c323890c0f121b80c72a2a4d1c5c2a35d010d)
  - S-02 Android Gate: PASS (commit 62334dce69ac25c316b5bc39497f08adbc20bd16)
  - S-03 Host state/action binding catalog: PASS (commit 5eeb87d091e84bfd0906135497bc4ebca94cffba)
  - F-05 editor/draft/trusted-write parity: PASS (commit bb2007d69d90204acf0e27deb325a492f9c37746)
  - F-06 recurrence scopes/date/DST parity: PASS (commit 177ec0b733952ba56d5c13542e97ead08489d1d9)
  - S-04 Studio preview/runtime parity: PASS (commit f051ca04e743b3cdbb315874150e2df36b490ae0)
  - S-05 resource/source round-trip: PASS (commit a008f20771062e2d037a9f810435e5328b70bd46)
  - F-07 AI chat/proposal/review parity: PASS (commit 25711d3569edfd59c23c774c3bb371fbbc192044)

DEFERRED_TASKS: Q-06 optional cleanup not required for APK delivery; no Worktree, user data, Studio project, design source or history cleanup performed. R-06/R-07/Q-05 code gates PASS.
PHASE: Product delivery / final acceptance
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
  Native_recovery: powercal-default. R-06 retired compiled/v2 daily dispatch; R-07 verified single Web runtime boot and Native recovery; Q-05 migration passes on the protected branch. Public Product Cutover remains HOLD.
  No second factory skin or multi-skin store. Q-05 protected-branch default migration is PASS; public-store release is not authorized.

OWNER_OVERRIDE_2026_11_03:
  Legacy_package_policy: R-03 cleanup was limited to isolated QA app-private installed packages; do not archive/export legacy packages or preserve a daily multi-skin list.
  Protected: Calendar Store, drafts, reminders, AI history, Studio source projects, design sources, fixtures, external SAF files, and Git Worktrees.
  Release_gate: Product Cutover remains HOLD.
OWNER_OVERRIDE_2026_11_05:
  Functional_preview: Checkpoint A is delivered at dist/powercal/PowerCal-Functional-Preview-2026-11-05.apk; it is a Debug internal preview.
  Runtime_work: R-07 is PASS and the isolated internal RC is delivered. Do not migrate production preferences before Q-01/Q-02 and the required safety checks. After the internal RC and required safety checks, Q-05 may switch the default on this protected branch; no public release is authorized.
  TalkBack: F-09 focus order remains NOT TESTED after one bounded official keyboard shortcut diagnostic; no PowerCal defect was confirmed.
  Release_gate: Product Cutover remains HOLD.
Q05_DELIVERY:
  status: PASS (protected branch only; no public rollout)
  code_commit: 7197a907cacc875e0e8ecf4c662d2d550342a2f4
  qa_apk: dist/powercal/PowerCal-Release-Candidate-Q05-QA-2026-11-05.apk
  package: dev.powerfulcalendar.d05qaq05final / 0.9.5-rc.1 / versionCode 905 / Debug
  apk_sha256: 08E096B994C9078666DB43732F78D11B52B3603E836763B51A789E39348BA455
  default_skin_sha256: b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3
  apk_pcskin_count: 1
  evidence: .local/android-test/reports/Q-05-20261105/manifest.md
  limitations: TalkBack traversal, physical/OEM reminder delivery, live AI provider and full SEP/SSA sign-off remain incomplete; Product Cutover HOLD.

LATEST_VERIFIED_EVIDENCE:
  NATIVE_095_MEMO:
    status: PARTIAL (same-AVD frozen Native/Web Open and Done empty-state screenshots; visual bounds within about 1px; row completion, paging/detail, and editor/deck parity not tested)
    commit: fcffd4635c139fdcaff9d045ee1afde4a1f3a44f
    reference_source: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    apk_sha256: 5AED08E4161F64E939A4C0F79C1B5A259B97982AF3AD1D7A2C1D3AF04A97F2EA
    default_skin_id: powercal-clear-default
    default_skin_sha256: D8469959DBA9DBF0DDE3B4A200B8981BB939CEE3884D6664E7D386D4F61C42F7
    tests: 974/974 JVM; both source boundaries, assemble, lint, AndroidTest Kotlin compile PASS; Studio distribution 2/2; app.js syntax PASS; package source/APK verification PASS, exactly one .pcskin
    android: isolated API 35 AVD PowerCal_Native095_Memo_20261105 / emulator-5554, 1080x2400, 420 dpi; Native captured before in-place app update, no data clear; Open/Done screenshots and filter touch verified
    interaction: memo tab open, Open/Done toggle, create entry opens draft surface; draft not published/discarded/edited; no before/after draft-count snapshot, so no Store zero-write claim
    evidence: .local/android-test/reports/native095-parity-20261105/memo-adjusted-final-gradle-gate.log; memo-native-empty.png; memo-native-done-empty.png; memo-web-adjusted-open.png; memo-web-adjusted-done.png; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    limits: memo rows were empty; row completion, paging, title/detail, full editor/deck/review pairing, physical/OEM and global visual sign-off remain NOT TESTED; Product Cutover HOLD
  NATIVE_095_ORIGINAL_APK_TIMELINE:
    status: PARTIAL (ACTIVITY fixture interaction and geometry verified; 1px paint/text delta and other page/timeline states remain open)
    commit: 10eae7e560e6b793e256e03482ac81cddd136117
    jvm: 973/973, 0 failures/errors/skips; both source boundaries, Debug assemble, lint, AndroidTest compile/package PASS.
    android: API 35 isolated AVD PowerCal_Native095_Original_20261105 / emulator-5582; frozen original APK and current Web v3 screenshot with fixed UTC and same four-ACTIVITY fixture; fold-title tap and Store no-write assertion 1/1 PASS.
    skin: exactly one assets/powercal-clear-default.pcskin; package and APK asset SHA-256 505535824f4780a4a6a50edd2e22130cd36131382baaae6bed18e86565a56177; APK SHA-256 AB97D9AD1B6E57EFB43AC519D7A649F4D55C1004054DBA023F286297923B4F62.
    studio: default-package Python 5/5; app.js syntax and source Chromium E2E PASS.
    evidence: .local/android-test/reports/native095-parity-20261105/timeline-native-pixel-raster-final.log; final-gate-native095-timeline.log; original-apk-native-fixture-today.png; webv3-mainactivity-native095-final.png; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md.
    limits: ALARM, DEADLINE, recurrence, overlaps, all other surfaces, accessibility and full native visual parity remain unsigned; Product Cutover HOLD.
  Q-05:
    status: PASS (startup migration and internal QA package; public Product Cutover HOLD)
    commit: 7197a907cacc875e0e8ecf4c662d2d550342a2f4
    jvm: 973/973 across 141 suites, 0 failures/errors/skips; focused migration/Native recovery 39/39.
    boundaries: Skin and Native boundaries PASS; Debug assemble and lint PASS; AndroidTest Kotlin compile PASS.
    android: API 35 isolated AVD PowerCal_Wave2_QA35_20261104 / emulator-5554; direct fresh install, legacy Native selection migration, third-party selection migration, cold launch, and corrupt-factory Native fallback passed on isolated `.d05qa` package IDs. Original `dev.powerfulcalendar` was not cleared, uninstalled or overwritten.
    store: fresh and migration snapshots unchanged; corrupt or missing factory stays on Native and preserves legacy package files until a valid factory is available.
    apk: `dist/powercal/PowerCal-Release-Candidate-Q05-QA-2026-11-05.apk`, SHA-256 `08E096B994C9078666DB43732F78D11B52B3603E836763B51A789E39348BA455`, 67,011,026 bytes, package `dev.powerfulcalendar.d05qaq05final`, version `0.9.5-rc.1`/905, Debug.
    factory: exactly one `assets/powercal-clear-default.pcskin`; source/APK bytes match; SHA-256 `b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3`.
    evidence: `.local/android-test/reports/Q-05-20261105/manifest.md` and command logs in the same directory.
    limits: no physical/OEM, TalkBack, live AI provider or complete SEP/SSA sign-off; no public production rollout.
  V-01:
    status: PASS (complete inventory and source mapping; no standalone Claude artwork available)
    evidence: docs/agent-handoff/VISUAL_ASSET_MAP.md
    current_source: tools/skin-studio/default-clear/source/web/clear-design.css, style.css, app.js, edit.js; licensed Inter font is bundled; no image artwork in the factory `.pcskin`.
    studio: project.json exposes color slots but has no project.pages/component-tree IDs. S-07 verified Studio source-file roundtrip; the map uses real HTML IDs, not invented Studio component IDs.
    host_surface: DraftDeckDialog currently uses Native Compose recovery/trusted-approval rendering; it has no matching Web `.pcskin` component/resource mapping.
    limitations: no custom Claude artwork delivered; `design/default-skin-01/assets/pc.css` referenced by CSS comment is absent and untracked; contrast has not been fully measured.
  R-07:
    status: PASS (isolated runtime boot/recovery task scope; Product Cutover remains HOLD)
    commit: b71cf0eedbd6dba9dafe0d9215ab33457258d045
    root_cause: Activity emergency gesture selected Native before the dirty editor reached prepareSkinChange; it now requests checkpoint first and stops visibly if checkpointing fails.
    jvm: 971/971; 141 XML suites; 0 failures/errors/skips.
    boundaries: Skin/Native checks PASS; APK assemble and lint PASS; AndroidTest compile/package PASS.
    android: verified API 35 AVD PowerCal_Wave2_QA35_20261104 / emulator-5554; factory present/corrupt/missing, selected-package recovery, process restart, Web renderer/JS failure, repeated boot and dirty editor recovery checked. Final exact APK emergency checkpoint 1/1 and persisted Clear boot 1/1.
    store: emergency checkpoint produced exactly one pending draft; zero formal rows and commits. Process recovery preserved isolated draft and Memo completion; no Store implementation changed.
    apk: dist/powercal/PowerCal-Release-Candidate-2026-11-05.apk; SHA-256 EC27EBE7A85FD117CB83F846F26CD4D6A374D4908F09001FC051231A8592C340; one factory package SHA-256 b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3.
    evidence: .local/android-test/reports/R-07-20261105/manifest.md
    limits: forced checkpoint-I/O failure, TalkBack traversal, physical/OEM reminders, live AI provider and full SEP sign-off remain NOT TESTED/INCOMPLETE.
  R-06:
    status: PASS (compiled example and v2 daily dispatch retired; production cutover unchanged)
    commit: ffde207343454bdd998ced223f3cfbcc0527d341
    jvm: 971/971, 141 XML suites, 0 failures/errors/skips.
    boundaries: Skin and Native checks PASS; APK assemble PASS; lint PASS; AndroidTest Kotlin compile PASS.
    android: API 35 isolated AVD emulator-5554; persisted Clear cold boot, renderer failure Native recovery, uncaught JS recovery, and v2 non-dispatch fixture PASS (4/4).
    runtime: no ActiveVisualSkin PROFILE switch; StudioExample implementations live only in JVM tests; RuntimeSkinSurfaces is disabled by default and test fixtures opt in explicitly. Native Host/recovery adapters and v3 factory package remain.
    package: APK SHA-256 255b9df256f3e35bbce32e29cb8c1f70d985804ece6fdfc20a659de810e3f87b; exactly one .pcskin; source/APK skin SHA-256 b9035719c2e0c75d335225216ea3687b72cc2cf4692e829ca43566c730c3fee3.
    store: instrumented v2 route had zero formal rows, drafts, or commits; renderer recovery assertions preserved isolated Store snapshots.
    evidence: .local/android-test/reports/R-06-20261105/manifest.yaml and task logs/screenshots.
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
    followup_2026_11_05: documented Alt+Right and Alt+Ctrl+Left tested after confirming system UI first; no stable label-verifiable Clear focus sequence, so traversal remains NOT TESTED; accessibility settings restored to null/0/0. Evidence: .local/android-test/reports/F-09-talkback-diag-20261105/.
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
  NATIVE_095_MONTH:
    status: PARTIAL (same-device layout/touch and recurrence cold-restart evidence; visual differences and other page parity remain)
    commit: 707dacce48ffa64e756bf2ad0a90fab73678b6d0
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    app_apk_sha256: 3D333AC5A96BDE2BF29B5FA7D9D6AEA4347A2D5084CC50F6DFAAD5DF6FEB0D4A
    default_skin_sha256: E43A6EADC577069476F63F2E8F82A92F710D1A7D74E53CD67B48737FD7E16498
    tests: 974/974 JVM; skin/native source boundaries, assemble, lint and AndroidTest Kotlin compile PASS; isolated recurrence prepare/restore 1/1 each PASS; Studio full Python 86/88 with two documented architecture/recovery source-literal failures; targeted Python 13/13 PASS.
    android: API 35 isolated AVD PowerCal_Native095_Parity_20261105 / emulator-5580, same device before/after APK update; actual month navigation, pickers, date, Today, event open and Back; Core Store unchanged.
    evidence: .local/android-test/reports/native095-parity-20261105/month-visual-parity-final-full-gate.log; month-real-device-touch-sequence.jsonl; month-recurrence-prepare.log; month-recurrence-restore.log; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    limits: screenshot metrics show remaining panel/agenda deltas; original-APK recurrence pixel pairing, Puppeteer browser E2E and Memo/AI/Settings/editor/review parity remain NOT TESTED; physical/OEM not tested.


  NATIVE_095_EMPTY_DATE_BAND_AND_API35_STATUS_INSET:
    status: PASS (two narrow slices; full Timeline and overall parity remain PARTIAL)
    commit: 2f30b70013fc3fa2f464cb75c79852a9837f2d02
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    tests: 977/977 JVM; source boundaries, assemble, lint, AndroidTest compile/package PASS; targeted isolated API 35 instrumentation 1/1 PASS; distribution tests 2/2 and package verification PASS
    apk_sha256: 254d2ff1a8e4da4ebb3af4cbbffd68f9a2f3c13ff92fdf57cbd2ac3f509683de
    default_skin_sha256: a60e791b35d8793082fd944974b6a05cc45a6d1b8040af8ff891a5985458f964; exactly one powercal-clear-default package, source/APK byte-identical
    android: API 35 isolated AVD PowerCal_Wave2_QA35_20261104 / emulator-5556; query-level date-band and live inset measurements; Core Store unchanged
    studio_python: earlier 86/89 run with three known failures; not rerun after this slice, not claimed PASS
    evidence: .local/android-test/reports/native095-empty-date-band-20261106/; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
  NATIVE_095_RAW_APK_DUAL_WING_INITIAL_VIEW:
    status: PASS (same-fixture raw Native APK and Web v3 initial lane placement plus independent scroll; full Timeline remains PARTIAL)
    commit: 4194d513826e6104d85c4aadee23cfb066e2a45b
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    tests: 977/977 JVM; source boundaries, assemble, lint, AndroidTest compile/package PASS; raw Native/Web targeted instrumentation 2/2 PASS; Node syntax, distribution 2/2, package verifier PASS
    apk_sha256: 070FCF76FCEA8A4CCEC86A5F6B516A7FE2FE2ACDD5DFC89861C7E920211FE192
    default_skin_sha256: 8a679cb53be26fb1b4fb53f4e0361ac02b802a80e773e74b19b45b3ec6374dca (10 files, one package, source/APK identical)
    android: raw reference AVD PowerCal_Native095_RawFixture_20261106/emulator-5590; Web AVD PowerCal_Wave2_QA35_20261104/emulator-5556; both API35 1080x2400 420dpi, WebView 124.0.6367.219
    geometry: Native lane3/lane1 x=32/824px; Web x=31.46/823.36px; both initial scroll=0; max range Native 458px / Web 174 CSS px (456.2px); independent swipes PASS
    store: drafts=0, commits=8, identities=8; event #0006 editor open/Back left counts unchanged
    screenshots: raw CF30CCD7CC76650802D6DF0778AE7D5ACDE067E94171D9E111510DDAE7BB042A; Web 499BEAE9C8A05F59426449BB2A718B8538C2CBFDD22E7C90C57A5FFAFFD639D8
    evidence: .local/android-test/reports/native095-raw-dualwing-20261106/manifest.md; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-TIMELINE-WINGS-20261106.md
    limitations: other Timeline states, recurrence, theme/font variants, and other pages remain unverified; physical/OEM parity remains untested
  NATIVE_095_MEMO_METADATA_ROW_TOUCH:
    status: PASS (Memo row metadata, measured bounds/color, and metadata-area open; Memo page remains PARTIAL)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    commit: f6183f3b7c76166bf65ed069494313b0e39ecb33
    tests: serial Gradle full gate PASS; 977/977 JVM, 0 failures/errors/skips; boundaries, assembleDebug, lint, AndroidTest compile/package PASS; raw Native and Web v3 instrumentation 1/1 each PASS; AndroidTest recompile after KDoc-only change PASS; distribution tests 2/2 and Node syntax PASS; package verifier PASS
    studio_python: 86/89; three existing route/recovery source-shape assertions fail against the current architecture; not claimed PASS
    apk_sha256: E8C932F7D259F6EDFF601553895F37A26E55E52B980FEB871A0781A625FFB0B6
    default_skin_sha256: 87D5A3892A83B8241FC59C5EA1012937F03ACF05659B9757099C00352F42B504 (10 entries; only factory .pcskin; source/APK byte-identical)
    android: raw frozen APK AVD PowerCal_Native095_RawFixture_20261106/emulator-5590; current Web v3 AVD PowerCal_Wave2_QA35_20261104/emulator-5556; both API 35, 1080x2400, 420dpi; WebView 124.0.6367.219
    geometry: Native row [42,447]-[1038,615], title [210,479]-[800,542], metadata [210,542]-[455,584], sequence [929,510]-[1017,552]; Web physical edges within 2.86px maximum; sampled row fill RGB(246,248,252) on both
    store: Native counts remained drafts=1/commits=11/identities=11; Web isolated Store remained drafts=0/commits=3/identities=3; fixture records unchanged before/after open and untouched close
    screenshots: Native B1B6062129214CB48F2140CAAB5211015D4F568105235C3D7840D17AC8E66F31; Web 8630F3DAAED4F42AE4CD6E534070239E7F32256ED256E3C924A73DD532914295
    evidence: .local/android-test/reports/native095-memo-metadata-20261106/manifest.md; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-MEMO-METADATA-ROW-TOUCH-20261106.md
    limitations: only MEMO rows and opening an untouched row are paired; completion toggles, >250 paging, other kinds, full Memo states, and non-Memo pages remain unverified; Product Cutover HOLD
  NATIVE_095_MONTH_PAGE_SWIPE:
    status: PASS (full-page Month horizontal gesture hit region, Android touch slop, strict >72dp navigation threshold and associated click suppression only; Month page remains PARTIAL)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    code_commit: 7e98e393ddf075db206e37a7bfa599afea6f47bd
    tests: serial Gradle gate PASS, 978/978 JVM; source boundaries, assembleDebug, lint and AndroidTest compile PASS; paired Native/Clear instrumentation 1/1; Studio distribution 2/2, preview 11/11, changed JS syntax and browser E2E PASS
    apk_sha256: 479B01BF9D4D8DC2CFD30839047DAFA7025DA7BE75A327732DBE75DF645181EB
    default_skin_sha256: E5CA9803AAD5A450AE639773477A7F2EDEF047334CFA567527B2E3296738EAB8 (10 entries, one factory package, source/APK byte-identical)
    android: frozen raw APK on PowerCal_Native095_RawFixture_20261106/emulator-5590; current Web v3 on PowerCal_Wave2_QA35_20261104/emulator-5556; API 35, 1080x2400, 420dpi, WebView 124.0.6367.219
    behavior: 5dp picker motion preserves tap; 20dp horizontal motion cancels tap without changing month; with 8dp platform slop, 79dp total does not navigate and 81dp does; header/grid/agenda/footer swipes navigate; event-card swipe does not open event; Camera and Store unchanged
    screenshots: frozen header 208/214px 3A1125A5BA1A86A866C198FB292E3C7C8246C216FF2A4B4EF376C3254455CC29 / 59618D27C515C9323AFE8C3D772B4BE6524C1C4F2B89254B3D6BE5CDB78E901C; Web header 208/214px 50593851EDB1A0D1A49A57DF173401BE5F26B58D6B03E0ADFA4510F400BCA486 / B0D9BCAB797095432E4425AADA9ECAA9C9955ED83FE75A6E63E7ECEB1CB34070
    evidence: .local/android-test/reports/native095-month-page-swipe-20261106/manifest.md; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-MONTH-PAGE-SWIPE-20261106.md
    limitations: dense recurrence projection limits, all other Month visuals/states, other Native pages, accessibility and physical/OEM parity remain unverified; Product Cutover HOLD
  NATIVE_095_MONTH_PROJECTION_CAPS:
    status: PASS (Web projection retains all supplied owners/occurrences; Month page and full Native-to-Web parity remain PARTIAL)
    code_commit: 2e9651c6a8cbd1cdf22f7db3a5046c3a3243d301
    tests: serial Gradle full gate PASS; 981/981 JVM, 0 failures/errors/skips; source boundaries, assembleDebug, lint, AndroidTest compile PASS; dense Native/Clear API 35 instrumentation 1/1 PASS; Studio v3 preview 11/11, single-default distribution 2/2, changed-JS syntax and browser E2E PASS
    apk_sha256: C36388596CB759BD048A319BC36790BF04FB4F4D33A8962050A4E13F95CC4F34
    default_skin_sha256: 974A63A4E8EC1A755DCD3ACB6A1F2E32390F588EA31E157E62586DEA6BE4B50A (411630 bytes, 10 files; APK has exactly one default .pcskin and source/APK asset hashes match)
    android: isolated PowerCal_Wave2_QA35_20261104 / emulator-5556, API 35, 1080x2400, 420dpi, System WebView 124.0.6367.219
    behavior: before-fix repro returned 350 for a Native 351-row case; after fix 401 owner rows match Native, November 30 shows 13 events, touch opens seq 390, Store snapshot unchanged; three JVM cases cover former 350/400 boundaries and 372 recurring instances
    screenshots: Native Compose reference 0D3EDE628E2F525478FE4E714CAB10046430F702B9BAD403904F1129A81EFD62; WebView F1D4CCBE4B37EB2CC2736CD7260527346918E146B885CF5360E592BA2C9FAFB4
    evidence: .local/android-test/reports/native095-month-projection-20261106/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-MONTH-PROJECTION-CAPS-20261106.md; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    limitation: paired screenshot uses the in-app Native Compose reference route, not the standalone original APK on the same dense fixture; visual/layout signoff and all non-projection Month states remain open; Product Cutover HOLD
  NATIVE_095_TIMED_POINT_HITBOX:
    status: PARTIAL (visible timed point hit regions aligned directionally; viewport-edge point inclusion remains open)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    code_commit: 06649a334d12d0e7e9371fcc08d26a3fcc7ee9ad
    current_apk_sha256: F288BAB9DE9C9DE059FD9BC5CD85395E77C329D65DB9AC57B06B6E1142BB0F3D
    default_skin_sha256: 4F027EDEEF8B8F60BE3C3DA778BDC54E79601ADB04CBD5D92FAC9A74C3922366 (411655 bytes; one factory package; source/APK byte-identical)
    tests: Gradle full gate PASS, 981/981 JVM and zero failures/errors/skips; boundaries, APK, lint and AndroidTest compile PASS; targeted Edge point geometry probe PASS; full Studio Python 86/89 (three unrelated source-shape failures); full browser E2E stops at pre-existing palette assertion before Timeline checks
    android: frozen raw APK PowerCal_Native095_RawFixture_20261106/emulator-5590 and updated Web v3 PowerCal_Wave2_QA35_20261104/emulator-5556; both API 35, 1080x2400, 420dpi, GMT; updated APK installed with adb install -r and app data preserved
    behavior: exact same timed Deadline title/date/time; both visible-title taps opened that Deadline editor; no Save action was issued. Raw target [267,1112]-[477,1259]; Web after fix [267,1109]-[490,1262]. Pixel ink bbox Native (278,1210)-(459,1233), Web (279,1209)-(463,1232); point line Native (267,1254)-(487,1258), Web (268,1257)-(488,1259)
    evidence: .local/android-test/reports/native095-deadline-title-20261106/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-TIMED-POINT-HITBOX-20261106.md; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    limitation: Native retains point events within an 80px viewport margin; Web host/runtime still omits events outside exact visibleStart/visibleEnd. Web accessibility target is 6px taller and 13px wider in this AVD. Timeline/full-surface parity remains PARTIAL; Product Cutover HOLD
  NATIVE_095_AI_BUBBLE_CHROME:
    status: NARROW PASS (AI page remains PARTIAL)
    source_baseline: b1f7629f0c52812e1887443872f43aa6c49908c4
    reference_apk_sha256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
    current_apk_sha256: 42edab078121d1ca69c80cf828b7406af868d04214baacb610276c1edfc7b849
    default_skin_sha256: ee2bc91820f6222f4ed820f35befada7e97b48a43dbc144496f610cc673ce0c8 (10 files, exactly one APK .pcskin, source/APK bytes match)
    android: frozen APK screenshot and updated Web v3 screenshot at API 35, 1080x2400, 420dpi; WebView 124.0.6367.219; manual Web capture on PowerCal_Wave2_QA35_20261104/emulator-5556
    geometry: same 42 synthetic messages; first user bubble x/y fill bounds Native [134..588,293..435], Web [133..588,293..435]; first five messages max vertical edge delta 2 physical px, left edge delta 1 px
    tests: serial full Gradle gate 987/987 JVM, zero failures/errors/skips; both source boundaries, assembleDebug, lintDebug, AndroidTest compile PASS; target WebView method 1/1 PASS
    screenshots: frozen Native B82A5AC4C43E8AE74F3C356E73F808F20381131E8B36685189833F2F7765F157; current Web 2E540564706F69E0FCD188E1A09323DA43D97B3FAEB04F3C54FA0DD95CB264F3
    device_scope: connected task also ran 1/1 on PowerCal_Native095_RawFixture_20261106/emulator-5590; unintended enumeration, no unchanged-state claim, no further commands there
    evidence: .local/android-test/reports/native095-ai-bubbles-20261107/manifest.md; docs/issues/PC-ISSUE-SEPARATION-NATIVE-AI-BUBBLE-CHROME-20261107.md; docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md
    limitation: AI menu visual layout, attachment/proposal/error/retry, composer and whole-page behavior remain open; dark mode was computed-style asserted, not screenshot-compared; Product Cutover HOLD
BLOCKERS: Whole-surface Native-to-Web parity remains incomplete. The just-verified left-wing initial edge is a narrow PASS; date-only Deadline Android A/B, other dense/multilane/locked/empty Timeline states and accessibility remain open. Month, Memo (including the Web-only load-more control), editor/Draft Deck states, AI attachment/proposal/error/retry states and full-page comparisons, and Settings routes remain open. AI history paging and the 6000-code-unit input boundary have narrow PASS evidence, but the AI page remains PARTIAL. Full Browser E2E now passes on the current test suite. Forced checkpoint-I/O failure, physical/OEM reminder delivery and live AI provider reliability remain NOT TESTED. Product Cutover remains HOLD.

AUTHORITATIVE_PLAN: docs/POWERCAL_SINGLE_DEFAULT_SKIN_EXECUTION_PLAN_2026-11-03_R2.md
ACCEPTANCE_MATRIX: docs/agent-handoff/SINGLE_SKIN_ACCEPTANCE.md
PROTECTED_ASSETS:
  - docs/agent-handoff/WORKTREE_DISPOSITION.md
  - Existing protected Worktrees, user files, Studio projects, design sources, and test fixtures.
