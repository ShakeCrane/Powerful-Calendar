PROJECT: Powerful-Calendar
PLAN: PLAN-2026-11-03-R2
BRANCH: codex/skin-single-default-closure
OWNER_VISUAL_BASELINE: frozen native PowerCal 0.9.5-rc.1 (2026-10-27); supersedes current Clear appearance and unapproved Claude concepts
OWNER_VISUAL_SOURCE_SHA: b1f7629f0c52812e1887443872f43aa6c49908c4
OWNER_VISUAL_REFERENCE_APK: .local/android-test/artifacts/physical-qa/PowerCal-0.9.5-rc.1-b1f7629-debug-core-HOLD.apk
OWNER_VISUAL_REFERENCE_APK_SHA256: DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76
WAVE2_START_HEAD: 3b54097d6383c0349b5d3d638c228320e3c8e22c
WAVE3_START_HEAD: 82b456fd2bfc15df379fd15d967fac3da57b899d
LATEST_CODE_HEAD: dcd283a9d6cc6cfa1b7b08609fd8c2f4f6162bdb (AI message roles, Host-owned long-press actions, and copy behavior aligned to frozen Native; overall Native 0.9.5 parity remains IN_PROGRESS)
LATEST_VERIFIED_HEAD: dcd283a9d6cc6cfa1b7b08609fd8c2f4f6162bdb (full serial Gradle gate 975/975; API 35 WebView long-press and Store invariance 1/1; Studio full suite 86/89 with three recorded failures)
CURRENT_TASK: NATIVE_095_VISUAL_PARITY_IMPLEMENTATION
TASK_STATUS: IN_PROGRESS
PRODUCT_CUTOVER: HOLD
TASK_BASE_HEAD: e41246d7ebe5b329adbdc657bd3e53735695efa1
WORKING_APK_SHA256: 40bf867049f303ea32da6b3c06d3321c30f0a56ba5aa20b2f07a38d1d07b770d
WORKING_DEFAULT_SKIN_SHA256: 81a99440f5f9e67608460611fece5825c61229852a8372b2455216086ee88456
WORKING_DEFAULT_SKIN_ID: powercal-clear-default
NATIVE_VISUAL_PARITY_AUDIT: docs/agent-handoff/NATIVE_095_VISUAL_PARITY_AUDIT.md (inventory complete; implementation and full parity remain IN PROGRESS)
PREVIOUS_TASK_STATE: Q-05 single-default startup migration and protected-branch cutover (PASS; public release remains HOLD)
LAST_COMPLETED_TASK: AI message roles, Host-owned long-press menu, and clipboard resolution slice (PASS; original APK menu screenshot NOT TESTED; overall AI page remains PARTIAL)
LAST_COMPLETED_COMMIT: dcd283a9d6cc6cfa1b7b08609fd8c2f4f6162bdb
LAST_ATTEMPTED_TASK: Native 0.9.5 visual and interaction parity migration (IN PROGRESS; see audit and latest Android evidence)
LAST_ATTEMPTED_COMMIT: dcd283a9d6cc6cfa1b7b08609fd8c2f4f6162bdb (AI message menu slice; see issue report and audit appendix)
NEXT_TASK: NATIVE_095_VISUAL_PARITY_IMPLEMENTATION
NEXT_ACTION: Migrate the measured Month differences next: reduce selected-day CSS radius from 24px to the frozen Compose 14dp chip radius, then re-capture the same-device/same-date Native APK and Web pair. Measure remaining calendar row height, panel surface, and agenda card spacing before changing them. AI remains PARTIAL; original APK message-menu screenshot, attachment authorization, proposal review, and error/retry remain open. Product Cutover remains HOLD.
LAST_TASK_EVIDENCE:
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

BLOCKERS: TalkBack focus order, forced checkpoint-I/O failure, physical/OEM reminder delivery and live AI provider reliability remain NOT TESTED; Studio full Python has two existing architecture/recovery source-literal failures and Puppeteer browser E2E is blocked by a missing local dependency; Memo and later page parity remain outstanding. Q-03 accessibility, Q-04 physical/OEM and historical SEP/SSA sign-offs remain incomplete. Public Product Cutover remains HOLD.

AUTHORITATIVE_PLAN: docs/POWERCAL_SINGLE_DEFAULT_SKIN_EXECUTION_PLAN_2026-11-03_R2.md
ACCEPTANCE_MATRIX: docs/agent-handoff/SINGLE_SKIN_ACCEPTANCE.md
PROTECTED_ASSETS:
  - docs/agent-handoff/WORKTREE_DISPOSITION.md
  - Existing protected Worktrees, user files, Studio projects, design sources, and test fixtures.
