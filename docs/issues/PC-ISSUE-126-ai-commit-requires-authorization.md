# PC-ISSUE-126 — AI commit requires scoped one-use authorization

Date: 2026-10-24

Priority: P1 candidate (`REC-P1-054`)

Status: **PASS for the narrowed PowerStore authorization boundary; user-confirmation UI and overall freeze remain HOLD**

## Reproduction and root cause

A new Robolectric API 34 regression called `PowerStore.commit` with `actor="ai"` and no `AiSession`. Before the fix, it failed the assertion requiring `CommitOutcome.NotAuthorized`. The no-session branch in `PowerStore.commit` only checked whether another batch held the lock, then continued into the normal formal-commit transaction. The audit actor string was therefore able to reach the commit path without the capability that the AI batch flow is supposed to carry.

The app's current batch runner supplies the reviewed session. The core store must still refuse the direct AI call if that session is missing.

## Minimal fix

- In the no-session branch, `PowerStore.commit` now returns `NotAuthorized` when `actor == "ai"`.
- AI commits with a session still pass through the existing liveness, epoch, reviewed-scope, version and operation checks.
- Human commits and non-AI test/audit actors keep the existing path.
- Added `AiAuthorizationTest.aiActorCannotCommitWithoutScopedOneUseAuthorization`, which checks that denial creates no formal object, commit history or schedule intent, leaves the draft pending, and releases no lock.
- No UI, network/model, database schema, or user data was changed.

## Verification

Before fix, targeted test failed at the expected `NotAuthorized` assertion:

- Pre-fix log SHA-256 `843260CCCF56DE7CD5F3518AD2C5CAAD6D198F58B6DD67942426E3B39F493BB2`.
- Pre-fix JUnit XML SHA-256 `9DFEEFA48D83AC44009A7873D008D1B11C32C7FB766649A91663FD7FE4CDF6A4` (1 failure, 0 errors).

After fix, targeted test passed **1/1**:

- Post-fix log SHA-256 `9FC4340B447B048B20ACE5ADF96F0AC8B691C4B3D15C6283832EC74DB503636D`.
- Post-fix JUnit XML SHA-256 `B8F2625BC1ED9A5E632001F39B6F5DD6EAB94F1079EE57E4161B8B38AD0D6A62`.
- Full `AiAuthorizationTest` XML: **9/9**, SHA-256 `FA6ECD539A5FBAC7C17B6310F98384DDAF9CA35169237F326DC7F3DA28FD46AB`; this includes the existing valid-session commit, stale replay denial, and the new missing-session denial.

Full command `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` passed **466/466** tests across 57 suites, zero failures/errors/skips; lint 0 errors / 58 warnings.

- Full log `.local/android-test/reports/freeze-20261024/pc-issue-126-full-gradle.log`, SHA-256 `30B7A125DCE04A7711CEDB3B9F97FE7B0DB3B02307454322B80990997327C7C3`.
- Lint XML SHA-256 `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256 `E2EE9DF7C315E2BCD237457CEFC69B3ACF4D830F7D191447DDE724FD0E892A60`.
- Environment: local Robolectric API 34/SQLite and Gradle; no emulator, physical device, live model or UI was exercised. The full run included the protected untracked `DraftDeckFeedbackTest.kt`; it was not modified or staged.

## Git and sign-off boundary

- Code/test commit `fc0e69ac90d25878c660d046a98818629d6c1d39` — `fix(授权): 拒绝无凭据的AI提交` — pushed to `origin/main`.
- This report and the narrowed ledger update are committed separately.
- Structural-only ledger artifact `.local/android-test/reports/freeze-20261024/pc-qa-gate-001-ledger-validation-after-pc-issue-126.json`, SHA-256 `3DC7D2B92820502212C3BF4CCEAECFF56D67BC01EF16FAE1773F0668AB51A75B`; ledger SHA-256 `A11035534F763B61330940D8970B45AABA0D2CAA3AAD1F56804C109FB6006338`. It checks ledger structure, not product behavior.

`REC-P1-054` passes only for the PowerStore rule that an AI commit requires its scoped one-use session. Wiring the user's confirmation screen to the gate, live model behavior, API/OEM device behavior, and the original 209-case mapping remain unverified. Overall freeze stays **HOLD**.
