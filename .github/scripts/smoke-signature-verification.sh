#!/usr/bin/env bash
# CI test only. No owner keys, no publishing, and no upload of signed test APKs.
set -euo pipefail
umask 077
work="$(mktemp -d)"
trap 'rm -rf -- "$work"' EXIT
unsigned=app/build/unsigned-release-candidates
tools="$ANDROID_HOME/build-tools/35.0.0"
mkdir -p "$work/signed"
# Throwaway certificate for this isolated test, unrelated to production identity.
# The password is disposable, not a repository or owner secret.
export NAJIM_TEST_KEY_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_hex(24))')"
keytool -genkeypair -noprompt -storetype PKCS12 -keystore "$work/test.p12" \
  -alias preflight -keyalg RSA -keysize 2048 -validity 2 \
  -dname 'CN=Najim CI Test Only' -storepass:env NAJIM_TEST_KEY_PASSWORD \
  -keypass:env NAJIM_TEST_KEY_PASSWORD >/dev/null 2>&1
keytool -exportcert -keystore "$work/test.p12" -alias preflight \
  -storepass:env NAJIM_TEST_KEY_PASSWORD -file "$work/test.der" >/dev/null 2>&1
fingerprint="$(sha256sum "$work/test.der" | cut -d ' ' -f 1)"
for edition in full lite; do
  for arch in arm32 arm64; do
    stem="Najim-$edition-$arch-release"
    "$tools/apksigner" sign --ks "$work/test.p12" --ks-key-alias preflight \
      --ks-pass env:NAJIM_TEST_KEY_PASSWORD --key-pass env:NAJIM_TEST_KEY_PASSWORD \
      --v1-signing-enabled false --v2-signing-enabled true \
      --v3-signing-enabled false --v4-signing-enabled false \
      --out "$work/signed/$stem.apk" "$unsigned/$stem-unsigned.apk"
  done
done
unset NAJIM_TEST_KEY_PASSWORD
python3 .github/scripts/verify_signed_release.py \
  --unsigned-dir "$unsigned" --signed-dir "$work/signed" \
  --report-dir "$work/verified" --sdk "$ANDROID_HOME" --certificate-sha256 "$fingerprint"
(cd "$work/signed"; sha256sum -c "$work/verified/SHA256SUMS.txt")
# Negative integration test: a different certificate fingerprint must fail and
# must not publish a report. Use the same real, valid signed APKs.
if python3 .github/scripts/verify_signed_release.py \
  --unsigned-dir "$unsigned" --signed-dir "$work/signed" \
  --report-dir "$work/rejected" --sdk "$ANDROID_HOME" \
  --certificate-sha256 "$(printf '%064d' 0)"; then
  echo "Wrong signing identity unexpectedly accepted" >&2
  exit 1
fi
test ! -e "$work/rejected"
echo "Ephemeral signing smoke test passed; test artifacts are never uploaded."
