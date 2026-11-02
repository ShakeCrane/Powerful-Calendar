# PC-ISSUE-CORE-DRAFT-REVIEW-API-V1 — Draft/Review Feature API v1 slice

Date: 2026-11-01 (China Standard Time) | Priority: P1
Status: **BASE SLICE IMPLEMENTED; CREATE-DRAFT REPLAY RESOLVED IN FOLLOW-UP; PHASE 1 OPEN**

## Goal and result

Expose the persisted Draft Deck and its review/approval operations through a platform-neutral Core Feature API shared by Native and Web Deck hosts. Keep formal writes behind trusted Host review and existing Store transactions. Preserve frozen event, recurrence, restore, and authorization semantics.

The `:core-api` module now defines `DraftReviewFeatureApiV1` DTOs and operations. The app Host creates one service over the existing `DeckController` and `PowerStore`; Native and Web Deck flows use that service. This is an implemented Draft/Review vertical slice, not full Core extraction, complete Phase 1 coverage, runtime cutover, or Native/Skin parity.

## Implemented behavior

1. Draft open/refresh flushes dirty edits before changing the selected card. Edit commands require both persisted `draftVersion` and the in-memory `stateRevision`; the controller checks the revision atomically when applying the edit.
2. Commit, conflict replacement, and restore approvals carry the reviewed state revision and draft version. The controller checks them and marks the command busy in one state-lock transition. A dirty reviewed form is staged against its reviewed persisted version; a mismatch aborts without retrying stale form data over the newer card. After a successful stage, Store commit rechecks the resulting version transactionally. Restore rechecks the tombstone revision, draft version, and exact reviewed form in the same transaction that restores the identity.
3. Formal replacement and restore begin as revision-checked proposals; neither operation changes the formal event before approval. Stale Store state returns a review/conflict result and requires a fresh review.
4. Custom runtime/imported Skin requests for commit, conflict replacement, and restore open Host-owned confirmation UI. The review shows the candidate fields, recurrence, reminders, enable/tracking state, and completed instances; the Host checks the captured revision/form before dispatch.
5. Controller state and `stateRevision` are published from the same transition. Successful Draft/Review writes publish through the shared Event API instance.

At the time of this base-slice report, the API receipt cache was bounded to 128 entries and process-local. The later `createDraft` replay resolution is recorded below; other Draft/Review request caches remain process-local.

## Verification

| Check | Result |
| --- | --- |
| Focused DraftReview API, DeckController, Store restore, Host review UI, and Web bridge tests | **190/190 PASS** |
| Forced full `:app:testDebugUnitTest --rerun-tasks --offline --console=plain` | **800 tests / 128 XML reports; 0 failures, errors, or skips — PASS** |
| `:app:verifySkinSourceBoundary` and `:app:verifyNativeSkinBoundary` | **PASS** |
| `:app:assembleDebug` | **PASS** |
| `:app:lintDebug` | **PASS**, 0 errors / 54 warnings |
| Clear editor `node --check` and `git diff --check` | **PASS** |
| Android instrumentation / AVD / physical device | **NOT TESTED**; no AVD was touched |

The Debug APK is `app/build/outputs/apk/debug/app-debug.apk`, 66,683,502 bytes, SHA-256 `C22E82E434CBE5B02CD790FF72932074DA969A10BDF9095416179D3B8331AFB4`. It was built from this worktree and was not installed on a device.

Logs are under the ignored directory `.local/android-test/reports/core-draft-review-api-20261101/`:

- `review-conflict-fix-focused-rerun.log` — SHA-256 `DEFEB24D516AA1C311F2031445E60941D9FC52F17596DCD1F7C04720F504E39B`.
- `full-jvm-final-verified.log` — SHA-256 `15B85AA0D5393C33E0754EB3B907E3CC8A68A08AC4E9C1C89153652D096F7FB0`.
- `boundary-apk-lint-final-verified.log` — SHA-256 `5269F568754C32CA8E09770B53F69F9233BE5FFDD13432A32ACF63E0E115D6AE`.

## Remaining work

- Phase 1 remains open. Formal-event searches, direct completion writes, some Host/Core reads and authorized mutation paths still bypass the shared API/change source; runtime Skin projections still include Core types. Other required domain APIs are not yet defined.
- Native/Skin device parity and instrumentation for this slice remain **NOT TESTED**.
- Draft/Review request caches other than `createDraft` remain process-local as described above.

## Git delivery

Implementation branch: `codex/core-feature-api-event`. Code commit `a12e107b868243e0563d5ca904edaf38c777f070`, based on `a3b51394bc0c546f1d7d4e02a2bd8cef9a8d22e9`, was ordinary fast-forward pushed to `origin/feat/skin-first-separation` after a freshness check. Native fallback remains available; no default Skin switch or cutover authorization is implied.

