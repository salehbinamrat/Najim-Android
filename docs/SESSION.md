# Phase 06A — session status and reported metrics
Reference: https://help.mikrotik.com/docs/spaces/ROS/pages/87162881/Hotspot%2Bcustomisation
- Reuse selected-network transport and portal identity checks. Read-only GET /status; no login/logout, no redirect following. Explicit consent still required for legacy HTTP. Share logged-out redirect policy with login flow.
- Parse stock combined upload/download and connected/time-left rows and explicit known labels. RouterOS bytes-in = upload; bytes-out = download. Bare counters are exact bytes; rounded display values retain reported unit and are never presented as exact. Missing, malformed or contradictory metrics remain null. Do not infer remaining total from directional limits or account balance from session counters. Zero timeout is ambiguous and remains unknown.
- Numeric status-page refresh intervals are accepted as inert HTML; URLs in refresh remain rejected. Ignore clearly hidden rows and account headings. No JavaScript or CSS execution; custom templates still require field validation.
- 23 JVM tests. No UI, polling, logout or real device tests yet. Next 06B: status card, explicit refresh and lifecycle handling; 06C: safe confirmed logout. Account expiry is not inferred from session time.
- No new dependencies or router configuration changes.
