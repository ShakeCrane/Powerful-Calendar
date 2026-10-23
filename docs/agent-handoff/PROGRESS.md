# Core freeze progress

Updated: 2026-10-24

## PC-ISSUE-086 — whole-series recurring rule persistence

- Added and pushed core regression `PowerStoreTest.wholeRecurringSeriesEditKeepsIdentityAndAnchorAcrossRestart`: `EditSession` saves the changed title/rule while keeping the event identity and original anchor; recurrence projection and stored state remain stable after SQLite reopen.
- Focused regression passed 1/1 (log SHA-256 `394711335A4B9DE80FEE60B81FF224C85645883FBA16403AC25A496A683550AA`; XML SHA-256 `25D33D615FBE7DD1FDD0AF36BA4727C467EE698B0139F69BA25546FCC129F2CE`). Full Gradle including source boundary passed 441/441 across 56 suites, zero failures/errors/skips; lint 0 errors / 58 warnings. Full log SHA-256 `1C898C47EFC096A0A6919609F543DE3FC3B25F63E0FA19EF8B263DA9DC602173`.
- Test commit `49f334e8cea1005de59ef9b98a16e32f544b937e` is pushed. Independent report: `docs/issues/PC-ISSUE-086-whole-series-core-edit.md`. `REC-P0-080` remains NOT TESTED because explicit whole-series scope selection and device behavior were not exercised; this is partial core persistence evidence only.
- Latest freeze remains HOLD: 129 PASS / 0 FAIL / 9 BLOCKED / 64 NOT TESTED / 7 SPEC UNRESOLVED; 108 reconstructed candidates have independent targeted evidence. Historical 209 IDs remain unmapped.
- User scope: no UI work; faded horizontal edge is accepted, and the blue current-time marker may cover the center axis. Keep Issue 002 PARTIAL/BLOCKED and do not retry around the free-model rate limit or call unauthorized paid models.
- Protected untracked files `DraftDeckFeedbackTest.kt`, `design/`, and `PC-OBS-005` remain unchanged and unstaged.
- Next: continue with the next feasible non-UI core acceptance item.

## PC-ISSUE-085 — recurring this-and-future split

- Core transaction implementation and regression tests committed and pushed: `cf1be08500f02adc0edba12c9c97bc4c7d53fea1` (`feat(重复): 支持指定实例及后续序列拆分`) and `86043231d14594cce89942f8847c5fe66e7bd178` (`fix(重复): 保留首个有效实例后的剩余次数`).
- Fix covers atomic old-series truncation/new-sequence creation, compatible occurrence-state migration, rollback, and retaining the existing identity when no earlier active non-excepted occurrence exists. If the selected slot becomes the anchor and the count is unchanged, only the remaining count is retained.
- Both controlled pre-fix reproductions failed on the identity/count mismatch; final focused set passed 4/4. Full Gradle, including source boundary, passed 440/440 across 56 suites; lint 0 errors / 58 warnings. Full log SHA-256: `0F4780D20727E1F0418E337F070A9229C4D1D898E5829DCB7A8AC42BDB25D433`.
- Independent report: `docs/issues/PC-ISSUE-085-this-and-future-series-split.md`. `REC-P0-079` remains NOT TESTED because editor scope selection/call path and API 35/physical-device behavior are not covered. `REC-P0-078` default scope and `REC-P0-080` whole-series selection remain open.
- No UI changes. User accepted the faded horizontal edge and confirmed the blue current-time marker may cover the center axis. Do not reopen UI work.
- At the Issue 085 snapshot, freeze remained HOLD: 129 PASS / 0 FAIL / 9 BLOCKED / 64 NOT TESTED / 7 SPEC UNRESOLVED; 107 reconstructed candidates had independent targeted evidence. Historical 209 IDs remained unmapped.
- Next: continue with the next feasible non-UI core acceptance item; keep Issue 002 PARTIAL/BLOCKED and do not bypass free-model rate limits or use unauthorized paid models.
