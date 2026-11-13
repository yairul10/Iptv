package cl.mitv.app;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import cl.mitv.app.data.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CatalogRepository {
 interface Task<T> { T run() throws Exception; }
 interface Callback<T> { void done(T result,Exception error); }
 private static volatile CatalogRepository instance;
 final ExecutorService io=Executors.newSingleThreadExecutor();
 final CatalogStore store;
 private final Context app;
 private final Handler main=new Handler(Looper.getMainLooper());
 private CatalogRepository(Context context) { app=context.getApplicationContext(); store=new CatalogStore(AppDatabase.get(app)); }
 static CatalogRepository get(Context context) {
  if(instance==null) synchronized(CatalogRepository.class) { if(instance==null) instance=new CatalogRepository(context); }
  return instance;
 }
 LiveData<List<CatalogItem>> observe() { return store.dao.observeCatalog(); }
 <T> void submit(Task<T> task,Callback<T> callback) {
  io.execute(() -> { T value=null; Exception failure=null; try { value=task.run(); } catch(Exception e) { failure=e; Diagnostics.record(app,"ERROR","catálogo","Operación no completada: "+e.getClass().getSimpleName()); }
   final T result=value; final Exception error=failure; if(callback!=null) main.post(() -> callback.done(result,error));
  });
 }
 void initialize(Callback<Void> callback) {
  submit(() -> { store.initialize(() -> {
   try(InputStream in=app.getAssets().open("channels.json")) { return read(in,CatalogDocument.MAX_BYTES); }
  },app.getSharedPreferences("mi_tv",Context.MODE_PRIVATE)); Diagnostics.record(app,"INFO","base","Catálogo Room preparado."); return null; },callback);
 }
 String syncNow() throws Exception {
  String url=store.dao.metadata("remote_url"); if(url==null) url=CatalogStore.DEFAULT_REMOTE;
  if(!NavigationPolicy.isPublicHttps(url)) throw new IllegalArgumentException("La sincronización remota requiere una URL HTTPS.");
  String source=NetworkText.get(url,CatalogDocument.MAX_BYTES);
  CatalogDocument doc=CatalogDocument.parseRemote(source);
  String result=store.sync(url,doc); Diagnostics.record(app,"INFO","sincronización",result); return result;
 }
 static String read(InputStream in,int limit) throws IOException {
  if(in==null) throw new IOException("No se pudo abrir el archivo.");
  try(ByteArrayOutputStream out=new ByteArrayOutputStream()) {
   byte[] buffer=new byte[4096]; int n;
   while((n=in.read(buffer))!=-1) { if(out.size()+n>limit) throw new IOException("Máximo 1 MB."); out.write(buffer,0,n); }
   return out.toString(StandardCharsets.UTF_8.name());
  }
 }
}
