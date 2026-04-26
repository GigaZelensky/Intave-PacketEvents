package de.jpx3.intave.module.linker.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.netty.buffer.UnpooledByteBufAllocationHelper;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import io.netty.buffer.ByteBuf;

import java.lang.reflect.Constructor;
import java.util.IdentityHashMap;
import java.util.Map;

public final class PacketEventBuffer {
  private static final ThreadLocal<Map<ProtocolPacketEvent, Object>> SNAPSHOTS =
    ThreadLocal.withInitial(IdentityHashMap::new);

  private PacketEventBuffer() {}

  static void capture(ProtocolPacketEvent event) {
    Map<ProtocolPacketEvent, Object> snapshots = SNAPSHOTS.get();
    Object previous = snapshots.put(event, ByteBufHelper.retainedDuplicate(event.getByteBuf()));
    if (previous != null) {
      ByteBufHelper.release(previous);
    }
  }

  static void release(ProtocolPacketEvent event) {
    Map<ProtocolPacketEvent, Object> snapshots = SNAPSHOTS.get();
    Object buffer = snapshots.remove(event);
    if (buffer != null) {
      ByteBufHelper.release(buffer);
    }
    if (snapshots.isEmpty()) {
      SNAPSHOTS.remove();
    }
  }

  public static Object cloneFullBuffer(ProtocolPacketEvent event) {
    Map<ProtocolPacketEvent, Object> snapshots = SNAPSHOTS.get();
    Object snapshot = snapshots.get(event);
    if (snapshot == null) {
      if (snapshots.isEmpty()) {
        SNAPSHOTS.remove();
      }
      return event.getFullBufferClone();
    }
    Object buffer = UnpooledByteBufAllocationHelper.buffer();
    ByteBufHelper.writeVarInt(buffer, event.getPacketId());
    ByteBufHelper.writeBytes(buffer, ByteBufHelper.copyBytes(snapshot));
    return buffer;
  }

  public static Object clonePacketForReplay(ProtocolPacketEvent event) {
    Object reencodedPacket = cloneReencodedWrapper(event);
    return reencodedPacket == null ? cloneFullBuffer(event) : reencodedPacket;
  }

  public static void releasePacket(Object packet) {
    if (packet instanceof ByteBuf) {
      ((ByteBuf) packet).release();
    }
  }

  private static PacketWrapper<?> cloneReencodedWrapper(ProtocolPacketEvent event) {
    if (!event.needsReEncode()) {
      return null;
    }
    PacketWrapper<?> wrapper = event.getLastUsedWrapper();
    if (wrapper == null) {
      return null;
    }
    try {
      Constructor<?> constructor = event instanceof PacketReceiveEvent
        ? wrapper.getClass().getConstructor(PacketReceiveEvent.class)
        : wrapper.getClass().getConstructor(PacketSendEvent.class);
      PacketWrapper<?> copy = (PacketWrapper<?>) constructor.newInstance(event);
      copy.buffer = null;
      return copy;
    } catch (ReflectiveOperationException | RuntimeException exception) {
      return null;
    }
  }
}
