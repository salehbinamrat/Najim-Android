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
