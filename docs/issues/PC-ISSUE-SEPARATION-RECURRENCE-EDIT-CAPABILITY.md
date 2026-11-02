# PC-ISSUE-SEPARATION-RECURRENCE-EDIT-CAPABILITY — Core-owned single-occurrence edit capability

日期：2026-11-02

状态：Core API, Web Bridge and Native Host automated regressions, full offline Gradle gate, source boundaries and APK **PASS**；Android instrumentation、打包 WebView 与设备级 Native/Web parity **NOT TESTED**。Phase 1 remains open.

## 结论

Core `EditSession` is the final authority for repeated-event edits. For `SINGLE_OCCURRENCE`, it permits title and time changes and rejects the other form fields at save. Two Host paths separately inferred that rule: Web Bridge rejected patch names locally, while Native Direct Event Editor derived its restricted UI from `form.recurrenceScope`.

The existing `EventEditorSnapshotV1.capabilities` now carries `EDIT_SINGLE_OCCURRENCE_TITLE_AND_TIME_ONLY` when Core has selected that scope. Web uses the capability for its existing immediate rejection and response; Native uses it to hide/disable unsupported fields and kind conversion. Both still submit allowed edits through the same Event API, and Core retains final save validation. No new feature API or persistence change was added.

## 根因与修复

| 根因 | 修改 | 验证 |
| --- | --- | --- |
| Bridge duplicated Core's single-occurrence field list at the Web patch boundary. | The Bridge consults the capability in its current `EventEditorSnapshotV1`; it preserves the existing “仅本次只能修改标题和时间” response and rejects before applying the patch. | `WebSkinRecurrenceBridgeTest.singleOccurrenceRequiresScopeAndSurvivesCheckpoint` verifies the immediate response, unchanged editor form and unchanged formal Store row. A whole-series notes patch still succeeds. |
| Native inferred the same business restriction from the recurrence-scope DTO. | `DirectEventEditorContent` now derives occurrence field restriction and kind-change availability from the Core capability. The scope DTO remains for selection presentation. | `DirectEventEditorComposeTest.recurringEditorRequiresScopeAndSingleScopeEditsOnlySelectedTitleAndTime` asserts the capability arrives after selecting single scope and the restricted UI remains. |
| Event API snapshots did not communicate the restriction owned by its `EditSession`. | The adapter adds the capability only for selected `SINGLE_OCCURRENCE`, in both initial and refreshed formal snapshots. It is absent before selection and for `WHOLE_SERIES` / `THIS_AND_FUTURE`. | `EventFeatureApiV1Test.recurrenceEditorSnapshotOwnsSingleOccurrenceFieldCapability` verifies all scope transitions. |

## 验证证据

- Focused tests: **35/35 PASS / 3 suites** — Event API 15, Web recurrence Bridge 14, Native Direct Event Editor 6. Log SHA-256: `71BFC3F8E87FE3027655F11CE724E8B13B6C88D58DA0EBF5A2426D985AE495E5`.
- The first invocation stopped before Gradle task evaluation because the isolated worktree had no `local.properties` and the shell lacked `ANDROID_HOME`. The verified SDK at `E:\android-sdk` was supplied through command-scoped environment variables. The first compile then failed on a Kotlin line-break syntax error in the capability membership expression; that syntax was corrected. The evidence manifest records both attempts and the exact compiler diagnostic summary; the raw failed compile log was not redirected.
- Final forced offline Gradle gate: **937/937 JVM / 139 XML suites / 0 failures, errors or skips / 154 tasks**. `verifySkinSourceBoundary`, `verifyNativeSkinBoundary`, `assembleDebug`, `lintDebug` (**0 errors / 54 warnings**), `compileDebugAndroidTestKotlin` and `git diff --check` all PASS. Gate log SHA-256: `406B9DD640065FED12C771E10354F6ED7101DC7E6BD95AB61950E45FF160812E`; deterministic JUnit XML file manifest SHA-256: `05F6A6A4499D6C5EC74B9BD83E900EF060C2E6C0F10C99F09D1DCDF55D39FAEB`.
- Debug APK: 67,076,802 bytes, SHA-256 `51973A19F40EE9DE28ABDF22FB6900494AF95A0DEBE10B0AD8B0C43A0941F4DA`.
- Skin Studio suite: **65/65 PASS**, log SHA-256 `EB6247315AF58A3776074C5752BF86DF50C9BE1680FD1DF7119A07A4D07785A3`. Clear JavaScript `node --check` PASS.
- Evidence directory: `.local/android-test/reports/phase1-single-occurrence-capability-20261102/`; summary: `manifest.json`. ADB had no devices and emulator AVD list was empty; instrumentation, packaged WebView and runtime Native/Web parity are **NOT TESTED**.

## 修改与后续

Code commit: `c8e22ce0147dfec361362e63ba91519003944ab5` (`分离：由 Core 提供重复单次编辑能力`). Progress and roadmap are updated in the documentation commit.

The next bounded issue is the duplicate event/occurrence preflight before `PowerCalCoreFeatureApiV1.openFormalEvent`; preserve Host AI-lock and active-editor orchestration, then make the existing API result own identity, revision and occurrence validity. This code and Issue were included in the successful fast-forward push to `feat/skin-first-separation` at `f533bf4c56ca56a88d71227ce697104dd2373611`.
