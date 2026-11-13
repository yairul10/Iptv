"""Load the single private repository secret. Never generate or cache signing keys in CI."""
import base64
import hashlib
import json
import os
import subprocess
from pathlib import Path

bundle = os.environ.get('MITV_SIGNING_BUNDLE', '')
env_file = Path(os.environ['GITHUB_ENV'])
if not bundle:
    with env_file.open('a') as out:
        out.write('KEYSTORE_AVAILABLE=false\n')
    print('Signing secret not configured: run checks and unsigned compilation only; no installable APK will be published.')
    raise SystemExit(0)
data = json.loads(bundle)
password, alias = data['password'], data['alias']
assert isinstance(password,str) and 12 <= len(password) <= 256 and '\n' not in password and '\r' not in password
assert isinstance(alias,str) and alias == 'mitv'
key = base64.b64decode(data['keystore'], validate=True)
assert 100 < len(key) < 100000
path = Path(os.environ['RUNNER_TEMP']) / 'mitv-private.jks'
path.write_bytes(key)
path.chmod(0o600)
print('::add-mask::' + password)
key_env = dict(os.environ, MITV_CERT_PASSWORD=password)
cert = subprocess.check_output(['keytool','-exportcert','-keystore',str(path),'-storepass:env','MITV_CERT_PASSWORD','-alias',alias],env=key_env,stderr=subprocess.DEVNULL)
signer = hashlib.sha256(cert).hexdigest()
assert signer == Path('config/signing-sha256.txt').read_text().strip(), 'Signing identity does not match the fixed certificate'
with env_file.open('a') as out:
    out.write(f'KEYSTORE_AVAILABLE=true\nSIGNER_SHA256={signer}\nSIGNING_STORE_FILE={path}\nSIGNING_STORE_PASSWORD={password}\nSIGNING_KEY_PASSWORD={password}\nSIGNING_KEY_ALIAS={alias}\n')
print('Fixed signing certificate validated.')
