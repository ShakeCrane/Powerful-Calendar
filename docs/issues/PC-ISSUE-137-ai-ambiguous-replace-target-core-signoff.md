# PC-ISSUE-137 — ambiguous AI replacement target core sign-off

Date: 2026-10-24 | Current risk: **P0** | Reconstructed key: `REC-P1-050` | Status: **PASS within the local core boundary**

`REC-P1-050` is a stable reconstruction key; the current risk priority is P0 because a wrong target could alter a formal event. This report signs only the parser → draft-service → SQLite core behavior. It does not sign live-model disambiguation, chat UI wording, API 35 provider behavior, or physical-device behavior.

## Finding and scope

PC-ISSUE-131 fixed the unsafe `#null` clarification message when a REPLACE proposal had no usable `eventSeq`. Its report left the candidate NOT TESTED because its acceptance environment also mentioned the live model/chat UI path. The current reconstructed matrix separates those claims: the core contract requires uncertain target selection to ask for clarification and forbids silently mutating the wrong event; live language-model and presentation behavior remain separate and untested.

No new production or test code was required. The existing `PowerStoreTest.ambiguousAiReplaceTargetRequestsClarificationWithoutWritingAcrossRestart` directly exercises the core boundary, and this issue reran it on the current baseline.

## Test method

- Robolectric API 34 with an isolated PowerStore SQLite database.
- Create two identical formal Activity records with the same title, then parse a synthetic REPLACE operation without `eventSeq`.
- Verify the proposal remains blocked, its target is unset, and the warning explicitly asks which item to modify.
- Pass the interpretation to `AiDraftService`; verify it creates no draft and leaves formal events, commit history, pending queue, identity count, and next-sequence preview unchanged.
- Close and reopen the same test database; verify those state values remain unchanged.
- No provider/network request, UI, user database, or physical device was used.

## Result

**PASS 1/1.** The ambiguous REPLACE stayed blocked and requested target clarification. `AiDraftService` persisted no draft; the two formal records, their histories, the queue, identity count, and next-sequence preview were unchanged before and after SQLite reopen.

Full offline regression also passed: **469/469 JVM tests across 58 suites**, zero failures/errors/skips; source-boundary verification, Debug APK, AndroidTest APK, and lint passed. Lint: 0 errors / 58 warnings.

## Evidence

- Targeted log: `.local/android-test/reports/freeze-20261024/pc-issue-137-ai-ambiguous-target-core-targeted-r1.log`, SHA-256 `96141B47183B3B8927B1BC6265304AE3E8280EAF1F558D7606BBC4F7982DCC85`.
- Preserved targeted JUnit XML: `.local/android-test/reports/freeze-20261024/pc-issue-137-ai-ambiguous-target-core-targeted-r1.xml`, SHA-256 `434D18A4CC596EFCC9B8B84F6833192B3094DA5EA5E2C6D73C93498C5DA2A595`.
- Full Gradle log: `.local/android-test/reports/freeze-20261024/pc-issue-137-full-gradle-r1.log`, SHA-256 `C2C97732B82813EE678F04F9ACC8CE8C249D797DF808E4D88AC0FB8190C70153`.
- Full `PowerStoreTest` XML: SHA-256 `FCD66D6EEEA42B1D006A4B7D3C2414C0DE5B8F51E52C45EFD7AFFA24EC4AE848`.
- Lint XML: SHA-256 `2BEABC23C862180FE0301AC2F3A3FABDBC432D106CFDDFBC0E1188879BCD1B47`.
- Debug APK: SHA-256 `35EB92E478AD2DEBBD53D03682844D5B1F56E24438469B4A38E89D247B7E40F4`.
- AndroidTest APK: SHA-256 `D168506174F68648C08CF2AFCA62326BC1C56DE424F9E08D61E65CFBB694515A`.
- Tested source baseline: `cc22d49cf79d5638c3a184f88e77ca29cf4fe91a`.

## Remaining coverage

This sign-off does not establish that a live model recognizes ambiguous natural-language references, that the chat interface displays the clarification correctly, or that an external provider or Android device behaves the same way. `REC-P1-047` and PC-ISSUE-002 remain blocked on the separate free-provider path. Overall core freeze remains **HOLD**.
