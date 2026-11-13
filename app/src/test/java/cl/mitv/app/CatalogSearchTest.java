package cl.mitv.app;
import cl.mitv.app.data.CatalogItem;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
public class CatalogSearchTest {
 @Test public void findsPartialNamesIgnoringCaseAccentsAndExtraSpaces() {
  assertTrue(CatalogSearch.matches("Canal 9 Bío Bío","BIO bio"));
  assertTrue(CatalogSearch.matches("Canal 13","  CANAL   13  "));
  assertTrue(CatalogSearch.matches("TVU","tv")); assertTrue(CatalogSearch.matches("TVU",""));
  assertFalse(CatalogSearch.matches("TVN","TVU"));
 }
 @Test public void channelSurfingUsesSearchAlongsideFavoritesAndGroups() {
  CatalogItem a=item("a","Canal 9",true),b=item("b","TVU",true),c=item("c","Canal 13",false);
  assertSame(a,ChannelNavigation.adjacent(Arrays.asList(a,b,c),a,1,"Todos",true,"canal"));
  assertSame(c,ChannelNavigation.adjacent(Arrays.asList(a,b,c),a,1,"Todos",false,"canal"));
  assertNull(ChannelNavigation.adjacent(Arrays.asList(a,b,c),a,1,"Otro",false,"canal"));
  assertEquals(3,ChannelNavigation.number(Arrays.asList(a,b,c),c));
 }
 private CatalogItem item(String id,String name,boolean favorite) { CatalogItem i=new CatalogItem();i.id=id;i.name=name;i.favorite=favorite;i.category="Nacionales";return i; }
}
