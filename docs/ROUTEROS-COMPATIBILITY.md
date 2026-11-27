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
