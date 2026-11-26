# Phase 02A — authentication request and outcome policy

Independent Kotlin implementation. NetPass is a behavioral reference, not copied source:
https://github.com/AbdulAziz-Hatem/netpass/tree/d401d50121afe4f09992d4f838c2c48689615d09

RouterOS reference: https://help.mikrotik.com/docs/spaces/ROS/pages/87162881/Hotspot%20customisation

- Username-only mode uses an empty password, not an automatic second credential attempt.
- HTTPS PAP uses a fixed local gateway /login endpoint; HTTP PAP and HTTP CHAP require explicit per-attempt approval. HTTP/CHAP is not equivalent to TLS; it cannot authenticate the server and has offline guessing risks.
- Legacy standard CHAP uses MD5 of identifier byte + password bytes + challenge. Only 16-byte challenge and ASCII password are supported initially; unsupported encoding fails closed.
- No host override, redirect following, credential logging, or success inferred from status code. This change introduces no INTERNET permission and does not send credentials.
- Sixteen JVM cases verify the protocol policy and independent digest vector.
- Phase 02B remains required: parse trusted gateway login forms and fresh challenge, revalidate exact Wi-Fi network before I/O, use Network-bound transport (API 23+), TLS verification without trust bypass, explicit insecure transport consent UI, bounded response, no automatic retry, and verify the requested session independently. Domain-based and customized portals require a separate safe validation design.
- A network change cancels the attempt; session verification and real RouterOS 6/7 tests are not claimed here.
- Keep minSdk 24, no new dependencies, CI SDK prerequisites, rate-limit retry, unit tests, lint and diagnostic artifacts. Do not merge without both exact-head CI jobs succeeding.

## Phase 02B1 — network-bound HTTPS transport
- Shared Android route resolver used by discovery and transport. VPN active, missing or changed Wi-Fi/gateway, or ambiguous selection stops the attempt.
- Android Network.openConnection(URL, Proxy.NO_PROXY) is available from API 23. No process-wide binding, external connectivity probe, system DNS fallback, trust bypass or automatic application-level retry.
- HTTPS only, fixed /login at selected gateway IP, redirects disabled, 5-second connect/read timeouts, 64 KiB response limit. HTTP remains disabled in manifest; no UI invokes the transport yet. TLS certificate validation can reject IP-based portals without a matching certificate; no silent fallback.
- INTERNET permission enables the future transport, not automatic communication. Unit tests use fake connections only.
- 13 regression tests cover routing checks, redirection, size bounds, credential encoding flow, failures and cleanup.
- Pending 02B2: validated portal form and fresh CHAP parsing, user consent for legacy HTTP, session verification against the intended account, actual login UI with cancellation and field validation. A changed network after transmission means the remote outcome is unknown, not proof that login was cancelled.
- Previous API errors are avoided through API 23+ network APIs, keeping minSdk 24. No added library or disabled checks.

## Phase 02B2a — offline portal and session evidence policy
- Use Jsoup 1.24.1 (pinned, no runtime dependencies) for real HTML parsing, not script execution or resource requests. Enable NIO core library desugaring 2.1.5 per jsoup Android requirements; minSdk remains 24. References: https://jsoup.org/download and https://developer.android.com/studio/write/java11-nio-support-table.
- Parse bounded UTF-8 HTTP 200 documents only, from the selected origin and expected path. Validate local POST action. Reject base overrides, refresh, external actions, ambiguous forms and unsupported login scripts. Extract the stock octal CHAP expression without executing it; malformed or unfamiliar scripts never imply PAP fallback.
- Status evidence requires same-origin logout and explicit exact account identity; conflicting evidence is unknown. Existing or unknown baseline cannot establish a new session. Matching status is evidence only, not independent proof of server identity or a causal login.
- 19 parser and 6 session policy tests; no actual network I/O in these tests. Current restrictions intentionally reject many customized forms and non-UTF-8 portals. Do not claim universal portal compatibility.
- Next: integrate fresh bounded GET /login and /status, explicit HTTPS/legacy HTTP consent, verified route/origin, no credential retry, and UI lifecycle. HTTP remains disabled and no login UI is active. Field verification required.

## Phase 02B2b — HTTPS orchestration
- Shared bounded GET and POST transport. GET allowed only for fixed local HTTPS /login and /status; no default route, arbitrary URL, cookies managed by application, external probe, redirect following or trust override.
- Explicit logged-out baseline: same-origin /status redirect to /login, never followed. Existing account or ambiguous status stops before transmission. Fresh supported login form required; HTTPS PAP only. Unknown/custom or CHAP forms stop without downgrade.
- Submit once, then independently fetch /status and compare exact normalized requested account on the selected Wi-Fi. Missing/changed network or transport failure after POST gives unknown session, never retries automatically. Matching status is evidence only, not proof of internet access.
- 15 new sequence tests using fake transport; GET transport tests cover no body and fixed endpoints. No UI invokes this sequence yet. Legacy HTTP/CHAP consent and transport, cancellation UI and field tests remain pending. Manifest cleartext remains disabled; IP-based HTTPS requires valid matching certificate. No new dependencies.

## Phase 02B2c — explicit legacy HTTP modes
- One selected protocol per attempt: HTTPS PAP (default), HTTP PAP, or HTTP CHAP. Both HTTP modes require separate explicit approval for that invocation; no implicit consent, no automatic fallback between protocols, no retry with a second credential.
- The shared transport validates scheme, exact selected gateway IP, network handle, fixed path, port, query/fragment and request protocol before I/O. VPN and network changes continue to fail closed. HTTP GET and POST are denied without consent. The fresh parsed form must match the selected mode; CHAP uses its freshly parsed challenge, never PAP fallback. Standard TLS verification remains unchanged.
- Android requires cleartext opt-in for dynamically detected IP gateways with Network.openConnection. Manifest cleartext is now enabled application-wide, so future network clients must also enforce explicit scope; do not add WebView or arbitrary cleartext endpoints. This is a platform permission, not user consent. No login UI invokes the transport yet.
- HTTP PAP exposes credentials on the local network. HTTP CHAP does not authenticate the server and permits offline guessing; HTTP status can be forged. Return LEGACY_MATCHING_SESSION separately from TLS evidence, never describe either as proof of internet access. Do not store credentials or consent based on this result.
- Tests cover refusal without consent, protocol mismatch, fresh CHAP proof, no downgrade/upgrade or fallback, existing sessions, changed routes, and separate legacy evidence. Custom portal support and real RouterOS 6/7 field validation remain pending.
- Next: lifecycle-aware login UI with per-attempt HTTP risk confirmation, explicit modes, cancellation and accurate Arabic outcomes; then error parsing. No new libraries.
