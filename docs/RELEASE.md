# Phase 13A — four debug preflight APKs

One source tree, two edition flavors (full, lite), two architecture flavors (arm32, arm64). Full retains bundled Latin OCR 16.0.1; Lite uses Google Play services text recognition 19.0.2, with on-device inference after model availability. QR remains bundled in both. No automatic install-time model-download metadata is added by us; first OCR use may request a download through Google Play services. Offline first-use OCR is not guaranteed in Lite; manual input and QR remain independent of text model readiness. Shared application ID intentionally allows upgrading/switching editions with a compatible signing key; not side-by-side installation. No release signing keys supplied or generated.

Both parallel CI jobs execute unit tests and lint for all four debug variants; Core scans resolved runtime/test dependency graphs for all variants, including release graphs. Release Preflight assembles only after verification, inspects signature, merged manifest, ZIP alignment and exact intended ARM ABI and 64-bit ELF segment alignment for each APK, then creates and checks SHA-256 for all four and uploads them. Debug APKs are NOT final release builds. No publishing or production signing. Confirm actual output names, APK sizes, new permissions and OSV counts in CI.

Remaining: license decision, signing through owner-controlled key/secret process, release build and audit, device installation and 16KB runtime validation, full/lite model readiness and size comparison, RouterOS 6/7 and RADIUS field tests, performance and TalkBack. Do not claim production readiness without these.
References: https://developers.google.com/ml-kit/vision/text-recognition/v2/android and https://developer.android.com/build/build-variants.


## Phase 13A verified (2026-11-27)
Run 45963895362 passed both jobs, OSV 134 components / 0 matches, 8 audit tests, Full 5 native libraries per ABI and Lite 4, 4 permissions and 0 audit findings per APK, all four checksums verified. Merged d7e7e3e9530dc7798079f14c9e94fbbeeca36a8e. Debug artifact: https://github.com/salehbinamrat/Najim-Android/actions/runs/45963895362/artifacts/15181273821.

## Phase 13B — unsigned release candidates
Both parallel jobs now verify debug AND release unit tests and lint for all four variants. Dependency inventory includes release test classpaths. After tests, Release Preflight builds the four debug APKs and four unsigned release APKs, checks exact ABI/manifest/16KB alignment and checksums separately. Release audit rejects debuggable, testOnly and SDK drift. Unsigned APKs are not installable and have not passed signature verification. Explicitly no debug signing of production candidates, no automatic publishing, no R8 policy change. R8 optimization remains disabled until runtime/field validation. 

### Owner-controlled signing (not executed)
Keep a backed-up release keystore offline; never commit passwords or keys, never send them in chat. Owner must select the signing identity and verify expected certificate SHA-256 via an independent trusted record. Sign each previously verified unsigned candidate with Android SDK apksigner (prompt for passwords; do not pass literal passwords on command line). Verify signature, certificate fingerprint, alignment and packaged manifest again after signing; generate fresh SHA-256 for the signed files. Test upgrades using the same certificate and application ID. Do not distribute signed APKs until physical device, RouterOS and RADIUS acceptance and distribution license are confirmed. Debug CI certificates may differ across runs; debug installations may require uninstall (which clears local data) before installing production. Back up any needed test vouchers manually before uninstalling. No real key or password generated, requested or uploaded.


## Phase 13B verified
Run 45964841827 passed both jobs: 134 resolved Maven components / zero OSV matches; 10 audit tests; 8 APKs passed applicable manifest/ABI/alignment gates and checksums. Four Debug APKs verified signed; four Release candidates remain unsigned. Merged af113744075b43dd68f0fc2b3ab43fb603908ca9.

## Phase 13C — owner-signed artifact verification
No production key is generated or stored. Download the four unsigned candidates and SHA256SUMS.txt from the specific approved CI run through authenticated GitHub access; verify their provenance independently (an untrusted archive containing its own checksum is not proof of origin). Retain the original bytes. Sign each locally with SDK 35.0.0 apksigner, using the owner's keystore and interactive password prompts. Name the four signed files Najim-full-arm32-release.apk, Najim-full-arm64-release.apk, Najim-lite-arm32-release.apk, Najim-lite-arm64-release.apk in a separate directory. Do not modify the original candidates.

Run locally with Python 3 and Android SDK tools installed (Linux/macOS shell; no passwords passed):
```sh
python3 .github/scripts/verify_signed_release.py \
  --unsigned-dir /path/to/approved-unsigned \
  --signed-dir /path/to/signed \
  --report-dir /path/to/new-verification-report \
  --sdk /path/to/android-sdk \
  --certificate-sha256 YOUR_INDEPENDENTLY_VERIFIED_64_HEX_CERTIFICATE_FINGERPRINT
```
The tool checks the exact four filenames, candidate checksum file, valid signature with exactly one matching certificate (rejects conventional Android Debug DN), unchanged non-signature ZIP payload, manifest and native ABI/alignment. It writes signed checksums and reports only if all four pass. This is not an attestation of source provenance or runtime behavior. Keep files unchanged during verification and distribution; confirm output SHA-256 again at distribution. Six synthetic verifier tests are added to CI; no production signing or actual signed-file verification has occurred. Field acceptance matrix: docs/FIELD-ACCEPTANCE.md. License and field results still require owner authorization/evidence.

### Phase 13C verifier hardening
Require a verified APK Signature Scheme v2 signature, not v1 alone (minimum API 24). Owners may enable v2 and optionally v3; do not disable v2. Recheck all unsigned and signed hashes before producing the final report. The Release Preflight additionally exercises the complete verifier on all four freshly built unsigned candidates using a short-lived CI-only test certificate; checks signed checksums and rejection of a mismatched certificate. The private key and test-signed copies stay in a temporary directory and are removed, never uploaded or used as production release artifacts. Real owner signing, field acceptance and distribution authorization remain pending. This test does not establish production signing identity.
