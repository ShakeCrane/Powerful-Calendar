# PC-ISSUE-CORE-OCCURRENCE-COMPLETION-API-V1 — Guarded occurrence completion command

Date: 2026-11-01 (China Standard Time) | Priority: P1
Status: **IMPLEMENTED / VERIFIED; PHASE 1 OPEN**

## Goal and result

Route Native and Web Memo completion toggles through one versioned Core Feature API command without changing completion, recurrence-slot, transaction, or frozen event semantics.

EventFeatureApiV1.setOccurrenceCompletion accepts the event sequence, expected event revision, original occurrence key, expected prior completion state, requested state, and request ID. Under the Store lock it validates identity and current revision, enabled/tracking state, occurrence existence, and the caller's observed completion state, then delegates the actual write to PowerStore.setCompleted and verifies the resulting revision/state. Stale revision or state returns CONFLICT; unavailable occurrences return INVALID; a locked Store returns LOCKED; an already-satisfied state returns NO_CHANGE. Exact request replay returns the cached receipt, while mismatched request-ID reuse is rejected. Only an actual verified update emits one FORMAL_UPDATED event.

Both Native Memo and Web completion paths now call this command. The Web Host retains a current-entry visibility precheck; this API migration does not remove unrelated Host/Core reads.

## Verification

| Check | Result |
| --- | --- |
| Focused Event API, adapter, Deck, and Web tests | **64/64 PASS** |
| Forced full JVM suite | **803 tests / 128 XML reports; 0 failures, errors, or skips — PASS** |
| AndroidTest Kotlin compilation | **PASS**; instrumentation behavior was not run |
| Skin and Native source-boundary checks | **PASS** |
| Debug APK assembly | **PASS** |
| lintDebug | **PASS**, 0 errors / 54 warnings |
| git diff --check | **PASS** |
| Android instrumentation / AVD / physical device | **NOT TESTED**; no device was touched |

Regression tests cover a single-occurrence update, unchanged Store commit count on no-op, exact replay, request-ID reuse, stale revision/state, invalid occurrence, and completing/reopening an original recurrence slot. The Debug APK at app/build/outputs/apk/debug/app-debug.apk is 67,552,075 bytes, SHA-256 `03108E62D7BE9473C65A887D87BB906EDEAAD7AFFA46B18D0415BCD864161C06`; it was not installed on a device.

Evidence logs are in `.local/android-test/reports/core-event-search-completion-20261101/`:

- `focused-final-v2.log` — SHA-256 `999EE10F8088119C45B619AF5E45A4F60C586B312A0F55066E7744746385B3B5`.
- `full-jvm-final.log` — SHA-256 `7747FC1513088D62269C92374E0D0B7CC1901C9F72E381D55C5C021C7CBE7178`.
- `android-test-compile-final.log` — SHA-256 `2E6062946E55A8E8013E5B874F00FDB0EAF9B8136B788BB23B96A859B4DFDDCC`.
- `boundary-apk-lint-final-v2.log` — SHA-256 `0502BA9261D5D6657E57EC7699A8BD0AF95CA3884E5BE8E47A69243A979B8B32`.

## Remaining work

- Request receipts are bounded and process-local; they do not provide durable exactly-once behavior across process death or adapter recreation.
- The Memo visibility precheck was subsequently routed through `MemoFeatureApiV1`; see the [Memo API report](PC-ISSUE-CORE-MEMO-API-V1.md). Other Host workflows still use direct Core projections or Store reads. Other authorized writes and the shared change source remain to be migrated.
- Instrumentation, WebView/device parity, and cutover are **NOT TESTED / NOT SIGNED OFF**. Native fallback remains available.

## Git delivery

Implementation commit `c9948d5465bc93da7e59984342c756549df9e295` on `codex/core-feature-api-event`, based on remote tip `d3bb0700d6c3911c651025e92c1906e8975eae14`, was ordinary fast-forward pushed to `origin/feat/skin-first-separation` after a freshness check.

## Follow-up — reminder intent consistency (2026-11-02)

The first completion API slice exposed a later revision-fencing gap: `PowerStore.setCompleted()` advanced the formal event revision and wrote its audit commit, but left the durable desired reminder row bound to the previous revision. `ReminderScheduler` correctly rejected the old broadcast, so the completion could silently leave the next reminder unscheduled. The existing `changingCompletionTrackingReconcilesTheReminderAtTheNewRevision` test covered editing the tracking option, not completing or reopening one occurrence.

The Store transaction now calls `writeDesiredIntent()` with the updated event after the completion/reopen commit. This preserves the independent `trackingEnabled`, `enabled`, and reminder policy fields while binding the next desired intent to the new revision. Both Native and Web Memo callbacks call the same Host helper after an `UPDATED` receipt; it reconciles through `ReminderFeatureApiV1` and reports an event-specific blocked or pending state without duplicating scheduling rules.

A new regression covers a scheduled dated Memo through complete and reopen: both revisions retain the same next reminder occurrence, trigger instant, and requirement; each new durable row returns to `PENDING`, is reconciled, and validates at the current revision. The previous revision is rejected, and the Robolectric AlarmManager retains one registration. The Web Compose integration also performs the completion through the Event API and waits for the Host reconcile to leave the new row `SCHEDULED` or honestly `BLOCKED`. Before the Store fix, the regression failed with expected reminder revision `2` but observed `1`.

| Follow-up check | Result |
| --- | --- |
| Focused Reminder and Web Memo completion tests | **20/20 PASS** (19 + 1), log SHA-256 `9F92A366C74EAC48FBB398C7D0B4D5FC3D9E02D5AA020B2E3F068D0B8C80DBE8`. |
| Combined AI Proposal, Reminder, and Web Memo focused regressions | **37/37 PASS** (17 + 19 + 1). Relevant full-suite XML hashes: AI `310B04DF35876A32DA33540B29EA331ABAE447BCE4685C7D3E38297BCED76D05`, Reminder `61E6DC438AADD0BE222E5F333766918FC561E1F785A1498EAB7E98702D3BF036`, Compose `BF684DDA75FDFFD5146AE1FF19D139B9947F455306DC6E180F6AACCE5ADAC2F3`. |
| Forced single-worker full JVM | **906/906 PASS**, 139 XML suites, 0 failures/errors/skips. |
| Native and Skin source boundaries | **PASS**. |
| Debug APK / lint / AndroidTest Kotlin compilation | **PASS**, 154 tasks; lint 0 errors / 54 warnings. APK 67,043,950 bytes, SHA-256 `A184290D72852F71F9A7C487C48ECE0AFE2847DACA2057E35D1A3B9732374D47`. |
| Full gate log | SHA-256 `8EB354C25C4AA9E2DFD81776A29A02EF530D26919AB3D9E09A503385DAED9EE4`, under `.local/android-test/reports/phase1-reminder-completion-20261102/`. |
| Focused integrated log | SHA-256 `3D29E621AC02FC6E462EE3A657FA4A0700CFAA8D807CA3F8BAFE59B9946FD15B`, in the same directory. |
| Android device, instrumentation, packaged WebView, Native/Skin runtime parity, physical/OEM behavior | **NOT TESTED**; `adb devices -l` listed no device. |

Implementation follow-up commit `d07ada6de56a86711dbe3dbddc2cf0c60b90352f` was validated with the forced full gate. The PHASE 1 scope remains open.