## Follow-up — persisted pending-queue query (2026-11-02)

- **Code:** `a7ee828f8bbcb4407afaa294755b4d7b1c6e3e88`, fast-forward pushed from `cb7630670dabd509ef71bafd620f13adf2fd7634` to `origin/feat/skin-first-separation`.
- Added `DraftReviewFeatureApiV1.queryPendingDrafts()`, returning detached `DraftReviewCardV1` values directly from the existing Store queue. The adapter preserves `queue_order ASC` and the existing `PENDING` / `CONFLICT` / `INVALID` filter. `PowerCalApp.reload()` now consumes the API; the Host converts those DTOs only for its existing trusted AI batch-review path.
- The query does not open or refresh `DeckController`, flush dirty editor input, write Store rows, change the DraftReview snapshot/revision, or publish feature notifications. Regression coverage includes oldest-first order, exclusion of a discarded row, persisted-versus-unsaved editor values, unchanged editor snapshot, Store counts, Event feature revision, and no change notifications.
- **Focused regression:** `DraftReviewFeatureApiV1Test` **14/14 PASS**, 0 failures/errors/skips; JUnit XML SHA-256 `0A5B6F4BDB607D13FB2A66B7A27BC8CE3DE9D0735B1BA39D24A22C129821F6F4`. Focused log SHA-256 `24C4234D60BF020D7C6A4EFB2FB6786F2B114D5BE2052C7135C4D61D34EEF51F`.
- **Full JVM result:** Two forced serial offline runs each completed **879 tests across 137 XML suites: 878 passed, 1 failed, 0 errors, 0 skipped**. The repository-documented retained `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` timed out after the Host exposed all 251 rows but continued to report one remaining/truncated row. The exact test passed an isolated filtered rerun (**1/1 PASS**). Because the full suite failed in both runs, it remains **FAIL**, not PASS. Full-run log SHA-256 values: `96C0FEB25144575E608B75CD2BD76EE7188EC49356D11962D39DAE7CCCCF242` and `3037CC48B2E35F2AD63297A6412B7DDA7303CE513E80725BB528905504659ABA`; isolated rerun log SHA-256 `24C4234D60BF020D7C6A4EFB2FB6786F2B114D5BE2052C7135C4D61D34EEF51F`.
- **Separate build gate:** `verifySkinSourceBoundary`, `verifyNativeSkinBoundary`, `assembleDebug`, `lintDebug`, and `compileDebugAndroidTestKotlin` **PASS**; lint reports 0 errors / 54 warnings, and `git diff --check` passes. APK **67,011,182 bytes**, SHA-256 `01DFC4235E47E052D828D775919D9DA9EA535B7631F5C8AF3AAEB51A243121C1`; build log SHA-256 `60CABEF7F82664C21FB29CD71B5E8A2533026F60B5E525DEF2CED95041C07756`.
- **Commands:** From the dated worktree with process-scoped `ANDROID_HOME=E:\android-sdk`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.core.api.v1.DraftReviewFeatureApiV1Test' --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.powerfulcalendar.ui.v2.WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites' --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
.\gradlew.bat :app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --max-workers=1 --console=plain --no-daemon
```

- No ADB device or AVD was configured. Android instrumentation, packaged WebView and Native/Skin runtime behavior remain **NOT TESTED**. No schema, backup format, frozen business semantics, default Skin, or Native fallback changed.
- **Next PHASE 1 action at the time of this report:** Route the remaining `PowerCalApp.reload()` Store diagnostics (`nextSequencePreview()` and `identityCount()`) through a versioned read-only Core diagnostics query. This was subsequently completed; see the [Core Diagnostics API Issue](PC-ISSUE-CORE-DIAGNOSTICS-API-V1.md).

## Follow-up — `createDraft` request replay across adapter recreation (2026-11-02)

- A temporary Robolectric characterization invoked `DraftReviewFeatureApiV1.createDraft(form, "audit-recreated-request")`, constructed a new `PowerCalCoreFeatureApiV1` over the same `PowerStore`, and retried the identical form with the identical request ID. Both calls returned `APPLIED`, the receipts contained different `draftId` values, and the Store held two pending drafts. The assertion intentionally captures the behavior at audit time; its passing test result is **reproduction evidence**, not a pass for durable idempotency. Process death / database reopen was not directly exercised in that characterization.
- Focused command: `:app:testDebugUnitTest --tests 'dev.powerfulcalendar.core.api.v1.DraftReviewFeatureApiV1Test.audit_sameRequestIdAfterAdapterRecreationReproducesDuplicateDraft' --offline --rerun-tasks --max-workers=1 --console=plain`. Result **1/1 PASS as a characterization**, with 0 failures/errors/skips; Gradle completed in 28 seconds (69 actionable tasks). The temporary test was removed after capture. Ignored evidence is under `.local/android-test/reports/phase1-request-replay-audit-20261102/`: log SHA-256 `7DD42DAC1C70B34AC9F4623F2624E2DA83D016663324DA63B5CB173CE5F73C7C`; JUnit XML SHA-256 `3CB927B25101415254BDF7D86372A3C11F6B5DF18344AE76526B0C486C24D752`; archived test source SHA-256 `8280F9E18E7FB62DEDE802B4BABCAB33EC80DD5A11F66A567043C24271902C5D`.
- Historical root cause at audit time: `StoreDraftReviewFeatureApiV1.execute` stored the 128-entry request/fingerprint cache only in that adapter instance. `createDraft` delegated to `PowerStore.newDraft` without a request key, so a new adapter had no durable receipt to look up. The Store was then at schema v5 and the `drafts` table had no API request identity. `newDraft` reserved an event identity in one transaction and inserted/activated the draft in a later transaction.
- Historical compatibility analysis at audit time: a durable implementation required an additive Store migration and a retention / ID-reuse policy. BackupEnvelope v1 then carried the core logical tables plus optional `aiTaskReceipts`, and schema v5 required those AI task receipts. The owner had not yet decided whether API request receipts should survive backup/restore or start a new replay domain. SharedPreferences alone could not atomically commit a receipt with the draft transaction. No database schema, backup format, formal state, or frozen business behavior changed during that audit.
- At the time of this audit, the API contract remained process-local and durable replay was unresolved. This finding was superseded after the owner selected durable last-write-wins behavior across restart and backup restore.

## Resolution — durable `createDraft` request replay (2026-11-02)

The owner authorized the schema and backup compatibility changes. Implementation and verification are recorded in [PC-ISSUE-CORE-DRAFT-CREATION-API-V1](PC-ISSUE-CORE-DRAFT-CREATION-API-V1.md): schema v6 persists the request mapping, and BackupEnvelope v1 schema v6 payloads carry the receipt. Same-ID changed content overwrites the mapped actionable CREATE draft; exact retries after Store recreation or backup restore reuse its identity. Terminal drafts remain unchanged, and new content creates a fresh reviewable draft. The receipt does not grant publication approval.

## Follow-up — Web equivalent replacement follows Core commit semantics (2026-11-02)

- **Problem:** `WebSkinEditBridge` compared a replacement form with the current formal value and returned `no_changes`, bypassing the shared DraftReview commit. Native Deck submits through the API (the custom-Skin path adds Host confirmation). The Core/Store contract treats an approved equivalent replacement as a commit: it advances the formal revision, records the commit, and publishes `FORMAL_UPDATED`.
- **Change:** Removed the Host-side equality shortcut. A valid equivalent replacement now retains Web Host confirmation and then uses the same DraftReview commit API as Native. Existing invalid-form, missing-target, and DELETE routing remains unchanged. No Store transaction, schema, backup, or frozen Core behavior changed.
- **Regression:** `WebSkinEditBridgeTest.equivalentReplacementStillUsesCoreReviewedCommitAndAdvancesRevision` verifies there is no write before approval and exactly one formal revision/commit afterward. Focused suite **39/39 PASS**, XML SHA-256 `469839E5CBA12786FFBFFA674F4B9238B4784C85D789A106493EF691301DB243`.
- **Full verification:** Forced full JVM rerun **908/908 PASS / 139 suites / 0 failures, errors, or skips**; both source boundaries, Debug APK, lint (0 errors / 54 warnings), AndroidTest Kotlin compilation, and `git diff --check` passed (154 tasks). APK **67,043,950 bytes**, SHA-256 `1EA97DCEFA88937FF696C3FED908C1D5BB3586775D3B48AD74906761A2CCF5FA`. Focused/full log SHA-256 `DC90EF720F0A5A5BD93C9199B3375E43BA804EE2C73C85BBE1CACE60019752C1` / `E69C2957030D5415EAF7EA63ABFEA8667824DAFF4DDCED230A0AAF13D7A50034`; reports are under `.local/android-test/reports/phase1-host-draft-commit-20261102/`.
- **Commands:** Focused `:app:testDebugUnitTest --offline --tests dev.powerfulcalendar.ui.v2.WebSkinEditBridgeTest --console=plain`; full `:app:verifySkinSourceBoundary :app:verifyNativeSkinBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --offline --rerun-tasks --console=plain` (process-scoped `ANDROID_HOME=E:\android-sdk`).
- **Limits:** No ADB device or AVD was available. Instrumentation, packaged WebView, and runtime Native/Skin parity are **NOT TESTED**. Phase 1 remains open. Code commit `9dee5ea10ac8c0e280ed5b23f75e0e5f752bcee1`.
