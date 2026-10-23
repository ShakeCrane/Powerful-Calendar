# PowerCal 0.9.5-rc.1 — CommandCode AI connectivity QA

Date: 2026-10-23
Status: **PASS — minimal real-model chat request**

## Scope and environment

- Device: repository-local Android 15 emulator `PowerCal_API35` / `emulator-5554`.
- App: PowerCal 0.9.5-rc.1, package `dev.powerfulcalendar`.
- Base URL: `https://api.commandcode.ai/provider/v1` (app normalizes to `/chat/completions`).
- Model ID: `stealth/glyph-cluster:free` (listed in provider's publicly accessible model catalog).
- User entered a new API key locally via `tools/android-emulator/Configure-PowerCalAiKey.ps1`.
- Verification through `-Status`: `key_present=True`, `enabled=True`, `share_context=False`.
- No credentials or authorization headers are included in this report or evidence.

## Actual emulator action and result

Using ADB UI input on PowerCal's own AI chat screen (not a host-side surrogate request):

- User message: `Reply exactly PC_CONNECT_OK`
- Actual visible model response: `PC_CONNECT_OK`
- Result: **PASS** for configured credentials, endpoint routing, real provider request and response, and chat reply presentation in the app.
- Screenshot: `.local/android-test/reports/regression-20261023/ai-connect-success.png` (local ignored QA evidence).

The chat history also contains **older** no-key/localhost failure messages, including HTTP 401. Those predate this configured-key test; do not count them as a new failure.

## Limits / nonclaims

- This confirms **one** minimal free-model connectivity request; it is not a pricing audit or a full provider reliability evaluation.
- It does not test AI structured event proposal generation, draft edits, consent/authorization, OCR, image input, chat recovery across restarts, or calendar writes.
- No formal events or calendar data were changed by this test; the short test conversation appears in the user's persisted local chat history.
- Keep the 209-case release-freeze verdict **HOLD** until its independent acceptance gate is satisfied.
