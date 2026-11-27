# RouterOS compatibility acceptance — Phase 11

Baseline: 22fd50ee9e72ced9f93f80aab94e7b3816a994fd.
Synthetic parser and flow tests are not evidence of a real router deployment. Use isolated lab accounts and sanitized portal captures; do not change production router or subscriber configuration.

| Target | Scenarios | Status |
|---|---|---|
| RouterOS 6.49.x + User Manager v6 | PAP/CHAP, username-only and password, expired/time/data/session limits, RADIUS timeout, status and logout | Field pending |
| RouterOS 7.x + User Manager v7 | Same cases; compare actual portal templates and counters, HTTPS only with valid TLS identity | Field pending |
| RouterOS 6/7 + FreeRADIUS | Accept/reject/timeout, exact account evidence, accounting and session limits | Field pending |
| Custom Arabic/English portal | Local actions, hidden fields, ambiguous balance, localized errors, unsupported scripts | Synthetic regressions added; field pending |
| Network transitions | Duplicate IPv4 gateways on distinct Wi-Fi networks, VPN, lost response, no automatic retry | Existing synthetic tests; field pending |

For each case record exact RouterOS version, RADIUS backend/version, Android version and ABI, selected protocol, sanitized HTML/headers and expected vs actual result. Use synthetic vouchers; never store real credentials or full subscriber pages in the repo. Check no false success, no false expiry, no cross-network writes, no automatic resubmission, no hidden evidence, no invented balance and no router changes. For HTTPS do not bypass certificate checks. Unsupported custom forms remain unknown. Actual compatibility must be established in lab before production release.


## Regression correction — CI run 45959484841 (2026-11-27)
Both jobs failed the same test: PortalParserTest.invalidAccountFormattingIsNotEvidence (368/369 passed). Jsoup Element.text() normalizes text before validation, including dropping zero-width characters. Preserve decoded wholeText for account evidence in headings and tables; keep ordinary metric display normalization. Retain the original failing assertion and add three regression cases for invisible characters/entities, exact valid identity and internal whitespace. CI must re-run against the corrected head; no skipped tests or relaxed validation.


## Phase 11B — synthetic whole-flow matrix
- Corrected 11A head passed both CI paths (run 45960013016); merged 77952f6156b9866fd2499fc76b05156a2876a087.
- Ten additional test methods cover all three protocols, 13 explicit localized rejection cases per protocol, all login outcome/save authorization combinations, same-IP different network IDs, concealed identity, malformed errors, transport loss, independent status and exact account mismatch. These are authored synthetic fixtures, not real firmware captures. Reject stale preflight or parsed evidence; preserve decoded error text before normalization. Real RouterOS 6/7 and RADIUS lab runs remain pending.


## Field report: login unavailable and existing card status missing (2026-11-27)
User reports failure on real network; exact response/protocol not captured. Prior CI success does not establish field compatibility. Fix observed client limitations: automatic HTTPS read on initial/changed foreground network, explicit read-only HTTP path, local HTML logged-out evidence, unique local form without name, additional Arabic status labels and headings. Verify on same network after build; use only sanitized rendered login/status HTML (remove account, password, cookie, MAC/IP identifying subscriber and secrets) and exact UI error/protocol if unresolved. Never change production router config. Official RouterOS template behavior: https://help.mikrotik.com/docs/spaces/ROS/pages/87162881/Hotspot%20customisation . Custom JavaScript, hostname alias and nonstandard status templates remain unverified; fail closed rather than guess.


## Najmhotspot actual callback contract
Reference: https://github.com/salehbinamrat/Najmhotspot/tree/044cde9e8b0d3aa04e8da5bc90ecc37c4f1d7a9d. Status action onStatusQuery with logged_in yes/no, URL-escaped username, bytes_in/out, remain_bytes_total, session_time_left and uptime. Login onLoginStart/onLoggedIn/onLoginError, original escaped reason first. Username-only POST omits password, optional separate password sends both. Reference main is username-only (passwordRequired:false); README explicitly says CHAP-only is unsupported without further work. App retains its separate traditional CHAP implementation, no automatic downgrade. Local fixed callback paths, no script execution or arbitrary redirects, exact independent matching account needed before confirmed save. No missing balance interpreted as unlimited. Real network retest still needed.


Callback follow-up: malformed JSON or transport failure no longer triggers traditional parser fallback; valid HTML/redirect/unsupported endpoint may use the same-origin traditional read path before credentials are submitted. A recognized login acknowledgement permits up to five read-only status queries with delays 0/400/950/1500/2000 ms and cooperative 18s budget; no POST retry. Temporary transport read failure may be retried inside that bound, identity mismatch or malformed evidence stops. Exact byte counters displayed in IEC binary units. No evidence of field resolution until user retests.


### Actual supplied archive comparison (2026-11-27)
All three uploaded variants contain matching status/fstatus/alogin callback templates. Password edition adds password only if nonempty, normal edition username only, speeds edition sends optional domain and port signals which are deliberately not adopted. None of these three login templates expose a dedicated CHAP challenge callback. Browser boot fetches same-origin status automatically. Android now uses read-only HTTP status at startup (no credential POST) then HTTPS for unknown/unavailable; keeps explicit HTTP credential consent and legacy warning. Actual Wi-Fi gateway, router logs, installed speech engine remain unobserved. Do not change router settings. Empty or missing quota remains unknown rather than claiming unlimited.
