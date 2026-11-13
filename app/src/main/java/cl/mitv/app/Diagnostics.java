package cl.mitv.app;
import android.content.Context;
import cl.mitv.app.data.*;
import java.util.concurrent.*;
final class Diagnostics {
 private static final ExecutorService io=Executors.newSingleThreadExecutor();
 static void record(Context context,String level,String category,String message) {
  Context app=context.getApplicationContext(); String safe=DiagnosticText.clean(message);
  io.execute(() -> { try {
   DiagnosticEvent event=new DiagnosticEvent(); event.timestamp=System.currentTimeMillis(); event.level=DiagnosticText.clean(level); event.category=DiagnosticText.clean(category); event.message=safe;
   AppDatabase.get(app).diagnostics().record(event);
  } catch(Exception ignored) { /* Logging must not crash playback or recurse into itself. */ } });
 }
}
