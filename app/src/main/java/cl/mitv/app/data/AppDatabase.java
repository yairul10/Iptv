package cl.mitv.app.data;
import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
@Database(entities={ChannelEntity.class,MovieEntity.class,GroupEntity.class,ResolverEntity.class,Metadata.class},version=1,exportSchema=true)
public abstract class AppDatabase extends RoomDatabase {
 public abstract CatalogDao catalog();
 private static volatile AppDatabase instance;
 public static AppDatabase get(Context context) {
  if(instance==null) synchronized(AppDatabase.class) {
   if(instance==null) instance=Room.databaseBuilder(context.getApplicationContext(),AppDatabase.class,"mitv-catalog.db").build();
  }
  return instance;
 }
}
