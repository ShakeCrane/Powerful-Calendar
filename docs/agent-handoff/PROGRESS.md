# Core freeze progress
## PC-ISSUE-135 — Android process restart draft persistence (REC-P2-001 RECONSTRUCTED)

- Added an Android instrumentation regression using a unique synthetic SQLite database. On a fresh API 35 AVD, 13 runner invocations across 13 distinct process IDs passed, with 12 host force-stops. One AI-origin PENDING draft retained its ID, event sequence and queue order; formal events, commit history and reminder intent remained absent. Only the generated synthetic database was removed after the final successful assertion.
- Separately verified one Android VM reboot on a second fresh API 35 AVD: boot count advanced 1→2, the installed package remained, and the same synthetic pending draft retained its identity/order/status across reboot. Three instrumentation invocations passed; the synthetic database was removed only after final assertions. This does not cover physical/OEM, crash/low-memory, or live chat-session recovery.
- Full offline Gradle boundary/test/Debug APK/AndroidTest APK/lint run passed: 469/469 JVM tests, 58 suites, 0 failures/errors/skips; lint 0 errors / 58 warnings. Debug APK SHA-256 `35EB92E478AD2DEBBD53D03682844D5B1F56E24438469B4A38E89D247B7E40F4`; AndroidTest APK SHA-256 `70933105F35F5486F316148DFDA1C86A6FA7AE93CFC5DFFE78BEF5EFF9BCC92D`.
- Independent report: `docs/issues/PC-ISSUE-135-android-process-restart-draft-recovery.md`. Run log SHA-256 `67507A6BB830D72B4A4930A8B4A9F5F4F5C77138D39BEC3E36CEF11B85F3C6DE`; summary JSON SHA-256 `2C7771DA82228515788C8ED28EE513FA49E7503A8559A422DC05B6F58C5D839D`; metadata-only DB inventory SHA-256 `4BFBBC3B9CE6F6FF5E8A4F5231A75FB59F8E179ED6BA4B473CC1912C604CFE0A`. A prior exploratory attempt on an already-running emulator was excluded; its default-named database was not read or removed, and the emulator was not reused.
- System reboot log SHA-256 `AA86B2BEDF5E1936A852F1967B32B8B8EAAA25B9F1A2D2540674D88A13E076B9`; summary SHA-256 `A72CDB86C4F46F0213BD9D0B6C37385F8A2C86D5BEA80E604DBB159F43549E14`; metadata-only post-reboot inventory SHA-256 `066D3B0F21854B5C72A4F6B804D184D126EFC2E9D18E80600F5F1B1DC326F757`.
- Scope excludes live chat transcript, Activity/UI restoration, system reboot, crash/low-memory and physical/OEM lifecycle behavior. The overall freeze remains HOLD. The acceptance CSV's risk-based P0/P1 reclassification is pending per the user's direction; historical 209 IDs and the old 126/82 split are not treated as a gate.

## PC-ISSUE-002 — read-only AVD configuration recheck

- API 35 emulator-5554 has no `dev.powerfulcalendar` package. Emulator-5556 has the package, but its shared-preference filename listing contains only `powercal-onboarding.xml` and `powercal-ui.xml`; `powercal-ai.xml` is absent. No preference values or credentials were read, the app was not launched, settings were not changed, and no provider request was sent. Evidence JSON SHA-256 824CF934453870ECA21A97D0BB3825801F70FA6918DDBD7D0FEE10F88F6CA797.
- This confirms the live retest is blocked by unavailable app configuration; it does not test product behavior. REC-P1-047 remains BLOCKED and PC-ISSUE-002 remains PARTIAL / HOLD. Structural validation of the updated ledger passed: artifact SHA-256 BD2D976A66A48EF71C543DE0906D2257BCB4F066147BAD7BF61B07D7E18E3D7C; ledger SHA-256 D500FE6D936C71C9514D2FA30645438DC74F3318A6B89BBE373B62ABCE093921; counts remain 164/0/8/30/7.
- Current-code local failure-path rerun passed 7/7: synthetic structured HTTP 429, free-model empty HTTP 200, and five completion-policy cases. No provider request was made; this does not replace REC-P1-047's blocked live free-provider requirement. Log SHA-256 84AEB6F1B874575ABB0979349EA9A459285D5A6B0F34ABC00A0ADCBB7A3A7759; test XML SHA-256 5422891B1A6C8451FED0314B39B4A26F4AECBB6A92C3ABBB817620E1F7DAE92A and 2165CDF68DA513B11BD0E66EA882AFBD7E51B33191B7410797AFCABCA124E5DE.

## PC-ISSUE-003 — current-code backup/restore core rerun

- `BackupEnvelopeTest` 24/24 plus three focused `PowerStoreTest` restore/schedule/high-water cases passed 27/27, zero failures/errors/skips. This is isolated JVM/Robolectric SQLite coverage; no UI, emulator app, or real business database was touched.
- Log SHA-256 C6AFA52BB816008BDD3A0DBF7AEDC7B2705A4D0EC33494A2B401FFD6B570FE7B; XML SHA-256 9A97B489626CDA0AE616F666C4B256A3DBB50B0B5847322F8C0DAADA3374992D and EB3B885E3A977385C93EC8D054B7D3407E662BABE1D13FA8EF0A977D6C7A8EB3. Real DocumentsUI/provider lifecycle and physical-device coverage remain open; PC-ISSUE-003 stays PARTIAL / HOLD.

## DST policy spec audit

- `PRODUCT_BASELINE_G1-G11.md` §9 explicitly leaves DST details unresolved. Current code documents gap-forward / earlier-offset fold handling, but that implementation comment is not product approval. `OccurrenceWindowTest` (19/19) and `TemporalProtocolTest` (38/38) reran successfully; current tests include a spring-gap case and wall-clock recurrence across DST, but no fold-specific regression was found. Targeted log SHA-256 6DD225D91BA3C9D9333E9FE7B55137B9679CE90E74FF361B3140811B955193F3; XML SHA-256 F913E61734A977C392D5F160F79B6D5BE85B25381A34D67DBE492A3DC1266118 / 815BED4DFC2EB57505FE9FEA5405533876C34105033B0A3DCCA186C843F23F24. REC-P0-074/075 remain SPEC UNRESOLVED pending owner decision.

## PC-ISSUE-001 — external ICS handler availability

- Read-only `ACTION_VIEW`/`text/calendar` package queries on API 35 emulators 5554 and 5556 both returned `No activities found`. No Activity was launched, no app was installed, and no application data was touched. Evidence JSON SHA-256 AC76851C91A02993DF200805345D78FB698396FE4D90A2D8B0BD4B37BEFA4933. Structural validation passed with 209 unique rows and unchanged counts; corrected artifact SHA-256 AFC1222BB7D94941E00E9676501618E7473B95642832384AF657A9F2977A0A21, ledger SHA-256 15605E858B6F6FE9519270ADB2442E78664D8D6552E5F3FF2EB1FE5B2AFFA552.
- This confirms an environment block only; no external ICS import/re-export behavior was tested. `REC-P1-081` and PC-ISSUE-001 remain BLOCKED / PARTIAL; physical-device tests remain the user's scope.

## PC-ISSUE-002 — bounded free-provider retry readiness

- Hardened the Android API 35 live probe so no paid/free provider is contacted unless the explicit instrumentation argument `powercalLiveProbe=deepseek|free` is supplied. The two accepted model IDs are fixed in test code; model override remains in memory, and the flow uses synthetic input plus a unique temporary database.
- The emulator retest was **not sent**: `emulator-5554` currently has no `dev.powerfulcalendar` package (`run-as` reports unknown package). No app was reinstalled and no credential was read or restored. The last free-provider trace remains chat HTTP 200 followed by structured HTTP 429; Issue 002 and REC-P1-047 remain PARTIAL / BLOCKED.
- Full offline regression passed 469/469 across 58 suites, zero failures/errors/skips; source boundary, Debug/AndroidTest APK assembly and lint passed (0 errors / 58 warnings). Log SHA-256 3AFAEC67B24C92E808B0767939F35AC86BA427E8D3D742D10095C5CC7AE7805E. Test-only commit c33eb36a9308066983667cbf0091b8c451a27806 is pushed. This did not execute Android instrumentation or make a provider request.

## PC-TECH-006 — lint risk classification

- Classified the latest lint XML: 58 warnings / 0 errors across platform/target (4), dependency suggestions (6), Compose conventions (14), KTX suggestions (19), launcher/resources (13), and redundant SDK conditions (2). No code, UI, dependencies, manifest, or design assets changed; this does not claim a security scan.
- The `targetSdk=35` warning and portrait-lock warnings identify an Android 16 compatibility risk: fixed orientation may be ignored in some device configurations. API 36 orientation behavior is unverified and remains open; no UI work was performed. Report: docs/issues/PC-TECH-006-lint-warning-risk-classification.md. Evidence is the PC-ISSUE-134 full Gradle log and lint XML; overall freeze remains HOLD.

