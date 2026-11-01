# PC-ISSUE-CORE-RECURRENCE-API-V1 — Recurrence Feature API v1

Date: 2026-11-02 (China Standard Time) | Priority: P1
Status: **IMPLEMENTED; scoped verification PASS; full serial JVM aggregate FAIL; PHASE 1 OPEN**

## Goal and result

Expose existing recurrence calculations through the versioned, platform-neutral
`RecurrenceFeatureApiV1`, then route Native/Web Host consumers through the shared Core provider
without changing recurrence semantics or formal state.

The API accepts detached `EventRecordV1` snapshots and an explicit local zone. It exposes:

- `queryNextOccurrence()` for the next slot at or after an instant, including the original key,
  interval, and skipped/moved flags.
- `queryOccurrenceByKey()` for materializing a specific original slot, including moved overrides,
  or returning `null` for an invalid, excepted, or out-of-series key.

The app adapter delegates to the existing `RecurrenceEngine`. Settings diagnostics use the shared
next-occurrence query for up to three repeating events. Web timeline event-open validation and Web
formal-write review use the by-key query over the same event snapshot used for revision checks.
The query itself does not authorize writes or establish that its snapshot remains current; existing
Core editor, approval, and Store transaction checks remain in force. Native timeline/month
recurrence expansion in `GlassPresentation` remains outside this slice.

## Verification

| Check | Result |
| --- | --- |
| Recurrence API, Web Host, Web edit bridge, and diagnostics focused suites | **92/92 PASS** across 8 XML suites; 0 failures/errors/skips |
| Full serial JVM attempt A | **FAIL**: 891 tests, 1 Compose timeout in `WebAiProposalReviewIntegrationTest.memoPagingContinuesTheSortedProjectionWithoutStoreWrites`; exact method isolated **1/1 PASS** |
| Full serial JVM attempt B | **FAIL**: 891 tests, 1 assertion in `WebSkinSystemDiagnosticsIntegrationTest.settingsProjectsNativeDiagnosticsAndTimelineReceivesNoSystemDiagnostics` (`expected 3`, observed 0); exact method isolated **1/1 PASS** |
| `verifySkinSourceBoundary` and `verifyNativeSkinBoundary` | **PASS** in both aggregate attempts and the separate build gate |
| Separate build gate: `assembleDebug`, `lintDebug`, `compileDebugAndroidTestKotlin` | **PASS**, 146 tasks executed |
| Debug APK | **PASS**, 67,011,182 bytes; SHA-256 `11D546714DF1AD20D02EC7FE915DDFD1126080BA8E9A91A143F4CFE6DC29B2FE` |
| Lint | **PASS**, 0 errors / 54 warnings |
| Android device / AVD | **NOT TESTED**; no connected device or configured AVD |

The aggregate JVM state is **FAIL**. Each failing Compose method passed in its exact isolated rerun,
but the reason each appears only in a full serial run is unresolved. The scoped recurrence/Web
regression set passed; it does not replace the failed aggregate result.

Evidence is under `.local/android-test/reports/phase1-recurrence-occurrence-key-api-20261102/`:

- `focused-rerun.log` SHA-256 `5223B7B4F0BB9F7D7E8BC86787F375721F6181632110E5635548318B9D0B7863`
- `focused-junit-manifest.csv` SHA-256 `E5412A340F82454BB97521070C10D1CE83C26A22543594012BC362C0B2524DE6`
- `full-gate.log` SHA-256 `DC49CE0EF19533813FA8F983F313EDDD5C34A95C9332B4BEAFFC65FFE86539C0`
- `memo-paging-isolated.log` SHA-256 `F61DBCE94641A473A2B5EAAE54E011673F91ABF191B8BFD281BD3EEEC940A09F`
- `full-gate-rerun.log` SHA-256 `110C0996BE3C131B2AC3E1DEB07C6FEC196479B4A67DADD78CD55C347A017C6B`
- `system-diagnostics-isolated.log` SHA-256 `69817AECD362EA81D2DDD8713E6CDA50C458FA44EE852D3C7BF60037072029A7`
- `build-gate.log` SHA-256 `B10DFECD5D6C6B7149031DC2A17B0D384F28D4DEBDA606789CF1D1A09A52C119`

No database schema, backup format, event state, recurrence policy, or authorization rule changed.
Android instrumentation, packaged WebView, Native/Clear parity, and physical/OEM behavior remain
**NOT TESTED / NOT SIGNED OFF**. PHASE 1 remains open.

## Next work

Add a bounded recurrence-window query and route Native `GlassPresentation` occurrence expansion
through the Core API. Preserve existing occurrence keys, DST behavior, timeline geometry, and
visible-window limits. The separate `createDraft` cross-adapter replay and backup-retention choice
remains owner-dependent; this API change does not modify it.

## Git delivery

Code commit `1f2506b178cebed4a92dd4ee0f01ade975325a08` follows remote base
`52816aa9fb50dd0fe7927d0f7e5b8039651f0058` on `codex/core-feature-api-event` and was fast-forward
pushed to `origin/feat/skin-first-separation`. The issue, API contract, roadmap, parity matrix, and
handoff progress evidence are committed in the subsequent documentation slice.
