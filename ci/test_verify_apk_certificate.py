import unittest
from verify_apk_certificate import verify

FINGERPRINT = "21ae112d0eac5a9b3830b1819b0746e545eb58e00a8794f1b0640c61abc93509"


class CertificateReportTest(unittest.TestCase):
    def test_legacy_signer_output(self):
        verify(f"Verifies\nNumber of signers: 1\nSigner #1 certificate SHA-256 digest: {FINGERPRINT}\n", FINGERPRINT)

    def test_scheme_labelled_signer_output(self):
        verify(f"Verifies\nNumber of signers: 1\nV2 Signer: certificate SHA-256 digest: {FINGERPRINT}\n", FINGERPRINT)

    def test_multiple_schemes_same_certificate(self):
        report = "Number of signers: 1\r\n" + "".join(
            f"V{version} Signer: certificate SHA-256 digest: {FINGERPRINT.upper()}\r\n"
            for version in ("2", "3", "3.1")
        )
        verify(report, FINGERPRINT)

    def test_public_key_digest_is_not_a_certificate(self):
        with self.assertRaises(ValueError):
            verify(f"Number of signers: 1\nV2 Signer: public key SHA-256 digest: {FINGERPRINT}\n", FINGERPRINT)

    def test_wrong_certificate_is_rejected(self):
        with self.assertRaises(ValueError):
            verify(f"Number of signers: 1\nV2 Signer: certificate SHA-256 digest: {'a' * 64}\n", FINGERPRINT)

    def test_multiple_signers_are_rejected(self):
        with self.assertRaises(ValueError):
            verify(f"Number of signers: 2\nV2 Signer: certificate SHA-256 digest: {FINGERPRINT}\n", FINGERPRINT)

    def test_empty_output_is_rejected(self):
        with self.assertRaises(ValueError):
            verify("", FINGERPRINT)


if __name__ == "__main__":
    unittest.main()