## PC-ISSUE-134 — authorized DeepSeek live chat-to-pending-draft flow

- On API 35 emulator-5554, one authorized DeepSeek V4.1 Flash chat and one structured-proposal request returned HTTP 200/200. A synthetic ACTIVITY became a PENDING draft in a unique temporary SQLite database and retained identity/order/status across close/reopen; no formal write occurred. Calendar context was disabled, model override was in memory, and credentials were not logged. No UI or physical device was used.
- Instrumentation passed 1/1. Full Debug boundary/test/APK/AndroidTest APK/lint passed 469/469 across 58 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Test commit 477ddf44f1199e3316c5981e0a640c41efcdd416 is pushed. Report: docs/issues/PC-ISSUE-134-deepseek-chat-to-draft-live-flow.md.
- This signs only the authorized DeepSeek model-to-local-draft route. REC-P1-047 remains BLOCKED because the free provider's structured stage returned HTTP 429; PC-ISSUE-002 remains PARTIAL / BLOCKED and the freeze remains HOLD. Ledger totals remain 164 PASS / 0 FAIL / 8 BLOCKED / 30 NOT TESTED / 7 SPEC UNRESOLVED. Structural-only artifact .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-134.json SHA-256 FB25654B9A34DFD676713F580D1C955B24E6BCDBBE504ECE8D16150A8582465A; ledger SHA-256 CD6275A893058EB678FBD9B3BEA04EAEAB6BFEEE26BFEFDC18366074852C8E58.

## PC-ISSUE-133 — AI chat-to-pending-draft local pipeline

- Added an API 34 Robolectric loopback regression linking chat-marker parsing, current-user source isolation, structured proposal parsing, AiDraftService persistence and SQLite reopen. A fabricated model source is discarded; the original input becomes one PENDING draft with stable identity/order; no formal event/history/schedule intent is written. No provider, UI, device or DeepSeek call.
- Focused test passed 1/1. Full Debug boundary/test/APK/AndroidTest APK/lint passed 469/469 across 58 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Test commit b4f5179efa689015bb5ec73a2987a5e76f2c6b82 is pushed.
- Report: docs/issues/PC-ISSUE-133-ai-chat-draft-pipeline.md. This is local stub evidence only; REC-P1-047 remains BLOCKED on the live free-provider structured HTTP 429, so PC-ISSUE-002 and the overall freeze remain PARTIAL / HOLD. Ledger counts remain 164 PASS / 0 FAIL / 8 BLOCKED / 30 NOT TESTED / 7 SPEC UNRESOLVED. Structural-only artifact .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-133.json SHA-256 CA6AD79C2254613BE21C06FCD23A6B49756227190F32CA0C672A7CF507D3002C; ledger SHA-256 F2B851D069C7322AAFA636E7406C3B3FA9A9C94B608BD1E3DD999F6CD3966820.

## PC-ISSUE-132 — SAF URI-grant revocation safe failure (REC-P0-107 narrowed PASS)

- A synthetic API 35 ContentProvider granted then revoked read access. Android enforced the revocation with SecurityException; safe permission wording and unchanged event/history/pending/identity/sequence state were verified in a disposable SQLite database. The production SAF reader is now shared through **SafBackupReader**. No UI was opened; real DocumentsUI/provider lifecycle and physical/OEM behavior remain untested.
- Emulator instrumentation passed 1/1. Full Debug boundary/test/APK/AndroidTest APK/lint passed 468/468 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Code/test commit 0d83d48f6bea18b64e8965ab62bcb39d55f164a8 is pushed.
- Report: docs/issues/PC-ISSUE-132-saf-uri-grant-revocation.md. PC-ISSUE-003 remains PARTIAL / HOLD. Ledger: 164 PASS / 0 FAIL / 8 BLOCKED / 30 NOT TESTED / 7 SPEC UNRESOLVED; P0 120/0/0/1/5, P1 44/0/8/29/1. Structural-only artifact .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-132.json SHA-256 7D3BDB2012A2838CE6CE85F55682B38C1E211EF0A3DD64AAD55C924614AD23E2; ledger SHA-256 FD79266CD97115B90641DA75F49147F6C706FDD4B60F852663DFCB2B3754AD74. Overall freeze remains HOLD.

## PC-ISSUE-131 — AI targetless REPLACE clarification guard (REC-P1-050 remains NOT TESTED)

- Reproduced that a blocked targetless REPLACE exposed `#null` instead of asking which event to change. Updated the core parser warning while retaining the existing blocked/no-write behavior; added a Robolectric API 34 parser → AiDraftService → SQLite-reopen regression with two identical formal targets. This is core-only; no UI layout, provider, or physical-device test.
- Pre-fix targeted test failed on the clarification text. Post-fix targeted checks passed 2/2. Full Debug boundary/test/APK/lint passed 468/468 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Code/test commit `e8daade48e78997a94c86645730b0226dfdf1cab` is pushed.
- Report: `docs/issues/PC-ISSUE-131-ai-ambiguous-replace-target.md`. `REC-P1-050` stays NOT TESTED because live-model and chat UI disambiguation remain unverified; totals remain 163 PASS / 0 FAIL / 9 BLOCKED / 30 NOT TESTED / 7 SPEC UNRESOLVED. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-131.json` SHA-256 `4F97B8068029A5BBDAA5052A01A556E5FDA925290694D6EA5C24E7E3A4EB5929`; ledger SHA-256 `7C6EF6938ECAF938656A3F25B8CE30CAEEC462DC2173D2C604B53BD791FC8F72`. It validates only 209 unique rows and 126/82/1 priorities; product_behavior_tested=false. Overall freeze remains HOLD.

## PC-ISSUE-130 — DeckController live edit state during autosave (REC-P1-043 narrowed PASS)

- Added currentId/dirty assertions while background autosave is blocked: the newest accepted form stays visible on the same card and remains dirty until storage catches up. Existing regression checks nonblocking edit and saves the latest version after release. No production/UI changes.
- Focused test passed 1/1. Full Debug boundary/test/APK/lint passed 467/467 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Test commit `d838c9b6d6d7ecb8e2f6199a429d07eca0a86e12` is pushed.
- Report: `docs/issues/PC-ISSUE-130-deck-controller-live-edit-state.md`. Ledger: 163 PASS / 0 FAIL / 9 BLOCKED / 30 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5, P1 44/0/8/29/1. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-130.json`, SHA-256 `39340110A14157DEA49F09AF6206579C953D02D4355032D042C32CAD9A160A16`; ledger SHA-256 `2A02489CD27FF377E677ABDDFFFFA93190966CC53E38D6F738226CEB554D0338`. Overall freeze remains HOLD.

## PC-ISSUE-129 — DeckController movement clamps (REC-P1-040 narrowed PASS)

- Extended the existing core movement test to verify reverse movement reaches the first card and clamps there; the same test verifies one-card forward movement and last-card clamping. No production/UI changes; gesture recognition and Compose/device mapping remain untested.
- Focused test passed 1/1. Full Debug boundary/test/APK/lint passed 467/467 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Test commit `07557fe62a7ac4506f38618028aff9439b86da95` is pushed.
- Report: `docs/issues/PC-ISSUE-129-deck-controller-move-clamping.md`. Ledger: 162 PASS / 0 FAIL / 9 BLOCKED / 31 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5, P1 43/0/8/30/1. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-129.json`, SHA-256 `46B90076E13454DF792678500CA49F1D3452DBFB77825E5C567950BD06AF17D8`; ledger SHA-256 `0230EDFF3547F140680396A60984A0A70DA819FF0C30CD1EA5646C1E6D410E6D`. Overall freeze remains HOLD.

## PC-ISSUE-128 — DeckController completion counts (REC-P1-046 narrowed PASS)

- Added a core-only regression that publishes one card and discards the final card, then checks completion reports published=1 and discarded=1. The initial attempt hit the existing one-second terminal-action guard because the fake clock did not advance; adding `tick()` fixed the test setup. No production/UI changes; timeline-camera restoration and SQLite-backed stats remain outside scope.
- Corrected focused regression passed 1/1; full Debug boundary/test/APK/lint passed 467/467 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Test commit `5c741192727721cc26831a01fb9b9c9cd9b2b408` is pushed.
- Report: `docs/issues/PC-ISSUE-128-deck-completion-counts.md`. Ledger: 161 PASS / 0 FAIL / 9 BLOCKED / 32 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5, P1 42/0/8/31/1. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-128.json`, SHA-256 `C04462462D939BA4BFB4765FB6960E1219741F0C46A280836E567D1E460B0A44`; ledger SHA-256 `015CE8D4281738348B6BAFE9780D1912F0EF172998695228ADB0BF4929BDA878`. Overall freeze remains HOLD.

## PC-ISSUE-127 — DeckController retryable save and card switching (REC-P1-044/045 narrowed PASS)

