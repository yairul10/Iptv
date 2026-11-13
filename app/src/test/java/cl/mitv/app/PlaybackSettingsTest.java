package cl.mitv.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackSettingsTest {
 @Test public void domainHeadersUseMostSpecificMatchAndNeverMatchSuffixSpoof() {
  PlaybackSettings s=new PlaybackSettings("MiTV-Test",PlaybackSettings.parseReferers("example.com=https://example.com/\ncdn.example.com=https://example.com/video"),720,1);
  assertEquals("https://example.com/video",s.headers("https://cdn.example.com/a.m3u8").get("Referer"));
  assertEquals("https://example.com/",s.headers("https://video.example.com/a").get("Referer"));
  assertNull(s.headers("https://example.com.evil.test/a").get("Referer"));
  assertEquals("MiTV-Test",s.headers("https://elsewhere.test/").get("User-Agent"));
 }
 @Test public void rejectsHeaderInjectionAndKeepsBufferBoundsAndTimer() {
  try { new PlaybackSettings("bad\r\nAuthorization: value",java.util.Collections.emptyMap(),0,1); fail(); } catch(IllegalArgumentException expected) { }
  try { PlaybackSettings.parseReferers("example.com=https://user:password@example.com/"); fail(); } catch(IllegalArgumentException expected) { }
  for(int i=0;i<3;i++) { PlaybackSettings s=new PlaybackSettings("",java.util.Collections.emptyMap(),0,i); assertTrue(s.maxBufferMs()>=s.minBufferMs()); assertTrue(s.minBufferMs()>=s.rebufferMs()); assertTrue(s.minBufferMs()>=s.startBufferMs()); }
  assertEquals(0,SettingsStore.remaining(1000,2000)); assertEquals(1000,SettingsStore.remaining(2000,1000));
 }
 @Test public void pinHashesAreSaltedAndInvalidPinsNeverMatch() {
  String first=PinCode.encode("1234"),second=PinCode.encode("1234"); assertNotEquals(first,second); assertTrue(first.matches("[a-f0-9]{48}:[a-f0-9]{64}"));
  assertTrue(PinCode.verify("1234",first)); assertFalse(PinCode.verify("9876",first)); assertFalse(PinCode.verify("",first)); assertFalse(PinCode.verify("1234","broken"));
 }
 @Test public void diagnosticRedactionHidesPathsCredentialsAndStandaloneTokens() {
  String text=DiagnosticText.clean("Error https://user:secret@host.example/live/private-token?token=ABC#fragment pin=1234 Authorization=Bearer secret");
  assertTrue(text.contains("host.example")); assertFalse(text.contains("user")); assertFalse(text.contains("private-token")); assertFalse(text.contains("ABC")); assertFalse(text.contains("1234")); assertFalse(text.contains("secret"));
 }
}
