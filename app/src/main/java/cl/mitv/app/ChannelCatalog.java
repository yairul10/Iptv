package cl.mitv.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ChannelCatalog {
    static List<Channel> parse(String source) throws org.json.JSONException {
        JSONArray array = new JSONArray(source);
        if(array.length() < 1 || array.length() > 300) throw new IllegalArgumentException("Invalid catalog size");
        List<Channel> result = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for(int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            Channel channel = new Channel(item);
            if(!ids.add(channel.id)) throw new IllegalArgumentException("Duplicate channel ID");
            result.add(channel);
        }
        return result;
    }
}
