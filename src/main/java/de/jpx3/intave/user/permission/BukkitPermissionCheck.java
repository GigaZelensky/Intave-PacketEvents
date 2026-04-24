package de.jpx3.intave.user.permission;

import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

public final class BukkitPermissionCheck {
  public static boolean permissionCheck(Permissible permissible, String permission) {
    if (permissible instanceof Player) {
      return playerPermissionCheck((Player) permissible, permission);
    }
    return nativePermissionCheck(permissible, permission);
  }

  private static boolean playerPermissionCheck(Player player, String permission) {
    if (!UserRepository.hasUser(player)) {
      return nativePermissionCheck(player, permission);
    }
    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return false;
    }
    PermissionCache permissionCache = user.permissionCache();
    if (permissionCache.cached(permission)) {
      return permissionCache.check(permission);
    } else {
      boolean access = nativePermissionCheck(player, permission);
      permissionCache.save(permission, access);
      return access;
    }
  }

  private static boolean nativePermissionCheck(Permissible permissible, String permission) {
    return permissible.hasPermission(permission);
  }
}
