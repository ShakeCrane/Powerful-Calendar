# PC-ISSUE-CORE-DRAFT-REVIEW-API-V1 — Draft/Review Feature API v1 slice

Date: 2026-11-01 (China Standard Time) | Priority: P1
Status: **PARTIAL / IMPLEMENTED; PHASE 1 OPEN**

## Goal and result

Expose the persisted Draft Deck and its review/approval operations through a platform-neutral Core Feature API shared by Native and Web Deck hosts. Keep formal writes behind trusted Host review and existing Store transactions. Preserve frozen event, recurrence, restore, and authorization semantics.

The `:core-api` module now defines `DraftReviewFeatureApiV1` DTOs and operations. The app Host creates one service over the existing `DeckController` and `PowerStore`; Native and Web Deck flows use that service. This is an implemented Draft/Review vertical slice, not full Core extraction, complete Phase 1 coverage, runtime cutover, or Native/Skin parity.

## Implemented behavior

1. Draft open/refresh flushes dirty edits before changing the selected card. Edit commands require both persisted `draftVersion` and the in-memory `stateRevision`; the controller checks the revision atomically when applying the edit.
2. Commit, conflict replacement, and restore approvals carry the reviewed state revision and draft version. The controller checks them and marks the command busy in one state-lock transition. A dirty reviewed form is staged against its reviewed persisted version; a mismatch aborts without retrying stale form data over the newer card. After a successful stage, Store commit rechecks the resulting version transactionally. Restore rechecks the tombstone revision, draft version, and exact reviewed form in the same transaction that restores the identity.
3. Formal replacement and restore begin as revision-checked proposals; neither operation changes the formal event before approval. Stale Store state returns a review/conflict result and requires a fresh review.
4. Custom runtime/imported Skin requests for commit, conflict replacement, and restore open Host-owned confirmation UI. The review shows the candidate fields, recurrence, reminders, enable/tracking state, and completed instances; the Host checks the captured revision/form before dispatch.
5. Controller state and `stateRevision` are published from the same transition. Successful Draft/Review writes publish through the shared Event API instance.

The API receipt cache is bounded to 128 entries and remains process-local. It does not provide durable exactly-once behavior across process death or adapter recreation.

## Verification

| Check | Result |
| --- | --- |
| Focused DraftReview API, DeckController, Store restore, Host review UI, and Web bridge tests | **190/190 PASS** |
| Forced full `:app:testDebugUnitTest --rerun-tasks --offline --console=plain` | **800 tests / 128 XML reports; 0 failures, errors, or skips — PASS** |
| `:app:verifySkinSourceBoundary` and `:app:verifyNativeSkinBoundary` | **PASS** |
| `:app:assembleDebug` | **PASS** |
| `:app:lintDebug` | **PASS**, 0 errors / 54 warnings |
| Clear editor `node --check` and `git diff --check` | **PASS** |
| Android instrumentation / AVD / physical device | **NOT TESTED**; no AVD was touched |

The Debug APK is `app/build/outputs/apk/debug/app-debug.apk`, 66,683,502 bytes, SHA-256 `C22E82E434CBE5B02CD790FF72932074DA969A10BDF9095416179D3B8331AFB4`. It was built from this worktree and was not installed on a device.

Logs are under the ignored directory `.local/android-test/reports/core-draft-review-api-20261101/`:

- `review-conflict-fix-focused-rerun.log` — SHA-256 `DEFEB24D516AA1C311F2031445E60941D9FC52F17596DCD1F7C04720F504E39B`.
- `full-jvm-final-verified.log` — SHA-256 `15B85AA0D5393C33E0754EB3B907E3CC8A68A08AC4E9C1C89153652D096F7FB0`.
- `boundary-apk-lint-final-verified.log` — SHA-256 `5269F568754C32CA8E09770B53F69F9233BE5FFDD13432A32ACF63E0E115D6AE`.

## Remaining work

- Phase 1 remains open. Formal-event searches, direct completion writes, some Host/Core reads and authorized mutation paths still bypass the shared API/change source; runtime Skin projections still include Core types. Other required domain APIs are not yet defined.
- Native/Skin device parity and instrumentation for this slice remain **NOT TESTED**.
- The request-receipt cache is process-local as described above.

## Git delivery

Implementation branch: `codex/core-feature-api-event`. Code commit `a12e107b868243e0563d5ca904edaf38c777f070`, based on `a3b51394bc0c546f1d7d4e02a2bd8cef9a8d22e9`, was ordinary fast-forward pushed to `origin/feat/skin-first-separation` after a freshness check. Native fallback remains available; no default Skin switch or cutover authorization is implied.