- Extended the existing storage-failure regression to recover the fake store and retry the same accepted input; verified it is staged and dirty/error/busy clear. Existing controller tests verify latest-text stage before card navigation and refusal to switch after stage failure. No production code changed; claims are limited to JVM controller behavior with fake persistence, excluding UI, SQLite durability, exit/lifecycle and devices.
- Focused regression passed 1/1. Full Debug boundary/test/APK/lint passed 466/466 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Test commit `41c7f849438128960b6f453c44563141feb0d1e5` is pushed.
- Report: `docs/issues/PC-ISSUE-127-deck-controller-retryable-save.md`. Ledger: 160 PASS / 0 FAIL / 9 BLOCKED / 33 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5, P1 41/0/8/32/1. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-127.json`, SHA-256 `77CEBCB85D696C3A5121B78EE1AED29F611AA6DFBB86B7FD8642109B0FF6E5E6`; ledger SHA-256 `38E9BEE15710B9F59029E6C992B52BE1D48721A7320754994A044F08E16157C`. Overall freeze remains HOLD.

## PC-ISSUE-126 — AI commit authorization gate (REC-P1-054 narrowed PASS)

- Reproduced that `PowerStore.commit(actor="ai")` without an `AiSession` could proceed to formal commit. The core store now denies AI actors without a scoped session. The regression confirms no object/history/intent write and a pending draft; existing valid-session and replay tests remain green.
- Pre-fix targeted failure; post-fix targeted 1/1 PASS; full Debug boundary/test/APK/lint passed 466/466 across 57 suites, zero failures/errors/skips, lint 0 errors / 58 warnings. Code/test commit `fc0e69ac90d25878c660d046a98818629d6c1d39` is pushed.
- Report: `docs/issues/PC-ISSUE-126-ai-commit-requires-authorization.md`. This signs only the PowerStore gate; user-confirmation UI and live AI remain untested. Ledger: 158 PASS / 0 FAIL / 9 BLOCKED / 35 NOT TESTED / 7 SPEC UNRESOLVED. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-126.json`: SHA-256 `3DC7D2B92820502212C3BF4CCEAECFF56D67BC01EF16FAE1773F0668AB51A75B`; ledger SHA-256 `A11035534F763B61330940D8970B45AABA0D2CAA3AAD1F56804C109FB6006338`. Overall freeze remains HOLD.

## PC-ISSUE-125 — AI proposal dependent-write rollback (REC-P1-059 narrowed PASS)

- Added assertions that an injected schedule-intent write failure leaves no formal event, commit history, or desired intent and preserves the pending draft after SQLite reopen. Other independent batch items still commit; a new authorization retries the failed item. No production source changed.
- Targeted Robolectric API 34 test passed 1/1; full Debug boundary/test/APK/lint passed 465/465 across 57 suites, zero failures/errors/skips, lint 0 errors / 58 warnings. Test commit `237486011a64a4598b3dd7a3183ee2c09314f034` is pushed.
- Report: `docs/issues/PC-ISSUE-125-ai-dependent-write-rollback.md`. This signs only one proposal's dependent SQLite writes; no cross-proposal dependency group, AI network or UI claim. Ledger totals: 157 PASS / 0 FAIL / 9 BLOCKED / 36 NOT TESTED / 7 SPEC UNRESOLVED. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-125.json`: SHA-256 `49EB75C9641F72F273EA5C4532EE95B9B30E8EC67D69D273315FF33EFB59AEA0`; ledger SHA-256 `DB65C04E298584BB31EEB1B29DC0985CF2069A9FB27CC63382FDA3EB7DFA1452`. Overall freeze remains HOLD.

## PC-ISSUE-124 — core timeline lane allocation (REC-P1-016/017)

