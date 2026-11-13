package cl.mitv.app;

import android.net.Uri;
import org.json.JSONObject;

final class Channel {
    final String id, name, category, description, playerUrl, siteUrl;
    final boolean directPlayer;

    Channel(JSONObject item) throws org.json.JSONException {
        id = item.getString("id");
        name = item.getString("name");
        category = item.getString("category");
        description = item.getString("description");
        playerUrl = item.getString("playerUrl");
        siteUrl = item.getString("siteUrl");
        directPlayer = item.getBoolean("directPlayer");
        requireHttps(playerUrl);
        requireHttps(siteUrl);
    }

    private static void requireHttps(String url) {
        Uri uri = Uri.parse(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Expected a public HTTPS page");
    }
}
