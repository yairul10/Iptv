package cl.mitv.app;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class UrlHealthTest {
 @Test public void doesNotMarkRestrictionsOrFirstTransientFailureDown() {
  assertEquals("UNKNOWN",HealthPolicy.status(403,10)); assertEquals("UNKNOWN",HealthPolicy.status(429,10));
  assertEquals("UNKNOWN",HealthPolicy.status(-1,1)); assertEquals("DOWN",HealthPolicy.status(-1,3));
  assertEquals("DOWN",HealthPolicy.status(404,1)); assertEquals("UP",HealthPolicy.status(200,0)); assertEquals(0,HealthPolicy.failures(200,5));
 }
 @Test public void headUnsupportedFallsBackToOneByteGetAndFollowsRedirects() throws Exception {
  try(MockWebServer server=new MockWebServer()) {
   server.enqueue(new MockResponse().setResponseCode(405)); server.enqueue(new MockResponse().setResponseCode(206).setBody("x"));
   assertEquals(206,UrlHealthWorker.probe(server.url("/live").toString()));
   assertEquals("HEAD",server.takeRequest().getMethod()); RecordedRequest get=server.takeRequest(); assertEquals("GET",get.getMethod()); assertEquals("bytes=0-0",get.getHeader("Range"));
   server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location","/next")); server.enqueue(new MockResponse().setResponseCode(200));
   assertEquals(200,UrlHealthWorker.probe(server.url("/redirect").toString()));
  }
 }
}
