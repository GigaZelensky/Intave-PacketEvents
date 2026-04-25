package de.jpx3.intave.check.combat.heuristics.detect.combatpatterns;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.DMG_LIGHT;

public final class RotationAccuracyYawHeuristic extends MetaCheckPart<Heuristics, RotationAccuracyYawHeuristic.RotationAccuracyHeuristicMeta> {
  private final IntavePlugin plugin;

  public RotationAccuracyYawHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationAccuracyHeuristicMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION_LOOK, LOOK
    }
  )
  public void receiveMovement(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    AttackMetadata attackData = meta.attack();
    RotationAccuracyHeuristicMeta heuristicMeta = metaOf(player);
    Entity entity = attackData.lastAttackedEntity();
    float rotationYaw = movementData.rotationYaw;
    float perfectYaw = attackData.perfectYaw();
    float closestPerfectYaw = attackData.perfectClosestYaw();
    float yawSpeed = MathHelper.distanceInDegrees(rotationYaw, movementData.lastRotationYaw);
    float distanceToPerfectYaw = MathHelper.distanceInDegrees(perfectYaw, rotationYaw);
    float distanceToClosestPerfectYaw = MathHelper.distanceInDegrees(closestPerfectYaw, rotationYaw);
    if (entity == null || movementData.lastTeleport < 5) {
      return;
    }
    if (attackData.recentlyAttacked(150)
      && yawSpeed > 1001
      && attackData.lastReach() > 1.0
      && !attackData.recentlySwitchedEntity(200)
    ) {
      if (heuristicMeta.snapVL++ > 0) {
        String description = "suspicious rotation snap (" + yawSpeed + ")";
        int options = LIMIT_4 | SUGGEST_MINING;
        String checkName = resolveCheckName(0);
        Anomaly anomaly = Anomaly.anomalyOf(checkName, Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, options);
        parentCheck().saveAnomaly(player, anomaly);
        //dmc16
//        user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, "16");
        user.nerf(AttackNerfStrategy.CRITICALS, checkName);
      }
    } else if (heuristicMeta.snapVL > 0) {
      heuristicMeta.snapVL -= 0.1;
    }
    if (entity.moving(0.05) && attackData.recentlyAttacked(1000)) {
      if (yawSpeed > 1.0) {
        if (yawSpeed > 3.0) {
          double increase = MathHelper.minmax(-2.5, (2.2 - distanceToPerfectYaw) * Math.min(6, yawSpeed), 2);
          heuristicMeta.followBalance += increase;
          if (heuristicMeta.followBalance < 0) {
            heuristicMeta.followBalance = 0;
          }
          if (heuristicMeta.followBalance > 25) {
            String description = "follows entity movement too precisely";
            int options = LIMIT_2 | LIMIT_1 | SUGGEST_MINING | DELAY_64s;
            String checkName = resolveCheckName(1);
            Anomaly anomaly = Anomaly.anomalyOf(checkName, Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, options);
            parentCheck().saveAnomaly(player, anomaly);
            heuristicMeta.followBalance -= 7;
//            plugin.eventService().attackCancelService().requestDamageCancel(user, AttackCancelType.LIGHT);
            user.nerf(AttackNerfStrategy.CRITICALS, checkName);
          }
        }
        // Check perfect yaw
        if (distanceToPerfectYaw == 0 || distanceToClosestPerfectYaw == 0) {
          String description = "rotated yaw too precise (0.0)";
          int options = LIMIT_2 | DELAY_128s | SUGGEST_MINING;
          String checkName = resolveCheckName(2);
          Anomaly anomaly = Anomaly.anomalyOf(checkName, Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, options);
          parentCheck().saveAnomaly(player, anomaly);
          //dmc17
          user.nerf(AttackNerfStrategy.CRITICALS, checkName);
        }
        // Check yaw accuracy
        if (yawSpeed > 3.0) {
          double expectedDifference = 2.0;//Math.min(10, yawSpeed * 0.6);
          heuristicMeta.balanceYawAccuracy += expectedDifference - (distanceToPerfectYaw / 0.8);
          heuristicMeta.balanceYawAccuracy = Math.max(0, heuristicMeta.balanceYawAccuracy);
          int suspiciousLevel = (int) heuristicMeta.balanceYawAccuracy;
          if (suspiciousLevel > 8) {
            if (heuristicMeta.rotationAccuracyVL++ > 3) {
              String description = "high accuracy rotation yaw vl:" + suspiciousLevel;
              int options = LIMIT_2 | DELAY_32s | SUGGEST_MINING;
              String checkName = resolveCheckName(3);
              Anomaly anomaly = Anomaly.anomalyOf(checkName, Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, options);
              parentCheck().saveAnomaly(player, anomaly);
              //dmc18
//              user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, "18");
              user.nerf(AttackNerfStrategy.CRITICALS, checkName);
            }
          } else if (heuristicMeta.rotationAccuracyVL > 0) {
            heuristicMeta.rotationAccuracyVL -= 0.005;
          }
        }
        // Check yaw accuracy (other)
        if (distanceToPerfectYaw > 4.0) {
          heuristicMeta.balanceYawAccuracyOther = 0;
        } else if (heuristicMeta.balanceYawAccuracyOther++ > 50) {
          String description = "keeps high yaw accuracy in " + (int) heuristicMeta.balanceYawAccuracyOther + " rotations";
          int options = LIMIT_2 | DELAY_32s | SUGGEST_MINING;
          Anomaly anomaly = Anomaly.anomalyOf(resolveCheckName(4), Confidence.MAYBE, Anomaly.Type.KILLAURA, description, options);
          parentCheck().saveAnomaly(player, anomaly);
          heuristicMeta.balanceYawAccuracyOther = 0;
          //dmc19
//          user.nerf(AttackNerfStrategy.HT_LIGHT, "19");
        }
      }
    }
    if (Hypot.fast(movementData.motionX(), movementData.motionZ()) < 0.05
      || attackData.lastReach() < 1
      || !entity.moving(0.05)) {
      return;
    }
    int direction = perfectYaw > rotationYaw ? 1 : 0;
    boolean sameYawDirection = heuristicMeta.lastBodyDirection == direction;
    if (!sameYawDirection) {
      heuristicMeta.bitBoxCornerBalance = 0;
    } else if (yawSpeed > 3 && !movementData.isInRidingVehicle()) {
      float deviation = MathHelper.distanceInDegrees(heuristicMeta.prevDistanceToPerfectYaw, distanceToPerfectYaw);
      double increase = MathHelper.minmax(-0.2, (1 - deviation) * 4, 4);
      heuristicMeta.bitBoxCornerBalance = (int) MathHelper.minmax(0, heuristicMeta.bitBoxCornerBalance + increase, 100);
      if (heuristicMeta.bitBoxCornerBalance > 30) {
        long lastDetection = System.currentTimeMillis() - heuristicMeta.lastHARYAnomaly;
        int options = SUGGEST_MINING | DELAY_16s | LIMIT_2;
        Confidence confidence = /*lastDetection < 2000 ? Confidence.LIKELY :*/ Confidence.LIKELY;
        String checkName = resolveCheckName(5);
        Anomaly anomaly = Anomaly.anomalyOf(checkName, confidence, Anomaly.Type.KILLAURA, "high accuracy rotation yaw on hit-box corners", options);
        parentCheck().saveAnomaly(player, anomaly);
        heuristicMeta.bitBoxCornerBalance -= 20;
        heuristicMeta.lastHARYAnomaly = System.currentTimeMillis();
        user.nerf(DMG_LIGHT, checkName);
      }
    }
    heuristicMeta.lastBodyDirection = direction;
    heuristicMeta.prevDistanceToPerfectYaw = distanceToPerfectYaw;
  }

  private String resolveCheckName(int id) {
    return "yaw:acc(" + id + ")";
  }

  public static final class RotationAccuracyHeuristicMeta extends CheckCustomMetadata {
    private double balanceYawAccuracy;
    private double balanceYawAccuracyOther;
    private double rotationAccuracyVL;
    private double followBalance;
    private double snapVL;

    private long lastHARYAnomaly;

    private int lastBodyDirection;
    private int bitBoxCornerBalance;
    private float prevDistanceToPerfectYaw;
  }
}
