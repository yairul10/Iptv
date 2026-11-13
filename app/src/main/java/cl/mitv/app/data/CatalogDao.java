package cl.mitv.app.data;
import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;
@Dao
public interface CatalogDao {
 String CHANNELS = "SELECT c.id,c.name,c.description,g.name AS category,c.groupId,c.resolverId,r.sourceUrl AS playerUrl,r.siteUrl,r.mode,r.health,r.checkedAt,0 AS movie,(r.mode='OFFICIAL_PLAYER') AS directPlayer,c.favorite,c.testResult,c.localEdited,c.sortOrder FROM channels c JOIN `groups` g ON c.groupId=g.id JOIN resolvers r ON c.resolverId=r.id WHERE c.deleted=0 AND c.enabled=1";
 String MOVIES = "SELECT c.id,c.name,c.description,g.name AS category,c.groupId,c.resolverId,r.sourceUrl AS playerUrl,r.siteUrl,r.mode,r.health,r.checkedAt,1 AS movie,(r.mode='OFFICIAL_PLAYER') AS directPlayer,c.favorite,c.testResult,c.localEdited,c.sortOrder FROM movies c JOIN `groups` g ON c.groupId=g.id JOIN resolvers r ON c.resolverId=r.id WHERE c.deleted=0 AND c.enabled=1";
 String ALL = CHANNELS + " UNION ALL " + MOVIES + " ORDER BY sortOrder,name COLLATE NOCASE";
 @Query(ALL) LiveData<List<CatalogItem>> observeCatalog();
 @Query(ALL) List<CatalogItem> catalog();
 @Query("SELECT * FROM `groups` ORDER BY sortOrder,name COLLATE NOCASE") List<GroupEntity> groups();
 @Query("SELECT * FROM resolvers WHERE id=:id") ResolverEntity resolver(String id);
 @Query("SELECT * FROM channels WHERE id=:id") ChannelEntity channel(String id);
 @Query("SELECT * FROM movies WHERE id=:id") MovieEntity movie(String id);
 @Query("SELECT * FROM channels WHERE sourceKey=:source AND remoteId=:remoteId LIMIT 1") ChannelEntity remoteChannel(String source,String remoteId);
 @Query("SELECT * FROM movies WHERE sourceKey=:source AND remoteId=:remoteId LIMIT 1") MovieEntity remoteMovie(String source,String remoteId);
 @Query("SELECT * FROM channels") List<ChannelEntity> allChannels();
 @Query("SELECT value FROM metadata WHERE `key`=:key") String metadata(String key);
 @Upsert void putGroup(GroupEntity value);
 @Upsert void putResolver(ResolverEntity value);
 @Upsert void putChannel(ChannelEntity value);
 @Upsert void putMovie(MovieEntity value);
 @Upsert void putMetadata(Metadata value);
 @Query("UPDATE channels SET favorite=:favorite WHERE id=:id") void favoriteChannel(String id,boolean favorite);
 @Query("UPDATE movies SET favorite=:favorite WHERE id=:id") void favoriteMovie(String id,boolean favorite);
 @Query("UPDATE channels SET testResult=:result WHERE id=:id") void resultChannel(String id,int result);
 @Query("UPDATE movies SET testResult=:result WHERE id=:id") void resultMovie(String id,int result);
 @Query("UPDATE channels SET deleted=1,localEdited=1 WHERE id=:id") void deleteChannel(String id);
 @Query("UPDATE movies SET deleted=1,localEdited=1 WHERE id=:id") void deleteMovie(String id);
 @Query("UPDATE channels SET enabled=0 WHERE sourceKey=:source AND localEdited=0") void hideRemoteChannels(String source);
 @Query("UPDATE movies SET enabled=0 WHERE sourceKey=:source AND localEdited=0") void hideRemoteMovies(String source);
 @Query("SELECT DISTINCT r.* FROM resolvers r WHERE r.id IN (SELECT resolverId FROM channels WHERE enabled=1 AND deleted=0 UNION SELECT resolverId FROM movies WHERE enabled=1 AND deleted=0) ORDER BY checkedAt LIMIT 50") List<ResolverEntity> healthBatch();
 @Query("UPDATE resolvers SET health=:health,checkedAt=:time,failures=:failures,httpCode=:code WHERE id=:id AND sourceUrl=:url") void setHealth(String id,String url,String health,long time,int failures,int code);
}
