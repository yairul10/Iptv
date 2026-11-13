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
 @Test public void getUnsupportedFallsBackToHeadAndFollowsRedirects() throws Exception {
  try(MockWebServer server=new MockWebServer()) {
   server.enqueue(new MockResponse().setResponseCode(405)); server.enqueue(new MockResponse().setResponseCode(206));
   assertEquals(206,UrlHealthWorker.probe(server.url("/live").toString()).code);
   RecordedRequest get=server.takeRequest(); assertEquals("GET",get.getMethod()); assertEquals("bytes=0-127",get.getHeader("Range")); assertEquals("HEAD",server.takeRequest().getMethod());
   server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location","/next")); server.enqueue(new MockResponse().setResponseCode(200));
   assertEquals(200,UrlHealthWorker.probe(server.url("/redirect").toString()).code);
  }
 }
 @Test public void transientFailuresBecomeDownOnThirdAttempt() {
  int failures=0;
  for(int i=1;i<=3;i++) { failures=HealthPolicy.failures(500,failures); assertEquals(i,failures); assertEquals(i==3?"DOWN":"UNKNOWN",HealthPolicy.status(500,failures)); }
  assertEquals(9,HealthPolicy.failures(-1,Integer.MAX_VALUE));
 }
 @Test public void rangeRetryMustStillValidateHlsBody() throws Exception {
  try(MockWebServer server=new MockWebServer()) {
   server.enqueue(new MockResponse().setResponseCode(416)); server.enqueue(new MockResponse().setBody("<html>error</html>"));
   UrlHealthWorker.Probe result=UrlHealthWorker.probe(server.url("/live.m3u8").toString()); assertEquals(200,result.code); assertFalse(result.manifest);
   assertEquals("bytes=0-127",server.takeRequest().getHeader("Range")); assertNull(server.takeRequest().getHeader("Range"));
   server.enqueue(new MockResponse().setBody("\uFEFF#EXTM3U\n#EXT-X-VERSION:3")); assertTrue(UrlHealthWorker.probe(server.url("/ok.m3u8").toString()).manifest);
  }
 }
}
