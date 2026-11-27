#!/usr/bin/env python3
"""Inspect the built APK, not just the source manifest. Standard library only."""
import json
import pathlib
import struct
import sys
import xml.etree.ElementTree as ET
import zipfile

A = "{http://schemas.android.com/apk/res/android}"
ABI = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183),
       "x86": (1, 3), "x86_64": (2, 62)}
REQUIRED = {"armeabi-v7a", "arm64-v8a"}
REQUIRED_PERMISSIONS = {"android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
                        "android.permission.CAMERA"}
DENIED_PERMISSIONS = {"android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS",
    "android.permission.READ_SMS", "android.permission.SEND_SMS", "android.permission.RECEIVE_SMS",
    "android.permission.RECORD_AUDIO", "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.READ_PHONE_STATE",
    "android.permission.READ_CALL_LOG", "android.permission.WRITE_CALL_LOG",
    "android.permission.QUERY_ALL_PACKAGES"}
COMPONENTS = {"activity", "activity-alias", "service", "receiver", "provider"}


def inspect_manifest(root):
    errors = []
    permissions = sorted({node.get(A + "name", "") for node in root
                          if node.tag.startswith("uses-permission")})
    if not REQUIRED_PERMISSIONS.issubset(permissions):
        errors.append("Required app permissions missing")
    for permission in sorted(DENIED_PERMISSIONS.intersection(permissions)):
        errors.append("Unexpected sensitive permission: " + permission)
    if root.get("package") != "com.najim.android":
        errors.append("Unexpected application ID")
    app = root.find("application")
    if app is None:
        raise ValueError("Missing application")
    if app.get(A + "allowBackup") != "false":
        errors.append("Backup must be disabled")
    components = []
    for item in app:
        if item.tag not in COMPONENTS:
            continue
        name = item.get(A + "name", "")
        exported = item.get(A + "exported")
        permission = item.get(A + "permission", app.get(A + "permission"))
        # Default exported behavior varies by API and component type; require explicit
        # false for providers and for components with intent filters except allowed entries.
        exposed = exported == "true" or (exported is None and
                   (item.tag == "provider" or item.find("intent-filter") is not None))
        components.append({"type": item.tag, "name": name, "exported": exported,
                           "permission": permission, "potentially_exposed": exposed})
        if not exposed:
            continue
        if item.tag == "activity" and name in (".MainActivity", "com.najim.android.MainActivity"):
            continue
        if (item.tag == "receiver" and
                name == "androidx.profileinstaller.ProfileInstallReceiver" and
                permission == "android.permission.DUMP"):
            continue
        errors.append("Unreviewed exported component: " + name)
    return {"permissions": permissions, "components": components,
            "allowBackup": app.get(A + "allowBackup"),
            "usesCleartextTraffic": app.get(A + "usesCleartextTraffic")}, errors


def elf_info(data, abi):
    if len(data) < 52 or data[:4] != b"\x7fELF" or data[5] != 1:
        raise ValueError("Invalid little-endian ELF")
    elf_class = data[4]
    machine = struct.unpack_from("<H", data, 18)[0]
    if (elf_class, machine) != ABI[abi]:
        raise ValueError("ELF class/machine mismatch")
    if elf_class == 2:
        if len(data) < 64:
            raise ValueError("Truncated ELF64 header")
        phoff = struct.unpack_from("<Q", data, 32)[0]
        entsize, count = struct.unpack_from("<HH", data, 54)
        fmt = "<IIQQQQQQ"
    else:
        phoff = struct.unpack_from("<I", data, 28)[0]
        entsize, count = struct.unpack_from("<HH", data, 42)
        fmt = "<IIIIIIII"
    if not count or entsize < struct.calcsize(fmt) or phoff + entsize * count > len(data):
        raise ValueError("Invalid program header table")
    alignments = []
    for index in range(count):
        fields = struct.unpack_from(fmt, data, phoff + index * entsize)
        if fields[0] != 1:  # PT_LOAD
            continue
        alignment = fields[-1]
        alignments.append(alignment)
    if not alignments:
        raise ValueError("ELF has no load segments")
    return {"class": 32 if elf_class == 1 else 64, "machine": machine,
            "load_alignments": alignments}


def inspect_native(apk):
    inventory, errors = [], []
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            errors.append("Duplicate ZIP entries")
        for item in archive.infolist():
            if not item.filename.startswith("lib/") or not item.filename.endswith(".so"):
                continue
            parts = item.filename.split("/")
            if len(parts) != 3 or parts[1] not in ABI:
                errors.append("Unsupported native library path: " + item.filename)
                continue
            abi = parts[1]
            try:
                if item.file_size > 128 * 1024 * 1024:
                    raise ValueError("Native library exceeds audit size bound")
                info = elf_info(archive.read(item), abi)
                inventory.append({"path": item.filename, "abi": abi, "bytes": item.file_size, **info})
                if abi in ("arm64-v8a", "x86_64") and any(
                        alignment < 16384 or alignment & (alignment - 1)
                        for alignment in info["load_alignments"]):
                    errors.append("64-bit ELF not 16KB aligned: " + item.filename)
            except (ValueError, struct.error) as error:
                errors.append(item.filename + ": " + str(error))
        present = {entry["abi"] for entry in inventory}
        if not REQUIRED.issubset(present):
            errors.append("Required ARM ABI missing: " + ", ".join(sorted(REQUIRED - present)))
    return inventory, errors


def main(apk, manifest, report):
    manifest_info, errors = inspect_manifest(ET.parse(manifest).getroot())
    libraries, native_errors = inspect_native(apk)
    errors.extend(native_errors)
    result = {"manifest": manifest_info, "native_libraries": libraries,
              "errors": errors, "status": "failed" if errors else "passed"}
    report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"APK audit: {len(manifest_info['permissions'])} permissions, "
          f"{len(libraries)} native libraries; {len(errors)} findings")
    for error in errors:
        print(error)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(*(pathlib.Path(arg) for arg in sys.argv[1:])))
