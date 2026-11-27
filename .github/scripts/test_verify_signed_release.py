import tempfile
import unittest
import zipfile
from pathlib import Path

from verify_signed_release import (certificate_fingerprint, check_unsigned_checksums,
                                   digest_file, file_names, payload)

FINGERPRINT = "a" * 64


def signature(digest=FINGERPRINT, count=1, dn="CN=Owner"):
    return (f"Number of signers: {count}\nSigner #1 certificate DN: {dn}\n"
            f"Signer #1 certificate SHA-256 digest: {digest}\n")


class FinalVerificationTest(unittest.TestCase):
    def test_single_signer_fingerprint(self):
        self.assertEqual(FINGERPRINT, certificate_fingerprint(signature(FINGERPRINT.upper())))

    def test_multiple_missing_or_debug_signer_rejected(self):
        for output in (signature(count=2), signature(digest="invalid"),
                       signature(dn="CN=Android Debug,O=Android,C=US"),
                       signature() + "Signer #2 certificate SHA-256 digest: " + FINGERPRINT):
            with self.subTest(output=output), self.assertRaises(ValueError):
                certificate_fingerprint(output)

    def test_payload_ignores_only_signature_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            left, right = (Path(directory) / name for name in ("left.apk", "right.apk"))
            for path, signed in ((left, False), (right, True)):
                with zipfile.ZipFile(path, "w") as archive:
                    archive.writestr("AndroidManifest.xml", b"manifest")
                    archive.writestr("classes.dex", b"code")
                    archive.writestr("META-INF/dependency.txt", b"dependency")
                    if signed:
                        archive.writestr("META-INF/CERT.SF", b"signature")
                        archive.writestr("META-INF/CERT.RSA", b"certificate")
                        archive.writestr("META-INF/MANIFEST.MF", b"signing manifest")
            self.assertEqual(payload(left), payload(right))
            with zipfile.ZipFile(right, "a") as archive:
                archive.writestr("META-INF/extra.txt", b"not signature metadata")
            self.assertNotEqual(payload(left), payload(right))

    def test_duplicate_zip_entries_and_missing_payload_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "duplicate.apk"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"manifest")
                archive.writestr("classes.dex", b"code")
                with self.assertWarns(UserWarning):
                    archive.writestr("classes.dex", b"changed")
            with self.assertRaises(ValueError):
                payload(path)
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"manifest")
            with self.assertRaises(ValueError):
                payload(path)

    def test_four_unsigned_checksums_and_tampering(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            names = sorted(file_names(True))
            for name in names:
                (root / name).write_bytes(name.encode())
            sums = "".join(f"{digest_file(root / name)}  {name}\n" for name in names)
            (root / "SHA256SUMS.txt").write_text(sums, encoding="ascii")
            self.assertEqual(4, len(check_unsigned_checksums(root)))
            (root / names[0]).write_bytes(b"changed")
            with self.assertRaises(ValueError):
                check_unsigned_checksums(root)

    def test_checksum_manifest_rejects_extra_or_duplicate_names(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            names = sorted(file_names(True))
            for name in names:
                (root / name).write_bytes(name.encode())
            sums = "".join(f"{digest_file(root / name)}  {name}\n" for name in names)
            for invalid in (sums + sums.splitlines()[0] + "\n",
                            sums + FINGERPRINT + "  ../other.apk\n"):
                (root / "SHA256SUMS.txt").write_text(invalid, encoding="ascii")
                with self.assertRaises(ValueError):
                    check_unsigned_checksums(root)
