package cl.mitv.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class CatalogDocumentTest {
 static final String LEGACY="[{\"id\":\"c9\",\"name\":\"Canal 9\",\"category\":\"Regionales\",\"description\":\"Biobío\",\"playerUrl\":\"https://rudo.video/live/c9\",\"siteUrl\":\"https://www.canal9.cl/\",\"directPlayer\":true}]";
 @Test public void parsesM3uQuotedCommasKindsAndDuplicateIds() throws Exception {
  CatalogDocument d=CatalogDocument.parse("\uFEFF#EXTM3U\n#EXTINF:-1 tvg-id=\"ABC HD\" group-title=\"Deporte, fútbol\",Canal uno\nhttps://video.example/live.m3u8\n#EXTINF:-1 tvg-id=\"ABC HD\",Duplicado\nhttps://video.example/live.m3u8\n#EXTINF:-1 tvg-id=\"film\" x-mitv-kind=\"movie\",Cine\nhttps://video.example/film.mp4\n#EXTINF:-1,Inválido\njavascript:alert(1)",false);
  assertEquals(2,d.items.size()); assertEquals(1,d.duplicates); assertEquals(1,d.errors.size());
  assertEquals("Deporte, fútbol",d.items.get(0).group); assertTrue(d.items.get(1).movie);
 }
 @Test public void exportsRoundTripJsonAndM3uWithModesGroupsAndMovies() throws Exception {
  CatalogDocument d=CatalogDocument.parse(LEGACY,false);
  CatalogDocument.Item movie=new CatalogDocument.Item(); movie.id="film"; movie.name="Película"; movie.group="Cine"; movie.mode="STREAM"; movie.url="https://video.example/movie.mp4"; movie.siteUrl=movie.url; movie.movie=true; d.items.add(movie);
  for(String text:new String[]{d.toJson(),d.toM3u()}) {
   CatalogDocument back=CatalogDocument.parse(text,false); assertEquals(2,back.items.size()); assertTrue(back.items.get(1).movie);
   assertEquals("OFFICIAL_PLAYER",back.items.get(0).mode); assertEquals("Cine",back.items.get(1).group);
   assertEquals("https://video.example/movie.mp4",back.items.get(1).url);
  }
 }
 @Test public void rejectsMalformedDataAndSupportsExplicitEmptyRemoteSnapshot() throws Exception {
  for(String invalid:new String[]{"{}","{} trailing","#EXTM3U\n#EXTINF:-1,Sin URL","[]","{\"version\":3}","[{}]"}) {
   try { CatalogDocument.parse(invalid,false); fail(invalid); } catch(Exception expected) { }
  }
  CatalogDocument empty=CatalogDocument.parseRemote("{\"schemaVersion\":1,\"version\":2,\"channels\":[],\"movies\":[]}");
  assertEquals(2,empty.version); assertTrue(empty.items.isEmpty());
  assertFalse(CatalogDocument.validUrl("file:///sdcard/a")); assertFalse(CatalogDocument.validUrl("https://user:pass@host.example/"));
  assertTrue(CatalogDocument.validUrl("http://192.168.1.10/live.m3u8"));
 }
}
