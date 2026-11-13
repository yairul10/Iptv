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

    static boolean isPublicHttps(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443) && host != null && host.length() <= 253
                && host.matches("(?i)[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,63}")
                && !host.toLowerCase(java.util.Locale.ROOT).endsWith(".local")
                && !host.toLowerCase(java.util.Locale.ROOT).endsWith(".localhost");
        } catch(URISyntaxException e) { return false; }
    }

    static boolean isChannelHttps(String url, String playerUrl, String siteUrl) {
        if(!isPublicHttps(url)) return false;
        if(isOfficialHttps(url)) return true;
        try {
            String host = new URI(url).getHost();
            return sameSite(host,new URI(playerUrl).getHost()) || sameSite(host,new URI(siteUrl).getHost());
        } catch(URISyntaxException e) { return false; }
    }
    private static boolean sameSite(String host, String seed) {
        if(seed == null) return false;
        seed = seed.toLowerCase(java.util.Locale.ROOT);
        if(seed.startsWith("www.")) seed = seed.substring(4);
        host = host.toLowerCase(java.util.Locale.ROOT);
        return host.equals(seed) || host.endsWith("." + seed);
    }
}
