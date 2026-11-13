package cl.mitv.app;
import android.app.*;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class PinScreensTest {
 @Test public void bothAdminAndSettingsRequirePinBeforeOpeningAndCancelClosesThem() {
  SharedPreferences prefs=SettingsStore.prefs(RuntimeEnvironment.getApplication()); prefs.edit().putString("pin_hash",PinCode.encode("4321")).commit();
  try {
   for(Class<? extends Activity> type:new Class[]{CatalogManagerActivity.class,SettingsActivity.class}) {
    ActivityController<? extends Activity> screen=Robolectric.buildActivity(type).create();
    AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog(); assertNotNull(dialog); assertTrue(dialog.isShowing());
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); assertTrue(screen.get().isFinishing()); screen.destroy();
   }
  } finally { prefs.edit().clear().commit(); }
 }
 @Test public void cancelingInlinePinKeepsCurrentScreenOpenAndDoesNotAuthorize() {
  SharedPreferences prefs=SettingsStore.prefs(RuntimeEnvironment.getApplication()); prefs.edit().putString("pin_hash",PinCode.encode("4321")).commit();
  ActivityController<Activity> screen=Robolectric.buildActivity(Activity.class).create();
  final boolean[] allowed={false},canceled={false};
  try {
   PinGate.require(screen.get(),() -> allowed[0]=true,() -> canceled[0]=true);
   AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog(); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
   assertTrue(canceled[0]); assertFalse(allowed[0]); assertFalse(screen.get().isFinishing());
  } finally { screen.destroy(); prefs.edit().clear().commit(); }
 }
}
