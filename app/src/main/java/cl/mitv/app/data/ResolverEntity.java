package cl.mitv.app.data;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName="resolvers")
public final class ResolverEntity {
 @PrimaryKey @NonNull public String id = "";
 @NonNull public String mode = "WEB_PAGE";
 @NonNull public String sourceUrl = "";
 @NonNull public String siteUrl = "";
 @NonNull public String health = "UNKNOWN";
 public long checkedAt;
 public int failures;
 public int httpCode;
}