- `TemporalProtocolTest` passed 38/38; five existing pure-model cases directly cover deterministic stable ordering, input-order independence, independent lanes and lane reuse. No app/test source, UI, emulator or physical device was changed or tested.
- Report: `docs/issues/PC-ISSUE-124-timeline-lane-core-signoff.md`. Targeted log SHA-256 `88772FC37E170E12B4D118852B4BFEB7251B18606A4AA716771914B79EC71321`; XML SHA-256 `F51185351FE75B645F0DB01F7D5B41FBB6CBB2CB33D9BE5BD000FE650B838E34`.
- REC-P1-016/017 are PASS for the narrowed core model only. Ledger totals: 156 PASS / 0 FAIL / 9 BLOCKED / 37 NOT TESTED / 7 SPEC UNRESOLVED. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-124.json`: SHA-256 `B37DF31495E1AC7DF2F06AC93AD1246EC9E252CF1A2B947892B7CA2B112AC1C8`; ledger SHA-256 `C659DC487695E0C117A7205092EB1C30C5B08CED4ED1A62C7EB6A24AADBD2406`. Overall freeze remains HOLD; physical-device testing is user-owned.

## PC-ISSUE-GREEDY-LABEL-FOLDING — REC-P1-028 long-title measurement sign-off

- Fixed long-title truncation by measuring a grapheme-boundary prefix plus an explicit ellipsis at the existing 10sp floor. Full event bounds and accessibility title remain unchanged.
- Focused Compose tests passed 8/8; greedy policy tests 8/8. Full Debug boundary/test/APK/lint regression passed 465/465 across 57 suites; lint 0 errors / 58 warnings. Code/test commit `2fe0c6643547dc0c195feeb602bb47460398c782` is pushed.
- Report: docs/issues/PC-ISSUE-GREEDY-LABEL-FOLDING.md. REC-P1-028 is signed PASS narrowly for Robolectric API 35; no AVD screenshot was captured because both existing emulators were online and no isolated AVD was registered. Core freeze remains HOLD at 154 PASS / 0 FAIL / 9 BLOCKED / 39 NOT TESTED / 7 SPEC UNRESOLVED.

## PC-ISSUE-121 — AI current-input source isolation guard (PC-ISSUE-002 remains PARTIAL/BLOCKED)

- Added a local `AiClient.chat` loopback regression: when the model response includes a fabricated `source`, the client hides the internal marker and exposes only the current raw user text as `proposalSource`. No production changes or real provider call.
- Focused test passed 1/1. Full Debug boundary/test/APK/lint passed 462/462 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-121-ai-current-input-source-isolation.md. Test commit `6345dbba2216b625bf95eaad7651d3b6cfc89275` is pushed.
- Local evidence does not prove live-model compliance. PC-ISSUE-002 remains PARTIAL/BLOCKED on prior free-provider HTTP 429/503; core freeze remains HOLD at 148 PASS / 0 FAIL / 9 BLOCKED / 45 NOT TESTED / 7 SPEC UNRESOLVED.

## PC-ISSUE-120 — free-model empty HTTP 200 core recovery guard (PC-ISSUE-002 remains PARTIAL/BLOCKED)

- Added a local loopback integration regression for the existing no-blind-retry policy: first-call budget is 2048; an empty HTTP 200 with `finish_reason=stop` produces a structured-stage warning, no proposal/raw reply, and exactly one request. No production code or external provider call.
- Focused test passed 1/1. Full Debug boundary/test/APK/lint passed 461/461 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-120-ai-empty-http200-core-recovery.md. Test commit `b51b0e830c9ff968b994cda564dcdd481986d46e` is pushed.
- PC-ISSUE-002 stays PARTIAL/BLOCKED because the provider proposal stage previously returned HTTP 429/503; no retry around rate limits or paid provider request. Per latest user scope, no UI work is in this continuation. Core freeze remains HOLD at 148 PASS / 0 FAIL / 9 BLOCKED / 45 NOT TESTED / 7 SPEC UNRESOLVED.

## PC-ISSUE-119 — AI incomplete-field core recovery sign-off (REC-P1-051 narrowed PASS)

- Missing Activity times remain blank; invalid submit creates no formal event/history, INVALID draft survives SQLite reopen, and repair returns it to PENDING for one commit. An undecidable missing kind requests clarification without allocating a proposal, draft, or event identity. Provider/API 35/device paths remain outside this sign-off.
- Targeted parser/PowerStore regressions passed 4/4. Full Debug boundary/test/APK/lint passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-119-ai-incomplete-proposal-core-signoff.md.
- Freeze remains HOLD: 148 PASS / 0 FAIL / 9 BLOCKED / 45 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5; P1 29/0/8/44/1. Structural-only validation .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-119.json, SHA-256 9D9C9ADF65C655E8C985772B3B0144FDB8CACF3D27A311E002E974A524F15512; ledger SHA-256 70204387131BD14FD70A96F43298598B6D365E68BB754DDDFFF3726CAD90E200.

## PC-ISSUE-118 — mixed CREATE/REPLACE core draft isolation sign-off (REC-P1-049 narrowed PASS)

- One local structured response yields independent CREATE and REPLACE operations; `AiDraftService` creates two pending drafts, leaves the formal CREATE absent, and does not mutate the REPLACE target. Provider/API 35, presentation, approval, and reopen persistence are outside this sign-off.
- Targeted Robolectric API 34 regressions passed 2/2. Full Debug boundary/test/APK/lint passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-118-mixed-proposals-core-signoff.md.
- Freeze remains HOLD: 147 PASS / 0 FAIL / 9 BLOCKED / 46 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5; P1 28/0/8/45/1. Structural-only validation .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-118.json, SHA-256 F2490DF21C9C00691FC71B760F729CF18D830BEFA2A161F4F3865FF2176D985C; ledger SHA-256 540B486F5493752F09B10DA863026ECB098E3134BDB25B4E186F4B09AA83A840.

## PC-ISSUE-117 — AI HTTP 429 client core contract sign-off (REC-P1-062 narrowed PASS)

- The local loopback HTTP 429 stub yields a structured-proposal-stage/status warning, no proposal/raw reply, and no provider error body or API key disclosure. No external provider was called; API 35/provider and presentation paths remain outside this sign-off.
- Targeted Robolectric API 34 regression passed 1/1. Full Debug boundary/test/APK/lint passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-117-ai-http429-core-signoff.md.
- Freeze remains HOLD: 146 PASS / 0 FAIL / 9 BLOCKED / 47 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5; P1 27/0/8/46/1. Structural-only validation .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-117.json, SHA-256 3AD7FA01ABB82266470A7C1C2881E421E6F227D6436EB39187E33557503A2CB0; ledger SHA-256 B932FEB0957DE7534F42418D43076DFB1B58D3FFD4D9C279DE87007BE8458816.

## PC-ISSUE-116 — repeated commit core idempotency sign-off (REC-P1-057 narrowed PASS)

- Repeating `PowerStore.commit` for one draft returns the original revision/commitId with `duplicate=true`; history remains one row. API 35/provider approval-entry and device paths are outside this sign-off.
- Targeted Robolectric API 34 regression passed 1/1. Full Debug boundary/test/APK/lint passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-116-repeat-commit-core-idempotency-signoff.md.
- Freeze remains HOLD: 145 PASS / 0 FAIL / 9 BLOCKED / 48 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5; P1 26/0/8/47/1. Structural-only validation .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-116.json, SHA-256 8293459B130547FDD612B8249859D0E09B5355FDE9CA47D3E11CB83912F47DB9; ledger SHA-256 7D76245F0CC198183152E0B404FA7B2B947258209AACA4C4130C0CC782CA865A.

## PC-ISSUE-115 — AI CREATE draft core persistence sign-off (REC-P1-056 PASS)

- A local parsed CREATE proposal becomes one PENDING draft without formal event/history; after SQLite reopen its ID, event sequence, version, operation, form, status, and queue order remain unchanged.
- Targeted Robolectric API 34 regression passed 1/1. Full Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-115-ai-draft-core-persistence-signoff.md. API 35/provider selection is outside this sign-off.
- Freeze remains HOLD: 144 PASS / 0 FAIL / 9 BLOCKED / 49 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5; P1 25/0/8/48/1. Structural-only validation .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-115.json, SHA-256 4B62D6669634D5B6C20A4C47E05585288019D311B95C43D1D34334535E5B7030; ledger SHA-256 42386817457F1EBDFE6C5BF131D068B492E9BBC2301F68C99C28B7201B8038A2.

## PC-ISSUE-114 — explicit single-occurrence core override sign-off (REC-P0-078 PASS)

- The explicit occurrence-key PowerStore operation moves only the selected slot from 07:30 to 09:15; the anchor, original keys, other slots, and event identity remain stable. SQLite reopen preserves the record/projection, one identity, and two history rows.
- Targeted Robolectric API 34 regression passed 1/1. Full Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-114-single-occurrence-core-signoff.md. Default-scope selection and other edit scopes are outside this sign-off.
- Freeze remains HOLD: 143 PASS / 0 FAIL / 9 BLOCKED / 50 NOT TESTED / 7 SPEC UNRESOLVED; P0 119/0/1/1/5; P1 24/0/8/49/1. Structural-only validation .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-114.json, SHA-256 3048D54BB5F8CCEB6762269685B535DD2AC5791EDFE0FA98744877040872B1F4; ledger SHA-256 1CC18C043E491828FCB23BCE54B02192ACECA1B63C376A9C57268EE1297BEAF1.

## PC-ISSUE-113 — AI/ICS core read-only projection sign-off (REC-P0-082 PASS)

- Scoped to the core SQLite-to-ICS and AI request-snapshot projections; reading does not create a second event source or mutate formal rows, history, reminder intent, identity count, or sequence preview. The local AI stub receives one formal event and produces no proposal or draft.
- Targeted Robolectric API 34 regression passed 2/2 (ICS 1/1; AI snapshot 1/1). Full Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.
- Independent report: docs/issues/PC-ISSUE-113-read-only-core-projections-signoff.md. Android platform, SAF, external calendar, and device behavior are not signed by this result.
- Freeze remains HOLD: 142 PASS / 0 FAIL / 9 BLOCKED / 51 NOT TESTED / 7 SPEC UNRESOLVED; P0 118/0/1/2/5; P1 24/0/8/49/1. Structural-only validation .local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-113.json, SHA-256 46B6F9A7ECDDE04D3FC04A998E734DBBEB54D347A7131FB40BEB3F1D7102162C; CSV SHA-256 55FB44E600F987C4B5B445382089E539949AEA23E5048804B56D16B674DDA73C.

## PC-ISSUE-112 — API 35 exact-alarm denial core sign-off (REC-P0-112 PASS)

- Revalidated the API 35 Robolectric scheduler/store denial branch: the EXACT reminder persists as BLOCKED with the exact-alarm permission hint, and ShadowAlarmManager receives no registration.
- Targeted regression passed 1/1. Full offline Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No app or test source changed.
- Independent report: `docs/issues/PC-ISSUE-112-api35-exact-alarm-core-signoff.md`. `REC-P0-112` is PASS for the core denial state only. Actual Android permission behavior and hint delivery remain untested.
- Freeze remains HOLD: 141 PASS / 0 FAIL / 9 BLOCKED / 52 NOT TESTED / 7 SPEC UNRESOLVED; P0 117/0/1/3/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-112.json`, SHA-256 `EC8710582C1E00B9BB43791493D62F53F7FAE9E9E0D5553406C80F34C7B70CC3`; ledger SHA-256 `6BEF65F539959780BFE2DF917F74E44F953BE8C7A64F051F03E6ED8E4A111EBC`.

## PC-ISSUE-111 — this-and-future recurrence core sign-off (REC-P0-079 PASS)

- Revalidated the four direct PowerStore transaction cases: split and migrate compatible future state, injected insert rollback, first generated slot identity retention, and first non-skipped slot/remaining-count handling.
- Targeted regression passed 4/4. Full offline Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No app or test source changed.
- Independent report: `docs/issues/PC-ISSUE-111-this-and-future-core-signoff.md`. `REC-P0-079` is PASS for the core transaction criterion; scope selection and device behavior remain outside the result.
- Freeze remains HOLD: 140 PASS / 0 FAIL / 9 BLOCKED / 53 NOT TESTED / 7 SPEC UNRESOLVED; P0 116/0/1/4/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-111.json`, SHA-256 `E7C3F871A1DD2743FC045B8251D116AAE0EF0AD47626CF24ED69B889A371B3AC`; ledger SHA-256 `8F75A6EB9BA2046EF604B0B9558BECF34ECFBF8AA9AD6EC995A8FFAA6A107E2A`.

## PC-ISSUE-110 — reminder recovery receiver core sign-off (REC-P0-117 PASS)

- Revalidated six synthetic API 34 recovery branches: boot, package replacement, timezone/time change, exact-alarm permission change, and explicit reconciliation. Receiver async completion, one rebuilt AlarmManager registration, durable SCHEDULED state after SQLite reopen, and lock release passed.
- Targeted regression passed 2/2. Full offline Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No app or test source changed.
- Independent report: `docs/issues/PC-ISSUE-110-reminder-recovery-core-signoff.md`. `REC-P0-117` is PASS for synthetic receiver core behavior only. Actual Android system broadcasts and device recovery remain untested.
- Freeze remains HOLD: 139 PASS / 0 FAIL / 9 BLOCKED / 54 NOT TESTED / 7 SPEC UNRESOLVED; P0 115/0/1/5/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-110.json`, SHA-256 `F4253D7827BFD7843976CAAF94989E5F9AEA4760119C7E900D1B1C56271899CD`; ledger SHA-256 `9F935D5F3746DAD97F8382CE0D4CCC47C0A0448EA1FD49280C6B73429118F33C`.

## PC-ISSUE-109 — date-only Deadline core sign-off (REC-P0-023 PASS)

