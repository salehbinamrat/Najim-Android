# Phase 01 — local discovery
NetPass reference: gateway discovery via ConnectivityManager. Independent implementation uses Wi-Fi network handles and default IPv4 routes, never a hard-coded IP.
- Prefer active non-VPN Wi-Fi; otherwise use unique Wi-Fi even if cellular is default.
- Multiple candidates or routes are explicitly ambiguous. No credential transmission, INTERNET permission, SSID/location permission, or process binding.
- Monitor only while Activity is started; refresh on changes and manually.
- Gateway is not proof of a captive portal or authenticated session.
- Eight JVM cases exercise selection; Android integration and field tests remain required (API 24+, captive Wi-Fi with cellular enabled, switching APs, disconnection, multiple Wi-Fi links and IPv6-only). Future authentication must revalidate the network and bind requests to the selected Network.
- Phase 00 passed run 45890561052; APK was debug preflight, not signed production.
