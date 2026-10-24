# PC-ISSUE-131 — AI replacement with an ambiguous target

Date: 2026-10-24 | Priority: P1 | Status: **core fail-closed path fixed; parent acceptance remains NOT TESTED**

Related: `PC-ISSUE-002` and reconstructed candidate `REC-P1-050`.

## Finding

When a structured AI proposal requested `REPLACE` but supplied no `eventSeq`, the parser correctly blocked the proposal. Its warning nevertheless interpolated the missing value as `#null`, so it did not tell the user how to resolve the ambiguity. The existing service skipped blocked proposals; the defect was the missing actionable clarification message, not an event write.

## Change

`AiResponseParser` now distinguishes a missing target from a supplied ID that is outside the current context. A missing target produces a clear request to identify which event should be changed and keeps the proposal blocked. A supplied but unknown ID retains its existing warning. No UI layout, database behavior, or authorization behavior changed.

The regression seeds two identical formal Activity records, parses a targetless `REPLACE`, passes it through `AiDraftService`, and verifies that it creates no draft, formal event, history entry, identity allocation, or sequence-watermark change. It then reopens SQLite and repeats the persistence assertions.

## Verification

- Pre-fix targeted test failed at the clarification assertion, reproducing the `#null` behavior. Log SHA-256: `B46F0009B4730BC8DAC8C6B90FA1793F1792FA756475A0D454D603DE8F37FFA5`.
- Post-fix targeted regression plus the existing unknown-event-ID guard: **2/2 PASS**. Log SHA-256: `84AEB6F1B874575ABB0979349EA9A459285D5A6B0F34ABC00A0ADCBB7A3A7759`.
- Full command: `:app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --rerun-tasks --console=plain` — **BUILD SUCCESSFUL**, 468/468 tests across 57 suites, 0 failures/errors/skips; lint 0 errors / 58 warnings. The worktree run included the protected untracked `DraftDeckFeedbackTest.kt`; it was not modified or staged.
- Full log SHA-256: `27E990D39B87CE0FB073E318A6B576785840CF9AF281DB4F0D717E3F14065056`.
- JUnit XML SHA-256: `PowerStoreTest` `4A42B0A156D9DD5C62F220D8AB03651C6014EA76DA357BD5C3B1C7DE6DA5B251`; `AiResponseParserTest` `9C63FA7B9180D16CF8324CA3E2A4E16269A46C81EC63332839428346084C1439`.
- Lint XML SHA-256: `E95DE7C795C7A50415CF5BF197DCD2A2FB73D55618CE34068C239E8648FC09DF`.
- Debug APK SHA-256: `B582143592625A51A2C7225156BAA9E83E2B24B344816A0B7C3AEEBB815DCA75`.
- Evidence files are under `.local/android-test/reports/freeze-20261024/pc-issue-131-*`.

## Scope remaining

This verifies the local parser/service safety path only. It does not prove that a live model detects ambiguous natural language or that the chat UI presents the clarification. Therefore `REC-P1-050` remains **NOT TESTED**, and the freeze counts do not change. The existing DeepSeek Flash emulator probe is not repeated; no provider request or physical-device test was made for this issue.

The structural-only ledger validation found 209 unique rows with the 126/82/1 priority partition and unchanged status counts. Artifact SHA-256: `4F97B8068029A5BBDAA5052A01A556E5FDA925290694D6EA5C24E7E3A4EB5929`; ledger SHA-256: `7C6EF6938ECAF938656A3F25B8CE30CAEEC462DC2173D2C604B53BD791FC8F72`. This check did not test product behavior.

## Git

- Code and regression commit: `e8daade48e78997a94c86645730b0226dfdf1cab` — `fix(ai): 提示消歧替换目标`; pushed to `origin/main`.
- Report and ledger documentation are committed separately from the code/test change.
- Overall core freeze remains **HOLD**.