- Narrowed the reconstructed expected result from rendered START-END presentation to the core DATE-data invariant because the row declares no UI/device environment. Completion preserves `deadlineDate` and DATE precision; `deadlineMillis/startMillis/endMillis` remain null, including after SQLite reopen. Rendering is not claimed.
- Targeted Robolectric regression passed 1/1. Full offline Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No app or test source changed.
- Independent report: `docs/issues/PC-ISSUE-109-date-deadline-core-signoff.md`. `REC-P0-023` is PASS for the narrowed reconstructed core candidate; the missing historical mapping remains unresolved.
- Freeze remains HOLD: 138 PASS / 0 FAIL / 9 BLOCKED / 55 NOT TESTED / 7 SPEC UNRESOLVED; P0 114/0/1/6/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-109.json`, SHA-256 `C5BE9C34C74C38278E8BA64DD4041C67408F4C714F16B23AEA3EEA93917CD49B`; ledger SHA-256 `E6E28BDC1391820B55B9EC963AD9CF9083D7A966DB3D28F014A3D5DAC7AF2FEA`.

## PC-ISSUE-108 — whole-series core persistence sign-off (REC-P0-080 PASS)

- Narrowed the reconstructed action from “Select whole series” to “Edit whole recurring rule through EditSession” because the declared Robolectric API 34/SQLite environment excludes a scope-selection interface. The existing test updates the stored recurrence on the same event identity, preserves the original anchor, verifies the projected occurrence keys and rechecks record/history/identity after reopen.
- Targeted regression passed 1/1. Full offline Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No app or test source changed.
- Independent report: `docs/issues/PC-ISSUE-108-whole-series-core-signoff.md`. `REC-P0-080` is PASS for its declared core persistence scope; UI scope-selection behavior is not asserted.
- Freeze remains HOLD: 137 PASS / 0 FAIL / 9 BLOCKED / 56 NOT TESTED / 7 SPEC UNRESOLVED; P0 113/0/1/7/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-108.json`, SHA-256 `7554EB9924140E345AE9A3292C46CF1B64DA00374396CD48CAEA1D4B87984AB1`; ledger SHA-256 `798E12FD3FF6174450BBCF593B48D2084981098F857127A842D85E55C492E251`.

## PC-ISSUE-107 — completion-tracking reminder core sign-off (REC-P0-017 PASS)

- Re-read the candidate’s declared environment and expected result: Robolectric API 34, isolated PowerStore SQLite, pending reminder intent and same-database reopen; UI, Android system scheduling and device are explicitly outside this reconstructed row. Revalidated the existing two core regressions: both tracking directions preserve enabled/reminder state and durable intent across SQLite reopen; API 34 reconcile schedules the updated revision once and rejects the old revision.
- Targeted tests passed 2/2. Current full offline Debug boundary/test/APK/lint run passed 460/460 across 57 suites, 0 failures/errors/skips; lint 0 errors / 58 warnings. No app or test source changed.
- Independent report: `docs/issues/PC-ISSUE-107-tracking-reminder-core-signoff.md`. `REC-P0-017` is now PASS only for its reconstructed core acceptance; Android OS delivery and original historical-case mapping are not claimed.
- Freeze remains HOLD: 136 PASS / 0 FAIL / 9 BLOCKED / 57 NOT TESTED / 7 SPEC UNRESOLVED; P0 112/0/1/8/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-107.json`, SHA-256 `B8AEF4DE9137931A66578EEAE219EEB38CD8D7C01515E128693E5C5192ED0FE8`; ledger SHA-256 `4A83C16E8D29F918D79415FE119B27FACFB887DAE0BF65150D260C0AAE68E1F6`.

## PC-ISSUE-106 — API 35 exact-alarm denial core checkpoint (REC-P0-112 remains NOT TESTED)

- Added one API 35 Robolectric `ReminderValidationTest`: exact reminder commit followed by permission-denied reconciliation persists `BLOCKED`, retains the exact-alarm permission hint, and leaves `ShadowAlarmManager` empty. Core test only; no production/UI change.
- API 35 method passed 1/1; the complete `ReminderValidationTest` class passed 17/17. Full offline Debug boundary/test/APK/lint run passed 460/460 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. APK SHA-256 `138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`.
- Test commit `f30c1ce5c6e6c6e835365f751dc689ab245b5aca` is pushed. At that checkpoint, actual API 35 Android permission behavior and device guidance were untested, so REC-P0-112 remained NOT TESTED. PC-ISSUE-112 later signs the narrowed scheduler/store denial branch PASS; actual system permission behavior and guidance delivery remain untested. Reports: `docs/issues/PC-ISSUE-106-api35-exact-alarm-denial-core.md` and `docs/issues/PC-ISSUE-112-api35-exact-alarm-core-signoff.md`.
- Full log `.local/android-test/reports/freeze-20261024/pc-issue-106-verified-gradle.log`, SHA-256 `B14E35666D4C5B2159CCAFEFE12C6244C803B864E23172A266DC736A02BBD15B`. Structural-only ledger validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-106.json`, SHA-256 `BA926DB3211EF7D46F5700D4228A9577B58C4A2A8C79BC848D434495D9F72965`; ledger SHA-256 `C7912597DC9DECBBB21D5A4FB50CB8EF5665E6FA91F7A05BBBC8240AE03B2C59`.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1.

## PC-ISSUE-105 — reminder grace-window and missed recovery core regression (REC-P0-118 remains SPEC UNRESOLVED)

- Added three Robolectric API 34 core tests: receiver delivery inside the current grace constant posts `FIRED`; a late receiver records `MISSED` without notification; reconciliation records an overdue durable `PENDING` intent as `MISSED` without re-arming it. Test-only; no production or UI change.
- Focused two-suite run passed 24/24. Standard offline Debug boundary/test/APK/lint command passed 459/459 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. APK SHA-256 `138EC1363E4D74E6153CFFA61CEE4C0DF4D51416B3BD64C954FE642ED0FD4096`.
- Test commit `2d89ffba5317eb9b0164682702868e5cf75c8fe4` is pushed. Independent report: `docs/issues/PC-ISSUE-105-missed-alarm-grace-window.md`. The current code constant is 10 minutes; the product-approved duration remains unspecified, so REC-P0-118 stays SPEC UNRESOLVED.
- Full log `.local/android-test/reports/freeze-20261024/pc-issue-105-verified-gradle.log`, SHA-256 `2289F28174AF65C694D74D3048788FB6A0190830782AC21AB97336B0FF6DD755`. `:app:check` separately stops at the existing Release keystore gate; see the report.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-105.json`, SHA-256 `9FB1049D3D268E0F08127E78D62D224261AA65547400B436AFF3706D9A4B3DC7`; ledger SHA-256 `93F9CE96FAC8E55041DE5E715BAC6CB09004DAABE834707FA1B45695BC9D045F`.

## PC-ISSUE-104 — AI shared-context read-only projection (candidate remains NOT TESTED)

- Added a local HTTP stub regression that passes one SQLite formal event snapshot to `AiClient`. The request contains exactly one event with its real identity/revision; the response creates no proposal, and rows, history, reminder intent, identity count, and sequence preview remain unchanged.
- Targeted Robolectric API 34 passed 1/1. Full Gradle passed 456/456 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No external provider or UI was used.
- Test commit `e2f413a74a7a504f3ca493adc5ee3ad69e497f25` is pushed. Independent report: `docs/issues/PC-ISSUE-104-ai-context-is-read-only-projection.md`. Paired with Issue 089 ICS evidence, this checkpoint left `REC-P0-082` NOT TESTED for the broader scope; PC-ISSUE-113 later signs the narrowed AI/ICS core criterion PASS.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-104.json`, SHA-256 `7D8CB24D683736DA27397053013F14AE29702918992A00956CD968BF3A07FD79`; ledger SHA-256 `440DCE8934F4782FC54A292DA10B1C0E88C4C051F069F7A88EAAA0D772278353`.

## PC-ISSUE-103 — AI missing-kind clarification (candidate remains NOT TESTED)

- Reproduced that an untyped ambiguous AI item was dropped with only an “ignored” warning. The core parser now asks which of the four event kinds the user means, while creating no proposal/draft or event identity until clarified.
- The pre-fix regression failed at the missing clarification text. Post-fix targeted Robolectric API 34 passed 4/4 across parser and PowerStore suites. Full Gradle passed 455/455 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No UI code changed.
- Fix/test commit `d381e1dc87a6ae1fe459b50aad79dd63b391fb9c` is pushed. Independent report: `docs/issues/PC-ISSUE-103-ai-missing-kind-clarification.md`. API 35/provider/card paths remain untested, so `REC-P1-051` remains NOT TESTED.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-103.json`, SHA-256 `C96848D5A9F75692FA837532EA7D3F3D91A0FD15086F4C96412536065DD74CD6`; ledger SHA-256 `31F3050CDC00C884E89A2EC219F2C53A51F35ED1D72387F89307984E625C84AC`.

## PC-ISSUE-102 — completion tracking toggle keeps reminder scheduling (candidate remains NOT TESTED)

- Added a Robolectric API 34 `ReminderValidationTest` that schedules an Activity reminder, disables completion tracking, then verifies the new revision returns to PENDING and reconciles to SCHEDULED with one Alarm; the updated revision validates and the old revision is rejected.
- Targeted test passed 1/1. Full Gradle passed 454/454 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. This is scheduler-adapter evidence only; real Android alarm/notification delivery remains untested. No UI or production code changed.
- Test commit `200d5ae4783bfe2d2b0d95acd0ce0f572a5d2ebe` is pushed. Independent report: `docs/issues/PC-ISSUE-102-tracking-toggle-keeps-reminder-scheduled.md`. `REC-P0-017` remains NOT TESTED.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-102.json`, SHA-256 `FB29AA81FCFCA98BED762116A0F6E6AFCEBC7439642421B6BBEA73FBB7DC9085`; ledger SHA-256 `671FDBD1B5371FC6B961A867713CBE0B222A03E943820294A2B3D4A6F6FDAA7E`.

