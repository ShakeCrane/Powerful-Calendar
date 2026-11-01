# Core DraftReview persisted draft query by ID v1

**Date:** 2026-11-02

**Status:** RESOLVED — scoped Core API routing and regression evidence PASS; Phase 1 remains open.

## Gap

`WebSkinEditBridge` reread persisted drafts with direct `PowerStore.draft(draftId)` calls during conflict review, restore review, commit review, and resolution. This bypassed the versioned DraftReview query boundary for state used to validate Host workflows.

## Change

Added `DraftReviewFeatureApiV1.queryDraft(draftId): DraftReviewCardV1?`. The Store adapter returns a detached card for any persisted status, including non-actionable terminal/conflict states; non-positive or missing IDs return `null`. The query does not open or mutate the Deck editor, write Store data, advance the Event feature revision, or publish a change.

All seven persisted-draft rereads in `WebSkinEditBridge` now use the DraftReview API. Existing status/form/version comparisons, Deck revision checks, Host approval gates, and transactional write validation remain in place. No business semantics, database schema, or backup format changed.

## Expected and actual

- **Expected:** API reads reflect the current persisted card by identity, including its status and version; invalid/missing IDs are absent; Web review paths retain their optimistic checks and approvals; reads are side-effect free.
- **Actual:** The new API regression verified pending and discarded cards, form/version/identity fields, invalid and missing IDs, and unchanged editor snapshot, Event feature revision, Store identity/sequence/draft/commit counts, and notifications. A recording Web adapter verified conflict review calls `queryDraft`; source inspection confirms `WebSkinEditBridge` has no remaining direct `store.draft(...)` rereads.

## Verification

- Focused `DraftReviewFeatureApiV1Test` and `WebSkinEditBridgeTest`: **53/53 PASS** (16 API tests and 37 Bridge tests). XML SHA-256: `72D7FA229ED55FA243130A0F1E4BA8C36072CE57560E5F141A1AE08368039314` / `B01796ECB30C993E150A5818BDB784447A1E8545538F0ECA4D23E5C62FEB0C92`.
- Forced serial `:app:testDebugUnitTest`: **883/883 PASS**, 138 XML suites, 0 failures/errors/skips. Totals were independently summed from the retained XML reports. Full log SHA-256: `3C12CB655FE771791F4E6C762418F046B4D93F349A9F574A3D829C61E53E7D9E`.
- `:app:verifySkinSourceBoundary`, `:app:verifyNativeSkinBoundary`, `:app:assembleDebug`, `:app:lintDebug`, `:app:compileDebugAndroidTestKotlin`: **PASS**, 146 actionable tasks. Lint: **0 errors / 54 warnings**. `git diff --check`: **PASS**.
- Debug APK: **67,011,182 bytes**, SHA-256 `EDFC858B1A7A95F625F2E8FB28FDC956B7A1DD3EAD0190E9C46B7974C064E3FA`.
- Focused log SHA-256 `24C4234D60BF020D7C6A4EFB2FB6786F2B114D5BE2052C7135C4D61D34EEF51F`; build log SHA-256 `861AE6754ABEDDF65C798C93ED01FC24FAB9D342F1B7D813B3C5CDC4962E76FA`. Reports: `.local/android-test/reports/draft-review-query-by-id-20261102/`.
- Code commit `1b0c21a0cfee077caf96b709033dd45399ad440a` (`1b016c4d13f2951be61b6849fb18260a6acd36b9` parent).

## Limits and follow-up

No ADB device was connected and no AVD was configured. Android instrumentation, packaged WebView execution, Native/Clear parity, and device/OEM behavior are **NOT TESTED**. This Issue closes only the persisted draft query boundary slice and does not claim Phase 1 completion or cutover readiness.
