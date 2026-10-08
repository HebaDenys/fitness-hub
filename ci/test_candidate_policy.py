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

    def test_open_food_facts_is_hard_disabled_in_candidate_di(self):
        module = (ROOT / "app/src/main/java/io/github/hebadenys/fitnesshub/di/AppModule.kt").read_text(encoding="utf-8")
        self.assertIn("OpenFoodFactsConnector(enabledProvider = { false })", module)

if __name__ == "__main__":
    unittest.main()
