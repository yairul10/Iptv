package cl.mitv.app;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=27,manifest=Config.NONE)
public class UpdatePackageTest {
 private PackageInfo pkg(long code,String signature) {
  PackageInfo value=new PackageInfo(); value.packageName="cl.mitv.app.preview"; value.versionCode=(int)code; value.signatures=new Signature[]{new Signature(signature.getBytes(StandardCharsets.UTF_8))}; return value;
 }
 private JSONObject metadata() throws Exception {
  return new JSONObject().put("versionName","0.8.0").put("versionCode",80).put("packageName","cl.mitv.app.preview")
   .put("certificateSha256",UpdatePackage.certificate(pkg(79,"release")))
   .put("apkUrl","https://github.com/yairul10/Iptv/releases/download/v0.8.0-build.80/Mi-TV.apk")
   .put("apkSha256",UpdatePackage.hex(MessageDigest.getInstance("SHA-256").digest("apk".getBytes(StandardCharsets.UTF_8))));
 }
 private void rejects(UpdatePackage update,PackageInfo current,PackageInfo archive) throws Exception {
  try { update.requireArchive(current,archive); fail("Invalid APK must be rejected"); } catch(IllegalArgumentException expected) { }
 }
 @Test public void signedUpgradeAcceptedButWrongPackageVersionAndSignerRejected() throws Exception {
  UpdatePackage update=new UpdatePackage(metadata().toString()); PackageInfo current=pkg(79,"release");
  update.requireArchive(current,pkg(80,"release"));
  rejects(update,current,pkg(80,"other signer")); rejects(update,current,pkg(81,"release")); rejects(update,current,null);
  PackageInfo other=pkg(80,"release"); other.packageName="other.app"; rejects(update,current,other);
  rejects(update,pkg(80,"release"),pkg(80,"release"));
 }
 @Test public void downloadHashRejectsTruncatedOrChangedBytes() throws Exception {
  UpdatePackage update=new UpdatePackage(metadata().toString()); File file=File.createTempFile("update-",".apk");
  try {
   Files.write(file.toPath(),"apk".getBytes(StandardCharsets.UTF_8)); update.requireHash(file);
   Files.write(file.toPath(),"ap".getBytes(StandardCharsets.UTF_8));
   try { update.requireHash(file); fail("Partial download accepted"); } catch(IllegalArgumentException expected) { }
  } finally { file.delete(); }
 }
 @Test public void metadataRequiresTrustedReleaseUrlAndChecksum() throws Exception {
  JSONObject value=metadata(); value.put("apkUrl","https://example.com/Mi-TV.apk");
  try { new UpdatePackage(value.toString()); fail("Untrusted host accepted"); } catch(IllegalArgumentException expected) { }
  value=metadata(); value.put("apkSha256","invalid");
  try { new UpdatePackage(value.toString()); fail("Missing integrity metadata accepted"); } catch(IllegalArgumentException expected) { }
 }
}
