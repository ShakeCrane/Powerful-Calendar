# Clear Settings exposes Host-owned calendar and data actions

**Date:** 2026-10-31
**Status:** This Settings slice is **PASS** on Chrome/CDP and an API 35 Android emulator. SEP-11 remains incomplete.

## Gap and fix

Clear Settings already showed reminder permissions, reconciliation, execution diagnostics and the native recovery route. Calendar import/export, backup/restore and legacy migration were only reachable from the separate data-options page, leaving the main skin Settings surface incomplete.

The Clear Settings page now renders the existing Host-projected optionGroups using the same registered invokeOption actions. It exposes six existing options for file import, system-calendar import, legacy migration, ICS export, backup export and backup restore. No Host capability or business action was added. The native full Settings/recovery entry remains available. The Bridge contract now documents this reuse and the Host-owned confirmation boundary.

## Evidence

- **API 35 emulator codex-clear-api35 / Android 15:** ClearSettingsOptionsInstrumentedTest, **1/1 PASS** against the packaged Clear WebView and production Host. It verified all six options, then opened the Host confirmation for legacy migration and cancelled it. Formal records, drafts, commit count, identity count and sequence watermark stayed unchanged. The Settings page and native recovery entry remained available.
- **Chrome 156/CDP:** canonical Clear Settings displayed all six Host-projected IDs and sent invokeOption(data.legacy.migrate). Permission and reminder controls still emitted their registered actions; the native recovery action remained present. The protocol Mock did not launch OS UI or read/write calendar data.
- **Skin Studio:** browser source/project/resource edit and export/re-import E2E passed; the complete Studio Python suite passed **62/62**. These verify Studio authoring and round-trip workflows, not Android Runtime execution.
- **Gradle gate:** :app:verifyNativeSkinBoundary :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --offline --rerun-tasks --max-workers=1 --console=plain — **BUILD SUCCESSFUL**, **784/784 JVM tests**, 125 suites, 0 failures/errors/skips; both boundaries, AndroidTest compilation, Debug APK and lint passed (152 tasks).
- **Canonical package:** 9 files, 394,565 bytes. Source bundle and Debug APK asset SHA-256 both 601e84ffe4316140dcf0310d9977482478b3f9dbfadf986d5d6da4d0cb10a884. Debug APK SHA-256 7D6A3A37C86116DEB94A1CFEEC5E1235AD0245BBD4C1AFBCAEA096A9B727B634.

Detailed evidence is under .local/android-test/reports/clear-settings-options-20261031/:

- Gradle gate SHA-256: 5CF3A138AFCF6A0E62CDDA11D195F4E7F3215A8CDE344C7B09198D8575254675
- Android instrumentation log SHA-256: C054896E19C5D801065935683223FFEF5D9D1E0DEDDBAF2C7B1CFD0A8EBEFFEB
- Studio Python log SHA-256: CE2EE8D802EF521DA2F9F699F1BCA47F89740E39BFD29B82D6DD742CA43A1FB5
- Studio browser source round-trip log SHA-256: C0949E6F0C6AACC2C621860625EF49E899D67C03AD78F3EC0E81087B86B53C41
- Clear Settings Chrome/CDP result SHA-256: 8A065DF839F14049384526A265CB2E90983DF6FB92A0341783FE98C4FB75D425
- Screenshot: .local/android-test/reports/clear-settings-options-20261031/chrome/settings-2026-10-30T18-48-45-903Z.png

The first emulator attempt timed out while a fast polling loop repeatedly queried the WebView. The test now checks the Host projection separately and paces DOM polling; the final instrumented run passed. The initial failure is retained in android-instrumentation.log.

## Scope left open

This does not close SEP-11. The actual emulator run cancelled the legacy-migration confirmation; it did not complete SAF import/export, backup restore, or system-calendar permission flows. AI credential/profile editing, skin management, full Native/Skin replay, physical/OEM behavior and other Settings error paths remain unverified. Native recovery remains available.

## Git

Runtime and test commit: dfcd5948f4b5404302a202825f61507d1a415492 (fix(皮肤分离): 在Clear设置页开放宿主日历与数据操作), pushed to origin/feat/skin-first-separation.

## Follow-up — Clear installed skin selection (2026-10-31)

Clear Settings now renders the Host-projected installed catalogue and sends only a catalogue ID. The Host accepts the action only from Settings, resolves it against the current catalogue, and reuses the existing trusted activation confirmation, edit checkpoint, and package manager. No package path, credentials, or calendar data cross the Bridge. Studio Preview Mock supports the same confirmation/cancel interaction without changing Android state.

