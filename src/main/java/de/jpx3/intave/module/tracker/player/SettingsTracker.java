package de.jpx3.intave.module.tracker.player;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CONFIG_CUSTOM_PAYLOAD_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.CUSTOM_PAYLOAD_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.SETTINGS;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.LOGIN;
import static de.jpx3.intave.user.UserRepository.userOf;

public final class SettingsTracker extends Module {
  private final Map<UUID, String> pendingBrands = new ConcurrentHashMap<>();

  @PacketSubscription(
    packetsIn = {
      SETTINGS
    }
  )
  public void receiveClientOptions(Player player, WrapperPlayClientSettings packet) {
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    if (MinecraftVersions.VER1_20_2.atOrAbove()) {
      clientData.setLocale("en_US");
      return;
    }
    String locale = packet.getLocale();
    clientData.setLocale(locale);
  }

  @PacketSubscription(
    packetsIn = {
      CUSTOM_PAYLOAD_IN
    }
  )
  public void receivePayloadPacket(Player player, WrapperPlayClientPluginMessage packet) {
    receiveBrandPayload(player, packet.getChannelName(), packet.getData());
  }

  @PacketSubscription(
    packetsIn = {
      CONFIG_CUSTOM_PAYLOAD_IN
    }
  )
  public void receiveConfigurationPayloadPacket(ProtocolPacketEvent event, WrapperConfigClientPluginMessage packet) {
    receiveBrandPayload(event.getPlayer(), event.getUser().getUUID(), packet.getChannelName(), packet.getData());
  }

  private void receiveBrandPayload(Player player, String tag, byte[] data) {
    receiveBrandPayload(player, player.getUniqueId(), tag, data);
  }

  private void receiveBrandPayload(Player player, UUID fallbackUniqueId, String tag, byte[] data) {
    if (!isBrandChannel(tag)) {
      return;
    }
    String brand = decodeBrand(data);
    if (player == null) {
      if (fallbackUniqueId != null) {
        pendingBrands.put(fallbackUniqueId, brand);
      }
      return;
    }
    if (!UserRepository.hasUser(player)) {
      pendingBrands.put(player.getUniqueId(), brand);
      return;
    }
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    clientData.setClientBrand(brand);
  }

  @PacketSubscription(
    packetsOut = {
      LOGIN
    }
  )
  public void applyPendingBrand(Player player) {
    if (!UserRepository.hasUser(player)) {
      return;
    }
    String brand = pendingBrands.remove(player.getUniqueId());
    if (brand == null) {
      return;
    }
    userOf(player).meta().protocol().setClientBrand(brand);
  }

  private boolean isBrandChannel(String tag) {
    if (tag == null) {
      return false;
    }
    String normalized = tag.toLowerCase(Locale.ROOT);
    if (normalized.startsWith("minecraft:")) {
      normalized = normalized.substring("minecraft:".length());
    }
    return normalized.equals("brand") || normalized.equals("mc|brand");
  }

  private String decodeBrand(byte[] data) {
    if (data == null || data.length == 0) {
      return "";
    }
    int[] offset = {0};
    int length = readVarInt(data, offset);
    if (length < 0 || offset[0] + length > data.length) {
      return new String(data, StandardCharsets.UTF_8);
    }
    return new String(data, offset[0], length, StandardCharsets.UTF_8);
  }

  private int readVarInt(byte[] data, int[] offset) {
    int value = 0;
    int position = 0;
    while (offset[0] < data.length && position < 35) {
      int currentByte = data[offset[0]++] & 0xFF;
      value |= (currentByte & 0x7F) << position;
      if ((currentByte & 0x80) == 0) {
        return value;
      }
      position += 7;
    }
    return -1;
  }
}
