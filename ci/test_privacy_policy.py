"""Source-policy regression tests; not a substitute for Android/OEM backup testing."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
DOMAINS = {'root', 'file', 'database', 'sharedpref', 'external', 'device_root', 'device_file', 'device_database', 'device_sharedpref'}

class PrivacyPolicyTest(unittest.TestCase):
    def test_manifest_explicitly_disables_automatic_backup(self):
        app = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot().find('application')
        self.assertEqual('false', app.get(ANDROID + 'allowBackup'))
        self.assertEqual('@xml/backup_rules', app.get(ANDROID + 'fullBackupContent'))
        self.assertEqual('@xml/data_extraction_rules', app.get(ANDROID + 'dataExtractionRules'))
        self.assertEqual('false', app.get(ANDROID + 'usesCleartextTraffic'))

    def assert_all_domains_excluded(self, element):
        self.assertIsNotNone(element)
        self.assertFalse(element.findall('include'))
        self.assertEqual(DOMAINS, {entry.get('domain') for entry in element.findall('exclude') if entry.get('path') == '.'})

    def test_legacy_backup_excludes_health_and_secret_storage(self):
        root = ET.parse(ROOT / 'app/src/main/res/xml/backup_rules.xml').getroot()
        self.assertEqual('full-backup-content', root.tag)
        self.assert_all_domains_excluded(root)

    def test_modern_cloud_and_device_transfer_are_both_excluded(self):
        root = ET.parse(ROOT / 'app/src/main/res/xml/data_extraction_rules.xml').getroot()
        self.assertEqual('data-extraction-rules', root.tag)
        self.assert_all_domains_excluded(root.find('cloud-backup'))
        self.assert_all_domains_excluded(root.find('device-transfer'))

    def test_variants_do_not_enable_automatic_backup_again(self):
        for path in (ROOT / 'app/src').glob('*/AndroidManifest.xml'):
            app = ET.parse(path).getroot().find('application')
            if app is not None:
                self.assertNotEqual('true', app.get(ANDROID + 'allowBackup'), str(path))

if __name__ == '__main__':
    unittest.main()