- **API 35 emulator `codex-clear-api35`: 9/9 actual packaged Clear WebView tests PASS** across Settings activation/cancellation, Memo, month/timeline create context, AI proposal review, and kind conversion. The Settings test verifies cancelling keeps Clear selected, confirming selects the Native recovery skin, and the teardown restores the emulator's original skin preference. Existing calendar/data cancellation assertions still show unchanged Store counters.
- During smoke setup, the kind-conversion test exposed three test-harness mismatches (CSS selector quoting, JSON-quoted `evaluateJavascript` strings, and the actual Host cancellation copy). Correcting the assertions made that test pass; no Clear runtime defect was reproduced in this path.
- Full gate: **784/784 JVM tests**, 125 suites, 0 failures/errors/skips; both source boundaries, AndroidTest Kotlin compilation, Debug APK and lint passed (152 tasks). Lint: 0 errors, 55 warnings. Studio Python **63/63 PASS**; Chrome/CDP Preview Mock E2E and JS syntax checks passed. Clear package verification matches the packaged APK asset byte-for-byte: 9 files, 395,062 bytes, SHA-256 `49e0f09edceb21834fa7bf2ac5ec0b15f2a81d75bb2ef67486531bd92f9246e2`. Debug APK SHA-256 `BF7B3633CC49CEB776F8A14E26C2DAC8E014E39E0F888595F0AA2EA60ADF2529`.
- Evidence under `.local/android-test/reports/sep11-clear-skin-catalog-20261031/`: full Gradle log SHA-256 `33BBDA7F3853FB0809CE94D9B879883AF9B3FAF75F63EC35571B250240834AB1`; connected instrumentation log `F1F0FE7CB8619137DC0D5392EF92F6D09B636E1E041EAA55DE12095C8B3F5CB6`; JUnit XML `AEA3B5EF01F293504DDC9EC10D478697D18BA702B5A6BDF5B91630B244DB4151`; Studio Python log `80E88373C31A4C8C61B4FEBD85C7279E0B97B14657E06DB2A965060D14138468`; Preview Mock JSON `83554BD191CDAEEBD9887E1BC039E680A08AA098320396BA19C5C459AA253509`.

This is evidence for the installed-skin selection slice only. It does not close SEP-11 or establish total Clear parity: AI profile/credential settings, skin import/export/delete, SAF and system permission completion, broader Native/Skin replay, accessibility and physical/OEM behavior remain unverified. The SEP-11 row remains **NOT TESTED** overall; S0-ST-06 remains **PARTIAL / HOLD**.

Implementation commit `886c1b779f7e2b11a8eeeacf80d1c42e7e6ce7ce` (`feat(皮肤分离): 在Clear设置中支持宿主确认切肤`) is pushed to `origin/feat/skin-first-separation`.

## Follow-up — Clear protected AI settings route (2026-10-31)

Clear Settings now has an explicit AI/privacy entry. It uses the existing `nativeSettings` action to open the Host-protected Native Tools form; the skin does not receive endpoint preferences or a key. The existing Host form owns endpoint/model/key/context settings and file-fallback consent. Studio can edit the button, label and surrounding source; its Preview Mock logs the route without launching Android UI or saving preferences.

- **API 35 `codex-clear-api35`: 10/10 actual packaged Clear WebView tests PASS**: two Settings tests, two Memo tests, two timeline/month-create tests, three AI proposal-review tests and one kind-conversion test. The new Settings test enters the Host AI form, expands it and verifies endpoint/model/enable/context controls are reachable; it asserts Bridge state contains no `apiKey`. It does not edit or save AI preferences, and restores the skin/onboarding state after the test.
- Full Gradle gate: **784/784 JVM tests**, 125 suites, 0 failures/errors/skips; boundaries, AndroidTest Kotlin compilation, Debug APK and lint pass (152/152 tasks; 0 lint errors / 55 warnings). Studio Python **63/63 PASS**, JS syntax checks and Chrome/CDP Preview Mock pass.
- Clear source/APK asset verification: 9 files, 395,184 bytes, SHA-256 `925b2ae7d3dfb28134e8f9a2c1ced81a146320aee8a68e106769c63f9acb880f`. Debug APK SHA-256 `54EAAAE53CABCE8DA4BD7EC1B679C6DDEF88D5A98D2B00C0B07A08B038783D8C`.
- Evidence under `.local/android-test/reports/sep11-clear-ai-settings-20261031/`: connected log SHA-256 `66B196E074FFC4C5F19FF583F81D397DB864D52733DB807A1C446E7F7F412674`; Android JUnit XML `7EAB645940FC1CF2C55C4E463141508CF811719C55718D4A8E9053ADF3BB6138`; full Gradle log `DE26BBD586543F7D1E1353E855DC60577594A37F229C64000DC7332983AA6848`; Studio Python log `D16D740A27082D3A8878F1A8F005A047B3BF1170949A4B6B971FA91DDDB7168C`. Preview Mock JSON SHA-256 `3CA1C2DA135E8E215DAFCBC52FDCF818A8B0659051E13A8ABC6F93BA97AEEB42` is under `.local/android-test/reports/sep11-clear-skin-catalog-20261031/studio-followup/`.

