package cl.mitv.app;
import androidx.media3.common.Player;
import java.lang.reflect.Proxy;
import org.junit.Test;
import static org.junit.Assert.*;
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class ChannelPlayerTest {
 @Test public void arrowsChangeChannelEvenForSingleItemButSeekingKeepsItsMeaning() {
  final int[] previous={0},next={0},back={0},forward={0};
  Player delegate=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(proxy,method,args) -> {
   switch(method.getName()) {
    case "getAvailableCommands": return new Player.Commands.Builder().addAll(Player.COMMAND_SEEK_BACK,Player.COMMAND_SEEK_FORWARD).build();
    case "isCommandAvailable": return (int)args[0]==Player.COMMAND_SEEK_BACK || (int)args[0]==Player.COMMAND_SEEK_FORWARD;
    case "seekBack": back[0]++; return null;
    case "seekForward": forward[0]++; return null;
    default: throw new AssertionError("Unexpected delegate call: "+method.getName());
   }
  });
  ChannelPlayer ui=new ChannelPlayer(delegate,() -> previous[0]++,() -> next[0]++);
  for(int command:new int[]{Player.COMMAND_SEEK_TO_PREVIOUS,Player.COMMAND_SEEK_TO_NEXT,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM}) {
   assertFalse(delegate.isCommandAvailable(command)); assertTrue(ui.isCommandAvailable(command)); assertTrue(ui.getAvailableCommands().contains(command));
  }
  ui.seekToPrevious(); ui.seekToPreviousMediaItem(); ui.seekToNext(); ui.seekToNextMediaItem();
  assertEquals(2,previous[0]); assertEquals(2,next[0]);
  ui.seekBack(); ui.seekForward(); assertEquals(1,back[0]); assertEquals(1,forward[0]);
  assertTrue(ui.getAvailableCommands().contains(Player.COMMAND_SEEK_BACK)); assertFalse(ui.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS));
 }
}
