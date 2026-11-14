package cl.mitv.app;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Metadata is checked again against the downloaded archive before offering installation. */
final class UpdatePackage {
    final long code;
    final String version,packageName,signer,url,sha256,json;
    UpdatePackage(String source) throws Exception {
        JSONObject value=new JSONObject(source);
        code=value.getLong("versionCode"); version=value.getString("versionName");
        packageName=value.getString("packageName"); signer=value.getString("certificateSha256");
        url=value.getString("apkUrl"); sha256=value.getString("apkSha256").toLowerCase(Locale.ROOT); json=source;
        if(code<1 || version.isEmpty() || version.length()>80 || !sha256.matches("[a-f0-9]{64}") || !UpdatePolicy.trustedApk(url))
            throw new IllegalArgumentException("Invalid update metadata");
    }
    static int signatureFlags() { return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES; }
    static long code(PackageInfo info) { return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode; }
    static String certificate(PackageInfo info) throws Exception {
        Signature[] values=Build.VERSION.SDK_INT>=28?(info.signingInfo==null?null:info.signingInfo.getApkContentsSigners()):info.signatures;
        if(values==null || values.length!=1) throw new IllegalArgumentException("Invalid signature");
        return hex(MessageDigest.getInstance("SHA-256").digest(values[0].toByteArray()));
    }
    void requireCompatible(PackageInfo current) throws Exception {
        if(!UpdatePolicy.compatible(code(current),code,current.packageName,packageName,certificate(current),signer,url))
            throw new IllegalArgumentException("Incompatible update");
    }
    void requireArchive(PackageInfo current,PackageInfo archive) throws Exception {
        requireCompatible(current);
        if(archive==null || code(archive)!=code || !packageName.equals(archive.packageName) || !signer.equalsIgnoreCase(certificate(archive)))
            throw new IllegalArgumentException("Incompatible APK");
    }
    static String hex(byte[] bytes) { StringBuilder out=new StringBuilder(); for(byte b:bytes) out.append(String.format(Locale.ROOT,"%02x",b&255)); return out.toString(); }
    void requireHash(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)) {
            byte[] buffer=new byte[65536]; int count;
            while((count=in.read(buffer))!=-1) { if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException(); digest.update(buffer,0,count); }
        }
        if(!sha256.equals(hex(digest.digest()))) throw new IllegalArgumentException("Incomplete or changed download");
    }
}
