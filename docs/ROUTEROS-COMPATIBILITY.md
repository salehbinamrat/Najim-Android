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
