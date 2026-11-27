#!/usr/bin/env bash
set -euo pipefail
tasks=()
for variant in FullArm32 FullArm64 LiteArm32 LiteArm64; do
  tasks+=(":app:test${variant}DebugUnitTest" ":app:lint${variant}Debug" ":app:test${variant}ReleaseUnitTest" ":app:lint${variant}Release")
done
bash .github/scripts/gradle-ci.sh "${tasks[@]}"
