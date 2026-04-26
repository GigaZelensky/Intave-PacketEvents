package de.jpx3.intave.connect.sibyl;

import de.jpx3.intave.executor.Synchronizer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class SibylMessageTransmitter {
  public static void sendMessage(Player player, String message, String... args) {
    if (!Bukkit.isPrimaryThread()) {
      Synchronizer.synchronize(() -> sendMessage(player, message, args));
      return;
    }
    player.sendMessage(ChatColor.RED + "(debug) " + ChatColor.RESET + String.format(message, (Object[]) args));
  }
}
