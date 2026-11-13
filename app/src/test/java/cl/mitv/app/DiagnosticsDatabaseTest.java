package cl.mitv.app;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import cl.mitv.app.data.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.InputStream;
import java.util.List;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class DiagnosticsDatabaseTest {
 @Test public void recordsOnlyLast200EventsInNewestFirstOrder() {
  Context c=RuntimeEnvironment.getApplication(); AppDatabase db=Room.inMemoryDatabaseBuilder(c,AppDatabase.class).allowMainThreadQueries().build();
  try { for(int i=0;i<225;i++) { DiagnosticEvent e=new DiagnosticEvent(); e.timestamp=i; e.message="evento "+i; db.diagnostics().record(e); }
   List<DiagnosticEvent> events=db.diagnostics().recent(); assertEquals(200,events.size()); assertEquals("evento 224",events.get(0).message); assertEquals("evento 25",events.get(199).message);
  } finally { db.close(); }
 }
 @Test public void migrationFromVersion1KeepsExistingCatalogAndUserState() throws Exception {
  verifyMigration(1);
  verifyMigration(2);
 }
 private void verifyMigration(int version) throws Exception {
  Context context=RuntimeEnvironment.getApplication(); String name="diagnostic-migration-test-"+version+".db"; context.deleteDatabase(name);
  JSONObject schema;
  try(InputStream in=getClass().getClassLoader().getResourceAsStream("cl.mitv.app.data.AppDatabase/"+version+".json")) { assertNotNull(in); schema=new JSONObject(CatalogRepository.read(in,1048576)).getJSONObject("database"); }
  SQLiteDatabase old=context.openOrCreateDatabase(name,0,null);
  for(int i=0;i<schema.getJSONArray("entities").length();i++) { JSONObject entity=schema.getJSONArray("entities").getJSONObject(i); old.execSQL(entity.getString("createSql").replace("${TABLE_NAME}",entity.getString("tableName"))); JSONArray indices=entity.getJSONArray("indices"); for(int k=0;k<indices.length();k++) old.execSQL(indices.getJSONObject(k).getString("createSql").replace("${TABLE_NAME}",entity.getString("tableName"))); }
  JSONArray setup=schema.getJSONArray("setupQueries"); for(int i=0;i<setup.length();i++) old.execSQL(setup.getString(i));
  old.execSQL("INSERT INTO metadata (`key`,value) VALUES ('initialized','1')");
  old.execSQL("INSERT INTO `groups` (id,name,sortOrder) VALUES ('g','Cine',0)");
  old.execSQL("INSERT INTO resolvers (id,mode,sourceUrl,siteUrl,health,checkedAt,failures,httpCode) VALUES ('r','STREAM','https://example.com/a.mp4','https://example.com/','UNKNOWN',0,0,0)");
  old.execSQL("INSERT INTO movies (id,name,description,groupId,resolverId,sourceKey,remoteId,remoteVersion,localEdited,deleted,enabled,favorite,testResult,sortOrder) VALUES ('film','Película','Descripción','g','r','local','film',0,1,0,1,1,1,0)");
  old.execSQL("INSERT INTO resolvers SELECT 'r2',mode,sourceUrl,siteUrl,health,checkedAt,failures,httpCode FROM resolvers WHERE id='r'");
  old.setVersion(version); old.close();
  AppDatabase db=Room.databaseBuilder(context,AppDatabase.class,name).addMigrations(AppDatabase.MIGRATION_1_2,AppDatabase.MIGRATION_2_3).allowMainThreadQueries().build();
  try { CatalogItem movie=db.catalog().catalog().get(0); assertTrue(movie.movie); assertTrue(movie.favorite); assertEquals(1,movie.testResult); assertEquals("STREAM",movie.mode); assertEquals("1",db.catalog().metadata("initialized")); assertTrue(db.diagnostics().recent().isEmpty());
   android.database.Cursor rows=db.query("SELECT COUNT(*) FROM resolvers WHERE sourceUrl='https://example.com/a.mp4'",null); try { assertTrue(rows.moveToFirst()); assertEquals(2,rows.getInt(0)); } finally { rows.close(); } }
  finally { db.close(); context.deleteDatabase(name); }
 }
}
