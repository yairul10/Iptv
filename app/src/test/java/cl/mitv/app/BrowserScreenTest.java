package cl.mitv.app;
import android.view.*;
import android.widget.*;
import cl.mitv.app.data.CatalogItem;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class BrowserScreenTest {
 private Object get(MainActivity a,String key) throws Exception { Field f=MainActivity.class.getDeclaredField(key); f.setAccessible(true); return f.get(a); }
 private void set(MainActivity a,String key,Object value) throws Exception { Field f=MainActivity.class.getDeclaredField(key); f.setAccessible(true); f.set(a,value); }
 private void call(MainActivity a,String key) throws Exception { Method m=MainActivity.class.getDeclaredMethod(key); m.setAccessible(true); m.invoke(a); }
 private CatalogItem channel(String id,String group,int result) { CatalogItem c=new CatalogItem(); c.id=id; c.name=id; c.category=group; c.testResult=result; return c; }
 private MainActivity screen() throws Exception {
  MainActivity a=Robolectric.buildActivity(MainActivity.class).get();
  FrameLayout root=new FrameLayout(a); a.setContentView(root); set(a,"root",root);
  List<CatalogItem> items=(List<CatalogItem>)get(a,"channels"); items.add(channel("Canal probado","Noticias",1)); items.add(channel("Canal nuevo","Cultura",0));
  call(a,"ensureBrowser"); call(a,"renderBrowserLists"); root.layout(0,0,1200,700); return a;
 }
 @Test public void okAndBackToggleListsWithoutReplacingVideo() throws Exception {
  MainActivity a=screen(); set(a,"currentChannel",((List<CatalogItem>)get(a,"channels")).get(0));
  FrameLayout host=(FrameLayout)get(a,"videoHost"); View playback=new View(a); host.addView(playback); call(a,"installVideoTarget");
  ((View)get(a,"videoTarget")).performClick();
  assertEquals(false,get(a,"browserVisible")); assertEquals(View.GONE,((View)get(a,"browserChrome")).getVisibility()); assertSame(host,playback.getParent());
  a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER)); a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));
  assertEquals(true,get(a,"browserVisible")); assertSame(host,playback.getParent());
  ((View)get(a,"videoTarget")).performClick(); a.onBackPressed();
  assertEquals(true,get(a,"browserVisible")); assertSame(host,playback.getParent());
 }
 @Test public void videoUsesCenterInLandscapeAndTopInPortrait() throws Exception {
  MainActivity a=screen(); FrameLayout root=(FrameLayout)get(a,"root"); FrameLayout host=(FrameLayout)get(a,"videoHost");
  FrameLayout.LayoutParams wide=(FrameLayout.LayoutParams)host.getLayoutParams(); assertTrue(wide.leftMargin>0); assertTrue(wide.width<root.getWidth());
  root.layout(0,0,400,900); call(a,"layoutBrowser");
  FrameLayout.LayoutParams tall=(FrameLayout.LayoutParams)host.getLayoutParams(); assertEquals(0,tall.leftMargin); assertEquals(400,tall.width);
  assertTrue(((FrameLayout.LayoutParams)((View)get(a,"channelScroll")).getLayoutParams()).topMargin>=tall.topMargin+tall.height);
 }
 @Test public void pendingAndGroupFiltersKeepReviewedChannelsOutWithoutStoppingPreview() throws Exception {
  MainActivity a=screen(); set(a,"currentChannel",((List<CatalogItem>)get(a,"channels")).get(0));
  FrameLayout host=(FrameLayout)get(a,"videoHost"); View playback=new View(a); host.addView(playback);
  set(a,"pendingOnly",true); call(a,"renderBrowserLists");
  LinearLayout panel=(LinearLayout)get(a,"browserChannels"); LinearLayout rows=null;
  for(int i=0;i<panel.getChildCount();i++) if(panel.getChildAt(i) instanceof LinearLayout) rows=(LinearLayout)panel.getChildAt(i);
  assertNotNull(rows); assertEquals(1,rows.getChildCount()); assertTrue(((Button)rows.getChildAt(0)).getText().toString().contains("Canal nuevo"));
  set(a,"selectedCategory","Noticias"); call(a,"renderBrowserLists");
  panel=(LinearLayout)get(a,"browserChannels");
  for(int i=0;i<panel.getChildCount();i++) if(panel.getChildAt(i) instanceof LinearLayout) rows=(LinearLayout)panel.getChildAt(i);
  assertEquals(0,rows.getChildCount()); assertSame(host,playback.getParent());
 }
}
