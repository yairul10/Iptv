package cl.mitv.app;

import java.net.URI;
import java.net.URISyntaxException;

/** Main-frame navigation only. Embedded media resources remain governed by WebView. */
final class NavigationPolicy {
    private static final String[] OFFICIAL_DOMAINS = {
        "canal9.cl", "tvu.cl", "tvn.cl", "24horas.cl", "chilevision.cl",
        "mega.cl", "meganoticias.cl", "13.cl", "t13.cl", "tvmas.tv", "rudo.video"
    };

    static boolean isOfficialHttps(String url) {
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(java.util.Locale.ROOT);
            for (String domain : OFFICIAL_DOMAINS) {
                if (host.equals(domain) || host.endsWith("." + domain)) return true;
            }
        } catch (URISyntaxException ignored) { }
        return false;
    }
}
