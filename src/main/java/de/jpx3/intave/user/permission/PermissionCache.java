package de.jpx3.intave.user.permission;

public interface PermissionCache {
  boolean check(String permission);

  boolean cached(String permission);

  void save(String permission, boolean access);

  void clear();
}
