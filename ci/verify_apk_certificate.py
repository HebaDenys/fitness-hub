"""Check a successfully verified apksigner report against the CI test certificate."""
import argparse
import pathlib
import re

CERTIFICATE = re.compile(
    r"^(?:Signer #\d+|V\d+(?:\.\d+)? Signer):? certificate SHA-256 digest: ([0-9a-fA-F]{64})\r?$",
    re.MULTILINE,
)


def verify(report: str, expected: str) -> None:
    if not re.fullmatch(r"[0-9a-fA-F]{64}", expected):
        raise ValueError("Invalid expected certificate fingerprint")
    if not re.search(r"^Number of signers: 1\r?$", report, re.MULTILINE):
        raise ValueError("Expected exactly one APK signer")
    fingerprints = CERTIFICATE.findall(report)
    if not fingerprints or {item.lower() for item in fingerprints} != {expected.lower()}:
        raise ValueError("APK certificate does not match the CI test certificate")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=pathlib.Path)
    parser.add_argument("expected")
    args = parser.parse_args()
    verify(args.report.read_text(encoding="utf-8"), args.expected)
    print("APK certificate matches the existing CI test key.")
