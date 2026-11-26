#!/usr/bin/env bash
# Retry only observed Maven Central HTTP 429 dependency download failures.
set -euo pipefail

if (( $# == 0 )); then
  echo "Gradle tasks required" >&2
  exit 2
fi

log="$(mktemp)"
trap 'rm -f "$log"' EXIT

for attempt in 1 2 3; do
  echo "Gradle attempt $attempt/3"
  set +e
  gradle --no-daemon --max-workers=2 --stacktrace "$@" 2>&1 | tee "$log"
  statuses=("${PIPESTATUS[@]}")
  set -e
  rc="${statuses[0]}"
  if (( statuses[1] != 0 )); then
    echo "Unable to capture Gradle output" >&2
    exit "${statuses[1]}"
  fi
  if (( rc == 0 )); then
    exit 0
  fi
  if (( attempt == 3 )) || ! grep -Eiq 'repo\.maven\.apache\.org/.*(429|Too Many Requests)' "$log"; then
    exit "$rc"
  fi
  # Leave the standard repositories and dependency versions unchanged.
  # Gradle reuses successfully downloaded artifacts on subsequent attempts.
  delay=$((30 * attempt))
  echo "::warning::Maven Central rate limit; retrying after ${delay}s"
  sleep "$delay"
done
