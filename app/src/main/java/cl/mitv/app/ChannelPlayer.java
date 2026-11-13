package cl.mitv.app;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.Player;

/** Adapts the native player's previous/next controls to the channel catalogue. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class ChannelPlayer extends ForwardingPlayer {
 private final Runnable previous,next;
 ChannelPlayer(Player player,Runnable previous,Runnable next) { super(player); this.previous=previous; this.next=next; }
 private static boolean channelCommand(int command) {
  return command==COMMAND_SEEK_TO_PREVIOUS || command==COMMAND_SEEK_TO_NEXT
   || command==COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM || command==COMMAND_SEEK_TO_NEXT_MEDIA_ITEM;
 }
 @Override public boolean isCommandAvailable(int command) { return channelCommand(command) || super.isCommandAvailable(command); }
 @Override public Commands getAvailableCommands() {
  return super.getAvailableCommands().buildUpon().addAll(COMMAND_SEEK_TO_PREVIOUS,COMMAND_SEEK_TO_NEXT,COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).build();
 }
 @Override public void seekToPrevious() { previous.run(); }
 @Override public void seekToNext() { next.run(); }
 @Override public void seekToPreviousMediaItem() { previous.run(); }
 @Override public void seekToNextMediaItem() { next.run(); }
}
