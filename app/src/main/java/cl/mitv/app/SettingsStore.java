package cl.mitv.app;
import android.content.Context;
import android.content.SharedPreferences;
final class SettingsStore {
 static SharedPreferences prefs(Context context) { return context.getApplicationContext().getSharedPreferences("playback_settings",Context.MODE_PRIVATE); }
 static void reload(Context context) {
  SharedPreferences p=prefs(context);
  try { PlaybackSettings.current=new PlaybackSettings(p.getString("ua",""),PlaybackSettings.parseReferers(p.getString("referers","")),p.getInt("quality",0),p.getInt("buffer",1)); }
  catch(Exception e) { PlaybackSettings.current=new PlaybackSettings("",java.util.Collections.emptyMap(),0,1); }
 }
 static long remaining(long deadline,long now) { return Math.max(0,deadline-now); }
}
