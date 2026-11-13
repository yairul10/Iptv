package cl.mitv.app;
import android.content.SharedPreferences;
import cl.mitv.app.data.*;
import java.util.*;

/** Synchronous transactional operations; callers must run off the UI thread. */
final class CatalogStore {
 interface Seed { String read() throws Exception; }
 final AppDatabase db;
 final CatalogDao dao;
 static final String DEFAULT_REMOTE="https://raw.githubusercontent.com/yairul10/Iptv/main/remote/catalog.json";
 CatalogStore(AppDatabase db) { this.db=db; dao=db.catalog(); }
 void initialize(Seed seed,SharedPreferences legacy) {
  db.runInTransaction(() -> {
   if("1".equals(dao.metadata("initialized"))) return;
   try {
    CatalogDocument first=CatalogDocument.parse(seed.read(),false);
    if(!first.errors.isEmpty()||first.duplicates>0) throw new IllegalArgumentException("El catálogo inicial no es válido.");
    apply(first,false,DEFAULT_REMOTE,0);
    if(legacy!=null) {
     String cached=legacy.getString("catalog_json",null);
     if(cached!=null) try { CatalogDocument old=CatalogDocument.parse(cached,false); if(!old.errors.isEmpty()||old.duplicates>0) throw new IllegalArgumentException("Invalid cache"); apply(old,false,DEFAULT_REMOTE,0); } catch(Exception ignored) { /* Keep the valid asset seed. */ }
     for(ChannelEntity item:dao.allChannels()) {
      item.favorite=legacy.getBoolean(item.id+"_favorite",false);
      item.testResult=Math.max(0,Math.min(2,legacy.getInt(item.id+"_result",0))); dao.putChannel(item);
     }
    }
    dao.putMetadata(new Metadata("remote_url",DEFAULT_REMOTE));
    dao.putMetadata(new Metadata("initialized","1"));
   } catch(Exception e) { throw new IllegalStateException("No se pudo inicializar el catálogo.",e); }
  });
 }
 int[] preview(CatalogDocument document) {
  int added=0,updated=0;
  for(CatalogDocument.Item item:document.items) { if(entry(item.id,item.movie)==null) added++; else updated++; }
  return new int[]{added,updated};
 }
 void importLocal(CatalogDocument document) { db.runInTransaction(() -> apply(document,true,"local",0)); }
 void save(CatalogDocument.Item item) {
  // Validate with exactly the same constraints used by imports before any transaction.
  try {
   CatalogDocument d=new CatalogDocument(); d.items.add(item);
   CatalogDocument valid=CatalogDocument.parse(d.toJson(),item.movie);
   importLocal(valid);
  } catch(Exception e) { throw new IllegalArgumentException("Revisa el nombre, el grupo, el modo y las URLs.",e); }
 }
 Entry entry(String id,boolean movie) { return movie?dao.movie(id):dao.channel(id); }
 private int apply(CatalogDocument document,boolean local,String source,long version) {
  int conflicts=0,order=0;
  if(!local) { dao.hideRemoteChannels(source); dao.hideRemoteMovies(source); }
  for(CatalogDocument.Item item:document.items) {
   Entry old=local?entry(item.id,item.movie):(item.movie?dao.remoteMovie(source,item.id):dao.remoteChannel(source,item.id));
   if(!local&&old!=null&&old.localEdited) { conflicts++; continue; }
   String id=old!=null?old.id:item.id;
   if(old==null && !local && entry(id,item.movie)!=null) id="r"+CatalogDocument.key(source+"\n"+item.id);
   Entry value=old!=null?old:(item.movie?new MovieEntity():new ChannelEntity());
   value.id=id; value.name=item.name; value.description=item.description;
   GroupEntity group=new GroupEntity(); group.id="g"+CatalogDocument.key(item.group); group.name=item.group; dao.putGroup(group);
   String rid=(item.movie?"movie:":"channel:")+id;
   ResolverEntity resolver=dao.resolver(rid);
   if(resolver==null||!resolver.sourceUrl.equals(item.url)||!resolver.mode.equals(item.mode)) resolver=new ResolverEntity();
   resolver.id=rid; resolver.mode=item.mode; resolver.sourceUrl=item.url; resolver.siteUrl=item.siteUrl; dao.putResolver(resolver);
   value.groupId=group.id; value.resolverId=rid; value.deleted=false; value.enabled=true; value.sortOrder=order++;
   if(old==null) { value.sourceKey=source; value.remoteId=item.id; }
   value.localEdited=local; value.remoteVersion=version;
   if(item.movie) dao.putMovie((MovieEntity)value); else dao.putChannel((ChannelEntity)value);
  }
  dao.deleteOrphanResolvers();
  return conflicts;
 }
 String sync(String source,CatalogDocument document) {
  if(document.version<1||!document.errors.isEmpty()||document.duplicates>0) throw new IllegalArgumentException("La sincronización necesita JSON con version positiva y sin entradas inválidas o duplicadas.");
  final String[] message={""};
  db.runInTransaction(() -> {
   String key="version:"+CatalogDocument.key(source); String previous=dao.metadata(key);
   long current=previous==null?0:Long.parseLong(previous);
   if(document.version<current) throw new IllegalArgumentException("Se rechazó una versión remota anterior.");
   if(document.version==current) { message[0]="El catálogo ya está actualizado (versión "+current+")."; return; }
   int protectedCount=apply(document,false,source,document.version);
   dao.putMetadata(new Metadata(key,Long.toString(document.version)));
   dao.putMetadata(new Metadata("last_sync:"+CatalogDocument.key(source),Long.toString(System.currentTimeMillis())));
   message[0]="Catálogo actualizado a versión "+document.version+". Ediciones locales protegidas: "+protectedCount+".";
  });
  return message[0];
 }
 void favorite(CatalogItem item,boolean value) { if(item.movie) dao.favoriteMovie(item.id,value); else dao.favoriteChannel(item.id,value); }
 void result(CatalogItem item,int result) { if(item.movie) dao.resultMovie(item.id,result); else dao.resultChannel(item.id,result); }
 void delete(CatalogItem item) { if(item.movie) dao.deleteMovie(item.id); else dao.deleteChannel(item.id); }
}
