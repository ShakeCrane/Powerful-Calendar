# PC-ISSUE-166 — Casual schedule mention suggestion button

Date: 2026-10-24

Status: **API 35 chat-screen subcase PASS / REC-P1-048 NOT TESTED end-to-end / core freeze HOLD**

## Scope

`CORE_CONTRACT_FREEZE_2026-10-21.md` invariant 10 says an explicit calendar request should open a proposal preview, while a mere mention of a possible schedule may offer a lightweight action. PC-ISSUE-147 already fixed the local routing distinction and added stub-backed parser/client coverage. This follow-up verifies the production chat screen's optional-button interaction on an API 35 emulator.

The test uses synthetic `AiChatMessage` values with a suggested proposal source. It does not call a provider, read AI settings, open PowerCal's store, or create a draft. No production code changed.

## Verification

- API 35 `emulator-5556`, AndroidJUnitRunner: **1/1 PASS** for `AiChatSuggestionAffordanceInstrumentedTest.casualScheduleMentionOffersButtonOnlyUntilUserTapsIt`.
- The production `NativeAiChatScreen` displayed “从此消息创建事件草稿” for an assistant message with a suggestion source. Rendering did not invoke `onPropose`, and no proposal review appeared. Tapping the button invoked the callback exactly with the original casual user text `下周也许找时间开个会。`.
- The pre-existing local routing tests remain as recorded in PC-ISSUE-147 (13/13 focused JVM tests); they use a loopback stub and do not establish real-model classification.
- Full forced-offline Gradle command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --rerun-tasks --console=plain`: **BUILD SUCCESSFUL**, 496/496 JVM tests across 67 suites, 0 failures/errors/skips; all 88 tasks executed.

Evidence SHA-256:

- Final API 35 runner log: `34CF02E1CDE5316E48352217666080A7E57A4FAB28EE77EABEDF26607AE532AD`.
- Full Gradle log: `6CB27E33765748096F388520C9AECAF65DDEAC03179B897C19D8B82F4D640835`.
- Debug APK: `290F13ADC31CAF38A17A1506E9C1AF57C19B2743BBC2F36CCB261918CBAC85C1`; AndroidTest APK: `9C8C887B1AC86236415889A1A665E25497D3E121C318C2BAD5A3474E9A64EE4B`.
- Test source SHA-256: `8A9FE647C5BA57DC75F7F57E98BDA064513A25E669F412E6A6F7E1902DF1DC92`.
- Initial compile attempt failed because Compose test extensions were imported individually; the import was corrected before the final build. Compile-r1 log SHA-256 `9BBC99B7D8DD769BED1D0C86CC13CA53E4F16466686DB30CB04F52CEACDFC7AC`; successful compile-r2 log SHA-256 `A4B94ECA5FA5DEFD1F3F85D9456651371C310DE19F282134514F47B0CB269E95`.

Artifacts are under `.local/android-test/reports/freeze-20261024/pc-issue-166-casual-chat-affordance/`.

## Remaining limits

This test starts with a synthetic suggestion message and verifies only the production chat screen. It does not establish that a live free model correctly classifies casual language, nor exercise the `PowerCalApp.submitAiChatInput` controller-to-screen path. No provider request was made. Therefore `REC-P1-048` remains **NOT TESTED**, and PC-ISSUE-002 remains **PARTIAL / BLOCKED**. The overall freeze remains **HOLD**.

Structural-only ledger validation r35 retains 211 rows / 210 active reconstructed candidates: 198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED; historical IDs mapped=0 and `product_behavior_tested=false`. Artifact SHA-256 `737FBDCE9BD75E2ABE362BD8C8AD6C47A4BF7B6FD46FC5D26C0E3B137254F013`; ledger SHA-256 `9073E925A8DD2ECEDD486F429740EE143CB65726777EA1657595074BD68D840E`. This is a structural check only and does not change the row's NOT TESTED state.

## Commit

Test-only commit `d081a7ebb16dd7889c7386770c3bb1e63571bfbb` (`test(AI聊天): 验证随口建议仅显示可选按钮`) is pushed to `origin/main`.
