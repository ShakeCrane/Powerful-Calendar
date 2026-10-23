# PC-ISSUE-005 — Explicit restoration of retired events

Date: 2026-10-23 | Severity: P0

Status: **CORE PATH FIXED / SOFTWARE PASS; PRODUCT ACCEPTANCE PARTIAL / HOLD**

## Finding and root cause

The store could preserve a tombstone and had a `restoreDraft()` commit path, but it had no normal way to create a new actionable draft against that tombstone. Delete finalizes or discards the event's active draft, while `newDraft()` rejects retired events. Consequently, the existing `NeedsRestore` prompt and `restoreDraft()` path could not be reached from a newly created restore proposal.

This left the explicit-approval and same-identity restoration contract without a usable core entry point. The existing retirement test only verified that an old committed draft could not restore the event.

## Change

- `PowerStore.proposeRestoreDraft()` now creates or resumes a `REPLACE`/`CONVERT` proposal from the tombstone snapshot. It leaves the event retired, allocates no new `eventSeq`, and rejects unknown or active identities.
- `DeckPersistence` and `StoreDeckPersistence` expose the proposal operation; `DeckController.beginRestore()` opens it in the existing Draft Deck flow. Committing the proposal still returns `NeedsRestore`; a separate confirmation calls the restore commit.
- `restoreDraft()` rejects non-user actors and cards that are not `REPLACE` or `CONVERT`. Successful confirmation reactivates the same `eventSeq` at the next revision and rebuilds its desired schedule.
- No Compose/UI source was changed. The core controller entry point is not yet connected to an application action that lets a user select a tombstone; that product path remains open.

## Verification

Targeted command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.powerfulcalendar.core.PowerStoreTest.explicitRestoreProposalKeepsTombstoneUntilHumanConfirmationAndReusesIdentity" --tests "dev.powerfulcalendar.core.DeckControllerTest.explicitRestoreEntryOpensProposalAndRequiresSeparateConfirmation" --offline --rerun-tasks --console=plain
```

Result: **2/2 PASS**, 0 failures/errors/skips. The SQLite test proves the event stays retired through proposal and `NeedsRestore`, rejects an AI restore actor, then reactivates the same identity at revision +1 with unchanged sequence watermark and a pending reminder. The controller test proves proposal opening and separate confirmation.

Targeted log: `.local/android-test/reports/freeze-20261023/pc-issue-005-restore-core-targeted-final-20261023.log` (SHA-256 `A60E93DB1727BE512F2C31A11EF3FBA7A50CAE661F1369A179017F9E871FBFBC`).

Full command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 366 JVM tests, 0 failures/errors/skips; lint 0 errors / 55 warnings. The full working-tree count includes 365 tracked tests and one protected untracked test; that test file was not modified or staged.

Full log: `.local/android-test/reports/freeze-20261023/pc-issue-005-restore-full-gradle-final-20261023.log` (SHA-256 `0F0271F238F4D6ACA62B0916A132854305740ECABB7CD7CCC12108A2C58B91F3`). Debug APK SHA-256: `9677B9816B9028EF5C25EC84025ABA56DDB92BEB5CF36B2197AC5615315BB0AE`.

## Git and remaining acceptance

- Code/test commit: `40e5dd35bd238da476f67507994b6c100b646b89` (`修复(核心): 补齐墓碑恢复审批入口`), pushed to `origin/main`.
- The SQLite and pure Kotlin controller paths pass. No Android device or UI entry-point test was run for this change. Keep this issue and the core verdict **PARTIAL / HOLD** until the user-facing tombstone selection path and applicable device acceptance are verified.
- No schema/data migration was made. Protected untracked assets remain untouched.

## Next

Continue the P0 candidate ledger with independently traceable core tests; keep the missing user-facing restore entry and device acceptance visible as HOLD.