This proves navigation to the protected Host form and the absence of a credential field in Bridge state. It does not prove save/restart behavior through Clear, profile editing, consent grant/revoke, or a skin-owned rendering of the credential form. S0-ST-03/04 remain **PARTIAL / HOLD**, S0-ST-05 remains **NOT TESTED**, and SEP-11 remains **NOT TESTED / INCOMPLETE**. Implementation commit `6a92d7eba594018559eeeb68de7cda33e7a1bf74` (`feat(皮肤分离): 从Clear设置进入受保护的AI配置`) is pushed to `origin/feat/skin-first-separation`.

## Follow-up — Clear skin package management (2026-10-31)

Clear Settings now exposes Host actions for importing a `.pcskin`, exporting the bundled Clear package, exporting an installed third-party archive, and deleting a third-party package. The Bridge accepts exact closed payloads; management actions are limited to Settings without a competing AI/editor/deck session. Host catalogue lookup rejects built-in export/delete targets. Import reuses `OpenDocument` and `SkinPackageManager.install`, retains validation/readback/atomic install, and does not activate the imported package. Export uses the existing package manager and preserves original archive bytes. Deletion requires the existing Host confirmation; deleting the active third-party package selects Native recovery first. No calendar Store write path was added.

API 35 `ClearSettingsOptionsInstrumentedTest` passed **3/3**, including the settings options, protected AI route and skin-management case. The management case drove the actual packaged Clear WebView action through the Host Bridge and installed an isolated fixture; it compared Clear export byte-for-byte with the canonical APK asset and third-party export byte-for-byte with the source archive; it cancelled and then confirmed deletion and asserted formal events, commits, identities and sequence watermarks were unchanged. To keep the test isolated, Android `ActivityResult` values were injected with fixture file URIs: this validates Host callbacks and package-manager behavior, but does **not** validate Android DocumentsUI interaction, real document-provider grants/errors, or picker cancellation UX. Active-package fallback and file-system failure recovery were not tested. The Studio Chrome/CDP Mock covers import/export/delete cancel/approve without writes, and the Studio Python suite passes **63/63**.

Verification on AVD `codex-clear-api35` (Android 15 / API 35, serial `emulator-5568`):

- Settings and skin management instrumentation **3/3 PASS**; final combined Clear WebView instrumentation **11/11 PASS**, 0 skipped/failures. The combined run covers Settings/package management, Memo projection, timeline/month create context, AI proposal confirmation, and kind conversion. JUnit XML SHA-256 `693825D7C029E6533F9F842C440799C1F537BBC9E625416AA09CE24793EAAF0B`; connected log SHA-256 `60A573601AD557A741C0E49E24906E7A3AAB061633DF709A94209BA18FB78AB8`.
- Full offline Gradle retry: **784/784 JVM, 125 suites, 0 failures/errors/skips; 152/152 tasks PASS**, including both source-boundary checks, AndroidTest Kotlin compilation, Debug APK assembly and lint (**0 errors / 55 warnings**). Log SHA-256 `E62E66D00E30E5903BA51C25B5F5DAB42E08AB4198F236A274026E8CD2EA0DBE`.
- The first full-gate attempt had one timeout at `PowerCalCasualSuggestionComposeTest.webMemoToggleIsOccurrenceScopedRevisionGuardedAndStableAcrossRestart`; its isolated rerun passed **1/1**, and the subsequent complete retry passed. The first-run log remains at `gradle-full-gate.log` (SHA-256 `28712C500F1C6B08DF0ADAEC7D1E6E39B4B33CF346B9A171453C7BD532492016`); isolated rerun log SHA-256 `BB334204D774E0DBB6C6331CECA2F639F36EDCE4A426218F914E5B65AAD67271`.
- Studio Python **63/63 PASS** (log SHA-256 `97DF37B2A52A3328B0CE24A4170DA96C093D68ABA626CF89FB7A3BCD70B650E1`); Chrome/CDP Preview Mock E2E passes, including import/export/delete cancellation and approval (result JSON SHA-256 `BD9DDE24E8D1D4B61350C1F336E3DF40D1C34042571923FBEFC197C89FF48D14`). JS syntax checks passed.
- APK `app/build/outputs/apk/debug/app-debug.apk`: **66,517,863 bytes**, SHA-256 `779B0CF777BD5E1EE377249CD4156DB426DC87690B6D35EDD458E5F27A27CD1B`. `build_default_clear.py --verify --apk` confirms the 9-file, 395,483-byte Clear package and APK asset match at SHA-256 `98eaedecb132b127ae1e6ec9d619659706e50dde603a52f720e27ea0a36eb7ba`.

