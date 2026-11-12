package cl.mitv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Personal launchpad using the public player linked by Canal 9 itself. No stream extraction. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(11,18,32), CARD = Color.rgb(23,35,53), MINT = Color.rgb(105,227,197);
    private static final String PLAYER = "https://rudo.video/live/c9";
    private static final String SITE = "https://www.canal9.cl/";
    private SharedPreferences prefs;
    private FrameLayout root;
    private WebView web;
    private LinearLayout playerLayout;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private TextView status;
    private ProgressBar progress;
    private boolean favoritesOnly;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("mi_tv", MODE_PRIVATE);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        showHome();
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
        b.setBackground(background(CARD, Color.TRANSPARENT)); b.setFocusable(true);
        b.setOnFocusChangeListener((v,focused) -> b.setBackground(background(focused ? Color.rgb(32,66,76) : CARD,focused ? MINT : Color.TRANSPARENT)));
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private void addButton(LinearLayout parent, Button b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(6),0,dp(6)); parent.addView(b,p);
    }
    private void showHome() {
        stopPlayer(); root.removeAllViews();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout page = column(); page.setPadding(dp(24),dp(24),dp(24),dp(24));
        scroll.addView(page); root.addView(scroll);
        TextView heading = text("Mi TV",36,Color.WHITE); heading.setTypeface(null,Typeface.BOLD); page.addView(heading);
        page.addView(text("Tu televisión, a un toque.",17,Color.LTGRAY));
        addButton(page,button(favoritesOnly ? "Ver todos los canales" : "Ver favoritos",() -> { favoritesOnly = !favoritesOnly; showHome(); }));
        boolean favorite = prefs.getBoolean("c9_favorite",false);
        if (!favoritesOnly || favorite) {
            LinearLayout card = column(); card.setPadding(dp(20),dp(16),dp(20),dp(16)); card.setBackground(background(CARD,Color.TRANSPARENT));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1,-2); cp.setMargins(0,dp(18),0,dp(18)); page.addView(card,cp);
            card.addView(text("9  /  BÍO BÍO",14,MINT));
            TextView title = text("Canal 9 Bío Bío",27,Color.WHITE); title.setTypeface(null,Typeface.BOLD); card.addView(title);
            card.addView(text("Noticias y programas de tu región",16,Color.LTGRAY));
            Button watch = button("Ver en vivo",() -> showPlayer(PLAYER)); addButton(card,watch);
            addButton(card,button(favorite ? "Quitar de favoritos" : "Agregar a favoritos",() -> {
                prefs.edit().putBoolean("c9_favorite",!favorite).apply(); showHome();
            }));
            addButton(card,button("Abrir página oficial",() -> openBrowser(SITE)));
            watch.requestFocus();
        } else { page.addView(text("Aún no tienes favoritos. Vuelve a todos los canales para agregar uno.",18,Color.LTGRAY)); }
        page.addView(text("Versión de prueba · 0.1.0",13,Color.GRAY));
        addButton(page,button("Ayuda",() -> new AlertDialog.Builder(this).setTitle("Mi TV")
            .setMessage("Usa las flechas y OK del control, o toca la pantalla.\n\nCanal 9 se abre mediante el reproductor enlazado en su sitio oficial. Puede requerir tocar Play o activar el sonido.\n\nLa disponibilidad depende del canal y de Android System WebView. Si no carga, prueba Abrir página oficial.\n\nEsta aplicación personal no está afiliada al canal y no retransmite ni almacena su señal.")
            .setPositiveButton("Entendido",null).show()));
    }
    @SuppressWarnings("SetJavaScriptEnabled")
    private void showPlayer(String url) {
        stopPlayer(); root.removeAllViews();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        playerLayout = column(); root.addView(playerLayout,new FrameLayout.LayoutParams(-1,-1));
        // A horizontal scroller keeps every control accessible even on a narrow phone.
        android.widget.HorizontalScrollView controlsScroll = new android.widget.HorizontalScrollView(this);
        LinearLayout controls = new LinearLayout(this); controls.setPadding(dp(8),dp(4),dp(8),dp(4));
        controlsScroll.addView(controls); playerLayout.addView(controlsScroll);
        addControl(controls,button("Canales",this::showHome));
        addControl(controls,button("Play / pausa",this::togglePlayback));
        addControl(controls,button("Pantalla completa",this::toggleFullscreen));
        addControl(controls,button("Recargar",() -> { if(web != null) web.reload(); }));
        addControl(controls,button("Página oficial",() -> openBrowser(SITE)));
        status = text("Cargando Canal 9…",13,Color.LTGRAY); status.setPadding(dp(12),0,dp(12),dp(4)); playerLayout.addView(status);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); playerLayout.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        web = new WebView(this); web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true); s.setLoadWithOverviewMode(true); s.setUseWideViewPort(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl(); String host = uri.getHost();
                if ("https".equalsIgnoreCase(uri.getScheme()) && ("rudo.video".equals(host) || "www.canal9.cl".equals(host) || "canal9.cl".equals(host))) return false;
                if ("https".equalsIgnoreCase(uri.getScheme())) openBrowser(uri.toString());
                return true;
            }
            @Override public void onPageFinished(WebView view,String page) { status.setText("Canal 9 · Pulsa Play si no comienza. Atrás para volver."); }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error) {
                if(request.isForMainFrame()) { progress.setVisibility(View.GONE); status.setText("No se pudo cargar. Comprueba internet y pulsa Recargar o Página oficial."); }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view,int value) { progress.setProgress(value); progress.setVisibility(value == 100 ? View.GONE : View.VISIBLE); }
            @Override public void onShowCustomView(View view,CustomViewCallback callback) {
                if(customView != null) { callback.onCustomViewHidden(); return; }
                customView = view; customCallback = callback;
                playerLayout.setVisibility(View.GONE); root.addView(view,new FrameLayout.LayoutParams(-1,-1)); setImmersive(true);
            }
            @Override public void onHideCustomView() { hideCustomView(); }
        });
        playerLayout.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        web.loadUrl(url); web.requestFocus();
    }
    private void addControl(LinearLayout row,Button b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2,-2); p.setMargins(dp(3),0,dp(3),0); row.addView(b,p);
    }
    private void togglePlayback() {
        if(web != null) web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){if(v.paused){v.muted=false;v.play().catch(function(){});}else{v.pause();}}})()",null);
    }
    private void toggleFullscreen() {
        if(web == null) return;
        boolean visible = playerLayout.getChildAt(0).getVisibility() == View.VISIBLE;
        playerLayout.getChildAt(0).setVisibility(visible ? View.GONE : View.VISIBLE);
        status.setVisibility(visible ? View.GONE : View.VISIBLE); setImmersive(visible); web.requestFocus();
    }
    private void setImmersive(boolean enabled) {
        getWindow().getDecorView().setSystemUiVisibility(enabled ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY : View.SYSTEM_UI_FLAG_VISIBLE);
    }
    private void hideCustomView() {
        if(customView == null) return;
        root.removeView(customView); customView = null;
        if(playerLayout != null) playerLayout.setVisibility(View.VISIBLE);
        WebChromeClient.CustomViewCallback cb = customCallback; customCallback = null;
        if(cb != null) cb.onCustomViewHidden(); setImmersive(false);
    }
    private void openBrowser(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
        catch(ActivityNotFoundException e) { Toast.makeText(this,"No hay un navegador instalado.",Toast.LENGTH_LONG).show(); }
    }
    private void stopPlayer() {
        hideCustomView();
        if(web != null) {
            web.stopLoading(); web.loadUrl("about:blank");
            if(web.getParent() instanceof ViewGroup) ((ViewGroup)web.getParent()).removeView(web);
            web.destroy(); web = null;
        }
        setImmersive(false);
    }
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        if(web != null && key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { togglePlayback(); return true; }
        if(web != null && key == KeyEvent.KEYCODE_MENU) { toggleFullscreen(); return true; }
        return super.onKeyDown(key,event);
    }
    @Override public void onBackPressed() {
        if(customView != null) { hideCustomView(); return; }
        if(web != null && playerLayout.getChildAt(0).getVisibility() == View.GONE) { toggleFullscreen(); return; }
        if(web != null) { showHome(); return; }
        if(favoritesOnly) { favoritesOnly = false; showHome(); return; }
        super.onBackPressed();
    }
    @Override protected void onPause() { if(web != null) { web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(v){v.pause();})",null); web.onPause(); } super.onPause(); }
    @Override protected void onResume() { super.onResume(); if(web != null) web.onResume(); }
    @Override protected void onDestroy() { stopPlayer(); super.onDestroy(); }
}
