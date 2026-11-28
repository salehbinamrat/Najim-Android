#!/usr/bin/env bash
set -euo pipefail
python3 -m unittest discover -s .github/scripts -p 'test_verify_unit_reports.py'
tasks=()
for variant in FullArm32 FullArm64 LiteArm32 LiteArm64; do
  tasks+=(":app:test${variant}DebugUnitTest" ":app:lint${variant}Debug" ":app:test${variant}ReleaseUnitTest" ":app:lint${variant}Release")
done
bash .github/scripts/gradle-ci.sh "${tasks[@]}"

python3 .github/scripts/verify_unit_reports.py app/build/test-results
