package cl.mitv.app;
import java.net.URI;
import java.util.regex.*;
final class DiagnosticText {
 static String clean(String input) {
  if(input==null) return "";
  Matcher urls=Pattern.compile("(?i)https?://[^\\s]+" ).matcher(input); StringBuffer out=new StringBuffer();
  while(urls.find()) {
   String replacement="[URL oculta]";
   try { URI u=new URI(urls.group()); if(u.getHost()!=null) replacement=u.getScheme()+"://"+u.getHost()+"/[oculto]"; } catch(Exception ignored) { }
   urls.appendReplacement(out,Matcher.quoteReplacement(replacement));
  }
  urls.appendTail(out);
  String result=out.toString().replaceAll("(?i)\\b(authorization|cookie|referer)\\s*[:=]\\s*[^\\r\\n]*","$1=[oculto]").replaceAll("(?i)\\b(authorization|cookie|referer|password|passwd|token|access[_-]?token|refresh[_-]?token|session(?:id)?|signature|secret|api[_-]?key|pin)\\s*[:=]\\s*[^\\s,;]+","$1=[oculto]")
    .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*","Bearer [oculto]").replace('\r',' ').replace('\n',' ');
  return result.length()>600?result.substring(0,600):result;
 }
}
