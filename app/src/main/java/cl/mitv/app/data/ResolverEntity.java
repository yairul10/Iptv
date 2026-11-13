package cl.mitv.app.data;
import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
@Entity(tableName="resolvers",indices=@Index(value="sourceUrl",unique=true))
public final class ResolverEntity {
 @PrimaryKey @NonNull public String id = "";
 @NonNull public String mode = "WEB_PAGE";
 @NonNull public String sourceUrl = "";
 @NonNull public String siteUrl = "";
 @NonNull public String health = "UNKNOWN";
 @ColumnInfo(defaultValue="0") public long checkedAt;
 @ColumnInfo(defaultValue="0") public int failures;
 @ColumnInfo(defaultValue="0") public int httpCode;
}
