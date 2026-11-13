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
 static final int MAX_REDIRECTS = 3, LOG_LIMIT = 40, SNIFF_BYTES = 128;
 static final String MANIFEST = "#EXTM3U";

 /** Resultado de una sonda: código HTTP y si el cuerpo pareció un manifiesto HLS. */
 static final class Probe {
  final int code; final boolean manifest;
  Probe(int code, boolean manifest) { this.code = code; this.manifest = manifest; }
 }

 public UrlHealthWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }

 static void schedule(Context context) {
  Constraints limits = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build();
  WorkManager.getInstance(context).enqueueUniquePeriodicWork("mitv-url-health", ExistingPeriodicWorkPolicy.KEEP,
    new PeriodicWorkRequest.Builder(UrlHealthWorker.class, 12, TimeUnit.HOURS).setConstraints(limits).build());
 }

 static void checkNow(Context context) {
  WorkManager.getInstance(context).enqueueUniqueWork("mitv-url-health-now", ExistingWorkPolicy.KEEP,
    new OneTimeWorkRequest.Builder(UrlHealthWorker.class).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build());
 }

 @NonNull @Override public Result doWork() {
  Context context = getApplicationContext();
  CatalogDao dao = AppDatabase.get(context).catalog();
  if (!"1".equals(dao.metadata("initialized"))) return Result.retry();
  List<ResolverEntity> batch = dao.healthBatch();
  Diagnostics.record(context, "INFO", "salud", "Comprobación de " + batch.size() + " URLs.");
  ExecutorService probes = Executors.newFixedThreadPool(4);
  List<String> notes = Collections.synchronizedList(new ArrayList<>());
  int[] counts = new int[3]; // 0 up, 1 unknown, 2 down
  try {
   List<Callable<Void>> tasks = new ArrayList<>();
   for (ResolverEntity resolver : batch) tasks.add(() -> {
    if (isStopped()) return null;
    Probe probe = probe(resolver.sourceUrl);
    int failures = HealthPolicy.failures(probe.code, resolver.failures, probe.manifest);
    String health = HealthPolicy.status(probe.code, failures, probe.manifest);
    if (isStopped() || Thread.currentThread().isInterrupted()) return null;
    dao.setHealth(resolver.id, resolver.sourceUrl, health, System.currentTimeMillis(), failures, probe.code);
    synchronized (counts) { counts["UP".equals(health) ? 0 : "DOWN".equals(health) ? 2 : 1]++; }
    if (!"UP".equals(health) && notes.size() < LOG_LIMIT) notes.add(health + " · HTTP " + probe.code + " · " + resolver.sourceUrl);
    return null;
   });
   probes.invokeAll(tasks, 7, TimeUnit.MINUTES);
   for (String note : notes) Diagnostics.record(context, "WARN", "salud", note);
   Diagnostics.record(context, "INFO", "salud", "Resultado: " + counts[0] + " vivas, " + counts[1] + " sin confirmar, " + counts[2] + " caídas.");
   return isStopped() ? Result.retry() : Result.success();
  } catch (InterruptedException e) { Thread.currentThread().interrupt(); return Result.retry(); }
  catch (Exception e) { return Result.retry(); }
  finally { probes.shutdownNow(); }
 }

 /** Solo la extensión decide si el cuerpo debe ser un manifiesto. */
 static boolean needsManifest(String address) {
  try {
   String path = new URL(address).getPath();
   return path != null && path.toLowerCase(Locale.ROOT).endsWith(".m3u8");
  } catch (Exception e) { return false; }
 }

 static Probe probe(String address) {
  try {
   URL url = new URL(address);
   boolean useGet = true, ranged = true;
   boolean requireManifest = needsManifest(address);
   Set<String> visited = new HashSet<>();
   long deadline = System.currentTimeMillis() + 20000;
   for (int hops = 0; hops <= MAX_REDIRECTS && !Thread.currentThread().isInterrupted() && System.currentTimeMillis() < deadline; hops++) {
    if (!CatalogDocument.validUrl(url.toString())) return new Probe(-1, false);
    if (!visited.add((useGet ? "GET" : "HEAD") + ranged + url.toString())) return new Probe(-1, false);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setConnectTimeout(3500); connection.setReadTimeout(3500);
    connection.setInstanceFollowRedirects(false);
    connection.setRequestMethod(useGet ? "GET" : "HEAD");
    connection.setRequestProperty("User-Agent", PlaybackSettings.current.userAgent.isEmpty() ? "MiTV/0.5.0" : PlaybackSettings.current.userAgent);
    connection.setRequestProperty("Accept", "application/vnd.apple.mpegurl,application/x-mpegURL,video/*,*/*");
    connection.setRequestProperty("Accept-Encoding", "identity");
    for (Map.Entry<String, String> header : PlaybackSettings.current.headers(url.toString()).entrySet()) connection.setRequestProperty(header.getKey(), header.getValue());
    if (useGet && ranged) connection.setRequestProperty("Range", "bytes=0-" + (SNIFF_BYTES - 1));
    try {
     int code = connection.getResponseCode();
     if ((code == 405 || code == 501) && useGet) { useGet = false; hops--; continue; }
     if (code == 416 && useGet && ranged) { ranged = false; hops--; continue; }
     if (code >= 300 && code < 400 && code != 304) {
      String location = connection.getHeaderField("Location");
      if (location == null) return new Probe(code, false);
      url = new URL(url, location); continue;
     }
     boolean manifest = !(requireManifest || needsManifest(url.toString()));
     if (useGet && code >= 200 && code < 300) {
      try (InputStream in = connection.getInputStream()) {
       byte[] buffer = new byte[SNIFF_BYTES];
       int read = 0, step;
       while (read < buffer.length && (step = in.read(buffer, read, buffer.length - read)) > 0) read += step;
       String head = new String(buffer, 0, Math.max(read, 0), java.nio.charset.StandardCharsets.UTF_8).trim();
       if (head.startsWith("\uFEFF")) head = head.substring(1).trim();
       manifest = !(requireManifest || needsManifest(url.toString())) || head.startsWith(MANIFEST);
      } catch (Exception ignored) { }
     }
     return new Probe(code, manifest);
    } finally { connection.disconnect(); }
   }
  } catch (Exception ignored) { }
  return new Probe(-1, false);
 }
}
