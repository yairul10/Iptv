package cl.mitv.app;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import cl.mitv.app.data.*;
import java.net.*;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

public final class UrlHealthWorker extends Worker {
 public UrlHealthWorker(@NonNull Context context,@NonNull WorkerParameters parameters) { super(context,parameters); }
 static void schedule(Context context) {
  Constraints limits=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build();
  WorkManager.getInstance(context).enqueueUniquePeriodicWork("mitv-url-health",ExistingPeriodicWorkPolicy.KEEP,
    new PeriodicWorkRequest.Builder(UrlHealthWorker.class,12,TimeUnit.HOURS).setConstraints(limits).build());
 }
 static void checkNow(Context context) {
  WorkManager.getInstance(context).enqueueUniqueWork("mitv-url-health-now",ExistingWorkPolicy.KEEP,
    new OneTimeWorkRequest.Builder(UrlHealthWorker.class).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build());
 }
 @NonNull @Override public Result doWork() {
  CatalogDao dao=AppDatabase.get(getApplicationContext()).catalog();
  if(!"1".equals(dao.metadata("initialized"))) return Result.retry();
  List<ResolverEntity> batch=dao.healthBatch(); Diagnostics.record(getApplicationContext(),"INFO","salud","Comprobación de "+batch.size()+" URLs.");
  ExecutorService probes=Executors.newFixedThreadPool(4);
  try {
   List<Callable<Void>> tasks=new ArrayList<>();
   for(ResolverEntity resolver:batch) tasks.add(() -> {
    if(isStopped()) return null;
    int code=probe(resolver.sourceUrl); int failures=HealthPolicy.failures(code,resolver.failures);
    String health=HealthPolicy.status(code,failures);
    if(!isStopped()&&!Thread.currentThread().isInterrupted()) {
     dao.setHealth(resolver.id,resolver.sourceUrl,health,System.currentTimeMillis(),failures,code);
     Diagnostics.record(getApplicationContext(),"DOWN".equals(health)?"WARN":"INFO","salud",health+" · HTTP "+code+" · "+resolver.sourceUrl);
    }
    return null;
   });
   probes.invokeAll(tasks,7,TimeUnit.MINUTES);
   return isStopped()?Result.retry():Result.success();
  } catch(InterruptedException e) { Thread.currentThread().interrupt(); return Result.retry(); }
  catch(Exception e) { return Result.retry(); }
  finally { probes.shutdownNow(); }
 }
 static int probe(String address) {
  try {
   URL url=new URL(address); boolean useGet=false;
   long deadline=System.currentTimeMillis()+20000;
   for(int redirects=0;redirects<4&&!Thread.currentThread().isInterrupted()&&System.currentTimeMillis()<deadline;redirects++) {
    if(!CatalogDocument.validUrl(url.toString())) return -1;
    HttpURLConnection connection=(HttpURLConnection)url.openConnection();
    connection.setConnectTimeout(3500); connection.setReadTimeout(3500); connection.setInstanceFollowRedirects(false);
    connection.setRequestMethod(useGet?"GET":"HEAD"); connection.setRequestProperty("User-Agent",PlaybackSettings.current.userAgent.isEmpty()?"MiTV/0.5.0":PlaybackSettings.current.userAgent);
    for(java.util.Map.Entry<String,String> header:PlaybackSettings.current.headers(url.toString()).entrySet()) connection.setRequestProperty(header.getKey(),header.getValue());
    if(useGet) connection.setRequestProperty("Range","bytes=0-0");
    try {
     int code=connection.getResponseCode();
     if(code==405||code==501) { if(useGet) return code; useGet=true; redirects--; continue; }
     if(code>=300&&code<400&&code!=304) {
      String location=connection.getHeaderField("Location"); if(location==null) return code;
      url=new URL(url,location); continue;
     }
     // Never consume a live stream body: headers (or a one-byte GET) suffice for reachability.
     if(useGet&&code>=200&&code<300) try(InputStream in=connection.getInputStream()) { in.read(); }
     return code;
    } finally { connection.disconnect(); }
   }
  } catch(Exception ignored) { }
  return -1;
 }
}
