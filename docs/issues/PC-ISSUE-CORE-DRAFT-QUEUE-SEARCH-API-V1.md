# PC-ISSUE-CORE-DRAFT-QUEUE-SEARCH-API-V1 — Shared Native/Web Draft Deck search

Date: 2026-11-02 (China Standard Time) | Priority: P2
Status: **IMPLEMENTED / FULL JVM AND BUILD GATE PASS; DEVICE PARITY NOT TESTED**

## Finding

Native Draft Deck reimplemented the Core draft matcher inline, while Web Draft Deck converted detached API cards into Core `DraftCard` entities to call `DraftQueueSearch.filter()`. Both implementations currently match case-insensitive title, raw event sequence and formatted sequence and preserve input order. The audit found no evidence of a current user-visible result mismatch; the rule owner and dependency path had diverged, leaving a parity drift risk.

## Change

- Added `DraftReviewFeatureApiV1.searchCards(query, cards)` as a pure operation over a caller-supplied detached snapshot. It returns the matching `DraftReviewCardV1` values in snapshot order and does not read Store, modify editor state, publish changes or mutate cards.
- `DraftQueueSearch.matches(title, eventSeq, query)` is now the single Core-owned predicate. The existing Core-card filter and the API adapter both use it.
- Native and Web Host paths use the same API method. Each Host still limits rendered search hits to four and retains its own search presentation and formal-event search behavior.
- Empty-query behavior remains “all cards in original order,” matching the existing Native/Web paths. The API searches the displayed snapshot rather than refreshing from Store, preserving editor visibility semantics.

## Verification

| Check | Result |
| --- | --- |
| Focused `DraftReviewFeatureApiV1Test`, `WebSkinEditBridgeTest`, `NativeDeckHostProjectionTest`, `DraftQueueSearchTest` | **63/63 PASS** (21 + 37 + 3 + 2), 0 failures/errors/skips. |
| Full forced serial JVM | **904/904 PASS**, 139 XML suites, 0 failures/errors/skips. |
| Native and Skin source boundaries | **PASS**. |
| Debug APK / lint / AndroidTest Kotlin compilation | **PASS**, 154 actionable tasks; lint 0 errors / 54 warnings. APK 67,043,950 bytes, SHA-256 `F894EAACADE12D8BE0EC9AB8C4207C1434A9ACB46BD0C514A3B97F3DC11DA97B`. |
| `git diff --check` | **PASS**. |
| Android device, instrumentation, packaged WebView, end-to-end Native/Skin parity | **NOT TESTED**; no ADB device was available. |

Focused JUnit XML SHA-256 values (`DraftReviewFeatureApiV1Test`, `WebSkinEditBridgeTest`, `NativeDeckHostProjectionTest`, `DraftQueueSearchTest`): `CC40644BCE6F5FBB2E2515C721575E715385FCEFCABEB9E74B46AE8059C6824B`, `0B47E8E60492445F423726C9468872A12DFC68DC0A5BC79B9E91C99F6AE2B3`, `92929A64C35CE3A73520923A7DAE9CA1D69894F9739196A447FC228AB66880D3`, `AB3E520DB03F7E66EDC2EBE23EA7F778D5EF20939B42302DCBF101E4709748DF`.

Full gate log SHA-256: `5ECEB9F0E4AF6498451F2767912B2551280501334668B313AD0EC23D68D6D4A3`, stored under `.local/android-test/reports/phase1-draft-search-api-20261102/`.

The API test covers title casing, raw and formatted sequences, empty query, snapshot order, stable queue order, unchanged Store/editor state, unchanged feature revision, and absence of notifications. The existing Web bridge integration also exercises the API route and preserves the four-result UI cap. These results validate the shared rule and Host bridge path, not on-device Native/Web interaction parity.

Code commit `7231009b53180924fd530d19e281458bdbacadcc` was fast-forward pushed to `origin/feat/skin-first-separation`. PHASE 1 remains open; Native/Web end-to-end search parity and device behavior remain unverified.
