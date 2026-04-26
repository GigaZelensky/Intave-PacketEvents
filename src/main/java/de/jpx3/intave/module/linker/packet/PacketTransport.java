package de.jpx3.intave.module.linker.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import de.jpx3.intave.user.User;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

public final class PacketTransport {
  private PacketTransport() {
  }

  public static void sendToClient(Player player, Object packet) {
    sendToClient(player, false, packet);
  }

  public static void sendToClientSilently(Player player, Object packet) {
    sendToClient(player, true, packet);
  }

  public static void sendToClient(Player player, boolean silent, Object... packets) {
    if (packets == null || packets.length == 0) {
      return;
    }
    executeOrdered(player, channel -> {
      for (Object packet : packets) {
        sendNow(channel, packet, silent);
      }
    }, packets);
  }

  public static void sendToClientIgnoring(User user, Object packet) {
    sendToClientIgnoring(user, false, packet);
  }

  public static void sendToClientIgnoring(User user, boolean silent, Object... packets) {
    if (packets == null || packets.length == 0) {
      return;
    }
    Player player = user.player();
    executeOrdered(player, channel -> {
      user.ignoreNextOutboundPacket();
      try {
        for (Object packet : packets) {
          sendNow(channel, packet, silent);
        }
      } finally {
        user.receiveNextOutboundPacketAgain();
      }
    }, packets);
  }

  private static void executeOrdered(Player player, Consumer<Object> action, Object[] packets) {
    Object channel;
    try {
      channel = PacketEvents.getAPI().getPlayerManager().getChannel(player);
    } catch (RuntimeException exception) {
      releaseAll(packets);
      return;
    }
    if (channel == null) {
      releaseAll(packets);
      return;
    }
    if (channel instanceof Channel) {
      Channel nettyChannel = (Channel) channel;
      if (!nettyChannel.isOpen()) {
        releaseAll(packets);
        return;
      }
      nettyChannel.eventLoop().execute(() -> {
        if (nettyChannel.isOpen()) {
          action.accept(nettyChannel);
        } else {
          releaseAll(packets);
        }
      });
    } else {
      action.accept(channel);
    }
  }

  private static void sendNow(Object channel, Object packet, boolean silent) {
    if (packet == null) {
      return;
    }
    if (packet instanceof PacketWrapper) {
      PacketWrapper<?> wrapper = (PacketWrapper<?>) packet;
      if (silent) {
        PacketEvents.getAPI().getProtocolManager().sendPacketSilently(channel, wrapper);
      } else {
        PacketEvents.getAPI().getProtocolManager().sendPacket(channel, wrapper);
      }
    } else if (silent) {
      PacketEvents.getAPI().getProtocolManager().sendPacketSilently(channel, packet);
    } else {
      PacketEvents.getAPI().getProtocolManager().sendPacket(channel, packet);
    }
  }

  private static void releaseAll(Object[] packets) {
    for (Object packet : packets) {
      if (packet instanceof ByteBuf) {
        ((ByteBuf) packet).release();
      }
    }
  }
}
