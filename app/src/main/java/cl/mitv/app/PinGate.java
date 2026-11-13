package cl.mitv.app;
import android.app.*;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.*;
final class PinGate {
 static void require(Activity activity,Runnable allowed) {
  SharedPreferences prefs=SettingsStore.prefs(activity); String hash=prefs.getString("pin_hash","");
  if(hash.isEmpty()) { allowed.run(); return; }
  EditText input=new EditText(activity); input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD); input.setHint("PIN de administración");
  AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Acceso protegido").setMessage("Introduce el PIN para abrir administración o ajustes.").setView(input)
    .setPositiveButton("Entrar",null).setNegativeButton("Cancelar",(d,w) -> activity.finish()).create();
  dialog.setOnCancelListener(d -> activity.finish());
  dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
   long locked=prefs.getLong("pin_locked_until",0)-System.currentTimeMillis();
   if(locked>0) { input.setError("Espera "+((locked+999)/1000)+" segundos."); return; }
   String pin=input.getText().toString(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
   CatalogRepository.get(activity).submit(() -> PinCode.verify(pin,hash),(valid,error) -> {
    if(activity.isFinishing()||activity.isDestroyed()) return;
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
    if(error==null&&Boolean.TRUE.equals(valid)) {
     prefs.edit().putInt("pin_attempts",0).remove("pin_locked_until").apply(); dialog.dismiss(); allowed.run();
    } else {
     int failures=prefs.getInt("pin_attempts",0)+1;
     prefs.edit().putInt("pin_attempts",failures>=5?0:failures).putLong("pin_locked_until",failures>=5?System.currentTimeMillis()+60000:0).apply();
     input.setText(""); input.setError(failures>=5?"Espera un minuto antes de reintentar.":"PIN incorrecto.");
     Diagnostics.record(activity,"WARN","acceso","Intento de PIN incorrecto.");
    }
   });
  })); dialog.show();
 }
}
