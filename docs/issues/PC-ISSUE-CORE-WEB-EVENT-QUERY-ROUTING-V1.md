# PC-ISSUE-CORE-WEB-EVENT-QUERY-ROUTING-V1 — Active Web edit reads through Event API v1

Date: 2026-11-02 (China Standard Time) | Priority: P1
Status: **IMPLEMENTED / FOCUSED AND BUILD-GATE PASS; FULL JVM FAIL**

## Goal and result

Move active formal-event rereads in `WebSkinEditBridge` from direct `PowerStore.get()` calls to the existing `EventFeatureApiV1.queryEvent()` contract. Preserve the current Host writer-availability checks, event revision comparisons, recurrence review, explicit formal-write approval, and transaction-time validation.

All nine active-record rereads now call one suspending Host helper that executes `queryEvent(eventSeq)` on `Dispatchers.IO` and converts the detached `EventRecordV1` to the existing private `TemporalRecord` representation. These reads cover timeline move staging, edit/delete review, conflict and restore-draft review, commit revalidation, and final Host review resolution. The Web Bridge has no remaining `store.get()` calls. A retired or missing identity continues to appear as missing/stale because `queryEvent` returns `null` for both.

Two conflict-review paths previously held `synchronized(store)` while making the read. Entering the synchronized Event API under the Store monitor would reverse the lock order used by Event completion writes (Event API monitor, then Store monitor). Those outer monitor blocks were removed; the API query is serialized by the Event adapter and the existing revision/transaction checks remain authoritative.

The Web bridge regression fixture wraps the Event API with a recording delegate. `timelineMoveRejectsStaleOwnerRevisionWithoutStaging` asserts the stale move path calls `queryEvent` for the requested identity, returns `stale`, and creates no draft. Existing Web edit tests cover save/delete confirmation, recurrence scope, stale formal revisions, conflict review, and approval resolution.

Implementation:

- [`WebSkinEditBridge`](../../app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridge.kt)
- [`WebSkinEditBridgeTest`](../../app/src/test/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridgeTest.kt)
- [`EventFeatureApiV1`](../../core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/CoreFeatureApiV1.kt)

## Verification

| Check | Result |
| --- | --- |
| `EventFeatureApiV1Test` | **8/8 PASS** |
| `WebSkinEditBridgeTest` | **37/37 PASS** |
| Focused aggregate | **45/45 PASS**, 0 failures/errors/skips |
| Final forced serial JVM suite | **FAIL**: 882 tests / 1 failure; `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` timed out waiting for Memo page 2 |
| Isolated Memo paging method | **1/1 PASS** |
| Skin and Native source-boundary checks | **PASS** |
| Debug APK assembly | **PASS**, 67,011,182 bytes |
| `lintDebug` | **PASS**, 0 errors / 54 warnings |
| AndroidTest Kotlin compilation | **PASS**, 146 tasks in the build gate; instrumentation was not run |
| `git diff --check` | **PASS** |
| Android device / AVD / packaged WebView | **NOT TESTED**; ADB listed no devices and `emulator -list-avds` listed no AVDs |

The full JVM aggregate is recorded as **FAIL**, despite the sole unrelated failing method passing by itself. Focused and build-gate results do not override the full-suite status.

The Debug APK SHA-256 is `9F2F912D2CD75AA85469025AD4FCD485941569A22545560C4B46565D671A3584`.

Evidence is under the ignored directory `.local/android-test/reports/active-event-query-api-20261102/`:

- Focused log SHA-256 `15974BD5997125888C1DCF0582016CB215EE98DCA2573C264F25FDCE7BCBD0A9`.
- Focused JUnit XML SHA-256: Event API `5E388B048A5F80FC65A126708E69FC9A5E60808BE833B03D020981C125933EE1`; Web bridge `368083E539E47948DBBEC161418C5FB94254538F5DBF0C3F130C84E365DB8A4E`.
- Full JVM log SHA-256 `2532494A0221C1A44BD0D49CC59A76A7C68209D824489EEB458665F74D1B5B5D`.
- Isolated Memo log / JUnit XML SHA-256 `24C4234D60BF020D7C6A4EFB2FB6786F2B114D5BE2052C7135C4D61D34EEF51F` / `6F5D885BCAB029C4115D0C5587F1037DEE0AF1EA6BFAF16999841C5AE60A4DC8`.
- Source-boundary / APK / lint / AndroidTest Kotlin log SHA-256 `84F1B01EBCA14437B3EAF3447216D20CDFAD1443A833A44163F4FE88A76733ED`.

## Remaining work

- Web Bridge persisted-draft and lock-state rereads still use Store methods in other workflows. These remain separate API-routing slices.
- Device, packaged WebView, and Native/Clear parity remain **NOT TESTED**. This issue does not close Phase 1 or authorize runtime cutover.

## Git delivery

Code commit `d4ee1c465346c897d597f634bd03dafd9459611d` (`通过EventFeature API读取Web编辑事件`) follows remote baseline `e0e0176a9a026c8e7f7805c0b87c07fec8dea590`. The integration target is `origin/feat/skin-first-separation`; only fast-forward delivery is permitted.
