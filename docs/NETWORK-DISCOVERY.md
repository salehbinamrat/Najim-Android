# Phase 01 — local discovery
NetPass reference: gateway discovery via ConnectivityManager. Independent implementation uses Wi-Fi network handles and default IPv4 routes, never a hard-coded IP.
- Prefer active non-VPN Wi-Fi; otherwise use unique Wi-Fi even if cellular is default.
- Multiple candidates or routes are explicitly ambiguous. No credential transmission, INTERNET permission, SSID/location permission, or process binding.
- Monitor only while Activity is started; refresh on changes and manually.
- Gateway is not proof of a captive portal or authenticated session.
- Sixteen JVM cases exercise selection and route validation; Android integration and field tests remain required (API 24+, captive Wi-Fi with cellular enabled, switching APs, disconnection, multiple Wi-Fi links and IPv6-only). Future authentication must revalidate the network and bind requests to the selected Network.
- Phase 00 passed run 45890561052; APK was debug preflight, not signed production.

## Regression prevention
- Run 45891453836 failed Android Lint NewApi: clearCapabilities requires API 30. Review also found hasGateway requires API 29. Replace both with API 21-compatible calls; retain minSdk 24 and mandatory Lint.
- Before each change, verify API introduction levels against Android SDK documentation; guard newer calls with SDK_INT or use supported alternatives. No NewApi suppression or baseline hiding errors.
- Both CI paths publish lint and test reports even when verification fails. Do not merge until both checks succeed on the exact PR head. SDK provisioning and bounded Maven 429 retries remain in place.
- Physical API 24+ Wi-Fi lifecycle tests are still pending.
