package cl.mitv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.common.MediaItem;
import cl.mitv.app.data.CatalogItem;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.ImageButton;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Personal access to channels' official pages and their publicly linked web players. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(11,18,32), CARD = Color.rgb(23,35,53), MINT = Color.rgb(105,227,197);
    private static final String[] RESULTS = {"Pendiente", "Funciona", "No funciona"};
    private final List<CatalogItem> channels = new ArrayList<>();
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final AppUpdates appUpdates = new AppUpdates();
    private boolean catalogLoading;
    private CatalogRepository repository;
    private LiveData<List<CatalogItem>> catalogLive;
    private Observer<List<CatalogItem>> catalogObserver;
    private boolean ready, showMovies;
    private ExoPlayer nativePlayer;
    private boolean resumeNative;
    private final android.os.Handler sleepHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable sleepTask=this::expireSleep;
    private FrameLayout root;
    private WebView web;
    private LinearLayout playerLayout;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private TextView status;
    private ProgressBar progress;
    private CatalogItem currentChannel;
    private String selectedCategory = "Todos", focusedChannelId = "";
    private boolean favoritesOnly;
    private String focusedAction="play";
    private ScrollView homeScroll;
    private int homeScrollY;
    private String searchQuery="";
    private EditText searchInput;
    private LinearLayout catalogRows;
    private TextView emptySearch;
    private boolean selecting, deleting;
    private final LinkedHashSet<String> selectedIds=new LinkedHashSet<>();
    private Button deleteSelectedButton;
    private boolean playerSuspended;
    private final android.os.Handler controlsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable hideControlsTask = () -> {
        if(currentChannel != null && customView == null && hasWindowFocus()
                && playerLayout != null && playerLayout.getChildAt(0).getVisibility() == View.VISIBLE) toggleFullscreen();
    };
    private boolean isTelevision() {
        int mode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_TYPE_MASK;
        return mode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
            || !getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN);
    }
    private void scheduleHideControls() {
        controlsHandler.removeCallbacks(hideControlsTask);
        if(isTelevision() && currentChannel != null && customView == null && playerLayout != null
                && playerLayout.getChildAt(0).getVisibility() == View.VISIBLE)
            controlsHandler.postDelayed(hideControlsTask,6000);
    }
    @Override public void onUserInteraction() {
        super.onUserInteraction(); scheduleHideControls();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(focused) scheduleHideControls(); else controlsHandler.removeCallbacks(hideControlsTask);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        repository = CatalogRepository.get(this);
        if(state != null) {
            selectedCategory = state.getString("category", "Todos");
            favoritesOnly = state.getBoolean("favorites", false);
            showMovies = state.getBoolean("movies",false);
            searchQuery = state.getString("searchQuery","");
            focusedChannelId = state.getString("focus", "");
            focusedAction = state.getString("focusAction","play");
            homeScrollY = state.getInt("scroll", 0);
        }
        if(state!=null) { selecting=state.getBoolean("selecting",false); ArrayList<String> saved=state.getStringArrayList("selectedIds"); if(saved!=null) selectedIds.addAll(saved); }
        root = new FrameLayout(this); root.setBackgroundColor(BG); setContentView(root);
        root.addView(text("Preparando catálogo…",20,Color.WHITE));
        catalogLive = repository.observe();
        catalogObserver = rows -> {
            if(isFinishing() || isDestroyed()) return;
            channels.clear(); if(rows != null) channels.addAll(rows);
            if(ready && web == null && nativePlayer == null) {
                if(homeScroll != null) homeScrollY = homeScroll.getScrollY();
                showHome();
            }
        };
        catalogLive.observeForever(catalogObserver);
        repository.initialize((unused,error) -> {
            if(isFinishing() || isDestroyed()) return;
            if(error != null) {
                new AlertDialog.Builder(this).setTitle("No se pudo abrir el catálogo")
                    .setMessage("No se borraron tus datos. Cierra y vuelve a abrir Popes TV para reintentar.").setPositiveButton("Cerrar",(d,w) -> finish()).show();
                return;
            }
            ready = true; showHome(); UrlHealthWorker.schedule(this); refreshCatalog(false); appUpdates.check(this,network,false);
        });
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setPadding(0,dp(5),0,dp(5)); return v;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private GradientDrawable background(int color, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(14)); d.setStroke(dp(2),stroke); return d;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setTextSize(15); b.setTextColor(Color.WHITE);
        b.setAllCaps(false); b.setMinHeight(dp(52)); b.setPadding(dp(16),dp(8),dp(16),dp(8));
        b.setBackground(background(CARD,Color.TRANSPARENT)); b.setFocusable(true);
        b.setOnFocusChangeListener((v,focused) -> b.setBackground(background(focused ? Color.rgb(32,66,76) : CARD,focused ? MINT : Color.TRANSPARENT)));
        b.setOnClickListener(v -> action.run()); return b;
    }
    private ImageButton catalogIcon(int drawable,String label,CatalogItem item,String action,Runnable click) {
        ImageButton icon=new ImageButton(this); icon.setImageResource(drawable); icon.setContentDescription(label);
        icon.setPadding(dp(13),dp(13),dp(13),dp(13)); icon.setFocusable(true);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf("favorite".equals(action)&&item.favorite?Color.rgb(255,210,100):MINT));
        icon.setBackground(background(CARD,Color.TRANSPARENT));
        icon.setOnFocusChangeListener((v,focused) -> {
            icon.setBackground(background(focused?Color.rgb(32,66,76):CARD,focused?MINT:Color.TRANSPARENT));
            if(focused) { focusedChannelId=item.id; focusedAction=action; }
        });
        icon.setOnClickListener(v -> { focusedAction=action; click.run(); });
        return icon;
    }
    private void addButton(LinearLayout parent, Button b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(6),0,dp(6)); parent.addView(b,p);
    }
    private void showHome() {
        boolean restoreSearchFocus=searchInput!=null && searchInput.hasFocus() && currentChannel==null;
        int searchCursor=restoreSearchFocus?searchInput.getSelectionStart():searchQuery.length();
        stopPlayer(); root.removeAllViews();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        homeScroll = new ScrollView(this); homeScroll.setFillViewport(true);
        LinearLayout page = column(); page.setPadding(dp(16),dp(20),dp(16),dp(20));
        homeScroll.addView(page); root.addView(homeScroll);
        TextView heading = text("Popes TV",36,Color.WHITE); heading.setTypeface(null,Typeface.BOLD); page.addView(heading);
        long total=0; for(CatalogItem item:channels) if(item.movie==showMovies) total++;
        page.addView(text(total + (showMovies ? " películas" : " canales") + " · Tu televisión, a un toque.",17,Color.LTGRAY));
        addButton(page,button(showMovies ? "Ver canales" : "Ver películas",() -> { selecting=false; selectedIds.clear(); showMovies=!showMovies; searchQuery=""; selectedCategory="Todos"; homeScrollY=0; showHome(); }));
        addButton(page,button("Administrar / importar / exportar",() -> startActivity(new Intent(this,CatalogManagerActivity.class).putExtra("movies",showMovies))));
        addButton(page,button(favoritesOnly ? "Ver todos los canales" : "Ver favoritos",() -> {
            favoritesOnly = !favoritesOnly; homeScrollY = 0; focusedChannelId = ""; showHome();
        }));
        addButton(page,button(selecting ? "Cancelar selección" : "Seleccionar para eliminar",() -> {
            if(deleting) return;
            if(selecting) { selecting=false; selectedIds.clear(); showHome(); }
            else PinGate.require(this,() -> { selecting=true; selectedIds.clear(); showHome(); },() -> { });
        }));
        if(selecting) {
            page.addView(text("Marca los que no usarás y pulsa Eliminar seleccionados.",15,Color.LTGRAY));
            addButton(page,button("Seleccionar todos los visibles",() -> {
                for(CatalogItem item:channels) if(visibleInList(item)) selectedIds.add(item.id);
                homeScrollY=homeScroll.getScrollY(); showHome();
            }));
            addButton(page,button("Desmarcar todos",() -> { selectedIds.clear(); homeScrollY=homeScroll.getScrollY(); showHome(); }));
            deleteSelectedButton=button("",this::confirmDeleteSelected); updateDeleteSelection(); addButton(page,deleteSelectedButton);
        }
        HorizontalScrollView filters = new HorizontalScrollView(this);
        LinearLayout filterRow = new LinearLayout(this); filters.addView(filterRow); page.addView(filters);
        LinkedHashSet<String> categories = new LinkedHashSet<>(); categories.add("Todos");
        for(CatalogItem channel : channels) if(channel.movie==showMovies) categories.add(channel.category);
        if(!categories.contains(selectedCategory)) selectedCategory = "Todos";
        for(String category : categories) {
            Button filter = button((category.equals(selectedCategory) ? "✓ " : "") + category,() -> {
                selectedCategory = category; focusedChannelId = ""; homeScrollY = 0; showHome();
            });
            addControl(filterRow,filter);
        }
        LinearLayout searchRow=new LinearLayout(this); searchRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        searchInput=new EditText(this); searchInput.setSingleLine(true); searchInput.setTextColor(Color.WHITE); searchInput.setHintTextColor(Color.LTGRAY);
        searchInput.setHint(showMovies?"Buscar película por nombre":"Buscar canal por nombre"); searchInput.setContentDescription(searchInput.getHint());
        searchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT); searchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        searchInput.setText(searchQuery); searchRow.addView(searchInput,new LinearLayout.LayoutParams(0,dp(56),1));
        addControl(searchRow,button("Limpiar",() -> { searchInput.setText(""); searchInput.requestFocus(); })); page.addView(searchRow);
        catalogRows=column(); page.addView(catalogRows);
        int count = 0; View focusTarget = null;
        for(CatalogItem channel : channels) {
            if(channel.movie!=showMovies) continue;
            boolean favorite = channel.favorite;
            if((favoritesOnly && !favorite) || (!selectedCategory.equals("Todos") && !selectedCategory.equals(channel.category))) continue;
            count++;
            LinearLayout row=new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12),dp(5),dp(5),dp(5)); row.setMinimumHeight(dp(64)); row.setBackground(background(CARD,Color.TRANSPARENT));
            LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2); rowParams.setMargins(0,dp(4),0,dp(4)); catalogRows.addView(row,rowParams); row.setTag(channel);
            if(selecting) {
                CheckBox check=new CheckBox(this); check.setText(channelLabel(channel)); check.setTextSize(18); check.setTextColor(Color.WHITE); check.setMinHeight(dp(52)); check.setChecked(selectedIds.contains(channel.id));
                check.setEnabled(!deleting);
                check.setOnCheckedChangeListener((b,checked) -> { if(checked) selectedIds.add(channel.id); else selectedIds.remove(channel.id); updateDeleteSelection(); });
                row.addView(check,new LinearLayout.LayoutParams(-1,-2));
                if(focusTarget==null || channel.id.equals(focusedChannelId)) focusTarget=check;
                check.setOnFocusChangeListener((v,hasFocus) -> { if(hasFocus) focusedChannelId=channel.id; });
                continue;
            }
            TextView title=text(channelLabel(channel),18,Color.WHITE); title.setTypeface(null,Typeface.BOLD); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            ImageButton play=catalogIcon(R.drawable.ic_play,"Reproducir "+channel.name,channel,"play",() -> { rememberHome(channel); showPlayer(channel); });
            ImageButton star=catalogIcon(favorite?R.drawable.ic_star_filled:R.drawable.ic_star_outline,(favorite?"Quitar de favoritos ":"Añadir a favoritos ")+channel.name,channel,"favorite",() -> {
                rememberHome(channel); repository.submit(() -> { repository.store.favorite(channel,!favorite); return null; },(v,e) -> {
                    if(e!=null&&!isFinishing()&&!isDestroyed()) Toast.makeText(this,"No se pudo guardar el favorito.",Toast.LENGTH_LONG).show();
                });
            });
            LinearLayout.LayoutParams actionParams=new LinearLayout.LayoutParams(dp(52),dp(52)); actionParams.setMargins(dp(4),0,0,0); row.addView(play,actionParams);
            LinearLayout.LayoutParams favoriteParams=new LinearLayout.LayoutParams(dp(52),dp(52)); favoriteParams.setMargins(dp(4),0,0,0); row.addView(star,favoriteParams);
            if(focusTarget==null) focusTarget=play;
            if(channel.id.equals(focusedChannelId)) focusTarget="favorite".equals(focusedAction)?star:play;
        }
        emptySearch=text("No hay resultados. Prueba otro nombre, limpia la búsqueda o cambia los filtros.",18,Color.LTGRAY); page.addView(emptySearch);
        filterSearchRows();
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence value,int start,int count,int after) { }
            public void onTextChanged(CharSequence value,int start,int before,int count) { searchQuery=value.toString(); filterSearchRows(); }
            public void afterTextChanged(Editable value) { }
        });
        page.addView(text("Popes TV · "+BuildConfig.VERSION_NAME,13,Color.GRAY));
        addButton(page,button("Ajustes",() -> startActivity(new Intent(this,SettingsActivity.class))));
        addButton(page,button("Diagnóstico",() -> startActivity(new Intent(this,DiagnosticsActivity.class))));
        addButton(page,button("Gestionar películas",() -> startActivity(new Intent(this,CatalogManagerActivity.class).putExtra("movies",true))));
        addButton(page,button("Actualizar canales",() -> refreshCatalog(true)));
        addButton(page,button("Buscar actualización de la app",() -> appUpdates.check(this,network)));
        addButton(page,button("Ayuda",() -> new AlertDialog.Builder(this).setTitle("Popes TV")
            .setMessage("Usa las flechas y OK del control, o toca la pantalla.\n\nVer en vivo abre el reproductor o la página oficial del canal. Algunos piden Play, activar sonido o aceptar cookies.\n\nEn las páginas oficiales usa sus propios controles. Ampliar vista oculta los botones de Popes TV; Atrás los recupera. MENU también alterna la vista.\n\nResultado guarda si cada canal funciona en este equipo. Las pruebas y favoritos del celular y el TV Box son independientes.\n\nLa lista se revisa por internet al abrir Popes TV. Actualizar canales permite revisarla de nuevo. Si no hay conexión, se conserva la última lista válida.\n\nBuscar actualización de la app descarga un APK compatible desde GitHub; Android confirma la instalación.\n\nSi un canal falla, pulsa Recargar o Página oficial. La app no está afiliada a los canales ni almacena o retransmite las señales.")
            .setPositiveButton("Entendido",null).show()));
        if(restoreSearchFocus) { searchInput.requestFocus(); searchInput.setSelection(Math.max(0,Math.min(searchCursor,searchInput.length()))); }
        else if(focusTarget!=null && focusTarget.isShown()) focusTarget.requestFocus();
        else searchInput.requestFocus();
        final ScrollView scroll = homeScroll;
        scroll.post(() -> scroll.scrollTo(0,homeScrollY));
    }
    private void refreshCatalog(boolean manual) {
        if(catalogLoading || !ready) return;
        catalogLoading=true;
        if(manual) Toast.makeText(this,"Sincronizando catálogo…",Toast.LENGTH_SHORT).show();
        repository.submit(() -> repository.syncNow(),(message,error) -> {
            catalogLoading=false;
            if(!isFinishing() && !isDestroyed() && manual) Toast.makeText(this,error==null?message:"No se pudo sincronizar. Se mantiene el catálogo local.",Toast.LENGTH_LONG).show();
        });
    }
    private void filterSearchRows() {
        int visible=0;
        for(int i=0;i<catalogRows.getChildCount();i++) {
            View row=catalogRows.getChildAt(i); CatalogItem item=(CatalogItem)row.getTag();
            boolean matches=CatalogSearch.matches(item.name,searchQuery); row.setVisibility(matches?View.VISIBLE:View.GONE); if(matches) visible++;
        }
        emptySearch.setVisibility(visible==0?View.VISIBLE:View.GONE);
    }
    private boolean visibleInList(CatalogItem item) {
        return CatalogSearch.matches(item.name,searchQuery) && item.movie==showMovies && (!favoritesOnly || item.favorite) && ("Todos".equals(selectedCategory) || selectedCategory.equals(item.category));
    }
    private void updateDeleteSelection() {
        if(deleteSelectedButton!=null) { deleteSelectedButton.setText("Eliminar seleccionados ("+selectedIds.size()+")"); deleteSelectedButton.setEnabled(!deleting && !selectedIds.isEmpty()); }
    }
    private void confirmDeleteSelected() {
        if(deleting || selectedIds.isEmpty()) return;
        ArrayList<String> ids=new ArrayList<>(selectedIds); boolean movies=showMovies;
        new AlertDialog.Builder(this).setTitle("Eliminar "+ids.size()+(movies?" películas":" canales")+" seleccionados")
            .setMessage("Se quitarán de tu lista. Los demás se conservarán y la sincronización no volverá a añadir los eliminados.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(dialog,which) -> {
                deleting=true; updateDeleteSelection();
                repository.submit(() -> repository.store.deleteMany(ids,movies),(count,error) -> {
                    if(isFinishing() || isDestroyed()) return;
                    deleting=false;
                    if(error==null) { selectedIds.clear(); selecting=false; homeScrollY=0; Toast.makeText(this,"Eliminados: "+count,Toast.LENGTH_SHORT).show(); }
                    else Toast.makeText(this,"No se pudo eliminar. Tus datos se conservaron.",Toast.LENGTH_LONG).show();
                    showHome();
                });
            }).show();
    }
    private void confirmDeletePlaying(CatalogItem channel) {
        if(deleting || currentChannel!=channel) return;
        PinGate.require(this,() -> {
            if(isFinishing() || isDestroyed() || currentChannel!=channel) return;
            new AlertDialog.Builder(this).setTitle(channel.movie?"Borrar película":"Borrar canal")
                .setMessage("¿Quieres quitar \""+channel.name+"\" de tu lista? La sincronización no volverá a añadirlo.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Borrar",(dialog,which) -> {
                    if(deleting) return;
                    deleting=true;
                    ArrayList<CatalogItem> before=new ArrayList<>(channels);
                    boolean expanded=customView!=null || playerLayout.getChildAt(0).getVisibility()==View.GONE;
                    repository.submit(() -> {
                        repository.store.deleteMany(java.util.Collections.singletonList(channel.id),channel.movie);
                        return repository.store.dao.catalog();
                    },(remaining,error) -> {
                        if(isFinishing() || isDestroyed()) return;
                        deleting=false;
                        if(error==null) {
                            Toast.makeText(this,channel.movie?"Película eliminada.":"Canal eliminado.",Toast.LENGTH_SHORT).show();
                            if(currentChannel==channel) {
                                CatalogItem next=ChannelNavigation.afterDeletion(before,channel,remaining,selectedCategory,favoritesOnly,searchQuery);
                                channels.clear(); channels.addAll(remaining);
                                if(next!=null) { rememberHome(next); showPlayer(next); if(expanded) toggleFullscreen(); }
                                else { homeScrollY=0; showHome(); }
                            }
                        } else Toast.makeText(this,"No se pudo borrar. Inténtalo de nuevo.",Toast.LENGTH_LONG).show();
                    });
                }).show();
        },() -> { });
    }
    private void rememberHome(CatalogItem channel) { focusedChannelId=channel.id; if(homeScroll!=null) homeScrollY=homeScroll.getScrollY(); }
    private int resultFor(CatalogItem channel) { return Math.max(0,Math.min(2,channel.testResult)); }
    private void markResult(CatalogItem channel,boolean fromPlayer) {
        new AlertDialog.Builder(this).setTitle(channel.name+" · Este equipo")
            .setSingleChoiceItems(RESULTS,resultFor(channel),(dialog,which) -> {
                dialog.dismiss(); rememberHome(channel);
                repository.submit(() -> { repository.store.result(channel,which); return null; },(v,error) -> {
                    if(!isFinishing()&&!isDestroyed()) Toast.makeText(this,error==null?"Resultado guardado.":"No se pudo guardar.",Toast.LENGTH_SHORT).show();
                });
            }).setNegativeButton("Cerrar",null).show();
    }
    @SuppressWarnings("SetJavaScriptEnabled")
    private void showPlayer(CatalogItem channel) {
        Diagnostics.record(this,"INFO","reproducción",(channel.movie?"Película":"Canal")+" abierto en modo "+channel.mode+" · "+channel.playerUrl);
        stopPlayer(); currentChannel = channel; playerSuspended = false; root.removeAllViews();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        playerLayout = column(); root.addView(playerLayout,new FrameLayout.LayoutParams(-1,-1));
        HorizontalScrollView controlsScroll = new HorizontalScrollView(this);
        LinearLayout controls = new LinearLayout(this); controls.setPadding(dp(8),dp(4),dp(8),dp(4));
        controlsScroll.addView(controls); playerLayout.addView(controlsScroll);
        addControl(controls,button("Canales",this::showHome));
        addControl(controls,button(channel.movie?"Borrar película":"Borrar canal",() -> confirmDeletePlaying(channel)));
        addControl(controls,button("Play / pausa",this::togglePlayback));
        addControl(controls,button("Ampliar vista",this::toggleFullscreen));
        addControl(controls,button("Recargar",() -> { if(web != null) web.loadUrl(channel.playerUrl,PlaybackSettings.current.headers(channel.playerUrl)); else if(nativePlayer!=null) { nativePlayer.prepare(); nativePlayer.play(); } }));
        addControl(controls,button("Resultado",() -> markResult(channel,true)));
        addControl(controls,button("Página oficial",() -> openBrowser(channel.siteUrl)));
        final TextView playerStatus = text("Cargando " + channelLabel(channel) + "…",13,Color.LTGRAY);
        status = playerStatus; status.setPadding(dp(12),0,dp(12),dp(4)); playerLayout.addView(status);
        final ProgressBar playerProgress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress = playerProgress; playerLayout.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        if("STREAM".equals(channel.mode)) {
            PlaybackSettings profile=PlaybackSettings.current;
            androidx.media3.datasource.DataSource.Factory http=new androidx.media3.datasource.DefaultHttpDataSource.Factory().setUserAgent(profile.userAgent.isEmpty()?"MiTV/0.5.0":profile.userAgent);
            androidx.media3.datasource.DataSource.Factory headers=new androidx.media3.datasource.ResolvingDataSource.Factory(http,spec -> {
                java.util.Map<String,String> values=new java.util.HashMap<>(spec.httpRequestHeaders); values.putAll(PlaybackSettings.current.headers(spec.uri.toString())); return spec.withRequestHeaders(values);
            });
            androidx.media3.exoplayer.DefaultLoadControl load=new androidx.media3.exoplayer.DefaultLoadControl.Builder().setBufferDurationsMs(profile.minBufferMs(),profile.maxBufferMs(),profile.startBufferMs(),profile.rebufferMs()).build();
            nativePlayer=new ExoPlayer.Builder(this).setLoadControl(load).setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(headers)).build();
            if(profile.qualityHeight>0) nativePlayer.setTrackSelectionParameters(nativePlayer.getTrackSelectionParameters().buildUpon().setMaxVideoSize(Integer.MAX_VALUE,profile.qualityHeight).build());
            nativePlayer.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_MEDIA).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
            PlayerView video=new PlayerView(this);
            video.setPlayer(channel.movie?nativePlayer:new ChannelPlayer(nativePlayer,() -> switchChannel(-1),() -> switchChannel(1)));
            if(!channel.movie) {
                video.setShowPreviousButton(true); video.setShowNextButton(true);
                View previous=video.findViewById(androidx.media3.ui.R.id.exo_prev),next=video.findViewById(androidx.media3.ui.R.id.exo_next);
                if(previous!=null) previous.setContentDescription("Canal anterior");
                if(next!=null) next.setContentDescription("Canal siguiente");
            }
            playerLayout.addView(video,new LinearLayout.LayoutParams(-1,0,1));
            nativePlayer.addListener(new androidx.media3.common.Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    playerProgress.setVisibility(state==androidx.media3.common.Player.STATE_BUFFERING?View.VISIBLE:View.GONE);
                    playerStatus.setText(channelLabel(channel)+" · Controles del reproductor");
                }
                @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    Diagnostics.record(MainActivity.this,"ERROR","reproducción","Error de video directo: "+error.getErrorCodeName());
                    playerProgress.setVisibility(View.GONE); playerStatus.setText("No se pudo reproducir. Revisa la URL y el modo en Editar.");
                }
            });
            nativePlayer.setMediaItem(MediaItem.fromUri(channel.playerUrl)); nativePlayer.prepare(); nativePlayer.play(); video.requestFocus();
            scheduleHideControls();
            return;
        }
        web = new WebView(this); web.setBackgroundColor(Color.BLACK);
        WebSettings settings = web.getSettings(); if(!PlaybackSettings.current.userAgent.isEmpty()) settings.setUserAgentString(PlaybackSettings.current.userAgent); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true); settings.setLoadWithOverviewMode(true); settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // Official pages often use third-party video providers. Their cookie notices remain intact.
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebViewClient(new WebViewClient() {
            private boolean failed;
            private void fail() {
                Diagnostics.record(MainActivity.this,"WARN","web","No se pudo cargar la página del reproductor.");
                failed = true; playerProgress.setVisibility(View.GONE);
                playerStatus.setText("No se pudo cargar " + channel.name + ". Prueba Recargar o Página oficial.");
            }
            @Override public void onPageStarted(WebView view,String page,android.graphics.Bitmap icon) {
                failed = false; playerProgress.setVisibility(View.VISIBLE); playerStatus.setText("Cargando " + channelLabel(channel) + "…");
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
                if(!request.isForMainFrame()) return false;
                String target = request.getUrl().toString();
                if(NavigationPolicy.isChannelUrl(target,channel.playerUrl,channel.siteUrl)) {
                    java.util.Map<String,String> headers=PlaybackSettings.current.headers(target);
                    if(request.hasGesture()&&!headers.isEmpty()) { view.loadUrl(target,headers); return true; }
                    return false;
                }
                if(request.hasGesture() && "https".equalsIgnoreCase(request.getUrl().getScheme())) openBrowser(target);
                return true;
            }
            @Override public void onPageFinished(WebView view,String page) {
                if(!failed) playerStatus.setText(channelLabel(channel) + (channel.directPlayer
                    ? " · Pulsa Play o el botón OK. Atrás para volver."
                    : " · Usa Play y los controles de la página oficial."));
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error) { if(request.isForMainFrame()) fail(); }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response) { if(request.isForMainFrame()) fail(); }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view,int value) { playerProgress.setProgress(value); playerProgress.setVisibility(value == 100 ? View.GONE : View.VISIBLE); }
            @Override public void onShowCustomView(View view,CustomViewCallback callback) {
                if(customView != null) { callback.onCustomViewHidden(); return; }
                customView = view; customCallback = callback;
                playerLayout.setVisibility(View.GONE); root.addView(view,new FrameLayout.LayoutParams(-1,-1)); setFullscreenUi(true);
            }
            @Override public void onHideCustomView() { hideCustomView(); }
        });
        playerLayout.addView(web,new LinearLayout.LayoutParams(-1,0,1)); web.loadUrl(channel.playerUrl,PlaybackSettings.current.headers(channel.playerUrl)); web.requestFocus();
        scheduleHideControls();
    }
    private String channelLabel(CatalogItem item) {
        int number=ChannelNavigation.number(channels,item);
        return number>0 ? number+" · "+item.name : item.name;
    }
    private void switchChannel(int direction) {
        CatalogItem next=ChannelNavigation.adjacent(channels,currentChannel,direction,selectedCategory,favoritesOnly,searchQuery);
        if(next==null) { Toast.makeText(this,"No hay otros canales en este filtro.",Toast.LENGTH_SHORT).show(); return; }
        if(next.id.equals(currentChannel.id)) { Toast.makeText(this,"Solo hay un canal en este filtro.",Toast.LENGTH_SHORT).show(); return; }
        boolean expanded=customView!=null || playerLayout.getChildAt(0).getVisibility()==View.GONE;
        rememberHome(next); showPlayer(next);
        if(expanded) toggleFullscreen();
        if(nativePlayer!=null) { View focus=root.findViewById(direction<0?androidx.media3.ui.R.id.exo_prev:androidx.media3.ui.R.id.exo_next); if(focus!=null) focus.requestFocus(); }
    }
    private void addControl(LinearLayout row,Button b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2,-2); p.setMargins(dp(3),0,dp(3),0); row.addView(b,p);
    }
    private void togglePlayback() {
        if(nativePlayer!=null) { if(nativePlayer.isPlaying()) nativePlayer.pause(); else nativePlayer.play(); return; }
        if(web == null) return;
        web.evaluateJavascript("(function(){var v=document.querySelector('video');if(!v)return false;if(v.paused){v.muted=false;v.play().catch(function(){});}else{v.pause();}return true;})()",
            result -> { if("false".equals(result)) Toast.makeText(this,"Usa Play en el reproductor de la página oficial.",Toast.LENGTH_LONG).show(); });
    }
    private void toggleFullscreen() {
        if(web == null && nativePlayer == null) return;
        boolean visible = playerLayout.getChildAt(0).getVisibility() == View.VISIBLE;
        playerLayout.getChildAt(0).setVisibility(visible ? View.GONE : View.VISIBLE);
        status.setVisibility(visible ? View.GONE : View.VISIBLE); setFullscreenUi(visible);
        if(visible) {
            View content=playerLayout.getChildAt(playerLayout.getChildCount()-1);
            content.requestFocus();
        } else {
            ((HorizontalScrollView)playerLayout.getChildAt(0)).getChildAt(0).requestFocus();
        }
        scheduleHideControls();
    }
    private void setFullscreenUi(boolean enabled) {
        getWindow().getDecorView().setSystemUiVisibility(enabled ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY : View.SYSTEM_UI_FLAG_VISIBLE);
    }
    private void hideCustomView() {
        if(customView == null) return;
        root.removeView(customView); customView = null;
        if(playerLayout != null) playerLayout.setVisibility(View.VISIBLE);
        WebChromeClient.CustomViewCallback cb = customCallback; customCallback = null;
        if(cb != null) cb.onCustomViewHidden(); setFullscreenUi(false);
    }
    private void openBrowser(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
        catch(ActivityNotFoundException e) { Toast.makeText(this,"No hay un navegador instalado.",Toast.LENGTH_LONG).show(); }
    }
    private void stopPlayer() {
        controlsHandler.removeCallbacks(hideControlsTask);
        hideCustomView();
        if(nativePlayer!=null) { nativePlayer.release(); nativePlayer=null; }
        if(web != null) {
            web.stopLoading(); web.setWebChromeClient(null); web.setWebViewClient(new WebViewClient()); web.loadUrl("about:blank");
            if(web.getParent() instanceof ViewGroup) ((ViewGroup)web.getParent()).removeView(web);
            web.destroy(); web = null;
        }
        playerSuspended = false; resumeNative=false; currentChannel = null; setFullscreenUi(false);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(currentChannel!=null && !currentChannel.movie && (event.getKeyCode()==KeyEvent.KEYCODE_CHANNEL_UP || event.getKeyCode()==KeyEvent.KEYCODE_CHANNEL_DOWN)) {
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) switchChannel(event.getKeyCode()==KeyEvent.KEYCODE_CHANNEL_UP?1:-1);
            return true;
        }
        // Do not swallow OK on entire websites: it must still activate their own links and buttons.
        if(web != null && currentChannel != null && currentChannel.directPlayer && web.hasFocus()
                && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
            if(event.getAction() == KeyEvent.ACTION_UP) togglePlayback(); return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        if((web != null || nativePlayer != null) && key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { togglePlayback(); return true; }
        if((web != null || nativePlayer != null) && key == KeyEvent.KEYCODE_MENU) { toggleFullscreen(); return true; }
        return super.onKeyDown(key,event);
    }
    @Override public void onBackPressed() {
        if(selecting && currentChannel==null) { if(!deleting) { selecting=false; selectedIds.clear(); showHome(); } return; }
        if(customView != null) { hideCustomView(); return; }
        if((web != null || nativePlayer != null) && playerLayout.getChildAt(0).getVisibility() == View.GONE) { toggleFullscreen(); return; }
        if(web != null || nativePlayer != null) { showHome(); return; }
        if(favoritesOnly || !selectedCategory.equals("Todos")) {
            favoritesOnly = false; selectedCategory = "Todos"; homeScrollY = 0; showHome(); return;
        }
        super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("searchQuery",searchQuery);
        state.putBoolean("selecting",selecting); state.putStringArrayList("selectedIds",new ArrayList<>(selectedIds));
        state.putBoolean("movies",showMovies); state.putString("category",selectedCategory); state.putBoolean("favorites",favoritesOnly);
        state.putString("focusAction",focusedAction); state.putString("focus",focusedChannelId); state.putInt("scroll",web == null && homeScroll != null ? homeScroll.getScrollY() : homeScrollY);
    }
    @Override protected void onPause() {
        controlsHandler.removeCallbacks(hideControlsTask);
        sleepHandler.removeCallbacks(sleepTask);
        if(nativePlayer!=null) { resumeNative=nativePlayer.isPlaying(); nativePlayer.pause(); }
        if(web != null) {
            // Navigating to blank stops audio inside cross-origin player iframes as well.
            hideCustomView(); web.loadUrl("about:blank"); web.onPause(); playerSuspended = true;
        }
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if(scheduleSleep()) return;
        if(nativePlayer!=null && resumeNative) { resumeNative=false; nativePlayer.play(); }
        if(web != null) {
            web.onResume();
            if(playerSuspended && currentChannel != null) { playerSuspended = false; web.loadUrl(currentChannel.playerUrl,PlaybackSettings.current.headers(currentChannel.playerUrl)); }
        }
    }
    private boolean scheduleSleep() {
        sleepHandler.removeCallbacks(sleepTask);
        long deadline=SettingsStore.prefs(this).getLong("sleep_deadline",0);
        if(deadline==0) return false;
        long remaining=SettingsStore.remaining(deadline,System.currentTimeMillis());
        if(remaining==0) { expireSleep(); return true; }
        sleepHandler.postDelayed(sleepTask,remaining); return false;
    }
    private void expireSleep() {
        sleepHandler.removeCallbacks(sleepTask); SettingsStore.prefs(this).edit().remove("sleep_deadline").apply();
        stopPlayer(); if(ready) showHome();
        Diagnostics.record(this,"INFO","temporizador","Temporizador finalizado. Reproducción detenida.");
        Toast.makeText(this,"Temporizador finalizado.",Toast.LENGTH_LONG).show();
    }
    @Override protected void onDestroy() { sleepHandler.removeCallbacks(sleepTask); if(catalogLive!=null && catalogObserver!=null) catalogLive.removeObserver(catalogObserver); network.shutdownNow(); stopPlayer(); super.onDestroy(); }
}
