package cl.mitv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;
import org.json.JSONObject;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;

final class AppUpdates {
    private static final String MANIFEST = "https://github.com/yairul10/Iptv/releases/latest/download/update.json";
    private boolean checking;

    void check(Activity activity,ExecutorService executor) { check(activity,executor,true); }
    void check(Activity activity, ExecutorService executor,boolean manual) {
        android.content.SharedPreferences preferences=SettingsStore.prefs(activity);
        if(!manual && System.currentTimeMillis()-preferences.getLong("update_checked",0)<24*60*60*1000L) return;
        if(checking) return;
        checking = true;
        if(manual) Toast.makeText(activity,"Buscando una actualización…",Toast.LENGTH_SHORT).show();
        Diagnostics.record(activity,"INFO","versión","Comprobando JSON remoto de actualización.");
        executor.execute(() -> {
            try {
                JSONObject update = new JSONObject(NetworkText.get(MANIFEST,32768));
                long offered = update.getLong("versionCode");
                PackageManager pm = activity.getPackageManager();
                PackageInfo current;
                Signature[] signatures;
                if(Build.VERSION.SDK_INT >= 28) {
                    current = pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
                    signatures = current.signingInfo.getApkContentsSigners();
                } else {
                    current = pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNATURES);
                    signatures = current.signatures;
                }
                long installed = Build.VERSION.SDK_INT >= 28 ? current.getLongVersionCode() : current.versionCode;
                StringBuilder digest = new StringBuilder();
                if(signatures == null || signatures.length != 1) throw new IllegalStateException("Unexpected signature");
                for(byte b : MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()))
                    digest.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
                String url = update.getString("apkUrl");
                if(!UpdatePolicy.compatible(installed,offered,current.packageName,update.getString("packageName"),
                    digest.toString(),update.getString("certificateSha256"),url) && offered > installed)
                    throw new IllegalStateException("Incompatible update");
                String version = update.getString("versionName");
                if(version.length()>80) throw new IllegalArgumentException("Invalid version label");
                preferences.edit().putLong("update_checked",System.currentTimeMillis()).apply();
                Diagnostics.record(activity,"INFO","versión",offered>installed?"Actualización compatible disponible.":"La app está al día.");
                activity.runOnUiThread(() -> {
                    checking = false;
                    if(activity.isFinishing() || activity.isDestroyed()) return;
                    if(offered <= installed) {
                        if(manual) Toast.makeText(activity,"Ya tienes la versión más reciente.",Toast.LENGTH_LONG).show(); return;
                    }
                    if(!manual && (preferences.getLong("update_prompted",0)>=offered || !activity.getWindow().getDecorView().hasWindowFocus())) return;
                    preferences.edit().putLong("update_prompted",offered).apply();
                    new AlertDialog.Builder(activity).setTitle("Mi TV " + version + " disponible")
                        .setMessage("Se descargará el APK desde GitHub. Ábrelo al terminar y elige Actualizar. Android te pedirá confirmar la instalación. Tus favoritos y resultados se conservan.")
                        .setPositiveButton("Descargar",(d,w) -> {
                            try { activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
                            catch(ActivityNotFoundException e) { Toast.makeText(activity,"Necesitas un navegador para descargar el APK.",Toast.LENGTH_LONG).show(); }
                        }).setNegativeButton("Más tarde",null).show();
                });
            } catch(Exception e) {
                Diagnostics.record(activity,"WARN","versión","No se pudo comprobar la actualización: "+e.getClass().getSimpleName());
                activity.runOnUiThread(() -> {
                    checking = false;
                    if(manual && !activity.isFinishing() && !activity.isDestroyed())
                        new AlertDialog.Builder(activity).setTitle("No se pudo comprobar la actualización")
                            .setMessage("Comprueba tu conexión e inténtalo más tarde. No se ofrecerá una actualización con otra firma o incompatible con esta instalación.")
                            .setPositiveButton("Entendido",null).show();
                });
            }
        });
    }
}
