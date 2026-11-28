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

## Phase 16A — consistent discovery and VPN blocking
- Phase 15A verified run 46158250188, both jobs passed; merged abc70da645a99842988e8b45dd1aa449cc770a13.
- Discovery previously published Ready with an active default VPN while transport refused it. Publish explicit VpnBlocked, no selected Network; UI explains the block. Existing controllers cancel stale work when discovery stops being Ready. No process-wide binding, permission, DNS, proxy or protocol changes.
- Recheck default network and Wi-Fi capability after enumerating candidates; unknown active capabilities or inconsistent snapshot fails closed. Duplicate handles fail closed. Same gateway with distinct handles remains distinct. Five JVM policy tests. Platform capture is not an atomic OS transaction; existing per-request network resolution still required. No claim that every VPN mode is detectable; only VPN reported on active default network. Real device tests pending, including cellular default, always-on VPN and network switching. Persistent profile identity is a separate concern: gateway IP alone is not a stable cross-network identifier; do not add automatic credential selection.

## Phase 16B — foreground monitoring lifetimes
- PR47 verified run 46162884954, both jobs passed; merged 11eea135a462b84d6674edcb5ab717940286dd39.
- Fresh listener instances per start; thread-safe lifetime gate admits at most one pending refresh for the current lifetime. Stop invalidates before unregister; late old callbacks and queued tasks cannot consume or schedule work for a restarted monitor. A callback during capture can schedule one follow-up. Registration attempts and cleanup are independent for Wi-Fi and default monitoring. Publish the new selection before notifying controllers.
- Six JVM gate tests including concurrent admission and stale lifetime rejection. No new permissions, dependencies, protocol or router changes. Manual refresh retained. Device callback delivery, registration failure behavior, rapid disconnect/reconnect and Android lifecycle remain field tests; OS snapshots are not atomic and this does not replace per-request validation. No claim of physical cancellation of an already-sent POST. Profile identity remains Phase 16C.

## Phase 16C — ephemeral network action isolation
- PR48 passed both jobs in 46167161055 and merged a3b664fb5154bdc98caa30861a0023e44ea06fbe.
- Persistent profiles already have separate UUIDs and encrypted records; gateway IP is only a compatibility filter, not proof of identity. Keep manual selection and all existing vault data and five-card retention unchanged. No SSID, BSSID, location permission, automatic profile matching or persistent network handle.
- Capture the rendered discovery state and input generation for create/load/delete/retention and login actions. Reject stale action scopes, including different handles at the same IP, stopped owner and A -> B -> A after observed transitions. Guard loaded-card delivery again. Increment the existing input generation on observed foreground network changes to recreate the form, discard prior profile/credentials/consent and return to home (existing gallery behavior retained). Explicit re-selection required. Five JVM scope tests. Existing controllers still invalidate in-flight work and transport revalidates actual route. No claim of cryptographic identity, unobserved transition detection or rollback of a write already begun. Device/Compose lifecycle tests pending.
