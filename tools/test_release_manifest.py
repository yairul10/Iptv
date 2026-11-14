"""The download URL, APK version, and release tag must be generated from the same version."""
import hashlib
import json
import os
from pathlib import Path
import runpy
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('release_manifest.py')

class ReleaseManifestTest(unittest.TestCase):
    def test_version_and_apk_url_follow_gradle(self):
        previous = Path.cwd()
        env_before = dict(os.environ)
        try:
            with tempfile.TemporaryDirectory() as folder:
                os.chdir(folder)
                Path('app').mkdir()
                Path('app/build.gradle').write_text("versionName '2.3.4'\n")
                Path('entrega').mkdir()
                Path('entrega/Mi-TV.apk').write_bytes(b'signed apk fixture')
                os.environ.update(GITHUB_RUN_NUMBER='123', SIGNER_SHA256='a' * 64)
                runpy.run_path(str(SCRIPT.resolve() if SCRIPT.is_absolute() else previous / SCRIPT))
                value = json.loads(Path('entrega/update.json').read_text())
                self.assertEqual(value['versionName'], '2.3.4')
                self.assertEqual(value['versionCode'], 123)
                self.assertEqual(value['apkUrl'], 'https://github.com/yairul10/Iptv/releases/download/v2.3.4-build.123/Mi-TV.apk')
                self.assertEqual(value['apkSha256'], hashlib.sha256(b'signed apk fixture').hexdigest())
        finally:
            os.chdir(previous)
            os.environ.clear()
            os.environ.update(env_before)

if __name__ == '__main__':
    unittest.main()
