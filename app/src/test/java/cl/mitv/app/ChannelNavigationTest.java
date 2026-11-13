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
}
