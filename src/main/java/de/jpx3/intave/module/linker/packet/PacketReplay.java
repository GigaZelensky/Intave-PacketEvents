package de.jpx3.intave.module.linker.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import de.jpx3.intave.user.User;

public final class PacketReplay {
  private PacketReplay() {
  }

  public static void receiveFromClient(User user, PacketWrapper<?> packet) {
    if (packet == null) {
      return;
    }
    user.ignoreNextInboundPacket();
    try {
      PacketEvents.getAPI().getPlayerManager().receivePacket(user.player(), packet);
    } finally {
      user.receiveNextInboundPacketAgain();
    }
  }

  public static void sendToClient(User user, Object packet) {
    if (packet == null) {
      return;
    }
    PacketTransport.sendToClientIgnoring(user, packet);
  }
}
