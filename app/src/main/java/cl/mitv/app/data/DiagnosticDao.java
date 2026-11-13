package cl.mitv.app.data;
import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;
@Dao
public interface DiagnosticDao {
 @Insert void insert(DiagnosticEvent event);
 @Query("DELETE FROM diagnostic_events WHERE id NOT IN (SELECT id FROM diagnostic_events ORDER BY id DESC LIMIT 200)") void prune();
 @Query("SELECT * FROM diagnostic_events ORDER BY id DESC LIMIT 200") List<DiagnosticEvent> recent();
 @Query("SELECT * FROM diagnostic_events ORDER BY id DESC LIMIT 200") LiveData<List<DiagnosticEvent>> observe();
 @Transaction default void record(DiagnosticEvent event) { insert(event); prune(); }
}
