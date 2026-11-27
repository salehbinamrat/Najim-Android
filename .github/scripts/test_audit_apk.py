import struct
import unittest
import xml.etree.ElementTree as ET

from audit_apk import elf_info, inspect_manifest, REQUIRED_PERMISSIONS


def manifest(extra="", components="", backup="false"):
    permissions = "".join(f'<uses-permission android:name="{p}"/>' for p in REQUIRED_PERMISSIONS)
    return ET.fromstring('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                         'package="com.najim.android">' + permissions + extra +
                         f'<application android:allowBackup="{backup}">'
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
