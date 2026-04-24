package de.jpx3.intave.entity.datawatcher;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.executor.Synchronizer;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

public final class DataWatcherAccess {
  private static final int BASE_FLAGS_INDEX = 0;
  private static final int ACTIVE_HAND_BIT = 0;

  private static final int WATCHER_BLOCKING_ID = MinecraftVersions.VER1_9_0.atOrAbove() ? 1 : 4;
  private static final int WATCHER_SNEAK_ID = 1;
  private static final int WATCHER_SPRINT_ID = 3;

  private DataWatcherAccess() {}

  public static void setSprintingFlag(Player player, boolean flag) {
    runOnMainThread(player, () -> {
      invokeBooleanSetter(player, "setSprinting", flag);
      sendBaseFlag(player, WATCHER_SPRINT_ID, flag);
    });
  }

  public static void setSneakingFlag(Player player, boolean flag) {
    runOnMainThread(player, () -> {
      invokeBooleanSetter(player, "setSneaking", flag);
      sendBaseFlag(player, WATCHER_SNEAK_ID, flag);
    });
  }

  public static void setBlockingFlag(Player player, boolean flag) {
    runOnMainThread(player, () -> {
      if (MinecraftVersions.VER1_9_0.atOrAbove()) {
        sendMetadataFlag(player, handStateIndex(), ACTIVE_HAND_BIT, flag);
      } else {
        sendBaseFlag(player, WATCHER_BLOCKING_ID, flag);
      }
    });
  }

  private static void sendBaseFlag(Player player, int bit, boolean enabled) {
    sendMetadataFlag(player, BASE_FLAGS_INDEX, bit, enabled);
  }

  private static void sendMetadataFlag(Player player, int index, int bit, boolean enabled) {
    byte current = metadataByte(player, index);
    byte updated = enabled ? (byte) (current | 1 << bit) : (byte) (current & ~(1 << bit));
    EntityData<Byte> metadata = new EntityData<>(index, EntityDataTypes.BYTE, updated);
    PacketEvents.getAPI().getPlayerManager().sendPacket(
      player,
      new WrapperPlayServerEntityMetadata(player.getEntityId(), Collections.singletonList(metadata))
    );
  }

  private static byte metadataByte(Player player, int index) {
    try {
      List<EntityData<?>> metadata = SpigotConversionUtil.getEntityMetadata(player);
      for (EntityData<?> data : metadata) {
        if (data.getIndex() == index && data.getValue() instanceof Number) {
          return ((Number) data.getValue()).byteValue();
        }
      }
    } catch (RuntimeException ignored) {
    }
    return index == BASE_FLAGS_INDEX ? baseFlagsFromBukkit(player) : 0;
  }

  private static byte baseFlagsFromBukkit(Player player) {
    byte flags = 0;
    if (player.isSneaking()) {
      flags |= 1 << WATCHER_SNEAK_ID;
    }
    if (player.isSprinting()) {
      flags |= 1 << WATCHER_SPRINT_ID;
    }
    return flags;
  }

  private static int handStateIndex() {
    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      return 8;
    } else if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      return 7;
    } else if (MinecraftVersions.VER1_10_0.atOrAbove()) {
      return 6;
    }
    return 5;
  }

  private static void runOnMainThread(Player player, Runnable task) {
    if (player == null) {
      return;
    }
    Runnable guardedTask = () -> {
      if (player.isOnline()) {
        task.run();
      }
    };
    if (Bukkit.isPrimaryThread()) {
      guardedTask.run();
    } else {
      Synchronizer.synchronize(guardedTask);
    }
  }

  private static void invokeBooleanSetter(Player player, String methodName, boolean value) {
    try {
      Method method = player.getClass().getMethod(methodName, boolean.class);
      method.invoke(player, value);
    } catch (ReflectiveOperationException | RuntimeException ignored) {
    }
  }
}
