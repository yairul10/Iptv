package cl.mitv.app.data;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName="diagnostic_events")
public final class DiagnosticEvent {
 @PrimaryKey(autoGenerate=true) public long id;
 public long timestamp;
 @NonNull public String level="INFO";
 @NonNull public String category="app";
 @NonNull public String message="";
}
