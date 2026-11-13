package cl.mitv.app;
import android.app.Application;
public final class MiTvApplication extends Application {
 @Override public void onCreate() { super.onCreate(); SettingsStore.reload(this); Diagnostics.record(this,"INFO","inicio","Mi TV iniciada."); }
}