The two early connected-management attempts failed because the ActivityResult test monitor omitted the launcher's `CATEGORY_OPENABLE` / MIME match; the monitor was corrected and the final 3/3 run passed. Those failed harness logs and the passing `connected-management-retry2.log` (SHA-256 `E023969C99D6AF5663EB5E69F51054B98098EBFB28DAE3F8A932D23CDD6B69CF`) are preserved under `.local/android-test/reports/sep11-clear-skin-management-20261031/`.

Implementation and test commit `fde41fe` (`feat(皮肤分离): 在Clear设置中管理皮肤包`) is pushed to `origin/feat/skin-first-separation`.

S0-ST-07/08/09 are now **PARTIAL / HOLD**, not closed; SEP-11 remains **NOT TESTED / INCOMPLETE** because actual SAF UX/failure handling, Settings/credential/backup parity, complete Native/Skin replay and device/OEM evidence remain open. This slice does not switch the default skin or remove Native recovery.

## Follow-up — real Android DocumentsUI import (2026-10-31)

The earlier package-management test injected an `ActivityResult` URI, so it proved the Host callback and archive handling but not the user-operated Android picker. A real API 35 run initially remained in the Downloads grid and produced no Host result. The accessibility trace showed the test tapped a non-clickable title child and then tapped the same item again while retrying. The test now clicks the row's clickable ancestor and does not repeat file selection; the runtime app code did not need a change.

- **API 35 AVD `codex-clear-api35`, Android 15, `emulator-5568`:** `clearSkinImportUsesDocumentsUiAndDoesNotActivateTheCandidate` passed **1/1** through packaged Clear WebView, the Host `OpenDocument` launcher, real `com.google.android.documentsui`, Downloads provider URI and `SkinPackageManager.install`. The candidate remained inactive and isolated Store snapshots (formal records, pending count, commits, identities, sequence preview) were unchanged.
- The complete `ClearSettingsOptionsInstrumentedTest` class passed **4/4**, including real picker import, protected AI-settings route, Settings options and Host-confirmed package-management actions. AndroidTest APK assembly passed.
- Full Gradle gate passed: **784/784 JVM tests, 125 suites, 0 failures/errors/skips; 171/171 tasks**, both source boundaries, Debug APK, AndroidTest APK and lint (**0 errors / 55 warnings**). Gate log SHA-256 `E70F066C3D4F2DF02DF22B5D7B744B80521A81BC1C708FEA079836D8C15D1309`; Debug APK SHA-256 `779B0CF777BD5E1EE377249CD4156DB426DC87690B6D35EDD458E5F27A27CD1B`.
- Direct instrumentation evidence: one-case log SHA-256 `91B367BCC6D27081116594ED85D9A094C4D97AC2061EF23346B1DD35ABAEF4F9`; class log SHA-256 `46C38A83E8274B72070D0F2B35957398E250B497CB96636BE0E7B07D0890E5EB`. Logs are under `.local/android-test/reports/sep11-clear-skin-management-20261031/`.
- Verified test/report commit `b3e72fa65ec162a231037ebd350ef6405043dde3` (`test(皮肤分离): 验证Clear真实SAF导入`) is pushed to `origin/feat/skin-first-separation`.

