import struct
import unittest
import xml.etree.ElementTree as ET

from audit_apk import elf_info, inspect_manifest, inspect_native, REQUIRED_PERMISSIONS
import tempfile
import zipfile
from pathlib import Path


def manifest(extra="", components="", backup="false"):
    permissions = "".join(f'<uses-permission android:name="{p}"/>' for p in REQUIRED_PERMISSIONS)
    return ET.fromstring('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                         'package="com.najim.android">' + permissions + extra +
                         f'<application android:allowBackup="{backup}" android:icon="@mipmap/ic_launcher" '
                         'android:roundIcon="@mipmap/ic_launcher">'
                         '<activity android:name=".MainActivity" android:exported="true"/>'
                         + components + '</application></manifest>')


def elf64(align=16384):
    data = bytearray(120)
    data[:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<H", data, 18, 183)
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<HH", data, 54, 56, 1)
    struct.pack_into("<IIQQQQQQ", data, 64, 1, 5, 0, 0, 0, 120, 120, align)
    return bytes(data)


class AuditTest(unittest.TestCase):
    def test_expected_manifest(self):
        _, errors = inspect_manifest(manifest())
        self.assertEqual([], errors)

    def test_sensitive_permission_and_backup(self):
        _, errors = inspect_manifest(manifest(
            extra='<uses-permission android:name="android.permission.READ_CONTACTS"/>', backup="true"))
        self.assertEqual(2, len(errors))

    def test_exported_component_blocked(self):
        _, errors = inspect_manifest(manifest(components='<service android:name="Bad" android:exported="true"/>'))
        self.assertEqual(1, len(errors))

    def test_known_protected_profile_receiver(self):
        _, errors = inspect_manifest(manifest(components=
            '<receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" '
            'android:exported="true" android:permission="android.permission.DUMP"/>'))
        self.assertEqual([], errors)

    def test_elf_alignment_extracted(self):
        self.assertEqual([16384], elf_info(elf64(), "arm64-v8a")["load_alignments"])
        self.assertEqual([4096], elf_info(elf64(4096), "arm64-v8a")["load_alignments"])

    def test_wrong_architecture_and_truncation(self):
        with self.assertRaises(ValueError):
            elf_info(elf64(), "armeabi-v7a")
        with self.assertRaises(ValueError):
            elf_info(elf64()[:64], "arm64-v8a")

    def test_single_abi_package_gate(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "fixture.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("lib/arm64-v8a/libsample.so", elf64())
            _, errors = inspect_native(apk, {"arm64-v8a"}, exact=True)
            self.assertEqual([], errors)
            _, errors = inspect_native(apk, {"armeabi-v7a"}, exact=True)
            self.assertTrue(any("missing" in e for e in errors))
            self.assertTrue(any("Unexpected native ABI" in e for e in errors))

    def test_single_abi_package_rejects_unaligned_64_bit_library(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "fixture.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("lib/arm64-v8a/libsample.so", elf64(4096))
            _, errors = inspect_native(apk, {"arm64-v8a"}, exact=True)
            self.assertTrue(any("not 16KB aligned" in e for e in errors))

    def test_release_rejects_debuggable_and_test_only(self):
        root = manifest()
        root.insert(0, ET.Element("uses-sdk", {"{http://schemas.android.com/apk/res/android}minSdkVersion": "24",
                                              "{http://schemas.android.com/apk/res/android}targetSdkVersion": "35"}))
        app = root.find("application")
        for attr in ("debuggable", "testOnly"):
            app.set("{http://schemas.android.com/apk/res/android}" + attr, "true")
        _, errors = inspect_manifest(root, release=True)
        self.assertEqual(2, len(errors))

    def test_release_requires_expected_sdk_and_accepts_non_debug(self):
        root = manifest()
        _, errors = inspect_manifest(root, release=True)
        self.assertEqual(["Unexpected release SDK levels"], errors)
        root.insert(0, ET.Element("uses-sdk", {"{http://schemas.android.com/apk/res/android}minSdkVersion": "24",
                                              "{http://schemas.android.com/apk/res/android}targetSdkVersion": "35"}))
        _, errors = inspect_manifest(root, release=True)
        self.assertEqual([], errors)

    def test_launcher_icon_required_in_packaged_manifest(self):
        root = manifest()
        app = root.find("application")
        namespace = "{http://schemas.android.com/apk/res/android}"
        app.attrib.pop(namespace + "icon")
        app.set(namespace + "roundIcon", "@null")
        _, errors = inspect_manifest(root)
        self.assertEqual(["Missing launcher icon", "Missing launcher roundIcon"], errors)
