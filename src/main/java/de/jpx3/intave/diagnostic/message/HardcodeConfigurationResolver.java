package de.jpx3.intave.diagnostic.message;

import org.bukkit.ChatColor;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class HardcodeConfigurationResolver implements ConfigurationResolver {
  @Override
  public OutputConfiguration of(UUID owner) {
    return defaultConfiguration(owner);
  }

  private OutputConfiguration defaultConfiguration(UUID owner) {
    EnumSet<MessageCategory> categories = EnumSet.allOf(MessageCategory.class);
    categories.remove(MessageCategory.SIMFUL);
    categories.remove(MessageCategory.TRUSTSET);
    categories.remove(MessageCategory.SIMFLT);

    Map<MessageCategory, ChatColor> colors = new HashMap<>();
    colors.put(MessageCategory.ATLALI, ChatColor.RED);
    colors.put(MessageCategory.ATRAFLT, ChatColor.RED);
    colors.put(MessageCategory.HERAN, ChatColor.RED);
    colors.put(MessageCategory.SIMFLT, ChatColor.DARK_GRAY);
    colors.put(MessageCategory.SIMFUL, ChatColor.GRAY);
    colors.put(MessageCategory.PKBF, ChatColor.GRAY);
    colors.put(MessageCategory.MKLG, ChatColor.DARK_PURPLE);
    colors.put(MessageCategory.PKDL, ChatColor.GRAY);
    colors.put(MessageCategory.TRUSTSET, ChatColor.GRAY);

    return OutputConfiguration.builder()
      .setOwner(owner)
      .setMinimumSeverity(MessageSeverity.LOW)
      .setPrefixDetail(PrefixDetail.REDUCED_NO_PREFIX)
      .setActiveCategories(categories)
      .setOutputColors(colors)
      .defaultDetailSelect(MessageDetail.FULL)
      .build();
  }
}
