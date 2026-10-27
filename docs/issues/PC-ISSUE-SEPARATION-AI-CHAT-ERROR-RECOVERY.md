# PC-ISSUE-SEPARATION-AI-CHAT-ERROR-RECOVERY — provider failure and explicit resend

Date: 2026-10-27

Status: **The bounded local-provider failure and user-resend path passes Robolectric integration and the full offline Gradle gate. This does not sign off SEP-09/SEP-10 or S3.**

## Scope and finding

This slice checks whether a user can recover from a transient chat-provider failure by explicitly re-sending the same prompt through the normal chat send action.

No production defect was reproduced. The Host already clears `chatBusy` after a failed request, projects a sanitized error message, retains conversation context, and accepts a later user send. The missing item was a regression that exercised a provider failure followed by an explicit resend and checked privacy and calendar-store invariants.

Clear does not currently expose a dedicated “Retry” button. This test verifies manual resend through the existing composer/send action; it does not claim one-tap replay of a failed request. The Studio’s AI Mock is intentionally non-writing and this issue adds no retry-specific Studio scenario.

## Regression behavior

`WebAiAttachmentFlowIntegrationTest.skinChatFailureCanBeRetriedWithSafeContext` drives the production `PowerCalApp` and `WebSkinBridgeRuntime` using a loopback HTTP server. The first request returns HTTP 503 with a synthetic provider-body marker; the second returns a successful response. No live provider, credential, external network, or paid model is used.

The test verifies:

- The first failure results in exactly one provider request before user action; no hidden automatic retry occurs.
- `chatBusy` returns to false and the Host projection shows HTTP 503 without exposing the provider response body.
- The user explicitly re-sends the identical prompt. The second request includes the original user turn, the sanitized assistant error, and the resent user turn.
- The synthetic provider-body marker is absent from the projected conversation, retry request, and persisted chat history.
- The successful second response is persisted.
- Draft, formal-event, identity, and commit counters are unchanged across failure and resend.

This is a Host/Compose integration check of the closed chat action, not an Android WebView interaction test. It does not verify a dedicated retry control because Clear has none.

## Test evidence

Focused command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain --tests 'dev.powerfulcalendar.ui.v2.WebAiAttachmentFlowIntegrationTest' --tests 'dev.powerfulcalendar.ai.AiConversationTaskSnapshotTest.persistedChatRetainsEvidenceIdsTimesAndReconstructsSameTaskRevision'
```

Result: **5/5 selected tests passed** (four integration-class tests plus the selected persisted-conversation snapshot test); zero failures, errors, or skips. Final focused log SHA-256: `49F4F12B45E9A8704F707EC2D79FFE24317B13EAC5F1F0AEE76F5460FE010944`.

Full offline gate:

```powershell
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --no-daemon --console=plain
```

Result: **589/589 JVM tests, 83 suites, 0 failures/errors/skips, 88/88 tasks; BUILD SUCCESSFUL.** Source-boundary verification, Debug APK, AndroidTest APK, and lint completed. Lint: **0 errors / 52 warnings**. Final gate log SHA-256: `DB2FAF8D11A47EAE304BAA06484A42DE8B419283C0E0AE5CA9BF8A328E9A6CEA`.

Additional Studio checks on the current working snapshot: Python suite **42/42 PASS**; Chrome source-round-trip E2E and the AI/attachment non-writing Mock scenarios passed; `build_default_clear.py --verify` passed for 8 files / 376661 bytes. These are generic source/Mock checks and do not prove a Studio retry workflow.

Evidence directory: `.local/android-test/reports/separation-20261027/ai-chat-recovery/`.

- Full JUnit XML snapshot: `full-junit-final/` (83 suite files; 116097 bytes).
- Verified Clear bundle SHA-256: `0e17aabfe216e8ecb0775dafb8ea66857e25687b09e1167b7c68e1d9ffd81623`.

- Final focused log: `focused-exact-resend-final.log`, SHA-256 `49F4F12B45E9A8704F707EC2D79FFE24317B13EAC5F1F0AEE76F5460FE010944`.
- Final full gate log: `full-exact-resend-final.log`, SHA-256 `DB2FAF8D11A47EAE304BAA06484A42DE8B419283C0E0AE5CA9BF8A328E9A6CEA`.
- Studio Python log: `studio-python.log`, SHA-256 `4A9EF055FEE99904A429FA4E47B060C02B03A21568E66B2E82DBCB03A2F03DD3`.
- Chrome source E2E log: `studio-source-e2e.log`, SHA-256 `770F64A46E489987B810EC0314F18A0F35FC6451C39E62C48774D7A732369510`.
- Test source SHA-256: `A8CA0F20EA89713A6C4A11A4508CBBA8FBC3FE39765CE27D115F72BBBB5C7FAB`.
- Debug / AndroidTest APK SHA-256: `B3115D80E25CBA24B1A6177D1EED5506DB613D467123E1BCE99268B31296780D` / `219E9587BBC4CFDEAEBFAD9FD398E0B0DC2E2FE4D0FAA75C787CAF10DE733CC6`.
- Lint XML SHA-256: `649A98F4109A9CDAAC7F73CA909EE9B58CD14C6DCC70ED9D8E65C8F88A0C5211`.

## Boundaries and remaining work

- No live provider, Android chooser, Android WebView, emulator, or physical/OEM test was run for this slice. ADB and emulator executables were not available on PATH or at the checked repository-default SDK location. Physical-device testing remains user-owned.
- The test resends the identical prompt through the existing send action; it does not prove one-tap replay, retry affordance presentation, process-death recovery, or provider-specific retry policy.
- Studio source round-trip and Mock checks are not a provider-error retry test. Track B remains incomplete for this behavior.
- SEP-09 and SEP-10 remain **NOT TESTED** as complete Native/Skin parity rows. Full multi-turn task handling, all proposal operations, batch recovery, trusted confirmation, Android WebView behavior, and restart/failure replay remain open. S3 remains in progress.
- The first local Gradle invocation stalled before producing output because `JAVA_HOME` was unset and the PATH Java shim was selected. An explicit JDK 20 run then hit the sandbox's write restriction on the shared Gradle wrapper lock under the user profile. The final offline runs succeeded with JDK 20 and authorized access to that existing cache; these startup attempts did not execute tests.

## Git

This issue is test-only; no production app or Studio source changed. Test, narrow SEP-09 wording, and this report are committed separately from the pre-existing Clear source edits. Test commit SHA: pending the final report follow-up commit.