During the first attempt, a previously running Gradle UTP invocation overlapped a direct instrumentation attempt. Its configured `uninstall_after_test: true` teardown removed both app and test packages from this QA AVD, including their package-private app data. The AVD is named `codex-clear-api35`; it was not a physical device. The UTP logs are retained, the APKs were reinstalled, direct runs use isolated database names and unique fixtures, and final checks found no `powercal-saf-*`, `clear-skin-saf-*`, or `qa-saf-*` residue. This environment-side data removal is recorded explicitly; it was not evidence of a Clear runtime crash. Future SAF tests use direct instrumentation only after confirming no UTP run is active.

As of this follow-up, only the **real SAF import happy path** was tested. Provider-grant denial, picker cancellation, malformed-package UX, import recovery, real `CreateDocument` export UI, active-package deletion recovery, broader Native/Skin replay and physical/OEM behavior remained unverified. S0-ST-07 stayed **PARTIAL / HOLD**, S0-ST-08/09 stayed **PARTIAL / HOLD**, and SEP-11 stayed **INCOMPLETE**. The later export follow-up below supersedes only the `CreateDocument` backup-export gap.

## Follow-up — real Android backup CreateDocument export (2026-10-31)

- Added `ClearSettingsOptionsInstrumentedTest.clearBackupExportUsesDocumentsUiAndVerifiesTheWrittenSnapshot`. On API 35 AVD `codex-clear-api35` (Android 15, `emulator-5568`), the test opens Clear Settings, invokes the existing Host option `data.backup.export`, uses the actual Android `com.google.android.documentsui` Save UI, selects Downloads, enters a unique filename, and saves the production backup.
- The production callback writes the envelope through the returned SAF URI. Instrumentation then reads only its unique test file through the Android shell, verifies the envelope digest, compares canonical payload against the isolated Store snapshot, confirms the seeded formal Memo is present, and checks formal records, pending drafts, commits, identities and sequence watermarks did not change. The production bounded readback remains in the existing callback; its transient success notice is not a separate assertion in this test. The test removes only its UUID-named Downloads artifact; final device scan found no `powercal-qa-backup-*` residue.
- **Focused instrumentation: 1/1 PASS. Full `ClearSettingsOptionsInstrumentedTest`: 5/5 PASS.** Final focused log SHA-256 `BE6DF809E79C29A0256CBA7D6AC82A418F376B2A91E2634E26F073C2DD034FD6`; full class log SHA-256 `BC94D94A58AB6BBCFD1EEDB03E35FAA68DF0C0DBAB899DD1F502448C234BA035`.
- Three failed harness attempts are retained and excluded from PASS: (1) the Compose notice was not exposed to that instrumentation semantics query although the file was created, log SHA-256 `24065E9B8387153FC78698D3E9141B9BEF38DFD7246DE59C3F5D3A464C0F192C`; (2) the test wrongly required a DocumentsUI-created Downloads item to appear in MediaStore, log SHA-256 `19444CFB951940D50DEDBBF8D5F24C1E1DF2D3EA7EB332D7372AF10C3FD4CEB1`; (3) the test compared against a separate earlier export timestamp rather than comparing the verified logical payload, log SHA-256 `2375258858B03142FD1F3F6DEA2EDA88BECAD126C8CD0408BA2A758A17166771`. The test was corrected; no runtime code change was required.
- Full offline Gradle gate: **784/784 JVM tests, 125 suites, 0 failures/errors/skips; 171/171 tasks PASS**, both skin/native boundary checks, Debug/AndroidTest APK assembly and lint. Lint: 0 errors / 55 warnings. Gate log SHA-256 `ACD3F6209EB75FBD308BFF20395EA4F327339D15679406804EED0CFFB25CE041`. Debug APK SHA-256 `779B0CF777BD5E1EE377249CD4156DB426DC87690B6D35EDD458E5F27A27CD1B`; AndroidTest APK SHA-256 `4F6AB9D4E98A381723BCC38070871D1FCEE1E745F1B54D7F7737D5B9E08E7FC4` (1,476,392 bytes). The AndroidTest assembly included two other Agent test files that remain modified in the worktree; this change does not include them.
- Evidence is under `.local/android-test/reports/sep11-clear-backup-export-20261031/`. SEP-11 remains **PARTIAL / HOLD / INCOMPLETE**: backup restore, real ICS and skin export picker completion, system-calendar permission grant/denial, provider failure/cancel, AI preference save/restart/profile and consent revocation, complete Native/Skin replay, and physical/OEM remain unverified. No production code, user calendar database or default skin changed.