## PC-ISSUE-101 — AI missing-time proposal core checkpoint (candidate remains NOT TESTED)

- Added `PowerStoreTest.parsedAiActivityWithMissingTimeRemainsRepairableDraftAcrossRestart`. The parser keeps empty ACTIVITY times blank; the proposal stays a draft, a formal commit is rejected, and the INVALID draft/form survive SQLite reopen. Filling both times restores PENDING and allows one valid commit.
- Targeted Robolectric API 34 passed 2/2. Full Gradle passed 453/453 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. At that checkpoint the missing-type and API 35/card paths were not tested; Issue 103 later added core missing-type clarification evidence, while API 35/provider/card paths remain untested. No UI changed.
- Test commit `814f3f2c6cb2a3d451495ae8492c8629d0aa7476` is pushed. Independent report: `docs/issues/PC-ISSUE-101-ai-incomplete-proposal-missing-time.md`. `REC-P1-051` remains NOT TESTED.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-101.json`, SHA-256 `7ECF656AF47EC32029B1B525ABE4636F06190BE762BE3119163422426FC1A6F4`; ledger SHA-256 `FB88F6EC57099AD2169DEF1828116634A22C2095A945E711E3013D546F00F519`.

## PC-ISSUE-100 — mixed AI CREATE/REPLACE core checkpoint (narrowed core candidate later PASS)

- Existing parser and PowerStore regressions were run directly. One structured response stays as separate CREATE and REPLACE proposals; two PENDING drafts are created, the REPLACE keeps the target eventSeq, and neither formal event is published or mutated.
- Targeted Robolectric API 34 passed 2/2 across two suites. The same code baseline's full Gradle run passed 452/452 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. No provider call or UI change.
- Independent checkpoint report: `docs/issues/PC-ISSUE-100-mixed-ai-create-replace-core-checkpoint.md`. PC-ISSUE-118 later signs the narrowed core parser/draft-isolation criterion PASS; API 35/provider, presentation, approval and reopen persistence remain untested.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-100.json`, SHA-256 `CC8184361CCE7C1C4921D3FEFCD5579E0D6939B8CF591820D468F72ABBB1026A`; ledger SHA-256 `040CD2EEFAF281FA018ED465194C8D6ADCAC93CCAAE40B333CAD7BD23D18E5BC`.

## PC-ISSUE-099 — structured proposal HTTP 429 stage diagnostics

