package cl.mitv.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.*;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.*;

/** DownloadManager continues the transfer when this screen is closed or Android restarts it. */
public final class UpdateDownloadActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Runnable poll=this::pollDownload;
    private DownloadManager downloads;
    private SharedPreferences prefs;
    private UpdatePackage update;
    private TextView message;
    private ProgressBar progress;
    private Button install,cancel;
    private boolean resumed,verifying,verified;
    static SharedPreferences preferences(Context context) { return context.getSharedPreferences("update_download",Context.MODE_PRIVATE); }
    static boolean pending(Context context) {
        SharedPreferences saved=preferences(context); long id=saved.getLong("id",0); if(id<=0) return false;
        try {
            long offered=new org.json.JSONObject(saved.getString("metadata","{}")).getLong("versionCode");
            if(offered<=BuildConfig.VERSION_CODE) {
                DownloadManager manager=(DownloadManager)context.getSystemService(DOWNLOAD_SERVICE); if(manager!=null) manager.remove(id);
                saved.edit().clear().apply(); return false;
            }
        } catch(Exception ignored) { /* Let the update screen report invalid saved metadata. */ }
        return true;
    }
    static void open(Activity activity,String json) { activity.startActivity(new Intent(activity,UpdateDownloadActivity.class).putExtra("update",json)); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); prefs=preferences(this); downloads=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        ScrollView scroll=new ScrollView(this); LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        int pad=Math.round(20*getResources().getDisplayMetrics().density); page.setPadding(pad,pad,pad,pad); page.setBackgroundColor(Color.rgb(11,18,32)); scroll.addView(page); setContentView(scroll);
        TextView heading=new TextView(this); heading.setText("Actualizar Popes TV"); heading.setTextSize(26); heading.setTextColor(Color.WHITE); page.addView(heading);
        message=new TextView(this); message.setTextSize(17); message.setTextColor(Color.LTGRAY); message.setPadding(0,pad,0,pad); page.addView(message);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); page.addView(progress,new LinearLayout.LayoutParams(-1,pad));
        install=button(page,"Instalar actualización",this::installOrRequestPermission); install.setVisibility(View.GONE);
        cancel=button(page,"Cancelar descarga",() -> { clearDownload(); message.setText("Descarga cancelada. Puedes volver a buscar la actualización desde ajustes."); progress.setVisibility(View.GONE); cancel.setVisibility(View.GONE); install.setVisibility(View.GONE); });
        button(page,"Volver a canales",this::finish);
        try {
            String source=getIntent().getStringExtra("update");
            if(source==null) source=prefs.getString("metadata",null);
            if(source==null) throw new IllegalStateException("No pending download");
            update=new UpdatePackage(source);
            PackageInfo current=getPackageManager().getPackageInfo(getPackageName(),UpdatePackage.signatureFlags());
            if(update.code<=UpdatePackage.code(current)) { clearDownload(); message.setText("Esta actualización ya está instalada. Puedes volver a los canales."); progress.setVisibility(View.GONE); cancel.setVisibility(View.GONE); return; }
            update.requireCompatible(current);
            if(prefs.getLong("id",0)>0 && !source.equals(prefs.getString("metadata",null))) clearDownload();
            if(prefs.getLong("id",0)==0) beginDownload();
            message.setText("Descargando Popes TV "+update.version+" desde GitHub…\nPuedes volver a los canales: la descarga continuará.");
        } catch(Exception e) { fail("No se pudo preparar la actualización. Vuelve a buscarla desde ajustes.",e); }
    }
    private Button button(LinearLayout page,String label,Runnable action) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); page.addView(b,new LinearLayout.LayoutParams(-1,-2)); return b;
    }
    private File apkFile() {
        File base=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if(base==null) throw new IllegalStateException("Storage unavailable");
        return new File(new File(base,"updates"),"Popes-TV-"+update.code+".apk");
    }
    private void beginDownload() throws Exception {
        File file=apkFile(); File directory=file.getParentFile();
        if(!directory.isDirectory()&&!directory.mkdirs()) throw new IllegalStateException("Storage unavailable");
        if(file.exists()&&!file.delete()) throw new IllegalStateException("Cannot replace old download");
        DownloadManager.Request request=new DownloadManager.Request(Uri.parse(update.url))
            .setTitle("Popes TV "+update.version).setDescription("Actualización de la app desde GitHub")
            .setMimeType("application/vnd.android.package-archive").setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationUri(Uri.fromFile(file));
        long id=downloads.enqueue(request);
        prefs.edit().putLong("id",id).putString("metadata",update.json).putBoolean("install_launched",false).putBoolean("awaiting_permission",false).apply();
    }
    private void pollDownload() {
        if(!resumed || verifying || update==null || prefs.getLong("id",0)==0) return;
        try(Cursor row=downloads.query(new DownloadManager.Query().setFilterById(prefs.getLong("id",0)))) {
            if(row==null||!row.moveToFirst()) throw new IllegalStateException("Download missing");
            int state=row.getInt(row.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if(state==DownloadManager.STATUS_FAILED) throw new IllegalStateException("Download failed: "+row.getInt(row.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
            if(state==DownloadManager.STATUS_SUCCESSFUL) { verifyDownload(); return; }
            long total=row.getLong(row.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),done=row.getLong(row.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            progress.setIndeterminate(total<=0); if(total>0) progress.setProgress((int)Math.min(100,done*100/total));
            message.setText((state==DownloadManager.STATUS_PAUSED?"Esperando conexión para continuar":"Descargando Popes TV "+update.version)+"\n"+String.format(Locale.ROOT,"%.1f MB",Math.max(0,done)/1000000d)+(total>0?String.format(Locale.ROOT," de %.1f MB",total/1000000d):"")+"\nPuedes volver a los canales sin cancelar la descarga.");
            handler.postDelayed(poll,800);
        } catch(Exception e) { fail("No se pudo completar la descarga. Comprueba la conexión y el espacio disponible; vuelve a buscar la actualización para reintentar.",e); }
    }
    private void verifyDownload() {
        if(verified) { showReady(); return; }
        final long verificationId=prefs.getLong("id",0);
        verifying=true; progress.setIndeterminate(true); message.setText("Descarga completa. Comprobando el archivo…");
        worker.execute(() -> {
            Exception failure=null;
            try {
                File apk=apkFile(); update.requireHash(apk);
                PackageInfo current=getPackageManager().getPackageInfo(getPackageName(),UpdatePackage.signatureFlags());
                update.requireArchive(current,getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),UpdatePackage.signatureFlags()));
            } catch(Exception e) { failure=e; }
            final Exception error=failure;
            runOnUiThread(() -> {
                verifying=false; if(isFinishing()||isDestroyed() || prefs.getLong("id",0)!=verificationId) return;
                if(error!=null) { fail("El archivo descargado no pasó la comprobación. No se instalará. Vuelve a buscar la actualización para descargarlo de nuevo.",error); return; }
                verified=true;
                if(resumed) {
                    showReady();
                    if(!prefs.getBoolean("install_launched",false)) installOrRequestPermission();
                }
            });
        });
    }
    private void showReady() {
        handler.removeCallbacks(poll); progress.setIndeterminate(false); progress.setProgress(100);
        message.setText("Popes TV "+update.version+" está lista. Android te pedirá confirmar Actualizar. Tus canales, favoritos y ajustes se conservan.");
        install.setVisibility(View.VISIBLE); cancel.setText("Eliminar descarga"); install.requestFocus();
    }
    private void installOrRequestPermission() {
        if(!verified || update==null) return;
        if(Build.VERSION.SDK_INT>=26 && !getPackageManager().canRequestPackageInstalls()) {
            message.setText("Para actualizar desde la app, permite que Popes TV instale aplicaciones. Se abrirán los ajustes de Android; activa el permiso y vuelve aquí.");
            install.setText("Permitir e instalar");
            install.setOnClickListener(v -> {
                try {
                    prefs.edit().putBoolean("awaiting_permission",true).apply();
                    startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));
                } catch(ActivityNotFoundException e) {
                    prefs.edit().putBoolean("awaiting_permission",false).apply();
                    try { startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)); }
                    catch(ActivityNotFoundException unavailable) { message.setText("Abre los ajustes de seguridad de Android y permite instalar aplicaciones desde Popes TV. Después vuelve y pulsa Instalar."); }
                    install.setText("Instalar actualización"); install.setOnClickListener(w -> installOrRequestPermission());
                }
            }); return;
        }
        try {
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",apkFile());
            Intent installer=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installer.setClipData(ClipData.newUri(getContentResolver(),"Actualización Popes TV",uri));
            prefs.edit().putBoolean("install_launched",true).putBoolean("awaiting_permission",false).apply();
            startActivity(installer);
        } catch(ActivityNotFoundException e) { message.setText("Este equipo no tiene un instalador de APK disponible. El archivo quedó descargado; revisa los ajustes del TV Box."); }
        catch(Exception e) { message.setText("No se pudo abrir el instalador. Pulsa Instalar actualización para reintentar."); }
    }
    private void clearDownload() {
        handler.removeCallbacks(poll); long id=prefs.getLong("id",0); if(id>0 && downloads!=null) downloads.remove(id);
        prefs.edit().clear().apply(); verified=false;
    }
    private void fail(String text,Exception error) {
        clearDownload(); message.setText(text); progress.setVisibility(View.GONE); install.setVisibility(View.GONE); cancel.setVisibility(View.GONE);
        Diagnostics.record(this,"WARN","actualización","Actualización no completada: "+error.getClass().getSimpleName());
    }
    @Override protected void onResume() {
        super.onResume(); resumed=true;
        if(update==null || prefs.getLong("id",0)==0) return;
        if(prefs.getBoolean("awaiting_permission",false)) {
            prefs.edit().putBoolean("awaiting_permission",false).apply();
            install.setText("Instalar actualización"); install.setOnClickListener(v -> installOrRequestPermission());
            if(verified) { showReady(); if(Build.VERSION.SDK_INT<26 || getPackageManager().canRequestPackageInstalls()) installOrRequestPermission(); return; }
        }
        if(verified) showReady(); else pollDownload();
    }
    @Override protected void onPause() { resumed=false; handler.removeCallbacks(poll); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacks(poll); worker.shutdownNow(); super.onDestroy(); }
}
