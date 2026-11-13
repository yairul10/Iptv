package cl.mitv.app.data;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName="metadata")
public final class Metadata {
 @PrimaryKey @NonNull public String key = "";
 @NonNull public String value = "";
 public Metadata(@NonNull String key,@NonNull String value) { this.key=key; this.value=value; }
}
