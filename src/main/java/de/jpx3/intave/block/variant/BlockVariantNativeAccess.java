package de.jpx3.intave.block.variant;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.BlockAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.user.User;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.Locale;

public final class BlockVariantNativeAccess {
  private static final boolean MODERN_MATERIAL_PROCESSING = MinecraftVersions.VER1_13_0.atOrAbove();

  public static void setup() {
  }

  /**
   * This method performs a direct type lookup, which will be quite heavy if the underlying chunk has not been loaded yet.
   * To avoid this performance-bottleneck, use {@link VolatileBlockAccess#variantIndexAccess(User, World, double, double, double)} instead,
   * providing fast performance, a robust cache implementation and stable chunk fallback
   */
  @Deprecated
  public static int variantAccess(Block block) {
    if (!MODERN_MATERIAL_PROCESSING) {
      return BlockAccess.global().variantIndexOf(block);
    }
    int index = BlockVariantRegister.variantIndexOf(block.getType(), nativeVariantAccess(block));
    return Math.max(index, 0);
  }

  public static int variantAccess(WrappedBlockState blockState) {
    if (!MODERN_MATERIAL_PROCESSING) {
      return SpigotConversionUtil.toBukkitMaterialData(blockState).getData();
    }
    Material type = materialAccess(blockState);
    Object nativeBlockData = blockState;
    int index = BlockVariantRegister.variantIndexOf(type, nativeBlockData);
    if (index < 0) {
      throw new IllegalStateException("Invalid block data update: " + type + "/" + blockState);
    }
    return index;
  }

  public static Material materialAccess(WrappedBlockState blockState) {
    if (!MODERN_MATERIAL_PROCESSING) {
      return SpigotConversionUtil.toBukkitMaterialData(blockState).getItemType();
    }
    return SpigotConversionUtil.toBukkitBlockData(blockState).getMaterial();
  }

  public static Object nativeVariantAccess(Block bukkitBlock) {
    if (MODERN_MATERIAL_PROCESSING) {
      return nativeVariantAccess(blockDataOf(bukkitBlock));
    }
    return BlockAccess.global().nativeVariantOf(bukkitBlock);
  }

  public static WrappedBlockState blockStateAccess(Block block) {
    if (MODERN_MATERIAL_PROCESSING) {
      return wrappedBlockStateOf(blockDataOf(block));
    }
    return SpigotConversionUtil.fromBukkitMaterialData(block.getState().getData());
  }

  private static Object nativeVariantAccess(BlockData blockData) {
    return wrappedBlockStateOf(blockData);
  }

  private static WrappedBlockState wrappedBlockStateOf(BlockData blockData) {
    WrappedBlockState blockState = SpigotConversionUtil.fromBukkitBlockData(blockData);
    if (!blockState.getType().isAir() || isAir(blockData.getMaterial())) {
      return blockState;
    }
    WrappedBlockState fallback = defaultStateOf(blockData.getMaterial());
    return fallback.getType().isAir() ? blockState : fallback;
  }

  private static WrappedBlockState defaultStateOf(Material material) {
    WrappedBlockState fallback = SpigotConversionUtil.fromBukkitBlockData(createBlockData(material));
    if (!fallback.getType().isAir()) {
      return fallback;
    }
    String key = material.name().toLowerCase(Locale.ROOT);
    StateType type = StateTypes.getByName("minecraft:" + key);
    return type == null ? fallback : WrappedBlockState.getDefaultState(type);
  }

  private static BlockData blockDataOf(Block block) {
    try {
      return (BlockData) block.getClass().getMethod("getBlockData").invoke(block);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Unable to resolve Bukkit block data for " + block.getType(), exception);
    }
  }

  private static BlockData createBlockData(Material material) {
    try {
      return (BlockData) Bukkit.class.getMethod("createBlockData", Material.class).invoke(null, material);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Unable to create block data for " + material, exception);
    }
  }

  public static boolean isAir(Material material) {
    return material.name().endsWith("AIR");
  }
}
