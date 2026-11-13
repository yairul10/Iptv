"""Generate metadata only after a signed APK and all required checks succeed."""
import hashlib
import json
import os
from pathlib import Path

version = '0.6.4'
number = int(os.environ['GITHUB_RUN_NUMBER'])
tag = f'v{version}-build.{number}'
url = f'https://github.com/yairul10/Iptv/releases/download/{tag}/Mi-TV.apk'
apk = Path('entrega/Mi-TV.apk')
signer = os.environ['SIGNER_SHA256']
assert len(signer) == 64 and all(c in '0123456789abcdef' for c in signer)
Path('entrega/update.json').write_text(json.dumps({
    'versionName': version,
    'versionCode': number,
    'packageName': 'cl.mitv.app.preview',
    'certificateSha256': signer,
    'apkUrl': url,
    'apkSha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
}, indent=2) + '\n')
