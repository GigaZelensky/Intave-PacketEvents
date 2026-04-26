package de.jpx3.intave.module.tracker.player;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.potion.PotionType;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.EffectMetadata;
import org.bukkit.entity.Player;

import java.util.Locale;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.HIGH;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.ENTITY_EFFECT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.REMOVE_ENTITY_EFFECT;

public final class EffectTracker extends Module {
  public static final int POTION_EFFECT_SPEED = 1;
  public static final int POTION_EFFECT_SLOWNESS = 2;
  public static final int POTION_EFFECT_JUMP_BOOST = 8;

  private final boolean EFFECT_ACCESS = MinecraftVersions.VER1_9_0.atOrAbove();

  @PacketSubscription(
    priority = HIGH,
    packetsOut = ENTITY_EFFECT
  )
  public void sentEffect(
    User user, Player player,
    WrapperPlayServerEntityEffect packet
  ) {
    int entityId = packet.getEntityId();
    if (entityId != player.getEntityId()) {
      return;
    }
    PotionEffectOutput effectOutput = new PotionEffectOutput(
      effectTypeId(packet.getPotionType()),
      packet.getEffectAmplifier(),
      packet.getEffectDurationTicks()
    );
    user.tickFeedback(() -> receiveEffect(player, effectOutput));
  }

  @PacketSubscription(
    priority = HIGH,
    packetsOut = {
      REMOVE_ENTITY_EFFECT
    }
  )
  public void sentRemoveEffect(ProtocolPacketEvent event, WrapperPlayServerRemoveEntityEffect packet) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    int entityId = packet.getEntityId();
    if (entityId != player.getEntityId()) {
      return;
    }
    int potionEffectType = effectTypeId(packet.getPotionType());
    user.tickFeedback(() -> receiveEffectRemoval(player, potionEffectType));
  }

  private void receiveEffectRemoval(Player player, int potionEffectType) {
    User user = UserRepository.userOf(player);
    EffectMetadata potionData = user.meta().potions();
    switch (potionEffectType) {
      case POTION_EFFECT_SPEED: {
        potionData.potionEffectSpeedAmplifier(0);
        potionData.potionEffectSpeedDuration = 0;
        break;
      }
      case POTION_EFFECT_SLOWNESS: {
        potionData.potionEffectSlownessAmplifier(0);
        potionData.potionEffectSlownessDuration = 0;
        break;
      }
      case POTION_EFFECT_JUMP_BOOST: {
        potionData.potionEffectJumpAmplifier(0);
        potionData.potionEffectJumpDuration = 0;
        break;
      }
    }
  }

  private void receiveEffect(Player player, PotionEffectOutput effectOutput) {
    User user = UserRepository.userOf(player);
    EffectMetadata potionData = user.meta().potions();

    int effectAmplifier = effectOutput.potionEffectAmplifier;
    int effectDuration = effectOutput.potionEffectDuration;

    boolean infiniteEffectsAllowed = user.meta().protocol().protocolVersion() >= 763;
    if (effectDuration == -1 && infiniteEffectsAllowed) {
      effectDuration = Integer.MAX_VALUE;
    }

    switch (effectOutput.potionEffectType) {
      case POTION_EFFECT_SPEED: {
        potionData.potionEffectSpeedAmplifier(effectAmplifier + 1);
        potionData.potionEffectSpeedDuration = effectDuration - 1;
        break;
      }
      case POTION_EFFECT_SLOWNESS: {
        potionData.potionEffectSlownessAmplifier(effectAmplifier + 1);
        potionData.potionEffectSlownessDuration = effectDuration - 1;
        break;
      }
      case POTION_EFFECT_JUMP_BOOST: {
        potionData.potionEffectJumpAmplifier(effectAmplifier);
        potionData.potionEffectJumpDuration = effectDuration - 1;
        break;
      }
    }
  }

  private int effectTypeId(PotionType potionType) {
    if (potionType == null) {
      return -1;
    }
    if (potionType == PotionTypes.SPEED || potionTypeNameMatches(potionType, "speed")) {
      return POTION_EFFECT_SPEED;
    }
    if (potionType == PotionTypes.SLOWNESS || potionTypeNameMatches(potionType, "slowness")) {
      return POTION_EFFECT_SLOWNESS;
    }
    if (potionType == PotionTypes.JUMP_BOOST || potionTypeNameMatches(potionType, "jump_boost")) {
      return POTION_EFFECT_JUMP_BOOST;
    }
    return -1;
  }

  private boolean potionTypeNameMatches(PotionType potionType, String key) {
    if (potionType.getName() == null) {
      return false;
    }
    String potionKey = potionType.getName().getKey();
    if (key.equalsIgnoreCase(potionKey)) {
      return true;
    }
    String text = potionType.getName().toString().toLowerCase(Locale.ROOT);
    return text.equals("minecraft:" + key) || text.equals(key);
  }

  private static class PotionEffectOutput {
    private final int potionEffectType;
    private final int potionEffectAmplifier;
    private final int potionEffectDuration;

    public PotionEffectOutput(
      int potionEffectType,
      int potionEffectAmplifier,
      int potionEffectDuration
    ) {
      this.potionEffectType = potionEffectType;
      this.potionEffectAmplifier = potionEffectAmplifier;
      this.potionEffectDuration = potionEffectDuration;
    }
  }
}
