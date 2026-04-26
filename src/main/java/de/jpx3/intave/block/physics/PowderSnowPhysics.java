package de.jpx3.intave.block.physics;

import de.jpx3.intave.version.MinecraftVersion;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.modifier.PowderSnowCollisionModifier;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Set;

final class PowderSnowPhysics implements BlockPhysic {
  private Set<Material> materials;
  private boolean supported;

  @Override
  public void setupFor(MinecraftVersion serverVersion) {
    materials = MaterialSearch.materialsThatContain("POWDER_SNOW");
    supported = !materials.isEmpty();
  }

  @Override
  public Motion entityCollidedWithBlock(User user, Location location, Location from, double motionX, double motionY, double motionZ) {
    MovementMetadata movementData = user.meta().movement();
    if (
      location.getBlockX() != (int) Math.floor(movementData.positionX) ||
      location.getBlockY() != (int) Math.floor(movementData.positionY) ||
      location.getBlockZ() != (int) Math.floor(movementData.positionZ)
    ) {
      return null;
    }
    Material block = VolatileBlockAccess.typeAccess(
      user, user.player().getWorld(),
      movementData.positionX,
      movementData.positionY,
      movementData.positionZ
    );
    boolean walkingOnPowderSnow = PowderSnowCollisionModifier.canWalkOnPowderSnow(user.player())
      && movementData.verifiedPositionY > location.getBlockY() + 1.0D - 1.0E-5D
      && !movementData.isSneaking();
    if (materials.contains(block) && !walkingOnPowderSnow) {
      movementData.setMotionMultiplier(new Vector(0.9f, 1.5f, 0.9f));
    }
    return null;
  }

  @Override
  public boolean supportedOnServerVersion() {
    return supported;
  }

  @Override
  public Set<Material> applicableMaterials() {
    return materials;
  }
}
