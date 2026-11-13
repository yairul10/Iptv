package cl.mitv.app;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import cl.mitv.app.data.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local catalogue editing, explicit import preview and Android document/share pickers. */
public final class CatalogManagerActivity extends Activity {
 private static final int OPEN=31,SAVE=32;
 private CatalogRepository repo;
 private LiveData<List<CatalogItem>> live;
 private Observer<List<CatalogItem>> observer;
 private final List<CatalogItem> rows=new ArrayList<>();
 private LinearLayout page;
 private boolean movies,ready,busy,importMovie;
 private String pendingExport,exportMime,exportName;
 private CatalogDocument pendingImport;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state); PinGate.require(this,() -> initializeScreen(state));
 }
 private void initializeScreen(Bundle state) {
  repo=CatalogRepository.get(this); movies=false;
  if(state!=null) { movies=false; importMovie=false; pendingExport=state.getString("export"); exportMime=state.getString("mime"); exportName=state.getString("filename"); }
  ScrollView scroll=new ScrollView(this); page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(24,20,24,20); page.setBackgroundColor(Color.rgb(11,18,32)); scroll.addView(page); setContentView(scroll);
  live=repo.observe(); observer=data -> { rows.clear(); if(data!=null) rows.addAll(data); if(ready) render(); }; live.observeForever(observer);
  repo.initialize((v,error) -> { if(isFinishing()||isDestroyed()) return; if(error!=null) { alert("No se pudo abrir la base", "Tus datos no se han borrado."); return; } ready=true; render(); });
 }
 private void label(String value,int size) { TextView text=new TextView(this); text.setText(value); text.setTextColor(Color.WHITE); text.setTextSize(size); text.setPadding(4,12,4,8); page.addView(text); }
 private Button button(LinearLayout parent,String label,Runnable click) { Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setMinHeight(52); b.setOnClickListener(v -> click.run()); parent.addView(b,new LinearLayout.LayoutParams(-1,-2)); return b; }
 private void render() {
  if(isFinishing()||isDestroyed()) return;
  page.removeAllViews(); label("Administrar catálogo",26);
  button(page,"Volver a Popes TV",this::finish);
  button(page,movies?"Añadir película":"Añadir canal",() -> editor(null));
  button(page,"Importar: URL, archivo o pegado",this::importOptions);
  button(page,"Exportar y compartir",this::exportOptions);
  button(page,"Configurar / sincronizar catálogo remoto",this::remoteSettings);
  button(page,"Comprobar salud de URLs ahora",() -> { UrlHealthWorker.checkNow(this); toast("Comprobación encolada. Android la ejecutará cuando haya conexión."); });
  label("Comprobación automática cada 12 h, sujeta a batería y conexión. URL accesible no garantiza que el video funcione.",13);
  int count=0;
  for(CatalogItem item:rows) {
   if(item.movie!=movies) continue; count++;
   label(item.name+" · "+item.category,19);
   label(modeLabel(item.mode)+" · "+healthLabel(item.health)+(item.localEdited?" · Edición local":""),13);
   button(page,"Editar "+item.name,() -> editor(item));
   button(page,"Eliminar "+item.name,() -> new AlertDialog.Builder(this).setTitle("Eliminar del catálogo")
     .setMessage(item.name+" dejará de aparecer. La sincronización no lo volverá a agregar automáticamente.")
     .setPositiveButton("Eliminar",(d,w) -> run(() -> { repo.store.delete(item); return null; },(v,e) -> { if(e!=null) alert("No se pudo eliminar",message(e)); }))
     .setNegativeButton("Cancelar",null).show());
  }
  if(count==0) label(movies?"Todavía no hay películas. Puedes añadirlas o importar una lista.":"No hay canales.",17);
 }
 private static String modeLabel(String mode) { return "STREAM".equals(mode)?"Video directo (HLS / MP4)":"OFFICIAL_PLAYER".equals(mode)?"Reproductor web":"Página web"; }
 private static String healthLabel(String health) { return "DOWN".equals(health)?"URL caída":"UP".equals(health)?"URL accesible":"Sin confirmar"; }
 private EditText field(LinearLayout form,String hint,String value) {
  EditText edit=new EditText(this); edit.setHint(hint); edit.setText(value); edit.setSingleLine(true); form.addView(edit); return edit;
 }
 private TextView caption(LinearLayout form,String value) { TextView t=new TextView(this); t.setText(value); form.addView(t); return t; }
 private void editor(CatalogItem item) {
  run(() -> repo.store.dao.groups(),(groups,error) -> {
   if(error!=null) { alert("No se pudo leer los grupos",message(error)); return; }
   LinearLayout form=new LinearLayout(this); form.setPadding(24,16,24,16); form.setOrientation(LinearLayout.VERTICAL);
   EditText name=field(form,"Nombre",item==null?"":item.name);
   EditText description=field(form,"Descripción",item==null?"":item.description);
   caption(form,"Grupo"); Spinner group=new Spinner(this); List<String> names=new ArrayList<>();
   for(GroupEntity g:groups) names.add(g.name); if(names.isEmpty()) names.add("Sin grupo"); names.add("Crear grupo…");
   group.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names)); form.addView(group);
   if(item!=null&&names.contains(item.category)) group.setSelection(names.indexOf(item.category));
   EditText newGroup=field(form,"Nombre del nuevo grupo (si eliges Crear grupo)","");
   caption(form,"Modo de reproducción"); Spinner mode=new Spinner(this);
   String[] modes={"Página web","Reproductor web","Video directo (HLS / MP4)"};
   mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes)); form.addView(mode);
   int selected=0; if(item!=null) for(int i=0;i<CatalogDocument.MODES.length;i++) if(CatalogDocument.MODES[i].equals(item.mode)) selected=i;
   if(item==null) selected=2; mode.setSelection(selected);
   EditText url=field(form,"URL HTTP/HTTPS",item==null?"":item.playerUrl); url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
   EditText site=field(form,"Página de origen (opcional)",item==null?"":item.siteUrl);
   ScrollView scroll=new ScrollView(this); scroll.addView(form);
   AlertDialog dialog=new AlertDialog.Builder(this).setTitle(item==null?(movies?"Nueva película":"Nuevo canal"):"Editar "+item.name)
     .setView(scroll).setPositiveButton("Guardar",null).setNegativeButton("Cancelar",null).create();
   dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
    CatalogDocument.Item value=new CatalogDocument.Item(); value.movie=item==null?movies:item.movie;
    value.id=item==null?"local:"+UUID.randomUUID():item.id; value.name=name.getText().toString().trim(); value.description=description.getText().toString().trim();
    value.group=group.getSelectedItemPosition()==names.size()-1?newGroup.getText().toString().trim():group.getSelectedItem().toString();
    value.mode=CatalogDocument.MODES[mode.getSelectedItemPosition()]; value.url=url.getText().toString().trim(); value.siteUrl=site.getText().toString().trim();
    if(value.name.isEmpty()||value.group.isEmpty()||!CatalogDocument.validUrl(value.url)||(!value.siteUrl.isEmpty()&&!CatalogDocument.validUrl(value.siteUrl))) { toast("Revisa el nombre, grupo y las URLs HTTP/HTTPS."); return; }
    if(value.siteUrl.isEmpty()) value.siteUrl=value.url;
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    run(() -> { repo.store.save(value); return null; },(nothing,failure) -> { if(failure==null) dialog.dismiss(); else { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); alert("No se pudo guardar",message(failure)); } });
   })); dialog.show();
  });
 }
 private void importOptions() {
  importMovie=movies;
  new AlertDialog.Builder(this).setTitle("Importar "+(movies?"películas":"canales"))
   .setItems(new String[]{"Desde URL","Desde archivo","Pegar M3U o JSON"},(d,which) -> {
    if(which==1) {
     Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
     try { startActivityForResult(intent,OPEN); } catch(ActivityNotFoundException e) { alert("No hay selector de archivos", "Prueba importar desde URL o pegar el texto."); }
    } else inputImport(which==0);
   }).show();
 }
 private void inputImport(boolean url) {
  EditText input=new EditText(this); input.setHint(url?"https://…/lista.m3u":"#EXTM3U o JSON"); input.setMinLines(url?1:6);
  input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(url?android.text.InputType.TYPE_TEXT_VARIATION_URI:android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE));
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle(url?"Importar desde URL":"Pegar catálogo").setView(input)
    .setPositiveButton("Validar",null).setNegativeButton("Cancelar",null).create();
  dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
   String value=input.getText().toString().trim();
   if(url&&!CatalogDocument.validUrl(value)) { toast("Introduce una URL HTTP/HTTPS válida."); return; }
   if(value.isEmpty()||busy) return; dialog.dismiss(); busy=true; toast("Validando importación…");
   run(() -> CatalogDocument.parse(url?NetworkText.get(value,CatalogDocument.MAX_BYTES):value,importMovie),(document,error) -> { busy=false; if(error!=null) alert("No se pudo importar",message(error)); else preview(document); });
  })); dialog.show();
 }
 private void preview(CatalogDocument document) {
  pendingImport=document; Diagnostics.record(this,"INFO","importación","Vista previa: "+document.items.size()+" válidos, "+document.invalidCount+" inválidos, "+document.duplicates+" duplicados.");
  run(() -> repo.store.preview(document),(counts,error) -> {
   if(error!=null) { alert("No se pudo preparar el resumen",message(error)); return; }
   int film=0; Set<String> groups=new HashSet<>(); for(CatalogDocument.Item item:document.items) { if(item.movie) film++; groups.add(item.group); }
   String summary="Canales válidos: "+(document.items.size()-film)+"\nPelículas válidas: "+film+"\nGrupos: "+groups.size()+"\nNuevos: "+counts[0]+"\nExistentes a actualizar: "+counts[1]+"\nDuplicados omitidos: "+document.duplicates+"\nEntradas inválidas omitidas: "+document.invalidCount+
     "\n\nSe guardará solo lo válido. Las entradas existentes incluidas se actualizarán como ediciones locales; lo demás no se elimina.";
   if(!document.errors.isEmpty()) summary+="\n\n"+android.text.TextUtils.join("\n",document.errors.subList(0,Math.min(5,document.errors.size())));
   AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Resumen de importación").setMessage(summary)
    .setPositiveButton("Confirmar importación",null).setNegativeButton("Cancelar",(d,w) -> pendingImport=null).create();
   dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    run(() -> { repo.store.importLocal(document); return null; },(nothing,failure) -> { pendingImport=null; dialog.dismiss(); if(failure==null) toast("Importación guardada."); else alert("No se guardó la importación",message(failure)); });
   })); dialog.show();
  });
 }
 private void exportOptions() {
  new AlertDialog.Builder(this).setTitle("Exportar catálogo completo").setMessage("Incluye canales, películas, grupos y modos. Las URLs pueden contener claves de acceso: comparte el archivo solo con personas de confianza.")
   .setPositiveButton("JSON",(d,w) -> prepareExport(false)).setNeutralButton("M3U",(d,w) -> prepareExport(true)).setNegativeButton("Cancelar",null).show();
 }
 private void prepareExport(boolean m3u) {
  run(() -> { CatalogDocument doc=CatalogDocument.fromRows(repo.store.dao.catalog()); return m3u?doc.toM3u():doc.toJson(); },(text,error) -> {
   if(error!=null) { alert("No se pudo exportar",message(error)); return; }
   Diagnostics.record(this,"INFO","exportación",m3u?"Catálogo exportado a M3U.":"Catálogo exportado a JSON.");
   pendingExport=text; exportMime=m3u?"audio/x-mpegurl":"application/json"; exportName=m3u?"Mi-TV-catalogo.m3u":"Mi-TV-catalogo.json";
   new AlertDialog.Builder(this).setTitle(exportName).setItems(new String[]{"Guardar archivo","Compartir con otra app"},(d,which) -> { if(which==0) saveExport(); else shareExport(); }).show();
  });
 }
 private void saveExport() {
  try { startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(exportMime).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,exportName),SAVE); }
  catch(ActivityNotFoundException e) { toast("No hay selector para guardar. Prueba Compartir."); }
 }
 private void shareExport() {
  final String text=pendingExport,name=exportName,mime=exportMime;
  run(() -> {
   File folder=new File(getCacheDir(),"exports"); if(!folder.exists()&&!folder.mkdirs()) throw new IOException("No se pudo preparar el archivo.");
   File file=new File(folder,System.currentTimeMillis()+"-"+name);
   try(FileOutputStream out=new FileOutputStream(file)) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
   return FileProvider.getUriForFile(this,getPackageName()+".files",file);
  },(uri,error) -> {
   if(error!=null) { alert("No se pudo compartir",message(error)); return; }
   Intent intent=new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
   intent.setClipData(ClipData.newUri(getContentResolver(),exportName,uri));
   try { startActivity(Intent.createChooser(intent,"Compartir catálogo")); } catch(ActivityNotFoundException e) { toast("No hay una app disponible para compartir."); }
  });
 }
 private void remoteSettings() {
  run(() -> repo.store.dao.metadata("remote_url"),(url,error) -> {
   if(error!=null) { alert("No se pudo abrir la configuración",message(error)); return; }
   EditText input=new EditText(this); input.setSingleLine(true); input.setText(url==null?CatalogStore.DEFAULT_REMOTE:url);
   AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Catálogo remoto por versión").setMessage("URL HTTPS de JSON con version numérica creciente. Se conservan las ediciones y eliminaciones locales. Sin conexión se utiliza Room.")
    .setView(input).setPositiveButton("Guardar y sincronizar",null).setNegativeButton("Cancelar",null).create();
   dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
    String value=input.getText().toString().trim(); if(!NavigationPolicy.isPublicHttps(value)) { toast("Usa una URL HTTPS válida."); return; }
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    run(() -> {
     CatalogDocument document=CatalogDocument.parseRemote(NetworkText.get(value,CatalogDocument.MAX_BYTES));
     String result=repo.store.sync(value,document); repo.store.dao.putMetadata(new Metadata("remote_url",value)); return result;
    },(result,failure) -> { if(failure==null) { dialog.dismiss(); toast(result); } else { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); alert("No se pudo sincronizar",message(failure)); } });
   })); dialog.show();
  });
 }
 @Override protected void onActivityResult(int request,int result,Intent data) {
  super.onActivityResult(request,result,data); if(result!=RESULT_OK||data==null||data.getData()==null) return; Uri uri=data.getData();
  if(request==OPEN) run(() -> { try(InputStream in=getContentResolver().openInputStream(uri)) { return CatalogDocument.parse(CatalogRepository.read(in,CatalogDocument.MAX_BYTES),importMovie); } },(document,error) -> { if(error!=null) alert("No se pudo leer el archivo",message(error)); else preview(document); });
  else if(request==SAVE&&pendingExport!=null) {
   final String text=pendingExport;
   run(() -> { try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")) { if(out==null) throw new IOException("No se pudo abrir el destino."); out.write(text.getBytes(StandardCharsets.UTF_8)); } return null; },(v,error) -> { if(error==null) toast("Catálogo guardado."); else alert("No se pudo guardar el archivo",message(error)); });
  }
 }
 private <T> void run(CatalogRepository.Task<T> task,CatalogRepository.Callback<T> callback) {
  repo.submit(task,(value,error) -> { if(!isFinishing()&&!isDestroyed()) callback.done(value,error); });
 }
 private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
 private void alert(String title,String text) { new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("Entendido",null).show(); }
 private static String message(Exception error) { String value=error.getMessage(); return value==null?"Comprueba los datos e inténtalo de nuevo.":value; }
 @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putBoolean("movies",movies); out.putBoolean("importMovie",importMovie); if(pendingExport!=null&&pendingExport.length()<200000) { out.putString("export",pendingExport); out.putString("mime",exportMime); out.putString("filename",exportName); } }
 @Override protected void onDestroy() { if(live!=null&&observer!=null) live.removeObserver(observer); super.onDestroy(); }
}
