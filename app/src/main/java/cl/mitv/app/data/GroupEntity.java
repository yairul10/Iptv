package cl.mitv.app.data;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName="groups")
public final class GroupEntity {
 @PrimaryKey @NonNull public String id = "";
 @NonNull public String name = "";
 public int sortOrder;
}
