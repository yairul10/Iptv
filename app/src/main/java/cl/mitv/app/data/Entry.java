package cl.mitv.app.data;
import androidx.annotation.NonNull;
import androidx.room.PrimaryKey;
public abstract class Entry {
 @PrimaryKey @NonNull public String id = "";
 @NonNull public String name = "";
 @NonNull public String description = "";
 @NonNull public String groupId = "";
 @NonNull public String resolverId = "";
 @NonNull public String sourceKey = "local";
 @NonNull public String remoteId = "";
 public long remoteVersion;
 public boolean localEdited;
 public boolean deleted;
 public boolean enabled = true;
 public boolean favorite;
 public int testResult;
 public int sortOrder;
}
