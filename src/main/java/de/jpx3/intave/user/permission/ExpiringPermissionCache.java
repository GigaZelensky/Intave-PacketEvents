package de.jpx3.intave.user.permission;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ExpiringPermissionCache implements PermissionCache {
  private final long cacheExpiration;
  private final Map<String, ExpiringPermissionCacheEntry> permissionEntries = new ConcurrentHashMap<>();

  ExpiringPermissionCache(long cacheExpires, TimeUnit unit) {
    cacheExpiration = unit.toMillis(cacheExpires);
  }

  @Override
  public boolean cached(String permission) {
    ExpiringPermissionCacheEntry entry = permissionEntries.get(permission);
    return entry != null && !entry.expired();
  }

  @Override
  public boolean check(String permission) {
    ExpiringPermissionCacheEntry entry = permissionEntries.get(permission);
    return entry != null && entry.hasAccess();
  }

  @Override
  public void save(String permission, boolean access) {
    permissionEntries
      .computeIfAbsent(permission, s ->
        new ExpiringPermissionCacheEntry(cacheExpiration))
      .setAccess(access);
  }

  @Override
  public void clear() {
    permissionEntries.clear();
  }

  public static ExpiringPermissionCache withDefaultExpirationTime() {
    return expiringAfter(4, TimeUnit.SECONDS);
  }

  public static ExpiringPermissionCache expiringAfter(long value, TimeUnit unit) {
    return new ExpiringPermissionCache(value, unit);
  }

  public static class ExpiringPermissionCacheEntry {
    private boolean access;
    private long checked;
    private final long duration;

    public ExpiringPermissionCacheEntry(long duration) {
      this.duration = duration;
    }

    public boolean hasAccess() {
      return access;
    }

    public void setAccess(boolean access) {
      this.access = access;
      this.checked = System.currentTimeMillis();
    }

    public long checked() {
      return checked;
    }

    public boolean expired() {
      return System.currentTimeMillis() - checked > duration;
    }
  }
}
