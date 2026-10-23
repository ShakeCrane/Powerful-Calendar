# PC-ISSUE-045 — Stale proposal is not silently field-merged

Date: 2026-10-23

Severity: P0 candidate invariant

Status: **CORE STORE REGRESSION PASS / OVERALL FREEZE HOLD**

## Scope and finding

`REC-P0-052` requires that stale proposed fields are not silently merged into a newer formal record. The durable store behavior is covered by the focused explicit-reapproval regression: an unapproved stale commit conflicts and preserves the complete newer state; an explicitly approved commit replaces the snapshot as a whole.

This candidate shares its evidence method with `PC-ISSUE-043` because the same transaction tests both approval gating and the no-merge invariant. No assertion is counted that the test does not make, and no separate UI behavior is claimed.

## Evidence

`PowerStoreTest.staleDraftRequiresExplicitReapprovalAfterRestart` was added in commit `07c7a30919fea42d090dcd7d646ea37322eb9fbd` (`test(冻结): 验证过期草稿显式重审`). A replacement based on revision 1 becomes stale when completion advances the formal record to revision 2. After SQLite reopen, default commit returns `CommitOutcome.Conflict`, retaining the newer title and completion key. Only a subsequent `approveStale=true` commit replaces the complete snapshot as revision 3, at which point the old completion key is cleared by the approved proposal.

The method passed its targeted pre-commit and post-commit runs, each **1/1 PASS**. Its assertions also pass in the latest full Gradle run: **407/407 tests**, 55 suites, zero failures/errors/skips; lint **0 errors / 55 warnings**.

- Targeted pre-commit log `.local/android-test/reports/freeze-20261023/pc-issue-043-explicit-stale-reapproval-targeted-precommit-20261023.log`, SHA-256 `27FCBBB172186ED037984407591F653CA64BC8588B1EBC5BD2599F20E85F9A78`; JUnit XML SHA-256 `152DB5F4203021BDACD20ED1E45F8C50AD113DE47B79C00EBDEAC4D738184CB1`.
- Targeted post-commit log `.local/android-test/reports/freeze-20261023/pc-issue-043-explicit-stale-reapproval-targeted-postcommit-20261023.log`, SHA-256 `A6D078A431A9870A8E89C49529A5E1F692355BB15E73A9E305C913A6325643B6`; JUnit XML SHA-256 `7A015A456728DFFA6A4046127BEE6B154D9649F6209528EBA056DBF46432190A`.
- Latest full Gradle log `.local/android-test/reports/freeze-20261023/pc-issue-044-whole-snapshot-replacement-full-gradle-20261023.log`, SHA-256 `236ABC1658703D51D816D1D56E24ECB2A037F10E9DEEB87D0A65C79AA3CE0663`; `PowerStoreTest` XML SHA-256 `1F9E906660EE8FC700BE0030E176A1B0D6A871BE62A8A6EFA6BA00B829357047`.

No code or test source was changed for this separate candidate mapping; the focused regression above is the evidence. No production code, business data, or protected untracked assets were changed.

## Candidate sign-off and limits

`REC-P0-052` is marked **PASS** in the reconstructed candidate ledger for the no-silent-field-merge store contract. It remains `UNMAPPED` to the original 209-case checklist. Overall core freeze remains **HOLD**.

The linked structural-only ledger validation is `.local/android-test/reports/freeze-20261023/pc-qa-gate-001-ledger-validation-after-pc-issue-045.json`, SHA-256 `7B2EAA14CDD64A11D68F2211E62DC83C492C68B075C07C44BDEB7277F71BA33F`; it validates row structure only, not product behavior.

## Commit and push

- Evidence test commit: `07c7a30919fea42d090dcd7d646ea37322eb9fbd` (pushed to `origin/main`).
- This independent candidate report and synchronized ledger/verdict updates are committed and pushed separately.
