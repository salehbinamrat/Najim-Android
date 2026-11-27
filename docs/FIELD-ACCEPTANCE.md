# Field acceptance — mandatory before distribution

Status: NOT EXECUTED. Use dedicated test devices and test vouchers, isolated lab or authorized test networks. Never store credentials, customer data, keys or unredacted screenshots. Record date, exact Git commit, artifact SHA-256, Android/ABI/RouterOS/backend versions, expected and observed result, and pass/fail evidence. Use the same signed candidate bytes intended for distribution; any code change requires regression review and new artifacts.

| Gate | Required evidence | Status |
|---|---|---|
| ARM32 Android 7+ and ARM64 modern Android | Install, launch, navigation, no crashes, exact architecture | Pending |
| ARM64 16KB runtime | Actual device/emulator page size 16384; startup, OCR, QR, session and vault | Pending |
| Full offline OCR/QR | Airplane mode with lab Wi-Fi, first use, manual confirmation, camera/gallery | Pending |
| Lite first-use OCR | Model unavailable, no Play services, download on supported device, offline reuse, QR/manual fallback | Pending |
| RouterOS 6.49.x + UM v6 | Login, logout, status, quota/time/session rejection, timeout distinction | Pending |
| RouterOS 7.x + UM v7 | Same scenarios with supported PAP/CHAP/HTTPS and valid TLS | Pending |
| RouterOS 6/7 + FreeRADIUS | Accept/reject/timeout/accounting, no inferred success | Pending |
| Security/network switching | Same gateway across separate Wi-Fi networks, VPN, redirects, invalid TLS, no credential replay, no unexpected network requests | Pending |
| Encrypted vault | Five per profile, manual/auto, restart, interrupted writes, denied/failed auth, no loss of other profiles | Pending |
| UI/accessibility/performance | 320–600dp, font scale, TalkBack, dark/light, camera cycles, startup/PSS/battery/thermal; record real measurements | Pending |
| Signed artifact verification | Four matching payloads, expected owner certificate, alignment, SHA-256 | Pending |
| Distribution authorization | Owner-selected license and signing identity, field acceptance review | Pending |

Do not alter production router or customer configurations. No automatic release publication. A failed or missing required gate blocks production distribution. See ROUTEROS-COMPATIBILITY.md, PERFORMANCE.md, SECURITY-AUDIT.md, RELEASE.md.

## Voice feature acceptance (Phase 14A)
All pending: Android 12+ device with installed Arabic local recognition (digits with leading zeros, ambiguity, timeout, denied/revoked permission, no model, airplane mode with lab Wi-Fi), microphone indicator stops on timeout/cancel/navigation/background, no candidate auto-submits, selected login mode preserved when using voice/OCR/QR, Android 7–11 fallback, local Arabic TTS summary only on user request with existing opt-in, stop spoken summary on network/session change, no account number or password read aloud. Inspect actual egress and memory/CPU on each edition/ABI; third-party recognition/TTS engine behavior not independently verified.

Field follow-up: test startup already-authenticated on each actual supplied archive over HTTP, no credential POST on startup, one HTTPS fallback for unavailable/unknown, Wi-Fi switch and background clearing. Test Arabic/English local voice, older device system provider consent and refusal, permission/language/timeout diagnostics, multiple candidates and manual confirmation. No guarantee that system provider honors offline preference; explicit warning and no automatic fallback. No actual field result yet.

Field diagnostic: on failed automatic status read, copy the displayed protocol, HTTP code and category (no card credentials). Test HTTP JSON, HTTPS fallback, VPN block, network switch, cancellation, unsupported endpoint, malformed JSON and absence of router reply. Check total usage matches exact received counters, missing/rounded/overflow remains unavailable. Actual field state remains pending.
