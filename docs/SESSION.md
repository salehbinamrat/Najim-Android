# Phase 06A — session status and reported metrics
Reference: https://help.mikrotik.com/docs/spaces/ROS/pages/87162881/Hotspot%2Bcustomisation
- Reuse selected-network transport and portal identity checks. Read-only GET /status; no login/logout, no redirect following. Explicit consent still required for legacy HTTP. Share logged-out redirect policy with login flow.
- Parse stock combined upload/download and connected/time-left rows and explicit known labels. RouterOS bytes-in = upload; bytes-out = download. Bare counters are exact bytes; rounded display values retain reported unit and are never presented as exact. Missing, malformed or contradictory metrics remain null. Do not infer remaining total from directional limits or account balance from session counters. Zero timeout is ambiguous and remains unknown.
- Numeric status-page refresh intervals are accepted as inert HTML; URLs in refresh remain rejected. Ignore clearly hidden rows and account headings. No JavaScript or CSS execution; custom templates still require field validation.
- 23 JVM tests. No UI, polling, logout or real device tests yet. Next 06B: status card, explicit refresh and lifecycle handling; 06C: safe confirmed logout. Account expiry is not inferred from session time.
- No new dependencies or router configuration changes.

## Phase 06B — manual session dashboard
- Arabic RTL card with masked account, reported remaining total/directional bytes, session uptime/time left and upload/download; missing fields explicitly unavailable. Display units as reported, durations in English digits, no quota estimates, countdown, percent or simulated data.
- Manual refresh only, independent per-read HTTP consent; HTTPS default. Legacy snapshots carry warning. No login credentials sent by refresh, no protocol fallback or background polling. Snapshot cleared immediately on network changes, background, mode changes, explicit clear and new login.
- Controller serializes with login worker, rejects duplicate refresh, suppresses late results after cancel or dispose; cancellation is cooperative, not a guarantee of instant socket closure. Login and gallery controls gated during refresh. Session card is foundational; final design is Phase 08.
- 12 JVM controller and formatting tests; no Compose or physical device verification claimed. Logout remains Phase 06C. No new library, permission or router changes.

## Phase 06C — explicit confirmed logout
- Use only fixed local POST /logout with erase-cookie=on (documented RouterOS logout parameter). No arbitrary URLs, credentials, administrator API, or router settings changes. No automatic retry or redirect following.
- Explicit confirmation bound to the last displayed snapshot, separate HTTP consent for logout. Read fresh status on the same selected route, refuse mismatched account or unknown baseline. Submit once and independently read status again even after a lost reply. Only a same-origin logged-out redirect is evidence of session end; HTTP evidence labeled separately. Same account could reconnect between checks, and cookie deletion/RouterOS compatibility require field validation.
- Existing controller and serial worker are reused. Clear displayed metrics immediately on starting logout, block refresh/login while busy, discard late results on lifecycle/network change. UI asks for fresh status before retrying uncertain outcomes. No new dependencies.
- Tests are synthetic. Real logout including cookie behavior, portal variations, and UI lifecycle remain part of field acceptance.
- Reference: https://manual.mikrotik.com/docs/authentication-authorization-accounting/hotspot-captive-portal/hotspot-customisation/


Phase 11A: configured session-timeout is not remaining time. Generic balance and upload/download labels require explicit byte units; bare numbers are allowed only in explicitly byte-labelled fields. Hidden descendants cannot establish metric or heading evidence. External CSS is not evaluated; unknown values are left unknown.
