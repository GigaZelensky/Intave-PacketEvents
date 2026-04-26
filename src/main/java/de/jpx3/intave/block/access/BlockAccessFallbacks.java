package de.jpx3.intave.block.access;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.share.BlockPosition;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

final class BlockAccessFallbacks {
  private BlockAccessFallbacks() {
  }

  static float blockDamage(World world, BlockPosition blockPosition) {
    WrappedBlockState blockState = blockState(world, blockPosition);
    float hardness = blockState.getType().getHardness();
    if (hardness < 0.0F) {
      return 0.0F;
    }
    if (hardness == 0.0F) {
      return 1.0F;
    }
    return 1.0F / (hardness * 30.0F);
  }

  static boolean replacementPlace(World world, Player player, BlockPosition blockPosition) {
    Block block = block(world, blockPosition);
    Material target = block.getType();
    Material held = player == null || player.getItemInHand() == null ? Material.AIR : player.getItemInHand().getType();
    return blockState(block).getType().isReplaceable() && target != held;
  }

  private static WrappedBlockState blockState(World world, BlockPosition blockPosition) {
    return blockState(block(world, blockPosition));
  }

  private static WrappedBlockState blockState(Block block) {
    return BlockVariantNativeAccess.blockStateAccess(block);
  }

  private static Block block(World world, BlockPosition blockPosition) {
    return VolatileBlockAccess.blockAccess(
      world,
      blockPosition.getBlockX(),
      blockPosition.getBlockY(),
      blockPosition.getBlockZ()
    );
  }
}
