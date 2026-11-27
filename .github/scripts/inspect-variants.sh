#!/usr/bin/env bash
set -euo pipefail
report_root=app/build/reports/apk-audit
output=app/build/verified-apks
mkdir -p "$report_root" "$output"
analyzer="$(find "$ANDROID_HOME/cmdline-tools" -path '*/bin/apkanalyzer' -type f -print -quit)"
test -n "$analyzer"
tools="$ANDROID_HOME/build-tools/35.0.0"
for edition in full lite; do
  for arch in arm32 arm64; do
    case "$arch" in arm32) abi=armeabi-v7a ;; arm64) abi=arm64-v8a ;; esac
    mapfile -t matches < <(find app/build/outputs/apk -type f -name "app-$edition-$arch-debug.apk")
    if (( ${#matches[@]} != 1 )); then
      echo "Expected exactly one $edition/$arch APK, got ${#matches[@]}" >&2
      exit 1
    fi
    apk="${matches[0]}"
    report="$report_root/$edition-$arch"
    mkdir -p "$report"
    "$analyzer" manifest print "$apk" > "$report/manifest.xml"
    "$tools/apksigner" verify --verbose --print-certs "$apk" > "$report/signature.txt"
    "$tools/zipalign" -c -P 16 -v 4 "$apk" > "$report/zipalign.txt"
    python3 .github/scripts/audit_apk.py "$apk" "$report/manifest.xml" "$report/inventory.json" --abi "$abi"
    cp "$apk" "$output/Najim-$edition-$arch-debug.apk"
  done
done
(cd "$output"; sha256sum Najim-*-debug.apk > SHA256SUMS.txt; sha256sum -c SHA256SUMS.txt)
