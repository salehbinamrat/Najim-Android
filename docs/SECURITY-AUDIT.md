# Phase 12B — Security and supply-chain audit (2026-11-27)

Baseline 61f2cec4c1ddd3359d7f2ed8ac6d6d16a9bf50cc; Phase 12A passed run 45961364825. Scope: CI workflow, direct dependency declarations, manifest, portal CHAP parser and existing storage protections.

## Findings and corrections
- Mutable action tags: pin all five action repositories to the exact SHAs observed in successful run 45961364825, keep existing versions. Disable checkout credential persistence; retain contents:read and pull_request (not pull_request_target). Limit each job to 25 minutes. Reference: https://docs.github.com/en/actions/reference/security/secure-use
- Dependency vulnerability visibility: repository alerts and SBOM endpoints are not available through the current connector. Add a CI-only resolved Maven component inventory (debug and release runtime, debug unit-test runtime) and OSV querybatch gate in Android Core. The report includes resolved transitive versions; any advisory match or incomplete scan fails Core. No ignored findings; triage before merging. Only dependency coordinates/versions are transmitted to OSV from CI, not app data. Existing repositories and runtime dependencies unchanged. Reference: https://google.github.io/osv.dev/post-v1-querybatch/
- Public jsoup review: configured 1.24.1 is outside affected versions for CVE-2026-71497 (<1.23.1) and CVE-2026-75140 (<1.23.2 per reviewed advisory). App uses offline HTML parsing, not Cleaner or XML parser. https://github.com/advisories/GHSA-pmhh-3w7g-xqp8 and https://github.com/advisories/GHSA-65r4-943x-97jj. This is not a complete dependency scan.
- CHAP parser: fixed-length octal groups remove unbounded repetition; reject multiple hash calls and preserve valid stock-style single expression; two regression tests. Existing 64KiB HTML limit, no script execution, no protocol fallback, explicit HTTP consent unchanged.
- Manifest declares network state, internet, optional camera; app-wide cleartext remains necessary for user-approved dynamic local HTTP portals. Existing transport restrictions, FLAG_SECURE and disabled backup retained. Merged manifest (including libraries), actual device network egress and backup/recents behavior still need verification.
- ML Kit bundled versions match official sample (https://github.com/googlesamples/mlkit/blob/master/android/vision-quickstart/app/build.gradle); this does not establish security or compatibility. Native libraries, build plugins and action sub-dependencies are not covered by the Maven runtime/test OSV gate. 16KB native page alignment, final signing, license review and real device/RouterOS validation are release gates.

No claim of a vulnerability-free application. Initial automated OSV scan is pending CI. Review any positive matches or failed API scan before merging; no bypass. No router/subscriber settings touched.

## Phase 12C — packaged artifact audit
Phase 12B passed run 45962335216: OSV checked 131 resolved Maven components; 0 matches. Both CI paths passed; merged ed7cd0f623b7f335f97bc596b015d9fad2aae91a.
- Add post-build APK signature verification, zipalign 16KB check, actual binary manifest XML inventory, sensitive-permission denylist, explicit exported-component gate, native ELF class/machine and 64-bit PT_LOAD alignment check. Require both ARM ABIs in the current universal debug APK (not four final variants). Permit the launcher and the known AndroidX profile installer receiver only when protected by DUMP. Inventory all other permissions and components for review; denylist is not a comprehensive security proof. Six CI-side Python tests; no app code or dependency change. Artifact inspection must succeed before debug APK upload. A diagnostic archive is uploaded even on failure. Actual APK findings pending CI; native code functionality and field acceptance are still pending. References: https://developer.android.com/tools/apkanalyzer and https://developer.android.com/guide/practices/page-sizes.


Phase 12C verified in run 45963075073: both jobs passed, 131 Maven components / zero OSV matches, universal debug APK 4 permissions and 20 native libraries / zero audit-rule findings, signature and alignment checks passed. Merged 34152f54db111cb3b8ec576793d3f78d87aaf17c. Phase 13A extends those gates to four exact-ABI APKs and scans all flavor dependency graphs. New Lite dependency not yet scanned; device validation pending.


Phase 13A verified: run 45963895362, 134 Maven coordinates / 0 OSV matches, all four APKs 4 permissions each / 0 rule findings, Full 5 and Lite 4 native libraries per ABI; checksum verification successful. Phase 13B adds release manifest restrictions and unsigned artifact inspection. Production signature verification is NOT complete; no signing identity supplied.


Phase 13B verified (45964841827): 134 Maven components, zero OSV matches; eight APKs passed applicable rules and SHA-256; ten CI audit tests passed. Release APKs unsigned, not signature-verified. Phase 13C adds an offline owner-signed APK verifier, using independently trusted certificate fingerprint, comparing entry contents to verified unsigned APKs (excluding conventional signing metadata only), rechecking signature and artifact rules. Candidate provenance and field acceptance remain separate; no production key supplied. Six synthetic verifier tests added. No runtime dependency changes.


13C follow-up: reject v1-only signatures, recheck complete input hashes, and test the verifier end-to-end using disposable CI-only signatures on all four release candidates plus a wrong-certificate negative case. No production key involved. Real signed artifact and field acceptance still pending.


13C verified in 45967205345: 134 Maven components / 0 OSV matches, both jobs passed, eight APK audits and hashes and actual four-variant disposable v2 signer smoke test succeeded; mismatched signer rejected. 13D separates artifact uploads without weakening gates and adds a launcher icon presence check to packaged manifest audit. No production signing or field acceptance yet.


Phase 14A: RECORD_AUDIO is intentionally authorized for user-requested voice input, microphone hardware must remain optional; no background recording service or cloud recognizer fallback. Android's on-device service and installed offline TTS are external components; app cannot independently verify their internal behavior. Start only on explicit tap with runtime permission, cancel/destroy on pause/disposal/timeout; no raw audio persistence, passwords or automatic login. Read-aloud only typed session metrics under existing audio/speech opt-in, no identifier. New audit rule checks optional microphone feature, all other denylist protections retained. Actual device testing and OSV verification pending.
