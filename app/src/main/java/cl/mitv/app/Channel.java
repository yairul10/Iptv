package cl.mitv.app;

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
        if(!id.matches("[a-z0-9]{1,40}") || name.trim().isEmpty() || name.length() > 80
            || category.trim().isEmpty() || category.length() > 40 || description.length() > 240)
            throw new IllegalArgumentException("Invalid channel fields");
        if(!NavigationPolicy.isPublicHttps(playerUrl) || !NavigationPolicy.isPublicHttps(siteUrl))
            throw new IllegalArgumentException("Expected public HTTPS URLs");
    }
}
