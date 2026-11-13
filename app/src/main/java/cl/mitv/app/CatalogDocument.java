package cl.mitv.app;
import org.json.*;
import java.net.URI;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Portable import/export format. Parsing never writes to the database or opens media URLs. */
final class CatalogDocument {
 static final int MAX_BYTES=1048576, MAX_ITEMS=2000;
 static final String[] MODES={"WEB_PAGE","OFFICIAL_PLAYER","STREAM"};
 static final class Item {
  String id,name,group="Sin grupo",description="",url,siteUrl,mode="STREAM";
  boolean movie;
 }
 final List<Item> items=new ArrayList<>();
 final List<String> errors=new ArrayList<>();
 int duplicates;
 long version;
 static boolean validUrl(String value) {
  try {
   URI u=new URI(value); String h=u.getHost();
   return value.length()<=8192 && ("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme()))
     && h!=null && !h.isEmpty() && u.getUserInfo()==null && (u.getPort()==-1||u.getPort()>0);
  } catch(Exception e) { return false; }
 }
 static String key(String text) {
  try { StringBuilder out=new StringBuilder(); for(byte b:MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))) out.append(String.format(Locale.ROOT,"%02x",b&255)); return out.toString(); }
  catch(Exception e) { throw new IllegalStateException(e); }
 }
 static CatalogDocument parse(String source,boolean defaultMovie) throws JSONException { return parse(source,defaultMovie,false); }
 static CatalogDocument parseRemote(String source) throws JSONException {
  if(!source.trim().startsWith("{")) throw new IllegalArgumentException("La sincronización requiere JSON con version.");
  return parse(source,false,true);
 }
 private static CatalogDocument parse(String source,boolean defaultMovie,boolean allowEmpty) throws JSONException {
  if(source.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES) throw new IllegalArgumentException("El archivo supera 1 MB.");
  source=source.replaceFirst("^\\uFEFF", "").trim(); CatalogDocument doc=new CatalogDocument();
  if(source.startsWith("#EXTM3U")) doc.m3u(source,defaultMovie);
  else if(source.startsWith("[")||source.startsWith("{")) doc.json(source,defaultMovie);
  else throw new IllegalArgumentException("Se esperaba una lista M3U (#EXTM3U) o JSON.");
  Set<String> ids=new HashSet<>(); Iterator<Item> iterator=doc.items.iterator();
  while(iterator.hasNext()) { Item item=iterator.next(); if(!ids.add((item.movie?"movie:":"channel:")+item.id)) { iterator.remove(); doc.duplicates++; } }
  if(doc.items.isEmpty() && !allowEmpty) throw new IllegalArgumentException("No hay entradas válidas. " + (doc.errors.isEmpty()?"":doc.errors.get(0)));
  return doc;
 }
 private void accept(Item item,String label) {
  if(items.size()>=MAX_ITEMS) throw new IllegalArgumentException("Máximo 2000 entradas por importación.");
  try {
   if(item.name==null||item.name.trim().isEmpty()||item.name.length()>160) throw new IllegalArgumentException("nombre vacío o demasiado largo");
   item.name=item.name.trim();
   if(!validUrl(item.url)) throw new IllegalArgumentException("URL no válida (solo HTTP/HTTPS, sin usuario/contraseña)");
   if(item.siteUrl==null||item.siteUrl.isEmpty()) item.siteUrl=item.url;
   if(!validUrl(item.siteUrl)) throw new IllegalArgumentException("página oficial no válida");
   if(!Arrays.asList(MODES).contains(item.mode)) throw new IllegalArgumentException("modo no reconocido");
   if(item.group==null||item.group.trim().isEmpty()) item.group="Sin grupo";
   if(item.group.length()>80||item.description.length()>2000) throw new IllegalArgumentException("grupo o descripción demasiado largos");
   item.group=item.group.trim();
   if(item.id==null||item.id.isEmpty()) item.id="i"+key(item.name+"\n"+item.url);
   if(!item.id.matches("[A-Za-z0-9_.:-]{1,160}")) throw new IllegalArgumentException("identificador no válido");
   items.add(item);
  } catch(IllegalArgumentException e) { if(errors.size()<100) errors.add(label+": "+e.getMessage()); }
 }
 private void json(String source,boolean defaultMovie) throws JSONException {
  JSONTokener tokenizer=new JSONTokener(source); Object root=tokenizer.nextValue();
  if(tokenizer.nextClean()!=0) throw new IllegalArgumentException("Hay texto extra después del JSON.");
  if(root instanceof JSONArray) { array((JSONArray)root,defaultMovie,Collections.emptyMap(),Collections.emptyMap()); return; }
  if(!(root instanceof JSONObject)) throw new IllegalArgumentException("JSON no reconocido.");
  JSONObject object=(JSONObject)root;
  if(!object.has("channels")&&!object.has("movies")&&!object.has("items")) throw new IllegalArgumentException("Faltan channels o movies en el JSON.");
  if(object.has("schemaVersion")&&object.getInt("schemaVersion")!=1) throw new IllegalArgumentException("Versión de formato JSON no compatible.");
  version=object.optLong("version",0);
  Map<String,String> groups=new HashMap<>(); Map<String,JSONObject> resolvers=new HashMap<>();
  JSONArray gs=object.optJSONArray("groups"),rs=object.optJSONArray("resolvers");
  if(gs!=null) for(int i=0;i<gs.length();i++) { JSONObject g=gs.getJSONObject(i); groups.put(g.getString("id"),g.getString("name")); }
  if(rs!=null) for(int i=0;i<rs.length();i++) { JSONObject r=rs.getJSONObject(i); resolvers.put(r.getString("id"),r); }
  if(object.has("channels")) array(object.getJSONArray("channels"),false,groups,resolvers);
  if(object.has("movies")) array(object.getJSONArray("movies"),true,groups,resolvers);
  if(object.has("items")) array(object.getJSONArray("items"),defaultMovie,groups,resolvers);
 }
 private void array(JSONArray array,boolean movie,Map<String,String> groups,Map<String,JSONObject> resolvers) throws JSONException {
  if(array.length()>MAX_ITEMS) throw new IllegalArgumentException("Máximo 2000 entradas.");
  for(int i=0;i<array.length();i++) {
   try {
    JSONObject o=array.getJSONObject(i); Item item=new Item();
    if(o.has("kind")&&!"movie".equals(o.getString("kind"))&&!"channel".equals(o.getString("kind"))) throw new IllegalArgumentException("tipo no reconocido");
    item.movie=o.has("kind")?"movie".equals(o.getString("kind")):movie;
    item.id=o.optString("id",""); item.name=o.optString("name",o.optString("title",""));
    item.group=o.optString("group",o.optString("category",groups.getOrDefault(o.optString("groupId"),"Sin grupo")));
    item.description=o.optString("description","");
    JSONObject r=o.has("resolverId")?resolvers.get(o.getString("resolverId")):o;
    if(r==null) throw new IllegalArgumentException("resolver no encontrado");
    item.url=r.optString("url",r.optString("sourceUrl",r.optString("playerUrl","")));
    item.siteUrl=r.optString("siteUrl",item.url);
    item.mode=r.optString("mode",r.has("directPlayer")?(r.optBoolean("directPlayer")?"OFFICIAL_PLAYER":"WEB_PAGE"):"STREAM");
    accept(item,"Entrada "+(i+1));
   } catch(Exception e) { if(errors.size()<100) errors.add("Entrada "+(i+1)+": "+e.getMessage()); }
  }
 }
 private static int commaOutsideQuotes(String line) {
  boolean quoted=false; for(int i=0;i<line.length();i++) { char c=line.charAt(i); if(c=='"') quoted=!quoted; else if(c==','&&!quoted) return i; } return -1;
 }
 private void m3u(String source,boolean movie) {
  Item pending=null; int index=0;
  for(String line:source.split("\\r?\\n")) {
   line=line.trim(); if(line.isEmpty()) continue;
   if(line.startsWith("#EXTINF:")) {
    if(pending!=null && errors.size()<100) errors.add("Entrada sin URL: "+pending.name);
    pending=new Item(); pending.movie=movie; int comma=commaOutsideQuotes(line); pending.name=comma<0?"":line.substring(comma+1).trim();
    Matcher m=Pattern.compile("([A-Za-z0-9_-]+)=\"([^\"]*)\"").matcher(comma<0?line:line.substring(0,comma));
    while(m.find()) {
     String k=m.group(1),v=m.group(2);
     switch(k) { case "tvg-id": pending.id=v.matches("[A-Za-z0-9_.:-]{1,160}")?v:"i"+key(v); break; case "group-title": pending.group=v; break;
      case "x-mitv-mode": pending.mode=v; break; case "x-mitv-site": pending.siteUrl=v; break;
      case "x-mitv-kind": pending.movie="movie".equals(v); break; default: break; }
    }
   } else if(line.startsWith("#EXTGRP:")&&pending!=null) pending.group=line.substring(8).trim();
   else if(!line.startsWith("#")) {
    index++; if(pending==null) { pending=new Item(); pending.movie=movie; pending.name="Entrada "+index; }
    pending.url=line; accept(pending,"Entrada "+index); pending=null;
   }
  }
  if(pending!=null&&errors.size()<100) errors.add("Última entrada sin URL.");
 }
 static CatalogDocument fromRows(List<cl.mitv.app.data.CatalogItem> rows) {
  CatalogDocument doc=new CatalogDocument();
  for(cl.mitv.app.data.CatalogItem row:rows) { Item i=new Item(); i.id=row.id; i.name=row.name; i.group=row.category; i.description=row.description; i.url=row.playerUrl; i.siteUrl=row.siteUrl; i.mode=row.mode; i.movie=row.movie; doc.items.add(i); }
  return doc;
 }
 String toJson() throws JSONException {
  JSONObject root=new JSONObject(); root.put("schemaVersion",1); root.put("version",version>0?version:1);
  JSONArray channels=new JSONArray(),movies=new JSONArray(),groups=new JSONArray(),resolvers=new JSONArray(); Set<String> seen=new HashSet<>();
  for(Item i:items) {
   String gid="g"+key(i.group),rid=(i.movie?"movie:":"channel:")+i.id;
   if(seen.add(gid)) groups.put(new JSONObject().put("id",gid).put("name",i.group));
   resolvers.put(new JSONObject().put("id",rid).put("mode",i.mode).put("url",i.url).put("siteUrl",i.siteUrl));
   JSONObject o=new JSONObject().put("id",i.id).put("name",i.name).put("description",i.description).put("groupId",gid).put("resolverId",rid);
   (i.movie?movies:channels).put(o);
  }
  return root.put("groups",groups).put("resolvers",resolvers).put("channels",channels).put("movies",movies).toString(2);
 }
 private static String clean(String value) { return value.replace('\r',' ').replace('\n',' ').replace('"','\''); }
 String toM3u() {
  StringBuilder out=new StringBuilder("#EXTM3U\n");
  for(Item i:items) out.append("#EXTINF:-1 tvg-id=\"").append(clean(i.id)).append("\" group-title=\"").append(clean(i.group)).append("\" x-mitv-kind=\"").append(i.movie?"movie":"channel").append("\" x-mitv-mode=\"").append(i.mode).append("\" x-mitv-site=\"").append(clean(i.siteUrl)).append("\",").append(clean(i.name)).append('\n').append(i.url).append('\n');
  return out.toString();
 }
}
