# PC-ISSUE-166 — Casual schedule mention suggestion button

Date: 2026-10-24

Status: **API 35 chat-screen subcase PASS / PowerCalApp loopback integration PASS / REC-P1-048 live-provider classification NOT TESTED / core freeze HOLD**

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

## PowerCalApp controller integration supplement (2026-10-25)

Added `PowerCalCasualSuggestionComposeTest`, which runs the actual `PowerCalApp` and `NativeAiChatScreen` in Robolectric Compose with an isolated store/preferences namespace and a loopback-only OpenAI-compatible stub. The stub returns a normal assistant reply plus an untrusted `intent=suggested` marker. The test observed the optional “从此消息创建事件草稿” action, no structured review UI, and exactly one chat HTTP request; there was no follow-up `interpret` request. It snapshots and compares draft, pending, formal-event, identity, sequence, queue-order and commit counts, all unchanged. The model marker's fake source is never used as a write; no button was tapped and no event write occurred. No real provider or credential was used.

- New Compose integration test: **1/1 PASS**. Targeted log SHA-256 `CDED618ECA042F79D0C87AA044BE54FBE45EBE4EDE569464242F122B02DD78A2`; JUnit XML SHA-256 `8C61B70DFAC2D2AC1AD61A14804B888C79210A2211ABC7CD639CBF372AFBA763`; test source SHA-256 `3E1C4C7A78CF7F1897C5D515C7473661026C0323A6986083F25AA43EE9AE65C8`.
- Related AI chat regression: **16/16 PASS across 4 suites**. Log SHA-256 `DAD7CB29011E756126650BC0CFFB32E1997D0A23A70E9F7CBA4A77D16D052D73`.
- Full clean detached-worktree gate with this exact test source copied in: **541/541 JVM tests, 74 suites, 0 failures/errors/skips; 88/88 tasks**, including `assembleDebugAndroidTest`; lint **0 errors / 53 warnings**. Log SHA-256 `CC533471A9113C8B2F8382468632442F2E92DB87F5F940397E974F7F366840CF`. Debug / AndroidTest APK SHA-256 `517DFFFCCFECA6E4B6CBF5961B3CAAA8F2802B3B3B1DD107457180C268F61614` / `2EF06F141A6AEEA12F6272D9C8861D7D6EEC33A316D0F4734A221C90BF7BE0B2`; lint XML SHA-256 `B00DE07D9F4E8C693E9B029589B583E2C3FB05D7B80BF28554FD0CAFCDF760FC`.
- Structural ledger validation after the evidence update: 218 rows / 217 active reconstructed; 204 PASS / 0 FAIL / 10 BLOCKED / 2 NOT TESTED / 1 SPEC UNRESOLVED / 1 RETIRED; P0/P1/P2 = 145/71/1; historical IDs mapped = 0. Ledger SHA-256 `CB9AF259F8149F494090CD83F78550D7144113EF048122143C94017E529BAD03`; validator artifact SHA-256 `568DF6C3FD6A6853AC78976E7C990886980A892CFC28124ABC90CE048C3B549D`. Validator unit tests passed 2/2. `product_behavior_tested=false` describes the structural validator only.
- The main worktree's broader JVM run was **542/542 across 75 suites**, including another protected untracked unit test; no AndroidTest compilation was attempted there. The clean worktree gate above excludes unrelated untracked files and passed AndroidTest compilation.
- Two test-harness failures were retained under `.local/android-test/reports/freeze-20261025/pc-issue-166-controller-integration/`: an initial unresolved Compose test import (no test body ran), then Robolectric SQLite startup with a 292-character Windows temp database path. Shortening the isolated database filename fixed the path issue. Neither failure involved production code.

These results verify the PowerCal controller's behavior for a supplied SUGGESTED classification. They do not verify that a live free model classifies casual language correctly or that this complete path passes on a physical device/API 35 emulator.

## Remaining limits

The earlier API 35 test starts with a synthetic message; the new loopback Compose test covers `PowerCalApp.submitAiChatInput` through the production chat screen but supplies the classification from a local stub. No live provider request was made, so correct classification by a free model remains **NOT TESTED** and `REC-P1-048` remains **NOT TESTED**. PC-ISSUE-002 remains **PARTIAL / BLOCKED**. The overall freeze remains **HOLD**.

Structural-only ledger validation r35 retains 211 rows / 210 active reconstructed candidates: 198 PASS / 0 FAIL / 8 BLOCKED / 2 NOT TESTED / 2 SPEC UNRESOLVED / 1 RETIRED; historical IDs mapped=0 and `product_behavior_tested=false`. Artifact SHA-256 `737FBDCE9BD75E2ABE362BD8C8AD6C47A4BF7B6FD46FC5D26C0E3B137254F013`; ledger SHA-256 `9073E925A8DD2ECEDD486F429740EE143CB65726777EA1657595074BD68D840E`. This is a structural check only and does not change the row's NOT TESTED state.

## Commit

Test-only commit `d081a7ebb16dd7889c7386770c3bb1e63571bfbb` (`test(AI聊天): 验证随口建议仅显示可选按钮`) is pushed to `origin/main`.

The PowerCalApp loopback integration test, its evidence supplement, and the ledger evidence update are delivered in the follow-up commit recorded in `docs/agent-handoff/PROGRESS.md`. `REC-P1-048` stays **NOT TESTED** until a real free provider classification is observed.
