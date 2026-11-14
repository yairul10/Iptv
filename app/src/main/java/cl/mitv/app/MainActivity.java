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
    private FrameLayout videoHost, browserChrome;
    private LinearLayout browserHeader, browserGroups, browserChannels;
    private ScrollView groupScroll, channelScroll;
    private View videoTarget;
    private boolean browserVisible=true;
    private TextView browserNow;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private TextView status;
    private ProgressBar progress;
    private CatalogItem currentChannel;
    private String selectedCategory = "Todos", focusedChannelId = "";
    private boolean favoritesOnly, recentOnly, pendingOnly;
    private final LinkedHashSet<String> seenChannelIds=new LinkedHashSet<>();
    private final LinkedHashSet<String> recentChannelIds=new LinkedHashSet<>();
    private android.content.SharedPreferences channelTracking;
    private boolean trackingReady;
    private void trackChannels(List<CatalogItem> rows) {
        if(rows==null || (!trackingReady && rows.isEmpty())) return;
        boolean changed=!trackingReady;
        for(CatalogItem item:rows) if(!item.movie && seenChannelIds.add(item.id)) {
            if(trackingReady) recentChannelIds.add(item.id);
            changed=true;
        }
        if(changed) {
            trackingReady=true;
            channelTracking.edit().putBoolean("initialized",true)
                .putStringSet("seen",new LinkedHashSet<>(seenChannelIds))
                .putStringSet("recent",new LinkedHashSet<>(recentChannelIds)).apply();
        }
    }
    private boolean matchesReviewFilter(CatalogItem item) {
        return (!pendingOnly || item.testResult==0)
            && (!recentOnly || (recentChannelIds.contains(item.id) && item.testResult==0));
    }
    private List<CatalogItem> reviewFiltered(List<CatalogItem> rows) {
        List<CatalogItem> filtered=new ArrayList<>();
        for(CatalogItem item:rows) if(matchesReviewFilter(item)) filtered.add(item);
        return filtered;
    }
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
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        repository = CatalogRepository.get(this);
        channelTracking=getSharedPreferences("channel_review_tracking",MODE_PRIVATE);
        trackingReady=channelTracking.getBoolean("initialized",false);
        seenChannelIds.addAll(channelTracking.getStringSet("seen",java.util.Collections.emptySet()));
        recentChannelIds.addAll(channelTracking.getStringSet("recent",java.util.Collections.emptySet()));
        if(state != null) {
            selectedCategory = state.getString("category", "Todos");
            favoritesOnly = state.getBoolean("favorites", false);
            recentOnly=state.getBoolean("recent",false); pendingOnly=state.getBoolean("pending",false);
            showMovies = false; // Movies are hidden in this release.
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
            trackChannels(rows);
            channels.clear(); if(rows != null) channels.addAll(rows);
            if(currentChannel!=null) for(CatalogItem item:channels) if(item.id.equals(currentChannel.id)) { currentChannel=item; break; }
            if(ready && !selecting && browserChrome!=null) {
                renderBrowserLists();
            } else if(ready && web == null && nativePlayer == null) {
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
        if(selecting) { showLegacyHome(); return; }
        ensureBrowser();
        if(currentChannel==null && videoHost.getChildCount()==0) {
            TextView prompt=text("Elige un canal para comenzar",18,Color.LTGRAY); prompt.setGravity(android.view.Gravity.CENTER);
            videoHost.addView(prompt,new FrameLayout.LayoutParams(-1,-1));
        }
        renderBrowserLists(); setBrowserVisible(true);
    }
    private void ensureBrowser() {
        if(videoHost!=null && videoHost.getParent()==root) return;
        root.removeAllViews();
        videoHost=new FrameLayout(this); videoHost.setBackgroundColor(Color.BLACK);
        root.addView(videoHost,new FrameLayout.LayoutParams(-1,-1));
        TextView prompt=text("Elige un canal para comenzar",18,Color.LTGRAY);
        prompt.setGravity(android.view.Gravity.CENTER); videoHost.addView(prompt,new FrameLayout.LayoutParams(-1,-1));
        browserChrome=new FrameLayout(this); root.addView(browserChrome,new FrameLayout.LayoutParams(-1,-1));
        browserHeader=new LinearLayout(this); browserHeader.setPadding(dp(12),0,dp(8),0); browserHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        browserHeader.setBackgroundColor(Color.argb(235,11,18,32)); browserChrome.addView(browserHeader);
        TextView brand=text("Popes TV",22,Color.WHITE); brand.setTypeface(null,Typeface.BOLD);
        browserHeader.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button options=button("⚙",this::showHomeOptions); options.setContentDescription("Opciones y configuración"); options.setMinWidth(0); options.setMinimumWidth(0);
        browserHeader.addView(options,new LinearLayout.LayoutParams(dp(48),dp(48)));
        groupScroll=new ScrollView(this); browserGroups=column(); browserGroups.setPadding(dp(6),dp(6),dp(6),dp(6));
        browserGroups.setBackgroundColor(Color.argb(230,11,18,32)); groupScroll.addView(browserGroups); browserChrome.addView(groupScroll);
        channelScroll=new ScrollView(this); browserChannels=column(); browserChannels.setPadding(dp(8),dp(6),dp(8),dp(6));
        browserChannels.setBackgroundColor(Color.argb(230,11,18,32)); channelScroll.addView(browserChannels); browserChrome.addView(channelScroll);
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> layoutBrowser());
        root.post(this::layoutBrowser);
    }
    private void place(View view,int width,int height,int left,int top) {
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(width,height); params.leftMargin=left; params.topMargin=top; view.setLayoutParams(params);
    }
    private void layoutBrowser() {
        if(videoHost==null || browserChrome==null || videoHost.getParent()!=root) return;
        int width=root.getWidth(),height=root.getHeight(); if(width==0||height==0) return;
        if(!browserVisible) { place(videoHost,width,height,0,0); return; }
        int header=Math.min(dp(56),height/5); place(browserHeader,width,header,0,0);
        if(width>height) {
            int left=Math.min(dp(210),width*22/100),right=Math.min(dp(320),width*30/100);
            place(groupScroll,left,height-header,0,header); place(channelScroll,right,height-header,width-right,header);
            place(videoHost,width-left-right,height-header,left,header);
        } else {
            int preview=Math.min(width*9/16,(height-header)*45/100),left=width*34/100;
            place(videoHost,width,preview,0,header);
            place(groupScroll,left,height-header-preview,0,header+preview);
            place(channelScroll,width-left,height-header-preview,left,header+preview);
        }
    }
    private void setBrowserVisible(boolean visible) {
        browserVisible=visible;
        if(browserChrome==null) return;
        browserChrome.setVisibility(visible?View.VISIBLE:View.GONE);
        if(status!=null) status.setVisibility(visible?View.VISIBLE:View.GONE);
        setFullscreenUi(!visible); layoutBrowser();
        if(videoTarget!=null) {
            videoTarget.requestFocus(); videoTarget.setBackground(background(Color.TRANSPARENT,visible?MINT:Color.TRANSPARENT));
        }
    }
    private void toggleBrowser() { if(currentChannel!=null) setBrowserVisible(!browserVisible); }
    private void browserFilter(int filter) {
        favoritesOnly=filter==1; recentOnly=filter==2; pendingOnly=filter==3;
        selectedCategory="Todos"; searchQuery=""; focusedChannelId=""; renderBrowserLists(); groupScroll.requestFocus();
    }
    private void renderBrowserLists() {
        if(browserChrome==null) return;
        browserGroups.removeAllViews(); browserChannels.removeAllViews();
        addButton(browserGroups,button(!favoritesOnly&&!recentOnly&&!pendingOnly?"✓ Todos":"Todos",() -> browserFilter(0)));
        addButton(browserGroups,button(favoritesOnly?"✓ Favoritos":"Favoritos",() -> browserFilter(1)));
        addButton(browserGroups,button(recentOnly?"✓ Recién agregados":"Recién agregados",() -> browserFilter(2)));
        addButton(browserGroups,button(pendingOnly?"✓ Sin revisar":"Sin revisar",() -> browserFilter(3)));
        browserGroups.addView(text("GRUPOS",12,MINT));
        LinkedHashSet<String> groups=new LinkedHashSet<>(); groups.add("Todos");
        for(CatalogItem item:channels) if(!item.movie) groups.add(item.category);
        for(String group:groups) addButton(browserGroups,button((selectedCategory.equals(group)?"✓ ":"")+group,() -> {
            selectedCategory=group; renderBrowserLists(); channelScroll.requestFocus();
        }));
        browserNow=text(currentChannel==null?"Canales":currentChannel.name,16,MINT); browserNow.setMaxLines(2); browserChannels.addView(browserNow);
        if(currentChannel!=null) addButton(browserChannels,button("Opciones del canal",this::showChannelOptions));
        EditText search=new EditText(this); search.setSingleLine(true); search.setTextColor(Color.WHITE); search.setHintTextColor(Color.LTGRAY);
        search.setHint("Buscar canal"); search.setContentDescription("Buscar canal por nombre"); search.setTextSize(15);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE); search.setText(searchQuery); browserChannels.addView(search);
        LinearLayout list=column(); browserChannels.addView(list);
        TextView empty=text("No hay canales en este filtro",15,Color.LTGRAY); browserChannels.addView(empty);
        int count=0; View playing=null;
        for(CatalogItem item:channels) {
            if(item.movie || !matchesReviewFilter(item) || (favoritesOnly&&!item.favorite)
                || (!"Todos".equals(selectedCategory)&&!selectedCategory.equals(item.category))) continue;
            boolean active=currentChannel!=null&&currentChannel.id.equals(item.id);
            Button entry=button((active?"▶ ":"")+channelLabel(item),() -> {
                rememberHome(item);
                if(currentChannel!=null&&currentChannel.id.equals(item.id)) setBrowserVisible(false);
                else { showPlayer(item); setBrowserVisible(true); }
            });
            entry.setTextSize(15); entry.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
            entry.setContentDescription((active?"Ampliar ":"Ver ")+item.name); entry.setTag(item);
            if(active) { entry.setTextColor(MINT); playing=entry; }
            entry.setOnLongClickListener(v -> { if(currentChannel!=null&&currentChannel.id.equals(item.id)) showChannelOptions(); else markResult(item,false); return true; });
            addButton(list,entry); boolean matches=CatalogSearch.matches(item.name,searchQuery); entry.setVisibility(matches?View.VISIBLE:View.GONE); if(matches) count++;
        }
        empty.setVisibility(count==0?View.VISIBLE:View.GONE);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence v,int start,int count,int after) { }
            public void afterTextChanged(Editable v) { }
            public void onTextChanged(CharSequence value,int start,int before,int count) {
                searchQuery=value.toString(); int visible=0;
                for(int i=0;i<list.getChildCount();i++) { View row=list.getChildAt(i); CatalogItem item=(CatalogItem)row.getTag();
                    boolean matches=CatalogSearch.matches(item.name,searchQuery); row.setVisibility(matches?View.VISIBLE:View.GONE); if(matches) visible++; }
                empty.setVisibility(visible==0?View.VISIBLE:View.GONE);
            }
        });
        if(playing!=null) { final View target=playing; channelScroll.post(() -> { if(target.getParent()!=null) channelScroll.smoothScrollTo(0,Math.max(0,list.getTop()+target.getTop()-dp(120))); }); }
    }
    private void showChannelOptions() {
        final CatalogItem item=currentChannel; if(item==null) return;
        new AlertDialog.Builder(this).setTitle(item.name).setItems(new String[]{"Resultado de la prueba",item.favorite?"Quitar de favoritos":"Añadir a favoritos","Recargar","Interactuar con página web","Página oficial","Borrar canal"},(d,which) -> {
            switch(which) {
                case 0: markResult(item,true); break;
                case 1: repository.submit(() -> { repository.store.favorite(item,!item.favorite); return null; },(v,e) -> { if(e!=null) Toast.makeText(this,"No se pudo guardar el favorito",Toast.LENGTH_SHORT).show(); }); break;
                case 2: if(web!=null) web.reload(); else if(nativePlayer!=null) { nativePlayer.prepare(); nativePlayer.play(); } break;
                case 3: if(web!=null) { if(videoTarget!=null) videoTarget.setVisibility(View.GONE); web.requestFocus(); Toast.makeText(this,"Atrás recupera las listas",Toast.LENGTH_SHORT).show(); } break;
                case 4: openBrowser(item.siteUrl); break;
                case 5: confirmDeletePlaying(item); break;
            }
        }).setNegativeButton("Cerrar",null).show();
    }
    private void installVideoTarget() {
        Button target=new Button(this); target.setText(""); target.setBackgroundColor(Color.TRANSPARENT); target.setFocusable(true);
        target.setContentDescription("Ampliar video o mostrar lista de canales"); target.setOnClickListener(v -> toggleBrowser());
        target.setOnFocusChangeListener((v,focused) -> target.setBackground(background(Color.TRANSPARENT,focused&&browserVisible?MINT:Color.TRANSPARENT)));
        videoTarget=target; videoHost.addView(target,new FrameLayout.LayoutParams(-1,-1)); target.requestFocus();
    }
    private void showLegacyHome() {
        showMovies = false;
        boolean restoreSearchFocus=searchInput!=null && searchInput.hasFocus() && currentChannel==null;
        int searchCursor=restoreSearchFocus?searchInput.getSelectionStart():searchQuery.length();
        stopPlayer(); root.removeAllViews(); videoHost=null; browserChrome=null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        homeScroll = new ScrollView(this); homeScroll.setFillViewport(true);
        LinearLayout page = column(); page.setPadding(dp(16),dp(20),dp(16),dp(20));
        homeScroll.addView(page); root.addView(homeScroll);
        LinearLayout header = new LinearLayout(this); header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout brand = column();
        TextView heading = text("Popes TV",28,Color.WHITE); heading.setTypeface(null,Typeface.BOLD); brand.addView(heading);
        long total=0; for(CatalogItem item:channels) if(!item.movie) total++;
        brand.addView(text(total + " canales · Elige qué ver",14,Color.LTGRAY));
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button menu=button("⚙",this::showHomeOptions); menu.setTextSize(24); menu.setMinWidth(0); menu.setMinimumWidth(0);
        menu.setContentDescription("Opciones y configuración");
        header.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48))); page.addView(header);
        HorizontalScrollView browsingScroll=new HorizontalScrollView(this); browsingScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout browsing = new LinearLayout(this); browsingScroll.addView(browsing);
        Button all=button(!favoritesOnly&&!recentOnly&&!pendingOnly?"✓ Todos":"Todos",() -> setReviewFilter(0));
        Button favorites=button(favoritesOnly?"✓ Favoritos":"Favoritos",() -> setReviewFilter(1));
        addControl(browsing,all); addControl(browsing,favorites);
        int newCount=0,pendingCount=0;
        for(CatalogItem item:channels) if(!item.movie && item.testResult==0) {
            pendingCount++; if(recentChannelIds.contains(item.id)) newCount++;
        }
        addControl(browsing,button((recentOnly?"✓ ":"")+"Recién agregados ("+newCount+")",() -> setReviewFilter(2)));
        addControl(browsing,button((pendingOnly?"✓ ":"")+"Sin revisar ("+pendingCount+")",() -> setReviewFilter(3)));
        LinearLayout.LayoutParams browseParams=new LinearLayout.LayoutParams(-1,-2); browseParams.setMargins(0,dp(16),0,dp(8)); page.addView(browsingScroll,browseParams);
        if(selecting) addButton(page,button("Cancelar selección",() -> { if(!deleting) { selecting=false; selectedIds.clear(); showHome(); } }));
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
        searchRow.setBackground(background(CARD,Color.TRANSPARENT)); searchRow.setPadding(dp(12),dp(2),dp(4),dp(2));
        searchInput.setBackgroundColor(Color.TRANSPARENT); searchInput.setTextSize(16);
        searchInput.setText(searchQuery); searchRow.addView(searchInput,new LinearLayout.LayoutParams(0,dp(56),1));
        Button clearSearch=button("×",() -> { searchInput.setText(""); searchInput.requestFocus(); });
        clearSearch.setTextSize(24); clearSearch.setMinWidth(0); clearSearch.setMinimumWidth(0); clearSearch.setContentDescription("Limpiar búsqueda");
        searchRow.addView(clearSearch,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(-1,-2); searchParams.setMargins(0,dp(12),0,dp(12)); page.addView(searchRow,searchParams);
        catalogRows=column(); page.addView(catalogRows);
        int count = 0; View focusTarget = null;
        for(CatalogItem channel : channels) {
            if(channel.movie!=showMovies || !matchesReviewFilter(channel)) continue;
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
        emptySearch=text(recentOnly?"No hay canales nuevos pendientes. Los que agregues desde esta actualización aparecerán aquí hasta marcarlos en Resultado."
            :pendingOnly?"No hay canales pendientes en este filtro. Usa Resultado para marcar Funciona o No funciona."
            :"No hay resultados. Prueba otro nombre, limpia la búsqueda o cambia los filtros.",18,Color.LTGRAY); page.addView(emptySearch);
        filterSearchRows();
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence value,int start,int count,int after) { }
            public void onTextChanged(CharSequence value,int start,int before,int count) { searchQuery=value.toString(); filterSearchRows(); }
            public void afterTextChanged(Editable value) { }
        });
        if(restoreSearchFocus) { searchInput.requestFocus(); searchInput.setSelection(Math.max(0,Math.min(searchCursor,searchInput.length()))); }
        else if(focusTarget!=null && focusTarget.isShown()) focusTarget.requestFocus();
        else searchInput.requestFocus();
        final ScrollView scroll = homeScroll;
        scroll.post(() -> scroll.scrollTo(0,homeScrollY));
    }
    private void setReviewFilter(int filter) {
        favoritesOnly=filter==1; recentOnly=filter==2; pendingOnly=filter==3;
        selectedCategory="Todos"; searchQuery=""; homeScrollY=0; focusedChannelId=""; showHome();
    }
    private void showHomeOptions() {
        String[] options={"Agregar / editar / importar canales","Seleccionar canales para eliminar","Ajustes","Actualizar canales","Buscar actualización de la app","Diagnóstico","Ayuda"};
        new AlertDialog.Builder(this).setTitle("Popes TV · "+BuildConfig.VERSION_NAME)
            .setItems(options,(dialog,which) -> {
                switch(which) {
                    case 0: startActivity(new Intent(this,CatalogManagerActivity.class).putExtra("movies",false)); break;
                    case 1:
                        if(!deleting) PinGate.require(this,() -> { selecting=true; selectedIds.clear(); homeScrollY=0; showHome(); },() -> { });
                        break;
                    case 2: startActivity(new Intent(this,SettingsActivity.class)); break;
                    case 3: refreshCatalog(true); break;
                    case 4: appUpdates.check(this,network); break;
                    case 5: startActivity(new Intent(this,DiagnosticsActivity.class)); break;
                    case 6: showHelp(); break;
                }
            }).setNegativeButton("Cerrar",null).show();
    }
    private void showHelp() {
        new AlertDialog.Builder(this).setTitle("Popes TV")
            .setMessage("Elige un grupo a la izquierda y un canal a la derecha.\n\nToca el video o pulsa OK sobre él para ampliar. Otro toque, OK o Atrás recupera las listas sin detener el canal. En pantalla completa, las flechas laterales cambian de canal.\n\nLas opciones del canal permiten guardar Resultado, favoritos, recargar o borrar. Si una página requiere Play o aceptar cookies, usa Interactuar con página web; Atrás recupera las listas.\n\nEl botón de ajustes abre la administración, importación, configuración y actualizaciones.\n\nRecién agregados y Sin revisar conservan los pendientes hasta que marques Funciona o No funciona. La app no almacena ni retransmite las señales.")
            .setPositiveButton("Entendido",null).show();
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
        return matchesReviewFilter(item) && CatalogSearch.matches(item.name,searchQuery) && item.movie==showMovies && (!favoritesOnly || item.favorite) && ("Todos".equals(selectedCategory) || selectedCategory.equals(item.category));
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
        if(deleting || currentChannel==null || !currentChannel.id.equals(channel.id)) return;
        PinGate.require(this,() -> {
            if(isFinishing() || isDestroyed() || currentChannel==null || !currentChannel.id.equals(channel.id)) return;
            new AlertDialog.Builder(this).setTitle(channel.movie?"Borrar película":"Borrar canal")
                .setMessage("¿Quieres quitar \""+channel.name+"\" de tu lista? La sincronización no volverá a añadirlo.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Borrar",(dialog,which) -> {
                    if(deleting) return;
                    deleting=true;
                    ArrayList<CatalogItem> before=new ArrayList<>(channels);
                    boolean expanded=customView!=null || !browserVisible;
                    repository.submit(() -> {
                        repository.store.deleteMany(java.util.Collections.singletonList(channel.id),channel.movie);
                        return repository.store.dao.catalog();
                    },(remaining,error) -> {
                        if(isFinishing() || isDestroyed()) return;
                        deleting=false;
                        if(error==null) {
                            Toast.makeText(this,channel.movie?"Película eliminada.":"Canal eliminado.",Toast.LENGTH_SHORT).show();
                            if(currentChannel!=null && currentChannel.id.equals(channel.id)) {
                                CatalogItem next=ChannelNavigation.afterDeletion(before,channel,reviewFiltered(remaining),selectedCategory,favoritesOnly,searchQuery);
                                channels.clear(); channels.addAll(remaining);
                                if(next!=null) { rememberHome(next); showPlayer(next); setBrowserVisible(!expanded); }
                                else { stopPlayer(); videoHost.removeAllViews(); homeScrollY=0; showHome(); }
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
        stopPlayer(); currentChannel = channel; playerSuspended = false; ensureBrowser(); videoHost.removeAllViews();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        playerLayout = column(); videoHost.addView(playerLayout,new FrameLayout.LayoutParams(-1,-1));
        HorizontalScrollView controlsScroll = new HorizontalScrollView(this);
        LinearLayout controls = new LinearLayout(this); controls.setPadding(dp(8),dp(4),dp(8),dp(4));
        controlsScroll.addView(controls); playerLayout.addView(controlsScroll);
        controlsScroll.setVisibility(View.GONE);
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
            video.setPlayer(nativePlayer); video.setUseController(false);
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
            installVideoTarget(); renderBrowserLists(); setBrowserVisible(browserVisible);
            return;
        }
        web = new WebView(this); web.setBackgroundColor(Color.BLACK);
        WebSettings settings = web.getSettings(); if(!PlaybackSettings.current.userAgent.isEmpty()) settings.setUserAgentString(PlaybackSettings.current.userAgent); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false); settings.setLoadWithOverviewMode(true); settings.setUseWideViewPort(true);
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
                if(!failed) {
                    playerStatus.setText(channelLabel(channel)+" · OK o toca el video para ampliar");
                    if(channel.directPlayer) view.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){v.play().catch(function(){});}})()",null);
                }
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error) { if(request.isForMainFrame()) fail(); }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response) { if(request.isForMainFrame()) fail(); }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view,int value) { playerProgress.setProgress(value); playerProgress.setVisibility(value == 100 ? View.GONE : View.VISIBLE); }
            @Override public void onShowCustomView(View view,CustomViewCallback callback) {
                if(customView != null) { callback.onCustomViewHidden(); return; }
                customView = view; customCallback = callback;
                playerLayout.setVisibility(View.GONE); videoHost.addView(view,new FrameLayout.LayoutParams(-1,-1));
                if(videoTarget!=null) videoTarget.bringToFront(); setBrowserVisible(false);
            }
            @Override public void onHideCustomView() { hideCustomView(); }
        });
        playerLayout.addView(web,new LinearLayout.LayoutParams(-1,0,1)); web.loadUrl(channel.playerUrl,PlaybackSettings.current.headers(channel.playerUrl)); web.requestFocus();
        installVideoTarget(); renderBrowserLists(); setBrowserVisible(browserVisible);
    }
    private String channelLabel(CatalogItem item) {
        int number=ChannelNavigation.number(channels,item);
        return number>0 ? number+" · "+item.name : item.name;
    }
    private void switchChannel(int direction) {
        CatalogItem next=ChannelNavigation.adjacent(reviewFiltered(channels),currentChannel,direction,selectedCategory,favoritesOnly,searchQuery);
        if(next==null) { Toast.makeText(this,"No hay otros canales en este filtro.",Toast.LENGTH_SHORT).show(); return; }
        if(next.id.equals(currentChannel.id)) { Toast.makeText(this,"Solo hay un canal en este filtro.",Toast.LENGTH_SHORT).show(); return; }
        boolean expanded=customView!=null || !browserVisible;
        rememberHome(next); showPlayer(next);
        setBrowserVisible(!expanded);
    }
    private void addControl(LinearLayout row,Button b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2,-2); p.setMargins(dp(3),0,dp(3),0); row.addView(b,p);
    }
    private void toggleFullscreen() { toggleBrowser(); }
    private void setFullscreenUi(boolean enabled) {
        getWindow().getDecorView().setSystemUiVisibility(enabled ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY : View.SYSTEM_UI_FLAG_VISIBLE);
    }
    private void hideCustomView() {
        if(customView == null) return;
        videoHost.removeView(customView); customView = null;
        if(playerLayout != null) playerLayout.setVisibility(View.VISIBLE);
        WebChromeClient.CustomViewCallback cb = customCallback; customCallback = null;
        if(cb != null) cb.onCustomViewHidden(); setFullscreenUi(!browserVisible);
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
        videoTarget=null; playerSuspended = false; resumeNative=false; currentChannel = null; setFullscreenUi(false);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(currentChannel!=null && !currentChannel.movie && (event.getKeyCode()==KeyEvent.KEYCODE_CHANNEL_UP || event.getKeyCode()==KeyEvent.KEYCODE_CHANNEL_DOWN)) {
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) switchChannel(event.getKeyCode()==KeyEvent.KEYCODE_CHANNEL_UP?1:-1);
            return true;
        }
        if(currentChannel!=null && !browserVisible && (event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode()==KeyEvent.KEYCODE_ENTER)) {
            if(event.getAction()==KeyEvent.ACTION_UP) { hideCustomView(); if(videoTarget!=null) videoTarget.setVisibility(View.VISIBLE); setBrowserVisible(true); } return true;
        }
        if(currentChannel!=null && !browserVisible && (event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT || event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) switchChannel(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        if(currentChannel!=null && key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { toggleBrowser(); return true; }
        if((web != null || nativePlayer != null) && key == KeyEvent.KEYCODE_MENU) { toggleFullscreen(); return true; }
        return super.onKeyDown(key,event);
    }
    @Override public void onBackPressed() {
        if(selecting && currentChannel==null) { if(!deleting) { selecting=false; selectedIds.clear(); showHome(); } return; }
        if(customView != null) { hideCustomView(); setBrowserVisible(true); return; }
        if(videoTarget!=null && videoTarget.getVisibility()!=View.VISIBLE) { videoTarget.setVisibility(View.VISIBLE); setBrowserVisible(true); return; }
        if(currentChannel!=null && !browserVisible) { setBrowserVisible(true); return; }
        if(currentChannel!=null) { stopPlayer(); videoHost.removeAllViews(); showHome(); return; }
        if(favoritesOnly || recentOnly || pendingOnly || !selectedCategory.equals("Todos")) {
            favoritesOnly = false; recentOnly=false; pendingOnly=false; selectedCategory = "Todos"; homeScrollY = 0; showHome(); return;
        }
        super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("searchQuery",searchQuery);
        state.putBoolean("selecting",selecting); state.putStringArrayList("selectedIds",new ArrayList<>(selectedIds));
        state.putBoolean("recent",recentOnly); state.putBoolean("pending",pendingOnly);
        state.putBoolean("movies",showMovies); state.putString("category",selectedCategory); state.putBoolean("favorites",favoritesOnly);
        state.putString("focusAction",focusedAction); state.putString("focus",focusedChannelId); state.putInt("scroll",web == null && homeScroll != null ? homeScroll.getScrollY() : homeScrollY);
    }
    @Override protected void onPause() {
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
