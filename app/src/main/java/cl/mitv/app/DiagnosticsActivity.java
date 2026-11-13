package cl.mitv.app;
import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.widget.*;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import cl.mitv.app.data.*;
import java.text.SimpleDateFormat;
import java.util.*;
public final class DiagnosticsActivity extends Activity {
 private LiveData<List<DiagnosticEvent>> live;
 private Observer<List<DiagnosticEvent>> observer;
 private String text="Sin eventos.";
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(20,20,20,20); page.setBackgroundColor(Color.rgb(11,18,32));
  TextView heading=new TextView(this); heading.setText("Diagnóstico · últimos 200 eventos"); heading.setTextColor(Color.WHITE); heading.setTextSize(22); page.addView(heading);
  Button copy=new Button(this); copy.setText("Copiar diagnóstico"); page.addView(copy);
  Button back=new Button(this); back.setText("Volver"); page.addView(back); back.setOnClickListener(v -> finish());
  TextView output=new TextView(this); output.setTextColor(Color.LTGRAY); output.setTextSize(14); output.setTextIsSelectable(true);
  ScrollView scroll=new ScrollView(this); scroll.addView(output); page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(page);
  copy.setOnClickListener(v -> { ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE); clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico Mi TV",text)); Toast.makeText(this,"Diagnóstico copiado.",Toast.LENGTH_SHORT).show(); });
  live=AppDatabase.get(this).diagnostics().observe(); observer=events -> {
   StringBuilder value=new StringBuilder("Mi TV 0.5.0 · Diagnóstico local\nLas URLs y posibles credenciales se ocultan.\n\n");
   SimpleDateFormat format=new SimpleDateFormat("dd/MM HH:mm:ss",Locale.getDefault());
   if(events==null||events.isEmpty()) value.append("Sin eventos.");
   else for(DiagnosticEvent event:events) value.append(format.format(new Date(event.timestamp))).append(" [").append(event.level).append(" / ").append(event.category).append("] ").append(event.message).append('\n');
   text=value.toString(); output.setText(text);
  }; live.observeForever(observer);
 }
 @Override protected void onDestroy() { if(live!=null&&observer!=null) live.removeObserver(observer); super.onDestroy(); }
}
