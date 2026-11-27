#!/usr/bin/env python3
"""Offline verification of owner-signed APKs. Never reads keys or passwords."""
import argparse
import hashlib
import json
import os
import re
import subprocess
import tempfile
import zipfile
from pathlib import Path

from audit_apk import main as audit_apk

VARIANTS = tuple((edition, arch, abi) for edition in ("full", "lite")
                 for arch, abi in (("arm32", "armeabi-v7a"), ("arm64", "arm64-v8a")))
HEX = re.compile(r"[0-9a-fA-F]{64}")
SIGNATURE_ENTRY = re.compile(r"META-INF/(?:MANIFEST\.MF|[A-Za-z0-9_-]+\.(?:SF|RSA|DSA|EC))",
                             re.IGNORECASE)


def digest_stream(stream):
    digest = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b""):
        digest.update(block)
    return digest.hexdigest()


def digest_file(path):
    with path.open("rb") as stream:
        return digest_stream(stream)


def certificate_fingerprint(text):
    if not re.search(r"^Verified using v2 scheme \(APK Signature Scheme v2\): true\s*$",
                     text, re.MULTILINE):
        raise ValueError("APK Signature Scheme v2 is required for Android 7+")
    counts = re.findall(r"^Number of signers:\s*(\d+)\s*$", text, re.MULTILINE)
    fingerprints = re.findall(r"^Signer #(\d+) certificate SHA-256 digest:\s*([0-9a-fA-F]{64})\s*$",
                              text, re.MULTILINE)
    if counts != ["1"] or len(fingerprints) != 1 or fingerprints[0][0] != "1":
        raise ValueError("Expected exactly one APK signer with SHA-256 fingerprint")
    if re.search(r"^Signer #1 certificate DN:.*CN\s*=\s*Android Debug(?:,|$)",
                 text, re.MULTILINE | re.IGNORECASE):
        raise ValueError("Android Debug certificate is not a production signing identity")
    return fingerprints[0][1].lower()


def file_names(unsigned):
    suffix = "release-unsigned" if unsigned else "release"
    return {f"Najim-{edition}-{arch}-{suffix}.apk" for edition, arch, _ in VARIANTS}


def require_apks(directory, unsigned):
    expected = file_names(unsigned)
    actual = {path.name for path in directory.iterdir() if path.suffix.lower() == ".apk"}
    if actual != expected:
        raise ValueError(f"Unexpected APK set: missing={sorted(expected-actual)}, extra={sorted(actual-expected)}")
    for name in expected:
        path = directory / name
        if path.is_symlink() or not path.is_file():
            raise ValueError("APK must be a regular file: " + name)
    return expected


def check_unsigned_checksums(directory):
    expected = require_apks(directory, True)
    path = directory / "SHA256SUMS.txt"
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 4096:
        raise ValueError("Invalid unsigned checksum file")
    found = {}
    for line in path.read_text(encoding="ascii").splitlines():
        match = re.fullmatch(r"([0-9a-fA-F]{64}) [ *](\S+)", line)
        if not match or match[2] not in expected or match[2] in found:
            raise ValueError("Invalid or duplicate unsigned checksum entry")
        found[match[2]] = match[1].lower()
    if set(found) != expected:
        raise ValueError("Incomplete unsigned checksums")
    for name, expected_hash in found.items():
        if digest_file(directory / name) != expected_hash:
            raise ValueError("Unsigned candidate checksum mismatch: " + name)
    return found


def payload(apk):
    result = {}
    with zipfile.ZipFile(apk) as archive:
        entries = archive.infolist()
        if len(entries) != len({item.filename for item in entries}):
            raise ValueError("Duplicate APK ZIP entries")
        # Ignore only conventional v1 signing metadata, not arbitrary META-INF files.
        # APK v2/v3 signing blocks are outside the ZIP entry payload.
        for item in entries:
            if item.is_dir() or SIGNATURE_ENTRY.fullmatch(item.filename):
                continue
            with archive.open(item) as stream:
                result[item.filename] = digest_stream(stream)
    if not {"AndroidManifest.xml", "classes.dex"}.issubset(result):
        raise ValueError("APK payload missing manifest or executable")
    return result


def run_tool(tool, *args):
    result = subprocess.run([str(tool), *map(str, args)], check=True, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120)
    return result.stdout


