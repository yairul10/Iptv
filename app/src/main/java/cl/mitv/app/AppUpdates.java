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

    void check(Activity activity, ExecutorService executor) {
        if(checking) return;
        checking = true;
        Toast.makeText(activity,"Buscando una actualización…",Toast.LENGTH_SHORT).show();
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
                activity.runOnUiThread(() -> {
                    checking = false;
                    if(activity.isFinishing() || activity.isDestroyed()) return;
                    if(offered <= installed) {
                        Toast.makeText(activity,"Ya tienes la versión más reciente.",Toast.LENGTH_LONG).show(); return;
                    }
                    new AlertDialog.Builder(activity).setTitle("Mi TV " + version + " disponible")
                        .setMessage("Se descargará el APK desde GitHub. Ábrelo al terminar y elige Actualizar. Android te pedirá confirmar la instalación. Tus favoritos y resultados se conservan.")
                        .setPositiveButton("Descargar",(d,w) -> {
                            try { activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
                            catch(ActivityNotFoundException e) { Toast.makeText(activity,"Necesitas un navegador para descargar el APK.",Toast.LENGTH_LONG).show(); }
                        }).setNegativeButton("Más tarde",null).show();
                });
            } catch(Exception e) {
                activity.runOnUiThread(() -> {
                    checking = false;
                    if(!activity.isFinishing() && !activity.isDestroyed())
                        new AlertDialog.Builder(activity).setTitle("No se pudo comprobar la actualización")
                            .setMessage("Comprueba tu conexión e inténtalo más tarde. No se ofrecerá una actualización con otra firma o incompatible con esta instalación.")
                            .setPositiveButton("Entendido",null).show();
                });
            }
        });
    }
}
