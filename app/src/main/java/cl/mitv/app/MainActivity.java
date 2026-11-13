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
    private ScrollView homeScroll;
    private int homeScrollY;
    private boolean playerSuspended;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        repository = CatalogRepository.get(this);
        if(state != null) {
            selectedCategory = state.getString("category", "Todos");
            favoritesOnly = state.getBoolean("favorites", false);
            showMovies = state.getBoolean("movies",false);
            focusedChannelId = state.getString("focus", "");
            homeScrollY = state.getInt("scroll", 0);
        }
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
                    .setMessage("No se borraron tus datos. Cierra y vuelve a abrir Mi TV para reintentar.").setPositiveButton("Cerrar",(d,w) -> finish()).show();
                return;
            }
            ready = true; showHome(); UrlHealthWorker.schedule(this); refreshCatalog(false);
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
    private void addButton(LinearLayout parent, Button b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(6),0,dp(6)); parent.addView(b,p);
    }
    private void showHome() {
        stopPlayer(); root.removeAllViews();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        homeScroll = new ScrollView(this); homeScroll.setFillViewport(true);
        LinearLayout page = column(); page.setPadding(dp(24),dp(24),dp(24),dp(24));
        homeScroll.addView(page); root.addView(homeScroll);
        TextView heading = text("Mi TV",36,Color.WHITE); heading.setTypeface(null,Typeface.BOLD); page.addView(heading);
        long total=0; for(CatalogItem item:channels) if(item.movie==showMovies) total++;
        page.addView(text(total + (showMovies ? " películas" : " canales") + " · Tu televisión, a un toque.",17,Color.LTGRAY));
        addButton(page,button(showMovies ? "Ver canales" : "Ver películas",() -> { showMovies=!showMovies; selectedCategory="Todos"; homeScrollY=0; showHome(); }));
        addButton(page,button("Administrar / importar / exportar",() -> startActivity(new Intent(this,CatalogManagerActivity.class))));
        addButton(page,button(favoritesOnly ? "Ver todos los canales" : "Ver favoritos",() -> {
            favoritesOnly = !favoritesOnly; homeScrollY = 0; focusedChannelId = ""; showHome();
        }));
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
        int count = 0; Button focusTarget = null;
        for(CatalogItem channel : channels) {
            if(channel.movie!=showMovies) continue;
            boolean favorite = channel.favorite;
            if((favoritesOnly && !favorite) || (!selectedCategory.equals("Todos") && !selectedCategory.equals(channel.category))) continue;
            count++;
            LinearLayout card = column(); card.setPadding(dp(18),dp(12),dp(18),dp(12)); card.setBackground(background(CARD,Color.TRANSPARENT));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1,-2); cp.setMargins(0,dp(12),0,dp(8)); page.addView(card,cp);
            card.addView(text(channel.category.toUpperCase(java.util.Locale.ROOT),12,MINT));
            TextView title = text(channel.name,25,Color.WHITE); title.setTypeface(null,Typeface.BOLD); card.addView(title);
            card.addView(text(channel.description,15,Color.LTGRAY));
            if("DOWN".equals(channel.health)) card.addView(text("URL caída · última comprobación",13,Color.rgb(255,170,130)));
            else if("UP".equals(channel.health)) card.addView(text("URL accesible · no confirma reproducción",12,Color.GRAY));
            int result = resultFor(channel);
            card.addView(text("Prueba en este equipo: " + RESULTS[result],13,result == 1 ? MINT : Color.LTGRAY));
            Button watch = button(channel.movie ? "Ver película" : "Ver en vivo",() -> {
                rememberHome(channel); showPlayer(channel);
            }); addButton(card,watch);
            watch.setContentDescription("Ver en vivo " + channel.name);
            if(focusTarget == null || channel.id.equals(focusedChannelId)) focusTarget = watch;
            LinearLayout actions = new LinearLayout(this);
            Button star = button(favorite ? "★ Favorito" : "☆ Favorito",() -> {
                rememberHome(channel); repository.submit(() -> { repository.store.favorite(channel,!favorite); return null; },(v,e) -> { if(e!=null) Toast.makeText(this,"No se pudo guardar el favorito.",Toast.LENGTH_LONG).show(); });
            }); star.setContentDescription((favorite ? "Quitar de favoritos " : "Agregar a favoritos ") + channel.name);
            addControl(actions,star);
            addControl(actions,button("Resultado",() -> markResult(channel,false)));
            HorizontalScrollView actionsScroll = new HorizontalScrollView(this); actionsScroll.addView(actions); card.addView(actionsScroll);
        }
        if(count == 0) page.addView(text("No hay canales en este filtro. Prueba otra categoría o vuelve a todos los canales.",18,Color.LTGRAY));
        page.addView(text("Mi TV · 0.4.0",13,Color.GRAY));
        addButton(page,button("Actualizar canales",() -> refreshCatalog(true)));
        addButton(page,button("Buscar actualización de la app",() -> appUpdates.check(this,network)));
        addButton(page,button("Ayuda",() -> new AlertDialog.Builder(this).setTitle("Mi TV")
            .setMessage("Usa las flechas y OK del control, o toca la pantalla.\n\nVer en vivo abre el reproductor o la página oficial del canal. Algunos piden Play, activar sonido o aceptar cookies.\n\nEn las páginas oficiales usa sus propios controles. Ampliar vista oculta los botones de Mi TV; Atrás los recupera. MENU también alterna la vista.\n\nResultado guarda si cada canal funciona en este equipo. Las pruebas y favoritos del celular y el TV Box son independientes.\n\nLa lista se revisa por internet al abrir Mi TV. Actualizar canales permite revisarla de nuevo. Si no hay conexión, se conserva la última lista válida.\n\nBuscar actualización de la app descarga un APK compatible desde GitHub; Android confirma la instalación.\n\nSi un canal falla, pulsa Recargar o Página oficial. La app no está afiliada a los canales ni almacena o retransmite las señales.")
            .setPositiveButton("Entendido",null).show()));
        if(focusTarget != null) focusTarget.requestFocus();
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
        stopPlayer(); currentChannel = channel; playerSuspended = false; root.removeAllViews();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        playerLayout = column(); root.addView(playerLayout,new FrameLayout.LayoutParams(-1,-1));
        HorizontalScrollView controlsScroll = new HorizontalScrollView(this);
        LinearLayout controls = new LinearLayout(this); controls.setPadding(dp(8),dp(4),dp(8),dp(4));
        controlsScroll.addView(controls); playerLayout.addView(controlsScroll);
        addControl(controls,button("Canales",this::showHome));
        addControl(controls,button("Play / pausa",this::togglePlayback));
        addControl(controls,button("Ampliar vista",this::toggleFullscreen));
        addControl(controls,button("Recargar",() -> { if(web != null) web.loadUrl(channel.playerUrl); else if(nativePlayer!=null) { nativePlayer.prepare(); nativePlayer.play(); } }));
        addControl(controls,button("Resultado",() -> markResult(channel,true)));
        addControl(controls,button("Página oficial",() -> openBrowser(channel.siteUrl)));
        final TextView playerStatus = text("Cargando " + channel.name + "…",13,Color.LTGRAY);
        status = playerStatus; status.setPadding(dp(12),0,dp(12),dp(4)); playerLayout.addView(status);
        final ProgressBar playerProgress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress = playerProgress; playerLayout.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        if("STREAM".equals(channel.mode)) {
            nativePlayer=new ExoPlayer.Builder(this).build();
            nativePlayer.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_MEDIA).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
            PlayerView video=new PlayerView(this); video.setPlayer(nativePlayer);
            playerLayout.addView(video,new LinearLayout.LayoutParams(-1,0,1));
            nativePlayer.addListener(new androidx.media3.common.Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    playerProgress.setVisibility(state==androidx.media3.common.Player.STATE_BUFFERING?View.VISIBLE:View.GONE);
                    playerStatus.setText(channel.name+" · Controles del reproductor");
                }
                @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    playerProgress.setVisibility(View.GONE); playerStatus.setText("No se pudo reproducir. Revisa la URL y el modo en Editar.");
                }
            });
            nativePlayer.setMediaItem(MediaItem.fromUri(channel.playerUrl)); nativePlayer.prepare(); nativePlayer.play(); video.requestFocus();
            return;
        }
        web = new WebView(this); web.setBackgroundColor(Color.BLACK);
        WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true); settings.setLoadWithOverviewMode(true); settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // Official pages often use third-party video providers. Their cookie notices remain intact.
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebViewClient(new WebViewClient() {
            private boolean failed;
            private void fail() {
                failed = true; playerProgress.setVisibility(View.GONE);
                playerStatus.setText("No se pudo cargar " + channel.name + ". Prueba Recargar o Página oficial.");
            }
            @Override public void onPageStarted(WebView view,String page,android.graphics.Bitmap icon) {
                failed = false; playerProgress.setVisibility(View.VISIBLE); playerStatus.setText("Cargando " + channel.name + "…");
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
                if(!request.isForMainFrame()) return false;
                String target = request.getUrl().toString();
                if(NavigationPolicy.isChannelUrl(target,channel.playerUrl,channel.siteUrl)) return false;
                if(request.hasGesture() && "https".equalsIgnoreCase(request.getUrl().getScheme())) openBrowser(target);
                return true;
            }
            @Override public void onPageFinished(WebView view,String page) {
                if(!failed) playerStatus.setText(channel.name + (channel.directPlayer
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
        playerLayout.addView(web,new LinearLayout.LayoutParams(-1,0,1)); web.loadUrl(channel.playerUrl); web.requestFocus();
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
        status.setVisibility(visible ? View.GONE : View.VISIBLE); setFullscreenUi(visible); if(web!=null) web.requestFocus();
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
        hideCustomView();
        if(nativePlayer!=null) { nativePlayer.release(); nativePlayer=null; }
        if(web != null) {
            web.stopLoading(); web.setWebChromeClient(null); web.setWebViewClient(new WebViewClient()); web.loadUrl("about:blank");
            if(web.getParent() instanceof ViewGroup) ((ViewGroup)web.getParent()).removeView(web);
            web.destroy(); web = null;
        }
        playerSuspended = false; currentChannel = null; setFullscreenUi(false);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
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
        state.putBoolean("movies",showMovies); state.putString("category",selectedCategory); state.putBoolean("favorites",favoritesOnly);
        state.putString("focus",focusedChannelId); state.putInt("scroll",web == null && homeScroll != null ? homeScroll.getScrollY() : homeScrollY);
    }
    @Override protected void onPause() {
        if(nativePlayer!=null) { resumeNative=nativePlayer.isPlaying(); nativePlayer.pause(); }
        if(web != null) {
            // Navigating to blank stops audio inside cross-origin player iframes as well.
            hideCustomView(); web.loadUrl("about:blank"); web.onPause(); playerSuspended = true;
        }
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if(nativePlayer!=null && resumeNative) { resumeNative=false; nativePlayer.play(); }
        if(web != null) {
            web.onResume();
            if(playerSuspended && currentChannel != null) { playerSuspended = false; web.loadUrl(currentChannel.playerUrl); }
        }
    }
    @Override protected void onDestroy() { if(catalogLive!=null && catalogObserver!=null) catalogLive.removeObserver(catalogObserver); network.shutdownNow(); stopPlayer(); super.onDestroy(); }
}
