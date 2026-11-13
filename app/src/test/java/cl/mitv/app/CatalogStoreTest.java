package cl.mitv.app;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.room.Room;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import cl.mitv.app.data.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class CatalogStoreTest {
 @Rule public InstantTaskExecutorRule rule=new InstantTaskExecutorRule();
 AppDatabase db; CatalogStore store; Context context;
 @Before public void before() { context=RuntimeEnvironment.getApplication(); db=Room.inMemoryDatabaseBuilder(context,AppDatabase.class).allowMainThreadQueries().build(); store=new CatalogStore(db); }
 @After public void after() { db.close(); }
 private void seed() { store.initialize(() -> CatalogDocumentTest.LEGACY,null); }
 @Test public void assetsAreReadOnlyOnceAndAnEmptyCatalogDoesNotReseed() {
  AtomicInteger reads=new AtomicInteger(); CatalogStore.Seed seed=() -> { reads.incrementAndGet(); return CatalogDocumentTest.LEGACY; };
  store.initialize(seed,null); CatalogItem row=db.catalog().catalog().get(0); store.delete(row);
  store.initialize(seed,null); assertEquals(1,reads.get()); assertTrue(db.catalog().catalog().isEmpty());
 }
 @Test public void migratesLegacyCacheFavoritesAndResultsOnce() {
  SharedPreferences legacy=context.getSharedPreferences("migration-test",0); legacy.edit().clear().putString("catalog_json",CatalogDocumentTest.LEGACY.replace("Canal 9","Mi canal"))
   .putBoolean("c9_favorite",true).putInt("c9_result",1).commit();
  store.initialize(() -> CatalogDocumentTest.LEGACY,legacy); CatalogItem row=db.catalog().catalog().get(0);
  assertEquals("Mi canal",row.name); assertTrue(row.favorite); assertEquals(1,row.testResult);
  legacy.edit().putBoolean("c9_favorite",false).commit(); store.initialize(() -> { throw new AssertionError("Must not read assets"); },legacy);
  assertTrue(db.catalog().catalog().get(0).favorite);
 }
 @Test public void previewDoesNotWriteAndImportIsAtomic() throws Exception {
  seed(); CatalogDocument incoming=CatalogDocument.parse(CatalogDocumentTest.LEGACY.replace("c9","another"),false);
  int before=db.catalog().catalog().size(); assertEquals(1,store.preview(incoming)[0]); assertEquals(before,db.catalog().catalog().size());
  store.importLocal(incoming); assertEquals(before+1,db.catalog().catalog().size());
 }
 @Test public void remoteVersionProtectsEditsDeletionsAndFavorites() throws Exception {
  seed(); CatalogItem row=db.catalog().catalog().get(0); store.favorite(row,true);
  CatalogDocument.Item edit=CatalogDocument.parse(CatalogDocumentTest.LEGACY,false).items.get(0); edit.name="Edición local"; store.save(edit);
  CatalogDocument remote=CatalogDocument.parse(CatalogDocumentTest.LEGACY.replace("Canal 9","Cambio remoto"),false); remote.version=2;
  store.sync(CatalogStore.DEFAULT_REMOTE,remote);
  assertEquals("Edición local",db.catalog().catalog().get(0).name); assertTrue(db.catalog().catalog().get(0).favorite);
  store.delete(db.catalog().catalog().get(0)); remote.version=3; store.sync(CatalogStore.DEFAULT_REMOTE,remote); assertTrue(db.catalog().catalog().isEmpty());
  remote.version=2; try { store.sync(CatalogStore.DEFAULT_REMOTE,remote); fail(); } catch(IllegalArgumentException expected) { }
  assertEquals("3",db.catalog().metadata("version:"+CatalogDocument.key(CatalogStore.DEFAULT_REMOTE)));
 }
 @Test public void sameVersionAndInvalidRemoteLeaveLastGoodDatabaseIntact() throws Exception {
  seed(); CatalogDocument remote=CatalogDocument.parse(CatalogDocumentTest.LEGACY,false); remote.version=1; store.sync(CatalogStore.DEFAULT_REMOTE,remote);
  remote.items.get(0).name="No debe cambiar"; store.sync(CatalogStore.DEFAULT_REMOTE,remote); assertEquals("Canal 9",db.catalog().catalog().get(0).name);
  remote.version=2; remote.errors.add("Invalid URL"); try { store.sync(CatalogStore.DEFAULT_REMOTE,remote); fail(); } catch(IllegalArgumentException expected) { }
  assertEquals("Canal 9",db.catalog().catalog().get(0).name);
 }
 @Test public void movieCrudAndResolverEditsAreReadThroughDatabaseJoins() throws Exception {
  seed(); CatalogDocument.Item film=new CatalogDocument.Item(); film.id="film"; film.name="Película"; film.movie=true; film.group="Cine"; film.url="https://video.example/film.mp4"; film.siteUrl=film.url;
  store.save(film); assertEquals(2,db.catalog().catalog().size()); CatalogItem row=db.catalog().catalog().stream().filter(i -> i.movie).findFirst().get();
  assertEquals("STREAM",row.mode); assertEquals("Cine",row.category);
  film.mode="WEB_PAGE"; film.url="https://video.example/page"; store.save(film);
  assertEquals("WEB_PAGE",db.catalog().resolver(row.resolverId).mode); store.delete(row); assertEquals(1,db.catalog().catalog().size());
 }
 @Test public void databaseReopenKeepsCatalogAndDoesNotReadAssets() {
  String file="catalog-reopen-test.db"; context.deleteDatabase(file);
  AppDatabase first=Room.databaseBuilder(context,AppDatabase.class,file).allowMainThreadQueries().build();
  new CatalogStore(first).initialize(() -> CatalogDocumentTest.LEGACY,null); first.close();
  AppDatabase reopened=Room.databaseBuilder(context,AppDatabase.class,file).allowMainThreadQueries().build();
  new CatalogStore(reopened).initialize(() -> { throw new AssertionError("Assets reopened"); },null);
  assertEquals(1,reopened.catalog().catalog().size()); reopened.close(); context.deleteDatabase(file);
 }
 @Test public void failedFirstSeedRollsBackAndCanRetry() {
  try { store.initialize(() -> "broken",null); fail(); } catch(IllegalStateException expected) { }
  assertNull(db.catalog().metadata("initialized")); assertTrue(db.catalog().catalog().isEmpty()); seed(); assertEquals(1,db.catalog().catalog().size());
 }
}
