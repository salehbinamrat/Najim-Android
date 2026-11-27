#!/usr/bin/env bash
set -euo pipefail
mode="${1:-debug}"
case "$mode" in
  debug) output=app/build/verified-apks; suffix=debug; extra=() ;;
  release) output=app/build/unsigned-release-candidates; suffix=release-unsigned; extra=(--release) ;;
  *) echo "Expected debug or release" >&2; exit 2 ;;
esac
report_root=app/build/reports/apk-audit/$mode
mkdir -p "$report_root" "$output"
analyzer="$(find "$ANDROID_HOME/cmdline-tools" -path '*/bin/apkanalyzer' -type f -print -quit)"
test -n "$analyzer"
tools="$ANDROID_HOME/build-tools/35.0.0"
for edition in full lite; do
  for arch in arm32 arm64; do
    case "$arch" in arm32) abi=armeabi-v7a ;; arm64) abi=arm64-v8a ;; esac
    mapfile -t matches < <(find app/build/outputs/apk -type f -name "app-$edition-$arch-$suffix.apk")
    if (( ${#matches[@]} != 1 )); then
      echo "Expected exactly one $edition/$arch APK, got ${#matches[@]}" >&2
      exit 1
    fi
    apk="${matches[0]}"
    report="$report_root/$edition-$arch"
    mkdir -p "$report"
    "$analyzer" manifest print "$apk" > "$report/manifest.xml"
    if [[ "$mode" == debug ]]; then
      "$tools/apksigner" verify --verbose --print-certs "$apk" > "$report/signature.txt"
    else
      # The release candidate has no production key. Signature verification is
      # mandatory AFTER owner-controlled signing, not represented as passed here.
      if "$tools/apksigner" verify "$apk" > "$report/signature.txt" 2>&1; then
        echo "Unexpected signed release candidate" >&2
        exit 1
      fi
      printf '\nUnsigned release candidate; signing and verification pending.\n' >> "$report/signature.txt"
    fi
    "$tools/zipalign" -c -P 16 -v 4 "$apk" > "$report/zipalign.txt"
    python3 .github/scripts/audit_apk.py "$apk" "$report/manifest.xml" "$report/inventory.json" --abi "$abi" "${extra[@]}"
    cp "$apk" "$output/Najim-$edition-$arch-$suffix.apk"
  done
done
(cd "$output"; sha256sum Najim-*-$suffix.apk > SHA256SUMS.txt; sha256sum -c SHA256SUMS.txt)
