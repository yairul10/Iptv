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
   db.execSQL("CREATE INDEX IF NOT EXISTS `index_resolvers_sourceUrl` ON `resolvers` (`sourceUrl`)");
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
