package cl.mitv.app;
import java.text.Normalizer;
import java.util.Locale;

final class CatalogSearch {
 private static String normalized(String value) {
  return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD)
   .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
 }
 static boolean matches(String name,String query) { return normalized(name).contains(normalized(query)); }
}
