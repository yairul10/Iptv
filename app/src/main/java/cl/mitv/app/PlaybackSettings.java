package cl.mitv.app;
import java.net.URI;
import java.util.*;
/** Immutable request/player configuration, safe for playback and worker threads. */
final class PlaybackSettings {
 static volatile PlaybackSettings current=new PlaybackSettings("",Collections.emptyMap(),0,1);
 final String userAgent;
 final Map<String,String> referers;
 final int qualityHeight,buffer;
 PlaybackSettings(String ua,Map<String,String> refs,int height,int buffer) {
  if(ua.length()>512||hasControl(ua)) throw new IllegalArgumentException("User-Agent no válido.");
  if(!Arrays.asList(0,480,720,1080).contains(height)||buffer<0||buffer>2) throw new IllegalArgumentException("Calidad o buffer no válidos.");
  userAgent=ua.trim(); referers=Collections.unmodifiableMap(new LinkedHashMap<>(refs)); qualityHeight=height; this.buffer=buffer;
 }
 private static boolean hasControl(String text) { for(char c:text.toCharArray()) if(c<32||c==127) return true; return false; }
 static Map<String,String> parseReferers(String source) {
  Map<String,String> result=new LinkedHashMap<>();
  for(String line:source.split("\\r?\\n")) {
   line=line.trim(); if(line.isEmpty()) continue; int equal=line.indexOf('=');
   if(equal<1) throw new IllegalArgumentException("Referer: usa dominio=https://página/ (una línea por dominio).");
   String domain=line.substring(0,equal).trim().toLowerCase(Locale.ROOT),value=line.substring(equal+1).trim();
   if(!domain.matches("[a-z0-9-]+(?:\\.[a-z0-9-]+)*")||!CatalogDocument.validUrl(value)||hasControl(value)) throw new IllegalArgumentException("Dominio o Referer no válido.");
   result.put(domain,value); if(result.size()>100) throw new IllegalArgumentException("Máximo 100 dominios.");
  }
  return result;
 }
 Map<String,String> headers(String address) {
  Map<String,String> headers=new LinkedHashMap<>();
  if(!userAgent.isEmpty()) headers.put("User-Agent",userAgent);
  try {
   String host=new URI(address).getHost(); if(host==null) return headers; host=host.toLowerCase(Locale.ROOT);
   String match=null;
   for(String domain:referers.keySet()) if((host.equals(domain)||host.endsWith("."+domain))&&(match==null||domain.length()>match.length())) match=domain;
   if(match!=null) headers.put("Referer",referers.get(match));
  } catch(Exception ignored) { }
  return headers;
 }
 int minBufferMs() { return new int[]{2000,15000,30000}[buffer]; }
 int maxBufferMs() { return new int[]{10000,30000,60000}[buffer]; }
 int startBufferMs() { return new int[]{1000,2500,3000}[buffer]; }
 int rebufferMs() { return new int[]{2000,5000,10000}[buffer]; }
}