- Reproduced that a local HTTP 429 returned a generic “AI request failed” warning without naming the structured-proposal stage. Updated `AiClient.interpret` to preserve the stage in the warning. The focused test also verifies no proposal/raw reply is returned and neither the provider error body nor API key is exposed.
- Pre-fix targeted reproduction failed 1/1 on the missing stage label. Post-fix Robolectric API 34 passed 1/1. Full Gradle passed 452/452 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Used a local HTTP stub only; no external provider or UI change.
- Core fix/test commit `55f3c1212eafaf306ad70e89b308fa1c3b3612c5` is pushed. Independent report: `docs/issues/PC-ISSUE-099-structured-proposal-http-429-stage.md`. PC-ISSUE-117 later signs the narrowed AI-client core HTTP 429 contract PASS; API 35/provider and presentation paths remain untested.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-099.json`, SHA-256 `D073E87A42F7970F8094DB7E1B2E8CF3DFF03A6B4DA9487A76076F3C0CD03D20`; ledger SHA-256 `386E2BD443FC58F9BE1D3F392CF8B3E7C5020EAB17439FE7DBCE038F26CED2B5`.

## PC-ISSUE-098 — repeated approval core idempotency checkpoint (narrowed core candidate later PASS)

- The existing `PowerStoreTest.repeatedCommitReturnsTheOriginalOutcome` was run directly: a second ordinary commit of the same draft returns `duplicate=true`, the same revision/commitId, and leaves one history record. No code defect was found and no source/test file changed.
- Targeted Robolectric API 34 passed 1/1. The current code baseline's full Gradle run passed 451/451 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. API 35 approval action and device behavior remain untested; no UI was changed.
- Independent checkpoint report: `docs/issues/PC-ISSUE-098-ai-approval-idempotency-core-checkpoint.md`. PC-ISSUE-116 later signs the narrowed core commit-idempotency criterion PASS; API 35/provider approval-entry behavior remains untested.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only artifact: `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-098.json`, SHA-256 `FD48462009013801EACEF7CE707CD0D5E4D05D908F2028813BB583211AC7733E`; ledger SHA-256 `09D00A90C6CF4F49E47B5AADA8C213E12F1ABCAA877A9F7547F730183C06A8E2`.

## PC-ISSUE-097 — AI draft persistence checkpoint (status at that time NOT TESTED)

- Added `PowerStoreTest.parsedAiCreateProposalPersistsAsAnUnpublishedDraftAcrossRestart`: a locally parsed CREATE proposal becomes a PENDING draft, creates no formal event/history, and keeps its identity, form, version, status, and queue entry after SQLite reopen. No production defect was found; this is partial core evidence only.
- Targeted Robolectric API 34 passed 1/1. Full Gradle passed 451/451 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. `adb` is unavailable; API 35/free-provider selection and device behavior were not tested. No UI was changed.
- Test commit `0aa6fa50a00925246aff7dd13822ad1ef15ece4f` is pushed. Independent checkpoint report: `docs/issues/PC-ISSUE-097-ai-draft-persistence-process-restart.md`. At that checkpoint `REC-P1-056` remained NOT TESTED for the API 35/provider path; PC-ISSUE-115 later signs the narrowed core persistence criterion PASS.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1. Structural-only validation `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-097.json` SHA-256 `66163C61D912A6B2984023F017ADD825D4E3DC346051253B955A92FEF8269EC3`; ledger SHA-256 `BC18A6B477FF77AC67EEC3F632424D294FC80410BAE18478D5F40EA8142C88A9`. Original 209 case IDs remain unmapped.

## PC-ISSUE-096 — AI proposal rejection preserves the formal event

- Added `PowerStoreTest.theAiBatchDiscardOnlyRejectsProposalAndPreservesFormalEventAcrossRestart`. It publishes an Activity, creates an AI REPLACE proposal, discards it through `AiBatchRunner`, and verifies the formal snapshot and commit history remain unchanged before and after SQLite reopen; the proposal is DISCARDED and the batch unlocks.
- Targeted Robolectric API 34 passed 1/1. Full Gradle passed 450/450 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. This was a coverage-only change; no production defect was reproduced.
- Test commit `65f6cabd653878f77127e167a583bea31043e5e3` is pushed. Independent report: `docs/issues/PC-ISSUE-096-ai-proposal-rejection-does-not-mutate-event.md`. `REC-P1-055` is PASS for the core discard/persistence contract.
- Freeze remains HOLD: 135 PASS / 0 FAIL / 9 BLOCKED / 58 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 24/0/8/49/1; 117 reconstructed candidates have independent evidence. Original 209 case IDs remain unmapped.
- `REC-P1-059` remains NOT TESTED: AI proposal, draft, and batch authorization models do not represent dependent-group identity, and the freeze spec does not define group membership. No group semantics or UI were invented. Continue with independent non-UI core acceptance.
## PC-ISSUE-095 — AI independent batch item failure and retry

- Added a SQLite-trigger regression for a three-item batch whose middle commit fails. The pre-fix runner published 1 item instead of the expected 2 and skipped the later independent item. Fixed `AiBatchRunner` to isolate ordinary exceptions per item, preserve cancellation propagation, report the failure and continue; a partial-failure batch now ends as `FAILED` rather than `CANCELLED`.
- The regression confirms first/last items commit, the failed draft transaction rolls back and remains pending, `published=2 / kept=1 / cancelled=false` and the report survive SQLite reopen, the lock releases, stale authorization is rejected, and a new batch retries the retained draft successfully.
- Pre-fix reproduction log SHA-256 `62EF0C91214E7C38C0DABAC65B2A60571C9B17E38264239510B60D8184C950FC`. Final targeted test passed 1/1 (log SHA-256 `B7BA661157DE0E9A4DD96C5F8ACBBEE26DB826EE709CD2E981E5D2AE0B88B17A`; JUnit XML SHA-256 `0D09525904E9BF11BCD4DFB01207801C03AE5869243FACB067CE8130F79291D0`). Full Gradle passed 449/449 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `2F809BCB7A5C03973688D09662B7809BE64DF7A333B17FDAF8C316281DC3572D`.
- Commit `361f074538a27ad75c2a91c7da4bdee6beb46795` is pushed. Independent report: `docs/issues/PC-ISSUE-095-ai-batch-independent-item-failure.md`. `REC-P1-058` is PASS for the core runner/SQLite partial-success contract; no model or network call was made.
- Freeze remains HOLD: 134 PASS / 0 FAIL / 9 BLOCKED / 59 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 23/0/8/50/1; 116 reconstructed candidates have independent evidence. Original 209 case IDs remain unmapped.
- Next: continue a feasible core acceptance item; keep remaining failures and device gaps visible.

## PC-ISSUE-094 — Reminder recovery broadcast matrix

- Added `BootReceiverRecoveryTest.recoveryBroadcastsReconcileDesiredAlarmAndFinishAsyncWork`. Five synthetic non-boot actions (app replaced, timezone changed, clock changed, exact-alarm permission changed, internal reconcile) each clear the simulated AlarmManager registration, dispatch the receiver, await `goAsync()` completion, and verify one registration is rebuilt from the durable `SCHEDULED` intent. The boot action remains covered by PC-ISSUE-093.
- Focused `BootReceiverRecoveryTest` passed 2/2 (log SHA-256 `15C9FC8800907C64CFEBEA601F91C2CAF56570B09920F6F9FFDC14238CB81803`; JUnit XML SHA-256 `A7BBB9E5A22B8BFEB6A21F851E19EDFF279F3CFF3FE0E649560912E2FE91E349`). Full Gradle passed 448/448 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `3BF5BF314F9F4320B07788CB0629653AA90199A83F1429A392F2A622DE5D4116`.
- Test commit `3983bf7dcc1ba5b5b1ec9e84d621fe4aad77d99c` is pushed. Independent report: `docs/issues/PC-ISSUE-094-reminder-recovery-broadcast-matrix.md`. `REC-P0-117` remains NOT TESTED because dispatch was synthetic and this environment has no `adb`; actual Android system delivery and physical/OEM recovery are unverified.
- Freeze remains HOLD: 133 PASS / 0 FAIL / 9 BLOCKED / 60 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 22/0/8/51/1. Original 209 case IDs remain unmapped. This loop is limited to core acceptance.
- Next: continue with a feasible core acceptance item while preserving the actual-device recovery gap.

## PC-ISSUE-093 — BootReceiver asynchronous schedule reconciliation

- Added `BootReceiverRecoveryTest.bootBroadcastReconcilesDesiredAlarmAndFinishesAsyncPendingResult`. Robolectric API 34 dispatches `ACTION_BOOT_COMPLETED` through `goAsync()`, changes the durable Alarm intent from PENDING to SCHEDULED, registers one ShadowAlarmManager task, completes `PendingResult`, and retains the result after SQLite reopen.
- Focused regression passed 1/1 (log SHA-256 `CEBEC27C47343C23D1998248D6C7FC99176310CFE41BDACECD9BA7AA58EC439F`; JUnit XML SHA-256 `C6B73D20FB7495F9E0C3AE24DF5E3F8A41918C93C0D62B2B427D8B2366FD621B`). Full Gradle passed 447/447 across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `AF800BA5E17A2822EC9A79CCB9309A980DE4A199769A54F7225560BFE19FF707`.
- Test commit `86e9d97106592edc41d988db317d47687be89976` is pushed. Independent report: `docs/issues/PC-ISSUE-093-boot-receiver-async-reconciliation.md`. `REC-P0-111` passes for the Robolectric receiver path; actual system boot broadcast and physical/OEM delivery remain untested. Test-harness failures from the first two attempts are documented with hashes.
- Latest freeze remains HOLD: 133 PASS / 0 FAIL / 9 BLOCKED / 60 NOT TESTED / 7 SPEC UNRESOLVED; P0 111/0/1/9/5; P1 22/0/8/51/1; 115 reconstructed candidates have independent evidence. Original 209 case IDs remain unmapped. Scope remains core-only; pale horizontal edge accepted. Protected untracked files remain unchanged and unstaged.
- Next: continue with a feasible non-UI core acceptance item.

## PC-ISSUE-092 — AI batch user cancellation and partial result

- Added and pushed `PowerStoreTest.theAiRunnerStopsAfterUserCancelAndPersistsPartialOutcomeAcrossRestart`. The runner receives a cancellation request after the first item, does not publish the second item, returns `published=1 / kept=0 / cancelled=true`, releases the lock and persists the report across SQLite reopen; stale session writes remain denied.
- Focused Robolectric API 34 regression passed 1/1 (log SHA-256 `FE85E768847D06B41AEC5128FD8CC85C528C997DD1127439EAA282FF05B0163D`; JUnit XML SHA-256 `F776710373A58963149903B83ED69D2667D4D27AB40F6DCFDBA924636013F638`). Full Gradle passed 446/446 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `4A8039764F0D6B25A47B8E0B4C9FC31445E15A64382FAA3EFD6363A61C10933A`.
- Test commit `d79b0f6d11870c5a18c6b7401ebe18e72fe95c05` is pushed. Independent report: `docs/issues/PC-ISSUE-092-ai-batch-user-cancel-partial-result.md`. `REC-P1-060` is PASS for the core runner cancellation and partial-report persistence path; the model service is not called.
- Latest freeze remains HOLD: 132 PASS / 0 FAIL / 9 BLOCKED / 61 NOT TESTED / 7 SPEC UNRESOLVED; 114 reconstructed candidates have independent evidence. The original 209 case IDs remain unmapped. Scope remains core-only; pale horizontal edge accepted. Protected untracked files remain unchanged and unstaged.
- Next: continue with a feasible non-UI core acceptance item.

## PC-ISSUE-091 — AI batch cancellation cleanup and stale-epoch fencing

- Added and pushed `PowerStoreTest.cancelledAiBatchRunnerReleasesLockAndFencesRemainingScopeAcrossRestart`. Cancellation after the first completed item preserves that formal event and the unfinished draft, releases the batch lock, persists `CANCELLED`, and rejects the stale `AiSession` after reopening SQLite. Existing orphan recovery separately simulates a previous owner token and rejects its late write.
- Focused Robolectric API 34 regression passed 1/1 (log SHA-256 `3D6BEDC5462DF72ADCE56A06A479A92FA2EB6521AFD400769A2C6D61554DE5DB`; JUnit XML SHA-256 `4B9C5709A0E8F9689B3E249B5CDCC5E3C5541DE507C29A95BE2BE602B2321D02`). Full Gradle passed 445/445 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `BDEB8CB1A9047D1E30A7FEDD0A2E63AEA30B21744748F298BF90360F5F82BB5D`.
- Test commit `65fb3ca8bda51cf7c6bd91794789e2b152e9603a` is pushed. Independent report: `docs/issues/PC-ISSUE-091-ai-batch-cancellation-recovery.md`. `REC-P1-061` is PASS for the core batch state/authorization contract; live model timeout and OS-level process-kill behavior were not exercised.
- Latest freeze remains HOLD: 131 PASS / 0 FAIL / 9 BLOCKED / 62 NOT TESTED / 7 SPEC UNRESOLVED; 113 reconstructed candidates have independent evidence. The original 209 case IDs remain unmapped. Scope remains core-only; pale horizontal edge accepted. Protected untracked files remain unchanged and unstaged.
- Next: continue with a feasible non-UI core acceptance item.

## PC-ISSUE-090 — ICS TZID and VTIMEZONE serialization

- Mapped existing `IcsExportRecurrenceTest` fixed-offset and DST round-trip assertions to `REC-P1-078`. Focused Robolectric API 34 run passed 6/6; full Gradle at the same code/test baseline passed 444/444 across 56 suites, with zero failures/errors/skips and lint 0 errors / 58 warnings.
- Focused log SHA-256 `67CB928029CBB1759776544081D710802D7A47E24E60F15733D6C7DE3B569676`; JUnit XML SHA-256 `B48271D52E3B178E494F252BFAE05A6796A333FA7D1F327DBF8522F089D9C2A6`. Independent report: `docs/issues/PC-ISSUE-090-ics-timezone-definition.md`. No source changes. `REC-P1-078` is PASS for codec output; third-party consumer behavior remains outside scope.
- Latest freeze remains HOLD: 130 PASS / 0 FAIL / 9 BLOCKED / 63 NOT TESTED / 7 SPEC UNRESOLVED; 112 reconstructed candidates have independent evidence. Historical 209 IDs remain unmapped.
- Next: continue with a core-only candidate; do not start UI work.
## PC-ISSUE-089 — ICS read projection preserves one formal event source

- Added and pushed `PowerStoreTest.icsActivityProjectionDoesNotCreateSecondFormalEventAcrossRestart`. A committed SQLite Activity projects through ICS as one matching event without warnings; formal rows, history, reminder intent, identity count and sequence preview remain unchanged before and after SQLite reopen. Test-only change; no production or UI changes.
- Focused Robolectric API 34 regression passed 1/1 (log SHA-256 `394711335A4B9DE80FEE60B81FF224C85645883FBA16403AC25A496A683550AA`; XML SHA-256 `F6CC86C486E178FF119E8AB730626FDD0C7F940B557A7480DEBE850D68D051F1`). Full Gradle passed 444/444 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Log SHA-256 `EE9DA7BC1A810D3C706DAA44F7CBE0D3EED6C5557572F5690F50C45621BFE44D`.
- Test commit `13e720bce54d0b31a179e68e9fa1a86eec518c61` is pushed. Independent report: `docs/issues/PC-ISSUE-089-ics-read-projection-does-not-mutate-store.md`. At the Issue 089 checkpoint, the combined AI and platform projection scope remained incomplete; PC-ISSUE-113 later signs the narrowed AI/ICS core criterion PASS.
- Evidence handling note: the prior structural-only Issue 088 JSON was reserialized from its captured content after a filename collision; its new SHA-256 is `4D8A8F14B8D50995CC9F66D990AF1ABF47C76ECBB887187381E6D00C09230A70`. Issue 088 test logs and JUnit evidence were untouched. Issue 089 validation is stored at its own filename.
- Latest freeze remains HOLD: 130 PASS / 0 FAIL / 9 BLOCKED / 63 NOT TESTED / 7 SPEC UNRESOLVED; 112 reconstructed candidates now have independent targeted evidence. Historical 209 IDs remain unmapped. User scope remains core-only; no UI work. Protected untracked files remain unchanged and unstaged.
- Next: continue with the next feasible non-UI core acceptance item.

Updated: 2026-10-24

## PC-ISSUE-088 — date-only Deadline completion precision

- Added and pushed `PowerStoreTest.completingDateOnlyDeadlinePreservesDatePrecisionAcrossRestart`. Completion changes only the completion key/revision; `deadlineDate` and DATE precision remain, and deadlineMillis/startMillis/endMillis stay null after SQLite reopen. No UI or production code changed.
- Focused regression passed 1/1 (log SHA-256 `09908CFC9350A75119B589F1AD665E2B14626B6AF39C71ED619B127F89FBF43F`; XML SHA-256 `3EFDF79FEAA9ED6862967256710C40E1522272741CAC74B9349D000F6E02CDB5`). Full Gradle including source boundary passed 443/443 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `84C5052C61812EFEE91155A59BFC42364DC3BEEB01701EA4DD1593F6474998CE`.
- Test commit `d94d98b1de5d3573420c8fd9e29f58c8f73ae9f8` is pushed. Independent report: `docs/issues/PC-ISSUE-088-date-only-deadline-completion.md`. `REC-P0-023` remains NOT TESTED because the required UI rendering behavior was not tested.
- Latest freeze remains HOLD: 129 PASS / 0 FAIL / 9 BLOCKED / 64 NOT TESTED / 7 SPEC UNRESOLVED; 110 reconstructed candidates have independent targeted evidence. Historical 209 IDs remain unmapped.
- User scope: no UI work; faded horizontal edge is accepted, and the blue current-time marker may cover the center axis. Keep Issue 002 PARTIAL/BLOCKED and do not retry around the free-model rate limit or call unauthorized paid models.
- Protected untracked files `DraftDeckFeedbackTest.kt`, `design/`, and `PC-OBS-005` remain unchanged and unstaged.
- Next: continue with the next feasible non-UI core acceptance item.

## PC-ISSUE-087 — completion tracking and reminder state independence

- Audited the reconstructed “Pause object” wording and found no such behavior in the approved contract or core code. Narrowed `REC-P0-017` to contract Invariant 2: tracking, reminder scheduling and event enabled state are independent.
- Added and pushed `PowerStoreTest.changingCompletionTrackingPreservesReminderIntentAcrossRestart`. Both tracking directions preserve enabled/reminder fields and the equivalent pending intent across SQLite reopen. Test commit `5b610b7bc48951e1205cf18cc72fe83782d93fac` is pushed.
- Focused regression passed 1/1 (log SHA-256 `486ED3A39D00EFC5B7ECAE685292F3D1CCD365BEA6AB16A6A2D9665FCC6EFBE8`; XML SHA-256 `395F805708218E12ED551E8FDD0C667E832F28A32D30677B8D1C6CEC6EBF672C`). Full Gradle including source boundary passed 442/442 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `F36A991D37DF9CEEABD17623CFC7175740EFC05F51DDCCA882EB9ACF66FCB4CF`.
- Independent report: `docs/issues/PC-ISSUE-087-tracking-and-reminder-state-independence.md`. `REC-P0-017` remains NOT TESTED because Android scheduler delivery and physical/OEM behavior are not covered. No UI or production code changed.
- At the Issue 087 snapshot, freeze remained HOLD: 129 PASS / 0 FAIL / 9 BLOCKED / 64 NOT TESTED / 7 SPEC UNRESOLVED; 109 reconstructed candidates had independent targeted evidence. Historical 209 IDs remained unmapped.
- User scope: no UI work; faded horizontal edge is accepted, and the blue current-time marker may cover the center axis. Keep Issue 002 PARTIAL/BLOCKED and do not retry around the free-model rate limit or call unauthorized paid models.
- Protected untracked files `DraftDeckFeedbackTest.kt`, `design/`, and `PC-OBS-005` remain unchanged and unstaged.
- Next: continue with the next feasible non-UI core acceptance item.

## PC-ISSUE-086 — whole-series recurring rule persistence

- Added and pushed core regression `PowerStoreTest.wholeRecurringSeriesEditKeepsIdentityAndAnchorAcrossRestart`: `EditSession` saves the changed title/rule while keeping the event identity and original anchor; recurrence projection and stored state remain stable after SQLite reopen.
- Focused regression passed 1/1 (log SHA-256 `394711335A4B9DE80FEE60B81FF224C85645883FBA16403AC25A496A683550AA`; XML SHA-256 `25D33D615FBE7DD1FDD0AF36BA4727C467EE698B0139F69BA25546FCC129F2CE`). Full Gradle including source boundary passed 441/441 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `1C898C47EFC096A0A6919609F543DE3FC3B25F63E0FA19EF8B263DA9DC602173`.
- Test commit `49f334e8cea1005de59ef9b98a16e32f544b937e` is pushed. Independent report: `docs/issues/PC-ISSUE-086-whole-series-core-edit.md`. `REC-P0-080` remains NOT TESTED because explicit whole-series scope selection and device behavior were not exercised; this is partial core persistence evidence only.
- At the Issue 086 snapshot, freeze remained HOLD: 129 PASS / 0 FAIL / 9 BLOCKED / 64 NOT TESTED / 7 SPEC UNRESOLVED; 108 reconstructed candidates had independent targeted evidence. Historical 209 IDs remained unmapped.
- User scope: no UI work; faded horizontal edge is accepted, and the blue current-time marker may cover the center axis. Keep Issue 002 PARTIAL/BLOCKED and do not retry around the free-model rate limit or call unauthorized paid models.
- Protected untracked files `DraftDeckFeedbackTest.kt`, `design/`, and `PC-OBS-005` remain unchanged and unstaged.
- Next: continue with the next feasible non-UI core acceptance item.

## PC-ISSUE-085 — recurring this-and-future split

- Core transaction implementation and regression tests committed and pushed: `cf1be08500f02adc0edba12c9c97bc4c7d53fea1` (`feat(重复): 支持指定实例及后续序列拆分`) and `86043231d14594cce89942f8847c5fe66e7bd178` (`fix(重复): 保留首个有效实例后的剩余次数`).
- Fix covers atomic old-series truncation/new-sequence creation, compatible occurrence-state migration, rollback, and retaining the existing identity when no earlier active non-excepted occurrence exists. If the selected slot becomes the anchor and the count is unchanged, only the remaining count is retained.
- Both controlled pre-fix reproductions failed on the identity/count mismatch; final focused set passed 4/4. Full Gradle, including source boundary, passed 440/440 across 56 suites; lint 0 errors / 58 warnings. Full log SHA-256: `0F4780D20727E1F0418E337F070A9229C4D1D898E5829DCB7A8AC42BDB25D433`.
- Independent report: `docs/issues/PC-ISSUE-085-this-and-future-series-split.md`. `REC-P0-079` remains NOT TESTED because editor scope selection/call path and API 35/physical-device behavior are not covered. `REC-P0-078` default scope and `REC-P0-080` whole-series selection remain open.
- No UI changes. User accepted the faded horizontal edge and confirmed the blue current-time marker may cover the center axis. Do not reopen UI work.
- At the Issue 085 snapshot, freeze remained HOLD: 129 PASS / 0 FAIL / 9 BLOCKED / 64 NOT TESTED / 7 SPEC UNRESOLVED; 107 reconstructed candidates had independent targeted evidence. Historical 209 IDs remained unmapped.
- Next: continue with the next feasible non-UI core acceptance item; keep Issue 002 PARTIAL/BLOCKED and do not bypass free-model rate limits or use unauthorized paid models.
