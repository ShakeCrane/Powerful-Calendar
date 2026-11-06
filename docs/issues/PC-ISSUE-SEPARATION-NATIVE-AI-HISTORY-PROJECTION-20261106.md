# PC Issue: Native 0.9.5 AI history projection mismatch

Date: 2026-11-06

Status: CONFIRMED — reproducible on frozen Native APK and current Web v3 APK; fix pending

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

Source-only adjacent risk: Web `renderChat()` currently resets `scrollTop` to the bottom on every render; no separate Android trigger/update test was run for that behavior in this reproduction.

## Data restoration

After capture, both apps were force-stopped. The Raw AVD's pre-test two-byte empty history file was restored from an app-private backup. The Web AVD's original chat-history file was absent, so the synthetic file was removed. Its onboarding prompt preference was restored to `false`, the value that had caused the displayed “暂不导入” choice. No calendar import was initiated, and no AVD was wiped or app data cleared.
