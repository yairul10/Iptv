package cl.mitv.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChannelCatalogTest {
    private static final String CHANNEL = "{\"id\":\"c9\",\"name\":\"Canal 9\",\"category\":\"Regionales\",\"description\":\"Biobío\",\"playerUrl\":\"https://rudo.video/live/c9\",\"siteUrl\":\"https://www.canal9.cl/\",\"directPlayer\":true}";

    @Test public void acceptsValidCatalogAndNewCategories() throws Exception {
        assertEquals("c9", ChannelCatalog.parse("[" + CHANNEL + "]").get(0).id);
        assertEquals("Deportes", ChannelCatalog.parse("[" + CHANNEL.replace("Regionales","Deportes") + "]").get(0).category);
    }
    @Test public void rejectsInvalidRemoteCatalogWithoutReturningPartialData() {
        String[] invalid = {"[]", "not json", "[" + CHANNEL + "," + CHANNEL + "]",
            "[" + CHANNEL + ",{}]", "[" + CHANNEL.replace("https://rudo.video/live/c9","http://rudo.video/live/c9") + "]",
            "[" + CHANNEL.replace("https://www.canal9.cl/","https://localhost/") + "]",
            "[" + CHANNEL.replace("\"id\":\"c9\"","\"id\":\"../../bad\"") + "]"};
        for(String data : invalid) {
            try { ChannelCatalog.parse(data); fail("Accepted invalid remote catalog"); }
            catch(Exception expected) { /* Previous validated catalogue remains usable. */ }
        }
    }
}
