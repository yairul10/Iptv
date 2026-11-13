package cl.mitv.app;
import android.app.*;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import java.util.*;
public final class SettingsActivity extends Activity {
 @Override public void onCreate(Bundle state) { super.onCreate(state); PinGate.require(this,this::showSettings); }
 private TextView label(LinearLayout page,String text) { TextView v=new TextView(this); v.setText(text); v.setTextColor(Color.WHITE); v.setPadding(4,14,4,6); page.addView(v); return v; }
 private EditText field(LinearLayout page,String value,boolean multiline) { EditText v=new EditText(this); v.setText(value); v.setTextColor(Color.WHITE); v.setSingleLine(!multiline); if(multiline) v.setMinLines(4); page.addView(v); return v; }
 private Spinner spinner(LinearLayout page,String[] values,int selection) { Spinner v=new Spinner(this); v.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values)); v.setSelection(selection); page.addView(v); return v; }
 private void showSettings() {
  SharedPreferences prefs=SettingsStore.prefs(this); PlaybackSettings current=PlaybackSettings.current;
  LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(24,20,24,24); page.setBackgroundColor(Color.rgb(11,18,32));
  ScrollView scroll=new ScrollView(this); scroll.addView(page); setContentView(scroll);
  TextView title=label(page,"Ajustes de Mi TV"); title.setTextSize(26);
  label(page,"User-Agent (vacío: predeterminado)"); EditText ua=field(page,current.userAgent,false);
  label(page,"Referer por dominio: una línea dominio=https://página/"); EditText referers=field(page,prefs.getString("referers",""),true);
  label(page,"Los dominios más específicos tienen prioridad. En páginas web se aplica a la navegación principal; los subrecursos los controla WebView.");
  label(page,"Calidad máxima · video directo"); int qualityIndex=0; int[] heights={0,480,720,1080}; for(int i=0;i<heights.length;i++) if(heights[i]==current.qualityHeight) qualityIndex=i;
  Spinner quality=spinner(page,new String[]{"Automática","Hasta 480p","Hasta 720p","Hasta 1080p"},qualityIndex);
  label(page,"Buffer · video directo"); Spinner buffer=spinner(page,new String[]{"Bajo · 2–10 s","Normal · 15–30 s","Alto · 30–60 s"},current.buffer);
  label(page,"Calidad y buffer se aplican al abrir el siguiente video directo. En reproductores web dependen del proveedor.");
  long remaining=SettingsStore.remaining(prefs.getLong("sleep_deadline",0),System.currentTimeMillis());
  label(page,remaining>0?"Temporizador activo: aproximadamente "+((remaining+59999)/60000)+" minutos.":"Temporizador desactivado.");
  CheckBox changeTimer=new CheckBox(this); changeTimer.setText("Cambiar temporizador al guardar"); page.addView(changeTimer);
  Spinner timer=spinner(page,new String[]{"Desactivar","15 minutos","30 minutos","60 minutos","90 minutos","120 minutos"},0); int[] minutes={0,15,30,60,90,120};
  label(page,"El temporizador cuenta desde que guardas y detiene la reproducción al finalizar.");
  label(page,prefs.getString("pin_hash","").isEmpty()?"Crear PIN (opcional)":"Cambiar PIN (deja vacío para conservarlo)");
  EditText pin=field(page,"",false); pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
  label(page,"Repetir nuevo PIN"); EditText confirm=field(page,"",false); confirm.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
  CheckBox removePin=new CheckBox(this); removePin.setText("Quitar PIN de administración"); page.addView(removePin);
  label(page,"El PIN protege administración y ajustes; no bloquea la reproducción. Se guarda como hash con sal, sin el PIN en texto.");
  Button save=new Button(this); save.setText("Guardar ajustes"); page.addView(save);
  Button back=new Button(this); back.setText("Volver"); page.addView(back); back.setOnClickListener(v -> finish());
  save.setOnClickListener(v -> {
   final PlaybackSettings candidate;
   String uaValue=ua.getText().toString().trim(),refValue=referers.getText().toString().trim(),pinValue=pin.getText().toString();
   try {
    candidate=new PlaybackSettings(uaValue,PlaybackSettings.parseReferers(refValue),heights[quality.getSelectedItemPosition()],buffer.getSelectedItemPosition());
    if(!pinValue.equals(confirm.getText().toString())) throw new IllegalArgumentException("Los PIN no coinciden.");
    if(!pinValue.isEmpty()&&!PinCode.valid(pinValue)) throw new IllegalArgumentException("El PIN debe tener de 4 a 8 dígitos.");
    if(removePin.isChecked()&&!pinValue.isEmpty()) throw new IllegalArgumentException("Elige cambiar o quitar el PIN.");
   } catch(Exception e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); return; }
   boolean remove=removePin.isChecked(),timerChanged=changeTimer.isChecked(); int timerMinutes=minutes[timer.getSelectedItemPosition()]; save.setEnabled(false);
   CatalogRepository.get(this).submit(() -> {
    String hash=pinValue.isEmpty()?null:PinCode.encode(pinValue);
    SharedPreferences.Editor editor=prefs.edit().putString("ua",candidate.userAgent).putString("referers",refValue).putInt("quality",candidate.qualityHeight).putInt("buffer",candidate.buffer);
    if(remove) editor.remove("pin_hash").remove("pin_attempts").remove("pin_locked_until");
    else if(hash!=null) editor.putString("pin_hash",hash).putInt("pin_attempts",0).remove("pin_locked_until");
    if(timerChanged) editor.putLong("sleep_deadline",timerMinutes==0?0:System.currentTimeMillis()+timerMinutes*60000L);
    if(!editor.commit()) throw new IllegalStateException("No se pudieron guardar los ajustes.");
    PlaybackSettings.current=candidate; return null;
   },(unused,error) -> {
    if(isFinishing()||isDestroyed()) return; save.setEnabled(true);
    if(error!=null) { Toast.makeText(this,"No se pudieron guardar los ajustes.",Toast.LENGTH_LONG).show(); return; }
    Diagnostics.record(this,"INFO","ajustes","Ajustes de reproducción guardados."); Toast.makeText(this,"Ajustes guardados.",Toast.LENGTH_SHORT).show(); finish();
   });
  });
 }
}
