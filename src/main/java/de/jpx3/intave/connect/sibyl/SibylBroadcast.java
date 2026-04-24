package de.jpx3.intave.connect.sibyl;

import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.user.MessageChannelSubscriptions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;

public final class SibylBroadcast {

  public static void setup() {
  }

  public static void broadcast(String message) {
    if (message == null || message.isEmpty()) {
      return;
    }
    Collection<? extends Player> receiver = MessageChannelSubscriptions.sibylReceivers();
    if (receiver.isEmpty()) {
      return;
    }
    if (!Bukkit.isPrimaryThread()) {
      Synchronizer.synchronize(() -> broadcast(message));
      return;
    }
    for (Player authenticatedPlayer : receiver) {
      SibylMessageTransmitter.sendMessage(authenticatedPlayer, message);
    }
  }
}
