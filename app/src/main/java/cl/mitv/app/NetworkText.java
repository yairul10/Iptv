package cl.mitv.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class NetworkText {
    static String get(String address, int maxBytes) throws IOException {
        URL url = new URL(address);
        final boolean secure="https".equals(url.getProtocol());
        for(int redirect = 0; redirect < 6; redirect++) {
            if(!"https".equals(url.getProtocol())&&!"http".equals(url.getProtocol())) throw new IOException("HTTP/HTTPS required");
            if(secure&&!"https".equals(url.getProtocol())) throw new IOException("Insecure redirect");
            HttpURLConnection c = (HttpURLConnection)url.openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(15000); c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent",PlaybackSettings.current.userAgent.isEmpty()?"MiTV/0.5.0":PlaybackSettings.current.userAgent);
            for(java.util.Map.Entry<String,String> header:PlaybackSettings.current.headers(url.toString()).entrySet()) c.setRequestProperty(header.getKey(),header.getValue());
            c.setUseCaches(false);
            try {
                int code = c.getResponseCode();
                if(code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = c.getHeaderField("Location");
                    if(location == null) throw new IOException("Missing redirect");
                    url = new URL(url,location); continue;
                }
                if(code != 200) throw new IOException("HTTP " + code);
                if(c.getContentLength() > maxBytes) throw new IOException("Response too large");
                try(InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096]; int n;
                    while((n = in.read(buffer)) != -1) {
                        if(out.size() + n > maxBytes) throw new IOException("Response too large");
                        out.write(buffer,0,n);
                    }
                    return out.toString(StandardCharsets.UTF_8.name());
                }
            } finally { c.disconnect(); }
        }
        throw new IOException("Too many redirects");
    }
}
