package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.ColliderResult;
import de.jpx3.intave.player.collider.simple.SimpleColliderResult;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import static de.jpx3.intave.share.ClientMath.cos;
import static de.jpx3.intave.share.ClientMath.sin;

final class ElytraSimulator extends BaseSimulator {
  private static final double FIREWORK_ACCELERATION = 0.1D;
  private static final double FIREWORK_TARGET_SPEED = 1.5D;
  private static final double FIREWORK_BLEND = 0.5D;
  private static final int FIREWORK_MAX_RANDOM_LIFETIME = 11;
  private static final int FIREWORK_TRACKING_LENIENCY_TICKS = 1;

  @Override
  public Simulation simulate(
    User user, Motion motion,
    SimulationEnvironment environment,
    MovementConfiguration configuration
  ) {
    Simulation vanillaSimulation = simulateStep(user, motion.copy(), environment, configuration, false).reusableCopy();
    MovementMetadata movementData = user.meta().movement();
    if (!fireworkBoostPossiblyActive(movementData)) {
      return vanillaSimulation;
    }

    Simulation fireworkSimulation = simulateStep(user, motion.copy(), environment, configuration, true).reusableCopy();
    Motion receivedMotion = environment.motion();
    return fireworkSimulation.accuracy(receivedMotion) < vanillaSimulation.accuracy(receivedMotion)
      ? fireworkSimulation
      : vanillaSimulation;
  }

  private Simulation simulateStep(
    User user, Motion motion,
    SimulationEnvironment environment,
    MovementConfiguration configuration,
    boolean applyFireworkBoost
  ) {
    float rotationPitch = environment.rotationPitch();
    Vector lookVector = environment.lookVector();

    double positionX = environment.verifiedPositionX();
    double positionY = environment.verifiedPositionY();
    double positionZ = environment.verifiedPositionZ();

    float pitchRad = rotationPitch * 0.017453292F;
    double lookVectorX = lookVector.getX();
    double lookVectorZ = lookVector.getZ();
    double distance = Math.sqrt(lookVectorX * lookVectorX + lookVectorZ * lookVectorZ);
    double dist2 = Math.sqrt(motion.motionX * motion.motionX + motion.motionZ * motion.motionZ);
    double rotationVectorLength = Math.sqrt(lookVector.lengthSquared());
    float pitchCosine = cos(pitchRad);
    pitchCosine = (float) ((double) pitchCosine * (double) pitchCosine * Math.min(1.0D, rotationVectorLength / 0.4D));
    motion.motionY += environment.gravity() * (-1 + pitchCosine * 0.75);

    if (motion.motionY < 0.0D && distance > 0.0D) {
      double d2 = motion.motionY * -0.1D * (double) pitchCosine;
      motion.motionY += d2;
      motion.motionX += lookVectorX * d2 / distance;
      motion.motionZ += lookVectorZ * d2 / distance;
    }

    if (pitchRad < 0.0F && distance > 0.0D) {
      double d9 = dist2 * (double) (-sin(pitchRad)) * 0.04D;
      motion.motionY += d9 * 3.2D;
      motion.motionX += -lookVectorX * d9 / distance;
      motion.motionZ += -lookVectorZ * d9 / distance;
    }

    if (distance > 0.0D) {
      motion.motionX += (lookVectorX / distance * dist2 - motion.motionX) * 0.1D;
      motion.motionZ += (lookVectorZ / distance * dist2 - motion.motionZ) * 0.1D;
    }

    if (applyFireworkBoost) {
      applyFireworkBoostIfActive(user.meta().movement(), motion, lookVector);
    }

    motion.motionX *= 0.99f;
    motion.motionY *= 0.98f;
    motion.motionZ *= 0.99f;

    tryRelinkFlyingPosition(user, motion, environment, applyFireworkBoost);

    ColliderResult collisionResult = Colliders.collision(
      user, environment, motion, environment.inWeb(),
      positionX, positionY, positionZ
    );
    notePossibleFlyingPacket(user, collisionResult);
    return Simulation.of(user, configuration, collisionResult);
  }

