# PC Issue: Native 0.9.5 AI history projection mismatch

Date: 2026-11-06

Status: FIXED (narrow history completeness and scroll behavior scope); overall AI page remains PARTIAL

Reference source: `b1f7629f0c52812e1887443872f43aa6c49908c4`

Reference APK SHA-256: `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`

Product Cutover: HOLD

## Observed behavior

Frozen Native loads up to 200 persisted chat messages and passes the full list to `NativeAiChatScreen`, whose `LazyColumn` contains `itemsIndexed(messages)`. A single rendered `Text` has a 75-line ellipsis limit, while copy/edit/proposal actions continue to reference the full underlying message.

The separated Web v3 host queries the same Core-owned conversation, but `WebAiChatHistory.project()` drops all except the last 40 messages and truncates each projected content string to 1600 characters. No history paging action exists. Core separately limits an AI request context to 80 messages; that does not define what the screen can display. The Host state feed has a 512,000-character bound, so sending an unbounded full conversation in one state frame is not a safe fix.

## Reproduction

Seed both isolated app sandboxes with the same synthetic fixture: 42 alternating user/assistant messages, including a final 1945-character message ending in `LONG_HISTORY_END_SENTINEL`.

- Frozen Native APK on `PowerCal_Native095_RawFixture_20261106` / serial `emulator-5590`, API 35, 1080×2400, 420 dpi.
- Current Web APK `EF29297DF78D253E2A9C3ED03B24C81664CC23CD2A429874B8C7E348D3E3789B` on `PowerCal_Wave2_QA35_20261104` / serial `emulator-5556`, API 35, 1080×2400, 420 dpi; WebView 124.0.6367.219.
- Installed frozen APK SHA-256 matched `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76` after pulling it from the Raw AVD.

Results: Native screen top displays `HISTORY_00_SENTINEL`; Web history top begins with `HISTORY_02_SENTINEL`. Native bottom shows `LONG_HISTORY_END_SENTINEL`; Web exposes only the first 1600 characters of the last message and has no end sentinel. The Web accessibility tree reports 39 numbered messages (indices 02–40) plus the long final message, 40 total. Screenshots and the fixture are under `.local/android-test/reports/native095-ai-history-audit-20261106/manifest.md`.

## Scope and safe fix direction

This is a confirmed UI projection loss; the Core-owned conversation source remains present. Navigation and scrolling were the only screen interactions; the test did not send, clear, edit or propose a chat message and did not invoke a Calendar Core write action.

Implement bounded, on-demand history projection through the existing Host/Core conversation snapshot, preserving full message content and making earlier messages reachable without exceeding `WebSkinStateFeed`'s 512,000-character frame limit. Preserve the current closed action schemas, stable Core message IDs, message revision checks and Native scroll anchoring behavior. Update the Studio Host binding catalog and preview contract for any new read-only Host action. Do not add Core business rules or raise the state transport limit to hide paging risks.

Pre-fix adjacent risk, resolved below: Web `renderChat()` reset `scrollTop` to the bottom on every render. The browser E2E now checks prepend anchoring, follow-to-bottom, and append while reading.

## Data restoration

After capture, both apps were force-stopped. The Raw AVD's pre-test two-byte empty history file was restored from an app-private backup. The Web AVD's original chat-history file was absent, so the synthetic file was removed. Its onboarding prompt preference was restored to `false`, the value that had caused the displayed “暂不导入” choice. No calendar import was initiated, and no AVD was wiped or app data cleared.


## Verified implementation (2026-11-06)

The narrow history projection defect is fixed without changing the Core conversation contract:

- `WebAiChatHistory.project()` now serves bounded pages of at most 40 full messages, capped at 128 KiB serialized JSON per page. It preserves the existing per-message 6000 UTF-16 code-unit boundary and stable message IDs; it no longer truncates display content to 1600 characters.
- `PowerCalApp` retains an in-memory projection cursor over the existing Core-owned conversation snapshot. The closed-schema `loadOlderChatHistory` Host action carries the observed conversation revision and client cache offset. Host ignores stale revisions, serves only a bounded page from the matching snapshot, and advances Web state to release pending requests; it does not write chat or Calendar data.
- Clear Web merges contiguous pages by absolute offsets and verifies overlapping message objects. It preserves the reader anchor when prepending older messages and follows new messages only when the reader was already near the bottom.
- The Studio Host catalog and preview expose the same read-only action and the implemented 6000-character send limit.

### Verification

- Serial Gradle full gate: PASS; 987 JVM tests, zero failures/errors/skips; Skin and Native source boundaries, `assembleDebug`, `lintDebug`, and AndroidTest Kotlin compilation/package PASS.
- Focused `WebAiChatHistoryTest` and `WebSkinHostProjectionTest`: PASS. Host catalog tests: 9/9 PASS. Node syntax checks, Clear paging E2E, and Studio Clear bridge authoring/export E2E: PASS; Studio bridge export verified 8 untouched source files and matching canonical Clear source SHA.
- Browser paging E2E: PASS; 84 messages merged in order after a delayed stale-page request interleaved with two appended messages. The stale revision was rejected, the authoritative newer page retained cached messages, the 1945-character tail was preserved, prepend anchor delta was 0 px, follow-to-bottom was conditional, and reset invalidated the old cache.
- Full Studio Python suite: 87/90 PASS. Three unrelated stale source-shape assertions remain FAIL: `test_full_route_registry_has_one_restore`, `test_privacy_editors_remain_host_owned_but_placeable`, and `test_native_settings_and_hidden_activity_rescue_are_both_available`. They were not modified or treated as passing.
- Final Debug APK SHA-256: `acabdf1fbb3df1c998d6b743851b01f3289042aa1159c8871364ca21063614ae`. It contains exactly one `powercal-clear-default.pcskin`; source/APK package SHA-256: `cd09d73f021fd34bc3ce56534df5b72e5ba26a8bba87e6e34dc3ffda2bc4e0c8`.
- Real Android WebView check on `PowerCal_Wave2_QA35_20261104` / `emulator-5556`, API 35, WebView `124.0.6367.219`, 1080x2400 at 420 dpi: the installed final APK displayed 40 current messages, then actual ADB touch swipes reached the list top and caused Host paging. The WebView then exposed all 42 fixture messages (`native095-audit-00` through `native095-audit-41`), with the complete 1945-character tail and `LONG_HISTORY_END_SENTINEL`; the old page action carried revision/cursor fields. Fixture SHA-256: `272cb979f92023335c55bff29bd453ec425bf4ea11832cc827147541842f4ce1`. Calendar DB SHA-256 remained `0c009f99188c8584d805a31f17812c507d5f54412d17d9ec68ef38e9cace765a`; synthetic history hash remained the fixture hash before/after the read-only paging interaction.
- Screenshots and logs are under `.local/android-test/reports/native095-ai-history-paging-20261106/`. The test-created private chat fixture and temporary device captures were removed, the pre-test onboarding preference was restored to `false`, and only the test-created ADB forward `tcp:19225` was removed. No app data, AVD, system calendar, or user database was cleared.

This closes only history availability/content integrity and reader-position behavior. AI bubble/role-label visual matching, original-APK menu/attachment/proposal/error-state parity, and overall AI page sign-off remain open. Product Cutover remains `HOLD`.
