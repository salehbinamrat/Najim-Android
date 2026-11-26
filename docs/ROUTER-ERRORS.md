# Phase 03 — explicit RouterOS and RADIUS error classification
Reference: https://help.mikrotik.com/docs/spaces/ROS/pages/87162881/Hotspot%20customisation
RouterOS exposes error and error-orig in login pages; errors.txt may translate the original strings. NetPass is a behavioral reference only; no broad substring matching or copied source.
- Classify only the response to the current POST, bounded to 64 KiB, on unchanged selected gateway. Require a valid same-origin POST login form and explicit visible error marker. Never interpret status-only HTTP or transport failure as expiry.
- Exact normalized allowlist of English and approved Arabic messages; unknown/custom or conflicting explicit errors get neutral rejection, never an invented reason. Hidden/template or incidental text is not evidence. No raw gateway text in UI/logs.
- Independent outcomes for username not found, invalid password, ambiguous credentials, data limit, online time, account expiry, session limit, already logged in, device restriction, RADIUS timeout and missing profile.
- Fixtures are synthetic; customized templates, encodings and dynamic messages not yet recognized safely remain neutral. HTTP evidence is untrusted on insecure networks. Field testing on ROS6/7, UM and FreeRADIUS remains required.
- No new dependencies. Keep existing CI and minSdk24. Tests cover each mapping in English and Arabic, ambiguity, hidden content, origin, limits and flow integration.