  private void tryRelinkFlyingPosition(
    User user,
    Motion motion,
    SimulationEnvironment environment,
    boolean applyFireworkBoost
  ) {
    Player player = user.player();
    MovementMetadata movementData = user.meta().movement();
    float rotationPitch = environment.rotationPitch();
    Vector lookVector = environment.lookVector();

    double positionX = environment.verifiedPositionX();
    double positionY = environment.verifiedPositionY();
    double positionZ = environment.verifiedPositionZ();

    boolean onGround;
    double resetMotion = environment.resetMotion();
    double jumpUpwardsMotion = environment.jumpMotion();

    int interpolations = 0;
    double interpolateX = motion.motionX;
    double interpolateY = motion.motionY;
    double interpolateZ = motion.motionZ;

    for (; interpolations <= 2; interpolations++) {
      SimpleColliderResult colliderResult = Colliders.simplifiedCollision(
        player, movementData,
        positionX, positionY, positionZ,
        interpolateX, interpolateY, interpolateZ
      );

      positionX += colliderResult.motionX();
      positionY += colliderResult.motionY();
      positionZ += colliderResult.motionZ();

      double diffX = positionX - environment.verifiedPositionX();
      double diffY = positionY - environment.verifiedPositionY();
      double diffZ = positionZ - environment.verifiedPositionZ();
      onGround = colliderResult.onGround();

      boolean jumpLessThanExpected = colliderResult.motionY() < jumpUpwardsMotion;
      boolean jump = onGround && Math.abs(((colliderResult.motionY()) + jumpUpwardsMotion) - environment.motionY()) < 1e-5 && jumpLessThanExpected;

      if (!flyingPacket(diffX, diffY, diffZ) && !jump) {
        break;
      }

      float f = rotationPitch * 0.017453292F;
      double rotationVectorDistance = Math.sqrt(lookVector.getX() * lookVector.getX() + lookVector.getZ() * lookVector.getZ());
      double dist2 = Math.sqrt(motion.motionX * motion.motionX + motion.motionZ * motion.motionZ);
      double rotationVectorLength = Math.sqrt(lookVector.lengthSquared());
      float pitchCosine = cos(f);
      pitchCosine = (float) ((double) pitchCosine * (double) pitchCosine * Math.min(1.0D, rotationVectorLength / 0.4D));
      motion.motionY += environment.gravity() * (-1 + pitchCosine * 0.75);

      if (motion.motionY < 0.0D && rotationVectorDistance > 0.0D) {
        double d2 = motion.motionY * -0.1D * (double) pitchCosine;
        motion.motionY += d2;
        motion.motionX += lookVector.getX() * d2 / rotationVectorDistance;
        motion.motionZ += lookVector.getZ() * d2 / rotationVectorDistance;
      }

      if (f < 0.0F && rotationVectorDistance > 0.0D) {
        double d9 = dist2 * (double) (-sin(f)) * 0.04D;
        motion.motionY += d9 * 3.2D;
        motion.motionX += -lookVector.getX() * d9 / rotationVectorDistance;
        motion.motionZ += -lookVector.getZ() * d9 / rotationVectorDistance;
      }

      if (rotationVectorDistance > 0.0D) {
        motion.motionX += (lookVector.getX() / rotationVectorDistance * dist2 - motion.motionX) * 0.1D;
        motion.motionZ += (lookVector.getZ() / rotationVectorDistance * dist2 - motion.motionZ) * 0.1D;
      }

      if (applyFireworkBoost) {
        applyFireworkBoostIfActive(movementData, motion, lookVector);
      }

      motion.motionX *= 0.99f;
      motion.motionY *= 0.98f;
      motion.motionZ *= 0.99f;

      if (Math.abs(interpolateX) < resetMotion) {
        interpolateX = 0;
      }
      if (Math.abs(interpolateY) < resetMotion) {
        interpolateY = 0;
      }
      if (Math.abs(interpolateZ) < resetMotion) {
        interpolateZ = 0;
      }
    }
    if (interpolations != 0) {
      movementData.resetFlyingPacketAccurate();
    }
  }

  @Override
  public String debugName() {
    return "ELYTRA";
  }

  @Override
  public boolean affectedByMovementKeys() {
    return false;
  }

  static int maxFireworkBoostTicks(MovementMetadata movementData) {
    return 10 * (Math.max(movementData.fireworkRocketsPower, 0) + 1)
      + FIREWORK_MAX_RANDOM_LIFETIME
      + FIREWORK_TRACKING_LENIENCY_TICKS;
  }

  static boolean fireworkBoostPossiblyActive(MovementMetadata movementData) {
    return movementData.fireworkRocketsTicks <= maxFireworkBoostTicks(movementData);
  }

  private static void applyFireworkBoostIfActive(MovementMetadata movementData, Motion motion, Vector lookVector) {
    if (!fireworkBoostPossiblyActive(movementData)) {
      return;
    }

    double currentX = motion.motionX;
    double currentY = motion.motionY;
    double currentZ = motion.motionZ;

    motion.motionX = currentX
      + lookVector.getX() * FIREWORK_ACCELERATION
      + (lookVector.getX() * FIREWORK_TARGET_SPEED - currentX) * FIREWORK_BLEND;
    motion.motionY = currentY
      + lookVector.getY() * FIREWORK_ACCELERATION
      + (lookVector.getY() * FIREWORK_TARGET_SPEED - currentY) * FIREWORK_BLEND;
    motion.motionZ = currentZ
      + lookVector.getZ() * FIREWORK_ACCELERATION
      + (lookVector.getZ() * FIREWORK_TARGET_SPEED - currentZ) * FIREWORK_BLEND;
  }
}
