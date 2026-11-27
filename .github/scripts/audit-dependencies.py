#!/usr/bin/env python3
"""CI only: query public Maven coordinates; never send source, secrets or user data."""
import json
import pathlib
import sys
import time
import urllib.error
import urllib.request

URL = "https://api.osv.dev/v1/querybatch"


def query(queries):
    request = urllib.request.Request(
        URL, data=json.dumps({"queries": queries}).encode("utf-8"),
        headers={"Content-Type": "application/json"}, method="POST")
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                data = response.read(4 * 1024 * 1024 + 1)
                if len(data) > 4 * 1024 * 1024:
                    raise ValueError("OSV response too large")
                results = json.loads(data)["results"]
                if not isinstance(results, list) or len(results) != len(queries):
                    raise ValueError("OSV returned an incomplete batch")
                return results
        except urllib.error.HTTPError as error:
            if error.code != 429 and not 500 <= error.code <= 599:
                raise
        except (urllib.error.URLError, TimeoutError):
            pass
        if attempt < 2:
            time.sleep(5 * (attempt + 1))
    raise RuntimeError("OSV unavailable after bounded retries")


def audit(path):
    components = sorted(set(tuple(line.split("\t")) for line in
                            path.read_text(encoding="utf-8").splitlines() if line))
    if not components or any(len(row) != 2 or ":" not in row[0] or
                             not all(row) for row in components):
        raise ValueError("Invalid dependency inventory")
    findings = set()
    for offset in range(0, len(components), 50):
        pending = [{"package": {"ecosystem": "Maven", "name": name}, "version": version}
                   for name, version in components[offset:offset + 50]]
        for _ in range(10):
            if not pending:
                break
            next_page = []
            for item, result in zip(pending, query(pending)):
                if not isinstance(result, dict):
                    raise ValueError("Invalid OSV result")
                for advisory in result.get("vulns", []):
                    findings.add((item["package"]["name"], item["version"], advisory["id"]))
                if result.get("next_page_token"):
                    next_page.append(dict(item, page_token=result["next_page_token"]))
            pending = next_page
        if pending:
            raise RuntimeError("OSV pagination limit exceeded")
    report = {"status": "findings" if findings else "no-known-findings",
              "components_checked": len(components),
              "findings": [{"package": p, "version": v, "id": i}
                           for p, v, i in sorted(findings)]}
    (path.parent / "osv-results.json").write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"OSV checked {len(components)} resolved Maven components; {len(findings)} matches")
    for package, version, advisory in sorted(findings):
        print(f"{package}:{version} — {advisory}")
    return 1 if findings else 0


if __name__ == "__main__":
    try:
        sys.exit(audit(pathlib.Path(sys.argv[1])))
    except (OSError, ValueError, KeyError, TypeError, RuntimeError) as error:
        print(f"Dependency audit incomplete: {error}", file=sys.stderr)
        sys.exit(2)
