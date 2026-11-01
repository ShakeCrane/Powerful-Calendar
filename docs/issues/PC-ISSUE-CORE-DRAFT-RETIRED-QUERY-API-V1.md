# PC-ISSUE-CORE-DRAFT-RETIRED-QUERY-API-V1 — Retired-event restore queries through Core API v1

Date: 2026-11-02 (China Standard Time) | Priority: P1
Status: **IMPLEMENTED / FOCUSED VERIFICATION PASS; FULL JVM AGGREGATE FAIL**

## Goal and result

Move Web Bridge retired-event recovery listing and tombstone rereads behind the shared, versioned `DraftReviewFeatureApiV1`. Preserve Store ordering, page behavior, separate Host restore approval, revision checks, and the transaction-time restore validation.

The API now exposes `queryRetiredEventsPage(offset, limit)` and `queryRetiredEvent(eventSeq)`. The page query accepts offsets `0..1,000,000` and limits `1..100`, then maps the existing Store page order (`updated_ms DESC, event_seq DESC`) to detached `EventRecordV1` DTOs. The tombstone query returns a detached record only while the identity is still retired; invalid, missing, or active identities return `null`. Its lookup and retirement check run under the Store monitor so the result cannot pair a record with a later retirement state.

Web Bridge retired-page loading, initial restore tombstone capture, stale-page revalidation, keep/restore validation, post-commit reload, and final review resolution now call the DraftReview API. The Web Bridge has no remaining direct calls to `store.retiredPage`, `store.getIncludingRetired`, or `store.isRetired`. This slice adds queries only: it does not write Store data, advance event or DraftReview revisions, allocate identities, or publish changes. Restore still requires its separate Host approval, and the existing restore transaction rechecks the reviewed draft, tombstone revision, and form.

Implementation:

- [`DraftReviewFeatureApiV1`](../../core-api/src/main/kotlin/dev/powerfulcalendar/core/api/v1/DraftReviewFeatureApiV1.kt)
- [`PowerCalCoreFeatureApiV1`](../../app/src/main/java/dev/powerfulcalendar/core/api/v1/PowerCalCoreFeatureApiV1.kt)
- [`WebSkinEditBridge`](../../app/src/main/java/dev/powerfulcalendar/ui/v2/WebSkinEditBridge.kt)
- [`DraftReviewFeatureApiV1Test`](../../app/src/test/java/dev/powerfulcalendar/core/api/v1/DraftReviewFeatureApiV1Test.kt)

## Verification

| Check | Result |
| --- | --- |
| DraftReview API + Web restore regressions | **17/17 PASS**, 2 JUnit XML files, 0 failures/errors/skips |
| Full forced serial JVM run 1 | **FAIL**: 882 tests; `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites` timed out waiting for Memo page 2 |
| Full forced serial JVM run 2 | **FAIL**: 882 tests; same Memo paging timeout plus `SystemCalendarImportTest.rejectedItemsArePersistedAsWarningsWithoutCalendarWrites` received SQLite `BUSY` while reading its fixture |
| Isolated Memo paging method | **1/1 PASS** |
| Isolated System Calendar import method | **1/1 PASS** |
| Skin and Native source-boundary checks | **PASS** |
| Debug APK assembly | **PASS**, 67,011,182 bytes |
| `lintDebug` | **PASS**, 0 errors / 54 warnings |
| AndroidTest Kotlin compilation | **PASS**; instrumentation was not run |
| `git diff --check` | **PASS** |
| Android device / AVD / packaged WebView | **NOT TESTED**; the available ADB list and AVD list were empty |

The 882-test aggregate results are recorded as **FAIL**, despite both unrelated failure methods passing their isolated reruns. The focused slice tests pass; the aggregate run is not represented as a clean full-suite pass.

The Debug APK SHA-256 is `7970E012E2AEA506FF8B7C31B8DA89E3172B5D4C84B7E9CAB144D661CE966A7C`.

Evidence is under the ignored directory `.local/android-test/reports/draft-review-retired-api-20261102/`:

- Focused log SHA-256 `7173BD79C7C73C7AFA28FF93AC4782B0E017E8031A36E54678EBA6369FABFDB6`.
- Focused JUnit XML SHA-256: DraftReview `325F9FB80EF88856DE20BA65D6F3AB4C6AB14093376BBA1C843A3F0C91F34BEB`; Web restore `779BC0F92A692663F2F5546787507B5BDF3562DE35F73D05C682A28DF19AC997`.
- Full JVM attempt 1 log SHA-256 `5AA00A004E7D490AA814B1415AE6721233374D35674BEED86F7C2DDAE88A4EB9`.
- Full JVM attempt 2 log SHA-256 `D156A1CFE1DD3DB11F69CA0FDE9E724C032C4967040CFCBBB68B7D3F53A57F1B`.
- Isolated System Calendar log SHA-256 `70C2CDE4BDC17B01EE8830B7CB88347EA9169956825EDFF980A09ED1D3F0DED7`.
- Source-boundary / APK / lint / AndroidTest Kotlin log SHA-256 `619BF90957F5D6D0F03B1D5FBDBD6C1543027FE893379D398B3A56BE2671BA93`.

## Remaining work

- `WebSkinEditBridge` still has direct Store reads of active formal events and persisted drafts/lock state in other workflows; these are separate migration slices.
- Device, packaged WebView, and Native/Clear parity remain **NOT TESTED**. This issue does not close Phase 1 or authorize runtime cutover.

## Git delivery

Code commit `092b9d2c48ed29539a242692a343d724d49c9470` (`将墓碑恢复只读查询接入DraftReview API`) follows remote baseline `867a0c4725c1c180bab13bc53efc28bf3a6245ee`. The integration target is `origin/feat/skin-first-separation`; only fast-forward delivery is permitted.
