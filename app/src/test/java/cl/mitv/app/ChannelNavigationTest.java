package cl.mitv.app;
import cl.mitv.app.data.CatalogItem;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChannelNavigationTest {
 private CatalogItem item(String id,boolean movie,String category,boolean favorite) {
  CatalogItem i=new CatalogItem(); i.id=id;i.movie=movie;i.category=category;i.favorite=favorite;return i;
 }
 @Test public void numbersIgnoreMoviesAndDoNotChangeWithFilters() {
  CatalogItem a=item("a",false,"A",false),film=item("a",true,"Cine",false),b=item("b",false,"B",true);
  List<CatalogItem> all=Arrays.asList(a,film,b);
  assertEquals(1,ChannelNavigation.number(all,a));assertEquals(2,ChannelNavigation.number(all,b));assertEquals(0,ChannelNavigation.number(all,film));
  assertSame(b,ChannelNavigation.adjacent(all,b,1,"Todos",true));assertEquals(2,ChannelNavigation.number(all,b));
 }
 @Test public void surfingWrapsAndRespectsGroupsAndFavorites() {
  CatalogItem a=item("a",false,"A",true),b=item("b",false,"B",false),c=item("c",false,"A",true),film=item("film",true,"A",true);
  List<CatalogItem> all=Arrays.asList(a,b,c,film);
  assertSame(c,ChannelNavigation.adjacent(all,a,1,"A",false));assertSame(a,ChannelNavigation.adjacent(all,c,1,"A",false));
  assertSame(c,ChannelNavigation.adjacent(all,a,-1,"Todos",true));assertSame(b,ChannelNavigation.adjacent(all,a,1,"Todos",false));
  assertNull(ChannelNavigation.adjacent(all,film,1,"Todos",false));assertNull(ChannelNavigation.adjacent(all,a,1,"Missing",false));
  assertSame(a,ChannelNavigation.adjacent(all,b,1,"A",true));
 }
 @Test public void deletionContinuesFromRemovedPositionUsingFreshRemainingRows() {
  CatalogItem a=item("a",false,"A",true),b=item("b",false,"A",true),c=item("c",false,"A",true),film=item("b",true,"A",true);
  a.name="Canal A";b.name="Canal B";c.name="Canal C";film.name="Canal película";
  List<CatalogItem> before=Arrays.asList(a,film,b,c);
  assertSame(c,ChannelNavigation.afterDeletion(before,b,Arrays.asList(a,film,c),"Todos",false,""));
  assertSame(a,ChannelNavigation.afterDeletion(before,c,Arrays.asList(a,film,b),"Todos",false,""));
  assertNull(ChannelNavigation.afterDeletion(before,b,Arrays.asList(film),"Todos",false,""));
  c.favorite=false;
  assertSame(a,ChannelNavigation.afterDeletion(before,b,Arrays.asList(a,c),"A",true,"canal"));
  assertNull(ChannelNavigation.afterDeletion(before,b,Arrays.asList(a,c),"A",true,"canal c"));
  assertNull(ChannelNavigation.afterDeletion(before,film,Arrays.asList(a,c),"Todos",false,""));
 }
}
