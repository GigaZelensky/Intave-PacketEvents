package de.jpx3.intave.module.mitigate;

import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.violation.placeholder.Placeholders;
import de.jpx3.intave.module.violation.placeholder.PlayerContext;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReconDelayLimiter extends Module {
  private final Map<UUID, Long> lastKicked = new ConcurrentHashMap<>();
  private final Map<InetAddress, Long> lastKickedIp = new ConcurrentHashMap<>();

  private volatile long delay;
  private volatile boolean refresh;
  private volatile String rawMessage;

  public void enable() {
    reloadConfiguration();
    plugin.eventLinker().registerEventsIn(this);
  }

  public void reloadConfiguration() {
    YamlConfiguration config = plugin.settings();

    delay = config.getInt("rejoin.delay") * 50L;
    refresh = config.getBoolean("rejoin.refresh");
    rawMessage = config.getString("rejoin.message");
  }

  @BukkitEventSubscription
  public void on(PlayerLoginEvent login) {
    Player player = login.getPlayer();
    long ipDelayLeft = System.currentTimeMillis() - lastKicked.getOrDefault(player.getUniqueId(), 0L);
    long accDelayLeft = System.currentTimeMillis() - lastKickedIp.getOrDefault(login.getAddress(), 0L);
    if (ipDelayLeft < delay || accDelayLeft < delay) {
      if (player.isBanned()) {
        if (refresh) {
          ban(login.getAddress(), player.getUniqueId(), "rejoin");
        }
        return;
      }
      // allow player to join if no other players are online
      if (Bukkit.getOnlinePlayers().isEmpty()) {
        return;
      }
      String message = rawMessage;
      PlayerContext playerContext = new PlayerContext(player.getName(), player.getUniqueId(), login.getAddress());
      message = Placeholders.replacePlaceholders(message, Placeholders.PLUGIN_CONTEXT, playerContext);
      message = ChatColor.translateAlternateColorCodes('&', message);
      login.setKickMessage(message);
      login.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, message);
      if (refresh) {
        ban(login.getAddress(), player.getUniqueId(), "rejoin");
      }
    }
  }

  public void ban(InetAddress address, UUID uuid, String check) {
    if (!address.getHostAddress().contains("127.0.0.1")) {
      lastKickedIp.put(address, System.currentTimeMillis());
    }
    lastKicked.put(uuid, System.currentTimeMillis());
  }
}