def verify(unsigned_dir, signed_dir, report_dir, sdk, expected_certificate):
    if not HEX.fullmatch(expected_certificate):
        raise ValueError("Provide independently trusted certificate SHA-256 (64 hex digits)")
    expected_certificate = expected_certificate.lower()
    if unsigned_dir.resolve() == signed_dir.resolve():
        raise ValueError("Unsigned and signed directories must be distinct")
    hashes = check_unsigned_checksums(unsigned_dir)
    require_apks(signed_dir, False)
    if report_dir.exists():
        raise ValueError("Choose a new report directory; existing reports are never overwritten")
    tools = sdk / "build-tools" / "35.0.0"
    analyzer = sdk / "cmdline-tools" / "latest" / "bin" / "apkanalyzer"
    if not analyzer.is_file():
        raise ValueError("Android SDK command-line tools (latest) required")
    records = []
    # Publish final reports only after all variants pass.
    report_dir.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=report_dir.parent) as temporary:
        temp = Path(temporary)
        for edition, arch, abi in VARIANTS:
            stem = f"Najim-{edition}-{arch}-release"
            original = unsigned_dir / (stem + "-unsigned.apk")
            signed = signed_dir / (stem + ".apk")
            if digest_file(original) != hashes[original.name]:
                raise ValueError("Unsigned candidate changed during verification")
            signed_hash = digest_file(signed)
            signature = run_tool(tools / "apksigner", "verify", "--verbose", "--print-certs", signed)
            if certificate_fingerprint(signature) != expected_certificate:
                raise ValueError("Unexpected signer: " + signed.name)
            if payload(original) != payload(signed):
                raise ValueError("Signed APK payload differs from verified candidate: " + signed.name)
            variant_report = temp / stem
            variant_report.mkdir()
            manifest = variant_report / "manifest.xml"
            manifest.write_text(run_tool(analyzer, "manifest", "print", signed), encoding="utf-8")
            (variant_report / "signature.txt").write_text(signature, encoding="utf-8")
            alignment = run_tool(tools / "zipalign", "-c", "-P", "16", "-v", "4", signed)
            (variant_report / "zipalign.txt").write_text(alignment, encoding="utf-8")
            if audit_apk(signed, manifest, variant_report / "inventory.json", abi, release=True):
                raise ValueError("Signed APK audit failed: " + signed.name)
            if digest_file(original) != hashes[original.name]:
                raise ValueError("Unsigned candidate changed during verification")
            if digest_file(signed) != signed_hash:
                raise ValueError("Signed APK changed during verification")
            records.append({"apk": signed.name, "sha256": signed_hash,
                            "unsigned_sha256": hashes[original.name],
                            "certificate_sha256": expected_certificate,
                            "abi": abi, "bytes": signed.stat().st_size})
        # Recheck the complete set before publishing evidence, including files
        # processed earlier. No concurrent changes are permitted during verification.
        if check_unsigned_checksums(unsigned_dir) != hashes:
            raise ValueError("Unsigned candidate set changed during verification")
        require_apks(signed_dir, False)
        for item in records:
            if digest_file(signed_dir / item["apk"]) != item["sha256"]:
                raise ValueError("Signed candidate set changed during verification")
        (temp / "SHA256SUMS.txt").write_text(
            "".join(f"{item['sha256']}  {item['apk']}\n" for item in records), encoding="ascii")
        (temp / "verification.json").write_text(json.dumps({
            "status": "signed-artifact-verification-passed",
            "field_acceptance": "not-verified-by-this-tool",
            "artifacts": records}, indent=2) + "\n", encoding="utf-8")
        # Same parent filesystem; no partial successful report on a failed variant.
        temp.rename(report_dir)
    print("Four signed APKs verified; device and field acceptance remain separate.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--unsigned-dir", required=True, type=Path)
    parser.add_argument("--signed-dir", required=True, type=Path)
    parser.add_argument("--report-dir", required=True, type=Path)
    parser.add_argument("--sdk", type=Path, default=os.environ.get("ANDROID_HOME"), required=not bool(os.environ.get("ANDROID_HOME")))
    parser.add_argument("--certificate-sha256", required=True)
    args = parser.parse_args()
    try:
        verify(args.unsigned_dir, args.signed_dir, args.report_dir, Path(args.sdk), args.certificate_sha256)
    except (ValueError, OSError, subprocess.SubprocessError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Release verification failed: {error}\n")
