package de.jpx3.intave.version;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class ProtocolVersionConverter {
  private static final ViaVersionProtocolLookup VIA_VERSION_PROTOCOLS = ViaVersionProtocolLookup.create();
  private static final Map<Integer, String> PROTOCOL_TO_VERSION = new HashMap<>();
  private static final Map<String, Integer> VERSION_TO_PROTOCOL = new HashMap<>();

  static {
    for (ClientVersion clientVersion : ClientVersion.values()) {
      registerPacketEventsVersion(clientVersion);
    }
    registerRange(4, "1.7.2-1.7.5", "1.7", 2, 5);
    registerRange(5, "1.7.6-1.7.10", "1.7", 6, 10);
    registerRange(47, "1.8.x", "1.8", 0, 9);
    register(107, "1.9");
    register(108, "1.9.1");
    register(109, "1.9.2");
    registerRange(110, "1.9.3-1.9.4", "1.9", 3, 4);
    registerRange(210, "1.10.x", "1.10", 0, 2);
    register(315, "1.11");
    registerRange(316, "1.11.1-1.11.2", "1.11", 1, 2);
    register(335, "1.12");
    register(338, "1.12.1");
    register(340, "1.12.2");
    register(393, "1.13");
    register(401, "1.13.1");
    register(404, "1.13.2");
    register(477, "1.14");
    register(480, "1.14.1");
    register(485, "1.14.2");
    register(490, "1.14.3");
    register(498, "1.14.4");
    register(573, "1.15");
    register(575, "1.15.1");
    register(578, "1.15.2");
    register(735, "1.16");
    register(736, "1.16.1");
    register(751, "1.16.2");
    register(753, "1.16.3");
    registerRange(754, "1.16.4-1.16.5", "1.16", 4, 5);
    register(755, "1.17");
    register(756, "1.17.1");
    registerRange(757, "1.18-1.18.1", "1.18", 0, 1);
    register(758, "1.18.2");
    register(759, "1.19");
    registerRange(760, "1.19.1-1.19.2", "1.19", 1, 2);
    register(761, "1.19.3");
    register(762, "1.19.4");
    registerRange(763, "1.20-1.20.1", "1.20", 0, 1);
    register(764, "1.20.2");
    registerRange(765, "1.20.3-1.20.4", "1.20", 3, 4);
    registerRange(766, "1.20.5-1.20.6", "1.20", 5, 6);
    registerRange(767, "1.21-1.21.1", "1.21", 0, 1);
    registerRange(768, "1.21.2-1.21.3", "1.21", 2, 3);
    register(769, "1.21.4");
    register(770, "1.21.5");
    register(771, "1.21.6");
    registerRange(772, "1.21.7-1.21.8", "1.21", 7, 8);
    registerRange(773, "1.21.9-1.21.10", "1.21", 9, 10);
    register(774, "1.21.11");
    registerRange(775, "26.1-26.1.2", "26.1", 0, 2);
  }

  public static String versionByProtocolVersion(int version) {
    if (version <= 0) {
      return "unknown";
    }
    String viaVersion = VIA_VERSION_PROTOCOLS.versionByProtocolVersion(version);
    if (viaVersion != null) {
      return viaVersion;
    }
    String mappedVersion = PROTOCOL_TO_VERSION.get(version);
    if (mappedVersion != null) {
      return mappedVersion;
    }
    return "unknown";
  }

  public static int protocolVersionBy(MinecraftVersion version) {
    return protocolVersionBy(version.getVersion());
  }

  public static int protocolVersionBy(String version) {
    int viaProtocolVersion = VIA_VERSION_PROTOCOLS.protocolVersionBy(version);
    if (viaProtocolVersion > 0) {
      return viaProtocolVersion;
    }
    Integer protocolVersion = VERSION_TO_PROTOCOL.get(version);
    if (protocolVersion != null) {
      return protocolVersion;
    }
    for (ClientVersion clientVersion : ClientVersion.values()) {
      if (clientVersion.getReleaseName().equals(version)) {
        return clientVersion.getProtocolVersion();
      }
    }
    return nearestProtocolVersion(version);
  }

  private static void register(int protocolVersion, String version, String... aliases) {
    PROTOCOL_TO_VERSION.put(protocolVersion, version);
    VERSION_TO_PROTOCOL.put(version, protocolVersion);
    for (String alias : aliases) {
      VERSION_TO_PROTOCOL.put(alias, protocolVersion);
    }
  }

  private static void registerRange(int protocolVersion, String version, String baseVersion, int from, int to) {
    PROTOCOL_TO_VERSION.put(protocolVersion, version);
    VERSION_TO_PROTOCOL.put(version, protocolVersion);
    for (int i = from; i <= to; i++) {
      if (i == 0) {
        VERSION_TO_PROTOCOL.put(baseVersion, protocolVersion);
      }
      VERSION_TO_PROTOCOL.put(baseVersion + "." + i, protocolVersion);
    }
  }

  private static void registerPacketEventsVersion(ClientVersion clientVersion) {
    String releaseName = clientVersion.getReleaseName();
    if (releaseName == null || releaseName.length() == 0 || !Character.isDigit(releaseName.charAt(0))) {
      return;
    }
    PROTOCOL_TO_VERSION.putIfAbsent(clientVersion.getProtocolVersion(), releaseName);
    VERSION_TO_PROTOCOL.putIfAbsent(releaseName, clientVersion.getProtocolVersion());
  }

  private static int nearestProtocolVersion(String version) {
    MinecraftVersion requestVersion = new MinecraftVersion(version);
    int nearestProtocolVersion = -1;
    int nearestDistance = Integer.MAX_VALUE;
    for (Map.Entry<String, Integer> entry : VERSION_TO_PROTOCOL.entrySet()) {
      int distance = Math.abs(new MinecraftVersion(entry.getKey()).compareTo(requestVersion));
      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearestProtocolVersion = entry.getValue();
      }
    }
    return nearestProtocolVersion;
  }

  private static final class ViaVersionProtocolLookup {
    private final Method getProtocol;
    private final Method getClosest;
    private final Method getName;
    private final Method getVersion;
    private final Method isKnown;

    private ViaVersionProtocolLookup(Method getProtocol, Method getClosest, Method getName, Method getVersion, Method isKnown) {
      this.getProtocol = getProtocol;
      this.getClosest = getClosest;
      this.getName = getName;
      this.getVersion = getVersion;
      this.isKnown = isKnown;
    }

    private static ViaVersionProtocolLookup create() {
      try {
        Class<?> protocolVersionClass = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
        return new ViaVersionProtocolLookup(
          protocolVersionClass.getMethod("getProtocol", int.class),
          protocolVersionClass.getMethod("getClosest", String.class),
          protocolVersionClass.getMethod("getName"),
          protocolVersionClass.getMethod("getVersion"),
          protocolVersionClass.getMethod("isKnown")
        );
      } catch (Throwable ignored) {
        return new ViaVersionProtocolLookup(null, null, null, null, null);
      }
    }

    private String versionByProtocolVersion(int protocolVersion) {
      if (getProtocol == null) {
        return null;
      }
      try {
        Object version = getProtocol.invoke(null, protocolVersion);
        if (version == null || !(Boolean) isKnown.invoke(version)) {
          return null;
        }
        String name = (String) getName.invoke(version);
        return name == null || name.length() == 0 || name.startsWith("UNKNOWN") ? null : name;
      } catch (Throwable ignored) {
        return null;
      }
    }

    private int protocolVersionBy(String versionName) {
      if (getClosest == null) {
        return -1;
      }
      try {
        Object version = getClosest.invoke(null, versionName);
        return version == null ? -1 : (int) getVersion.invoke(version);
      } catch (Throwable ignored) {
        return -1;
      }
    }
  }
}
