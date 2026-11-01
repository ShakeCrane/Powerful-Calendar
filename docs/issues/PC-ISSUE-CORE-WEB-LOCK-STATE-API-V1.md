# Web Bridge AI lock-state query and Core-free constructor boundary v1

**Date:** 2026-11-02

**Status:** Code complete. Focused and build gates **PASS**; the final full JVM aggregate is **FAIL** on one recurring Memo paging timeout in both attempts. PHASE 1 remains open.

## Gap

`WebSkinEditService` used nine direct `PowerStore.isLocked()` reads for temporary AI-batch preflight and result feedback. After Web persistence reads had moved to Event and DraftReview queries, the service still retained a `PowerStore` constructor dependency. Its compatibility constructor also accepted the Core-internal `DeckController`, leaving an unnecessary Core capability at the transport boundary.

## Change

The nine lock-state reads now call `AiFeatureApiV1.queryBatchState().locked` through an I/O-dispatched helper. The query is used only to report temporary unavailability and avoid beginning UI review while a batch lock is active; it is not write authorization. Existing Event/DraftReview commands and Store transactions remain responsible for final lock enforcement.

Removed both `PowerStore` and `DeckController` constructor dependencies from `WebSkinEditService`. The Host supplies `DraftReviewFeatureApiV1`, `PowerCalCoreFeatureApiV1`, Event and AI APIs, plus Host callbacks. Tests that need a controller adapt it through the Core feature API before constructing the bridge. No Store schema or frozen business semantics changed.

## Expected and actual

- **Expected:** lock prechecks observe the shared AI batch state off the UI dispatcher, preserve locked/unlocked responses and form-retention behavior, and leave final write validation in Core/Store. The Bridge constructor should expose neither Store nor Core controller types.
- **Actual:** recording API tests verify `queryBatchState()` reports `locked=true` during an active batch and `locked=false` after completion. The Bridge implementation has no `PowerStore`, `DeckController`, or `store.` reference. The focused integration checks and build gates below pass.

## Verification

- Focused `WebSkinEditBridgeTest`, `WebSkinRecurrenceBridgeTest`, `WebSkinTrustedWriteReviewTest`, and `CoreApiAdapterTest`: **55/55 PASS** across four XML suites, 0 failures/errors/skips. Focused log SHA-256: `6B4FB0AF7B8A259AD52A4964CD6E613E002CD62F78793214B0CDEC2A2BC3F18B`. XML hashes: Bridge `6DCCE51F64B9F4EA9F6837FA82A86FA497ADCF817BC6395894D69AF0CF1379F4`, recurrence `7BCA2C2CA2296E7A8E9BE4A6993FE12675EC8688B8126C25120FCCA750B6AE57`, trusted write `397AAD534A3D4F770EEEC4DA4CD2F39702620CED41DCFC0853ED6E6F724C82EE`, Core adapter `D9C8EF7A8176AD4B383ABE96C908C8BF99F658ECC16C11006C661D215D24C770`.
- Two forced serial offline JVM runs each collected **883 tests across 138 XML suites**, with 1 failure, 0 errors and 0 skipped. Both failed only at `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites`, a Compose timeout while awaiting the Memo page projection; preserve both aggregate results as **FAIL**. The exact method passed in isolation **1/1**. First/second aggregate log SHA-256: `5C10170707F1D21E74EC20BB595459248DF23F7180465C91C32685ADFF463C48` / `E8F58E14A5C426E1CC0DFFF6F41BBBE34391B4C377D6872CCB8437301BFB5B91`; isolated log: `E91594D3F5A25B29048A813F846D2FFE3A19699B66BB34EA78B681A33156611D`. Failed test XML SHA-256 for the first/second attempt: `299630814E4ACF718688008ABE9963B2C13FE7E363503DC2B4695E8B8AA9556F` / `F22BC405385CB3CDE93643377603C6C9FB68DAE358B88F1903B6FB6BC9CD5939`; isolated XML: `FF53939BC514534C2D0A3016659F5FB897B5AA748C9ACBA263826204994E5397`.
- `:app:verifySkinSourceBoundary`, `:app:verifyNativeSkinBoundary`, `:app:assembleDebug`, `:app:lintDebug`, and `:app:compileDebugAndroidTestKotlin`: **PASS**, 146 actionable tasks; lint **0 errors / 54 warnings**; `git diff --check`: **PASS**. Build log SHA-256: `970724BAD47AA97C6FC135D7C86862C9CFA79D57EB2D76C36554FF33AB1FBAA4`.
- Debug APK: **67,011,182 bytes**, SHA-256 `9F8C6632F5BE1ABAE556C561004B9F2867055DCDFD352AA3DBCD598352B3BEDD`.
- Logs and JUnit XML are under `.local/android-test/reports/core-web-api-only-20261102/`.
- Code commits `d5c68f2eff43a8af01fdb253f8ab27f7daf8fec3`, `3726025ce8f735529427abb36c077eb646ae587a`, and `c66438761953a37ae075e072812238a7dd8c7fa3` follow `4865ad32135647d49a57a2ab81c1d034ad60a53e`.

## Limits and follow-up

No ADB device was connected and no AVD was configured. Android instrumentation, packaged WebView execution, Native/Clear replay, and device/OEM behavior remain **NOT TESTED**. This Issue establishes only the Web API boundary. A bounded comparison against the explicit PHASE 1 exit conditions follows; it does not claim phase completion or skin cutover.
