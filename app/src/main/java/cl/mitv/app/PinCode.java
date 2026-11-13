package cl.mitv.app;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
final class PinCode {
 static boolean valid(String pin) { return pin!=null&&pin.matches("[0-9]{4,8}"); }
 static String encode(String pin) {
  if(!valid(pin)) throw new IllegalArgumentException("El PIN debe tener de 4 a 8 dígitos.");
  byte[] salt=new byte[24]; new SecureRandom().nextBytes(salt); return hex(salt)+":"+hex(derive(pin,salt));
 }
 static boolean verify(String pin,String record) {
  if(!valid(pin)) return false;
  try { String[] fields=record.split(":"); return fields.length==2&&MessageDigest.isEqual(unhex(fields[1]),derive(pin,unhex(fields[0]))); } catch(Exception e) { return false; }
 }
 private static byte[] derive(String pin,byte[] salt) {
  PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,100000,256);
  try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded(); }
  catch(Exception e) { throw new IllegalStateException("No se pudo guardar el PIN."); }
  finally { spec.clearPassword(); }
 }
 private static String hex(byte[] bytes) { StringBuilder s=new StringBuilder(); for(byte b:bytes) s.append(String.format(java.util.Locale.ROOT,"%02x",b&255)); return s.toString(); }
 private static byte[] unhex(String s) { if(s.length()%2!=0||s.length()>256) throw new IllegalArgumentException(); byte[] out=new byte[s.length()/2]; for(int i=0;i<out.length;i++) out[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16); return out; }
}
