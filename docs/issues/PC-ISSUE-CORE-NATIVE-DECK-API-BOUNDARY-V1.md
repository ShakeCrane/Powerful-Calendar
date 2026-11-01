# Native/runtime Draft Deck through DraftReview Feature API v1

**Date:** 2026-11-02

**Status:** RESOLVED — API boundary, focused/full JVM verification, and build gates **PASS**. Device, instrumentation, and packaged WebView behavior remain **NOT TESTED**. PHASE 1 remains open.

## Gap

`SkinnableDraftDeck` accepted a `DeckController`, observed its Core state through `observeAdditional`, and read controller state alongside the already-injected `DraftReviewFeatureApiV1`. `PowerCalCoreFeatureApiV1` also exposed its controller as an `internal` property, which `PowerCalApp` read and passed into the deck. This left the Native/runtime presentation adapter coupled to a Core controller even though deck actions and versioned snapshots were already available from the shared DraftReview API.

## Change

`DraftDeckDialog` now accepts only `DraftReviewFeatureApiV1` plus Host callbacks. It initializes and observes `DraftReviewSnapshotV1`, reads the last discard identity from that snapshot, and refreshes error/state projections through the API. `PowerCalCoreFeatureApiV1` retains its controller input only as an adapter constructor parameter and no longer exposes a readable controller property. `PowerCalApp` no longer reads or passes the controller. Compose and instrumentation call sites were updated to use the API.

Store-backed controller state, serialized mutation behavior, approval snapshots, temporal conversion, and the existing Native fallback remain behind the Host/API adapter. This change does not alter the DraftReview contract or frozen event semantics.

## Expected and actual

- **Expected:** Native and runtime Draft Deck presentation observe the same detached DraftReview snapshot and call the same versioned commands. No `DeckController` state or observer should enter `SkinnableDraftDeck` or `PowerCalApp`.
- **Actual:** the final `SkinnableDraftDeck`/`PowerCalApp` source scan finds no `DeckController`, `controller.state`, `observeAdditional`, or `coreFeatureApi.deckController` reference. API observer state contains the fields the renderer adapter uses, including `busy`, `aiLocked`, and `lastDiscardId`. Existing Native/runtime Compose coverage passed.

## Verification

- Focused `CoreApiAdapterTest`, `DraftReviewFeatureApiV1Test`, `GlassComposeTest`, and `GlassRenderTest`: **42/42 PASS** across four XML suites, 0 failures/errors/skips. Focused log SHA-256: `9B5826362B1F0CDC5C96970C2DF13EA5F1FB2404A01260F555E4ADE67D1FFC9D`. XML SHA-256: Core adapter `67DFD7C69D95DC12FF5683243C91D35DB231E38DB367EEC7A27AFAA80809BF4E`; DraftReview API `0B8E8E14508E7EF9A59DDE4B5BC43EFC2F31DA4034E962809FD5CD4408E9DF6B`; Glass Compose `A6F9FA3E8705077D04C085BA09DE70FA1AAFB486AFCDDE957035FAC5F3FAF498`; Glass render `07511FF855F939DA216EF343C6573C2F96C9E1E7DECA6FDD0654A509A1E5739A`.
- Forced serial offline JVM suite: **883/883 PASS across 138 XML suites**, 0 failures/errors/skips. Full log SHA-256: `7FBC2D92B3AFF4BFA938030185EA84F781CF7BD5EA1F5ACD6ABC05F3E87B8097`.
- `:app:verifySkinSourceBoundary`, `:app:verifyNativeSkinBoundary`, `:app:assembleDebug`, `:app:lintDebug`, and `:app:compileDebugAndroidTestKotlin`: **PASS**, 146 actionable tasks. Lint: **0 errors / 54 warnings**. `git diff --check`: **PASS**. Build log SHA-256: `0513C2E603C65B9420BAB083AAA652C29C07A32D00812A0982FEB9968C4BC63F`.
- Debug APK: **67,011,182 bytes**, SHA-256 `F264694BE380E0D1D2D885A17173E2932311FD0BA106F9A337C4FF67E708775B`.
- Logs and JUnit XML are under `.local/android-test/reports/phase1-core-controller-encapsulation-20261102/`. Two intermediate compile attempts in the same report directory captured stale call-site/compiler errors; the final focused and full gate logs above passed after updating those call sites.
- Code commit `97acf03ae736d817f9d92381d75b90b6c6f43b94` follows `1118bc062216a5904d5dea6f7619283f80a41e76`.

## Limits and follow-up

ADB had no connected device and `emulator -list-avds` returned no configured AVD. Android instrumentation was compiled, not run; Native/runtime device parity and packaged WebView behavior remain **NOT TESTED**. This establishes an API-only Deck presentation boundary, not full Native/Clear parity or cutover. The next PHASE 1 work is a bounded reconciliation of remaining Settings/Data and Host workflow call sites; do not treat that as permission to expose credentials or Android system capabilities through Core.
