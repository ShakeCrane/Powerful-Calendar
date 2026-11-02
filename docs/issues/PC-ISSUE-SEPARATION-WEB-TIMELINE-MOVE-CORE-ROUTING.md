# PC-ISSUE-SEPARATION-WEB-TIMELINE-MOVE-CORE-ROUTING — Core owns current event revision checks

日期：2026-11-02

状态：Web Bridge regressions and final full offline Gradle gate **PASS**; Android instrumentation, packaged WebView and device Native/Web behavior **NOT TESTED**. Phase 1 remains open.

## 结论

Before this change, Web `timeline.move` loaded the current Event DTO and independently rejected a missing event or stale revision before calling `EventFeatureApiV1.stageTimelineMove`. Core already checks event existence, expected revision, move eligibility, and replacement staging as one API operation. The extra Store read created a second decision point and a race between validation and staging. Native sends identity/revision directly to the same Core operation.

The Web Host now retains its distinct presentation-boundary check that the requested revision belongs to the current visible timeline projection. It then passes the request identity/revision to Core, which owns the authoritative Store check. Core missing/conflict receipts map back to the existing Web `stale` state, detail messages, and refresh signal. Successful moves remain review drafts; they never directly update a formal event.

## 根因与修复

| 根因 | 修改 | 回归 |
| --- | --- | --- |
| Web read the current Event solely to repeat identity/revision checks made atomically by `stageTimelineMove`. | Removed the `queryCurrentFormalEvent` read and call Core with `cmd.eventSeq`, expected revision, snapped target and request ID. | `timelineMoveRejectsStaleOwnerRevisionWithoutStaging` proves the API receives a stale request, returns `EVENT_CONFLICT`, requests refresh, and stages no draft. |
| Removing that read must not weaken the Skin's current timeline-projection constraint or change Web response semantics. | Kept `visibleEventRevisions` validation before Core. The adapter maps Core `EVENT_MISSING` and `EVENT_CONFLICT` to the previous `stale` status and localized details. | `timelineMoveRequiresOwnerProjectionForTheDisplayedRevision` verifies off-projection requests stop before Core; `timelineMoveDelegatesMissingOwnerToCoreWithoutStaging` verifies a matching projection with a retired owner reaches Core and does not stage. |

No new API, database schema, backup format or product move rule was added. Existing date-only DEADLINE policy remains shared: Native and Web allow it, retain DATE precision, and impose no target-year bound; Core form validation decides whether the replacement can be staged.

## 验证证据

- Focused `WebSkinEditBridgeTest.timelineMove*`: **7/7 PASS**. This includes projection gating, missing/stale Core receipts, DATE precision, a 2201 target, duration preservation and unsupported kinds. Log SHA-256: `02c1b03f6bc6f273cbcf7ab777f3aa6c867aeab1c6a66b5e30de23e3a58f1267`.
- First full forced gate: **FAIL** at `SystemCalendarImportTest.rejectedItemsArePersistedAsWarningsWithoutCalendarWrites`; its separate read-only `commitCount()` connection received `SQLiteDatabaseLockedException` for `SELECT COUNT(*) FROM commits`. Log SHA-256: `82695e8f18f2ed94cd49d91170f6038c19f448aac8193cc40d0cb0277290b8ff`.
- The failing system-calendar test passed in isolation (**1/1**), log SHA-256 `ffc0caf725bdd524c6cb96d9a835812bec32fc32c77d7987fbca83855b81030d`. A second full forced offline gate then passed **938/938 JVM / 139 XML suites / 0 failures, errors or skips / 154 tasks**. Both source boundaries, Debug APK, lint (**0 errors / 54 warnings**), AndroidTest Kotlin compilation and `git diff --check` passed. The initial failure and both follow-up runs are retained; the unrelated test was not modified. Final gate log SHA-256: `19544049e8c813995fa996c597f5e6ff817c56f3e4d954628b9de3b04d6e3c09`; JUnit XML manifest SHA-256: `5ca93105aa9c1672bc787988bd7ceb57516f157c2e1f30ec13e27f6b82703b7b`.
- Debug APK: **67,060,418 bytes**, SHA-256 `6681f9bdacd074f34950573e2ad5b2a6397dedbffb0adf2ea371b8af7fb91340`.
- Evidence: `.local/android-test/reports/phase1-web-timeline-move-core-route-20261102/manifest.json`. ADB had no attached devices and no AVD was listed; Android instrumentation, packaged WebView and runtime parity remain **NOT TESTED**.

## 修改与后续

Code commit: `b982c6d7040e80c6dd3f28d411dfb6d38370e9bc` (`分离：由 Core 原子校验时间轴移动`). The subsequent ICS import retry/checkpoint review is now implemented through the existing Import/Export Feature; see the [ICS Import Workflow Issue](PC-ISSUE-SEPARATION-ICS-IMPORT-WORKFLOW-CORE-OWNERSHIP.md). The next bounded review aligns the Native/Web AI batch mode type at the Host boundary while preserving the existing explicit confirmation and one-time approval flow. Phase 1 remains open.
