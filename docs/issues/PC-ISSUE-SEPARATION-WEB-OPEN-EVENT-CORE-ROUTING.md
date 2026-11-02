# PC-ISSUE-SEPARATION-WEB-OPEN-EVENT-CORE-ROUTING — Core owns Web formal-event open validation

日期：2026-11-02

状态：Web Bridge regression 与完整离线 Gradle 门禁 **PASS**；Android instrumentation、打包 WebView 与设备级 Native/Web parity **NOT TESTED**。Phase 1 remains open.

## 结论

Web `OpenEvent` previously queried the current Event DTO, separately resolved a recurrence occurrence, converted the DTO back into a Core `TemporalRecord`, and ran Host validation before calling `EventFeatureApiV1.openFormalEvent`. The editor API then repeated the identity, revision, occurrence and writer checks. This created two decision points in the Host route and a race between the preflight and the authoritative editor open.

The Web action now retains only Host-owned availability orchestration (AI lock and an already active editor) and passes `eventSeq`, `expectedRevision` and `occurrenceKey` to the existing Core Event API. The API result owns identity, revision, Store-lock, occurrence and editor-lease outcomes. No new API, persistence schema, backup format or event rule was added. Native continues to use the same formal editor API.

## 根因与修复

| 根因 | 修改 | 回归 |
| --- | --- | --- |
| Web Host read and reconstructed a Core event DTO solely to preflight an operation whose existing API already performs the authoritative checks. | Removed `queryEvent`, occurrence lookup, DTO-to-`TemporalRecord` conversion and `validateOpenEvent` from the action. The existing `EventFeatureApiV1.openFormalEvent` handles identity, revision and occurrence validation. | `WebSkinRecurrenceBridgeTest.webOpenActionDelegatesIdentityAndOccurrenceChecksToCore` covers valid occurrence, stale revision, missing identity, forged occurrence and Host writer unavailable. It checks that the successful Core result owns the writer lease and the formal Store row remains unchanged when rejected. |
| Host messages could collapse distinct Core results or lose the concurrent human-writer lease detail. | A small Host presentation mapper preserves stale/missing, Store-locked, invalid-occurrence and unavailable categories. `EDITOR_OPEN_FAILED` retains the Core message for the race where a second open arrives before Host Compose state reflects the first lease. | `WebSkinBridgeTest.eventOpenOutcomesKeepHostFeedbackCategories` and the recurrence Bridge race assertion check the mapped outcomes. |

Host still blocks the action while AI is locked or a formal editor is already present in UI state. If a competing open wins before that state updates, Core's editor lease is authoritative and its `EDITOR_OPEN_FAILED` message is surfaced. Platform state and message presentation remain in Host.

## 验证证据

- Focused `WebSkinBridgeTest` and `WebSkinRecurrenceBridgeTest`: **21/21 PASS / 2 suites**. Focused log SHA-256: `a356530e194d117eb505cc8a8f9bad2e91d902d8c38a55ae3cbe8e9873cf22c3`.
- Final forced offline Gradle gate: **937/937 JVM / 139 XML suites / 0 failures, errors or skips / 154 tasks**. `verifySkinSourceBoundary`, `verifyNativeSkinBoundary`, `assembleDebug`, `lintDebug` (**0 errors / 54 warnings**), `compileDebugAndroidTestKotlin` and `git diff --check` all PASS. Full log SHA-256: `19544049e8c813995fa996c597f5e6ff817c56f3e4d954628b9de3b04d6e3c09`; deterministic JUnit XML manifest SHA-256: `5cb1835570e1d15c2d36804fc40538928036142fed3ef9fdcdf3bce2a45e7611`.
- Debug APK: **67,060,418 bytes**, SHA-256 `2b81bcba641de44bc54cfb0d10fc07ace609340caf5505b6747d0b0a1f7cf052`.
- Evidence directory: `.local/android-test/reports/phase1-open-event-core-routing-20261102/`; `manifest.json` records toolchain, test totals, APK and log hashes. ADB had no attached devices and no AVD was listed; Android instrumentation, packaged WebView and runtime Native/Web parity are **NOT TESTED**. Skin Studio was not rerun for this Kotlin-only change.

## 修改与后续

Code commit: `f4b146e25bd4e70caf23567f6897cc7b8eae2de2` (`分离：由 Core 统一校验 Web 事件打开`). The Issue and progress records were pushed by ordinary fast-forward; `git ls-remote` verified `feat/skin-first-separation` at `798f764702385815eb83a26f7d104d9e7f682506`. The next review traces the remaining Native and Web formal-event save, move, delete and conflict paths for repeated business decisions or direct Store/service access. Phase 1 remains open.
