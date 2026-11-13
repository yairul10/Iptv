"""Check catalogue integrity and the relationship between configured navigation and channels."""
import json
import re
import subprocess
import tempfile
from pathlib import Path
from urllib.parse import urlparse

root = Path(__file__).resolve().parent.parent
items = json.loads((root / 'app/src/main/assets/channels.json').read_text())
ids = set()
policy = root / 'app/src/main/java/cl/mitv/app/NavigationPolicy.java'
# Compile the actual Java navigation policy and test every configured URL against it.
with tempfile.TemporaryDirectory() as output:
    subprocess.run(['javac', '-d', output, str(policy), str(root / 'tests/NavigationPolicyTest.java'), str(root / 'app/src/main/java/cl/mitv/app/UpdatePolicy.java'), str(root / 'tests/UpdatePolicyTest.java')], check=True)
    subprocess.run(['java', '-cp', output, 'cl.mitv.app.NavigationPolicyTest'], check=True)
    subprocess.run(['java', '-cp', output, 'cl.mitv.app.UpdatePolicyTest'], check=True)
    cases = []
    for channel in items:
        assert re.fullmatch(r'[a-z0-9]+', channel['id']), 'Invalid preference key'
        assert channel['id'] not in ids, 'Duplicate preference key'
        ids.add(channel['id'])
        assert channel['name'] and channel['description']
        assert isinstance(channel['category'], str) and 0 < len(channel['category'].strip()) <= 40
        assert isinstance(channel['directPlayer'], bool)
        for field in ['playerUrl', 'siteUrl']:
            url = channel[field]
            parsed = urlparse(url)
            assert parsed.scheme == 'https' and parsed.hostname and not parsed.username
            cases.append('if (!NavigationPolicy.isPublicHttps(' + json.dumps(url) + ')) throw new AssertionError("Configured URL blocked");')
    probe = Path(output) / 'CatalogNavigationTest.java'
    probe.write_text('package cl.mitv.app; public class CatalogNavigationTest { public static void main(String[] a) {' + ''.join(cases) + '} }')
    subprocess.run(['javac', '-cp', output, '-d', output, str(probe)], check=True)
    subprocess.run(['java', '-cp', output, 'cl.mitv.app.CatalogNavigationTest'], check=True)
assert 'c9' in ids, 'Keep Canal 9 and its existing favorite key'
print(f'Catalogue: {len(items)} unique channels; all configured URLs accepted by navigation policy')
