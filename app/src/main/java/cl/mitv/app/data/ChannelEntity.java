package cl.mitv.app.data;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
@Entity(tableName="channels", foreignKeys={
 @ForeignKey(entity=GroupEntity.class,parentColumns="id",childColumns="groupId",onDelete=ForeignKey.RESTRICT),
 @ForeignKey(entity=ResolverEntity.class,parentColumns="id",childColumns="resolverId",onDelete=ForeignKey.RESTRICT)
},indices={@Index("groupId"),@Index("resolverId"),@Index(value={"sourceKey","remoteId"},unique=true)})
public final class ChannelEntity extends Entry { }
