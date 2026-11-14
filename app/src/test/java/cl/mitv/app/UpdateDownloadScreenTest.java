package cl.mitv.app;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Button;
import java.lang.reflect.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=27,manifest=Config.NONE)
public class UpdateDownloadScreenTest {
 private void set(Object value,String key,Object content) throws Exception { Field f=UpdateDownloadActivity.class.getDeclaredField(key); f.setAccessible(true); f.set(value,content); }
 @Test public void updateOpensInternalScreenWithoutBrowser() {
  Activity a=Robolectric.buildActivity(Activity.class).create().get();
  UpdateDownloadActivity.open(a,"metadata"); Intent intent=Shadows.shadowOf(a).getNextStartedActivity();
  assertEquals(UpdateDownloadActivity.class.getName(),intent.getComponent().getClassName()); assertEquals("metadata",intent.getStringExtra("update")); assertNull(intent.getData());
 }
 @Test public void pendingDownloadSurvivesScreenClosureAndInstalledVersionCleansItUp() throws Exception {
  Activity a=Robolectric.buildActivity(Activity.class).create().get();
  android.content.SharedPreferences prefs=UpdateDownloadActivity.preferences(a);
  try {
   prefs.edit().clear().putLong("id",123).putString("metadata",new JSONObject().put("versionCode",BuildConfig.VERSION_CODE+1).toString()).commit();
   assertTrue(UpdateDownloadActivity.pending(a));
   Activity other=Robolectric.buildActivity(Activity.class).create().get(); assertTrue(UpdateDownloadActivity.pending(other));
   prefs.edit().putString("metadata",new JSONObject().put("versionCode",BuildConfig.VERSION_CODE).toString()).commit();
   assertFalse(UpdateDownloadActivity.pending(other)); assertEquals(0,prefs.getLong("id",0));
  } finally { prefs.edit().clear().commit(); }
 }
 @Test public void installationPermissionOpensAndroidSettingsAndRemembersReturn() throws Exception {
  org.robolectric.android.controller.ActivityController<UpdateDownloadActivity> controller=Robolectric.buildActivity(UpdateDownloadActivity.class,new Intent().putExtra("update","{}"));
  UpdateDownloadActivity a=controller.create().start().resume().get();
  try {
   JSONObject json=new JSONObject().put("versionName","0.8.0").put("versionCode",9999).put("packageName",a.getPackageName()).put("certificateSha256",new String(new char[64]).replace('\0','a'))
    .put("apkUrl","https://github.com/yairul10/Iptv/releases/download/v0.8.0-build.9999/Mi-TV.apk").put("apkSha256",new String(new char[64]).replace('\0','b'));
   set(a,"update",new UpdatePackage(json.toString())); set(a,"verified",true);
   assertFalse(a.getPackageManager().canRequestPackageInstalls());
   Method method=UpdateDownloadActivity.class.getDeclaredMethod("installOrRequestPermission"); method.setAccessible(true); method.invoke(a);
   Field field=UpdateDownloadActivity.class.getDeclaredField("install"); field.setAccessible(true); Button button=(Button)field.get(a);
   assertEquals("Permitir e instalar",button.getText().toString()); button.performClick();
   Intent launched=Shadows.shadowOf(a).getNextStartedActivity(); assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,launched.getAction());
   assertEquals("package:"+a.getPackageName(),launched.getDataString()); assertTrue(UpdateDownloadActivity.preferences(a).getBoolean("awaiting_permission",false));
  } finally { controller.pause().stop().destroy(); UpdateDownloadActivity.preferences(a).edit().clear().commit(); }
 }
}
