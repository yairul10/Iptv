package cl.mitv.app;
import cl.mitv.app.data.CatalogItem;
import java.util.*;

/** Channel numbers follow the full catalogue; filters only narrow channel surfing. */
final class ChannelNavigation {
 static int number(List<CatalogItem> catalog, CatalogItem current) {
  if(current==null || current.movie) return 0;
  int number=0;
  for(CatalogItem item:catalog) if(!item.movie) { number++; if(item.id.equals(current.id)) return number; }
  return 0;
 }
 static CatalogItem adjacent(List<CatalogItem> catalog,CatalogItem current,int direction,String category,boolean favorites) {
  return adjacent(catalog,current,direction,category,favorites,"");
 }
 static CatalogItem adjacent(List<CatalogItem> catalog,CatalogItem current,int direction,String category,boolean favorites,String query) {
  if(current==null || current.movie) return null;
  List<CatalogItem> eligible=new ArrayList<>();
  for(CatalogItem item:catalog) if(!item.movie && CatalogSearch.matches(item.name,query) && (!favorites || item.favorite) && ("Todos".equals(category) || category.equals(item.category))) eligible.add(item);
  if(eligible.isEmpty()) return null;
  int index=-1;
  for(int i=0;i<eligible.size();i++) if(eligible.get(i).id.equals(current.id)) { index=i; break; }
  if(index<0) return eligible.get(direction<0?eligible.size()-1:0);
  return eligible.get((index+(direction<0?-1:1)+eligible.size())%eligible.size());
 }
}
