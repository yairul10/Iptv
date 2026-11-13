package cl.mitv.app;

import java.net.URI;
import java.net.URISyntaxException;

final class UpdatePolicy {
    static boolean trustedApk(String value) {
        try {
            URI u = new URI(value);
            return "https".equalsIgnoreCase(u.getScheme()) && "github.com".equalsIgnoreCase(u.getHost())
                && u.getUserInfo() == null && u.getPort() == -1 && u.getQuery() == null && u.getFragment() == null
                && u.getRawPath().matches("/yairul10/Iptv/releases/download/v[0-9]+\\.[0-9]+\\.[0-9]+-build\\.[0-9]+/Mi-TV\\.apk");
        } catch(URISyntaxException e) { return false; }
    }
    static boolean compatible(long installed, long offered, String installedPackage, String offeredPackage,
                              String installedSigner, String offeredSigner, String apkUrl) {
        return offered > installed && installedPackage.equals(offeredPackage)
            && installedSigner.matches("[a-f0-9]{64}") && installedSigner.equalsIgnoreCase(offeredSigner)
            && trustedApk(apkUrl);
    }
}
