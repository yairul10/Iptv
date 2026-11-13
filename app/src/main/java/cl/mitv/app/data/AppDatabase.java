package cl.mitv.app.data;
import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;
@Database(entities={ChannelEntity.class,MovieEntity.class,GroupEntity.class,ResolverEntity.class,Metadata.class,DiagnosticEvent.class},version=3,exportSchema=true)
public abstract class AppDatabase extends RoomDatabase {
 public abstract CatalogDao catalog();
 public abstract DiagnosticDao diagnostics();
 public static final Migration MIGRATION_1_2=new Migration(1,2) {
  @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
   db.execSQL("CREATE TABLE IF NOT EXISTS `diagnostic_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `level` TEXT NOT NULL, `category` TEXT NOT NULL, `message` TEXT NOT NULL)");
  }
 };
 public static final Migration MIGRATION_2_3=new Migration(2,3) {
  @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
   // Deduplica por URL repuntando canales y películas al superviviente antes de borrar.
   db.execSQL("CREATE TEMP TABLE IF NOT EXISTS `resolver_dups` AS SELECT r.id AS oldId,(SELECT MIN(r2.id) FROM resolvers r2 WHERE r2.sourceUrl=r.sourceUrl) AS keepId FROM resolvers r WHERE r.id<>(SELECT MIN(r3.id) FROM resolvers r3 WHERE r3.sourceUrl=r.sourceUrl)");
   db.execSQL("UPDATE channels SET resolverId=(SELECT keepId FROM resolver_dups WHERE oldId=channels.resolverId) WHERE resolverId IN (SELECT oldId FROM resolver_dups)");
   db.execSQL("UPDATE movies SET resolverId=(SELECT keepId FROM resolver_dups WHERE oldId=movies.resolverId) WHERE resolverId IN (SELECT oldId FROM resolver_dups)");
   db.execSQL("DELETE FROM resolvers WHERE id IN (SELECT oldId FROM resolver_dups)");
   db.execSQL("DROP TABLE resolver_dups");
   db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_resolvers_sourceUrl` ON `resolvers` (`sourceUrl`)");
  }
 };
 private static volatile AppDatabase instance;
 public static AppDatabase get(Context context) {
  if(instance==null) synchronized(AppDatabase.class) {
   if(instance==null) instance=Room.databaseBuilder(context.getApplicationContext(),AppDatabase.class,"mitv-catalog.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3).build();
  }
  return instance;
 }
}
