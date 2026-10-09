import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

class CandidatePolicyTest(unittest.TestCase):
    def test_candidate_has_no_general_network_permission_or_health_writes(self):
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertNotIn("android.permission.INTERNET", manifest)
        self.assertNotIn("android.permission.health.WRITE_", manifest)
        self.assertIn("android.permission.health.READ_WEIGHT", manifest)
        self.assertIn("android.permission.health.READ_BODY_FAT", manifest)

    def test_xiaomi_network_remains_private_signing_gated(self):
        access = (ROOT / "app/src/main/java/io/github/hebadenys/fitnesshub/core/xiaomi/XiaomiAccess.kt").read_text(encoding="utf-8")
        self.assertIn("AwaitingPrivateSigning", access)
        self.assertIn("PRIVATE_SIGNING_REQUIRED", access)

    def test_release_network_is_gated_by_actual_private_certificate_and_debug_flag(self):
        release = (ROOT / "app/src/release/AndroidManifest.xml").read_text()
        self.assertIn("android.permission.INTERNET", release)
        runtime = (ROOT / "app/src/main/java/io/github/hebadenys/fitnesshub/core/xiaomi/XiaomiCloudClient.kt").read_text()
        self.assertIn("val gate = XiaomiPrivateSigningGate(context)", runtime)
        gate = (ROOT / "app/src/main/java/io/github/hebadenys/fitnesshub/core/xiaomi/XiaomiPrivateSigningGate.kt").read_text()
        self.assertIn("GET_SIGNING_CERTIFICATES", gate)
        self.assertIn("FLAG_DEBUGGABLE", gate)
        self.assertIn("certificates.size == 1", gate)
        self.assertIn("a1345938ecf27fb5d87609695c161ef4331529648dabdb8700ea1b51c1df3d13", gate)

    def test_open_food_facts_is_hard_disabled_in_candidate_di(self):
        module = (ROOT / "app/src/main/java/io/github/hebadenys/fitnesshub/di/AppModule.kt").read_text(encoding="utf-8")
        self.assertIn("OpenFoodFactsConnector(enabledProvider = { false })", module)

if __name__ == "__main__":
    unittest.main()
