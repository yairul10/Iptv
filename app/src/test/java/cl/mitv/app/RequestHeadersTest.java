package cl.mitv.app;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class RequestHeadersTest {
 @Test public void downloadsAndHealthUseCustomHeadersWithoutForwardingRefererToAnotherDomain() throws Exception {
  PlaybackSettings previous=PlaybackSettings.current;
  try(MockWebServer server=new MockWebServer()) {
   HttpUrl first=server.url("/catalog"); String other=first.host().equals("localhost")?"127.0.0.1":"localhost";
   PlaybackSettings.current=new PlaybackSettings("MiTV-Header-Test",PlaybackSettings.parseReferers(first.host()+"=https://ref.example/"),0,1);
   server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location",server.url("/next").newBuilder().host(other).build()));
   server.enqueue(new MockResponse().setBody("ok")); assertEquals("ok",NetworkText.get(first.toString(),1024));
   RecordedRequest original=server.takeRequest(),redirected=server.takeRequest();
   assertEquals("MiTV-Header-Test",original.getHeader("User-Agent")); assertEquals("https://ref.example/",original.getHeader("Referer")); assertNull(redirected.getHeader("Referer"));
   server.enqueue(new MockResponse().setResponseCode(200)); assertEquals(200,UrlHealthWorker.probe(first.toString())); RecordedRequest health=server.takeRequest();
   assertEquals("MiTV-Header-Test",health.getHeader("User-Agent")); assertEquals("https://ref.example/",health.getHeader("Referer"));
  } finally { PlaybackSettings.current=previous; }
 }
}
